package com.naury.kinetica.editor

import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.TextAutoSize
import com.naury.kinetica.core.scene.TextDecoration
import com.naury.kinetica.core.scene.TextNumberFormat

/** 글자 노드 전체의 글꼴·기울임·밑줄 입력을 명령으로 바꾼다. 입력 하나가 실행 취소 한 단계다. */
internal class TextEdits(private val state: EditorState) {
    /** `null`이면 기본 글꼴. 이름 형식이 틀리면 `false`. */
    fun setFontFamily(id: NodeId, family: String?): Boolean = edit(id) { it.copy(fontFamily = family) }

    /** 글자에 맞춰 상자 크기를 바꾸는 방식. 측정은 세션의 측정기가 한다. */
    fun setAutoSize(id: NodeId, mode: TextAutoSize): Boolean = edit(id) { it.copy(autoSize = mode) }

    fun toggleItalic(id: NodeId): Boolean = edit(id) { it.copy(italic = !it.italic) }

    /** 같은 장식을 다시 고르면 끈다. */
    fun toggleDecoration(id: NodeId, decoration: TextDecoration): Boolean =
        edit(id) { it.copy(decoration = if (it.decoration == decoration) TextDecoration.None else decoration) }

    /**
     * 숫자 데이터를 보여 줄 형식. 입력한 칸만 바꾼다. 앞뒤 글자가 모두 비고 소수 자릿수도 없으면 형식을 지운다.
     * 소수 자릿수가 숫자가 아니거나 0..6 밖이면 `false`.
     */
    fun setNumberFormat(id: NodeId, prefix: String? = null, suffix: String? = null, decimalsText: String? = null): Boolean {
        val decimals = decimalsText?.trim()?.let { text -> if (text.isEmpty()) null else text.toIntOrNull() ?: return false }
        if (decimals != null && decimals !in 0..TextNumberFormat.MAX_DECIMALS) return false
        return edit(id) { content ->
            val current = content.numberFormat ?: TextNumberFormat()
            val next = current.copy(
                prefix = prefix ?: current.prefix,
                suffix = suffix ?: current.suffix,
                decimals = if (decimalsText == null) current.decimals else decimals,
            )
            content.copy(
                numberFormat = next.takeUnless {
                    it.prefix.isEmpty() && it.suffix.isEmpty() && it.decimals == null && it.grouping
                },
            )
        }
    }

    private fun edit(id: NodeId, change: (NodeContent.Text) -> NodeContent.Text): Boolean {
        val node = state.document.node(id) ?: return false
        val text = node.content as? NodeContent.Text ?: return false
        val updated = node.copy(content = change(text))
        if (updated == node) return true
        return state.execute(SceneCommand.UpdateProperties(updated))
    }
}
