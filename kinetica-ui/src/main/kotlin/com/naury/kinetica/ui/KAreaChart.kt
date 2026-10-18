package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 영역 차트. [stacked]면 계열을 아래에서부터 쌓아 전체 합과 구성을 함께 보이고, 아니면 계열마다 선 아래를 옅게 채워 겹쳐 그린다.
 * 쌓은 영역 사이에는 2dp 틈을 비워 색이 비슷해도 경계가 보인다.
 *
 * [KLineChart]와의 관계: 계열 하나의 흐름을 보일 때는 [KLineChart]에 `fill`을 주면 되고, 이 차트는 여러 계열의 합과 비중을
 * 보일 때 쓴다. 처음 그릴 때 왼쪽부터 드러나고, 데이터가 바뀌면 점 수가 같을 때 이전 모양에서 부드럽게 바뀐다.
 * 누르거나 가로로 끌면 세로 안내선과 계열별 값, 합계가 든 말풍선이 따라다닌다. '애니메이션 삭제' 설정에서는 바로 최종 모양을 그린다.
 *
 * 시간 축은 RTL 언어에서도 왼쪽이 처음이다([KLineChart]와 같은 정책). 아래 축 이름은 가장 긴 이름이 겹치지 않도록 건너뛰며 보이므로
 * 넓은 화면에서는 더 많이, 좁은 화면이나 큰 글자에서는 더 적게 보인다. 계열이 둘 이상이면 아래에 범례를 둔다.
 *
 * TalkBack은 전체 요약을 읽은 뒤 가로 항목마다 표의 한 행처럼 '월: 앱 3, 웹 5, 합계 8'을 읽는다. 행에서 두 번 탭하면 말풍선을 연다.
 * 크기를 정하지 않으면 최소 240×180dp이다.
 *
 * ```kotlin
 * val colors = KChartPalettes.categorical(KineticaTheme.surface)
 * KAreaChart(
 *     listOf(ChartSeries(app, colors[0], "앱"), ChartSeries(web, colors[1], "웹")),
 *     Modifier.fillMaxWidth().height(200.dp),
 *     labels = days,
 * )
 * ```
 *
 * @param series 그릴 계열. 아래(첫 계열)부터 쌓는다. 점 수가 다르면 가장 긴 계열에 맞추고 모자란 값은 0으로 본다.
 * @param labels 가로 항목 이름. 모자라면 번호로 읽는다.
 * @param stacked true면 쌓고, false면 겹친다. 쌓을 때 음수 값이 있으면 [IllegalArgumentException]을 던진다.
 * @param smooth 점 사이를 값이 넘치지 않는 곡선으로 이을지
 * @param valueFormatter 말풍선과 TalkBack에 쓸 값 문자열
 * @param onSelect 고른 항목 번호가 바뀔 때 부른다. 선택이 풀리면 `null`
 */
@Composable
public fun KAreaChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    labels: List<String>? = null,
    stacked: Boolean = true,
    smooth: Boolean = true,
    valueFormatter: (Float) -> String = KChartDefaults::formatValue,
    onSelect: ((Int?) -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(!stacked || series.all { s -> s.points.all { it >= 0f } }) { "쌓는 영역 차트에는 음수 값을 넣을 수 없습니다." }
    val data = remember(series, stacked) { AreaData(series, stacked) }
    val still = reducedMotion()
    val reveal = rememberChartReveal(AREA_REVEAL_MS)
    val morph = remember { ChartMorph(data.tops, ::areaRange) }
    LaunchedEffect(data) { morph.retarget(data.tops, !still) }
    val plot = remember { AreaPlot() }
    with(LocalDensity.current) { plot.inset = AREA_INSET.toPx() }
    plot.count = data.count
    var picked by remember { mutableIntStateOf(-1) }
    val selected = picked.takeIf { it in 0 until data.count } ?: -1
    val latestSelect = rememberUpdatedState(onSelect)
    val pick: (Int, Boolean) -> Unit = { index, toggle ->
        val next = if (toggle && picked == index) -1 else index
        if (next != picked) {
            picked = next
            latestSelect.value?.invoke(next.takeIf { it >= 0 })
        }
    }
    val guide by animateFloatAsState(if (selected >= 0) 1f else 0f, spring(), label = "area-guide")
    val names = seriesNames(series)
    val summary = pluralStringResource(R.plurals.kinetica_ui_chart_area, data.count, data.count, series.size)
    val outer = LocalLayoutDirection.current
    val frame = AreaFrame(data, series, names, labels, valueFormatter, surface)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, LocalChartTextDirection provides outer) {
        Column(modifier.defaultMinSize(AREA_MIN_WIDTH, AREA_MIN_HEIGHT).chartSummary(summary)) {
            Box(Modifier.fillMaxWidth().weight(1f).onSizeChanged { plot.size = it }.areaGestures(plot, pick)) {
                Spacer(
                    Modifier
                        .matchParentSize()
                        // 쌓은 영역 사이 틈은 그린 색을 지워서 만든다. 지운 자리가 표면을 비추려면 따로 그린 레이어가 필요하다.
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithCache {
                            val look = AreaLook(
                                List(data.tops.size) { ChartLineBuffer() },
                                surface.contentColor,
                                Stroke(LINE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                                Stroke(GAP_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                                series.map { it.fill ?: KChartDefaults.fill(it.color) },
                            )
                            onDrawBehind {
                                if (selected >= 0) plot.held = selected
                                drawArea(frame, morph, plot, look, AreaMotion(chartStage(reveal.value, 0f, 1f), guide, smooth))
                            }
                        },
                )
                AreaTargets(frame, selected) { pick(it, true) }
                ChartBubble(selected >= 0, { areaAnchor(plot, morph, data, selected) }, surface) { AreaBubble(frame, selected) }
            }
            if (!labels.isNullOrEmpty()) {
                ChartXAxis(labels.take(data.count), surface.contentColor, Modifier.fillMaxWidth().padding(top = AXIS_GAP)) { i, width ->
                    plot.x(i, width.toFloat())
                }
            }
            if (series.size > 1) ChartLegend(names, series.map { it.color }, surface.contentColor, Modifier.padding(top = LEGEND_GAP))
        }
    }
}

/** 계열 이름 앞에 색 점을 붙인 범례. 줄이 넘치면 다음 줄로 내린다. 범례 자체는 TalkBack이 읽지 않는다(값 행이 이름을 읽는다). */
@Composable
internal fun ChartLegend(names: List<String>, colors: List<Color>, ink: Color, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LocalChartTextDirection.current) {
        FlowRow(
            modifier.fillMaxWidth().clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(LEGEND_ITEM_GAP),
            verticalArrangement = Arrangement.spacedBy(LEGEND_ROW_GAP),
        ) {
            names.forEachIndexed { i, name ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LEGEND_DOT_GAP)) {
                    Box(Modifier.size(LEGEND_DOT).background(colors[i % colors.size], RoundedCornerShape(LEGEND_DOT_CORNER)))
                    ThemedText(name, style = chartAxisText(ink).copy(color = ink.copy(alpha = LEGEND_ALPHA)))
                }
            }
        }
    }
}

/** 계열 값을 같은 길이로 맞추고, 쌓을 때는 누적 값(각 계열 영역의 위쪽 경계)을 만든다. */
private class AreaData(series: List<ChartSeries>, val stacked: Boolean) {
    val count = series.maxOfOrNull { it.points.size } ?: 0
    val values: List<FloatArray> = series.map { s -> FloatArray(count) { s.points.getOrElse(it) { 0f } } }
    val tops: List<FloatArray> = if (!stacked) {
        values
    } else {
        val running = FloatArray(count)
        values.map { v ->
            for (i in 0 until count) running[i] += v[i]
            running.copyOf()
        }
    }

    fun total(index: Int): Float = values.sumOf { it[index].toDouble() }.toFloat()
}

private class AreaFrame(
    val data: AreaData,
    val series: List<ChartSeries>,
    val names: List<String>,
    val labels: List<String>?,
    val valueFormatter: (Float) -> String,
    val surface: SurfaceStyle,
)

private class AreaLook(val buffers: List<ChartLineBuffer>, val ink: Color, val line: Stroke, val gap: Stroke, val fills: List<Brush>)

private class AreaMotion(val reveal: Float, val guide: Float, val smooth: Boolean)

private class AreaPlot {
    var size by mutableStateOf(IntSize.Zero)
    var inset = 0f
    var count = 0
    var held = -1

    fun x(index: Int, width: Float = size.width.toFloat()): Float =
        if (count <= 1) width / 2 else inset + (width - inset * 2) * index / (count - 1)

    fun y(fraction: Float): Float = size.height * (1f - fraction)

    fun indexAt(x: Float): Int {
        if (count == 0) return -1
        if (count == 1) return 0
        val step = (size.width - inset * 2) / (count - 1)
        return ((x - inset) / step).roundToInt().coerceIn(0, count - 1)
    }
}

private fun Modifier.areaGestures(plot: AreaPlot, pick: (Int, Boolean) -> Unit): Modifier = this
    .pointerInput(plot) {
        detectTapGestures { offset -> plot.indexAt(offset.x).takeIf { it >= 0 }?.let { pick(it, true) } }
    }.pointerInput(plot) {
        detectHorizontalDragGestures(onDragStart = { offset ->
            plot.indexAt(offset.x).takeIf { it >= 0 }?.let { pick(it, false) }
        }) { change, _ ->
            change.consume()
            plot.indexAt(change.position.x).takeIf { it >= 0 }?.let { pick(it, false) }
        }
    }

private fun DrawScope.drawArea(frame: AreaFrame, morph: ChartMorph, plot: AreaPlot, look: AreaLook, motion: AreaMotion) {
    val buffers = look.buffers
    val count = frame.data.count
    val layers = morph.target.size
    if (count == 0 || layers == 0) return
    val base = plot.y(morph.fraction(0f))
    drawLine(look.ink.copy(alpha = CHART_GRID_ALPHA * 2), Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
    for (s in 0 until layers) {
        val buffer = buffers[s]
        buffer.ensure(count)
        for (i in 0 until count) {
            buffer.xs[i] = plot.x(i)
            buffer.ys[i] = plot.y(morph.fraction(morph.value(s, i)))
        }
        buffer.build(count, motion.smooth, base)
    }
    clipRect(right = size.width * motion.reveal) {
        if (frame.data.stacked) {
            // 위 계열부터 바닥까지 칠하고 아래 계열을 덮어 띠를 만든 뒤, 띠 경계를 지워 틈을 낸다.
            for (s in layers - 1 downTo 0) drawPath(buffers[s].area, frame.series[s].color)
            for (s in 0 until layers - 1) drawPath(buffers[s].line, Color.Black, style = look.gap, blendMode = BlendMode.Clear)
        } else {
            for (s in 0 until layers) drawPath(buffers[s].area, look.fills[s])
            for (s in 0 until layers) drawPath(buffers[s].line, frame.series[s].color, style = look.line)
        }
    }
    drawGuide(frame, plot, look, motion.guide)
}

private fun DrawScope.drawGuide(frame: AreaFrame, plot: AreaPlot, look: AreaLook, guide: Float) {
    val at = plot.held
    if (guide <= 0f || at !in 0 until frame.data.count) return
    val x = plot.x(at)
    drawLine(look.ink.copy(alpha = GUIDE_ALPHA * guide), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
    val ring = DOT_RING.toPx()
    val dot = DOT_RADIUS.toPx()
    look.buffers.forEachIndexed { s, buffer ->
        val center = Offset(buffer.xs[at], buffer.ys[at])
        // 점 둘레를 지워 영역 위에서도 점이 떠 보이게 한다.
        drawCircle(Color.Black, (dot + ring) * guide, center, blendMode = BlendMode.Clear)
        drawCircle(frame.series[s].color, dot * guide, center)
    }
}

private fun areaAnchor(plot: AreaPlot, morph: ChartMorph, data: AreaData, index: Int): ChartBubbleAnchor? {
    if (index !in 0 until data.count || plot.size == IntSize.Zero || morph.target.isEmpty()) return null
    val top = morph.target.indices.minOf { plot.y(morph.fraction(morph.value(it, index))) }
    return ChartBubbleAnchor(plot.x(index), top, top, sideways = true, leftFirst = index > data.count / 2)
}

/** 가로 항목마다 TalkBack이 표의 한 행처럼 읽는 투명한 칸 */
@Composable
private fun BoxScope.AreaTargets(frame: AreaFrame, selected: Int, choose: (Int) -> Unit) {
    val count = frame.data.count
    val descriptions = (0 until count).map { areaRowDescription(frame, it) }
    Row(Modifier.matchParentSize().chartTable(count, 1)) {
        repeat(count) { i ->
            Box(
                Modifier.weight(1f).fillMaxHeight().chartCell(i, 0).semantics(mergeDescendants = true) {
                    contentDescription = descriptions[i]
                    this.selected = i == selected
                    onClick {
                        choose(i)
                        true
                    }
                },
            )
        }
    }
}

@Composable
private fun areaRowDescription(frame: AreaFrame, index: Int): String {
    val name = frame.labels?.getOrNull(index) ?: stringResource(R.string.kinetica_ui_chart_point, index + 1)
    val parts = frame.series.indices.map { s ->
        stringResource(R.string.kinetica_ui_data_pair, frame.names[s], frame.valueFormatter(frame.data.values[s][index]))
    }
    val total = if (frame.series.size > 1) {
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
private fun AreaBubble(frame: AreaFrame, index: Int) {
    val shown = remember { ChartIndexHolder() }
    if (index >= 0) shown.value = index
    val at = shown.value
    if (at !in 0 until frame.data.count) return
    ChartBubbleColumn {
        frame.labels?.getOrNull(at)?.let { ChartBubbleTitle(it) }
        // 쌓은 차트는 위 띠가 위에 오도록 거꾸로 적는다.
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
        if (frame.series.size > 1 && frame.data.stacked) {
            ChartBubbleRow(null, stringResource(R.string.kinetica_ui_chart_total), frame.valueFormatter(frame.data.total(at)))
        }
    }
}

private fun areaRange(arrays: List<FloatArray>): ChartRange = chartRange(arrays, includeZero = true, padding = AREA_RANGE_PADDING)

private const val AREA_REVEAL_MS = 900
private const val AREA_RANGE_PADDING = 0.08f
private const val GUIDE_ALPHA = 0.35f
private const val LEGEND_ALPHA = 0.8f
private val AREA_INSET = 6.dp
private val LINE_WIDTH = 2.dp
private val GAP_WIDTH = 2.dp
private val DOT_RADIUS = 4.dp
private val DOT_RING = 2.dp
private val AXIS_GAP = 6.dp
private val LEGEND_GAP = 10.dp
private val LEGEND_ITEM_GAP = 14.dp
private val LEGEND_ROW_GAP = 6.dp
private val LEGEND_DOT_GAP = 6.dp
private val LEGEND_DOT = 10.dp
private val LEGEND_DOT_CORNER = 3.dp
private val AREA_MIN_WIDTH = 240.dp
private val AREA_MIN_HEIGHT = 180.dp
