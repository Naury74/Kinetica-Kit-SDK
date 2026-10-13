package com.naury.kinetica.core.scene

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.testing.TestScenes
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.group
import com.naury.kinetica.core.testing.TestScenes.id
import com.naury.kinetica.core.testing.TestScenes.rect
import org.junit.Test

class HitTesterTest {
    private fun hit(document: SceneDocument, x: Double, y: Double, slop: Double = 0.0): String? =
        HitTester(SceneGraph(document)).hitTest(Vec2(x, y), slop)?.value

    @Test
    fun `회전한 사각형은 실제 모양으로 맞힌다`() {
        val rotated =
            rect("r", size = Size(200.0, 20.0), transform = Transform2D(translationX = 100.0, translationY = 100.0, rotationDegrees = 45.0))
        val doc = document(listOf(rotated))

        assertThat(hit(doc, 100.0 + 70.0, 100.0 + 70.0 + 5.0)).isEqualTo("r")
        assertThat(hit(doc, 100.0 + 150.0, 100.0 + 10.0)).isNull()
    }

    @Test
    fun `겹친 노드는 맨 위를 맞힌다`() {
        val doc = document(listOf(rect("bottom"), rect("top")), rootOrder = listOf("bottom", "top"))
        val swapped = doc.copy(rootOrder = listOf(id("top"), id("bottom")))

        assertThat(hit(doc, 10.0, 10.0)).isEqualTo("top")
        assertThat(hit(swapped, 10.0, 10.0)).isEqualTo("bottom")
    }

    @Test
    fun `그룹 안 노드는 부모 변환을 거쳐 맞힌다`() {
        val doc = TestScenes.sample()

        assertThat(hit(doc, 100.0 + 200.0 + 10.0, 800.0 + 10.0)).isEqualTo("d")
        assertThat(hit(doc, 100.0 + 150.0, 800.0 + 10.0)).isNull()
    }

    @Test
    fun `숨긴 노드와 잠긴 노드는 화면 조작에서 맞히지 않는다`() {
        val hidden = rect("top").copy(visible = false)
        val doc = document(listOf(rect("bottom"), hidden))
        val lockedGroup = document(
            listOf(group("g", listOf("c")).copy(locked = true), rect("c", parent = "g")),
            rootOrder = listOf("g"),
        )

        assertThat(hit(doc, 10.0, 10.0)).isEqualTo("bottom")
        assertThat(hit(lockedGroup, 10.0, 10.0)).isNull()
        assertThat(HitTester(SceneGraph(lockedGroup)).hitTest(Vec2(10.0, 10.0), includeLocked = true)).isEqualTo(id("c"))
    }

    @Test
    fun `타원은 바깥 모서리를 맞히지 않고 여유 거리는 넓혀 맞힌다`() {
        val doc = document(listOf(SceneNode(id("e"), NodeContent.Ellipse(Size(100.0, 100.0)))))

        assertThat(hit(doc, 50.0, 50.0)).isEqualTo("e")
        assertThat(hit(doc, 5.0, 5.0)).isNull()
        assertThat(hit(doc, 104.0, 50.0)).isNull()
        assertThat(hit(doc, 104.0, 50.0, slop = 5.0)).isEqualTo("e")
    }

    @Test
    fun `열린 경로는 선 근처만, 닫힌 경로는 안쪽도 맞힌다`() {
        val line = listOf(PathCommand.MoveTo(Vec2(0.0, 0.0)), PathCommand.LineTo(Vec2(100.0, 0.0)), PathCommand.LineTo(Vec2(100.0, 100.0)))
        val open = SceneNode(id("open"), NodeContent.Path(line), style = NodeStyle(strokeWidth = 4.0))
        val closed = SceneNode(id("closed"), NodeContent.Path(line + PathCommand.Close), transform = Transform2D(translationX = 500.0))
        val doc = document(listOf(open, closed))

        assertThat(hit(doc, 50.0, 1.5)).isEqualTo("open")
        assertThat(hit(doc, 80.0, 40.0)).isNull()
        assertThat(hit(doc, 580.0, 40.0)).isEqualTo("closed")
        assertThat(hit(doc, 520.0, 60.0)).isNull()
    }

    @Test
    fun `사각 선택은 범위가 겹치는 도형을 그리기 순서로 돌려준다`() {
        val found = HitTester(SceneGraph(TestScenes.sample())).nodesIntersecting(Rect(0.0, 0.0, 300.0, 1000.0))

        assertThat(found.map { it.value }).containsExactly("a", "c", "d").inOrder()
    }

    @Test
    fun `그리기 순서는 깊이 우선으로 펼친다`() {
        assertThat(SceneGraph(TestScenes.sample()).drawOrder.map { it.value }).containsExactly("a", "b", "g", "c", "d").inOrder()
    }

    @Test
    fun `노드 하나의 선택 테두리는 노드와 함께 돌고 회전 손잡이는 위로 나간다`() {
        val node =
            rect("r", size = Size(100.0, 50.0), transform = Transform2D(translationX = 10.0, translationY = 20.0, rotationDegrees = 90.0))
        val graph = SceneGraph(document(listOf(node)))

        val frame = checkNotNull(SelectionFrame.of(graph, listOf(id("r"))))
        val handles = frame.handles(Viewport(), rotateOffsetPx = 20.0)

        assertThat(frame.corners[1].x).isWithin(1e-9).of(10.0)
        assertThat(frame.corners[1].y).isWithin(1e-9).of(120.0)
        val rotate = handles.getValue(SelectionHandle.Rotate)
        assertThat(rotate.x).isWithin(1e-9).of(30.0)
        assertThat(rotate.y).isWithin(1e-9).of(70.0)
        assertThat(SelectionHandle.at(handles, Vec2(31.0, 71.0), touchRadiusPx = 24.0)).isEqualTo(SelectionHandle.Rotate)
        assertThat(SelectionHandle.at(handles, Vec2(500.0, 500.0), touchRadiusPx = 24.0)).isNull()
    }

    @Test
    fun `여러 노드의 선택 테두리는 축 정렬 합집합이다`() {
        val frame = checkNotNull(SelectionFrame.of(SceneGraph(TestScenes.sample()), listOf(id("a"), id("g"))))

        assertThat(frame.corners.first()).isEqualTo(Vec2(100.0, 200.0))
        assertThat(frame.corners[2]).isEqualTo(Vec2(400.0, 850.0))
    }
}
