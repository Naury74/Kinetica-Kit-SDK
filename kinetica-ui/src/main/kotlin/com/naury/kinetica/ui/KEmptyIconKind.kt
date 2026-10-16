package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** [KEmptyIcon]이 그리는 기본 그림. */
public enum class KEmptyIconKind {
    /** 검색 결과가 없을 때. 돋보기 안에 ×가 있다. */
    NoResults,

    /** 네트워크에 연결되지 않았을 때. 구름에 사선이 그어져 있다. */
    Offline,

    /** 받은 편지함이나 알림 목록이 비었을 때. 빈 트레이 모양이다. */
    EmptyInbox,

    /** 불러오지 못했을 때. 삼각형 안에 느낌표가 있다. */
    Error,
}

/**
 * 빈 화면에 쓰는 기본 그림을 선으로 그린다. 장식용이라 TalkBack은 읽지 않는다.
 *
 * @param color 선 색. 안쪽은 같은 색을 옅게 채운다.
 */
@Composable
public fun KEmptyIcon(kind: KEmptyIconKind, modifier: Modifier = Modifier, color: Color = KineticaTheme.colors.accent) {
    Spacer(
        modifier
            .size(ICON_SIZE)
            .drawWithCache {
                val s = size.minDimension
                val stroke = Stroke(s * LINE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val (fill, lines) = iconPaths(kind, s)
                val fillColor = color.copy(alpha = FILL_ALPHA)
                onDrawBehind {
                    drawPath(fill, fillColor)
                    drawPath(fill, color, style = stroke)
                    drawPath(lines, color, style = stroke)
                }
            },
    )
}

/**
 * 기본 그림의 모양. 좌표는 그림 크기를 1로 둔 값이다.
 *
 * @property circles 채우는 원들(중심 x, 중심 y, 반지름을 차례로)
 * @property polygon 채우는 다각형(x, y를 차례로)
 * @property lines 덧그리는 꺾은선들
 */
private class IconSpec(val circles: FloatArray, val polygon: FloatArray, val lines: List<FloatArray>)

private val Specs = mapOf(
    KEmptyIconKind.NoResults to IconSpec(
        circles = floatArrayOf(0.42f, 0.42f, 0.25f),
        polygon = floatArrayOf(),
        lines = listOf(
            floatArrayOf(0.6f, 0.6f, 0.82f, 0.82f),
            floatArrayOf(0.34f, 0.34f, 0.5f, 0.5f),
            floatArrayOf(0.5f, 0.34f, 0.34f, 0.5f),
        ),
    ),
    KEmptyIconKind.Offline to IconSpec(
        circles = floatArrayOf(0.3f, 0.58f, 0.14f, 0.5f, 0.46f, 0.2f, 0.7f, 0.58f, 0.14f),
        polygon = floatArrayOf(0.3f, 0.5f, 0.7f, 0.5f, 0.7f, 0.72f, 0.3f, 0.72f),
        lines = listOf(floatArrayOf(0.16f, 0.2f, 0.84f, 0.86f)),
    ),
    KEmptyIconKind.EmptyInbox to IconSpec(
        circles = floatArrayOf(),
        polygon = floatArrayOf(0.14f, 0.56f, 0.28f, 0.26f, 0.72f, 0.26f, 0.86f, 0.56f, 0.86f, 0.78f, 0.14f, 0.78f),
        lines = listOf(floatArrayOf(0.14f, 0.56f, 0.36f, 0.56f, 0.42f, 0.65f, 0.58f, 0.65f, 0.64f, 0.56f, 0.86f, 0.56f)),
    ),
    KEmptyIconKind.Error to IconSpec(
        circles = floatArrayOf(),
        polygon = floatArrayOf(0.5f, 0.16f, 0.87f, 0.8f, 0.13f, 0.8f),
        lines = listOf(floatArrayOf(0.5f, 0.4f, 0.5f, 0.58f), floatArrayOf(0.5f, 0.69f, 0.5f, 0.69f)),
    ),
)

/** [kind] 그림의 채우는 윤곽과 덧그리는 선을 [s] 크기 정사각형 좌표로 만든다. 크기가 바뀔 때만 부른다. */
private fun iconPaths(kind: KEmptyIconKind, s: Float): Pair<Path, Path> {
    val spec = Specs.getValue(kind)
    var fill = Path()
    fun union(shape: Path) {
        fill = Path().apply { op(fill, shape, PathOperation.Union) }
    }
    for (i in spec.circles.indices step CIRCLE_VALUES) {
        union(Path().apply { addOval(Rect(Offset(spec.circles[i] * s, spec.circles[i + 1] * s), spec.circles[i + 2] * s)) })
    }
    if (spec.polygon.isNotEmpty()) union(polyline(spec.polygon, s).apply { close() })
    val lines = Path()
    spec.lines.forEach { lines.addPath(polyline(it, s)) }
    return fill to lines
}

private fun polyline(points: FloatArray, s: Float): Path = Path().apply {
    moveTo(points[0] * s, points[1] * s)
    for (i in 2 until points.size step 2) lineTo(points[i] * s, points[i + 1] * s)
}

private val ICON_SIZE = 72.dp
private const val LINE_WIDTH = 0.055f
private const val FILL_ALPHA = 0.16f
private const val CIRCLE_VALUES = 3
