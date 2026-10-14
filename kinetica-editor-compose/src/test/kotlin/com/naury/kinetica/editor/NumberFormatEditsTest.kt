package com.naury.kinetica.editor

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.TextNumberFormat
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NumberFormatEditsTest {
    @Test
    fun `숫자 형식 앞뒤 글자·소수 자리를 바꾸고 모두 비우면 지운다`() {
        val text = SceneNode(NodeId("t"), NodeContent.Text(Size(100.0, 40.0), "0"))
        val session = (
            KineticaSession.create(
                SceneDocument("n", Artboard(200.0, 200.0), mapOf(text.id to text), listOf(text.id)),
            ) as SessionOpenResult.Opened
            ).session
        val edits = TextEdits(EditorState(session))
        fun format() = (session.document.value.node(text.id)!!.content as NodeContent.Text).numberFormat

        assertThat(edits.setNumberFormat(text.id, suffix = "원")).isTrue()
        assertThat(edits.setNumberFormat(text.id, decimalsText = "2")).isTrue()
        assertThat(format()).isEqualTo(TextNumberFormat(suffix = "원", decimals = 2))
        assertThat(edits.setNumberFormat(text.id, decimalsText = "9")).isFalse()

        edits.setNumberFormat(text.id, suffix = "", decimalsText = "")
        assertThat(format()).isNull()
    }
}
