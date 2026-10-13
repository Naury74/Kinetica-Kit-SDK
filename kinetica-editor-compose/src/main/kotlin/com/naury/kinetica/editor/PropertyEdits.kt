package com.naury.kinetica.editor

import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.BoundProperty
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.Shadow
import com.naury.kinetica.core.scene.TextSpan
import com.naury.kinetica.core.scene.TextSpans
import com.naury.kinetica.core.scene.sizeOrNull
import com.naury.kinetica.core.scene.withSize

/** 속성 패널에서 고칠 수 있는 숫자 값. */
internal enum class NumericField { X, Y, Width, Height, Rotation, Opacity, CornerRadius, FontWeight }

/**
 * 속성 패널 입력을 명령으로 바꾼다. 입력 하나가 실행 취소 한 단계다. 모션 편집 중이면 보이는 값을 기준으로 고치고
 * 애니메이션 속성은 keyframe이 된다([EditorState.execute]).
 * 숫자가 아니거나 유한하지 않거나 범위를 벗어나면 문서를 바꾸지 않고 `false`를 돌려준다.
 */
internal class PropertyEdits(private val state: EditorState) {
    /** 지금 값. 크기가 없는 노드(경로·그룹)의 너비·높이는 `null`이다. opacity는 0..100(%)이다. */
    fun value(node: SceneNode, field: NumericField): Double? = when (field) {
        NumericField.X -> node.transform.translationX

        NumericField.Y -> node.transform.translationY

        NumericField.Width -> node.content.size()?.width

        NumericField.Height -> node.content.size()?.height

        NumericField.Rotation -> node.transform.rotationDegrees

        NumericField.Opacity -> node.style.opacity * PERCENT

        NumericField.CornerRadius -> when (val content = node.content) {
            is NodeContent.Rectangle -> content.cornerRadius
            is NodeContent.Frame -> content.cornerRadius
            else -> null
        }

        NumericField.FontWeight -> (node.content as? NodeContent.Text)?.fontWeight?.toDouble()
    }

    fun setNumber(id: NodeId, field: NumericField, text: String): Boolean {
        val node = state.document.node(id) ?: return false
        val number = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite) ?: return false
        val updated = when (field) {
            NumericField.X -> node.copy(transform = node.transform.copy(translationX = number))

            NumericField.Y -> node.copy(transform = node.transform.copy(translationY = number))

            NumericField.Rotation -> node.copy(transform = node.transform.copy(rotationDegrees = number))

            NumericField.Opacity -> node.copy(style = node.style.copy(opacity = number / PERCENT)).takeIf { number in 0.0..PERCENT }

            NumericField.Width, NumericField.Height -> resized(node, field, number)

            NumericField.CornerRadius -> withCorner(node, number)

            NumericField.FontWeight -> (node.content as? NodeContent.Text)
                ?.takeIf { number == Math.rint(number) && number in MIN_WEIGHT..MAX_WEIGHT }
                ?.let { node.copy(content = it.copy(fontWeight = number.toInt())) }
        } ?: return false
        return update(updated)
    }

    /** `#RRGGBB` 또는 `#AARRGGBB`. `#`는 빼도 된다. */
    fun setFill(id: NodeId, text: String): Boolean {
        val node = state.document.node(id) ?: return false
        val hex = text.trim().let { if (it.startsWith("#")) it else "#$it" }
        val color = ArgbColor.parseHexOrNull(hex) ?: return false
        return update(node.copy(style = node.style.copy(fill = color)))
    }

    /**
     * 글자 노드의 내용을 바꾼다. [unbind]면 내용 데이터 묶음도 같은 실행 취소 단계에서 푼다.
     * [spans]를 주면 리치 텍스트 구간을 그것으로 바꾸고, 주지 않으면 지금 구간을 바뀐 글자에 맞춰 옮긴다.
     * 상한·유니코드 검사를 통과하지 못하면 `false`이고 문서는 그대로다.
     */
    fun setText(id: NodeId, text: String, unbind: Boolean = false, spans: List<TextSpan>? = null): Boolean {
        val node = state.document.node(id) ?: return false
        val content = node.content as? NodeContent.Text ?: return false
        val bindings = if (unbind) node.bindings - BoundProperty.Text else node.bindings
        val nextSpans = spans ?: TextSpans.remap(content.spans, content.text, text)
        return update(node.copy(content = content.copy(text = text, spans = nextSpans), bindings = bindings))
    }

    /** 글자 크기(문서 단위). 0 이하는 거부한다. */
    fun setFontSize(id: NodeId, text: String): Boolean {
        val node = state.document.node(id) ?: return false
        val content = node.content as? NodeContent.Text ?: return false
        val size = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return false
        return update(node.copy(content = content.copy(fontSize = size)))
    }

    /**
     * [property]를 데이터 키 [key]에 묶는다. 빈 글자면 묶음을 푼다. 키 형식이 틀리면 `false`이고 문서는 그대로다.
     */
    fun setBinding(id: NodeId, property: BoundProperty, key: String): Boolean {
        val node = state.document.node(id) ?: return false
        val trimmed = key.trim()
        val bindings = if (trimmed.isEmpty()) node.bindings - property else node.bindings + (property to trimmed)
        return update(node.copy(bindings = bindings))
    }

    /** 누르면 호스트에 넘길 동작 이름. 빈 글자면 지운다. */
    fun setAction(id: NodeId, action: String): Boolean {
        val node = state.document.node(id) ?: return false
        return update(node.copy(action = action.trim().ifEmpty { null }))
    }

    /** 바깥 그림자를 켜고 끈다. 켤 때는 옅은 기본 그림자를 맨 앞에 넣고, 끌 때는 바깥 그림자를 모두 뺀다. */
    fun toggleShadow(id: NodeId): Boolean {
        val node = state.document.node(id) ?: return false
        val outer = node.style.shadows.filterNot { it.inner }
        val shadows = if (outer.isEmpty()) {
            listOf(Shadow(ArgbColor(DEFAULT_SHADOW), offsetY = DEFAULT_SHADOW_Y, blur = DEFAULT_SHADOW_BLUR)) + node.style.shadows
        } else {
            node.style.shadows.filter { it.inner }
        }
        return update(node.copy(style = node.style.copy(shadows = shadows)))
    }

    /** Frame이 자식을 상자 안으로 자를지 바꾼다. */
    fun toggleClip(id: NodeId): Boolean {
        val node = state.document.node(id) ?: return false
        val frame = node.content as? NodeContent.Frame ?: return false
        return update(node.copy(content = frame.copy(clip = !frame.clip)))
    }

    fun toggleVisible(id: NodeId): Boolean = state.document.node(id)?.let { update(it.copy(visible = !it.visible)) } ?: false

    fun toggleLocked(id: NodeId): Boolean = state.document.node(id)?.let { update(it.copy(locked = !it.locked)) } ?: false

    private fun update(node: SceneNode): Boolean {
        if (state.document.node(node.id) == node) return true
        return state.execute(SceneCommand.UpdateProperties(node))
    }

    private fun withCorner(node: SceneNode, radius: Double): SceneNode? {
        if (radius < 0.0) return null
        return when (val content = node.content) {
            is NodeContent.Rectangle -> node.copy(content = content.copy(cornerRadius = radius))
            is NodeContent.Frame -> node.copy(content = content.copy(cornerRadius = radius))
            else -> null
        }
    }

    /** 크기를 바꿔도 pivot이 같은 비율 자리에 있게 한다. 0 이하는 거부한다. */
    private fun resized(node: SceneNode, field: NumericField, value: Double): SceneNode? {
        val size = node.content.sizeOrNull() ?: return null
        if (value <= 0.0) return null
        val next = if (field == NumericField.Width) Size(value, size.height) else Size(size.width, value)
        val fx = if (size.width > 0.0) next.width / size.width else 1.0
        val fy = if (size.height > 0.0) next.height / size.height else 1.0
        return node.copy(
            content = node.content.withSize(next),
            transform = node.transform.copy(pivotX = node.transform.pivotX * fx, pivotY = node.transform.pivotY * fy),
        )
    }

    private companion object {
        const val PERCENT = 100.0
        const val MIN_WEIGHT = 100.0
        const val MAX_WEIGHT = 900.0
        const val DEFAULT_SHADOW = 0x33000000
        const val DEFAULT_SHADOW_Y = 8.0
        const val DEFAULT_SHADOW_BLUR = 24.0
    }
}

internal fun NodeContent.size(): Size? = sizeOrNull()
