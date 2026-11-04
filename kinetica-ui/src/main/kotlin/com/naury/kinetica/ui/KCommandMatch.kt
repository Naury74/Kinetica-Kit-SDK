package com.naury.kinetica.ui

/** 명령 제목이 검색어와 맞는 정도. [score]가 클수록 위에 놓고, [ranges]는 제목에서 강조할 글자 범위다. */
internal class CommandMatch(val score: Int, val ranges: List<IntRange>)

/**
 * [title]과 [keywords]가 [query]와 맞는지 본다. 맞지 않으면 `null`이다. 대소문자는 가리지 않는다.
 *
 * 제목에 검색어가 그대로 들어 있으면 가장 높게 치고, 제목 맨 앞이나 낱말 첫머리에서 맞으면 더 높게 친다.
 * 그다음은 검색어 글자가 제목에 순서대로 흩어져 있는 경우(`opst`가 `Open Settings`에 맞음)로, 붙어 있는 글자가 많을수록 높다.
 * 검색어가 한글 자음뿐이면 제목 글자의 초성과 맞춘다(`ㅅㅈ`이 `새 주문`에 맞음). 점수는 그대로 들어 있을 때보다 조금 낮다.
 * 키워드에만 맞으면 가장 낮게 치고 제목은 강조하지 않는다. 검색어가 비어 있으면 점수 0으로 모두 맞는다.
 */
internal fun matchCommand(title: String, keywords: List<String>, query: String): CommandMatch? {
    val q = query.trim()
    if (q.isEmpty()) return CommandMatch(0, emptyList())
    return exactMatch(title, q)
        ?: initialsMatch(title, q)
        ?: scatteredMatch(title, q.filterNot { it.isWhitespace() })?.let { CommandMatch(SCATTERED_SCORE - it.size * GAP_PENALTY, it) }
        ?: CommandMatch(KEYWORD_SCORE, emptyList()).takeIf { keywords.any { it.contains(q, ignoreCase = true) } }
}

/** 제목에 [q]가 그대로 들어 있을 때의 점수. 맨 앞이나 낱말 첫머리면 더 높다. */
private fun exactMatch(title: String, q: String): CommandMatch? {
    val at = title.indexOf(q, ignoreCase = true)
    if (at < 0) return null
    val bonus = when {
        at == 0 -> PREFIX_BONUS
        !title[at - 1].isLetterOrDigit() -> WORD_BONUS
        else -> 0
    }
    return CommandMatch(EXACT_SCORE + bonus - at, listOf(at until at + q.length))
}

/** 검색어가 한글 자음으로만 되어 있으면 제목을 초성으로 바꿔 맞춘다. 초성 문자열은 제목과 길이가 같아 강조 범위를 그대로 쓴다. */
private fun initialsMatch(title: String, q: String): CommandMatch? {
    val consonants = q.filterNot { it.isWhitespace() }
    if (consonants.isEmpty() || consonants.any { it !in CHOSUNG }) return null
    val initials = buildString(title.length) { title.forEach { append(initialOf(it)) } }
    exactMatch(initials, q)?.let { return CommandMatch(it.score - INITIALS_PENALTY, it.ranges) }
    return scatteredMatch(initials, consonants)?.let { CommandMatch(SCATTERED_SCORE - INITIALS_PENALTY - it.size * GAP_PENALTY, it) }
}

/** 한글 음절이면 초성 자음(호환 자모), 아니면 그 글자 그대로. */
private fun initialOf(ch: Char): Char = if (ch in '가'..'힣') CHOSUNG[(ch - '가') / SYLLABLES_PER_INITIAL] else ch

/** [needle] 글자가 [text]에 순서대로 모두 있으면 맞은 글자를 이어진 범위로 묶어 돌려준다. */
private fun scatteredMatch(text: String, needle: String): List<IntRange>? {
    if (needle.isEmpty()) return null
    val ranges = ArrayList<IntRange>()
    var from = 0
    for (ch in needle) {
        val i = text.indexOf(ch, from, ignoreCase = true)
        if (i < 0) return null
        val last = ranges.lastOrNull()
        if (last != null && last.last == i - 1) ranges[ranges.size - 1] = last.first..i else ranges += i..i
        from = i + 1
    }
    return ranges
}

/** 명령 판의 한 줄. 묶음 제목이거나 명령이다. */
internal sealed interface PaletteRow {
    val key: String

    class Header(val title: String, override val key: String) : PaletteRow

    class Item(val command: KCommand, val match: CommandMatch, override val key: String) : PaletteRow
}

/**
 * 판에 보일 줄을 만든다. 검색어가 비어 있으면 [recentIds] 순서로 최근 명령 묶음을 맨 위에 두고 모든 묶음을 그대로 잇는다.
 * 검색어가 있으면 묶음마다 맞는 명령만 점수 순으로 남기고, 맞는 명령이 없는 묶음은 뺀다. 최근 묶음은 두지 않는다.
 */
internal fun paletteRows(sections: List<KCommandSection>, query: String, recentIds: List<String>, recentTitle: String): List<PaletteRow> {
    val rows = ArrayList<PaletteRow>()
    if (query.isBlank()) {
        val byId = sections.flatMap { it.commands }.associateBy { it.id }
        val recent = recentIds.mapNotNull { byId[it] }
        if (recent.isNotEmpty()) {
            rows += PaletteRow.Header(recentTitle, "recent")
            recent.forEach { rows += PaletteRow.Item(it, CommandMatch(0, emptyList()), "recent:${it.id}") }
        }
    }
    sections.forEachIndexed { index, section ->
        val matched = section.commands
            .mapNotNull { command -> matchCommand(command.title, command.keywords, query)?.let { command to it } }
            .sortedByDescending { it.second.score }
        if (matched.isEmpty()) return@forEachIndexed
        if (section.title.isNotEmpty()) rows += PaletteRow.Header(section.title, "section:$index")
        matched.forEach { (command, match) -> rows += PaletteRow.Item(command, match, "item:$index:${command.id}") }
    }
    return rows
}

/** [rows]에서 [from] 다음(또는 [step]이 음수면 이전) 실행할 수 있는 명령 줄. 끝에 닿으면 반대쪽 끝으로 돈다. 없으면 -1이다. */
internal fun nextSelectable(rows: List<PaletteRow>, from: Int, step: Int): Int {
    if (rows.none { it.isSelectable() }) return -1
    var i = from
    repeat(rows.size) {
        i = Math.floorMod(i + step, rows.size)
        if (rows[i].isSelectable()) return i
    }
    return -1
}

internal fun PaletteRow.isSelectable(): Boolean = this is PaletteRow.Item && command.enabled

private const val EXACT_SCORE = 1000
private const val PREFIX_BONUS = 300
private const val WORD_BONUS = 150
private const val SCATTERED_SCORE = 500
private const val GAP_PENALTY = 20
private const val KEYWORD_SCORE = 100
private const val INITIALS_PENALTY = 50
private const val SYLLABLES_PER_INITIAL = 588
private const val CHOSUNG = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
