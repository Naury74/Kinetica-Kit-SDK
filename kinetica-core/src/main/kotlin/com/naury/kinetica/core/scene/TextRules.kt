package com.naury.kinetica.core.scene

private const val ONE_BYTE_LIMIT = 0x80
private const val TWO_BYTE_LIMIT = 0x800
private const val SURROGATE_PAIR_BYTES = 4
private const val THREE_BYTES = 3

/** 짝이 맞지 않는 surrogate가 없는지 확인한다. */
internal fun isWellFormedUtf16(text: String): Boolean {
    var index = 0
    while (index < text.length) {
        val char = text[index]
        when {
            char.isHighSurrogate() -> {
                if (index + 1 >= text.length || !text[index + 1].isLowSurrogate()) return false
                index += 2
            }

            char.isLowSurrogate() -> return false

            else -> index++
        }
    }
    return true
}

/** 올바른 UTF-16 문자열을 UTF-8로 바꿨을 때의 바이트 수. 새 배열을 만들지 않는다. */
internal fun utf8Length(text: String): Int {
    var bytes = 0
    var index = 0
    while (index < text.length) {
        val char = text[index]
        when {
            char.code < ONE_BYTE_LIMIT -> bytes += 1

            char.code < TWO_BYTE_LIMIT -> bytes += 2

            char.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate() -> {
                bytes += SURROGATE_PAIR_BYTES
                index++
            }

            else -> bytes += THREE_BYTES
        }
        index++
    }
    return bytes
}

private val uriPattern = Regex("([A-Za-z][A-Za-z0-9+.-]*):(.+)")
private val traversalPattern = Regex("(^|/)(\\.|%2[eE]){2}(/|$)")

/** scheme이 허용 목록에 있고 제어 문자, 공백, 상위 경로 이동(`..`)이 없는 URI인지 확인한다. */
internal fun isAllowedImageUri(uri: String, limits: SceneLimits): Boolean {
    if (uri.length > limits.maxUriLength || uri.any { it.isWhitespace() || it.isISOControl() }) return false
    val match = uriPattern.matchEntire(uri) ?: return false
    val scheme = match.groupValues[1].lowercase()
    val rest = match.groupValues[2]
    return scheme in limits.allowedImageUriSchemes && !traversalPattern.containsMatchIn(rest)
}
