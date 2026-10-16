package com.naury.kinetica.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** 배너, 알림 상자, 상태 배지의 글자와 아이콘 대비. 모든 표면 프리셋과 상태, 변형을 돈다. */
class FeedbackContrastTest {
    private val surfaces = listOf(
        "Liquid" to SurfaceStyles.Liquid,
        "LiquidDark" to SurfaceStyles.LiquidDark,
        "Acrylic" to SurfaceStyles.Acrylic,
        "AcrylicDark" to SurfaceStyles.AcrylicDark,
        "OneUiGlass" to SurfaceStyles.OneUiGlass,
        "OneUiGlassDark" to SurfaceStyles.OneUiGlassDark,
        "Vision" to SurfaceStyles.Vision,
        "Frosted" to SurfaceStyles.Frosted,
        "Mica" to SurfaceStyles.Mica,
        "MicaDark" to SurfaceStyles.MicaDark,
        "MaterialGlass" to SurfaceStyles.MaterialGlass,
        "MaterialGlassDark" to SurfaceStyles.MaterialGlassDark,
        "HarmonyOs" to SurfaceStyles.HarmonyOs,
        "Obsidian" to SurfaceStyles.Obsidian,
        "Pastel" to SurfaceStyles.Pastel,
        "CleanLight" to SurfaceStyles.CleanLight,
        "CleanDark" to SurfaceStyles.CleanDark,
        "OneUi" to SurfaceStyles.OneUi,
        "OneUiDark" to SurfaceStyles.OneUiDark,
        "Material" to SurfaceStyles.Material,
        "MaterialDark" to SurfaceStyles.MaterialDark,
        "Tonal" to SurfaceStyles.Tonal,
        "TonalDark" to SurfaceStyles.TonalDark,
        "Tonal 초록 어두움" to SurfaceStyles.tonal(Color(0xFF34C759), dark = true),
        "Tonal 빨강 어두움" to SurfaceStyles.tonal(Color(0xFFFF3B30), dark = true),
        "Tonal 주황 밝음" to SurfaceStyles.tonal(Color(0xFFFF9F0A)),
    )
    private val themes = listOf(
        "기본" to KineticaColors(),
        "보라 강조" to KineticaColors(accent = Color(0xFF8E7CFF)),
    )

    @Test
    fun `배너와 알림 상자는 모든 표면과 상태에서 글자 4점5대1, 아이콘 3대1 이상이다`() {
        val cases = themes.flatMap { theme -> surfaces.map { theme to it } }
            .flatMap { pair -> KStatus.entries.flatMap { status -> FeedbackBox.entries.map { Triple(pair, status, it) } } }
        val failures = cases.flatMap { (pair, status, box) ->
            val (theme, named) = pair
            val ink = feedbackInk(box, status, theme.second, named.second)
            val where = "${theme.first}/${named.first}/$status/$box"
            listOf(
                "제목" to contrast(ink.title, ink.back) / TEXT,
                "본문" to contrast(ink.body.compositeOver(ink.back), ink.back) / TEXT,
                "아이콘" to contrast(ink.icon.fill, ink.back) / ICON,
                "아이콘 기호" to contrast(ink.icon.glyph, ink.icon.fill) / ICON,
            ).filter { it.second < 1f }.map { "$where ${it.first} 기준의 ${it.second}배" }
        }
        assertWithMessage(failures.joinToString("\n")).that(failures).isEmpty()
    }

    @Test
    fun `톤 어두운 표면의 정보 아이콘은 같은 계열 바탕에 묻히지 않게 4점5대1까지 밝힌다`() {
        val colors = KineticaColors()
        for (box in listOf(FeedbackBox.Banner, FeedbackBox.Tonal, FeedbackBox.Plain)) {
            val ink = feedbackInk(box, KStatus.Info, colors, SurfaceStyles.TonalDark)
            assertWithMessage("$box 고친 아이콘 색").that(ink.icon.fill).isNotEqualTo(colors.accent)
            assertWithMessage("$box 아이콘 대비").that(contrast(ink.icon.fill, ink.back)).isAtLeast(TEXT)
            assertWithMessage("$box 기호 대비").that(contrast(ink.icon.glyph, ink.icon.fill)).isAtLeast(ICON)
        }
    }

    @Test
    fun `토스트처럼 표면 위에 바로 놓인 상태 아이콘도 모든 표면에서 3대1 이상이다`() {
        val failures = mutableListOf<String>()
        for ((name, surface) in surfaces) {
            val back = surface.inkBackground()
            for (status in KStatus.entries) {
                val icon = statusIconColors(status, KineticaColors(), surface, back)
                if (contrast(icon.fill, back) < ICON) failures += "$name/$status 아이콘"
                if (contrast(icon.glyph, icon.fill) < ICON) failures += "$name/$status 기호"
            }
        }
        assertWithMessage(failures.joinToString("\n")).that(failures).isEmpty()
    }

    @Test
    fun `채운 알림 상자는 채우는 색을 고쳐 흰 글자를 지킨다`() {
        val colors = KineticaColors()
        val ink = feedbackInk(FeedbackBox.Filled, KStatus.Info, colors, SurfaceStyles.CleanLight)
        assertWithMessage("글자는 흰색 그대로").that(ink.title).isEqualTo(Color.White)
        assertWithMessage("글자 대비").that(contrast(ink.title, ink.back)).isAtLeast(TEXT)
    }

    @Test
    fun `상태 배지는 모든 표면과 변형에서 글자 4점5대1 이상이다`() {
        val colors = KineticaColors()
        val variants = listOf(KVariant.Filled, KVariant.Tonal, KVariant.Outlined)
        val failures = surfaces.flatMap { (name, surface) ->
            val back = surface.inkBackground()
            KStatus.entries.flatMap { status ->
                variants.mapNotNull { variant ->
                    val (fill, ink) = pillColors(variant, status.feedbackColor(colors, surface), badgeInk(status, back), back)
                    val ratio = contrast(ink, fill.compositeOver(back))
                    if (ratio < TEXT) "$name/$status/$variant $ratio" else null
                }
            }
        }
        assertWithMessage(failures.joinToString("\n")).that(failures).isEmpty()
    }

    /** [KStatusBadge]가 채운 배지 글자에 쓰는 색 */
    private fun badgeInk(status: KStatus, back: Color): Color = when (status) {
        KStatus.Warning -> Color(0xFF1F1300)
        KStatus.Neutral -> back
        else -> Color.White
    }

    private companion object {
        const val TEXT = 4.5f
        const val ICON = 3f
    }
}
