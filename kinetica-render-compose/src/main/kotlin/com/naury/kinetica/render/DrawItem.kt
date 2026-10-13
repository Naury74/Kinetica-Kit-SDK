package com.naury.kinetica.render

import com.naury.kinetica.core.geometry.Mat3
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.core.scene.SceneNode

/** 그릴 도형 하나. [world]는 node-local → 문서 행렬, [alpha]는 조상 opacity까지 곱한 값이다. */
internal class DrawItem(val node: SceneNode, val world: Mat3, val alpha: Float)

/**
 * 그리기 순서대로 그릴 도형 목록을 만든다. 숨긴 노드(조상 포함), 투명한 노드, [visibleDocumentRect] 밖 노드, 역행렬이 없는 노드는 뺀다.
 * 그룹은 직접 그리지 않고 opacity만 자식에게 곱한다. 그룹은 자식을 자르지 않는다.
 */
internal fun planDraw(graph: SceneGraph, visibleDocumentRect: Rect): List<DrawItem> {
    val nodes = graph.document.nodes
    val alphas = HashMap<NodeId, Double>()
    return graph.drawOrder.mapNotNull { id ->
        val node = nodes.getValue(id)
        val parentAlpha = node.parentId?.let { alphas[it] } ?: 1.0
        val alpha = if (node.visible) parentAlpha * node.style.opacity else 0.0
        alphas[id] = alpha
        val world = graph.worldMatrix(id)
        val bounds = graph.worldBounds(id)
        val drawable = node.content !is NodeContent.Group && alpha > 0.0 && world != null &&
            world.inverseOrNull() != null && bounds != null && bounds.intersects(visibleDocumentRect)
        if (drawable) DrawItem(node, checkNotNull(world), alpha.toFloat()) else null
    }
}
