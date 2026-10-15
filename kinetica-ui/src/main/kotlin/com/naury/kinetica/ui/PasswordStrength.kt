package com.naury.kinetica.ui

/** [passwordStrength]가 매기는 비밀번호 강도. */
public enum class PasswordStrength {
    /** 8자보다 짧거나, 흔한 단어나 반복, 연속된 글자가 들어 있거나, 한 종류 글자만 쓴 비밀번호 */
    Weak,

    /** 8자 이상이고 대소문자 섞기, 숫자, 기호, 12자 이상 중 한두 가지를 갖춘 비밀번호 */
    Medium,

    /** 8자 이상이고 위 조건 가운데 세 가지 이상을 갖춘 비밀번호 */
    Strong,
}

/**
 * 비밀번호 강도를 간단한 규칙으로 매긴다. 서버 정책을 대신하지 않으며, 입력하는 동안 사용자에게 방향을 알려 주는 용도다.
 *
 * 8자 미만이면 [PasswordStrength.Weak]다. 그 밖에는 대소문자를 섞었는지, 숫자, 기호, 12자 이상, 16자 이상마다 1점을 주고,
 * `password`나 `qwerty` 같은 흔한 단어, 한 글자 반복, `1234`나 `abcd` 같은 연속 글자가 들어 있으면 2점을 뺀다.
 * 3점 이상이면 [PasswordStrength.Strong], 1~2점이면 [PasswordStrength.Medium], 그보다 낮으면 [PasswordStrength.Weak]다.
 */
public fun passwordStrength(password: String): PasswordStrength {
    if (password.length < PASSWORD_MIN_LENGTH) return PasswordStrength.Weak
    var score = 0
    if (password.any(Char::isLowerCase) && password.any(Char::isUpperCase)) score++
    if (password.any(Char::isDigit)) score++
    if (password.any(::isPasswordSymbol)) score++
    if (password.length >= LONG_LENGTH) score++
    if (password.length >= VERY_LONG_LENGTH) score++
    if (hasWeakPattern(password)) score -= WEAK_PATTERN_PENALTY
    return when {
        score >= STRONG_SCORE -> PasswordStrength.Strong
        score >= 1 -> PasswordStrength.Medium
        else -> PasswordStrength.Weak
    }
}

internal fun isPasswordSymbol(c: Char): Boolean = !c.isLetterOrDigit() && !c.isWhitespace()

private fun hasWeakPattern(password: String): Boolean {
    val lower = password.lowercase()
    if (COMMON_WORDS.any { it in lower }) return true
    var run = 1
    var sequence = 1
    for (i in 1 until lower.length) {
        run = if (lower[i] == lower[i - 1]) run + 1 else 1
        sequence = if (lower[i] - lower[i - 1] == 1 && lower[i].isLetterOrDigit()) sequence + 1 else 1
        if (run >= PATTERN_RUN || sequence >= PATTERN_RUN) return true
    }
    return false
}

internal const val PASSWORD_MIN_LENGTH = 8
private const val LONG_LENGTH = 12
private const val VERY_LONG_LENGTH = 16
private const val STRONG_SCORE = 3
private const val WEAK_PATTERN_PENALTY = 2
private const val PATTERN_RUN = 4
private val COMMON_WORDS = listOf("password", "passwd", "qwerty", "asdf", "letmein", "iloveyou", "admin", "welcome", "monkey", "dragon")
