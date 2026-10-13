package com.naury.kinetica.editor

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.naury.kinetica.history.KineticaSession

private const val NUDGE = 1.0
private const val BIG_NUDGE = 10.0

/** 캔버스에 포커스가 있을 때의 키보드 동작. 처리한 키만 소비한다. */
internal fun Modifier.editorShortcuts(session: KineticaSession, actions: EditorActions): Modifier = onKeyEvent { event ->
    val action = if (event.type == KeyEventType.KeyDown) shortcutFor(event, session, actions) else null
    action?.invoke()
    action != null
}

private fun shortcutFor(event: KeyEvent, session: KineticaSession, actions: EditorActions): (() -> Unit)? {
    val step = if (event.isShiftPressed) BIG_NUDGE else NUDGE
    if (event.isCtrlPressed || event.isMetaPressed) {
        return when (event.key) {
            Key.Z -> if (event.isShiftPressed) ({ session.history.redo() }) else ({ session.history.undo() })
            Key.Y -> ({ session.history.redo() })
            else -> null
        }
    }
    return mapOf<Key, () -> Unit>(
        Key.Delete to actions::delete,
        Key.Backspace to actions::delete,
        Key.Escape to session::clearSelection,
        Key.DirectionLeft to { actions.nudge(-step, 0.0) },
        Key.DirectionRight to { actions.nudge(step, 0.0) },
        Key.DirectionUp to { actions.nudge(0.0, -step) },
        Key.DirectionDown to { actions.nudge(0.0, step) },
    )[event.key]
}
