package com.naury.kinetica.core.scene

import com.naury.kinetica.core.geometry.Mat3
import com.naury.kinetica.core.geometry.Rect

/**
 * 문서의 계층 구조에서 계산하는 값. 문서가 바뀌면 새로 만든다(불변 문서의 계산 캐시).
 *
 * 그리기 순서는 최상위부터 깊이 우선으로 펼친 순서이며 뒤에 있을수록 위에 그려진다.
 */
public class SceneGraph(public val document: SceneDocument) {
    private val worldMatrices = HashMap<NodeId, Mat3>()

    /** 깊이 우선으로 펼친 그리기 순서. 그룹 자신도 자식보다 먼저 들어간다. */
    public val drawOrder: List<NodeId> by lazy {
        val result = ArrayList<NodeId>(document.nodes.size)
        val pending = ArrayDeque<NodeId>()
        document.rootOrder.asReversed().forEach(pending::addFirst)
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (!document.nodes.containsKey(id)) continue
            result += id
            document.childrenOf(id).asReversed().forEach(pending::addFirst)
        }
        result
    }

    /** node-local 좌표를 문서 좌표로 옮기는 행렬. 부모 변환을 모두 곱한다. 노드가 없으면 `null`. */
    public fun worldMatrix(id: NodeId): Mat3? {
        worldMatrices[id]?.let { return it }
        val node = document.nodes[id] ?: return null
        val parentMatrix = node.parentId?.let(::worldMatrix) ?: Mat3.Identity
        return (parentMatrix * node.transform.matrix()).also { worldMatrices[id] = it }
    }

    /** 문서 좌표의 축 정렬 범위. 그룹은 자손 범위의 합이며 자손이 없으면 `null`이다. */
    public fun worldBounds(id: NodeId): Rect? {
        val node = document.nodes[id] ?: return null
        val local = node.localBounds
        if (local != null) return worldMatrix(id)?.mapBounds(local)
        return document.childrenOf(id).mapNotNull(::worldBounds).reduceOrNull(Rect::union)
    }

    /** 노드와 모든 조상이 보이는지. */
    public fun isEffectivelyVisible(id: NodeId): Boolean = ancestry(id).all { it.visible }

    /** 노드나 조상 중 하나라도 잠겼는지. */
    public fun isEffectivelyLocked(id: NodeId): Boolean = ancestry(id).any { it.locked }

    /** 노드에서 최상위까지 올라가는 순서. */
    public fun ancestry(id: NodeId): List<SceneNode> = generateSequence(document.nodes[id]) { node ->
        node.parentId?.let(document.nodes::get)
    }.toList()

    /** 최상위 노드 중 [id]를 담은 것. 선택 단위를 최상위로 올릴 때 쓴다. */
    public fun topLevelAncestor(id: NodeId): NodeId? = ancestry(id).lastOrNull()?.id
}
