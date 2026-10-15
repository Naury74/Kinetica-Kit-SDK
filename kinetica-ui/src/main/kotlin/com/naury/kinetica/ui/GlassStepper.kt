package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 빼기와 더하기 버튼이 붙은 캡슐 모양 글래스 스테퍼. 가운데 값은 [RollingText]처럼 자릿수가 위아래로 굴러가며 바뀐다.
 *
 * 버튼을 눌렀다 떼면 한 칸 바뀐다. 길게 누르고 있으면 길게 누름이 확정될 때부터 값이 계속 바뀌고, 오래 누를수록 빨라진다.
 * 누른 채 터치 슬롭 넘게 움직이면 바꾸지 않으므로 스크롤 목록 안에서도 스크롤을 막지 않는다. 값이 [range] 끝에 닿으면 그쪽 버튼이 흐려지고 눌러도 바뀌지 않는다.
 * 값이 바뀔 때마다 가벼운 햅틱을 준다. 키보드 방향키(위와 오른쪽은 증가, RTL에서는 왼쪽이 증가)로도 조절할 수 있다.
 * TalkBack은 스테퍼 전체를 하나의 조절 항목으로 읽고, 볼륨 키나 '늘리기', '줄이기' 동작으로 값을 바꿀 수 있다.
 *
 * ```kotlin
 * GlassStepper(quantity, { quantity = it }, range = 1..20)
 * ```
 *
 * @param value 현재 값. [range] 밖이면 가까운 끝 값으로 보여 준다.
 * @param onValueChange 바뀐 값을 받는다. 항상 [range] 안의 값이다.
 * @param range 값 범위. 비어 있으면 [IllegalArgumentException]을 던진다.
 * @param step 한 번에 바뀌는 크기. 1 이상이 아니면 [IllegalArgumentException]을 던진다.
 * @param enabled `false`면 값을 바꿀 수 없고 흐리게 그린다.
 * @param showValue `false`면 가운데 값을 숨기고 두 버튼만 보여 준다.
 */
@Composable
public fun GlassStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..DEFAULT_MAX,
    step: Int = 1,
    enabled: Boolean = true,
    showValue: Boolean = true,
    style: GlassStyle = LocalGlassStyle.current,
) {
    require(!range.isEmpty()) { "range가 비어 있습니다." }
    require(step >= 1) { "step은 1 이상이어야 합니다." }
    val shown = value.coerceIn(range)
    val latestValue by rememberUpdatedState(shown)
    val latestChange by rememberUpdatedState(onValueChange)
    val haptic = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val increase = stringResource(R.string.kinetica_ui_increase)
    val decrease = stringResource(R.string.kinetica_ui_decrease)
    val canDecrease = enabled && shown > range.first
    val canIncrease = enabled && shown < range.last

    fun change(from: Int, delta: Int): Int {
        val next = (from + delta).coerceIn(range)
        if (next != from) {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            latestChange(next)
        }
        return next
    }

    Row(
        modifier
            .height(STEPPER_HEIGHT)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .glass(style, CircleShape)
            .stepperSemantics(StepperA11y(shown, range, step, enabled, increase, decrease)) { latestChange(it) }
            .focusable(enabled)
            .onKeyEvent { event ->
                val delta = if (enabled && event.type == KeyEventType.KeyDown) stepperKey(event.key, rtl) else null
                if (delta != null) change(latestValue, delta * step)
                delta != null
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(plus = false, canDecrease, style.onGlass) { change(it ?: latestValue, -step) }
        if (showValue) {
            RollingText(
                shown.toString(),
                Modifier.widthIn(min = VALUE_MIN_WIDTH).padding(horizontal = VALUE_PADDING),
                TextStyle(color = style.onGlass, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            )
        } else {
            Box(
                Modifier
                    .padding(vertical = DIVIDER_INSET)
                    .fillMaxHeight()
                    .width(DIVIDER_WIDTH)
                    .drawBehind { drawRect(style.onGlass.copy(alpha = DIVIDER_ALPHA)) },
            )
        }
        StepButton(plus = true, canIncrease, style.onGlass) { change(it ?: latestValue, step) }
    }
}

/**
 * 스테퍼의 버튼 한 칸. 탭하면 손을 뗄 때 한 번 바꾸고, 길게 누름이 확정되면 그때부터 점점 빠르게 반복한다.
 * [onStep]은 직전 결과 값(첫 번째는 `null`)을 받아 새 값을 돌려준다. 재구성을 기다리지 않고 이어서 셀 수 있게 하려는 것이다.
 */
@Composable
private fun StepButton(plus: Boolean, enabled: Boolean, color: Color, onStep: (Int?) -> Int) {
    val latestStep by rememberUpdatedState(onStep)
    val latestEnabled by rememberUpdatedState(enabled)
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dim by animateFloatAsState(if (enabled) 1f else EDGE_ALPHA, tween(EDGE_FADE_MS), label = "step-dim")
    Box(
        Modifier
            .fillMaxHeight()
            .widthIn(min = BUTTON_WIDTH)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!latestEnabled) return@awaitEachGesture
                    scope.launch { press.animateTo(1f, tween(PRESS_IN_MS)) }
                    try {
                        val tapped = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            awaitTapOrMove(down, viewConfiguration.touchSlop)
                        }
                        when (tapped) {
                            true -> latestStep(null)
                            false -> Unit
                            null -> holdToRepeat(scope) { latestStep(it) }
                        }
                    } finally {
                        scope.launch { press.animateTo(0f, tween(PRESS_OUT_MS)) }
                    }
                }
            }
            .drawBehind {
                val inset = size.height * PRESS_INSET
                val pressAlpha = press.value * PRESS_ALPHA
                if (pressAlpha > 0f) {
                    drawCircle(color.copy(alpha = pressAlpha), radius = size.height / 2 - inset)
                }
                val half = size.height * GLYPH_HALF
                val stroke = GLYPH_STROKE.toPx()
                val tint = color.copy(alpha = color.alpha * dim)
                drawLine(tint, Offset(center.x - half, center.y), Offset(center.x + half, center.y), stroke, StrokeCap.Round)
                if (plus) drawLine(tint, Offset(center.x, center.y - half), Offset(center.x, center.y + half), stroke, StrokeCap.Round)
            },
    )
}

/**
 * 손을 떼면 `true`(탭), 터치 슬롭 넘게 움직이거나 다른 쪽(부모 스크롤 등)이 이벤트를 가져가면 `false`(취소)를 돌려준다.
 */
private suspend fun AwaitPointerEventScope.awaitTapOrMove(down: PointerInputChange, slop: Float): Boolean {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return false
        if (change.isConsumed) return false
        if (!change.pressed) {
            change.consume()
            return true
        }
        if ((change.position - down.position).getDistance() > slop) return false
    }
}

/** 길게 누름이 확정된 뒤 손을 뗄 때까지 반복한다. */
private suspend fun AwaitPointerEventScope.holdToRepeat(scope: CoroutineScope, onStep: (Int?) -> Int) {
    val repeat = scope.launch { repeatWhilePressed(onStep) }
    try {
        waitForUpOrCancellation()?.consume()
    } finally {
        repeat.cancel()
    }
}

/** 곧바로 한 번 바꾸고, 간격을 줄여 가며 반복한다. 끝에 닿아 값이 그대로면 멈춘다. */
private suspend fun repeatWhilePressed(onStep: (Int?) -> Int) = coroutineScope {
    var last = onStep(null)
    var interval = REPEAT_START_MS
    while (true) {
        delay(interval)
        val next = onStep(last)
        if (next == last) break
        last = next
        interval = (interval * REPEAT_SPEED_UP).toLong().coerceAtLeast(REPEAT_MIN_MS)
    }
}

/** TalkBack용 스테퍼 정보. */
private class StepperA11y(
    val value: Int,
    val range: IntRange,
    val step: Int,
    val enabled: Boolean,
    val increase: String,
    val decrease: String,
)

private fun Modifier.stepperSemantics(a11y: StepperA11y, onChange: (Int) -> Unit): Modifier = clearAndSetSemantics {
    val range = a11y.range
    val value = a11y.value
    stateDescription = value.toString()
    // 정수 범위를 눈금으로 알려 TalkBack 볼륨 키 한 번에 한 칸씩 움직이게 한다.
    val steps = ((range.last - range.first) / a11y.step - 1).coerceAtLeast(0)
    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.first.toFloat()..range.last.toFloat(), steps)
    if (!a11y.enabled) {
        disabled()
        return@clearAndSetSemantics
    }
    setProgress { target ->
        val next = target.roundToInt().coerceIn(range)
        if (next != value) onChange(next)
        next != value
    }
    fun action(label: String, delta: Int) = CustomAccessibilityAction(label) {
        onChange((value + delta).coerceIn(range))
        true
    }
    customActions = buildList {
        if (value < range.last) add(action(a11y.increase, a11y.step))
        if (value > range.first) add(action(a11y.decrease, -a11y.step))
    }
}

/** 방향키를 증감 방향으로 바꾼다. 위와 진행 방향 쪽(LTR 오른쪽, RTL 왼쪽)이 증가다. 스테퍼 키가 아니면 `null`. */
private fun stepperKey(key: Key, rtl: Boolean): Int? = when (key) {
    Key.DirectionUp -> 1
    Key.DirectionDown -> -1
    Key.DirectionRight -> if (rtl) -1 else 1
    Key.DirectionLeft -> if (rtl) 1 else -1
    else -> null
}

private val STEPPER_HEIGHT = 40.dp
private val BUTTON_WIDTH = 48.dp
private val VALUE_MIN_WIDTH = 32.dp
private val VALUE_PADDING = 4.dp
private val DIVIDER_INSET = 10.dp
private val DIVIDER_WIDTH = 1.dp
private val GLYPH_STROKE = 2.dp
private const val GLYPH_HALF = 0.17f
private const val PRESS_INSET = 0.1f
private const val PRESS_ALPHA = 0.14f
private const val DIVIDER_ALPHA = 0.18f
private const val EDGE_ALPHA = 0.3f
private const val DISABLED_ALPHA = 0.5f
private const val EDGE_FADE_MS = 160
private const val PRESS_IN_MS = 60
private const val PRESS_OUT_MS = 220
private const val REPEAT_START_MS = 140L
private const val REPEAT_MIN_MS = 35L
private const val REPEAT_SPEED_UP = 0.86f
private const val DEFAULT_MAX = 99
