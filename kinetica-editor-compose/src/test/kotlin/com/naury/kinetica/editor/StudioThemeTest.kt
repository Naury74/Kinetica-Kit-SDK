package com.naury.kinetica.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StudioThemeTest {
    @Test
    fun `밝은 테마는 밝은 패널에 어두운 글자이고 편집기 입력란 색이 테마를 따른다`() {
        val light = StudioTheme.light()
        val scheme = light.colorScheme()

        assertThat(light.isDark).isFalse()
        assertThat(light.panel.luminanceOf()).isGreaterThan(light.onPanel.luminanceOf())
        assertThat(scheme.surface).isEqualTo(light.panel)
        assertThat(scheme.onSurface).isEqualTo(light.onPanel)
        assertThat(scheme.primary).isEqualTo(light.accent)
        assertThat(StudioTheme.dark()).isEqualTo(StudioTheme())
    }

    private fun androidx.compose.ui.graphics.Color.luminanceOf() = red * 0.2126f + green * 0.7152f + blue * 0.0722f
}
