package com.naury.kinetica.board

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** 보드 편집 화면의 색. [zone]은 분할 영역과 격자 칸 미리보기를 칠하는 색이다. */
@Immutable
public data class BoardColors(
    val accent: Color = Color(ACCENT),
    val outline: Color = Color(ACCENT),
    val zone: Color = Color(ZONE),
    val onAccent: Color = Color.White,
) {
    public companion object {
        /** 현재 MaterialTheme의 primary, onPrimary 색을 따른다. 라이트 테마와 다크 테마 모두 따라간다. */
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

        /** XML 화면에서 쓸 색을 강조색 하나로 만든다. */
        @JvmStatic
        public fun fromAccent(accent: Int): BoardColors = Color(accent).let { BoardColors(it, it, it.copy(alpha = ZONE_ALPHA)) }
    }
}

private const val ACCENT = 0xFF7C8CFF
private const val ZONE = 0x337C8CFF
private const val ZONE_ALPHA = 0.2f
