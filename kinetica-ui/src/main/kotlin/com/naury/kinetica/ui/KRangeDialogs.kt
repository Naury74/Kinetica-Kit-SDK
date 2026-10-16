package com.naury.kinetica.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.launch

/** 기간 다이얼로그의 빠른 선택 칩. [end]가 [start]보다 앞이면 [IllegalArgumentException]을 던진다. */
@Immutable
public data class KDateRangePreset(val label: String, val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "end는 start와 같거나 뒤여야 합니다." }
    }
}

/** 시간 범위 다이얼로그의 빠른 선택 칩. [end]가 [start]보다 앞이면 다음 날까지 이어지는 범위다. */
@Immutable
public data class KTimeRangePreset(val label: String, val start: LocalTime, val end: LocalTime)

/** 기본 빠른 선택 목록 */
public object KRangePresets {
    /** 오늘, 최근 7일, 최근 30일, 이번 달, 지난달. 이름은 기기 언어를 따른다. */
    @Composable
    public fun dates(today: LocalDate = LocalDate.now()): List<KDateRangePreset> {
        val month = YearMonth.from(today)
        val last = month.minusMonths(1)
        return listOf(
            KDateRangePreset(stringResource(R.string.kinetica_ui_range_today), today, today),
            KDateRangePreset(stringResource(R.string.kinetica_ui_range_last_days, WEEK), today.minusDays(WEEK - 1L), today),
            KDateRangePreset(stringResource(R.string.kinetica_ui_range_last_days, MONTH_DAYS), today.minusDays(MONTH_DAYS - 1L), today),
            KDateRangePreset(stringResource(R.string.kinetica_ui_range_this_month), month.atDay(1), today),
            KDateRangePreset(stringResource(R.string.kinetica_ui_range_last_month), last.atDay(1), last.atEndOfMonth()),
        )
    }

    /** 오전(9~12시), 오후(13~18시), 업무 시간(9~18시) */
    @Composable
    public fun times(): List<KTimeRangePreset> = listOf(
        KTimeRangePreset(stringResource(R.string.kinetica_ui_range_morning), hour(MORNING), hour(NOON)),
        KTimeRangePreset(stringResource(R.string.kinetica_ui_range_afternoon), hour(AFTERNOON), hour(EVENING)),
        KTimeRangePreset(stringResource(R.string.kinetica_ui_range_work), hour(MORNING), hour(EVENING)),
    )

    private fun hour(value: Int): LocalTime = LocalTime.of(value, 0)

    private const val MORNING = 9
    private const val NOON = 12
    private const val AFTERNOON = 13
    private const val EVENING = 18
    private const val WEEK = 7
    private const val MONTH_DAYS = 30
}

/**
 * 기간 선택 다이얼로그. 위에 빠른 선택 칩(오늘, 최근 7일 …)과 고른 기간 요약, 가운데 [KCalendar] 범위 모드, 아래에 취소와 적용 버튼을 놓는다.
 * 칩을 누르면 달력이 그 기간의 끝 달로 넘어가고, 달력에서 시작일과 종료일을 차례로 눌러 직접 고를 수도 있다. 종료일을 고르기 전에는 적용할 수 없다.
 * 다이얼로그를 열 때마다 [start], [end]에서 다시 시작하고, 적용을 눌러야 [onConfirm]이 불린다. 달력은 RTL에서 [KCalendar] 규칙대로 뒤집힌다.
 *
 * TalkBack은 칩을 켜고 끄는 항목으로, 요약을 '10월 1일 ~ 10월 14일, 14일'처럼 읽고 바뀔 때마다 알린다.
 *
 * ```kotlin
 * KDateRangeDialog(open, { open = false }, from, to, onConfirm = { s, e -> from = s; to = e })
 * ```
 *
 * @param range 고를 수 있는 날짜 범위
 * @param presets 빠른 선택 칩. 비어 있으면 줄을 숨긴다.
 */
@Composable
public fun KDateRangeDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    start: LocalDate?,
    end: LocalDate?,
    onConfirm: (start: LocalDate, end: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    presets: List<KDateRangePreset> = KRangePresets.dates(),
    range: ClosedRange<LocalDate> = LocalDate.now().minusYears(YEARS_BACK)..LocalDate.now().plusYears(YEARS_AHEAD),
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val heading = title ?: stringResource(R.string.kinetica_ui_range_date_title)
    KDialog(visible, onDismissRequest, modifier, surface, title = heading) {
        var draft by remember(visible) {
            mutableStateOf(KCalendarSelection.Range(start, end?.takeIf { start != null && !it.isBefore(start) }))
        }
        val calendar = rememberKCalendarState(YearMonth.from(draft.end ?: draft.start ?: LocalDate.now()), range)
        val scope = rememberCoroutineScope()
        DialogHeading(heading, surface)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (presets.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { preset ->
                        val on = draft.start == preset.start && draft.end == preset.end
                        KFilterChip(preset.label, on, {
                            draft = KCalendarSelection.Range(preset.start, preset.end)
                            scope.launch { calendar.animateScrollToMonth(YearMonth.from(preset.end)) }
                        }, surface = surface)
                    }
                }
            }
            RangeSummary(draft.start, draft.end, surface)
            KCalendar(calendar, draft, { draft = it as KCalendarSelection.Range }, surface = surface)
        }
        val chosenStart = draft.start
        val chosenEnd = draft.end
        DialogActions(surface, onDismissRequest, enabled = chosenStart != null && chosenEnd != null) {
            if (chosenStart != null && chosenEnd != null) onConfirm(chosenStart, chosenEnd)
        }
    }
}

/**
 * 시간 범위 다이얼로그. 위에 빠른 선택 칩, 그 아래 시작과 종료 칸을 두고, 고른 칸의 시각을 휠([KTimePicker])로 바꾼다.
 * 종료가 시작보다 앞이면 밤을 넘기는 범위로 보고, 종료가 다음 날이라는 안내를 띄운다. 적용을 눌러야 [onConfirm]이 불린다.
 *
 * TalkBack은 시작과 종료 칸을 탭처럼 읽고(고른 칸은 선택됨), 휠은 [KTimePicker] 규칙대로 읽는다.
 *
 * ```kotlin
 * KTimeRangeDialog(open, { open = false }, LocalTime.of(9, 0), LocalTime.of(18, 0), onConfirm = { s, e -> hours = s to e })
 * ```
 *
 * @param is24Hour 24시간제인지. 기본값은 기기 설정이다.
 */
@Composable
public fun KTimeRangeDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    start: LocalTime,
    end: LocalTime,
    onConfirm: (start: LocalTime, end: LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    presets: List<KTimeRangePreset> = KRangePresets.times(),
    is24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val heading = title ?: stringResource(R.string.kinetica_ui_range_time_title)
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.ROOT
    val formatter =
        remember(locale, is24Hour) {
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale)
        }
    KDialog(visible, onDismissRequest, modifier, surface, title = heading) {
        var from by remember(visible) { mutableStateOf(start) }
        var to by remember(visible) { mutableStateOf(end) }
        var editing by remember(visible) { mutableIntStateOf(0) }
        DialogHeading(heading, surface)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (presets.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { preset ->
                        KFilterChip(preset.label, from == preset.start && to == preset.end, {
                            from = preset.start
                            to = preset.end
                        }, surface = surface)
                    }
                }
            }
            KSegmentedControl(
                listOf(
                    stringResource(R.string.kinetica_ui_range_start, formatter.format(from)),
                    stringResource(R.string.kinetica_ui_range_end, formatter.format(to)),
                ),
                editing,
                { editing = it },
                Modifier.fillMaxWidth(),
                surface = surface,
            )
            if (to.isBefore(from)) {
                ThemedText(
                    stringResource(R.string.kinetica_ui_range_next_day),
                    Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    style = TextStyle(
                        color = surface.contentColor.copy(alpha = NOTE_ALPHA),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
            val shown = if (editing == 0) from else to
            KTimePicker(shown.hour, shown.minute, { h, m ->
                val time = LocalTime.of(h, m)
                if (editing == 0) from = time else to = time
            }, Modifier.fillMaxWidth(), is24Hour = is24Hour, surface = surface)
        }
        DialogActions(surface, onDismissRequest, enabled = true) { onConfirm(from, to) }
    }
}

@Composable
private fun DialogHeading(text: String, surface: SurfaceStyle) {
    ThemedText(
        text,
        Modifier.fillMaxWidth().padding(bottom = 14.dp).semantics { heading() },
        style = TextStyle(color = surface.contentColor, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
    )
}

/** 고른 기간 요약. 끝을 고르기 전이면 시작일만 쓴다. */
@Composable
private fun RangeSummary(start: LocalDate?, end: LocalDate?, surface: SurfaceStyle) {
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.ROOT
    val formatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val text = when {
        start == null -> stringResource(R.string.kinetica_ui_range_pick_start)

        end == null -> stringResource(R.string.kinetica_ui_range_pick_end, formatter.format(start))

        else -> {
            val days = ChronoUnit.DAYS.between(start, end).toInt() + 1
            stringResource(R.string.kinetica_ui_range_summary, formatter.format(start), formatter.format(end)) + ", " +
                pluralStringResource(R.plurals.kinetica_ui_range_days, days, days)
        }
    }
    ThemedText(
        text,
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        style = TextStyle(color = surface.contentColor, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
    )
}

/** 취소와 적용 버튼. 취소는 시작 쪽에 둔다. */
@Composable
private fun ColumnScope.DialogActions(surface: SurfaceStyle, onDismiss: () -> Unit, enabled: Boolean, onApply: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp).align(Alignment.CenterHorizontally),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KButton(
            stringResource(R.string.kinetica_ui_range_cancel),
            onDismiss,
            Modifier.weight(1f),
            variant = KVariant.Tonal,
            tone = KTone.Neutral,
            surface = surface,
        )
        KButton(
            stringResource(R.string.kinetica_ui_range_apply),
            {
                onDismiss()
                onApply()
            },
            Modifier.weight(1f),
            enabled = enabled,
            surface = surface,
        )
    }
}

private const val NOTE_ALPHA = 0.64f
private const val YEARS_BACK = 5L
private const val YEARS_AHEAD = 5L
