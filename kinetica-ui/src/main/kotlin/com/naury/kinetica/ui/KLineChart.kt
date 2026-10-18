package com.naury.kinetica.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.roundToInt

/**
 * 계열 하나를 그리는 글래스 선 그래프. [series]를 받는 쪽과 동작이 같다.
 *
 * ```kotlin
 * KLineChart(listOf(3f, 5f, 4f, 8f, 7f), Modifier.fillMaxWidth().height(180.dp), labels = days)
 * ```
 *
 * @param points 왼쪽부터 차례로 그릴 값. NaN이나 무한대가 있으면 [IllegalArgumentException]을 던진다.
 * @param fill 선 아래를 채울 브러시. `null`이면 채우지 않는다.
 */
@Composable
public fun KLineChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    labels: List<String>? = null,
    color: Color = KineticaTheme.colors.accent,
    fill: Brush? = KChartDefaults.fill(color),
    smooth: Boolean = true,
    valueFormatter: (Float) -> String = KChartDefaults::formatValue,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val series = remember(points, color, fill) { listOf(ChartSeries(points, color, fill = fill)) }
    KLineChart(series, modifier, labels, smooth, valueFormatter, surface)
}

/**
 * 글래스 선 그래프. 처음 보일 때 선이 왼쪽부터 그려지고, 선 아래 채우기가 아래에서 차오른다.
 *
 * 손가락을 대고 가로로 끌면 세로 안내선과 점, 값 말풍선이 따라다니고 점을 지날 때마다 가볍게 햅틱을 준다.
 * 세로로 끌면 손을 떼고 바깥 스크롤에 넘긴다. 한 번 누르면 그 점의 말풍선이 고정되고, 같은 점을 다시 누르면 닫힌다.
 * 데이터가 바뀌면 계열 수와 점 수가 같을 때 이전 모양에서 새 모양으로 부드럽게 바뀌고, 다르면 바로 바뀐다.
 * '애니메이션 삭제' 접근성 설정이 켜져 있으면 처음부터 최종 모양을 그린다.
 *
 * 시간 축은 오른쪽에서 왼쪽으로 쓰는 언어에서도 왼쪽이 처음이다. 주가나 걸음 수 같은 시계열 그래프는 아랍어와 히브리어
 * 앱에서도 대부분 왼쪽에서 오른쪽으로 읽고, 숫자 역시 왼쪽에서 오른쪽으로 쓰기 때문이다. 말풍선 글자는 앱 방향을 따른다.
 *
 * TalkBack은 그래프 전체 요약(점 수와 계열별 최저, 최고, 마지막 값)을 읽고, 점마다 이름과 값을 따로 탐색할 수 있다.
 * 점에서 두 번 탭하면 그 점의 말풍선을 연다. 크기를 정하지 않으면 최소 240×160dp이다.
 *
 * ```kotlin
 * KLineChart(
 *     listOf(ChartSeries(thisWeek, KChartDefaults.Blue, "이번 주"), ChartSeries(lastWeek, KChartDefaults.Palette[2], "지난주")),
 *     Modifier.fillMaxWidth().height(200.dp),
 *     labels = days,
 * )
 * ```
 *
 * @param series 그릴 계열. 모두 같은 가로 축을 쓰며, 점 수가 다르면 가장 긴 계열에 맞춰 간격을 정한다.
 * @param labels 점마다 붙일 이름. 아래 축에 몇 개를 골라 보이고 말풍선과 TalkBack이 읽는다. 모자라면 그 점은 번호로 읽는다.
 * @param smooth 점 사이를 곡선으로 이을지 여부. 곡선은 값이 실제 최고점과 최저점을 넘어가지 않는다.
 * @param valueFormatter 말풍선과 TalkBack에 쓸 값 문자열
 */
@JvmName("KLineChartSeries")
@Composable
public fun KLineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    labels: List<String>? = null,
    smooth: Boolean = true,
    valueFormatter: (Float) -> String = KChartDefaults::formatValue,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val count = series.maxOfOrNull { it.points.size } ?: 0
    val arrays = remember(series) { series.map { it.points.toFloatArray() } }
    val still = reducedMotion()
    val reveal = rememberChartReveal(LINE_REVEAL_MS)
    val morph = remember { ChartMorph(arrays, ::lineRange) }
    LaunchedEffect(arrays) { morph.retarget(arrays, !still) }
    val plot = remember { LinePlot() }
    with(LocalDensity.current) {
        plot.inset = LINE_INSET.toPx()
        plot.dot = (DOT_RADIUS + DOT_RING).toPx()
    }
    var picked by remember { mutableIntStateOf(-1) }
    val selected = picked.takeIf { it in 0 until count } ?: -1
    val summary = lineSummary(series, count, valueFormatter)
    val buffers = remember(series.size) { List(series.size) { ChartLineBuffer() } }
    val outer = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, LocalChartTextDirection provides outer) {
        Column(modifier.defaultMinSize(LINE_MIN_WIDTH, LINE_MIN_HEIGHT).chartSummary(summary)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { plot.size = it }
                    .lineScrub(plot, count, onScrub = { picked = it }, onTap = { picked = if (picked == it) -1 else it })
                    .drawWithCache {
                        val look = LineLook(surface.contentColor, Stroke(LINE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        onDrawBehind {
                            drawLineGrid(look.ink)
                            drawSeries(LineFrame(series, morph, plot, smooth, reveal.value), buffers, look)
                            if (picked in 0 until count) drawPicked(series, morph, plot, picked, look)
                        }
                    },
            ) {
                PointTargets(series, labels, count, valueFormatter) { picked = if (picked == it) -1 else it }
                ChartBubble(selected >= 0, { plot.anchorFor(series, picked, morph) }, surface) {
                    LineBubble(series, labels, selected, valueFormatter)
                }
            }
            if (!labels.isNullOrEmpty() && count > 0) LineAxis(labels, count, plot, surface.contentColor)
        }
    }
}

/** 그리기와 손가락 위치 계산이 함께 쓰는 그래프 영역 */
private class LinePlot {
    var size by mutableStateOf(IntSize.Zero)
    var inset = 0f
    var dot = 0f

    fun x(index: Int, count: Int): Float {
        if (count <= 1) return size.width / 2f
        return inset + (size.width - inset * 2) * index / (count - 1)
    }

    fun y(fraction: Float): Float = inset + (size.height - inset * 2) * (1f - fraction)

    fun indexAt(x: Float, count: Int): Int {
        if (count <= 1) return 0
        val usable = (size.width - inset * 2).coerceAtLeast(1f)
        return ((x - inset) / usable * (count - 1)).roundToInt().coerceIn(0, count - 1)
    }

    fun anchorFor(series: List<ChartSeries>, index: Int, morph: ChartMorph): ChartBubbleAnchor? {
        val count = series.maxOfOrNull { it.points.size } ?: 0
        if (index !in 0 until count) return null
        var top = Float.MAX_VALUE
        series.forEachIndexed { s, item ->
            if (index < item.points.size) top = minOf(top, y(morph.fraction(item.points[index])))
        }
        return ChartBubbleAnchor(x(index, count), top - dot, top + dot, sideways = true)
    }
}

private class LineLook(val ink: Color, val stroke: Stroke)

private class LineFrame(val series: List<ChartSeries>, val morph: ChartMorph, val plot: LinePlot, val smooth: Boolean, val reveal: Float)

private fun lineRange(arrays: List<FloatArray>): ChartRange = chartRange(arrays, includeZero = false, padding = LINE_RANGE_PADDING)

private fun DrawScope.drawLineGrid(ink: Color) {
    val color = ink.copy(alpha = CHART_GRID_ALPHA)
    for (i in 0..GRID_LINES) {
        val y = size.height * i / GRID_LINES
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
}

private fun DrawScope.drawSeries(frame: LineFrame, buffers: List<ChartLineBuffer>, look: LineLook) {
    val lineEnd = size.width * chartStage(frame.reveal, 0f, LINE_STAGE_END)
    val rise = chartStage(frame.reveal, FILL_STAGE_START, 1f)
    val count = frame.series.maxOfOrNull { it.points.size } ?: 0
    frame.series.forEachIndexed { s, item ->
        val buffer = buffers.getOrNull(s) ?: return@forEachIndexed
        val target = frame.morph.target.getOrNull(s) ?: return@forEachIndexed
        val n = target.size
        buffer.ensure(n)
        for (i in 0 until n) {
            buffer.xs[i] = frame.plot.x(i, count)
            buffer.ys[i] = frame.plot.y(frame.morph.fraction(frame.morph.value(s, i)))
        }
        buffer.build(n, frame.smooth, size.height)
        val fill = item.fill
        if (fill != null && rise > 0f) {
            clipRect(right = lineEnd, top = size.height * (1f - rise)) { drawPath(buffer.area, fill) }
        }
        if (n == 1) {
            drawCircle(item.color, look.stroke.width * 2, Offset(buffer.xs[0], buffer.ys[0]), alpha = frame.reveal.coerceIn(0f, 1f))
        } else {
            clipRect(right = lineEnd) { drawPath(buffer.line, item.color, style = look.stroke) }
        }
    }
}

private fun DrawScope.drawPicked(series: List<ChartSeries>, morph: ChartMorph, plot: LinePlot, index: Int, look: LineLook) {
    val count = series.maxOfOrNull { it.points.size } ?: return
    val x = plot.x(index, count)
    drawLine(look.ink.copy(alpha = GUIDE_ALPHA), Offset(x, 0f), Offset(x, size.height), strokeWidth = GUIDE_WIDTH.toPx())
    series.forEachIndexed { s, item ->
        if (index >= item.points.size) return@forEachIndexed
        val center = Offset(x, plot.y(morph.fraction(morph.value(s, index))))
        drawCircle(Color.White, DOT_RADIUS.toPx() + DOT_RING.toPx(), center)
        drawCircle(item.color, DOT_RADIUS.toPx(), center)
    }
}

/** 가로로 끌면 [onScrub]으로 점을 따라가고 손을 떼면 -1을 넘긴다. 끌지 않고 떼면 [onTap]으로 그 점을 누른 것으로 본다. */
@Composable
private fun Modifier.lineScrub(plot: LinePlot, count: Int, onScrub: (Int) -> Unit, onTap: (Int) -> Unit): Modifier {
    val haptic = kHapticFeedback()
    val latestCount = rememberUpdatedState(count)
    val latestPick = rememberUpdatedState(onScrub)
    val latestTap = rememberUpdatedState(onTap)
    return pointerInput(plot) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
            if (drag == null) {
                val up = currentEvent.changes.firstOrNull { it.id == down.id }
                val still = up != null && !up.pressed && (up.position - down.position).getDistance() < viewConfiguration.touchSlop
                if (still) latestTap.value(plot.indexAt(down.position.x, latestCount.value))
                return@awaitEachGesture
            }
            var last = plot.indexAt(drag.position.x, latestCount.value)
            latestPick.value(last)
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            horizontalDrag(drag.id) { change ->
                change.consume()
                val index = plot.indexAt(change.position.x, latestCount.value)
                if (index != last) {
                    last = index
                    latestPick.value(index)
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
            }
            latestPick.value(-1)
        }
    }
}

/** 점마다 TalkBack이 탐색할 투명한 세로 띠 */
@Composable
private fun BoxScope.PointTargets(
    series: List<ChartSeries>,
    labels: List<String>?,
    count: Int,
    valueFormatter: (Float) -> String,
    onToggle: (Int) -> Unit,
) {
    if (count == 0) return
    Row(Modifier.matchParentSize()) {
        repeat(count) { index ->
            val description = pointDescription(series, labels, index, valueFormatter)
            Box(
                Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) {
                    contentDescription = description
                    onClick {
                        onToggle(index)
                        true
                    }
                },
            )
        }
    }
}

@Composable
private fun LineBubble(series: List<ChartSeries>, labels: List<String>?, index: Int, valueFormatter: (Float) -> String) {
    val shown = remember { ChartIndexHolder() }
    if (index >= 0) shown.value = index
    val at = shown.value
    if (at < 0) return
    ChartBubbleColumn {
        labels?.getOrNull(at)?.let { ChartBubbleTitle(it) }
        series.forEach { item ->
            item.points.getOrNull(at)?.let { ChartBubbleRow(item.color.takeIf { series.size > 1 }, item.label, valueFormatter(it)) }
        }
    }
}

/** 아래 축 이름. 최대 [AXIS_LABELS]개를 고르게 골라 점 아래에 두고, 글자가 커서 겹치면 겹치는 이름은 건너뛴다. */
@Composable
private fun LineAxis(labels: List<String>, count: Int, plot: LinePlot, ink: Color) {
    val picks = remember(count, labels.size) { axisPicks(minOf(count, labels.size), count) }
    val textStyle = chartAxisText(ink)
    Layout(
        content = { picks.forEach { ThemedText(labels[it], style = textStyle, maxLines = 1) } },
        modifier = Modifier.fillMaxWidth().padding(top = AXIS_GAP),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            var right = Int.MIN_VALUE
            placeables.forEachIndexed { i, text ->
                val center = plot.x(picks[i], count)
                val x = (center - text.width / 2f).roundToInt().coerceIn(0, (constraints.maxWidth - text.width).coerceAtLeast(0))
                if (x >= right) {
                    text.place(x, 0)
                    right = x + text.width + AXIS_MIN_SPACE.roundToPx()
                }
            }
        }
    }
}

/** 축에 보일 점 번호. 간격이 고르게 나뉘는 개수를 먼저 찾고, 없으면 [AXIS_LABELS]개를 반올림해 고른다. */
private fun axisPicks(available: Int, count: Int): List<Int> {
    if (available <= 0) return emptyList()
    if (available <= AXIS_LABELS) return (0 until available).toList()
    val even = (AXIS_LABELS downTo AXIS_MIN_EVEN).firstOrNull { (available - 1) % (it - 1) == 0 } ?: AXIS_LABELS
    return (0 until even).map { (it * (available - 1) / (even - 1).toFloat()).roundToInt() }.distinct().filter { it < count }
}

@Composable
private fun pointDescription(series: List<ChartSeries>, labels: List<String>?, index: Int, valueFormatter: (Float) -> String): String {
    val name = labels?.getOrNull(index) ?: stringResource(R.string.kinetica_ui_chart_point, index + 1)
    val values = series.mapNotNull { item ->
        item.points.getOrNull(index)?.let { listOfNotNull(item.label, valueFormatter(it)).joinToString(" ") }
    }
    return stringResource(R.string.kinetica_ui_chart_entry, name, values.joinToString(", "))
}

@Composable
private fun lineSummary(series: List<ChartSeries>, count: Int, valueFormatter: (Float) -> String): String {
    val parts = mutableListOf(pluralStringResource(R.plurals.kinetica_ui_chart_line, count, count))
    series.filter { it.points.isNotEmpty() }.forEach { item ->
        val stats = stringResource(
            R.string.kinetica_ui_chart_stats,
            valueFormatter(item.points.min()),
            valueFormatter(item.points.max()),
            valueFormatter(item.points.last()),
        )
        parts += if (item.label != null) stringResource(R.string.kinetica_ui_chart_entry, item.label, stats) else stats
    }
    return parts.joinToString(". ")
}

private const val LINE_REVEAL_MS = 1_100
private const val LINE_STAGE_END = 0.8f
private const val FILL_STAGE_START = 0.25f
private const val LINE_RANGE_PADDING = 0.1f
private const val GRID_LINES = 3
private const val GUIDE_ALPHA = 0.35f
private const val AXIS_LABELS = 5
private const val AXIS_MIN_EVEN = 3
private val LINE_INSET = 8.dp
private val LINE_WIDTH = 2.5.dp
private val GUIDE_WIDTH = 1.dp
private val DOT_RADIUS = 5.dp
private val DOT_RING = 2.dp
private val AXIS_GAP = 6.dp
private val AXIS_MIN_SPACE = 8.dp
private val LINE_MIN_WIDTH = 240.dp
private val LINE_MIN_HEIGHT = 160.dp
