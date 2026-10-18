package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 이름을 직접 정한 노드가 안쪽 글자를 다시 읽지 않는지. TalkBack은 이름과 함께 묶인 글자도 읽어서,
 * 통화 시간 "1:47" 뒤에 콜론을 한 번 더 읽었다(호스트 앱 TalkBack 16 피드백).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko")
class ReadOnceTest {
    @get:Rule
    val compose = createComposeRule()

    private fun SemanticsNodeInteraction.mergedText() =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    @Test
    fun `굴러가는 숫자는 전체 값만 읽고 콜론을 따로 읽지 않는다`() {
        compose.setContent { RollingText("1:47") }
        assertThat(compose.onNodeWithContentDescription("1:47").mergedText()).isEmpty()
    }

    @Test
    fun `타자 효과는 전체 문장만 읽고 치는 중인 글자를 읽지 않는다`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { TypewriterText("안녕하세요") }
        compose.mainClock.advanceTimeBy(100)
        assertThat(compose.onNodeWithContentDescription("안녕하세요").mergedText()).isEmpty()
    }

    @Test
    fun `비밀번호 강도는 이름과 상태만 읽고 강도 글자를 또 읽지 않는다`() {
        compose.setContent { KPasswordField("Abcdef12!@#xyz", {}, "비밀번호") }
        val meter = compose.onNodeWithContentDescription("비밀번호 강도")
        assertThat(meter.fetchSemanticsNode().config[SemanticsProperties.StateDescription]).isNotEmpty()
        assertThat(meter.mergedText()).isEmpty()
    }

    @Test
    fun `채팅 입력은 라벨과 안내 글이 같으면 한 번만 읽는다`() {
        compose.setContent {
            KChatInput(androidx.compose.foundation.text.input.rememberTextFieldState(), onSend = {}, label = "메시지", placeholder = "메시지")
        }
        val field = compose.onNode(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
        assertThat(field.mergedText()).containsExactly("메시지")
    }

    @Test
    fun `알림 상자와 배너는 상태, 제목, 본문을 한 번에 읽는다`() {
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                KAlert("테스트 전용 토큰입니다", status = KStatus.Warning, title = "토큰 출처")
                KBanner(true, "연결이 끊겼어요", status = KStatus.Error, title = "오프라인")
            }
        }
        listOf("토큰 출처" to "테스트 전용 토큰입니다", "오프라인" to "연결이 끊겼어요").forEach { (title, body) ->
            val node = compose.onNode(androidx.compose.ui.test.hasText(title)).fetchSemanticsNode()
            assertThat(node.config[SemanticsProperties.Text].map { it.text }).containsExactly(title, body).inOrder()
            // 상태 아이콘의 이름도 같은 노드에 합쳐져 따로 멈추지 않는다.
            assertThat(node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).isNotEmpty()
        }
    }

    private fun expandableState(): String? {
        compose.setContent { KExpandable(false, {}, { androidx.compose.foundation.text.BasicText("배송 정보") }) { } }
        val node = compose.onNode(androidx.compose.ui.test.hasText("배송 정보")).fetchSemanticsNode()
        assertThat(node.config.contains(androidx.compose.ui.semantics.SemanticsActions.Expand)).isTrue()
        return node.config.getOrNull(SemanticsProperties.StateDescription)
    }

    @Test
    fun `Android 15까지는 펼침 상태를 문구로 알린다`() {
        assertThat(expandableState()).isEqualTo("접힘")
    }

    @Test
    @Config(sdk = [36])
    fun `Android 16부터는 TalkBack이 펼침 동작에서 상태를 읽으므로 문구를 또 넣지 않는다`() {
        assertThat(expandableState()).isNull()
    }

    @Test
    fun `전후 비교는 TalkBack이 멈추는 슬라이더 하나로 읽힌다`() {
        lateinit var view: android.view.View
        compose.setContent {
            view = androidx.compose.ui.platform.LocalView.current
            BeforeAfterSlider(
                before = { Box(Modifier.fillMaxSize()) },
                after = { Box(Modifier.fillMaxSize()) },
                modifier = Modifier.size(200.dp),
            )
        }
        val node = compose.onNodeWithContentDescription("전후 비교").fetchSemanticsNode()
        val info = view.accessibilityNodeProvider.createAccessibilityNodeInfo(node.id)!!
        // 자식이 남은 채 묶지 않으면 TalkBack이 쓸어 넘길 때 이 노드에 멈추지 않는다.
        assertThat(info.isScreenReaderFocusable).isTrue()
        assertThat(info.stateDescription.toString()).isEqualTo("전 50%, 후 50%")
        // 안쪽 '전', '후' 표시는 상태 문구가 이미 말하므로 따로 읽지 않는다.
        assertThat(node.config.getOrNull(SemanticsProperties.Text)).isNull()
    }

    @Test
    fun `태그 칩은 태그 이름을 한 번만 읽는다`() {
        compose.setContent { KTagInput(listOf("디자인"), {}) }
        assertThat(compose.onNodeWithContentDescription("디자인").mergedText()).isEmpty()
    }
}
