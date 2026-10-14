package com.naury.kinetica.serialization

import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.InstanceOf
import com.naury.kinetica.core.scene.Override
import com.naury.kinetica.serialization.JsonKeys as K
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** 디자인 토큰·컴포넌트 인스턴스의 JSON. */
internal object ComponentJson {
    fun tokens(tokens: Map<String, ArgbColor>): JsonObject =
        buildJsonObject { tokens.toSortedMap().forEach { (name, color) -> put(name, color.toHex()) } }

    fun readTokens(root: JsonObject): Map<String, ArgbColor> =
        root.optional(K.TOKENS)?.asObject(K.TOKENS)?.mapValues { (name, value) -> value.asColor("${K.TOKENS}.$name") }.orEmpty()

    fun instance(link: InstanceOf): JsonObject = buildJsonObject {
        put(K.MAIN, link.main.value)
        if (link.overrides.isNotEmpty()) {
            putJsonObject(K.OVERRIDES) {
                link.overrides.entries.sortedBy { it.key.value }.forEach { (source, override) ->
                    putJsonObject(source.value) {
                        override.text?.let { put(K.TEXT, it) }
                        override.fill?.let { put(K.FILL, it.toHex()) }
                        override.visible?.let { put(K.VISIBLE_OVERRIDE, it) }
                        override.imageUri?.let { put(K.URI, it) }
                    }
                }
            }
        }
    }

    fun readInstance(json: JsonObject, path: String): InstanceOf = InstanceOf(
        main = json.required(K.MAIN, path).asNodeId("$path.${K.MAIN}"),
        overrides = json.optional(K.OVERRIDES)?.asObject("$path.${K.OVERRIDES}")?.entries?.associate { (source, element) ->
            val itemPath = "$path.${K.OVERRIDES}.$source"
            val item = element.asObject(itemPath)
            val id = com.naury.kinetica.core.scene.NodeId.parseOrNull(source) ?: fail(itemPath, "id 형식이 올바르지 않습니다.")
            id to Override(
                text = item.optional(K.TEXT)?.asString("$itemPath.${K.TEXT}"),
                fill = item.colorOrNull(K.FILL, itemPath),
                visible = item.optional(K.VISIBLE_OVERRIDE)?.let { item.booleanOr(K.VISIBLE_OVERRIDE, itemPath, default = true) },
                imageUri = item.optional(K.URI)?.asString("$itemPath.${K.URI}"),
            )
        }.orEmpty(),
    )
}
