package com.naury.kinetica.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class KNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `슬라이더는 화면 읽기로 값을 바꾸고 눈금에 맞춘다`() {
        val value = mutableFloatStateOf(0f)
        var finished = 0
        compose.setContent {
            KSlider(value.floatValue, {
                value.floatValue = it
            }, Modifier.testTag("slider"), valueRange = 0f..4f, steps = 3, onValueChangeFinished = { finished++ })
        }

        compose.onNodeWithTag("slider").performSemanticsAction(SemanticsActions.SetProgress) { it(2.4f) }

        assertThat(value.floatValue).isEqualTo(2f)
        assertThat(finished).isEqualTo(1)
        val info = compose.onNodeWithTag("slider").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertThat(info.steps).isEqualTo(3)
    }

    @Test
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    fun `당겨서 새로 고침은 화면 읽기 동작으로도 부를 수 있고 새로 고치는 중에는 다시 부르지 않는다`() {
        val refreshing = mutableStateOf(false)
        var calls = 0
        compose.setContent {
            KRefreshBox(refreshing.value, {
                calls++
                refreshing.value = true
            }, Modifier.fillMaxSize().testTag("refresh"), indicatorTop = 100.dp) {
                LazyColumn { item { BasicText("목록") } }
            }
        }

        compose.onNodeWithTag("refresh").performCustomAccessibilityActionWithLabel("Refresh")
        compose.onNodeWithTag("refresh").performCustomAccessibilityActionWithLabel("Refresh")

        assertThat(calls).isEqualTo(1)
        compose.onNodeWithText("목록").assertExists()
        // 물방울은 indicatorTop 아래에서 나온다(위 막대에 가리지 않게).
        val top = compose.onNodeWithContentDescription("Refreshing").fetchSemanticsNode().boundsInRoot.top
        assertThat(top).isAtLeast(with(compose.density) { 100.dp.toPx() })
    }

    @Test
    fun `화면을 쌓으면 새 화면이 보이고 빼면 아래 화면으로 돌아가며 상태가 남는다`() {
        val stack = mutableStateOf(listOf("목록"))
        compose.setContent {
            PushNavigation(stack.value, { stack.value = stack.value.dropLast(1) }, Modifier.fillMaxSize()) { screen ->
                BasicText("$screen 화면")
            }
        }

        stack.value = listOf("목록", "상세")
        compose.waitForIdle()
        compose.onNodeWithText("상세 화면").assertExists()
        compose.onNodeWithText("목록 화면").assertDoesNotExist()

        stack.value = listOf("목록")
        compose.waitForIdle()
        compose.onNodeWithText("목록 화면").assertExists()
        compose.onNodeWithText("상세 화면").assertDoesNotExist()
    }

    @Test
    fun `아래로 내려간 화면의 저장 상태는 다시 돌아오면 남아 있다`() {
        val stack = mutableStateOf(listOf("목록"))
        compose.setContent {
            PushNavigation(stack.value, { stack.value = stack.value.dropLast(1) }, Modifier.fillMaxSize()) { screen ->
                var count by rememberSaveable { mutableIntStateOf(0) }
                BasicText("$screen $count", Modifier.clickable { count++ })
            }
        }
        compose.onNodeWithText("목록 0").performClick()
        compose.onNodeWithText("목록 1").assertExists()

        stack.value = listOf("목록", "상세")
        compose.waitForIdle()
        stack.value = listOf("목록")
        compose.waitForIdle()

        compose.onNodeWithText("목록 1").assertExists()
    }

    @Test
    fun `카드를 열면 상세가 보이고 닫으면 카드 자리로 돌아가 사라진다`() {
        val state = KExpandState()
        compose.setContent {
            KineticaScaffold(Modifier.fillMaxSize()) {
                Box(Modifier.size(100.dp).expandSource(state, 1))
                KExpandHost(state) { key -> BasicText("상세 $key") }
            }
        }

        state.expand(1)
        compose.waitForIdle()
        compose.onNodeWithText("상세 1").assertExists()
        assertThat(state.expandedKey).isEqualTo(1)

        state.collapse()
        compose.waitForIdle()
        compose.onNodeWithText("상세 1").assertDoesNotExist()
        assertThat(state.activeKey).isNull()
    }

    @Test
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    fun `슬라이더는 방향키로 조절하고 꺼져 있으면 바뀌지 않는다`() {
        val value = mutableFloatStateOf(0.5f)
        val enabled = mutableStateOf(true)
        compose.setContent {
            KSlider(value.floatValue, { value.floatValue = it }, Modifier.testTag("slider"), enabled = enabled.value)
        }
        compose.onNodeWithTag("slider").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionRight) }
        compose.onNodeWithTag("slider").requestFocus()
        compose.onNodeWithTag("slider").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionRight) }
        assertThat(value.floatValue).isWithin(1e-4f).of(0.55f)

        enabled.value = false
        compose.waitForIdle()
        compose.onNodeWithTag("slider").performSemanticsAction(SemanticsActions.SetProgress) { it(0.9f) }
        assertThat(value.floatValue).isWithin(1e-4f).of(0.55f)
    }

    @Test
    fun `같은 화면을 두 번 쌓으면 바로 알린다`() {
        val failure = runCatching { compose.setContent { PushNavigation(listOf("a", "a"), {}) { BasicText(it) } } }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `뺀 화면의 저장 상태는 지워 다시 열면 처음부터 시작한다`() {
        val stack = mutableStateOf(listOf("목록", "상세"))
        compose.setContent {
            PushNavigation(stack.value, { stack.value = stack.value.dropLast(1) }, Modifier.fillMaxSize()) { screen ->
                var count by rememberSaveable { mutableIntStateOf(0) }
                BasicText("$screen $count", Modifier.clickable { count++ })
            }
        }
        compose.onNodeWithText("상세 0").performClick()
        stack.value = listOf("목록")
        compose.waitForIdle()
        stack.value = listOf("목록", "상세")
        compose.waitForIdle()

        compose.onNodeWithText("상세 0").assertExists()
    }

    @Test
    fun `호스트가 뒤로 가기를 거절하면 맨 위 화면이 그대로 남는다`() {
        val stack = mutableStateOf(listOf("목록", "상세"))
        var asked = 0
        lateinit var dispatcher: androidx.activity.OnBackPressedDispatcher
        compose.setContent {
            dispatcher = checkNotNull(androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            PushNavigation(stack.value, { asked++ }, Modifier.fillMaxSize()) { screen -> BasicText("$screen 화면") }
        }

        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.waitForIdle()

        assertThat(asked).isEqualTo(1)
        compose.onNodeWithText("상세 화면").assertIsDisplayed()
        compose.onNodeWithText("목록 화면").assertDoesNotExist()
    }

    @Test
    fun `열린 카드는 화면을 다시 만들어도 열린 채로 돌아온다`() {
        val restore = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        lateinit var state: KExpandState
        restore.setContent {
            state = rememberKExpandState()
            KineticaScaffold(Modifier.fillMaxSize()) {
                KExpandHost(state) { key -> BasicText("상세 $key") }
            }
        }
        compose.runOnUiThread { state.expand(7) }
        compose.waitForIdle()

        restore.emulateSavedInstanceStateRestore()

        assertThat(state.expandedKey).isEqualTo(7)
        compose.onNodeWithText("상세 7").assertExists()
    }
}
