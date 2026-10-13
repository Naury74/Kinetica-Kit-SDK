package com.naury.kinetica.render

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.PathCommand
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.Shadow

/** 배경·테두리·그림자. 순서: 그림자 → 배경 → 테두리. */
@Composable
internal fun Modifier.boxStyle(node: SceneNode, cornerRadius: Double): Modifier {
    val style = node.style
    val shape = cornerShape(cornerRadius)
    var result = shadow(style.shadow, cornerRadius, oval = false)
    style.fill?.let { result = result.background(it.toColor(), shape) }
    val stroke = style.stroke
    if (stroke != null && style.strokeWidth > 0.0) result = result.borderPx(style.strokeWidth, stroke.toColor(), shape)
    return result
}

/** 흐린 그림자를 노드 뒤에 그린다. API 28 미만에서는 하드웨어 가속이 흐림을 지원하지 않아 흐림 없이 그려질 수 있다. */
internal fun Modifier.shadow(shadow: Shadow?, cornerRadius: Double, oval: Boolean): Modifier {
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
internal fun Modifier.requiredSizePx(size: Size): Modifier = with(LocalDensity.current) {
    requiredSize(size.width.toFloat().toDp(), size.height.toFloat().toDp())
}

@Composable
internal fun Modifier.borderPx(width: Double, color: Color, shape: Shape): Modifier = with(LocalDensity.current) {
    border(width.toFloat().toDp(), color, shape)
}

@Composable
internal fun cornerShape(radius: Double): Shape = with(LocalDensity.current) { RoundedCornerShape(radius.toFloat().toDp()) }

/** 캔버스가 이미 문서 단위로 확대되어 있으므로 글자 크기는 density·글꼴 배율과 무관한 문서 단위여야 한다. */
internal fun Density.unitsToSp(units: Double): TextUnit = (units.toFloat() / (density * fontScale)).sp

internal fun List<PathCommand>.toPath(): Path = Path().apply {
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

internal val PLACEHOLDER = Color(0xFF8A919E)
