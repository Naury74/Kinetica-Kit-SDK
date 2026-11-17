package com.naury.kinetica.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 입력란에 들어간 멘션 하나. [start]..[end](끝 제외)는 글 안의 위치이고, 그 자리 글은 [trigger]와 [label]을 이은 것이다.
 *
 * @property id 고른 후보의 [KMentionCandidate.id]
 */
@Immutable
public data class KMention(val id: String, val label: String, val trigger: Char, val start: Int, val end: Int)

/**
 * 멘션 제안 팝업의 후보.
 *
 * @property id 후보를 가리는 값. 고르면 [KMention.id]가 된다.
 * @property label 글에 들어갈 이름. 트리거 문자 뒤에 붙는다.
 * @property supporting 이름 아래에 보일 팀이나 설명
 * @property avatar 있으면 이름 앞에 아바타를 그린다. 없으면 트리거 문자를 그린다.
 */
@Immutable
public data class KMentionCandidate(val id: String, val label: String, val supporting: String? = null, val avatar: KAvatarData? = null)

/** 커서 앞에서 찾은 입력 중인 멘션. [start]는 트리거 문자 위치, [query]는 그 뒤부터 커서까지의 글이다. */
@Immutable
internal data class MentionQuery(val trigger: Char, val start: Int, val query: String)

/** 글을 고친 결과. */
internal class MentionEdit(val value: TextFieldValue, val mentions: List<KMention>)

/**
 * [cursor] 바로 앞의 입력 중인 멘션을 찾는다. 커서에서 뒤로 가며 공백을 만나기 전에 트리거 문자가 나오고, 그 문자가 글 맨 앞이거나
 * 공백 뒤이며 이미 들어간 멘션 안이 아니면 찾은 것이다. 트리거 뒤 글이 [maxLength]보다 길면 찾지 않는다.
 */
internal fun findMentionQuery(
    text: String,
    cursor: Int,
    triggers: Set<Char>,
    mentions: List<KMention>,
    maxLength: Int = MAX_QUERY_LENGTH,
): MentionQuery? {
    if (cursor !in 0..text.length) return null
    var i = cursor - 1
    while (i >= 0 && cursor - i <= maxLength + 1) {
        val c = text[i]
        if (c in triggers) {
            val boundary = i == 0 || text[i - 1].isWhitespace()
            val inside = mentions.any { i >= it.start && i < it.end }
            return if (boundary && !inside) MentionQuery(c, i, text.substring(i + 1, cursor)) else null
        }
        if (c.isWhitespace()) return null
        i--
    }
    return null
}

/**
 * 입력란이 보낸 새 값 [new]를 멘션에 맞춰 고친다.
 *
 * 바뀐 부분은 앞뒤에서 같은 글을 걷어 내고 남은 구간으로 본다. 같은 글자가 이어지면 어느 쪽을 고쳤는지 글만으로는 알 수 없으므로
 * 앞에서 걷어 내는 양을 고치기 전후 커서 가운데 앞쪽 위치까지로 제한해 커서 자리를 고친 것으로 본다.
 * 그 구간이 멘션을 지우면(한 글자라도) 멘션 전체를 한 번에 지운다. 멘션 안에 글을 넣거나 바꾸면 그 멘션은 평범한 글이 된다.
 * 바뀐 곳 뒤의 멘션은 늘거나 준 만큼 위치를 옮긴다.
 * 글은 그대로이고 커서만 움직였으면 멘션 안에 놓인 커서를 움직인 방향의 멘션 끝으로 보낸다. 왼쪽으로 움직였으면 멘션 앞이다.
 */
internal fun applyMentionEdit(old: TextFieldValue, mentions: List<KMention>, new: TextFieldValue): MentionEdit {
    if (old.text == new.text) {
        val left = new.selection.start < old.selection.start
        return MentionEdit(new.copy(selection = snapOutOfMentions(new.selection, mentions, left)), mentions)
    }
    val a = old.text
    val b = new.text
    val shorter = minOf(a.length, b.length)
    val prefixLimit = minOf(old.selection.min, new.selection.min, shorter).coerceAtLeast(0)
    var prefix = 0
    while (prefix < prefixLimit && a[prefix] == b[prefix]) prefix++
    var suffix = 0
    while (suffix < shorter - prefix && a[a.length - 1 - suffix] == b[b.length - 1 - suffix]) suffix++
    val oldEnd = a.length - suffix
    val inserted = b.length - suffix - prefix
    val removed = oldEnd - prefix
    val touched = mentions.filter { m ->
        if (removed > 0) prefix < m.end && oldEnd > m.start else prefix > m.start && prefix < m.end
    }
    if (touched.isNotEmpty() && inserted == 0) return deleteWhole(a, mentions, touched, prefix, oldEnd)
    val delta = inserted - removed
    val kept = mentions.filter { it !in touched }.map { if (it.start >= oldEnd) it.shift(delta) else it }
    return MentionEdit(new.copy(selection = snapOutOfMentions(new.selection, kept, left = false)), kept)
}

/** 지운 구간 [from]..[to]가 멘션에 걸치면 그 멘션까지 넓혀 지운다. 커서는 지운 자리에 둔다. */
private fun deleteWhole(text: String, mentions: List<KMention>, touched: List<KMention>, from: Int, to: Int): MentionEdit {
    val start = minOf(from, touched.minOf { it.start })
    val end = maxOf(to, touched.maxOf { it.end })
    val removed = end - start
    val kept = mentions.filter { it !in touched }.map { if (it.start >= end) it.shift(-removed) else it }
    return MentionEdit(TextFieldValue(text.removeRange(start, end), TextRange(start)), kept)
}

/**
 * 고른 [candidate]를 [query] 자리(트리거부터 커서까지)에 멘션으로 넣는다. 뒤에 공백이 없으면 하나 붙이고 커서를 그 뒤로 보낸다.
 */
internal fun insertMention(
    value: TextFieldValue,
    mentions: List<KMention>,
    query: MentionQuery,
    candidate: KMentionCandidate,
): MentionEdit {
    val text = value.text
    val end = value.selection.end.coerceIn(query.start, text.length)
    val token = "${query.trigger}${candidate.label}"
    val spaced = end < text.length && text[end].isWhitespace()
    val insert = if (spaced) token else "$token "
    val delta = insert.length - (end - query.start)
    val shifted = mentions.map { if (it.start >= end) it.shift(delta) else it }
    val mention = KMention(candidate.id, candidate.label, query.trigger, query.start, query.start + token.length)
    val cursor = query.start + token.length + 1
    return MentionEdit(
        TextFieldValue(text.replaceRange(query.start, end, insert), TextRange(cursor)),
        (shifted + mention).sortedBy {
            it.start
        },
    )
}

private fun KMention.shift(delta: Int): KMention = copy(start = start + delta, end = end + delta)

/** 접힌 커서가 멘션 안에 있으면 [left]면 멘션 앞으로, 아니면 멘션 끝으로 보낸다. 범위 선택은 그대로 둔다. */
private fun snapOutOfMentions(selection: TextRange, mentions: List<KMention>, left: Boolean): TextRange {
    if (!selection.collapsed) return selection
    val inside = mentions.firstOrNull { selection.start > it.start && selection.start < it.end } ?: return selection
    return TextRange(if (left) inside.start else inside.end)
}

private const val MAX_QUERY_LENGTH = 40
