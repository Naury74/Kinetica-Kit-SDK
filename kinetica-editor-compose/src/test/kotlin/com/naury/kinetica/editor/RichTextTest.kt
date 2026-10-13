package com.naury.kinetica.editor

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.TextSpan
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
class RichTextTest {
    @get:Rule
    val compose = createComposeRule()

    private val title = SceneNode(NodeId("title"), NodeContent.Text(Size(400.0, 80.0), "Hello world", fontSize = 40.0))
    private val session = (
        KineticaSession.create(
            SceneDocument(
                documentId = "rich",
                artboard = Artboard(600.0, 800.0),
                nodes = mapOf(title.id to title),
                rootOrder = listOf(title.id),
            ),
        ) as SessionOpenResult.Opened
        ).session

    private fun content() = session.document.value.node(NodeId("title"))!!.content as NodeContent.Text

    @Test
    fun `고른 부분만 굵게·빨갛게 하고 확인하면 한 번에 되돌린다`() {
        compose.setContent { KineticaEditor(session) }
        session.select(listOf(NodeId("title")))
        compose.onNodeWithText("Edit text").performClick()

        compose.onNodeWithTag(TEXT_EDIT_TAG).performTextInputSelection(TextRange(6, 11))
        compose.onNodeWithText("Bold").performClick()
        compose.onNodeWithText("Color").performTextReplacement("#FFFF0000")
        compose.onNodeWithText("Color").performImeAction()
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()

        assertThat(content().text).isEqualTo("Hello world")
        assertThat(
            content().spans,
        ).containsExactly(TextSpan(6, 11, fontWeight = 700), TextSpan(6, 11, color = ArgbColor(0xFFFF0000.toInt())))
        session.history.undo()
        assertThat(content().spans).isEmpty()
    }

    @Test
    fun `속성 패널에서 글자 전체 글꼴과 기울임을 바꾼다`() {
        compose.setContent { KineticaEditor(session) }
        session.select(listOf(NodeId("title")))
        compose.waitForIdle()

        compose.onNodeWithText("Serif").performScrollTo().performClick()
        compose.onNodeWithText("Italic").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(content().fontFamily).isEqualTo("serif")
        assertThat(content().italic).isTrue()
    }
}
