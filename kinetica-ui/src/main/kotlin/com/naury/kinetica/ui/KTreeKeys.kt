package com.naury.kinetica.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 트리 키보드 처리에 필요한 것. */
internal class TreeKeyContext<T>(
    val rows: List<TreeRow<T>>,
    val actions: TreeActions<T>,
    val listState: LazyListState,
    val scope: CoroutineScope,
    val rtl: Boolean,
    val checkable: Boolean,
)

/** 트리에서 누른 키를 처리한다. 처리했으면 `true`. 떼는 사건도 같은 키면 삼켜서 바깥에서 두 번 처리하지 않게 한다. */
internal fun <T> treeKey(event: KeyEvent, context: TreeKeyContext<T>): Boolean {
    val move = treeMove(event.key, context.rtl)
    if (move == TreeMove.None || context.rows.isEmpty()) return false
    if (event.type == KeyEventType.KeyDown) applyTreeMove(move, context)
    return true
}

/** 키보드 초점 행이 없으면 고른 행(없으면 첫 행)에 두고, 있으면 [move]를 한다. */
private fun <T> applyTreeMove(move: TreeMove, context: TreeKeyContext<T>) {
    val state = context.actions.state
    val rows = context.rows
    val current = rows.indexOfFirst { it.key == state.active }
    if (current < 0) {
        val start = rows.indexOfFirst { it.key == state.selected }.coerceAtLeast(0)
        state.active = rows[start].key
        reveal(context, start)
        return
    }
    when (move) {
        TreeMove.Select -> context.actions.onSelect(rows[current])
        TreeMove.Check -> if (context.checkable) context.actions.onToggleCheck(rows[current])
        else -> Unit
    }
    val target = treeTarget(move, current, rows, state)
    if (target != current) {
        state.active = rows[target].key
        reveal(context, target)
    }
}

internal enum class TreeMove { None, Up, Down, In, Out, First, Last, Select, Check }

private fun treeMove(key: Key, rtl: Boolean): TreeMove = when (key) {
    Key.DirectionUp -> TreeMove.Up
    Key.DirectionDown -> TreeMove.Down
    Key.DirectionRight -> if (rtl) TreeMove.Out else TreeMove.In
    Key.DirectionLeft -> if (rtl) TreeMove.In else TreeMove.Out
    Key.MoveHome -> TreeMove.First
    Key.MoveEnd -> TreeMove.Last
    Key.Enter, Key.NumPadEnter -> TreeMove.Select
    Key.Spacebar -> TreeMove.Check
    else -> TreeMove.None
}

/**
 * [move]로 옮겨 갈 행 번호. 펼치기와 접기처럼 행을 옮기지 않는 동작은 상태만 바꾸고 [current]를 돌려준다.
 * 오른쪽은 접힌 부모를 펼치고, 이미 펼쳐 있으면 첫 자식으로 간다. 왼쪽은 펼친 부모를 접고, 아니면 부모로 간다.
 */
internal fun <T> treeTarget(move: TreeMove, current: Int, rows: List<TreeRow<T>>, state: KTreeState): Int = when (move) {
    TreeMove.Up -> (current - 1).coerceAtLeast(0)
    TreeMove.Down -> (current + 1).coerceAtMost(rows.lastIndex)
    TreeMove.First -> 0
    TreeMove.Last -> rows.lastIndex
    TreeMove.In -> treeIn(current, rows, state)
    TreeMove.Out -> treeOut(current, rows, state)
    else -> current
}

private fun <T> treeIn(current: Int, rows: List<TreeRow<T>>, state: KTreeState): Int {
    val row = rows[current]
    return when {
        !row.hasChildren -> current
        !state.isExpanded(row.key) -> current.also { state.expand(row.key) }
        rows.getOrNull(current + 1)?.parent == row.key -> current + 1
        else -> current
    }
}

private fun <T> treeOut(current: Int, rows: List<TreeRow<T>>, state: KTreeState): Int {
    val row = rows[current]
    return when {
        row.hasChildren && state.isExpanded(row.key) -> current.also { state.collapse(row.key) }
        row.parent != null -> rows.indexOfFirst { it.key == row.parent }.takeIf { it >= 0 } ?: current
        else -> current
    }
}

/** [index] 행이 목록 안에 다 보이도록 필요한 만큼만 스크롤한다. */
private fun reveal(context: TreeKeyContext<*>, index: Int) {
    if (index < 0) return
    val list = context.listState
    context.scope.launch {
        val layout = list.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
        when {
            item == null -> list.scrollToItem(index)

            item.offset < layout.viewportStartOffset -> list.scrollBy((item.offset - layout.viewportStartOffset).toFloat())

            item.offset + item.size > layout.viewportEndOffset ->
                list.scrollBy((item.offset + item.size - layout.viewportEndOffset).toFloat())

            else -> Unit
        }
    }
}
