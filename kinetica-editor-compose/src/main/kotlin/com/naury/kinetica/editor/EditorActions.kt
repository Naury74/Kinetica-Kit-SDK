package com.naury.kinetica.editor

import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.AlignMode
import com.naury.kinetica.core.scene.ArrangeOps
import com.naury.kinetica.core.scene.AutoLayout
import com.naury.kinetica.core.scene.DistributeAxis
import com.naury.kinetica.core.scene.Insets
import com.naury.kinetica.core.scene.LayoutDirection
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.core.scene.TransformOps
import com.naury.kinetica.history.KineticaSession

/**
 * 툴바 동작. 명령이 여러 개인 동작은 트랜잭션 하나로 묶어 실행 취소 한 단계로 만든다.
 * 잠긴 노드는 옮기지 않는다.
 */
internal class EditorActions(private val state: EditorState) {
    private val session: KineticaSession get() = state.session
    private val selection: List<NodeId> get() = session.selection.value.selectedIds
    private val graph: SceneGraph get() = SceneGraph(state.document)

    val canGroup: Boolean get() = selection.size >= 2
    val canUngroup: Boolean
        get() = selection.any { session.document.value.node(it)?.content is NodeContent.Group }

    fun group() {
        val groupId = NodeFactory.newId()
        if (session.commands.execute(SceneCommand.Group(selection, groupId)) is CommandResult.Applied) session.select(listOf(groupId))
    }

    /** 선택한 노드를 세로 자동 레이아웃 Frame으로 감싼다. 크기는 콘텐츠에 맞추고 간격과 패딩은 16이다. 노드가 가로로 더 넓게 퍼져 있으면 가로 방향으로 만든다. */
    fun wrapInAutoLayout() {
        val bounds = selection.mapNotNull(graph::worldBounds)
        val wide =
            bounds.isNotEmpty() &&
                (bounds.maxOf { it.right } - bounds.minOf { it.left }) > (bounds.maxOf { it.bottom } - bounds.minOf { it.top })
        val layout = AutoLayout(
            direction = if (wide) LayoutDirection.Horizontal else LayoutDirection.Vertical,
            gap = DEFAULT_SPACING,
            padding = Insets.all(DEFAULT_SPACING),
            hugWidth = true,
            hugHeight = true,
        )
        val frameId = NodeFactory.newId()
        if (session.commands.execute(SceneCommand.WrapInFrame(selection, frameId, layout)) is CommandResult.Applied) {
            session.select(listOf(frameId))
        }
    }

    fun ungroup() {
        val groups = selection.filter { session.document.value.node(it)?.content is NodeContent.Group }
        val released = groups.flatMap { session.document.value.childrenOf(it) }
        state.transaction("ungroup") { groups.all { session.commands.execute(SceneCommand.Ungroup(it)) is CommandResult.Applied } }
        session.select(released)
    }

    fun align(mode: AlignMode) = move("align", ArrangeOps.align(graph, movable(), mode))

    fun distribute(axis: DistributeAxis) = move("distribute", ArrangeOps.distribute(graph, movable(), axis))

    fun delete() {
        if (selection.isNotEmpty()) session.commands.execute(SceneCommand.RemoveNodes(selection.toSet()))
    }

    /** 선택한 노드를 문서 좌표로 [dx], [dy]만큼 옮긴다. 방향키 한 번이 실행 취소 한 단계다. */
    fun nudge(dx: Double, dy: Double) = move("nudge", TransformOps.move(graph, movable(), Vec2(dx, dy)))

    /** 선택한 노드를 한 단계 앞으로 가져온다. 이미 맨 앞인 노드는 그대로 둔다. */
    fun bringForward() = reorder(forward = true)

    fun sendBackward() = reorder(forward = false)

    private fun reorder(forward: Boolean) {
        val document = session.document.value
        val byParent = selection.mapNotNull(document::node).groupBy { it.parentId }
        state.transaction(if (forward) "bring-forward" else "send-backward") {
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
            true
        }
    }

    private fun movable(): List<NodeId> = graph.let { graph -> selection.filterNot(graph::isEffectivelyLocked) }

    private fun move(label: String, transforms: Map<NodeId, Transform2D>) {
        if (transforms.isEmpty()) return
        state.transaction(label) { state.execute(SceneCommand.TransformNodes(transforms)) }
    }

    private companion object {
        const val DEFAULT_SPACING = 16.0
    }
}
