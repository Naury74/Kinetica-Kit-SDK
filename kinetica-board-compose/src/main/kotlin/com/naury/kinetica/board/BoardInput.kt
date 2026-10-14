package com.naury.kinetica.board

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import kotlinx.coroutines.flow.first

/**
 * 키보드로 고른(초점이 있는) 위젯을 다룬다: 방향키로 옮기기, Shift+방향키로 크기, Delete·Backspace로 빼기.
 * 격자 배치에서는 한 칸씩 움직인다.
 */
internal fun Modifier.keyboardEditing(state: KineticaBoardState, key: String, permissions: BoardPermissions, mirrored: Boolean): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        // 화면이 뒤집혀 있으면 화면 왼쪽이 저장 좌표의 오른쪽이다.
        val delta = arrowDelta(event.key)?.let { (dx, dy) -> (if (mirrored) -dx else dx) to dy }
        when {
            event.key == Key.Delete || event.key == Key.Backspace -> permissions.remove && state.remove(key)
            delta == null -> false
            event.isShiftPressed -> permissions.resize && state.resizeBy(key, delta.first, delta.second)
            else -> permissions.move && state.moveBy(key, delta.first, delta.second)
        }
    }.focusable()

private fun arrowDelta(key: Key): Pair<Float, Float>? = when (key) {
    Key.DirectionLeft -> -NUDGE_DP to 0f
    Key.DirectionRight -> NUDGE_DP to 0f
    Key.DirectionUp -> 0f to -NUDGE_DP
    Key.DirectionDown -> 0f to NUDGE_DP
    else -> null
}

/** TalkBack 등에서 끌기 없이 옮기고 크기를 바꾸고 빼는 동작. 허용된 것만 준다. */
@Composable
internal fun accessibilityActions(state: KineticaBoardState, key: String, permissions: BoardPermissions): List<CustomAccessibilityAction> {
    val side = if (LocalBoardMirrored.current) -1f else 1f
    val moves = listOf(
        R.string.kinetica_board_move_up to (0f to -NUDGE_DP),
        R.string.kinetica_board_move_down to (0f to NUDGE_DP),
        R.string.kinetica_board_move_left to (-NUDGE_DP * side to 0f),
        R.string.kinetica_board_move_right to (NUDGE_DP * side to 0f),
    ).map { (label, delta) -> stringResource(label) to delta }
    val larger = stringResource(R.string.kinetica_board_larger)
    val smaller = stringResource(R.string.kinetica_board_smaller)
    val remove = stringResource(R.string.kinetica_board_remove_action)
    return buildList {
        if (permissions.move) {
            moves.forEach { (label, delta) ->
                add(CustomAccessibilityAction(label) { state.moveBy(key, delta.first, delta.second) })
            }
        }
        if (permissions.resize) {
            add(CustomAccessibilityAction(larger) { state.resizeBy(key, NUDGE_DP * 2, NUDGE_DP * 2) })
            add(CustomAccessibilityAction(smaller) { state.resizeBy(key, -NUDGE_DP * 2, -NUDGE_DP * 2) })
        }
        if (permissions.remove) add(CustomAccessibilityAction(remove) { state.remove(key) })
    }
}
