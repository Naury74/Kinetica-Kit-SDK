package com.naury.kinetica.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.LinkAnnotation
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiKitTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `마크다운은 제목을 제목으로 읽고 목록과 코드 블록을 그린다`() {
        compose.setContent {
            KMarkdownText("# 요약\n\n- **첫째** 항목\n- 둘째\n\n```kotlin\nval a = 1\n```", Modifier.testTag("md"))
        }
        compose.onNode(isHeading()).assert(hasText("요약"))
        compose.onNodeWithText("첫째 항목").assertExists()
        compose.onNodeWithContentDescription("Kotlin code\nval a = 1").assertExists()
        // 비순서 목록의 점은 TalkBack이 읽지 않는다.
        compose.onAllNodesWithText("•").assertCountEquals(0)
    }

    @Test
    fun `링크는 접근성 링크로 붙고 누르면 주소를 넘긴다`() {
        val opened = mutableListOf<String>()
        compose.setContent { KMarkdownText("자세한 내용은 [문서](https://kinetica.dev)를 보세요.", onLinkClick = { opened += it }) }
        val text = compose.onNodeWithText("자세한 내용은 문서를 보세요.").fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text)!!.first()
        val link = text.getLinkAnnotations(0, text.length).single().item as LinkAnnotation.Clickable
        link.linkInteractionListener!!.onClick(link)
        assertThat(opened).containsExactly("https://kinetica.dev")
    }

    @Test
    fun `스트리밍으로 늘어나는 글도 깨지지 않고 마지막 블록만 바뀐다`() {
        var text by mutableStateOf("")
        compose.setContent { LazyColumn(Modifier.fillMaxSize()) { item { KMarkdownText(text, streaming = true) } } }
        val full = "## 단계\n1. 설치\n2. 설정\n\n```json\n{\"a\": 1}\n```\n끝 **굵게"
        for (end in 1..full.length step 3) {
            text = full.substring(0, end)
            compose.waitForIdle()
        }
        text = full
        compose.waitForIdle()
        compose.onNode(isHeading()).assert(hasText("단계"))
        compose.onNodeWithText("끝 굵게").assertExists()
        compose.onNodeWithContentDescription("JSON code\n{\"a\": 1}").assertExists()
    }

    @Test
    fun `빈 마크다운은 아무것도 그리지 않는다`() {
        compose.setContent { KMarkdownText("", Modifier.testTag("md")) }
        compose.onNodeWithTag("md").assertExists()
    }

    @Test
    fun `코드 블록은 한 번에 읽고 복사 동작을 호스트 태그가 붙은 노드에 둔다`() {
        var copied = 0
        compose.setContent {
            KCodeBlock("print(1)\nprint(2)", Modifier.testTag("code"), language = "py", showLineNumbers = true, onCopy = { copied++ })
        }
        val node = compose.onNodeWithTag("code")
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Python code\nprint(1)\nprint(2)")))
        val actions = node.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertThat(actions.map { it.label }).containsExactly("Copy")
        compose.runOnIdle { actions.single().action() }
        assertThat(copied).isEqualTo(1)
        // 줄 번호와 코드 글자는 따로 읽지 않는다.
        compose.onAllNodesWithText("1", substring = true).assertCountEquals(0)
    }

    @Test
    fun `복사하지 않는 코드 블록과 모르는 언어와 빈 코드도 그린다`() {
        compose.setContent {
            KCodeBlock("", Modifier.testTag("empty"), language = "rust", copyable = false, wrapLines = true, showLineNumbers = true)
        }
        val node = compose.onNodeWithTag("empty").fetchSemanticsNode()
        assertThat(node.config.getOrNull(SemanticsActions.CustomActions)).isNull()
        assertThat(node.config[SemanticsProperties.ContentDescription]).containsExactly("rust code\n")
    }

    @Test
    fun `프롬프트는 빈 글이면 보내지 않고 글을 쓰면 보낸다`() {
        val state = TextFieldState()
        var sent = 0
        compose.setContent { KPromptInput(state, onSend = { sent++ }, Modifier.fillMaxWidth().testTag("prompt")) }
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("안녕")
        compose.onNodeWithContentDescription("Send").assertIsEnabled().performClick()
        assertThat(sent).isEqualTo(1)
        compose.onNodeWithTag("prompt").assertExists()
    }

    @Test
    fun `생성 중에는 멈춤 버튼이 되고 보내지 않는다`() {
        val state = TextFieldState("다음 질문")
        var sent = 0
        var stopped = 0
        var generating by mutableStateOf(true)
        compose.setContent { KPromptInput(state, onSend = { sent++ }, generating = generating, onStop = { stopped++ }) }
        compose.onNodeWithContentDescription("Stop generating").assertIsEnabled().performClick()
        assertThat(stopped).isEqualTo(1)
        val field = compose.onNode(hasSetTextAction())
        field.requestFocus()
        field.performKeyInput { pressKey(Key.Enter) }
        assertThat(sent).isEqualTo(0)
        assertThat(state.text.toString()).isEqualTo("다음 질문")
        generating = false
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Send").assertIsEnabled()
    }

    @Test
    fun `하드웨어 Enter는 보내고 Shift+Enter는 줄을 바꾼다`() {
        val state = TextFieldState("첫 줄")
        var sent = 0
        compose.setContent { KPromptInput(state, onSend = { sent++ }) }
        val field = compose.onNode(hasSetTextAction())
        field.requestFocus()
        field.performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) } }
        assertThat(sent).isEqualTo(0)
        field.performKeyInput { pressKey(Key.Enter) }
        assertThat(sent).isEqualTo(1)
    }

    @Test
    fun `첨부 칩의 X를 누르면 그 첨부를 넘기고 꺼져 있으면 버튼이 없다`() {
        val removed = mutableListOf<String>()
        var enabled by mutableStateOf(true)
        val files = listOf(KPromptAttachment("a", "설계.pdf", "1.2MB"), KPromptAttachment("b", "화면.png"))
        compose.setContent {
            KPromptInput(TextFieldState(), onSend = {}, attachments = files, onRemoveAttachment = { removed += it.id }, enabled = enabled)
        }
        compose.onAllNodesWithContentDescription("Remove")[1].performSemanticsAction(SemanticsActions.OnClick)
        assertThat(removed).containsExactly("b")
        enabled = false
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Remove").assertCountEquals(0)
    }

    @Test
    fun `애니메이션 삭제에서도 프롬프트와 생각 중 표시를 그린다`() {
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides true) {
                KPromptInput(TextFieldState("x"), onSend = {}, generating = true)
                KThinkingIndicator(thinking = true, elapsedSeconds = 4)
            }
        }
        compose.onNodeWithContentDescription("Stop generating").assertExists()
        compose.onNodeWithText("Thinking 4s").assertExists()
    }

    @Test
    fun `생각 중 표시는 시간을 세고 끝나면 생각한 시간으로 바뀐다`() {
        var thinking by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { KThinkingIndicator(thinking = thinking) }
        compose.onNodeWithText("Thinking").assertExists()
        advanceBoth(3_100)
        compose.onNodeWithText("Thinking 3s").assertExists()
        thinking = false
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Thought for 3s").assertExists()
    }

    @Test
    fun `단계가 있으면 눌러 펼치고 단계 상태를 읽는다`() {
        val steps = listOf(
            KThinkingStep("q", "질문 이해", done = true),
            KThinkingStep("s", "자료 찾기", done = false),
        )
        compose.setContent {
            KThinkingIndicator(thinking = false, steps = steps, elapsedSeconds = 75, modifier = Modifier.testTag("think"))
        }
        val header = compose.onNodeWithText("Thought for 1m 15s")
        header.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
        compose.onNodeWithText("질문 이해").assertDoesNotExist()
        header.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("질문 이해")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Done"))
        compose.onNodeWithText("자료 찾기")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Stopped"))
        compose.onNodeWithText("Thought for 1m 15s").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
    }

    @Test
    fun `단계가 없으면 펼칠 수 없고 음수 시간은 0으로 본다`() {
        compose.setContent { KThinkingIndicator(thinking = false, elapsedSeconds = -5) }
        compose.onNodeWithText("Thought for 0s")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Expand))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    /** 컴포넌트는 시스템 시계로 시간을 재고 화면은 테스트 시계로 갱신하므로 둘을 함께 옮긴다. */
    private fun advanceBoth(ms: Long) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(ms))
        compose.mainClock.advanceTimeBy(ms)
    }

    @Test
    fun `순서 목록 번호와 글을 한 번에 읽는다`() {
        compose.setContent { KMarkdownText("1. 설치\n2. 실행") }
        compose.onNodeWithText("설치").assert(hasText("1."))
        compose.onNodeWithText("실행").assert(hasText("2."))
    }

    @Test
    fun `완성된 마크다운의 짝 없는 별표는 그대로 보이고 생성 중에는 열린 강조로 본다`() {
        var streaming by mutableStateOf(true)
        compose.setContent { KMarkdownText("앞 문단\n\nO(n*m) 시간", streaming = streaming) }
        compose.onNodeWithText("O(nm) 시간").assertExists()
        streaming = false
        compose.waitForIdle()
        compose.onNodeWithText("O(n*m) 시간").assertExists()
    }

    @Test
    fun `빈 글에서 하드웨어 Enter는 보내지도 줄을 바꾸지도 않는다`() {
        val state = TextFieldState()
        var sent = 0
        compose.setContent { KPromptInput(state, onSend = { sent++ }) }
        val field = compose.onNode(hasSetTextAction())
        field.requestFocus()
        field.performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertThat(state.text.toString()).isEmpty() }
        assertThat(sent).isEqualTo(0)
    }

    @Test
    fun `생각이 끝나면 한 번만 알리고 펼치고 접을 때는 다시 알리지 않는다`() {
        val live = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)
        val quiet = SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion)
        var thinking by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KThinkingIndicator(thinking = thinking, steps = listOf(KThinkingStep("q", "질문", done = true)), elapsedSeconds = 2)
        }
        compose.onNodeWithText("Thinking 2s").assert(quiet)
        thinking = false
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        val header = compose.onNodeWithText("Thought for 2s")
        header.assert(live)
        header.performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Thought for 2s").assert(quiet)
        thinking = true
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        thinking = false
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Thought for 2s").assert(live)
        compose.mainClock.advanceTimeBy(3_500)
        compose.onNodeWithText("Thought for 2s").assert(quiet)
    }

    @Test
    fun `처음부터 끝난 생각은 알리지 않는다`() {
        compose.setContent { KThinkingIndicator(thinking = false, elapsedSeconds = 5) }
        compose.onNodeWithText("Thought for 5s").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `직접 센 시간은 구성에서 빠져 있던 동안에도 흐른다`() {
        var shown by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val holder = rememberSaveableStateHolder()
            if (shown) holder.SaveableStateProvider("think") { KThinkingIndicator(thinking = true) }
        }
        advanceBoth(2_100)
        compose.onNodeWithText("Thinking 2s").assertExists()
        shown = false
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(5))
        shown = true
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Thinking 7s").assertExists()
    }
}
