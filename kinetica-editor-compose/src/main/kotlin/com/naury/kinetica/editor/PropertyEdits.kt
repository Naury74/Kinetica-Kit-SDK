package com.naury.kinetica.editor

import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.history.KineticaSession

/** 속성 패널에서 고칠 수 있는 숫자 값. */
internal enum class NumericField { X, Y, Width, Height, Rotation, Opacity }

/**
 * 속성 패널 입력을 명령으로 바꾼다. 입력 하나가 실행 취소 한 단계다.
 * 숫자가 아니거나 유한하지 않거나 범위를 벗어나면 문서를 바꾸지 않고 `false`를 돌려준다.
 */
internal class PropertyEdits(private val session: KineticaSession) {
    /** 지금 값. 크기가 없는 노드(경로·그룹)의 너비·높이는 `null`이다. opacity는 0..100(%)이다. */
    fun value(node: SceneNode, field: NumericField): Double? = when (field) {
        NumericField.X -> node.transform.translationX
        NumericField.Y -> node.transform.translationY
        NumericField.Width -> node.content.size()?.width
        NumericField.Height -> node.content.size()?.height
        NumericField.Rotation -> node.transform.rotationDegrees
        NumericField.Opacity -> node.style.opacity * PERCENT
    }

    fun setNumber(id: NodeId, field: NumericField, text: String): Boolean {
        val node = session.document.value.node(id) ?: return false
        val number = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite) ?: return false
        val updated = when (field) {
            NumericField.X -> node.copy(transform = node.transform.copy(translationX = number))
            NumericField.Y -> node.copy(transform = node.transform.copy(translationY = number))
            NumericField.Rotation -> node.copy(transform = node.transform.copy(rotationDegrees = number))
            NumericField.Opacity -> node.copy(style = node.style.copy(opacity = number / PERCENT)).takeIf { number in 0.0..PERCENT }
            NumericField.Width, NumericField.Height -> resized(node, field, number)
        } ?: return false
        return update(updated)
    }

    /** `#RRGGBB` 또는 `#AARRGGBB`. `#`는 빼도 된다. */
    fun setFill(id: NodeId, text: String): Boolean {
        val node = session.document.value.node(id) ?: return false
        val hex = text.trim().let { if (it.startsWith("#")) it else "#$it" }
        val color = ArgbColor.parseHexOrNull(hex) ?: return false
        return update(node.copy(style = node.style.copy(fill = color)))
    }

    fun toggleVisible(id: NodeId): Boolean = session.document.value.node(id)?.let { update(it.copy(visible = !it.visible)) } ?: false

    fun toggleLocked(id: NodeId): Boolean = session.document.value.node(id)?.let { update(it.copy(locked = !it.locked)) } ?: false

    private fun update(node: SceneNode): Boolean {
        if (session.document.value.node(node.id) == node) return true
        return session.commands.execute(SceneCommand.UpdateProperties(node)) is CommandResult.Applied
    }

    /** 크기를 바꿔도 pivot이 같은 비율 자리에 있게 한다. 0 이하는 거부한다. */
    private fun resized(node: SceneNode, field: NumericField, value: Double): SceneNode? {
        val size = node.content.size() ?: return null
        if (value <= 0.0) return null
        val next = if (field == NumericField.Width) Size(value, size.height) else Size(size.width, value)
        val content = when (val content = node.content) {
            is NodeContent.Rectangle -> content.copy(size = next)
            is NodeContent.Ellipse -> content.copy(size = next)
            is NodeContent.Text -> content.copy(size = next)
            is NodeContent.Image -> content.copy(size = next)
            is NodeContent.Group, is NodeContent.Path -> return null
        }
        val fx = if (size.width > 0.0) next.width / size.width else 1.0
        val fy = if (size.height > 0.0) next.height / size.height else 1.0
        return node.copy(
            content = content,
            transform = node.transform.copy(
                pivotX = node.transform.pivotX * fx,
                pivotY =
                    node.transform.pivotY * fy,
            ),
        )
    }

    private companion object {
        const val PERCENT = 100.0
    }
}

internal fun NodeContent.size(): Size? = when (this) {
    is NodeContent.Rectangle -> size
    is NodeContent.Ellipse -> size
    is NodeContent.Text -> size
    is NodeContent.Image -> size
    is NodeContent.Group, is NodeContent.Path -> null
}
