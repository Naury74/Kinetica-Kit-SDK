// 파일 이름은 대표 컴포저블인 KLoadingIndicator를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** [KLoadingIndicator]의 움직임 모양. */
public enum class KLoadingStyle {
    /** 점 세 개가 차례로 튀어 오른다. 채팅의 입력 중 표시나 버튼 안처럼 작은 자리에 어울린다. */
    Dots,

    /** 막대 다섯 개가 물결처럼 차례로 늘었다 줄어든다. 음성 인식이나 재생 준비에 어울린다. */
    Bars,

    /** 둥근 도형이 원, 꽃잎, 별, 쿠키 모양을 돌아가며 바뀌면서 회전한다. 화면 가운데 큰 로딩에 쓴다. */
    Morph,

    /** 점 네 개가 꼬리를 물고 원 궤도를 돈다. 한 바퀴마다 빨라졌다 느려진다. */
    Orbit,

    /** 가운데 점에서 고리가 퍼져 나가며 옅어진다. 연결 중, 기기 찾는 중처럼 기다림이 긴 곳에 어울린다. */
    Pulse,
}

/**
 * 진행 정도를 모를 때 쓰는 로딩 표시. 링 하나뿐인 [KProgressRing]보다 표정이 많은 모양 다섯 가지를 [style]로 고른다.
 * 움직임은 그리기 단계에서만 읽으므로 돌아가는 동안 다시 구성하지 않고, 화면 밖으로 스크롤되면 멈춘다.
 *
 * '애니메이션 삭제' 설정이 켜져 있으면 움직이는 모양 대신 멈춘 모래시계 아이콘과 [label] 글자를 나란히 보여 준다.
 * TalkBack은 [label]과 함께 진행 정도를 모르는 진행 표시로 읽는다.
 *
 * ```kotlin
 * KLoadingIndicator(style = KLoadingStyle.Morph, contained = true)
 * ```
 *
 * @param color 움직이는 모양의 색
 * @param size 그리는 영역 한 변의 길이. 16dp보다 작으면 16dp로 그린다.
 * @param contained `true`면 표면 스타일 원판 위에 모양을 작게 올린다. 사진이나 목록 위에 떠 있는 로딩에 쓴다.
 * @param label TalkBack이 읽고 '애니메이션 삭제'에서 아이콘 옆에 보일 글자. null이면 "불러오는 중…"이다.
 */
@Composable
public fun KLoadingIndicator(
    modifier: Modifier = Modifier,
    style: KLoadingStyle = KLoadingStyle.Morph,
    color: Color = KineticaTheme.colors.accent,
    size: Dp = 48.dp,
    contained: Boolean = false,
    label: String? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val text = label ?: stringResource(R.string.kinetica_ui_feedback_loading_text)
    val semantics = Modifier.clearAndSetSemantics {
        contentDescription = text
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }
    if (reducedMotion()) {
        StillLoading(text, color, size, surface, modifier.then(semantics))
        return
    }
    val side = size.coerceAtLeast(MIN_SIZE)
    val clock = rememberLoopClock(running = true)
    val morph = remember { MorphPath() }
    val container = if (contained) Modifier.kSurface(surface, CircleShape) else Modifier
    Spacer(
        modifier
            .then(semantics)
            .size(side)
            .loopVisibility(clock)
            .then(container)
            .drawWithCache {
                val scale = if (contained) CONTAINED_SCALE else 1f
                val stroke = Stroke(this.size.minDimension * BAR_WIDTH, cap = StrokeCap.Round)
                onDrawBehind {
                    val s = this.size.minDimension * scale
                    val t = clock.seconds
                    when (style) {
                        KLoadingStyle.Dots -> drawDots(t, s, color)
                        KLoadingStyle.Bars -> drawBars(t, s, color, stroke)
                        KLoadingStyle.Morph -> drawMorph(t, s, color, morph)
                        KLoadingStyle.Orbit -> drawOrbit(t, s, color)
                        KLoadingStyle.Pulse -> drawPulse(t, s, color)
                    }
                }
            },
    )
}

/** '애니메이션 삭제'일 때의 로딩: 멈춘 모래시계와 글자. 움직임 없이도 기다리는 중임을 알 수 있게 글자를 함께 둔다. */
@Composable
private fun StillLoading(text: String, color: Color, size: Dp, surface: SurfaceStyle, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(KMetrics.IconTextGap), verticalAlignment = Alignment.CenterVertically) {
        BusyGlyph(size.coerceIn(MIN_SIZE, STILL_ICON_MAX), color, spinning = false)
        val style = TextStyle(color = surface.muted(STILL_TEXT_ALPHA), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        ThemedText(text, style = style)
    }
}

/**
 * 토스트, 버튼처럼 글자 옆에 놓는 작은 로딩 표시. [spinning]이면 원호가 돌고, 아니면 멈춘 모래시계를 그린다.
 * '애니메이션 삭제' 설정이면 [spinning]이어도 모래시계를 그린다.
 */
@Composable
internal fun BusyGlyph(size: Dp, color: Color, spinning: Boolean = true, modifier: Modifier = Modifier) {
    val still = !spinning || reducedMotion()
    val clock = rememberLoopClock(running = !still)
    Box(
        modifier
            .size(size)
            .loopVisibility(clock)
            .drawWithCache {
                val s = this.size.minDimension
                val stroke = Stroke(s * GLYPH_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val glass = hourglass(s)
                onDrawBehind {
                    if (still) {
                        drawPath(glass, color, style = stroke)
                    } else {
                        val inset = stroke.width / 2 + s * ARC_INSET
                        val arc = Size(s - inset * 2, s - inset * 2)
                        drawArc(color.copy(alpha = TRACK_ALPHA), 0f, FULL_TURN, false, Offset(inset, inset), arc, style = stroke)
                        drawArc(color, clock.seconds * SPIN_DEG_PER_SEC, SPIN_SWEEP, false, Offset(inset, inset), arc, style = stroke)
                    }
                }
            },
    )
}

private fun hourglass(s: Float): Path = Path().apply {
    moveTo(s * GLASS_LEFT, s * GLASS_TOP)
    lineTo(s * GLASS_RIGHT, s * GLASS_TOP)
    lineTo(s / 2, s / 2)
    lineTo(s * GLASS_RIGHT, s * GLASS_BOTTOM)
    lineTo(s * GLASS_LEFT, s * GLASS_BOTTOM)
    lineTo(s / 2, s / 2)
    close()
}

/** 0..1 사이를 오가는 값의 소수 부분 */
private fun frac(x: Float): Float = x - kotlin.math.floor(x)

/** 천천히 출발해 천천히 멈추는 곡선 */
private fun easeInOut(x: Float): Float = (1f - cos(x * PI.toFloat())) / 2f

private fun DrawScope.drawDots(t: Float, s: Float, color: Color) {
    val r = s * DOT_RADIUS
    val gap = s * DOT_GAP
    repeat(DOT_COUNT) { i ->
        val phase = frac(t / DOTS_PERIOD - i * DOT_DELAY)
        // 한 주기의 앞쪽 절반에만 튀고 나머지는 바닥에서 쉰다.
        val hop = if (phase < HOP_SHARE) sin(phase / HOP_SHARE * PI.toFloat()) else 0f
        val c = Offset(center.x + (i - 1) * gap, center.y + s * DOT_REST - hop * s * DOT_HOP)
        drawCircle(color.copy(alpha = DOT_MIN_ALPHA + (1f - DOT_MIN_ALPHA) * hop), r * (1f + hop * DOT_GROW), c)
    }
}

private fun DrawScope.drawBars(t: Float, s: Float, color: Color, stroke: Stroke) {
    val step = s * BAR_STEP
    repeat(BAR_COUNT) { i ->
        val wave = (1f + sin((t / BARS_PERIOD - i * BAR_DELAY) * TWO_PI)) / 2f
        val h = s * (BAR_MIN + (BAR_MAX - BAR_MIN) * wave)
        val x = center.x + (i - BAR_COUNT / 2) * step
        drawLine(
            color.copy(alpha = BAR_MIN_ALPHA + (1f - BAR_MIN_ALPHA) * wave),
            Offset(x, center.y - h / 2),
            Offset(x, center.y + h / 2),
            stroke.width,
            StrokeCap.Round,
        )
    }
}

/** 도형 모핑에서 매 프레임 다시 쓰는 경로. 새로 만들지 않고 비운 뒤 다시 긋는다. */
private class MorphPath {
    val path = Path()
}

private fun DrawScope.drawMorph(t: Float, s: Float, color: Color, morph: MorphPath) {
    val beat = t / MORPH_BEAT
    val index = beat.toInt()
    val from = MorphShapes[index % MorphShapes.size]
    val to = MorphShapes[(index + 1) % MorphShapes.size]
    // 한 박자의 뒤쪽에서만 모양이 바뀌고 앞쪽은 그 모양으로 잠시 머문다.
    val f = easeInOut(((frac(beat) - MORPH_HOLD) / (1f - MORPH_HOLD)).coerceIn(0f, 1f))
    val radius = s * MORPH_RADIUS
    val path = morph.path
    path.reset()
    for (k in 0..MORPH_SAMPLES) {
        val a = k * TWO_PI / MORPH_SAMPLES
        val r = radius * (from.radius(a) + (to.radius(a) - from.radius(a)) * f)
        val x = center.x + r * cos(a)
        val y = center.y + r * sin(a)
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    // 계속 돌되 모양이 바뀌는 순간 반 박자 더 빨리 돌아 Expressive 로딩처럼 튀는 느낌을 준다.
    rotate(t * MORPH_SPIN + (index + f) * MORPH_KICK) { drawPath(path, color) }
}

/** 극좌표 반지름이 `1 + amount × cos(lobes × 각도)`인 둥근 도형. 가장 바깥이 1이 되도록 줄여 쓴다. */
private class MorphShape(val lobes: Int, val amount: Float) {
    fun radius(angle: Float): Float = (1f + amount * cos(lobes * angle)) / (1f + amount)
}

private val MorphShapes = listOf(
    MorphShape(0, 0f),
    MorphShape(4, 0.14f),
    MorphShape(5, 0.2f),
    MorphShape(8, 0.07f),
    MorphShape(3, 0.16f),
    MorphShape(6, 0.12f),
    MorphShape(2, 0.12f),
)

private fun DrawScope.drawOrbit(t: Float, s: Float, color: Color) {
    val ring = s * ORBIT_RADIUS
    drawCircle(color.copy(alpha = TRACK_ALPHA), ring, center, style = Stroke(s * ORBIT_TRACK))
    repeat(ORBIT_DOTS) { i ->
        val angle = easeInOut(frac((t - i * ORBIT_LAG) / ORBIT_PERIOD)) * TWO_PI - PI.toFloat() / 2
        val c = Offset(center.x + ring * cos(angle), center.y + ring * sin(angle))
        drawCircle(color.copy(alpha = 1f - i * ORBIT_FADE), s * ORBIT_DOT * (1f - i * ORBIT_SHRINK), c)
    }
}

private fun DrawScope.drawPulse(t: Float, s: Float, color: Color) {
    repeat(PULSE_RINGS) { i ->
        val f = frac(t / PULSE_PERIOD + i.toFloat() / PULSE_RINGS)
        val fade = (1f - f).pow(PULSE_FADE_POWER)
        drawCircle(color.copy(alpha = PULSE_RING_ALPHA * fade), s * PULSE_MAX * f, center, style = Stroke(s * PULSE_STROKE * (1f - f) + 1f))
        drawCircle(color.copy(alpha = PULSE_FILL_ALPHA * fade), s * PULSE_MAX * f, center)
    }
    val breathe = (1f + sin(t * TWO_PI / PULSE_PERIOD)) / 2f
    drawCircle(color, s * (PULSE_CORE + PULSE_CORE_GROW * abs(breathe)), center)
}

private val MIN_SIZE = 16.dp
private val STILL_ICON_MAX = 24.dp
private const val STILL_TEXT_ALPHA = 0.78f
private const val CONTAINED_SCALE = 0.6f
private const val TWO_PI = 6.2831855f
private const val FULL_TURN = 360f
private const val GLYPH_STROKE = 0.12f
private const val ARC_INSET = 0.06f
private const val TRACK_ALPHA = 0.18f
private const val SPIN_DEG_PER_SEC = 400f
private const val SPIN_SWEEP = 100f
private const val GLASS_LEFT = 0.26f
private const val GLASS_RIGHT = 0.74f
private const val GLASS_TOP = 0.16f
private const val GLASS_BOTTOM = 0.84f

private const val DOT_COUNT = 3
private const val DOT_RADIUS = 0.085f
private const val DOT_GAP = 0.27f
private const val DOT_HOP = 0.22f
private const val DOT_REST = 0.08f
private const val DOT_GROW = 0.12f
private const val DOT_MIN_ALPHA = 0.45f
private const val DOT_DELAY = 0.14f
private const val DOTS_PERIOD = 1.1f
private const val HOP_SHARE = 0.5f

private const val BAR_COUNT = 5
private const val BAR_WIDTH = 0.09f
private const val BAR_STEP = 0.17f
private const val BAR_MIN = 0.22f
private const val BAR_MAX = 0.72f
private const val BAR_MIN_ALPHA = 0.55f
private const val BAR_DELAY = 0.12f
private const val BARS_PERIOD = 1.0f

private const val MORPH_BEAT = 0.75f
private const val MORPH_HOLD = 0.35f
private const val MORPH_RADIUS = 0.42f
private const val MORPH_SAMPLES = 72
private const val MORPH_SPIN = 140f
private const val MORPH_KICK = 50f

private const val ORBIT_DOTS = 4
private const val ORBIT_RADIUS = 0.34f
private const val ORBIT_TRACK = 0.04f
private const val ORBIT_DOT = 0.085f
private const val ORBIT_LAG = 0.07f
private const val ORBIT_FADE = 0.2f
private const val ORBIT_SHRINK = 0.16f
private const val ORBIT_PERIOD = 1.3f

private const val PULSE_RINGS = 3
private const val PULSE_PERIOD = 1.8f
private const val PULSE_MAX = 0.48f
private const val PULSE_STROKE = 0.05f
private const val PULSE_RING_ALPHA = 0.7f
private const val PULSE_FILL_ALPHA = 0.12f
private const val PULSE_FADE_POWER = 1.6f
private const val PULSE_CORE = 0.1f
private const val PULSE_CORE_GROW = 0.03f
