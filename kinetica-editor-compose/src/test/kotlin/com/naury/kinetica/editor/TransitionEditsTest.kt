package com.naury.kinetica.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneTransitionEdge
import com.naury.kinetica.core.scene.SceneTransitionKind
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransitionEditsTest {
    @get:Rule
    val compose = createComposeRule()

    private val session = (KineticaSession.create(SceneDocument("a", Artboard(100.0, 100.0))) as SessionOpenResult.Opened).session
    private val state = EditorState(session)
    private val edits = TransitionEdits(state)

    @Test
    fun `종류, 방향, 시간을 바꾸고 하나씩 되돌린다`() {
        assertThat(edits.setFrom(SceneTransitionEdge.Top)).isFalse()
        assertThat(edits.setKind(SceneTransitionKind.Wipe)).isTrue()
        assertThat(edits.setFrom(SceneTransitionEdge.Bottom)).isTrue()
        assertThat(edits.setDuration("250")).isTrue()
        assertThat(edits.setDuration("-3")).isFalse()
        assertThat(edits.setKind(SceneTransitionKind.Iris)).isTrue()

        val spec = session.document.value.transition!!
        assertThat(spec.kind).isEqualTo(SceneTransitionKind.Iris)
        assertThat(spec.from).isEqualTo(SceneTransitionEdge.Bottom)
        assertThat(spec.durationMs).isEqualTo(250)
        session.history.undo()
        assertThat(session.document.value.transition!!.kind).isEqualTo(SceneTransitionKind.Wipe)
        assertThat(edits.setKind(null)).isTrue()
        assertThat(session.document.value.transition).isNull()
    }

    @Test
    fun `종류 칩을 누르면 방향 칩이 방향 있는 전환에만 나온다`() {
        compose.setContent {
            val document by session.document.collectAsState()
            Column { TransitionSection(document, edits, StudioTheme.dark()) }
        }
        compose.onNodeWithText("Comes in from").assertDoesNotExist()
        compose.onNodeWithText("Push").performClick()
        compose.onNodeWithText("Comes in from").assertExists()
        assertThat(session.document.value.transition!!.kind).isEqualTo(SceneTransitionKind.Push)
        compose.onNodeWithText("Iris").performClick()
        compose.onNodeWithText("Comes in from").assertDoesNotExist()
    }
}
