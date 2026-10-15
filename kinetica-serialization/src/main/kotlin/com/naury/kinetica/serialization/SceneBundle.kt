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
 * 여러 화면을 한 번에 주고받는 번들 JSON을 읽는다. 형식은 `{"bundleVersion":1,"scenes":{"home":{…장면…},"promo":{…}}}`이다.
 * 화면 id는 [NodeId] 규칙을 따른다. 장면마다 [codec]으로 따로 검증하므로 한 화면이 깨져도 나머지 화면은 읽는다.
 *
 * @param maxBundleBytes 번들 전체의 최대 크기(UTF-8 바이트). 기본값은 20 MiB다.
 * @param maxScenes 번들 하나에 담을 수 있는 최대 화면 수
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

    /** 번들을 읽는다. 바깥 구조가 잘못되었으면 [BundleDecodeResult.Failure]를, 그렇지 않으면 화면별 결과를 돌려준다. */
    public fun decode(bytes: ByteArray): BundleDecodeResult {
        if (bytes.size > maxBundleBytes) return BundleDecodeResult.Failure(SceneLoadError.TooLarge(bytes.size.toLong(), maxBundleBytes))
        return try {
            BundleDecodeResult.Success(scenes(bytes.toString(Charsets.UTF_8)).mapValues { (_, scene) -> codec.decode(scene.toString()) })
        } catch (failure: LoadFailure) {
            BundleDecodeResult.Failure(failure.error)
        }
    }

    private fun scenes(text: String): JsonObject {
        // 장면 하나의 중첩 한도에 번들 구조가 차지하는 두 단계를 더한다.
        if (nestingDepth(text) > SceneCodec.MAX_JSON_DEPTH + BUNDLE_DEPTH) fail("$", "JSON 중첩이 너무 깊습니다.")
        val root = try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: fail("$", "최상위 값은 객체여야 합니다.")
        val version = (root[BUNDLE_VERSION] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        if (version != CURRENT_VERSION) fail(BUNDLE_VERSION, "지원하지 않는 번들 버전입니다.")
        val scenes = root[SCENES] as? JsonObject ?: fail(SCENES, "객체여야 합니다.")
        if (scenes.size > maxScenes) fail(SCENES, "화면 수가 최대 $maxScenes 개를 넘습니다.")
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
    /** 화면 id별 결과. 깨진 화면은 [DecodeResult.Failure]다. */
    public data class Success(val scenes: Map<String, DecodeResult>) : BundleDecodeResult

    public data class Failure(val error: SceneLoadError) : BundleDecodeResult
}
