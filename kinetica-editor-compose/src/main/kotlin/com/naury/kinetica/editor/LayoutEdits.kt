package com.naury.kinetica.editor

import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.scene.AutoLayout
import com.naury.kinetica.core.scene.Insets
import com.naury.kinetica.core.scene.LayoutConstraints
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneNode

/** 자동 배치·제약 입력을 명령으로 바꾼다. 입력 하나가 실행 취소 한 단계이며, 받을 수 없는 값이면 문서를 바꾸지 않고 `false`다. */
internal class LayoutEdits(private val state: EditorState) {
    /** Frame 자동 배치를 바꾼다. [change]가 `null`을 돌려주면 자동 배치를 끈다. Frame이 아니면 `false`다. */
    fun setLayout(id: NodeId, change: (AutoLayout?) -> AutoLayout?): Boolean {
        val node = state.document.node(id) ?: return false
        val frame = node.content as? NodeContent.Frame ?: return false
        return update(node.copy(content = frame.copy(layout = change(frame.layout))))
    }

    /** 간격(문서 단위, 0 이상). */
    fun setGap(id: NodeId, text: String): Boolean = parse(text)?.let { gap -> setLayout(id) { it?.copy(gap = gap) } } ?: false

    /** 네 변 같은 여백(문서 단위, 0 이상). */
    fun setPadding(id: NodeId, text: String): Boolean =
        parse(text)?.let { padding -> setLayout(id) { it?.copy(padding = Insets.all(padding)) } } ?: false

    fun setConstraints(id: NodeId, constraints: LayoutConstraints): Boolean =
        state.document.node(id)?.let { update(it.copy(constraints = constraints)) } ?: false

    /** 부모가 자동 배치 Frame이면 위치는 배치가 정하므로 제약을 쓰지 않는다. */
    fun isLaidOut(node: SceneNode): Boolean = (node.parentId?.let(state.document::node)?.content as? NodeContent.Frame)?.layout != null

    private fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

    private fun update(node: SceneNode): Boolean {
        if (state.document.node(node.id) == node) return true
        return state.execute(SceneCommand.UpdateProperties(node))
    }
}
