package com.naury.kinetica.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** 표면 위에 글자를 놓는 버튼 변형의 대비. 모든 표면 프리셋, 톤, 변형을 돈다. */
class VariantContrastTest {
    private val presets: List<Pair<String, SurfaceStyle>> = SurfaceStyles::class.java.fields
        .filter { SurfaceStyle::class.java.isAssignableFrom(it.type) }
        .map { it.name to it.get(null) as SurfaceStyle }
    private val surfaces = presets + listOf(
        "Tonal 초록 어두움" to SurfaceStyles.tonal(Color(0xFF34C759), dark = true),
        "Tonal 주황 밝음" to SurfaceStyles.tonal(Color(0xFFFF9F0A)),
        "Tonal 빨강 밝음" to SurfaceStyles.tonal(Color(0xFFFF3B30)),
    )
    private val themes = listOf(
        "기본" to KineticaColors(),
        "보라 강조" to KineticaColors(accent = Color(0xFF8E7CFF)),
        "노랑 강조" to KineticaColors(accent = Color(0xFFFFCC00)),
    )
    private val inkVariants = listOf(KVariant.Tonal, KVariant.Outlined, KVariant.Text, KVariant.Glass, KVariant.Elevated)

    /** [KTone.colors]와 같은 규칙. 컴포지션 밖에서 쓰려고 옮겼다. */
    private fun toneColors(tone: KTone, colors: KineticaColors, surface: SurfaceStyle): ToneColors = when (tone) {
        KTone.Accent -> ToneColors(colors.accent, colors.onAccent)
        KTone.Destructive -> ToneColors(colors.destructive, colors.onDestructive)
        KTone.Positive -> ToneColors(colors.positive, colors.onPositive)
        KTone.Neutral -> ToneColors(surface.contentColor, surface.fillColor.copy(alpha = 1f))
    }

    @Test
    fun `프리셋을 모두 읽었다`() {
        assertThat(presets.map { it.first }).containsAtLeast("CleanLight", "CleanDark", "Liquid", "Tonal", "TonalDark", "Material")
    }

    @Test
    fun `모든 표면과 톤과 변형에서 버튼 글자는 4점5대1, 아이콘 버튼은 3대1 이상이다`() {
        val cases = themes.flatMap { theme -> surfaces.map { theme to it } }
            .flatMap { pair -> KTone.entries.flatMap { tone -> inkVariants.map { Triple(pair, tone, it) } } }
        val failures = cases.flatMap { (pair, tone, variant) ->
            val (theme, named) = pair
            val surface = named.second
            val colors = toneColors(tone, theme.second, surface)
            val back = variantInkBackground(variant, colors, surface)
            val text = contrast(buttonInk(variant, tone, colors, surface), back)
            val icon = contrast(buttonInk(variant, tone, colors, surface, INK_GRAPHIC_CONTRAST), back)
            val where = "${theme.first}/${named.first}/$tone/$variant"
            listOfNotNull(
                "$where 글자 $text".takeIf { text < INK_TEXT_CONTRAST },
                "$where 아이콘 $icon".takeIf { icon < INK_GRAPHIC_CONTRAST },
            )
        }
        assertWithMessage(failures.joinToString("\n")).that(failures).isEmpty()
    }

    @Test
    fun `밝은 표면에서 기본 강조색 글자는 기준을 막 넘는 만큼만 어둡게 한다`() {
        val accent = ToneColors(Color(0xFF0A84FF), Color.White)
        for (variant in listOf(KVariant.Tonal, KVariant.Outlined, KVariant.Text)) {
            val back = variantInkBackground(variant, accent, SurfaceStyles.CleanLight)
            val ink = buttonInk(variant, KTone.Accent, accent, SurfaceStyles.CleanLight)
            assertWithMessage("$variant 고친 색").that(ink).isNotEqualTo(accent.color)
            assertWithMessage("$variant 대비").that(contrast(ink, back)).isIn(com.google.common.collect.Range.closed(4.5f, 4.6f))
        }
        // 아이콘 버튼은 3:1이면 되므로 기본 강조색(3.6:1)을 그대로 쓴다.
        assertThat(buttonInk(KVariant.Text, KTone.Accent, accent, SurfaceStyles.CleanLight, INK_GRAPHIC_CONTRAST)).isEqualTo(accent.color)
    }

    @Test
    fun `대비가 충분한 브랜드 색은 그대로 쓴다`() {
        val brand = ToneColors(Color(0xFF1A3A8A), Color.White)
        for (variant in listOf(KVariant.Tonal, KVariant.Outlined, KVariant.Text)) {
            assertWithMessage("$variant").that(buttonInk(variant, KTone.Accent, brand, SurfaceStyles.CleanLight)).isEqualTo(brand.color)
        }
    }

    @Test
    fun `어두운 표면에서 옅은 강조색 버튼과 글자 버튼의 글자는 4점5대1 이상이다`() {
        val accent = ToneColors(Color(0xFF0A84FF), Color.White)
        listOf(SurfaceStyles.CleanDark, SurfaceStyle.Tonal(Color(0xFF3B6CF6), true)).forEach { surface ->
            val base = surface.fillColor.copy(alpha = 1f)
            val tonalBg = accent.color.copy(alpha = 0.16f).compositeOver(base)
            assertThat(contrast(buttonInk(KVariant.Tonal, KTone.Accent, accent, surface), tonalBg)).isAtLeast(4.5f)
            assertThat(contrast(buttonInk(KVariant.Text, KTone.Accent, accent, surface), base)).isAtLeast(4.5f)
        }
    }

    @Test
    fun `배너 안 글자 버튼은 상태 색이 깔린 배너 바탕 위에서 4점5대1 이상이다`() {
        val failures = mutableListOf<String>()
        for ((name, surface) in surfaces) {
            for (status in KStatus.entries) {
                val back = feedbackInk(FeedbackBox.Banner, status, KineticaColors(), surface).back
                val action = SurfaceStyle.Solid(back, surface.contentColor, surface.background)
                val colors = toneColors(KTone.Accent, KineticaColors(), action)
                val ratio = contrast(buttonInk(KVariant.Text, KTone.Accent, colors, action), back)
                if (ratio < INK_TEXT_CONTRAST) failures += "$name/$status $ratio"
            }
        }
        assertWithMessage(failures.joinToString("\n")).that(failures).isEmpty()
    }
}
