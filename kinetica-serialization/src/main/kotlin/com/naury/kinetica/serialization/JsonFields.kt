package com.naury.kinetica.serialization

import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.serialization.JsonKeys as K
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal fun fail(path: String, reason: String): Nothing = throw LoadFailure(SceneLoadError.Malformed(path, reason))

internal fun JsonElement.asObject(path: String): JsonObject = this as? JsonObject ?: fail(path, "객체여야 합니다.")

internal fun JsonElement.asArray(path: String): JsonArray = this as? JsonArray ?: fail(path, "배열이어야 합니다.")

private fun JsonElement.asNumber(path: String): JsonPrimitive {
    val primitive = this as? JsonPrimitive
    if (primitive == null || primitive.isString || primitive is JsonNull) fail(path, "숫자여야 합니다.")
    return primitive
}

internal fun JsonElement.asString(path: String): String {
    val primitive = this as? JsonPrimitive
    if (primitive == null || !primitive.isString) fail(path, "문자열이어야 합니다.")
    return primitive.content
}

internal fun JsonElement.asFinite(path: String): Double {
    val value = asNumber(path).doubleOrNull ?: fail(path, "숫자여야 합니다.")
    if (!value.isFinite()) fail(path, "유한한 숫자여야 합니다.")
    return value
}

internal fun JsonElement.asLong(path: String): Long = asNumber(path).longOrNull ?: fail(path, "정수여야 합니다.")

internal fun JsonElement.asInt(path: String): Int = asNumber(path).intOrNull ?: fail(path, "정수여야 합니다.")

internal fun JsonElement.asNodeId(path: String): NodeId = NodeId.parseOrNull(asString(path)) ?: fail(path, "id 형식이 올바르지 않습니다.")

internal fun JsonElement.asColor(path: String): ArgbColor =
    ArgbColor.parseHexOrNull(asString(path)) ?: fail(path, "#RRGGBB 또는 #AARRGGBB여야 합니다.")

internal fun JsonObject.required(key: String, path: String): JsonElement = this[key] ?: fail("$path.$key", "필드가 없습니다.")

/** 필드가 없거나 `null`이면 `null`. */
internal fun JsonObject.optional(key: String): JsonElement? = this[key]?.takeUnless { it is JsonNull }

internal fun JsonObject.finite(key: String, path: String): Double = required(key, path).asFinite("$path.$key")

internal fun JsonObject.finiteOr(key: String, path: String, default: Double): Double = optional(key)?.asFinite("$path.$key") ?: default

internal fun JsonObject.string(key: String, path: String): String = required(key, path).asString("$path.$key")

internal fun JsonObject.booleanOr(key: String, path: String, default: Boolean): Boolean {
    val element = optional(key) ?: return default
    val primitive = element as? JsonPrimitive
    return primitive?.takeUnless { it.isString }?.booleanOrNull ?: fail("$path.$key", "true 또는 false여야 합니다.")
}

internal fun JsonObject.colorOrNull(key: String, path: String): ArgbColor? = optional(key)?.asColor("$path.$key")

internal fun JsonObject.size(key: String, path: String): Size {
    val sizePath = "$path.$key"
    val element = required(key, path).asObject(sizePath)
    val width = element.finite(K.WIDTH, sizePath)
    val height = element.finite(K.HEIGHT, sizePath)
    if (width < 0.0 || height < 0.0) fail(sizePath, "크기는 음수일 수 없습니다.")
    return Size(width, height)
}

internal fun JsonObject.point(xKey: String, yKey: String, path: String): Vec2 = Vec2(finite(xKey, path), finite(yKey, path))
