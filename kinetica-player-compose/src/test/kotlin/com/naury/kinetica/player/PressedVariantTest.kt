package com.naury.kinetica.player

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.Components
import com.naury.kinetica.core.scene.InstanceOf
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
class PressedVariantTest {
    @get:Rule
    val compose = createComposeRule()

    private fun main(id: String, name: String, color: Int) = SceneNode(
        NodeId(id),
        NodeContent.Frame(Size(200.0, 100.0)),
        component = name,
        style = NodeStyle(fill = ArgbColor(color)),
        transform = Transform2D(translationX = 1_000.0),
    )

    @Test
    fun `눌림 변형이 있는 버튼은 누르는 동안 그 변형으로 보이고 떼면 돌아온다`() {
        val instance =
            SceneNode(NodeId("buy"), NodeContent.Frame(Size(200.0, 100.0)), instanceOf = InstanceOf(NodeId("up")), action = "buy")
        val document = Components.sync(
            SceneDocument(
                "p",
                Artboard(200.0, 100.0),
                listOf(main("up", "버튼/기본", 0xFF0000FF.toInt()), main("down", "버튼/눌림", 0xFFFF0000.toInt()), instance).associateBy { it.id },
                listOf(NodeId("up"), NodeId("down"), NodeId("buy")),
            ),
        )
        var actions = 0
        compose.setContent {
            KineticaPlayer(document, Modifier.size(200.dp, 100.dp).testTag("player"), onAction = { actions++ })
        }
        fun center() = compose.onNodeWithTag("player").captureToImage().toPixelMap().let { it[it.width / 2, it.height / 2] }

        assertThat(center()).isEqualTo(Color.Blue)
        compose.onNodeWithTag("player").performTouchInput { down(Offset(width / 2f, height / 2f)) }
        compose.waitForIdle()
        // 누름 물결이 겹쳐 조금 어둡지만 빨간 눌림 변형이다.
        center().let {
            assertThat(it.red).isGreaterThan(0.5f)
            assertThat(it.blue).isLessThan(0.1f)
        }
        compose.onNodeWithTag("player").performTouchInput { up() }
        compose.waitForIdle()

        assertThat(center()).isEqualTo(Color.Blue)
        assertThat(actions).isEqualTo(1)
    }
}
