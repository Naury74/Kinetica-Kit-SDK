package com.naury.kinetica.editor

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccessibilityEditsTest {
    @Test
    fun `화면 읽기 이름과 장식 표시를 바꾸고 되돌린다`() {
        val logo = SceneNode(NodeId("logo"), NodeContent.Rectangle(Size(40.0, 40.0)))
        val document =
            SceneDocument(documentId = "a", artboard = Artboard(100.0, 100.0), nodes = mapOf(logo.id to logo), rootOrder = listOf(logo.id))
        val session = (KineticaSession.create(document) as SessionOpenResult.Opened).session
        val edits = AccessibilityEdits(EditorState(session))

        assertThat(edits.set(logo.id, label = "  회사 로고 ")).isTrue()
        assertThat(edits.set(logo.id, hidden = true)).isTrue()
        val node = session.document.value.node(logo.id)!!
        session.history.undo()

        assertThat(node.accessibility.label).isEqualTo("회사 로고")
        assertThat(node.accessibility.hidden).isTrue()
        assertThat(session.document.value.node(logo.id)!!.accessibility.hidden).isFalse()
    }
}
