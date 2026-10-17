package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 볼륨 조절 같은 곳에 어울리는 슬라이더. 잡으면 트랙이 굵어지고 손잡이가 커진다. 끝을 넘어서 드래그하면
 * 트랙이 고무줄처럼 늘어났다가 손을 떼면 튕겨 돌아온다. 트랙을 탭하면 그 값으로 이동한다. 가로로 움직이기 시작해야 드래그로 보므로
 * 세로 스크롤 목록 안에 두어도 스크롤을 막지 않는다. 키보드 방향키로 조절할 수 있고, TalkBack은 값과 범위를 읽고 값을 바꿀 수 있다.
 *
 * @param value [valueRange] 안의 값. 범위를 벗어나면 가까운 끝 값으로 그린다.
 * @param valueRange 값 범위. 끝이 시작보다 크지 않으면 [IllegalArgumentException]을 던진다.
 * @param steps 양 끝 사이의 눈금 수. 0이면 연속 값이고, 1 이상이면 눈금 위치에만 멈춘다. 음수면 [IllegalArgumentException]을 던진다.
 * @param enabled `false`면 터치·키보드·TalkBack으로 값을 바꿀 수 없고 채워진 구간을 흐리게 그린다.
 * @param onValueChangeFinished 손을 떼거나 키보드·TalkBack으로 값을 바꾼 뒤 한 번 호출된다.
 * @param accent 채워진 구간의 색. 기본값은 [KineticaTheme]의 `accent`다.
 * @param thumb 손잡이 모양. 기본값은 [KineticaDesign.controls]의 [KineticaControls.sliderThumb]다.
 */
@Composable
public fun KSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    accent: Color = KineticaTheme.colors.accent,
    surface: SurfaceStyle = KineticaTheme.surface,
    thumb: KSliderThumb = KineticaTheme.design.controls.sliderThumb,
) {
    require(steps >= 0) { "steps는 0 이상이어야 합니다." }
    require(valueRange.endInclusive > valueRange.start) { "valueRange의 끝이 시작보다 커야 합니다." }
    val paint = sliderPaint(if (enabled) accent else surface.contentColor.copy(alpha = DISABLED_ALPHA), surface)
    val latestChange by rememberUpdatedState(onValueChange)
    val latestFinished by rememberUpdatedState(onValueChangeFinished)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val grab = remember { Animatable(0f) }
    val stretch = remember { Animatable(0f) }
    // 키보드 방향키 한 번에 움직이는 비율. 눈금이 있으면 한 칸, 없으면 범위의 5%.
    val stepFraction = if (steps > 0) 1f / (steps + 1) else KEY_FRACTION
    val fraction = ((value.coerceIn(valueRange) - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    fun valueAt(raw: Float): Float {
        val snapped = if (steps == 0) raw else (raw * (steps + 1)).roundToInt().toFloat() / (steps + 1)
        return valueRange.start + snapped * (valueRange.endInclusive - valueRange.start)
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(SLIDER_HEIGHT)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange, steps)
                if (!enabled) disabled()
                setProgress { target ->
                    if (!enabled) return@setProgress false
                    val raw = (target.coerceIn(valueRange) - valueRange.start) / (valueRange.endInclusive - valueRange.start)
                    latestChange(valueAt(raw))
                    latestFinished?.invoke()
                    true
                }
            }
            // onFocusChanged는 focusable보다 앞에 있어야 그 포커스 변화를 받는다.
            .onFocusChanged { focused -> scope.launch { grab.animateTo(if (focused.isFocused) 1f else 0f) } }
            .focusable(enabled)
            .onKeyEvent { event ->
                val delta = if (enabled && event.type == KeyEventType.KeyDown) keyStep(event.key, rtl) else null
                if (delta != null) {
                    latestChange(valueAt((fraction + delta * stepFraction).coerceIn(0f, 1f)))
                    latestFinished?.invoke()
                }
                delta != null
            }
            .then(
                if (enabled) {
                    Modifier.sliderGesture(
                        SliderGesture(rtl, steps, valueRange, ::valueAt, {
                            latestChange(it)
                        }, { latestFinished?.invoke() }),
                        scope,
                        grab,
                        stretch,
                    )
                } else {
                    Modifier
                },
            )
            .drawBehind { drawSlider(SliderLook(fraction, grab.value, stretch.value, rtl, thumb), paint) },
    )
}

/**
 * 가로로 터치 슬롭을 넘으면 드래그를 시작하고, 움직이지 않고 떼면 탭으로 본다. 세로로 먼저 움직이면 부모 스크롤에 넘긴다.
 */
private class SliderGesture(
    val rtl: Boolean,
    val steps: Int,
    val valueRange: ClosedFloatingPointRange<Float>,
    val valueAt: (Float) -> Float,
    val onChange: (Float) -> Unit,
    val onFinished: () -> Unit,
)

private fun Modifier.sliderGesture(
    gesture: SliderGesture,
    scope: CoroutineScope,
    grab: Animatable<Float, *>,
    stretch: Animatable<Float, *>,
): Modifier = pointerInput(gesture.rtl, gesture.steps, gesture.valueRange) {
    val rtl = gesture.rtl
    val valueAt = gesture.valueAt
    val onChange = gesture.onChange
    val onFinished = gesture.onFinished
    awaitEachGesture {
        val down = awaitFirstDown()
        val width = size.width.toFloat()
        val inset = THUMB_WIDTH.toPx() / 2
        val usable = (width - inset * 2).coerceAtLeast(1f)
        fun rawAt(x: Float): Float = ((if (rtl) width - x else x) - inset) / usable
        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
        if (start == null) {
            // 움직이지 않고 뗐으면 탭이다. 세로 스크롤이 가져갔으면(소비됨) 아무것도 하지 않는다.
            val up = currentEvent.changes.firstOrNull { it.id == down.id }
            if (up != null && !up.pressed && !up.isConsumed) {
                onChange(valueAt(rawAt(up.position.x).coerceIn(0f, 1f)))
                onFinished()
            }
            return@awaitEachGesture
        }
        scope.launch { grab.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        onChange(valueAt(rawAt(start.position.x).coerceIn(0f, 1f)))
        horizontalDrag(start.id) { change ->
            val raw = rawAt(change.position.x)
            onChange(valueAt(raw.coerceIn(0f, 1f)))
            // 끝을 넘은 거리가 커질수록 늘어나는 비율을 줄여 고무줄처럼 버티게 한다.
            val over = (if (raw < 0f) raw else (raw - 1f).coerceAtLeast(0f)) * usable
            scope.launch { stretch.snapTo(over / (abs(over) + RUBBER_SOFTNESS * density)) }
            change.consume()
        }
        scope.launch { grab.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        scope.launch { stretch.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
        onFinished()
    }
}

/** 슬라이더 색. [active]는 채워진 구간과 막대 손잡이 색이다. */
@Composable
internal fun sliderPaint(active: Color, surface: SurfaceStyle): ThumbPaint = ThumbPaint(
    active,
    surface.contentColor,
    surface.fillColor.copy(alpha = 1f),
    surface.isDark,
    KineticaTheme.design.borderWidth,
    surface.contentColor.copy(alpha = TRACK_ALPHA),
)

/** 방향키를 값 변화 방향(+1 증가, -1 감소)으로 바꾼다. RTL에서는 좌우가 뒤집힌다. 슬라이더 키가 아니면 `null`. */
private fun keyStep(key: Key, rtl: Boolean): Int? = when (key) {
    Key.DirectionRight -> if (rtl) -1 else 1
    Key.DirectionLeft -> if (rtl) 1 else -1
    Key.DirectionUp -> 1
    Key.DirectionDown -> -1
    else -> null
}

/** 그리기에 쓰는 슬라이더 상태. 채운 비율, 잡은 정도(0..1), 끝을 넘어 늘어난 정도(음수면 시작 쪽), RTL 여부를 담는다. */
private class SliderLook(val fraction: Float, val grab: Float, val stretch: Float, val rtl: Boolean, val thumb: KSliderThumb)

private fun DrawScope.drawSlider(look: SliderLook, paint: ThumbPaint) {
    val fraction = look.fraction
    val grab = look.grab
    val stretch = look.stretch
    val rtl = look.rtl
    val inset = THUMB_WIDTH.toPx() / 2
    val trackHeight = TRACK_HEIGHT.toPx() * (1f + TRACK_GROW * grab)
    // 고무줄처럼 보이도록 넘친 쪽 반대편 끝을 고정점으로 가로로 늘리고 세로는 살짝 가늘게 한다.
    val pull = abs(stretch) * RUBBER_MAX
    val pivot = if ((stretch < 0f) != rtl) size.width else 0f
    scale(1f + pull, 1f - pull * RUBBER_THIN, Offset(pivot, size.height / 2)) {
        val filled = inset + (size.width - inset * 2) * fraction
        val center = if (rtl) size.width - filled else filled
        drawSliderTrack(look.thumb, center, trackHeight, paint, rtl)
        drawSliderThumb(look.thumb, Offset(center, size.height / 2), grab, paint)
    }
}

private val SLIDER_HEIGHT = 44.dp
private val TRACK_HEIGHT = 6.dp
private const val TRACK_GROW = 1f
private const val TRACK_ALPHA = 0.16f
private val THUMB_WIDTH = 36.dp
private const val RUBBER_SOFTNESS = 120f
private const val RUBBER_MAX = 0.06f
private const val RUBBER_THIN = 0.8f
private const val KEY_FRACTION = 0.05f
private const val DISABLED_ALPHA = 0.3f
