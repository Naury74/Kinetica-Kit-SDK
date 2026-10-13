package com.naury.kinetica.render

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.core.testing.TestScenes
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.group
import com.naury.kinetica.core.testing.TestScenes.rect
import org.junit.Test

class RenderPlanTest {
    private val everything = Rect(-1e6, -1e6, 1e6, 1e6)

    private fun plan(doc: com.naury.kinetica.core.scene.SceneDocument, visible: Rect = everything) =
        planDraw(SceneGraph(doc), visible).map { it.node.id.value }

    @Test
    fun `그리기 순서대로 도형만 담는다`() {
        assertThat(plan(TestScenes.sample())).containsExactly("a", "b", "c", "d").inOrder()
    }

    @Test
    fun `보이는 영역 밖 노드는 뺀다`() {
        assertThat(plan(TestScenes.sample(), Rect(0.0, 0.0, 300.0, 300.0))).containsExactly("a")
    }

    @Test
    fun `숨긴 조상과 투명한 노드는 뺀다`() {
        val doc = document(
            listOf(
                group("g", listOf("c")).copy(visible = false),
                rect("c", parent = "g"),
                rect("clear").copy(style = NodeStyle(opacity = 0.0)),
                rect("shown"),
            ),
            rootOrder = listOf("g", "clear", "shown"),
        )

        assertThat(plan(doc)).containsExactly("shown")
    }

    @Test
    fun `그룹 opacity를 자식에게 곱한다`() {
        val doc = document(
            listOf(
                group("g", listOf("c")).copy(style = NodeStyle(opacity = 0.5)),
                rect("c", parent = "g").copy(style = NodeStyle(opacity = 0.5)),
            ),
            rootOrder = listOf("g"),
        )

        assertThat(planDraw(SceneGraph(doc), everything).single().alpha).isWithin(1e-6f).of(0.25f)
    }

    @Test
    fun `배율이 0인 평가 결과는 그리지 않는다`() {
        val doc = document(listOf(rect("flat", transform = Transform2D(scaleX = 0.0))))

        assertThat(plan(doc)).isEmpty()
    }
}
