package com.naury.kinetica.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.SceneTransitionEdge
import com.naury.kinetica.core.scene.SceneTransitionKind
import com.naury.kinetica.core.scene.SceneTransitionSpec
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnimatedSceneTest {
    @get:Rule
    val compose = createComposeRule()

    private fun doc(id: String, transition: SceneTransitionSpec? = null) =
        SceneDocument(documentId = id, artboard = Artboard(100.0, 100.0), transition = transition)

    private val red = doc("red")
    private val blue = doc("blue", SceneTransitionSpec(SceneTransitionKind.Wipe, durationMs = 400, easing = EasingSpec.Linear))

    private var shown by mutableStateOf(red)

    private fun setContent(
        forward: Boolean = true,
        reduced: Boolean = false,
        direction: LayoutDirection = LayoutDirection.Ltr,
        ends: MutableList<String> = mutableListOf(),
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalLayoutDirection provides direction) {
                AnimatedScene(
                    shown,
                    Modifier.size(100.dp).testTag("scene"),
                    forward = forward,
                    reducedMotion = reduced,
                    onTransitionEnd = { ends += shown.documentId },
                ) { scene ->
                    Box(Modifier.fillMaxSize().background(if (scene.documentId == "red") Color.Red else Color.Blue)) {
                        BasicText(scene.documentId)
                    }
                }
            }
        }
    }

    private fun pixels() = compose.onNodeWithTag("scene").captureToImage().toPixelMap()

    private fun switchAndAdvance(next: SceneDocument, ms: Long) {
        compose.mainClock.autoAdvance = false
        shown = next
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(ms)
    }

    @Test
    fun `문서의 전환으로 바뀌고 중간에는 두 장면이 함께 보이며 나가는 장면은 TalkBack에서 숨긴다`() {
        val ends = mutableListOf<String>()
        setContent(ends = ends)
        switchAndAdvance(blue, 200)

        val mid = pixels()
        assertThat(mid[20, 50]).isEqualTo(Color.Red)
        assertThat(mid[80, 50]).isEqualTo(Color.Blue)
        compose.onNodeWithText("red").assertDoesNotExist()
        compose.onNodeWithText("blue").assertExists()

        compose.mainClock.advanceTimeBy(400)
        assertThat(pixels()[20, 50]).isEqualTo(Color.Blue)
        assertThat(ends).containsExactly("blue")
    }

    @Test
    fun `되돌아가면 반대쪽에서, RTL이면 왼쪽에서 드러난다`() {
        setContent(forward = false)
        switchAndAdvance(blue, 100)
        val back = pixels()
        assertThat(back[10, 50]).isEqualTo(Color.Blue)
        assertThat(back[90, 50]).isEqualTo(Color.Red)
    }

    @Test
    fun `RTL이면 끝 쪽이 왼쪽이다`() {
        setContent(direction = LayoutDirection.Rtl)
        switchAndAdvance(blue, 100)
        val rtl = pixels()
        assertThat(rtl[10, 50]).isEqualTo(Color.Blue)
        assertThat(rtl[90, 50]).isEqualTo(Color.Red)
    }

    @Test
    fun `애니메이션 삭제면 바로 바뀌고 끝 알림도 없다`() {
        val ends = mutableListOf<String>()
        setContent(reduced = true, ends = ends)
        switchAndAdvance(blue, 16)

        assertThat(pixels()[20, 50]).isEqualTo(Color.Blue)
        compose.onNodeWithText("red").assertDoesNotExist()
        assertThat(ends).isEmpty()
    }

    @Test
    fun `같은 id의 문서로 바뀌면 전환 없이 바로 바꾼다`() {
        setContent()
        switchAndAdvance(red.copy(transition = blue.transition, artboard = Artboard(50.0, 50.0)), 16)

        assertThat(pixels()[90, 50]).isEqualTo(Color.Red)
    }

    @Test
    fun `transition 파라미터가 문서의 전환보다 우선한다`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                AnimatedScene(
                    shown,
                    Modifier.size(100.dp).testTag("scene"),
                    transition = SceneTransitionSpec(SceneTransitionKind.Slide, SceneTransitionEdge.Top, 400, EasingSpec.Linear),
                ) { scene -> Box(Modifier.fillMaxSize().background(if (scene.documentId == "red") Color.Red else Color.Blue)) }
            }
        }
        switchAndAdvance(blue, 200)

        val mid = pixels()
        assertThat(mid[50, 20]).isEqualTo(Color.Blue)
        assertThat(mid[50, 80]).isEqualTo(Color.Red)
    }

    @Test
    fun `모프는 같은 id 노드를 이어 그린다`() {
        fun card(id: String, x: Double) = SceneNode(
            NodeId("card"),
            NodeContent.Rectangle(Size(50.0, 100.0)),
            transform = Transform2D(translationX = x),
            style = NodeStyle(fill = ArgbColor(0xFF00FF00.toInt())),
        ).let { node ->
            doc(
                id,
                SceneTransitionSpec(SceneTransitionKind.Morph, durationMs = 400, easing = EasingSpec.Linear),
            ).copy(nodes = mapOf(node.id to node), rootOrder = listOf(node.id))
        }
        shown = card("a", 0.0)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                AnimatedScene(shown, Modifier.size(100.dp).testTag("scene")) { scene -> KineticaPlayer(scene, Modifier.fillMaxSize()) }
            }
        }
        switchAndAdvance(card("b", 50.0), 200)

        val mid = pixels()
        assertThat(mid[10, 50]).isNotEqualTo(Color(0xFF00FF00))
        assertThat(mid[50, 50]).isEqualTo(Color(0xFF00FF00))
        assertThat(mid[90, 50]).isNotEqualTo(Color(0xFF00FF00))
    }

    @Test
    fun `호스트가 정한 진행도로 그린다`() {
        val from = doc("a").copy(artboard = Artboard(100.0, 100.0, ArgbColor(0xFFFF0000.toInt())))
        val to = doc("b").copy(artboard = Artboard(100.0, 100.0, ArgbColor(0xFF0000FF.toInt())))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val progress = remember { 0.5f }
                SceneTransition(from, to, SceneTransitionSpec(SceneTransitionKind.Push), {
                    progress
                }, Modifier.size(100.dp).testTag("scene"))
            }
        }
        val mid = pixels()
        assertThat(mid[90, 50]).isEqualTo(Color.Blue)
        assertThat(mid[20, 50].red).isGreaterThan(0.5f)
    }
}
