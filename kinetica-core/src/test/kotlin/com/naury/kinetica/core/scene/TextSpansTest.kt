package com.naury.kinetica.core.scene

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextSpansTest {
    private val bold = TextSpan(6, 11, fontWeight = 700)

    @Test
    fun `앞에 글자를 넣으면 구간이 밀리고 구간 앞에서 넣은 글자는 모양을 받지 않는다`() {
        assertThat(TextSpans.remap(listOf(bold), "hello world", "oh hello world")).containsExactly(bold.copy(start = 9, end = 14))
        assertThat(TextSpans.remap(listOf(bold), "hello world", "hello new world")).containsExactly(bold.copy(start = 10, end = 15))
    }

    @Test
    fun `구간 끝에서 이어 쓰면 구간이 늘고 구간 안에서 지우면 줄어든다`() {
        assertThat(TextSpans.remap(listOf(bold), "hello world", "hello worlds")).containsExactly(bold.copy(end = 12))
        assertThat(TextSpans.remap(listOf(bold), "hello world", "hello wd")).containsExactly(bold.copy(end = 8))
    }

    @Test
    fun `구간 전체를 지우면 구간도 사라지고 뒤를 고치면 그대로다`() {
        assertThat(TextSpans.remap(listOf(bold), "hello world", "hello ")).isEmpty()
        assertThat(
            TextSpans.remap(listOf(TextSpan(0, 5, italic = true)), "hello world", "hello there"),
        ).containsExactly(TextSpan(0, 5, italic = true))
    }

    @Test
    fun `구간 안의 글자를 바꾸면 바꾼 글자도 구간 모양을 따른다`() {
        assertThat(TextSpans.remap(listOf(bold), "hello world", "hello wXYld")).containsExactly(bold)
    }

    @Test
    fun `길이를 넘는 구간은 자른다`() {
        assertThat(TextSpans.clamp(listOf(bold, TextSpan(12, 20)), 8)).containsExactly(bold.copy(end = 8))
    }

    @Test
    fun `같은 값을 다시 주면 구간이 쌓이지 않고 뒤 구간이 앞을 덮는다`() {
        val once = TextSpans.apply(emptyList(), 0, 5, TextSpan(0, 0, fontWeight = 700))
        val twice = TextSpans.apply(once, 0, 5, TextSpan(0, 0, fontWeight = 400))
        val colored = TextSpans.apply(twice, 2, 4, TextSpan(0, 0, color = ArgbColor(0xFFFF0000.toInt())))

        assertThat(twice).containsExactly(TextSpan(0, 5, fontWeight = 400))
        assertThat(TextSpans.styleAt(colored, 3)).isEqualTo(TextSpan(3, 4, fontWeight = 400, color = ArgbColor(0xFFFF0000.toInt())))
        assertThat(TextSpans.styleAt(colored, 9).fontWeight).isNull()
    }
}
