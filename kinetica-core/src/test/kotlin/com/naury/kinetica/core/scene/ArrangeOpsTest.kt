package com.naury.kinetica.core.scene

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.group
import com.naury.kinetica.core.testing.TestScenes.id
import com.naury.kinetica.core.testing.TestScenes.rect
import org.junit.Test

class ArrangeOpsTest {
    private fun rectAt(name: String, x: Double, y: Double, width: Double = 100.0) =
        rect(name, size = Size(width, 50.0), transform = Transform2D(translationX = x, translationY = y))

    private fun SceneDocument.apply(transforms: Map<NodeId, Transform2D>): SceneDocument =
        copy(nodes = nodes + transforms.map { (id, t) -> id to nodes.getValue(id).copy(transform = t) })

    private fun SceneDocument.bounds(name: String) = checkNotNull(SceneGraph(this).worldBounds(id(name)))

    @Test
    fun `여러 노드는 선택 범위에 맞춘다`() {
        val doc = document(listOf(rectAt("a", 10.0, 0.0), rectAt("b", 200.0, 100.0, width = 50.0)))
        val graph = SceneGraph(doc)

        val left = doc.apply(ArrangeOps.align(graph, listOf(id("a"), id("b")), AlignMode.Left))
        val right = doc.apply(ArrangeOps.align(graph, listOf(id("a"), id("b")), AlignMode.Right))
        val middle = doc.apply(ArrangeOps.align(graph, listOf(id("a"), id("b")), AlignMode.CenterVertical))

        assertThat(left.bounds("b").left).isEqualTo(10.0)
        assertThat(ArrangeOps.align(graph, listOf(id("a"), id("b")), AlignMode.Left).keys).containsExactly(id("b"))
        assertThat(right.bounds("a").right).isEqualTo(250.0)
        assertThat(middle.bounds("a").center.y).isEqualTo(middle.bounds("b").center.y)
    }

    @Test
    fun `노드 하나는 artboard에 맞춘다`() {
        val doc = document(listOf(rectAt("a", 10.0, 0.0)))

        val centered = doc.apply(ArrangeOps.align(SceneGraph(doc), listOf(id("a")), AlignMode.CenterHorizontal))

        assertThat(centered.bounds("a").center.x).isEqualTo(540.0)
    }

    @Test
    fun `분배하면 사이 간격이 같아지고 양 끝은 그대로다`() {
        val doc =
            document(listOf(rectAt("a", 0.0, 0.0), rectAt("b", 130.0, 0.0, width = 40.0), rectAt("c", 500.0, 0.0), rectAt("d", 210.0, 0.0)))
        val graph = SceneGraph(doc)

        val spread = doc.apply(ArrangeOps.distribute(graph, listOf(id("a"), id("b"), id("c"), id("d")), DistributeAxis.Horizontal))

        val ordered = listOf("a", "b", "d", "c").map { spread.bounds(it) }
        val gaps = ordered.zipWithNext { first, second -> second.left - first.right }
        gaps.forEach { assertThat(it).isWithin(1e-9).of(gaps.first()) }
        assertThat(ordered.first().left).isEqualTo(0.0)
        assertThat(ordered.last().right).isEqualTo(600.0)
        assertThat(ArrangeOps.distribute(graph, listOf(id("a"), id("b")), DistributeAxis.Horizontal)).isEmpty()
    }

    @Test
    fun `회전한 부모 안 노드도 문서 기준으로 맞춘다`() {
        val doc = document(
            listOf(
                group("g", listOf("a"), transform = Transform2D(rotationDegrees = 90.0, translationX = 500.0)),
                rect("a", parent = "g"),
                rectAt("b", 0.0, 0.0),
            ),
            rootOrder = listOf("g", "b"),
        )

        val aligned = doc.apply(ArrangeOps.align(SceneGraph(doc), listOf(id("a"), id("b")), AlignMode.Top))

        assertThat(aligned.bounds("a").top).isWithin(1e-9).of(aligned.bounds("b").top)
    }
}
