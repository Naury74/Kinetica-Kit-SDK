package com.naury.kinetica.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 띠나 쪽 위에서 시작한 끌기만 받아 넘긴다. 타일보다 먼저(Initial 단계에서) 움직임을 보고, 넘기는 방향으로 터치 여유 거리를 넘게
 * 움직였을 때만 가로챈다. 그 전까지는 아무것도 소비하지 않으므로 타일 누르기와 길게 누르기는 그대로 타일이 받는다.
 * 쪽은 손을 떼면 가까운 쪽(빠르게 던지면 던진 방향 다음 쪽)에 붙고, 띠는 속도만큼 미끄러지다 멈춘다.
 */
internal fun Modifier.gridScroll(state: KParticipantGridState, plan: GridPlan): Modifier = composed {
    if (plan.maxScroll <= 0f) return@composed this
    val scope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Float>()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val strip = plan.strip
    val horizontal = strip?.horizontal ?: true
    val zone = strip?.area ?: plan.area
    pointerInput(plan.maxScroll, zone, rtl) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!zone.contains(down.position.logical(size.width.toFloat(), rtl))) return@awaitEachGesture
            scope.launch { state.scroll.stop() }
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            val axis = DragAxis(horizontal, rtl, slop)
            val dragged = awaitAxisDrag(down.id, down.position, axis, tracker) { delta ->
                scope.launch { state.scroll.snapTo((state.scroll.value - delta).coerceIn(0f, plan.maxScroll)) }
            }
            if (!dragged) return@awaitEachGesture
            val v = tracker.calculateVelocity()
            val velocity = -(if (horizontal) v.x.mirrored(rtl) else v.y)
            val fling = FLING.toPx()
            scope.launch {
                if (strip != null) {
                    state.scroll.animateDecay(velocity, decay)
                } else {
                    val page = targetPage(state.scroll.value / plan.pageWidth, velocity, fling, plan.pages)
                    state.scroll.animateTo(page * plan.pageWidth, spring(stiffness = Spring.StiffnessMediumLow), velocity)
                }
            }
        }
    }
}

/** 넘기는 방향과 터치 여유 거리 */
private class DragAxis(val horizontal: Boolean, val rtl: Boolean, val slop: Float)

/**
 * [axis] 방향 끌기를 기다린다. 가로지르는 방향으로 먼저 움직이면 손을 떼고 false를, 넘기는 방향으로 여유 거리를 넘으면 그때부터
 * 움직임을 소비하며 [onDelta]로 논리 방향 이동량을 넘기고, 손을 떼면 true를 돌려준다.
 */
private suspend fun AwaitPointerEventScope.awaitAxisDrag(
    id: PointerId,
    start: Offset,
    axis: DragAxis,
    tracker: VelocityTracker,
    onDelta: (Float) -> Unit,
): Boolean {
    var dragging = false
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == id }
        if (change == null || !change.pressed) return dragging
        tracker.addPosition(change.uptimeMillis, change.position)
        val moved = change.position - start
        val along = abs(if (axis.horizontal) moved.x else moved.y)
        val across = abs(if (axis.horizontal) moved.y else moved.x)
        if (!dragging && across > axis.slop && across > along) return false
        dragging = dragging || along > axis.slop
        if (dragging) onDelta(change.consumeAlong(axis))
    }
}

/** 움직임을 소비하고 넘기는 방향의 논리 이동량을 돌려준다. */
private fun PointerInputChange.consumeAlong(axis: DragAxis): Float {
    val delta = positionChange().let { if (axis.horizontal) it.x.mirrored(axis.rtl) else it.y }
    consume()
    return delta
}

/** 손을 뗀 뒤 붙을 쪽. 빠르게 던지면([fling] 이상) 던진 방향 다음 쪽, 아니면 가까운 쪽이다. */
internal fun targetPage(current: Float, velocity: Float, fling: Float, pages: Int): Float = when {
    velocity > fling -> floor(current) + 1
    velocity < -fling -> ceil(current) - 1
    else -> current.roundToInt().toFloat()
}.coerceIn(0f, (pages - 1).toFloat())

/** 1:1 내 작은 창 끌기. 놓으면 가까운 모서리로 스프링처럼 붙는다. */
internal fun Modifier.floatingDrag(state: KParticipantGridState, motion: TileMotion, full: Rect): Modifier = composed {
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    pointerInput(full, rtl) {
        val drop: () -> Unit = {
            val moved = motion.rect.value.translate(state.drag)
            scope.launch {
                motion.rect.snapTo(moved)
                state.drag = Offset.Zero
                state.selfCorner = nearestCorner(moved.center, full)
            }
        }
        detectDragGestures(onDragEnd = drop, onDragCancel = drop) { change, amount ->
            change.consume()
            state.drag += Offset(amount.x.mirrored(rtl), amount.y)
        }
    }
}

/** 쪽 위치 점. 지금 쪽은 길게 그린다. TalkBack은 '2/3쪽'처럼 읽고, 사용자 지정 동작으로 쪽을 넘긴다. */
@Composable
internal fun BoxScope.PageDots(state: KParticipantGridState, plan: GridPlan) {
    val scope = rememberCoroutineScope()
    val page by remember(state) { derivedStateOf { state.page } }
    val label = stringResource(R.string.kinetica_ui_call_page, page + 1, plan.pages)
    val next = stringResource(R.string.kinetica_ui_call_page_next)
    val previous = stringResource(R.string.kinetica_ui_call_page_previous)
    val go: (Int) -> Boolean = { target ->
        scope.launch { state.scroll.animateTo(target * plan.pageWidth) }
        true
    }
    Box(
        Modifier.fillMaxWidth().offset { IntOffset(0, plan.area.bottom.roundToInt()) }.height(DOTS_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = DOTS_SCRIM))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .clearAndSetSemantics {
                    contentDescription = label
                    customActions = listOfNotNull(
                        CustomAccessibilityAction(next) { go(page + 1) }.takeIf { page < plan.pages - 1 },
                        CustomAccessibilityAction(previous) { go(page - 1) }.takeIf { page > 0 },
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(plan.pages) { index ->
                Box(
                    Modifier
                        .size(width = if (index == page) DOT_ACTIVE else DOT, height = DOT)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (index == page) 1f else DOT_IDLE)),
                )
            }
        }
    }
}

/** 터치 위치를 시작 쪽이 0인 논리 좌표로 */
private fun Offset.logical(width: Float, rtl: Boolean): Offset = if (rtl) Offset(width - x, y) else this

private fun Float.mirrored(rtl: Boolean): Float = if (rtl) -this else this

private val FLING = 1_000.dp
private val DOTS_HEIGHT = 24.dp
private val DOT = 6.dp
private val DOT_ACTIVE = 16.dp
private const val DOT_IDLE = 0.4f
private const val DOTS_SCRIM = 0.35f
