package com.naury.kinetica.render

import com.naury.kinetica.core.geometry.Mat3
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.core.scene.SceneNode

/**
 * 그릴 도형 하나. [world]는 노드 좌표를 문서 좌표로 바꾸는 행렬이고, [alpha]는 레이어로 합성하지 않은 조상의 opacity까지 곱한 값이다.
 * 그리기 전에 [openLayers]의 불투명도로 레이어를 차례로 열고, 그린 뒤 [closeLayers]개를 닫는다.
 */
internal class DrawItem(
    val node: SceneNode,
    val world: Mat3,
    val alpha: Float,
    val openLayers: List<Float> = emptyList(),
    var closeLayers: Int = 0,
)

/**
 * 그리기 순서대로 도형 목록을 만든다. 자신이나 조상이 숨겨진 노드, 완전히 투명한 노드, [visibleDocumentRect] 밖에 있는 노드,
 * 역행렬이 없는 노드는 제외한다. 그룹은 직접 그리지 않고 자식을 잘라 내지 않는다.
 *
 * 반투명 그룹은 자식을 한 레이어에 그린 뒤 그 레이어를 그룹 opacity로 합성한다. 자식마다 opacity를 곱하면 겹친 부분이 더 진해지기 때문이다.
 */
internal fun planDraw(graph: SceneGraph, visibleDocumentRect: Rect): List<DrawItem> {
    val nodes = graph.document.nodes
    val alphas = HashMap<NodeId, Double>()
    val layers = ArrayDeque<OpenLayer>()
    val items = ArrayList<DrawItem>()
    fun closeLayersOutside(node: SceneNode) {
        while (layers.isNotEmpty() && !isDescendant(nodes, node, layers.last().group)) {
            if (layers.removeLast().opened) items.last().closeLayers++
        }
    }
    graph.drawOrder.forEach { id ->
        val node = nodes.getValue(id)
        closeLayersOutside(node)
        val parentAlpha = node.parentId?.let { alphas[it] } ?: 1.0
        val opacity = if (node.visible) node.style.opacity else 0.0
        if (node.isTranslucentGroup(opacity)) {
            alphas[id] = parentAlpha
            layers.addLast(OpenLayer(id, opacity.toFloat()))
            return@forEach
        }
        val alpha = parentAlpha * opacity
        alphas[id] = alpha
        val world = graph.worldMatrix(id)
        if (world != null && isDrawable(node, alpha, world, graph.worldBounds(id), visibleDocumentRect)) {
            // 레이어는 그 안에 실제로 그릴 도형이 생길 때 연다. 자식이 모두 화면 밖이면 레이어를 만들지 않는다.
            val opens = layers.filterNot { it.opened }.onEach { it.opened = true }.map { it.alpha }
            items += DrawItem(node, world, alpha.toFloat(), opens)
        }
    }
    layers.filter { it.opened }.forEach { _ -> items.last().closeLayers++ }
    return items
}

private fun SceneNode.isTranslucentGroup(opacity: Double): Boolean = content is NodeContent.Group && opacity > 0.0 && opacity < 1.0

private fun isDrawable(node: SceneNode, alpha: Double, world: Mat3, bounds: Rect?, visible: Rect): Boolean =
    node.content !is NodeContent.Group && alpha > 0.0 && world.inverseOrNull() != null && bounds != null && bounds.intersects(visible)

private class OpenLayer(val group: NodeId, val alpha: Float) {
    var opened = false
}

private fun isDescendant(nodes: Map<NodeId, SceneNode>, node: SceneNode, ancestor: NodeId): Boolean {
    var parent = node.parentId
    while (parent != null) {
        if (parent == ancestor) return true
        parent = nodes[parent]?.parentId
    }
    return false
}
