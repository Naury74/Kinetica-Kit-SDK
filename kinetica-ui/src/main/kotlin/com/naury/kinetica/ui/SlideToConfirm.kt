package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * '밀어서 결제' 같은 글래스 캡슐. 손잡이를 끝까지 밀면 햅틱과 함께 확정되고, 손잡이의 화살표가 체크로 바뀌며 캡슐이 [color]로 채워진다.
 * 끝까지 밀지 않고 놓으면 손잡이가 튕기며 처음 자리로 돌아온다. 가운데 글자는 빛이 지나가듯 반짝이고, 손잡이가 지나간 만큼 흐려진다.
 * RTL에서는 오른쪽에서 왼쪽으로 민다.
 *
 * TalkBack에서는 [text]를 이름으로 하는 버튼으로 읽히고, 두 번 탭하면 확정된다. 터치로 누르기만 해서는 확정되지 않는다.
 *
 * ```kotlin
 * var paid by remember { mutableStateOf(false) }
 * GlassSlideToConfirm("밀어서 결제", onConfirm = { paid = true }, confirmed = paid)
 * ```
 *
 * @param onConfirm 확정되는 순간 한 번 부른다.
 * @param confirmed 확정된 상태로 보일지 여부. 확정 뒤 이 값을 `true`에서 `false`로 바꾸면 손잡이가 처음 자리로 돌아와 다시 밀 수 있다.
 *   계속 `false`로 두면 한 번 확정한 뒤로는 확정된 모습을 유지한다.
 * @param color 확정되었을 때 캡슐을 채우는 색
 */
@Composable
public fun GlassSlideToConfirm(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmed: Boolean = false,
    color: Color = Color(CONFIRM_GREEN),
    style: GlassStyle = LocalGlassStyle.current,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val slide = remember { SlideState(confirmed) }
    LaunchedEffect(confirmed) { slide.follow(confirmed) }
    LaunchedEffect(slide.done) { slide.check.animateTo(if (slide.done) 1f else 0f, SNAP_SPEC) }
    val confirm = {
        if (!slide.done) {
            slide.done = true
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            scope.launch { slide.position.animateTo(1f, SNAP_SPEC) }
            onConfirm()
        }
    }
    val base = with(LocalDensity.current) { (THUMB_INSET * 2 + THUMB_SIZE).toPx() }
    val confirmedLabel = stringResource(R.string.kinetica_ui_confirmed)
    Box(
        modifier
            .fillMaxWidth()
            .height(HEIGHT)
            .glass(style, CircleShape)
            .onSizeChanged { slide.travel = (it.width - base).coerceAtLeast(1f) }
            .drawBehind { drawTrail(slide, color, base, rtl) }
            .slideSemantics(text, slide.done, confirmedLabel, confirm)
            .draggable(
                rememberDraggableState { delta ->
                    if (slide.dragBy(delta, scope)) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                },
                Orientation.Horizontal,
                enabled = !slide.done,
                reverseDirection = rtl,
                onDragStopped = { velocity -> if (!slide.release(velocity)) confirm() },
            ),
    ) {
        ShimmerText(
            text,
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = HEIGHT)
                .graphicsLayer { alpha = (1f - slide.position.value * TEXT_FADE).coerceIn(0f, 1f) },
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            color = style.onGlass.copy(alpha = TEXT_ALPHA),
        )
        Thumb(
            color,
            slide.check,
            Modifier
                .align(Alignment.CenterStart)
                .padding(THUMB_INSET)
                .offset { IntOffset((slide.travel * slide.position.value).roundToInt(), 0) },
        )
    }
}

/** 손잡이 위치(0..1)와 확정 상태. */
@Stable
private class SlideState(confirmed: Boolean) {
    val position = Animatable(if (confirmed) 1f else 0f)
    val check = Animatable(if (confirmed) 1f else 0f)
    var done by mutableStateOf(confirmed)
    var travel by mutableFloatStateOf(1f)

    /** 바깥에서 준 확정 값을 따른다. 확정이 풀리면 손잡이를 처음 자리로 돌린다. */
    suspend fun follow(confirmed: Boolean) {
        if (confirmed) {
            done = true
            position.animateTo(1f, SNAP_SPEC)
        } else if (done) {
            done = false
            position.animateTo(0f, RETURN_SPEC)
        }
    }

    /** 손을 놓았을 때 확정 지점 앞이면 튕기며 돌아가고 `true`를 돌려준다. 확정 지점을 넘었으면 `false`다. */
    suspend fun release(velocity: Float): Boolean {
        if (position.value >= CONFIRM_AT) return false
        position.animateTo(0f, RETURN_SPEC, velocity / travel)
        return true
    }

    /** 손잡이를 [delta](px)만큼 옮긴다. 확정 지점을 막 넘었으면 `true`를 돌려준다. */
    fun dragBy(delta: Float, scope: CoroutineScope): Boolean {
        val before = position.value
        val next = (before + delta / travel).coerceIn(0f, 1f)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { position.snapTo(next) }
        return before < CONFIRM_AT && next >= CONFIRM_AT
    }
}

/** TalkBack에서는 [text]를 이름으로 하는 버튼으로 읽히고, 확정된 뒤에는 상태를 알리며 누를 수 없다. */
private fun Modifier.slideSemantics(text: String, done: Boolean, confirmedLabel: String, confirm: () -> Unit): Modifier =
    clearAndSetSemantics {
        contentDescription = text
        role = Role.Button
        if (done) {
            stateDescription = confirmedLabel
            disabled()
        } else {
            onClick(text) {
                confirm()
                true
            }
        }
    }

/** 손잡이가 지나간 자리를 [color]로 옅게 칠하고, 확정되면 캡슐 전체를 채운다. [base]는 손잡이와 양쪽 여백을 더한 너비(px)다. */
private fun DrawScope.drawTrail(slide: SlideState, color: Color, base: Float, rtl: Boolean) {
    val progress = slide.position.value
    val level = slide.check.value
    val width = lerp(base + slide.travel * progress, size.width, level)
    val alpha = lerp(TRAIL_ALPHA * (progress * 2f).coerceAtMost(1f), 1f, level)
    drawRoundRect(
        color.copy(alpha = color.alpha * alpha),
        topLeft = Offset(if (rtl) size.width - width else 0f, 0f),
        size = Size(width, size.height),
        cornerRadius = CornerRadius(size.height / 2),
    )
}

/** 흰 손잡이. 화살표가 [check] 진행률에 따라 작아지며 사라지고, 체크 표시가 그려지듯 나타난다. 경로는 크기가 바뀔 때만 만든다. */
@Composable
private fun Thumb(color: Color, check: Animatable<Float, *>, modifier: Modifier) {
    Box(
        modifier
            .size(THUMB_SIZE)
            .graphicsLayer {
                shadowElevation = THUMB_SHADOW.toPx()
                shape = CircleShape
                clip = true
            }
            .drawWithCache {
                val w = size.width
                val arrow = Path().apply {
                    moveTo(w * ARROW_TAIL, w / 2)
                    lineTo(w * ARROW_TIP, w / 2)
                    moveTo(w * ARROW_HEAD, w * ARROW_WING)
                    lineTo(w * ARROW_TIP, w / 2)
                    lineTo(w * ARROW_HEAD, w * (1f - ARROW_WING))
                }
                val tick = Path().apply {
                    moveTo(w * CHECK_START_X, w * CHECK_START_Y)
                    lineTo(w * CHECK_MID_X, w * CHECK_MID_Y)
                    lineTo(w * CHECK_END_X, w * CHECK_END_Y)
                }
                val measure = PathMeasure().apply { setPath(tick, false) }
                val partial = Path()
                val stroke = Stroke(ICON_STROKE.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind {
                    drawCircle(Color.White)
                    val level = check.value.coerceIn(0f, 1f)
                    val mirror = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                    scale(mirror * (1f - level), 1f - level) { drawPath(arrow, color, style = stroke) }
                    if (level > 0f) {
                        partial.reset()
                        measure.getSegment(0f, measure.length * level, partial, true)
                        drawPath(partial, color, style = stroke)
                    }
                }
            },
    )
}

private const val CONFIRM_GREEN = 0xFF34C759
private val HEIGHT = 64.dp
private val THUMB_SIZE = 54.dp
private val THUMB_INSET = 5.dp
private val THUMB_SHADOW = 4.dp
private val ICON_STROKE = 2.5.dp
private const val CONFIRM_AT = 0.92f
private const val TRAIL_ALPHA = 0.35f
private const val TEXT_FADE = 1.6f
private const val TEXT_ALPHA = 0.7f
private val SNAP_SPEC = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)
private val RETURN_SPEC = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
private const val ARROW_TAIL = 0.3f
private const val ARROW_TIP = 0.7f
private const val ARROW_HEAD = 0.53f
private const val ARROW_WING = 0.33f
private const val CHECK_START_X = 0.3f
private const val CHECK_START_Y = 0.52f
private const val CHECK_MID_X = 0.44f
private const val CHECK_MID_Y = 0.66f
private const val CHECK_END_X = 0.71f
private const val CHECK_END_Y = 0.37f
