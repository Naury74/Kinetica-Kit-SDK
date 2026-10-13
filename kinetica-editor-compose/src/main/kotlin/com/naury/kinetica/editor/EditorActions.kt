package com.naury.kinetica.editor

import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.AlignMode
import com.naury.kinetica.core.scene.ArrangeOps
import com.naury.kinetica.core.scene.DistributeAxis
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.core.scene.TransformOps
import com.naury.kinetica.history.KineticaSession

/**
 * 도구 줄 동작. 명령이 여러 개인 동작은 트랜잭션 하나로 묶어 실행 취소 한 단계로 만든다.
 * 잠긴 노드는 옮기지 않는다.
 */
internal class EditorActions(private val session: KineticaSession) {
    private val selection: List<NodeId> get() = session.selection.value.selectedIds
    private val graph: SceneGraph get() = SceneGraph(session.document.value)

    val canGroup: Boolean get() = selection.size >= 2
    val canUngroup: Boolean
        get() = selection.any { session.document.value.node(it)?.content is NodeContent.Group }

    fun group() {
        val groupId = NodeFactory.newId()
        if (session.commands.execute(SceneCommand.Group(selection, groupId)) is CommandResult.Applied) session.select(listOf(groupId))
    }

    fun ungroup() {
        val groups = selection.filter { session.document.value.node(it)?.content is NodeContent.Group }
        val released = groups.flatMap { session.document.value.childrenOf(it) }
        transaction("ungroup") { groups.forEach { session.commands.execute(SceneCommand.Ungroup(it)) } }
        session.select(released)
    }

    fun align(mode: AlignMode) = move("align", ArrangeOps.align(graph, movable(), mode))

    fun distribute(axis: DistributeAxis) = move("distribute", ArrangeOps.distribute(graph, movable(), axis))

    fun delete() {
        if (selection.isNotEmpty()) session.commands.execute(SceneCommand.RemoveNodes(selection.toSet()))
    }

    /** 고른 노드를 문서 좌표로 [dx]·[dy]만큼 옮긴다. 키보드 화살표 한 번이 실행 취소 한 단계다. */
    fun nudge(dx: Double, dy: Double) = move("nudge", TransformOps.move(graph, movable(), Vec2(dx, dy)))

    /** 고른 노드를 한 칸씩 위(앞)로 옮긴다. 이미 맨 위인 노드는 그대로다. */
    fun bringForward() = reorder(forward = true)

    fun sendBackward() = reorder(forward = false)

    private fun reorder(forward: Boolean) {
        val document = session.document.value
        val byParent = selection.mapNotNull(document::node).groupBy { it.parentId }
        transaction(if (forward) "bring-forward" else "send-backward") {
            byParent.forEach { (parentId, nodes) ->
                val siblings = document.childrenOf(parentId)
                val indices = nodes.map { siblings.indexOf(it.id) }.sorted()
                val ordered = if (forward) indices.reversed() else indices
                var limit = if (forward) siblings.size else -1
                ordered.forEach { index ->
                    val target = if (forward) minOf(index + 1, limit - 1) else maxOf(index - 1, limit + 1)
                    if (target != index) session.commands.execute(SceneCommand.ReorderNode(siblings[index], target))
                    limit = target
                }
            }
        }
    }

    private fun movable(): List<NodeId> = graph.let { graph -> selection.filterNot(graph::isEffectivelyLocked) }

    private fun move(label: String, transforms: Map<NodeId, Transform2D>) {
        if (transforms.isEmpty()) return
        transaction(label) { session.commands.execute(SceneCommand.TransformNodes(transforms)) }
    }

    private inline fun transaction(label: String, block: () -> Unit) {
        if (!session.commands.beginTransaction(label)) return
        block()
        session.commands.commitTransaction()
    }
}
