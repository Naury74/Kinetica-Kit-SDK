package com.naury.kinetica.core.scene

import java.util.Locale

/** 호스트가 바인딩에 넘기는 값. */
public sealed interface DataValue {
    public data class Text(val value: String) : DataValue

    public data class Number(val value: Double) : DataValue

    public data class Bool(val value: Boolean) : DataValue

    public data class Color(val value: ArgbColor) : DataValue
}

/**
 * 호스트 데이터. 키 → 값. 불변이며 값이 바뀌면 새 객체를 만든다(보통 `StateFlow<SceneData>`로 넘긴다).
 */
public data class SceneData(val values: Map<String, DataValue> = emptyMap()) {
    public operator fun get(key: String): DataValue? = values[key]

    public fun with(key: String, value: DataValue): SceneData = SceneData(values + (key to value))

    public companion object {
        public val Empty: SceneData = SceneData()

        public fun of(vararg pairs: Pair<String, DataValue>): SceneData = SceneData(pairs.toMap())
    }
}

/**
 * 바인딩을 적용해 화면에 보일 문서를 만든다. 원래 문서는 바꾸지 않으며 결과는 그리기 전용이다.
 *
 * - 데이터에 키가 없거나 값 종류가 속성과 맞지 않으면 저장된 값을 그대로 쓴다.
 * - 글자는 [limits]의 바이트 상한을 넘으면 잘라 쓰고, 이미지 URI는 허용 규칙을 통과할 때만 쓴다.
 * - 숫자 글자는 소수점 아래 0을 지운 형태로 쓴다(기기 언어와 상관없이 `.`).
 * - 데이터가 보임을 바꾸면 자동 배치 Frame이 그 자리를 메우도록 다시 배치한다.
 * - 데이터가 인스턴스 변형을 바꾸면 그 인스턴스의 자식을 새 원본으로 다시 만든다. 타임라인과 함께 쓰면 평가 전에
 *   [resolveVariants]를 먼저 불러야 키 값이 원본 값으로 덮이지 않는다(재생기·편집기는 그렇게 한다).
 */
public object Bindings {
    /** @param measurer 있으면 바인딩으로 바뀐 글자 중 글자에 맞춰 크기가 바뀌는 노드를 다시 재고 배치한다. */
    @JvmOverloads
    public fun apply(
        document: SceneDocument,
        data: SceneData,
        limits: SceneLimits = SceneLimits.Default,
        measurer: TextMeasurer? = null,
    ): SceneDocument {
        if (data.values.isEmpty() || document.nodes.values.none { it.bindings.isNotEmpty() }) return document
        // 변형을 먼저 바꿔 인스턴스 자식을 새 원본으로 만든 뒤, 그 자식에도 나머지 바인딩을 적용한다.
        val source = resolveVariants(document, data)
        val nodes = LinkedHashMap(source.nodes)
        source.nodes.values.filter { it.bindings.isNotEmpty() }.forEach { node ->
            nodes[node.id] = node.bindings.entries.fold(node) { current, (property, key) ->
                data[key]?.let { value -> current.with(property, value, limits) } ?: current
            }
        }
        val bound = source.copy(nodes = nodes)
        // 데이터로 보임이 바뀐 노드가 있으면 자동 배치를 다시 해 빈자리를 메운다. 그 밖에는 애니메이션 위치를 덮지 않도록 다시 하지 않는다.
        val visibilityChanged = nodes.any { (id, node) -> source.nodes.getValue(id).visible != node.visible }
        val autoTextChanged = measurer != null && nodes.any { (id, node) ->
            (node.content as? NodeContent.Text)?.autoSize?.let { it != TextAutoSize.Fixed } == true && source.nodes.getValue(id) != node
        }
        val relayout = source !== document || visibilityChanged || autoTextChanged
        return if (relayout) Layouts.apply(bound, measurer) else bound
    }

    private fun SceneNode.with(property: BoundProperty, value: DataValue, limits: SceneLimits): SceneNode = when (property) {
        BoundProperty.Text -> withText(value, limits)

        BoundProperty.Visible -> (value as? DataValue.Bool)?.let { copy(visible = it.value) } ?: this

        BoundProperty.ImageUri -> withImage(value, limits)

        BoundProperty.Fill -> (value as? DataValue.Color)?.let { copy(style = style.copy(fill = it.value)) } ?: this

        BoundProperty.Opacity -> (value as? DataValue.Number)?.value?.takeIf { it.isFinite() }
            ?.let { copy(style = style.copy(opacity = it.coerceIn(0.0, 1.0))) } ?: this

        // 변형은 문서 전체(같은 세트의 원본)를 봐야 하므로 withVariants가 따로 처리한다.
        BoundProperty.Variant -> this
    }

    /**
     * 변형에 묶인 인스턴스([BoundProperty.Variant])의 원본을 데이터가 고른 변형으로 바꾸고 인스턴스 자식을 다시 만든다.
     * 다른 인스턴스 안에 복사된 인스턴스는 그 원본 쪽 인스턴스를 바꿔, 복사본 모두가 따라가게 한다.
     * 바꿀 것이 없으면 [document]를 그대로 돌려준다(같은 객체). 그리기 전용이며 원래 문서는 바꾸지 않는다.
     */
    @JvmStatic
    public fun resolveVariants(document: SceneDocument, data: SceneData): SceneDocument {
        if (data.values.isEmpty()) return document
        val swapped = document.nodes.values.filter { BoundProperty.Variant in it.bindings && !isCopyInsideInstance(document, it) }
            .mapNotNull { node ->
                val name = (data[node.bindings.getValue(BoundProperty.Variant)] as? DataValue.Text)?.value?.trim()
                val current = node.instanceOf?.main?.let(document::node)?.component?.let(ComponentVariants::variantName)
                val link = name?.takeIf { it != current }?.let { ComponentVariants.swap(document, node.id, it) }
                if (link == null || link == node.instanceOf) null else node.id to node.copy(instanceOf = link)
            }
        // 새 원본이 자동 배치 Frame이면 배치를 다시 해야 자식 자리가 맞는다.
        return if (swapped.isEmpty()) document else Layouts.apply(Components.sync(document.copy(nodes = document.nodes + swapped)))
    }

    /** 다른 인스턴스가 원본에서 복사해 만든 노드인지. 이런 노드는 다시 맞출 때마다 원본 값으로 돌아가므로 원본 쪽을 바꾼다. */
    private fun isCopyInsideInstance(document: SceneDocument, node: SceneNode): Boolean =
        generateSequence(node.parentId?.let(document::node)) { it.parentId?.let(document::node) }
            .take(document.nodes.size)
            .any { it.instanceOf != null }

    private fun SceneNode.withText(value: DataValue, limits: SceneLimits): SceneNode {
        val text = when (value) {
            is DataValue.Text -> value.value
            is DataValue.Number -> (content as? NodeContent.Text)?.numberFormat?.format(value.value) ?: format(value.value)
            else -> null
        }
        val content = this.content as? NodeContent.Text
        if (text == null || content == null || !isWellFormedUtf16(text)) return this
        val clipped = clip(text, limits.maxTextBytes)
        return copy(content = content.copy(text = clipped, spans = TextSpans.clamp(content.spans, clipped.length)))
    }

    private fun SceneNode.withImage(value: DataValue, limits: SceneLimits): SceneNode {
        val uri = (value as? DataValue.Text)?.value
        val content = this.content as? NodeContent.Image
        if (uri == null || content == null || !isAllowedImageUri(uri, limits)) return this
        return copy(content = content.copy(uri = uri))
    }

    private fun format(number: Double): String = when {
        !number.isFinite() -> ""
        number == Math.rint(number) && kotlin.math.abs(number) < MAX_EXACT -> number.toLong().toString()
        else -> String.format(Locale.ROOT, "%s", number)
    }

    private fun clip(text: String, maxBytes: Int): String {
        if (utf8Length(text) <= maxBytes) return text
        var end = text.length
        while (end > 0 && utf8Length(text.substring(0, end)) > maxBytes) end = maxOf(0, end - maxOf(1, (end / CLIP_STEP_DIVISOR)))
        if (end > 0 && text[end - 1].isHighSurrogate()) end--
        return text.substring(0, end)
    }

    private const val MAX_EXACT = 9.0e15
    private const val CLIP_STEP_DIVISOR = 16
}
