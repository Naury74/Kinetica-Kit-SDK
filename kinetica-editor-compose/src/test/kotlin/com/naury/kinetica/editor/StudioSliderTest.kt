package com.naury.kinetica.editor

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudioSliderTest {
    @get:Rule
    val compose = createComposeRule()

    private var value by mutableFloatStateOf(2f)
    private var finished by mutableIntStateOf(0)

    private fun show(direction: LayoutDirection = LayoutDirection.Ltr) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                StudioSlider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0f..10f,
                    steps = 9,
                    onValueChangeFinished = { finished++ },
                    modifier = Modifier.width(300.dp).testTag("slider"),
                )
            }
        }
    }

    @Test
    fun `방향키는 한 칸씩 옮기고 바꿀 때마다 끝났다고 알린다`() {
        show()
        val slider = compose.onNodeWithTag("slider")
        slider.requestFocus()
        slider.performKeyInput { pressKey(Key.DirectionRight) }
        slider.performKeyInput { pressKey(Key.DirectionRight) }
        slider.performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()

        assertThat(value).isWithin(0.001f).of(3f)
        assertThat(finished).isEqualTo(3)
    }

    @Test
    fun `오른쪽에서 왼쪽으로 쓰면 왼쪽 방향키가 값을 키운다`() {
        show(LayoutDirection.Rtl)
        val slider = compose.onNodeWithTag("slider")
        slider.requestFocus()
        slider.performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()

        assertThat(value).isWithin(0.001f).of(3f)
    }

    @Test
    fun `TalkBack 값 조절은 눈금에 맞추고 범위 정보를 알린다`() {
        show()
        val slider = compose.onNodeWithTag("slider")
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(6.4f) }
        compose.waitForIdle()

        assertThat(value).isWithin(0.001f).of(6f)
        assertThat(finished).isEqualTo(1)
        val info = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertThat(info.range).isEqualTo(0f..10f)
        assertThat(info.steps).isEqualTo(9)
    }

    @Test
    fun `끌면 끄는 동안 값이 바뀌고 손을 뗄 때 한 번만 끝났다고 알린다`() {
        show()
        compose.onNodeWithTag("slider").performTouchInput { swipeRight(centerX, right, 300) }
        compose.waitForIdle()

        assertThat(value).isWithin(0.001f).of(10f)
        assertThat(finished).isEqualTo(1)
    }
}
