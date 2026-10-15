package com.naury.kinetica.editor

import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.scene.CornerRadii
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneNode

/** 모서리별 반경과 다각형 입력을 명령으로 바꾼다. 입력 하나가 실행 취소 한 단계이고, 받을 수 없는 값이면 `false`를 돌려준다. */
internal class ShapeEdits(private val state: EditorState) {
    /** 모서리별 반경을 켜고 끈다. 켤 때는 지금 반경을 네 모서리에 똑같이 넣는다. */
    fun toggleCorners(id: NodeId): Boolean {
        val node = state.document.node(id) ?: return false
        val content = when (val content = node.content) {
            is NodeContent.Rectangle -> content.copy(corners = if (content.corners == null) CornerRadii.all(content.cornerRadius) else null)
            is NodeContent.Frame -> content.copy(corners = if (content.corners == null) CornerRadii.all(content.cornerRadius) else null)
            else -> return false
        }
        return update(node.copy(content = content))
    }

    /** 모서리 [index](왼쪽 위부터 시계 방향으로 0..3)의 반경을 바꾼다. 모서리별 반경이 꺼져 있으면 `false`를 돌려준다. */
    fun setCorner(id: NodeId, index: Int, text: String): Boolean {
        val radius = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return false
        val node = state.document.node(id) ?: return false
        fun CornerRadii.with(value: Double): CornerRadii? {
            val values = listOf(topLeft, topRight, bottomRight, bottomLeft).toMutableList()
            if (index !in values.indices) return null
            values[index] = value
            return CornerRadii(values[TOP_LEFT], values[TOP_RIGHT], values[BOTTOM_RIGHT], values[BOTTOM_LEFT])
        }
        val content = when (val content = node.content) {
            is NodeContent.Rectangle -> content.corners?.with(radius)?.let { content.copy(corners = it) }
            is NodeContent.Frame -> content.corners?.with(radius)?.let { content.copy(corners = it) }
            else -> null
        } ?: return false
        return update(node.copy(content = content))
    }

    fun setSides(id: NodeId, text: String): Boolean {
        val sides = text.trim().toIntOrNull() ?: return false
        return editPolygon(id) { it.copy(sides = sides) }
    }

    /** 별 비율. 1이면 정다각형이다. */
    fun setInnerRatio(id: NodeId, text: String): Boolean {
        val ratio = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite) ?: return false
        return editPolygon(id) { it.copy(innerRatio = ratio) }
    }

    private fun editPolygon(id: NodeId, change: (NodeContent.Polygon) -> NodeContent.Polygon): Boolean {
        val node = state.document.node(id) ?: return false
        val polygon = node.content as? NodeContent.Polygon ?: return false
        return update(node.copy(content = change(polygon)))
    }

    private fun update(node: SceneNode): Boolean {
        if (state.document.node(node.id) == node) return true
        return state.execute(SceneCommand.UpdateProperties(node))
    }

    private companion object {
        const val TOP_LEFT = 0
        const val TOP_RIGHT = 1
        const val BOTTOM_RIGHT = 2
        const val BOTTOM_LEFT = 3
    }
}
