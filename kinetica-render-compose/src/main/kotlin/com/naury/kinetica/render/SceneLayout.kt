package com.naury.kinetica.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.PathCommand
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.Shadow
import com.naury.kinetica.core.scene.TextAlignment

/**
 * 호스트가 [com.naury.kinetica.core.scene.NodeContent.HostSlot]의 키마다 주는 Composable. 키가 없으면 빈 틀을 그린다.
 * 내용은 노드 상자 크기로 배치되며, 호스트 데이터가 바뀌면 그대로 다시 그려진다.
 */
public typealias SceneSlots = Map<String, @Composable () -> Unit>

/**
 * 문서를 노드마다 실제 Compose 요소로 그린다(docs 30). 호스트 뷰가 실제로 동작하고 그룹 불투명도·Frame 자르기가 레이어로 합성된다.
 *
 * - 노드 배치는 문서 변환과 같다: `graphicsLayer(translation, rotation, scale, transformOrigin = pivot)`.
 * - 문서 단위 1 = 픽셀 1(배율 1). [viewport]가 전체를 옮기고 확대한다.
 * - [interactive]가 `true`면 동작([SceneNode.action])이 있는 노드를 누를 때 [onAction]을 부른다. `false`면(편집 모드) 누르기를 처리하지 않는다.
 *
 * 바인딩이나 애니메이션은 이 함수 밖에서 적용한 문서를 넘긴다.
 */
@Composable
public fun SceneLayout(
    document: SceneDocument,
    viewport: Viewport,
    modifier: Modifier = Modifier,
    images: SceneImageProvider = SceneImageProvider.None,
    slots: SceneSlots = emptyMap(),
    interactive: Boolean = false,
    onAction: (String) -> Unit = {},
) {
    val scope = NodeScope(document, images, slots, if (interactive) onAction else null)
    Box(modifier.clipToBounds()) {
        Box(
            Modifier.graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = viewport.panX.toFloat()
                translationY = viewport.panY.toFloat()
                scaleX = viewport.zoom.toFloat()
                scaleY = viewport.zoom.toFloat()
            },
        ) {
            Artboard(document)
            Children(document.rootOrder, scope)
        }
    }
}

private class NodeScope(
    val document: SceneDocument,
    val images: SceneImageProvider,
    val slots: SceneSlots,
    val onAction: ((String) -> Unit)?,
)

@Composable
private fun Artboard(document: SceneDocument) {
    val background = document.artboard.background ?: return
    Box(Modifier.requiredSizePx(Size(document.artboard.width, document.artboard.height)).background(background.toColor()))
}

/** 자식을 각자의 크기로 재고 원점에 놓는다. 위치는 자식의 graphicsLayer가 정한다. 컨테이너 크기는 [size](없으면 0)다. */
@Composable
private fun Children(ids: List<NodeId>, scope: NodeScope, size: Size? = null) {
    Layout(content = { ids.forEach { id -> scope.document.nodes[id]?.let { NodeView(it, scope) } } }) { measurables, _ ->
        val placeables = measurables.map { it.measure(Constraints()) }
        layout(size?.width?.toInt() ?: 0, size?.height?.toInt() ?: 0) { placeables.forEach { it.place(0, 0) } }
    }
}

@Composable
private fun NodeView(node: SceneNode, scope: NodeScope) {
    if (!node.visible || node.style.opacity <= 0.0) return
    val placed = Modifier.placement(node).actionable(node, scope)
    when (val content = node.content) {
        is NodeContent.Group -> Box(placed) { Children(content.childIds, scope) }
        is NodeContent.Frame -> FrameView(node, content, placed, scope)
        is NodeContent.Rectangle -> Box(placed.requiredSizePx(content.size).boxStyle(node, content.cornerRadius))
        is NodeContent.Ellipse -> EllipseView(node, content, placed)
        is NodeContent.Path -> PathView(node, content, placed)
        is NodeContent.Text -> TextView(node, content, placed)
        is NodeContent.Image -> ImageView(content, placed, scope)
        is NodeContent.HostSlot -> HostView(node, content, placed, scope)
    }
}

/** 문서 변환과 같은 배치: translation 뒤 pivot 기준 회전·배율. 불투명도는 레이어로 합성한다. */
private fun Modifier.placement(node: SceneNode): Modifier {
    val bounds = node.localBounds
    val transform = node.transform
    return graphicsLayer {
        val width = bounds?.width?.takeIf { it > 0.0 } ?: 1.0
        val height = bounds?.height?.takeIf { it > 0.0 } ?: 1.0
        transformOrigin = TransformOrigin((transform.pivotX / width).toFloat(), (transform.pivotY / height).toFloat())
        translationX = transform.translationX.toFloat()
        translationY = transform.translationY.toFloat()
        rotationZ = transform.rotationDegrees.toFloat()
        scaleX = transform.scaleX.toFloat()
        scaleY = transform.scaleY.toFloat()
        alpha = node.style.opacity.toFloat()
    }
}

private fun Modifier.actionable(node: SceneNode, scope: NodeScope): Modifier {
    val action = node.action ?: return this
    val onAction = scope.onAction ?: return this
    return clickable { onAction(action) }
}

@Composable
private fun FrameView(node: SceneNode, content: NodeContent.Frame, placed: Modifier, scope: NodeScope) {
    val shape = cornerShape(content.cornerRadius)
    val clipped = if (content.clip) Modifier.clip(shape) else Modifier
    Box(placed.requiredSizePx(content.size).boxStyle(node, content.cornerRadius).then(clipped)) {
        Children(content.childIds, scope, content.size)
    }
}

@Composable
private fun EllipseView(node: SceneNode, content: NodeContent.Ellipse, placed: Modifier) {
    val fill = node.style.fill?.toColor()
    val stroke = node.style.stroke?.toColor()?.takeIf { node.style.strokeWidth > 0.0 }
    Canvas(placed.requiredSizePx(content.size).shadow(node.style.shadow, content.size.height / 2.0, oval = true)) {
        fill?.let { drawOval(it) }
        stroke?.let { drawOval(it, style = Stroke(node.style.strokeWidth.toFloat())) }
    }
}

@Composable
private fun PathView(node: SceneNode, content: NodeContent.Path, placed: Modifier) {
    val bounds = content.localBounds() ?: return
    val fill = node.style.fill?.toColor()
    val stroke = node.style.stroke?.toColor()?.takeIf { node.style.strokeWidth > 0.0 }
    // 경로 좌표는 노드 원점 기준이라, 범위의 왼쪽 위까지 그릴 수 있게 원점에서 오른쪽 아래 끝까지를 크기로 잡는다.
    val size = Size(maxOf(bounds.right, 0.0), maxOf(bounds.bottom, 0.0))
    Canvas(placed.requiredSizePx(size)) {
        val path = content.commands.toPath()
        fill?.let { drawPath(path, it) }
        stroke?.let { drawPath(path, it, style = Stroke(node.style.strokeWidth.toFloat())) }
    }
}

@Composable
private fun TextView(node: SceneNode, content: NodeContent.Text, placed: Modifier) {
    val density = LocalDensity.current
    val style = TextStyle(
        color = node.style.fill?.toColor() ?: Color.Black,
        fontSize = density.unitsToSp(content.fontSize),
        fontWeight = FontWeight(content.fontWeight),
        lineHeight = if (content.lineHeight > 0.0) density.unitsToSp(content.lineHeight) else TextUnit.Unspecified,
        letterSpacing = density.unitsToSp(content.letterSpacing),
        textAlign = when (content.alignment) {
            TextAlignment.Start -> TextAlign.Start
            TextAlignment.Center -> TextAlign.Center
            TextAlignment.End -> TextAlign.End
        },
    )
    BasicText(content.text, placed.requiredSizePx(content.size).clipToBounds(), style = style, overflow = TextOverflow.Clip)
}

@Composable
private fun ImageView(content: NodeContent.Image, placed: Modifier, scope: NodeScope) {
    val image = scope.images.image(content.uri)
    if (image == null) {
        Placeholder(content.size, placed)
    } else {
        Image(image, contentDescription = null, modifier = placed.requiredSizePx(content.size), contentScale = ContentScale.FillBounds)
    }
}

@Composable
private fun HostView(node: SceneNode, content: NodeContent.HostSlot, placed: Modifier, scope: NodeScope) {
    val slot = scope.slots[content.key]
    if (slot == null) {
        Placeholder(content.size, placed)
        return
    }
    Box(placed.requiredSizePx(content.size).boxStyle(node, 0.0).clipToBounds()) { slot() }
}

@Composable
private fun Placeholder(size: Size, placed: Modifier) {
    Canvas(placed.requiredSizePx(size)) {
        drawRect(PLACEHOLDER, style = Stroke(Stroke.HairlineWidth))
        drawLine(PLACEHOLDER, Offset.Zero, Offset(this.size.width, this.size.height))
        drawLine(PLACEHOLDER, Offset(this.size.width, 0f), Offset(0f, this.size.height))
    }
}

/** 배경·테두리·그림자. 순서: 그림자 → 배경 → 테두리. */
@Composable
private fun Modifier.boxStyle(node: SceneNode, cornerRadius: Double): Modifier {
    val style = node.style
    val shape = cornerShape(cornerRadius)
    var result = shadow(style.shadow, cornerRadius, oval = false)
    style.fill?.let { result = result.background(it.toColor(), shape) }
    val stroke = style.stroke
    if (stroke != null && style.strokeWidth > 0.0) result = result.borderPx(style.strokeWidth, stroke.toColor(), shape)
    return result
}

/** 흐린 그림자를 노드 뒤에 그린다. API 28 미만에서는 하드웨어 가속이 흐림을 지원하지 않아 흐림 없이 그려질 수 있다. */
private fun Modifier.shadow(shadow: Shadow?, cornerRadius: Double, oval: Boolean): Modifier {
    if (shadow == null) return this
    return drawBehind {
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = shadow.color.toColor().toArgb()
                if (shadow.blur >
                    0.0
                ) {
                    maskFilter = android.graphics.BlurMaskFilter(shadow.blur.toFloat(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
            }
            val left = shadow.offsetX.toFloat()
            val top = shadow.offsetY.toFloat()
            val rect = android.graphics.RectF(left, top, left + size.width, top + size.height)
            if (oval) {
                canvas.nativeCanvas.drawOval(rect, paint)
            } else {
                val radius = CornerRadius(cornerRadius.toFloat().coerceAtMost(minOf(size.width, size.height) / 2f))
                canvas.nativeCanvas.drawRoundRect(rect, radius.x, radius.y, paint)
            }
        }
    }
}

/** 노드 크기(문서 단위 = 픽셀)를 부모 제약과 상관없이 정확히 쓴다. */
@Composable
private fun Modifier.requiredSizePx(size: Size): Modifier = with(LocalDensity.current) {
    requiredSize(size.width.toFloat().toDp(), size.height.toFloat().toDp())
}

@Composable
private fun Modifier.borderPx(width: Double, color: Color, shape: Shape): Modifier = with(LocalDensity.current) {
    border(width.toFloat().toDp(), color, shape)
}

@Composable
private fun cornerShape(radius: Double): Shape = with(LocalDensity.current) { RoundedCornerShape(radius.toFloat().toDp()) }

/** 캔버스가 이미 문서 단위로 확대되어 있으므로 글자 크기는 density·글꼴 배율과 무관한 문서 단위여야 한다. */
private fun Density.unitsToSp(units: Double): TextUnit = (units.toFloat() / (density * fontScale)).sp

private fun List<PathCommand>.toPath(): Path = Path().apply {
    forEach { command ->
        when (command) {
            is PathCommand.MoveTo -> moveTo(command.point.x.toFloat(), command.point.y.toFloat())

            is PathCommand.LineTo -> lineTo(command.point.x.toFloat(), command.point.y.toFloat())

            is PathCommand.QuadTo -> quadraticTo(
                command.control.x.toFloat(),
                command.control.y.toFloat(),
                command.point.x.toFloat(),
                command.point.y.toFloat(),
            )

            is PathCommand.CubicTo -> cubicTo(
                command.control1.x.toFloat(),
                command.control1.y.toFloat(),
                command.control2.x.toFloat(),
                command.control2.y.toFloat(),
                command.point.x.toFloat(),
                command.point.y.toFloat(),
            )

            PathCommand.Close -> close()
        }
    }
}

private val PLACEHOLDER = Color(0xFF8A919E)
