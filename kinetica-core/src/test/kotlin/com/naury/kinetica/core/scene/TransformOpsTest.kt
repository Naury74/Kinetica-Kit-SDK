package com.naury.kinetica.core.scene

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.group
import com.naury.kinetica.core.testing.TestScenes.id
import com.naury.kinetica.core.testing.TestScenes.rect
import kotlin.random.Random
import org.junit.Test

class TransformOpsTest {
    private val corners = mapOf(
        SelectionHandle.TopLeft to SelectionHandle.BottomRight,
        SelectionHandle.TopRight to SelectionHandle.BottomLeft,
        SelectionHandle.BottomRight to SelectionHandle.TopLeft,
        SelectionHandle.BottomLeft to SelectionHandle.TopRight,
        SelectionHandle.Top to SelectionHandle.Bottom,
        SelectionHandle.Bottom to SelectionHandle.Top,
        SelectionHandle.Left to SelectionHandle.Right,
        SelectionHandle.Right to SelectionHandle.Left,
    )

    private fun handlePosition(graph: SceneGraph, nodeId: NodeId, handle: SelectionHandle): Vec2 =
        checkNotNull(SelectionFrame.of(graph, listOf(nodeId))).handles(com.naury.kinetica.core.geometry.Viewport(), 0.0).getValue(handle)

    private fun nestedScene(random: Random, path: Boolean): SceneDocument {
        val parent = group(
            "g",
            listOf("n"),
            transform = Transform2D(
                translationX = random.nextDouble(-200.0, 200.0),
                translationY = random.nextDouble(-200.0, 200.0),
                scaleX = random.nextDouble(0.5, 2.0),
                scaleY = random.nextDouble(0.5, 2.0),
                rotationDegrees = random.nextDouble(-180.0, 180.0),
            ),
        )
        val transform = Transform2D(
            translationX = random.nextDouble(-100.0, 100.0),
            translationY = random.nextDouble(-100.0, 100.0),
            scaleX = if (path) random.nextDouble(0.5, 2.0) else 1.0,
            scaleY = if (path) random.nextDouble(0.5, 2.0) else 1.0,
            rotationDegrees = random.nextDouble(-180.0, 180.0),
            pivotX = random.nextDouble(0.0, 60.0),
            pivotY = random.nextDouble(0.0, 30.0),
        )
        val child = if (path) {
            SceneNode(
                id("n"),
                NodeContent.Path(
                    listOf(PathCommand.MoveTo(Vec2(-10.0, 5.0)), PathCommand.LineTo(Vec2(50.0, 5.0)), PathCommand.LineTo(Vec2(20.0, 45.0))),
                ),
                parentId = id("g"),
                transform = transform,
            )
        } else {
            rect("n", parent = "g", size = Size(60.0, 30.0), transform = transform)
        }
        return document(listOf(parent, child), rootOrder = listOf("g"))
    }

    @Test
    fun `회전·중첩된 노드를 어느 손잡이로 늘려도 반대쪽 손잡이는 제자리다`() {
        val random = Random(seed = 105)
        repeat(300) { iteration ->
            val doc = nestedScene(random, path = iteration % 2 == 1)
            val graph = SceneGraph(doc)
            corners.forEach { (handle, opposite) ->
                val anchor = handlePosition(graph, id("n"), opposite)
                val pointer = handlePosition(graph, id("n"), handle) + Vec2(random.nextDouble(-30.0, 30.0), random.nextDouble(-30.0, 30.0))

                val resized = checkNotNull(TransformOps.resize(graph, id("n"), handle, pointer))
                val after = SceneGraph(doc.copy(nodes = doc.nodes + (resized.id to resized)))

                assertThat(handlePosition(after, id("n"), opposite).distanceTo(anchor)).isLessThan(1e-6)
            }
        }
    }

    @Test
    fun `오른쪽 손잡이는 내용 크기를 포인터까지 늘린다`() {
        val graph = SceneGraph(document(listOf(rect("n", size = Size(100.0, 50.0), transform = Transform2D(translationX = 10.0)))))

        val resized = checkNotNull(TransformOps.resize(graph, id("n"), SelectionHandle.Right, Vec2(160.0, 999.0)))

        assertThat(resized.content).isEqualTo(NodeContent.Rectangle(Size(150.0, 50.0)))
        assertThat(resized.transform.translationX).isWithin(1e-9).of(10.0)
    }

    @Test
    fun `왼쪽 손잡이로 늘리면 원점이 옮겨지고 반전하지 않는다`() {
        val graph = SceneGraph(document(listOf(rect("n", size = Size(100.0, 50.0)))))

        val wider = checkNotNull(TransformOps.resize(graph, id("n"), SelectionHandle.Left, Vec2(-20.0, 0.0)))
        val crossed = checkNotNull(TransformOps.resize(graph, id("n"), SelectionHandle.Left, Vec2(500.0, 0.0)))

        assertThat(wider.transform.translationX).isWithin(1e-9).of(-20.0)
        assertThat((wider.content as NodeContent.Rectangle).size.width).isWithin(1e-9).of(120.0)
        assertThat((crossed.content as NodeContent.Rectangle).size.width).isEqualTo(TransformOps.MIN_EXTENT)
        assertThat(crossed.transform.isFlipped).isFalse()
    }

    @Test
    fun `비율 유지는 큰 쪽 배율을 두 축에 쓴다`() {
        val graph = SceneGraph(document(listOf(rect("n", size = Size(100.0, 50.0)))))

        val resized = checkNotNull(TransformOps.resize(graph, id("n"), SelectionHandle.BottomRight, Vec2(150.0, 60.0), keepAspect = true))

        assertThat(resized.content).isEqualTo(NodeContent.Rectangle(Size(150.0, 75.0)))
    }

    @Test
    fun `회전은 상자 가운데를 제자리에 두고 스냅한다`() {
        val random = Random(seed = 7)
        repeat(100) {
            val doc = nestedScene(random, path = it % 2 == 0)
            val graph = SceneGraph(doc)
            val center = checkNotNull(SelectionFrame.of(graph, listOf(id("n")))).center
            val transform = checkNotNull(TransformOps.rotate(graph, id("n"), random.nextDouble(-400.0, 400.0)))
            val after = SceneGraph(doc.copy(nodes = doc.nodes + (id("n") to doc.nodes.getValue(id("n")).copy(transform = transform))))

            assertThat(checkNotNull(SelectionFrame.of(after, listOf(id("n")))).center.distanceTo(center)).isLessThan(1e-6)
        }
        val graph = SceneGraph(document(listOf(rect("n"))))
        assertThat(TransformOps.rotate(graph, id("n"), 37.0, snapDegrees = 15.0)?.rotationDegrees).isEqualTo(30.0)
    }

    @Test
    fun `옮기기는 회전·확대된 부모 안에서도 손가락을 따라간다`() {
        val random = Random(seed = 9)
        repeat(100) {
            val doc = nestedScene(random, path = false)
            val graph = SceneGraph(doc)
            val delta = Vec2(random.nextDouble(-100.0, 100.0), random.nextDouble(-100.0, 100.0))
            val before = checkNotNull(graph.worldMatrix(id("n"))).map(Vec2.Zero)

            val moved = TransformOps.move(graph, listOf(id("n")), delta).getValue(id("n"))
            val after = SceneGraph(doc.copy(nodes = doc.nodes + (id("n") to doc.nodes.getValue(id("n")).copy(transform = moved))))

            assertThat(checkNotNull(after.worldMatrix(id("n"))).map(Vec2.Zero).distanceTo(before + delta)).isLessThan(1e-6)
        }
    }

    @Test
    fun `여러 노드를 함께 늘리면 반대쪽은 고정되고 자리와 크기가 같은 비율로 바뀐다`() {
        val doc = document(
            listOf(
                rect("a", transform = Transform2D(translationX = 0.0, translationY = 0.0)),
                rect("b", transform = Transform2D(translationX = 100.0, translationY = 50.0)),
            ),
        )
        val graph = SceneGraph(doc)
        // 두 사각형을 감싼 상자는 (0,0)-(200,100). 오른쪽 아래 핸들을 (400,100)까지 끌면 가로만 두 배.
        val nodes = TransformOps.resizeMany(graph, listOf(id("a"), id("b")), SelectionHandle.BottomRight, Vec2(400.0, 100.0))
        val after = SceneGraph(nodes.fold(doc) { d, n -> d.copy(nodes = d.nodes + (n.id to n)) })

        assertThat(checkNotNull(after.worldBounds(id("a")))).isEqualTo(com.naury.kinetica.core.geometry.Rect(0.0, 0.0, 200.0, 50.0))
        assertThat(checkNotNull(after.worldBounds(id("b")))).isEqualTo(com.naury.kinetica.core.geometry.Rect(200.0, 50.0, 400.0, 100.0))
    }

    @Test
    fun `여러 노드를 함께 돌리면 감싼 상자 가운데를 축으로 돈다`() {
        val doc = document(
            listOf(
                rect("a", size = Size(20.0, 20.0)),
                rect("b", transform = Transform2D(translationX = 80.0), size = Size(20.0, 20.0)),
            ),
        )
        val graph = SceneGraph(doc)
        val transforms = TransformOps.rotateMany(graph, listOf(id("a"), id("b")), 90.0)
        val after = SceneGraph(doc.copy(nodes = doc.nodes.mapValues { (k, v) -> transforms[k]?.let { v.copy(transform = it) } ?: v }))

        // 상자 가운데 (50,10). a의 가운데 (10,10)은 90도 돌면 (50,-30)으로 간다.
        val centerA = checkNotNull(after.worldBounds(id("a"))).center
        assertThat(centerA.distanceTo(Vec2(50.0, -30.0))).isLessThan(1e-6)
        assertThat(transforms.getValue(id("a")).rotationDegrees).isWithin(1e-9).of(90.0)
    }
}
