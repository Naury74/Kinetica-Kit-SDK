package com.naury.kinetica.serialization

import com.naury.kinetica.core.scene.AppearEffect
import com.naury.kinetica.core.scene.AppearKind
import com.naury.kinetica.core.scene.LoopEffect
import com.naury.kinetica.core.scene.LoopKind
import com.naury.kinetica.core.scene.NodeEffects
import com.naury.kinetica.core.scene.PressEffect
import com.naury.kinetica.core.scene.PressKind
import com.naury.kinetica.serialization.JsonKeys as K
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** 노드 움직임 효과(`effects`)의 JSON. 기본값인 항목은 쓰지 않는다. */
internal object EffectsJson {
    fun write(effects: NodeEffects): JsonObject = buildJsonObject {
        if (effects.press.isNotEmpty()) {
            putJsonArray(K.PRESS) {
                effects.press.forEach { press ->
                    add(
                        buildJsonObject {
                            put(K.KIND, press.kind.wireName)
                            press.color?.let { put(K.COLOR, it.toHex()) }
                            press.amount?.let { put(K.AMOUNT, numberPrimitive(it)) }
                        },
                    )
                }
            }
        }
        effects.appear?.let { appear ->
            putJsonObject(K.APPEAR) {
                put(K.KIND, appear.kind.wireName)
                if (appear.durationMs != AppearEffect.DEFAULT_DURATION_MS) put(K.DURATION_MS, appear.durationMs)
                if (appear.delayMs != 0L) put(K.DELAY_MS, appear.delayMs)
            }
        }
        effects.loop?.let { loop ->
            putJsonObject(K.LOOP) {
                put(K.KIND, loop.kind.wireName)
                if (loop.periodMs != LoopEffect.DEFAULT_PERIOD_MS) put(K.PERIOD_MS, loop.periodMs)
                loop.amount?.let { put(K.AMOUNT, numberPrimitive(it)) }
                loop.color?.let { put(K.COLOR, it.toHex()) }
            }
        }
    }

    fun read(json: JsonObject, path: String): NodeEffects = NodeEffects(
        press = json.optional(K.PRESS)?.asArray("$path.${K.PRESS}")?.mapIndexed { i, element ->
            val itemPath = "$path.${K.PRESS}[$i]"
            val item = element.asObject(itemPath)
            PressEffect(
                kind = kind(item, itemPath, PressKind::fromWireName),
                color = item.colorOrNull(K.COLOR, itemPath),
                amount = item.optional(K.AMOUNT)?.asFinite("$itemPath.${K.AMOUNT}"),
            )
        }.orEmpty(),
        appear = json.optional(K.APPEAR)?.let { element ->
            val itemPath = "$path.${K.APPEAR}"
            val item = element.asObject(itemPath)
            AppearEffect(
                kind = kind(item, itemPath, AppearKind::fromWireName),
                durationMs = item.optional(K.DURATION_MS)?.asLong("$itemPath.${K.DURATION_MS}") ?: AppearEffect.DEFAULT_DURATION_MS,
                delayMs = item.optional(K.DELAY_MS)?.asLong("$itemPath.${K.DELAY_MS}") ?: 0L,
            )
        },
        loop = json.optional(K.LOOP)?.let { element ->
            val itemPath = "$path.${K.LOOP}"
            val item = element.asObject(itemPath)
            LoopEffect(
                kind = kind(item, itemPath, LoopKind::fromWireName),
                periodMs = item.optional(K.PERIOD_MS)?.asLong("$itemPath.${K.PERIOD_MS}") ?: LoopEffect.DEFAULT_PERIOD_MS,
                amount = item.optional(K.AMOUNT)?.asFinite("$itemPath.${K.AMOUNT}"),
                color = item.colorOrNull(K.COLOR, itemPath),
            )
        },
    )

    private fun <T> kind(item: JsonObject, path: String, parse: (String) -> T?): T =
        parse(item.string(K.KIND, path)) ?: fail("$path.${K.KIND}", "지원하지 않는 효과입니다.")
}
