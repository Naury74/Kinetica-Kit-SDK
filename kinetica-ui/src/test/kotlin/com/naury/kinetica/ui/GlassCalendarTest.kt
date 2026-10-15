package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GlassCalendarTest {
    @get:Rule
    val compose = createComposeRule()

    private val october = YearMonth.of(2026, 10)

    @Test
    fun `기간 모드는 두 번 눌러 시작과 끝을 정하고 앞선 날이나 세 번째 누름은 새로 시작한다`() {
        val day = { d: Int -> LocalDate.of(2026, 10, d) }
        val single = GlassCalendarSelection.Single().tap(day(3))
        assertThat(single).isEqualTo(GlassCalendarSelection.Single(day(3)))

        val first = GlassCalendarSelection.Range().tap(day(10))
        val second = first.tap(day(14))
        assertThat(second).isEqualTo(GlassCalendarSelection.Range(day(10), day(14)))
        assertThat(second.isInside(day(12))).isTrue()
        assertThat(second.isInside(day(14))).isFalse()
        assertThat(second.tap(day(20))).isEqualTo(GlassCalendarSelection.Range(day(20), null))
        assertThat(first.tap(day(5))).isEqualTo(GlassCalendarSelection.Range(day(5), null))
    }

    @Test
    @Config(qualifiers = "ko")
    fun `날짜는 날짜와 요일과 일정 수를 읽고 누르면 선택되며 범위 밖 날짜는 누를 수 없다`() {
        val selection = mutableStateOf<GlassCalendarSelection>(GlassCalendarSelection.Single())
        val dots = listOf(Color.Red, Color.Green)
        compose.setContent {
            GlassCalendar(
                rememberGlassCalendarState(october, LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 10, 20)),
                selection.value,
                { selection.value = it },
                events = { if (it.dayOfMonth == 15) dots else emptyList() },
            )
        }
        val target = compose.onNodeWithContentDescription("2026년 10월 15일 목요일, 일정 2개")
        target.performClick()
        compose.waitForIdle()
        assertThat(selection.value).isEqualTo(GlassCalendarSelection.Single(LocalDate.of(2026, 10, 15)))
        target.assertIsSelected()

        compose.onNodeWithContentDescription("2026년 10월 21일 수요일").assertIsNotEnabled()
        compose.onNodeWithText("2026년 10월").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    @Config(qualifiers = "ko")
    fun `기간을 고르면 양 끝과 사이 날짜의 상태를 읽어 준다`() {
        val selection = mutableStateOf<GlassCalendarSelection>(GlassCalendarSelection.Range())
        compose.setContent { GlassCalendar(rememberGlassCalendarState(october), selection.value, { selection.value = it }) }
        compose.onNodeWithContentDescription("2026년 10월 6일 화요일").performClick()
        compose.onNodeWithContentDescription("2026년 10월 9일 금요일").performClick()
        compose.waitForIdle()

        assertThat(selection.value).isEqualTo(GlassCalendarSelection.Range(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 9)))
        compose.onNodeWithContentDescription("2026년 10월 6일 화요일")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "시작일"))
        compose.onNodeWithContentDescription("2026년 10월 7일 수요일")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "기간에 포함"))
    }

    @Test
    @Config(qualifiers = "ko")
    fun `화살표와 사용자 지정 동작과 스와이프로 달을 넘긴다`() {
        compose.setContent {
            GlassCalendar(rememberGlassCalendarState(october), GlassCalendarSelection.Single(), {}, Modifier.testTag("calendar"))
        }
        compose.onNodeWithContentDescription("다음 달").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("2026년 11월").assertExists()

        compose.onNodeWithText("2026년 11월").performCustomAccessibilityActionWithLabel("이전 달")
        compose.waitForIdle()
        compose.onNodeWithText("2026년 10월").assertExists()

        compose.onNodeWithTag("calendar").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("2026년 9월").assertExists()
    }

    @Test
    @Config(qualifiers = "ko")
    fun `키보드 방향키로 날짜를 옮기고 Enter로 고르며 달 끝을 넘으면 다음 달로 넘어간다`() {
        val selection = mutableStateOf<GlassCalendarSelection>(GlassCalendarSelection.Single(LocalDate.of(2026, 10, 30)))
        compose.setContent {
            GlassCalendar(rememberGlassCalendarState(october), selection.value, { selection.value = it }, Modifier.testTag("calendar"))
        }
        val calendar = compose.onNodeWithTag("calendar")
        calendar.requestFocus()
        calendar.performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionRight)
            pressKey(Key.Enter)
        }
        compose.waitForIdle()

        assertThat(selection.value).isEqualTo(GlassCalendarSelection.Single(LocalDate.of(2026, 11, 7)))
        compose.onNodeWithText("2026년 11월").assertExists()
    }

    @Test
    fun `영어에서는 날짜를 영어 형식으로 읽고 오늘을 알려 준다`() {
        val today = LocalDate.now()
        compose.setContent {
            GlassCalendar(rememberGlassCalendarState(YearMonth.from(today)), GlassCalendarSelection.Single(), {})
        }
        compose.onNodeWithContentDescription(
            java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, y", java.util.Locale.US).format(today),
        )
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Today"))
    }

    @Test
    fun `플립 숫자는 전체 텍스트를 읽고 카운트다운과 시계 문구를 맞게 만든다`() {
        val text = mutableStateOf("12:59")
        compose.setContent { FlipDigits(text.value, Modifier.testTag("flip").padding(8.dp)) }
        compose.onNodeWithTag("flip").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("12:59")))
        text.value = "13:00"
        compose.waitForIdle()
        compose.onNodeWithTag("flip").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("13:00")))

        assertThat(countdownText(61_000)).isEqualTo("01:01")
        assertThat(countdownText(200)).isEqualTo("00:01")
        assertThat(countdownText(-5)).isEqualTo("00:00")
        assertThat(countdownText(90_061_000)).isEqualTo("25:01:01")
        assertThat(clockText(LocalTime.of(0, 5, 9), showSeconds = true, is24Hour = false)).isEqualTo("12:05:09")
        assertThat(clockText(LocalTime.of(13, 5), showSeconds = false, is24Hour = false)).isEqualTo("01:05")
        assertThat(clockText(LocalTime.of(13, 5), showSeconds = false, is24Hour = true)).isEqualTo("13:05")
    }

    @Test
    fun `카운트다운은 1초마다 줄고 0에서 멈춘다`() {
        var now = 0L
        var left = -1L
        compose.mainClock.autoAdvance = false
        compose.setContent { left = rememberCountdown(2_500L) { now }.value }
        compose.waitForIdle()
        assertThat(left).isEqualTo(2_500L)
        now = 500L
        compose.mainClock.advanceTimeBy(600L)
        assertThat(left).isEqualTo(2_000L)
        now = 3_000L
        compose.mainClock.advanceTimeBy(2_100L)
        assertThat(left).isEqualTo(0L)
    }

    @Test
    fun `구간 슬라이더의 손잡이는 따로 움직이며 서로를 넘지 않는다`() {
        val range = mutableStateOf(20f..60f)
        compose.setContent {
            GlassRangeSlider(range.value, { range.value = it }, valueRange = 0f..100f, steps = 9)
        }
        val start = compose.onNodeWithContentDescription("Minimum")
        val end = compose.onNodeWithContentDescription("Maximum")
        start.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "20"))

        start.performSemanticsAction(SemanticsActions.SetProgress) { it(90f) }
        compose.waitForIdle()
        assertThat(range.value).isEqualTo(60f..60f)

        end.requestFocus()
        end.performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        assertThat(range.value).isEqualTo(60f..70f)

        end.performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
        compose.waitForIdle()
        assertThat(range.value).isEqualTo(60f..60f)
    }

    @Test
    fun `구간 슬라이더를 가로로 끌면 가까운 손잡이가 따라오고 겹친 손잡이는 끈 방향으로 고른다`() {
        val range = mutableStateOf(50f..50f)
        compose.setContent {
            Box(Modifier.width(300.dp)) {
                GlassRangeSlider(range.value, { range.value = it }, Modifier.testTag("slider"), valueRange = 0f..100f)
            }
        }
        compose.onNodeWithTag("slider").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(-80f, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(-80f, 0f))
            up()
        }
        compose.waitForIdle()
        assertThat(range.value.start).isLessThan(50f)
        assertThat(range.value.endInclusive).isEqualTo(50f)
    }

    @Test
    fun `구간 슬라이더는 범위와 눈금이 바뀌면 새 기준으로 누른 위치를 읽는다`() {
        val setup = mutableStateOf(Triple(0f..100f, 0, 0f..100f))
        compose.setContent {
            val (valueRange, steps, range) = setup.value
            Box(Modifier.width(300.dp)) {
                GlassRangeSlider(
                    range,
                    { setup.value = setup.value.copy(third = it) },
                    Modifier.testTag("slider"),
                    valueRange = valueRange,
                    steps = steps,
                )
            }
        }
        // 한 번 눌러 제스처 입력을 시작해 둔 뒤 범위와 눈금을 바꾼다.
        compose.onNodeWithTag("slider").performTouchInput { click(percentOffset(0.1f, 0.5f)) }
        compose.waitForIdle()
        setup.value = Triple(0f..10f, 1, 0f..10f)
        compose.waitForIdle()

        compose.onNodeWithTag("slider").performTouchInput { click(percentOffset(0.35f, 0.5f)) }
        compose.waitForIdle()

        assertThat(setup.value.third).isEqualTo(5f..10f)
    }
}
