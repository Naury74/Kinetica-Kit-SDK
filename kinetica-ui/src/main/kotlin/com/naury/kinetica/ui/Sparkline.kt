package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 목록 행이나 카드 귀퉁이에 넣는 작은 선 그래프. 축과 눈금 없이 흐름만 보이고, 마지막 점을 강조한다.
 * 애니메이션이 없어 스크롤 목록에 많이 넣어도 다시 그릴 일이 적다. 시간 축은 RTL에서도 왼쪽이 처음이다([GlassLineChart] 참고).
 *
 * TalkBack은 처음 값과 마지막 값, 최저값, 최고값을 읽는다. 크기를 정하지 않으면 최소 72×28dp이다.
 *
 * ```kotlin
 * Sparkline(prices, Modifier.size(64.dp, 24.dp), color = if (prices.last() >= prices.first()) up else down)
 * ```
 *
 * @param points 왼쪽부터 차례로 그릴 값. NaN이나 무한대가 있으면 [IllegalArgumentException]을 던진다. 비어 있으면 아무것도 그리지 않는다.
 * @param color 선 색
 * @param fill 선 아래를 옅게 채울지 여부
 * @param lineWidth 선 두께
 * @param valueFormatter TalkBack에 쓸 값 문자열
 */
@Composable
public fun Sparkline(
    points: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = GlassChartDefaults.Blue,
    fill: Boolean = true,
    lineWidth: Dp = 1.5.dp,
    valueFormatter: (Float) -> String = GlassChartDefaults::formatValue,
) {
    require(points.all { it.isFinite() }) { "points에 NaN이나 무한대를 넣을 수 없습니다." }
    val description = if (points.isEmpty()) {
        ""
    } else {
        stringResource(
            R.string.kinetica_ui_chart_trend,
            valueFormatter(points.first()),
            valueFormatter(points.last()),
            valueFormatter(points.min()),
            valueFormatter(points.max()),
        )
    }
    Spacer(
        modifier
            .defaultMinSize(SPARK_WIDTH, SPARK_HEIGHT)
            .semantics { if (description.isNotEmpty()) contentDescription = description }
            .drawWithCache {
                val buffer = ChartLineBuffer()
                val stroke = Stroke(lineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val dot = lineWidth.toPx() * DOT_SCALE
                val inset = dot * HALO_SCALE
                val range = chartRange(listOf(points.toFloatArray()), includeZero = false, padding = 0f)
                buffer.ensure(points.size)
                points.forEachIndexed { i, value ->
                    buffer.xs[i] = if (points.size == 1) size.width / 2 else inset + (size.width - inset * 2) * i / (points.size - 1)
                    buffer.ys[i] = inset + (size.height - inset * 2) * (1f - range.fraction(value))
                }
                buffer.build(points.size, smooth = true, bottom = size.height)
                val brush = GlassChartDefaults.fill(color)
                onDrawBehind {
                    if (points.isEmpty()) return@onDrawBehind
                    if (fill) drawPath(buffer.area, brush, alpha = FILL_ALPHA)
                    drawPath(buffer.line, color, style = stroke)
                    val last = Offset(buffer.xs[points.size - 1], buffer.ys[points.size - 1])
                    drawCircle(color.copy(alpha = HALO_ALPHA), dot * HALO_SCALE, last)
                    drawCircle(color, dot, last)
                }
            },
    )
}

private const val DOT_SCALE = 1.6f
private const val HALO_SCALE = 2f
private const val HALO_ALPHA = 0.25f
private const val FILL_ALPHA = 0.8f
private val SPARK_WIDTH = 72.dp
private val SPARK_HEIGHT = 28.dp
