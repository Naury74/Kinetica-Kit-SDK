package com.naury.kinetica.core.scene

/**
 * 컴포넌트 변형. 이름이 `세트/변형` 형식인 컴포넌트 원본은 같은 세트의 변형으로 묶인다. 예를 들어 `버튼/기본`, `버튼/눌림`, `버튼/꺼짐`이 그렇다.
 * 이름의 마지막 `/` 앞이 세트 이름, 뒤가 변형 이름이며 앞뒤 공백은 무시한다. `/`가 없으면 세트에 속하지 않은 컴포넌트다.
 *
 * [swap]으로 인스턴스를 같은 세트의 다른 변형으로 바꾸면, 오버라이드는 원본 안에서 레이어 이름 경로가 같은 노드로 옮긴다.
 * 인스턴스에 [BoundProperty.Variant]를 바인딩하면 호스트 데이터의 문자열을 변형 이름으로 써서 고른다.
 * 세트에 [PRESSED_NAMES]에 해당하는 변형이 있으면 재생기는 인스턴스를 누르는 동안 그 변형을 보여 준다([pressed]).
 */
public object ComponentVariants {
    /** 누르는 동안 보여 줄 변형 이름. 대소문자를 구분하지 않는다. */
    public val PRESSED_NAMES: Set<String> = setOf("pressed", "눌림")

    /** 컴포넌트 이름 [component]에서 세트 이름을 꺼낸다. 세트가 없으면 `null`이다. */
    public fun setName(component: String): String? = component.substringBeforeLast('/', "").trim().takeIf { it.isNotEmpty() }

    /** 컴포넌트 이름 [component]에서 변형 이름을 꺼낸다. 세트가 없으면 이름 전체를 돌려준다. */
    public fun variantName(component: String): String = component.substringAfterLast('/').trim()

    /** 원본 [mainId]를 포함해 같은 세트에 속한 원본 목록을 문서 순서로 돌려준다. 세트가 없으면 자신만, 원본이 아니면 빈 목록이다. */
    public fun variantsOf(document: SceneDocument, mainId: NodeId): List<SceneNode> {
        val main = document.node(mainId) ?: return emptyList()
        val name = main.component ?: return emptyList()
        val set = setName(name) ?: return listOf(main)
        return document.nodes.values.filter { node -> node.component?.let(::setName) == set }
    }

    /**
     * 인스턴스 [instanceId]를 같은 세트의 [variant] 변형으로 바꾼 [InstanceOf]를 돌려준다.
     * 오버라이드는 이름 경로가 같은 노드로 옮기고, 그런 노드가 없으면 버린다.
     * 인스턴스가 아니거나 그 이름의 변형이 없으면 `null`이고, 이미 그 변형이면 현재 값을 그대로 돌려준다.
     */
    public fun swap(document: SceneDocument, instanceId: NodeId, variant: String): InstanceOf? {
        val link = document.node(instanceId)?.instanceOf ?: return null
        val wanted = variant.trim()
        val target = variantsOf(document, link.main).firstOrNull { it.component?.let(::variantName) == wanted } ?: return null
        return swapTo(document, link, target.id)
    }

    /**
     * 인스턴스 [instanceId]를 누르는 동안 보여 줄 문서. 세트에 pressed 변형([PRESSED_NAMES])이 없거나 이미 그 변형이면 `null`이다.
     * 그리기 용도로만 쓰며, 누르는 동안에는 데이터로 고르는 변형 바인딩을 무시한다.
     */
    public fun pressed(document: SceneDocument, instanceId: NodeId): SceneDocument? {
        val node = document.node(instanceId) ?: return null
        val link = node.instanceOf ?: return null
        val target = variantsOf(document, link.main).firstOrNull { main ->
            main.component?.let(::variantName)?.lowercase() in PRESSED_NAMES
        } ?: return null
        if (target.id == link.main) return null
        val swapped = node.copy(instanceOf = swapTo(document, link, target.id), bindings = node.bindings - BoundProperty.Variant)
        return Layouts.apply(Components.sync(document.copy(nodes = document.nodes + (instanceId to swapped))))
    }

    /** [link]의 원본을 [mainId]로 바꾼 [InstanceOf]. 오버라이드는 이름 경로를 따라 옮긴다. */
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

    /** 원본에서 각 노드까지의 레이어 이름 경로. 원본 자신은 빈 경로다. */
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
                // 새 레이어는 종류 이름을 그대로 쓰는 경우가 많아 이름이 같은 형제가 흔하므로 순번으로 구별한다.
                val index = seen.merge(child.name, 1, Int::plus)!! - 1
                result[childId] = parentPath + "${child.name}#$index"
                pending += childId
            }
        }
        return result
    }
}
