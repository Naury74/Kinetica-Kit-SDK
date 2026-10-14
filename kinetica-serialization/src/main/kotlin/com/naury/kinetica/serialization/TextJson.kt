package com.naury.kinetica.serialization

import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.TextAutoSize
import com.naury.kinetica.core.scene.TextDecoration
import com.naury.kinetica.core.scene.TextNumberFormat
import com.naury.kinetica.core.scene.TextSpan
import com.naury.kinetica.serialization.JsonKeys as K
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** 글꼴·기울임·밑줄과 리치 텍스트 구간의 JSON. 기본값인 항목은 쓰지 않는다. */
internal object TextJson {
    fun write(builder: JsonObjectBuilder, text: NodeContent.Text) = with(builder) {
        text.fontFamily?.let { put(K.FONT_FAMILY, it) }
        if (text.italic) put(K.ITALIC, true)
        if (text.decoration != TextDecoration.None) put(K.DECORATION, text.decoration.wireName)
        if (text.spans.isNotEmpty()) putJsonArray(K.SPANS) { text.spans.forEach { add(span(it)) } }
        if (text.autoSize != TextAutoSize.Fixed) put(K.AUTO_SIZE, text.autoSize.wireName)
        text.numberFormat?.let { format ->
            putJsonObject(K.NUMBER_FORMAT) {
                if (format.prefix.isNotEmpty()) put(K.PREFIX, format.prefix)
                if (format.suffix.isNotEmpty()) put(K.SUFFIX, format.suffix)
                format.decimals?.let { put(K.DECIMALS, it) }
                if (!format.grouping) put(K.GROUPING, false)
            }
        }
    }

    fun readNumberFormat(json: JsonObject, path: String): TextNumberFormat? {
        val formatPath = "$path.${K.NUMBER_FORMAT}"
        val format = json.optional(K.NUMBER_FORMAT)?.asObject(formatPath) ?: return null
        return TextNumberFormat(
            prefix = format.optional(K.PREFIX)?.asString("$formatPath.${K.PREFIX}").orEmpty(),
            suffix = format.optional(K.SUFFIX)?.asString("$formatPath.${K.SUFFIX}").orEmpty(),
            decimals = format.optional(K.DECIMALS)?.asInt("$formatPath.${K.DECIMALS}"),
            grouping = format.booleanOr(K.GROUPING, formatPath, default = true),
        )
    }

    fun readAutoSize(json: JsonObject, path: String): TextAutoSize = json.optional(K.AUTO_SIZE)?.let {
        TextAutoSize.fromWireName(it.asString("$path.${K.AUTO_SIZE}"))
            ?: fail("$path.${K.AUTO_SIZE}", "fixed·height·auto 중 하나여야 합니다.")
    } ?: TextAutoSize.Fixed

    fun readDecoration(json: JsonObject, path: String): TextDecoration = json.optional(K.DECORATION)?.let {
        TextDecoration.fromWireName(it.asString("$path.${K.DECORATION}"))
            ?: fail("$path.${K.DECORATION}", "none·underline·strike 중 하나여야 합니다.")
    } ?: TextDecoration.None

    fun readSpans(json: JsonObject, path: String): List<TextSpan> = json.optional(K.SPANS)?.asArray("$path.${K.SPANS}")
        ?.mapIndexed { i, element ->
            val spanPath = "$path.${K.SPANS}[$i]"
            val span = element.asObject(spanPath)
            TextSpan(
                start = span.required(K.START, spanPath).asInt("$spanPath.${K.START}"),
                end = span.required(K.END, spanPath).asInt("$spanPath.${K.END}"),
                fontFamily = span.optional(K.FONT_FAMILY)?.asString("$spanPath.${K.FONT_FAMILY}"),
                fontSize = span.optional(K.FONT_SIZE)?.asFinite("$spanPath.${K.FONT_SIZE}"),
                fontWeight = span.optional(K.FONT_WEIGHT)?.asInt("$spanPath.${K.FONT_WEIGHT}"),
                italic = span.optional(K.ITALIC)?.let { span.booleanOr(K.ITALIC, spanPath, default = false) },
                decoration = span.optional(K.DECORATION)?.let { readDecoration(span, spanPath) },
                color = span.colorOrNull(K.COLOR, spanPath),
            )
        }.orEmpty()

    private fun span(span: TextSpan): JsonObject = buildJsonObject {
        put(K.START, span.start)
        put(K.END, span.end)
        span.fontFamily?.let { put(K.FONT_FAMILY, it) }
        span.fontSize?.let { put(K.FONT_SIZE, numberPrimitive(it)) }
        span.fontWeight?.let { put(K.FONT_WEIGHT, it) }
        span.italic?.let { put(K.ITALIC, it) }
        span.decoration?.let { put(K.DECORATION, it.wireName) }
        span.color?.let { put(K.COLOR, it.toHex()) }
    }
}
