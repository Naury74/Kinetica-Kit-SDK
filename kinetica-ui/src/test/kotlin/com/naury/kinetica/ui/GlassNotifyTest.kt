package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
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
class GlassNotifyTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var scope: CoroutineScope

    private fun setUp(island: GlassIslandState? = null, snackbar: GlassSnackbarState? = null) {
        compose.setContent {
            scope = rememberCoroutineScope()
            GlassScaffold(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) { BasicText("내용") }
                island?.let { GlassIslandHost(it) }
                snackbar?.let { GlassSnackbarHost(it) }
            }
        }
    }

    /** 시계를 멈춘 채 [ms]만큼 흘린다. 자동으로 흘리면 waitForIdle이 표시 시간까지 건너뛴다. */
    private fun step(ms: Long = 500) {
        compose.mainClock.advanceTimeBy(ms)
    }

    @Test
    fun `아일랜드는 compact로 시작해 누르면 펼치고 다시 누르면 Clicked로 끝난다`() {
        val state = GlassIslandState()
        setUp(island = state)
        var result: GlassIslandResult? = null
        scope.launch { result = state.show(GlassIslandContent("타이머", "남은 시간", compactText = "00:42"), durationMs = null) }
        compose.waitForIdle()

        compose.onNodeWithText("00:42", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("남은 시간", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("타이머")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithText("남은 시간", useUnmergedTree = true).assertExists()

        state.update(GlassIslandContent("타이머", "남은 시간 00:41", compactText = "00:41"))
        compose.waitForIdle()
        compose.onNodeWithText("남은 시간 00:41", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertThat(result).isEqualTo(GlassIslandResult.Clicked)
        assertThat(state.isShowing).isFalse()
    }

    @Test
    fun `아일랜드는 닫기 동작으로 닫히고 시간이 지나면 TimedOut으로 끝난다`() {
        val state = GlassIslandState()
        setUp(island = state)
        var first: GlassIslandResult? = null
        var second: GlassIslandResult? = null
        scope.launch {
            first = state.show(GlassIslandContent("새 메시지", "민지: 오늘 저녁 어때?"), durationMs = null)
            second = state.show(GlassIslandContent("업로드 완료"), durationMs = 1_000)
        }
        compose.waitForIdle()
        compose.onNodeWithText("새 메시지", substring = true).performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitForIdle()
        assertThat(first).isEqualTo(GlassIslandResult.Dismissed)

        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        assertThat(second).isEqualTo(GlassIslandResult.TimedOut)
        assertThat(runCatching { runBlocking { state.show(GlassIslandContent("x"), 0) } }.isFailure).isTrue()
    }

    @Test
    fun `아일랜드가 줄어드는 중에 취소되어도 캡슐이 남지 않는다`() {
        val state = GlassIslandState()
        setUp(island = state)
        compose.mainClock.autoAdvance = false
        val job = scope.launch { state.show(GlassIslandContent("업로드 완료"), durationMs = 1_000) }
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
        val state = GlassSnackbarState()
        setUp(snackbar = state)
        compose.mainClock.autoAdvance = false
        val results = mutableListOf<GlassSnackbarResult>()
        scope.launch { results += state.show("사진을 지웠습니다", actionLabel = "되돌리기", durationMs = 60_000) }
        scope.launch { results += state.show("두 번째", durationMs = 60_000) }
        step()

        compose.onNodeWithText("사진을 지웠습니다", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("두 번째", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("되돌리기").performClick()
        step()
        assertThat(results).containsExactly(GlassSnackbarResult.Action)

        step()
        compose.onNodeWithText("두 번째", useUnmergedTree = true).assertExists()
        assertThat(state.currentMessage).isEqualTo("두 번째")
        compose.onNodeWithContentDescription("Dismiss").performClick()
        step()
        assertThat(results).containsExactly(GlassSnackbarResult.Action, GlassSnackbarResult.Dismissed).inOrder()
    }

    @Test
    fun `스낵바는 시간이 지나면 Dismissed로 끝나고 TalkBack이 읽도록 알린다`() {
        val state = GlassSnackbarState()
        setUp(snackbar = state)
        compose.mainClock.autoAdvance = false
        var result: GlassSnackbarResult? = null
        scope.launch { result = state.show("저장했습니다", durationMs = 2_000) }
        step()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).assertExists()
        assertThat(result).isNull()

        step(2_000)
        step()
        assertThat(result).isEqualTo(GlassSnackbarResult.Dismissed)
        compose.onNodeWithText("저장했습니다", useUnmergedTree = true).assertDoesNotExist()
    }
}
