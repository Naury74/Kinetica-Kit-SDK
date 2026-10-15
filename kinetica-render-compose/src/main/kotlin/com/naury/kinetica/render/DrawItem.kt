package com.naury.kinetica.render

import com.naury.kinetica.core.geometry.Mat3
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.core.scene.SceneNode

/** 그릴 도형 하나. [world]는 노드 좌표를 문서 좌표로 바꾸는 행렬이고, [alpha]는 조상의 opacity까지 곱한 값이다. */
internal class DrawItem(val node: SceneNode, val world: Mat3, val alpha: Float)

/**
 * 그리기 순서대로 도형 목록을 만든다. 자신이나 조상이 숨겨진 노드, 완전히 투명한 노드, [visibleDocumentRect] 밖에 있는 노드,
 * 역행렬이 없는 노드는 제외한다. 그룹은 직접 그리지 않고 opacity만 자식에게 곱하며, 자식을 잘라 내지 않는다.
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
