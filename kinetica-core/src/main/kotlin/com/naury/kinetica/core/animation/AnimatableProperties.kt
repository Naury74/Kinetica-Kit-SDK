package com.naury.kinetica.core.animation

import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.sizeOrNull
import com.naury.kinetica.core.scene.withSize

/**
 * 노드에서 애니메이션 속성 값을 읽고 쓴다. 재생(평가)·keyframe 넣기·자동 키가 같은 규칙을 쓴다.
 * 그 노드에 없는 속성(크기 없는 노드의 너비, 그림자 없는 노드의 그림자 흐림 등)은 읽으면 `null`이고 쓰면 그대로다.
 */
public object AnimatableProperties {
    // 속성마다 한 갈래인 분기다. 나누면 어느 속성을 어디서 다루는지 오히려 흐려진다.
    @Suppress("CyclomaticComplexMethod")
    public fun read(node: SceneNode, property: AnimatableProperty): KeyValue? {
        val style = node.style
        return when (property) {
            AnimatableProperty.TranslationX -> KeyValue.Scalar(node.transform.translationX)
            AnimatableProperty.TranslationY -> KeyValue.Scalar(node.transform.translationY)
            AnimatableProperty.ScaleX -> KeyValue.Scalar(node.transform.scaleX)
            AnimatableProperty.ScaleY -> KeyValue.Scalar(node.transform.scaleY)
            AnimatableProperty.RotationDegrees -> KeyValue.Scalar(node.transform.rotationDegrees)
            AnimatableProperty.Opacity -> KeyValue.Scalar(style.opacity)
            AnimatableProperty.StrokeWidth -> KeyValue.Scalar(style.strokeWidth)
            AnimatableProperty.Fill -> style.fill?.let(KeyValue::Color)
            AnimatableProperty.Stroke -> style.stroke?.let(KeyValue::Color)
            AnimatableProperty.Width -> node.content.sizeOrNull()?.let { KeyValue.Scalar(it.width) }
            AnimatableProperty.Height -> node.content.sizeOrNull()?.let { KeyValue.Scalar(it.height) }
            AnimatableProperty.CornerRadius -> corner(node.content)?.let(KeyValue::Scalar)
            AnimatableProperty.Blur -> KeyValue.Scalar(style.blur)
            AnimatableProperty.ShadowBlur -> style.shadows.firstOrNull()?.let { KeyValue.Scalar(it.blur) }
            AnimatableProperty.ShadowOffsetY -> style.shadows.firstOrNull()?.let { KeyValue.Scalar(it.offsetY) }
        }
    }

    /** [value]를 노드에 쓴다. 범위를 벗어난 값(음수 크기 등)은 허용 범위로 맞춘다. */
    @Suppress("CyclomaticComplexMethod")
    public fun write(node: SceneNode, property: AnimatableProperty, value: KeyValue): SceneNode {
        val scalar = (value as? KeyValue.Scalar)?.value
        val color = (value as? KeyValue.Color)?.color
        val style = node.style
        val transform = node.transform
        return when (property) {
            AnimatableProperty.TranslationX -> scalar?.let { node.copy(transform = transform.copy(translationX = it)) }
            AnimatableProperty.TranslationY -> scalar?.let { node.copy(transform = transform.copy(translationY = it)) }
            AnimatableProperty.ScaleX -> scalar?.let { node.copy(transform = transform.copy(scaleX = it)) }
            AnimatableProperty.ScaleY -> scalar?.let { node.copy(transform = transform.copy(scaleY = it)) }
            AnimatableProperty.RotationDegrees -> scalar?.let { node.copy(transform = transform.copy(rotationDegrees = it)) }
            AnimatableProperty.Opacity -> scalar?.let { node.copy(style = style.copy(opacity = it.coerceIn(0.0, 1.0))) }
            AnimatableProperty.StrokeWidth -> scalar?.let { node.copy(style = style.copy(strokeWidth = it.coerceAtLeast(0.0))) }
            AnimatableProperty.Fill -> color?.let { node.copy(style = style.copy(fill = it, gradient = null)) }
            AnimatableProperty.Stroke -> color?.let { node.copy(style = style.copy(stroke = it)) }
            AnimatableProperty.Width -> scalar?.let { width -> resize(node) { Size(width.coerceAtLeast(MIN_SIZE), it.height) } }
            AnimatableProperty.Height -> scalar?.let { height -> resize(node) { Size(it.width, height.coerceAtLeast(MIN_SIZE)) } }
            AnimatableProperty.CornerRadius -> scalar?.let { withCorner(node, it.coerceAtLeast(0.0)) }
            AnimatableProperty.Blur -> scalar?.let { node.copy(style = style.copy(blur = it.coerceAtLeast(0.0))) }
            AnimatableProperty.ShadowBlur -> scalar?.let { blur -> firstShadow(node) { it.copy(blur = blur.coerceAtLeast(0.0)) } }
            AnimatableProperty.ShadowOffsetY -> scalar?.let { offset -> firstShadow(node) { it.copy(offsetY = offset) } }
        } ?: node
    }

    private fun corner(content: NodeContent): Double? = when (content) {
        is NodeContent.Rectangle -> content.cornerRadius
        is NodeContent.Frame -> content.cornerRadius
        else -> null
    }

    private fun withCorner(node: SceneNode, radius: Double): SceneNode? = when (val content = node.content) {
        is NodeContent.Rectangle -> node.copy(content = content.copy(cornerRadius = radius))
        is NodeContent.Frame -> node.copy(content = content.copy(cornerRadius = radius))
        else -> null
    }

    private fun resize(node: SceneNode, change: (Size) -> Size): SceneNode? = node.content.sizeOrNull()?.let {
        node.copy(content = node.content.withSize(change(it)))
    }

    private fun firstShadow(
        node: SceneNode,
        change: (com.naury.kinetica.core.scene.Shadow) -> com.naury.kinetica.core.scene.Shadow,
    ): SceneNode? {
        val first = node.style.shadows.firstOrNull() ?: return null
        return node.copy(style = node.style.copy(shadows = listOf(change(first)) + node.style.shadows.drop(1)))
    }

    private const val MIN_SIZE = 1.0
}
