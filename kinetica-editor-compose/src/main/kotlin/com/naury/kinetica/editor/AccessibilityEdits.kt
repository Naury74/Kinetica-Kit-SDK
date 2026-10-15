package com.naury.kinetica.editor

import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.scene.NodeId

/** 접근성 입력을 명령으로 바꾼다. 입력 하나가 실행 취소 한 단계다. */
internal class AccessibilityEdits(private val state: EditorState) {
    /** 스크린 리더가 읽을 라벨을 정한다. 빈 문자열이면 라벨을 지우고, [hidden]이면 장식용으로 보고 읽지 않는다. */
    fun set(id: NodeId, label: String? = null, hidden: Boolean? = null): Boolean {
        val node = state.document.node(id) ?: return false
        val current = node.accessibility
        val next = current.copy(
            label = if (label == null) current.label else label.trim().ifEmpty { null },
            hidden = hidden ?: current.hidden,
        )
        if (next == current) return true
        return state.execute(SceneCommand.UpdateProperties(node.copy(accessibility = next)))
    }
}
