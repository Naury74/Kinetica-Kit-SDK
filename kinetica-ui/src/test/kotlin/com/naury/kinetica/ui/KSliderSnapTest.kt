package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** KSlider의 기준값 스냅, 두 번 탭 초기화, TalkBack 초기화 동작과 기본 인자일 때 예전 동작. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KSliderSnapTest {
    @get:Rule
    val compose = createComposeRule()

    private class Recording : HapticFeedback {
        var count = 0

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            count++
        }
    }

    private val slider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    // 폭 300dp에서 손잡이 반폭 18dp를 뺀 264dp가 값 범위다.
    private fun xFor(fraction: Float) = (18f + 264f * fraction).dp

    @Test
    fun `스냅 범위 안으로 끌면 기준값에 붙고 햅틱은 한 번만 낸다`() {
        val haptics = Recording()
        var value by mutableFloatStateOf(-1f)
        var finished = 0
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                Box(Modifier.width(300.dp).testTag("box")) {
                    KSlider(
                        value,
                        { value = it },
                        valueRange = -1f..1f,
                        onValueChangeFinished = { finished++ },
                        snap = KSliderSnap(0f, 0.1f),
                    )
                }
            }
        }
        compose.onNode(slider).performTouchInput {
            down(Offset(xFor(0.05f).toPx(), centerY))
            moveTo(Offset(xFor(0.3f).toPx(), centerY))
            moveTo(Offset(xFor(0.52f).toPx(), centerY))
            moveTo(Offset(xFor(0.48f).toPx(), centerY))
        }
        compose.waitForIdle()
        assertThat(value).isEqualTo(0f)
        assertThat(haptics.count).isEqualTo(1)

        compose.onNode(slider).performTouchInput {
            moveTo(Offset(xFor(0.8f).toPx(), centerY))
            up()
        }
        compose.waitForIdle()
        assertThat(value).isWithin(0.01f).of(0.6f)
        assertThat(haptics.count).isEqualTo(1)
        assertThat(finished).isEqualTo(1)
    }

    @Test
    fun `두 번 탭하면 초기화 값으로 돌아간다`() {
        var value by mutableFloatStateOf(0.7f)
        var finished = 0
        compose.setContent {
            Box(Modifier.width(300.dp)) {
                KSlider(value, { value = it }, onValueChangeFinished = { finished++ }, resetValue = 0.2f)
            }
        }
        compose.onNode(slider).performTouchInput { doubleClick(Offset(xFor(0.9f).toPx(), centerY)) }
        compose.waitForIdle()
        assertThat(value).isEqualTo(0.2f)
        assertThat(finished).isEqualTo(2)
    }

    @Test
    fun `초기화 값이 없으면 스냅 기준값으로 돌아가고 RTL에서도 같다`() {
        var value by mutableFloatStateOf(0.9f)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.width(300.dp)) {
                    KSlider(value, { value = it }, snap = KSliderSnap(0.5f, 0.02f))
                }
            }
        }
        // RTL에서는 오른쪽이 시작이므로 왼쪽 끝 근처를 한 번 탭하면 끝 쪽 값이 된다.
        compose.onNode(slider).performTouchInput { click(Offset(xFor(0.1f).toPx(), centerY)) }
        compose.waitForIdle()
        assertThat(value).isWithin(0.01f).of(0.9f)
        // 앞 탭과 이어진 두 번 탭으로 보지 않도록 시간을 띄운다.
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNode(slider).performTouchInput { doubleClick(Offset(xFor(0.3f).toPx(), centerY)) }
        compose.waitForIdle()
        assertThat(value).isEqualTo(0.5f)
    }

    @Test
    fun `기본 인자면 탭은 그 값으로 옮기고 두 번 탭해도 초기화하지 않으며 초기화 동작도 없다`() {
        val haptics = Recording()
        var value by mutableFloatStateOf(0f)
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                Box(Modifier.width(300.dp)) { KSlider(value, { value = it }, steps = 3) }
            }
        }
        compose.onNode(slider).performTouchInput { click(Offset(xFor(0.7f).toPx(), centerY)) }
        compose.waitForIdle()
        assertThat(value).isEqualTo(0.75f)
        compose.onNode(slider).performTouchInput { doubleClick(Offset(xFor(0.2f).toPx(), centerY)) }
        compose.waitForIdle()
        assertThat(value).isEqualTo(0.25f)
        // 끝을 넘어 끌어도 값은 끝에 머문다.
        compose.onNode(slider).performTouchInput {
            down(Offset(xFor(0.5f).toPx(), centerY))
            moveTo(Offset(xFor(0.8f).toPx(), centerY))
            moveTo(Offset(width + 80.dp.toPx(), centerY))
            up()
        }
        compose.waitForIdle()
        assertThat(value).isEqualTo(1f)
        assertThat(haptics.count).isEqualTo(0)
        val config = compose.onNode(slider).fetchSemanticsNode().config
        assertThat(config.contains(SemanticsActions.CustomActions)).isFalse()
        compose.runOnIdle { config[SemanticsActions.SetProgress].action!!.invoke(0.3f) }
        compose.waitForIdle()
        assertThat(value).isWithin(0.001f).of(0.25f)
    }

    @Test
    fun `TalkBack 초기화 동작으로 초기화 값에 돌아간다`() {
        var value by mutableFloatStateOf(0.8f)
        var finished = 0
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides true) {
                KSlider(value, { value = it }, onValueChangeFinished = { finished++ }, resetValue = 0.4f)
            }
        }
        val actions = compose.onNode(slider).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertThat(actions.map { it.label }).containsExactly("Reset")
        compose.runOnIdle { actions.single().action() }
        compose.waitForIdle()
        assertThat(value).isEqualTo(0.4f)
        assertThat(finished).isEqualTo(1)
        // 값을 바꾸는 기존 동작은 그대로 있다.
        assertThat(compose.onNode(slider).fetchSemanticsNode().config.contains(SemanticsActions.SetProgress)).isTrue()
    }

    @Test
    fun `꺼진 슬라이더는 초기화 동작을 내놓지 않는다`() {
        compose.setContent { KSlider(0.8f, {}, enabled = false, resetValue = 0.4f) }
        assertThat(compose.onNode(slider).fetchSemanticsNode().config.contains(SemanticsActions.CustomActions)).isFalse()
    }

    @Test
    fun `값 배지는 손잡이 위에 그리고 TalkBack은 배지를 읽지 않는다`() {
        compose.setContent {
            Box(Modifier.width(300.dp).testTag("box")) {
                KSlider(0.5f, {}, valueBadge = { Text("배지 $it") })
            }
        }
        compose.onAllNodesWithText("배지 0.5").assertCountEquals(0)
        val slider = compose.onNode(slider).fetchSemanticsNode().boundsInRoot
        val box = compose.onNodeWithTag("box").fetchSemanticsNode().boundsInRoot
        assertThat(slider.top).isGreaterThan(box.top)
        assertThat(slider.bottom).isEqualTo(box.bottom)
    }
}
