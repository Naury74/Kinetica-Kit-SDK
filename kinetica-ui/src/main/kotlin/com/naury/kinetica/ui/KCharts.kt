package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * 선 그래프에 그릴 계열 하나.
 *
 * 생성할 때 [points]에 NaN이나 무한대가 있으면 [IllegalArgumentException]을 던진다.
 *
 * @property points 왼쪽부터 차례로 그릴 값. 비어 있으면 이 계열은 그리지 않는다.
 * @property color 선과 점의 색
 * @property label 계열 이름. 값 말풍선과 TalkBack이 값 앞에 붙인다. `null`이면 값만 보인다.
 * @property fill 선 아래를 채울 브러시. 그래프 영역 크기에 맞춰 칠한다. `null`이면 채우지 않는다.
 */
@Immutable
public data class ChartSeries(
    val points: List<Float>,
    val color: Color = KChartDefaults.Blue,
    val label: String? = null,
    val fill: Brush? = null,
) {
    init {
        require(points.all { it.isFinite() }) { "points에 NaN이나 무한대를 넣을 수 없습니다." }
    }
}

/**
 * 도넛 차트의 조각 하나.
 *
 * 생성할 때 [value]가 음수이거나 NaN, 무한대이면 [IllegalArgumentException]을 던진다.
 *
 * @property value 조각의 크기. 모든 조각 합에 대한 비율로 각도를 정한다. 0이면 그리지 않는다.
 * @property label 조각 이름. 가운데 슬롯과 TalkBack이 읽는다.
 * @property color 조각 색
 */
@Immutable
public data class ChartSlice(val value: Float, val label: String, val color: Color) {
    init {
        require(value.isFinite() && value >= 0f) { "value는 0 이상의 유한한 값이어야 합니다." }
    }
}

/**
 * [ActivityRings]의 링 하나.
 *
 * 생성할 때 [progress]가 음수이거나 NaN, 무한대이면 [IllegalArgumentException]을 던진다.
 *
 * @property progress 목표 대비 비율. 1이면 한 바퀴이고, 1을 넘으면 그만큼 겹쳐서 더 돈다.
 * @property color 링 색. 시작 부분은 이 색보다 조금 어둡게 그린다.
 * @property label 링 이름. TalkBack이 비율 앞에 읽는다. `null`이면 "1번째 링"처럼 순서로 읽는다.
 */
@Immutable
public data class ActivityRing(val progress: Float, val color: Color, val label: String? = null) {
    init {
        require(progress.isFinite() && progress >= 0f) { "progress는 0 이상의 유한한 값이어야 합니다." }
    }
}

/** [KBarChart]의 막대 방향 */
public enum class KBarOrientation {
    /** 막대가 아래에서 위로 자란다. 항목은 왼쪽부터 놓는다. */
    Vertical,

    /** 막대가 시작 쪽에서 끝 쪽으로 자란다. 항목은 위부터 놓고, 이름은 시작 쪽에 둔다. */
    Horizontal,
}

/** 차트 컴포넌트의 기본 색과 값 형식 */
public object KChartDefaults {
    /** 기본 선과 막대 색 */
    public val Blue: Color = Color(0xFF0A84FF)

    /** 여러 계열이나 조각에 차례로 쓰기 좋은 색 목록. 밝은 배경과 어두운 배경 모두에서 서로 구별된다. */
    public val Palette: List<Color> = listOf(
        Blue,
        Color(0xFF30D158),
        Color(0xFFFF9F0A),
        Color(0xFFFF375F),
        Color(0xFFBF5AF2),
        Color(0xFF40C8E0),
        Color(0xFFFFD60A),
    )

    /** 활동 링의 움직이기 색 */
    public val Move: Color = Color(0xFFFA114F)

    /** 활동 링의 운동하기 색 */
    public val Exercise: Color = Color(0xFF92E82A)

    /** 활동 링의 일어서기 색 */
    public val Stand: Color = Color(0xFF1EEAEF)

    /** [color]를 위에서 아래로 옅어지게 칠하는 선 아래 채우기 브러시 */
    public fun fill(color: Color): Brush = Brush.verticalGradient(listOf(color.copy(alpha = FILL_TOP_ALPHA), color.copy(alpha = 0f)))

    /** 기기 언어에 맞춰 소수 둘째 자리까지 쓴 값. 천 단위 구분 기호를 넣는다. */
    public fun formatValue(value: Float): String =
        NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(value.toDouble())
}

/** 값 축 범위. [min]이 아래(또는 왼쪽) 끝이다. */
internal class ChartRange(val min: Float, val max: Float) {
    fun fraction(value: Float): Float = if (max == min) CHART_HALF else (value - min) / (max - min)
}

/** [arrays]의 모든 값을 담는 범위. [includeZero]면 0도 넣고, 위아래로 [padding] 비율만큼 여유를 둔다. 값이 하나뿐이면 ±1로 넓힌다. */
internal fun chartRange(arrays: List<FloatArray>, includeZero: Boolean, padding: Float): ChartRange {
    var low = if (includeZero) 0f else Float.POSITIVE_INFINITY
    var high = if (includeZero) 0f else Float.NEGATIVE_INFINITY
    arrays.forEach { values ->
        values.forEach {
            low = minOf(low, it)
            high = maxOf(high, it)
        }
    }
    if (low > high) return ChartRange(0f, 1f)
    if (low == high) return ChartRange(low - 1f, high + 1f)
    val extra = (high - low) * padding
    val bottom = if (includeZero && low == 0f) 0f else low - extra
    val top = if (includeZero && high == 0f) 0f else high + extra
    return ChartRange(bottom, top)
}

/**
 * 데이터가 바뀌면 이전 모양에서 새 모양으로 값을 보간한다. 배열 수와 각 길이가 같을 때만 보간하고, 다르면 바로 바꾼다.
 * 값은 그리기 단계에서 [value]로 읽어 재구성 없이 다시 그린다.
 */
@Stable
internal class ChartMorph(initial: List<FloatArray>, private val rangeOf: (List<FloatArray>) -> ChartRange) {
    private var from by mutableStateOf(initial)
    var target by mutableStateOf(initial)
        private set
    private var fromRange by mutableStateOf(rangeOf(initial))
    private var toRange by mutableStateOf(fromRange)
    private val progress = Animatable(1f)

    fun value(array: Int, index: Int): Float {
        val end = target[array][index]
        val start = from.getOrNull(array)?.getOrNull(index) ?: return end
        return lerp(start, end, progress.value)
    }

    fun rangeMin(): Float = lerp(fromRange.min, toRange.min, progress.value)

    fun rangeMax(): Float = lerp(fromRange.max, toRange.max, progress.value)

    /** 지금 보이는 범위에서 [value]의 위치(0..1) */
    fun fraction(value: Float): Float {
        val low = rangeMin()
        val high = rangeMax()
        return if (high == low) CHART_HALF else (value - low) / (high - low)
    }

    suspend fun retarget(next: List<FloatArray>, animate: Boolean) {
        if (next.size == target.size && next.indices.all { next[it].contentEquals(target[it]) }) return
        val sameShape = next.size == target.size && next.indices.all { next[it].size == target[it].size }
        val shown = target.indices.map { a -> FloatArray(target[a].size) { value(a, it) } }
        val shownRange = ChartRange(rangeMin(), rangeMax())
        from = shown
        target = shown
        fromRange = shownRange
        toRange = shownRange
        progress.snapTo(0f)
        target = next
        toRange = rangeOf(next)
        if (!sameShape) from = next
        if (sameShape && animate) {
            progress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
        } else {
            progress.snapTo(1f)
        }
    }
}

/** 처음 그릴 때 0에서 1로 [durationMs] 동안 오르는 값. '애니메이션 삭제'가 켜져 있으면 처음부터 1이다. */
@Composable
internal fun rememberChartReveal(durationMs: Int): Animatable<Float, AnimationVector1D> {
    val still = reducedMotion()
    val reveal = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!still) reveal.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }
    return reveal
}

/** 전체 진행 [t] 가운데 [start]..[end] 구간을 0..1로 바꿔 감속 곡선을 입힌 값 */
internal fun chartStage(t: Float, start: Float, end: Float): Float =
    FastOutSlowInEasing.transform(((t - start) / (end - start)).coerceIn(0f, 1f))

/** 그리기 중 [Path]를 새로 만들지 않도록 좌표와 기울기를 담아 두는 버퍼 */
internal class ChartLineBuffer {
    val line = Path()
    val area = Path()
    var xs = FloatArray(0)
        private set
    var ys = FloatArray(0)
        private set
    private var slopes = FloatArray(0)

    fun ensure(count: Int) {
        if (xs.size < count) {
            xs = FloatArray(count)
            ys = FloatArray(count)
            slopes = FloatArray(count)
        }
    }

    /** 앞 [count]개 점을 [line]에, 그 아래를 [bottom]까지 닫은 영역을 [area]에 넣는다. [smooth]면 값이 넘치지 않는 단조 3차 곡선으로 잇는다. */
    fun build(count: Int, smooth: Boolean, bottom: Float) {
        line.reset()
        area.reset()
        if (count == 0) return
        trace(line, count, smooth)
        trace(area, count, smooth)
        area.lineTo(xs[count - 1], bottom)
        area.lineTo(xs[0], bottom)
        area.close()
    }

    private fun trace(path: Path, count: Int, smooth: Boolean) {
        path.moveTo(xs[0], ys[0])
        if (!smooth || count < MIN_CURVE_POINTS) {
            for (i in 1 until count) path.lineTo(xs[i], ys[i])
            return
        }
        monotoneSlopes(count)
        for (i in 0 until count - 1) {
            val third = (xs[i + 1] - xs[i]) / THIRDS
            path.cubicTo(
                xs[i] + third,
                ys[i] + slopes[i] * third,
                xs[i + 1] - third,
                ys[i + 1] - slopes[i + 1] * third,
                xs[i + 1],
                ys[i + 1],
            )
        }
    }

    // 조화 평균 기울기(Fritsch-Butland)는 이웃 구간의 증감이 다르면 0이 되어, 곡선이 최고점과 최저점을 넘어가지 않는다.
    private fun monotoneSlopes(count: Int) {
        fun secant(k: Int): Float {
            val dx = xs[k + 1] - xs[k]
            return if (dx == 0f) 0f else (ys[k + 1] - ys[k]) / dx
        }
        slopes[0] = secant(0)
        slopes[count - 1] = secant(count - 2)
        for (k in 1 until count - 1) {
            val before = secant(k - 1)
            val after = secant(k)
            slopes[k] = if (before * after <= 0f) 0f else 2f / (1f / before + 1f / after)
        }
    }
}

/**
 * 말풍선을 붙일 위치(px). 위쪽에 자리가 있으면 [top] 위에 둔다. 자리가 없거나 [preferBelow]면 [bottom] 아래에 두는데,
 * [sideways]면 아래 대신 [top] 높이를 가운데로 [x]의 오른쪽(자리가 없으면 왼쪽) 옆에 둬서 같은 세로줄의 다른 점을 가리지 않는다.
 * [leftFirst]면 왼쪽을 먼저 본다.
 */
internal class ChartBubbleAnchor(
    val x: Float,
    val top: Float,
    val bottom: Float,
    val preferBelow: Boolean = false,
    val sideways: Boolean = false,
    val leftFirst: Boolean = false,
)

/** 바깥 레이아웃 방향을 기억해 두는 곳. 차트 안을 LTR로 그려도 말풍선 글자는 앱 방향을 따른다. */
internal val LocalChartTextDirection = compositionLocalOf { LayoutDirection.Ltr }

/**
 * 차트 위에 뜨는 글래스 값 말풍선. 부모를 가득 채우는 레이어로 두고, [anchor]를 배치 단계에서 읽어 재구성 없이 따라다닌다.
 * [visible]이 꺼지면 마지막 위치에서 옅어지며 사라진다.
 */
@Composable
internal fun ChartBubble(visible: Boolean, anchor: () -> ChartBubbleAnchor?, surface: SurfaceStyle, content: @Composable () -> Unit) {
    val shown by animateFloatAsState(if (visible) 1f else 0f, spring(stiffness = Spring.StiffnessMedium), label = "chart-bubble")
    if (!visible && shown == 0f) return
    val last = remember { ChartAnchorHolder() }
    val shape = remember { RoundedCornerShape(BUBBLE_CORNER) }
    Layout(
        content = {
            Box(
                Modifier
                    .graphicsLayer {
                        alpha = shown
                        val grow = BUBBLE_MIN_SCALE + (1f - BUBBLE_MIN_SCALE) * shown
                        scaleX = grow
                        scaleY = grow
                        transformOrigin = TransformOrigin(CHART_HALF, 1f)
                    }.kSurface(surface.forPopup(), shape)
                    .padding(horizontal = BUBBLE_PAD_H, vertical = BUBBLE_PAD_V),
            ) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides LocalChartTextDirection.current,
                    LocalKineticaContentColor provides surface.contentColor,
                ) {
                    content()
                }
            }
        },
    ) { measurables, constraints ->
        val bubble = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val target = anchor()?.also { last.value = it } ?: last.value ?: return@layout
            val spot =
                bubbleSpot(
                    target,
                    IntSize(bubble.width, bubble.height),
                    IntSize(constraints.maxWidth, constraints.maxHeight),
                    BUBBLE_GAP.roundToPx(),
                )
            bubble.place(spot)
        }
    }
}

private fun bubbleSpot(target: ChartBubbleAnchor, bubble: IntSize, area: IntSize, gap: Int): IntOffset {
    val maxX = (area.width - bubble.width).coerceAtLeast(0)
    val maxY = (area.height - bubble.height).coerceAtLeast(0)
    val above = target.top.roundToInt() - gap - bubble.height
    if (above >= 0 && !target.preferBelow) {
        return IntOffset((target.x - bubble.width / 2f).roundToInt().coerceIn(0, maxX), above)
    }
    if (target.sideways) {
        val right = target.x.roundToInt() + gap
        val left = target.x.roundToInt() - gap - bubble.width
        val x = when {
            target.leftFirst && left >= 0 -> left
            !target.leftFirst && right + bubble.width <= area.width -> right
            target.leftFirst -> right
            else -> left
        }
        return IntOffset(x.coerceIn(0, maxX), (target.top.roundToInt() - bubble.height / 2).coerceIn(0, maxY))
    }
    val x = (target.x - bubble.width / 2f).roundToInt().coerceIn(0, maxX)
    return IntOffset(x, (target.bottom.roundToInt() + gap).coerceAtMost(maxY))
}

private class ChartAnchorHolder {
    var value: ChartBubbleAnchor? = null
}

/** 재구성 사이에 마지막으로 고른 번호를 기억한다. 상태가 아니므로 써도 재구성을 부르지 않는다. */
internal class ChartIndexHolder {
    var value: Int = -1
}

/** 말풍선 안 한 줄. 색 점, 이름, 값 순서로 놓는다. */
@Composable
internal fun ChartBubbleRow(color: Color?, name: String?, value: String) {
    val ink = LocalKineticaContentColor.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BUBBLE_ROW_GAP)) {
        if (color != null) Box(Modifier.size(BUBBLE_DOT).background(color, CircleShape))
        if (name != null) ThemedText(name, style = TextStyle(color = ink.copy(alpha = MUTED_ALPHA), fontSize = SMALL_TEXT))
        ThemedText(value, style = TextStyle(color = ink, fontSize = VALUE_TEXT, fontWeight = FontWeight.SemiBold))
    }
}

/** 말풍선 맨 위의 작은 제목 */
@Composable
internal fun ChartBubbleTitle(text: String) {
    ThemedText(text, style = TextStyle(color = LocalKineticaContentColor.current.copy(alpha = MUTED_ALPHA), fontSize = SMALL_TEXT))
}

/** 말풍선 내용 묶음 */
@Composable
internal fun ChartBubbleColumn(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(BUBBLE_ROW_GAP)) { content() }
}

/** 축 이름 글자 모양 */
internal fun chartAxisText(ink: Color): TextStyle = TextStyle(color = ink.copy(alpha = AXIS_ALPHA), fontSize = SMALL_TEXT)

internal const val CHART_HALF = 0.5f
internal const val CHART_FULL_TURN = 360f
internal const val CHART_QUARTER_TURN = 90f
internal const val CHART_GRID_ALPHA = 0.08f
internal const val CHART_DIM_ALPHA = 0.32f
private const val FILL_TOP_ALPHA = 0.32f
private const val THIRDS = 3f
private const val MIN_CURVE_POINTS = 3
private const val BUBBLE_MIN_SCALE = 0.86f
private const val MUTED_ALPHA = 0.66f
private const val AXIS_ALPHA = 0.56f
private val BUBBLE_CORNER = 12.dp
private val BUBBLE_PAD_H = 10.dp
private val BUBBLE_PAD_V = 6.dp
private val BUBBLE_GAP = 8.dp
private val BUBBLE_ROW_GAP = 4.dp
private val BUBBLE_DOT = 7.dp
private val SMALL_TEXT = 11.sp
private val VALUE_TEXT = 13.sp
