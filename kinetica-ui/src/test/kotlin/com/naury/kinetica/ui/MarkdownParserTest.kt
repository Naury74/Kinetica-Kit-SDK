package com.naury.kinetica.ui

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun `제목 세 단계와 문단을 나눈다`() {
        val blocks = parseMarkdown("# 하나\n## 둘\n### 셋\n#### 넷\n본문 첫 줄\n둘째 줄\n\n다음 문단")
        assertThat(blocks).containsExactly(
            MdBlock.Heading(1, "하나"),
            MdBlock.Heading(2, "둘"),
            MdBlock.Heading(3, "셋"),
            MdBlock.Heading(4, "넷"),
            MdBlock.Paragraph("본문 첫 줄\n둘째 줄"),
            MdBlock.Paragraph("다음 문단"),
        ).inOrder()
    }

    @Test
    fun `샵 뒤에 공백이 없으면 제목이 아니다`() {
        assertThat(parseMarkdown("#해시태그")).containsExactly(MdBlock.Paragraph("#해시태그"))
        assertThat(parseMarkdown("## 제목 ##")).containsExactly(MdBlock.Heading(2, "제목"))
    }

    @Test
    fun `코드 울타리는 언어와 내용을 담고 닫히지 않으면 끝까지 코드다`() {
        val closed = parseMarkdown("앞\n```kotlin\nval a = 1\n\nval b = 2\n```\n뒤")
        assertThat(closed).containsExactly(
            MdBlock.Paragraph("앞"),
            MdBlock.Code("kotlin", "val a = 1\n\nval b = 2", closed = true),
            MdBlock.Paragraph("뒤"),
        ).inOrder()
        val open = parseMarkdown("```py\nprint(1)\n# 아직")
        assertThat(open).containsExactly(MdBlock.Code("py", "print(1)\n# 아직", closed = false))
    }

    @Test
    fun `스트리밍 중 반쯤 온 닫는 울타리는 코드에 넣지 않는다`() {
        assertThat(parseMarkdown("```js\nlet a\n``")).containsExactly(MdBlock.Code("js", "let a", closed = false))
        assertThat(parseMarkdown("```")).containsExactly(MdBlock.Code(null, "", closed = false))
    }

    @Test
    fun `목록은 순서와 두 단계 중첩과 이어지는 줄을 안다`() {
        val blocks = parseMarkdown("- 사과\n  - 홍옥\n    - 더 깊음\n- 배\n  이어지는 줄\n3. 셋\n4) 넷")
        assertThat(blocks).containsExactly(
            MdBlock.Bullets(
                listOf(
                    MdListItem(0, ordered = false, number = 0, text = "사과"),
                    MdListItem(1, ordered = false, number = 0, text = "홍옥"),
                    MdListItem(1, ordered = false, number = 0, text = "더 깊음"),
                    MdListItem(0, ordered = false, number = 0, text = "배\n이어지는 줄"),
                    MdListItem(0, ordered = true, number = 3, text = "셋"),
                    MdListItem(0, ordered = true, number = 4, text = "넷"),
                ),
            ),
        )
    }

    @Test
    fun `빈 줄은 목록을 끝내고 문단이 목록을 끊지 않으면 이어진다`() {
        val blocks = parseMarkdown("- 하나\n\n- 둘\n# 제목")
        assertThat(blocks).hasSize(3)
        assertThat(blocks[2]).isEqualTo(MdBlock.Heading(1, "제목"))
    }

    @Test
    fun `인용은 안쪽을 다시 블록으로 나누고 수평선을 안다`() {
        val blocks = parseMarkdown("> **주의**\n> - 하나\n\n---\n***\n- - -")
        assertThat(blocks).containsExactly(
            MdBlock.Quote(listOf(MdBlock.Paragraph("**주의**"), MdBlock.Bullets(listOf(MdListItem(0, false, 0, "하나"))))),
            MdBlock.Rule,
            MdBlock.Rule,
            MdBlock.Rule,
        ).inOrder()
    }

    @Test
    fun `아주 깊은 인용도 스택을 넘지 않는다`() {
        val blocks = parseMarkdown(">".repeat(5_000) + " 끝")
        assertThat(blocks).isNotEmpty()
    }

    @Test
    fun `빈 글과 공백뿐인 글은 블록이 없다`() {
        assertThat(parseMarkdown("")).isEmpty()
        assertThat(parseMarkdown("\n \n\t\n")).isEmpty()
    }

    @Test
    fun `캐시는 뒤에 글이 붙으면 끝난 앞 블록을 다시 파싱하지 않는다`() {
        val cache = MarkdownCache()
        val first = cache.update("# 제목\n\n첫 문단\n\n```kotlin\nval a")
        assertThat(cache.lastParseFrom).isEqualTo(0)
        val second = cache.update("# 제목\n\n첫 문단\n\n```kotlin\nval a = 1\n```\n끝")
        assertThat(cache.lastParseFrom).isGreaterThan(0)
        assertThat(second[0]).isSameInstanceAs(first[0])
        assertThat(second[1]).isSameInstanceAs(first[1])
        assertThat(second).isEqualTo(parseMarkdown("# 제목\n\n첫 문단\n\n```kotlin\nval a = 1\n```\n끝"))
    }

    @Test
    fun `캐시를 써도 한 글자씩 스트리밍한 결과가 한 번에 파싱한 것과 같다`() {
        val text = """
            # 답변
            문단 **굵게** 와 `코드`.
            #해시태그는 문단
            - 하나
              - 둘
            1. 첫째
            > 인용
            > 이어짐

            ---
            ```kotlin
            fun main() = println("hi")
            ```
            마지막 [링크](https://example.com)
        """.trimIndent()
        val cache = MarkdownCache()
        for (end in 1..text.length) {
            val partial = text.substring(0, end)
            assertThat(cache.update(partial)).isEqualTo(parseMarkdown(partial))
        }
    }

    @Test
    fun `앞부분이 바뀌면 처음부터 다시 파싱한다`() {
        val cache = MarkdownCache()
        cache.update("하나\n\n둘\n\n")
        val blocks = cache.update("셋\n\n둘")
        assertThat(cache.lastParseFrom).isEqualTo(0)
        assertThat(blocks).containsExactly(MdBlock.Paragraph("셋"), MdBlock.Paragraph("둘")).inOrder()
    }

    @Test
    fun `인라인 강조와 코드와 링크를 나눈다`() {
        val nodes = parseInline("a **굵게** *기울임* ~~취소~~ `x*y` [링크](https://k.io \"제목\")")
        assertThat(nodes).containsExactly(
            MdInline.Text("a "),
            MdInline.Styled(MdEmphasis.Bold, listOf(MdInline.Text("굵게"))),
            MdInline.Text(" "),
            MdInline.Styled(MdEmphasis.Italic, listOf(MdInline.Text("기울임"))),
            MdInline.Text(" "),
            MdInline.Styled(MdEmphasis.Strike, listOf(MdInline.Text("취소"))),
            MdInline.Text(" "),
            MdInline.Code("x*y"),
            MdInline.Text(" "),
            MdInline.Link("https://k.io", listOf(MdInline.Text("링크"))),
        ).inOrder()
    }

    @Test
    fun `닫히지 않은 굵게와 인라인 코드는 끝까지 열려 있다`() {
        assertThat(parseInline("앞 **굵은 글")).containsExactly(
            MdInline.Text("앞 "),
            MdInline.Styled(MdEmphasis.Bold, listOf(MdInline.Text("굵은 글"))),
        ).inOrder()
        assertThat(parseInline("앞 `코드")).containsExactly(MdInline.Text("앞 "), MdInline.Code("코드")).inOrder()
    }

    @Test
    fun `글 끝에 막 시작한 기호와 덜 온 링크 주소는 그리지 않는다`() {
        assertThat(parseInline("앞 **").plainText()).isEqualTo("앞 ")
        assertThat(parseInline("앞 `").plainText()).isEqualTo("앞 ")
        assertThat(parseInline("[문서](https://exa")).containsExactly(MdInline.Text("문서"))
    }

    @Test
    fun `곱셈 별표와 낱말 속 밑줄은 강조가 아니다`() {
        assertThat(parseInline("2 * 3 = 6").plainText()).isEqualTo("2 * 3 = 6")
        assertThat(parseInline("snake_case_name")).containsExactly(MdInline.Text("snake_case_name"))
        assertThat(parseInline("\\*그대로\\*")).containsExactly(MdInline.Text("*그대로*"))
    }

    @Test
    fun `굵은 기울임과 강조 안 링크를 중첩한다`() {
        val nodes = parseInline("***둘 다*** **[안](https://a.b)**")
        assertThat(nodes[0]).isEqualTo(
            MdInline.Styled(MdEmphasis.Bold, listOf(MdInline.Styled(MdEmphasis.Italic, listOf(MdInline.Text("둘 다"))))),
        )
        assertThat(nodes[2]).isEqualTo(
            MdInline.Styled(MdEmphasis.Bold, listOf(MdInline.Link("https://a.b", listOf(MdInline.Text("안"))))),
        )
    }

    @Test
    fun `맨 주소와 꺾쇠 주소를 링크로 만들고 끝 문장부호는 뺀다`() {
        val nodes = parseInline("보기: https://kinetica.dev/docs. 또는 <https://a.io>")
        assertThat(nodes).contains(MdInline.Link("https://kinetica.dev/docs", listOf(MdInline.Text("https://kinetica.dev/docs"))))
        assertThat(nodes).contains(MdInline.Link("https://a.io", listOf(MdInline.Text("https://a.io"))))
    }

    @Test
    fun `깊게 중첩한 기호도 스택을 넘지 않는다`() {
        assertThat(parseInline("*a ".repeat(10_000)).plainText()).isNotEmpty()
        assertThat(parseInline("[".repeat(5_000) + "x" + "]".repeat(5_000) + "(u)").plainText()).isNotEmpty()
    }

    @Test
    fun `AnnotatedString에 굵기와 링크 주석을 붙인다`() {
        val styles = MdInlineStyles(SpanStyle(), TextLinkStyles())
        val opened = mutableListOf<String>()
        val text = buildInline(parseInline("**굵게** [열기](https://k.io)"), styles) { opened += it }
        assertThat(text.text).isEqualTo("굵게 열기")
        assertThat(text.spanStyles.single().item.fontWeight).isEqualTo(FontWeight.Bold)
        val link = text.getLinkAnnotations(0, text.length).single()
        assertThat((link.item as LinkAnnotation.Clickable).tag).isEqualTo("https://k.io")
        (link.item as LinkAnnotation.Clickable).linkInteractionListener?.onClick(link.item)
        assertThat(opened).containsExactly("https://k.io")
        val system = buildInline(parseInline("[열기](https://k.io)"), styles, null)
        assertThat((system.getLinkAnnotations(0, system.length).single().item as LinkAnnotation.Url).url).isEqualTo("https://k.io")
    }
}
