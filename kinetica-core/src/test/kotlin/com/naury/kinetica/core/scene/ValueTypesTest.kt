package com.naury.kinetica.core.scene

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ValueTypesTest {
    @Test
    fun `색은 16진수와 왕복한다`() {
        assertThat(ArgbColor.parseHexOrNull("#4466FF")).isEqualTo(ArgbColor(0xFF4466FF.toInt()))
        assertThat(ArgbColor.parseHexOrNull("#804466ff")?.toHex()).isEqualTo("#804466FF")
        assertThat(ArgbColor(0xFF4466FF.toInt()).toHex()).isEqualTo("#4466FF")
        assertThat(ArgbColor.fromChannels(0x80, 1, 2, 3).toHex()).isEqualTo("#80010203")
    }

    @Test
    fun `잘못된 색 문자열은 null이다`() {
        listOf("4466FF", "#4466F", "#GG66FF", "#4466FF00FF", "").forEach {
            assertThat(ArgbColor.parseHexOrNull(it)).isNull()
        }
    }

    @Test
    fun `NodeId 형식을 확인한다`() {
        assertThat(NodeId.parseOrNull("01HZY3-node_a.b:c")).isNotNull()
        assertThat(NodeId.parseOrNull("")).isNull()
        assertThat(NodeId.parseOrNull("a b")).isNull()
        assertThat(NodeId.parseOrNull("x".repeat(NodeId.MAX_LENGTH + 1))).isNull()
        assertThrows(IllegalArgumentException::class.java) { NodeId("../etc") }
    }

    @Test
    fun `UTF-8 길이는 인코딩 결과와 같다`() {
        listOf("", "abc", "가나다", "😀emoji", "ß€𝄞").forEach {
            assertThat(utf8Length(it)).isEqualTo(it.toByteArray(Charsets.UTF_8).size)
        }
    }
}
