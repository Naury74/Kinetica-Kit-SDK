package com.naury.kinetica.core.scene

/**
 * 컴포넌트 변형. 컴포넌트 이름이 `세트/변형`인 원본들은 같은 세트의 변형이다(예: `버튼/기본`, `버튼/눌림`, `버튼/꺼짐`).
 * 이름의 마지막 `/` 앞이 세트, 뒤가 변형 이름이며 앞뒤 공백은 무시한다. `/`가 없으면 세트 없이 혼자인 컴포넌트다.
 *
 * 인스턴스를 같은 세트의 다른 변형으로 바꾸면([swap]) 덮어쓴 값은 원본 안에서 레이어 이름 경로가 같은 노드로 옮긴다.
 * 인스턴스에 [BoundProperty.Variant]를 묶으면 호스트 데이터의 글자(변형 이름)로 고른다.
 */
public object ComponentVariants {
    /** 컴포넌트 이름 [component]의 세트 이름. 세트가 없으면 `null`. */
    public fun setName(component: String): String? = component.substringBeforeLast('/', "").trim().takeIf { it.isNotEmpty() }

    /** 컴포넌트 이름 [component]의 변형 이름. 세트가 없으면 이름 전체다. */
    public fun variantName(component: String): String = component.substringAfterLast('/').trim()

    /** 원본 [mainId]와 같은 세트의 원본들(자신 포함, 문서 순서). 세트가 없으면 자신만, 원본이 아니면 빈 목록이다. */
    public fun variantsOf(document: SceneDocument, mainId: NodeId): List<SceneNode> {
        val main = document.node(mainId) ?: return emptyList()
        val name = main.component ?: return emptyList()
        val set = setName(name) ?: return listOf(main)
        return document.nodes.values.filter { node -> node.component?.let(::setName) == set }
    }

    /**
     * 인스턴스 [instanceId]를 같은 세트의 [variant] 변형으로 바꾼 연결. 덮어쓴 값은 이름 경로가 같은 노드로 옮기고 못 찾으면 버린다.
     * 인스턴스가 아니거나 그 이름의 변형이 없으면 `null`. 이미 그 변형이면 지금 연결을 그대로 준다.
     */
    public fun swap(document: SceneDocument, instanceId: NodeId, variant: String): InstanceOf? {
        val link = document.node(instanceId)?.instanceOf ?: return null
        val wanted = variant.trim()
        val target = variantsOf(document, link.main).firstOrNull { it.component?.let(::variantName) == wanted } ?: return null
        return swapTo(document, link, target.id)
    }

    /** [link]의 원본을 [mainId]로 바꾼 연결. 덮어쓴 값은 이름 경로로 옮긴다. */
    public fun swapTo(document: SceneDocument, link: InstanceOf, mainId: NodeId): InstanceOf {
        if (link.main == mainId) return link
        val targetPaths = paths(document, mainId)
        val sourcePaths = paths(document, link.main).entries.associate { (id, path) -> id to path }
        val byPath = targetPaths.entries.groupBy({ it.value }, { it.key }).mapValues { it.value.first() }
        val overrides = link.overrides.mapNotNull { (sourceId, override) ->
            sourcePaths[sourceId]?.let(byPath::get)?.let { it to override }
        }.toMap()
        return InstanceOf(mainId, overrides)
    }

    /** 원본 자신(빈 경로)과 자손마다 원본에서 그 노드까지의 레이어 이름 경로. */
    private fun paths(document: SceneDocument, mainId: NodeId): Map<NodeId, List<String>> {
        val result = LinkedHashMap<NodeId, List<String>>()
        result[mainId] = emptyList()
        val pending = ArrayDeque(listOf(mainId))
        while (pending.isNotEmpty() && result.size <= document.nodes.size) {
            val parent = pending.removeFirst()
            val parentPath = result.getValue(parent)
            val seen = HashMap<String, Int>()
            document.childrenOf(parent).forEach { childId ->
                val child = document.node(childId) ?: return@forEach
                if (childId in result) return@forEach
                // 이름이 같은 형제는 차례 번호로 구별한다(새 레이어는 종류 이름을 그대로 쓰는 일이 많다).
                val index = seen.merge(child.name, 1, Int::plus)!! - 1
                result[childId] = parentPath + "${child.name}#$index"
                pending += childId
            }
        }
        return result
    }
}
