package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * [MorphIcon]이 모핑하는 선 아이콘. 24×24 격자 좌표의 꺾은선(획) 묶음이다. 첫 점과 끝 점이 같은 획은 닫힌 획으로 보고 [filled]일 때 안을 칠한다.
 * 획마다 길이를 따라 같은 수의 점으로 다시 나누므로 점 수가 달라도 서로 모핑할 수 있다. 획 수가 다르면 남는 획은 자기 중심점으로 줄어들며 사라진다.
 * 생성할 때 획이 없거나 점이 2개보다 적은 획이 있으면 [IllegalArgumentException]을 던진다.
 *
 * @property filled 닫힌 획의 안을 칠할지
 * @property mirrored RTL에서 좌우를 뒤집을지. 뒤로 가기 화살표처럼 방향이 있는 아이콘에 쓴다.
 */
@Immutable
public class KMorphGlyph(strokes: List<List<Offset>>, public val filled: Boolean = false, public val mirrored: Boolean = false) {
    init {
        require(strokes.isNotEmpty() && strokes.all { it.size >= 2 }) { "획이 하나 이상이고 획마다 점이 2개 이상이어야 합니다." }
    }

    internal val points: List<FloatArray> = strokes.map { resample(it) }
    internal val closed: List<Boolean> = strokes.map { it.first() == it.last() }
}

/**
 * [MorphIcon]에 쓰는 아이콘. 아래 다섯 쌍은 서로 모핑할 때 자연스럽게 보이도록 획 순서를 맞췄다.
 * [Search]와 [Close], [Plus]와 [Check], [Menu]와 [ArrowBack], [Heart]와 [HeartFilled], [VolumeLow]와 [VolumeHigh].
 */
public object KMorphIcons {
    public val Search: KMorphGlyph = KMorphGlyph(listOf(circle(10.5f, 10.5f, 6f), listOf(Offset(15f, 15f), Offset(20f, 20f))))

    public val Close: KMorphGlyph = KMorphGlyph(listOf(listOf(Offset(6f, 6f), Offset(18f, 18f)), listOf(Offset(18f, 6f), Offset(6f, 18f))))

    public val Plus: KMorphGlyph = KMorphGlyph(listOf(listOf(Offset(12f, 5f), Offset(12f, 19f)), listOf(Offset(5f, 12f), Offset(19f, 12f))))

    public val Check: KMorphGlyph =
        KMorphGlyph(listOf(listOf(Offset(9.5f, 17f), Offset(19f, 7f)), listOf(Offset(5f, 12.5f), Offset(9.5f, 17f))))

    public val Menu: KMorphGlyph = KMorphGlyph(
        listOf(
            listOf(Offset(4f, 6f), Offset(20f, 6f)),
            listOf(Offset(4f, 12f), Offset(20f, 12f)),
            listOf(Offset(4f, 18f), Offset(20f, 18f)),
        ),
    )

    public val ArrowBack: KMorphGlyph = KMorphGlyph(
        listOf(
            listOf(Offset(4.5f, 12f), Offset(11f, 5.5f)),
            listOf(Offset(4.5f, 12f), Offset(19.5f, 12f)),
            listOf(Offset(4.5f, 12f), Offset(11f, 18.5f)),
        ),
        mirrored = true,
    )

    public val Heart: KMorphGlyph = KMorphGlyph(listOf(heart()))

    public val HeartFilled: KMorphGlyph = KMorphGlyph(listOf(heart()), filled = true)

    public val VolumeLow: KMorphGlyph = KMorphGlyph(listOf(speaker(), arc(12.5f, 12f, 3.6f, WAVE_SMALL)))

    public val VolumeHigh: KMorphGlyph =
        KMorphGlyph(listOf(speaker(), arc(12.5f, 12f, 3.6f, WAVE_SMALL), arc(12.5f, 12f, 7.2f, WAVE_LARGE)))
}

/**
 * [atEnd]가 바뀌면 [from] 아이콘이 [to] 아이콘으로 선을 따라 모핑한다. 칠하는 아이콘으로 바뀌면 안이 차오르며 살짝 튄다.
 * 기본 크기는 24dp이고 `modifier`의 크기가 있으면 그것을 따른다. 아이콘만 그리므로 TalkBack 이름과 클릭은 감싸는 버튼에 둔다.
 * '애니메이션 삭제' 설정에서는 바로 바뀐다.
 *
 * ```kotlin
 * KIconButton(…) 대신 Box(Modifier.kClickable { searching = !searching }) { MorphIcon(KMorphIcons.Search, KMorphIcons.Close, searching) }
 * ```
 *
 * @param strokeWidth 선 두께. 24dp 기준이며 크기에 맞춰 늘고 준다.
 */
@Composable
public fun MorphIcon(
    from: KMorphGlyph,
    to: KMorphGlyph,
    atEnd: Boolean,
    modifier: Modifier = Modifier,
    color: Color = LocalKineticaContentColor.current,
    strokeWidth: Dp = 2.dp,
) {
    val still = reducedMotion()
    val progress = animateFloatAsState(
        if (atEnd) 1f else 0f,
        if (still) snap() else spring(dampingRatio = MORPH_DAMPING, stiffness = MORPH_STIFFNESS),
        label = "morph-icon",
    )
    MorphIcon(from, to, { progress.value }, modifier, color, strokeWidth)
}

/**
 * [progress]만큼 [from]에서 [to]로 모핑한 아이콘을 그린다. 스크롤 위치나 끌기처럼 직접 진행을 정할 때 쓴다. [progress]는 그리기 단계에서 읽는다.
 *
 * @param progress 0이면 [from], 1이면 [to]. 스프링으로 넘친 값도 그대로 그린다.
 */
@Composable
public fun MorphIcon(
    from: KMorphGlyph,
    to: KMorphGlyph,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = LocalKineticaContentColor.current,
    strokeWidth: Dp = 2.dp,
) {
    val pairs = remember(from, to) { MorphPairs(from, to) }
    Spacer(
        modifier
            .size(ICON_SIZE)
            .drawWithCache {
                val unit = size.minDimension / GRID
                val stroke =
                    Stroke(strokeWidth.toPx() * size.minDimension / ICON_SIZE.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val path = Path()
                val fillPath = Path()
                val flip = layoutDirection == LayoutDirection.Rtl && (from.mirrored || to.mirrored)
                onDrawBehind {
                    val p = progress()
                    pairs.build(p, unit, path, fillPath)
                    val fill = (pairs.fillFrom + (pairs.fillTo - pairs.fillFrom) * p).coerceIn(0f, 1f)
                    val pop = if (pairs.fillFrom != pairs.fillTo) 1f + POP * sin(PI.toFloat() * p.coerceIn(0f, 1f)) else 1f
                    scale(if (flip) -pop else pop, pop) {
                        if (fill > 0f) drawPath(fillPath, color.copy(alpha = color.alpha * fill))
                        drawPath(path, color, style = stroke)
                    }
                }
            },
    )
}

/** 두 아이콘의 획을 짝지어 보간한다. 남는 획은 자기 중심점과 짝짓는다. */
private class MorphPairs(from: KMorphGlyph, to: KMorphGlyph) {
    private val count = maxOf(from.points.size, to.points.size)
    private val starts = List(count) { from.points.getOrNull(it) ?: collapsed(to.points[it]) }
    private val ends = List(count) { to.points.getOrNull(it) ?: collapsed(from.points[it]) }
    private val closed = List(count) { (from.closed.getOrNull(it) ?: false) || (to.closed.getOrNull(it) ?: false) }
    val fillFrom = if (from.filled) 1f else 0f
    val fillTo = if (to.filled) 1f else 0f

    fun build(p: Float, unit: Float, path: Path, fill: Path) {
        path.reset()
        fill.reset()
        for (s in 0 until count) {
            val a = starts[s]
            val b = ends[s]
            for (i in 0 until SAMPLES) {
                val x = (a[i * 2] + (b[i * 2] - a[i * 2]) * p) * unit
                val y = (a[i * 2 + 1] + (b[i * 2 + 1] - a[i * 2 + 1]) * p) * unit
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                if (closed[s] && i == 0) fill.moveTo(x, y)
                if (closed[s] && i > 0) fill.lineTo(x, y)
            }
            if (closed[s]) {
                path.close()
                fill.close()
            }
        }
    }

    private fun collapsed(points: FloatArray): FloatArray {
        var cx = 0f
        var cy = 0f
        for (i in 0 until SAMPLES) {
            cx += points[i * 2]
            cy += points[i * 2 + 1]
        }
        cx /= SAMPLES
        cy /= SAMPLES
        return FloatArray(SAMPLES * 2) { if (it % 2 == 0) cx else cy }
    }
}

/** 꺾은선을 길이를 따라 [SAMPLES]개 점으로 고르게 다시 나눈다. */
private fun resample(stroke: List<Offset>): FloatArray {
    val lengths = FloatArray(stroke.size)
    for (i in 1 until stroke.size) lengths[i] = lengths[i - 1] + hypot(stroke[i].x - stroke[i - 1].x, stroke[i].y - stroke[i - 1].y)
    val total = lengths.last()
    val out = FloatArray(SAMPLES * 2)
    var segment = 1
    for (k in 0 until SAMPLES) {
        val at = total * k / (SAMPLES - 1)
        while (segment < stroke.size - 1 && lengths[segment] < at) segment++
        val span = (lengths[segment] - lengths[segment - 1]).coerceAtLeast(EPSILON)
        val t = if (total <= 0f) 0f else ((at - lengths[segment - 1]) / span).coerceIn(0f, 1f)
        out[k * 2] = stroke[segment - 1].x + (stroke[segment].x - stroke[segment - 1].x) * t
        out[k * 2 + 1] = stroke[segment - 1].y + (stroke[segment].y - stroke[segment - 1].y) * t
    }
    return out
}

private fun circle(cx: Float, cy: Float, r: Float): List<Offset> = List(CURVE_POINTS + 1) { i ->
    val a = (i % CURVE_POINTS) * 2 * PI / CURVE_POINTS - PI / 2
    Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
}

private fun arc(cx: Float, cy: Float, r: Float, half: Float): List<Offset> = List(CURVE_POINTS / 2) { i ->
    val a = Math.toRadians((-half + 2 * half * i / (CURVE_POINTS / 2 - 1)).toDouble())
    Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
}

/** 고전적인 하트 곡선(16sin³t, 13cos t − 5cos 2t − 2cos 3t − cos 4t)을 격자 가운데에 맞춘다. */
@Suppress("MagicNumber") // 하트 곡선 식의 계수와 24 격자 위치
private fun heart(): List<Offset> = List(CURVE_POINTS + 1) { i ->
    val t = (i % CURVE_POINTS) * 2 * PI / CURVE_POINTS
    val x = 16 * sin(t) * sin(t) * sin(t)
    val y = 13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)
    Offset((12 + x * HEART_SCALE).toFloat(), (11.2 - y * HEART_SCALE).toFloat())
}

@Suppress("MagicNumber") // 24 격자 좌표
private fun speaker(): List<Offset> = listOf(
    Offset(4f, 9.5f),
    Offset(7.5f, 9.5f),
    Offset(12f, 5.5f),
    Offset(12f, 18.5f),
    Offset(7.5f, 14.5f),
    Offset(4f, 14.5f),
    Offset(4f, 9.5f),
)

private val ICON_SIZE = 24.dp
private const val GRID = 24f
private const val SAMPLES = 48
private const val CURVE_POINTS = 48
private const val HEART_SCALE = 0.5
private const val WAVE_SMALL = 50f
private const val WAVE_LARGE = 55f
private const val EPSILON = 1e-4f
private const val POP = 0.18f
private const val MORPH_DAMPING = 0.72f
private const val MORPH_STIFFNESS = 380f
