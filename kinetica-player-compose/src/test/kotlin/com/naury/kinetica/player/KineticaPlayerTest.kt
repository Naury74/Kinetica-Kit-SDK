package com.naury.kinetica.player

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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.PropertyTrack
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.motion.FrameClock
import com.naury.kinetica.motion.PlaybackController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KineticaPlayerTest {
    @get:Rule
    val compose = createComposeRule()

    // 100x100 artboard, 왼쪽 위 50x50 빨간 사각형이 1초 동안 오른쪽으로 50 이동한다.
    private val box =
        SceneNode(NodeId("box"), NodeContent.Rectangle(Size(50.0, 50.0)), style = NodeStyle(fill = ArgbColor(0xFFFF0000.toInt())))
    private val timeline = Timeline(
        "main",
        1_000,
        listOf(
            PropertyTrack(
                box.id,
                AnimatableProperty.TranslationX,
                listOf(Keyframe(0, KeyValue.Scalar(0.0)), Keyframe(1_000, KeyValue.Scalar(50.0))),
            ),
        ),
    )
    private val document = SceneDocument(
        documentId = "player",
        artboard = Artboard(100.0, 100.0, ArgbColor(0xFFFFFFFF.toInt())),
        nodes = mapOf(box.id to box.copy(transform = Transform2D())),
        rootOrder = listOf(box.id),
        timelines = listOf(timeline),
    )

    private fun render(playback: PlaybackController?) = run {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                KineticaPlayer(document, Modifier.size(100.dp).testTag("player"), playback)
            }
        }
        compose.onNodeWithTag("player").captureToImage().toPixelMap()
    }

    @Test
    fun `재생기 없이 처음 장면을 그린다`() {
        val pixels = render(null)

        assertThat(pixels[10, 10]).isEqualTo(Color.Red)
        assertThat(pixels[80, 10]).isEqualTo(Color.White)
    }

    @Test
    fun `재생 위치의 장면을 그린다`() {
        val playback = PlaybackController(timeline, FrameClock { 0L })
        playback.seek(1_000.0)

        val pixels = render(playback)

        assertThat(pixels[10, 10]).isEqualTo(Color.White)
        assertThat(pixels[80, 10]).isEqualTo(Color.Red)
    }
}
