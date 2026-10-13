package com.naury.kinetica.player

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.BoundProperty
import com.naury.kinetica.core.scene.DataValue
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerHostTest {
    @get:Rule
    val compose = createComposeRule()

    private val nodes = listOf(
        SceneNode(
            NodeId("counter"),
            NodeContent.HostSlot(Size(300.0, 150.0), "widget.counter"),
            transform = Transform2D(translationX = 20.0, translationY = 20.0),
        ),
        SceneNode(
            NodeId("label"),
            NodeContent.Text(Size(300.0, 80.0), "-", fontSize = 40.0),
            transform = Transform2D(translationX = 20.0, translationY = 200.0),
            bindings = mapOf(BoundProperty.Text to "user.name"),
        ),
        SceneNode(
            NodeId("button"),
            NodeContent.Frame(Size(300.0, 80.0), cornerRadius = 20.0),
            transform = Transform2D(translationX = 20.0, translationY = 320.0),
            style = NodeStyle(fill = ArgbColor(0xFF7C8CFF.toInt())),
            action = "checkout",
        ),
    )
    private val document = SceneDocument("player-host", Artboard(340.0, 420.0), nodes.associateBy { it.id }, nodes.map { it.id })

    @Test
    fun `사용 모드에서는 호스트 뷰가 동작하고 데이터·동작이 연결된다`() {
        val hostClicks = mutableIntStateOf(0)
        val actions = mutableListOf<String>()
        val data = mutableStateOf(SceneData.of("user.name" to DataValue.Text("나우리")))
        compose.setContent {
            KineticaPlayer(
                document,
                Modifier.size(340.dp, 420.dp).testTag("player"),
                data = data.value,
                slots = mapOf(
                    "widget.counter" to {
                        TextButton(onClick = { hostClicks.intValue++ }) { Text("count ${hostClicks.intValue}") }
                    },
                ),
                onAction = { actions += it },
            )
        }

        compose.onNodeWithText("count 0").performClick()
        compose.onNodeWithText("count 1").assertIsDisplayed()
        compose.onNodeWithText("나우리").assertIsDisplayed()
        data.value = SceneData.of("user.name" to DataValue.Text("Kinetica"))
        compose.onNodeWithText("Kinetica").assertIsDisplayed()

        assertThat(actions).isEmpty()
        // 버튼 Frame은 artboard의 (20..320, 320..400)에 있다. 재생기는 artboard를 영역에 꽉 맞춘다.
        compose.onNodeWithTag("player").performTouchInput { click(Offset(width * 0.5f, height * (360f / 420f))) }
        compose.onNodeWithTag("player").performTouchInput { click(Offset(width * 0.5f, height * (250f / 420f))) }
        compose.waitForIdle()
        assertThat(actions).containsExactly("checkout")
        assertThat(hostClicks.intValue).isEqualTo(1)
    }
}
