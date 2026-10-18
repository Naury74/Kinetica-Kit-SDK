package com.naury.kinetica.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
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
class KInputs2Test {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `코드 입력란은 숫자만 받고 다 채우면 한 번 완료를 알리며 진행 상황과 자동 채우기 종류를 알린다`() {
        val code = mutableStateOf("")
        val completed = mutableListOf<String>()
        compose.setContent {
            KCodeInput(code.value, { code.value = it }, Modifier.testTag("code"), length = 4, onComplete = { completed += it })
        }
        val field = compose.onNodeWithTag("code")
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, ContentType.SmsOtpCode))
        field.performTextInput("1a2")
        compose.waitForIdle()
        assertThat(code.value).isEqualTo("12")
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2 of 4 digits entered"))

        field.performTextInput("34")
        compose.waitForIdle()
        assertThat(code.value).isEqualTo("1234")
        assertThat(completed).containsExactly("1234")

        field.performTextInput("5")
        compose.waitForIdle()
        assertThat(code.value).isEqualTo("1234")
        assertThat(completed).hasSize(1)
    }

    @Test
    fun `코드를 붙여 넣으면 새 코드로 채우고 오류 상태를 알린다`() {
        assertThat(acceptCode("12", "12987-654", 6)).isEqualTo("987654")
        assertThat(acceptCode("12", "123", 6)).isEqualTo("123")
        assertThat(acceptCode("123456", "1234567", 6)).isEqualTo("123456")

        val error = mutableStateOf(false)
        compose.setContent { KCodeInput("12", {}, Modifier.testTag("code"), error = error.value) }
        error.value = true
        compose.waitForIdle()
        compose.onNodeWithTag("code").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Incorrect code"))
    }

    @Test
    fun `태그 입력란은 쉼표와 완료 키로 칩을 만들고 중복을 막으며 지우기 키를 두 번 눌러 마지막 칩을 지운다`() {
        val tags = mutableStateOf(listOf<String>())
        compose.setContent { KTagInput(tags.value, { tags.value = it }, Modifier.testTag("tags"), placeholder = "태그") }
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("Kotlin, compose")
        compose.waitForIdle()
        assertThat(tags.value).containsExactly("Kotlin")
        field.performImeAction()
        compose.waitForIdle()
        assertThat(tags.value).containsExactly("Kotlin", "compose").inOrder()

        field.performTextInput("kotlin,")
        compose.waitForIdle()
        assertThat(tags.value).hasSize(2)

        field.requestFocus()
        field.performKeyInput { pressKey(Key.Backspace) }
        compose.waitForIdle()
        assertThat(tags.value).hasSize(2)
        compose.onNodeWithContentDescription("compose").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "compose selected. Press delete again to remove it"),
        )
        field.performKeyInput { pressKey(Key.Backspace) }
        compose.waitForIdle()
        assertThat(tags.value).containsExactly("Kotlin")

        compose.onNodeWithContentDescription("Kotlin").performCustomAccessibilityActionWithLabel("Remove Kotlin")
        compose.waitForIdle()
        assertThat(tags.value).isEmpty()
    }

    @Test
    fun `태그 추천은 앞부분이 맞는 항목을 먼저 보이고 누르면 추가한다`() {
        assertThat(matchSuggestions(listOf("앱 디자인", "디자인", "개발", "디자인 시스템"), listOf("디자인 시스템"), "디자", 5))
            .containsExactly("디자인", "앱 디자인").inOrder()
        assertThat(splitTagInput("a,b,c").committed).containsExactly("a", "b")
        assertThat(mergeTags(listOf("A"), listOf(" a ", " ", "b")).tags).containsExactly("A", "b")

        val tags = mutableStateOf(listOf<String>())
        compose.setContent {
            KTagInput(tags.value, { tags.value = it }, placeholder = "태그", suggestions = listOf("개발", "디자인"))
        }
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        field.performTextInput("디")
        compose.waitForIdle()
        compose.onNodeWithText("디자인").performClick()
        compose.waitForIdle()
        assertThat(tags.value).containsExactly("디자인")
    }

    @Test
    fun `태그 추천 목록은 아래 내용을 밀어내지 않고 팝업 창에서 눌러 추가된다`() = checkSuggestionOverlay(hosted = false)

    @Test
    fun `태그 추천 목록은 KineticaScaffold 안에서 오버레이 레이어에 뜨고 눌러 추가된다`() = checkSuggestionOverlay(hosted = true)

    private fun checkSuggestionOverlay(hosted: Boolean) {
        val tags = mutableStateOf(listOf<String>())
        compose.setContent {
            val body = @androidx.compose.runtime.Composable {
                androidx.compose.foundation.layout.Column {
                    KTagInput(tags.value, { tags.value = it }, placeholder = "태그", suggestions = listOf("개발", "디자인"))
                    BasicText("아래 내용", Modifier.testTag("below"))
                }
            }
            if (hosted) KineticaScaffold { body() } else body()
        }
        val before = compose.onNodeWithTag("below").getUnclippedBoundsInRoot().top
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        field.performTextInput("디")
        compose.waitForIdle()
        compose.onNodeWithText("디자인").assertExists()
        assertThat(compose.onNodeWithTag("below").getUnclippedBoundsInRoot().top).isEqualTo(before)
        compose.onNodeWithText("디자인").performClick()
        compose.waitForIdle()
        assertThat(tags.value).containsExactly("디자인")
    }

    @Test
    fun `태그 추천 목록은 아래 공간이 모자라면 입력란 위로 뜬다`() {
        assertThat(suggestionTop(anchorTop = 100, anchorBottom = 160, windowHeight = 1000, height = 200, gap = 6)).isEqualTo(166)
        assertThat(suggestionTop(anchorTop = 700, anchorBottom = 760, windowHeight = 900, height = 200, gap = 6)).isEqualTo(494)
        // 위아래 모두 모자라면 넓은 쪽(여기서는 위)에 놓는다.
        assertThat(suggestionTop(anchorTop = 150, anchorBottom = 210, windowHeight = 300, height = 200, gap = 6)).isEqualTo(-56)
    }

    @Test
    fun `태그를 다음 컴포지션 전에 연달아 추가해도 앞 태그가 사라지지 않는다`() {
        var latest = emptyList<String>()
        val model = TagInputModel().apply { onChange = { latest = it } }
        model.add(listOf("a"))
        model.add(listOf("b"))
        assertThat(latest).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `색상 선택기는 색 이름과 HEX를 읽고 TalkBack 동작과 HEX 입력, 투명도로 색을 바꾼다`() {
        val color = mutableStateOf(Color(0xFF0000FF))
        compose.setContent { KColorPicker(color.value, { color.value = it }) }
        val wheel = compose.onNodeWithContentDescription("Color picker")
        wheel.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "blue, #0000FF"))

        wheel.performCustomAccessibilityActionWithLabel("More muted")
        compose.waitForIdle()
        assertThat(color.value.toPickerHsv().s).isWithin(0.01f).of(0.9f)
        assertThat(color.value.toPickerHsv().h).isWithin(0.5f).of(240f)

        compose.onNodeWithContentDescription("Hex color code").performTextReplacement("FF0000")
        compose.waitForIdle()
        assertThat(color.value).isEqualTo(Color(0xFFFF0000))

        compose.onNodeWithContentDescription("Opacity").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        compose.waitForIdle()
        assertThat(color.value.alpha).isWithin(0.01f).of(0.5f)
        assertThat(color.value.red).isWithin(0.01f).of(1f)
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun `색상 선택기는 Tab으로 고리, 사각형, 투명도 순서로 옮겨 방향키로 조절한다`() {
        val color = mutableStateOf(Color.hsv(200f, 0.5f, 0.5f, 0.8f))
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                BasicText("시작", Modifier.testTag("start").focusable())
                KColorPicker(color.value, { color.value = it }, presets = emptyList(), showHexInput = false)
            }
        }
        val start = compose.onNodeWithTag("start")
        start.requestFocus()
        start.performKeyInput { pressKey(Key.Tab) }
        start.performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        assertThat(color.value.toPickerHsv().h).isWithin(0.5f).of(202f)

        start.performKeyInput { pressKey(Key.Tab) }
        start.performKeyInput { pressKey(Key.DirectionUp) }
        start.performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()
        assertThat(color.value.toPickerHsv().v).isWithin(0.01f).of(0.52f)
        assertThat(color.value.toPickerHsv().s).isWithin(0.01f).of(0.48f)

        start.performKeyInput { pressKey(Key.Tab) }
        start.performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()
        assertThat(color.value.alpha).isWithin(0.01f).of(0.78f)
        assertThat(alphaKeyStep(Key.DirectionLeft, rtl = true)).isGreaterThan(0f)
    }

    @Test
    fun `색 변환과 이름은 무채색에서도 색조를 유지하고 대략적인 이름을 준다`() {
        assertThat(Color.Black.toPickerHsv(fallbackHue = 120f).h).isEqualTo(120f)
        val hsv = Color(0xFF3D8BFF).toPickerHsv()
        assertThat(hsv.toColor().red).isWithin(0.01f).of(Color(0xFF3D8BFF).red)
        assertThat(colorNameOf(Color(0xFFFF3B30)).first).isEqualTo(R.string.kinetica_ui_color_red)
        assertThat(colorNameOf(Color(0xFF0A3B10)).second).isEqualTo(R.string.kinetica_ui_color_dark)
        assertThat(colorNameOf(Color.White).first).isEqualTo(R.string.kinetica_ui_color_white)
        assertThat(parseHex("#80FF0000", 1f)).isEqualTo(Color(0x80FF0000))
        assertThat(parseHex("12345", 1f)).isNull()
    }

    @Test
    fun `서명란은 그린 획을 기록해 이미지와 SVG로 내보내고 그리는 동안 바깥 스크롤을 막는다`() {
        val state = SignaturePadState(emptyList())
        val scroll = androidx.compose.foundation.ScrollState(0)
        compose.setContent {
            Column(Modifier.height(300.dp).verticalScroll(scroll)) {
                KSignaturePad(Modifier.fillMaxWidth().height(200.dp).testTag("pad"), state)
                Column(Modifier.height(600.dp)) {}
            }
        }
        val pad = compose.onNodeWithTag("pad")
        pad.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Empty"))
        pad.performTouchInput {
            down(Offset(40f, 150f))
            for (i in 1..20) moveTo(Offset(40f + i * 12f, 150f - i * 6f), delayMillis = 16)
            up()
        }
        compose.waitForIdle()
        assertThat(scroll.value).isEqualTo(0)
        assertThat(state.isEmpty).isFalse()
        pad.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Signed"))
        assertThat(state.toSvgPath()).startsWith("M40 150")
        assertThat(state.toSvg()).contains("<path d=\"M40 150")

        val image = state.toImageBitmap(color = Color.Black, background = Color.White)
        val pixels = image.toPixelMap()
        val dark = (0 until image.width step 2).sumOf { x -> (0 until image.height step 2).count { y -> pixels[x, y].red < 0.5f } }
        assertThat(dark).isGreaterThan(20)

        pad.performCustomAccessibilityActionWithLabel("Undo")
        compose.waitForIdle()
        assertThat(state.isEmpty).isTrue()
        assertThat(state.toSvgPath()).isEmpty()
    }

    @Test
    fun `비밀번호 강도는 길이와 글자 종류, 흔한 패턴으로 매긴다`() {
        assertThat(passwordStrength("")).isEqualTo(PasswordStrength.Weak)
        assertThat(passwordStrength("Ab1!")).isEqualTo(PasswordStrength.Weak)
        assertThat(passwordStrength("sunflower")).isEqualTo(PasswordStrength.Weak)
        assertThat(passwordStrength("sunflower7")).isEqualTo(PasswordStrength.Medium)
        assertThat(passwordStrength("Password1!")).isEqualTo(PasswordStrength.Medium)
        assertThat(passwordStrength("Sun7flower!")).isEqualTo(PasswordStrength.Strong)
        assertThat(passwordStrength("aaaaaaaa1A")).isEqualTo(PasswordStrength.Weak)
    }

    @Test
    fun `비밀번호 입력란은 눈 아이콘으로 보이기를 바꾸고 강도와 체크리스트를 읽는다`() {
        val value = mutableStateOf("")
        compose.setContent {
            KPasswordField(value.value, { value.value = it }, "Password", Modifier.testTag("pw"), showChecklist = true)
        }
        compose.onNodeWithText("Password").assertExists()
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, ContentType.NewPassword))

        compose.onNode(hasSetTextAction()).performTextInput("Sun7flower!")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Password strength")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Strong"))
        compose.onNodeWithText("Symbol").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Met"))

        compose.onNodeWithContentDescription("Show password").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Hide password").assertContentDescriptionEquals("Hide password")
    }
}
