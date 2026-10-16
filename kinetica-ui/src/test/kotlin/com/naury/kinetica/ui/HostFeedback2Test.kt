package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 영상 통화 호스트 앱의 2차 피드백: TextFieldState 입력, 아일랜드 key 합치기와 leading 슬롯, 글로우 진하기, 판 위 말풍선. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HostFeedback2Test {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `TextFieldState 채팅 입력줄은 키보드 보내기와 하드웨어 Enter로 보내고 최대 글자 수를 지킨다`() {
        val state = TextFieldState()
        val sent = mutableListOf<String>()
        compose.setContent {
            KChatInput(
                state,
                onSend = {
                    sent += state.text.toString()
                    state.clearText()
                },
                maxLength = 5,
                showCounter = true,
            )
        }
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
        compose.onNodeWithText("Message").performTextInput("안녕")
        compose.onNodeWithContentDescription("2 of 5 characters", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Send").assertIsEnabled()

        compose.onNodeWithText("안녕").performImeAction()
        assertThat(sent).containsExactly("안녕")
        assertThat(state.text.toString()).isEmpty()

        compose.onNodeWithText("Message").performTextInput("1234567")
        // 꽉 차지 않은 상태에서 길게 넣으면 넘치는 끝만 버린다.
        assertThat(state.text.toString()).isEqualTo("12345")
        compose.onNodeWithText("12345").performTextInput("6")
        assertThat(state.text.toString()).isEqualTo("12345")

        compose.onNodeWithText("12345").performKeyInput { pressKey(Key.Enter) }
        assertThat(sent).containsExactly("안녕", "12345").inOrder()
    }

    @Test
    fun `String 채팅 입력줄도 키보드 보내기와 최대 글자 수를 쓴다`() {
        var text by mutableStateOf("")
        var sent = 0
        compose.setContent { KChatInput(text, { text = it }, { sent++ }, maxLength = 3) }
        compose.onNodeWithText("Message").performTextInput("abcdef")
        assertThat(text).isEqualTo("abc")
        compose.onNodeWithText("abc").performImeAction()
        assertThat(sent).isEqualTo(1)
    }

    @Test
    fun `TextFieldState 입력란은 글자 수를 지키고 지우기 버튼으로 상태를 비운다`() {
        val name = TextFieldState()
        val memo = TextFieldState("첫 줄")
        var submitted = 0
        compose.setContent {
            Box {
                KTextField(
                    name,
                    "이름",
                    Modifier.testTag("name"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    onKeyboardAction = { submitted++ },
                    maxLength = 4,
                )
                KTextArea(memo, "메모", Modifier.padding(top = 120.dp).testTag("memo"))
            }
        }
        val nameField = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("name")))
        val memoField = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("memo")))
        nameField.performTextInput("가나다라마바")
        assertThat(name.text.toString()).isEqualTo("가나다라")
        compose.onNodeWithContentDescription("4 of 4 characters", useUnmergedTree = true).assertExists()
        nameField.performImeAction()
        assertThat(submitted).isEqualTo(1)

        nameField.performClick()
        compose.onNodeWithContentDescription("Clear text").performClick()
        assertThat(name.text.toString()).isEmpty()

        compose.onNodeWithText("첫 줄").assertExists()
        memoField.performTextInput("\n둘째 줄")
        assertThat(memo.text.toString()).isEqualTo("첫 줄\n둘째 줄")
    }

    private lateinit var scope: CoroutineScope

    private fun island(state: KIslandState) {
        compose.setContent {
            scope = rememberCoroutineScope()
            KineticaScaffold(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) { BasicText("내용") }
                KIslandHost(state)
            }
        }
    }

    @Test
    fun `아일랜드 leading 슬롯은 compact와 expanded 모드 모두에서 아이콘 대신 보인다`() {
        val state = KIslandState()
        island(state)
        val avatar = KIslandContent(
            "민지",
            "오늘 저녁 어때?",
            compactText = "1",
            leading = { Box(Modifier.size(64.dp).background(Color.Red).testTag("avatar")) },
        )
        scope.launch { state.show(avatar, durationMs = null) }
        compose.waitForIdle()
        compose.onNodeWithTag("avatar", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("민지").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("오늘 저녁 어때?", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("avatar", useUnmergedTree = true).assertExists()
        state.dismiss()
        compose.waitForIdle()
    }

    @Test
    fun `같은 key의 알림은 대기열에서 새 내용으로 바뀌고 합쳐진 호출은 같은 결과를 받는다`() {
        val state = KIslandState()
        island(state)
        val results = mutableListOf<Pair<String, KIslandResult>>()
        compose.mainClock.autoAdvance = false
        scope.launch { results += "통화" to state.show(KIslandContent("통화 중"), durationMs = null) }
        scope.launch { results += "채팅1" to state.show(KIslandContent("채팅 1"), durationMs = 1_000, key = "chat") }
        scope.launch { results += "채팅2" to state.show(KIslandContent("채팅 2"), durationMs = 1_000, key = "chat") }
        scope.launch { results += "채팅3" to state.show(KIslandContent("채팅 3"), durationMs = 1_000, key = "chat") }
        compose.mainClock.advanceTimeBy(500)
        assertThat(state.content?.title).isEqualTo("통화 중")

        state.dismiss()
        compose.mainClock.advanceTimeBy(600)
        // 대기 중이던 채팅 알림 하나만 마지막 내용으로 보인다.
        assertThat(state.content?.title).isEqualTo("채팅 3")
        compose.mainClock.advanceTimeBy(1_200)
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(state.isShowing).isFalse()
        assertThat(results.toMap()).isEqualTo(
            mapOf(
                "통화" to KIslandResult.Dismissed,
                "채팅1" to KIslandResult.TimedOut,
                "채팅2" to KIslandResult.TimedOut,
                "채팅3" to KIslandResult.TimedOut,
            ),
        )
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun `replaceShowing이면 보이는 같은 key 알림을 바로 바꾸고 표시 시간을 다시 잰다`() {
        val state = KIslandState()
        island(state)
        var first: KIslandResult? = null
        var second: KIslandResult? = null
        compose.mainClock.autoAdvance = false
        scope.launch { first = state.show(KIslandContent("채팅 1"), durationMs = 1_000, key = "chat") }
        compose.mainClock.advanceTimeBy(800)
        scope.launch { second = state.show(KIslandContent("채팅 2"), durationMs = 1_000, key = "chat", replaceShowing = true) }
        compose.mainClock.advanceTimeBy(500)
        // 처음 표시 시간(1초)은 지났지만 바꾼 순간부터 다시 재므로 아직 보인다.
        assertThat(state.content?.title).isEqualTo("채팅 2")
        assertThat(first).isNull()

        // 다른 key는 합치지 않고 뒤에 줄을 선다.
        var other: KIslandResult? = null
        scope.launch { other = state.show(KIslandContent("업로드"), durationMs = 1_000, key = "upload", replaceShowing = true) }
        compose.mainClock.advanceTimeBy(100)
        assertThat(state.content?.title).isEqualTo("채팅 2")

        compose.mainClock.advanceTimeBy(600)
        assertThat(second).isEqualTo(KIslandResult.TimedOut)
        compose.mainClock.advanceTimeBy(600)
        // 처음 호출은 줄어드는 애니메이션까지 기다린 뒤 돌아온다.
        assertThat(first).isEqualTo(KIslandResult.TimedOut)
        assertThat(state.content?.title).isEqualTo("업로드")
        state.dismiss()
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(other).isEqualTo(KIslandResult.Dismissed)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun `글로우 테두리는 진하기 0이면 그리지 않고 람다 진하기를 그리기 단계에서 읽는다`() {
        var level by mutableFloatStateOf(0f)
        var floatLevel by mutableFloatStateOf(0f)
        compose.setContent {
            Box(Modifier.width(300.dp).background(Color.White)) {
                Box(Modifier.padding(30.dp).size(100.dp).glowBorder({ level }).testTag("lambda"))
                Box(Modifier.padding(start = 160.dp, top = 30.dp).size(100.dp).glowBorder(intensity = floatLevel).testTag("float"))
            }
        }
        fun edgeIsWhite(tag: String): Boolean {
            val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            val edge = pixels[1, pixels.height / 2]
            return edge.red > 0.98f && edge.green > 0.98f && edge.blue > 0.98f
        }
        assertThat(edgeIsWhite("lambda")).isTrue()
        assertThat(edgeIsWhite("float")).isTrue()
        level = 1f
        floatLevel = 1f
        compose.waitForIdle()
        assertThat(edgeIsWhite("lambda")).isFalse()
        assertThat(edgeIsWhite("float")).isFalse()
    }

    @Test
    fun `글로우 진하기는 애니메이션 값을 받아 서서히 나타난다`() {
        var on by mutableStateOf(false)
        compose.setContent {
            val level by animateFloatAsState(if (on) 1f else 0f, label = "glow")
            Box(Modifier.background(Color.White).padding(20.dp).size(80.dp).glowBorder({ level }).testTag("glow"))
        }
        on = true
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("glow").captureToImage().toPixelMap()
        assertThat(pixels[1, pixels.height / 2].red < 0.98f || pixels[1, pixels.height / 2].blue < 0.98f).isTrue()
    }

    @Test
    fun `팝업 판 안의 받은 말풍선은 그림자 없이 옅은 한 겹으로 그린다`() {
        compose.setContent {
            Box(Modifier.testTag("frame").width(300.dp).background(Color.White).padding(vertical = 40.dp)) {
                KGlassLayer {
                    KChatBubble("안녕하세요", KChatSide.Received, Modifier.testTag("bubble"), tail = false, surface = SurfaceStyles.Liquid)
                }
            }
        }
        val bubble = compose.onNodeWithTag("bubble").fetchSemanticsNode().boundsInRoot
        val pixels = compose.onNodeWithTag("frame").captureToImage().toPixelMap()
        val x = (bubble.left + bubble.width / 8).toInt()
        // 말풍선 바로 아래는 그림자가 없어 바탕 그대로다.
        val below = pixels[x, bubble.bottom.toInt() + 4]
        assertThat(below.red).isGreaterThan(0.97f)
        // 말풍선 안은 글자 색을 옅게 깐 한 겹이라 바탕보다 조금 어둡다.
        val inside = pixels[(bubble.left + 20).toInt(), (bubble.top + bubble.height / 2).toInt()]
        assertThat(inside.red).isLessThan(0.97f)
    }
}
