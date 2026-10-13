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
 */
public object Bindings {
    public fun apply(document: SceneDocument, data: SceneData, limits: SceneLimits = SceneLimits.Default): SceneDocument {
        if (data.values.isEmpty() || document.nodes.values.none { it.bindings.isNotEmpty() }) return document
        val nodes = LinkedHashMap(document.nodes)
        document.nodes.values.filter { it.bindings.isNotEmpty() }.forEach { node ->
            nodes[node.id] = node.bindings.entries.fold(node) { current, (property, key) ->
                data[key]?.let { value -> current.with(property, value, limits) } ?: current
            }
        }
        return document.copy(nodes = nodes)
    }

    private fun SceneNode.with(property: BoundProperty, value: DataValue, limits: SceneLimits): SceneNode = when (property) {
        BoundProperty.Text -> withText(value, limits)

        BoundProperty.Visible -> (value as? DataValue.Bool)?.let { copy(visible = it.value) } ?: this

        BoundProperty.ImageUri -> withImage(value, limits)

        BoundProperty.Fill -> (value as? DataValue.Color)?.let { copy(style = style.copy(fill = it.value)) } ?: this

        BoundProperty.Opacity -> (value as? DataValue.Number)?.value?.takeIf { it.isFinite() }
            ?.let { copy(style = style.copy(opacity = it.coerceIn(0.0, 1.0))) } ?: this
    }

    private fun SceneNode.withText(value: DataValue, limits: SceneLimits): SceneNode {
        val text = when (value) {
            is DataValue.Text -> value.value
            is DataValue.Number -> format(value.value)
            else -> null
        }
        val content = this.content as? NodeContent.Text
        if (text == null || content == null || !isWellFormedUtf16(text)) return this
        return copy(content = content.copy(text = clip(text, limits.maxTextBytes)))
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
