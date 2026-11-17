package com.naury.kinetica.ui

import androidx.compose.runtime.Immutable

/** [KMarkdownText]가 그리는 마크다운 블록 하나. 파서가 만들고 블록마다 따로 구성한다. */
@Immutable
internal sealed interface MdBlock {
    /** 제목. [level]은 1..6이고 4 이상은 3단계 모양으로 그린다. */
    data class Heading(val level: Int, val text: String) : MdBlock

    /** 문단. 줄 바꿈은 그대로 둔다. */
    data class Paragraph(val text: String) : MdBlock

    /** 코드 블록. [closed]가 false면 닫는 울타리가 아직 오지 않아 글 끝까지 코드로 본 것이다. */
    data class Code(val language: String?, val code: String, val closed: Boolean) : MdBlock

    /** 인용. 안쪽은 블록으로 다시 파싱한다. */
    data class Quote(val blocks: List<MdBlock>) : MdBlock

    /** 순서 목록과 비순서 목록. 항목마다 순서 여부를 따로 가진다. */
    data class Bullets(val items: List<MdListItem>) : MdBlock

    /** 수평선 */
    data object Rule : MdBlock
}

/** 목록 항목. [level]은 0(바깥)이나 1(안쪽)이다. [number]는 순서 목록에서 쓴 숫자다. */
@Immutable
internal data class MdListItem(val level: Int, val ordered: Boolean, val number: Int, val text: String)

/** 파싱한 블록과 그 블록을 끝낸 줄이 시작하는 글자 위치. 캐시가 앞 블록을 다시 쓸지 정할 때 쓴다. */
internal class MdEntry(val block: MdBlock, val endOffset: Int)

/** 마크다운 전체를 블록으로 나눈다. 닫히지 않은 코드 울타리는 글 끝까지 코드다. */
internal fun parseMarkdown(text: String): List<MdBlock> = parseMarkdownEntries(normalizeLineBreaks(text), 0).map { it.block }

/** CRLF와 CR 줄 끝을 LF로 바꾼다. 줄 끝 \r이 남으면 줄 끝을 `$`로 맞추는 블록 문법이 맞지 않는다. */
internal fun normalizeLineBreaks(text: String): String =
    if (text.indexOf('\r') < 0) text else text.replace("\r\n", "\n").replace('\r', '\n')

/** [text]의 [from] 위치(줄 시작)부터 블록으로 나눈다. [text]는 줄 끝을 LF로 맞춘 글이고 돌려주는 위치는 [text] 기준이다. */
internal fun parseMarkdownEntries(text: String, from: Int): List<MdEntry> {
    val lines = ArrayList<String>()
    val starts = ArrayList<Int>()
    var start = from
    while (true) {
        val end = text.indexOf('\n', start)
        starts += start
        if (end < 0) {
            lines += text.substring(start)
            break
        }
        lines += text.substring(start, end)
        start = end + 1
    }
    val parser = BlockParser(lines)
    return parser.parse().map { (block, endLine) ->
        MdEntry(block, if (endLine < starts.size) starts[endLine] else text.length)
    }
}

/**
 * 스트리밍으로 뒤에 글이 붙을 때 앞에서 끝난 블록을 다시 파싱하지 않는 캐시.
 * 블록을 끝낸 다음 줄까지 줄 바꿈으로 완성된 블록만 그대로 두고, 그 뒤부터 다시 파싱한다. 앞부분이 바뀌면 처음부터 파싱한다.
 */
internal class MarkdownCache {
    private var source = ""
    private var entries: List<MdEntry> = emptyList()

    /** 마지막으로 다시 파싱을 시작한 글자 위치. 테스트가 캐시를 썼는지 확인할 때 본다. */
    var lastParseFrom: Int = 0
        private set

    fun update(raw: String): List<MdBlock> {
        val text = normalizeLineBreaks(raw)
        if (text != source || entries.isEmpty()) {
            val keep = if (source.isNotEmpty() && text.startsWith(source)) stableCount() else 0
            val from = if (keep == 0) 0 else entries[keep - 1].endOffset
            lastParseFrom = from
            entries = entries.subList(0, keep) + parseMarkdownEntries(text, from)
            source = text
        }
        return entries.map { it.block }
    }

    private fun stableCount(): Int {
        val lastBreak = source.lastIndexOf('\n')
        var count = 0
        while (count < entries.size && entries[count].endOffset <= lastBreak) count++
        return count
    }
}

private class BlockParser(private val lines: List<String>, private val depth: Int = 0) {
    private var i = 0
    private val out = ArrayList<Pair<MdBlock, Int>>()

    fun parse(): List<Pair<MdBlock, Int>> {
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                i++
                continue
            }
            val block = fence(line) ?: heading(line) ?: rule(line) ?: quote(line) ?: bullets(line) ?: paragraph()
            out += block to i
        }
        return out
    }

    private fun fence(line: String): MdBlock? {
        // 목록 항목 안에서 들여쓴 울타리도 코드 블록으로 받는다. 목록은 그런 줄에서 끝난다.
        val open = fenceOf(line, anyIndent = true) ?: return null
        i++
        val body = ArrayList<String>()
        var closed = false
        while (i < lines.size) {
            val current = lines[i]
            i++
            if (closesFence(current, open)) {
                closed = true
                break
            }
            body += current.dropIndent(open.indent)
        }
        // 글 끝에서 아직 다 오지 않은 닫는 울타리(` 몇 개)는 코드로 보이지 않게 뺀다.
        if (!closed && body.isNotEmpty() && isPartialFence(body.last(), open)) body.removeAt(body.lastIndex)
        return MdBlock.Code(open.language, body.joinToString("\n"), closed)
    }

    private fun heading(line: String): MdBlock? {
        val match = HEADING.matchEntire(line) ?: return null
        i++
        return MdBlock.Heading(match.groupValues[1].length, match.groupValues[2].trim())
    }

    private fun rule(line: String): MdBlock? {
        if (!RULE.matches(line)) return null
        i++
        return MdBlock.Rule
    }

    private fun quote(line: String): MdBlock? {
        // 인용 안 인용이 아주 깊으면 재귀가 스택을 넘을 수 있어 일정 깊이부터는 문단으로 본다.
        if (depth >= MAX_QUOTE_DEPTH || QUOTE.matchEntire(line) == null) return null
        val inner = ArrayList<String>()
        while (i < lines.size) {
            val match = QUOTE.matchEntire(lines[i]) ?: break
            inner += match.groupValues[1]
            i++
        }
        return MdBlock.Quote(BlockParser(inner, depth + 1).parse().map { it.first })
    }

    private fun bullets(line: String): MdBlock? {
        val first = markerOf(line) ?: return null
        val base = first.indent
        val items = ArrayList<MdListItem>()
        while (i < lines.size && lines[i].isNotBlank() && addListLine(items, base, lines[i])) i++
        return MdBlock.Bullets(items)
    }

    /** 목록 줄 하나를 [items]에 더한다. 목록을 끝내는 줄이면 더하지 않고 false를 돌려준다. */
    private fun addListLine(items: MutableList<MdListItem>, base: Int, line: String): Boolean {
        val marker = markerOf(line)
        if (marker != null && !RULE.matches(line)) {
            val level = if (marker.indent >= base + NEST_INDENT) 1 else 0
            items += MdListItem(level, marker.ordered, marker.number, marker.text)
            return true
        }
        // 항목 안 들여쓴 코드 울타리를 항목 글로 붙이면 코드 안 빈 줄에서 목록이 끝나고 닫는 울타리가 새 코드를 연다.
        if (fenceOf(line, anyIndent = true) != null || (indentOf(line) < NEST_INDENT && startsBlock(line))) return false
        val last = items.removeAt(items.lastIndex)
        items += last.copy(text = last.text + "\n" + line.trim())
        return true
    }

    private fun paragraph(): MdBlock {
        val text = StringBuilder(lines[i].trim())
        i++
        while (i < lines.size) {
            val current = lines[i]
            if (current.isBlank() || startsBlock(current) || markerOf(current) != null) break
            text.append('\n').append(current.trim())
            i++
        }
        return MdBlock.Paragraph(text.toString())
    }
}

/** 문단이나 목록의 이어지는 줄을 끊고 새 블록을 시작하는 줄인지 */
private fun startsBlock(line: String): Boolean = fenceOf(line) != null || HEADING.matches(line) || RULE.matches(line) || QUOTE.matches(line)

private class Fence(val char: Char, val length: Int, val indent: Int, val language: String?)

private fun fenceOf(line: String, anyIndent: Boolean = false): Fence? {
    val match = (if (anyIndent) INDENTED_FENCE else FENCE).matchEntire(line) ?: return null
    val (indent, run, rest) = match.destructured
    val info = rest.trim()
    // 백틱 울타리의 정보 문자열에는 백틱이 들어갈 수 없다.
    if (run[0] == '`' && info.contains('`')) return null
    return Fence(run[0], run.length, indentOf(indent), info.substringBefore(' ').ifEmpty { null })
}

private fun closesFence(line: String, open: Fence): Boolean {
    val trimmed = line.trim()
    return trimmed.length >= open.length && trimmed.all { it == open.char } && indentOf(line) < open.indent + CODE_INDENT
}

private fun isPartialFence(line: String, open: Fence): Boolean {
    val trimmed = line.trim()
    return trimmed.isNotEmpty() && trimmed.length < open.length && trimmed.all { it == open.char }
}

private class Marker(val indent: Int, val ordered: Boolean, val number: Int, val text: String)

private fun markerOf(line: String): Marker? {
    val match = LIST.matchEntire(line) ?: return null
    val (indent, digits, text) = match.destructured
    val number = if (digits.isEmpty()) 0 else digits.toIntOrNull() ?: 0
    return Marker(indentOf(indent), digits.isNotEmpty(), number, text.trim())
}

/** 줄 앞 공백 폭. 탭은 4칸으로 센다. */
private fun indentOf(line: String): Int {
    var width = 0
    for (c in line) {
        when (c) {
            ' ' -> width++
            '\t' -> width += TAB_WIDTH
            else -> return width
        }
    }
    return width
}

/** 앞 공백을 폭 [count]까지 뗀다. 탭은 4칸으로 센다. */
private fun String.dropIndent(count: Int): String {
    var k = 0
    var width = 0
    while (k < length && width < count) {
        width += when (this[k]) {
            ' ' -> 1
            '\t' -> TAB_WIDTH
            else -> break
        }
        k++
    }
    return substring(k)
}

private val FENCE = Regex("^( {0,3})(`{3,}|~{3,})(.*)$")
private val INDENTED_FENCE = Regex("^([ \\t]*)(`{3,}|~{3,})(.*)$")
private val HEADING = Regex("^ {0,3}(#{1,6})(?:[ \\t]+(.*?))??(?:[ \\t]+#+)?[ \\t]*$")
private val RULE = Regex("^ {0,3}([-*_])(?:[ \\t]*\\1){2,}[ \\t]*$")
private val QUOTE = Regex("^ {0,3}> ?(.*)$")
private val LIST = Regex("^([ \\t]*)(?:[-*+]|(\\d{1,9})[.)])[ \\t]+(.*)$")
private const val NEST_INDENT = 2
private const val MAX_QUOTE_DEPTH = 4
private const val CODE_INDENT = 4
private const val TAB_WIDTH = 4
