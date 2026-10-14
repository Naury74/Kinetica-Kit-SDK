package com.naury.kinetica.editor

import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.InstanceOf
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.isValidDataKey
import com.naury.kinetica.presets.UiKit

/** 컴포넌트·인스턴스·디자인 토큰 입력을 명령으로 바꾼다. 입력 하나가 실행 취소 한 단계이며, 받을 수 없는 값이면 `false`다. */
internal class ComponentEdits(private val state: EditorState) {
    val variants = VariantEdits(state)

    /**
     * 고른 노드를 컴포넌트 원본으로 만든다. Frame 하나면 그대로, 아니면 Frame으로 감싼 뒤 원본으로 만든다.
     * 이미 원본·인스턴스이거나 고른 것이 없으면 `false`.
     */
    fun makeComponent(ids: List<NodeId>): Boolean {
        val document = state.document
        val single = ids.singleOrNull()?.let(document::node)
        if (single != null && (single.component != null || single.instanceOf != null)) return false
        val name = "Component ${document.nodes.values.count { it.component != null } + 1}"
        return state.transaction("component") {
            val target = if (single?.content is NodeContent.Frame) {
                single
            } else {
                val frameId = NodeFactory.newId()
                if (ids.isEmpty() ||
                    state.session.commands.execute(SceneCommand.WrapInFrame(ids, frameId)) !is CommandResult.Applied
                ) {
                    return@transaction false
                }
                state.document.node(frameId) ?: return@transaction false
            }
            val ok = state.execute(SceneCommand.UpdateProperties(target.copy(component = name, name = target.name.ifBlank { name })))
            if (ok) state.select(listOf(target.id))
            ok
        }
    }

    fun rename(id: NodeId, name: String): Boolean {
        val node = state.document.node(id)?.takeIf { it.component != null } ?: return false
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return false
        return update(node.copy(component = trimmed, name = trimmed))
    }

    /** 원본 [mainId]의 인스턴스를 [center]에 넣고 고른다. */
    fun insertInstance(mainId: NodeId, center: Vec2): Boolean {
        val main = state.document.node(mainId)?.takeIf { it.component != null } ?: return false
        val frame = main.content as? NodeContent.Frame ?: return false
        val instance = SceneNode(
            id = NodeFactory.newId(),
            content = frame.copy(childIds = emptyList()),
            name = main.component.orEmpty(),
            transform = Transform2D(translationX = center.x - frame.size.width / 2.0, translationY = center.y - frame.size.height / 2.0),
            instanceOf = InstanceOf(mainId),
        )
        val added = state.session.commands.execute(SceneCommand.AddNode(instance)) is CommandResult.Applied
        if (added) state.select(listOf(instance.id))
        return added
    }

    fun resetOverrides(id: NodeId): Boolean {
        val node = state.document.node(id) ?: return false
        val link = node.instanceOf ?: return false
        return update(node.copy(instanceOf = link.copy(overrides = emptyMap())))
    }

    /** 인스턴스를 원본과 끊어 지금 모습 그대로의 일반 Frame으로 만든다. */
    fun detach(id: NodeId): Boolean {
        val node = state.document.node(id)?.takeIf { it.instanceOf != null } ?: return false
        return update(node.copy(instanceOf = null))
    }

    fun selectMain(id: NodeId): Boolean {
        val main = state.document.node(id)?.instanceOf?.main ?: return false
        state.document.node(main) ?: return false
        state.select(listOf(main))
        return true
    }

    /** 토큰 [name]을 `#RRGGBB`·`#AARRGGBB` 색으로 정한다. 새 이름이면 더한다. */
    fun setToken(name: String, text: String): Boolean {
        val key = name.trim()
        val color = parseColor(text) ?: return false
        if (!isValidDataKey(key)) return false
        return state.session.commands.execute(SceneCommand.SetToken(key, color)) is CommandResult.Applied
    }

    fun removeToken(name: String): Boolean = state.session.commands.execute(SceneCommand.SetToken(name, null)) is CommandResult.Applied

    /** 채우기를 토큰 [name]에 잇는다. `null`이면 연결을 끊고 지금 색을 둔다. */
    fun linkFill(id: NodeId, name: String?): Boolean = state.editStyle(id) { style ->
        if (name ==
            null
        ) {
            style.copy(fillToken = null)
        } else {
            state.document.tokens[name]?.let { style.copy(fill = it, gradient = null, fillToken = name) }
        }
    }

    /** 기본 컴포넌트 키트를 artboard 오른쪽 밖에 넣는다. 이미 넣었으면 `false`. */
    fun insertStarterKit(): Boolean {
        val document = state.document
        if (document.node(NodeId("$KIT_PREFIX-button")) != null) return false
        val kit = UiKit.starter(Vec2(document.artboard.width + KIT_MARGIN, 0.0), KIT_PREFIX, document.tokens)
        return state.transaction("kit") {
            kit.tokens.filterKeys { it !in document.tokens }.all { (name, color) ->
                state.session.commands.execute(SceneCommand.SetToken(name, color)) is CommandResult.Applied
            } && kit.roots.all { root -> state.addSubtree(kit.nodes, root, null) }
        }
    }

    private fun update(node: SceneNode): Boolean {
        if (state.document.node(node.id) == node) return true
        return state.execute(SceneCommand.UpdateProperties(node))
    }

    private companion object {
        const val KIT_PREFIX = "kit"
        const val KIT_MARGIN = 200.0
    }
}
