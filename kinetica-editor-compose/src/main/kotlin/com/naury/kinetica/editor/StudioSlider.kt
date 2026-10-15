package com.naury.kinetica.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 편집기 슬라이더. 높이 44dp 안에 4dp 트랙과 20dp 손잡이를 그린다. 누르거나 끄는 동안 손잡이 둘레에 옅은 원이 나타나고 기본 리플은 없다.
 *
 * [steps]가 0보다 크면 범위를 `steps + 1`칸으로 나눈 값에만 멈춘다. 끄는 동안에는 [onValueChange]만 부르고 손을 떼면
 * [onValueChangeFinished]를 부른다. 방향키(한 칸, 눈금이 없으면 범위의 1%)와 TalkBack의 값 조절은 바꿀 때마다 둘 다 부른다.
 */
@Composable
internal fun StudioSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onValueChangeFinished: () -> Unit = {},
) {
    val theme = LocalStudioTheme.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val latestChange by rememberUpdatedState(onValueChange)
    val latestFinish by rememberUpdatedState(onValueChangeFinished)
    val model = remember(valueRange, steps, rtl) { SliderModel(valueRange, steps, rtl) }
    val halo by animateFloatAsState(if (model.active) 1f else 0f, tween(if (model.active) 0 else HALO_FADE_MS))
    val current = value.coerceIn(valueRange)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(SLIDER_HEIGHT)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(current, valueRange, steps)
                setProgress { target ->
                    latestChange(model.snap(target))
                    latestFinish()
                    true
                }
            }
            .onKeyEvent { event ->
                val direction = if (event.type == KeyEventType.KeyDown) model.keyDirection(event.key) else 0
                if (direction != 0) {
                    latestChange(model.nudge(value, direction))
                    latestFinish()
                }
                direction != 0
            }
            .focusable(interactionSource = source)
            .sliderPointer(model, { latestChange(it) }, { latestFinish() }),
    ) {
        drawSlider(model.fraction(current), rtl, steps, halo, focused, theme)
    }
}

// 범위, 눈금, 방향에 따른 값 계산과 누르고 있는지 여부.
private class SliderModel(val range: ClosedFloatingPointRange<Float>, val steps: Int, val rtl: Boolean) {
    var active by mutableStateOf(false)
    private val span = range.endInclusive - range.start

    fun snap(raw: Float): Float {
        val clamped = raw.coerceIn(range)
        if (steps <= 0 || span <= 0f) return clamped
        val step = span / (steps + 1)
        return range.start + ((clamped - range.start) / step).roundToInt() * step
    }

    fun fraction(value: Float): Float = if (span > 0f) (value - range.start) / span else 0f

    // 트랙은 손잡이 반지름만큼 안쪽에서 시작하고 끝난다.
    fun valueAt(x: Float, width: Float, radius: Float): Float {
        val inner = (width - 2 * radius).coerceAtLeast(1f)
        val fraction = ((x - radius) / inner).coerceIn(0f, 1f).let { if (rtl) 1f - it else it }
        return snap(range.start + fraction * span)
    }

    fun nudge(value: Float, direction: Int): Float {
        val step = if (steps > 0) span / (steps + 1) else span * KEY_STEP_FRACTION
        return snap(value + direction * step)
    }

    fun keyDirection(key: Key): Int = when (key) {
        Key.DirectionUp -> 1
        Key.DirectionDown -> -1
        Key.DirectionRight -> if (rtl) -1 else 1
        Key.DirectionLeft -> if (rtl) 1 else -1
        else -> 0
    }
}

private fun Modifier.sliderPointer(model: SliderModel, onChange: (Float) -> Unit, onFinish: () -> Unit): Modifier = this
    .pointerInput(model) {
        val radius = THUMB_SIZE.toPx() / 2
        detectTapGestures(
            onPress = {
                model.active = true
                tryAwaitRelease()
                model.active = false
            },
            onTap = { offset ->
                onChange(model.valueAt(offset.x, size.width.toFloat(), radius))
                onFinish()
            },
        )
    }
    .pointerInput(model) {
        val radius = THUMB_SIZE.toPx() / 2
        val stop = {
            model.active = false
            onFinish()
        }
        detectHorizontalDragGestures(
            onDragStart = {
                model.active = true
                onChange(model.valueAt(it.x, size.width.toFloat(), radius))
            },
            onHorizontalDrag = { change, _ ->
                change.consume()
                onChange(model.valueAt(change.position.x, size.width.toFloat(), radius))
            },
            onDragEnd = stop,
            onDragCancel = stop,
        )
    }

@Suppress("LongParameterList")
private fun DrawScope.drawSlider(fraction: Float, rtl: Boolean, steps: Int, halo: Float, focused: Boolean, theme: StudioTheme) {
    val radius = THUMB_SIZE.toPx() / 2
    val track = TRACK_HEIGHT.toPx()
    val left = radius
    val right = size.width - radius
    val x = left + (right - left) * (if (rtl) 1f - fraction else fraction)
    val cy = size.height / 2
    val corner = CornerRadius(track / 2)
    drawRoundRect(theme.onPanel.copy(alpha = TRACK_ALPHA), Offset(left, cy - track / 2), Size(right - left, track), corner)
    val activeStart = if (rtl) x else left
    val activeEnd = if (rtl) right else x
    drawRoundRect(theme.accent, Offset(activeStart, cy - track / 2), Size(activeEnd - activeStart, track), corner)
    if (steps in 1..MAX_DRAWN_TICKS) {
        for (i in 0..steps + 1) {
            drawCircle(
                theme.panel.copy(alpha = TICK_ALPHA),
                track / 2,
                Offset(left + (right - left) * i / (steps + 1), cy),
            )
        }
    }
    if (halo > 0f) drawCircle(theme.accent.copy(alpha = HALO_ALPHA * halo), HALO_SIZE.toPx() / 2, Offset(x, cy))
    if (focused) drawCircle(theme.accent, radius + FOCUS_GAP.toPx(), Offset(x, cy), style = Stroke(FOCUS_STROKE.toPx()))
    drawCircle(theme.accent, radius * (1f + THUMB_GROW * halo), Offset(x, cy))
    drawCircle(theme.panel, radius * INNER_DOT, Offset(x, cy))
}

private val SLIDER_HEIGHT = 44.dp
private val TRACK_HEIGHT = 4.dp
private val THUMB_SIZE = 20.dp
private val HALO_SIZE = 40.dp
private val FOCUS_GAP = 4.dp
private val FOCUS_STROKE = 2.dp
private const val TRACK_ALPHA = 0.18f
private const val TICK_ALPHA = 0.6f
private const val HALO_ALPHA = 0.18f
private const val THUMB_GROW = 0.1f
private const val INNER_DOT = 0.35f
private const val HALO_FADE_MS = 200
private const val KEY_STEP_FRACTION = 0.01f
private const val MAX_DRAWN_TICKS = 30
