package com.naury.kinetica.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.motion.MorphResult
import com.naury.kinetica.motion.SceneMorph
import com.naury.kinetica.motion.TransitionEnd
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SceneTransitionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun scene(x: Double, color: Long) = SceneNode(
        NodeId("card"),
        NodeContent.Rectangle(Size(50.0, 100.0)),
        transform = Transform2D(translationX = x),
        style = NodeStyle(fill = ArgbColor(color.toInt())),
    ).let { node ->
        SceneDocument(
            documentId = "s",
            artboard = Artboard(100.0, 100.0, ArgbColor(0xFFFFFFFF.toInt())),
            nodes = mapOf(node.id to node),
            rootOrder = listOf(node.id),
        )
    }

    private val morph = (SceneMorph.create(scene(0.0, 0xFFFF0000), scene(50.0, 0xFF0000FF)) as MorphResult.Ready).morph

    @Test
    fun `진행률에 따라 원본과 대상 장면을 그린다`() {
        val progress = 0.0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                SceneTransition(morph, progress, Modifier.size(100.dp).testTag("t"))
            }
        }
        val start = compose.onNodeWithTag("t").captureToImage().toPixelMap()
        assertThat(start[10, 50]).isEqualTo(Color.Red)
        assertThat(start[90, 50]).isEqualTo(Color.White)
    }

    @Test
    fun `조금 끌면 되돌아가고 많이 끌면 끝을 한 번 알린다`() {
        val ends = mutableListOf<TransitionEnd>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val driver = rememberTransitionDriver(onEnd = ends::add)
                val state by driver.state.collectAsState()
                Box(Modifier.size(200.dp).testTag("area").dragToDismiss(driver, travelPx = 200f)) {
                    SceneTransition(morph, 1.0 - state.progress, Modifier.size(100.dp))
                }
            }
        }

        compose.onNodeWithTag("area").performTouchInput { swipe(Offset(100f, 20f), Offset(100f, 50f), durationMillis = 600) }
        compose.waitForIdle()
        assertThat(ends).containsExactly(TransitionEnd.Canceled)

        compose.onNodeWithTag("area").performTouchInput { swipe(Offset(100f, 10f), Offset(100f, 190f), durationMillis = 400) }
        compose.waitForIdle()
        compose.onNodeWithTag("area").performTouchInput { swipe(Offset(100f, 10f), Offset(100f, 190f), durationMillis = 400) }
        compose.waitForIdle()
        assertThat(ends).containsExactly(TransitionEnd.Canceled, TransitionEnd.Completed).inOrder()
    }
}
