package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 놓을 자리. [column] 열에서 원래 목록의 [before]번 카드 앞이다. 끝이면 그 열 카드 수와 같다. */
@Immutable
internal data class KanbanTarget(val column: Any, val before: Int)

/** 끌기 단계. 놓은 뒤에는 떠 있는 카드가 자리로 내려앉는 동안 [Settling]이다. */
internal enum class KanbanPhase { Idle, Dragging, Settling }

/** 끌기 시작할 때 잡은 카드 정보. [from]은 원래 자리이고, 위치는 모두 루트 좌표(px)다. */
internal class KanbanGrab<T>(
    val item: T,
    val key: Any,
    val from: KanbanTarget,
    val size: IntSize,
    val topLeft: Offset,
    val pointer: Offset,
) {
    val column: Any get() = from.column
    val index: Int get() = from.before
}

/**
 * 칸반 보드의 끌기 상태. 끄는 동안 데이터는 그대로 두고 떠 있는 카드와 놓을 자리 표시만 그리며, 놓을 때 한 번만 옮긴다.
 * 손가락 위치는 상태로 두되 컴포지션이 아니라 그리기와 graphicsLayer 단계에서만 읽는다.
 */
@Stable
internal class KanbanDragState<T>(private val scope: CoroutineScope) {
    var phase by mutableStateOf(KanbanPhase.Idle)
        private set
    var grab: KanbanGrab<T>? by mutableStateOf(null)
        private set
    var target: KanbanTarget? by mutableStateOf(null)
        private set

    // 끄는 동안 손가락이 움직인 거리. 놓은 뒤에는 [settleTopLeft]가 위치를 맡는다.
    private var dragDelta by mutableStateOf(Offset.Zero)
    private val settleTopLeft = Animatable(Offset.Zero, Offset.VectorConverter)
    val lift = Animatable(0f)

    var onMove: (Any, Any, Int) -> Unit = { _, _, _ -> }
    var columns: List<KKanbanColumn<T>> = emptyList()
    var cardKey: (T) -> Any = { it as Any }

    // 지연 목록 항목 key는 호스트 키를 바꾼 값일 수 있으므로 보이는 항목과 비교할 때는 이것으로 바꿔 비교한다.
    var itemKeys = LazyItemKeys(null)
    var horizontal: ScrollState? = null
    var boardBounds = Rect.Zero
    var edgePx = 0f
    var maxSpeedPx = 0f

    // 열 목록의 루트 좌표 경계와 스크롤 상태. 배치될 때마다 갱신하고 끄는 중에만 읽는다.
    val listBounds = HashMap<Any, Rect>()
    val lists = HashMap<Any, LazyListState>()

    private var pointer = Offset.Zero
    private var autoScroll: Job? = null

    /** 떠 있는 카드의 왼쪽 위(루트 좌표). 끄는 동안은 손가락을 따르고, 놓은 뒤에는 자리로 움직인다. 그리기 단계에서 읽는다. */
    fun floatTopLeft(): Offset {
        val grab = grab ?: return Offset.Zero
        return if (phase == KanbanPhase.Dragging) grab.topLeft + dragDelta else settleTopLeft.value
    }

    fun isDragged(key: Any): Boolean = phase != KanbanPhase.Idle && grab?.key == key

    fun start(grab: KanbanGrab<T>) {
        if (phase == KanbanPhase.Dragging) return
        this.grab = grab
        pointer = grab.pointer
        phase = KanbanPhase.Dragging
        dragDelta = Offset.Zero
        target = grab.from
        scope.launch { lift.animateTo(1f, LIFT_SPEC) }
        autoScroll?.cancel()
        autoScroll = scope.launch { scrollNearEdges() }
    }

    fun moveTo(pointer: Offset) {
        val grab = grab ?: return
        if (phase != KanbanPhase.Dragging) return
        this.pointer = pointer
        dragDelta = pointer - grab.pointer
        retarget()
    }

    /** 놓는다. 자리가 바뀌었으면 [onMove]를 부르고 떠 있는 카드를 놓을 자리로 내려앉힌다. */
    fun drop() {
        val grab = grab ?: return
        if (phase != KanbanPhase.Dragging) return
        val target = target
        if (target == null) {
            settle(grab.topLeft)
            return
        }
        // 끄는 동안 같은 열에 카드가 더해지거나 빠졌을 수 있으므로 시작할 때 번호가 아니라 지금 번호로 계산한다.
        val from = kanbanCurrentIndex(columns, cardKey, grab)
        val to = finalIndex(target.before, from, target.column == grab.column)
        val destination = slotTopLeft(target, grab)
        if (target.column != grab.column || to != from) onMove(grab.key, target.column, to)
        settle(destination ?: grab.topLeft)
    }

    /** 터치가 취소되었거나 카드가 사라졌다. 옮기지 않고 원래 자리로 돌려보낸다. */
    fun cancel(home: Offset? = null) {
        val grab = grab ?: return
        if (phase != KanbanPhase.Dragging) return
        settle(home ?: grab.topLeft)
    }

    /** 보드가 사라질 때 끌기를 바로 끝낸다. */
    fun reset() {
        autoScroll?.cancel()
        phase = KanbanPhase.Idle
        grab = null
        target = null
    }

    private fun settle(destination: Offset) {
        autoScroll?.cancel()
        val from = floatTopLeft()
        target = null
        phase = KanbanPhase.Settling
        val settling = grab
        scope.launch {
            launch { lift.animateTo(0f, SETTLE_SPEC) }
            settleTopLeft.snapTo(from)
            settleTopLeft.animateTo(destination, SETTLE_OFFSET_SPEC)
            if (grab === settling && phase == KanbanPhase.Settling) {
                phase = KanbanPhase.Idle
                grab = null
            }
        }
    }

    private fun retarget() {
        val grab = grab ?: return
        val center = pointer - grab.pointer + grab.topLeft + Offset(grab.size.width / 2f, grab.size.height / 2f)
        val column = kanbanColumnAt(center.x, columns, listBounds) ?: return
        val bounds = listBounds[column] ?: return
        val list = lists[column] ?: return
        val layout = list.layoutInfo
        val y = center.y - bounds.top + layout.viewportStartOffset
        val dragged = itemKeys.of(grab.key)
        val slots = layout.visibleItemsInfo.filter { it.key != dragged }.map { KanbanSlot(it.index, it.offset, it.size) }
        val before = kanbanDropIndex(slots, layout.totalItemsCount, y)
        val next = KanbanTarget(column, before)
        if (next != target) target = next
    }

    /** 놓을 자리 표시선의 목록 안 y(px, 목록 위 가장자리 기준). 자리가 보이지 않으면 목록 끝으로 붙인다. */
    fun indicatorY(column: Any, list: LazyListState): Float? {
        val target = target ?: return null
        if (target.column != column) return null
        val layout = list.layoutInfo
        val dragged = grab?.key?.let { itemKeys.of(it) }
        val visible = layout.visibleItemsInfo
        val before = visible.firstOrNull { it.index == target.before && it.key != dragged }
        val after = visible.lastOrNull { it.index < target.before && it.key != dragged }
        val y = when {
            before != null -> before.offset - gapPx / 2
            after != null -> after.offset + after.size + gapPx / 2
            else -> 0f
        }
        return y - layout.viewportStartOffset
    }

    /** 카드 사이 틈(px). 표시선은 이 틈 가운데에 긋는다. */
    var gapPx = 0f

    private fun slotTopLeft(target: KanbanTarget, grab: KanbanGrab<T>): Offset? {
        val bounds = listBounds[target.column] ?: return null
        val list = lists[target.column] ?: return null
        val y = indicatorY(target.column, list) ?: return null
        val top = (y + gapPx / 2).coerceIn(0f, (bounds.height - grab.size.height).coerceAtLeast(0f))
        return Offset(bounds.left + (bounds.width - grab.size.width) / 2f, bounds.top + top)
    }

    private suspend fun scrollNearEdges() {
        while (scope.isActive && phase == KanbanPhase.Dragging) {
            // 두 방향을 모두 시도해야 하므로 단락 평가하지 않는 or를 쓴다.
            if (scrollBoard() or scrollColumn()) retarget()
            withFrameNanos { }
        }
    }

    /** 손가락이 보드 가장자리 가까이 있으면 보드를 가로로 민다. 움직였으면 `true`. */
    private suspend fun scrollBoard(): Boolean {
        val scroll = horizontal ?: return false
        val speed = kanbanEdgeSpeed(pointer.x, boardBounds.left, boardBounds.right, edgePx, maxSpeedPx)
        return speed != 0f && scroll.scrollBy(speed * horizontalSign) != 0f
    }

    /** 끄는 카드가 놓을 열의 위아래 가장자리 가까이 있으면 그 열을 민다. 움직였으면 `true`. */
    private suspend fun scrollColumn(): Boolean {
        val grab = grab ?: return false
        val column = target?.column ?: return false
        val bounds = listBounds[column] ?: return false
        val list = lists[column] ?: return false
        val center = pointer.y - grab.pointer.y + grab.topLeft.y + grab.size.height / 2f
        val speed = kanbanEdgeSpeed(center, bounds.top, bounds.bottom, edgePx, maxSpeedPx)
        return speed != 0f && list.scrollBy(speed) != 0f
    }

    /** RTL이면 가로 스크롤 값이 커질수록 왼쪽 내용이 보이므로 방향을 뒤집는다. */
    var horizontalSign = 1f
}

/** 위치 [at]이 [start]..[end] 가장자리에 [edge]보다 가까우면 가까운 만큼 빠르게(프레임당 px, 최대 [maxSpeed]) 스크롤할 양. */
internal fun kanbanEdgeSpeed(at: Float, start: Float, end: Float, edge: Float, maxSpeed: Float): Float {
    val reach = edge.coerceAtLeast(1f)
    return when {
        end - start < reach * 2 -> 0f
        at < start + reach -> -maxSpeed * ((start + reach - at) / reach).coerceIn(0f, 1f)
        at > end - reach -> maxSpeed * ((at - (end - reach)) / reach).coerceIn(0f, 1f)
        else -> 0f
    }
}

/** 가로 위치 [x]에 있는 열. 열 사이 틈이면 가까운 열이다. 손가락이 움직일 때마다 부르므로 목록을 새로 만들지 않는다. */
internal fun kanbanColumnAt(x: Float, columns: List<KKanbanColumn<*>>, bounds: Map<Any, Rect>): Any? {
    var best: Any? = null
    var bestDistance = Float.POSITIVE_INFINITY
    for (i in columns.indices) {
        val id = columns[i].id
        val r = bounds[id] ?: continue
        val distance = if (x in r.left..r.right) 0f else minOf(kotlin.math.abs(x - r.left), kotlin.math.abs(x - r.right))
        if (distance < bestDistance) {
            best = id
            bestDistance = distance
        }
    }
    return best
}

/** 보이는 카드 하나의 목록 안 위치(px). */
internal class KanbanSlot(val index: Int, val offset: Int, val size: Int)

/**
 * 목록 안 y(px)에 놓으면 들어갈 자리. 가운데가 [y]보다 아래인 첫 카드의 번호이고, 그런 카드가 없으면 마지막으로 보인 카드 다음이다.
 * 보이는 카드가 없으면(빈 열) 0이다. [slots]에는 끄는 카드 자신을 빼고 넣는다.
 */
internal fun kanbanDropIndex(slots: List<KanbanSlot>, total: Int, y: Float): Int {
    if (slots.isEmpty()) return if (total <= 1) 0 else total
    slots.firstOrNull { it.offset + it.size / 2f > y }?.let { return it.index }
    return (slots.last().index + 1).coerceAtMost(total)
}

/** 잡은 카드의 원래 열 안 지금 번호. 열이나 카드를 찾지 못하면 시작할 때 번호다. */
internal fun <T> kanbanCurrentIndex(columns: List<KKanbanColumn<T>>, cardKey: (T) -> Any, grab: KanbanGrab<T>): Int {
    val cards = columns.firstOrNull { it.id == grab.column }?.cards ?: return grab.index
    val index = cards.indexOfFirst { cardKey(it) == grab.key }
    return if (index >= 0) index else grab.index
}

/** 원래 목록의 [before]번 앞에 놓을 때 옮긴 뒤 목록에서의 번호. 같은 열에서 아래로 옮기면 자기 자리가 빠지므로 하나 줄어든다. */
internal fun finalIndex(before: Int, from: Int, sameColumn: Boolean): Int = if (sameColumn && before > from) before - 1 else before

private val LIFT_SPEC = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
private val SETTLE_SPEC = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
private val SETTLE_OFFSET_SPEC =
    spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Offset(0.5f, 0.5f))
