package com.naury.kinetica.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 방사형 차트. 12시 방향 축부터 시계 방향으로 축을 놓고, 계열마다 반투명하게 채운 다각형과 선을 그린다.
 * 값 범위는 [minValue]가 가운데, 최댓값이 바깥 고리다. 범위를 벗어난 값은 가운데나 바깥 고리에 붙여 그린다.
 * 처음 보일 때 계열이 차례로 가운데에서 펼쳐지고, 값이 바뀌면 이전 모양에서 새 모양으로 부드럽게 바뀐다.
 * '애니메이션 삭제' 설정에서는 바로 최종 모양을 그린다. 시계 방향은 RTL에서도 바꾸지 않는다([KDonutChart]와 같은 정책).
 *
 * 축 근처를 누르면 그 축을 강조하고 계열별 값을 말풍선으로 보인다. 같은 축을 다시 누르면 닫는다. 계열이 둘 이상이면 아래에 범례를 둔다.
 *
 * TalkBack은 전체 요약(축 수와 계열 수)을 읽고, 계열마다 '이번 시즌: 속도 82, 지구력 70, …, 가장 높은 축 속도'를 따로 읽는다.
 * 크기를 정하지 않으면 최소 240×240dp이고, 높이를 정하지 않으면 폭만큼 높이를 쓴다. 축 이름이 길거나 글자가 크면 차트가 그만큼 작아진다.
 *
 * ```kotlin
 * KRadarChart(
 *     axes = listOf("속도", "지구력", "근력", "유연성", "균형"),
 *     series = listOf(RadarSeries(listOf(82f, 70f, 64f, 55f, 77f), colors[0], "이번 시즌")),
 *     modifier = Modifier.fillMaxWidth(),
 *     maxValue = 100f,
 * )
 * ```
 *
 * @param axes 축 이름. 3개 이상 12개 이하가 아니면 [IllegalArgumentException]을 던진다.
 * @param series 계열. 값 개수가 축 수와 다르면 [IllegalArgumentException]을 던진다.
 * @param maxValue 바깥 고리 값. `null`이면 데이터 최댓값을 덮는 깔끔한 값(1, 2, 2.5, 5 × 10ⁿ 간격)으로 정한다.
 *   [minValue] 이하이거나 유한하지 않으면 [IllegalArgumentException]을 던진다.
 * @param minValue 가운데 값. 유한하지 않으면 [IllegalArgumentException]을 던진다.
 * @param rings 눈금 고리 수. 1..10 밖이면 [IllegalArgumentException]을 던진다.
 * @param valueFormatter 말풍선과 TalkBack에 쓸 값 문자열
 * @param onSelectAxis 고른 축이 바뀔 때 부른다. 선택이 풀리면 `null`
 */
@Composable
public fun KRadarChart(
    axes: List<String>,
    series: List<RadarSeries>,
    modifier: Modifier = Modifier,
    maxValue: Float? = null,
    minValue: Float = 0f,
    rings: Int = 4,
    valueFormatter: (Float) -> String = KChartDefaults::formatValue,
    onSelectAxis: ((Int?) -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(axes.size in MIN_AXES..MAX_AXES) { "axes는 ${MIN_AXES}..${MAX_AXES}개여야 합니다." }
    require(series.all { it.values.size == axes.size }) { "series마다 values 개수가 axes 수와 같아야 합니다." }
    require(rings in 1..MAX_RINGS) { "rings는 1..${MAX_RINGS}여야 합니다." }
    require(minValue.isFinite()) { "minValue는 유한한 값이어야 합니다." }
    require(maxValue == null || (maxValue.isFinite() && maxValue > minValue)) { "maxValue는 minValue보다 큰 유한한 값이어야 합니다." }
    // 호스트가 같은 SnapshotStateList를 고쳐도 참조가 그대로라 remember 키로는 모르므로, 목록 읽기를 추적한다.
    val arrays by remember(series) { derivedStateOf { series.map { it.values.toFloatArray() } } }
    val rangeOf = remember(maxValue, minValue, rings) { { data: List<FloatArray> -> radarRange(data, minValue, maxValue, rings) } }
    val still = reducedMotion()
    val reveal = rememberChartReveal(RADAR_REVEAL_MS)
    val morph = remember(rangeOf) { ChartMorph(arrays, rangeOf) }
    LaunchedEffect(morph, arrays) { morph.retarget(arrays, !still) }
    val plot = remember(axes.size) { RadarPlot(axes.size) }
    var picked by remember { mutableIntStateOf(-1) }
    val selected = picked.takeIf { it in axes.indices } ?: -1
    val latestSelect = rememberUpdatedState(onSelectAxis)
    val toggle: (Int) -> Unit = { axis ->
        picked = if (picked == axis) -1 else axis
        latestSelect.value?.invoke(picked.takeIf { it >= 0 })
    }
    val summary = pluralStringResource(R.plurals.kinetica_ui_radar_summary, axes.size, axes.size, series.size)
    val frame = RadarFrame(axes, series, morph, plot, RadarMotion(reveal::value, selected), rings)
    val ink = surface.contentColor
    CompositionLocalProvider(LocalChartTextDirection provides LocalLayoutDirection.current) {
        Layout(
            content = {
                RadarPlotArea(frame, valueFormatter, surface, toggle)
                if (series.size > 1) {
                    ChartLegend(series.mapIndexed { i, s -> seriesName(s, i) }, series.map { it.color }, ink)
                } else {
                    Box(Modifier)
                }
                val style = chartAxisText(ink).copy(textAlign = TextAlign.Center)
                axes.forEach { ThemedText(it, style = style, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            },
            modifier = modifier.defaultMinSize(RADAR_MIN_SIZE, RADAR_MIN_SIZE).chartSummary(summary),
        ) { measurables, constraints -> placeRadar(plot, measurables, constraints) }
    }
}

/**
 * 차트 영역, 범례, 축 이름을 재고 놓는다. 높이를 정해 주면 그 높이를 채우고, 아니면 폭만큼(정사각형) 쓰되 받은 범위 안에 맞춘다.
 * 축 이름이 영역 안에 들어가도록 반지름을 줄인다.
 */
private fun MeasureScope.placeRadar(plot: RadarPlot, measurables: List<Measurable>, constraints: Constraints): MeasureResult {
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
    val gap = LABEL_GAP.roundToPx()
    val legend = measurables[1].measure(Constraints(maxWidth = width))
    val legendSpace = if (legend.height > 0) legend.height + LEGEND_GAP.roundToPx() else 0
    val areaHeight = width.coerceIn(
        (constraints.minHeight - legendSpace).coerceAtLeast(0),
        (constraints.maxHeight - legendSpace).coerceAtLeast(0),
    )
    val labels = measurables.drop(2).map { it.measure(Constraints(maxWidth = (width / LABEL_WIDTH_DIVISOR).coerceAtLeast(0))) }
    val radius = fitRadarRadius(plot, labels.map { it.width to it.height }, width, areaHeight, gap)
    plot.radius = radius.coerceAtLeast(MIN_RADIUS.toPx()).coerceAtMost(minOf(width, areaHeight) / 2f)
    val area = measurables[0].measure(Constraints.fixed(width, areaHeight))
    val height = (areaHeight + legendSpace).coerceIn(constraints.minHeight, constraints.maxHeight)
    return layout(width, height) {
        area.place(0, 0)
        legend.place(0, areaHeight + legendSpace - legend.height)
        val reach = plot.radius + gap
        labels.forEachIndexed { i, label ->
            if (i >= plot.cos.size) return@forEachIndexed
            val x = width / 2f + plot.cos[i] * reach + (plot.cos[i] - 1f) * label.width / 2f
            val y = areaHeight / 2f + plot.sin[i] * reach + (plot.sin[i] - 1f) * label.height / 2f
            label.place(x.toInt(), y.toInt())
        }
    }
}

@Composable
private fun RadarPlotArea(frame: RadarFrame, valueFormatter: (Float) -> String, surface: SurfaceStyle, toggle: (Int) -> Unit) {
    val haptic = kHapticFeedback()
    val latestToggle = rememberUpdatedState(toggle)
    val plot = frame.plot
    val buffers = remember(frame.series.size) { RadarBuffers(frame.series.size) }
    val ink = surface.contentColor
    Box(
        Modifier
            .onSizeChanged { plot.size = it }
            .pointerInput(plot) {
                detectTapGestures { offset ->
                    val axis = plot.axisAt(offset.x - size.width / 2f, offset.y - size.height / 2f)
                    if (axis >= 0) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        latestToggle.value(axis)
                    }
                }
            }.drawWithCache {
                val look = radarLook(ink, plot, frame.rings, size.width / 2f, size.height / 2f)
                onDrawBehind { drawRadar(frame, look, buffers) }
            },
    ) {
        Column(Modifier.matchParentSize().chartTable(frame.series.size, 1)) {
            frame.series.forEachIndexed { i, item ->
                val description = seriesDescription(frame.axes, item, i, valueFormatter)
                Box(
                    Modifier.fillMaxWidth().weight(1f).chartCell(i, 0).semantics(mergeDescendants = true) {
                        contentDescription = description
                    },
                )
            }
        }
        val selected = frame.motion.selected
        ChartBubble(selected >= 0, { plot.anchorFor(frame, selected) }, surface) { RadarBubble(frame, selected, valueFormatter) }
    }
}

@Composable
private fun RadarBubble(frame: RadarFrame, axis: Int, valueFormatter: (Float) -> String) {
    val shown = remember { ChartIndexHolder() }
    if (axis >= 0) shown.value = axis
    val at = shown.value
    if (at !in frame.axes.indices) return
    ChartBubbleColumn {
        ChartBubbleTitle(frame.axes[at])
        frame.series.forEachIndexed { i, item ->
            ChartBubbleRow(item.color, if (frame.series.size > 1) seriesName(item, i) else null, valueFormatter(item.values[at]))
        }
    }
}

@Composable
private fun seriesName(series: RadarSeries, index: Int): String =
    series.label ?: stringResource(R.string.kinetica_ui_data_series, index + 1)

@Composable
private fun seriesDescription(axes: List<String>, series: RadarSeries, index: Int, valueFormatter: (Float) -> String): String {
    val values = axes.indices.map { stringResource(R.string.kinetica_ui_data_pair, axes[it], valueFormatter(series.values[it])) }
    val best = series.values.indices.maxByOrNull { series.values[it] } ?: 0
    val text = joinChartParts(values + stringResource(R.string.kinetica_ui_radar_best, axes[best]))
    return stringResource(R.string.kinetica_ui_chart_entry, seriesName(series, index), text)
}

/**
 * 축 이름이 차트 영역 안에 들어가는 가장 큰 반지름(px). 이름은 축 끝에서 [gap]만큼 떨어져 축 방향으로 붙는다.
 * 축 방향 성분이 작은 이름은 그 방향 제약을 받지 않는다.
 */
private fun fitRadarRadius(plot: RadarPlot, labels: List<Pair<Int, Int>>, width: Int, height: Int, gap: Int): Float {
    var radius = minOf(width, height) / 2f
    labels.forEachIndexed { i, (w, h) ->
        val dx = abs(plot.cos[i])
        val dy = abs(plot.sin[i])
        // 이름의 바깥쪽 끝 = 방향 × (반지름 + 간격) + 방향 × 크기/2 + 크기/2 가 절반 크기 안에 있어야 한다.
        if (dx > DIRECTION_EPSILON) radius = minOf(radius, (width / 2f - w / 2f * (1f + dx)) / dx - gap)
        if (dy > DIRECTION_EPSILON) radius = minOf(radius, (height / 2f - h / 2f * (1f + dy)) / dy - gap)
    }
    return radius
}

private fun radarRange(data: List<FloatArray>, minValue: Float, maxValue: Float?, rings: Int): ChartRange {
    if (maxValue != null) return ChartRange(minValue, maxValue)
    val top = data.maxOfOrNull { values -> values.maxOrNull() ?: minValue } ?: minValue
    val step = niceStep(((top - minValue) / rings).toDouble().coerceAtLeast(0.0))
    return ChartRange(minValue, (minValue + step * rings).toFloat())
}

internal const val RADAR_REVEAL_MS = 1_000
private const val MIN_AXES = 3
private const val MAX_AXES = 12
private const val MAX_RINGS = 10
private const val LABEL_WIDTH_DIVISOR = 3
private const val DIRECTION_EPSILON = 0.05f
private val LABEL_GAP = 6.dp
private val LEGEND_GAP = 12.dp
private val MIN_RADIUS = 24.dp
private val RADAR_MIN_SIZE = 240.dp
