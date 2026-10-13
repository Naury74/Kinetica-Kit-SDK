package com.naury.kinetica.render

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.naury.kinetica.core.geometry.Mat3
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.PathCommand
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.core.scene.TextAlignment
import kotlin.math.roundToInt

/**
 * 문서를 [viewport]로 그린다. 편집 기록이나 도구 상태를 모르며, 손잡이·안내선 같은 편집 표시는 그리지 않는다.
 * 애니메이션을 보여 주려면 [com.naury.kinetica.motion] 등으로 평가한 문서를 넘긴다.
 *
 * @param artboardOutside artboard 밖을 칠할 색
 */
@Composable
public fun SceneCanvas(
    document: SceneDocument,
    viewport: Viewport,
    modifier: Modifier = Modifier,
    images: SceneImageProvider = SceneImageProvider.None,
    artboardOutside: Color = Color.Transparent,
) {
    val graph = remember(document) { SceneGraph(document) }
    val textMeasurer = rememberTextMeasurer(cacheSize = TEXT_LAYOUT_CACHE_SIZE)
    Canvas(modifier) {
        drawRect(artboardOutside)
        drawScene(graph, viewport, textMeasurer, images)
    }
}

/**
 * [graph]의 문서를 이 DrawScope에 [viewport]로 그린다(artboard 배경과 노드). 화면 밖 노드는 그리지 않는다.
 * 화면([SceneCanvas])과 내보내기가 같은 함수를 써서 결과가 같다.
 */
public fun DrawScope.drawScene(graph: SceneGraph, viewport: Viewport, textMeasurer: TextMeasurer, images: SceneImageProvider): Unit =
    drawScene(graph, viewport.documentToViewport(), textMeasurer, images)

/**
 * 문서 좌표를 이 DrawScope 좌표로 옮기는 [documentToCanvas]로 그린다. 확대 범위 제한이 없어 내보내기 등에 쓴다.
 * 역행렬이 없으면 아무것도 그리지 않는다.
 */
public fun DrawScope.drawScene(graph: SceneGraph, documentToCanvas: Mat3, textMeasurer: TextMeasurer, images: SceneImageProvider) {
    val canvasToDocument = documentToCanvas.inverseOrNull() ?: return
    val visible = Rect(0.0, 0.0, size.width.toDouble(), size.height.toDouble())
    val visibleDocument = canvasToDocument.mapBounds(visible)
    withTransform({ transform(documentToCanvas.toComposeMatrix()) }) {
        drawArtboard(graph.document)
        planDraw(graph, visibleDocument).forEach { item ->
            withTransform({ transform(item.world.toComposeMatrix()) }) { drawItem(item, textMeasurer, images) }
        }
    }
}

private const val TEXT_LAYOUT_CACHE_SIZE = 64
private val placeholderColor = Color(0xFF8A919E)

private fun DrawScope.drawArtboard(document: SceneDocument) {
    val background = document.artboard.background ?: return
    drawRect(background.toColor(), size = Size(document.artboard.width.toFloat(), document.artboard.height.toFloat()))
}

private fun DrawScope.drawItem(item: DrawItem, textMeasurer: TextMeasurer, images: SceneImageProvider) {
    val style = item.node.style
    val fill = style.fill?.toColor()
    val stroke = style.stroke?.toColor()?.takeIf { style.strokeWidth > 0.0 }
    val strokeStyle = Stroke(width = style.strokeWidth.toFloat())
    when (val content = item.node.content) {
        is NodeContent.Group -> Unit

        is NodeContent.Rectangle -> {
            val size = content.size.toCompose()
            val radius = content.cornerRadius.toFloat().coerceAtMost(minOf(size.width, size.height) / 2f)
            fill?.let { drawRoundRect(it, size = size, cornerRadius = CornerRadius(radius), alpha = item.alpha) }
            stroke?.let { drawRoundRect(it, size = size, cornerRadius = CornerRadius(radius), style = strokeStyle, alpha = item.alpha) }
        }

        is NodeContent.Ellipse -> {
            fill?.let { drawOval(it, size = content.size.toCompose(), alpha = item.alpha) }
            stroke?.let { drawOval(it, size = content.size.toCompose(), style = strokeStyle, alpha = item.alpha) }
        }

        is NodeContent.Path -> {
            val path = content.commands.toComposePath()
            fill?.let { drawPath(path, it, alpha = item.alpha) }
            stroke?.let { drawPath(path, it, style = strokeStyle, alpha = item.alpha) }
        }

        is NodeContent.Text -> drawTextNode(content, fill ?: Color.Black, item.alpha, textMeasurer)

        is NodeContent.Image -> drawImageNode(content, item.alpha, images)
    }
}

private fun DrawScope.drawTextNode(content: NodeContent.Text, color: Color, alpha: Float, textMeasurer: TextMeasurer) {
    val width = content.size.width.toFloat().roundToInt().coerceAtLeast(1)
    val height = content.size.height.toFloat().roundToInt().coerceAtLeast(1)
    // 캔버스가 이미 문서 단위로 확대되어 있으므로 글자 크기는 density·글꼴 배율과 무관한 문서 단위여야 한다.
    val fontSize = (content.fontSize.toFloat() / (density * fontScale))
    val layout = textMeasurer.measure(
        text = content.text,
        style = TextStyle(
            color = color,
            fontSize = fontSize.sp,
            textAlign = when (content.alignment) {
                TextAlignment.Start -> TextAlign.Start
                TextAlignment.Center -> TextAlign.Center
                TextAlignment.End -> TextAlign.End
            },
        ),
        overflow = TextOverflow.Clip,
        constraints = Constraints(maxWidth = width, maxHeight = height),
    )
    drawText(layout, alpha = alpha)
}

private fun DrawScope.drawImageNode(content: NodeContent.Image, alpha: Float, images: SceneImageProvider) {
    val size = content.size.toCompose()
    val image = images.image(content.uri)
    if (image == null) {
        drawRect(placeholderColor, size = size, style = Stroke(width = Stroke.HairlineWidth), alpha = alpha)
        drawLine(placeholderColor, Offset.Zero, Offset(size.width, size.height), alpha = alpha)
        drawLine(placeholderColor, Offset(size.width, 0f), Offset(0f, size.height), alpha = alpha)
        return
    }
    drawImage(
        image,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = alpha,
    )
}

private fun List<PathCommand>.toComposePath(): Path = Path().apply {
    forEach { command ->
        when (command) {
            is PathCommand.MoveTo -> moveTo(command.point.xf, command.point.yf)

            is PathCommand.LineTo -> lineTo(command.point.xf, command.point.yf)

            is PathCommand.QuadTo -> quadraticTo(command.control.xf, command.control.yf, command.point.xf, command.point.yf)

            is PathCommand.CubicTo -> cubicTo(
                command.control1.xf,
                command.control1.yf,
                command.control2.xf,
                command.control2.yf,
                command.point.xf,
                command.point.yf,
            )

            PathCommand.Close -> close()
        }
    }
}

private val Vec2.xf: Float get() = x.toFloat()
private val Vec2.yf: Float get() = y.toFloat()

private fun com.naury.kinetica.core.geometry.Size.toCompose(): Size = Size(width.toFloat(), height.toFloat())

internal fun ArgbColor.toColor(): Color = Color(argb)

/** 열 벡터 affine 행렬을 Compose의 4x4 열 우선 행렬로 바꾼다. */
internal fun Mat3.toComposeMatrix(): Matrix = Matrix().apply {
    values[Matrix.ScaleX] = a.toFloat()
    values[Matrix.SkewY] = b.toFloat()
    values[Matrix.SkewX] = c.toFloat()
    values[Matrix.ScaleY] = d.toFloat()
    values[Matrix.TranslateX] = tx.toFloat()
    values[Matrix.TranslateY] = ty.toFloat()
}
