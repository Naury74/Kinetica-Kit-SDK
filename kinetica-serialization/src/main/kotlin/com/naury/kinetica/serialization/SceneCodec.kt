package com.naury.kinetica.serialization

import com.naury.kinetica.core.scene.Components
import com.naury.kinetica.core.scene.Layouts
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneLimits
import com.naury.kinetica.core.scene.SceneValidator
import com.naury.kinetica.core.scene.SceneViolation
import com.naury.kinetica.core.scene.Tokens
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 장면 문서를 UTF-8 JSON으로 직렬화하고 역직렬화한다.
 *
 * 읽을 때는 외부 입력을 신뢰하지 않는다. 크기 제한, JSON 문법, 스키마 버전과 마이그레이션, 필드 타입과 유한한 숫자,
 * [SceneValidator] 순서로 확인하고 모두 통과해야 문서를 돌려준다. 실패하면 예외를 던지지 않고 [DecodeResult.Failure]를 돌려준다.
 *
 * 모르는 필드는 무시하고 버린다. 다른 버전과 주고받으면서 보존해야 하는 값은 문서의 `extensions`에 넣는다.
 *
 * @param maxJsonBytes 읽을 수 있는 JSON의 최대 크기(UTF-8 바이트). 기본값은 10 MiB다.
 * @param migrations 이전 스키마 버전을 현재 버전으로 올리는 마이그레이션 단계
 */
public class SceneCodec(
    private val limits: SceneLimits = SceneLimits.Default,
    public val maxJsonBytes: Int = DEFAULT_MAX_JSON_BYTES,
    private val migrations: SceneMigrations = SceneMigrations.None,
) {
    init {
        require(maxJsonBytes > 0) { "maxJsonBytes는 0보다 커야 합니다: $maxJsonBytes" }
    }

    private val validator = SceneValidator(limits)
    private val json = Json

    /**
     * 문서를 한 줄짜리 JSON으로 쓴다. 같은 문서는 항상 같은 문자열이 된다.
     * [decode]가 거부할 문서는 쓰지 않는다. 서버가 코드로 만든 문서를 모든 기기가 거부하는 일을 쓰는 쪽에서 미리 막기 위해서다.
     *
     * @throws IllegalArgumentException 문서가 [validate]를 통과하지 못한 경우. 메시지에 위반 코드와 노드 id가 들어 있다.
     */
    public fun encode(document: SceneDocument): String {
        val violations = validate(document)
        require(violations.isEmpty()) {
            "검증을 통과하지 못한 문서는 쓸 수 없습니다: " +
                violations.take(MAX_REPORTED).joinToString { "${it.code}(${it.nodeId?.value ?: "-"})" }
        }
        return SceneJsonWriter.write(document).toString()
    }

    /** [decode]와 같은 기준으로 문서를 검증한다. 빈 목록이면 문제없이 쓰고 읽을 수 있다. */
    public fun validate(document: SceneDocument): List<SceneViolation> = validator.validate(document)

    public fun decode(bytes: ByteArray): DecodeResult {
        if (bytes.size > maxJsonBytes) return DecodeResult.Failure(SceneLoadError.TooLarge(bytes.size.toLong(), maxJsonBytes))
        return decodeChecked(bytes.toString(Charsets.UTF_8))
    }

    public fun decode(text: String): DecodeResult {
        val bytes = utf8Size(text)
        if (bytes > maxJsonBytes) return DecodeResult.Failure(SceneLoadError.TooLarge(bytes, maxJsonBytes))
        return decodeChecked(text)
    }

    private fun decodeChecked(text: String): DecodeResult = try {
        val document = SceneJsonReader(limits).read(upgrade(parse(text)))
        val violations = validator.validate(document)
        // 다른 도구가 만든 문서도 자동 레이아웃을 적용한 상태로 연다. 레이아웃이 계산한 값도 다시 검증한다.
        val laidOut = if (violations.isEmpty()) Layouts.apply(Components.sync(Tokens.resolve(document))) else document
        val finalViolations = if (laidOut === document) violations else validator.validate(laidOut)
        if (finalViolations.isEmpty()) DecodeResult.Success(laidOut) else DecodeResult.Failure(SceneLoadError.Invalid(finalViolations))
    } catch (failure: LoadFailure) {
        DecodeResult.Failure(failure.error)
    }

    private fun parse(text: String): JsonObject {
        // kotlinx JSON 파서는 중첩마다 재귀하므로, 지나치게 깊은 입력은 스택이 넘치기 전에 미리 걸러 낸다.
        if (nestingDepth(text) > MAX_JSON_DEPTH) fail("$", "JSON 중첩이 $MAX_JSON_DEPTH 단계를 넘습니다.")
        val root = try {
            json.parseToJsonElement(text)
        } catch (error: SerializationException) {
            fail("$", "JSON 문법 오류(${error::class.simpleName})")
        }
        return root as? JsonObject ?: fail("$", "최상위 값은 객체여야 합니다.")
    }

    private fun upgrade(root: JsonObject): JsonObject {
        val version = (root[SCHEMA_VERSION_KEY] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            ?: fail(SCHEMA_VERSION_KEY, "정수여야 합니다.")
        val current = SceneDocument.CURRENT_SCHEMA_VERSION
        val migrated = migrations.upgrade(root, from = version, to = current).takeIf { version <= current }
            ?: throw LoadFailure(SceneLoadError.UnsupportedSchema(version))
        check(migrated[SCHEMA_VERSION_KEY]?.jsonPrimitive?.int == current) { "마이그레이션 뒤 schemaVersion은 $current 이어야 합니다." }
        return migrated
    }

    public companion object {
        private const val MAX_REPORTED = 5

        public const val DEFAULT_MAX_JSON_BYTES: Int = 10_485_760

        /** 허용하는 JSON 최대 중첩 깊이. 스키마 버전 1 문서는 7단계면 충분하고, 나머지 여유는 extensions 데이터를 위한 것이다. */
        public const val MAX_JSON_DEPTH: Int = 64
        internal const val SCHEMA_VERSION_KEY = "schemaVersion"
    }
}

public sealed interface DecodeResult {
    public data class Success(val document: SceneDocument) : DecodeResult

    public data class Failure(val error: SceneLoadError) : DecodeResult
}

/** 문서를 읽지 못한 이유. 사용자 콘텐츠는 담지 않으므로 로그에 남겨도 된다. */
public sealed interface SceneLoadError {
    /** 입력이 [maxBytes]를 넘어 내용을 해석하지 않았다. */
    public data class TooLarge(val bytes: Long, val maxBytes: Int) : SceneLoadError

    /** JSON 문법이나 필드 타입이 잘못되었다. [path]는 `nodes[3].transform.x` 같은 위치다. */
    public data class Malformed(val path: String, val reason: String) : SceneLoadError

    /** 이 SDK가 지원하지 않는 스키마 버전이다. */
    public data class UnsupportedSchema(val schemaVersion: Int) : SceneLoadError

    /** 형식은 맞지만 문서 규칙을 어겼다. */
    public data class Invalid(val violations: List<SceneViolation>) : SceneLoadError
}

private const val ONE_BYTE_LIMIT = 0x80
private const val TWO_BYTE_LIMIT = 0x800
private const val THREE_BYTES = 3L
private const val FOUR_BYTES = 4L

private fun utf8Size(text: String): Long {
    var bytes = 0L
    var index = 0
    while (index < text.length) {
        val char = text[index]
        bytes += when {
            char.code < ONE_BYTE_LIMIT -> 1L

            char.code < TWO_BYTE_LIMIT -> 2L

            char.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate() -> {
                index++
                FOUR_BYTES
            }

            else -> THREE_BYTES
        }
        index++
    }
    return bytes
}

/** 문자열 리터럴 안은 건너뛰면서 괄호 중첩의 최대 깊이를 잰다. 재귀하지 않는다. */
internal fun nestingDepth(text: String): Int {
    var depth = 0
    var deepest = 0
    var inString = false
    var escaped = false
    for (char in text) {
        when {
            escaped -> escaped = false

            inString && char == '\\' -> escaped = true

            char == '"' -> inString = !inString

            inString -> Unit

            char == '{' || char == '[' -> {
                depth++
                if (depth > deepest) deepest = depth
            }

            char == '}' || char == ']' -> depth--
        }
    }
    return deepest
}
