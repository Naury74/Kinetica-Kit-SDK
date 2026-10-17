package com.naury.kinetica.history

import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.SceneTransitionSpec

/**
 * 두 문서 사이에서 바뀐 부분만 담는다. 바뀌지 않은 노드는 참조가 같아 비교가 빠르고, 기록 크기는 바뀐 노드 수에 비례한다.
 *
 * 노드 값이 `null`이면 그쪽 문서에 노드가 없다는 뜻이다.
 */
internal class SceneDiff private constructor(
    private val nodesBefore: Map<NodeId, SceneNode?>,
    private val nodesAfter: Map<NodeId, SceneNode?>,
    private val rootOrder: Change<List<NodeId>>?,
    private val timelines: Change<List<Timeline>>?,
    private val header: Change<Header>?,
) {
    val changedNodeCount: Int get() = nodesAfter.size

    val isEmpty: Boolean
        get() = nodesAfter.isEmpty() && rootOrder == null && timelines == null && header == null

    fun redo(document: SceneDocument): SceneDocument = apply(document, nodesAfter, forward = true)

    fun undo(document: SceneDocument): SceneDocument = apply(document, nodesBefore, forward = false)

    private fun apply(document: SceneDocument, nodes: Map<NodeId, SceneNode?>, forward: Boolean): SceneDocument {
        val merged = LinkedHashMap(document.nodes)
        nodes.forEach { (id, node) -> if (node == null) merged.remove(id) else merged[id] = node }
        val picked = header?.pick(forward) ?: Header.of(document)
        return document.copy(
            nodes = merged,
            rootOrder = rootOrder?.pick(forward) ?: document.rootOrder,
            artboard = picked.artboard,
            timelines = timelines?.pick(forward) ?: document.timelines,
            extensions = picked.extensions,
            tokens = picked.tokens,
            transition = picked.transition,
        )
    }

    /** 노드 밖에 있는 문서 값. 자주 바뀌지 않으므로 하나로 묶어 비교한다. */
    private data class Header(
        val artboard: Artboard,
        val extensions: Map<String, String>,
        val tokens: Map<String, ArgbColor>,
        val transition: SceneTransitionSpec?,
    ) {
        companion object {
            fun of(document: SceneDocument) = Header(document.artboard, document.extensions, document.tokens, document.transition)
        }
    }

    private class Change<T>(val before: T, val after: T) {
        fun pick(forward: Boolean): T = if (forward) after else before
    }

    companion object {
        fun between(before: SceneDocument, after: SceneDocument): SceneDiff {
            val nodesBefore = HashMap<NodeId, SceneNode?>()
            val nodesAfter = HashMap<NodeId, SceneNode?>()
            (before.nodes.keys + after.nodes.keys).forEach { id ->
                val old = before.nodes[id]
                val new = after.nodes[id]
                if (old !== new && old != new) {
                    nodesBefore[id] = old
                    nodesAfter[id] = new
                }
            }
            return SceneDiff(
                nodesBefore = nodesBefore,
                nodesAfter = nodesAfter,
                rootOrder = change(before.rootOrder, after.rootOrder),
                timelines = change(before.timelines, after.timelines),
                header = change(Header.of(before), Header.of(after)),
            )
        }

        private fun <T> change(before: T, after: T): Change<T>? = if (before == after) null else Change(before, after)
    }
}
