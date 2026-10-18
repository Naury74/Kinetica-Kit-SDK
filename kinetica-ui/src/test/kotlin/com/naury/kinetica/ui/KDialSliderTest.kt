package com.naury.kinetica.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 가로 눈금 다이얼: 끌기, 관성, 완료 콜백, 기준값 스냅과 두 번 탭, 접근성, RTL, 햅틱 끄기. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KDialSliderTest {
    @get:Rule
    val compose = createComposeRule()

    private class Recording : HapticFeedback {
        var count = 0

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            count++
        }
    }

    private var value = 0f
    private var finished = 0
    private val haptics = Recording()

    private fun setDial(
        initial: Float = 0f,
        snapThreshold: Float = 1.5f,
        direction: LayoutDirection = LayoutDirection.Ltr,
        hapticsOn: Boolean = true,
        reduced: Boolean? = null,
    ) {
        value = initial
        compose.setContent {
            var current by remember { mutableFloatStateOf(initial) }
            CompositionLocalProvider(
                LocalLayoutDirection provides direction,
                LocalHapticFeedback provides haptics,
                LocalKineticaHaptics provides hapticsOn,
                LocalKineticaReducedMotion provides reduced,
            ) {
                KDialSlider(
                    current,
                    {
                        current = it
                        value = it
                    },
                    label = LABEL,
                    onValueChangeFinished = { finished++ },
                    snapThreshold = snapThreshold,
                )
            }
        }
    }

    private fun dial() = compose.onNodeWithContentDescription(LABEL)

    /** 다이얼 눈금 줄 높이에서 [dx]dp만큼 천천히 끈다. 천천히 끌어 관성이 생기지 않는다. */
    private fun slowDrag(dx: Float) {
        dial().performTouchInput {
            val y = height * 0.75f
            val start = Offset(centerX, y)
            swipe(start, start + Offset(dx.dp.toPx(), 0f), durationMillis = 3_000)
        }
        compose.waitForIdle()
    }

    @Test
    fun `끌면 값이 바뀌고 손을 뗀 뒤 완료가 한 번 불린다`() {
        setDial()
        slowDrag(-80f)
        // 눈금을 왼쪽으로 밀면 오른쪽(큰 값)이 가운데로 온다. 터치 슬롭만큼은 끌기 전이라 빠진다.
        assertThat(value).isGreaterThan(10f)
        assertThat(value).isLessThan(21f)
        assertThat(finished).isEqualTo(1)
        assertThat(haptics.count).isGreaterThan(0)
    }

    @Test
    fun `세게 밀면 관성으로 더 가고 완료는 미끄러짐이 끝난 뒤 한 번만 불린다`() {
        setDial()
        compose.mainClock.autoAdvance = false
        dial().performTouchInput {
            val start = Offset(centerX, height * 0.75f)
            swipe(start, start + Offset(-60.dp.toPx(), 0f), durationMillis = 60)
        }
        compose.mainClock.advanceTimeByFrame()
        val released = value
        assertThat(finished).isEqualTo(0)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertThat(value).isGreaterThan(released)
        assertThat(finished).isEqualTo(1)
    }

    @Test
    fun `움직임 줄이기면 관성 없이 놓은 자리에서 멈춘다`() {
        setDial(reduced = true)
        compose.mainClock.autoAdvance = false
        dial().performTouchInput {
            val start = Offset(centerX, height * 0.75f)
            swipe(start, start + Offset(-60.dp.toPx(), 0f), durationMillis = 60)
        }
        compose.mainClock.advanceTimeByFrame()
        val released = value
        assertThat(finished).isEqualTo(1)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertThat(value).isEqualTo(released)
    }

    @Test
    fun `두 번 탭하면 기준값으로 돌아간다`() {
        setDial(initial = 40f)
        dial().performTouchInput { doubleClick(Offset(centerX, height * 0.75f)) }
        compose.waitForIdle()
        assertThat(value).isEqualTo(0f)
        assertThat(finished).isEqualTo(1)
    }

    @Test
    fun `기준값 근처에서 놓으면 기준값에 붙는다`() {
        setDial(initial = 20f, snapThreshold = 8f)
        // 슬롭을 빼고 약 15단위 줄어 5 근처에 놓인다. 스냅 거리 8 안이라 0에 붙는다.
        slowDrag(68f)
        assertThat(value).isEqualTo(0f)
        assertThat(finished).isEqualTo(1)
    }

    @Test
    fun `TalkBack은 범위를 읽고 setProgress로 값을 바꾼다`() {
        setDial()
        dial().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0"))
        dial().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        dial().performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        compose.waitForIdle()
        assertThat(value).isEqualTo(30f)
        assertThat(finished).isEqualTo(1)
        dial().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "30"))
        dial().performSemanticsAction(SemanticsActions.SetProgress) { it(500f) }
        compose.waitForIdle()
        assertThat(value).isEqualTo(100f)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `방향키로 한 눈금씩 움직이고 RTL에서는 반대다`() {
        setDial(initial = 3f)
        dial().requestFocus()
        dial().performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        assertThat(value).isEqualTo(4f)
        dial().performKeyInput { pressKey(Key.DirectionLeft) }
        dial().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()
        assertThat(value).isEqualTo(0f)
        assertThat(finished).isEqualTo(3)
    }

    @Test
    fun `RTL에서는 같은 쪽으로 끌어도 값이 반대로 움직인다`() {
        setDial(direction = LayoutDirection.Rtl)
        slowDrag(-80f)
        assertThat(value).isLessThan(-10f)
    }

    @Test
    fun `LocalKineticaHaptics가 false면 햅틱을 내지 않는다`() {
        setDial(hapticsOn = false)
        slowDrag(-80f)
        assertThat(value).isGreaterThan(10f)
        assertThat(haptics.count).isEqualTo(0)
    }

    @Test
    fun `잘못된 인자는 IllegalArgumentException으로 막는다`() {
        assertThrows(IllegalArgumentException::class.java) {
            compose.setContent { KDialSlider(0f, {}, LABEL, valueRange = 1f..1f) }
        }
    }

    private companion object {
        const val LABEL = "노출"
    }
}
