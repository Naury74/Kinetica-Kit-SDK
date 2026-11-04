package com.naury.kinetica.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * 녹음 중인 소리 크기를 막대로 흘려 보여 준다. 새 막대가 끝 쪽에서 들어와 시작 쪽으로 밀려 나간다. RTL에서는 방향이 바뀐다.
 *
 * [level]은 그리기 단계에서만 읽으므로 소리 크기가 바뀌어도 다시 구성하지 않는다. 막대 값은 크기가 정해진 링버퍼(최대 256개)에 담아
 * 다시 쓰고, 프레임마다 새로 만들지 않는다. [active]가 false이거나 화면 밖이면 흐르지 않고 마지막 모양을 그대로 둔다.
 * '애니메이션 삭제' 설정에서는 흐르지 않고 0.25초마다 모든 막대를 지금 크기에 맞춘다. 장식이므로 TalkBack은 읽지 않는다.
 *
 * ```kotlin
 * KLiveWaveform(level = { recorder.amplitude }, Modifier.weight(1f).height(32.dp), active = recording)
 * ```
 *
 * @param level 지금 소리 크기. 0..1이고 범위를 넘으면 잘라 쓴다.
 * @param sampleIntervalMs 막대 하나가 맡는 시간(ms). 16 미만이면 16으로 본다.
 * @param color 막대 색. 지정하지 않으면 지금 글자 색이다.
 */
@Composable
public fun KLiveWaveform(
    level: () -> Float,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    color: Color = Color.Unspecified,
    barWidth: Dp = 3.dp,
    barGap: Dp = 2.dp,
    sampleIntervalMs: Long = 60L,
) {
    val still = reducedMotion()
    val clock = rememberLoopClock(running = active && !still)
    val ring = remember { LevelRing() }
    val latestLevel by rememberUpdatedState(level)
    val ink = if (color.isSpecified) color else LocalKineticaContentColor.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var stillTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(active, still) {
        while (active && still) {
            stillTick++
            delay(STILL_REFRESH_MS)
        }
    }
    val interval = sampleIntervalMs.coerceAtLeast(MIN_INTERVAL_MS) / MILLIS
    Box(
        modifier
            .heightIn(min = MIN_HEIGHT)
            .loopVisibility(clock)
            .clearAndSetSemantics { }
            .drawBehind {
                val bars = BarGeometry(barWidth.toPx(), barGap.toPx(), size)
                if (still) {
                    // stillTick을 읽어 두어야 0.25초마다 다시 그린다.
                    if (active && stillTick >= 0) ring.fill(latestLevel().coerceIn(0f, 1f))
                    flip(rtl) { drawRing(ring, bars, 0f, ink) }
                } else {
                    val position = clock.seconds / interval
                    if (active) ring.advanceTo(floor(position).toLong()) { latestLevel().coerceIn(0f, 1f) }
                    val shift = if (active) (position - floor(position)) * bars.pitch else 0f
                    flip(rtl) { drawRing(ring, bars, shift, ink) }
                }
            },
    )
}

/**
 * 녹음 파일의 모양과 재생 위치를 보여 주는 파형. 들은 부분은 강조색, 남은 부분은 옅은 색이고, 재생 위치가 막대 사이를 부드럽게 지나간다.
 * [onSeek]가 있으면 누르거나 옆으로 끌어 위치를 옮긴다. 끄는 동안은 손가락 위치를 그리고, 손을 떼면 [onSeek]를 한 번 부른다.
 * 세로로 먼저 움직이면 부모 스크롤에 넘긴다. 키보드 방향키는 5%씩 옮긴다. RTL에서는 오른쪽에서 왼쪽으로 재생한다.
 *
 * [samples]는 막대 수에 맞춰 구간마다 가장 큰 값으로 줄이고, 가장 큰 값을 1로 맞춰 그린다. 다시 계산은 [samples]나 폭이 바뀔 때만 한다.
 * [progress]는 그리기 단계에서 읽는다. TalkBack에는 값 범위(0..1)와 위치 옮기기 동작을 내보내고, 1% 단위로만 다시 구성한다.
 *
 * @param samples 소리 크기 표본. 0 이상이면 단위는 상관없다. 비어 있으면 낮은 막대만 그린다.
 * @param progress 재생 위치. 0..1이고 범위를 넘으면 잘라 쓴다.
 * @param onSeek 옮길 위치(0..1)를 받는다. null이면 누를 수 없고 TalkBack은 진행 표시로 읽는다.
 * @param label TalkBack이 읽을 이름. null이면 '재생 위치'다.
 * @param durationMillis 전체 길이(ms). 있으면 TalkBack이 '지금 위치 / 전체'를 시간으로 읽는다.
 * @param playedColor 들은 부분 색. 지정하지 않으면 테마 강조색이다.
 * @param idleColor 남은 부분 색. 지정하지 않으면 글자 색을 옅게 쓴다.
 */
@Composable
public fun KWaveform(
    samples: FloatArray,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    onSeek: ((Float) -> Unit)? = null,
    label: String? = null,
    durationMillis: Long? = null,
    playedColor: Color = Color.Unspecified,
    idleColor: Color = Color.Unspecified,
    barWidth: Dp = 3.dp,
    barGap: Dp = 2.dp,
) {
    val played = if (playedColor.isSpecified) playedColor else KineticaTheme.colors.accent
    val idle = if (idleColor.isSpecified) idleColor else LocalKineticaContentColor.current.copy(alpha = IDLE_ALPHA)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val latestProgress by rememberUpdatedState(progress)
    val scrub = remember { ScrubState() }
    val buckets = remember(samples) { WaveBuckets(samples) }
    val name = label ?: stringResource(R.string.kinetica_ui_waveform_position)
    Box(
        modifier
            .heightIn(min = MIN_HEIGHT)
            .waveSemantics(WaveA11y(name, durationMillis, onSeek), { latestProgress() })
            .then(if (onSeek != null) Modifier.waveInput(scrub, rtl, { latestProgress() }, onSeek) else Modifier)
            .drawBehind {
                val bars = BarGeometry(barWidth.toPx(), barGap.toPx(), size)
                val values = buckets.forCount(bars.count)
                val at = (if (scrub.value >= 0f) scrub.value else latestProgress()).coerceIn(0f, 1f)
                flip(rtl) {
                    drawBars(values, bars, idle)
                    clipRect(right = size.width * at) { drawBars(values, bars, played) }
                }
            },
    )
}

/** TalkBack에 내보낼 이름과 동작. */
private class WaveA11y(val name: String, val durationMillis: Long?, val onSeek: ((Float) -> Unit)?)

@Composable
private fun Modifier.waveSemantics(a11y: WaveA11y, progress: () -> Float): Modifier {
    val percent by remember { derivedStateOf { (progress().coerceIn(0f, 1f) * PERCENT).roundToInt() } }
    val value = percent / PERCENT
    val state = a11y.durationMillis?.let { "${elapsedText((it * value).toLong())} / ${elapsedText(it)}" }
    val seek = a11y.onSeek
    return clearAndSetSemantics {
        contentDescription = a11y.name
        progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
        state?.let { stateDescription = it }
        if (seek != null) {
            setProgress { target ->
                seek(target.coerceIn(0f, 1f))
                true
            }
        }
    }
}

private fun Modifier.waveInput(scrub: ScrubState, rtl: Boolean, progress: () -> Float, onSeek: (Float) -> Unit): Modifier = this
    .pointerInput(rtl, onSeek) {
        fun fractionAt(x: Float): Float {
            val f = (x / size.width).coerceIn(0f, 1f)
            return if (rtl) 1f - f else f
        }
        awaitEachGesture {
            val down = awaitFirstDown()
            val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
            if (start == null) {
                // 슬롭을 넘기 전에 뗐으면 누른 자리로 옮긴다. 세로로 움직여 부모가 가져갔으면 아무것도 하지 않는다.
                val tapped = currentEvent.changes.any { it.id == down.id && !it.pressed && !it.isConsumed }
                if (tapped) onSeek(fractionAt(down.position.x))
                return@awaitEachGesture
            }
            scrub.value = fractionAt(start.position.x)
            val finished = horizontalDrag(start.id) { change ->
                change.consume()
                scrub.value = fractionAt(change.position.x)
            }
            val target = scrub.value
            scrub.value = -1f
            if (finished) onSeek(target)
        }
    }
    .focusable()
    .onKeyEvent { event ->
        val delta = if (event.type == KeyEventType.KeyDown) keyDelta(event.key, rtl) else null
        if (delta != null) onSeek((progress() + delta).coerceIn(0f, 1f))
        delta != null
    }

/** 방향키 한 번에 옮길 몫. 방향키가 아니면 null. */
private fun keyDelta(key: Key, rtl: Boolean): Float? = when (key) {
    Key.DirectionRight -> if (rtl) -KEY_STEP else KEY_STEP
    Key.DirectionLeft -> if (rtl) KEY_STEP else -KEY_STEP
    else -> null
}

/** 끄는 동안의 위치(0..1). 음수면 끄는 중이 아니다. */
private class ScrubState {
    var value by mutableFloatStateOf(-1f)
}

/** 막대 폭과 간격, 그릴 수 있는 막대 수(px). */
private class BarGeometry(val width: Float, gap: Float, val area: Size) {
    val pitch: Float = max(width + gap, 1f)
    val count: Int = ((area.width + gap) / pitch).toInt().coerceIn(0, MAX_BARS)
}

/** 표본을 막대 수에 맞춰 줄인 값. 막대 수가 바뀔 때만 다시 계산하고 배열은 다시 쓴다. */
private class WaveBuckets(private val samples: FloatArray) {
    private val values = FloatArray(MAX_BARS)
    private var count = -1
    private val peak = samples.fold(0f) { acc, v -> max(acc, v) }

    fun forCount(n: Int): FloatArray {
        if (n == count) return values
        count = n
        for (i in 0 until n) {
            val from = i * samples.size / n
            val to = max(from + 1, (i + 1) * samples.size / n).coerceAtMost(samples.size)
            var top = 0f
            for (j in from until to) top = max(top, samples[j])
            values[i] = if (peak > 0f) top / peak else 0f
        }
        return values
    }
}

/** 고정 크기 링버퍼. [head]가 가장 새 값 다음 자리다. */
internal class LevelRing {
    private val values = FloatArray(MAX_BARS)
    private var head = 0
    private var last = -1L

    /** 표본 번호 [index]까지 비어 있는 자리를 [sample]로 채운다. 오래 멈췄다 돌아와도 한 바퀴 넘게 돌지 않는다. */
    fun advanceTo(index: Long, sample: () -> Float) {
        if (last < 0L || index < last) last = index - 1
        val missing = (index - last).coerceAtMost(MAX_BARS.toLong()).toInt()
        if (missing <= 0) return
        val value = sample()
        repeat(missing) { push(value) }
        last = index
    }

    fun fill(value: Float) {
        for (i in values.indices) values[i] = value
    }

    private fun push(value: Float) {
        values[head] = value
        head = (head + 1) % MAX_BARS
    }

    /** 가장 새 값에서 [back]번째 전 값. */
    operator fun get(back: Int): Float = values[Math.floorMod(head - 1 - back, MAX_BARS)]
}

private inline fun DrawScope.flip(rtl: Boolean, block: DrawScope.() -> Unit) {
    if (rtl) scale(-1f, 1f) { block() } else block()
}

/** 링버퍼를 끝 쪽부터 그린다. [shift]만큼 시작 쪽으로 밀어 다음 막대가 들어오는 사이를 메운다. */
private fun DrawScope.drawRing(ring: LevelRing, bars: BarGeometry, shift: Float, color: Color) {
    val h = bars.area.height
    val minBar = bars.width
    val radius = CornerRadius(bars.width / 2)
    for (i in 0..bars.count) {
        val x = bars.area.width - bars.width - i * bars.pitch - shift
        if (x + bars.width < 0f) break
        val barH = max(minBar, ring[i] * h)
        drawRoundRect(color, Offset(x, (h - barH) / 2), Size(bars.width, barH), radius)
    }
}

private fun DrawScope.drawBars(values: FloatArray, bars: BarGeometry, color: Color) {
    val h = bars.area.height
    val radius = CornerRadius(bars.width / 2)
    for (i in 0 until bars.count) {
        val barH = max(bars.width, values[i] * h)
        drawRoundRect(color, Offset(i * bars.pitch, (h - barH) / 2), Size(bars.width, barH), radius)
    }
}

private const val MAX_BARS = 256
private const val MIN_INTERVAL_MS = 16L
private const val MILLIS = 1_000f
private const val STILL_REFRESH_MS = 250L
private const val IDLE_ALPHA = 0.3f
private const val PERCENT = 100f
private const val KEY_STEP = 0.05f
private val MIN_HEIGHT = 16.dp
