package com.naury.kinetica.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** [GlassCalendar]에서 고른 날짜. 하루를 고르는 [Single]과 기간을 고르는 [Range] 두 모드가 있다. */
@Immutable
public sealed class GlassCalendarSelection {
    /** 하루를 고른다. [date]가 `null`이면 아직 고른 날이 없다. */
    public data class Single(val date: LocalDate? = null) : GlassCalendarSelection()

    /**
     * 기간을 고른다. 처음 누른 날이 [start], 두 번째로 누른 날이 [end]가 되고, [end]가 `null`이면 끝 날짜를 기다리는 중이다.
     * 끝을 고른 뒤 다시 누르거나 [start]보다 앞선 날을 누르면 그 날부터 새로 시작한다.
     * [start] 없이 [end]만 있거나 [end]가 [start]보다 앞이면 [IllegalArgumentException]을 던진다.
     */
    public data class Range(val start: LocalDate? = null, val end: LocalDate? = null) : GlassCalendarSelection() {
        init {
            require(end == null || (start != null && !end.isBefore(start))) { "end는 start와 같거나 뒤여야 합니다." }
        }
    }

    /** 이 선택에서 [date]를 눌렀을 때의 다음 선택. */
    internal fun tap(date: LocalDate): GlassCalendarSelection = when (this) {
        is Single -> Single(date)

        is Range -> {
            val first = start
            if (first == null || end != null || date.isBefore(first)) {
                Range(date, null)
            } else {
                Range(first, date)
            }
        }
    }

    /** 인디케이터를 채워 그리는 날인지. 하루 모드의 고른 날, 기간 모드의 시작일과 종료일이다. */
    internal fun isEndpoint(date: LocalDate): Boolean = when (this) {
        is Single -> this.date == date
        is Range -> start == date || end == date
    }

    /** 기간의 시작일과 종료일 사이(양 끝 제외)에 있는지. */
    internal fun isInside(date: LocalDate): Boolean {
        val range = this as? Range ?: return false
        val start = range.start ?: return false
        val end = range.end ?: return false
        return date.isAfter(start) && date.isBefore(end)
    }
}

/**
 * [GlassCalendar]의 색.
 *
 * @param accent 선택한 날의 인디케이터, 오늘 테두리, 기간 띠의 색. [GlassCalendar]의 기본값은 [GlassTheme]의 `accent`다.
 * @param onAccent 선택한 날 숫자의 색
 * @param sunday 일요일 숫자의 색
 * @param saturday 토요일 숫자의 색. 평일과 같게 하려면 [Color.Unspecified]를 넘긴다.
 */
@Immutable
public data class GlassCalendarColors(
    val accent: Color = Color(ACCENT),
    val onAccent: Color = Color.White,
    val sunday: Color = Color(SUNDAY_RED),
    val saturday: Color = Color(SATURDAY_BLUE),
)

/**
 * [GlassCalendar]가 보여 주는 달과 고를 수 있는 날짜 범위. [rememberGlassCalendarState]로 만든다.
 * 보이는 달은 화면 회전 뒤에도 유지된다.
 */
@Stable
public class GlassCalendarState internal constructor(
    internal val pager: PagerState,
    internal val firstMonth: YearMonth,
    /** 고를 수 있는 날짜 범위(양 끝 포함). 범위 밖 날짜는 흐리게 그리고 누를 수 없다. */
    public val range: ClosedRange<LocalDate>,
) {
    internal val monthCount: Int = monthsBetween(firstMonth, YearMonth.from(range.endInclusive)) + 1

    /** 지금 보이는 달. 스와이프 중에는 화면을 절반 넘게 차지한 달이다. */
    public val currentMonth: YearMonth get() = monthAt(pager.currentPage)

    /** [month]로 애니메이션하며 넘긴다. [range] 밖의 달이면 가까운 끝 달로 간다. */
    public suspend fun animateScrollToMonth(month: YearMonth) {
        pager.animateScrollToPage(pageOf(month))
    }

    /** [month]로 바로 넘긴다. [range] 밖의 달이면 가까운 끝 달로 간다. */
    public suspend fun scrollToMonth(month: YearMonth) {
        pager.scrollToPage(pageOf(month))
    }

    internal fun pageOf(month: YearMonth): Int = monthsBetween(firstMonth, month).coerceIn(0, monthCount - 1)

    internal fun monthAt(page: Int): YearMonth = firstMonth.plusMonths(page.toLong())
}

/**
 * [GlassCalendar]의 상태를 만든다.
 *
 * @param initialMonth 처음 보여 줄 달. [range] 밖이면 가까운 끝 달을 보여 준다.
 * @param range 고를 수 있는 날짜 범위(양 끝 포함). 끝이 시작보다 앞이면 [IllegalArgumentException]을 던진다.
 */
@Composable
public fun rememberGlassCalendarState(
    initialMonth: YearMonth = YearMonth.now(),
    range: ClosedRange<LocalDate> = DefaultCalendarRange,
): GlassCalendarState {
    require(!range.endInclusive.isBefore(range.start)) { "range의 끝이 시작보다 앞입니다." }
    val first = YearMonth.from(range.start)
    val count = monthsBetween(first, YearMonth.from(range.endInclusive)) + 1
    val pager = rememberPagerState(initialPage = monthsBetween(first, initialMonth).coerceIn(0, count - 1)) { count }
    return remember(pager, range) { GlassCalendarState(pager, first, range) }
}

/**
 * 한 달씩 넘겨 보는 글래스 달력. 좌우로 스와이프하거나 머리글의 화살표를 누르면 달이 바뀌고, 년월 제목이 위아래로 굴러 바뀐다.
 * 요일 순서와 이름은 기기 언어를 따른다. 예를 들어 한국어와 영어(미국)는 일요일부터, 독일어는 월요일부터 시작한다.
 *
 * 오늘은 테두리로, 고른 날은 글래스 인디케이터로 표시하고, 다른 날을 고르면 인디케이터가 그 날로 미끄러지듯 이동한다.
 * [GlassCalendarSelection.Range] 모드에서는 시작일과 종료일 사이를 띠로 칠한다. [events]가 색을 돌려주는 날은 숫자 아래에 점을 최대 3개까지 찍는다.
 *
 * TalkBack은 날짜마다 '2026년 10월 15일 목요일, 일정 2개'처럼 읽고 선택 상태와 오늘 여부를 알려 주며, 사용자 지정 동작으로 달을 넘길 수 있다.
 * 키보드로 포커스를 주면 방향키로 날짜를, Page Up/Down으로 달을 옮기고 Enter나 Space로 고른다.
 * 달력 높이는 6주 기준으로 고정이라 달을 넘겨도 바뀌지 않는다.
 *
 * ```kotlin
 * var selection by remember { mutableStateOf<GlassCalendarSelection>(GlassCalendarSelection.Single()) }
 * GlassCalendar(rememberGlassCalendarState(), selection, { selection = it }, events = { schedule[it].orEmpty() })
 * ```
 *
 * @param selection 지금 고른 날짜와 모드. 모드는 이 값의 타입을 따른다.
 * @param onSelectionChange 날짜를 누르면 [selection]에 그 날을 반영한 새 값을 받는다. 범위 밖 날짜로는 호출되지 않는다.
 * @param events 날짜별 일정 색. 앞의 3개만 점으로 그리고, TalkBack은 전체 개수를 읽는다. 날짜마다 구성할 때 호출되므로 가볍게 만든다.
 */
@Composable
public fun GlassCalendar(
    state: GlassCalendarState,
    selection: GlassCalendarSelection,
    onSelectionChange: (GlassCalendarSelection) -> Unit,
    modifier: Modifier = Modifier,
    events: (LocalDate) -> List<Color> = NoEvents,
    colors: GlassCalendarColors = GlassCalendarColors(accent = GlassTheme.colors.accent, onAccent = GlassTheme.colors.onAccent),
    style: GlassStyle = LocalGlassStyle.current,
) {
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.ROOT
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val latestSelect by rememberUpdatedState(onSelectionChange)
    val latestSelection by rememberUpdatedState(selection)
    val keys = remember(state) { CalendarKeys(state, scope) }
    val strings = CalendarStrings(
        previousMonth = stringResource(R.string.kinetica_ui_calendar_previous_month),
        nextMonth = stringResource(R.string.kinetica_ui_calendar_next_month),
        today = stringResource(R.string.kinetica_ui_calendar_today),
        rangeStart = stringResource(R.string.kinetica_ui_calendar_range_start),
        rangeEnd = stringResource(R.string.kinetica_ui_calendar_range_end),
        inRange = stringResource(R.string.kinetica_ui_calendar_in_range),
    )
    val monthActions = remember(strings.previousMonth, strings.nextMonth, keys) {
        listOf(
            CustomAccessibilityAction(strings.previousMonth) { keys.page(-1) },
            CustomAccessibilityAction(strings.nextMonth) { keys.page(1) },
        )
    }
    val env = CalendarEnv(
        today = remember { LocalDate.now() },
        firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek },
        locale = locale,
        strings = strings,
        monthActions = monthActions,
        range = state.range,
    )
    val look = CalendarLook(env, selection, colors, style, keys.cursor.takeIf { keys.focused }, events)
    val still = reducedMotion()
    val select: (LocalDate) -> Unit = { date ->
        if (date in state.range) {
            keys.cursor = date
            latestSelect(latestSelection.tap(date))
        }
    }
    Column(
        modifier
            .onFocusChanged { focus ->
                keys.focused = focus.isFocused
                if (focus.isFocused && keys.cursor == null) keys.cursor = keys.startCursor(latestSelection, env.today)
            }
            .focusable()
            .onKeyEvent { event ->
                event.type == KeyEventType.KeyDown && keys.handle(event.key, rtl, latestSelection, env.today, select)
            },
    ) {
        CalendarHeader(state, look, keys::page)
        WeekdayRow(look)
        HorizontalPager(state.pager, Modifier.fillMaxWidth()) { page -> MonthGrid(state.monthAt(page), look, still, select) }
    }
}

/** 키보드로 옮기는 날짜 커서와 달 넘기기. */
@Stable
private class CalendarKeys(private val state: GlassCalendarState, private val scope: CoroutineScope) {
    var focused by mutableStateOf(false)
    var cursor by mutableStateOf<LocalDate?>(null)

    // 방향키로 달을 넘기는 애니메이션이 끝나기 전에 다음 키가 오면 currentPage는 아직 이전 달이다.
    private var pendingPage = -1

    fun page(delta: Int): Boolean {
        val target = state.pager.currentPage + delta
        if (target !in 0 until state.monthCount) return false
        scope.launch { state.pager.animateScrollToPage(target) }
        return true
    }

    fun startCursor(selection: GlassCalendarSelection, today: LocalDate): LocalDate {
        val month = state.currentMonth
        val chosen = when (selection) {
            is GlassCalendarSelection.Single -> selection.date
            is GlassCalendarSelection.Range -> selection.end ?: selection.start
        }
        val start = listOfNotNull(chosen, today).firstOrNull { YearMonth.from(it) == month } ?: month.atDay(1)
        return start.coerceIn(state.range)
    }

    fun handle(key: Key, rtl: Boolean, selection: GlassCalendarSelection, today: LocalDate, select: (LocalDate) -> Unit): Boolean {
        // 스와이프로 달을 넘겼으면 커서가 화면 밖에 있으므로 보이는 달에서 다시 시작한다.
        val base =
            cursor?.takeIf { state.pageOf(YearMonth.from(it)).let { page -> page == state.pager.currentPage || page == pendingPage } }
                ?: startCursor(selection, today)
        if (key == Key.Enter || key == Key.NumPadEnter || key == Key.Spacebar) {
            select(base)
            return true
        }
        val next = keyTarget(key, base, rtl)?.coerceIn(state.range) ?: return false
        cursor = next
        reveal(state.pageOf(YearMonth.from(next)))
        return true
    }

    private fun keyTarget(key: Key, base: LocalDate, rtl: Boolean): LocalDate? = when (key) {
        Key.DirectionLeft -> base.plusDays(if (rtl) 1 else -1)
        Key.DirectionRight -> base.plusDays(if (rtl) -1 else 1)
        Key.DirectionUp -> base.minusWeeks(1)
        Key.DirectionDown -> base.plusWeeks(1)
        Key.PageUp -> base.minusMonths(1)
        Key.PageDown -> base.plusMonths(1)
        else -> null
    }

    private fun reveal(page: Int) {
        if (page != state.pager.currentPage) {
            pendingPage = page
            scope.launch {
                try {
                    state.pager.animateScrollToPage(page)
                } finally {
                    if (pendingPage == page) pendingPage = -1
                }
            }
        }
    }
}

private class CalendarStrings(
    val previousMonth: String,
    val nextMonth: String,
    val today: String,
    val rangeStart: String,
    val rangeEnd: String,
    val inRange: String,
)

/** 선택과 상관없이 정해지는 달력 환경. */
private class CalendarEnv(
    val today: LocalDate,
    val firstDay: DayOfWeek,
    val locale: Locale,
    val strings: CalendarStrings,
    val monthActions: List<CustomAccessibilityAction>,
    val range: ClosedRange<LocalDate>,
)

private class CalendarLook(
    val env: CalendarEnv,
    val selection: GlassCalendarSelection,
    val colors: GlassCalendarColors,
    val style: GlassStyle,
    val cursor: LocalDate?,
    val events: (LocalDate) -> List<Color>,
) {
    fun dayColor(day: DayOfWeek, normal: Color): Color {
        val weekend = when (day) {
            DayOfWeek.SUNDAY -> colors.sunday
            DayOfWeek.SATURDAY -> colors.saturday
            else -> Color.Unspecified
        }
        return if (weekend == Color.Unspecified) normal else weekend
    }
}

@Composable
private fun CalendarHeader(state: GlassCalendarState, look: CalendarLook, onPage: (Int) -> Boolean) {
    val page = state.pager.currentPage
    Row(
        Modifier.fillMaxWidth().padding(start = TITLE_START, bottom = HEADER_BOTTOM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonthTitle(state.monthAt(page), look, Modifier.weight(1f))
        ArrowButton(previous = true, enabled = page > 0, look) { onPage(-1) }
        ArrowButton(previous = false, enabled = page < state.monthCount - 1, look) { onPage(1) }
    }
}

@Composable
private fun MonthTitle(month: YearMonth, look: CalendarLook, modifier: Modifier) {
    val locale = look.env.locale
    val formatter = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMMM"), locale) }
    val text = TextStyle(color = look.style.onGlass, fontSize = TITLE_SIZE, fontWeight = FontWeight.SemiBold)
    AnimatedContent(
        targetState = month,
        modifier = modifier
            .clipToBounds()
            .semantics(mergeDescendants = true) {
                heading()
                liveRegion = LiveRegionMode.Polite
                customActions = look.env.monthActions
            },
        transitionSpec = {
            val forward = targetState > initialState
            val roll = spring<IntOffset>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
            (slideInVertically(roll) { if (forward) it else -it } + fadeIn()) togetherWith
                (slideOutVertically(roll) { if (forward) -it else it } + fadeOut()) using SizeTransform(clip = false)
        },
        contentAlignment = Alignment.CenterStart,
        label = "calendar-month",
    ) { shown -> ThemedText(formatter.format(shown), style = text, maxLines = 1) }
}

@Composable
private fun ArrowButton(previous: Boolean, enabled: Boolean, look: CalendarLook, onClick: () -> Unit) {
    // 이전 달 화살표는 읽는 방향의 시작 쪽을 가리킨다. RTL에서는 오른쪽이다.
    val pointLeft = previous != (LocalLayoutDirection.current == LayoutDirection.Rtl)
    // 강조색 배경 위에 놓이면 강조색 화살표가 보이지 않으므로 글래스 글자 색으로 그린다.
    val fill = look.style.onGlass
    val ink = fill
    Box(
        Modifier
            .size(ARROW_SIZE)
            .clip(CircleShape)
            .clickable(null, GlassIndication(CenteredCircleShape(ARROW_INSET)), enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (previous) look.env.strings.previousMonth else look.env.strings.nextMonth }
            .drawWithCache {
                val arm = CHEVRON_ARM.toPx()
                val tip = if (pointLeft) -arm / 2 else arm / 2
                val mid = Offset(size.width / 2, size.height / 2)
                val path = Path().apply {
                    moveTo(mid.x - tip, mid.y - arm)
                    lineTo(mid.x + tip, mid.y)
                    lineTo(mid.x - tip, mid.y + arm)
                }
                val stroke = Stroke(width = CHEVRON_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind {
                    drawCircle(fill.copy(alpha = ARROW_FILL_ALPHA), radius = size.minDimension / 2 - ARROW_INSET.toPx())
                    drawPath(path, if (enabled) ink else fill.copy(alpha = DISABLED_ALPHA), style = stroke)
                }
            },
    )
}

@Composable
private fun WeekdayRow(look: CalendarLook) {
    val normal = look.style.onGlass.copy(alpha = WEEKDAY_ALPHA)
    // 날짜마다 요일까지 읽으므로 요일 머리는 TalkBack에서 건너뛴다.
    Row(Modifier.fillMaxWidth().padding(vertical = WEEKDAY_PADDING).clearAndSetSemantics {}) {
        repeat(DAYS_PER_WEEK) { index ->
            val day = look.env.firstDay.plus(index.toLong())
            ThemedText(
                day.getDisplayName(DateTextStyle.SHORT, look.env.locale),
                Modifier.weight(1f),
                style = TextStyle(
                    color = look.dayColor(day, normal),
                    fontSize = WEEKDAY_SIZE,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

/** 한 달 안에서 칸 번호로 나타낸 선택 표시. [band]는 칸 단위 구간이고 끝이 시작보다 크지 않으면 띠가 없다. */
private class CellMarks(val pill: Int, val end: Int, val bandFrom: Float, val bandTo: Float) {
    val hasBand: Boolean get() = bandTo > bandFrom

    companion object {
        fun of(selection: GlassCalendarSelection, month: YearMonth, lead: Int): CellMarks {
            fun index(date: LocalDate?): Int = if (date != null && YearMonth.from(date) == month) lead + date.dayOfMonth - 1 else -1
            val range = selection as? GlassCalendarSelection.Range
            val start = range?.start
            val end = range?.end
            val first = month.atDay(1)
            val last = month.atEndOfMonth()
            return when {
                selection is GlassCalendarSelection.Single -> CellMarks(index(selection.date), -1, 0f, 0f)

                start == null || end == null || start == end -> CellMarks(index(start), -1, 0f, 0f)

                end.isBefore(first) || start.isAfter(last) -> CellMarks(-1, -1, 0f, 0f)

                // 띠는 시작일과 종료일 칸의 가운데에서 끝나 양 끝 인디케이터 밑으로 숨는다. 다른 달로 이어지면 그 달의 첫날이나 마지막 날 칸 끝까지 칠한다.
                else -> CellMarks(
                    index(start),
                    index(end),
                    if (start.isBefore(first)) lead.toFloat() else index(start) + HALF,
                    if (end.isAfter(last)) (lead + month.lengthOfMonth()).toFloat() else index(end) + HALF,
                )
            }
        }
    }
}

/** 선택 인디케이터의 위치(칸 단위의 열, 행), 크기, 기간 띠가 칠해진 정도. */
@Stable
private class PillMotion(marks: CellMarks) {
    val position = Animatable(cellOf(max(marks.pill, 0)), Offset.VectorConverter)
    val scale = Animatable(if (marks.pill >= 0) 1f else 0f)
    val band = Animatable(if (marks.hasBand) 1f else 0f)

    /** 기간 종료일 인디케이터의 크기(0..1). 띠가 거의 다 칠해졌을 때 커진다. */
    fun endGrowth(): Float = ((band.value - END_REVEAL) / (1f - END_REVEAL)).coerceIn(0f, 1f)

    /** [index] 칸이 인디케이터에 덮인 정도(0..1). 미끄러지는 인디케이터와 기간 종료일 인디케이터 중 큰 값이다. */
    fun coverage(marks: CellMarks, index: Int): Float {
        val distance = (position.value - cellOf(index)).getDistance()
        val sliding = scale.value * (1f - distance).coerceIn(0f, 1f)
        return max(sliding, if (index == marks.end) endGrowth() else 0f)
    }
}

private fun cellOf(index: Int): Offset = Offset((index % DAYS_PER_WEEK).toFloat(), (index / DAYS_PER_WEEK).toFloat())

@Composable
private fun rememberPillMotion(marks: CellMarks, still: Boolean): PillMotion {
    val motion = remember { PillMotion(marks) }
    LaunchedEffect(marks.pill, still) {
        val target = marks.pill
        when {
            target < 0 -> if (still) motion.scale.snapTo(0f) else motion.scale.animateTo(0f, tween(PILL_OUT_MS))

            still || motion.scale.value == 0f -> {
                motion.position.snapTo(cellOf(target))
                if (still) motion.scale.snapTo(1f) else motion.scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }

            else -> {
                launch { motion.scale.animateTo(1f) }
                motion.position.animateTo(cellOf(target), spring(dampingRatio = PILL_DAMPING, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }
    LaunchedEffect(marks.hasBand, marks.end, still) {
        when {
            !marks.hasBand -> motion.band.snapTo(0f)
            still || motion.band.value >= 1f -> motion.band.snapTo(1f)
            else -> motion.band.animateTo(1f, tween(BAND_MS, easing = FastOutSlowInEasing))
        }
    }
    return motion
}

@Composable
private fun MonthGrid(month: YearMonth, look: CalendarLook, still: Boolean, onTap: (LocalDate) -> Unit) {
    val lead = Math.floorMod(month.atDay(1).dayOfWeek.value - look.env.firstDay.value, DAYS_PER_WEEK)
    val marks = remember(look.selection, month, lead) { CellMarks.of(look.selection, month, lead) }
    val motion = rememberPillMotion(marks, still)
    val cellHeight = CELL_HEIGHT * LocalDensity.current.fontScale.coerceIn(1f, MAX_CELL_SCALE)
    val accent = look.colors.accent
    Column(
        Modifier.fillMaxWidth().drawWithCache {
            val grid = GridGeometry(size.width / DAYS_PER_WEEK, size.height / ROWS, layoutDirection == LayoutDirection.Rtl, size.width)
            val diameter = min(grid.cellWidth, grid.cellHeight) - PILL_INSET.toPx() * 2
            val pill = PillPaint(accent, Stroke(PILL_EDGE.toPx()), PILL_DROP.toPx())
            onDrawBehind {
                drawBand(grid, marks, motion.band.value, diameter, accent.copy(alpha = BAND_ALPHA))
                if (marks.end >= 0) {
                    val grow = motion.endGrowth()
                    drawGlassPill(grid.center(cellOf(marks.end)), Size(diameter * grow, diameter * grow), pill)
                }
                val scale = motion.scale.value
                if (scale > 0f) {
                    // 이동 속도만큼 이동 방향으로 늘리고 반대 방향으로 줄여 액체가 흐르듯 보이게 한다.
                    val velocity = motion.position.velocity
                    val stretchX = 1f + min(abs(velocity.x) * STRETCH, STRETCH_MAX)
                    val stretchY = 1f + min(abs(velocity.y) * STRETCH, STRETCH_MAX)
                    val pillSize = Size(diameter * scale * stretchX / sqrt(stretchY), diameter * scale * stretchY / sqrt(stretchX))
                    drawGlassPill(grid.center(motion.position.value), pillSize, pill)
                }
            }
        },
    ) {
        repeat(ROWS) { row ->
            Row(Modifier.fillMaxWidth().height(cellHeight)) {
                repeat(DAYS_PER_WEEK) { column ->
                    val day = row * DAYS_PER_WEEK + column - lead + 1
                    if (day in 1..month.lengthOfMonth()) {
                        val index = row * DAYS_PER_WEEK + column
                        DayCell(month.atDay(day), look, onTap, { motion.coverage(marks, index) }, Modifier.weight(1f).fillMaxHeight())
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private class GridGeometry(val cellWidth: Float, val cellHeight: Float, val rtl: Boolean, val width: Float) {
    /** 칸 단위 좌표(열, 행)의 칸 가운데를 픽셀 좌표로 바꾼다. RTL에서는 0열이 오른쪽 끝이다. */
    fun center(cell: Offset): Offset {
        val x = (cell.x + HALF) * cellWidth
        return Offset(if (rtl) width - x else x, (cell.y + HALF) * cellHeight)
    }
}

private class PillPaint(val color: Color, val edge: Stroke, val drop: Float)

private fun DrawScope.drawBand(grid: GridGeometry, marks: CellMarks, progress: Float, height: Float, color: Color) {
    if (!marks.hasBand || progress <= 0f) return
    val to = marks.bandFrom + (marks.bandTo - marks.bandFrom) * progress
    var row = floor(marks.bandFrom / DAYS_PER_WEEK).toInt()
    while (row * DAYS_PER_WEEK < to) {
        val rowStart = (row * DAYS_PER_WEEK).toFloat()
        val from = max(marks.bandFrom, rowStart) - rowStart
        val until = min(to, rowStart + DAYS_PER_WEEK) - rowStart
        if (until > from) {
            val left = if (grid.rtl) grid.width - until * grid.cellWidth else from * grid.cellWidth
            val top = row * grid.cellHeight + (grid.cellHeight - height) / 2
            drawRoundRect(color, Offset(left, top), Size((until - from) * grid.cellWidth, height), CornerRadius(height / 2))
        }
        row++
    }
}

/** 강조색 위에 위쪽 반사광과 밝은 테두리를 얹어 유리처럼 보이는 인디케이터를 그린다. */
private fun DrawScope.drawGlassPill(center: Offset, size: Size, paint: PillPaint) {
    if (size.width <= 0f || size.height <= 0f) return
    val topLeft = Offset(center.x - size.width / 2, center.y - size.height / 2)
    val radius = CornerRadius(min(size.width, size.height) / 2)
    drawRoundRect(paint.color.copy(alpha = PILL_SHADOW_ALPHA), topLeft + Offset(0f, paint.drop), size, radius)
    drawRoundRect(paint.color, topLeft, size, radius)
    val sheen = Size(size.width * SHEEN_WIDTH, size.height * SHEEN_HEIGHT)
    drawRoundRect(
        Color.White.copy(alpha = SHEEN_ALPHA),
        Offset(center.x - sheen.width / 2, topLeft.y + size.height * SHEEN_TOP),
        sheen,
        CornerRadius(sheen.height / 2),
    )
    drawRoundRect(Color.White.copy(alpha = PILL_EDGE_ALPHA), topLeft, size, radius, style = paint.edge)
}

@Composable
private fun DayCell(date: LocalDate, look: CalendarLook, onTap: (LocalDate) -> Unit, coverage: () -> Float, modifier: Modifier) {
    val env = look.env
    val enabled = date in env.range
    val endpoint = look.selection.isEndpoint(date)
    val events = look.events(date)
    val formatter = remember(env.locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(env.locale) }
    val eventText = if (events.isEmpty()) null else pluralStringResource(R.plurals.kinetica_ui_calendar_events, events.size, events.size)
    val description = listOfNotNull(formatter.format(date), eventText).joinToString(", ")
    val state = cellState(date, look)
    val base = look.dayColor(date.dayOfWeek, look.style.onGlass)
    val ink = if (enabled) base else base.copy(alpha = DISABLED_ALPHA)
    val onAccent = look.colors.onAccent
    val marker = CellMarker(
        today = date == env.today,
        focused = look.cursor == date,
        dots = events.take(MAX_DOTS),
        accent = look.colors.accent,
        focus = look.style.onGlass.copy(alpha = FOCUS_ALPHA),
        onAccent = onAccent,
    )
    val scale = LocalDensity.current.fontScale.coerceIn(1f, MAX_CELL_SCALE)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .clearAndSetSemantics {
                contentDescription = description
                if (state != null) stateDescription = state
                selected = endpoint
                customActions = env.monthActions
                if (enabled) {
                    onClick {
                        onTap(date)
                        true
                    }
                } else {
                    disabled()
                }
            }
            .then(if (enabled) Modifier.dayTap(date, interaction, onTap) else Modifier)
            .indication(interaction, DayIndication)
            .dayMarks(marker, scale, coverage),
        contentAlignment = Alignment.Center,
    ) {
        ThemedText(
            date.dayOfMonth.toString(),
            style = TextStyle(
                fontSize = DAY_SIZE,
                fontWeight = if (marker.today || endpoint) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            // 인디케이터가 미끄러져 오는 만큼 그리기 단계에서 글자색을 바꿔, 덮이기 전에 흰 글자가 배경에 묻히지 않게 한다.
            color = { lerp(ink, onAccent, coverage()) },
        )
    }
}

/**
 * 날짜 칸 탭. 접근성 정보는 칸이 직접 정하므로 clickable 대신 탭 감지에 눌림 상태만 [interaction]으로 알린다.
 */
private fun Modifier.dayTap(date: LocalDate, interaction: MutableInteractionSource, onTap: (LocalDate) -> Unit): Modifier =
    pointerInput(date) {
        detectTapGestures(
            onPress = { offset ->
                val press = PressInteraction.Press(offset)
                interaction.emit(press)
                interaction.emit(if (tryAwaitRelease()) PressInteraction.Release(press) else PressInteraction.Cancel(press))
            },
        ) { onTap(date) }
    }

private class CellMarker(
    val today: Boolean,
    val focused: Boolean,
    val dots: List<Color>,
    val accent: Color,
    val focus: Color,
    val onAccent: Color,
)

/** 오늘 테두리, 키보드 커서 테두리, 일정 점을 그린다. 인디케이터에 덮이는 만큼 테두리는 사라지고 점은 [CellMarker.onAccent]로 바뀐다. */
private fun Modifier.dayMarks(marker: CellMarker, scale: Float, coverage: () -> Float): Modifier = drawWithCache {
    val radius = min(size.width, size.height) / 2 - PILL_INSET.toPx()
    val ring = Stroke(RING_WIDTH.toPx())
    val dotY = size.height / 2 + DOT_OFFSET.toPx() * scale
    val gap = DOT_GAP.toPx()
    onDrawBehind {
        val covered = coverage()
        if (marker.today && covered < 1f) {
            // 강조색과 비슷한 색의 배경 위에서도 오늘이 보이도록 글자색으로 옅은 원을 먼저 깐다.
            drawCircle(marker.focus, radius, alpha = TODAY_FILL * (1f - covered))
            drawCircle(marker.accent, radius - ring.width / 2, alpha = 1f - covered, style = ring)
        }
        if (marker.focused) drawCircle(marker.focus, radius + FOCUS_GAP.toPx(), style = ring)
        val firstX = center.x - (marker.dots.size - 1) * gap / 2
        marker.dots.forEachIndexed { i, color ->
            drawCircle(lerp(color, marker.onAccent, covered), DOT_RADIUS.toPx(), Offset(firstX + i * gap, dotY))
        }
    }
}

private fun cellState(date: LocalDate, look: CalendarLook): String? {
    val strings = look.env.strings
    val selection = look.selection
    val range = selection as? GlassCalendarSelection.Range
    val complete = range?.end != null && range.start != range.end
    return listOfNotNull(
        strings.today.takeIf { date == look.env.today },
        strings.rangeStart.takeIf { complete && date == range?.start },
        strings.rangeEnd.takeIf { complete && date == range?.end },
        strings.inRange.takeIf { selection.isInside(date) },
    ).joinToString(", ").ifEmpty { null }
}

private fun monthsBetween(from: YearMonth, to: YearMonth): Int = ChronoUnit.MONTHS.between(from, to).toInt()

private val NoEvents: (LocalDate) -> List<Color> = { emptyList() }
private val DefaultCalendarRange: ClosedRange<LocalDate> = LocalDate.of(1900, 1, 1)..LocalDate.of(2100, 12, 31)

private const val DAYS_PER_WEEK = 7
private const val ROWS = 6
private const val HALF = 0.5f
private val CELL_HEIGHT = 46.dp
private const val MAX_CELL_SCALE = 1.5f
private val TITLE_START = 8.dp
private val HEADER_BOTTOM = 4.dp
private val TITLE_SIZE = 18.sp
private val ARROW_SIZE = 44.dp
private val ARROW_INSET = 4.dp
private const val ARROW_FILL_ALPHA = 0.07f
private val CHEVRON_ARM = 6.dp
private val CHEVRON_WIDTH = 2.dp
private val WEEKDAY_PADDING = 6.dp
private val WEEKDAY_SIZE = 12.sp
private const val WEEKDAY_ALPHA = 0.55f
private val DAY_SIZE = 16.sp
private val PILL_INSET = 3.dp
private val DayIndication = GlassIndication(CenteredCircleShape(PILL_INSET))
private val PILL_EDGE = 1.dp
private val PILL_DROP = 2.dp
private const val PILL_SHADOW_ALPHA = 0.28f
private const val PILL_EDGE_ALPHA = 0.45f
private const val PILL_DAMPING = 0.72f
private const val PILL_OUT_MS = 160
private const val SHEEN_ALPHA = 0.28f
private const val SHEEN_WIDTH = 0.62f
private const val SHEEN_HEIGHT = 0.34f
private const val SHEEN_TOP = 0.08f
private const val STRETCH = 0.05f
private const val STRETCH_MAX = 0.35f
private const val BAND_ALPHA = 0.18f
private const val BAND_MS = 420
private const val END_REVEAL = 0.8f
private val RING_WIDTH = 2.dp
private const val TODAY_FILL = 0.6f
private val FOCUS_GAP = 2.dp
private const val FOCUS_ALPHA = 0.6f
private val DOT_OFFSET = 13.dp
private val DOT_GAP = 6.dp
private val DOT_RADIUS = 2.dp
private const val MAX_DOTS = 3
private const val DISABLED_ALPHA = 0.3f
private const val ACCENT = 0xFF0A84FF
private const val SUNDAY_RED = 0xFFFF453A
private const val SATURDAY_BLUE = 0xFF3478F6
