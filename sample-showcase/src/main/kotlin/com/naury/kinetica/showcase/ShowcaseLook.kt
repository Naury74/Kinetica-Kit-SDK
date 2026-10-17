// 시트 글자 크기와 프리셋 색은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.GlassStyle
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KSheet
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaDesign
import com.naury.kinetica.ui.KineticaDesigns
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.LocalKineticaSurface
import com.naury.kinetica.ui.MeshGradientBackground
import com.naury.kinetica.ui.MeshPalette
import com.naury.kinetica.ui.SurfaceStyle
import com.naury.kinetica.ui.SurfaceStyles

/** 쇼케이스 전체에서 고르는 표면 종류. */
internal enum class SurfaceFamily(val label: String) {
    Glass("글래스"),
    Solid("단색"),
    Tonal("톤"),
}

/** 앱 화면 예의 표면을 정하는 방식. */
internal enum class AppSurface(val label: String) {
    /** 모양 설정의 표면 종류와 프리셋을 따른다. 표면 종류가 앱 추천과 같으면 앱이 고른 스타일을 그대로 쓴다. */
    FollowLook("설정 따름"),

    /** 앱마다 추천하는 표면을 쓴다. 밝기와 형태는 여전히 설정을 따른다. */
    Recommended("앱 추천"),
}

/** 밝기. [Auto]면 데모마다 처음 디자인한 밝기를 쓴다. */
internal enum class Brightness(val label: String) {
    Auto("화면 기본"),
    Light("밝게"),
    Dark("어둡게"),
}

/**
 * 앱 전체 모양 설정. 상단 바의 모양 버튼이 여는 시트 한 곳에서만 고르고, 모든 화면이 이 값을 따른다.
 * [preset]이 [DEFAULT_PRESET]이면 화면마다 처음 디자인한 스타일을 쓰고, 아니면 [family]의 프리셋 목록에서 그 순서의 것을 쓴다.
 */
@Stable
internal class ShowcaseLook(
    family: SurfaceFamily = SurfaceFamily.Glass,
    brightness: Brightness = Brightness.Auto,
    preset: Int = DEFAULT_PRESET,
    design: Int = 0,
    appSurface: AppSurface = AppSurface.FollowLook,
) {
    var family by mutableStateOf(family)
        private set
    var brightness by mutableStateOf(brightness)
    var preset by mutableIntStateOf(preset)
        private set

    /** 고른 디자인 언어(형태)의 [KineticaDesigns.all] 순서. 표면과 따로 고르며 모든 화면이 따른다. */
    var design by mutableIntStateOf(design.coerceIn(0, KineticaDesigns.all.lastIndex))
        private set

    /** 앱 화면 예가 표면을 이 설정에서 가져올지, 앱마다 추천 표면을 쓸지. 형태는 어느 쪽이든 이 설정을 따른다. */
    var appSurface by mutableStateOf(appSurface)

    /** 디자인 언어를 바꾼다. 범위 밖이면 가장 가까운 끝 값으로 맞춘다. */
    fun selectDesign(index: Int) {
        design = index.coerceIn(0, KineticaDesigns.all.lastIndex)
    }

    /** 고른 디자인 언어 */
    val selectedDesign: KineticaDesign get() = KineticaDesigns.all[design].second

    /** 고른 디자인 언어 이름. 예: "Cupertino" */
    val designName: String get() = KineticaDesigns.all[design].first

    /** 표면 종류와 그 종류의 프리셋을 함께 바꾼다. 종류만 바꾸면 프리셋은 화면 기본으로 돌아간다. */
    fun select(family: SurfaceFamily, preset: Int = DEFAULT_PRESET) {
        this.family = family
        this.preset = preset.coerceIn(DEFAULT_PRESET, presetsOf(family).lastIndex)
    }

    /** 고른 프리셋. 화면 기본이면 null이다. */
    val selectedPreset: SurfacePreset? get() = presetsOf(family).getOrNull(preset)

    /** 처음 디자인이 어두운 화면인지([defaultDark])를 받아 지금 밝기 설정으로 푼다. */
    fun isDark(defaultDark: Boolean): Boolean = when (brightness) {
        Brightness.Auto -> defaultDark
        Brightness.Light -> false
        Brightness.Dark -> true
    }

    /** [isDark]와 같되, 한 가지 밝기만 있는 프리셋(Obsidian 등)을 골랐으면 그 프리셋의 밝기를 따른다. 상태 표시줄 아이콘 색에 쓴다. */
    fun resolvedDark(defaultDark: Boolean): Boolean = selectedPreset?.surface(isDark(defaultDark))?.isDark ?: isDark(defaultDark)

    /** 첫 화면에 한 줄로 보여 줄 지금 모양. 예: "글래스 · Cupertino", "글래스 · Mica · Fluent · 어둡게" */
    fun summary(): String = listOfNotNull(
        family.label,
        selectedPreset?.name,
        designName,
        brightness.takeIf { it != Brightness.Auto }?.label,
    ).joinToString(" · ")

    companion object {
        /** 화면마다 처음 디자인한 스타일을 쓴다는 뜻의 프리셋 값 */
        const val DEFAULT_PRESET = -1

        val Saver: Saver<ShowcaseLook, IntArray> = Saver(
            save = { intArrayOf(it.family.ordinal, it.brightness.ordinal, it.preset, it.design, it.appSurface.ordinal) },
            restore = {
                ShowcaseLook(
                    SurfaceFamily.entries[it[0]],
                    Brightness.entries[it[1]],
                    it.getOrElse(2) { DEFAULT_PRESET },
                    it.getOrElse(3) { 0 },
                    AppSurface.entries[it.getOrElse(4) { 0 }],
                )
            },
        )
    }
}

internal val LocalShowcaseLook = staticCompositionLocalOf { ShowcaseLook() }

@Composable
internal fun rememberShowcaseLook(): ShowcaseLook = rememberSaveable(saver = ShowcaseLook.Saver) { ShowcaseLook() }

/** 데모 화면이 글래스일 때 쓸 스타일과 단색일 때 쓸 스타일, 톤 강조색. 표면 종류와 밝기를 고르면 이 중에서 하나가 정해진다. */
internal class DemoLook(
    val glassLight: GlassStyle,
    val glassDark: GlassStyle,
    val defaultDark: Boolean,
    val solidLight: SurfaceStyle = SurfaceStyles.CleanLight,
    val solidDark: SurfaceStyle = SurfaceStyles.CleanDark,
    val tonalAccent: Color = TONAL_ACCENT,
)

/** 지금 고른 표면 종류와 밝기에 맞는 이 데모의 표면. */
@Composable
internal fun demoSurface(look: DemoLook): SurfaceStyle = LocalShowcaseLook.current.surfaceFor(look)

/** [look]으로 디자인한 화면이 지금 설정에서 쓸 표면. 프리셋을 골랐으면 화면 기본 대신 그 프리셋을 쓴다. */
internal fun ShowcaseLook.surfaceFor(look: DemoLook): SurfaceStyle {
    val dark = isDark(look.defaultDark)
    selectedPreset?.let { return it.surface(dark) }
    return when (family) {
        SurfaceFamily.Glass -> SurfaceStyle.Glass(if (dark) look.glassDark else look.glassLight)
        SurfaceFamily.Solid -> if (dark) look.solidDark else look.solidLight
        SurfaceFamily.Tonal -> SurfaceStyle.Tonal(look.tonalAccent, dark)
    }
}

/**
 * 데모 배경. 글래스 표면이면 비칠 색이 있도록 [glass](없으면 오로라 그라디언트)를 깔고, 단색과 톤이면 스캐폴드가 칠한 바탕색을 그대로 둔다.
 * [KineticaScaffold]의 background 안에서 부른다.
 */
@Composable
internal fun BoxScope.DemoBackdrop(glass: (@Composable BoxScope.() -> Unit)? = null) {
    val surface = LocalKineticaSurface.current
    if (surface !is SurfaceStyle.Glass) return
    if (glass != null) glass() else Aurora(Modifier.fillMaxSize(), dark = surface.isDark)
}

/** 밝은 글래스면 [light], 어두운 글래스면 [dark] 팔레트의 메시 그라디언트. [DemoBackdrop] 안에서 쓴다. */
@Composable
internal fun BoxScope.MeshBackdrop(light: MeshPalette, dark: MeshPalette) {
    MeshGradientBackground(Modifier.fillMaxSize(), palette = if (LocalKineticaSurface.current.isDark) dark else light)
}

/** 모양 시트의 내용: 표면 종류, 프리셋, 밝기, 형태. 앱 전체에서 모양을 고르는 곳은 이 한 곳이다. */
@Composable
internal fun LookPicker(modifier: Modifier = Modifier) {
    val look = LocalShowcaseLook.current
    val faint = LocalKineticaContentColor.current.copy(alpha = 0.6f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("표면", 13, FontWeight.SemiBold, color = faint)
        KSegmentedControl(
            SurfaceFamily.entries.map { it.label },
            look.family.ordinal,
            { look.select(SurfaceFamily.entries[it]) },
            Modifier.fillMaxWidth(),
        )
        Label("프리셋", 13, FontWeight.SemiBold, color = faint, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val names = listOf("화면 기본") + presetsOf(look.family).map { it.name }
            names.forEachIndexed { i, name ->
                val index = i - 1
                Pill(name, filled = look.preset == index) { look.select(look.family, index) }
            }
        }
        Label("밝기", 13, FontWeight.SemiBold, color = faint, modifier = Modifier.padding(top = 8.dp))
        KSegmentedControl(
            Brightness.entries.map { it.label },
            look.brightness.ordinal,
            { look.brightness = Brightness.entries[it] },
            Modifier.fillMaxWidth(),
        )
        Label("형태", 13, FontWeight.SemiBold, color = faint, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KineticaDesigns.all.forEachIndexed { index, (name, _) ->
                Pill(name, filled = look.design == index) { look.selectDesign(index) }
            }
        }
    }
}

/** 앱 전체 모양을 고르는 바텀 시트. 상단 바 버튼과 첫 화면의 모양 줄이 같은 시트를 연다. */
@Composable
internal fun LookSheet(visible: Boolean, onDismiss: () -> Unit) {
    KSheet(visible, onDismiss, title = "모양") {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Label("모양", SHEET_TITLE_SP, FontWeight.Bold)
            Label(
                "여기서 고른 표면, 밝기, 형태를 모든 화면이 따릅니다. 화면 기본은 화면마다 처음 디자인한 스타일입니다. " +
                    "단색과 톤은 배경을 기록하지 않아 블러 비용이 없습니다. 형태는 모서리, 깊이, 밀도, 컨트롤 모양을 함께 바꿉니다.",
                SHEET_BODY_SP,
                color = LocalKineticaContentColor.current.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )
            LookPicker(Modifier.padding(bottom = 8.dp))
        }
    }
}

/** 상단 바 끝에 두는 모양 버튼. 누르면 [LookSheet]가 열린다. */
@Composable
internal fun SurfacePickerButton() {
    var open by rememberSaveable { mutableStateOf(false) }
    KIconButton(ShowcaseIcons.Contrast, "모양", { open = true }, variant = KVariant.Text, tone = KTone.Neutral)
    LookSheet(open) { open = false }
}

/** 프리셋 하나. 밝은 화면과 어두운 화면에 쓸 표면을 함께 둔다. 한 가지 밝기만 있는 프리셋은 둘이 같다. */
internal class SurfacePreset(val name: String, val light: SurfaceStyle, val dark: SurfaceStyle) {
    fun surface(dark: Boolean): SurfaceStyle = if (dark) this.dark else light
}

private fun glass(name: String, light: GlassStyle, dark: GlassStyle = light) =
    SurfacePreset(name, SurfaceStyle.Glass(light), SurfaceStyle.Glass(dark))

private fun tonal(name: String, accent: Color) = SurfacePreset(name, SurfaceStyle.Tonal(accent), SurfaceStyle.Tonal(accent, dark = true))

private val GLASS_PRESETS = listOf(
    glass("Liquid", GlassStyles.Liquid, GlassStyles.LiquidDark),
    glass("Acrylic", GlassStyles.Acrylic, GlassStyles.AcrylicDark),
    glass("One UI", GlassStyles.OneUi, GlassStyles.OneUiDark),
    glass("Vision", GlassStyles.Vision),
    glass("Material", GlassStyles.Material, GlassStyles.MaterialDark),
    glass("Mica", GlassStyles.Mica, GlassStyles.MicaDark),
    glass("HarmonyOS", GlassStyles.HarmonyOs),
    glass("Obsidian", GlassStyles.Obsidian),
    glass("Pastel", GlassStyles.Pastel),
    glass("Frosted", GlassStyles.Frosted, GlassStyles.Frosted.copy(tint = Color.Black.copy(alpha = 0.35f), onGlass = Color.White)),
)

private val SOLID_PRESETS = listOf(
    SurfacePreset("Clean", SurfaceStyles.CleanLight, SurfaceStyles.CleanDark),
    SurfacePreset("One UI", SurfaceStyles.OneUi, SurfaceStyles.OneUiDark),
    SurfacePreset("Material", SurfaceStyles.Material, SurfaceStyles.MaterialDark),
)

private val TONAL_PRESETS = listOf(
    tonal("파랑", Color(0xFF3B6CF6)),
    tonal("보라", Color(0xFF8B5CF6)),
    tonal("초록", Color(0xFF16A34A)),
    tonal("주황", Color(0xFFF97316)),
)

/** [family]에서 고를 수 있는 프리셋 */
internal fun presetsOf(family: SurfaceFamily): List<SurfacePreset> = when (family) {
    SurfaceFamily.Glass -> GLASS_PRESETS
    SurfaceFamily.Solid -> SOLID_PRESETS
    SurfaceFamily.Tonal -> TONAL_PRESETS
}

private const val SHEET_TITLE_SP = 20
private const val SHEET_BODY_SP = 14
private val TONAL_ACCENT = Color(0xFF3B6CF6)
