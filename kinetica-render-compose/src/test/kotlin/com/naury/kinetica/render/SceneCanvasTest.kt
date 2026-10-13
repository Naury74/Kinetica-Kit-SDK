package com.naury.kinetica.render

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.PathCommand
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SceneCanvasTest {
    @get:Rule
    val compose = createComposeRule()

    private val red = ArgbColor(0xFFFF0000.toInt())
    private val green = ArgbColor(0xFF00FF00.toInt())
    private val blue = ArgbColor(0xFF0000FF.toInt())

    private fun node(
        id: String,
        content: NodeContent,
        x: Double,
        y: Double,
        fill: ArgbColor?,
        rotation: Double = 0.0,
        stroke: ArgbColor? = null,
    ) = SceneNode(
        NodeId(id),
        content,
        transform = Transform2D(translationX = x, translationY = y, rotationDegrees = rotation),
        style = NodeStyle(fill = fill, stroke = stroke, strokeWidth = if (stroke == null) 0.0 else 4.0),
    )

    private val document = run {
        val nodes = listOf(
            node("rect", NodeContent.Rectangle(Size(40.0, 40.0)), 0.0, 0.0, red),
            node("ellipse", NodeContent.Ellipse(Size(40.0, 40.0)), 50.0, 0.0, green),
            node(
                "path",
                NodeContent.Path(
                    listOf(
                        PathCommand.MoveTo(Vec2(0.0, 0.0)),
                        PathCommand.LineTo(Vec2(40.0, 0.0)),
                        PathCommand.LineTo(Vec2(0.0, 40.0)),
                        PathCommand.Close,
                    ),
                ),
                0.0,
                50.0,
                blue,
            ),
            node("text", NodeContent.Text(Size(40.0, 40.0), "■■", fontSize = 30.0), 50.0, 50.0, red),
            node("image", NodeContent.Image(Size(40.0, 40.0), "content://test/missing"), 100.0, 0.0, null),
            node("turned", NodeContent.Rectangle(Size(20.0, 20.0)), 110.0, 60.0, green, rotation = 45.0, stroke = blue),
        )
        SceneDocument(
            documentId = "render",
            artboard = Artboard(160.0, 100.0, background = ArgbColor(0xFFFFFFFF.toInt())),
            nodes = nodes.associateBy { it.id },
            rootOrder = nodes.map { it.id },
        )
    }

    private fun render(viewport: Viewport): ImageBitmap {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                SceneCanvas(document, viewport, Modifier.size(320.dp, 200.dp).testTag("canvas"), artboardOutside = Color.Black)
            }
        }
        return compose.onNodeWithTag("canvas").captureToImage()
    }

    private fun ImageBitmap.colorAt(x: Int, y: Int): Color = toPixelMap()[x, y]

    @Test
    fun `여섯 노드 종류를 문서 좌표대로 그린다`() {
        val image = render(Viewport())

        assertThat(image.colorAt(20, 20)).isEqualTo(Color.Red)
        assertThat(image.colorAt(70, 20)).isEqualTo(Color.Green)
        assertThat(image.colorAt(52, 2)).isEqualTo(Color.White)
        assertThat(image.colorAt(10, 60)).isEqualTo(Color.Blue)
        assertThat(image.colorAt(35, 85)).isEqualTo(Color.White)
        assertThat(image.colorAt(130, 10)).isEqualTo(Color.White)
        assertThat(image.colorAt(110, 10)).isNotEqualTo(Color.White)
        assertThat(image.colorAt(200, 50)).isEqualTo(Color.Black)
    }

    @Test
    fun `글자 노드는 채움 색으로 그린다`() {
        val image = render(Viewport())
        val pixels = image.toPixelMap()
        val textPixels = (50 until 90).flatMap { x -> (50 until 90).map { y -> pixels[x, y] } }

        assertThat(textPixels).contains(Color.Red)
    }

    @Test
    fun `회전한 노드와 viewport 확대를 반영한다`() {
        val image = render(Viewport(zoom = 2.0))

        assertThat(image.colorAt(40, 40)).isEqualTo(Color.Red)
        assertThat(image.colorAt(140, 40)).isEqualTo(Color.Green)
        assertThat(image.colorAt(220, 150)).isEqualTo(Color.Green)
        assertThat(image.colorAt(240, 125)).isNotEqualTo(Color.Green)
    }
}
