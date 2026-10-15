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
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
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
class GlassNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `슬라이더는 화면 읽기로 값을 바꾸고 눈금에 맞춘다`() {
        val value = mutableFloatStateOf(0f)
        var finished = 0
        compose.setContent {
            GlassSlider(value.floatValue, {
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
            GlassRefreshBox(refreshing.value, {
                calls++
                refreshing.value = true
            }, Modifier.fillMaxSize().testTag("refresh"), indicatorTop = 100.dp) {
                LazyColumn { item { BasicText("목록") } }
            }
        }

        compose.onNodeWithTag("refresh").performCustomAccessibilityActionWithLabel("새로 고침")
        compose.onNodeWithTag("refresh").performCustomAccessibilityActionWithLabel("새로 고침")

        assertThat(calls).isEqualTo(1)
        compose.onNodeWithText("목록").assertExists()
        // 물방울은 indicatorTop 아래에서 나온다(위 막대에 가리지 않게).
        val top = compose.onNodeWithContentDescription("새로 고치는 중").fetchSemanticsNode().boundsInRoot.top
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
        val state = GlassExpandState()
        compose.setContent {
            GlassScaffold(Modifier.fillMaxSize()) {
                Box(Modifier.size(100.dp).expandSource(state, 1))
                GlassExpandHost(state) { key -> BasicText("상세 $key") }
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
}
