package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 손잡이 두 개로 구간을 고르는 글래스 슬라이더로, [GlassSlider]와 같은 모양이다. 두 손잡이는 서로를 넘지 않는다.
 * 손잡이를 잡거나 키보드 포커스를 주면 손잡이가 커지고 위에 값 말풍선이 뜬다. 말풍선은 슬라이더 영역 위로 약 24dp 넘쳐 그려지므로
 * 위쪽에 그만큼 여백을 둔다. 가로로 움직이기 시작해야 드래그로 보므로 세로 스크롤 목록 안에서도 스크롤을 막지 않는다.
 * 트랙을 탭하면 더 가까운 손잡이가 그 값으로 이동한다. 두 손잡이가 겹쳐 있으면 드래그 방향으로 손잡이를 고른다.
 *
 * TalkBack과 키보드에서는 손잡이마다 따로 포커스를 받고, 방향키나 값 설정 동작으로 그 손잡이만 움직인다.
 *
 * ```kotlin
 * var price by remember { mutableStateOf(20f..80f) }
 * GlassRangeSlider(price, { price = it }, valueRange = 0f..100f, steps = 19, valueLabel = { "${it.roundToInt()}만 원" })
 * ```
 *
 * @param range 고른 구간. [valueRange] 밖은 가까운 끝으로 맞춰 그린다.
 * @param onRangeChange 새 구간을 받는다. 시작은 언제나 끝보다 크지 않다.
 * @param valueRange 값 범위. 끝이 시작보다 크지 않으면 [IllegalArgumentException]을 던진다.
 * @param steps 양 끝 사이의 눈금 수. 0이면 연속 값이고, 1 이상이면 눈금 위치에만 멈춘다. 음수면 [IllegalArgumentException]을 던진다.
 * @param enabled `false`면 터치, 키보드, TalkBack으로 값을 바꿀 수 없고 채워진 구간을 흐리게 그린다.
 * @param onRangeChangeFinished 손을 떼거나 키보드, TalkBack으로 값을 바꾼 뒤 한 번 호출된다.
 * @param valueLabel 말풍선과 TalkBack이 읽는 값 문구. 기본값은 기기 언어 형식에 소수 둘째 자리까지다.
 * @param activeColor 채워진 구간과 말풍선의 색
 */
@Composable
public fun GlassRangeSlider(
    range: ClosedFloatingPointRange<Float>,
    onRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onRangeChangeFinished: (() -> Unit)? = null,
    valueLabel: (Float) -> String = DefaultValueLabel,
    activeColor: Color = Color(ACCENT),
    style: GlassStyle = LocalGlassStyle.current,
) {
    require(steps >= 0) { "steps는 0 이상이어야 합니다." }
    require(valueRange.endInclusive > valueRange.start) { "valueRange의 끝이 시작보다 커야 합니다." }
    val model = RangeModel(valueRange, steps)
    val start = range.start.coerceIn(valueRange)
    val end = range.endInclusive.coerceIn(valueRange).coerceAtLeast(start)
    val latestRange by rememberUpdatedState(start..end)
    val latestChange by rememberUpdatedState(onRangeChange)
    val latestFinished by rememberUpdatedState(onRangeChangeFinished)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val thumbs = remember { RangeThumbs() }
    val move: (Boolean, Float) -> Unit = { isStart, value ->
        val current = latestRange
        val snapped = model.snap(value)
        val next = if (isStart) {
            snapped.coerceAtMost(current.endInclusive)..current.endInclusive
        } else {
            current.start..snapped.coerceAtLeast(current.start)
        }
        thumbs.startOnTop = isStart
        if (next != current) latestChange(next)
    }
    val finish: () -> Unit = { latestFinished?.invoke() }
    val fill = if (enabled) activeColor else style.onGlass.copy(alpha = DISABLED_ALPHA)
    val track = style.onGlass.copy(alpha = TRACK_ALPHA)
    val startFraction = model.fraction(start)
    val endFraction = model.fraction(end)
    val thumbSpec = ThumbShared(model, enabled, rtl, fill, valueLabel)
    // 제스처 입력은 다시 시작하지 않고, 누를 때마다 지금의 범위, 눈금, 방향을 읽는다.
    val gesture = rememberUpdatedState(RangeGesture(rtl, { latestRange }, model, move, finish))
    Layout(
        content = {
            RangeThumb(stringResource(R.string.kinetica_ui_range_slider_start), start, thumbs.start, thumbSpec, scope, {
                move(true, it)
            }, finish)
            RangeThumb(stringResource(R.string.kinetica_ui_range_slider_end), end, thumbs.end, thumbSpec, scope, {
                move(false, it)
            }, finish)
        },
        modifier = modifier
            .fillMaxWidth()
            .height(SLIDER_HEIGHT)
            .then(
                if (enabled) {
                    Modifier.rangeGesture(gesture, thumbs, scope)
                } else {
                    Modifier
                },
            )
            .drawBehind {
                val grab = maxOf(thumbs.start.value, thumbs.end.value)
                drawRangeTrack(RangeLook(startFraction, endFraction, grab, rtl), fill, track)
            },
        measurePolicy = thumbPlacement(startFraction, endFraction, rtl, thumbs),
    )
}

/** 두 손잡이를 값 위치에 놓는다. 마지막으로 움직인 손잡이를 위에 놓아 겹쳤을 때 그 손잡이를 다시 잡을 수 있게 한다. */
private fun thumbPlacement(startFraction: Float, endFraction: Float, rtl: Boolean, thumbs: RangeThumbs) =
    MeasurePolicy { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val thumbWidth = THUMB_TOUCH.roundToPx()
        val inset = THUMB_WIDTH.toPx() / 2
        val usable = width - inset * 2
        val placeables = measurables.map { it.measure(Constraints.fixed(thumbWidth, height)) }
        layout(width, height) {
            listOf(startFraction, endFraction).forEachIndexed { index, fraction ->
                val center = inset + usable * fraction
                val x = if (rtl) width - center else center
                val onTop = (index == 0) == thumbs.startOnTop
                placeables[index].place((x - thumbWidth / 2f).roundToInt(), 0, zIndex = if (onTop) 1f else 0f)
            }
        }
    }

/** 값과 비율(0..1) 사이의 변환과 눈금 맞춤. */
private class RangeModel(val valueRange: ClosedFloatingPointRange<Float>, val steps: Int) {
    private val span = valueRange.endInclusive - valueRange.start

    // 키보드 방향키 한 번에 움직이는 비율. 눈금이 있으면 한 칸, 없으면 범위의 5%.
    val keyFraction: Float = if (steps > 0) 1f / (steps + 1) else KEY_FRACTION

    fun fraction(value: Float): Float = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    fun valueAt(fraction: Float): Float = snap(valueRange.start + fraction.coerceIn(0f, 1f) * span)

    fun snap(value: Float): Float {
        val raw = fraction(value)
        val snapped = if (steps == 0) raw else (raw * (steps + 1)).roundToInt().toFloat() / (steps + 1)
        return valueRange.start + snapped * span
    }
}

/** 두 손잡이의 잡은 정도(0..1)와 겹쳤을 때 위에 놓을 손잡이. */
@Stable
private class RangeThumbs {
    val start = Animatable(0f)
    val end = Animatable(0f)
    var startOnTop by mutableStateOf(false)
}

private class ThumbShared(
    val model: RangeModel,
    val enabled: Boolean,
    val rtl: Boolean,
    val fill: Color,
    val valueLabel: (Float) -> String,
)

@Composable
private fun RangeThumb(
    name: String,
    value: Float,
    grab: Animatable<Float, AnimationVector1D>,
    shared: ThumbShared,
    scope: CoroutineScope,
    onValue: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val label = shared.valueLabel(value)
    val model = shared.model
    Box(
        Modifier
            .semantics {
                contentDescription = name
                stateDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, model.valueRange, model.steps)
                if (!shared.enabled) disabled()
                setProgress { target ->
                    if (!shared.enabled) return@setProgress false
                    onValue(target.coerceIn(model.valueRange))
                    onFinished()
                    true
                }
            }
            .onFocusChanged { focus -> scope.launch { grab.animateTo(if (focus.isFocused) 1f else 0f, GrabSpring) } }
            .focusable(shared.enabled)
            .onKeyEvent { event ->
                val delta = if (shared.enabled && event.type == KeyEventType.KeyDown) rangeKeyStep(event.key, shared.rtl) else null
                if (delta != null) {
                    onValue(model.valueAt(model.fraction(value) + delta * model.keyFraction))
                    onFinished()
                }
                delta != null
            }
            .drawWithCache {
                val text = measurer.measure(label, TextStyle(color = Color.White, fontSize = BUBBLE_TEXT, fontWeight = FontWeight.SemiBold))
                onDrawBehind {
                    val amount = grab.value
                    drawThumb(amount)
                    if (amount > 0f) drawBubble(amount, shared.fill, text)
                }
            },
    )
}

private fun DrawScope.drawThumb(grab: Float) {
    val thumbWidth = THUMB_WIDTH.toPx() * (1f + THUMB_GROW * grab)
    val thumbHeight = THUMB_HEIGHT.toPx() * (1f + THUMB_GROW * grab)
    val topLeft = Offset((size.width - thumbWidth) / 2, (size.height - thumbHeight) / 2)
    val radius = CornerRadius(thumbHeight / 2)
    drawRoundRect(Color.Black.copy(alpha = THUMB_SHADOW), topLeft + Offset(0f, THUMB_DROP.toPx()), Size(thumbWidth, thumbHeight), radius)
    // 잡은 동안에는 손잡이를 반투명하게 해 뒤의 트랙이 비쳐 보이게 한다.
    drawRoundRect(Color.White.copy(alpha = 1f - THUMB_CLEAR * grab), topLeft, Size(thumbWidth, thumbHeight), radius)
}

/** 손잡이 위에 값 말풍선을 그린다. 잡은 정도만큼 손잡이 쪽에서 커지며 나타난다. */
private fun DrawScope.drawBubble(grab: Float, color: Color, text: TextLayoutResult) {
    val width = text.size.width + BUBBLE_PAD_X.toPx() * 2
    val height = text.size.height + BUBBLE_PAD_Y.toPx() * 2
    val thumbTop = (size.height - THUMB_HEIGHT.toPx() * (1f + THUMB_GROW * grab)) / 2
    val anchor = Offset(size.width / 2, thumbTop - BUBBLE_GAP.toPx())
    val topLeft = Offset(anchor.x - width / 2, anchor.y - height)
    scale(grab, anchor) {
        drawRoundRect(color, topLeft, Size(width, height), CornerRadius(height / 2), alpha = grab)
        drawText(text, topLeft = topLeft + Offset(BUBBLE_PAD_X.toPx(), BUBBLE_PAD_Y.toPx()), alpha = grab)
    }
}

/**
 * 트랙 탭과 드래그. 가로로 터치 슬롭을 넘어야 드래그로 보고, 세로로 먼저 움직이면 부모 스크롤에 넘긴다.
 * [current]는 지금 구간을, [move]는 (시작 손잡이인지, 새 값)을 받는다.
 */
private class RangeGesture(
    val rtl: Boolean,
    val current: () -> ClosedFloatingPointRange<Float>,
    val model: RangeModel,
    val move: (Boolean, Float) -> Unit,
    val finish: () -> Unit,
) {
    /** [fraction] 위치에서 잡을 손잡이가 시작 손잡이인지. 두 손잡이가 겹치면 [forward](값이 커지는 쪽) 드래그는 끝 손잡이를 잡는다. */
    fun pickStart(fraction: Float, forward: Boolean?): Boolean {
        val range = current()
        val toStart = abs(fraction - model.fraction(range.start))
        val toEnd = abs(fraction - model.fraction(range.endInclusive))
        return if (abs(toStart - toEnd) <
            TIE
        ) {
            forward == false || (forward == null && fraction < model.fraction(range.start))
        } else {
            toStart < toEnd
        }
    }
}

private fun Modifier.rangeGesture(latest: State<RangeGesture>, thumbs: RangeThumbs, scope: CoroutineScope): Modifier =
    pointerInput(latest) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val gesture = latest.value
            val width = size.width.toFloat()
            val inset = THUMB_WIDTH.toPx() / 2
            val usable = (width - inset * 2).coerceAtLeast(1f)
            fun fractionAt(x: Float): Float = (((if (gesture.rtl) width - x else x) - inset) / usable).coerceIn(0f, 1f)
            var overSlop = 0f
            val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                change.consume()
                overSlop = over
            }
            if (drag == null) {
                // 움직이지 않고 뗐으면 탭이다. 세로 스크롤이 가져갔으면(소비됨) 아무것도 하지 않는다.
                val up = currentEvent.changes.firstOrNull { it.id == down.id }
                if (up != null && !up.pressed && !up.isConsumed) {
                    val fraction = fractionAt(up.position.x)
                    gesture.move(gesture.pickStart(fraction, null), gesture.model.valueAt(fraction))
                    gesture.finish()
                }
                return@awaitEachGesture
            }
            val isStart = gesture.pickStart(fractionAt(down.position.x), (overSlop > 0f) != gesture.rtl)
            val grab = if (isStart) thumbs.start else thumbs.end
            scope.launch { grab.animateTo(1f, GrabSpring) }
            gesture.move(isStart, gesture.model.valueAt(fractionAt(drag.position.x)))
            horizontalDrag(drag.id) { change ->
                gesture.move(isStart, gesture.model.valueAt(fractionAt(change.position.x)))
                change.consume()
            }
            scope.launch { grab.animateTo(0f, GrabSpring) }
            gesture.finish()
        }
    }

/** 방향키를 값 변화 방향(+1 증가, -1 감소)으로 바꾼다. RTL에서는 좌우가 뒤집힌다. 슬라이더 키가 아니면 `null`. */
private fun rangeKeyStep(key: Key, rtl: Boolean): Int? = when (key) {
    Key.DirectionRight -> if (rtl) -1 else 1
    Key.DirectionLeft -> if (rtl) 1 else -1
    Key.DirectionUp -> 1
    Key.DirectionDown -> -1
    else -> null
}

/** 그리기에 쓰는 트랙 상태. 두 손잡이의 비율, 잡은 정도(0..1), RTL 여부를 담는다. */
private class RangeLook(val from: Float, val to: Float, val grab: Float, val rtl: Boolean)

private fun DrawScope.drawRangeTrack(look: RangeLook, active: Color, track: Color) {
    val inset = THUMB_WIDTH.toPx() / 2
    val trackHeight = TRACK_HEIGHT.toPx() * (1f + TRACK_GROW * look.grab)
    val top = (size.height - trackHeight) / 2
    val radius = CornerRadius(trackHeight / 2)
    drawRoundRect(track, Offset(0f, top), Size(size.width, trackHeight), radius)
    val usable = size.width - inset * 2
    val a = inset + usable * look.from
    val b = inset + usable * look.to
    val left = if (look.rtl) size.width - b else a
    // 두 손잡이가 겹쳐도 채워진 구간이 손잡이 밑에 보이도록 트랙 높이만큼은 남긴다.
    val width = (b - a).coerceAtLeast(trackHeight)
    drawRoundRect(active, Offset(left - (width - (b - a)) / 2, top), Size(width, trackHeight), radius)
}

private val DefaultValueLabel: (Float) -> String = { value ->
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(value)
}
private val GrabSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy)

private val SLIDER_HEIGHT = 44.dp
private val TRACK_HEIGHT = 6.dp
private const val TRACK_GROW = 1f
private const val TRACK_ALPHA = 0.16f
private val THUMB_WIDTH = 36.dp
private val THUMB_HEIGHT = 24.dp
private val THUMB_TOUCH = 48.dp
private val THUMB_DROP = 1.dp
private const val THUMB_GROW = 0.25f
private const val THUMB_SHADOW = 0.14f
private const val THUMB_CLEAR = 0.35f
private val BUBBLE_TEXT = 13.sp
private val BUBBLE_PAD_X = 10.dp
private val BUBBLE_PAD_Y = 4.dp
private val BUBBLE_GAP = 6.dp
private const val KEY_FRACTION = 0.05f
private const val TIE = 0.001f
private const val DISABLED_ALPHA = 0.3f
private const val ACCENT = 0xFF0A84FF
