package com.naury.kinetica.ui

import androidx.compose.runtime.Immutable

/** 코드 조각의 종류 */
internal enum class CodeTokenKind { Keyword, Text, Number, Comment, Type, Function, Property }

/** [start]..[end) 구간의 코드 조각. 위치는 원래 코드의 글자 위치다. */
@Immutable
internal data class CodeToken(val start: Int, val end: Int, val kind: CodeTokenKind)

/** 구문 강조를 아는 언어 */
internal enum class CodeLanguage(val title: String) {
    Kotlin("Kotlin"),
    Java("Java"),
    JavaScript("JavaScript"),
    TypeScript("TypeScript"),
    Json("JSON"),
    Python("Python"),
    Shell("Shell"),
}

/** 코드 블록 언어 이름을 [CodeLanguage]로 바꾼다. 모르는 이름이면 null이다. 대소문자는 가리지 않는다. */
internal fun codeLanguageOf(name: String?): CodeLanguage? = when (name?.trim()?.lowercase()) {
    "kotlin", "kt", "kts" -> CodeLanguage.Kotlin
    "java" -> CodeLanguage.Java
    "javascript", "js", "jsx", "mjs", "cjs" -> CodeLanguage.JavaScript
    "typescript", "ts", "tsx" -> CodeLanguage.TypeScript
    "json", "jsonc" -> CodeLanguage.Json
    "python", "py" -> CodeLanguage.Python
    "shell", "sh", "bash", "zsh", "console", "shellscript" -> CodeLanguage.Shell
    else -> null
}

/**
 * [code]를 강조할 조각으로 나눈다. 강조하지 않는 글자는 조각에 넣지 않는다. 조각은 앞에서부터 겹치지 않게 나온다.
 * 닫히지 않은 문자열과 블록 주석은 그 줄 끝이나 코드 끝까지 칠한다. [language]가 null이면 빈 목록이다.
 */
internal fun highlightCode(code: String, language: CodeLanguage?): List<CodeToken> {
    if (language == null || code.isEmpty()) return emptyList()
    val tokens = ArrayList<CodeToken>()
    scanTokens(code, grammarOf(language), 0, tokens)
    return tokens
}

private fun scanTokens(code: String, grammar: Grammar, from: Int, out: MutableList<CodeToken>) {
    for (match in grammar.pattern.findAll(code, from)) {
        val kind = if (match.range.isEmpty()) null else tokenKind(match, code, grammar)
        if (kind != null) out += CodeToken(match.range.first, match.range.last + 1, kind)
    }
}

/**
 * 스트리밍으로 뒤에 코드가 붙을 때 앞부분 조각을 다시 쓰는 강조 캐시. 결과는 늘 [highlightCode]와 같다.
 * 마지막 줄 바꿈 앞에서 끝난 조각은 뒤에 글이 붙어도 바뀌지 않으므로 두고, 그 뒤부터 다시 찾는다.
 * 여러 줄에 걸친 주석과 문자열은 닫히지 않으면 코드 끝까지 가므로 그 조각은 두지 않는다.
 */
internal class CodeHighlightCache {
    private var code = ""
    private var language: CodeLanguage? = null
    private var tokens: List<CodeToken> = emptyList()

    /** 마지막으로 다시 찾기 시작한 글자 위치. 테스트가 캐시를 썼는지 확인할 때 본다. */
    var lastScanFrom: Int = 0
        private set

    fun update(code: String, language: CodeLanguage?): List<CodeToken> {
        if (code == this.code && language == this.language) return tokens
        val grows = language != null && language == this.language && this.code.isNotEmpty() && code.startsWith(this.code)
        tokens = if (grows && language != null) grow(code, grammarOf(language)) else highlightCode(code, language).also { lastScanFrom = 0 }
        this.code = code
        this.language = language
        return tokens
    }

    private fun grow(code: String, grammar: Grammar): List<CodeToken> {
        val old = this.code
        var limit = old.lastIndexOf('\n')
        // JSON 문자열은 뒤의 공백 너머 콜론을 보고 키인지 정하므로 공백만 남은 끝에 닿은 조각은 다시 본다.
        if (grammar.language == CodeLanguage.Json) limit = minOf(limit, old.indexOfLast { !it.isWhitespace() })
        var keep = 0
        while (keep < tokens.size && tokens[keep].end <= limit) keep++
        val from = if (keep == 0) 0 else tokens[keep - 1].end
        lastScanFrom = from
        val next = ArrayList<CodeToken>(tokens.size + RESERVE)
        next.addAll(tokens.subList(0, keep))
        scanTokens(code, grammar, from, next)
        return next
    }
}

/** 맞은 조각의 종류. 강조하지 않는 낱말이면 null이다. */
private fun tokenKind(match: MatchResult, code: String, grammar: Grammar): CodeTokenKind? {
    val end = match.range.last + 1
    return when {
        match.groups[GROUP_COMMENT] != null -> CodeTokenKind.Comment
        match.groups[GROUP_STRING] != null -> stringKind(code, end, grammar.language)
        match.groups[GROUP_NUMBER] != null -> CodeTokenKind.Number
        else -> wordKind(match.value, code, end, grammar)
    }
}

private fun stringKind(code: String, end: Int, language: CodeLanguage): CodeTokenKind {
    if (language != CodeLanguage.Json) return CodeTokenKind.Text
    var k = end
    while (k < code.length && code[k].isWhitespace()) k++
    return if (k < code.length && code[k] == ':') CodeTokenKind.Property else CodeTokenKind.Text
}

private fun wordKind(word: String, code: String, end: Int, grammar: Grammar): CodeTokenKind? = when {
    word in grammar.keywords -> CodeTokenKind.Keyword
    grammar.language == CodeLanguage.Json || grammar.language == CodeLanguage.Shell -> null
    end < code.length && code[end] == '(' -> CodeTokenKind.Function
    grammar.types && word[0].isUpperCase() -> CodeTokenKind.Type
    else -> null
}

private class Grammar(val language: CodeLanguage, val pattern: Regex, val keywords: Set<String>, val types: Boolean)

private fun grammarOf(language: CodeLanguage): Grammar = GRAMMARS.getOrPut(language) {
    when (language) {
        CodeLanguage.Kotlin -> Grammar(language, cLike(TRIPLE_DOUBLE + "|" + DOUBLE + "|" + CHAR), KOTLIN, types = true)

        CodeLanguage.Java -> Grammar(language, cLike(TRIPLE_DOUBLE + "|" + DOUBLE + "|" + CHAR), JAVA, types = true)

        CodeLanguage.JavaScript -> Grammar(language, cLike(BACKTICK + "|" + DOUBLE + "|" + SINGLE), JS, types = true)

        CodeLanguage.TypeScript -> Grammar(language, cLike(BACKTICK + "|" + DOUBLE + "|" + SINGLE), JS + TS, types = true)

        CodeLanguage.Json -> Grammar(language, pattern("//[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|\\z)", DOUBLE), JSON, types = false)

        CodeLanguage.Python -> Grammar(
            language,
            pattern("#[^\\n]*", "$TRIPLE_DOUBLE|$TRIPLE_SINGLE|$DOUBLE|$SINGLE"),
            PYTHON,
            types = true,
        )

        CodeLanguage.Shell -> Grammar(language, pattern("(?<![\\w$])#[^\\n]*", "$DOUBLE|$SINGLE"), SHELL, types = false)
    }
}

private fun cLike(strings: String): Regex = pattern("//[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|\\z)", strings)

/** 1번 묶음은 주석, 2번은 문자열, 3번은 숫자, 4번은 단어다. 앞 묶음이 먼저 맞으므로 문자열 안 주석 기호는 주석이 되지 않는다. */
private fun pattern(comment: String, strings: String): Regex = Regex("($comment)|($strings)|($NUMBER)|($WORD)")

private val GRAMMARS = java.util.concurrent.ConcurrentHashMap<CodeLanguage, Grammar>()

private const val GROUP_COMMENT = 1
private const val RESERVE = 16
private const val GROUP_STRING = 2
private const val GROUP_NUMBER = 3

private const val TRIPLE_DOUBLE = "\"\"\"[\\s\\S]*?(?:\"\"\"|\\z)"
private const val TRIPLE_SINGLE = "'''[\\s\\S]*?(?:'''|\\z)"
private const val DOUBLE = "\"(?:\\\\.|[^\"\\\\\\n])*\"?"
private const val SINGLE = "'(?:\\\\.|[^'\\\\\\n])*'?"
private const val CHAR = "'(?:\\\\.|[^'\\\\\\n]){0,8}'"
private const val BACKTICK = "`(?:\\\\[\\s\\S]|[^`\\\\])*`?"
private const val NUMBER = "\\b(?:0[xX][0-9a-fA-F_]+|\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)[fFlLuU]?\\b"
private const val WORD = "[A-Za-z_$][A-Za-z0-9_$]*"

private val KOTLIN = setOf(
    "package", "import", "class", "interface", "object", "fun", "val", "var", "if", "else", "when", "for", "while", "do",
    "return", "break", "continue", "try", "catch", "finally", "throw", "is", "in", "as", "null", "true", "false", "this",
    "super", "private", "public", "internal", "protected", "override", "open", "abstract", "data", "sealed", "enum",
    "companion", "suspend", "inline", "const", "lateinit", "typealias", "by", "init", "annotation", "inner", "operator",
)
private val JAVA = setOf(
    "package", "import", "class", "interface", "enum", "extends", "implements", "new", "if", "else", "switch", "case",
    "default", "for", "while", "do", "return", "break", "continue", "try", "catch", "finally", "throw", "throws", "null",
    "true", "false", "this", "super", "public", "private", "protected", "static", "final", "abstract", "void", "int", "long",
    "double", "float", "boolean", "char", "byte", "short", "var", "record", "instanceof", "synchronized",
)
private val JS = setOf(
    "const", "let", "var", "function", "return", "if", "else", "for", "while", "do", "switch", "case", "default", "break",
    "continue", "new", "class", "extends", "import", "export", "from", "async", "await", "try", "catch", "finally", "throw",
    "typeof", "instanceof", "in", "of", "null", "undefined", "true", "false", "this", "yield", "delete", "void",
)
private val TS = setOf(
    "interface", "type", "enum", "implements", "public", "private", "protected", "readonly", "as", "keyof", "declare",
    "namespace", "abstract", "string", "number", "boolean", "any", "unknown", "never",
)
private val JSON = setOf("true", "false", "null")
private val PYTHON = setOf(
    "def", "class", "return", "if", "elif", "else", "for", "while", "in", "not", "and", "or", "is", "import", "from", "as",
    "with", "try", "except", "finally", "raise", "lambda", "yield", "pass", "break", "continue", "None", "True", "False",
    "async", "await", "global", "nonlocal", "self",
)
private val SHELL = setOf(
    "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "until", "case", "esac", "function", "return",
    "export", "local", "echo", "cd", "sudo", "exit", "source",
)
