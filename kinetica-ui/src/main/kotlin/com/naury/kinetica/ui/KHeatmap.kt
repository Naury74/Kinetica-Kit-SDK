package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 히트맵. 행과 열 격자에 값의 크기를 한 색상의 진하기로 칠한다. 요일×시간 활동량이나 지역×월 매출처럼 두 축이 있는 크기에 쓴다.
 * 색은 [colors] 단계로 나눠 칠하므로 비슷한 값끼리 같은 색이 되어 무리 지어 보인다. 값이 없는 칸(`null`)은 옅은 회색으로 비운다.
 * 기본 색은 [KChartPalettes.sequential]이라 어두운 표면에서는 작은 값이 바탕 쪽으로 물러난다.
 *
 * 칸을 누르면 테두리를 두르고 값 말풍선을 띄운다. 열 축은 시간인 경우가 많아 RTL에서도 왼쪽이 처음이다(차트 공통 정책).
 * 칸 크기는 폭에 맞춰 [maxCell]까지 커지므로 넓은 화면에서 칸이 지나치게 커지지 않고, 열 이름은 겹치지 않게 건너뛰며 보인다.
 *
 * TalkBack은 표로 읽는다. 칸마다 행 이름, 열 이름, 값을 읽고 행과 열 번호를 알린다.
 *
 * ```kotlin
 * KHeatmap(activity, Modifier.fillMaxWidth(), rowLabels = listOf("월", "화", "수"), columnLabels = hours)
 * ```
 *
 * @param values 행 목록. 행마다 열 값 목록이다. 행 길이가 다르면 가장 긴 행에 맞추고 모자란 칸은 값이 없는 것으로 본다.
 *   NaN이나 무한대가 있으면 [IllegalArgumentException]을 던진다.
 * @param colors 작은 값부터 큰 값까지의 색 단계. 두 개 미만이면 [IllegalArgumentException]을 던진다.
 * @param maxCell 칸 한 변의 최대 크기
 * @param legend 아래에 '적음 ▢▢▢ 많음' 범례를 둘지
 * @param onSelect 고른 칸(행, 열)이 바뀔 때 부른다. 선택이 풀리면 `null`
 */
@Composable
public fun KHeatmap(
    values: List<List<Float?>>,
    modifier: Modifier = Modifier,
    rowLabels: List<String>? = null,
    columnLabels: List<String>? = null,
    colors: List<Color> = KChartPalettes.sequential(KineticaTheme.surface),
    maxCell: Dp = 28.dp,
    legend: Boolean = true,
    valueFormatter: (Float) -> String = KChartDefaults::formatValue,
    onSelect: ((Pair<Int, Int>?) -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val rowNames = (values.indices).map { rowLabels?.getOrNull(it) ?: stringResource(R.string.kinetica_ui_chart_point, it + 1) }
    val columns = values.maxOfOrNull { it.size } ?: 0
    val columnNames = (0 until columns).map { columnLabels?.getOrNull(it) ?: stringResource(R.string.kinetica_ui_chart_point, it + 1) }
    HeatmapCore(
        HeatmapSpec(values, colors, MIN_CELL..maxCell, valueFormatter, surface, calendar = false),
        modifier,
        HeatmapText(rowLabels.orEmpty(), columnLabels.orEmpty(), legend) { r, c ->
            stringResource(R.string.kinetica_ui_heatmap_cell, rowNames[r], columnNames[c])
        },
        onSelect,
    )
}

/**
 * 달력형 히트맵(잔디). [start]부터 [end]까지 하루를 한 칸으로, 한 주를 한 열로 놓는다. 습관, 커밋, 운동 기록에 쓴다.
 * 요일 순서는 기기 언어의 한 주 시작을 따르고, 월 이름은 그 달이 시작하는 주 위에 적는다. 기간이 길어 칸이 [minCell]보다
 * 작아지면 가로로 스크롤되고, 처음에는 가장 최근 주([end])가 보이게 끝으로 스크롤해 둔다. 시간 축이라 RTL에서도 오른쪽이 최근이다.
 *
 * TalkBack은 표로 읽고, 칸마다 '2026년 3월 2일 월요일: 4'처럼 날짜와 값을 읽는다. 값이 없는 날은 '기록 없음'으로 읽는다.
 *
 * ```kotlin
 * KCalendarHeatmap(workouts, LocalDate.now().minusWeeks(20), LocalDate.now(), Modifier.fillMaxWidth())
 * ```
 *
 * @param values 날짜별 값. 없는 날은 기록 없음으로 칠한다. NaN이나 무한대가 있으면 [IllegalArgumentException]을 던진다.
 * @param end [start]보다 앞이면 [IllegalArgumentException]을 던진다.
 * @param minCell 칸 한 변의 최소 크기. 이보다 작아지면 가로로 스크롤한다.
 */
@Composable
public fun KCalendarHeatmap(
    values: Map<LocalDate, Float>,
    start: LocalDate,
    end: LocalDate,
    modifier: Modifier = Modifier,
    colors: List<Color> = KChartPalettes.sequential(KineticaTheme.surface),
    minCell: Dp = 12.dp,
    maxCell: Dp = 18.dp,
    legend: Boolean = true,
    valueFormatter: (Float) -> String = KChartDefaults::formatValue,
    onSelect: ((LocalDate?) -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(!end.isBefore(start)) { "end는 start와 같거나 뒤여야 합니다." }
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.ROOT
    val grid = remember(values, start, end, locale) { CalendarGrid(values, start, end, locale) }
    val latestSelect = rememberUpdatedState(onSelect)
    HeatmapCore(
        HeatmapSpec(grid.values, colors, minCell..maxCell, valueFormatter, surface, calendar = true),
        modifier,
        HeatmapText(grid.rowLabels, grid.columnLabels, legend) { r, c -> grid.dateAt(r, c)?.let(grid::describe).orEmpty() },
    ) { cell -> latestSelect.value?.invoke(cell?.let { grid.dateAt(it.first, it.second) }) }
}

/** 날짜를 요일(행)×주(열) 칸으로 놓는다. */
private class CalendarGrid(source: Map<LocalDate, Float>, val start: LocalDate, val end: LocalDate, locale: Locale) {
    private val firstDay: DayOfWeek = WeekFields.of(locale).firstDayOfWeek
    private val origin: LocalDate = start.with(TemporalAdjusters.previousOrSame(firstDay))
    private val weeks = (ChronoUnit.DAYS.between(origin, end) / DAYS_PER_WEEK + 1).toInt()
    private val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    val values: List<List<Float?>> = List(DAYS_PER_WEEK) { row -> List(weeks) { col -> dateAt(row, col)?.let { source[it] } } }

    // 한 칸 건너 하나씩(월, 수, 금처럼) 요일 이름을 적어 줄 간격을 지킨다.
    val rowLabels: List<String> = List(DAYS_PER_WEEK) { row ->
        if (row % 2 == 1) firstDay.plus(row.toLong()).getDisplayName(JavaTextStyle.SHORT, locale) else ""
    }
    val columnLabels: List<String> = List(weeks) { col ->
        val weekStart = origin.plusWeeks(col.toLong())
        val firstOfMonth = (0 until DAYS_PER_WEEK).map { weekStart.plusDays(it.toLong()) }.firstOrNull { it.dayOfMonth == 1 }
        when {
            firstOfMonth != null -> firstOfMonth.month.getDisplayName(JavaTextStyle.SHORT, locale)
            col == 0 -> start.month.getDisplayName(JavaTextStyle.SHORT, locale)
            else -> ""
        }
    }

    init {
        require(source.values.all { it.isFinite() }) { "values에 NaN이나 무한대를 넣을 수 없습니다." }
    }

    fun dateAt(row: Int, column: Int): LocalDate? =
        origin.plusDays(column.toLong() * DAYS_PER_WEEK + row).takeIf { !it.isBefore(start) && !it.isAfter(end) }

    fun describe(date: LocalDate): String = formatter.format(date)
}

private class HeatmapSpec(
    val values: List<List<Float?>>,
    val colors: List<Color>,
    /** 칸 한 변의 최소..최대 크기. 최소보다 작아지면 가로로 스크롤한다. */
    val cell: ClosedRange<Dp>,
    val valueFormatter: (Float) -> String,
    val surface: SurfaceStyle,
    /** 달력 히트맵인지. 월 이름처럼 띄엄띄엄 놓인 열 이름을 모두 보이고, 처음에 끝(최근)으로 스크롤한다. */
    val calendar: Boolean,
)

/** 행·열 이름과 칸 이름. [cellName]이 빈 문자열이면 그 칸은 범위 밖이라 TalkBack에 내보내지 않는다. */
private class HeatmapText(
    val rowLabels: List<String>,
    val columnLabels: List<String>,
    val legend: Boolean,
    val cellName: @Composable (Int, Int) -> String,
)

@Composable
private fun HeatmapCore(spec: HeatmapSpec, modifier: Modifier, text: HeatmapText, onSelect: ((Pair<Int, Int>?) -> Unit)?) {
    require(spec.colors.size >= 2) { "colors는 두 개 이상이어야 합니다." }
    require(spec.values.all { row -> row.all { it == null || it.isFinite() } }) { "values에 NaN이나 무한대를 넣을 수 없습니다." }
    val rows = spec.values.size
    val columns = spec.values.maxOfOrNull { it.size } ?: 0
    val range = remember(spec.values) { heatRange(spec.values) }
    var picked by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val latestSelect = rememberUpdatedState(onSelect)
    val choose: (Pair<Int, Int>) -> Unit = { cell ->
        picked = if (picked == cell) null else cell
        latestSelect.value?.invoke(picked)
    }
    val ink = spec.surface.contentColor
    val names = List(rows) { r -> List(columns) { c -> text.cellName(r, c) } }
    val outer = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, LocalChartTextDirection provides outer) {
        Column(modifier) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val labelWidth = if (text.rowLabels.any { it.isNotEmpty() }) ROW_LABEL_WIDTH else 0.dp
                val fit = (maxWidth - labelWidth - GAP * (columns - 1)) / columns.coerceAtLeast(1)
                val cell = minOf(spec.cell.endInclusive, maxOf(fit, spec.cell.start))
                val scroll = rememberScrollState()
                if (spec.calendar) LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
                val density = LocalDensity.current
                val stepPx = with(density) { (cell + GAP).toPx() }
                val cellPx = with(density) { cell.toPx() }
                Row {
                    if (labelWidth > 0.dp) RowLabels(text.rowLabels, cell, labelWidth, ink)
                    Column(Modifier.horizontalScroll(scroll, enabled = fit < spec.cell.start)) {
                        HeatmapGrid(spec, HeatmapGeometry(rows, columns, cell, range), names, picked, choose)
                        if (text.columnLabels.isNotEmpty()) {
                            val width = (cell + GAP) * columns - GAP
                            ChartXAxis(text.columnLabels, ink, Modifier.width(width).padding(top = AXIS_GAP), spec.calendar) { i, _ ->
                                i * stepPx + cellPx / 2
                            }
                        }
                    }
                }
            }
            if (text.legend) HeatLegend(spec.colors, ink, Modifier.padding(top = LEGEND_GAP))
        }
    }
}

/** 값 범위. 값이 하나뿐이거나 모두 같으면 그 값 하나를 가장 진한 색으로 칠한다. */
private fun heatRange(values: List<List<Float?>>): ClosedFloatingPointRange<Float> {
    val all = values.flatten().filterNotNull()
    if (all.isEmpty()) return 0f..1f
    return all.min()..all.max()
}

private class HeatmapGeometry(val rows: Int, val columns: Int, val cell: Dp, val range: ClosedFloatingPointRange<Float>) {
    /** [value]가 속한 색 단계 번호 */
    fun level(value: Float, levels: Int): Int {
        val span = range.endInclusive - range.start
        if (span <= 0f) return levels - 1
        val fraction = (value - range.start) / span
        return (fraction * levels).toInt().coerceIn(0, levels - 1)
    }
}

/** 행 이름 열. 칸 높이에 맞춰 이름을 세로 가운데에 둔다. */
@Composable
private fun RowLabels(labels: List<String>, cell: Dp, width: Dp, ink: Color) {
    val style = chartAxisText(ink)
    CompositionLocalProvider(LocalLayoutDirection provides LocalChartTextDirection.current) {
        Column(Modifier.width(width).clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(GAP)) {
            labels.forEach { label ->
                Box(Modifier.height(cell).fillMaxWidth().padding(end = 6.dp), contentAlignment = Alignment.CenterEnd) {
                    ThemedText(label, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun HeatmapGrid(
    spec: HeatmapSpec,
    geometry: HeatmapGeometry,
    names: List<List<String>>,
    picked: Pair<Int, Int>?,
    choose: (Pair<Int, Int>) -> Unit,
) {
    val haptic = kHapticFeedback()
    val latestChoose = rememberUpdatedState(choose)
    val ink = spec.surface.contentColor
    val ring by animateFloatAsState(if (picked != null) 1f else 0f, spring(), label = "heat-ring")
    val held = remember { HeldCell() }
    if (picked != null) held.cell = picked
    val empty = stringResource(R.string.kinetica_ui_heatmap_empty)
    Box {
        Layout(
            content = {
                for (r in 0 until geometry.rows) {
                    for (c in 0 until geometry.columns) {
                        HeatCell(names[r][c], spec.values[r].getOrNull(c)?.let(spec.valueFormatter) ?: empty, r, c, picked == (r to c)) {
                            latestChoose.value(r to c)
                        }
                    }
                }
            },
            modifier = Modifier
                .chartTable(geometry.rows, geometry.columns)
                .pointerInput(geometry.cell) {
                    detectTapGestures { offset ->
                        val step = (geometry.cell + GAP).toPx()
                        val r = (offset.y / step).toInt()
                        val c = (offset.x / step).toInt()
                        if (r in 0 until geometry.rows && c in 0 until geometry.columns && names[r][c].isNotEmpty()) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            latestChoose.value(r to c)
                        }
                    }
                }.drawBehind { drawHeatCells(spec, geometry, names, HeatRing(held.cell, ring, ink)) },
        ) { measurables, _ ->
            val cellPx = geometry.cell.roundToPx()
            val step = (geometry.cell + GAP).toPx()
            val placeables = measurables.map { it.measure(Constraints.fixed(cellPx, cellPx)) }
            val width = (step * geometry.columns - GAP.toPx()).roundToInt().coerceAtLeast(0)
            val height = (step * geometry.rows - GAP.toPx()).roundToInt().coerceAtLeast(0)
            layout(width, height) {
                placeables.forEachIndexed { i, p ->
                    p.place((i % geometry.columns * step).roundToInt(), (i / geometry.columns * step).roundToInt())
                }
            }
        }
        // 가로 스크롤 안에서는 폭 제한이 없으므로 말풍선 영역을 격자 크기로 묶는다.
        Box(Modifier.matchParentSize()) { HeatBubble(spec, geometry, names, picked, held) }
    }
}

/** TalkBack이 읽는 투명한 칸. 범위 밖 칸([name]이 빈 문자열)은 자리만 차지한다. */
@Composable
private fun HeatCell(name: String, value: String, row: Int, column: Int, selected: Boolean, onClick: () -> Unit) {
    if (name.isEmpty()) {
        Spacer(Modifier)
        return
    }
    val description = stringResource(R.string.kinetica_ui_chart_entry, name, value)
    Spacer(
        Modifier.chartCell(row, column).semantics {
            contentDescription = description
            this.selected = selected
            onClick {
                onClick()
                true
            }
        },
    )
}

@Composable
private fun HeatBubble(spec: HeatmapSpec, geometry: HeatmapGeometry, names: List<List<String>>, picked: Pair<Int, Int>?, held: HeldCell) {
    val density = LocalDensity.current
    val empty = stringResource(R.string.kinetica_ui_heatmap_empty)
    ChartBubble(picked != null, {
        picked?.let { (r, c) ->
            val step = with(density) { (geometry.cell + GAP).toPx() }
            val cellPx = with(density) { geometry.cell.toPx() }
            ChartBubbleAnchor(c * step + cellPx / 2, r * step, r * step + cellPx, preferBelow = r < 2)
        }
    }, spec.surface) {
        val cell = held.cell ?: return@ChartBubble
        ChartBubbleColumn {
            ChartBubbleTitle(names[cell.first][cell.second])
            val value = spec.values[cell.first].getOrNull(cell.second)
            ChartBubbleRow(value?.let { spec.colors[geometry.level(it, spec.colors.size)] }, null, value?.let(spec.valueFormatter) ?: empty)
        }
    }
}

private class HeldCell {
    var cell: Pair<Int, Int>? = null
}

private class HeatRing(val cell: Pair<Int, Int>?, val level: Float, val ink: Color)

private fun DrawScope.drawHeatCells(spec: HeatmapSpec, geometry: HeatmapGeometry, names: List<List<String>>, ring: HeatRing) {
    val ink = ring.ink
    val held = ring.cell
    val cell = geometry.cell.toPx()
    val step = cell + GAP.toPx()
    val radius = CornerRadius(CELL_CORNER.toPx())
    val size = Size(cell, cell)
    for (r in 0 until geometry.rows) {
        for (c in 0 until geometry.columns) {
            if (names[r][c].isEmpty()) continue
            val value = spec.values[r].getOrNull(c)
            val color = if (value == null) spec.surface.raisedFill(EMPTY_ALPHA) else spec.colors[geometry.level(value, spec.colors.size)]
            drawRoundRect(color, Offset(c * step, r * step), size, radius)
        }
    }
    if (held != null && ring.level > 0f) {
        val width = RING_WIDTH.toPx()
        drawRoundRect(
            ink.copy(alpha = ring.level),
            Offset(held.second * step - width / 2, held.first * step - width / 2),
            Size(cell + width, cell + width),
            CornerRadius(radius.x + width / 2),
            style = Stroke(width),
        )
    }
}

/** '적음 ▢▢▢▢▢ 많음' 범례. TalkBack은 읽지 않는다. */
@Composable
private fun HeatLegend(colors: List<Color>, ink: Color, modifier: Modifier) {
    val style = chartAxisText(ink)
    CompositionLocalProvider(LocalLayoutDirection provides LocalChartTextDirection.current) {
        Row(
            modifier.clearAndSetSemantics {
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ThemedText(stringResource(R.string.kinetica_ui_heatmap_less), style = style)
            colors.forEach { Box(Modifier.size(LEGEND_CELL).background(it, RoundedCornerShape(CELL_CORNER))) }
            ThemedText(stringResource(R.string.kinetica_ui_heatmap_more), style = style)
        }
    }
}

private const val DAYS_PER_WEEK = 7
private const val EMPTY_ALPHA = 0.07f
private val MIN_CELL = 10.dp
private val GAP = 3.dp
private val CELL_CORNER = 3.dp
private val RING_WIDTH = 2.dp
private val ROW_LABEL_WIDTH = 36.dp
private val AXIS_GAP = 6.dp
private val LEGEND_GAP = 10.dp
private val LEGEND_CELL = 12.dp
