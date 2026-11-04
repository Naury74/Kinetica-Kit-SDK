package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.collectLatest

/**
 * [KWavyProgressBar]와 [KWavyProgressRing]의 모양. 기본값은 Material 3 Expressive의 물결 진행 표시와 비슷하다.
 *
 * @param thickness 선 두께. 0 이하이면 [IllegalArgumentException]을 던진다.
 * @param amplitude 물결 높이(가운데에서 꼭대기까지). 0이면 늘 직선이다. 음수면 [IllegalArgumentException]을 던진다.
 * @param wavelength 물결 한 주기의 길이. 0 이하이면 [IllegalArgumentException]을 던진다.
 * @param gap 진행된 부분과 남은 트랙 사이 틈. 음수면 [IllegalArgumentException]을 던진다.
 */
@Stable
public class KWavyProgressLook(
    public val thickness: Dp = 4.dp,
    public val amplitude: Dp = 3.dp,
    public val wavelength: Dp = 40.dp,
    public val gap: Dp = 4.dp,
) {
    init {
        require(thickness.value > 0f) { "thickness는 0보다 커야 합니다." }
        require(amplitude.value >= 0f) { "amplitude는 0 이상이어야 합니다." }
        require(wavelength.value > 0f) { "wavelength는 0보다 커야 합니다." }
        require(gap.value >= 0f) { "gap은 0 이상이어야 합니다." }
    }

    override fun equals(other: Any?): Boolean = other is KWavyProgressLook &&
        thickness == other.thickness &&
        amplitude == other.amplitude &&
        wavelength == other.wavelength &&
        gap == other.gap

    override fun hashCode(): Int =
        ((thickness.hashCode() * HASH + amplitude.hashCode()) * HASH + wavelength.hashCode()) * HASH + gap.hashCode()

    public companion object {
        /** 막대 기본값. 두께 4dp, 물결 높이 3dp, 주기 40dp, 틈 4dp */
        public val Bar: KWavyProgressLook = KWavyProgressLook()

        /** 링 기본값. 두께 4dp, 물결 높이 1.6dp, 주기 15dp, 틈 4dp */
        public val Ring: KWavyProgressLook = KWavyProgressLook(amplitude = 1.6.dp, wavelength = 15.dp)
    }
}

/**
 * 물결 진행 막대. 진행된 부분은 사인파로 출렁이고, 남은 트랙은 직선이며, 둘 사이에 틈이 있고 트랙 끝에 끝점(stop indicator)이 있다.
 * 진행 값이 바뀌는 동안 물결이 흐르다가 값이 1.5초 넘게 그대로이면 물결이 잦아들어 평평해지고, 다시 바뀌면 살아난다.
 * 0과 1 가까이에서도 물결이 낮아진다. '애니메이션 삭제' 설정이면 늘 직선이다. RTL에서는 오른쪽부터 찬다.
 *
 * [progress]는 그리기 단계에서만 읽으므로 값이 매 프레임 바뀌어도 다시 구성하지 않는다. 값을 따라가는 애니메이션은 없으니
 * 부드럽게 채우려면 호출한 쪽이 `animateFloatAsState`로 넘긴다. TalkBack은 진행 비율을 읽는다.
 *
 * ```kotlin
 * KWavyProgressBar({ downloaded / total.toFloat() }, Modifier.fillMaxWidth())
 * ```
 *
 * @param progress 0..1 사이의 진행 비율을 돌려주는 람다. 범위를 벗어나면 가까운 끝 값으로, NaN은 0으로 본다.
 * @param color 진행된 부분과 끝점 색. 기본값은 테마 강조색이다.
 * @param trackColor 남은 트랙 색. [Color.Unspecified]면 [color]와 [surface]에 맞춘 옅은 색이다.
 * @param look 두께, 물결 높이, 주기, 틈. 막대 높이는 두께에 물결 높이의 두 배를 더한 값이다.
 * @param stopIndicator 트랙 끝에 끝점을 그릴지 여부
 */
@Composable
public fun KWavyProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = KineticaTheme.colors.accent,
    trackColor: Color = Color.Unspecified,
    look: KWavyProgressLook = KWavyProgressLook.Bar,
    stopIndicator: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val current = rememberUpdatedState(progress)
    val clock = rememberLoopClock(running = false)
    val motion = rememberWavyMotion({ current.value() }, running = clock.onScreen && !reducedMotion())
    val ink = WavyInk(color, if (trackColor.isSpecified) trackColor else progressTrackColor(color, surface))
    val path = remember { Path() }
    val active = remember { WavySegments(1) }
    Spacer(
        modifier
            .fillMaxWidth()
            .height(look.thickness + look.amplitude * 2)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(wavyFraction(current.value()), 0f..1f) }
            .loopVisibility(clock)
            .drawWithCache {
                val px = WavyPx(look.thickness.toPx(), look.amplitude.toPx(), look.wavelength.toPx(), look.gap.toPx(), path)
                onDrawBehind {
                    val level = wavyFraction(current.value())
                    active.bounds.setSegment(0, 0f, level)
                    active.count = 1
                    val wave = WavyWave(px.amplitude * motion.amplitude * edgeRamp(level), motion.phase)
                    active.stop = stopIndicator && level < 1f
                    mirrorWavy { drawWavyBar(px, ink, wave, active) }
                }
            },
    )
}

/**
 * 진행 정도를 모를 때의 물결 진행 막대. 물결치는 두 토막이 트랙을 따라 계속 흐른다. 화면 밖으로 스크롤되면 멈추고,
 * '애니메이션 삭제' 설정이면 가운데에 멈춘 직선 토막을 그린다. TalkBack은 진행 정도를 모르는 진행 표시로 읽는다.
 */
@Composable
public fun KWavyProgressBar(
    modifier: Modifier = Modifier,
    color: Color = KineticaTheme.colors.accent,
    trackColor: Color = Color.Unspecified,
    look: KWavyProgressLook = KWavyProgressLook.Bar,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val still = reducedMotion()
    val clock = rememberLoopClock(running = !still)
    val ink = WavyInk(color, if (trackColor.isSpecified) trackColor else progressTrackColor(color, surface))
    val path = remember { Path() }
    val active = remember { WavySegments(FLOW_SEGMENTS) }
    Spacer(
        modifier
            .fillMaxWidth()
            .height(look.thickness + look.amplitude * 2)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
            .loopVisibility(clock)
            .drawWithCache {
                val px = WavyPx(look.thickness.toPx(), look.amplitude.toPx(), look.wavelength.toPx(), look.gap.toPx(), path)
                onDrawBehind {
                    active.count = if (still) stillSegment(active.bounds) else flowingSegments(clock.seconds / BAR_PERIOD_S, active.bounds)
                    val wave = WavyWave(if (still) 0f else px.amplitude, clock.seconds * WAVE_CYCLES_PER_S)
                    mirrorWavy { drawWavyBar(px, ink, wave, active) }
                }
            },
    )
}

/**
 * 물결 진행 링. 진행된 원호는 바깥쪽으로 출렁이고, 남은 트랙은 매끈한 원호이며 둘 사이에 틈이 있다. 12시에서 시작해 시계 방향으로 찬다.
 * 물결이 잦아드는 규칙, 그리기 단계에서 읽는 [progress], TalkBack은 [KWavyProgressBar]와 같다. 크기를 정하지 않으면 지름 48dp이다.
 *
 * @param progress 0..1 사이의 진행 비율을 돌려주는 람다. 범위를 벗어나면 가까운 끝 값으로, NaN은 0으로 본다.
 * @param look 두께, 물결 높이, 주기, 틈. 원 둘레에 물결이 정수 개로 들어가도록 주기를 조금 맞춘다.
 */
@Composable
public fun KWavyProgressRing(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = KineticaTheme.colors.accent,
    trackColor: Color = Color.Unspecified,
    look: KWavyProgressLook = KWavyProgressLook.Ring,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val current = rememberUpdatedState(progress)
    val clock = rememberLoopClock(running = false)
    val motion = rememberWavyMotion({ current.value() }, running = clock.onScreen && !reducedMotion())
    val ink = WavyInk(color, if (trackColor.isSpecified) trackColor else progressTrackColor(color, surface))
    val path = remember { Path() }
    Spacer(
        modifier
            .size(RING_SIZE)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(wavyFraction(current.value()), 0f..1f) }
            .loopVisibility(clock)
            .drawWithCache {
                val px = WavyPx(look.thickness.toPx(), look.amplitude.toPx(), look.wavelength.toPx(), look.gap.toPx(), path)
                onDrawBehind {
                    val level = wavyFraction(current.value())
                    val wave = WavyWave(px.amplitude * motion.amplitude * edgeRamp(level), motion.phase)
                    drawWavyRing(px, ink, wave, RingSpan(-QUARTER_TURN, FULL_TURN * level))
                }
            },
    )
}

/**
 * 진행 정도를 모를 때의 물결 진행 링. 물결치는 원호가 늘었다 줄며 돈다. 화면 밖이면 멈추고, '애니메이션 삭제' 설정이면 멈춘 직선 원호를 그린다.
 */
@Composable
public fun KWavyProgressRing(
    modifier: Modifier = Modifier,
    color: Color = KineticaTheme.colors.accent,
    trackColor: Color = Color.Unspecified,
    look: KWavyProgressLook = KWavyProgressLook.Ring,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val still = reducedMotion()
    val clock = rememberLoopClock(running = !still)
    val ink = WavyInk(color, if (trackColor.isSpecified) trackColor else progressTrackColor(color, surface))
    val path = remember { Path() }
    Spacer(
        modifier
            .size(RING_SIZE)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
            .loopVisibility(clock)
            .drawWithCache {
                val px = WavyPx(look.thickness.toPx(), look.amplitude.toPx(), look.wavelength.toPx(), look.gap.toPx(), path)
                onDrawBehind {
                    val t = clock.seconds
                    val span = if (still) RingSpan(-QUARTER_TURN, STILL_SWEEP) else spinningSpan(t / RING_PERIOD_S)
                    drawWavyRing(px, ink, WavyWave(if (still) 0f else px.amplitude, t * WAVE_CYCLES_PER_S), span)
                }
            },
    )
}

/**
 * 물결 높이(0..1)와 흐르는 위치. 진행 값이 바뀌면 [STALL_S] 동안 높이를 1로 올리고, 그 뒤 값이 그대로면 0으로 내린다.
 * 높이가 0이고 값이 그대로인 동안에는 프레임을 요청하지 않는다. 값은 그리기 단계에서 읽는다.
 */
@Stable
internal class WavyMotion {
    var amplitude by mutableFloatStateOf(0f)
    var phase by mutableFloatStateOf(0f)

    /** [dt]초만큼 [target] 높이로 다가가고 물결을 흘린다. 다 닿았으면 true. */
    fun step(dt: Float, target: Float): Boolean {
        val next = amplitude + (target - amplitude) * (1f - exp(-dt / AMPLITUDE_TAU_S))
        amplitude = if (kotlin.math.abs(next - target) < SETTLED) target else next
        // 낮아질수록 천천히 흘러 멈추는 느낌이 나게 한다.
        val moved = phase + dt * WAVE_CYCLES_PER_S * amplitude
        phase = moved - floor(moved)
        return amplitude == target
    }

    /** 진행 값이 바뀐 직후부터 물결을 살렸다가, 멈추면 평평해질 때까지 프레임을 돈다. */
    suspend fun ride(level: Float) {
        val alive = level > 0f && level < 1f
        var last = -1L
        var elapsed = 0f
        while (true) {
            val settled = withFrameNanos { now ->
                val dt = if (last < 0) 0f else (now - last) / NANOS_PER_SECOND
                last = now
                elapsed += dt
                val target = if (alive && elapsed < STALL_S) 1f else 0f
                step(dt, target) && target == 0f
            }
            if (settled) return
        }
    }
}

/** [progress]를 지켜보며 물결을 움직이는 [WavyMotion]. [running]이 false면 곧바로 평평하게 둔다. */
@Composable
internal fun rememberWavyMotion(progress: () -> Float, running: Boolean): WavyMotion {
    val motion = remember { WavyMotion() }
    val current = rememberUpdatedState(progress)
    LaunchedEffect(motion, running) {
        if (!running) {
            motion.amplitude = 0f
            return@LaunchedEffect
        }
        snapshotFlow { wavyFraction(current.value()) }.collectLatest { motion.ride(it) }
    }
    return motion
}

/** 진행 비율을 0..1로 맞춘다. NaN은 0으로 본다. */
internal fun wavyFraction(value: Float): Float = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)

/** 0과 1 가까이에서 물결을 낮춘다. 시작점과 끝점에서 물결이 튀어나와 보이지 않게 한다. */
private fun edgeRamp(level: Float): Float = minOf(1f, level / EDGE_RAMP, (1f - level) / EDGE_RAMP).coerceAtLeast(0f)

private class WavyInk(val active: Color, val track: Color)

/** 크기 값(px)과 프레임마다 비워 다시 쓰는 경로 */
private class WavyPx(val stroke: Float, val amplitude: Float, val wavelength: Float, val gap: Float, val path: Path)

/** 물결 토막들. [bounds]에 (시작, 끝 비율) 쌍이 왼쪽부터 들어 있고 앞 [count]쌍만 쓴다. 프레임마다 값만 바꿔 다시 쓴다. */
private class WavySegments(slots: Int) {
    val bounds = FloatArray(slots * 2)
    var count = 0
    var stop = false
}

/** [amplitude]는 이번 프레임의 물결 높이(px), [phase]는 흐른 주기 수다. */
private class WavyWave(val amplitude: Float, val phase: Float)

/** 링에서 그릴 원호. 각도는 3시 방향에서 시계 방향으로 잰 도 단위다. */
private class RingSpan(val start: Float, val sweep: Float)

private inline fun DrawScope.mirrorWavy(block: DrawScope.() -> Unit) {
    if (layoutDirection == LayoutDirection.Rtl) scale(-1f, 1f) { block() } else block()
}

/** 멈춘 토막 하나. 0에서 시작하지 않게 가운데에 두어 진행 값으로 읽히지 않게 한다. */
private fun stillSegment(out: FloatArray): Int {
    out.setSegment(0, STILL_FROM, STILL_TO)
    return 1
}

/** 앞 토막이 지나간 뒤 뒤 토막이 따라오는 흐름. [cycle]의 소수 부분이 한 바퀴 안 위치다. */
private fun flowingSegments(cycle: Float, out: FloatArray): Int {
    val f = cycle - floor(cycle)
    // 뒤 토막이 늘 왼쪽에 있으므로 먼저 둔다. 그리는 쪽은 토막이 왼쪽부터 놓여 있다고 본다.
    out.setSegment(0, easeWavy((f - SECOND_TAIL_DELAY) / SECOND_SPAN), easeWavy((f - SECOND_HEAD_DELAY) / SECOND_SPAN))
    out.setSegment(1, easeWavy((f - FIRST_TAIL_DELAY) / FIRST_SPAN), easeWavy(f / FIRST_SPAN))
    return 2
}

private fun FloatArray.setSegment(index: Int, from: Float, to: Float) {
    this[index * 2] = from
    this[index * 2 + 1] = to
}

private fun easeWavy(x: Float): Float = (1f - cos(x.coerceIn(0f, 1f) * PI.toFloat())) / 2f

/** 늘었다 줄며 도는 원호 */
private fun spinningSpan(cycle: Float): RingSpan {
    val f = cycle - floor(cycle)
    val sweep = SPIN_MIN_SWEEP + SPIN_EXTRA_SWEEP * sin(f * PI.toFloat())
    return RingSpan(f * FULL_TURN * 2 - QUARTER_TURN, sweep)
}

/**
 * 막대를 그린다. [active]의 앞 [count]쌍(시작, 끝 비율)이 물결 토막이고 나머지 자리는 틈을 두고 직선 트랙을 그린다.
 * 둥근 끝이 선 두께의 절반만큼 튀어나오므로 비율 0과 1은 그만큼 안쪽 자리다.
 */
private fun DrawScope.drawWavyBar(px: WavyPx, ink: WavyInk, wave: WavyWave, segments: WavySegments) {
    val stop = segments.stop
    val active = segments.bounds
    val count = segments.count
    val half = px.stroke / 2
    val length = (size.width - px.stroke).coerceAtLeast(0f)
    val cy = size.height / 2
    fun x(f: Float) = half + f * length
    val room = px.gap + px.stroke
    var trackFrom = x(0f)
    for (i in 0 until count) {
        val a = x(active[i * 2])
        val b = x(active[i * 2 + 1])
        if (b - a <= 0f) continue
        if (a - room > trackFrom) drawTrackLine(ink.track, trackFrom, a - room, cy, px.stroke)
        drawWaveLine(ink.active, a, b, px, wave)
        trackFrom = maxOf(trackFrom, b + room)
    }
    val end = x(1f)
    if (end > trackFrom) drawTrackLine(ink.track, trackFrom, end, cy, px.stroke)
    if (stop && end - trackFrom > px.stroke) drawCircle(ink.active, half * STOP_SCALE, Offset(end, cy))
}

private fun DrawScope.drawTrackLine(color: Color, from: Float, to: Float, cy: Float, stroke: Float) {
    drawLine(color, Offset(from, cy), Offset(to, cy), stroke, StrokeCap.Round)
}

private fun DrawScope.drawWaveLine(color: Color, from: Float, to: Float, px: WavyPx, wave: WavyWave) {
    val path = px.path
    val cy = size.height / 2
    if (wave.amplitude <= FLAT_PX) {
        drawLine(color, Offset(from, cy), Offset(to, cy), px.stroke, StrokeCap.Round)
        return
    }
    val step = px.wavelength / WAVE_SAMPLES_PER_CYCLE
    path.reset()
    var x = from
    while (true) {
        // 물결은 막대의 절대 위치에 붙어 있어 진행이 늘어도 출렁임이 따라 늘어나지 않고 흐름만 보인다.
        val y = cy + wave.amplitude * sin((x / px.wavelength - wave.phase) * TWO_PI)
        if (x == from) path.moveTo(x, y) else path.lineTo(x, y)
        if (x >= to) break
        x = minOf(x + step, to)
    }
    drawPath(path, color, style = Stroke(px.stroke, cap = StrokeCap.Round))
}

/** 링을 그린다. [span] 원호는 바깥쪽으로 출렁이고, 나머지는 틈을 두고 매끈한 트랙이다. */
private fun DrawScope.drawWavyRing(px: WavyPx, ink: WavyInk, wave: WavyWave, span: RingSpan) {
    val path = px.path
    val radius = (size.minDimension - px.stroke) / 2 - px.amplitude
    if (radius <= 0f) return
    val gapDeg = (px.gap + px.stroke) / radius * DEG_PER_RAD
    val sweep = span.sweep.coerceIn(0f, FULL_TURN)
    val trackSweep = FULL_TURN - sweep - gapDeg * 2
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    val trackStroke = Stroke(px.stroke, cap = StrokeCap.Round)
    when {
        sweep <= 0f -> drawArc(ink.track, 0f, FULL_TURN, false, topLeft, arcSize, style = trackStroke)
        trackSweep > 0f -> drawArc(ink.track, span.start + sweep + gapDeg, trackSweep, false, topLeft, arcSize, style = trackStroke)
    }
    if (sweep <= 0f) return
    if (wave.amplitude <= FLAT_PX) {
        drawArc(ink.active, span.start, sweep, false, topLeft, arcSize, style = trackStroke)
        return
    }
    val waves = (TWO_PI * radius / px.wavelength).roundToInt().coerceAtLeast(MIN_RING_WAVES)
    val samples = (sweep / RING_SAMPLE_DEG).roundToInt().coerceAtLeast(2)
    path.reset()
    for (i in 0..samples) {
        val deg = span.start + sweep * i / samples
        val rad = deg / DEG_PER_RAD
        // 물결은 링에 붙은 각도(12시 기준)로 정해 원호가 돌아도 같은 자리에서 출렁인다.
        val r = radius + wave.amplitude * sin(waves * (rad + PI.toFloat() / 2) - wave.phase * TWO_PI)
        val x = center.x + r * cos(rad)
        val y = center.y + r * sin(rad)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, ink.active, style = trackStroke)
}

private val RING_SIZE = 48.dp
private const val HASH = 31
private const val STALL_S = 1.5f
private const val AMPLITUDE_TAU_S = 0.25f
private const val SETTLED = 0.01f
private const val WAVE_CYCLES_PER_S = 1.1f
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val EDGE_RAMP = 0.08f
private const val STOP_SCALE = 1f
private const val FLAT_PX = 0.05f
private const val WAVE_SAMPLES_PER_CYCLE = 20f
private const val TWO_PI = 6.2831855f
private const val DEG_PER_RAD = 57.29578f
private const val QUARTER_TURN = 90f
private const val FULL_TURN = 360f
private const val RING_SAMPLE_DEG = 3f
private const val MIN_RING_WAVES = 3
private const val BAR_PERIOD_S = 1.8f
private const val RING_PERIOD_S = 1.4f
private const val FIRST_SPAN = 0.55f
private const val FIRST_TAIL_DELAY = 0.18f
private const val SECOND_SPAN = 0.45f
private const val SECOND_HEAD_DELAY = 0.45f
private const val SECOND_TAIL_DELAY = 0.6f
private const val FLOW_SEGMENTS = 2
private const val STILL_FROM = 0.3f
private const val STILL_TO = 0.7f
private const val STILL_SWEEP = 100f
private const val SPIN_MIN_SWEEP = 30f
private const val SPIN_EXTRA_SWEEP = 220f
