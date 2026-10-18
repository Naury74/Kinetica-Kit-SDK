package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class KNotifyTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var scope: CoroutineScope

    private fun setUp(island: KIslandState? = null, snackbar: KSnackbarState? = null) {
        compose.setContent {
            scope = rememberCoroutineScope()
            KineticaScaffold(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) { BasicText("내용") }
                island?.let { KIslandHost(it) }
                snackbar?.let { KSnackbarHost(it) }
            }
        }
    }

    /** 시계를 멈춘 채 [ms]만큼 흘린다. 자동으로 흘리면 waitForIdle이 표시 시간까지 건너뛴다. */
    private fun step(ms: Long = 500) {
        compose.mainClock.advanceTimeBy(ms)
    }

    @Test
    fun `아일랜드 알림 문장에는 역할 이름이 붙지 않는다`() {
        val state = KIslandState()
        setUp(island = state)
        scope.launch { state.show(KIslandContent("Alex", "Loud and clear"), durationMs = null) }
        compose.waitForIdle()

        // TalkBack은 live region이 바뀌면 그 노드를 읽는데, 같은 노드에 역할이 있으면 "Button, Alex, ..."처럼 역할부터 읽는다.
        val live = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).fetchSemanticsNode()
        assertThat(live.config.getOrNull(SemanticsProperties.Role)).isNull()
        assertThat(live.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).containsExactly("Alex", "Loud and clear")
        assertThat(live.config.contains(SemanticsActions.OnClick)).isTrue()
    }

    @Test
    fun `아일랜드는 compact로 시작해 누르면 펼치고 다시 누르면 Clicked로 끝난다`() {
        val state = KIslandState()
        setUp(island = state)
        var result: KIslandResult? = null
        scope.launch { result = state.show(KIslandContent("타이머", "남은 시간", compactText = "00:42"), durationMs = null) }
        compose.waitForIdle()

        compose.onNodeWithText("00:42", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("남은 시간", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("타이머")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithText("남은 시간", useUnmergedTree = true).assertExists()

        assertThat(state.update(KIslandContent("타이머", "남은 시간 00:41", compactText = "00:41"))).isTrue()
        compose.waitForIdle()
        compose.onNodeWithText("남은 시간 00:41", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertThat(result).isEqualTo(KIslandResult.Clicked)
        assertThat(state.isShowing).isFalse()
        assertThat(state.update(KIslandContent("늦은 갱신"))).isFalse()
    }

    @Test
    fun `아일랜드는 닫기 동작으로 닫히고 시간이 지나면 TimedOut으로 끝난다`() {
        val state = KIslandState()
        setUp(island = state)
        var first: KIslandResult? = null
        var second: KIslandResult? = null
        scope.launch {
            first = state.show(KIslandContent("새 메시지", "민지: 오늘 저녁 어때?"), durationMs = null)
            second = state.show(KIslandContent("업로드 완료"), durationMs = 1_000)
        }
        compose.waitForIdle()
        compose.onNodeWithText("새 메시지", substring = true).performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitForIdle()
        assertThat(first).isEqualTo(KIslandResult.Dismissed)

        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        assertThat(second).isEqualTo(KIslandResult.TimedOut)
        assertThat(runCatching { runBlocking { state.show(KIslandContent("x"), 0) } }.isFailure).isTrue()
    }

    @Test
    fun `아일랜드가 줄어드는 중에 취소되어도 캡슐이 남지 않는다`() {
        val state = KIslandState()
        setUp(island = state)
        compose.mainClock.autoAdvance = false
        val job = scope.launch { state.show(KIslandContent("업로드 완료"), durationMs = 1_000) }
        step(1_100)
        assertThat(state.isShowing).isFalse()
        assertThat(state.visible).isTrue()

        // 시간이 다 되어 줄어드는 애니메이션을 기다리는 동안 취소한다.
        job.cancel()
        step(16)
        assertThat(state.visible).isFalse()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun `스낵바는 동작 버튼을 누르면 Action을 돌려주고 다음 스낵바를 차례로 보여 준다`() {
        val state = KSnackbarState()
        setUp(snackbar = state)
        compose.mainClock.autoAdvance = false
        val results = mutableListOf<KSnackbarResult>()
        scope.launch { results += state.show("사진을 지웠습니다", actionLabel = "되돌리기", durationMs = 60_000) }
        scope.launch { results += state.show("두 번째", durationMs = 60_000) }
        step()

        compose.onNodeWithText("사진을 지웠습니다", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("두 번째", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("되돌리기").performClick()
        step()
        assertThat(results).containsExactly(KSnackbarResult.Action)

        step()
        compose.onNodeWithText("두 번째", useUnmergedTree = true).assertExists()
        assertThat(state.currentMessage).isEqualTo("두 번째")
        compose.onNodeWithContentDescription("Dismiss").performClick()
        step()
        assertThat(results).containsExactly(KSnackbarResult.Action, KSnackbarResult.Dismissed).inOrder()
    }

    @Test
    fun `스낵바는 시간이 지나면 Dismissed로 끝나고 TalkBack이 읽도록 알린다`() {
        val state = KSnackbarState()
        setUp(snackbar = state)
        compose.mainClock.autoAdvance = false
        var result: KSnackbarResult? = null
        scope.launch { result = state.show("저장했습니다", durationMs = 2_000) }
        step()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).assertExists()
        assertThat(result).isNull()

        step(2_000)
        step()
        assertThat(result).isEqualTo(KSnackbarResult.Dismissed)
        compose.onNodeWithText("저장했습니다", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `스캐폴드 안 스낵바는 탭 바 위에 뜬다`() {
        val snackbar = KSnackbarState()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            scope = rememberCoroutineScope()
            KineticaScaffold(
                Modifier.fillMaxSize(),
                bottomBar = { Box(Modifier.fillMaxWidth().height(100.dp).testTag("bar")) },
            ) {
                KSnackbarHost(snackbar)
            }
        }
        scope.launch { snackbar.show("지웠어요", durationMs = 10_000) }
        compose.waitForIdle()
        step(800)

        val bar = compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
        val pill = compose.onNodeWithText("지웠어요", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertThat(pill.bottom).isAtMost(bar.top)
    }

    @Test
    fun `같은 상태를 두 호스트가 그리면 나중에 들어온 호스트만 알림을 띄운다`() {
        // 앱 전체 호스트의 상태를 통화 화면처럼 안에 호스트를 둔 화면에 넘겨도 알림은 한 번만 뜬다.
        val state = KIslandState()
        var inner by mutableStateOf(false)
        compose.setContent {
            scope = rememberCoroutineScope()
            KineticaScaffold(Modifier.fillMaxSize()) {
                if (inner) KIslandHost(state, Modifier.testTag("inner"))
                KIslandHost(state, Modifier.testTag("outer"))
            }
        }
        scope.launch { state.show(KIslandContent("윤아", "화면 잘 보여요"), durationMs = null) }
        compose.waitForIdle()
        compose.onNodeWithTag("outer").assertExists()

        inner = true
        compose.waitForIdle()
        assertThat(compose.onAllNodesWithText("윤아", useUnmergedTree = true).fetchSemanticsNodes()).hasSize(1)
        compose.onNodeWithTag("inner").assertExists()
        compose.onNodeWithTag("outer").assertDoesNotExist()

        // 안쪽 화면이 닫히면 앞의 호스트가 이어서 그린다.
        inner = false
        compose.waitForIdle()
        compose.onNodeWithTag("outer").assertExists()
        state.dismiss()
        compose.waitForIdle()
    }
}
