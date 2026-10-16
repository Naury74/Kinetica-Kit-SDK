package com.naury.kinetica.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VariantContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun `어두운 표면에서 옅은 강조색 버튼과 글자 버튼의 글자는 4점5대1 이상이다`() {
        val accent = ToneColors(Color(0xFF0A84FF), Color.White)
        listOf(SurfaceStyles.CleanDark, SurfaceStyle.Tonal(Color(0xFF3B6CF6), true)).forEach { surface ->
            val base = surface.fillColor.copy(alpha = 1f)
            val tonalBg = accent.color.copy(alpha = 0.16f).compositeOver(base)
            assertThat(contrast(variantContent(KVariant.Tonal, KTone.Accent, accent, surface), tonalBg)).isAtLeast(4.5f)
            assertThat(contrast(variantContent(KVariant.Text, KTone.Accent, accent, surface), base)).isAtLeast(4.5f)
        }
    }

    @Test
    fun `밝은 표면에서는 강조색을 그대로 쓴다`() {
        val accent = ToneColors(Color(0xFF0A84FF), Color.White)
        assertThat(variantContent(KVariant.Text, KTone.Accent, accent, SurfaceStyles.CleanLight)).isEqualTo(accent.color)
    }
}
