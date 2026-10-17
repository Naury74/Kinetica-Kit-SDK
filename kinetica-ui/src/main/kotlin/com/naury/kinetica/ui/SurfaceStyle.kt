package com.naury.kinetica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * kinetica-ui 컴포넌트의 표면을 어떻게 칠할지 정한다. 버튼, 카드, 시트, 다이얼로그, 탭 바, 입력란이 모두 이 값으로 배경을 그린다.
 *
 * - [Glass]: 배경을 블러 처리해 비치는 글래스 표면. [KineticaScaffold]가 화면을 기록해 두어야 블러가 보인다.
 * - [Solid]: 불투명한 단색 표면. 테두리와 그림자를 줄 수 있고, 배경 기록이 필요 없어 그리기 비용이 거의 없다.
 * - [Tonal]: 강조색을 옅게 섞은 단색 표면. Material 3의 톤 표면과 비슷하다.
 *
 * 앱 전체는 [KineticaTheme]의 `surface`로 고르고, 컴포넌트마다 `surface` 파라미터로 바꿀 수 있다. 프리셋은 [SurfaceStyles]에 있다.
 * 글자와 아이콘 색([contentColor]), 눌림 표시 색, [KineticaScaffold]가 고르는 시스템 바 아이콘 밝기가 이 값에서 정해진다.
 */
@Immutable
public sealed interface SurfaceStyle {
    /** 표면 위에 올리는 글자와 아이콘 색 */
    public val contentColor: Color

    /**
     * 이 표면에 어울리는 화면 바탕색. [KineticaScaffold]가 `background` 아래에 칠한다.
     * 글래스는 비칠 배경을 호스트가 그리므로 투명하다.
     */
    public val background: Color

    /** 어두운 표면인지. [contentColor]가 밝으면 true다. 시스템 바 아이콘을 밝게 할지 정할 때 쓴다. */
    public val isDark: Boolean get() = contentColor.luminance() > DARK_CONTENT_LUMINANCE

    /** 다이얼로그와 바텀 시트에 맞춘 표면. 글래스는 블러와 색을 진하게 하고, 단색은 그림자를 키운다. */
    public fun forPopup(): SurfaceStyle

    /**
     * 글래스 표면. 블러 효과 자체는 [style]이 정한다.
     *
     * @property style 블러, 색, 테두리, 그림자를 정하는 글래스 스타일
     */
    @Immutable
    public data class Glass(val style: GlassStyle) : SurfaceStyle {
        override val contentColor: Color get() = style.onGlass
        override val background: Color get() = Color.Transparent

        override fun forPopup(): SurfaceStyle = Glass(style.forPopup())
    }

    /**
     * 불투명한 단색 표면. [elevation]이 0보다 크면 표면 바깥에 부드러운 그림자를 드리운다.
     * 생성할 때 [borderWidth]나 [elevation]이 음수면 [IllegalArgumentException]을 던진다.
     *
     * @property color 표면 색
     * @property contentColor 표면 위 글자와 아이콘 색
     * @property background 화면 바탕색. 카드가 바탕과 구분되도록 [color]와 조금 다르게 준다.
     * @property border 테두리 색. 투명하면 그리지 않는다.
     * @property borderWidth 테두리 두께
     * @property elevation 그림자 반경. 0이면 그림자가 없다.
     */
    @Immutable
    public data class Solid(
        val color: Color,
        override val contentColor: Color,
        override val background: Color,
        val border: Color = Color.Transparent,
        val borderWidth: Dp = 0.dp,
        val elevation: Dp = 0.dp,
    ) : SurfaceStyle {
        init {
            require(borderWidth >= 0.dp && elevation >= 0.dp) { "borderWidth와 elevation은 0 이상이어야 합니다." }
        }

        override fun forPopup(): SurfaceStyle = copy(
            // 어두운 화면에서는 그림자가 잘 안 보이므로 떠 있는 판을 조금 밝혀 구분한다.
            color = if (isDark) lerp(color, Color.White, POPUP_DARK_LIFT) else color,
            elevation = maxOf(elevation, POPUP_ELEVATION),
        )
    }

    /**
     * [accent]를 옅게 섞은 톤 표면. 밝은 표면이면 흰색에, [dark]면 짙은 회색에 강조색을 섞는다.
     * 생성할 때 [elevation]이 음수면 [IllegalArgumentException]을 던진다.
     *
     * @property accent 섞을 강조색
     * @property dark 어두운 톤 표면인지
     * @property elevation 그림자 반경. 0이면 그림자가 없다.
     */
    @Immutable
    public data class Tonal(val accent: Color, val dark: Boolean = false, val elevation: Dp = 0.dp) : SurfaceStyle {
        init {
            require(elevation >= 0.dp) { "elevation은 0 이상이어야 합니다." }
        }

        /** 강조색을 섞은 표면 색 */
        public val color: Color = if (dark) lerp(TONAL_DARK_BASE, accent, TONAL_DARK_MIX) else lerp(Color.White, accent, TONAL_LIGHT_MIX)

        override val contentColor: Color = if (dark) {
            lerp(
                Color.White,
                accent,
                TONAL_DARK_INK,
            )
        } else {
            lerp(TONAL_LIGHT_INK, accent, TONAL_INK_MIX)
        }

        override val background: Color =
            if (dark) lerp(TONAL_DARK_PAGE, accent, TONAL_DARK_PAGE_MIX) else lerp(Color.White, accent, TONAL_LIGHT_PAGE_MIX)

        /** 같은 톤 표면끼리 겹쳐도 경계가 보이도록 두르는 옅은 강조색 테두리 */
        public val border: Color = accent.copy(alpha = if (dark) TONAL_DARK_BORDER else TONAL_LIGHT_BORDER)

        override fun forPopup(): SurfaceStyle = copy(elevation = maxOf(elevation, POPUP_ELEVATION))
    }
}

/**
 * 표면 스타일 프리셋. 이름이 `Dark`로 끝나는 것은 다크 테마용이다.
 * 글래스 프리셋은 [GlassStyles]의 같은 이름 스타일을 [SurfaceStyle.Glass]로 감싼 것이다.
 */
public object SurfaceStyles {
    @JvmField public val Liquid: SurfaceStyle = SurfaceStyle.Glass(GlassStyles.Liquid)

    @JvmField public val LiquidDark: SurfaceStyle = SurfaceStyle.Glass(GlassStyles.LiquidDark)

    @JvmField public val Acrylic: SurfaceStyle = SurfaceStyle.Glass(GlassStyles.Acrylic)

    @JvmField public val AcrylicDark: SurfaceStyle = SurfaceStyle.Glass(GlassStyles.AcrylicDark)

    @JvmField public val OneUiGlass: SurfaceStyle = SurfaceStyle.Glass(GlassStyles.OneUi)

    @JvmField public val OneUiGlassDark: SurfaceStyle = SurfaceStyle.Glass(GlassStyles.OneUiDark)

    @JvmField public val Vision: SurfaceStyle = SurfaceStyle.Glass(GlassStyles.Vision)

    @JvmField public val Frosted: SurfaceStyle = SurfaceStyle.Glass(GlassStyles.Frosted)

    @JvmField public val Mica: SurfaceStyle = SurfaceStyle.Glass(GlassStyles.Mica)

    @JvmField public val MicaDark: SurfaceStyle = SurfaceStyle.Glass(GlassStyles.MicaDark)

    @JvmField public val MaterialGlass: SurfaceStyle = SurfaceStyle.Glass(GlassStyles.Material)

    @JvmField public val MaterialGlassDark: SurfaceStyle = SurfaceStyle.Glass(GlassStyles.MaterialDark)

    @JvmField public val HarmonyOs: SurfaceStyle = SurfaceStyle.Glass(GlassStyles.HarmonyOs)

    @JvmField public val Obsidian: SurfaceStyle = SurfaceStyle.Glass(GlassStyles.Obsidian)

    @JvmField public val Pastel: SurfaceStyle = SurfaceStyle.Glass(GlassStyles.Pastel)

    /** 흰 카드에 머리카락 두께 테두리와 옅은 그림자를 준 깔끔한 단색 표면. 바탕은 iOS 설정 화면 같은 연회색이다. */
    @JvmField
    public val CleanLight: SurfaceStyle = SurfaceStyle.Solid(
        color = Color.White,
        contentColor = Color(INK),
        background = Color(0xFFF2F2F7),
        border = Color.Black.copy(alpha = 0.06f),
        borderWidth = 1.dp,
        elevation = 6.dp,
    )

    /** [CleanLight]의 다크 테마. 그림자 대신 옅은 흰 테두리로 표면을 구분한다. */
    @JvmField
    public val CleanDark: SurfaceStyle = SurfaceStyle.Solid(
        color = Color(0xFF1C1C1E),
        contentColor = Color(0xFFF5F5F7),
        background = Color.Black,
        border = Color.White.copy(alpha = 0.08f),
        borderWidth = 1.dp,
        elevation = 4.dp,
    )

    /** One UI 단색 스타일. 테두리와 그림자 없이 흰 카드를 연회색 바탕에 놓는다. */
    @JvmField
    public val OneUi: SurfaceStyle = SurfaceStyle.Solid(
        color = Color(0xFFFCFCFC),
        contentColor = Color(0xFF111111),
        background = Color(0xFFF0F0F2),
    )

    @JvmField
    public val OneUiDark: SurfaceStyle = SurfaceStyle.Solid(
        color = Color(0xFF1A1A1C),
        contentColor = Color(0xFFFAFAFA),
        background = Color(0xFF050505),
    )

    /** Material 3 단색 스타일. 보라빛 컨테이너 색 표면을 테두리 없이 쓴다. */
    @JvmField
    public val Material: SurfaceStyle = SurfaceStyle.Solid(
        color = Color(0xFFF3EDF7),
        contentColor = Color(0xFF1D1B20),
        background = Color(0xFFFEF7FF),
        elevation = 1.dp,
    )

    @JvmField
    public val MaterialDark: SurfaceStyle = SurfaceStyle.Solid(
        color = Color(0xFF211F26),
        contentColor = Color(0xFFE6E0E9),
        background = Color(0xFF141218),
    )

    /** 파란 강조색을 섞은 톤 표면 */
    @JvmField public val Tonal: SurfaceStyle = SurfaceStyle.Tonal(Color(DEFAULT_TONAL_ACCENT))

    @JvmField public val TonalDark: SurfaceStyle = SurfaceStyle.Tonal(Color(DEFAULT_TONAL_ACCENT), dark = true)

    /** [accent]를 섞은 톤 표면. 앱 강조색에 맞춘 표면을 만들 때 쓴다. */
    @JvmStatic
    public fun tonal(accent: Color, dark: Boolean = false): SurfaceStyle = SurfaceStyle.Tonal(accent, dark)
}

/** 지금 적용된 표면 스타일. [KineticaTheme]과 [KineticaScaffold]가 넣는다. 둘 다 없으면 [SurfaceStyles.Liquid]다. */
public val LocalKineticaSurface: ProvidableCompositionLocal<SurfaceStyle> = staticCompositionLocalOf { SurfaceStyles.Liquid }

/**
 * [surface]로 표면을 그린다. 모든 kinetica-ui 컴포넌트가 배경을 이 수정자로 칠하므로, 직접 만든 컴포넌트도 이것을 쓰면 테마와 같은 표면이 된다.
 * 글래스면 [glass]와 같고, 단색이나 톤이면 [shape] 모양으로 색과 테두리, 그림자를 그린다. 단색과 톤은 [backdrop]을 쓰지 않는다.
 * 콘텐츠를 자르지는 않으므로 눌림 표시까지 모양 안에 가두려면 `clip(shape)`을 이어 붙인다.
 *
 * 다이얼로그나 바텀 시트 안에 놓이면 판과 같은 색으로 묻히지 않도록, 판 위에 얹힌 옅은 한 겹으로 그린다.
 *
 * @param intensity 효과 강도(0..1). 색과 그림자를 함께 줄인다. 범위를 벗어난 값은 0..1로 맞춘다.
 */
@Composable
public fun Modifier.kSurface(
    surface: SurfaceStyle = KineticaTheme.surface,
    shape: Shape = RectangleShape,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    intensity: Float = 1f,
): Modifier {
    val design = LocalKineticaDesign.current
    val nested = LocalGlassLayered.current && backdrop === LocalGlassBackdrop.current
    val styled = surface.withDepth(design)
    val offset = if (design.depth == KDepth.Offset && !nested) {
        hardShadow(shape, surface.contentColor, design.offsetDistance(surface.floating), intensity)
    } else {
        Modifier
    }
    return this.then(offset).then(
        when (styled) {
            is SurfaceStyle.Glass -> Modifier.glass(styled.style, shape, backdrop, intensity)
            else -> Modifier.filledSurface(styled, shape, intensity, nested)
        },
    )
}

/**
 * 떠 있는 판(다이얼로그, 시트, [KVariant.Elevated])인지. 그림자가 [FLOATING_ELEVATION] 이상이면 떠 있다고 본다.
 * 글래스 스타일은 기본 그림자가 커서 이 값으로 가리지 않는다.
 */
internal val SurfaceStyle.floating: Boolean
    get() = when (this) {
        is SurfaceStyle.Glass -> false
        is SurfaceStyle.Solid -> elevation >= FLOATING_ELEVATION
        is SurfaceStyle.Tonal -> elevation >= FLOATING_ELEVATION
    }

/**
 * [design]의 깊이 표현에 맞춰 그림자와 테두리를 바꾼 표면. [KDepth.Shadow]면 그대로다.
 * 톤 표면은 테두리를 바꿔야 하므로 같은 색의 단색 표면이 된다.
 */
internal fun SurfaceStyle.withDepth(design: KineticaDesign): SurfaceStyle {
    if (design.depth == KDepth.Shadow) return this
    val float = floating
    val ink = contentColor
    val (elevation, border, width) = when (design.depth) {
        KDepth.Shadow -> return this
        KDepth.Border -> Triple(if (float) POPUP_BORDER_SHADOW else 0.dp, borderInk(ink, isDark), design.borderWidth)
        KDepth.Flat -> Triple(if (float) POPUP_ELEVATION else 0.dp, Color.Transparent, 0.dp)
        KDepth.Offset -> Triple(0.dp, ink, design.borderWidth)
    }
    return when (this) {
        // 글래스는 팝업도 같은 그림자를 쓰므로 떠 있는지 가릴 수 없다. 그래서 0으로 없애지 않고 줄여서 판 사이 위계를 남긴다.
        is SurfaceStyle.Glass -> SurfaceStyle.Glass(
            style.copy(
                shadow = if (design.depth == KDepth.Offset) 0.dp else style.shadow * GLASS_FLAT_SHADOW,
                edgeLight = if (border.alpha > 0f) border else style.edgeLight,
                edgeWidth = if (border.alpha > 0f) width else style.edgeWidth,
            ),
        )

        is SurfaceStyle.Solid -> copy(elevation = elevation, border = border, borderWidth = width)

        is SurfaceStyle.Tonal -> SurfaceStyle.Solid(color, contentColor, background, border, width, elevation)
    }
}

/** 오른쪽 아래로 [distance]만큼 비낀 단단한 그림자. RTL에서는 왼쪽 아래로 비낀다. */
internal fun hardShadow(shape: Shape, color: Color, distance: Dp, intensity: Float = 1f): Modifier = Modifier.drawBehind {
    val d = distance.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    val dx = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) -d else d
    translate(dx, d) { drawOutline(outline, color.copy(alpha = color.alpha * intensity.coerceIn(0f, 1f))) }
}

/** 단색이나 톤 표면. 그림자 Paint와 Path는 모양이 바뀔 때만 새로 만든다. */
@Composable
private fun Modifier.filledSurface(surface: SurfaceStyle, shape: Shape, intensity: Float, nested: Boolean): Modifier {
    val cache = remember { EffectCache() }
    val fill = when (surface) {
        is SurfaceStyle.Solid -> surface.color
        is SurfaceStyle.Tonal -> surface.color
        is SurfaceStyle.Glass -> surface.style.fallback
    }
    val border = surface.edgeColor
    val borderWidth = when (surface) {
        is SurfaceStyle.Solid -> surface.borderWidth
        is SurfaceStyle.Tonal -> TONAL_BORDER_WIDTH
        is SurfaceStyle.Glass -> 0.dp
    }
    val elevation = when (surface) {
        is SurfaceStyle.Solid -> surface.elevation
        is SurfaceStyle.Tonal -> surface.elevation
        is SurfaceStyle.Glass -> 0.dp
    }
    val shadowAlpha = if (surface.isDark) DARK_SHADOW_ALPHA else LIGHT_SHADOW_ALPHA
    val ink = surface.contentColor
    return drawBehind {
        val level = intensity.coerceIn(0f, 1f)
        val outline = shape.createOutline(size, layoutDirection, this)
        if (nested) {
            // 판 위에 같은 색 표면을 얹으면 경계가 사라지므로 글자 색을 옅게 덮어 한 겹 얹힌 것처럼 보이게 한다.
            drawOutline(outline, ink.copy(alpha = NESTED_FILL * level))
            return@drawBehind
        }
        drawSoftShadow(outline, elevation.toPx() * level, shadowAlpha * level, cache)
        drawOutline(outline, fill.copy(alpha = fill.alpha * level))
        val stroke = borderWidth.toPx()
        if (border.alpha > 0f && stroke > 0f) {
            val path = cachedPath(outline, cache)
            // 경계선 안쪽 절반만 남겨 테두리가 모양 밖으로 번지지 않게 한다.
            clipPath(path) { drawPath(path, border.copy(alpha = border.alpha * level), style = Stroke(stroke * 2)) }
        }
    }
}

/**
 * 입력란에 쓸 표면. 시트나 카드처럼 이미 표면 위에 놓인 입력란은 그림자를 거의 없애 표면에 붙어 보이게 한다.
 * 그림자가 그대로면 입력란이 표면 위로 떠 보인다.
 */
@Composable
internal fun SurfaceStyle.forField(): SurfaceStyle {
    if (!LocalGlassLayered.current) return this
    return when (this) {
        is SurfaceStyle.Glass -> SurfaceStyle.Glass(style.copy(shadow = style.shadow.coerceAtMost(NESTED_SHADOW)))
        is SurfaceStyle.Solid -> copy(elevation = elevation.coerceAtMost(NESTED_SHADOW))
        is SurfaceStyle.Tonal -> copy(elevation = elevation.coerceAtMost(NESTED_SHADOW))
    }
}

/** 그림자를 뺀 표면. 경계에 잘려 그림자가 보이지 않는 곳(XML View 배경)에 쓴다. */
internal fun SurfaceStyle.withoutShadow(): SurfaceStyle = when (this) {
    is SurfaceStyle.Glass -> SurfaceStyle.Glass(style.copy(shadow = 0.dp))
    is SurfaceStyle.Solid -> copy(elevation = 0.dp)
    is SurfaceStyle.Tonal -> copy(elevation = 0.dp)
}

/** 글자 색만 바꾼 표면. 톤 표면은 같은 색의 단색 표면이 된다. */
internal fun SurfaceStyle.withContentColor(color: Color): SurfaceStyle = when (this) {
    is SurfaceStyle.Glass -> SurfaceStyle.Glass(style.copy(onGlass = color))
    is SurfaceStyle.Solid -> copy(contentColor = color)
    is SurfaceStyle.Tonal -> SurfaceStyle.Solid(this.color, color, background, elevation = elevation)
}

/** 표면 가장자리 색. 글래스는 글로우 테두리, 단색은 테두리 색이다. 없으면 투명하다. */
internal val SurfaceStyle.edgeColor: Color
    get() = when (this) {
        is SurfaceStyle.Glass -> style.edgeLight
        is SurfaceStyle.Solid -> border
        is SurfaceStyle.Tonal -> border
    }

/** 표면 기본 색. 글래스는 블러를 못 쓸 때 칠하는 색이다. 강조 칠이나 겹친 요소 색을 정할 때 쓴다. */
internal val SurfaceStyle.fillColor: Color
    get() = when (this) {
        is SurfaceStyle.Glass -> style.fallback
        is SurfaceStyle.Solid -> color
        is SurfaceStyle.Tonal -> color
    }

private const val DARK_CONTENT_LUMINANCE = 0.5f
private const val LIGHT_SHADOW_ALPHA = 0.10f
private const val DARK_SHADOW_ALPHA = 0.45f
private const val NESTED_FILL = 0.06f
private const val POPUP_DARK_LIFT = 0.06f
private val POPUP_ELEVATION = 18.dp
private val POPUP_BORDER_SHADOW = 10.dp
private const val GLASS_FLAT_SHADOW = 0.4f
private val NESTED_SHADOW = 2.dp
private const val INK = 0xFF15161A
private const val DEFAULT_TONAL_ACCENT = 0xFF0A84FF
private const val TONAL_LIGHT_MIX = 0.10f
private const val TONAL_LIGHT_PAGE_MIX = 0.04f
private const val TONAL_DARK_MIX = 0.16f
private const val TONAL_DARK_PAGE_MIX = 0.07f
private const val TONAL_INK_MIX = 0.22f
private const val TONAL_DARK_INK = 0.10f
private const val TONAL_LIGHT_BORDER = 0.18f
private const val TONAL_DARK_BORDER = 0.28f
private val TONAL_BORDER_WIDTH = 1.dp
private val TONAL_LIGHT_INK = Color(0xFF101318)
private val TONAL_DARK_BASE = Color(0xFF18191D)
private val TONAL_DARK_PAGE = Color(0xFF0C0D10)
