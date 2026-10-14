package com.naury.kinetica.serialization

import com.naury.kinetica.core.scene.CornerRadii
import com.naury.kinetica.core.scene.Gradient
import com.naury.kinetica.core.scene.GradientStop
import com.naury.kinetica.core.scene.GradientType
import com.naury.kinetica.core.scene.LineCap
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.Shadow
import com.naury.kinetica.serialization.JsonKeys as K
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** 그림자·그라디언트·점선·흐림·선 끝·모서리의 JSON. 기본값인 항목은 쓰지 않는다. */
internal object StyleJson {
    fun write(builder: JsonObjectBuilder, style: NodeStyle) = with(builder) {
        if (style.shadows.isNotEmpty()) putJsonArray(K.SHADOWS) { style.shadows.forEach { add(shadow(it)) } }
        style.gradient?.let { put(K.GRADIENT, gradient(it)) }
        if (style.strokeDash.isNotEmpty()) putJsonArray(K.DASH) { style.strokeDash.forEach { add(numberPrimitive(it)) } }
        if (style.blur != 0.0) put(K.LAYER_BLUR, numberPrimitive(style.blur))
        if (style.startCap != LineCap.None) put(K.START_CAP, style.startCap.wireName)
        if (style.endCap != LineCap.None) put(K.END_CAP, style.endCap.wireName)
        style.fillToken?.let { put(K.FILL_TOKEN, it) }
        style.strokeToken?.let { put(K.STROKE_TOKEN, it) }
    }

    /** 예전 형식의 그림자 하나(`shadow`)도 읽는다. */
    fun readShadows(json: JsonObject, path: String): List<Shadow> {
        val list = json.optional(K.SHADOWS)?.asArray("$path.${K.SHADOWS}")
            ?.mapIndexed { i, element -> readShadow(element.asObject("$path.${K.SHADOWS}[$i]"), "$path.${K.SHADOWS}[$i]") }
        val legacy = json.optional(K.SHADOW)?.let { readShadow(it.asObject("$path.${K.SHADOW}"), "$path.${K.SHADOW}") }
        return list ?: listOfNotNull(legacy)
    }

    fun readGradient(json: JsonObject, path: String): Gradient? = json.optional(K.GRADIENT)?.let { element ->
        val gradientPath = "$path.${K.GRADIENT}"
        val gradient = element.asObject(gradientPath)
        Gradient(
            type =
                GradientType.fromWireName(gradient.string(K.TYPE, gradientPath))
                    ?: fail("$gradientPath.${K.TYPE}", "linear·radial 중 하나여야 합니다."),
            stops = gradient.required(K.STOPS, gradientPath).asArray("$gradientPath.${K.STOPS}").mapIndexed { i, stop ->
                val stopPath = "$gradientPath.${K.STOPS}[$i]"
                val stopJson = stop.asObject(stopPath)
                GradientStop(stopJson.finite(K.POSITION, stopPath), stopJson.required(K.COLOR, stopPath).asColor("$stopPath.${K.COLOR}"))
            },
            angleDegrees = gradient.finiteOr(K.ANGLE, gradientPath, DEFAULT_ANGLE),
        )
    }

    fun readDash(json: JsonObject, path: String): List<Double> =
        json.optional(K.DASH)?.asArray("$path.${K.DASH}")?.mapIndexed { i, value -> value.asFinite("$path.${K.DASH}[$i]") }.orEmpty()

    fun readCap(json: JsonObject, key: String, path: String): LineCap =
        json.optional(key)?.let { LineCap.fromWireName(it.asString("$path.$key")) ?: fail("$path.$key", "none·arrow·circle 중 하나여야 합니다.") }
            ?: LineCap.None

    fun corners(corners: CornerRadii): JsonArray = JsonArray(
        corners.run {
            listOf(topLeft, topRight, bottomRight, bottomLeft)
        }.map(::numberPrimitive),
    )

    fun readCorners(json: JsonObject, path: String): CornerRadii? = json.optional(K.CORNERS)?.asArray("$path.${K.CORNERS}")?.let { array ->
        val values = array.mapIndexed { i, value -> value.asFinite("$path.${K.CORNERS}[$i]") }
        if (values.size != CORNER_COUNT) fail("$path.${K.CORNERS}", "왼쪽 위·오른쪽 위·오른쪽 아래·왼쪽 아래 네 값이어야 합니다.")
        CornerRadii(values[0], values[1], values[2], values[CORNER_COUNT - 1])
    }

    private fun shadow(shadow: Shadow): JsonObject = buildJsonObject {
        put(K.COLOR, shadow.color.toHex())
        put(K.X, numberPrimitive(shadow.offsetX))
        put(K.Y, numberPrimitive(shadow.offsetY))
        put(K.BLUR, numberPrimitive(shadow.blur))
        if (shadow.spread != 0.0) put(K.SPREAD, numberPrimitive(shadow.spread))
        if (shadow.inner) put(K.INNER, true)
    }

    private fun readShadow(json: JsonObject, path: String): Shadow = Shadow(
        color = json.required(K.COLOR, path).asColor("$path.${K.COLOR}"),
        offsetX = json.finiteOr(K.X, path, 0.0),
        offsetY = json.finiteOr(K.Y, path, 0.0),
        blur = json.finiteOr(K.BLUR, path, 0.0),
        spread = json.finiteOr(K.SPREAD, path, 0.0),
        inner = json.booleanOr(K.INNER, path, default = false),
    )

    private fun gradient(gradient: Gradient): JsonObject = buildJsonObject {
        put(K.TYPE, gradient.type.wireName)
        putJsonArray(K.STOPS) {
            gradient.stops.forEach { stop ->
                add(
                    buildJsonObject {
                        put(K.POSITION, numberPrimitive(stop.position))
                        put(K.COLOR, stop.color.toHex())
                    },
                )
            }
        }
        if (gradient.angleDegrees != DEFAULT_ANGLE) put(K.ANGLE, numberPrimitive(gradient.angleDegrees))
    }

    private const val DEFAULT_ANGLE = 90.0
    private const val CORNER_COUNT = 4
}
