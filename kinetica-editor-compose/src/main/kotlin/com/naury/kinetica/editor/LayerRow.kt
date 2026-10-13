package com.naury.kinetica.editor

import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode

/** 레이어 목록 한 줄. [depth]는 그룹 중첩 깊이(최상위 0)다. */
internal data class LayerRow(val node: SceneNode, val depth: Int)

/** 맨 위에 그려지는 노드가 먼저 오는 레이어 목록. 그룹 자식은 그룹 바로 아래에 들여 쓴다. */
internal fun layerRows(document: SceneDocument): List<LayerRow> {
    val rows = ArrayList<LayerRow>(document.nodes.size)
    fun visit(ids: List<NodeId>, depth: Int) {
        ids.asReversed().forEach { id ->
            val node = document.nodes[id] ?: return@forEach
            rows += LayerRow(node, depth)
            visit(document.childrenOf(id), depth + 1)
        }
    }
    visit(document.rootOrder, 0)
    return rows
}
