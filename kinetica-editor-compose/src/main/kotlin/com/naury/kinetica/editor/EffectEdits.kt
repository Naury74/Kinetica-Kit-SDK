package com.naury.kinetica.editor

import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.Shadow

/** 그림자 하나의 숫자 입력 항목. */
internal enum class ShadowField { X, Y, Blur, Spread }

/** 그림자와 레이어 블러 입력을 명령으로 바꾼다. 입력 하나가 실행 취소 한 단계이고, 받을 수 없는 값이면 `false`를 돌려준다. */
internal class EffectEdits(private val state: EditorState) {
    /** 그림자를 목록 맨 뒤에 추가한다. */
    fun addShadow(id: NodeId, inner: Boolean): Boolean = state.editStyle(id) { style ->
        val shadow = if (inner) {
            Shadow(ArgbColor(DEFAULT_SHADOW), offsetY = INNER_Y, blur = INNER_BLUR, inner = true)
        } else {
            Shadow(ArgbColor(DEFAULT_SHADOW), offsetY = OUTER_Y, blur = OUTER_BLUR)
        }
        style.copy(shadows = style.shadows + shadow)
    }

    fun removeShadow(id: NodeId, index: Int): Boolean = state.editStyle(id) { style ->
        style.takeIf { index in it.shadows.indices }?.copy(shadows = style.shadows.filterIndexed { i, _ -> i != index })
    }

    fun setShadow(id: NodeId, index: Int, field: ShadowField, text: String): Boolean {
        val value = parseNumber(text) ?: return false
        if ((field == ShadowField.Blur) && value < 0.0) return false
        return updateShadow(id, index) { shadow ->
            when (field) {
                ShadowField.X -> shadow.copy(offsetX = value)
                ShadowField.Y -> shadow.copy(offsetY = value)
                ShadowField.Blur -> shadow.copy(blur = value)
                ShadowField.Spread -> shadow.copy(spread = value)
            }
        }
    }

    fun setShadowColor(id: NodeId, index: Int, text: String): Boolean {
        val color = parseColor(text) ?: return false
        return updateShadow(id, index) { it.copy(color = color) }
    }

    fun setBlur(id: NodeId, text: String): Boolean {
        val blur = parseNumber(text)?.takeIf { it >= 0.0 } ?: return false
        return state.editStyle(id) { it.copy(blur = blur) }
    }

    private fun updateShadow(id: NodeId, index: Int, change: (Shadow) -> Shadow): Boolean = state.editStyle(id) { style ->
        style.takeIf { index in it.shadows.indices }?.copy(
            shadows = style.shadows.mapIndexed { i, shadow ->
                if (i ==
                    index
                ) {
                    change(shadow)
                } else {
                    shadow
                }
            },
        )
    }

    private companion object {
        const val DEFAULT_SHADOW = 0x40000000
        const val OUTER_Y = 8.0
        const val OUTER_BLUR = 24.0
        const val INNER_Y = 2.0
        const val INNER_BLUR = 8.0
    }
}
