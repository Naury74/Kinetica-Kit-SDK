package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 가로 진행 막대. 진행 값이 바뀌면 용수철처럼 따라가며, RTL에서는 오른쪽부터 찬다.
 *
 * [progress]가 `null`이면 진행 정도를 모르는 상태로 보고, 빛 한 줄기가 트랙을 따라 계속 흐른다.
 * [wave]를 켜면 채워진 쪽의 앞 끝이 물결 모양으로 출렁이고, 값이 크게 바뀔수록 더 크게 출렁인다.
 * '애니메이션 삭제' 접근성 설정이 켜져 있으면 흐르는 빛과 물결이 멈춘다. TalkBack은 진행 비율을 읽는다.
 *
 * ```kotlin
 * KProgressBar(downloaded / total.toFloat(), Modifier.fillMaxWidth(), wave = true)
 * ```
 *
 * @param progress 0..1 사이의 진행 비율. 범위를 벗어나면 가까운 끝 값으로 그리고, NaN은 0으로 본다. `null`이면 진행 정도를 모르는 상태다.
 * @param color 채워지는 부분의 색. 기본값은 테마 강조색([KineticaColors.accent])이다.
 * @param thickness 막대 두께. 물결을 켜면 10dp 이상일 때 잘 보인다.
 * @param wave 앞 끝을 물결 모양으로 출렁이게 할지 여부
 */
@Composable
public fun KProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = KineticaTheme.colors.accent,
    thickness: Dp = 8.dp,
    wave: Boolean = false,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val shown = followProgress(progress)
    val still = reducedMotion()
    val flow = loopingFraction(progress == null && !still, FLOW_PERIOD_MS)
    val phase = loopingFraction(progress != null && wave && !still, WAVE_PERIOD_MS)
    val track = surface.contentColor.copy(alpha = TRACK_ALPHA)
    Box(
        modifier
            .fillMaxWidth()
            .height(thickness)
            .progressSemantics(progress)
            .clip(CircleShape)
            .drawWithCache {
                val glow = Brush.horizontalGradient(listOf(Color.Transparent, color, Color.Transparent), 0f, size.width * FLOW_LENGTH)
                val edge = Path()
                onDrawBehind {
                    drawRect(track)
                    mirrorForRtl {
                        if (progress == null) {
                            drawFlow(glow, if (still) STILL_FLOW else flow.value)
                        } else {
                            val wobble = if (wave) WaveLook(phase.value, abs(shown.velocity)) else null
                            drawFill(shown.value.coerceIn(0f, 1f), color, wobble, edge)
                        }
                    }
                }
            },
    )
}

/**
 * 원형 진행 링. 가운데 [content] 슬롯에 퍼센트나 아이콘을 넣을 수 있다. 진행 값이 바뀌면 용수철처럼 따라간다.
 *
 * [progress]가 `null`이면 원호가 늘었다 줄며 계속 돈다. [wave]를 켜면 링 안쪽에 물이 차오르듯 물결치는 수면을 그린다.
 * '애니메이션 삭제' 접근성 설정이 켜져 있으면 회전과 물결이 멈춘다. TalkBack은 진행 비율을 읽는다.
 * 크기를 정하지 않으면 지름 56dp이다.
 *
 * ```kotlin
 * KProgressRing(0.72f, Modifier.size(96.dp), wave = true) { BasicText("72%") }
 * ```
 *
 * @param progress 0..1 사이의 진행 비율. 범위를 벗어나면 가까운 끝 값으로 그리고, NaN은 0으로 본다. `null`이면 진행 정도를 모르는 상태다.
 * @param color 원호 색. 기본값은 테마 강조색([KineticaColors.accent])이다.
 * @param thickness 원호 두께
 * @param wave 링 안쪽에 물결치는 수면을 그릴지 여부. [progress]가 `null`이면 그리지 않는다.
 */
@Composable
public fun KProgressRing(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = KineticaTheme.colors.accent,
    thickness: Dp = 6.dp,
    wave: Boolean = false,
    surface: SurfaceStyle = KineticaTheme.surface,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shown = followProgress(progress)
    val still = reducedMotion()
    val spin = loopingFraction(progress == null && !still, SPIN_PERIOD_MS)
    val phase = loopingFraction(progress != null && wave && !still, WAVE_PERIOD_MS)
    val track = surface.contentColor.copy(alpha = TRACK_ALPHA)
    Box(
        modifier
            .defaultMinSize(RING_SIZE, RING_SIZE)
            .aspectRatio(1f)
            .progressSemantics(progress)
            .drawWithCache {
                val stroke = thickness.toPx()
                val water = Path()
                val inner = Path().apply {
                    val inset = stroke * INNER_GAP
                    addOval(Rect(Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2)))
                }
                ringDraw(stroke) { arc ->
                    drawArc(track, 0f, FULL_TURN, false, arc.topLeft, arc.size, style = arc.stroke)
                    val level = shown.value.coerceIn(0f, 1f)
                    when {
                        progress == null -> drawSpinner(color, if (still) STILL_SPIN else spin.value, arc)

                        else -> {
                            if (wave) {
                                clipPath(inner) { drawWater(color, level, WaveLook(phase.value, abs(shown.velocity)), water) }
                            }
                            if (level >
                                0f
                            ) {
                                drawArc(color, -QUARTER_TURN, FULL_TURN * level, false, arc.topLeft, arc.size, style = arc.stroke)
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor) { content() }
    }
}

/** 진행 값을 용수철로 따라가는 값. `null`이 되면 마지막 값에 머문다. */
@Composable
private fun followProgress(progress: Float?): Animatable<Float, *> {
    val shown = remember { Animatable(progressFraction(progress ?: 0f)) }
    LaunchedEffect(progress) {
        if (progress != null) {
            shown.animateTo(progressFraction(progress), spring(dampingRatio = FOLLOW_DAMPING, stiffness = Spring.StiffnessLow))
        }
    }
    return shown
}

/** [running]일 때만 0..1을 [periodMs]마다 반복하는 값. 멈춰 있으면 프레임을 요청하지 않는다. */
@Composable
private fun loopingFraction(running: Boolean, periodMs: Int): State<Float> {
    if (!running) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "progress-loop").animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
        label = "fraction",
    )
}

private fun Modifier.progressSemantics(progress: Float?): Modifier = semantics {
    progressBarRangeInfo = if (progress == null) {
        ProgressBarRangeInfo.Indeterminate
    } else {
        ProgressBarRangeInfo(progressFraction(progress), 0f..1f)
    }
}

/** 진행 비율을 0..1로 맞춘다. NaN은 0으로 본다. */
private fun progressFraction(progress: Float): Float = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)

/** 물결 모양. [phase]는 0..1 반복 위치, [speed]는 진행 값이 변하는 속도(1초당 비율)로 클수록 크게 출렁인다. */
private class WaveLook(val phase: Float, val speed: Float)

private inline fun DrawScope.mirrorForRtl(block: DrawScope.() -> Unit) {
    if (layoutDirection == LayoutDirection.Rtl) scale(-1f, 1f) { block() } else block()
}

/** 흐르는 빛. [fraction]이 0에서 1로 가는 동안 트랙 바깥 왼쪽에서 오른쪽 바깥까지 지나간다. */
private fun DrawScope.drawFlow(glow: Brush, fraction: Float) {
    val length = size.width * FLOW_LENGTH
    translate(left = -length + (size.width + length) * fraction) {
        drawRect(glow, size = Size(length, size.height))
    }
}

/** 채워진 부분. 물결이 있으면 앞 끝을 사인 곡선으로 그린다. */
private fun DrawScope.drawFill(level: Float, color: Color, wobble: WaveLook?, edge: Path) {
    if (level <= 0f) return
    val end = size.width * level
    if (wobble == null || level >= 1f) {
        drawRoundRect(color, size = Size(end, size.height), cornerRadius = CornerRadius(size.height / 2))
        return
    }
    val amplitude = size.height * (WAVE_BASE + (wobble.speed * WAVE_SPEED_GAIN).coerceAtMost(WAVE_EXTRA))
    edge.reset()
    edge.moveTo(0f, 0f)
    for (i in 0..WAVE_SAMPLES) {
        val y = size.height * i / WAVE_SAMPLES
        val angle = (wobble.phase + y / size.height) * TWO_PI
        edge.lineTo((end + amplitude * sin(angle)).coerceAtLeast(0f), y)
    }
    edge.lineTo(0f, size.height)
    edge.close()
    drawPath(edge, color)
    // 위쪽에 옅은 반사를 얹어 액체처럼 보이게 한다.
    drawRect(Color.White.copy(alpha = GLOSS_ALPHA), size = Size(end, size.height * GLOSS_HEIGHT))
}

/** 링을 그리는 데 쓰는 원호 위치와 선 모양 */
private class RingArc(val topLeft: Offset, val size: Size, val stroke: Stroke)

private fun CacheDrawScope.ringDraw(strokePx: Float, block: DrawScope.(RingArc) -> Unit): DrawResult {
    val arc =
        RingArc(
            Offset(strokePx / 2, strokePx / 2),
            Size(size.width - strokePx, size.height - strokePx),
            Stroke(strokePx, cap = StrokeCap.Round),
        )
    return onDrawBehind { block(arc) }
}

/** 진행 정도를 모를 때 도는 원호. 한 바퀴 동안 원호가 늘었다가 줄어든다. */
private fun DrawScope.drawSpinner(color: Color, fraction: Float, arc: RingArc) {
    val sweep = SPIN_MIN_SWEEP + SPIN_EXTRA_SWEEP * sin(fraction * PI.toFloat())
    val start = fraction * FULL_TURN * 2 - QUARTER_TURN
    drawArc(color, start, sweep, false, arc.topLeft, arc.size, style = arc.stroke)
}

/** 링 안쪽에 [level]만큼 찬 물. 수면은 사인 곡선이고 [WaveLook.phase]에 따라 옆으로 흐른다. */
private fun DrawScope.drawWater(color: Color, level: Float, wobble: WaveLook, water: Path) {
    if (level <= 0f) return
    val surface = size.height * (1f - level)
    val amplitude = size.height * (RING_WAVE_BASE + (wobble.speed * WAVE_SPEED_GAIN * RING_WAVE_BASE).coerceAtMost(RING_WAVE_EXTRA))
    water.reset()
    water.moveTo(0f, size.height)
    for (i in 0..WAVE_SAMPLES) {
        val x = size.width * i / WAVE_SAMPLES
        val angle = (x / size.width - wobble.phase) * TWO_PI
        water.lineTo(x, surface + amplitude * sin(angle))
    }
    water.lineTo(size.width, size.height)
    water.close()
    drawPath(water, color.copy(alpha = WATER_ALPHA))
}

private val RING_SIZE = 56.dp
private const val TRACK_ALPHA = 0.12f
private const val FOLLOW_DAMPING = 0.8f
private const val FLOW_PERIOD_MS = 1_400
private const val FLOW_LENGTH = 0.4f
private const val STILL_FLOW = 0.7f
private const val WAVE_PERIOD_MS = 1_600
private const val WAVE_SAMPLES = 16
private const val WAVE_BASE = 0.18f
private const val WAVE_EXTRA = 0.6f
private const val WAVE_SPEED_GAIN = 0.8f
private const val GLOSS_ALPHA = 0.22f
private const val GLOSS_HEIGHT = 0.4f
private const val SPIN_PERIOD_MS = 1_300
private const val SPIN_MIN_SWEEP = 30f
private const val SPIN_EXTRA_SWEEP = 220f
private const val STILL_SPIN = 0.5f
private const val INNER_GAP = 1.6f
private const val RING_WAVE_BASE = 0.03f
private const val RING_WAVE_EXTRA = 0.08f
private const val WATER_ALPHA = 0.28f
private const val QUARTER_TURN = 90f
private const val FULL_TURN = 360f
private const val TWO_PI = 6.2831855f
