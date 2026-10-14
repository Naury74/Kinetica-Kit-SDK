package com.naury.kinetica.core.scene

/**
 * 컴포넌트 인스턴스. [main]은 [SceneNode.component]가 있는 원본 Frame이다.
 *
 * @property overrides 원본 자손 id(원본 Frame 자신 포함) → 이 인스턴스에서 덮어쓴 값
 */
public data class InstanceOf(val main: NodeId, val overrides: Map<NodeId, Override> = emptyMap())

/** 인스턴스에서 덮어쓴 값. `null`인 값은 원본을 따른다. */
public data class Override(
    val text: String? = null,
    val fill: ArgbColor? = null,
    val visible: Boolean? = null,
    val imageUri: String? = null,
) {
    internal val isEmpty: Boolean get() = text == null && fill == null && visible == null && imageUri == null
}

/**
 * 컴포넌트 원본과 인스턴스를 맞추고, 인스턴스 자식에서 고친 값을 덮어쓴 값으로 남기는 순수 함수 모음.
 * [com.naury.kinetica.core.command.SceneReducer]가 명령마다 부른다.
 */
public object Components {
    /**
     * 인스턴스마다 원본 자손을 복사해 자식을 다시 만든다. 위치·크기·모양은 원본을 따르고 덮어쓴 값만 인스턴스 것이다.
     * 인스턴스 자신의 위치·크기는 그대로다. 원본이 없어진 인스턴스는 지금 자식을 둔 채 일반 Frame이 된다.
     * 원본 안에 다른 컴포넌트 인스턴스가 있으면 안쪽부터 맞추도록 깊이만큼 반복한다.
     */
    public fun sync(document: SceneDocument): SceneDocument {
        if (document.nodes.values.none { it.instanceOf != null }) return document
        var current = document
        repeat(MAX_NESTING) {
            val next = syncOnce(current)
            if (next == current) return current
            current = next
        }
        return current
    }

    /** 인스턴스 [instanceId] 안에서 원본 [sourceId]를 복사한 자식의 id. 같은 입력이면 늘 같다. */
    public fun childId(instanceId: NodeId, sourceId: NodeId): NodeId {
        var hash = FNV_OFFSET
        "${instanceId.value}|${sourceId.value}".forEach { char ->
            hash = hash xor char.code.toLong()
            hash *= FNV_PRIME
        }
        return NodeId("i" + java.lang.Long.toHexString(hash).padStart(HASH_DIGITS, '0'))
    }

    /**
     * 명령 전후를 비교해, 인스턴스 자식에서 바뀐 글자·채우기·보임·이미지를 그 인스턴스의 덮어쓴 값으로 옮긴다.
     * 원본과 같은 값으로 되돌리면 덮어쓴 값을 지운다.
     */
    internal fun captureOverrides(before: SceneDocument, after: SceneDocument): SceneDocument {
        val updated = after.nodes.values.filter { it.instanceOf != null }.mapNotNull { instance ->
            captured(instance, before, after)?.let { instance.id to instance.copy(instanceOf = it) }
        }
        return if (updated.isEmpty()) after else after.copy(nodes = after.nodes + updated)
    }

    /** [instance] 자식에서 바뀐 값을 담은 새 연결. 바뀐 것이 없으면 `null`. */
    private fun captured(instance: SceneNode, before: SceneDocument, after: SceneDocument): InstanceOf? {
        val link = instance.instanceOf ?: return null
        val main = after.nodes[link.main] ?: return null
        var overrides = link.overrides
        sourceIds(after, main.id).forEach { sourceId ->
            val id = if (sourceId == main.id) instance.id else childId(instance.id, sourceId)
            val now = after.nodes[id]
            val was = before.nodes[id]
            if (now == null || was == null || now == was) return@forEach
            val override = overrideOf(now, after.nodes.getValue(sourceId), overrides[sourceId] ?: Override(), was)
            overrides = if (override.isEmpty) overrides - sourceId else overrides + (sourceId to override)
        }
        return link.copy(overrides = overrides).takeIf { overrides != link.overrides }
    }

    /** 바뀐 값만 덮어쓴 값에 반영한다. 원본과 같아지면 그 값은 덮어쓰지 않는다. */
    private fun overrideOf(now: SceneNode, source: SceneNode, current: Override, was: SceneNode): Override {
        var result = current
        val text = (now.content as? NodeContent.Text)?.text
        if (text != (was.content as? NodeContent.Text)?.text) {
            result = result.copy(text = text.takeIf { it != (source.content as? NodeContent.Text)?.text })
        }
        if (now.style.fill != was.style.fill) result = result.copy(fill = now.style.fill.takeIf { it != source.style.fill })
        if (now.visible != was.visible) result = result.copy(visible = now.visible.takeIf { it != source.visible })
        val uri = (now.content as? NodeContent.Image)?.uri
        if (uri != (was.content as? NodeContent.Image)?.uri) {
            result = result.copy(imageUri = uri.takeIf { it != (source.content as? NodeContent.Image)?.uri })
        }
        return result
    }

    private fun syncOnce(document: SceneDocument): SceneDocument {
        val nodes = LinkedHashMap(document.nodes)
        document.nodes.values.filter { it.instanceOf != null }.forEach { snapshot ->
            // 바깥 인스턴스가 이번 차례에 이 노드를 새로 복사했을 수 있으므로(안쪽 인스턴스의 원본 바뀜) 지금 값을 쓴다.
            val instance = nodes[snapshot.id]?.takeIf { it.instanceOf != null } ?: return@forEach
            val link = requireNotNull(instance.instanceOf)
            val main = document.nodes[link.main]
            val mainFrame = main?.content as? NodeContent.Frame
            if (main?.component == null || mainFrame == null || isInside(document, instance.id, main.id)) {
                // 원본이 없거나 컴포넌트가 아니면 지금 모습 그대로 일반 Frame이 된다.
                nodes[instance.id] = instance.copy(instanceOf = null, bindings = instance.bindings - BoundProperty.Variant)
                return@forEach
            }
            val copies = copySubtree(document, main, instance, link.overrides)
            val frame = instance.content as? NodeContent.Frame ?: return@forEach
            val old = descendants(document, instance.id)
            old.filterNot(copies::containsKey).forEach(nodes::remove)
            nodes.putAll(copies)
            // 인스턴스 크기가 원본과 다르면 자식은 원본 크기 기준 제약대로 따라간다. 자동 배치면 배치가 정한다.
            if (mainFrame.layout == null && mainFrame.size != frame.size) {
                Layouts.follow(nodes, mainFrame.childIds.map { childId(instance.id, it) }, mainFrame.size, frame.size)
            }
            val rootOverride = link.overrides[main.id]
            nodes[instance.id] = instance.copy(
                content = mainFrame.copy(size = frame.size, childIds = mainFrame.childIds.map { childId(instance.id, it) }),
                style = main.style.withOverride(rootOverride),
                visible = rootOverride?.visible ?: instance.visible,
            )
        }
        return document.copy(nodes = nodes)
    }

    /** 원본의 자손을 인스턴스 아래로 복사한다. 원본 루트는 빼고 그 아래만이다. */
    private fun copySubtree(
        document: SceneDocument,
        main: SceneNode,
        instance: SceneNode,
        overrides: Map<NodeId, Override>,
    ): Map<NodeId, SceneNode> {
        val copies = LinkedHashMap<NodeId, SceneNode>()
        fun copy(sourceId: NodeId, parent: NodeId) {
            val source = document.nodes[sourceId] ?: return
            val id = childId(instance.id, sourceId)
            val override = overrides[sourceId]
            val content = copiedContent(source.content, instance.id, override)
            // 원본 안의 인스턴스는 자기 자식을 스스로 맞추므로, 그 자식 대신 원본 인스턴스의 정보만 옮긴다.
            copies[id] = source.copy(
                id = id,
                parentId = parent,
                content = content,
                style = source.style.withOverride(override),
                visible = override?.visible ?: source.visible,
                component = null,
                locked = false,
            )
            (source.content as? NodeContent.Container)?.childIds?.forEach { copy(it, id) }
        }
        (main.content as NodeContent.Frame).childIds.forEach { copy(it, instance.id) }
        return copies
    }

    private fun copiedContent(content: NodeContent, instanceId: NodeId, override: Override?): NodeContent = when (content) {
        is NodeContent.Group -> content.copy(childIds = content.childIds.map { childId(instanceId, it) })
        is NodeContent.Frame -> content.copy(childIds = content.childIds.map { childId(instanceId, it) })
        is NodeContent.Text -> override?.text?.let { content.copy(text = it, spans = TextSpans.clamp(content.spans, it.length)) } ?: content
        is NodeContent.Image -> override?.imageUri?.let { content.copy(uri = it) } ?: content
        else -> content
    }

    private fun NodeStyle.withOverride(override: Override?): NodeStyle =
        override?.fill?.let { copy(fill = it, gradient = null, fillToken = null) } ?: this

    /** 원본 자신과 모든 자손 id. */
    private fun sourceIds(document: SceneDocument, mainId: NodeId): List<NodeId> = listOf(mainId) + descendants(document, mainId)

    private fun descendants(document: SceneDocument, id: NodeId): List<NodeId> {
        val result = ArrayList<NodeId>()
        val pending = ArrayDeque(document.childrenOf(id))
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            if (document.nodes.containsKey(next) && result.size < document.nodes.size) {
                result += next
                pending.addAll(document.childrenOf(next))
            }
        }
        return result
    }

    /** [nodeId]가 [ancestorId] 자신이거나 그 안에 있는지. 원본 안에 자기 인스턴스를 넣는 순환을 막는다. */
    private fun isInside(document: SceneDocument, nodeId: NodeId, ancestorId: NodeId): Boolean =
        generateSequence(document.nodes[nodeId]) { node -> node.parentId?.let(document.nodes::get) }.take(document.nodes.size + 1).any {
            it.id ==
                ancestorId
        }

    private const val MAX_NESTING = 8
    private const val FNV_OFFSET = -0x340d631b7bdddcdbL
    private const val FNV_PRIME = 0x100000001b3L
    private const val HASH_DIGITS = 16
}

/** 디자인 토큰을 노드 색에 반영하는 순수 함수 모음. */
public object Tokens {
    /** 토큰에 연결된 채우기·선 색을 토큰 색으로 맞춘다. 문서에 없는 토큰이면 지금 색을 둔다. */
    public fun resolve(document: SceneDocument): SceneDocument {
        if (document.nodes.values.none { it.style.fillToken != null || it.style.strokeToken != null }) return document
        val nodes = document.nodes.mapValues { (_, node) ->
            val style = node.style
            val fill = style.fillToken?.let(document.tokens::get)
            val stroke = style.strokeToken?.let(document.tokens::get)
            val fillCurrent = fill == null || (fill == style.fill && style.gradient == null)
            val strokeCurrent = stroke == null || stroke == style.stroke
            if (fillCurrent && strokeCurrent) {
                node
            } else {
                node.copy(
                    style = style.copy(
                        fill = fill ?: style.fill,
                        gradient = if (fill !=
                            null
                        ) {
                            null
                        } else {
                            style.gradient
                        },
                        stroke = stroke ?: style.stroke,
                    ),
                )
            }
        }
        return if (nodes == document.nodes) document else document.copy(nodes = nodes)
    }

    /** 명령이 토큰에 연결된 색을 직접 바꿨으면 그 노드의 연결을 끊는다(토큰 값으로 되돌리지 않게). */
    internal fun unlinkEdited(before: SceneDocument, after: SceneDocument): SceneDocument {
        var changed = false
        val nodes = after.nodes.mapValues { (id, node) ->
            val was = before.nodes[id] ?: return@mapValues node
            val style = node.style
            val fillEdited = style.fillToken != null && style.fillToken == was.style.fillToken &&
                (style.fill != was.style.fill || style.gradient != was.style.gradient) && before.tokens == after.tokens
            val strokeEdited = style.strokeToken != null && style.strokeToken == was.style.strokeToken &&
                style.stroke != was.style.stroke && before.tokens == after.tokens
            if (!fillEdited && !strokeEdited) return@mapValues node
            changed = true
            node.copy(
                style = style.copy(
                    fillToken = style.fillToken.takeUnless {
                        fillEdited
                    },
                    strokeToken = style.strokeToken.takeUnless { strokeEdited },
                ),
            )
        }
        return if (changed) after.copy(nodes = nodes) else after
    }
}
