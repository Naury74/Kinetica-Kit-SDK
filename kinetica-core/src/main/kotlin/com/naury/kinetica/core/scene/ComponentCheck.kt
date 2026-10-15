package com.naury.kinetica.core.scene

private const val MAX_TOKENS = 256
private const val MAX_OVERRIDES = 512

/** 토큰 이름은 데이터 키와 형식이 같고 개수 한도가 있다. */
internal fun checkTokens(document: SceneDocument, sink: ViolationSink) {
    if (document.tokens.size > MAX_TOKENS || document.tokens.keys.any { !isValidDataKey(it) }) {
        sink.add(ViolationCode.InvalidStyle, "디자인 토큰 이름이 올바르지 않거나 ${MAX_TOKENS}개를 넘습니다.")
    }
}

/** 컴포넌트 원본과 인스턴스는 Frame이어야 하고, 이름과 토큰, 오버라이드의 형식과 개수 한도를 지켜야 한다. */
internal fun checkComponent(node: SceneNode, limits: SceneLimits, sink: ViolationSink) {
    val linked = node.component != null || node.instanceOf != null
    when {
        linked && node.content !is NodeContent.Frame -> sink.add(ViolationCode.InvalidStyle, "컴포넌트와 인스턴스는 Frame이어야 합니다.", node.id)
        node.component != null && node.instanceOf != null -> sink.add(ViolationCode.InvalidStyle, "인스턴스를 그대로 컴포넌트 원본으로 쓸 수 없습니다.", node.id)
        !componentValuesValid(node, limits) -> sink.add(ViolationCode.InvalidStyle, "컴포넌트 이름, 오버라이드, 토큰 이름 중 올바르지 않은 값이 있습니다.", node.id)
    }
}

private fun componentValuesValid(node: SceneNode, limits: SceneLimits): Boolean {
    val name = node.component
    val nameOk = name == null || (name.isNotBlank() && name.length <= limits.maxNameLength && isWellFormedUtf16(name))
    val overrides = node.instanceOf?.overrides.orEmpty()
    val overridesOk = overrides.size <= MAX_OVERRIDES && overrides.values.all { override ->
        (override.text?.let(::isWellFormedUtf16) ?: true) && (override.imageUri?.let { isAllowedImageUri(it, limits) } ?: true)
    }
    val tokensOk = listOfNotNull(node.style.fillToken, node.style.strokeToken).all(::isValidDataKey)
    return nameOk && overridesOk && tokensOk
}
