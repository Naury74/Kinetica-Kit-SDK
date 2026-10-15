package com.naury.kinetica.core.command

import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument

internal object NodeEdits {
    fun add(document: SceneDocument, command: SceneCommand.AddNode): CommandResult {
        val node = command.node
        val parentId = node.parentId
        val siblings = document.childrenOf(parentId)
        val index = command.index ?: siblings.size
        val error = when {
            document.nodes.containsKey(node.id) -> CommandError.NodeAlreadyExists(node.id)

            (node.content as? NodeContent.Container)?.childIds?.isNotEmpty() == true -> CommandError.StructureChange(node.id)

            else -> parentError(document, parentId)
                ?: CommandError.IndexOutOfRange(index, siblings.size).takeIf { index !in 0..siblings.size }
        }
        if (error != null) return rejected(error)
        val reordered = siblings.toMutableList().apply { add(index, node.id) }
        return applied(document.copy(nodes = document.nodes + (node.id to node)).withChildren(parentId, reordered))
    }

    fun remove(document: SceneDocument, command: SceneCommand.RemoveNodes): CommandResult {
        command.ids.firstOrNull { !document.nodes.containsKey(it) }?.let { return rejected(CommandError.NodeNotFound(it)) }
        val removed = withDescendants(document, command.ids)
        val nodes = document.nodes.filterKeys { it !in removed }.mapValues { (_, node) ->
            val container = node.content as? NodeContent.Container
            if (container != null && container.childIds.any(removed::contains)) {
                node.copy(content = container.withChildren(container.childIds.filterNot(removed::contains)))
            } else {
                node
            }
        }
        val timelines = document.timelines.map { timeline ->
            timeline.copy(tracks = timeline.tracks.filterNot { it.nodeId in removed })
        }
        return applied(document.copy(nodes = nodes, rootOrder = document.rootOrder.filterNot(removed::contains), timelines = timelines))
    }

    fun update(document: SceneDocument, command: SceneCommand.UpdateProperties): CommandResult {
        val node = command.node
        val existing = document.nodes[node.id] ?: return rejected(CommandError.NodeNotFound(node.id))
        val sameChildren = (existing.content as? NodeContent.Container)?.childIds == (node.content as? NodeContent.Container)?.childIds
        if (existing.kind != node.kind || existing.parentId != node.parentId || !sameChildren) {
            return rejected(CommandError.StructureChange(node.id))
        }
        return applied(document.copy(nodes = document.nodes + (node.id to node)))
    }

    fun transform(document: SceneDocument, command: SceneCommand.TransformNodes): CommandResult {
        val updates = command.transforms.map { (id, transform) ->
            val node = document.nodes[id] ?: return rejected(CommandError.NodeNotFound(id))
            id to node.copy(transform = transform)
        }
        return applied(document.copy(nodes = document.nodes + updates))
    }

    fun reorder(document: SceneDocument, command: SceneCommand.ReorderNode): CommandResult {
        val node = document.nodes[command.id] ?: return rejected(CommandError.NodeNotFound(command.id))
        val siblings = document.childrenOf(node.parentId)
        if (command.toIndex !in siblings.indices) return rejected(CommandError.IndexOutOfRange(command.toIndex, siblings.size))
        val reordered = siblings.toMutableList().apply {
            remove(node.id)
            add(command.toIndex, node.id)
        }
        return applied(document.withChildren(node.parentId, reordered))
    }

    private fun parentError(document: SceneDocument, parentId: NodeId?): CommandError? {
        if (parentId == null) return null
        val parent = document.nodes[parentId] ?: return CommandError.NodeNotFound(parentId)
        return if (parent.content is NodeContent.Container) null else CommandError.StructureChange(parentId)
    }

    private fun withDescendants(document: SceneDocument, roots: Set<NodeId>): Set<NodeId> {
        val result = LinkedHashSet<NodeId>()
        val pending = ArrayDeque(roots)
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (result.add(id)) pending.addAll(document.childrenOf(id))
        }
        return result
    }
}

/** [parentId]의 자식 순서를 바꾼 문서를 돌려준다. `null`이면 최상위 순서를 바꾼다. 부모는 그룹이나 Frame 같은 컨테이너여야 한다. */
internal fun SceneDocument.withChildren(parentId: NodeId?, children: List<NodeId>): SceneDocument {
    if (parentId == null) return copy(rootOrder = children)
    val parent = checkNotNull(nodes[parentId]) { "부모 노드가 있어야 합니다." }
    val container = checkNotNull(parent.content as? NodeContent.Container) { "부모는 컨테이너여야 합니다." }
    return copy(nodes = nodes + (parentId to parent.copy(content = container.withChildren(children))))
}
