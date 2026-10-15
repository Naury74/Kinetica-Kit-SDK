package com.naury.kinetica.core.scene

/**
 * TalkBack 같은 스크린 리더에 알리는 노드 정보. 텍스트 노드는 내용을 그대로 읽으므로 따로 정하지 않아도 된다.
 *
 * @property label 읽어 줄 이름. 이미지, 아이콘, 도형에 붙인다. `null`이면 읽을 텍스트가 있을 때만 읽는다.
 * @property role 노드 역할. 액션([SceneNode.action])이 있는 노드는 따로 정하지 않아도 버튼으로 알린다.
 * @property hidden `true`면 장식용으로 보고 이 노드와 자손을 읽지 않는다.
 */
public data class NodeAccessibility(val label: String? = null, val role: NodeRole? = null, val hidden: Boolean = false) {
    public val isDefault: Boolean get() = this == Default

    public companion object {
        public val Default: NodeAccessibility = NodeAccessibility()
    }
}

/** 스크린 리더에 알리는 노드 역할. [wireName]은 JSON에 저장할 때 쓰는 이름이다. */
public enum class NodeRole(public val wireName: String) {
    Button("button"),
    Image("image"),

    /** 제목. 스크린 리더에서 제목 단위로 건너뛸 때 쓴다. */
    Heading("heading"),
    ;

    public companion object {
        public fun fromWireName(name: String): NodeRole? = entries.firstOrNull { it.wireName == name }
    }
}

internal fun checkAccessibility(node: SceneNode, limits: SceneLimits, sink: ViolationSink) {
    val label = node.accessibility.label ?: return
    if (label.isBlank() || label.length > limits.maxNameLength || !isWellFormedUtf16(label)) {
        sink.add(ViolationCode.InvalidMetadata, "접근성 이름이 비어 있거나 너무 길거나 형식이 잘못되었습니다.", node.id)
    }
}
