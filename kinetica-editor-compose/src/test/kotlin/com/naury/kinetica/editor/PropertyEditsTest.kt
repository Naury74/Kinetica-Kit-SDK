package com.naury.kinetica.editor

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.NodeContent
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
    private val edits = PropertyEdits(session)
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
