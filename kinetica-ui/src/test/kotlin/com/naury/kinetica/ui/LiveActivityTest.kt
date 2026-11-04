package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LiveActivityTest {
    @get:Rule
    val compose = createComposeRule()

    private fun activity(id: String, title: String, status: String, panel: String = "$title 패널") = KLiveActivity(
        id,
        title,
        status,
        leading = { Box(Modifier.size(20.dp)) },
        trailing = { BasicText(status) },
        expanded = { BasicText(panel) },
    )

    private fun setUp(
        state: KLiveActivityState,
        position: KLiveActivityPosition = KLiveActivityPosition.Top,
        reduced: Boolean? = null,
        extra: @Composable () -> Unit = {
        },
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides reduced) {
                KineticaScaffold(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) { BasicText("내용") }
                    KLiveActivityHost(state, position = position)
                    extra()
                }
            }
        }
    }

    @Test
    fun `상태는 같은 id를 제자리에서 바꾸고 끝내면 펼침도 푼다`() {
        val state = KLiveActivityState()
        state.start(activity("a", "배달", "12분"))
        state.start(activity("b", "타이머", "5분"))
        state.start(activity("a", "배달", "10분"))
        assertThat(state.activities.map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(state.activities.first().status).isEqualTo("10분")
        assertThat(state.update(activity("c", "없음", ""))).isFalse()

        assertThat(state.expand("b")).isTrue()
        assertThat(state.expandedId).isEqualTo("b")
        assertThat(state.end("b")).isTrue()
        assertThat(state.expandedId).isNull()
        assertThat(state.expand("zzz")).isFalse()

        state.start(activity("c", "녹음", "00:10"))
        assertThat(state.promote("c")).isTrue()
        assertThat(state.activities.map { it.id }).containsExactly("c", "a").inOrder()
    }

    @Test
    fun `접힌 캡슐은 이름과 상태를 읽고 펼치기 동작으로 펼친 화면을 보인다`() {
        val state = KLiveActivityState()
        setUp(state)
        state.start(activity("timer", "타이머", "5분 남음"))
        compose.waitForIdle()

        compose.onNodeWithText("타이머 패널", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("타이머, 5분 남음").performSemanticsAction(SemanticsActions.Expand)
        compose.waitForIdle()
        assertThat(state.expandedId).isEqualTo("timer")
        compose.onNodeWithText("타이머 패널", useUnmergedTree = true).assertExists()

        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse)).performSemanticsAction(SemanticsActions.Collapse)
        compose.waitForIdle()
        assertThat(state.expandedId).isNull()
        compose.onNodeWithText("타이머 패널", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `활동이 둘이면 두 번째는 원으로 보이고 누르면 그 활동을 펼친다`() {
        val state = KLiveActivityState()
        setUp(state, KLiveActivityPosition.Bottom)
        state.start(activity("ride", "배차", "3분 뒤 도착"))
        state.start(activity("rec", "녹음", "00:12"))
        compose.waitForIdle()

        compose.onNodeWithContentDescription("녹음, 00:12").performClick()
        compose.waitForIdle()
        assertThat(state.expandedId).isEqualTo("rec")
        compose.onNodeWithText("녹음 패널", useUnmergedTree = true).assertExists()
        // 펼친 동안 원은 캡슐에 합쳐져 TalkBack에도 보이지 않는다.
        compose.onAllNodesWithContentDescription("배차, 3분 뒤 도착").assertCountEquals(0)
    }

    @Test
    fun `같은 상태를 두 호스트가 그리면 한 번만 보인다`() {
        val state = KLiveActivityState()
        setUp(state) { KLiveActivityHost(state, position = KLiveActivityPosition.Bottom) }
        state.start(activity("call", "통화 중", "민지"))
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("통화 중, 민지").assertCountEquals(1)
    }

    @Test
    fun `애니메이션 삭제에서는 펼친 화면으로 바로 바뀐다`() {
        val state = KLiveActivityState()
        setUp(state, reduced = true)
        state.start(activity("w", "운동", "3.2km"))
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        state.expand("w")
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("운동 패널", useUnmergedTree = true).assertExists()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun `보통 설정에서는 접힌 몸체가 잠깐 흐려진 뒤 펼친 화면으로 바뀐다`() {
        val state = KLiveActivityState()
        setUp(state)
        state.start(activity("w", "운동", "3.2km"))
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        state.expand("w")
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("운동 패널", useUnmergedTree = true).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("운동 패널", useUnmergedTree = true).assertExists()
        compose.mainClock.autoAdvance = true
    }
}
