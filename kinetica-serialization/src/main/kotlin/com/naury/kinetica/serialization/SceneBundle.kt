package com.naury.kinetica.serialization

import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * 여러 화면을 한 번에 주고받는 묶음 JSON: `{"bundleVersion":1,"scenes":{"home":{…장면…},"promo":{…}}}`.
 * 화면 id는 [NodeId] 규칙을 따른다. 각 장면은 [codec]으로 따로 검증하므로 한 화면이 깨져도 나머지는 읽는다.
 *
 * @param maxBundleBytes 묶음 전체의 최대 UTF-8 바이트 수(기본 20 MiB)
 * @param maxScenes 한 묶음의 최대 화면 수
 */
public class SceneBundleCodec @JvmOverloads constructor(
    private val codec: SceneCodec = SceneCodec(),
    public val maxBundleBytes: Int = DEFAULT_MAX_BUNDLE_BYTES,
    public val maxScenes: Int = DEFAULT_MAX_SCENES,
) {
    public fun encode(scenes: Map<String, SceneDocument>): String = buildJsonObject {
        put(BUNDLE_VERSION, CURRENT_VERSION)
        putJsonObject(SCENES) {
            scenes.toSortedMap().forEach { (id, document) -> put(id, Json.parseToJsonElement(codec.encode(document))) }
        }
    }.toString()

    /** 묶음을 읽는다. 바깥 형식이 틀리면 [BundleDecodeResult.Failure], 그 밖에는 화면마다의 결과다. */
    public fun decode(bytes: ByteArray): BundleDecodeResult {
        if (bytes.size > maxBundleBytes) return BundleDecodeResult.Failure(SceneLoadError.TooLarge(bytes.size.toLong(), maxBundleBytes))
        return try {
            BundleDecodeResult.Success(scenes(bytes.toString(Charsets.UTF_8)).mapValues { (_, scene) -> codec.decode(scene.toString()) })
        } catch (failure: LoadFailure) {
            BundleDecodeResult.Failure(failure.error)
        }
    }

    private fun scenes(text: String): JsonObject {
        // 장면 하나의 중첩 상한에 묶음 두 단계를 더한다.
        if (nestingDepth(text) > SceneCodec.MAX_JSON_DEPTH + BUNDLE_DEPTH) fail("$", "JSON 중첩이 너무 깊습니다.")
        val root = try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: fail("$", "최상위 값은 객체여야 합니다.")
        val version = (root[BUNDLE_VERSION] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        if (version != CURRENT_VERSION) fail(BUNDLE_VERSION, "지원하지 않는 묶음 형식입니다.")
        val scenes = root[SCENES] as? JsonObject ?: fail(SCENES, "객체여야 합니다.")
        if (scenes.size > maxScenes) fail(SCENES, "화면이 $maxScenes 개를 넘습니다.")
        if (scenes.keys.any { !NodeId.isValid(it) }) fail(SCENES, "화면 id 형식이 올바르지 않습니다.")
        return scenes
    }

    public companion object {
        public const val DEFAULT_MAX_BUNDLE_BYTES: Int = 20_971_520
        public const val DEFAULT_MAX_SCENES: Int = 64
        private const val CURRENT_VERSION = 1
        private const val BUNDLE_DEPTH = 2
        private const val BUNDLE_VERSION = "bundleVersion"
        private const val SCENES = "scenes"
    }
}

/** [SceneBundleCodec.decode]의 결과. */
public sealed interface BundleDecodeResult {
    /** 화면 id마다의 결과. 깨진 화면은 [DecodeResult.Failure]다. */
    public data class Success(val scenes: Map<String, DecodeResult>) : BundleDecodeResult

    public data class Failure(val error: SceneLoadError) : BundleDecodeResult
}
