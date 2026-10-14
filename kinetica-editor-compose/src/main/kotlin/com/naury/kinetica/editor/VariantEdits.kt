package com.naury.kinetica.editor

import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.scene.ComponentVariants
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode

/** 컴포넌트 변형 입력을 명령으로 바꾼다. 입력 하나가 실행 취소 한 단계이며, 받을 수 없으면 `false`다. */
internal class VariantEdits(private val state: EditorState) {
    /** 인스턴스 [id]를 같은 세트의 원본 [mainId] 변형으로 바꾼다. 덮어쓴 값은 이름이 같은 레이어로 옮긴다. */
    fun swapVariant(id: NodeId, mainId: NodeId): Boolean {
        val node = state.document.node(id) ?: return false
        val link = node.instanceOf ?: return false
        if (ComponentVariants.variantsOf(state.document, link.main).none { it.id == mainId }) return false
        return update(node.copy(instanceOf = ComponentVariants.swapTo(state.document, link, mainId)))
    }

    /**
     * 원본 [mainId]를 복사해 같은 세트의 새 변형을 원본 오른쪽에 만들고 고른다. 원본에 세트가 없으면 원본 이름을 `이름/Default`로 바꿔 세트를 만든다.
     * 원본이 아니면 `false`.
     */
    fun addVariant(mainId: NodeId): Boolean {
        val document = state.document
        val main = document.node(mainId)?.takeIf { it.content is NodeContent.Frame }
        val name = main?.component ?: return false
        val set = ComponentVariants.setName(name) ?: name.trim()
        val taken = ComponentVariants.variantsOf(document, mainId).mapNotNull { it.component?.let(ComponentVariants::variantName) }.toSet()
        val variant = generateSequence(taken.size + 1) { it + 1 }.map { "Variant $it" }.first { it !in taken }
        val width = (main.content as NodeContent.Frame).size.width
        val copies = cloneSubtree(document, mainId)
        val rootId = copies.first().id
        val root = copies.first().copy(
            component = "$set/$variant",
            name = "$set/$variant",
            transform = main.transform.copy(translationX = main.transform.translationX + width + VARIANT_GAP),
        )
        return state.transaction("variant") {
            val renamed = ComponentVariants.setName(name) != null ||
                state.execute(SceneCommand.UpdateProperties(main.copy(component = "$set/Default", name = "$set/Default")))
            val added = renamed && state.addSubtree(listOf(root) + copies.drop(1), rootId, main.parentId)
            if (added) state.select(listOf(rootId))
            added
        }
    }

    /** [rootId]와 자손을 새 id로 복사한다(첫 원소가 루트). 인스턴스의 자식은 인스턴스가 스스로 다시 만들므로 복사하지 않는다. */
    private fun cloneSubtree(document: SceneDocument, rootId: NodeId): List<SceneNode> {
        val ids = HashMap<NodeId, NodeId>()
        val result = ArrayList<SceneNode>()
        fun clone(id: NodeId, parent: NodeId?) {
            val node = document.node(id) ?: return
            val newId = NodeFactory.newId().also { ids[id] = it }
            val children = if (node.instanceOf != null) emptyList() else (node.content as? NodeContent.Container)?.childIds.orEmpty()
            result += node.copy(id = newId, parentId = parent, locked = false)
            children.forEach { clone(it, newId) }
        }
        clone(rootId, null)
        return result.map { node ->
            when (val content = node.content) {
                is NodeContent.Frame -> node.copy(
                    content = content.copy(childIds = if (node.instanceOf != null) emptyList() else content.childIds.mapNotNull(ids::get)),
                )

                is NodeContent.Group -> node.copy(content = content.copy(childIds = content.childIds.mapNotNull(ids::get)))

                else -> node
            }
        }
    }

    private fun update(node: SceneNode): Boolean {
        if (state.document.node(node.id) == node) return true
        return state.execute(SceneCommand.UpdateProperties(node))
    }

    private companion object {
        const val VARIANT_GAP = 40.0
    }
}

/** 부모부터 자식 순서로 넣는다. AddNode는 부모가 먼저 있어야 받는다. */
internal fun EditorState.addSubtree(nodes: List<SceneNode>, rootId: NodeId, rootParent: NodeId?): Boolean {
    val byId = nodes.associateBy { it.id }
    fun add(id: NodeId, parent: NodeId?): Boolean {
        val node = byId.getValue(id)
        val children = (node.content as? NodeContent.Container)?.childIds.orEmpty()
        val empty = when (val content = node.content) {
            is NodeContent.Frame -> node.copy(content = content.copy(childIds = emptyList()))
            is NodeContent.Group -> node.copy(content = content.copy(childIds = emptyList()))
            else -> node
        }
        val added = session.commands.execute(SceneCommand.AddNode(empty.copy(parentId = parent))) is CommandResult.Applied
        return added && children.all { add(it, id) }
    }
    return add(rootId, rootParent)
}
