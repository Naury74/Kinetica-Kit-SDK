package com.naury.kinetica.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CodeHighlightTest {
    private fun kinds(code: String, language: CodeLanguage?): List<Pair<String, CodeTokenKind>> =
        highlightCode(code, language).map { code.substring(it.start, it.end) to it.kind }

    @Test
    fun `Kotlin 키워드와 문자열과 숫자와 주석과 함수와 타입을 나눈다`() {
        val tokens = kinds("val n: Int = max(1, 2) // 끝\n\"a\\\"b\"", CodeLanguage.Kotlin)
        assertThat(tokens).containsExactly(
            "val" to CodeTokenKind.Keyword,
            "Int" to CodeTokenKind.Type,
            "max" to CodeTokenKind.Function,
            "1" to CodeTokenKind.Number,
            "2" to CodeTokenKind.Number,
            "// 끝" to CodeTokenKind.Comment,
            "\"a\\\"b\"" to CodeTokenKind.Text,
        ).inOrder()
    }

    @Test
    fun `문자열 안 주석 기호는 주석이 아니다`() {
        val tokens = kinds("const url = \"https://a.b\"", CodeLanguage.JavaScript)
        assertThat(tokens).containsExactly("const" to CodeTokenKind.Keyword, "\"https://a.b\"" to CodeTokenKind.Text).inOrder()
    }

    @Test
    fun `닫히지 않은 블록 주석과 문자열은 끝까지 칠한다`() {
        assertThat(kinds("a /* 아직", CodeLanguage.Java).last()).isEqualTo("/* 아직" to CodeTokenKind.Comment)
        assertThat(kinds("x = 'abc\ny = 1", CodeLanguage.Python)).contains("'abc" to CodeTokenKind.Text)
        assertThat(kinds("s = \"\"\"여러\n줄", CodeLanguage.Python).last()).isEqualTo("\"\"\"여러\n줄" to CodeTokenKind.Text)
    }

    @Test
    fun `JSON은 키를 속성으로 값 문자열을 문자열로 칠한다`() {
        val tokens = kinds("{\"name\": \"kinetica\", \"ok\": true, \"n\": 1.5e3}", CodeLanguage.Json)
        assertThat(tokens).containsExactly(
            "\"name\"" to CodeTokenKind.Property,
            "\"kinetica\"" to CodeTokenKind.Text,
            "\"ok\"" to CodeTokenKind.Property,
            "true" to CodeTokenKind.Keyword,
            "\"n\"" to CodeTokenKind.Property,
            "1.5e3" to CodeTokenKind.Number,
        ).inOrder()
    }

    @Test
    fun `Shell 주석은 줄 앞이나 공백 뒤의 샵만이다`() {
        val tokens = kinds("echo \$HOME#x # 설명", CodeLanguage.Shell)
        assertThat(tokens).containsExactly("echo" to CodeTokenKind.Keyword, "# 설명" to CodeTokenKind.Comment).inOrder()
    }

    @Test
    fun `TypeScript는 JavaScript 키워드에 타입 키워드를 더한다`() {
        assertThat(kinds("interface A { b: string }", CodeLanguage.TypeScript)).containsExactly(
            "interface" to CodeTokenKind.Keyword,
            "A" to CodeTokenKind.Type,
            "string" to CodeTokenKind.Keyword,
        ).inOrder()
        assertThat(kinds("interface", CodeLanguage.JavaScript)).isEmpty()
    }

    @Test
    fun `언어 이름 줄임말을 알고 모르는 언어나 빈 코드는 강조하지 않는다`() {
        assertThat(codeLanguageOf("KT")).isEqualTo(CodeLanguage.Kotlin)
        assertThat(codeLanguageOf(" tsx ")).isEqualTo(CodeLanguage.TypeScript)
        assertThat(codeLanguageOf("bash")).isEqualTo(CodeLanguage.Shell)
        assertThat(codeLanguageOf("rust")).isNull()
        assertThat(codeLanguageOf(null)).isNull()
        assertThat(highlightCode("fn main()", null)).isEmpty()
        assertThat(highlightCode("", CodeLanguage.Kotlin)).isEmpty()
    }

    @Test
    fun `조각은 겹치지 않고 앞에서부터 나온다`() {
        val code = "fun a() { return \"x\" + 0x1F /* c */ }\n".repeat(200)
        val tokens = highlightCode(code, CodeLanguage.Kotlin)
        tokens.zipWithNext().forEach { (a, b) -> assertThat(a.end).isAtMost(b.start) }
        assertThat(tokens.all { it.start < it.end && it.end <= code.length }).isTrue()
    }

    @Test
    fun `줄 번호 글은 접힌 화면 줄만큼 빈 줄을 끼운다`() {
        assertThat(gutterText("a\nb\nc", null)).isEqualTo("1\n2\n3")
        assertThat(gutterText("a\nb\nc", intArrayOf(0, 3, 4))).isEqualTo("1\n\n\n2\n3")
        assertThat(gutterText("", null)).isEqualTo("1")
    }

    @Test
    fun `강조 결과에 종류별 색을 입힌다`() {
        val code = "val a = 1"
        val text = buildHighlighted(code, highlightCode(code, CodeLanguage.Kotlin), CodePalette.Light)
        assertThat(text.text).isEqualTo(code)
        assertThat(text.spanStyles.map { code.substring(it.start, it.end) }).containsExactly("val", "1").inOrder()
        assertThat(text.spanStyles.first().item.color).isEqualTo(CodePalette.Light.spanOf(CodeTokenKind.Keyword).color)
    }

    @Test
    fun `늘어나는 코드의 이어 하는 강조는 처음부터 한 강조와 같다`() {
        val samples = mapOf(
            CodeLanguage.Kotlin to
                "/* 블록\n주석 */\nfun main() {\n    val s = \"\"\"여러\n줄\"\"\"\n    println(\"x\" + 0x1F) // 끝\n    val c = 'a'\n}\n",
            CodeLanguage.JavaScript to "const a = `템플릿\n\${b}`;\nfunction f(x) { return x * 2.5e3 }\n// 주석\n",
            CodeLanguage.Json to "{\n  \"key\"\n  : \"value\",\n  \"n\": 12,\n  \"ok\": true\n}\n",
            CodeLanguage.Python to "def f(x):\n    '''문서\n    문자열'''\n    return x # 주석\n",
            CodeLanguage.Shell to "echo \"a#b\" # 주석\nexport X=1\n",
        )
        for ((language, code) in samples) {
            for (step in listOf(1, 3, 7)) {
                val cache = CodeHighlightCache()
                var end = 0
                while (end < code.length) {
                    end = minOf(code.length, end + step)
                    val partial = code.substring(0, end)
                    assertThat(cache.update(partial, language)).isEqualTo(highlightCode(partial, language))
                }
            }
        }
    }

    @Test
    fun `강조 캐시는 끝난 줄을 다시 찾지 않고 앞이 바뀌면 처음부터 찾는다`() {
        val cache = CodeHighlightCache()
        cache.update("val a = 1\nval b", CodeLanguage.Kotlin)
        cache.update("val a = 1\nval b = 2", CodeLanguage.Kotlin)
        assertThat(cache.lastScanFrom).isGreaterThan(0)
        cache.update("var a = 1\nval b = 2", CodeLanguage.Kotlin)
        assertThat(cache.lastScanFrom).isEqualTo(0)
        assertThat(cache.update("x", null)).isEmpty()
    }
}
