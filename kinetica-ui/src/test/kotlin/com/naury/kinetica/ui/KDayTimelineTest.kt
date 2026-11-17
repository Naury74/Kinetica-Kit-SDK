package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KDayTimelineTest {
    @get:Rule
    val compose = createComposeRule()

    private fun event(id: String, from: String, to: String) = KDayEvent(id, id, LocalTime.parse(from), LocalTime.parse(to))

    @Test
    fun `겹치는 일정은 묶음마다 열을 나누고 겹치지 않는 일정은 한 열을 쓴다`() {
        val events = listOf(
            event("a", "09:00", "10:00"),
            event("b", "09:30", "11:00"),
            event("c", "10:00", "10:30"),
            event("d", "12:00", "13:00"),
        )
        val slots = arrangeDayEvents(events, DayRange(0, 24 * 60), minMinutes = 0f).associateBy { it.event.id }
        assertThat(slots.getValue("a").column).isEqualTo(0)
        assertThat(slots.getValue("b").column).isEqualTo(1)
        // a가 10시에 끝나므로 c는 a의 열을 다시 쓴다.
        assertThat(slots.getValue("c").column).isEqualTo(0)
        assertThat(slots.getValue("a").columns).isEqualTo(2)
        assertThat(slots.getValue("d").columns).isEqualTo(1)
    }

    @Test
    fun `짧은 일정은 그려지는 높이로 겹침을 판단하고 범위 밖 일정은 뺀다`() {
        val events = listOf(event("short", "09:00", "09:05"), event("next", "09:05", "09:30"), event("night", "22:00", "23:00"))
        val slots = arrangeDayEvents(events, DayRange(8 * 60, 20 * 60), minMinutes = 20f)
        assertThat(slots.map { it.event.id }).containsExactly("short", "next")
        assertThat(slots.first { it.event.id == "next" }.column).isEqualTo(1)
        assertThat(arrangeDayEvents(emptyList(), DayRange(0, 60), 0f)).isEmpty()
    }

    @Test
    fun `길게 누른 자리는 15분 단위로 내리고 끝에서는 마지막 칸으로 맞춘다`() {
        val range = DayRange(8 * 60, 10 * 60)
        assertThat(snapMinute(y = 50f, range = range, pxPerMinute = 1f)).isEqualTo(8 * 60 + 45)
        assertThat(snapMinute(y = 9999f, range = range, pxPerMinute = 1f)).isEqualTo(10 * 60 - 15)
    }

    @Test
    fun `일정 블록은 제목과 시작 끝 시각을 읽고 누르면 알린다`() {
        var clicked: Any? = null
        compose.setContent {
            KDayTimeline(
                listOf(event("Standup", "09:00", "10:30")),
                Modifier.height(400.dp),
                startHour = 8,
                endHour = 12,
                onEventClick = { clicked = it.id },
                clock = { LocalTime.of(9, 15) },
            )
        }
        val block = compose.onNode(
            SemanticsMatcher("일정 블록") { node ->
                SemanticsProperties.ContentDescription in node.config &&
                    node.config[SemanticsProperties.ContentDescription].firstOrNull()?.startsWith("Standup, ") == true
            },
        )
        val description = block.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()
        assertThat(description).contains("9:00")
        assertThat(description).contains("10:30")
        block.performClick()
        assertThat(clicked).isEqualTo("Standup")
    }

    @Test
    fun `빈 시간을 길게 누르면 그 시각을 알리고 일정 위에서는 알리지 않는다`() {
        val times = mutableListOf<LocalTime>()
        compose.setContent {
            KDayTimeline(
                listOf(event("Lunch", "12:00", "13:00")),
                Modifier.height(400.dp).testTag("day"),
                startHour = 8,
                endHour = 14,
                hourHeight = 60.dp,
                scrollToNow = false,
                onLongPressTime = { times += it },
            )
        }
        val hour = with(compose.density) { 60.dp.toPx() }
        compose.onNodeWithTag("day").performTouchInput { longClick(Offset(right - 20f, hour * 1.6f)) }
        compose.onNodeWithTag("day").performTouchInput { longClick(Offset(right - 20f, hour * 4.5f)) }
        assertThat(times).containsExactly(LocalTime.of(9, 30))
    }

    @Test
    fun `TalkBack에서는 시간 눈금의 동작으로 새 일정을 만든다`() {
        val times = mutableListOf<LocalTime>()
        compose.setContent {
            KDayTimeline(emptyList(), Modifier.height(400.dp), startHour = 9, endHour = 11, onLongPressTime = { times += it })
        }
        val label = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))[0].fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].first()
        label.action()
        assertThat(times).containsExactly(LocalTime.of(9, 0))
    }

    @Test
    fun `높이가 무한한 부모에서도 그린다`() {
        compose.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                KDayTimeline(listOf(event("a", "09:00", "10:00")), Modifier.testTag("day"), startHour = 8, endHour = 18)
            }
        }
        compose.onNodeWithTag("day").assertExists()
    }

    @Test
    fun `0분 일정과 자정을 넘는 일정은 거부하지 않고 하루 안으로 잘라 놓는다`() {
        val zero = event("zero", "09:00", "09:00")
        val overnight = event("overnight", "23:00", "01:00")
        val midnight = KDayEvent("midnight", "midnight", LocalTime.of(22, 0), LocalTime.MAX)
        val slots = arrangeDayEvents(listOf(zero, overnight, midnight, event("outside", "05:00", "05:00")), DayRange(8 * 60, 24 * 60), 20f)
        assertThat(slots.map { it.event.id }).containsExactly("zero", "overnight", "midnight")
        val zeroSlot = slots.first { it.event.id == "zero" }
        assertThat(zeroSlot.start to zeroSlot.end).isEqualTo(9 * 60 to 9 * 60)
        assertThat(slots.first { it.event.id == "overnight" }.end).isEqualTo(24 * 60)
        assertThat(slots.first { it.event.id == "midnight" }.end).isEqualTo(24 * 60)
    }

    @Test
    fun `자정에 끝나는 일정은 자정까지로 읽는다`() {
        compose.setContent {
            KDayTimeline(
                listOf(KDayEvent("late", "Late", LocalTime.of(22, 0), LocalTime.MAX), event("ping", "21:00", "21:00")),
                Modifier.height(400.dp),
                startHour = 20,
                scrollToNow = false,
            )
        }
        val description = compose.onNode(
            contentStartsWith("Late, "),
        ).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()
        assertThat(description).contains("12:00")
        assertThat(description).doesNotContain("11:59")
        compose.onNode(contentStartsWith("ping, ")).assertExists()
    }

    private fun contentStartsWith(prefix: String) = SemanticsMatcher("$prefix…") { node ->
        SemanticsProperties.ContentDescription in node.config &&
            node.config[SemanticsProperties.ContentDescription].firstOrNull()?.startsWith(prefix) == true
    }

    @Test
    fun `같은 SnapshotStateList에 일정을 더해도 보인다`() {
        val events = mutableStateListOf(event("a", "09:00", "10:00"))
        compose.setContent { KDayTimeline(events, Modifier.height(400.dp), startHour = 8, endHour = 12, scrollToNow = false) }
        compose.onNode(contentStartsWith("a, ")).assertExists()
        events.add(event("b", "10:00", "11:00"))
        compose.waitForIdle()
        compose.onNode(contentStartsWith("b, ")).assertExists()
    }

    @Test
    fun `시간 높이가 바뀐 뒤 길게 누르면 새 높이로 시각을 정한다`() {
        val times = mutableListOf<LocalTime>()
        var height by mutableStateOf(60.dp)
        compose.setContent {
            KDayTimeline(
                emptyList(),
                Modifier.height(400.dp).testTag("day"),
                startHour = 8,
                endHour = 14,
                hourHeight = height,
                scrollToNow = false,
                onLongPressTime = { times += it },
            )
        }
        height = 30.dp
        compose.waitForIdle()
        val hour = with(compose.density) { 30.dp.toPx() }
        compose.onNodeWithTag("day").performTouchInput { longClick(Offset(right - 20f, hour * 1.6f)) }
        assertThat(times).containsExactly(LocalTime.of(9, 30))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `시간 범위가 잘못되면 거부한다`() {
        compose.setContent { KDayTimeline(emptyList(), startHour = 10, endHour = 10) }
    }
}
