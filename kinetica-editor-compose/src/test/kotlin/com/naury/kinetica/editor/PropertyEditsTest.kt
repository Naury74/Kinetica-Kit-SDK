package com.naury.kinetica.editor

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.BoundProperty
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.testing.TestScenes
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.id
import com.naury.kinetica.core.testing.TestScenes.rect
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import org.junit.Test

class PropertyEditsTest {
    private val scene = document(listOf(rect("a", size = Size(100.0, 50.0), transform = Transform2D(pivotX = 50.0, pivotY = 25.0))))
    private val session = (KineticaSession.create(scene) as SessionOpenResult.Opened).session
    private val edits = PropertyEdits(EditorState(session))
    private fun node() = session.document.value.node(id("a"))!!

    @Test
    fun `숫자 입력 하나가 실행 취소 한 단계다`() {
        assertThat(edits.setNumber(id("a"), NumericField.X, " 120.5 ")).isTrue()
        assertThat(edits.setNumber(id("a"), NumericField.Rotation, "45")).isTrue()
        assertThat(edits.setNumber(id("a"), NumericField.Opacity, "40")).isTrue()

        assertThat(node().transform.translationX).isEqualTo(120.5)
        assertThat(node().style.opacity).isWithin(1e-12).of(0.4)
        session.history.undo()
        session.history.undo()
        session.history.undo()
        assertThat(session.document.value).isEqualTo(scene)
    }

    @Test
    fun `잘못된 입력은 문서를 바꾸지 않는다`() {
        listOf("", "abc", "NaN", "Infinity", "1e309").forEach {
            assertThat(edits.setNumber(id("a"), NumericField.X, it)).isFalse()
        }
        assertThat(edits.setNumber(id("a"), NumericField.Opacity, "150")).isFalse()
        assertThat(edits.setNumber(id("a"), NumericField.Width, "0")).isFalse()
        assertThat(edits.setFill(id("a"), "#GGGGGG")).isFalse()

        assertThat(session.document.value).isEqualTo(scene)
        assertThat(session.history.state.value.canUndo).isFalse()
    }

    @Test
    fun `크기를 바꾸면 pivot도 같은 비율로 옮긴다`() {
        edits.setNumber(id("a"), NumericField.Width, "200")

        assertThat(node().content).isEqualTo(NodeContent.Rectangle(Size(200.0, 50.0)))
        assertThat(node().transform.pivotX).isEqualTo(100.0)
        assertThat(edits.value(node(), NumericField.Width)).isEqualTo(200.0)
    }

    @Test
    fun `쉼표 소수점과 #이 없는 색도 받는다`() {
        assertThat(edits.setNumber(id("a"), NumericField.Y, "3,5")).isTrue()
        assertThat(edits.setFill(id("a"), "ff0000")).isTrue()

        assertThat(node().transform.translationY).isEqualTo(3.5)
        assertThat(node().style.fill).isEqualTo(ArgbColor(0xFFFF0000.toInt()))
    }

    @Test
    fun `글자 내용과 크기를 바꾸고 잘못된 값은 거부한다`() {
        val textScene = document(listOf(SceneNode(id("t"), NodeContent.Text(Size(200.0, 80.0), "글자"))))
        val textSession = (KineticaSession.create(textScene) as SessionOpenResult.Opened).session
        val textEdits = PropertyEdits(EditorState(textSession))

        assertThat(textEdits.setText(id("t"), "안녕하세요\n둘째 줄")).isTrue()
        assertThat(textEdits.setFontSize(id("t"), "36")).isTrue()
        assertThat(textEdits.setText(id("t"), "깨진\uD800")).isFalse()
        assertThat(textEdits.setFontSize(id("t"), "0")).isFalse()
        assertThat(textEdits.setText(id("a-missing"), "x")).isFalse()

        val content = textSession.document.value.node(id("t"))!!.content as NodeContent.Text
        assertThat(content.text).isEqualTo("안녕하세요\n둘째 줄")
        assertThat(content.fontSize).isEqualTo(36.0)
    }

    @Test
    fun `묶인 글자는 묶음을 풀며 고치면 한 단계로 되돌린다`() {
        val bound = SceneNode(id("t"), NodeContent.Text(Size(200.0, 80.0), "기본"), bindings = mapOf(BoundProperty.Text to "user.name"))
        val textScene = document(listOf(bound))
        val textSession = (KineticaSession.create(textScene) as SessionOpenResult.Opened).session
        val textEdits = PropertyEdits(EditorState(textSession))

        assertThat(textEdits.setText(id("t"), "직접 쓴 글자", unbind = true)).isTrue()

        val edited = textSession.document.value.node(id("t"))!!
        assertThat((edited.content as NodeContent.Text).text).isEqualTo("직접 쓴 글자")
        assertThat(edited.bindings).isEmpty()
        textSession.history.undo()
        assertThat(textSession.document.value).isEqualTo(textScene)
    }

    @Test
    fun `데이터 키·동작·그림자를 바꾸고 틀린 키는 거부한다`() {
        assertThat(edits.setBinding(id("a"), BoundProperty.Visible, " store.open ")).isTrue()
        assertThat(edits.setBinding(id("a"), BoundProperty.Fill, "틀린 키")).isFalse()
        assertThat(edits.setAction(id("a"), " refresh ")).isTrue()
        assertThat(edits.toggleShadow(id("a"))).isTrue()

        assertThat(node().bindings).containsExactly(BoundProperty.Visible, "store.open")
        assertThat(node().action).isEqualTo("refresh")
        assertThat(node().style.shadows).isNotEmpty()

        assertThat(edits.setBinding(id("a"), BoundProperty.Visible, "")).isTrue()
        assertThat(edits.setAction(id("a"), " ")).isTrue()
        assertThat(edits.toggleShadow(id("a"))).isTrue()
        assertThat(node()).isEqualTo(scene.node(id("a")))
    }

    @Test
    fun `자르기는 Frame에서만 바뀐다`() {
        assertThat(edits.toggleClip(id("a"))).isFalse()
    }

    @Test
    fun `숨기기·잠그기를 바꾼다`() {
        edits.toggleVisible(id("a"))
        edits.toggleLocked(id("a"))

        assertThat(node().visible).isFalse()
        assertThat(node().locked).isTrue()
    }

    @Test
    fun `레이어 목록은 맨 위 노드부터 그룹 자식을 들여 쓴다`() {
        val rows = layerRows(TestScenes.sample())

        assertThat(rows.map { it.node.id.value to it.depth }).containsExactly("g" to 0, "d" to 1, "c" to 1, "b" to 0, "a" to 0).inOrder()
    }
}
