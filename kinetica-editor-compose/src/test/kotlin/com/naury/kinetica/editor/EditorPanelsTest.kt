package com.naury.kinetica.editor

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
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
@Config(qualifiers = "w1000dp-h800dp")
class EditorPanelsTest {
    @get:Rule
    val compose = createComposeRule()

    private val session = (KineticaSession.create(SceneDocument.empty(Artboard(1080.0, 1920.0))) as SessionOpenResult.Opened).session

    private fun node() = session.document.value.node(session.document.value.rootOrder.single())!!

    private fun field(label: String) = compose.onAllNodes(hasSetTextAction() and hasText(label)).onFirst()

    @Test
    fun `넓은 화면에서 속성 패널로 숫자를 고치고 잘못된 값은 거부한다`() {
        compose.setContent { KineticaEditor(session) }
        compose.onNodeWithText("Rectangle").performClick()
        compose.waitForIdle()

        field("X").performTextReplacement("123")
        field("X").performImeAction()
        compose.waitForIdle()
        assertThat(node().transform.translationX).isEqualTo(123.0)

        field("Rotation (°)").performTextReplacement("abc")
        field("Rotation (°)").performImeAction()
        compose.waitForIdle()
        assertThat(node().transform.rotationDegrees).isEqualTo(0.0)
        compose.onNodeWithText("Invalid value. The previous value was kept.").assertExists()
    }

    @Test
    fun `레이어 패널에서 숨기고 잠근다`() {
        compose.setContent { KineticaEditor(session) }
        compose.onNodeWithText("Ellipse").performClick()
        compose.waitForIdle()

        compose.onAllNodes(isToggleable() and hasText("Locked")).onFirst().performClick()
        compose.onAllNodes(isToggleable() and hasText("Visible")).onFirst().performClick()
        compose.waitForIdle()

        assertThat(node().locked).isTrue()
        assertThat(node().visible).isFalse()
        session.history.undo()
        assertThat(node().visible).isTrue()
    }
}
