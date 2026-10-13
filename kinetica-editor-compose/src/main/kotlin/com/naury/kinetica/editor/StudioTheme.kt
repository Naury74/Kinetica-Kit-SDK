package com.naury.kinetica.editor

import androidx.compose.ui.graphics.Color

/** 편집기 색. 기본값은 docs 10의 제안 토큰이다. 호스트가 바꿀 수 있다. */
public data class StudioTheme(
    val canvasBackground: Color = Color(CANVAS_BACKGROUND),
    val panel: Color = Color(PANEL),
    val onPanel: Color = Color(ON_PANEL),
    val accent: Color = Color(ACCENT),
    val selection: Color = Color(SELECTION),
    val warning: Color = Color(WARNING),
)

private const val CANVAS_BACKGROUND = 0xFF101216
private const val PANEL = 0xFF1A1D24
private const val ON_PANEL = 0xFFE6E8EE
private const val ACCENT = 0xFF7C8CFF
private const val SELECTION = 0xFF64C8FF
private const val WARNING = 0xFFF0B35A
