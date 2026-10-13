package com.naury.kinetica.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.google.common.truth.Truth.assertThat
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
class KineticaEditorTest {
    @get:Rule
    val compose = createComposeRule()

    private val session = (KineticaSession.create(SceneDocument.empty(Artboard(1080.0, 1920.0))) as SessionOpenResult.Opened).session

    @Test
    fun `도구 줄로 만들고 끌어 옮기고 지우고 되돌린다`() {
        compose.setContent { KineticaEditor(session) }
        compose.onNodeWithText("Undo").assertIsNotEnabled()

        compose.onNodeWithText("Rectangle").performClick()
        compose.waitForIdle()
        val created = session.document.value.rootOrder.single()
        assertThat(session.selection.value.selectedIds).containsExactly(created)
        val before = session.document.value.node(created)!!.transform.translationX

        compose.onNodeWithTag(CANVAS_TAG).performTouchInput {
            swipe(center, center + Offset(100f, 0f), durationMillis = 300)
        }
        compose.waitForIdle()
        val moved = session.document.value.node(created)!!.transform.translationX
        assertThat(moved).isGreaterThan(before + 1.0)
        compose.onNodeWithText("Undo").assertIsEnabled()

        compose.onNodeWithText("Delete").performClick()
        compose.waitForIdle()
        assertThat(session.document.value.nodes).isEmpty()

        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        assertThat(session.document.value.node(created)!!.transform.translationX).isEqualTo(moved)
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        assertThat(session.document.value.node(created)!!.transform.translationX).isEqualTo(before)
    }

    @Test
    fun `빈 곳을 누르면 선택을 지운다`() {
        compose.setContent { KineticaEditor(session) }
        compose.onNodeWithText("Ellipse").performClick()
        compose.waitForIdle()
        assertThat(session.selection.value.isEmpty).isFalse()

        compose.onNodeWithTag(CANVAS_TAG).performTouchInput { click(Offset(5f, 5f)) }
        compose.waitForIdle()

        assertThat(session.selection.value.isEmpty).isTrue()
    }
}
