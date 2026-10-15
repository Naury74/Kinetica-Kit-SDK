@file:JvmName("BoardStacks")

package com.naury.kinetica.board

import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val STACK_KEY = "kinetica.stack"
private const val MEMBER_ID = "id"
private const val MEMBER_KEY = "key"

internal fun SceneNode.stackMembers(): List<StackMember> = metadata[STACK_KEY]?.let { raw ->
    runCatching {
        Json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
            val item = element.jsonObject
            val id = item[MEMBER_ID]?.jsonPrimitive?.contentOrNull
            val key = item[MEMBER_KEY]?.jsonPrimitive?.contentOrNull
            if (id != null && key != null) StackMember(id, key) else null
        }
    }.getOrNull()
}.orEmpty()

private fun stackMetadata(members: List<StackMember>): Map<String, String> = mapOf(
    STACK_KEY to
        JsonArray(members.map { JsonObject(mapOf(MEMBER_ID to JsonPrimitive(it.id), MEMBER_KEY to JsonPrimitive(it.key))) }).toString(),
)

/** 저장본을 열 때 목록에 없는 위젯을 스택에서 제거한다. 하나만 남으면 일반 위젯 노드를, 하나도 없으면 `null`을 돌려준다. */
internal fun reconcileStack(node: SceneNode, keys: Set<String>): SceneNode? {
    val members = node.stackMembers().filter { it.key in keys }
    return when (members.size) {
        0 -> null

        1 -> members.single().let {
            node.copy(id = NodeId(it.id), content = slot(node, it.id), metadata = KineticaBoardState.widgetMetadata(it.id, it.key))
        }

        else -> node.copy(metadata = stackMetadata(members))
    }
}

private fun slot(node: SceneNode, key: String): NodeContent.HostSlot = (node.content as NodeContent.HostSlot).copy(key = key)

/**
 * 슬롯 [id]가 스택이면 그 안의 슬롯 id를 보이는 순서대로 돌려준다. 스택이 아니면 빈 목록이다.
 */
public fun KineticaBoardState.stackMembers(id: String): List<String> =
    NodeId.parseOrNull(id)?.let(document.value::node)?.stackMembers()?.map { it.id }.orEmpty()

/**
 * [itemId] 위젯을 [targetId] 위젯 위에 겹쳐 스택으로 만든다. 휴대폰 홈 화면의 위젯 스택처럼 한 위치에서 넘겨 본다.
 * [targetId]가 이미 스택이면 맨 뒤에 추가한다. 스택은 [targetId]의 위치와 크기를 그대로 쓰고 `stack.N` id를 받는다. 실행 취소 한 단계다.
 *
 * @return 스택 id. 둘 중 하나가 놓여 있지 않거나 같은 슬롯이면 `null`
 */
public fun KineticaBoardState.stack(targetId: String, itemId: String): String? {
    val document = document.value
    val target = NodeId.parseOrNull(targetId)?.let(document::node)
    val item = NodeId.parseOrNull(itemId)?.let(document::node)
    if (target == null || item == null || target.id == item.id) return null
    fun membersOf(node: SceneNode) =
        node.stackMembers().ifEmpty { listOf(StackMember(node.id.value, KineticaBoardState.widgetKeyOf(node))) }
    val members = membersOf(target) + membersOf(item)
    val stackId = target.id.value.takeIf { target.stackMembers().isNotEmpty() }
        ?: generateSequence(1) { it + 1 }.map { "stack.$it" }.first { document.node(NodeId(it)) == null }
    val stackNode = target.copy(id = NodeId(stackId), content = slot(target, stackId), metadata = stackMetadata(members), name = "")
    // 둘을 지운 뒤 원래 대상이 있던 그리기 순서에 넣는다.
    val index = document.rootOrder.takeWhile { it != target.id }.count { it != item.id }
    if (!commands.beginTransaction("stack")) return null
    val ok =
        commands.execute(
            SceneCommand.RemoveNodes(
                setOfNotNull(
                    item.id,
                    target.id.takeIf {
                        it.value != stackId
                    },
                ),
            ),
        ) is CommandResult.Applied &&
            (stackId == target.id.value || commands.execute(SceneCommand.AddNode(stackNode, index)) is CommandResult.Applied) &&
            (stackId != target.id.value || commands.execute(SceneCommand.UpdateProperties(stackNode)) is CommandResult.Applied)
    if (ok) commands.commitTransaction() else commands.rollbackTransaction()
    return stackId.takeIf { ok }
}

/**
 * 스택 [stackId]를 풀어 위젯을 하나씩 따로 놓는다. 첫 위젯은 스택이 있던 위치에, 나머지는 빈 위치에 놓는다. 실행 취소 한 단계다.
 */
public fun KineticaBoardState.unstack(stackId: String): Boolean {
    val start = document.value
    val stackNode = NodeId.parseOrNull(stackId)?.let(start::node)
    val members = stackNode?.stackMembers().orEmpty()
    if (stackNode == null || members.isEmpty() || !commands.beginTransaction("unstack")) return false
    val first = members.first()
    val firstNode = stackNode.copy(
        id = NodeId(first.id),
        content = slot(stackNode, first.id),
        metadata = KineticaBoardState.widgetMetadata(first.id, first.key),
    )
    var ok = commands.execute(SceneCommand.RemoveNodes(setOf(stackNode.id))) is CommandResult.Applied &&
        commands.execute(SceneCommand.AddNode(firstNode, start.rootOrder.indexOf(stackNode.id))) is CommandResult.Applied
    members.drop(1).forEach { member ->
        val widget = catalog.firstOrNull { it.key == member.key }
        val node = widget?.let { placementFor(it, document.value, member.id) }
            ?: firstNode.copy(id = NodeId(member.id), content = slot(stackNode, member.id).copy(size = Size(1.0, 1.0)))
        ok =
            ok &&
            commands.execute(
                SceneCommand.AddNode(node.copy(metadata = KineticaBoardState.widgetMetadata(member.id, member.key))),
            ) is CommandResult.Applied
    }
    if (ok) commands.commitTransaction() else commands.rollbackTransaction()
    return ok
}
