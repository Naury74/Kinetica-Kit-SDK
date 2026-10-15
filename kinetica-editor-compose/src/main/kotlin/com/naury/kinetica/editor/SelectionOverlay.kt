package com.naury.kinetica.editor

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.SelectionFrame
import com.naury.kinetica.core.scene.SelectionHandle
import com.naury.kinetica.guides.GuideAxis
import com.naury.kinetica.guides.SnapGuide

/**
 * 문서 위에 그리는 편집 오버레이. 문서 렌더러와 따로 그리며, 크기는 확대 배율과 상관없이 dp로 고정이다.
 * 선택 테두리 1.5dp, 크기 핸들은 10dp 사각형, 회전 핸들은 원, 가이드선 1dp.
 */
@Composable
internal fun SelectionOverlay(
    frame: SelectionFrame?,
    showHandles: Boolean,
    marquee: Rect?,
    guides: List<SnapGuide>,
    viewport: Viewport,
    theme: StudioTheme,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val outline = 1.5.dp.toPx()
        val handleSize = 10.dp.toPx()
        marquee?.let {
            drawRect(
                theme.selection.copy(alpha = MARQUEE_FILL_ALPHA),
                Offset(it.left.toFloat(), it.top.toFloat()),
                Size(it.width.toFloat(), it.height.toFloat()),
            )
            drawRect(
                theme.selection,
                Offset(it.left.toFloat(), it.top.toFloat()),
                Size(it.width.toFloat(), it.height.toFloat()),
                style = Stroke(outline),
            )
        }
        guides.forEach { guide ->
            val (start, end) = when (guide.axis) {
                GuideAxis.Vertical -> Vec2(guide.position, guide.spanStart) to Vec2(guide.position, guide.spanEnd)
                GuideAxis.Horizontal -> Vec2(guide.spanStart, guide.position) to Vec2(guide.spanEnd, guide.position)
            }
            val a = viewport.toViewport(start)
            val b = viewport.toViewport(end)
            drawLine(theme.accent, Offset(a.xf, a.yf), Offset(b.xf, b.yf), strokeWidth = 1.dp.toPx())
        }
        frame ?: return@Canvas
        val corners = frame.corners.map(viewport::toViewport)
        val path = Path().apply {
            moveTo(corners.first().xf, corners.first().yf)
            corners.drop(1).forEach { lineTo(it.xf, it.yf) }
            close()
        }
        drawPath(path, theme.selection, style = Stroke(outline))
        if (!showHandles) return@Canvas
        val rotateOffset = ROTATE_HANDLE_OFFSET_DP.dp.toPx().toDouble()
        frame.handles(viewport, rotateOffset).forEach { (handle, point) ->
            val center = Offset(point.xf, point.yf)
            if (handle == SelectionHandle.Rotate) {
                drawCircle(Color.White, handleSize / 2f, center)
                drawCircle(theme.selection, handleSize / 2f, center, style = Stroke(outline))
            } else {
                val topLeft = center - Offset(handleSize / 2f, handleSize / 2f)
                drawRect(Color.White, topLeft, Size(handleSize, handleSize))
                drawRect(theme.selection, topLeft, Size(handleSize, handleSize), style = Stroke(outline))
            }
        }
    }
}

internal const val ROTATE_HANDLE_OFFSET_DP = 20
private const val MARQUEE_FILL_ALPHA = 0.12f
private val Vec2.xf: Float get() = x.toFloat()
private val Vec2.yf: Float get() = y.toFloat()
