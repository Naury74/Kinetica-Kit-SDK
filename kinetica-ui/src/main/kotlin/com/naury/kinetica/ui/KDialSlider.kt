package com.naury.kinetica.ui

import android.os.SystemClock
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 눈금자를 좌우로 끌어 값을 고르는 가로 눈금 다이얼(사진 앱 보정 다이얼 방식).
 *
 * 가운데 기준선은 고정되고 눈금이 손가락을 따라 움직인다. [resetValue]부터 현재 값까지의 눈금은 [accent]로 칠하고,
 * 값은 위에 크게 보여 준다. 세게 밀고 놓으면 그 속도로 미끄러지다 서서히 멈추고, 미끄러지는 중에 다시 잡으면 그 자리에서 멈춘다.
 * 눈금을 지날 때마다 짧은 톱니 햅틱을, 큰 눈금에서는 조금 더 또렷한 햅틱을, [resetValue]와 양 끝에서는 분명한 햅틱을 낸다
 * ([LocalKineticaHaptics]를 따른다). 두 번 탭하면 [resetValue]로 돌아간다. RTL에서는 큰 값이 왼쪽에 있다.
 * 키보드 좌우 방향키로 한 눈금씩, TalkBack은 범위 정보와 값 설정 동작으로 조절한다. 화면 가장자리에서 끌어도 시스템 뒤로 가기로
 * 처리되지 않는다. [isReducedMotion]이 true면 놓은 자리에서 바로 멈춘다.
 *
 * ```kotlin
 * var exposure by remember { mutableFloatStateOf(0f) }
 * KDialSlider(exposure, { exposure = it }, label = "노출", formatValue = { it.roundToInt().toString() })
 * ```
 *
 * @param value 현재 값. [valueRange] 밖이면 가까운 끝 값으로 그린다.
 * @param onValueChange 끌거나 미끄러지는 동안 값이 바뀔 때마다 받는다. 항상 [valueRange] 안의 값이다.
 * @param label TalkBack이 읽는 이름. [showLabel]이 true면 값 위에도 보여 준다.
 * @param valueRange 값 범위. 끝이 시작보다 크지 않거나 유한하지 않으면 예외 없이 `enabled = false`처럼 입력을 받지 않고 흐리게 그린다.
 * @param formatValue 위에 크게 보여 주고 TalkBack이 읽을 값 글자. 기본값은 반올림한 정수다.
 * @param onValueChangeFinished 손을 떼고 관성으로 미끄러지는 것까지 끝난 뒤 한 번 호출된다. 미끄러지는 중에 다시 잡으면
 * 그때 한 번, 두 번 탭 초기화, 방향키, TalkBack으로 바꾼 뒤에도 한 번 호출된다.
 * @param resetValue 기준값. 이 값부터 현재 값까지를 강조색으로 칠하고, 두 번 탭하면 이 값으로 돌아간다.
 * [valueRange] 밖이면 가까운 끝으로 맞춰 그리고, 예외 없이 입력을 받지 않고 흐리게 그린다.
 * @param spacing 값 1만큼의 눈금 간격(dp). 클수록 세밀하게 조절된다. 0보다 크지 않으면 [IllegalArgumentException]을 던진다.
 * @param tickEvery 작은 눈금을 그리는 값 간격. 0에서부터 이 간격의 배수 자리에 그린다. 방향키 한 번에 이만큼 움직인다.
 * 0보다 크지 않으면 [IllegalArgumentException]을 던진다.
 * @param majorEvery 큰 눈금을 그리는 값 간격. 0보다 크지 않으면 [IllegalArgumentException]을 던진다.
 * @param snapThreshold 값이 [resetValue]에서 이 거리 안에 들면 [resetValue]에 붙는다. 0이면 붙지 않는다.
 * 음수면 [IllegalArgumentException]을 던진다.
 * @param enabled `false`면 터치, 키보드, TalkBack으로 값을 바꿀 수 없고 흐리게 그린다.
 * @param showLabel `true`면 [label]을 값 위에 작게 보여 준다.
 * @param accent 기준값부터 현재 값까지의 눈금과 바뀐 값 글자의 색. 기본값은 [KineticaTheme]의 `accent`다.
 * @param surface 글자와 흐린 눈금 색을 정하는 표면. 다이얼은 배경을 그리지 않는다.
 * @param changedThreshold 값이 바뀐 것으로 보는 거리로, 값과 같은 단위다. |값 - [resetValue]|가 이 값 이상일 때만 눈금, 기준점, 값 글자를
 *   [accent]로 칠한다. 0이면 값이 [resetValue]와 다르기만 하면 칠한다. 보이는 글자가 반올림돼 기준값과 같아 보일 때 칠하지 않으려면
 *   반올림 폭(정수면 0.5)을 준다. 음수거나 NaN이면 [IllegalArgumentException]을 던진다.
 * @param valueTextStyle 값 글자 스타일. 기본 스타일(16sp, 굵게, 고정폭 숫자, 가운데 정렬) 위에 덮어쓴다. 색을 정하지 않으면
 *   바뀜 여부에 따라 [accent]나 표면 글자색을 쓰고, 색을 정하면 늘 그 색이다. null이면 기본 스타일 그대로다.
 */
@Composable
public fun KDialSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = DEFAULT_RANGE,
    formatValue: (Float) -> String = { it.roundToInt().toString() },
    onValueChangeFinished: (() -> Unit)? = null,
    resetValue: Float = 0f,
    spacing: Dp = 4.dp,
    tickEvery: Float = 2f,
    majorEvery: Float = 10f,
    snapThreshold: Float = 1.5f,
    enabled: Boolean = true,
    showLabel: Boolean = false,
    accent: Color = KineticaTheme.colors.accent,
    surface: SurfaceStyle = KineticaTheme.surface,
    changedThreshold: Float = 0f,
    valueTextStyle: TextStyle? = null,
) {
    require(changedThreshold >= 0f) { "changedThreshold는 0 이상이어야 합니다." }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // 범위와 기준값은 데이터에서 오는 일이 많아, 잘못돼도 화면 전체를 죽이지 않고 입력만 막는다.
    val usableRange = isUsableRange(valueRange)
    val range = if (usableRange) valueRange else fallbackRange(valueRange)
    val usable = usableRange && resetValue in range
    val reset = if (resetValue.isFinite()) resetValue.coerceIn(range) else range.start
    val active = enabled && usable
    val dial = TickDialSpec(range, reset, spacing, tickEvery, majorEvery, snapThreshold, rtl, changedThreshold)
    val shown = value.coerceIn(range)
    val shownState = rememberUpdatedState(shown)
    val haptic = kHapticFeedback()
    val ticker = remember(dial, haptic) { TickDialTicker(dial, haptic) }
    val control = remember { TickDialControl() }
    control.dial = dial
    control.ticker = ticker
    control.onChange = onValueChange
    control.onFinished = onValueChangeFinished
    control.value = shown
    control.reducedMotion = isReducedMotion()
    val changed = dial.isChanged(shown)
    val text = formatValue(shown)
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (active) 1f else DISABLED_ALPHA }
            // focusable을 clearAndSetSemantics 뒤에 두면 포커스 요청 동작까지 지워진다.
            .focusable(active)
            .dialSemantics(TickDialA11y(label, text, shown, range, active), control)
            .onKeyEvent { event ->
                val direction = if (active && event.type == KeyEventType.KeyDown) dialKey(event.key, rtl) else null
                if (direction != null) control.stepBy(direction)
                direction != null
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val weight = KineticaTheme.design.label(FontWeight.SemiBold)
        if (showLabel) {
            ThemedText(label, style = TextStyle(color = surface.muted(MUTED_ALPHA), fontSize = 13.sp, textAlign = TextAlign.Center))
        }
        val baseStyle = TextStyle(
            color = if (changed) accent else surface.contentColor,
            fontSize = 16.sp,
            fontWeight = weight,
            fontFeatureSettings = "tnum",
            textAlign = TextAlign.Center,
        )
        ThemedText(text, style = if (valueTextStyle == null) baseStyle else baseStyle.merge(valueTextStyle))
        val ink = TickDialInk(accent, surface.muted(MUTED_ALPHA), surface.contentColor)
        Spacer(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(DIAL_HEIGHT)
                // 화면 가장자리에서 시작한 끌기가 시스템 뒤로 가기로 처리되지 않게 한다.
                .systemGestureExclusion()
                .then(if (active) Modifier.dialGestures(control) else Modifier)
                .drawBehind { drawDial(dial, shownState, ink) },
        )
    }
}

/** 1.0.0-alpha03까지의 시그니처. 컴파일된 호출자를 위해 남긴다. */
@Deprecated("바이너리 호환용", level = DeprecationLevel.HIDDEN)
@Composable
public fun KDialSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = DEFAULT_RANGE,
    formatValue: (Float) -> String = { it.roundToInt().toString() },
    onValueChangeFinished: (() -> Unit)? = null,
    resetValue: Float = 0f,
    spacing: Dp = 4.dp,
    tickEvery: Float = 2f,
    majorEvery: Float = 10f,
    snapThreshold: Float = 1.5f,
    enabled: Boolean = true,
    showLabel: Boolean = false,
    accent: Color = KineticaTheme.colors.accent,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    KDialSlider(
        value, onValueChange, label, modifier, valueRange, formatValue, onValueChangeFinished, resetValue, spacing, tickEvery,
        majorEvery, snapThreshold, enabled, showLabel, accent, surface, changedThreshold = 0f,
    )
}

/** 다이얼의 범위와 눈금 설정. 모양과 손맛을 함께 정한다. 범위와 기준값은 부르는 쪽에서 쓸 수 있는 값으로 맞춰 넘긴다. */
private data class TickDialSpec(
    val range: ClosedFloatingPointRange<Float>,
    val resetValue: Float,
    val spacing: Dp,
    val tickEvery: Float,
    val majorEvery: Float,
    val snapThreshold: Float,
    val rtl: Boolean,
    val changedThreshold: Float,
) {
    init {
        // 아래 셋은 보통 상수로 주는 값이라 잘못되면 개발 중에 바로 드러나도록 던진다. 0이면 눈금 계산이 0으로 나뉜다.
        require(spacing.value > 0f) { "spacing은 0보다 커야 합니다." }
        require(tickEvery > 0f && majorEvery > 0f) { "tickEvery와 majorEvery는 0보다 커야 합니다." }
        require(snapThreshold >= 0f) { "snapThreshold는 0 이상이어야 합니다." }
    }

    /** 손가락이 오른쪽으로 갈 때 값이 바뀌는 방향. 눈금을 미는 느낌이라 LTR에서는 값이 줄어든다. */
    val direction: Float get() = if (rtl) 1f else -1f

    fun clamp(v: Float): Float = v.coerceIn(range.start, range.endInclusive)

    fun snapped(v: Float): Float = if (snapThreshold > 0f && abs(v - resetValue) <= snapThreshold) resetValue else v

    fun isMajor(v: Float): Boolean = abs(v / majorEvery - (v / majorEvery).roundToInt()) < EPSILON

    fun isEdge(v: Float): Boolean = v == range.start || v == range.endInclusive

    fun isChanged(v: Float): Boolean = v != resetValue && abs(v - resetValue) >= changedThreshold

    fun tickOf(v: Float): Int = floor(v / tickEvery + EPSILON).toInt()
}

private data class TickDialInk(val accent: Color, val muted: Color, val content: Color)

private class TickDialA11y(
    val label: String,
    val text: String,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val enabled: Boolean,
)

/**
 * 제스처, 키보드, TalkBack이 함께 쓰는 최신 값과 콜백. 재구성마다 바꿔 넣어 제스처를 다시 시작하지 않고도 새 값을 쓴다.
 * 관성 미끄러짐 작업도 여기에 두어 다시 잡을 때 멈춘다.
 */
private class TickDialControl {
    lateinit var dial: TickDialSpec
    lateinit var ticker: TickDialTicker
    var onChange: (Float) -> Unit = {}
    var onFinished: (() -> Unit)? = null
    var value = 0f
    var reducedMotion = false
    var fling: Job? = null

    fun emit(next: Float) {
        if (next != value) {
            value = next
            onChange(next)
        }
    }

    /** 미끄러지는 중이면 멈추고 완료를 한 번 알린다. 멈췄으면 true. */
    fun stopFling(): Boolean {
        val running = fling?.isActive == true
        fling?.cancel()
        fling = null
        if (running) onFinished?.invoke()
        return running
    }

    fun jumpTo(target: Float, now: Long) {
        stopFling()
        val next = dial.clamp(target)
        ticker.reset(value)
        ticker.step(next, now)
        emit(next)
        onFinished?.invoke()
    }

    /** [direction] 쪽 다음 눈금으로 옮긴다. 눈금 사이에 있으면 그쪽 가장 가까운 눈금으로 간다. */
    fun stepBy(direction: Int) {
        val tick = dial.tickEvery
        val grid = value / tick
        val index = if (direction > 0) floor(grid + EPSILON) + 1 else ceil(grid - EPSILON) - 1
        jumpTo(index * tick, SystemClock.uptimeMillis())
    }
}

private fun Modifier.dialSemantics(a11y: TickDialA11y, control: TickDialControl): Modifier = clearAndSetSemantics {
    contentDescription = a11y.label
    stateDescription = a11y.text
    progressBarRangeInfo = ProgressBarRangeInfo(a11y.value, a11y.range)
    if (!a11y.enabled) {
        disabled()
        return@clearAndSetSemantics
    }
    setProgress { target ->
        control.jumpTo(target, SystemClock.uptimeMillis())
        true
    }
}

/** 좌우 방향키를 값의 증감으로 바꾼다. 진행 방향 쪽(LTR 오른쪽, RTL 왼쪽)이 증가다. 다이얼 키가 아니면 `null`. */
private fun dialKey(key: Key, rtl: Boolean): Int? = when (key) {
    Key.DirectionRight -> if (rtl) -1 else 1
    Key.DirectionLeft -> if (rtl) 1 else -1
    else -> null
}

private fun Modifier.dialGestures(control: TickDialControl): Modifier = this
    .pointerInput(control) {
        detectTapGestures(onDoubleTap = { control.jumpTo(control.dial.resetValue, SystemClock.uptimeMillis()) })
    }
    .pointerInput(control) {
        coroutineScope { dragDial(control, this) }
    }

/** 끌기와 관성. 손가락을 대면 미끄러짐을 멈추고, 가로로 터치 슬롭을 넘으면 끌기로 본다. 세로로 먼저 움직이면 부모 스크롤에 넘긴다. */
private suspend fun PointerInputScope.dragDial(control: TickDialControl, scope: CoroutineScope) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        control.stopFling()
        val dial = control.dial
        val pxPerUnit = dial.spacing.toPx()
        var raw = control.value
        control.ticker.reset(raw)
        val velocity = VelocityTracker()
        velocity.addPointerInputChange(down)
        fun move(dx: Float, now: Long) {
            raw = dial.clamp(raw + dial.direction * dx / pxPerUnit)
            val next = dial.snapped(raw)
            control.ticker.step(next, now)
            control.emit(next)
        }
        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            velocity.addPointerInputChange(change)
            move(over, change.uptimeMillis)
        } ?: return@awaitEachGesture
        // horizontalDrag는 손을 뗀 이벤트를 넘기지 않는다. 손 뗌은 마지막 움직임보다 늦게 와 '멈춘 뒤 뗐다'로 읽힐 수 있어 속도에서 빠지는 편이 맞다.
        val completed = horizontalDrag(start.id) { change ->
            velocity.addPointerInputChange(change)
            move(change.position.x - change.previousPosition.x, change.uptimeMillis)
            change.consume()
        }
        val unitsPerSecond = dial.direction * velocity.calculateVelocity().x / pxPerUnit
        if (!completed || control.reducedMotion || abs(unitsPerSecond) < MIN_FLING_UNITS_PER_SECOND) {
            control.onFinished?.invoke()
        } else {
            control.fling = scope.launch { flingDial(control, raw, unitsPerSecond) }
        }
    }
}

/** 놓은 속도만큼 이어서 미끄러지다 서서히 멈춘다. 기준값 근처에서 멈추면 기준값에 붙인다. */
private suspend fun flingDial(control: TickDialControl, from: Float, unitsPerSecond: Float) {
    val dial = control.dial
    var position = from
    AnimationState(initialValue = from, initialVelocity = unitsPerSecond)
        .animateDecay(exponentialDecay(frictionMultiplier = FLING_FRICTION)) {
            position = dial.clamp(this.value)
            control.ticker.step(position, lastFrameTimeNanos / NANOS_PER_MILLI)
            control.emit(position)
            // 끝에 닿으면 더 갈 곳이 없으니 멈춘다.
            if (dial.isEdge(position)) cancelAnimation()
        }
    val snapped = dial.snapped(position)
    if (snapped != position) {
        control.ticker.step(snapped, SystemClock.uptimeMillis())
        control.emit(snapped)
    }
    control.fling = null
    control.onFinished?.invoke()
}

private fun DrawScope.drawDial(dial: TickDialSpec, valueState: State<Float>, ink: TickDialInk) {
    val value = valueState.value
    val pxPerUnit = dial.spacing.toPx()
    val center = size.width / 2f
    val mid = size.height / 2f
    val sign = if (dial.rtl) -1f else 1f
    fun x(v: Float) = center + sign * (v - value) * pxPerUnit
    val reach = center / pxPerUnit
    val range = dial.range
    val first = ceil(maxOf(range.start, value - reach) / dial.tickEvery - EPSILON).toInt()
    val last = floor(minOf(range.endInclusive, value + reach) / dial.tickEvery + EPSILON).toInt()
    val low = minOf(dial.resetValue, value)
    val high = maxOf(dial.resetValue, value)
    val changed = dial.isChanged(value)
    val minor = TICK_WIDTH.toPx()
    for (index in first..last) {
        val v = index * dial.tickEvery
        val major = dial.isMajor(v)
        val active = changed && v >= low - EPSILON && v <= high + EPSILON
        val length = size.height * if (major) MAJOR_LENGTH else MINOR_LENGTH
        val tx = x(v)
        // 가장자리로 갈수록 흐리게 해 다이얼이 휘어 보이게 한다.
        val fade = (1f - abs(tx - center) / center).coerceIn(0f, 1f)
        val base = if (active) ink.accent else ink.muted
        val alpha = base.alpha * (if (major) MAJOR_ALPHA else MINOR_ALPHA) * (FADE_FLOOR + (1f - FADE_FLOOR) * fade)
        drawLine(
            base.copy(alpha = alpha),
            Offset(tx, mid - length / 2f),
            Offset(tx, mid + length / 2f),
            if (major) minor * MAJOR_WIDTH_SCALE else minor,
            cap = StrokeCap.Round,
        )
    }
    // 기준값이 범위 안쪽에 있으면 그 자리를 점으로 보여 준다.
    if (dial.resetValue > range.start && dial.resetValue < range.endInclusive) {
        drawCircle(if (changed) ink.accent else ink.muted, RESET_DOT_RADIUS.toPx(), Offset(x(dial.resetValue), RESET_DOT_Y.toPx()))
    }
    // 고정된 가운데 기준선.
    drawLine(
        Brush.verticalGradient(listOf(ink.content, ink.content.copy(alpha = ink.content.alpha * CENTER_LINE_FADE))),
        Offset(center, 0f),
        Offset(center, size.height),
        CENTER_LINE_WIDTH.toPx(),
        cap = StrokeCap.Round,
    )
}

/**
 * 값이 움직일 때 지난 눈금을 세어 톱니 햅틱을 낸다. 끌 때와 관성으로 미끄러질 때 함께 쓴다.
 * 작은 눈금은 약하게, 큰 눈금은 조금 더 또렷하게, 기준값과 끝은 분명하게 울리며, 너무 촘촘하면 [MIN_TICK_INTERVAL_MS]만큼 거른다.
 */
private class TickDialTicker(private val dial: TickDialSpec, private val haptic: HapticFeedback) {
    private var last = 0f
    private var lastTick = 0
    private var lastAt = Long.MIN_VALUE / 2

    fun reset(value: Float) {
        last = value
        lastTick = dial.tickOf(value)
    }

    fun step(next: Float, now: Long) {
        if (next == last) return
        val reset = dial.resetValue
        val crossedReset = (last - reset) * (next - reset) < 0f || (next == reset && last != reset)
        val tick = dial.tickOf(next)
        when {
            crossedReset || dial.isEdge(next) -> {
                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                lastAt = now
            }

            tick != lastTick && now - lastAt >= MIN_TICK_INTERVAL_MS -> {
                val crossedMajor = (minOf(tick, lastTick) + 1..maxOf(tick, lastTick)).any { dial.isMajor(it * dial.tickEvery) }
                haptic.performHapticFeedback(if (crossedMajor) HapticFeedbackType.SegmentTick else HapticFeedbackType.SegmentFrequentTick)
                lastAt = now
            }
        }
        last = next
        lastTick = tick
    }
}

private val DEFAULT_RANGE = -100f..100f
private val DIAL_HEIGHT = 40.dp
private val TICK_WIDTH = 1.dp
private val CENTER_LINE_WIDTH = 2.dp
private val RESET_DOT_RADIUS = 2.5.dp
private val RESET_DOT_Y = 2.dp
private const val MAJOR_LENGTH = 0.55f
private const val MINOR_LENGTH = 0.3f
private const val MAJOR_ALPHA = 0.9f
private const val MINOR_ALPHA = 0.55f
private const val MAJOR_WIDTH_SCALE = 1.5f
private const val FADE_FLOOR = 0.25f
private const val CENTER_LINE_FADE = 0.6f
private const val MUTED_ALPHA = 0.6f
private const val DISABLED_ALPHA = 0.4f
private const val EPSILON = 0.001f
private const val NANOS_PER_MILLI = 1_000_000L

// 이보다 느리게 놓으면 미끄러지지 않는다(값 단위/초).
private const val MIN_FLING_UNITS_PER_SECOND = 15f

// 클수록 빨리 멈춘다. 다이얼이 너무 멀리 가지 않도록 기본값(1)보다 크게 둔다.
private const val FLING_FRICTION = 2.2f

// 눈금 진동 사이 최소 간격(ms). 이보다 빠르면 진동이 이어져 한 덩어리로 느껴진다.
private const val MIN_TICK_INTERVAL_MS = 18L
