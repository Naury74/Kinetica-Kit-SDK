package com.naury.kinetica.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.BoundProperty
import com.naury.kinetica.core.scene.DataValue
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
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
@Config(qualifiers = "w1000dp-h900dp-mdpi")
class HostComposerTest {
    @get:Rule
    val compose = createComposeRule()

    private fun session(vararg nodes: SceneNode) = (
        KineticaSession.create(
            SceneDocument(
                documentId = "host",
                artboard = Artboard(600.0, 800.0),
                nodes = nodes.associateBy { it.id },
                rootOrder = nodes.map { it.id },
            ),
        ) as SessionOpenResult.Opened
        ).session

    @Test
    fun `호스트 뷰 메뉴로 넣으면 호스트 Composable이 그려지고 편집 중에는 눌리지 않는다`() {
        val session = session()
        var clicks = 0
        compose.setContent {
            KineticaEditor(
                session,
                slots = mapOf(
                    "widget.counter" to {
                        Box(Modifier.fillMaxSize().background(Color.Green).testTag("host-widget")) {
                            androidx.compose.material3.TextButton(onClick = { clicks++ }) { Text("count") }
                        }
                    },
                ),
                hostViews = listOf(HostViewOption("widget.counter", "Counter", 300.0, 200.0)),
            )
        }

        compose.onNodeWithText("Host view").performScrollTo().performClick()
        compose.onNodeWithText("Counter").performClick()
        compose.waitForIdle()

        val node = session.document.value.nodes.values.single()
        assertThat(node.content).isEqualTo(NodeContent.HostSlot(Size(300.0, 200.0), "widget.counter"))
        compose.onNodeWithTag("host-widget").assertIsDisplayed()
        compose.onNodeWithText("count").performClick()
        compose.waitForIdle()
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `글자를 두 번 누르면 편집 창이 열리고 확인하면 바뀐다`() {
        val label = SceneNode(
            NodeId("label"),
            NodeContent.Text(Size(400.0, 120.0), "처음", fontSize = 60.0),
            transform = Transform2D(translationX = 100.0, translationY = 300.0),
        )
        val session = session(label)
        compose.setContent { KineticaEditor(session) }
        compose.waitForIdle()

        compose.onNodeWithTag(CANVAS_TAG).performTouchInput { doubleClick(center) }
        compose.waitForIdle()
        compose.onNodeWithTag(TEXT_EDIT_TAG).performTextReplacement("바뀐 글자")
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()

        assertThat((session.document.value.node(NodeId("label"))!!.content as NodeContent.Text).text).isEqualTo("바뀐 글자")
        session.history.undo()
        assertThat((session.document.value.node(NodeId("label"))!!.content as NodeContent.Text).text).isEqualTo("처음")
    }

    @Test
    fun `편집기는 호스트 데이터가 바뀌면 바인딩된 글자를 바로 바꿔 그리고 문서는 그대로다`() {
        val label = SceneNode(
            NodeId("price"),
            NodeContent.Text(Size(400.0, 120.0), "0원", fontSize = 60.0),
            bindings = mapOf(BoundProperty.Text to "order.total"),
        )
        val session = session(label)
        val data = mutableStateOf(SceneData.of("order.total" to DataValue.Text("12,000원")))
        compose.setContent { KineticaEditor(session, data = data.value) }

        compose.onNodeWithText("12,000원").assertIsDisplayed()
        data.value = SceneData.of("order.total" to DataValue.Text("15,500원"))
        compose.waitForIdle()

        compose.onNodeWithText("15,500원").assertIsDisplayed()
        assertThat((session.document.value.node(NodeId("price"))!!.content as NodeContent.Text).text).isEqualTo("0원")
    }
}
