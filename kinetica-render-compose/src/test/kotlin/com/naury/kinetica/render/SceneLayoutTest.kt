package com.naury.kinetica.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SceneLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val red = ArgbColor(0xFFFF0000.toInt())
    private val blue = ArgbColor(0xFF0000FF.toInt())

    private fun scene(
        vararg nodes: SceneNode,
        roots: List<String> = nodes.filter {
            it.parentId == null
        }.map { it.id.value },
    ) = SceneDocument(
        documentId = "layout",
        artboard = Artboard(200.0, 200.0, ArgbColor(0xFFFFFFFF.toInt())),
        nodes = nodes.associateBy { it.id },
        rootOrder = roots.map(::NodeId),
    )

    // 회전·배율·pivot이 있는 Frame 안에, 또 변환된 사각형이 들어 있다.
    private val nested = scene(
        SceneNode(
            NodeId("frame"),
            NodeContent.Frame(Size(120.0, 80.0), listOf(NodeId("inner")), clip = false),
            transform = Transform2D(
                translationX = 40.0,
                translationY = 30.0,
                rotationDegrees = 30.0,
                scaleX = 1.2,
                scaleY = 0.8,
                pivotX = 60.0,
                pivotY = 40.0,
            ),
            style = NodeStyle(fill = blue),
        ),
        SceneNode(
            NodeId("inner"),
            NodeContent.Rectangle(Size(30.0, 20.0)),
            parentId = NodeId("frame"),
            transform = Transform2D(translationX = 70.0, translationY = 10.0, rotationDegrees = -45.0, pivotX = 15.0, pivotY = 10.0),
            style = NodeStyle(fill = red),
        ),
    )

    private fun capture(content: @androidx.compose.runtime.Composable () -> Unit) = run {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(200.dp).testTag("root")) { content() }
            }
        }
        compose.onNodeWithTag("root").captureToImage().toPixelMap()
    }

    @Test
    fun `영역보다 큰 artboard도 왼쪽 위부터 viewport대로 그린다`() {
        val wide = scene().copy(artboard = Artboard(400.0, 400.0, red))

        val pixels = capture { SceneLayout(wide, Viewport(panX = 0.0, panY = 0.0, zoom = 0.5), Modifier.fillMaxSize()) }

        assertThat(pixels[2, 2]).isEqualTo(Color.Red)
        assertThat(pixels[197, 197]).isEqualTo(Color.Red)
    }

    @Test
    fun `레이어 렌더러는 Canvas 렌더러와 같은 자리에 그린다`() {
        val viewport = Viewport(panX = 5.0, panY = -3.0, zoom = 0.9)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Column {
                    Box(Modifier.size(200.dp).testTag("canvas")) { SceneCanvas(nested, viewport, Modifier.fillMaxSize()) }
                    Box(Modifier.size(200.dp).testTag("layout")) { SceneLayout(nested, viewport, Modifier.fillMaxSize()) }
                }
            }
        }
        val canvas = compose.onNodeWithTag("canvas").captureToImage().toPixelMap()
        val layout = compose.onNodeWithTag("layout").captureToImage().toPixelMap()

        // 가장자리 안티앨리어싱 차이는 빼고, 두 결과가 모두 확실히 한 색인 점만 비교한다.
        val pure = listOf(Color.Red, Color.Blue, Color.White)
        val samples = (2 until 198 step 3).flatMap { x -> (2 until 198 step 3).map { y -> canvas[x, y] to layout[x, y] } }
            .filter { (a, b) -> a in pure && b in pure }
        val compared = samples.size
        val mismatched = samples.count { (a, b) -> a != b }
        assertThat(compared).isGreaterThan(3_000)
        assertThat(mismatched.toDouble() / compared).isLessThan(0.002)
        assertThat(layout.buffer.count { it == Color.Red.toArgbInt() }).isGreaterThan(200)
    }

    @Test
    fun `호스트 뷰를 변환된 자리에 그리고 없는 키는 빈 틀로 그린다`() {
        val doc = scene(
            SceneNode(
                NodeId("host"),
                NodeContent.HostSlot(Size(50.0, 50.0), "widget.green"),
                transform = Transform2D(translationX = 100.0, translationY = 20.0),
            ),
            SceneNode(
                NodeId("missing"),
                NodeContent.HostSlot(Size(50.0, 50.0), "widget.none"),
                transform = Transform2D(translationX = 10.0, translationY = 120.0),
            ),
        )
        val pixels = capture {
            SceneLayout(
                doc,
                Viewport(),
                Modifier.fillMaxSize(),
                slots = mapOf(
                    "widget.green" to {
                        Box(Modifier.fillMaxSize().background(Color.Green))
                    },
                ),
            )
        }

        assertThat(pixels[125, 45]).isEqualTo(Color.Green)
        assertThat(pixels[95, 45]).isEqualTo(Color.White)
        assertThat(pixels[35, 145]).isNotEqualTo(Color.Green)
    }

    @Test
    fun `Frame은 자식을 상자 밖으로 그리지 않는다`() {
        val doc = scene(
            SceneNode(
                NodeId("frame"),
                NodeContent.Frame(Size(60.0, 60.0), listOf(NodeId("big"))),
                transform = Transform2D(translationX = 20.0, translationY = 20.0),
            ),
            SceneNode(NodeId("big"), NodeContent.Rectangle(Size(150.0, 150.0)), parentId = NodeId("frame"), style = NodeStyle(fill = red)),
        )
        val pixels = capture { SceneLayout(doc, Viewport(), Modifier.fillMaxSize()) }

        assertThat(pixels[50, 50]).isEqualTo(Color.Red)
        assertThat(pixels[120, 120]).isEqualTo(Color.White)
    }

    @Test
    fun `동작은 사용 모드에서만 호스트에 알린다`() {
        val actions = mutableListOf<String>()
        val doc = scene(
            SceneNode(
                NodeId("button"),
                NodeContent.Rectangle(Size(80.0, 40.0)),
                transform = Transform2D(translationX = 60.0, translationY = 80.0),
                style = NodeStyle(fill = blue),
                action = "open_order",
            ),
        )
        val interactive = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                SceneLayout(doc, Viewport(), Modifier.size(200.dp).testTag("root"), interactive = interactive.value, onAction = {
                    actions +=
                        it
                })
            }
        }
        compose.onNodeWithTag("root").performTouchInput { click(Offset(100f, 100f)) }
        compose.waitForIdle()
        assertThat(actions).isEmpty()

        interactive.value = true
        compose.waitForIdle()
        compose.onNodeWithTag("root").performTouchInput { click(Offset(100f, 100f)) }
        compose.onNodeWithTag("root").performTouchInput { click(Offset(20f, 20f)) }
        compose.waitForIdle()
        assertThat(actions).containsExactly("open_order")
    }

    private fun Color.toArgbInt(): Int =
        (alpha * 255).toInt() shl 24 or ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or (blue * 255).toInt()
}
