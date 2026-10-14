package com.naury.kinetica.render

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.AppearEffect
import com.naury.kinetica.core.scene.AppearKind
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeEffects
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.PressEffect
import com.naury.kinetica.core.scene.PressKind
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NodeEffectsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun center() = compose.onNodeWithTag("box").captureToImage().toPixelMap().let { it[it.width / 2, it.height / 2] }

    @Test
    fun `나타나기는 처음에 보이지 않다가 시간이 지나면 보인다`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(100.dp).background(Color.White)) {
                Box(
                    Modifier.size(
                        100.dp,
                    ).testTag("box").kineticaEffects(NodeEffects(appear = AppearEffect(AppearKind.Fade, 400))).background(Color.Red),
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val start = center()
        compose.mainClock.advanceTimeBy(1_000)

        // 흰 바탕 위 빨간 상자가 아직 투명하면 초록 성분이 남아 있다.
        assertThat(start.green).isGreaterThan(0.5f)
        assertThat(center()).isEqualTo(Color.Red)
    }

    @Test
    fun `리플은 누른 동안 어둡게 덮고 아래 클릭은 그대로 전달된다`() {
        var clicks = 0
        compose.setContent {
            Box(
                Modifier
                    .size(100.dp)
                    .testTag("box")
                    .kineticaEffects(NodeEffects(press = listOf(PressEffect(PressKind.Ripple, amount = 0.5))))
                    .clickable { clicks++ }
                    .background(Color.White),
            )
        }

        compose.onNodeWithTag("box").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(600)
        val pressed = center()
        compose.onNodeWithTag("box").performTouchInput { up() }
        compose.waitForIdle()

        assertThat(pressed.red).isLessThan(0.9f)
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `편집 화면(누르기 처리 안 함)에서는 효과 없이 바로 보인다`() {
        compose.mainClock.autoAdvance = false
        val node = SceneNode(
            NodeId("card"),
            NodeContent.Rectangle(Size(100.0, 100.0)),
            style = NodeStyle(fill = ArgbColor(0xFFFF0000.toInt())),
            effects = NodeEffects(appear = AppearEffect(AppearKind.Fade, 400, delayMs = 500)),
        )
        val document = SceneDocument(
            documentId = "fx",
            artboard = Artboard(100.0, 100.0, background = ArgbColor(0xFFFFFFFF.toInt())),
            nodes = mapOf(node.id to node),
            rootOrder = listOf(node.id),
        )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                SceneLayout(document, Viewport(), Modifier.size(100.dp).testTag("box"), interactive = false)
            }
        }
        compose.mainClock.advanceTimeByFrame()

        assertThat(center()).isEqualTo(Color.Red)
    }
}
