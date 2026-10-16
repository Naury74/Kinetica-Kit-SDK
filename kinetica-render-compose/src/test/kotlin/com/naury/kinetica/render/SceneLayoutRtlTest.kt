package com.naury.kinetica.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.CornerRadii
import com.naury.kinetica.core.scene.Gradient
import com.naury.kinetica.core.scene.GradientStop
import com.naury.kinetica.core.scene.GradientType
import com.naury.kinetica.core.scene.LineCap
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.PathCommand
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.Shadow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** 호스트가 RTL이어도 장면은 문서 좌표 그대로(LTR과 같은 픽셀로) 그려야 한다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SceneLayoutRtlTest {
    @get:Rule
    val compose = createComposeRule()

    private val red = ArgbColor(0xFFE53935.toInt())
    private val blue = ArgbColor(0xFF1E88E5.toInt())
    private val green = ArgbColor(0xFF43A047.toInt())
    private val shadowColor = ArgbColor(0x99000000.toInt())

    private fun node(id: String, content: NodeContent, x: Double, y: Double, style: NodeStyle) =
        SceneNode(NodeId(id), content, transform = Transform2D(translationX = x, translationY = y), style = style)

    private fun document(vararg nodes: SceneNode) = SceneDocument(
        documentId = "rtl",
        // 편집기의 빈 문서처럼 배경이 없는 artboard. 장면 크기가 0이 되어 RTL 배치 결함이 드러난다.
        artboard = Artboard(240.0, 240.0),
        nodes = nodes.associateBy { it.id },
        rootOrder = nodes.map { it.id },
    )

    // 좌우가 비대칭이라 뒤집히면 픽셀이 달라진다.
    private val shapes = document(
        node("ellipse", NodeContent.Ellipse(Size(60.0, 36.0)), 8.0, 8.0, NodeStyle(fill = blue)),
        node("rect", NodeContent.Rectangle(Size(50.0, 30.0)), 80.0, 8.0, NodeStyle(fill = red)),
        node(
            "round",
            NodeContent.Rectangle(
                Size(60.0, 40.0),
                corners = CornerRadii(topLeft = 20.0, topRight = 0.0, bottomRight = 6.0, bottomLeft = 0.0),
            ),
            150.0,
            8.0,
            NodeStyle(fill = green, stroke = blue, strokeWidth = 3.0, strokeDash = listOf(6.0, 3.0)),
        ),
        node("star", NodeContent.Polygon(Size(50.0, 50.0), sides = 5, innerRatio = 0.45), 8.0, 70.0, NodeStyle(fill = red)),
        node("poly", NodeContent.Polygon(Size(50.0, 40.0), sides = 3), 80.0, 70.0, NodeStyle(fill = blue)),
        node(
            "line",
            NodeContent.Path(listOf(PathCommand.MoveTo(Vec2(0.0, 0.0)), PathCommand.LineTo(Vec2(60.0, 30.0)))),
            150.0,
            70.0,
            NodeStyle(stroke = green, strokeWidth = 4.0, endCap = LineCap.Arrow),
        ),
        node(
            "linear",
            NodeContent.Ellipse(Size(60.0, 40.0)),
            8.0,
            140.0,
            NodeStyle(
                gradient = Gradient(GradientType.Linear, listOf(GradientStop(0.0, red), GradientStop(1.0, blue)), angleDegrees = 20.0),
            ),
        ),
        node(
            "radial",
            NodeContent.Rectangle(Size(50.0, 40.0), cornerRadius = 8.0),
            80.0,
            140.0,
            NodeStyle(gradient = Gradient(GradientType.Radial, listOf(GradientStop(0.0, green), GradientStop(1.0, blue)))),
        ),
        node(
            "shadow",
            NodeContent.Ellipse(Size(50.0, 36.0)),
            150.0,
            140.0,
            NodeStyle(
                fill = blue,
                shadows = listOf(
                    Shadow(shadowColor, offsetX = 6.0, offsetY = 4.0, blur = 6.0),
                    Shadow(shadowColor, offsetX = 4.0, inner = true),
                ),
            ),
        ),
        node("blur", NodeContent.Ellipse(Size(40.0, 24.0)), 20.0, 200.0, NodeStyle(fill = red, blur = 4.0)),
        node("rotated", NodeContent.Ellipse(Size(50.0, 20.0)), 100.0, 200.0, NodeStyle(fill = green))
            .let { it.copy(transform = it.transform.copy(rotationDegrees = 25.0, pivotX = 25.0, pivotY = 10.0)) },
    )

    /** 같은 컴포지션에서 방향만 바꿔 LTR, RTL 순서로 캡처한다. */
    private fun captureBoth(content: @androidx.compose.runtime.Composable () -> Unit): Pair<ImageBitmap, ImageBitmap> {
        var direction by mutableStateOf(LayoutDirection.Ltr)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalLayoutDirection provides direction) {
                Box(Modifier.size(240.dp).testTag("root")) { content() }
            }
        }
        val ltr = compose.onNodeWithTag("root").captureToImage()
        direction = LayoutDirection.Rtl
        compose.waitForIdle()
        return ltr to compose.onNodeWithTag("root").captureToImage()
    }

    @Test
    fun `RTL에서도 모든 도형을 LTR과 같은 픽셀로 그린다`() {
        val (ltr, rtl) = captureBoth { SceneLayout(shapes, Viewport(), Modifier.fillMaxSize()) }

        // 타원 채우기가 실제로 그려졌는지 먼저 본다(가운데가 파랑).
        assertThat(rtl.toPixelMap()[38, 26]).isEqualTo(Color(0xFF1E88E5))
        assertSamePixels(ltr, rtl)
    }

    @Test
    fun `RTL에서 영역보다 작은 artboard도 오른쪽으로 밀지 않는다`() {
        val small = shapes.copy(artboard = Artboard(120.0, 120.0, ArgbColor(0xFFFFFFFF.toInt())))

        val (ltr, rtl) = captureBoth { SceneLayout(small, Viewport(), Modifier.fillMaxSize()) }

        assertThat(rtl.toPixelMap()[38, 26]).isEqualTo(Color(0xFF1E88E5))
        assertSamePixels(ltr, rtl)
    }

    @Test
    fun `RTL에서 효과가 있는 장면과 artboard를 자른 장면도 LTR과 같다`() {
        val (ltr, rtl) = captureBoth {
            SceneLayout(
                shapes,
                Viewport(panX = 10.0, panY = 6.0, zoom = 0.8),
                Modifier.fillMaxSize(),
                interactive = true,
                clipToArtboard = true,
            )
        }

        assertThat(rtl.toPixelMap()[40, 26]).isEqualTo(Color(0xFF1E88E5))
        assertSamePixels(ltr, rtl)
    }
}

internal fun assertSamePixels(expected: ImageBitmap, actual: ImageBitmap) {
    val a = expected.toPixelMap()
    val b = actual.toPixelMap()
    assertThat(b.width).isEqualTo(a.width)
    assertThat(b.height).isEqualTo(a.height)
    val different = buildList {
        for (y in 0 until a.height) for (x in 0 until a.width) if (a[x, y] != b[x, y]) add(x to y)
    }
    assertThat(different.take(10)).isEmpty()
}
