package com.naury.kinetica.editor

import androidx.compose.foundation.focusable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 타임라인 패널의 키보드 동작. 패널 안을 누르면 패널이 포커스를 받는다. 처리한 키만 소비한다.
 * 키를 다루지 않는 단축키(실행 취소 등)는 캔버스에 포커스가 있을 때만 동작한다.
 */
internal fun Modifier.timelineShortcuts(state: EditorState): Modifier = composed {
    val focus = remember { FocusRequester() }
    this
        .pointerInput(focus) {
            awaitPointerEventScope { while (true) if (awaitPointerEvent().changes.any { it.pressed }) focus.requestFocus() }
        }
        .focusRequester(focus)
        .onKeyEvent { event ->
            val action = if (event.type == KeyEventType.KeyDown) timelineShortcut(event, state, timelineFocused = true) else null
            action?.invoke()
            action != null
        }
        .focusable()
}

/**
 * 타임라인 키에 대한 단축키. 처리할 것이 없으면 `null`이라 캔버스의 노드 단축키로 넘어간다.
 *
 * - Delete, Backspace: 고른 키 삭제
 * - Ctrl(Cmd)+C: 고른 키 복사, Ctrl(Cmd)+V: 재생 위치에 붙여넣기
 * - Ctrl(Cmd)+A: 타임라인에 포커스가 있을 때만, 고른 레이어(없으면 timeline 전체)의 키를 모두 선택
 * - Esc: 키 선택과 선택 모드 해제
 */
internal fun timelineShortcut(event: KeyEvent, state: EditorState, timelineFocused: Boolean): (() -> Unit)? = when {
    !state.motionEnabled -> null
    event.isCtrlPressed || event.isMetaPressed -> commandShortcut(event.key, state, timelineFocused)
    else -> plainShortcut(event.key, state)
}

private fun plainShortcut(key: Key, state: EditorState): (() -> Unit)? {
    val selection = state.keySelection
    val keys = selection.keys
    return when {
        (key == Key.Delete || key == Key.Backspace) && keys.isNotEmpty() -> {
            { if (state.keyEdits.delete(keys)) selection.keys = emptySet() }
        }

        key == Key.Escape && (keys.isNotEmpty() || selection.selectMode) -> selection::clear

        else -> null
    }
}

private fun commandShortcut(key: Key, state: EditorState, timelineFocused: Boolean): (() -> Unit)? {
    val keys = state.keySelection.keys
    return when {
        key == Key.C && keys.isNotEmpty() -> {
            { state.keyEdits.copy(keys) }
        }

        key == Key.V && state.keyEdits.clipboard.isNotEmpty() -> {
            { paste(state) }
        }

        key == Key.A && timelineFocused -> {
            { state.keySelection.keys = allKeys(state) }
        }

        else -> null
    }
}

private fun paste(state: EditorState) {
    val pasted = state.keyEdits.paste(state.keyTimeMs, state.selection.singleOrNull())
    if (pasted.isNotEmpty()) state.keySelection.keys = pasted
}

private fun allKeys(state: EditorState): Set<SelectedKey> {
    val node = state.selection.singleOrNull()
    val tracks = state.timeline?.tracks.orEmpty().filter { node == null || it.nodeId == node }
    return tracks.flatMap { track -> track.keyframes.map { SelectedKey(track.nodeId, track.property, it.timeMs) } }.toSet()
}
