package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
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
class GlassButtonsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `버튼은 누르면 호출되고 불러오는 중에는 누를 수 없으며 상태를 알린다`() {
        val clicks = mutableIntStateOf(0)
        val loading = mutableStateOf(false)
        compose.setContent {
            GlassScaffold {
                GlassButton("저장", { clicks.intValue++ }, Modifier.testTag("button"), loading = loading.value)
            }
        }

        compose.onNodeWithTag("button").assertIsEnabled().performClick()
        assertThat(clicks.intValue).isEqualTo(1)

        loading.value = true
        compose.waitForIdle()
        compose.onNodeWithTag("button")
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Loading"))
        compose.onNodeWithText("저장").assertExists()
        compose.onNodeWithTag("button").performClick()
        assertThat(clicks.intValue).isEqualTo(1)
    }

    @Test
    fun `모든 종류와 크기의 버튼을 그릴 수 있다`() {
        compose.setContent {
            GlassScaffold {
                androidx.compose.foundation.layout.Column {
                    GlassButtonKind.entries.forEach { kind ->
                        GlassButtonSize.entries.forEach { size -> GlassButton("$kind $size", {}, kind = kind, size = size) }
                    }
                    GlassButton("꺼짐", {}, enabled = false)
                }
            }
        }

        compose.onNodeWithText("Plain Large").assertExists()
        compose.onNodeWithText("꺼짐").assertIsNotEnabled()
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun `스테퍼는 범위 끝에서 멈추고 화면 읽기 동작으로 값을 바꾼다`() {
        val value = mutableIntStateOf(1)
        compose.setContent {
            GlassScaffold {
                GlassStepper(value.intValue, { value.intValue = it }, Modifier.testTag("stepper"), range = 0..2)
            }
        }
        val stepper = compose.onNodeWithTag("stepper")

        stepper.assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(1f, 0f..2f, 1)))
        stepper.performCustomAccessibilityActionWithLabel("Increase")
        assertThat(value.intValue).isEqualTo(2)

        // 끝에 닿으면 늘리기 동작이 사라지고, 더하기 버튼을 눌러도 바뀌지 않는다.
        stepper.assert(
            SemanticsMatcher("늘리기 동작 없음") { node ->
                node.config[SemanticsActions.CustomActions].none { it.label == "Increase" }
            },
        )
        stepper.performTouchInput { click(centerRight.copy(x = right - 10f)) }
        assertThat(value.intValue).isEqualTo(2)

        stepper.performTouchInput { click(centerLeft.copy(x = left + 10f)) }
        assertThat(value.intValue).isEqualTo(1)

        stepper.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        assertThat(value.intValue).isEqualTo(0)
    }

    @Test
    fun `스테퍼를 길게 누르면 값이 계속 바뀐다`() {
        val value = mutableIntStateOf(0)
        compose.setContent {
            GlassScaffold {
                GlassStepper(value.intValue, { value.intValue = it }, Modifier.testTag("stepper"), range = 0..50)
            }
        }
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag("stepper").performTouchInput { down(centerRight.copy(x = right - 10f)) }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithTag("stepper").performTouchInput { up() }
        compose.mainClock.autoAdvance = true

        assertThat(value.intValue).isGreaterThan(5)
    }

    @Test
    fun `스테퍼는 손을 뗄 때 한 칸 바꾸고 누른 채 움직이면 바꾸지 않는다`() {
        val value = mutableIntStateOf(5)
        compose.setContent {
            GlassScaffold {
                GlassStepper(value.intValue, { value.intValue = it }, Modifier.testTag("stepper"), range = 0..50)
            }
        }
        val stepper = compose.onNodeWithTag("stepper")

        stepper.performTouchInput { down(centerRight.copy(x = right - 10f)) }
        assertThat(value.intValue).isEqualTo(5)
        stepper.performTouchInput { up() }
        assertThat(value.intValue).isEqualTo(6)

        // 목록 스크롤처럼 누른 채 세로로 움직이면 취소된다.
        stepper.performTouchInput {
            down(centerRight.copy(x = right - 10f))
            moveBy(androidx.compose.ui.geometry.Offset(0f, 60f))
            up()
        }
        assertThat(value.intValue).isEqualTo(6)
    }

    @Test
    fun `칩은 단일 선택이면 옮겨 가고 다중 선택이면 켜고 끈다`() {
        val single = mutableStateOf(setOf(0))
        val multi = mutableStateOf(setOf<Int>())
        compose.setContent {
            GlassScaffold {
                androidx.compose.foundation.layout.Column {
                    GlassChipGroup(listOf("전체", "사진"), single.value, { single.value = it })
                    GlassChipGroup(listOf("빨강", "파랑"), multi.value, { multi.value = it }, selection = GlassChipSelection.Multiple)
                }
            }
        }

        compose.onNodeWithText("사진").performClick()
        compose.onNodeWithText("빨강").performClick()
        compose.onNodeWithText("파랑").performClick()

        assertThat(single.value).containsExactly(1)
        assertThat(multi.value).containsExactly(0, 1)
        compose.onNodeWithText("사진").assertIsSelected()
        compose.onNodeWithText("전체").assertIsNotSelected()
        compose.onNodeWithText("파랑").assertIsOn().performClick()
        compose.onNodeWithText("파랑").assertIsOff()
        assertThat(nextSelection(GlassChipSelection.Multiple, setOf(9), 0, 2)).containsExactly(0)
    }

    @Test
    fun `진행 막대와 링은 진행 비율을 알리고 모르는 상태도 알린다`() {
        val progress = mutableStateOf<Float?>(0.4f)
        compose.setContent {
            GlassScaffold {
                androidx.compose.foundation.layout.Column {
                    GlassProgressBar(progress.value, Modifier.testTag("bar"), wave = true)
                    GlassProgressRing(progress.value, Modifier.testTag("ring"), wave = true) {
                        androidx.compose.foundation.text.BasicText("40%")
                    }
                }
            }
        }

        val range = SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.4f, 0f..1f))
        compose.onNodeWithTag("bar").assert(range)
        compose.onNodeWithTag("ring").assert(range)
        compose.onNodeWithText("40%").assertExists()

        progress.value = null
        compose.waitForIdle()
        val unknown = SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)
        compose.onNodeWithTag("bar").assert(unknown)
        compose.onNodeWithTag("ring").assert(unknown)
    }

    @Test
    fun `진행 막대와 링과 별점은 NaN을 0으로 본다`() {
        compose.setContent {
            GlassScaffold {
                androidx.compose.foundation.layout.Column {
                    GlassProgressBar(Float.NaN, Modifier.testTag("bar"), wave = true)
                    GlassProgressRing(Float.NaN, Modifier.testTag("ring"), wave = true)
                    GlassRating(Float.NaN, onRatingChange = {}, Modifier.testTag("rating"))
                }
            }
        }

        val zero = SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0f, 0f..1f))
        compose.onNodeWithTag("bar").assert(zero)
        compose.onNodeWithTag("ring").assert(zero)
        compose.onNodeWithTag("rating").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0f, 0f..5f, 4)),
        )
    }

    @Test
    fun `배지는 99를 넘으면 줄여 쓰고 개수를 상태로 읽는다`() {
        val count = mutableStateOf<Int?>(3)
        compose.setContent {
            GlassScaffold {
                Box(Modifier.size(40.dp).testTag("icon").glassBadge(count.value))
            }
        }

        compose.onNodeWithTag("icon").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 new items"))
        count.value = 0
        compose.waitForIdle()
        compose.onNodeWithTag("icon").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        assertThat(badgeLabel(120, 99)).isEqualTo("99+")
        assertThat(badgeLabel(99, 99)).isEqualTo("99")
    }
}
