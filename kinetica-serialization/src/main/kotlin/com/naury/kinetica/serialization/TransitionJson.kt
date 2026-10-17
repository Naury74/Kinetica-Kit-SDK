package com.naury.kinetica.serialization

import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.scene.SceneTransitionEdge
import com.naury.kinetica.core.scene.SceneTransitionKind
import com.naury.kinetica.core.scene.SceneTransitionSpec
import com.naury.kinetica.serialization.JsonKeys as K
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 문서 최상위 `transition`(이 장면이 들어올 때의 전환)을 읽고 쓴다. 기본값과 같은 항목은 쓰지 않는다. */
internal object TransitionJson {
    private const val ORIGIN_CENTER = 0.5

    fun write(spec: SceneTransitionSpec, easing: (EasingSpec) -> JsonObject): JsonObject = buildJsonObject {
        put(K.KIND, spec.kind.jsonName)
        if (spec.from != SceneTransitionEdge.End) put(K.FROM, spec.from.jsonName)
        if (spec.durationMs != SceneTransitionSpec.DEFAULT_DURATION_MS) put(K.DURATION_MS, spec.durationMs)
        if (spec.easing != EasingSpec.EaseInOut) put(K.EASING, easing(spec.easing))
        if (spec.originX != ORIGIN_CENTER) put(K.ORIGIN_X, numberPrimitive(spec.originX))
        if (spec.originY != ORIGIN_CENTER) put(K.ORIGIN_Y, numberPrimitive(spec.originY))
    }

    // 전환은 화면을 그리는 데 꼭 필요하지 않다. 그래서 이 버전이 모르는 종류나 방향이 와도 문서를 거부하지 않고 페이드와 기본 방향으로 읽는다.
    fun read(json: JsonObject, path: String, readEasing: (JsonObject, String) -> EasingSpec): SceneTransitionSpec {
        val kind = SceneTransitionKind.fromJsonName(json.string(K.KIND, path)) ?: SceneTransitionKind.Fade
        val from = json.optional(K.FROM)?.asString("$path.${K.FROM}")?.let(SceneTransitionEdge::fromJsonName) ?: SceneTransitionEdge.End
        val duration = json.optional(K.DURATION_MS)?.asInt("$path.${K.DURATION_MS}") ?: SceneTransitionSpec.DEFAULT_DURATION_MS
        if (duration !in SceneTransitionSpec.MIN_DURATION_MS..SceneTransitionSpec.MAX_DURATION_MS) {
            fail("$path.${K.DURATION_MS}", "${SceneTransitionSpec.MIN_DURATION_MS}..${SceneTransitionSpec.MAX_DURATION_MS} 이어야 합니다.")
        }
        val originX = json.finiteOr(K.ORIGIN_X, path, ORIGIN_CENTER)
        val originY = json.finiteOr(K.ORIGIN_Y, path, ORIGIN_CENTER)
        if (originX !in 0.0..1.0) fail("$path.${K.ORIGIN_X}", "0..1이어야 합니다.")
        if (originY !in 0.0..1.0) fail("$path.${K.ORIGIN_Y}", "0..1이어야 합니다.")
        val easing = json.optional(K.EASING)?.let { readEasing(it.asObject("$path.${K.EASING}"), "$path.${K.EASING}") }
            ?: EasingSpec.EaseInOut
        return SceneTransitionSpec(kind, from, duration, easing, originX, originY)
    }
}
