// 파일 이름은 상태 클래스 하나가 아니라 순서 바꾸기 기능 전체(상태, 수정자, 항목)를 가리킨다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 세로 `LazyColumn`에서 길게 눌러 끌어 순서를 바꾸는 상태. [rememberReorderableListState]로 만들고, 항목에
 * [Modifier.reorderable]이나 [ReorderableItem]을 붙인다. 항목에는 반드시 `key`를 주어야 한다.
 */
@Stable
public class ReorderableListState internal constructor(
    /** 순서를 바꿀 목록의 스크롤 상태 */
    public val listState: LazyListState,
    private val scope: CoroutineScope,
) {
    internal var onMove: (Int, Int) -> Unit = { _, _ -> }
    internal var edgePx = 0f
    internal var maxSpeedPx = 0f

    // 순서를 바꿀 수 있는 항목의 key. 머리글처럼 이 수정자가 없는 항목과는 자리를 바꾸지 않는다.
    internal val keys = HashSet<Any>()

    /** 끌고 있는 항목의 key. 끌고 있지 않으면 `null`이다. */
    public var draggingKey: Any? by mutableStateOf(null)
        private set

    internal var droppedKey: Any? by mutableStateOf(null)
    private val dropOffset = Animatable(0f)
    private val lift = Animatable(0f)
    private var startOffset = 0
    private var dragDelta by mutableFloatStateOf(0f)
    private var autoScroll: Job? = null

    // 순서를 바꾼 뒤 목록이 다시 배치되기 전에는 예전 위치로 또 바꾸지 않도록 마지막으로 본 배치를 기억한다.
    private var swappedLayout: Any? = null

    /** [key] 항목을 끌고 있으면 `true`다. */
    public fun isDragging(key: Any): Boolean = draggingKey == key

    internal fun offsetOf(key: Any): Float = when (key) {
        draggingKey -> info(key)?.let { startOffset + dragDelta - it.offset } ?: 0f
        droppedKey -> dropOffset.value
        else -> 0f
    }

    internal fun liftOf(key: Any): Float = if (key == draggingKey || key == droppedKey) lift.value else 0f

    internal fun start(key: Any) {
        val item = info(key) ?: return
        draggingKey = key
        droppedKey = null
        startOffset = item.offset
        dragDelta = 0f
        scope.launch { lift.animateTo(1f, LIFT_SPEC) }
        autoScroll = scope.launch { scrollNearEdges() }
    }

    internal fun drag(delta: Float) {
        if (draggingKey == null) return
        dragDelta += delta
        swapIfNeeded()
    }

    internal fun end() {
        val key = draggingKey ?: return
        val from = offsetOf(key)
        draggingKey = null
        autoScroll?.cancel()
        droppedKey = key
        scope.launch {
            dropOffset.snapTo(from)
            dropOffset.animateTo(0f, DROP_SPEC)
            if (droppedKey == key) droppedKey = null
        }
        scope.launch { lift.animateTo(0f, LIFT_SPEC) }
    }

    /** [key] 항목을 [step]칸(음수면 위로) 옮긴다. 목록 범위를 벗어나거나 이웃이 순서를 바꿀 수 없는 항목이면 `false`다. */
    internal fun moveBy(key: Any, step: Int): Boolean {
        val item = info(key) ?: return false
        val to = item.index + step
        if (to !in 0 until listState.layoutInfo.totalItemsCount) return false
        val neighbor = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == to }
        if (neighbor != null && neighbor.key !in keys) return false
        onMove(item.index, to)
        if (neighbor == null || to == listState.firstVisibleItemIndex) listState.requestScrollToItem(to)
        return true
    }

    private fun info(key: Any): LazyListItemInfo? = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    private fun swapIfNeeded() {
        val layout = listState.layoutInfo
        val dragged = draggingKey?.let(::info)
        if (layout === swappedLayout || dragged == null) return
        val target = swapTarget(dragged, layout.visibleItemsInfo) ?: return
        val first = listState.firstVisibleItemIndex
        // 맨 위 항목이 바뀌면 LazyColumn이 그 항목을 따라 스크롤하므로 지금 위치를 붙잡아 둔다.
        if (target.index == first || dragged.index == first) {
            listState.requestScrollToItem(first, listState.firstVisibleItemScrollOffset)
        }
        swappedLayout = layout
        onMove(dragged.index, target.index)
    }

    /** 끄는 항목이 넘어선 가장 먼 이웃. 겹친 정도를 작은 항목 기준으로 재야 크기가 다른 항목과 바꾼 직후 되돌아가며 떨리지 않는다. */
    private fun swapTarget(dragged: LazyListItemInfo, visible: List<LazyListItemInfo>): LazyListItemInfo? {
        val top = startOffset + dragDelta
        val bottom = top + dragged.size
        return visible.lastOrNull { other ->
            val half = minOf(other.size, dragged.size) / 2f
            other.key in keys && other.index > dragged.index && bottom > other.offset + other.size - half
        } ?: visible.firstOrNull { other ->
            val half = minOf(other.size, dragged.size) / 2f
            other.key in keys && other.index < dragged.index && top < other.offset + half
        }
    }

    private suspend fun scrollNearEdges() {
        while (scope.isActive && draggingKey != null) {
            val speed = edgeSpeed()
            if (speed != 0f) {
                listState.scrollBy(speed)
                swapIfNeeded()
            }
            withFrameNanos { }
        }
    }

    /** 끄는 항목이 목록 가장자리에 [edgePx]보다 가까우면 가까운 만큼 빠르게(프레임당 px) 스크롤한다. */
    private fun edgeSpeed(): Float {
        val dragged = info(draggingKey ?: return 0f) ?: return 0f
        val layout = listState.layoutInfo
        val top = startOffset + dragDelta
        val bottom = top + dragged.size
        val start = layout.viewportStartOffset + layout.beforeContentPadding + edgePx
        val end = layout.viewportEndOffset - layout.afterContentPadding - edgePx
        val edge = edgePx.coerceAtLeast(1f)
        return when {
            top < start -> -maxSpeedPx * ((start - top) / edge).coerceIn(0f, 1f)
            bottom > end -> maxSpeedPx * ((bottom - end) / edge).coerceIn(0f, 1f)
            else -> 0f
        }
    }
}

/**
 * [lazyListState] 목록의 순서를 바꾸는 상태를 만든다. 순서를 바꿀 때마다 [onMove]를 `LazyColumn`의 항목 위치(0부터)로 부른다.
 * 머리글처럼 앞에 다른 항목이 있으면 그만큼 위치가 밀리므로 받는 쪽에서 빼야 한다. [onMove]에서 바로 목록을 고쳐야 다음 비교가 맞다.
 *
 * ```kotlin
 * val reorder = rememberReorderableListState(listState) { from, to -> items.add(to, items.removeAt(from)) }
 * ```
 */
@Composable
public fun rememberReorderableListState(lazyListState: LazyListState, onMove: (from: Int, to: Int) -> Unit): ReorderableListState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val state = remember(lazyListState, scope) { ReorderableListState(lazyListState, scope) }
    SideEffect {
        state.onMove = onMove
        state.edgePx = with(density) { AUTO_SCROLL_EDGE.toPx() }
        state.maxSpeedPx = with(density) { AUTO_SCROLL_SPEED.toPx() }
    }
    return state
}

/**
 * `LazyColumn` 항목을 길게 눌러 끌 수 있게 한다. 잡은 항목은 살짝 커지고 그림자가 생기며 손가락을 따라오고, 목록 가장자리
 * 가까이 끌면 그쪽으로 자동 스크롤한다. 다른 항목이 부드럽게 비켜나게 하려면 항목 루트에 `Modifier.animateItem`을 함께 걸되,
 * 끄는 항목에는 `placementSpec`을 `null`로 준다. 이 처리를 대신 해 주는 [ReorderableItem]을 쓰는 편이 간단하다.
 *
 * 항목 루트에 걸어야 끄는 항목이 다른 항목 위에 그려진다. TalkBack은 '위로 이동'과 '아래로 이동'을 사용자 지정 동작으로 제공하고,
 * 항목 안에 포커스가 있을 때 Alt+위/아래 방향키로도 옮길 수 있다.
 *
 * @param key 이 항목의 `LazyColumn` key와 같은 값
 * @param shape 떠오를 때 그림자의 모양
 */
@Composable
public fun Modifier.reorderable(state: ReorderableListState, key: Any, shape: Shape = RoundedCornerShape(16.dp)): Modifier {
    val haptic = kHapticFeedback()
    val upLabel = stringResource(R.string.kinetica_ui_move_up)
    val downLabel = stringResource(R.string.kinetica_ui_move_down)
    DisposableEffect(state, key) {
        state.keys += key
        onDispose { state.keys -= key }
    }
    val rootTop = remember { FloatArray(1) }
    val lifted = state.draggingKey == key || state.droppedKey == key
    return this
        .zIndex(if (lifted) 1f else 0f)
        .semantics {
            customActions = listOf(
                CustomAccessibilityAction(upLabel) { state.moveBy(key, -1) },
                CustomAccessibilityAction(downLabel) { state.moveBy(key, 1) },
            )
        }
        .onKeyEvent { event ->
            val step = when (event.key) {
                Key.DirectionUp -> -1
                Key.DirectionDown -> 1
                else -> 0
            }
            event.type == KeyEventType.KeyDown && event.isAltPressed && step != 0 && state.moveBy(key, step)
        }
        // 항목이 자리를 바꾸면 이 노드도 옮겨 가므로, 손가락 위치를 루트 좌표로 바꿔 이동 거리를 잰다.
        .onGloballyPositioned { rootTop[0] = it.positionInRoot().y }
        .pointerInput(state, key) {
            var last = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = { at ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    last = at.y + rootTop[0]
                    state.start(key)
                },
                onDragEnd = state::end,
                onDragCancel = state::end,
            ) { change, _ ->
                change.consume()
                val now = change.position.y + rootTop[0]
                state.drag(now - last)
                last = now
            }
        }
        .graphicsLayer {
            val level = state.liftOf(key)
            translationY = state.offsetOf(key)
            scaleX = 1f + LIFT_SCALE * level
            scaleY = 1f + LIFT_SCALE * level
            shadowElevation = LIFT_SHADOW.toPx() * level
            this.shape = shape
        }
}

/**
 * [Modifier.reorderable]과 `animateItem`을 함께 건 `LazyColumn` 항목. 끄는 항목은 손가락을 따라가고 나머지는 용수철처럼 비켜난다.
 *
 * ```kotlin
 * items(tasks, key = { it.id }) { task ->
 *     ReorderableItem(reorder, task.id) { dragging -> TaskRow(task, dragging) }
 * }
 * ```
 *
 * @param key 이 항목의 `LazyColumn` key와 같은 값
 */
@Composable
public fun LazyItemScope.ReorderableItem(
    state: ReorderableListState,
    key: Any,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable (isDragging: Boolean) -> Unit,
) {
    val dragging = state.draggingKey == key
    val held = dragging || state.droppedKey == key
    Box(
        modifier
            .animateItem(placementSpec = if (held) null else PLACEMENT_SPEC)
            .reorderable(state, key, shape),
    ) { content(dragging) }
}

private val PLACEMENT_SPEC = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
private val LIFT_SPEC = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
private val DROP_SPEC = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
private const val LIFT_SCALE = 0.03f
private val LIFT_SHADOW = 16.dp
private val AUTO_SCROLL_EDGE = 64.dp
private val AUTO_SCROLL_SPEED = 14.dp
