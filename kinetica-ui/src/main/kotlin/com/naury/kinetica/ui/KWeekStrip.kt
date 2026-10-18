package com.naury.kinetica.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 주간 달력 띠. 한 주 7일을 한 줄로 보이고 좌우로 넘겨 다른 주로 간다. 일정, 습관, 식단 앱 화면 위쪽에 둔다.
 * 요일 순서는 기기 언어의 한 주 시작을 따르고, RTL에서는 [KCalendar]처럼 줄과 넘기는 방향이 뒤집힌다.
 * 고른 날은 강조색 원, 오늘은 강조색 글자로 표시한다. [selected]가 다른 주로 바뀌면 그 주로 넘어간다.
 * [events]가 색을 돌려주는 날은 숫자 아래에 점을 3개까지 찍는다.
 *
 * TalkBack은 날마다 '2026년 10월 16일 금요일, 오늘, 일정 2개'처럼 읽고 고른 날은 선택됨으로 알린다. 띠에 이전 주, 다음 주 동작이 있다.
 *
 * ```kotlin
 * KWeekStrip(day, { day = it }, Modifier.fillMaxWidth(), events = { schedule[it].orEmpty() })
 * ```
 *
 * @param range 넘겨 볼 수 있는 날짜 범위. [selected]가 범위 밖이면 가까운 끝 주를 보인다.
 * @param events 날짜별 일정 색. 날마다 구성할 때 부르므로 가볍게 만든다.
 */
@Composable
public fun KWeekStrip(
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    events: (LocalDate) -> List<Color> = { emptyList() },
    range: ClosedRange<LocalDate> = LocalDate.now().minusYears(2)..LocalDate.now().plusYears(2),
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.ROOT
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val origin = remember(range, firstDay) { range.start.with(TemporalAdjusters.previousOrSame(firstDay)) }
    val count = remember(origin, range) { (ChronoUnit.WEEKS.between(origin, range.endInclusive) + 1).toInt() }
    val weekOf: (LocalDate) -> Int = { (ChronoUnit.DAYS.between(origin, it) / DAYS).toInt().coerceIn(0, count - 1) }
    val pager = rememberPagerState(initialPage = weekOf(selected)) { count }
    LaunchedEffect(selected) {
        val target = weekOf(selected)
        if (target != pager.currentPage) pager.animateScrollToPage(target)
    }
    val scope = rememberCoroutineScope()
    val actions = listOf(
        CustomAccessibilityAction(stringResource(R.string.kinetica_ui_week_previous)) {
            scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }
            true
        },
        CustomAccessibilityAction(stringResource(R.string.kinetica_ui_week_next)) {
            scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(count - 1)) }
            true
        },
    )
    val today = remember { LocalDate.now() }
    val look = WeekLook(locale, today, selected, surface, events)
    HorizontalPager(pager, modifier.semantics { customActions = actions }) { page ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            repeat(DAYS) { d ->
                val date = origin.plusWeeks(page.toLong()).plusDays(d.toLong())
                DayCell(date, date in range, look, Modifier.weight(1f)) { onSelect(date) }
            }
        }
    }
}

private class WeekLook(
    val locale: Locale,
    val today: LocalDate,
    val selected: LocalDate,
    val surface: SurfaceStyle,
    val events: (LocalDate) -> List<Color>,
) {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
}

@Composable
private fun DayCell(date: LocalDate, enabled: Boolean, look: WeekLook, modifier: Modifier, onClick: () -> Unit) {
    val colors = KineticaTheme.colors
    val ink = look.surface.contentColor
    val isSelected = date == look.selected
    val isToday = date == look.today
    val fill by animateColorAsState(if (isSelected) colors.accent else Color.Transparent, label = "week-day")
    val dots = look.events(date)
    val parts = listOfNotNull(
        look.formatter.format(date),
        if (isToday) stringResource(R.string.kinetica_ui_calendar_today) else null,
        if (dots.isNotEmpty()) pluralStringResource(R.plurals.kinetica_ui_week_events, dots.size, dots.size) else null,
    )
    val description = joinChartParts(parts)
    val shape = roundedShapeFor(CELL_RADIUS) { indicator }
    Column(
        modifier
            .padding(horizontal = 2.dp)
            .clip(shape)
            .kSelectable(isSelected, shape, enabled, Role.Tab, onClick = onClick)
            .heightIn(min = CELL_HEIGHT)
            .padding(vertical = 6.dp)
            .clearAndSetSemantics {
                contentDescription = description
                selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThemedText(
            date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, look.locale),
            style = TextStyle(color = if (enabled) look.surface.muted(MUTED) else ink.copy(alpha = DISABLED), fontSize = 12.sp),
            maxLines = 1,
        )
        DayNumber(date.dayOfMonth, fill, dayInk(isSelected, isToday, enabled, ink), isSelected || isToday)
        Row(Modifier.height(DOT), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            dots.take(MAX_DOTS).forEach { Box(Modifier.size(DOT).background(it, CircleShape)) }
        }
    }
}

/** 날짜 숫자 색. 고른 날은 강조색 위 글자 색, 오늘은 강조색이다. */
@Composable
private fun dayInk(selected: Boolean, today: Boolean, enabled: Boolean, ink: Color): Color = when {
    selected -> KineticaTheme.colors.onAccent
    today -> KineticaTheme.colors.accent
    !enabled -> ink.copy(alpha = DISABLED)
    else -> ink
}

@Composable
private fun DayNumber(day: Int, fill: Color, color: Color, bold: Boolean) {
    Box(Modifier.size(DAY_CIRCLE).background(fill, shapeFor { indicator }), contentAlignment = Alignment.Center) {
        val weight = if (bold) KineticaTheme.design.label(FontWeight.SemiBold) else FontWeight.Normal
        ThemedText(
            day.toString(),
            style = TextStyle(color = color, fontSize = 16.sp, fontWeight = weight),
        )
    }
}

private const val DAYS = 7
private const val MAX_DOTS = 3
private const val MUTED = 0.6f
private const val DISABLED = 0.3f
private val CELL_RADIUS = 14.dp
private val CELL_HEIGHT = 76.dp
private val DAY_CIRCLE = 36.dp
private val DOT = 5.dp
