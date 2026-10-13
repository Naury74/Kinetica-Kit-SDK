package com.naury.kinetica.core.scene

/**
 * 모든 노드가 최상위 순서나 부모 그룹의 자식 목록 중 정확히 한 곳에 있고, 그 위치가 노드의 parentId와 같으며,
 * 최상위에서 순환 없이 도달할 수 있는지 확인한다.
 */
internal fun checkStructure(document: SceneDocument, limits: SceneLimits, sink: ViolationSink) {
    val nodes = document.nodes
    nodes.forEach { (key, node) ->
        if (key != node.id) sink.add(ViolationCode.NodeKeyMismatch, "nodes의 키와 노드 id가 다릅니다.", key)
    }
    val container = collectPlacements(document, sink)
    nodes.values.forEach { node ->
        val parentId = node.parentId
        if (parentId != null) {
            val parent = nodes[parentId]
            when {
                parent == null -> sink.add(ViolationCode.MissingNode, "부모 노드가 없습니다.", node.id)
                parent.content !is NodeContent.Group -> sink.add(ViolationCode.ParentNotGroup, "부모가 그룹이 아닙니다.", node.id)
            }
        }
        if (!container.containsKey(node.id)) {
            sink.add(ViolationCode.UnreachableNode, "노드가 최상위 순서나 부모의 자식 목록에 없습니다.", node.id)
        }
    }
    val depths = measureDepths(document, limits, sink)
    container.keys.filterNot(depths::containsKey).forEach {
        sink.add(ViolationCode.Cycle, "최상위에서 도달할 수 없는 순환 관계에 있습니다.", it)
    }
}

/** 자식 id마다 그 id를 담은 곳(최상위면 `null`)을 돌려준다. */
private fun collectPlacements(document: SceneDocument, sink: ViolationSink): Map<NodeId, NodeId?> {
    val nodes = document.nodes
    val container = HashMap<NodeId, NodeId?>()
    fun place(childId: NodeId, containerId: NodeId?) {
        val child = nodes[childId]
        when {
            child == null -> sink.add(ViolationCode.MissingNode, "자식 목록이 없는 노드를 가리킵니다.", containerId)

            container.containsKey(childId) -> sink.add(ViolationCode.DuplicateChild, "노드가 자식 목록에 두 번 이상 있습니다.", childId)

            else -> {
                container[childId] = containerId
                if (child.parentId != containerId) {
                    sink.add(ViolationCode.ParentMismatch, "parentId가 노드를 담은 자식 목록과 다릅니다.", childId)
                }
            }
        }
    }
    document.rootOrder.forEach { place(it, null) }
    nodes.values.forEach { node -> (node.content as? NodeContent.Group)?.childIds?.forEach { place(it, node.id) } }
    return container
}

/** 최상위에서 너비 우선으로 내려가며 깊이를 잰다. 최상위 노드의 깊이는 1이다. */
private fun measureDepths(document: SceneDocument, limits: SceneLimits, sink: ViolationSink): Map<NodeId, Int> {
    val depths = HashMap<NodeId, Int>()
    val pending = ArrayDeque<Pair<NodeId, Int>>()
    document.rootOrder.filter(document.nodes::containsKey).forEach { pending.addLast(it to 1) }
    var reportedTooDeep = false
    while (pending.isNotEmpty()) {
        val (id, depth) = pending.removeFirst()
        if (depths.containsKey(id)) continue
        depths[id] = depth
        if (depth > limits.maxDepth && !reportedTooDeep) {
            sink.add(ViolationCode.TooDeep, "그룹 중첩이 ${limits.maxDepth}단계를 넘습니다.", id)
            reportedTooDeep = true
        }
        document.childrenOf(id)
            .filter { document.nodes.containsKey(it) && !depths.containsKey(it) }
            .forEach { pending.addLast(it to depth + 1) }
    }
    return depths
}
