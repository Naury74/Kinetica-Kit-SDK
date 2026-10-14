package com.naury.kinetica.editor

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import com.naury.kinetica.presets.UiKit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h1400dp-mdpi")
class ComponentEditorTest {
    @get:Rule
    val compose = createComposeRule()

    private fun session(vararg nodes: SceneNode) = (
        KineticaSession.create(
            SceneDocument(
                documentId = "c",
                artboard = Artboard(600.0, 800.0),
                nodes = nodes.associateBy {
                    it.id
                },
                rootOrder = nodes.filter { it.parentId == null }.map { it.id },
            ),
        ) as SessionOpenResult.Opened
        ).session

    @Test
    fun `기본 키트를 넣고 브랜드 토큰을 바꾸면 버튼 색이 바뀐다`() {
        val session = session()
        compose.setContent { KineticaEditor(session) }

        compose.onNodeWithText("+ Insert starter kit").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(session.document.value.nodes.values.count { it.component != null }).isEqualTo(9)

        compose.onNodeWithText(UiKit.Token.BRAND).performScrollTo().performTextReplacement("#FFFF0000")
        compose.onNodeWithText(UiKit.Token.BRAND).performImeAction()
        compose.waitForIdle()

        assertThat(session.document.value.node(NodeId("kit-button"))!!.style.fill).isEqualTo(ArgbColor(0xFFFF0000.toInt()))
    }

    @Test
    fun `Frame을 컴포넌트로 만들고 인스턴스를 넣어 원본 글자를 바꾸면 인스턴스도 바뀐다`() {
        val card = SceneNode(NodeId("card"), NodeContent.Frame(Size(200.0, 100.0), listOf(NodeId("title"))))
        val title = SceneNode(NodeId("title"), NodeContent.Text(Size(180.0, 40.0), "Title"), parentId = NodeId("card"))
        val session = session(card, title)
        compose.setContent { KineticaEditor(session) }
        session.select(listOf(NodeId("card")))
        compose.waitForIdle()

        compose.onNodeWithText("Make component").performClick()
        session.clearSelection()
        compose.waitForIdle()
        compose.onAllNodesWithText("Insert").onFirst().performScrollTo().performClick()
        compose.waitForIdle()
        val instance = session.document.value.nodes.values.single { it.instanceOf != null }
        val edits = PropertyEdits(EditorState(session))
        edits.setText(NodeId("title"), "Changed")

        val copy = session.document.value.node(
            (session.document.value.node(instance.id)!!.content as NodeContent.Frame).childIds.single(),
        )!!
        assertThat(session.document.value.node(NodeId("card"))!!.component).isNotNull()
        assertThat((copy.content as NodeContent.Text).text).isEqualTo("Changed")
    }

    @Test
    fun `변형을 추가하고 인스턴스에서 변형을 고르면 덮어쓴 글자를 지닌 채 바뀐다`() {
        val card = SceneNode(
            NodeId("card"),
            NodeContent.Frame(Size(200.0, 100.0), listOf(NodeId("title"))),
            component = "Card",
        )
        val title = SceneNode(NodeId("title"), NodeContent.Text(Size(180.0, 40.0), "Title"), parentId = NodeId("card"), name = "title")
        val session = session(card, title)
        compose.setContent { KineticaEditor(session) }
        session.select(listOf(NodeId("card")))
        compose.waitForIdle()

        compose.onNodeWithText("+ Add variant").performScrollTo().performClick()
        compose.waitForIdle()
        val mains = session.document.value.nodes.values.filter { it.component != null }
        assertThat(mains.map { it.component }).containsExactly("Card/Default", "Card/Variant 2")
        val variant = mains.single { it.component == "Card/Variant 2" }
        assertThat(session.document.value.childrenOf(variant.id)).hasSize(1)

        val state = EditorState(session)
        state.session.clearSelection()
        assertThat(ComponentEdits(state).insertInstance(NodeId("card"), com.naury.kinetica.core.geometry.Vec2(300.0, 600.0))).isTrue()
        val instanceId = session.document.value.nodes.values.single { it.instanceOf != null }.id
        PropertyEdits(state).setText(
            com.naury.kinetica.core.scene.Components.childId(instanceId, NodeId("title")),
            "Buy",
        )
        compose.waitForIdle()

        compose.onNodeWithText("Variant 2").performScrollTo().performClick()
        compose.waitForIdle()

        val instance = session.document.value.node(instanceId)!!
        assertThat(instance.instanceOf!!.main).isEqualTo(variant.id)
        val copy = session.document.value.node((instance.content as NodeContent.Frame).childIds.single())!!
        assertThat((copy.content as NodeContent.Text).text).isEqualTo("Buy")
    }
}
