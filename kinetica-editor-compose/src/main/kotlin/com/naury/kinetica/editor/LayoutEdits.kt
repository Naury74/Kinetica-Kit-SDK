package com.naury.kinetica.editor

import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.scene.AutoLayout
import com.naury.kinetica.core.scene.Insets
import com.naury.kinetica.core.scene.LayoutConstraints
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneNode

/** 자동 레이아웃과 제약 입력을 명령으로 바꾼다. 입력 하나가 실행 취소 한 단계이고, 받을 수 없는 값이면 문서를 바꾸지 않고 `false`를 돌려준다. */
internal class LayoutEdits(private val state: EditorState) {
    /** Frame의 자동 레이아웃을 바꾼다. [change]가 `null`을 돌려주면 자동 레이아웃을 끈다. Frame이 아니면 `false`를 돌려준다. */
    fun setLayout(id: NodeId, change: (AutoLayout?) -> AutoLayout?): Boolean {
        val node = state.document.node(id) ?: return false
        val frame = node.content as? NodeContent.Frame ?: return false
        return update(node.copy(content = frame.copy(layout = change(frame.layout))))
    }

    /** 간격(문서 단위, 0 이상). */
    fun setGap(id: NodeId, text: String): Boolean = parse(text)?.let { gap -> setLayout(id) { it?.copy(gap = gap) } } ?: false

    /** 네 변에 같은 값으로 주는 패딩(문서 단위, 0 이상). */
    fun setPadding(id: NodeId, text: String): Boolean =
        parse(text)?.let { padding -> setLayout(id) { it?.copy(padding = Insets.all(padding)) } } ?: false

    /** 줄 바꿈을 켰을 때 줄 사이 간격(문서 단위, 0 이상). */
    fun setLineGap(id: NodeId, text: String): Boolean = parse(text)?.let { gap -> setLayout(id) { it?.copy(lineGap = gap) } } ?: false

    fun toggleWrap(id: NodeId): Boolean = setLayout(id) { it?.copy(wrap = !it.wrap) }

    /** 최소 또는 최대 크기 하나를 바꾼다. 빈 값이면 그 제한을 없앤다. 최소가 최대보다 커지면 `false`를 돌려준다. */
    fun setSizeLimit(id: NodeId, field: LimitField, text: String): Boolean {
        val node = state.document.node(id) ?: return false
        val value = if (text.isBlank()) null else parse(text) ?: return false
        val limits = node.sizeLimits.let {
            when (field) {
                LimitField.MinWidth -> it.copy(minWidth = value)
                LimitField.MaxWidth -> it.copy(maxWidth = value)
                LimitField.MinHeight -> it.copy(minHeight = value)
                LimitField.MaxHeight -> it.copy(maxHeight = value)
            }
        }
        return update(node.copy(sizeLimits = limits))
    }

    /** 자동 레이아웃 Frame 안에서 남은 공간을 채울지 정한다. */
    fun toggleFill(id: NodeId): Boolean = state.document.node(id)?.let { update(it.copy(layoutFill = !it.layoutFill)) } ?: false

    fun setConstraints(id: NodeId, constraints: LayoutConstraints): Boolean =
        state.document.node(id)?.let { update(it.copy(constraints = constraints)) } ?: false

    /** 부모가 자동 레이아웃 Frame이면 위치를 레이아웃이 정하므로 제약은 쓰이지 않는다. */
    fun isLaidOut(node: SceneNode): Boolean = (node.parentId?.let(state.document::node)?.content as? NodeContent.Frame)?.layout != null

    private fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

    private fun update(node: SceneNode): Boolean {
        if (state.document.node(node.id) == node) return true
        return state.execute(SceneCommand.UpdateProperties(node))
    }
}

/** 최소·최대 크기 입력 항목. */
internal enum class LimitField { MinWidth, MaxWidth, MinHeight, MaxHeight }
