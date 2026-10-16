package com.naury.kinetica.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.Currency
import java.util.Locale
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
class KDomainBlocksTest {
    @get:Rule
    val compose = createComposeRule()

    private fun state(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test
    fun `타임라인은 항목마다 상태를 알린다`() {
        compose.setContent {
            KTimeline(
                listOf(
                    KTimelineItem("Ordered", status = KTimelineStatus.Done),
                    KTimelineItem("Shipping", status = KTimelineStatus.Current),
                    KTimelineItem("Delivered"),
                ),
            )
        }
        compose.onNodeWithText("Ordered", useUnmergedTree = false).assert(state("Completed"))
        compose.onNodeWithText("Shipping").assert(state("In progress"))
        compose.onNodeWithText("Delivered").assert(state("Not started"))
    }

    @Test
    fun `단계 표시는 몇 단계 중 몇 단계인지 읽고 끝난 단계만 누를 수 있다`() {
        var clicked = -1
        compose.setContent {
            KStepIndicator(listOf("Cart", "Ship", "Pay"), 1, Modifier.testTag("steps"), onStepClick = { clicked = it })
        }
        compose.onNodeWithTag("steps").assert(state("Step 2 of 3, Ship"))
        compose.onNodeWithContentDescription("Step 1, Cart").performClick()
        assertThat(clicked).isEqualTo(0)
        compose.onNodeWithContentDescription("Step 3, Pay").performClick()
        assertThat(clicked).isEqualTo(0)
    }

    @Test
    fun `마법사는 다음과 이전으로 단계를 옮기고 마지막에 완료를 부른다`() {
        var finished = false
        var valid by mutableStateOf(true)
        lateinit var wizard: KWizardState
        compose.setContent {
            wizard = rememberKWizardState(2)
            KWizard(wizard, listOf("Account", "Confirm"), onFinish = {
                finished = true
            }, nextEnabled = valid) { step -> androidx.compose.foundation.text.BasicText("page $step") }
        }
        compose.onNodeWithText("page 0").assertExists()
        compose.onNodeWithText("Next").performClick()
        compose.waitForIdle()
        assertThat(wizard.current).isEqualTo(1)
        compose.onNodeWithText("Back").performClick()
        compose.waitForIdle()
        assertThat(wizard.current).isEqualTo(0)
        wizard.next()
        compose.waitForIdle()
        valid = false
        compose.onNodeWithText("Done").performClick()
        assertThat(finished).isFalse()
        valid = true
        compose.waitForIdle()
        compose.onNodeWithText("Done").performClick()
        assertThat(finished).isTrue()
    }

    @Test
    fun `테두리 수량 스테퍼는 하나의 조절 항목이고 동작으로 늘린다`() {
        var count by mutableIntStateOf(2)
        compose.setContent { KQuantityStepper(count, { count = it }, Modifier.testTag("q"), range = 1..3) }
        compose.onNodeWithTag("q").assert(state("2")).performCustomAccessibilityActionWithLabel("Increase")
        assertThat(count).isEqualTo(3)
        compose.onNodeWithTag("q").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `접히는 수량 스테퍼는 0이면 담기 버튼이고 1에서 빼면 접힌다`() {
        var count by mutableIntStateOf(0)
        compose.setContent { KQuantityStepper(count, { count = it }, Modifier.testTag("q"), style = KQuantityStyle.Collapsible) }
        compose.onNodeWithContentDescription("Add, Quantity").performClick()
        assertThat(count).isEqualTo(1)
        compose.waitForIdle()
        compose.onNodeWithTag("q").assert(state("1")).performCustomAccessibilityActionWithLabel("Decrease")
        assertThat(count).isEqualTo(0)
    }

    @Test
    fun `키패드는 숫자와 지우기를 넘기고 지우기를 길게 누르면 모두 지운다`() {
        var text by mutableStateOf("")
        compose.setContent {
            KKeypad(onDigit = {
                text += it
            }, onBackspace = { text = text.dropLast(1) }, onClear = { text = "" }, mode = KKeypadMode.Numeric)
        }
        compose.onNodeWithContentDescription("7").performClick()
        compose.onNodeWithContentDescription("00").performClick()
        assertThat(text).isEqualTo("700")
        compose.onNodeWithContentDescription("Delete").performClick()
        assertThat(text).isEqualTo("70")
        compose.onNodeWithContentDescription("Delete").performTouchInput { longClick() }
        assertThat(text).isEqualTo("")
    }

    @Test
    fun `PIN 점은 입력한 자리 수를 읽는다`() {
        compose.setContent { KPinDots(6, 2, Modifier.testTag("pin")) }
        compose.onNodeWithTag("pin").assert(state("2 of 6 digits entered"))
    }

    @Test
    fun `금액 입력 값은 통화 소수 자리와 자릿수 제한을 지킨다`() {
        assertThat(KAmountDefaults.append("", '0', 0, 12)).isEqualTo("0")
        assertThat(KAmountDefaults.append("0", '5', 0, 12)).isEqualTo("5")
        assertThat(KAmountDefaults.append("12", '.', 0, 12)).isEqualTo("12")
        assertThat(KAmountDefaults.append("12", '.', 2, 12)).isEqualTo("12.")
        assertThat(KAmountDefaults.append("12.34", '5', 2, 12)).isEqualTo("12.34")
        assertThat(KAmountDefaults.append("999", '1', 0, 3)).isEqualTo("999")
        assertThat(KAmountDefaults.add("12000", BigDecimal(10_000), 12)).isEqualTo("22000")
        val krw = Currency.getInstance("KRW")
        val eur = Currency.getInstance("EUR")
        assertThat(KAmountDefaults.format("1234567", krw, Locale.KOREA)).isEqualTo("₩1,234,567")
        assertThat(KAmountDefaults.format("1234.5", eur, Locale.GERMANY)).isEqualTo("1.234,5 €")
        assertThat(KAmountDefaults.format("12.", Currency.getInstance("USD"), Locale.US)).isEqualTo("$12.")
    }

    @Test
    fun `금액 입력은 키패드와 빠른 금액 칩으로 값을 바꾼다`() {
        var value by mutableStateOf("1")
        compose.setContent {
            KAmountInput(value, {
                value = it
            }, Currency.getInstance("USD"), label = "Send", presets = listOf(KAmountPreset("+10", BigDecimal.TEN)))
        }
        compose.onNodeWithText("+10").performClick()
        assertThat(value).isEqualTo("11")
        compose.onNodeWithContentDescription("Decimal point").performClick()
        compose.onNodeWithContentDescription("5").performClick()
        assertThat(value).isEqualTo("11.5")
        compose.onNodeWithContentDescription("Send, $11.5").assertExists()
    }

    @Test
    fun `가격표는 정가와 할인율을 한 번에 읽고 요금제 카드는 라디오로 고른다`() {
        var picked = false
        compose.setContent {
            KPriceTag("$89", Modifier.testTag("price"), originalPrice = "$110", discountPercent = 19)
            KPricingCard("Pro", "$9", picked, { picked = true }, Modifier.width(300.dp).testTag("plan"), features = listOf("No ads"))
        }
        compose.onNodeWithContentDescription("$89, regular price $110, 19% off").assertExists()
        compose.onNodeWithText("Pro").performClick()
        assertThat(picked).isTrue()
        assertThat(KPriceDefaults.discountPercent(89_000.0, 110_000.0)).isEqualTo(19)
        assertThat(KPriceDefaults.discountPercent(120.0, 110.0)).isEqualTo(0)
        assertThat(KPriceDefaults.format(89_000.0, Currency.getInstance("KRW"), Locale.US)).isEqualTo("₩89,000")
    }

    @Test
    fun `요금제 카드는 라디오 역할이다`() {
        compose.setContent { KPricingCard("Pro", "$9", true, {}, Modifier.testTag("plan")) }
        compose.onNodeWithText("Pro").assert(
            SemanticsMatcher("radio") { node ->
                generateSequence(node) { it.parent }.any {
                    it.config.getOrElseNullable(SemanticsProperties.Role) { null } ==
                        Role.RadioButton
                }
            },
        )
    }

    @Test
    fun `프로필 숫자 칸은 이름과 값을 함께 읽고 누를 수 있다`() {
        var opened = false
        compose.setContent {
            KProfileHeader("Minji", avatar = KAvatar("Minji"), stats = listOf(KProfileStat("1.2K", "Followers") { opened = true }))
        }
        compose.onNodeWithContentDescription("Followers 1.2K").performClick()
        assertThat(opened).isTrue()
    }

    @Test
    fun `기간 다이얼로그는 빠른 선택을 고르고 적용해야 결과를 넘긴다`() {
        var result: Pair<LocalDate, LocalDate>? = null
        val today = LocalDate.of(2026, 10, 16)
        compose.setContent {
            KineticaScaffold {
                KDateRangeDialog(true, {}, null, null, { s, e -> result = s to e }, presets = KRangePresets.dates(today))
            }
        }
        compose.onNodeWithText("Apply").performClick()
        assertThat(result).isNull()
        compose.onNodeWithText("Last 7 days").performClick()
        compose.onNodeWithText("Apply").performClick()
        assertThat(result).isEqualTo(today.minusDays(6) to today)
    }

    @Test
    fun `시간 범위 다이얼로그는 밤을 넘기면 안내하고 적용한 값을 넘긴다`() {
        var result: Pair<LocalTime, LocalTime>? = null
        compose.setContent {
            KineticaScaffold {
                KTimeRangeDialog(true, {}, LocalTime.of(22, 0), LocalTime.of(6, 0), { s, e -> result = s to e }, is24Hour = true)
            }
        }
        compose.onNodeWithText("Ends the next day").assertExists()
        compose.onNodeWithText("Work hours").performClick()
        compose.onNodeWithText("Apply").performClick()
        assertThat(result).isEqualTo(LocalTime.of(9, 0) to LocalTime.of(18, 0))
    }

    @Test
    fun `주간 띠는 날짜를 누르면 고르고 고른 날을 선택됨으로 읽는다`() {
        var day by mutableStateOf(LocalDate.of(2026, 10, 14))
        compose.setContent { KWeekStrip(day, { day = it }, Modifier.width(360.dp)) }
        compose.onNodeWithContentDescription("Wednesday, October 14, 2026", substring = true).assertIsSelected()
        compose.onNodeWithContentDescription("Thursday, October 15, 2026", substring = true).performClick()
        assertThat(day).isEqualTo(LocalDate.of(2026, 10, 15))
    }
}
