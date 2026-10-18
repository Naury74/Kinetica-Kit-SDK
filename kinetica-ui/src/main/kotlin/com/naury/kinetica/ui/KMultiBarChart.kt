// 파일 이름은 대표 컴포저블인 KMultiBarChart를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/** [KMultiBarChart]에서 여러 계열을 한 항목에 놓는 방법 */
public enum class KBarMode {
    /** 계열을 한 막대에 아래부터 쌓는다. 합계와 구성을 함께 보인다. 음수 값은 넣을 수 없다. */
    Stacked,

    /** 계열마다 가는 막대를 나란히 세운다. 같은 항목 안에서 계열끼리 비교할 때 쓴다. */
    Grouped,
}

/**
 * 여러 계열 막대 그래프. [mode]로 누적 막대와 묶음 막대를 고른다. 계열이 하나면 [KBarChart]를 쓴다.
 * 누적 막대의 조각 사이와 묶음 막대 사이에는 2dp 틈이 있고, 끝쪽 모서리만 둥글다. 처음 보일 때 막대가 기준선에서 자라고,
 * 값이 바뀌면 막대 수가 같을 때 이전 길이에서 부드럽게 바뀐다. '애니메이션 삭제' 설정에서는 바로 최종 모양을 그린다.
 *
 * 항목을 누르면 그 항목만 진하게 남기고 계열별 값과 합계가 든 말풍선을 띄운다. 항목은 RTL에서도 왼쪽부터 놓는다([KBarChart]와 같은 정책).
 * 아래 축 이름은 겹치지 않게 건너뛰며 보이고, 계열이 둘 이상이면 범례를 둔다.
 *
 * TalkBack은 전체 요약을 읽은 뒤 항목마다 표의 한 행처럼 '1월: 앱 3, 웹 5, 합계 8'을 읽는다. 크기를 정하지 않으면 최소 240×180dp이다.
 *
 * ```kotlin
 * KMultiBarChart(listOf(ChartSeries(app, colors[0], "앱"), ChartSeries(web, colors[1], "웹")), Modifier.height(200.dp), labels = months)
 * ```
 *
 * @param series 계열. 점 수가 다르면 가장 긴 계열에 맞추고 모자란 값은 0으로 본다. [KBarMode.Stacked]에서 음수가 있으면 [IllegalArgumentException]을 던진다.
 * @param labels 항목 이름. 모자라면 번호로 읽는다.
 * @param onSelect 고른 항목 번호가 바뀔 때 부른다. 선택이 풀리면 `null`
 */
@Composable
public fun KMultiBarChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    labels: List<String>? = null,
    mode: KBarMode = KBarMode.Stacked,
    valueFormatter: (Float) -> String = KChartDefaults::formatValue,
    onSelect: ((Int?) -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val stacked = mode == KBarMode.Stacked
    require(!stacked || series.all { s -> s.points.all { it >= 0f } }) { "누적 막대에는 음수 값을 넣을 수 없습니다." }
    val data = remember(series, stacked) { BarsData(series, stacked) }
    val still = reducedMotion()
    val reveal = rememberChartReveal(BARS_REVEAL_MS)
    val morph = remember { ChartMorph(data.ends, ::barsRange) }
    LaunchedEffect(data) { morph.retarget(data.ends, !still) }
    val plot = rememberBarsPlot(data, series.size)
    var picked by remember { mutableIntStateOf(-1) }
    val selected = picked.takeIf { it in 0 until data.count } ?: -1
    val latestSelect = rememberUpdatedState(onSelect)
    val toggle: (Int) -> Unit = { index ->
        picked = if (picked == index) -1 else index
        latestSelect.value?.invoke(picked.takeIf { it >= 0 })
    }
    val dim = animateFloatAsState(if (selected >= 0) 1f else 0f, spring(), label = "bars-dim")
    val names = seriesNames(series)
    val summary = pluralStringResource(
        if (stacked) R.plurals.kinetica_ui_chart_stacked_bar else R.plurals.kinetica_ui_chart_grouped_bar,
        data.count,
        data.count,
        series.size,
    )
    val frame = BarsFrame(data, series, names, labels, valueFormatter, surface)
    val haptic = LocalHapticFeedback.current
    val outer = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, LocalChartTextDirection provides outer) {
        Column(modifier.defaultMinSize(BARS_MIN_WIDTH, BARS_MIN_HEIGHT).chartSummary(summary)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { plot.size = it }
                    .pointerInput(plot) {
                        detectTapGestures { offset ->
                            val index = plot.indexAt(offset.x)
                            if (index >= 0) {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                toggle(index)
                            }
                        }
                    }.drawWithCache {
                        val path = Path()
                        onDrawBehind {
                            if (selected >= 0) plot.held = selected
                            drawMultiBars(frame, morph, plot, BarsMotion(reveal.value, dim.value), path)
                        }
                    },
            ) {
                BarsTargets(frame, selected, toggle)
                ChartBubble(selected >= 0, { barsAnchor(plot, morph, data, selected) }, surface) { BarsBubble(frame, selected) }
            }
            BarsFooter(frame)
        }
    }
}

/** 막대 위치 계산. 매 구성마다 지금 데이터의 항목 수와 계열 수를 넣는다. */
@Composable
private fun rememberBarsPlot(data: BarsData, layers: Int): BarsPlot {
    val plot = remember { BarsPlot() }
    with(LocalDensity.current) {
        plot.maxThickness = (if (data.stacked) STACK_MAX else GROUP_MAX).toPx()
        plot.gap = BAR_GAP.toPx()
    }
    plot.count = data.count
    plot.layers = layers
    plot.stacked = data.stacked
    return plot
}

/** 아래 축 이름과 범례 */
@Composable
private fun BarsFooter(frame: BarsFrame) {
    val count = frame.data.count
    val ink = frame.surface.contentColor
    if (!frame.labels.isNullOrEmpty()) {
        ChartXAxis(frame.labels.take(count), ink, Modifier.fillMaxWidth().padding(top = AXIS_GAP)) { i, width ->
            if (count == 0) 0f else width / count.toFloat() * (i + CHART_HALF)
        }
    }
    if (frame.series.size > 1) ChartLegend(frame.names, frame.series.map { it.color }, ink, Modifier.padding(top = LEGEND_GAP))
}

/** 계열 이름. 이름이 없으면 'n번째 계열'이다. */
@Composable
internal fun seriesNames(series: List<ChartSeries>): List<String> =
    series.mapIndexed { i, s -> s.label ?: stringResource(R.string.kinetica_ui_data_series, i + 1) }

/** 계열 값을 같은 길이로 맞추고, 누적이면 조각마다 위쪽 끝값을 만든다. */
private class BarsData(series: List<ChartSeries>, val stacked: Boolean) {
    val count = series.maxOfOrNull { it.points.size } ?: 0
    val values: List<FloatArray> = series.map { s -> FloatArray(count) { s.points.getOrElse(it) { 0f } } }
    val ends: List<FloatArray> = if (!stacked) {
        values
    } else {
        val running = FloatArray(count)
        values.map { v ->
            for (i in 0 until count) running[i] += v[i]
            running.copyOf()
        }
    }

    /** 항목마다 값이 있는 맨 위 계열 번호. 그 조각만 끝을 둥글게 한다. */
    val topLayer = IntArray(count) { i -> values.indexOfLast { it[i] > 0f } }

    fun total(index: Int): Float = values.sumOf { it[index].toDouble() }.toFloat()
}

private class BarsFrame(
    val data: BarsData,
    val series: List<ChartSeries>,
    val names: List<String>,
    val labels: List<String>?,
    val valueFormatter: (Float) -> String,
    val surface: SurfaceStyle,
)

private class BarsMotion(val reveal: Float, val dim: Float)

private class BarsPlot {
    var size by mutableStateOf(IntSize.Zero)
    var count = 0
    var layers = 0
    var stacked = true
    var maxThickness = 0f
    var gap = 0f
    var held = -1

    fun slot(): Float = if (count == 0) 0f else size.width.toFloat() / count

    fun center(index: Int): Float = slot() * (index + CHART_HALF)

    /** 누적이면 막대 하나, 묶음이면 계열 막대 하나의 두께 */
    fun thickness(): Float = if (stacked || layers <= 1) {
        minOf(slot() * STACK_FILL, maxThickness)
    } else {
        minOf((slot() * GROUP_FILL - gap * (layers - 1)) / layers, maxThickness).coerceAtLeast(1f)
    }

    /** 묶음 막대에서 [layer]번째 계열 막대의 가운데 */
    fun groupCenter(index: Int, layer: Int): Float {
        val thick = thickness()
        val width = thick * layers + gap * (layers - 1)
        return center(index) - width / 2 + thick / 2 + layer * (thick + gap)
    }

    fun y(fraction: Float): Float = size.height * (1f - fraction)

    fun indexAt(x: Float): Int = if (slot() <= 0f) -1 else (x / slot()).toInt().coerceIn(0, count - 1)
}

private fun DrawScope.drawMultiBars(frame: BarsFrame, morph: ChartMorph, plot: BarsPlot, motion: BarsMotion, path: Path) {
    val data = frame.data
    if (data.count == 0 || morph.target.isEmpty()) return
    val base = plot.y(morph.fraction(0f))
    drawLine(frame.surface.contentColor.copy(alpha = BASELINE_ALPHA), Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
    val corner = BAR_CORNER.toPx()
    val thick = plot.thickness()
    for (i in 0 until data.count) {
        val grow = chartStage(motion.reveal, i * STAGGER / data.count, i * STAGGER / data.count + GROW_PART)
        val alpha = if (i == plot.held) 1f else lerp(1f, CHART_DIM_ALPHA, motion.dim)
        drawCategory(frame, morph, plot, CategorySpec(i, base, grow, alpha, thick, corner), path)
    }
}

private class CategorySpec(val index: Int, val base: Float, val grow: Float, val alpha: Float, val thickness: Float, val corner: Float)

/** 항목 하나의 막대들. 누적이면 아래부터 쌓고 위 조각과 사이에 틈을 둔다. */
private fun DrawScope.drawCategory(frame: BarsFrame, morph: ChartMorph, plot: BarsPlot, spec: CategorySpec, path: Path) {
    val data = frame.data
    val i = spec.index
    var below = spec.base
    for (s in morph.target.indices) {
        val end = lerp(spec.base, plot.y(morph.fraction(morph.value(s, i))), spec.grow)
        val color = frame.series[s].color
        if (!data.stacked) {
            drawSegment(SegmentSpec(plot.groupCenter(i, s), spec.thickness, spec.base, end, spec.corner), color, spec.alpha, path)
            continue
        }
        val top = s >= data.topLayer[i]
        // 조각 사이 틈은 아래 조각 위쪽을 깎아 만든다. 맨 위 조각만 끝을 둥글게 한다.
        val shortened = if (top) end else minOf(end + plot.gap, below)
        drawSegment(SegmentSpec(plot.center(i), spec.thickness, below, shortened, if (top) spec.corner else 0f), color, spec.alpha, path)
        below = end
    }
}

private class SegmentSpec(val center: Float, val thickness: Float, val base: Float, val end: Float, val corner: Float)

/** 기준선 쪽은 각지고 끝쪽 모서리만 둥근 막대 조각 */
private fun DrawScope.drawSegment(spec: SegmentSpec, color: Color, alpha: Float, path: Path) {
    val length = kotlin.math.abs(spec.end - spec.base)
    if (length < MIN_SEGMENT_PX) return
    val radius = CornerRadius(minOf(spec.corner, spec.thickness / 2, length))
    val up = spec.end < spec.base
    val left = spec.center - spec.thickness / 2
    val right = left + spec.thickness
    val rect = RoundRect(
        left,
        minOf(spec.base, spec.end),
        right,
        maxOf(spec.base, spec.end),
        topLeftCornerRadius = if (up) radius else CornerRadius.Zero,
        topRightCornerRadius = if (up) radius else CornerRadius.Zero,
        bottomLeftCornerRadius = if (up) CornerRadius.Zero else radius,
        bottomRightCornerRadius = if (up) CornerRadius.Zero else radius,
    )
    path.reset()
    path.addRoundRect(rect)
    drawPath(path, color, alpha = alpha)
}

private fun barsAnchor(plot: BarsPlot, morph: ChartMorph, data: BarsData, index: Int): ChartBubbleAnchor? {
    if (index !in 0 until data.count || plot.size == IntSize.Zero || morph.target.isEmpty()) return null
    val tops = morph.target.indices.map { plot.y(morph.fraction(morph.value(it, index))) }
    val base = plot.y(morph.fraction(0f))
    val top = minOf(tops.min(), base)
    return ChartBubbleAnchor(plot.center(index), top, maxOf(tops.max(), base))
}

@Composable
private fun BoxScope.BarsTargets(frame: BarsFrame, selected: Int, toggle: (Int) -> Unit) {
    val count = frame.data.count
    val descriptions = (0 until count).map { barsRowDescription(frame, it) }
    Row(Modifier.matchParentSize().chartTable(count, 1)) {
        repeat(count) { i ->
            Box(
                Modifier.weight(1f).fillMaxHeight().chartCell(i, 0).semantics(mergeDescendants = true) {
                    contentDescription = descriptions[i]
                    this.selected = i == selected
                    onClick {
                        toggle(i)
                        true
                    }
                },
            )
        }
    }
}

@Composable
private fun barsRowDescription(frame: BarsFrame, index: Int): String {
    val name = frame.labels?.getOrNull(index) ?: stringResource(R.string.kinetica_ui_chart_point, index + 1)
    val parts = frame.series.indices.map { s ->
        stringResource(R.string.kinetica_ui_data_pair, frame.names[s], frame.valueFormatter(frame.data.values[s][index]))
    }
    val total = if (frame.data.stacked && frame.series.size > 1) {
        stringResource(
            R.string.kinetica_ui_data_pair,
            stringResource(R.string.kinetica_ui_chart_total),
            frame.valueFormatter(frame.data.total(index)),
        )
    } else {
        ""
    }
    return stringResource(R.string.kinetica_ui_chart_entry, name, joinChartParts(parts + total))
}

@Composable
private fun BarsBubble(frame: BarsFrame, index: Int) {
    val shown = remember { ChartIndexHolder() }
    if (index >= 0) shown.value = index
    val at = shown.value
    if (at !in 0 until frame.data.count) return
    ChartBubbleColumn {
        frame.labels?.getOrNull(at)?.let { ChartBubbleTitle(it) }
        val order = if (frame.data.stacked) frame.series.indices.reversed() else frame.series.indices
        order.forEach { s ->
            ChartBubbleRow(
                frame.series[s].color,
                frame.names[s].takeIf {
                    frame.series.size > 1
                },
                frame.valueFormatter(frame.data.values[s][at]),
            )
        }
        if (frame.data.stacked && frame.series.size > 1) {
            ChartBubbleRow(null, stringResource(R.string.kinetica_ui_chart_total), frame.valueFormatter(frame.data.total(at)))
        }
    }
}

private fun barsRange(arrays: List<FloatArray>): ChartRange = chartRange(arrays, includeZero = true, padding = BARS_RANGE_PADDING)

private const val BARS_REVEAL_MS = 900
private const val BARS_RANGE_PADDING = 0.08f
private const val STAGGER = 0.4f
private const val GROW_PART = 0.6f
private const val STACK_FILL = 0.6f
private const val GROUP_FILL = 0.78f
private const val BASELINE_ALPHA = 0.22f
private const val MIN_SEGMENT_PX = 0.5f
private val STACK_MAX = 40.dp
private val GROUP_MAX = 22.dp
private val BAR_GAP = 2.dp
private val BAR_CORNER = 4.dp
private val AXIS_GAP = 6.dp
private val LEGEND_GAP = 10.dp
private val BARS_MIN_WIDTH = 240.dp
private val BARS_MIN_HEIGHT = 180.dp
