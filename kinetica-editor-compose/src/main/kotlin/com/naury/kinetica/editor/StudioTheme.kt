package com.naury.kinetica.editor

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 편집기 색. 기본값은 다크 테마([dark])이고 [light]나 [system]으로 바꾸거나 값을 하나씩 바꿔 쓸 수 있다.
 * 편집기 안의 입력란, 칩, 시트도 호스트 앱의 MaterialTheme과 상관없이 이 색을 따른다.
 *
 * @property isDark 어두운 색 조합인지 여부. 입력란 글자색과 대비 계산에 쓴다.
 */
public data class StudioTheme(
    val canvasBackground: Color = Color(CANVAS_BACKGROUND),
    val panel: Color = Color(PANEL),
    val onPanel: Color = Color(ON_PANEL),
    val accent: Color = Color(ACCENT),
    val selection: Color = Color(SELECTION),
    val warning: Color = Color(WARNING),
    val isDark: Boolean = true,
) {
    /** 편집기 안의 Material 컴포넌트에 쓰는 색. */
    internal fun colorScheme(): ColorScheme {
        val base = if (isDark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = accent,
            onPrimary = if (isDark) Color(ON_ACCENT_DARK) else Color.White,
            surface = panel,
            onSurface = onPanel,
            surfaceContainerLow = panel,
            surfaceContainer = panel,
            surfaceContainerHigh = panel,
            background = canvasBackground,
            onBackground = onPanel,
            onSurfaceVariant = onPanel.copy(alpha = VARIANT_ALPHA),
            outline = onPanel.copy(alpha = OUTLINE_ALPHA),
            secondaryContainer = accent.copy(alpha = CONTAINER_ALPHA),
            onSecondaryContainer = onPanel,
        )
    }

    public companion object {
        /** 다크 테마(기본값). */
        @JvmStatic
        public fun dark(): StudioTheme = StudioTheme()

        /** 라이트 테마. */
        @JvmStatic
        public fun light(): StudioTheme = StudioTheme(
            canvasBackground = Color(LIGHT_CANVAS),
            panel = Color(LIGHT_PANEL),
            onPanel = Color(LIGHT_ON_PANEL),
            accent = Color(LIGHT_ACCENT),
            selection = Color(LIGHT_SELECTION),
            warning = Color(LIGHT_WARNING),
            isDark = false,
        )

        /** 시스템 다크 모드 설정을 따른다. */
        @Composable
        public fun system(): StudioTheme = if (isSystemInDarkTheme()) dark() else light()
    }
}

private const val CANVAS_BACKGROUND = 0xFF101216
private const val PANEL = 0xFF1A1D24
private const val ON_PANEL = 0xFFE6E8EE
private const val ACCENT = 0xFF7C8CFF
private const val SELECTION = 0xFF64C8FF
private const val WARNING = 0xFFF0B35A
private const val ON_ACCENT_DARK = 0xFF101216
private const val LIGHT_CANVAS = 0xFFE9ECF2
private const val LIGHT_PANEL = 0xFFFFFFFF
private const val LIGHT_ON_PANEL = 0xFF1B1E26
private const val LIGHT_ACCENT = 0xFF4F5FE0
private const val LIGHT_SELECTION = 0xFF1A8FE0
private const val LIGHT_WARNING = 0xFFB86E00
private const val VARIANT_ALPHA = 0.72f
private const val OUTLINE_ALPHA = 0.4f
private const val CONTAINER_ALPHA = 0.3f
