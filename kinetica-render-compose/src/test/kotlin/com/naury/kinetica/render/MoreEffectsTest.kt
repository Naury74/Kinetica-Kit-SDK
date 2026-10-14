package com.naury.kinetica.render

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.scene.LoopEffect
import com.naury.kinetica.core.scene.LoopKind
import com.naury.kinetica.core.scene.NodeEffects
import com.naury.kinetica.core.scene.PressEffect
import com.naury.kinetica.core.scene.PressKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "mdpi")
class MoreEffectsTest {
    @get:Rule
    val compose = createComposeRule()

    /** 200dp 흰 바탕 가운데 60dp 상자. 상자 밖(위쪽 띠)에 흰색이 아닌 픽셀이 있는지 본다. */
    private fun showBox(effects: NodeEffects, onClick: () -> Unit = {}) {
        compose.setContent {
            Box(Modifier.size(200.dp).background(Color.White).testTag("area"), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(
                        60.dp,
                    ).testTag(
                        "box",
                    ).kineticaEffects(effects, CircleShape).clickable(onClick = onClick).background(Color.White, CircleShape),
                )
            }
        }
    }

    private fun PixelMap.paintedAboveBox(): Boolean = (10 until 68).any { y -> (90 until 110).any { x -> this[x, y] != Color.White } }

    @Test
    fun `터지기는 손을 뗄 때 상자 밖으로 조각을 그리고 클릭은 그대로 전달된다`() {
        var clicks = 0
        showBox(NodeEffects(press = listOf(PressEffect(PressKind.Burst))), onClick = { clicks++ })
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val before = compose.onNodeWithTag("area").captureToImage().toPixelMap().paintedAboveBox()

        compose.onNodeWithTag("box").performTouchInput {
            down(center)
            up()
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(150)
        val during = compose.onNodeWithTag("area").captureToImage().toPixelMap().paintedAboveBox()
        compose.mainClock.advanceTimeBy(1_000)
        val after = compose.onNodeWithTag("area").captureToImage().toPixelMap().paintedAboveBox()

        assertThat(before).isFalse()
        assertThat(during).isTrue()
        assertThat(after).isFalse()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `퍼지는 고리는 상자 밖에 그린다`() {
        compose.mainClock.autoAdvance = false
        showBox(NodeEffects(loop = LoopEffect(LoopKind.Ping, periodMs = 1_000, amount = 30.0)))
        compose.mainClock.advanceTimeBy(300)

        assertThat(compose.onNodeWithTag("area").captureToImage().toPixelMap().paintedAboveBox()).isTrue()
    }

    @Test
    fun `숫자는 바뀐 값까지 차례로 올라간다`() {
        val target = androidx.compose.runtime.mutableDoubleStateOf(100.0)
        compose.setContent {
            val value by animateNumberAsState(target.doubleValue, durationMs = 1_000)
            BasicText(value.toLong().toString(), Modifier.testTag("count"))
        }
        fun shown() = compose.onNodeWithTag("count").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text]
            .first().text.toLong()
        val first = shown()
        compose.mainClock.autoAdvance = false
        target.doubleValue = 1_000_100.0
        // 시계를 멈춘 채로 다시 구성·효과 시작만 처리한다.
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        val middle = shown()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertThat(first).isEqualTo(100L)
        assertThat(middle).isGreaterThan(100L)
        assertThat(middle).isLessThan(1_000_100L)
        assertThat(shown()).isEqualTo(1_000_100L)
    }

    @Test
    fun `내용 전환이 끝나면 새 내용만 보인다`() {
        var page by mutableIntStateOf(0)
        compose.setContent {
            AnimatedContent(page, transitionSpec = { ContentTransitions.sharedAxisX(forward = targetState > initialState) }, label = "t") {
                BasicText("page $it")
            }
        }
        page = 1
        compose.waitForIdle()

        compose.onNodeWithText("page 1").assertExists()
        compose.onNodeWithText("page 0").assertDoesNotExist()
    }

    @Test
    fun `끌다가 뗀 것(스크롤)은 터지지 않는다`() {
        showBox(NodeEffects(press = listOf(PressEffect(PressKind.Burst))))
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag("box").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 25f))
            up()
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(150)

        assertThat(compose.onNodeWithTag("area").captureToImage().toPixelMap().paintedAboveBox()).isFalse()
    }

    @Test
    fun `숫자가 아닌 값은 무시하고 지금 값을 둔다`() {
        val target = androidx.compose.runtime.mutableDoubleStateOf(5.0)
        var shown = 0.0
        compose.setContent {
            val value by animateNumberAsState(target.doubleValue)
            shown = value
        }
        compose.waitForIdle()
        target.doubleValue = Double.NaN
        compose.waitForIdle()
        target.doubleValue = 7.0
        compose.waitForIdle()

        assertThat(shown).isEqualTo(7.0)
    }
}
