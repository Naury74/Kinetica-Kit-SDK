package com.naury.kinetica.board

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** 보드 편집 표시 색. [zone]은 분할·칸 자리 미리보기를 칠하는 색이다. */
@Immutable
public data class BoardColors(
    val accent: Color = Color(ACCENT),
    val outline: Color = Color(ACCENT),
    val zone: Color = Color(ZONE),
    val onAccent: Color = Color.White,
) {
    public companion object {
        /** 지금 MaterialTheme 색(primary·onPrimary)을 따르는 색. 밝은·어두운 테마를 함께 따른다. */
        @Composable
        public fun fromMaterialTheme(): BoardColors {
            val scheme = MaterialTheme.colorScheme
            return BoardColors(
                accent = scheme.primary,
                outline = scheme.primary,
                zone = scheme.primary.copy(alpha = ZONE_ALPHA),
                onAccent = scheme.onPrimary,
            )
        }

        /** XML 화면에서 강조색 하나로 만든다. */
        @JvmStatic
        public fun fromAccent(accent: Int): BoardColors = Color(accent).let { BoardColors(it, it, it.copy(alpha = ZONE_ALPHA)) }
    }
}

private const val ACCENT = 0xFF7C8CFF
private const val ZONE = 0x337C8CFF
private const val ZONE_ALPHA = 0.2f
