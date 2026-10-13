package com.naury.kinetica.board

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** 보드 편집 표시 색. [zone]은 분할·칸 자리 미리보기를 칠하는 색이다. */
@Immutable
public data class BoardColors(
    val accent: Color = Color(ACCENT),
    val outline: Color = Color(ACCENT),
    val zone: Color = Color(ZONE),
    val onAccent: Color = Color.White,
)

private const val ACCENT = 0xFF7C8CFF
private const val ZONE = 0x337C8CFF
