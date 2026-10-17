package com.naury.kinetica.ui

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** androidx.activity 1.13은 뒤로 가기를 취소하면 handler 코루틴도 취소한다. 되돌리는 애니메이션이 함께 끊겨 반쯤 멈추면 안 된다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BackCancelTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var dispatcher: OnBackPressedDispatcher

    private fun startAndCancel(progress: Float) {
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 0f, progress, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
    }

    @Test
    fun `서랍은 예측형 뒤로 가기를 취소하면 끝까지 다시 열린다`() {
        lateinit var state: KDrawerState
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            state = rememberKDrawerState(initiallyOpen = true)
            KDrawer(drawerContent = { BasicText("메뉴") }, Modifier.fillMaxSize(), state) { Box(Modifier.fillMaxSize()) }
        }
        startAndCancel(0.8f)
        assertThat(state.progress).isLessThan(1f)

        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()

        assertThat(state.progress).isEqualTo(1f)
        assertThat(state.isOpen).isTrue()
    }

    @Test
    fun `스토리 뷰어는 예측형 뒤로 가기를 취소하면 원래 크기로 돌아온다`() {
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            // 스캐폴드 안이어야 대화 상자 창이 아닌 같은 창에 떠서 이 dispatcher로 뒤로 가기를 받는다.
            KineticaScaffold(Modifier.fillMaxSize()) {
                KStoryViewer(true, {}, rememberKStoryViewerState(2), durationMs = 600_000) {
                    Box(Modifier.fillMaxSize().testTag("page"))
                }
            }
        }
        compose.waitForIdle()
        val full = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot.width
        startAndCancel(1f)
        assertThat(compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot.width).isLessThan(full * 0.98f)

        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()

        assertThat(compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot.width).isWithin(0.5f).of(full)
    }
}
