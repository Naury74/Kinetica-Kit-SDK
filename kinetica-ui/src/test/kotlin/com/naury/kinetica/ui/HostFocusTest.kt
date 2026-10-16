package com.naury.kinetica.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 모달이 열리고 닫힐 때 키보드 초점과 TalkBack 트리가 모달 안에 머무는지 확인한다. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HostFocusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val opener = FocusRequester()
    private var open by mutableStateOf(false)

    /** 키보드로 쓰는 상태에서 뒤 화면의 "열기" 버튼에 초점을 둔다. */
    @Composable
    private fun Background() {
        val input = LocalInputModeManager.current
        remember { input.requestInputMode(InputMode.Keyboard) }
        Column {
            BasicText("열기", Modifier.testTag("opener").focusRequester(opener).clickable { open = true })
            BasicText("뒤 버튼", Modifier.clickable { })
        }
    }

    /** 대화 상자 창은 입력 모드를 따로 가진다. 실제 기기에서는 터치 모드가 화면 전체에 공통이므로 여기서도 키보드로 맞춘다. */
    @Composable
    private fun Panel() {
        val input = LocalInputModeManager.current
        remember { input.requestInputMode(InputMode.Keyboard) }
        BasicText("확인", Modifier.testTag("ok").clickable { open = false })
        BasicText("취소", Modifier.clickable { open = false })
    }

    private fun checkModal(modal: @Composable () -> Unit) {
        compose.setContent {
            KineticaScaffold(Modifier.fillMaxSize()) {
                Background()
                modal()
            }
        }
        compose.runOnIdle { opener.requestFocus() }
        compose.onNodeWithTag("opener").assertIsFocused()

        open = true
        compose.waitForIdle()
        compose.onNodeWithTag("ok").assertIsFocused()
        compose.onNodeWithText("뒤 버튼").assertDoesNotExist()

        // Tab을 여러 번 눌러도 초점은 모달 밖으로 나가지 않는다.
        repeat(TABS) {
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.Tab) }
            compose.waitForIdle()
            compose.onNodeWithTag("opener", useUnmergedTree = true).assertIsNotFocused()
        }

        open = false
        compose.waitForIdle()
        compose.onNodeWithText("뒤 버튼").assertExists()
        compose.onNodeWithTag("opener").assertIsFocused()
    }

    @Test
    fun `KineticaScaffold 안 다이얼로그는 초점을 안으로 가져가고 닫히면 연 버튼으로 돌려준다`() {
        checkModal { KDialog(open, { open = false }) { Panel() } }
    }

    @Test
    fun `KineticaScaffold 안 시트도 같은 초점 규칙을 따른다`() {
        checkModal { KSheet(open, { open = false }) { Panel() } }
    }

    /**
     * Robolectric에서는 대화 상자 창의 Compose 요소가 초점을 받지 못한다(RequestFocus 동작을 직접 보내도 실패한다).
     * 그래서 창 안으로 초점이 들어가는 것은 기기에서 확인하고, 여기서는 창이 닫힌 뒤 뒤 화면 초점이 그대로 남는지만 본다.
     */
    @Test
    fun `밖에서 대화 상자 창으로 연 다이얼로그가 닫히면 뒤 화면 초점이 그대로다`() {
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                Background()
                KDialog(open, { open = false }) { Panel() }
            }
        }
        compose.runOnIdle { opener.requestFocus() }
        open = true
        compose.waitForIdle()
        compose.onNodeWithText("확인").assertExists()
        open = false
        compose.waitForIdle()
        compose.onNodeWithText("확인").assertDoesNotExist()
        compose.onNodeWithTag("opener").assertIsFocused()
    }

    @Test
    fun `터치로 연 시트는 안쪽 입력란에 저절로 초점을 주지 않고 뒤 입력란 초점은 걷는다`() {
        val field = FocusRequester()
        compose.setContent {
            KineticaScaffold(Modifier.fillMaxSize()) {
                androidx.compose.foundation.text.BasicTextField("", {}, Modifier.testTag("back").focusRequester(field))
                KSheet(open, { open = false }) { androidx.compose.foundation.text.BasicTextField("", {}, Modifier.testTag("inside")) }
            }
        }
        compose.runOnIdle { field.requestFocus() }
        compose.onNodeWithTag("back").assertIsFocused()
        open = true
        compose.waitForIdle()
        compose.onNodeWithTag("inside").assertIsNotFocused()
        compose.onNodeWithTag("back", useUnmergedTree = true).assertIsNotFocused()
    }

    @Test
    fun `컨텍스트 메뉴가 열리면 뒤 화면은 TalkBack에서 숨는다`() {
        compose.setContent {
            KineticaScaffold(Modifier.fillMaxSize()) {
                Column {
                    BasicText("사진", Modifier.testTag("photo").kContextMenu(listOf(KMenuItem("복사") {})))
                    BasicText("뒤 버튼", Modifier.clickable { })
                }
            }
        }
        compose.onNodeWithTag("photo").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("복사").assertExists()
        compose.onNodeWithText("뒤 버튼").assertDoesNotExist()
        compose.onNodeWithText("복사").performKeyInput { pressKey(Key.Back) }
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        compose.onNodeWithText("복사").assertDoesNotExist()
        compose.onNodeWithText("뒤 버튼").assertExists()
    }

    @Test
    fun `코치 마크가 떠 있는 동안 뒤 화면은 TalkBack에서 숨고 끝나면 돌아온다`() {
        lateinit var coach: CoachMarkState
        compose.setContent {
            coach = rememberCoachMarkState()
            KineticaScaffold(Modifier.fillMaxSize()) {
                BasicText("뒤 버튼", Modifier.coachMarkTarget(coach, "back").clickable { })
                CoachMarkHost(coach, listOf(CoachStep("back", "안내", "여기를 누르세요")))
            }
        }
        compose.runOnIdle { coach.start() }
        compose.waitForIdle()
        compose.onNodeWithText("여기를 누르세요").assertExists()
        compose.onNodeWithText("뒤 버튼").assertDoesNotExist()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        compose.onNodeWithText("여기를 누르세요").assertDoesNotExist()
        compose.onNodeWithText("뒤 버튼").assertExists()
    }

    @Test
    fun `토스트와 스낵바는 뒤 화면을 숨기지 않는다`() {
        val toast = KToastState()
        lateinit var scope: CoroutineScope
        compose.setContent {
            scope = rememberCoroutineScope()
            KineticaScaffold(Modifier.fillMaxSize()) {
                BasicText("뒤 버튼", Modifier.clickable { })
                KToastHost(toast)
            }
        }
        compose.mainClock.autoAdvance = false
        scope.launch { toast.show("저장했어요") }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("저장했어요").assertExists()
        compose.onNodeWithText("뒤 버튼").assertExists()
    }

    @Test
    fun `서랍이 열리면 뒤 화면은 숨고 초점은 서랍 안으로 들어갔다가 닫히면 돌아온다`() {
        lateinit var drawer: KDrawerState
        lateinit var scope: CoroutineScope
        compose.setContent {
            drawer = rememberKDrawerState()
            scope = rememberCoroutineScope()
            KDrawer(drawerContent = { Panel() }, state = drawer) { Background() }
        }
        compose.runOnIdle { opener.requestFocus() }
        scope.launch { drawer.open() }
        compose.waitForIdle()
        compose.onNodeWithTag("ok").assertIsFocused()
        compose.onNodeWithText("뒤 버튼").assertDoesNotExist()
        scope.launch { drawer.close() }
        compose.waitForIdle()
        compose.onNodeWithTag("opener").assertIsFocused()
        assertThat(drawer.isOpen).isFalse()
    }

    private companion object {
        const val TABS = 4
    }
}
