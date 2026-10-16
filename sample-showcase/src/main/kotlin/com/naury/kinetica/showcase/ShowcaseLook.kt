package com.naury.kinetica.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KSheet
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KVariant
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

/** 밝기. [Auto]면 데모마다 처음 디자인한 밝기를 쓴다. */
internal enum class Brightness(val label: String) {
    Auto("화면 기본"),
    Light("밝게"),
    Dark("어둡게"),
}

/** 첫 화면과 데모 상단 바에서 고른 표면 스타일. 모든 데모가 이 값을 따른다. */
@Stable
internal class ShowcaseLook(family: SurfaceFamily = SurfaceFamily.Glass, brightness: Brightness = Brightness.Auto) {
    var family by mutableStateOf(family)
    var brightness by mutableStateOf(brightness)

    /** 처음 디자인이 어두운 화면인지([defaultDark])를 받아 지금 밝기 설정으로 푼다. */
    fun isDark(defaultDark: Boolean): Boolean = when (brightness) {
        Brightness.Auto -> defaultDark
        Brightness.Light -> false
        Brightness.Dark -> true
    }

    companion object {
        val Saver: Saver<ShowcaseLook, IntArray> = Saver(
            save = { intArrayOf(it.family.ordinal, it.brightness.ordinal) },
            restore = { ShowcaseLook(SurfaceFamily.entries[it[0]], Brightness.entries[it[1]]) },
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
internal fun demoSurface(look: DemoLook): SurfaceStyle {
    val showcase = LocalShowcaseLook.current
    val dark = showcase.isDark(look.defaultDark)
    return when (showcase.family) {
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

/** 표면 종류와 밝기를 고르는 두 줄 세그먼트. */
@Composable
internal fun SurfacePicker(modifier: Modifier = Modifier) {
    val look = LocalShowcaseLook.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KSegmentedControl(
            SurfaceFamily.entries.map { it.label },
            look.family.ordinal,
            { look.family = SurfaceFamily.entries[it] },
            Modifier.fillMaxWidth(),
        )
        KSegmentedControl(
            Brightness.entries.map { it.label },
            look.brightness.ordinal,
            { look.brightness = Brightness.entries[it] },
            Modifier.fillMaxWidth(),
        )
    }
}

/** 상단 바 끝에 두는 표면 스타일 버튼. 누르면 바텀 시트에서 표면 종류와 밝기를 바꾼다. */
@Composable
internal fun SurfacePickerButton() {
    var open by rememberSaveable { mutableStateOf(false) }
    KIconButton(ShowcaseIcons.Contrast, "표면 스타일", { open = true }, variant = KVariant.Text, tone = KTone.Neutral)
    KSheet(open, { open = false }, title = "표면 스타일") {
        Label("표면 스타일", SHEET_TITLE_SP, FontWeight.Bold)
        Label(
            "같은 화면을 글래스, 단색, 톤 표면으로 바꿔 봅니다. 단색과 톤은 배경을 기록하지 않아 블러 비용이 없습니다.",
            SHEET_BODY_SP,
            color = LocalKineticaContentColor.current.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        SurfacePicker(Modifier.padding(bottom = 8.dp))
    }
}

private const val SHEET_TITLE_SP = 20
private const val SHEET_BODY_SP = 14
private val TONAL_ACCENT = Color(0xFF3B6CF6)
