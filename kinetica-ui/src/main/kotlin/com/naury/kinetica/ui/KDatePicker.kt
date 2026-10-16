package com.naury.kinetica.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.text.DateFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 연도, 월, 일 휠 세 개로 날짜를 고르는 글래스 날짜 피커. 휠 순서와 표시 형식은 기기 언어를 따른다.
 * 한국어는 '2026년 10월 15일' 순서로, 영어는 'October 15 2026' 순서로 놓인다.
 *
 * 월을 바꿨는데 그 달에 원래 일이 없으면(예: 3월 31일에서 2월로) 그 달의 마지막 날로 맞춘다. 결과는 언제나 [range] 안에 있다.
 *
 * @param date 선택한 날짜. [range] 밖이면 가까운 끝 날짜를 선택한 것으로 그린다.
 * @param onDateChange 사용자가 고른 날짜를 받는다. [range] 밖의 날짜는 넘기지 않는다.
 * @param range 고를 수 있는 날짜 범위(양 끝 포함). 끝이 시작보다 앞이면 [IllegalArgumentException]을 던진다.
 * @param visibleCount 한 번에 보이는 줄 수. 3 이상의 홀수가 아니면 [IllegalArgumentException]을 던진다.
 */
@Composable
public fun KDatePicker(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedRange<LocalDate> = DefaultDateRange,
    visibleCount: Int = DEFAULT_VISIBLE_COUNT,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(!range.endInclusive.isBefore(range.start)) { "range의 끝이 시작보다 앞입니다." }
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.ROOT
    val labels = remember(locale) { DateLabels(locale) }
    val current = date.clampTo(range)
    val model = DateWheels(current, range)
    val names = FieldNames(
        stringResource(R.string.kinetica_ui_picker_year),
        stringResource(R.string.kinetica_ui_picker_month),
        stringResource(R.string.kinetica_ui_picker_day),
    )
    fun change(next: LocalDate) {
        val clamped = next.clampTo(range)
        if (clamped != current) onDateChange(clamped)
    }
    Box(modifier) {
        WheelBand(surface, Modifier.align(Alignment.Center))
        Row(Modifier.fillMaxWidth()) {
            for (field in labels.order) {
                when (field) {
                    DateField.Year -> Wheel(
                        WheelSpec(model.years.map(labels::year), current.year - model.years.first, visibleCount, false, names.year),
                        { change(model.withYear(model.years.first + it)) },
                        Modifier.weight(YEAR_WEIGHT),
                        surface,
                    )

                    DateField.Month -> Wheel(
                        WheelSpec(
                            model.months.map(labels::month),
                            current.monthValue - model.months.first,
                            visibleCount,
                            false,
                            names.month,
                        ),
                        { change(model.withMonth(model.months.first + it)) },
                        Modifier.weight(labels.monthWeight),
                        surface,
                    )

                    DateField.Day -> Wheel(
                        WheelSpec(model.days.map(labels::day), current.dayOfMonth - model.days.first, visibleCount, false, names.day),
                        { change(current.withDayOfMonth(model.days.first + it)) },
                        Modifier.weight(DAY_WEIGHT),
                        surface,
                    )
                }
            }
        }
    }
}

/**
 * 시와 분 휠로 시각을 고르는 시간 피커. [is24Hour]가 `false`면 오전/오후 휠이 더해지고, 그 위치는 기기 언어를 따른다.
 * 한국어는 오전/오후가 앞에, 영어는 뒤에 온다. 시와 분 휠은 끝없이 굴릴 수 있다.
 *
 * @param hour 시(0..23). 범위를 벗어나면 [IllegalArgumentException]을 던진다.
 * @param minute 분(0..59). 범위를 벗어나면 [IllegalArgumentException]을 던진다.
 * @param onTimeChange 사용자가 고른 시(0..23)와 분(0..59)을 받는다.
 * @param is24Hour `true`면 0..23시로 고르고, `false`면 1..12시와 오전/오후로 고른다. 기본값은 기기 설정을 따른다.
 */
@Composable
public fun KTimePicker(
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(hour in 0 until HOURS_PER_DAY) { "hour는 0..23이어야 합니다." }
    require(minute in 0 until MINUTES_PER_HOUR) { "minute는 0..59여야 합니다." }
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.ROOT
    val labels = remember(locale, is24Hour) { TimeLabels(locale, is24Hour) }
    val hourName = stringResource(R.string.kinetica_ui_picker_hour)
    val minuteName = stringResource(R.string.kinetica_ui_picker_minute)
    val amPmName = stringResource(R.string.kinetica_ui_picker_am_pm)
    val pm = hour >= HALF_DAY
    Box(modifier) {
        WheelBand(surface, Modifier.align(Alignment.Center))
        Row(Modifier.fillMaxWidth()) {
            if (!is24Hour && labels.amPmFirst) {
                AmPmWheel(labels, pm, amPmName, surface) { onTimeChange(hour % HALF_DAY + if (it) HALF_DAY else 0, minute) }
            }
            Wheel(
                WheelSpec(labels.hours, if (is24Hour) hour else (hour + HALF_DAY - 1) % HALF_DAY, DEFAULT_VISIBLE_COUNT, true, hourName),
                { index -> onTimeChange(if (is24Hour) index else (index + 1) % HALF_DAY + if (pm) HALF_DAY else 0, minute) },
                Modifier.weight(1f),
                surface,
            )
            Wheel(
                WheelSpec(labels.minutes, minute, DEFAULT_VISIBLE_COUNT, true, minuteName),
                { onTimeChange(hour, it) },
                Modifier.weight(1f),
                surface,
            )
            if (!is24Hour && !labels.amPmFirst) {
                AmPmWheel(labels, pm, amPmName, surface) { onTimeChange(hour % HALF_DAY + if (it) HALF_DAY else 0, minute) }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.AmPmWheel(
    labels: TimeLabels,
    pm: Boolean,
    name: String,
    surface: SurfaceStyle,
    onPm: (Boolean) -> Unit,
) {
    Wheel(
        WheelSpec(labels.amPm, if (pm) 1 else 0, DEFAULT_VISIBLE_COUNT, false, name),
        { onPm(it == 1) },
        Modifier.weight(1f),
        surface,
    )
}

/** 날짜 피커의 휠 종류. */
internal enum class DateField { Year, Month, Day }

/**
 * 날짜 형식 패턴([DateFormat.getBestDateTimePattern] 결과)에서 연도, 월, 일이 나오는 순서를 읽는다.
 * 패턴에 없는 필드는 기본 순서(연도, 월, 일)대로 뒤에 붙인다.
 */
internal fun dateFieldOrder(pattern: String): List<DateField> {
    val positions = mapOf(
        DateField.Year to pattern.indexOfFirst { it == 'y' || it == 'Y' },
        DateField.Month to pattern.indexOfFirst { it == 'M' || it == 'L' },
        DateField.Day to pattern.indexOf('d'),
    )
    return DateField.entries.sortedBy { positions.getValue(it).let { index -> if (index < 0) Int.MAX_VALUE else index } }
}

private class FieldNames(val year: String, val month: String, val day: String)

/** 지금 날짜에서 각 휠에 놓일 값의 범위를 계산한다. 범위의 첫해와 마지막 해에서는 월과 일이 잘린다. */
private class DateWheels(val current: LocalDate, val range: ClosedRange<LocalDate>) {
    val years: IntRange = range.start.year..range.endInclusive.year
    val months: IntRange = monthsOf(current.year)
    val days: IntRange = daysOf(current.year, current.monthValue)

    fun monthsOf(year: Int): IntRange {
        val first = if (year == range.start.year) range.start.monthValue else 1
        val last = if (year == range.endInclusive.year) range.endInclusive.monthValue else MONTHS_PER_YEAR
        return first..last
    }

    fun daysOf(year: Int, month: Int): IntRange {
        val month0 = YearMonth.of(year, month)
        val first = if (month0 == YearMonth.from(range.start)) range.start.dayOfMonth else 1
        val last = if (month0 == YearMonth.from(range.endInclusive)) range.endInclusive.dayOfMonth else month0.lengthOfMonth()
        return first..last
    }

    fun withYear(year: Int): LocalDate = dateOf(year, current.monthValue.coerceIn(monthsOf(year)))

    fun withMonth(month: Int): LocalDate = dateOf(current.year, month)

    private fun dateOf(year: Int, month: Int): LocalDate = LocalDate.of(year, month, current.dayOfMonth.coerceIn(daysOf(year, month)))
}

/** 기기 언어에 맞춘 휠 순서와 항목 글자. */
private class DateLabels(locale: Locale) {
    val order: List<DateField> = dateFieldOrder(DateFormat.getBestDateTimePattern(locale, "yMd"))
    private val yearFormat = formatter(locale, "y")
    private val monthFormat = formatter(locale, "LLLL")
    private val dayFormat = formatter(locale, "d")

    /** 영어처럼 월 이름이 긴 언어는 월 휠을 넓힌다. */
    val monthWeight: Float = if (monthFormat.format(LocalDate.of(SAMPLE_YEAR, LONGEST_MONTH, 1)).length >
        SHORT_MONTH
    ) {
        WIDE_MONTH_WEIGHT
    } else {
        1f
    }

    fun year(value: Int): String = yearFormat.format(LocalDate.of(value, 1, 1))

    fun month(value: Int): String = monthFormat.format(LocalDate.of(SAMPLE_YEAR, value, 1))

    fun day(value: Int): String = dayFormat.format(LocalDate.of(SAMPLE_YEAR, 1, value))
}

private class TimeLabels(locale: Locale, is24Hour: Boolean) {
    val hours: List<String> = if (is24Hour) {
        (0 until HOURS_PER_DAY).map { String.format(locale, "%02d", it) }
    } else {
        (1..HALF_DAY).map { String.format(locale, "%d", it) }
    }
    val minutes: List<String> = (0 until MINUTES_PER_HOUR).map { String.format(locale, "%02d", it) }
    val amPm: List<String> = DateFormatSymbols.getInstance(locale).amPmStrings.toList()
    val amPmFirst: Boolean = amPmComesFirst(DateFormat.getBestDateTimePattern(locale, "hm"))
}

private fun LocalDate.clampTo(range: ClosedRange<LocalDate>): LocalDate = when {
    isBefore(range.start) -> range.start
    isAfter(range.endInclusive) -> range.endInclusive
    else -> this
}

private fun formatter(locale: Locale, skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

private val DefaultDateRange: ClosedRange<LocalDate> = LocalDate.of(1900, 1, 1)..LocalDate.of(2100, 12, 31)
private const val HOURS_PER_DAY = 24
private const val HALF_DAY = 12
private const val MINUTES_PER_HOUR = 60
private const val MONTHS_PER_YEAR = 12
private const val YEAR_WEIGHT = 1.1f
private const val DAY_WEIGHT = 0.8f
private const val WIDE_MONTH_WEIGHT = 1.5f

/** 월과 일 글자를 만들 때 쓰는 아무 윤년. 2월 29일도 만들 수 있어야 한다. */
private const val SAMPLE_YEAR = 2000
private const val LONGEST_MONTH = 9
private const val SHORT_MONTH = 4

/** 날짜 형식 [pattern]에서 오전/오후('a')가 처음 나오는 시 문자('h', 'H', 'k', 'K')보다 앞에 있는지 본다. 둘 중 하나라도 없으면 `false`다. */
internal fun amPmComesFirst(pattern: String): Boolean {
    val hour = pattern.indexOfFirst { it in HOUR_LETTERS }
    val amPm = pattern.indexOf('a')
    return amPm >= 0 && hour >= 0 && amPm < hour
}

private const val HOUR_LETTERS = "hHkK"
