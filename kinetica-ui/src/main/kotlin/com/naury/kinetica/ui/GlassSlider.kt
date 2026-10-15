package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 유리 슬라이더(iOS 26 음량 막대 느낌). 잡으면 막대가 굵어지고 손잡이가 커지며, 끝을 넘어 끌면 막대가 고무줄처럼 늘어났다가
 * 놓으면 튕겨 돌아온다. 막대 아무 곳이나 눌러도 그 값으로 간다. 화면 읽기는 값 범위를 읽고 값을 바꿀 수 있다.
 *
 * @param value [valueRange] 안의 값. 밖이면 가까운 끝으로 맞춰 그린다.
 * @param steps 양 끝 사이 눈금 수. 0이면 연속 값이고, 1 이상이면 그 눈금에만 멈춘다.
 * @param onValueChangeFinished 손을 뗄 때 한 번 부른다.
 * @param activeColor 채워진 부분 색
 */
@Composable
public fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    activeColor: Color = Color(ACCENT),
    style: GlassStyle = LocalGlassStyle.current,
) {
    require(steps >= 0) { "눈금 수는 0 이상이어야 합니다." }
    require(valueRange.endInclusive > valueRange.start) { "범위의 끝이 시작보다 커야 합니다." }
    val latestChange by rememberUpdatedState(onValueChange)
    val latestFinished by rememberUpdatedState(onValueChangeFinished)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val grab = remember { Animatable(0f) }
    val stretch = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(0f) }
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
                setProgress { target ->
                    val raw = (target.coerceIn(valueRange) - valueRange.start) / (valueRange.endInclusive - valueRange.start)
                    latestChange(valueAt(raw))
                    latestFinished?.invoke()
                    true
                }
            }
            .pointerInput(rtl, steps, valueRange) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    width = size.width.toFloat()
                    val inset = THUMB_WIDTH.toPx() / 2
                    val usable = (width - inset * 2).coerceAtLeast(1f)
                    fun rawAt(x: Float): Float = ((if (rtl) width - x else x) - inset) / usable
                    scope.launch { grab.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                    latestChange(valueAt(rawAt(down.position.x).coerceIn(0f, 1f)))
                    down.consume()
                    drag(down.id) { change ->
                        val raw = rawAt(change.position.x)
                        latestChange(valueAt(raw.coerceIn(0f, 1f)))
                        // 끝을 넘은 만큼 줄어드는 비율로 늘어나 고무줄처럼 버틴다.
                        val over = (if (raw < 0f) raw else (raw - 1f).coerceAtLeast(0f)) * usable
                        scope.launch { stretch.snapTo(over / (abs(over) + RUBBER_SOFTNESS * density)) }
                        change.consume()
                    }
                    scope.launch { grab.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                    scope.launch {
                        stretch.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                    }
                    latestFinished?.invoke()
                }
            }
            .drawBehind {
                drawSlider(SliderLook(fraction, grab.value, stretch.value, rtl), activeColor, style.onGlass.copy(alpha = TRACK_ALPHA))
            },
    )
}

/** 그릴 때 쓰는 슬라이더 상태: 채운 비율, 잡은 정도(0..1), 끝 너머로 끈 정도(음수면 시작 쪽), 오른쪽에서 왼쪽인지. */
private class SliderLook(val fraction: Float, val grab: Float, val stretch: Float, val rtl: Boolean)

private fun DrawScope.drawSlider(look: SliderLook, active: Color, track: Color) {
    val fraction = look.fraction
    val grab = look.grab
    val stretch = look.stretch
    val rtl = look.rtl
    val thumbWidth = THUMB_WIDTH.toPx() * (1f + THUMB_GROW * grab)
    val thumbHeight = THUMB_HEIGHT.toPx() * (1f + THUMB_GROW * grab)
    val inset = THUMB_WIDTH.toPx() / 2
    val trackHeight = TRACK_HEIGHT.toPx() * (1f + TRACK_GROW * grab)
    val top = (size.height - trackHeight) / 2
    // 넘친 쪽 끝을 기준으로 가로로 늘리고 세로는 살짝 가늘게 한다(고무줄).
    val pull = abs(stretch) * RUBBER_MAX
    val pivot = if ((stretch < 0f) != rtl) size.width else 0f
    scale(1f + pull, 1f - pull * RUBBER_THIN, Offset(pivot, size.height / 2)) {
        val radius = CornerRadius(trackHeight / 2)
        drawRoundRect(track, Offset(0f, top), Size(size.width, trackHeight), radius)
        val filled = inset + (size.width - inset * 2) * fraction
        val start = if (rtl) size.width - filled else 0f
        drawRoundRect(active, Offset(start, top), Size(filled, trackHeight), radius)
        val center = if (rtl) size.width - filled else filled
        val thumbLeft = center - thumbWidth / 2
        val thumbTop = (size.height - thumbHeight) / 2
        val thumbRadius = CornerRadius(thumbHeight / 2)
        drawRoundRect(
            Color.Black.copy(alpha = THUMB_SHADOW),
            Offset(thumbLeft, thumbTop + THUMB_DROP.toPx()),
            Size(thumbWidth, thumbHeight),
            thumbRadius,
        )
        // 잡으면 손잡이가 유리처럼 비쳐 뒤 막대가 보인다.
        drawRoundRect(
            Color.White.copy(alpha = 1f - THUMB_CLEAR * grab),
            Offset(thumbLeft, thumbTop),
            Size(thumbWidth, thumbHeight),
            thumbRadius,
        )
    }
}

private val SLIDER_HEIGHT = 44.dp
private val TRACK_HEIGHT = 6.dp
private const val TRACK_GROW = 1f
private const val TRACK_ALPHA = 0.16f
private val THUMB_WIDTH = 36.dp
private val THUMB_HEIGHT = 24.dp
private val THUMB_DROP = 1.dp
private const val THUMB_GROW = 0.25f
private const val THUMB_SHADOW = 0.14f
private const val THUMB_CLEAR = 0.35f
private const val RUBBER_SOFTNESS = 120f
private const val RUBBER_MAX = 0.06f
private const val RUBBER_THIN = 0.8f
private const val ACCENT = 0xFF0A84FF
