package com.naury.kinetica.render

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asAndroidPathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.CornerRadii
import com.naury.kinetica.core.scene.Gradient
import com.naury.kinetica.core.scene.GradientType
import com.naury.kinetica.core.scene.LineCap
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.PathCommand
import com.naury.kinetica.core.scene.Shadow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * 화면(SceneLayout)과 내보내기(SceneCanvas)가 함께 쓰는 모양 그리기. 같은 함수를 써야 두 결과가 같다.
 * 좌표는 노드 좌표(문서 단위)이고, 호출하는 쪽이 노드 변환을 이미 적용해 둔다.
 */

/** 채우기·그림자·선을 그릴 노드 윤곽. 글자·이미지·그룹처럼 윤곽이 없는 노드는 `null`. */
internal fun NodeContent.outline(): Path? = when (this) {
    is NodeContent.Rectangle -> roundRect(size.width, size.height, cornerRadius, corners)

    is NodeContent.Frame -> roundRect(size.width, size.height, cornerRadius, corners)

    is NodeContent.HostSlot -> roundRect(size.width, size.height, 0.0, null)

    is NodeContent.Ellipse -> Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(0f, 0f, size.width.toFloat(), size.height.toFloat()))
    }

    is NodeContent.Polygon -> Path().apply {
        vertices().forEachIndexed { index, point -> if (index == 0) moveTo(point.xf, point.yf) else lineTo(point.xf, point.yf) }
        close()
    }

    is NodeContent.Path -> commands.toComposePath()

    is NodeContent.Text, is NodeContent.Image, is NodeContent.Group -> null
}

private fun roundRect(width: Double, height: Double, radius: Double, corners: CornerRadii?): Path {
    val limit = minOf(width, height) / 2.0
    fun r(value: Double) = CornerRadius(value.coerceIn(0.0, limit).toFloat())
    val radii = corners ?: CornerRadii.all(radius)
    return Path().apply {
        addRoundRect(
            RoundRect(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                topLeftCornerRadius = r(radii.topLeft),
                topRightCornerRadius = r(radii.topRight),
                bottomRightCornerRadius = r(radii.bottomRight),
                bottomLeftCornerRadius = r(radii.bottomLeft),
            ),
        )
    }
}

/** 채우기 붓. 그라디언트가 있으면 그것, 없으면 단색. 둘 다 없으면 `null`. */
internal fun NodeStyle.fillBrush(size: Size): Brush? = gradient?.toBrush(size) ?: fill?.let { SolidColor(it.toColor()) }

// 색 수가 16개 이하라 배열 복사 비용은 무시할 만하다. 위치가 있는 색 목록을 받는 Brush 함수는 가변 인자만 있다.
@Suppress("SpreadOperator")
internal fun Gradient.toBrush(size: Size): Brush {
    val stops = stops.map { it.position.toFloat() to it.color.toColor() }.toTypedArray()
    val center = Offset(size.width / 2f, size.height / 2f)
    return when (type) {
        GradientType.Radial -> Brush.radialGradient(*stops, center = center, radius = max(size.width, size.height).coerceAtLeast(1f) / 2f)

        GradientType.Linear -> {
            val radians = Math.toRadians(angleDegrees)
            val dx = cos(radians).toFloat()
            val dy = sin(radians).toFloat()
            // 방향으로 상자를 덮는 길이의 반. 끝 색이 모서리에 닿는다.
            val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
            Brush.linearGradient(*stops, start = center - Offset(dx, dy) * half, end = center + Offset(dx, dy) * half)
        }
    }
}

/**
 * 바깥 그림자 → 채우기 → 안쪽 그림자. 선은 [drawOutlineStroke]로 따로 그린다(자식 위에 그리기 위해).
 * [blur]가 0보다 크면 채우기를 그 반지름(노드 단위)으로 흐린다. 화면은 노드 전체에 흐림을 걸므로 0을 넘긴다.
 */
internal fun DrawScope.drawFilledOutline(path: Path, style: NodeStyle, size: Size, alpha: Float = 1f, blur: Double = 0.0) {
    style.shadows.filterNot { it.inner }.forEach { drawShadow(path, it, size, alpha) }
    style.fillBrush(size)?.let { brush ->
        if (blur > 0.0) drawBlurredPath(path, brush, size, alpha, blur, null) else drawPath(path, brush, alpha = alpha)
    }
    val inner = style.shadows.filter { it.inner }
    if (inner.isNotEmpty()) clipPath(path) { inner.forEach { drawInnerShadow(path, it, size, alpha) } }
}

/** 선(점선 포함)과 열린 경로의 끝 모양. */
internal fun DrawScope.drawOutlineStroke(
    path: Path,
    style: NodeStyle,
    alpha: Float = 1f,
    open: List<PathCommand>? = null,
    blur: Double = 0.0,
) {
    val color = style.stroke?.toColor() ?: return
    if (style.strokeWidth <= 0.0) return
    val width = style.strokeWidth.toFloat()
    val dash = style.strokeDash.takeIf { it.isNotEmpty() }?.let { values ->
        PathEffect.dashPathEffect(values.map(Double::toFloat).let { if (it.size % 2 == 1) it + it else it }.toFloatArray())
    }
    val stroke = Stroke(width = width, pathEffect = dash)
    if (blur >
        0.0
    ) {
        drawBlurredPath(path, SolidColor(color), size, alpha, blur, stroke)
    } else {
        drawPath(path, color, alpha = alpha, style = stroke)
    }
    if (open != null) drawCaps(open, style, color, width, alpha)
}

/** Compose의 drawPath에는 흐림 필터를 걸 수 없어 플랫폼 Paint에 BlurMaskFilter를 달아 그린다. 그림자와 같은 방식이다. */
// 붓·크기·투명도·흐림·선 모양이 모두 그리기에 필요한 값이라 묶을 곳이 없다.
@Suppress("LongParameterList")
private fun DrawScope.drawBlurredPath(path: Path, brush: Brush, size: Size, alpha: Float, blur: Double, stroke: Stroke?) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        when (brush) {
            is SolidColor -> color = brush.value.copy(alpha = brush.value.alpha * alpha).toArgb()

            is ShaderBrush -> {
                shader = brush.createShader(size)
                this.alpha = (alpha * OPAQUE).roundToInt()
            }
        }
        if (stroke != null) {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = stroke.width
            pathEffect = stroke.pathEffect?.asAndroidPathEffect()
        }
        maskFilter = BlurMaskFilter(blur.toFloat(), BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), paint) }
}

private fun DrawScope.drawShadow(path: Path, shadow: Shadow, size: Size, alpha: Float) {
    val shape = spreadPath(path, size, shadow.spread.toFloat()).apply {
        translate(Offset(shadow.offsetX.toFloat(), shadow.offsetY.toFloat()))
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawPath(shape.asAndroidPath(), blurPaint(shadow, alpha))
    }
}

/** 모양 바깥을 덮는 큰 사각형에서 (옮기고 줄인) 모양을 뺀 영역을 흐리게 칠한 뒤, 모양 안으로 잘라 가장자리만 남긴다. */
private fun DrawScope.drawInnerShadow(path: Path, shadow: Shadow, size: Size, alpha: Float) {
    val margin = (shadow.blur + abs(shadow.offsetX) + abs(shadow.offsetY) + abs(shadow.spread)).toFloat() + size.maxDimension
    val hole = spreadPath(path, size, -shadow.spread.toFloat()).apply {
        translate(Offset(shadow.offsetX.toFloat(), shadow.offsetY.toFloat()))
    }
    val ring = Path().apply {
        addRect(androidx.compose.ui.geometry.Rect(-margin, -margin, size.width + margin, size.height + margin))
        addPath(hole)
        fillType = PathFillType.EvenOdd
    }
    drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPath(ring.asAndroidPath(), blurPaint(shadow, alpha)) }
}

private fun blurPaint(shadow: Shadow, alpha: Float) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    color = shadow.color.toColor().copy(alpha = shadow.color.toColor().alpha * alpha).toArgb()
    if (shadow.blur > 0.0) maskFilter = BlurMaskFilter(shadow.blur.toFloat(), BlurMaskFilter.Blur.NORMAL)
}

/** 모양을 가운데 기준으로 [spread]만큼 넓힌(음수면 좁힌) 경로. 비율로 늘려 근사한다. */
private fun spreadPath(path: Path, size: Size, spread: Float): Path {
    val copy = Path().apply { addPath(path) }
    if (spread == 0f || size.width <= 0f || size.height <= 0f) return copy
    val sx = ((size.width + 2 * spread) / size.width).coerceAtLeast(0f)
    val sy = ((size.height + 2 * spread) / size.height).coerceAtLeast(0f)
    copy.transform(
        Matrix().apply {
            translate(size.width / 2f, size.height / 2f)
            scale(sx, sy)
            translate(-size.width / 2f, -size.height / 2f)
        },
    )
    return copy
}

private fun DrawScope.drawCaps(commands: List<PathCommand>, style: NodeStyle, color: Color, width: Float, alpha: Float) {
    if (commands.lastOrNull() == PathCommand.Close) return
    val points = commands.flatMap { it.points }
    if (points.size < 2) return
    val look = CapLook(color, width, alpha)
    drawCap(style.startCap, points[0], points[1], look)
    drawCap(style.endCap, points[points.lastIndex], points[points.lastIndex - 1], look)
}

/** [tip]에 끝 모양을 그린다. 화살표는 [from]→[tip] 방향을 가리킨다. */
private fun DrawScope.drawCap(cap: LineCap, tip: Vec2, from: Vec2, look: CapLook) {
    val (color, width, alpha) = look
    when (cap) {
        LineCap.None -> Unit

        LineCap.Circle -> drawCircle(color, radius = width * CIRCLE_SCALE, center = Offset(tip.xf, tip.yf), alpha = alpha)

        LineCap.Arrow -> {
            val angle = atan2(tip.y - from.y, tip.x - from.x)
            val length = max(width * ARROW_SCALE, MIN_ARROW)
            fun corner(turn: Double) = Offset(
                (tip.x - length * cos(angle + turn)).toFloat(),
                (tip.y - length * sin(angle + turn)).toFloat(),
            )
            val head = Path().apply {
                moveTo(tip.xf, tip.yf)
                corner(ARROW_SPREAD).let { lineTo(it.x, it.y) }
                corner(-ARROW_SPREAD).let { lineTo(it.x, it.y) }
                close()
            }
            drawPath(head, color, alpha = alpha)
        }
    }
}

private data class CapLook(val color: Color, val width: Float, val alpha: Float)

internal fun List<PathCommand>.toComposePath(): Path = Path().apply {
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

internal val Vec2.xf: Float get() = x.toFloat()
internal val Vec2.yf: Float get() = y.toFloat()

private const val OPAQUE = 255f
private const val CIRCLE_SCALE = 1.5f
private const val ARROW_SCALE = 4f
private const val MIN_ARROW = 6f
private const val ARROW_SPREAD = Math.PI / 7
