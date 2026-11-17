package com.naury.kinetica.ui

import android.text.format.DateFormat
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * [KDayTimeline]의 일정 하나.
 *
 * 달력 데이터를 그대로 넘겨도 되도록 시각을 검사하지 않는다. [end]가 [start]와 같은 0분 일정은 최소 높이로 그리고,
 * [end]가 [start]보다 이른 일정은 자정을 넘는 일정으로 보고 그날 자정까지만 그린다. [end]가 `LocalTime.MAX`이면 자정까지로 읽는다.
 *
 * @property id 일정을 가리는 키. 일정마다 달라야 한다.
 * @property color 블록 색. 지정하지 않으면 강조색이다.
 * @property subtitle 시간 아래에 보일 장소 같은 보조 글
 */
@Immutable
public data class KDayEvent(
    val id: Any,
    val title: String,
    val start: LocalTime,
    val end: LocalTime,
    val color: Color = Color.Unspecified,
    val subtitle: String? = null,
)

/**
 * 하루 일정 보기. 왼쪽에 시간 눈금을, 오른쪽에 일정 블록을 시각에 맞춰 놓는다. 시간이 겹치는 일정은 겹치는 묶음마다 열을 나눠 나란히 놓는다.
 *
 * [showNow]면 지금 시각에 강조색 선을 긋는다. 선은 1분마다만 옮기고, 보기가 화면 밖에 있으면 갱신을 멈춘다.
 * [scrollToNow]면 처음 보일 때 지금 시각이 위에서 3분의 1쯤 오도록 스크롤한다. 지금이 범위 밖이면 가까운 끝으로 간다. 회전 뒤에는 다시 스크롤하지 않는다.
 * 일정이 없는 곳을 길게 누르면 15분 단위로 내린 시각으로 [onLongPressTime]을 부른다. TalkBack에서는 시간 눈금마다 같은 동작을 쓸 수 있다.
 * 일정 블록은 '제목, 오전 9:00부터 오전 10:30까지'처럼 읽는다. 시각 형식은 기기의 12시간, 24시간 설정을 따른다.
 *
 * 높이가 정해진 곳에서는 안에서 세로로 스크롤한다. 세로 스크롤 부모 안처럼 높이가 무한하면 하루 전체 높이로 그린다.
 *
 * ```kotlin
 * KDayTimeline(
 *     events = listOf(KDayEvent("standup", "팀 회의", LocalTime.of(9, 0), LocalTime.of(9, 30))),
 *     startHour = 7,
 *     endHour = 22,
 *     onEventClick = { open(it.id) },
 *     onLongPressTime = { create(it) },
 * )
 * ```
 *
 * @param events 일정 목록. 새 목록을 넘기거나 `SnapshotStateList`를 제자리에서 고쳐도 반영한다.
 * @param startHour 맨 위 시각(시). 0..23이어야 한다.
 * @param endHour 맨 아래 시각(시). [startHour]보다 크고 24 이하여야 한다. 범위 밖 일정은 잘라서 보이거나 빠진다.
 * @param hourHeight 한 시간의 높이. 0보다 커야 한다.
 * @param clock 지금 시각을 주는 함수. 다른 시간대를 보일 때나 테스트에서 바꾼다.
 * @throws IllegalArgumentException 위 범위를 벗어나면
 */
@Composable
public fun KDayTimeline(
    events: List<KDayEvent>,
    modifier: Modifier = Modifier,
    startHour: Int = 0,
    endHour: Int = 24,
    hourHeight: Dp = 64.dp,
    showNow: Boolean = true,
    scrollToNow: Boolean = true,
    onEventClick: ((KDayEvent) -> Unit)? = null,
    onLongPressTime: ((LocalTime) -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    clock: () -> LocalTime = DefaultClock,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(startHour in 0..LAST_HOUR) { "startHour는 0..23이어야 합니다." }
    require(endHour in startHour + 1..HOURS_PER_DAY) { "endHour는 startHour보다 크고 24 이하여야 합니다." }
    require(hourHeight > 0.dp) { "hourHeight는 0보다 커야 합니다." }
    val minuteClock = rememberMinuteClock(clock, showNow)
    val range = DayRange(startHour * DAY_MINUTES_PER_HOUR, endHour * DAY_MINUTES_PER_HOUR)
    val pxPerMinute = with(LocalDensity.current) { hourHeight.toPx() } / DAY_MINUTES_PER_HOUR
    val minMinutes = with(LocalDensity.current) { DAY_MIN_BLOCK_HEIGHT.toPx() } / pxPerMinute
    // 호스트가 같은 SnapshotStateList에 일정을 더해도 다시 배치하도록 목록 읽기를 derivedStateOf로 추적한다.
    val slots by remember(events, range, minMinutes) { derivedStateOf { arrangeDayEvents(events, range, minMinutes) } }
    val formats = rememberTimeFormats()
    BoxWithConstraints(modifier) {
        val bounded = constraints.hasBoundedHeight
        val scroll = if (bounded) Modifier.verticalScroll(scrollState) else Modifier
        Row(scroll.fillMaxWidth().height(hourHeight * (endHour - startHour))) {
            HourGutter(range, formats, onLongPressTime, surface)
            DayGrid(
                slots,
                DayGridSpec(range, pxPerMinute, minuteClock, formats, surface, showNow),
                onEventClick,
                onLongPressTime,
                Modifier.weight(1f),
            )
        }
    }
    if (scrollToNow) ScrollToNowOnce(scrollState, range, pxPerMinute, clock)
}

/** 보이는 시간 범위(하루 시작부터 분). [end]는 [start]보다 크다. */
@Immutable
internal data class DayRange(val start: Int, val end: Int)

/** 시간 눈금 글과 일정 시각 글 형식. 기기의 12시간, 24시간 설정과 언어를 따른다. */
internal class TimeFormats(val hour: DateTimeFormatter, val time: DateTimeFormatter)

@Composable
private fun rememberTimeFormats(): TimeFormats {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24 = DateFormat.is24HourFormat(context)
    return remember(locale, is24) {
        TimeFormats(
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24) "H" else "ha"), locale),
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24) "Hm" else "hma"), locale),
        )
    }
}

/** 1분마다 바뀌는 지금 시각. 보기가 화면에 있을 때만 돈다. */
@Stable
internal class MinuteClock(initial: LocalTime) {
    var now by mutableStateOf(initial)
    var onScreen by mutableStateOf(false)
        private set

    // 수정자를 다시 만들 때마다 같은 콜백을 넘겨야 노드가 매번 갱신되지 않는다.
    val visibility: (Boolean) -> Unit = { onScreen = it }
}

@Composable
private fun rememberMinuteClock(clock: () -> LocalTime, running: Boolean): MinuteClock {
    val current by rememberUpdatedState(clock)
    val minute = remember { MinuteClock(clock().truncatedTo(ChronoUnit.MINUTES)) }
    val active = running && minute.onScreen
    LaunchedEffect(minute, active) {
        while (active) {
            val time = current()
            minute.now = time.truncatedTo(ChronoUnit.MINUTES)
            val intoMinute = time.second * MILLIS_PER_SECOND + time.nano / NANOS_PER_MILLI
            delay((MILLIS_PER_MINUTE - intoMinute).coerceAtLeast(1L))
        }
    }
    return minute
}

@Composable
private fun ScrollToNowOnce(scrollState: ScrollState, range: DayRange, pxPerMinute: Float, clock: () -> LocalTime) {
    var done by rememberSaveable { mutableStateOf(false) }
    val current by rememberUpdatedState(clock)
    LaunchedEffect(scrollState) {
        if (done) return@LaunchedEffect
        val viewport = snapshotFlow { scrollState.viewportSize }.first { it > 0 }
        val minute = (current().toSecondOfDay() / DAY_SECONDS_PER_MINUTE).coerceIn(range.start, range.end)
        val y = (minute - range.start) * pxPerMinute - viewport / NOW_POSITION
        scrollState.scrollTo(y.roundToInt().coerceAtLeast(0))
        done = true
    }
}

private val DefaultClock: () -> LocalTime = { LocalTime.now() }

internal const val DAY_MINUTES_PER_HOUR = 60
internal const val DAY_SECONDS_PER_MINUTE = 60
private const val HOURS_PER_DAY = 24
private const val LAST_HOUR = 23
private const val MILLIS_PER_SECOND = 1_000L
private const val NANOS_PER_MILLI = 1_000_000L
private const val MILLIS_PER_MINUTE = 60_000L
private const val NOW_POSITION = 3
internal val DAY_MIN_BLOCK_HEIGHT = 22.dp
