package com.naury.kinetica.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/** 블록 안 글 조각 */
@Immutable
internal sealed interface MdInline {
    data class Text(val text: String) : MdInline

    data class Code(val text: String) : MdInline

    data class Styled(val emphasis: MdEmphasis, val children: List<MdInline>) : MdInline

    data class Link(val url: String, val children: List<MdInline>) : MdInline
}

internal enum class MdEmphasis { Bold, Italic, Strike }

/**
 * 블록 안 글을 조각으로 나눈다. 굵게, 기울임, 취소선, 인라인 코드, 링크, `<주소>`와 맨 http 주소를 안다.
 * 닫는 짝이 없는 기호는 글자 그대로 둔다. [streaming]이면 글이 아직 오는 중이므로 닫히지 않은 강조와 인라인 코드를
 * 글 끝까지 열린 것으로 보고, 글 끝에 홀로 남은 기호와 주소가 덜 온 링크의 나머지는 그리지 않는다.
 */
internal fun parseInline(text: String, streaming: Boolean = false): List<MdInline> = InlineParser(text, streaming).parse(null, 0)

/** 링크를 누를 때 받을 주소 */
internal fun interface MdLinkHandler {
    fun open(url: String)
}

/** 인라인 조각을 그릴 모양 */
@Immutable
internal class MdInlineStyles(val code: SpanStyle, val link: TextLinkStyles)

/**
 * 조각을 [AnnotatedString]으로 만든다. 링크는 [LinkAnnotation]으로 붙여 TalkBack이 링크로 읽고 누를 수 있게 한다.
 * [handler]가 null이면 시스템이 주소를 연다.
 */
internal fun buildInline(nodes: List<MdInline>, styles: MdInlineStyles, handler: MdLinkHandler?): AnnotatedString =
    buildAnnotatedString { appendInline(nodes, styles, handler) }

private fun AnnotatedString.Builder.appendInline(nodes: List<MdInline>, styles: MdInlineStyles, handler: MdLinkHandler?) {
    for (node in nodes) {
        when (node) {
            is MdInline.Text -> append(node.text)

            is MdInline.Code -> withStyle(styles.code) { append(node.text) }

            is MdInline.Styled -> withStyle(spanOf(node.emphasis)) { appendInline(node.children, styles, handler) }

            is MdInline.Link -> {
                val link = if (handler == null) {
                    LinkAnnotation.Url(node.url, styles.link)
                } else {
                    LinkAnnotation.Clickable(node.url, styles.link, LinkInteractionListener { handler.open(node.url) })
                }
                withLink(link) { appendInline(node.children, styles, handler) }
            }
        }
    }
}

private fun spanOf(emphasis: MdEmphasis): SpanStyle = when (emphasis) {
    MdEmphasis.Bold -> BOLD
    MdEmphasis.Italic -> ITALIC
    MdEmphasis.Strike -> STRIKE
}

/** 조각의 글자만 이어 붙인다. */
internal fun List<MdInline>.plainText(): String = buildString { appendPlain(this@plainText) }

private fun StringBuilder.appendPlain(nodes: List<MdInline>) {
    for (node in nodes) {
        when (node) {
            is MdInline.Text -> append(node.text)
            is MdInline.Code -> append(node.text)
            is MdInline.Styled -> appendPlain(node.children)
            is MdInline.Link -> appendPlain(node.children)
        }
    }
}

/** 한 단계 파싱 중 모은 조각과 아직 조각으로 만들지 않은 글자 */
private class InlineFrame {
    val nodes = ArrayList<MdInline>()
    val buf = StringBuilder()

    fun flush() {
        if (buf.isNotEmpty()) {
            nodes += MdInline.Text(buf.toString())
            buf.setLength(0)
        }
    }

    fun add(node: MdInline) {
        flush()
        nodes += node
    }
}

private class InlineParser(private val s: String, private val streaming: Boolean) {
    private var i = 0

    /** 마지막으로 끝난 [parse]가 닫는 기호를 만났는지 */
    private var closedLast = false

    /** 닫는 짝을 찾지 못한 강조 기호와 그 여는 위치. 그 뒤 같은 기호도 짝이 없다고 보고 다시 찾지 않는다. */
    private val unclosedFrom = HashMap<String, Int>()

    /** 짝을 찾지 못한 인라인 코드 백틱 묶음 길이 */
    private val unclosedCode = HashSet<Int>()

    /** [closer]를 만날 때까지 파싱한다. 만나지 못하면 글 끝까지가 이 단계다. */
    fun parse(closer: String?, depth: Int): List<MdInline> {
        val frame = InlineFrame()
        var closed = false
        while (!closed && i < s.length) closed = step(frame, closer, depth)
        frame.flush()
        closedLast = closed
        return frame.nodes
    }

    /** 글자 하나나 묶음 하나를 읽는다. [closer]로 이 단계가 닫히면 true다. */
    private fun step(frame: InlineFrame, closer: String?, depth: Int): Boolean {
        val c = s[i]
        when {
            c == '\\' && i + 1 < s.length && s[i + 1] in ESCAPABLE -> {
                frame.buf.append(s[i + 1])
                i += 2
            }

            c == '`' -> code(frame)

            c in DELIMITERS -> return delimiter(frame, c, closer, depth)

            else -> link(c, depth)?.let(frame::add) ?: run {
                frame.buf.append(c)
                i++
            }
        }
        return false
    }

    private fun delimiter(frame: InlineFrame, c: Char, closer: String?, depth: Int): Boolean {
        val run = runLength(c)
        if (closer != null && closesHere(closer, c, run)) {
            i += closer.length
            return true
        }
        val opener = if (depth < MAX_DEPTH) openerAt(c, run) else null
        if (opener != null && (streaming || mayClose(opener))) {
            val start = i
            i += opener.length
            val children = parse(opener, depth + 1)
            if (closedLast || streaming) {
                frame.add(MdInline.Styled(emphasisOf(opener), children))
                return false
            }
            // 완성된 글에서 짝이 없는 여는 기호는 강조가 아니라 글자다. 그 뒤부터 다시 읽는다.
            unclosedFrom.putIfAbsent(opener, start)
            frame.buf.append(opener)
            i = start + opener.length
        } else if (opener != null) {
            frame.buf.append(opener)
            i += opener.length
        } else {
            if (!(streaming && danglingAtEnd(run))) frame.buf.append(s, i, i + run)
            i += run
        }
        return false
    }

    /** 뒤에 [opener]를 닫을 수 있는 기호가 남아 있는지 대강 본다. 짝을 찾지 못한 적이 있는 기호는 다시 찾지 않는다. */
    private fun mayClose(opener: String): Boolean {
        val failed = unclosedFrom[opener]
        if (failed != null && failed < i) return false
        return s.indexOf(opener, i + opener.length) >= 0
    }

    private fun runLength(c: Char): Int {
        var k = i
        while (k < s.length && s[k] == c) k++
        return k - i
    }

    private fun closesHere(closer: String, c: Char, run: Int): Boolean {
        if (closer[0] != c || run < closer.length) return false
        if (i == 0 || s[i - 1].isWhitespace()) return false
        val after = i + closer.length
        return c != '_' || after >= s.length || !s[after].isLetterOrDigit()
    }

    private fun openerAt(c: Char, run: Int): String? {
        val next = i + run
        if (next >= s.length || s[next].isWhitespace()) return null
        if (c == '_' && i > 0 && s[i - 1].isLetterOrDigit()) return null
        return when {
            c == '~' -> if (run >= 2) "~~" else null
            run >= 2 -> "$c$c"
            else -> "$c"
        }
    }

    /** 글 끝에 공백 뒤로 홀로 남은 기호 묶음. 스트리밍 중 강조가 막 시작된 것이라 그리지 않는다. */
    private fun danglingAtEnd(run: Int): Boolean = i + run == s.length && (i == 0 || s[i - 1].isWhitespace())

    /**
     * 백틱 묶음에서 시작하는 인라인 코드. 닫는 묶음이 없으면 백틱을 글자로 둔다.
     * 스트리밍 중이면 닫는 묶음이 없을 때 끝까지 코드로 보고, 글 끝의 백틱뿐이면 막 시작된 것이라 그리지 않는다.
     */
    private fun code(frame: InlineFrame) {
        val run = runLength('`')
        val open = i + run
        if (!streaming && run in unclosedCode) {
            frame.buf.append(s, i, open)
            i = open
            return
        }
        var j = open
        while (j < s.length) {
            if (s[j] == '`') {
                var k = j
                while (k < s.length && s[k] == '`') k++
                if (k - j == run) {
                    i = k
                    frame.add(MdInline.Code(trimCode(s.substring(open, j))))
                    return
                }
                j = k
            } else {
                j++
            }
        }
        if (streaming) {
            i = s.length
            if (open < s.length) frame.add(MdInline.Code(s.substring(open)))
        } else {
            unclosedCode += run
            frame.buf.append(s, i, open)
            i = open
        }
    }

    private fun link(c: Char, depth: Int): MdInline? = when (c) {
        '[' -> bracketLink(depth)
        '<' -> angleLink()
        'h' -> bareLink()
        else -> null
    }

    private fun bracketLink(depth: Int): MdInline? {
        if (depth >= MAX_DEPTH) return null
        val close = matching(i, '[', ']') ?: return null
        if (close + 1 >= s.length || s[close + 1] != '(') return null
        val end = matching(close + 1, '(', ')')
        if (end == null && !streaming) return null
        val label = InlineParser(s.substring(i + 1, close), streaming = false).parse(null, depth + 1)
        if (end == null) {
            // 주소가 아직 다 오지 않았다. 이름만 글자로 보여 주고 나머지는 닫힐 때까지 숨긴다.
            i = s.length
            return textOnly(label)
        }
        val url = s.substring(close + 2, end).trim().substringBefore(' ')
        i = end + 1
        return if (url.isEmpty()) textOnly(label) else MdInline.Link(url, label)
    }

    private fun angleLink(): MdInline? {
        val end = s.indexOf('>', i + 1)
        if (end < 0) return null
        val url = s.substring(i + 1, end)
        if (!isWebAddress(url, 0) || url.any { it.isWhitespace() }) return null
        i = end + 1
        return MdInline.Link(url, listOf(MdInline.Text(url)))
    }

    private fun bareLink(): MdInline? {
        if (!isWebAddress(s, i)) return null
        if (i > 0 && s[i - 1].isLetterOrDigit()) return null
        var end = i
        while (end < s.length && isUrlChar(s[end])) end++
        end = trimUrlEnd(s, i, end)
        val url = s.substring(i, end)
        if (url.length <= url.indexOf("//") + 2) return null
        i = end
        return MdInline.Link(url, listOf(MdInline.Text(url)))
    }

    /** [from]의 여는 괄호와 짝인 닫는 괄호 위치. 없으면 null이다. */
    private fun matching(from: Int, open: Char, close: Char): Int? {
        var level = 0
        var k = from
        while (k < s.length) {
            when (s[k]) {
                '\\' -> k++
                open -> level++
                close -> if (--level == 0) return k
            }
            k++
        }
        return null
    }
}

/** 주소 끝의 문장부호, 강조 기호, 짝 없는 닫는 괄호를 뺀 끝 위치. GFM 확장 자동 링크 규칙을 따른다. */
private fun trimUrlEnd(s: String, start: Int, from: Int): Int {
    var end = from
    while (end > start) {
        val last = s[end - 1]
        val drop = last in TRAILING_PUNCTUATION || (last == ')' && unbalancedClose(s, start, end))
        if (!drop) break
        end--
    }
    return end
}

private fun unbalancedClose(s: String, start: Int, end: Int): Boolean {
    var open = 0
    var close = 0
    for (k in start until end) {
        when (s[k]) {
            '(' -> open++
            ')' -> close++
        }
    }
    return close > open
}

private fun emphasisOf(opener: String): MdEmphasis = when {
    opener[0] == '~' -> MdEmphasis.Strike
    opener.length == 2 -> MdEmphasis.Bold
    else -> MdEmphasis.Italic
}

/** 앞뒤가 모두 공백이면 하나씩 뗀다. 공백만 있는 코드는 그대로 둔다. */
private fun trimCode(code: String): String {
    val padded = code.length >= 2 && code.first() == ' ' && code.last() == ' '
    return if (padded && code.isNotBlank()) code.substring(1, code.length - 1) else code
}

private fun textOnly(label: List<MdInline>): MdInline = MdInline.Text(label.plainText())

private fun isWebAddress(text: String, at: Int): Boolean = text.startsWith("http://", at) || text.startsWith("https://", at)

/** 주소에 쓰는 ASCII 글자인지. 주소 뒤에 붙은 한국어 조사 같은 글자는 주소가 아니다. */
private fun isUrlChar(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in URL_SYMBOLS

private val BOLD = SpanStyle(fontWeight = FontWeight.Bold)
private val ITALIC = SpanStyle(fontStyle = FontStyle.Italic)
private val STRIKE = SpanStyle(textDecoration = TextDecoration.LineThrough)
private const val DELIMITERS = "*_~"
private const val ESCAPABLE = "\\`*_{}[]()#+-.!~>|<"
private const val TRAILING_PUNCTUATION = ".,;:!?'*_~"
private const val URL_SYMBOLS = "-._~:/?#@!\$&'()*+,;=%"
private const val MAX_DEPTH = 8
