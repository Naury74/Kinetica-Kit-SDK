package com.naury.kinetica.core.scene

/** 글자를 고칠 때 리치 텍스트 구간을 함께 옮기는 계산. */
public object TextSpans {
    /**
     * [before]를 [after]로 고쳤을 때 [spans]를 옮긴다. 앞뒤가 같은 부분을 뺀 가운데 하나를 바꾼 것으로 보고,
     * 바뀐 곳보다 뒤 구간은 길이 차이만큼 밀고, 바뀐 곳에 걸친 구간은 늘이거나 줄인다. 길이가 0이 된 구간은 지운다.
     */
    public fun remap(spans: List<TextSpan>, before: String, after: String): List<TextSpan> {
        if (before == after || spans.isEmpty()) return spans
        val edit = Edit.between(before, after)
        return spans.mapNotNull { span ->
            span.copy(start = edit.moveStart(span.start), end = edit.moveEnd(span.end, span.start).coerceAtMost(after.length))
                .takeIf { it.end > it.start }
        }
    }

    /** 앞 [prefix]자와 뒤가 같고, 그 사이 [removedEnd]까지를 지우고 [delta]만큼 길이가 바뀐 한 번의 수정. */
    private class Edit(val prefix: Int, val removedEnd: Int, val delta: Int) {
        private val insertedEnd = removedEnd + delta

        // 지운 곳 안에서 시작한 구간은 새로 쓴 글자 뒤에서 시작한다(지운 글자의 모양을 새 글자에 주지 않는다).
        fun moveStart(index: Int): Int = when {
            index < prefix -> index
            index >= removedEnd -> maxOf(index + delta, insertedEnd)
            else -> insertedEnd
        }

        // 구간 끝에서 바로 이어 쓴 글자는 그 구간에 들어간다(굵은 글자 끝에서 계속 쓰면 굵게).
        fun moveEnd(index: Int, start: Int): Int = when {
            index < prefix -> index
            index == prefix && start < prefix -> if (prefix == removedEnd) insertedEnd else prefix
            index >= removedEnd -> index + delta
            else -> prefix
        }

        companion object {
            fun between(before: String, after: String): Edit {
                val limit = minOf(before.length, after.length)
                var prefix = 0
                while (prefix < limit && before[prefix] == after[prefix]) prefix++
                var suffix = 0
                while (suffix < limit - prefix && before[before.length - 1 - suffix] == after[after.length - 1 - suffix]) suffix++
                return Edit(prefix, before.length - suffix, after.length - before.length)
            }
        }
    }

    /** 글자 길이에 맞지 않는 구간을 잘라 낸다. 데이터로 글자가 바뀌어 짧아졌을 때 그리기 전에 쓴다. */
    public fun clamp(spans: List<TextSpan>, length: Int): List<TextSpan> =
        spans.mapNotNull { span -> span.copy(end = span.end.coerceAtMost(length)).takeIf { it.start < it.end } }

    /** [index] 글자에 실제로 적용되는 구간 모양(뒤 구간이 앞을 덮는다). 없으면 모든 값이 `null`이다. */
    public fun styleAt(spans: List<TextSpan>, index: Int): TextSpan = spans.filter { index in it.start until it.end }.fold(
        TextSpan(
            index,
            index + 1,
        ),
    ) { acc, span ->
        acc.copy(
            fontFamily = span.fontFamily ?: acc.fontFamily,
            fontSize = span.fontSize ?: acc.fontSize,
            fontWeight = span.fontWeight ?: acc.fontWeight,
            italic = span.italic ?: acc.italic,
            decoration = span.decoration ?: acc.decoration,
            color = span.color ?: acc.color,
        )
    }

    /**
     * [start]..[end]에 [style]의 값(`null`이 아닌 것)을 준다. 그 범위 안에 들어 있고 같은 값만 정하던 앞 구간은 지워 구간이 쌓이지 않게 한다.
     */
    public fun apply(spans: List<TextSpan>, start: Int, end: Int, style: TextSpan): List<TextSpan> {
        if (end <= start) return spans
        val added = style.copy(start = start, end = end)
        val kept = spans.filterNot { span -> span.start >= start && span.end <= end && setFields(span).all { it in setFields(added) } }
        return kept + added
    }

    private fun setFields(span: TextSpan): Set<String> = buildSet {
        if (span.fontFamily != null) add("family")
        if (span.fontSize != null) add("size")
        if (span.fontWeight != null) add("weight")
        if (span.italic != null) add("italic")
        if (span.decoration != null) add("decoration")
        if (span.color != null) add("color")
    }
}
