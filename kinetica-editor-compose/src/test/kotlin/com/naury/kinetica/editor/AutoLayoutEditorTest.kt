package com.naury.kinetica.editor

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.CrossAlign
import com.naury.kinetica.core.scene.LayoutDirection
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.Pin
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
class AutoLayoutEditorTest {
    @get:Rule
    val compose = createComposeRule()

    private fun box(id: String, x: Double, y: Double) =
        SceneNode(NodeId(id), NodeContent.Rectangle(Size(100.0, 40.0)), transform = Transform2D(translationX = x, translationY = y))

    private val session = (
        KineticaSession.create(
            SceneDocument(
                documentId = "layout",
                artboard = Artboard(600.0, 800.0),
                nodes = listOf(box("a", 100.0, 300.0), box("b", 140.0, 100.0)).associateBy { it.id },
                rootOrder = listOf(NodeId("a"), NodeId("b")),
            ),
        ) as SessionOpenResult.Opened
        ).session

    private fun frame(): Pair<SceneNode, NodeContent.Frame> {
        val node = session.document.value.nodes.values.single { it.content is NodeContent.Frame }
        return node to node.content as NodeContent.Frame
    }

    @Test
    fun `고른 노드를 자동 배치로 감싸고 속성 패널에서 방향·정렬을 바꾼 뒤 한 번에 하나씩 되돌린다`() {
        compose.setContent { KineticaEditor(session) }
        session.select(listOf(NodeId("a"), NodeId("b")))
        compose.waitForIdle()

        compose.onNodeWithText("Auto layout").performClick()
        compose.waitForIdle()
        val (wrapped, content) = frame()
        assertThat(content.childIds).containsExactly(NodeId("b"), NodeId("a")).inOrder()
        assertThat(content.layout!!.direction).isEqualTo(LayoutDirection.Vertical)
        assertThat(session.selection.value.selectedIds).containsExactly(wrapped.id)

        compose.onNodeWithText("Row").performScrollTo().performClick()
        compose.onNodeWithText("Stretch").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(frame().second.layout!!.direction).isEqualTo(LayoutDirection.Horizontal)
        assertThat(frame().second.layout!!.crossAlign).isEqualTo(CrossAlign.Stretch)
        assertThat(session.document.value.node(NodeId("a"))!!.transform.translationX).isEqualTo(132.0)

        session.history.undo()
        assertThat(frame().second.layout!!.crossAlign).isEqualTo(CrossAlign.Start)
    }

    @Test
    fun `최상위 노드의 화면 크기 제약을 고친다`() {
        compose.setContent { KineticaEditor(session) }
        session.select(listOf(NodeId("a")))
        compose.waitForIdle()

        compose.onNodeWithText("Bottom").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(session.document.value.node(NodeId("a"))!!.constraints.vertical).isEqualTo(Pin.End)
    }

    @Test
    fun `줄 바꿈을 켜고 최소 너비를 넣으면 크기가 그 아래로 줄지 않는다`() {
        compose.setContent { KineticaEditor(session) }
        session.select(listOf(NodeId("a"), NodeId("b")))
        compose.waitForIdle()
        compose.onNodeWithText("Auto layout").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Wrap").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(frame().second.layout!!.wrap).isTrue()

        session.select(listOf(NodeId("a")))
        compose.waitForIdle()
        compose.onNodeWithText("Min width").performScrollTo().performTextReplacement("150")
        compose.onNodeWithText("Min width").performImeAction()
        compose.waitForIdle()

        val a = session.document.value.node(NodeId("a"))!!
        assertThat(a.sizeLimits.minWidth).isEqualTo(150.0)
        assertThat((a.content as NodeContent.Rectangle).size.width).isEqualTo(150.0)
    }

    @Test
    fun `움직임 효과에서 리플·튕기며·반짝임을 고르면 노드에 저장되고 한 번씩 되돌린다`() {
        compose.setContent { KineticaEditor(session) }
        session.select(listOf(NodeId("a")))
        compose.waitForIdle()

        compose.onNodeWithText("Ripple").performScrollTo().performClick()
        compose.onNodeWithText("Pop").performScrollTo().performClick()
        compose.onNodeWithText("Shimmer").performScrollTo().performClick()
        compose.waitForIdle()

        val effects = session.document.value.node(NodeId("a"))!!.effects
        assertThat(effects.press.map { it.kind }).containsExactly(com.naury.kinetica.core.scene.PressKind.Ripple)
        assertThat(effects.appear?.kind).isEqualTo(com.naury.kinetica.core.scene.AppearKind.Pop)
        assertThat(effects.loop?.kind).isEqualTo(com.naury.kinetica.core.scene.LoopKind.Shimmer)
        session.history.undo()
        assertThat(session.document.value.node(NodeId("a"))!!.effects.loop).isNull()
    }
}
