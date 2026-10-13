package com.naury.kinetica.core.command

import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.fromMatrix
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode

internal object GroupEdits {
    fun group(document: SceneDocument, command: SceneCommand.Group): CommandResult {
        val members = command.ids.distinct()
        val missing = members.firstOrNull { !document.nodes.containsKey(it) }
        val parents = members.mapNotNull(document.nodes::get).map { it.parentId }.distinct()
        val error = when {
            missing != null -> CommandError.NodeNotFound(missing)
            document.nodes.containsKey(command.groupId) -> CommandError.NodeAlreadyExists(command.groupId)
            parents.size != 1 -> CommandError.MixedParents(members)
            else -> null
        }
        if (error != null) return rejected(error)
        val parentId = parents.single()
        val siblings = document.childrenOf(parentId)
        val ordered = siblings.filter { it in members }
        val insertAt = siblings.indexOf(ordered.last()) - (ordered.size - 1)
        val group = SceneNode(
            id = command.groupId,
            content = NodeContent.Group(ordered),
            parentId = parentId,
            name = command.name,
        )
        val nodes = document.nodes + ordered.associateWith { document.nodes.getValue(it).copy(parentId = command.groupId) } +
            (group.id to group)
        val remaining = siblings.filterNot { it in members }.toMutableList().apply { add(insertAt, group.id) }
        return applied(document.copy(nodes = nodes).withChildren(parentId, remaining))
    }

    fun ungroup(document: SceneDocument, command: SceneCommand.Ungroup): CommandResult {
        val group = document.nodes[command.groupId] ?: return rejected(CommandError.NodeNotFound(command.groupId))
        val content = group.content as? NodeContent.Group ?: return rejected(CommandError.StructureChange(command.groupId))
        val groupMatrix = group.transform.matrix()
        val children = content.childIds.map { id ->
            val child = document.nodes.getValue(id)
            val transform = Transform2D.fromMatrix(groupMatrix * child.transform.matrix(), child.transform.pivotX, child.transform.pivotY)
                ?: return rejected(CommandError.UnrepresentableTransform(id))
            child.copy(
                parentId = group.parentId,
                transform = transform,
                style = child.style.copy(opacity = child.style.opacity * group.style.opacity),
                visible = child.visible && group.visible,
                locked = child.locked || group.locked,
            )
        }
        val siblings = document.childrenOf(group.parentId).toMutableList()
        val index = siblings.indexOf(group.id)
        siblings.removeAt(index)
        siblings.addAll(index, content.childIds)
        val nodes = document.nodes - group.id + children.associateBy { it.id }
        val timelines = document.timelines.map { timeline -> timeline.copy(tracks = timeline.tracks.filterNot { it.nodeId == group.id }) }
        return applied(document.copy(nodes = nodes, timelines = timelines).withChildren(group.parentId, siblings))
    }
}
