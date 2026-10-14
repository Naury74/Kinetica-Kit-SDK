package com.naury.kinetica.core.animation

import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.CornerRadii
import com.naury.kinetica.core.scene.Gradient
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

            AnimatableProperty.GradientStart -> style.gradient?.stops?.firstOrNull()?.let { KeyValue.Color(it.color) }

            AnimatableProperty.GradientEnd -> style.gradient?.stops?.lastOrNull()?.let { KeyValue.Color(it.color) }

            AnimatableProperty.GradientAngle -> style.gradient?.let { KeyValue.Scalar(it.angleDegrees) }

            AnimatableProperty.ShadowColor -> style.shadows.firstOrNull()?.let { KeyValue.Color(it.color) }

            AnimatableProperty.ShadowOffsetX -> style.shadows.firstOrNull()?.let { KeyValue.Scalar(it.offsetX) }

            AnimatableProperty.ShadowSpread -> style.shadows.firstOrNull()?.let { KeyValue.Scalar(it.spread) }

            AnimatableProperty.CornerTopLeft, AnimatableProperty.CornerTopRight,
            AnimatableProperty.CornerBottomRight, AnimatableProperty.CornerBottomLeft,
            -> radii(node.content)?.let { KeyValue.Scalar(it.values[CORNERS.indexOf(property)]) }
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

            AnimatableProperty.GradientStart -> color?.let { c ->
                gradient(node) {
                    it.copy(
                        stops =
                            listOf(it.stops.first().copy(color = c)) + it.stops.drop(1),
                    )
                }
            }

            AnimatableProperty.GradientEnd -> color?.let { c ->
                gradient(node) {
                    it.copy(
                        stops =
                            it.stops.dropLast(1) + it.stops.last().copy(color = c),
                    )
                }
            }

            AnimatableProperty.GradientAngle -> scalar?.let { angle -> gradient(node) { it.copy(angleDegrees = angle) } }

            AnimatableProperty.ShadowColor -> color?.let { c -> firstShadow(node) { it.copy(color = c) } }

            AnimatableProperty.ShadowOffsetX -> scalar?.let { offset -> firstShadow(node) { it.copy(offsetX = offset) } }

            AnimatableProperty.ShadowSpread -> scalar?.let { spread -> firstShadow(node) { it.copy(spread = spread) } }

            AnimatableProperty.CornerTopLeft, AnimatableProperty.CornerTopRight,
            AnimatableProperty.CornerBottomRight, AnimatableProperty.CornerBottomLeft,
            -> scalar?.let { withOneCorner(node, property, it.coerceAtLeast(0.0)) }
        } ?: node
    }

    // 모서리별 반지름이 있으면 그것이 그려지므로, 전체 반지름은 왼쪽 위 값을 읽고 네 모서리에 같이 쓴다.
    private fun corner(content: NodeContent): Double? = radii(content)?.topLeft

    private fun radii(content: NodeContent): CornerRadii? = when (content) {
        is NodeContent.Rectangle -> content.corners ?: CornerRadii.all(content.cornerRadius)
        is NodeContent.Frame -> content.corners ?: CornerRadii.all(content.cornerRadius)
        else -> null
    }

    private fun withCorner(node: SceneNode, radius: Double): SceneNode? = when (val content = node.content) {
        is NodeContent.Rectangle -> node.copy(
            content = content.copy(
                cornerRadius = radius,
                corners = content.corners?.let {
                    CornerRadii.all(radius)
                },
            ),
        )

        is NodeContent.Frame -> node.copy(
            content = content.copy(
                cornerRadius = radius,
                corners = content.corners?.let {
                    CornerRadii.all(radius)
                },
            ),
        )

        else -> null
    }

    private fun withOneCorner(node: SceneNode, property: AnimatableProperty, radius: Double): SceneNode? {
        val current = radii(node.content) ?: return null
        val corners = when (property) {
            AnimatableProperty.CornerTopLeft -> current.copy(topLeft = radius)
            AnimatableProperty.CornerTopRight -> current.copy(topRight = radius)
            AnimatableProperty.CornerBottomRight -> current.copy(bottomRight = radius)
            else -> current.copy(bottomLeft = radius)
        }
        return when (val content = node.content) {
            is NodeContent.Rectangle -> node.copy(content = content.copy(corners = corners))
            is NodeContent.Frame -> node.copy(content = content.copy(corners = corners))
            else -> null
        }
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

    private fun gradient(node: SceneNode, change: (Gradient) -> Gradient): SceneNode? {
        val current = node.style.gradient?.takeIf { it.stops.isNotEmpty() } ?: return null
        return node.copy(style = node.style.copy(gradient = change(current)))
    }

    private const val MIN_SIZE = 1.0

    // CornerRadii.values와 같은 순서.
    private val CORNERS = listOf(
        AnimatableProperty.CornerTopLeft,
        AnimatableProperty.CornerTopRight,
        AnimatableProperty.CornerBottomRight,
        AnimatableProperty.CornerBottomLeft,
    )
}
