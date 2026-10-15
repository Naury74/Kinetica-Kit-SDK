package com.naury.kinetica.serialization

import com.naury.kinetica.core.scene.AutoLayout
import com.naury.kinetica.core.scene.CrossAlign
import com.naury.kinetica.core.scene.Insets
import com.naury.kinetica.core.scene.LayoutConstraints
import com.naury.kinetica.core.scene.LayoutDirection
import com.naury.kinetica.core.scene.MainAlign
import com.naury.kinetica.core.scene.Pin
import com.naury.kinetica.core.scene.SizeLimits
import com.naury.kinetica.serialization.JsonKeys as K
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Frame 자동 레이아웃(`layout`)과 노드 제약(`constraints`)을 JSON으로 읽고 쓴다. 기본값인 항목은 쓰지 않는다. */
internal object LayoutJson {
    fun layout(layout: AutoLayout): JsonObject = buildJsonObject {
        put(K.DIRECTION, layout.direction.wireName)
        if (layout.gap != 0.0) put(K.GAP, numberPrimitive(layout.gap))
        if (layout.padding != Insets.Zero) {
            putJsonArray(K.PADDING) {
                layout.padding.run { listOf(left, top, right, bottom) }.forEach { add(numberPrimitive(it)) }
            }
        }
        if (layout.mainAlign != MainAlign.Start) put(K.MAIN_ALIGN, layout.mainAlign.wireName)
        if (layout.crossAlign != CrossAlign.Start) put(K.CROSS_ALIGN, layout.crossAlign.wireName)
        if (layout.hugWidth) put(K.HUG_WIDTH, true)
        if (layout.hugHeight) put(K.HUG_HEIGHT, true)
        if (layout.wrap) put(K.WRAP, true)
        if (layout.lineGap != 0.0) put(K.LINE_GAP, numberPrimitive(layout.lineGap))
    }

    fun sizeLimits(limits: SizeLimits): JsonObject = buildJsonObject {
        limits.minWidth?.let { put(K.MIN_WIDTH, numberPrimitive(it)) }
        limits.maxWidth?.let { put(K.MAX_WIDTH, numberPrimitive(it)) }
        limits.minHeight?.let { put(K.MIN_HEIGHT, numberPrimitive(it)) }
        limits.maxHeight?.let { put(K.MAX_HEIGHT, numberPrimitive(it)) }
    }

    fun readSizeLimits(json: JsonObject, path: String): SizeLimits = SizeLimits(
        minWidth = json.optional(K.MIN_WIDTH)?.asFinite("$path.${K.MIN_WIDTH}"),
        maxWidth = json.optional(K.MAX_WIDTH)?.asFinite("$path.${K.MAX_WIDTH}"),
        minHeight = json.optional(K.MIN_HEIGHT)?.asFinite("$path.${K.MIN_HEIGHT}"),
        maxHeight = json.optional(K.MAX_HEIGHT)?.asFinite("$path.${K.MAX_HEIGHT}"),
    )

    fun constraints(constraints: LayoutConstraints): JsonObject = buildJsonObject {
        if (constraints.horizontal != Pin.Start) put(K.HORIZONTAL, constraints.horizontal.wireName)
        if (constraints.vertical != Pin.Start) put(K.VERTICAL, constraints.vertical.wireName)
    }

    fun readLayout(json: JsonObject, path: String): AutoLayout {
        val padding = json.optional(K.PADDING)?.asArray("$path.${K.PADDING}")?.let { array ->
            val values = array.mapIndexed { i, element -> element.asFinite("$path.${K.PADDING}[$i]") }
            if (values.size != PADDING_SIDES) fail("$path.${K.PADDING}", "왼쪽, 위, 오른쪽, 아래 순서의 값 네 개여야 합니다.")
            Insets(left = values[0], top = values[1], right = values[2], bottom = values[PADDING_SIDES - 1])
        } ?: Insets.Zero
        return AutoLayout(
            direction = wire(json.required(K.DIRECTION, path), "$path.${K.DIRECTION}", LayoutDirection::fromWireName),
            gap = json.finiteOr(K.GAP, path, 0.0),
            padding = padding,
            mainAlign = json.optional(K.MAIN_ALIGN)?.let { wire(it, "$path.${K.MAIN_ALIGN}", MainAlign::fromWireName) } ?: MainAlign.Start,
            crossAlign =
                json.optional(K.CROSS_ALIGN)?.let { wire(it, "$path.${K.CROSS_ALIGN}", CrossAlign::fromWireName) } ?: CrossAlign.Start,
            hugWidth = json.booleanOr(K.HUG_WIDTH, path, default = false),
            hugHeight = json.booleanOr(K.HUG_HEIGHT, path, default = false),
            wrap = json.booleanOr(K.WRAP, path, default = false),
            lineGap = json.finiteOr(K.LINE_GAP, path, 0.0),
        )
    }

    fun readConstraints(json: JsonObject, path: String): LayoutConstraints = LayoutConstraints(
        horizontal = json.optional(K.HORIZONTAL)?.let { wire(it, "$path.${K.HORIZONTAL}", Pin::fromWireName) } ?: Pin.Start,
        vertical = json.optional(K.VERTICAL)?.let { wire(it, "$path.${K.VERTICAL}", Pin::fromWireName) } ?: Pin.Start,
    )

    private fun <T> wire(element: JsonElement, path: String, parse: (String) -> T?): T =
        parse(element.asString(path)) ?: fail(path, "지원하지 않는 값입니다.")

    private const val PADDING_SIDES = 4
}
