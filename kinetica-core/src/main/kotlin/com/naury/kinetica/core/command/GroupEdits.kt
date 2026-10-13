package com.naury.kinetica.core.command

import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.fromMatrix
import com.naury.kinetica.core.scene.Insets
import com.naury.kinetica.core.scene.LayoutDirection
import com.naury.kinetica.core.scene.Layouts
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

    fun wrapInFrame(document: SceneDocument, command: SceneCommand.WrapInFrame): CommandResult {
        val result = group(document, SceneCommand.Group(command.ids, command.frameId, command.name))
        val grouped = result as? CommandResult.Applied ?: return result
        val nodes = grouped.document.nodes
        val group = nodes.getValue(command.frameId)
        val children = (group.content as NodeContent.Group).childIds
        val bounds = children.associateWith { Layouts.boundsInParent(nodes, it) }
        val padding = command.layout?.padding ?: Insets.Zero
        // 크기가 없는(빈 그룹·한 점 경로) 노드만 감싸면 Frame 크기가 0이 되어 받을 수 없다.
        val union = bounds.values.filterNotNull().reduceOrNull(Rect::union)
            ?.takeIf { it.width + padding.left + padding.right > 0.0 && it.height + padding.top + padding.bottom > 0.0 }
            ?: return rejected(CommandError.StructureChange(command.frameId))
        val originX = union.left - padding.left
        val originY = union.top - padding.top
        val size = Size(union.width + padding.left + padding.right, union.height + padding.top + padding.bottom)
        val ordered = command.layout?.let { layout ->
            children.sortedBy { id ->
                val rect = bounds[id]
                if (layout.direction == LayoutDirection.Horizontal) rect?.left else rect?.top
            }
        } ?: children
        val frame = group.copy(
            content = NodeContent.Frame(size, ordered, clip = false, layout = command.layout),
            transform = Transform2D(translationX = originX, translationY = originY),
        )
        val shifted = children.associateWith { id ->
            val child = nodes.getValue(id)
            child.copy(
                transform = child.transform.copy(
                    translationX = child.transform.translationX - originX,
                    translationY = child.transform.translationY - originY,
                ),
            )
        }
        return applied(grouped.document.copy(nodes = nodes + shifted + (frame.id to frame)))
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
