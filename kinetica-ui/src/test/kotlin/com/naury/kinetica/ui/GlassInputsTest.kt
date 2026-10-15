package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeUp
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
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
class GlassInputsTest {
    @get:Rule
    val compose = createComposeRule()

    private val items = listOf("사과", "배", "포도", "귤", "감", "자두", "복숭아")

    @Test
    fun `휠 피커는 현재 항목을 읽고 TalkBack 동작으로 다음과 이전 항목을 고른다`() {
        val selected = mutableStateOf(2)
        compose.setContent {
            GlassWheelPicker(items, selected.value, { selected.value = it }, Modifier.testTag("wheel"), contentDescription = "과일")
        }
        val wheel = compose.onNodeWithTag("wheel")
        wheel.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "포도"))
        wheel.assert(hasContentDescription("과일"))

        wheel.performCustomAccessibilityActionWithLabel("Next")
        compose.waitForIdle()
        assertThat(selected.value).isEqualTo(3)
        wheel.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "귤"))

        wheel.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        assertThat(selected.value).isEqualTo(0)

        wheel.requestFocus()
        wheel.performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        assertThat(selected.value).isEqualTo(1)
    }

    @Test
    fun `끝없이 도는 휠은 첫 항목의 이전이 마지막 항목이고 끝이 있는 휠은 멈춘다`() {
        val cyclic = mutableStateOf(0)
        val bounded = mutableStateOf(0)
        compose.setContent {
            Box {
                GlassWheelPicker(items, cyclic.value, { cyclic.value = it }, Modifier.testTag("cyclic"), cyclic = true)
            }
            GlassWheelPicker(items, bounded.value, { bounded.value = it }, Modifier.testTag("bounded"))
        }
        compose.onNodeWithTag("cyclic").performCustomAccessibilityActionWithLabel("Previous")
        compose.onNodeWithTag("bounded").performCustomAccessibilityActionWithLabel("Previous")
        compose.waitForIdle()

        assertThat(cyclic.value).isEqualTo(items.lastIndex)
        assertThat(bounded.value).isEqualTo(0)
        assertThat(nearestCyclic(current = 70, target = 6, size = 7)).isEqualTo(69)
    }

    @Test
    fun `휠을 위로 밀면 스크롤이 멈춘 뒤 아래쪽 항목을 고른다`() {
        val selected = mutableStateOf(0)
        compose.setContent {
            GlassWheelPicker(items, selected.value, { selected.value = it }, Modifier.fillMaxWidth().testTag("wheel"))
        }
        compose.onNodeWithTag("wheel").performTouchInput { swipeUp() }
        compose.waitForIdle()

        assertThat(selected.value).isGreaterThan(0)
        compose.onNodeWithTag("wheel")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, items[selected.value]))
    }

    @Test
    fun `날짜 휠 순서는 언어의 날짜 형식을 따른다`() {
        assertThat(dateFieldOrder("y. M. d.")).containsExactly(DateField.Year, DateField.Month, DateField.Day).inOrder()
        assertThat(dateFieldOrder("M/d/y")).containsExactly(DateField.Month, DateField.Day, DateField.Year).inOrder()
        assertThat(dateFieldOrder("dd.MM.y")).containsExactly(DateField.Day, DateField.Month, DateField.Year).inOrder()
    }

    @Test
    @Config(qualifiers = "ko")
    fun `달을 바꿔 그 달에 없는 날이 되면 마지막 날로 맞추고 범위 밖으로 나가지 않는다`() {
        val date = mutableStateOf(LocalDate.of(2024, 3, 31))
        compose.setContent {
            GlassDatePicker(date.value, { date.value = it }, range = LocalDate.of(2020, 1, 15)..LocalDate.of(2030, 6, 30))
        }
        compose.onNodeWithContentDescription("월")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3월"))
        compose.onNodeWithContentDescription("연도")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2024년"))

        compose.onNodeWithContentDescription("월").performCustomAccessibilityActionWithLabel("이전")
        compose.waitForIdle()
        assertThat(date.value).isEqualTo(LocalDate.of(2024, 2, 29))

        compose.onNodeWithContentDescription("연도").performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.waitForIdle()
        assertThat(date.value).isEqualTo(LocalDate.of(2020, 2, 29))
    }

    @Test
    fun `12시간 시간 피커는 오후 시각을 1부터 12시로 보여 주고 시를 바꿔도 오후를 유지한다`() {
        val time = mutableStateOf(13 to 5)
        compose.setContent {
            GlassTimePicker(time.value.first, time.value.second, { h, m -> time.value = h to m }, is24Hour = false)
        }
        compose.onNodeWithContentDescription("Hour")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1"))
        compose.onNodeWithContentDescription("AM/PM")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "PM"))

        compose.onNodeWithContentDescription("Hour").performCustomAccessibilityActionWithLabel("Previous")
        compose.waitForIdle()
        assertThat(time.value).isEqualTo(12 to 5)

        compose.onNodeWithContentDescription("AM/PM").performCustomAccessibilityActionWithLabel("Previous")
        compose.waitForIdle()
        assertThat(time.value).isEqualTo(0 to 5)
    }

    @Test
    fun `검색창은 글자를 지우고 검색 키를 전달하며 포커스를 받으면 취소 버튼을 보인다`() {
        val query = mutableStateOf("")
        var searched: String? = null
        compose.setContent {
            GlassSearchBar(query.value, { query.value = it }, { searched = it }, Modifier.testTag("search"))
        }
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("유리")
        compose.waitForIdle()
        assertThat(query.value).isEqualTo("유리")
        compose.onNodeWithText("Cancel").assertExists()

        compose.onNode(hasSetTextAction()).performImeAction()
        assertThat(searched).isEqualTo("유리")

        compose.onNodeWithContentDescription("Clear text").performClick()
        compose.waitForIdle()
        assertThat(query.value).isEmpty()
        compose.onNodeWithContentDescription("Clear text").assertDoesNotExist()

        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun `입력란은 라벨을 보이고 오류가 있으면 TalkBack에 오류를 알린다`() {
        val value = mutableStateOf("")
        val error = mutableStateOf<String?>(null)
        compose.setContent {
            GlassTextField(value.value, { value.value = it }, "이메일", error = error.value)
        }
        compose.onNode(hasSetTextAction()).performTextInput("a@b")
        compose.waitForIdle()
        assertThat(value.value).isEqualTo("a@b")
        compose.onNodeWithText("이메일", useUnmergedTree = true).assertExists()

        error.value = "올바른 이메일이 아닙니다"
        compose.waitForIdle()
        compose.onNode(hasSetTextAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "올바른 이메일이 아닙니다"))
        compose.onNodeWithText("올바른 이메일이 아닙니다").assertExists()

        error.value = null
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    }

    @Test
    fun `입력란은 IME 동작과 사용 안 함과 읽기 전용과 여러 줄을 전달한다`() {
        val value = mutableStateOf("")
        var done = 0
        val enabled = mutableStateOf(true)
        val readOnly = mutableStateOf(false)
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                GlassTextField(
                    value.value,
                    { value.value = it },
                    "이름",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { done++ }),
                    enabled = enabled.value,
                    readOnly = readOnly.value,
                    modifier = Modifier.testTag("name"),
                )
                GlassTextField("첫 줄", {}, "메모", singleLine = false, maxLines = 3, modifier = Modifier.testTag("memo"))
            }
        }
        fun name() = compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText) and
                androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("name")),
        )
        name().performTextInput("민지")
        name().performImeAction()
        assertThat(done).isEqualTo(1)

        readOnly.value = true
        compose.waitForIdle()
        name().assert(SemanticsMatcher.expectValue(SemanticsProperties.IsEditable, false))

        enabled.value = false
        compose.waitForIdle()
        name().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))

        compose.onNode(hasSetTextAction() and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("memo")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("첫 줄")))
    }

    @Test
    fun `12시간 형식에서 오전 오후 위치는 처음 나오는 시 문자를 기준으로 본다`() {
        assertThat(amPmComesFirst("a h:mm")).isTrue()
        assertThat(amPmComesFirst("h:mm a")).isFalse()
        assertThat(amPmComesFirst("a K:mm")).isTrue()
        assertThat(amPmComesFirst("K:mm a")).isFalse()
        assertThat(amPmComesFirst("H:mm")).isFalse()
        assertThat(amPmComesFirst("ah:mm")).isTrue()
    }
}
