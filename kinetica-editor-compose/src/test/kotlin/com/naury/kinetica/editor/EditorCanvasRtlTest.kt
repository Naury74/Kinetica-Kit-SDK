package com.naury.kinetica.editor

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp")
class EditorCanvasRtlTest {
    @get:Rule
    val compose = createComposeRule()

    // 편집기의 빈 문서처럼 artboard 배경이 없다. 이때 장면 크기가 0이어서 RTL에서 캔버스 밖으로 밀렸었다.
    private val document = SceneDocument.empty(Artboard(1080.0, 1920.0)).let { empty ->
        val nodes = listOf(
            NodeFactory.ellipse(Vec2(300.0, 400.0), 0),
            NodeFactory.rectangle(Vec2(780.0, 400.0), 2),
            NodeFactory.polygon(Vec2(300.0, 900.0), 3),
            NodeFactory.star(Vec2(780.0, 900.0), 4),
            NodeFactory.line(Vec2(540.0, 1400.0), 6, arrow = true),
        )
        empty.copy(nodes = nodes.associateBy { it.id }, rootOrder = nodes.map { it.id })
    }

    private val session = (KineticaSession.create(document) as SessionOpenResult.Opened).session

    private companion object {
        const val ELLIPSE_FILL = 0xFF7C8CFF.toInt()
    }

    private fun canvas(): ImageBitmap = compose.onNodeWithTag(CANVAS_TAG).captureToImage()

    @Test
    fun `RTL 편집기 캔버스도 도형을 LTR과 같은 픽셀로 그린다`() {
        var direction by mutableStateOf(LayoutDirection.Rtl)
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides direction) { KineticaEditor(session) } }
        compose.waitForIdle()
        val rtl = canvas()
        direction = LayoutDirection.Ltr
        compose.waitForIdle()
        val ltr = canvas()

        val a = ltr.toPixelMap()
        val b = rtl.toPixelMap()
        // 타원 채우기 색(팔레트 첫 색)이 RTL 캔버스에 실제로 그려졌다.
        val ellipseFill = buildList { for (y in 0 until b.height) for (x in 0 until b.width) if (b[x, y].toArgb() == ELLIPSE_FILL) add(x) }
        assertThat(ellipseFill.size).isGreaterThan(100)
        assertThat(b.width).isEqualTo(a.width)
        val different = buildList { for (y in 0 until a.height) for (x in 0 until a.width) if (a[x, y] != b[x, y]) add(x to y) }
        assertThat(different.take(10)).isEmpty()
    }
}
