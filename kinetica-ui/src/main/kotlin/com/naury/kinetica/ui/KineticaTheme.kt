package com.naury.kinetica.ui

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow

/**
 * kinetica-ui 컴포넌트가 기본값으로 쓰는 색. [KineticaTheme]로 한 번에 바꾼다.
 *
 * Material 3 앱이라면 `KineticaColors(accent = colorScheme.primary, onAccent = colorScheme.onPrimary, destructive = colorScheme.error,
 * onDestructive = colorScheme.onError)`처럼 호스트 색을 짝으로 넘긴다. 다크 테마의 `error`는 어두운 글자와 짝을 이루는 옅은 색이라,
 * 짝인 `onError` 없이 넘기면 채운 버튼이 흰 글자 대비를 맞추려고 색을 진하게 바꿔 탁해진다.
 *
 * @property accent 버튼, 칩, 슬라이더, 진행 표시, 입력란 포커스, 달력 선택처럼 선택되거나 활성인 상태를 칠하는 색
 * @property onAccent [accent] 위에 올리는 글자와 아이콘 색
 * @property positive 스위치가 켜진 상태와 밀어서 확인하기처럼 긍정적인 동작을 칠하는 색
 * @property destructive 배지와 삭제 메뉴처럼 주의를 끌거나 되돌리기 어려운 동작을 칠하는 색
 * @property onPositive [positive]로 채운 버튼 위 글자와 아이콘 색
 * @property onDestructive [destructive]로 채운 버튼 위 글자와 아이콘 색
 */
@Immutable
public data class KineticaColors(
    val accent: Color = Color(DEFAULT_ACCENT),
    val onAccent: Color = Color.White,
    val positive: Color = Color(DEFAULT_POSITIVE),
    val destructive: Color = Color(DEFAULT_DESTRUCTIVE),
    val onPositive: Color = Color.White,
    val onDestructive: Color = Color.White,
)

/** 지금 적용된 [KineticaColors]. [KineticaTheme] 밖에서는 [KineticaColors]의 기본값이다. */
public val LocalKineticaColors: ProvidableCompositionLocal<KineticaColors> = staticCompositionLocalOf { KineticaColors() }

/**
 * kinetica-ui 컴포넌트의 글자가 따를 글꼴. 이 스타일에서 글꼴 관련 값(fontFamily, fontFeatureSettings, fontSynthesis,
 * localeList, platformStyle)만 가져오고, 글자 크기와 굵기, 줄 높이, 색은 컴포넌트가 정한다. 그래서 `MaterialTheme.typography.bodyLarge`를
 * 그대로 넘겨도 작은 배지나 칩의 크기가 바뀌지 않는다. 기본값은 [TextStyle.Default]다.
 */
public val LocalKineticaTextStyle: ProvidableCompositionLocal<TextStyle> = staticCompositionLocalOf { TextStyle.Default }

/**
 * 표면 스타일, 강조색, 글꼴, 눌림 표시를 한 번에 지정한다. 화면 루트(보통 `MaterialTheme` 바로 안쪽)에 한 번 감싸면
 * 그 안의 모든 kinetica-ui 컴포넌트와 [KineticaScaffold]가 이 값을 기본값으로 쓴다. 컴포넌트에 직접 넘긴 값이 있으면 그 값이 우선한다.
 *
 * kinetica-ui는 Material 3에 의존하지 않으므로 Material 테마 값은 호스트가 넘긴다.
 *
 * ```kotlin
 * MaterialTheme(colorScheme = scheme, typography = typography) {
 *     KineticaTheme(
 *         colors = KineticaColors(accent = MaterialTheme.colorScheme.primary, onAccent = MaterialTheme.colorScheme.onPrimary),
 *         textStyle = MaterialTheme.typography.bodyLarge,
 *     ) { App() }
 * }
 * ```
 *
 * @param darkTheme 다크 테마인지. 기본값은 시스템 설정이다. 앱이 직접 다크 모드를 정한다면 그 값을 넘긴다.
 * 글래스 대신 단색 표면을 쓰려면 `surface = SurfaceStyles.CleanLight`처럼 넘긴다. 단색과 톤 표면은 배경을 기록하지 않으므로 그리기 비용이 거의 없다.
 *
 * Material 3 `Card(onClick)`와 `Surface(onClick)`는 `LocalIndication`이 아니라 자기 `ripple()`을 직접 쓰므로 이 안에서도 리플이 나온다.
 * 그런 요소에는 `Modifier.kClickable`을 쓰거나 `onClick` 없는 `Card` 안에 넣는다. 또 이 안에서 `MaterialTheme`을 다시 감싸면
 * `MaterialTheme`이 `LocalIndication`을 리플로 되돌리므로, `MaterialTheme`은 늘 이 바깥에 둔다.
 *
 * @param surface 기본 표면 스타일. 기본값은 [darkTheme]에 따라 [SurfaceStyles.Liquid] 또는 [SurfaceStyles.LiquidDark]다.
 * @param colors 강조색 등 컴포넌트 기본 색
 * @param textStyle 컴포넌트 글자가 따를 글꼴. 글꼴 관련 값만 쓰인다([LocalKineticaTextStyle] 참고).
 * @param indication 이 안에서 쓴 일반 `clickable`의 눌림 표시(`LocalIndication`). 기본값은 사각 리플 대신 [KIndication]이고,
 * null이면 바깥(호스트) 값을 그대로 둔다.
 * @param design 컴포넌트의 형태(모서리, 깊이, 밀도, 인디케이터, 컨트롤 모양, 모션). 기본값은 바깥 값이고, 가장 바깥이면
 * [KineticaDesigns.Cupertino]다. 표면 스타일과 따로 고른다.
 */
@Composable
public fun KineticaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    surface: SurfaceStyle = if (darkTheme) SurfaceStyles.LiquidDark else SurfaceStyles.Liquid,
    colors: KineticaColors = LocalKineticaColors.current,
    textStyle: TextStyle = LocalKineticaTextStyle.current,
    indication: Indication? = KIndication(),
    design: KineticaDesign = LocalKineticaDesign.current,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalKineticaDesign provides design,
        LocalKineticaSurface provides surface,
        LocalGlassStyle provides surface.glassStyle(),
        LocalKineticaContentColor provides surface.contentColor,
        LocalKineticaColors provides colors,
        LocalKineticaTextStyle provides textStyle,
        LocalIndication provides (indication ?: LocalIndication.current),
        LocalKineticaIndication provides indication,
        content = content,
    )
}

/** [KineticaTheme]가 지금 제공하는 값. 직접 만든 컴포넌트를 kinetica-ui와 같은 색과 글꼴로 맞출 때 읽는다. */
public object KineticaTheme {
    /** 지금 적용된 표면 스타일 */
    public val surface: SurfaceStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalKineticaSurface.current

    /** [Modifier.glass]가 기본으로 쓰는 글래스 스타일. 표면이 글래스가 아니면 표면 밝기에 맞춘 Liquid 스타일이다. */
    public val glassStyle: GlassStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassStyle.current

    /** 지금 적용된 컴포넌트 색 */
    public val colors: KineticaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKineticaColors.current

    /** 지금 적용된 디자인 언어 */
    public val design: KineticaDesign
        @Composable
        @ReadOnlyComposable
        get() = LocalKineticaDesign.current

    /** 지금 적용된 모양 토큰. `KineticaTheme.shapes.shape(KineticaTheme.shapes.card)`처럼 직접 만든 컴포넌트의 모양을 맞출 때 읽는다. */
    public val shapes: KineticaShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalKineticaDesign.current.shapes

    /** 지금 적용된 글자 바탕 스타일 */
    public val textStyle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalKineticaTextStyle.current

    /**
     * 키트 컴포넌트가 쓰는 글자 단계. [textStyle]의 글꼴과 [design]의 굵기를 입힌 값이다.
     * 직접 만든 글자를 컴포넌트와 같은 크기로 맞출 때 `KineticaTheme.typography.body.copy(color = ...)`처럼 읽는다.
     */
    public val typography: KineticaTypography
        @Composable
        @ReadOnlyComposable
        get() = KineticaTypography.of(LocalKineticaTextStyle.current, LocalKineticaDesign.current)
}

/** 블러 효과가 기본으로 쓸 글래스 스타일. 글래스가 아닌 표면은 밝기에 맞춘 Liquid 스타일을 고른다. */
internal fun SurfaceStyle.glassStyle(): GlassStyle = when (this) {
    is SurfaceStyle.Glass -> style
    else -> if (isDark) GlassStyles.LiquidDark else GlassStyles.Liquid
}

/** [LocalKineticaTextStyle] 위에 [style]을 덮어 그리는 글자. 컴포넌트 안 글자는 모두 이것으로 그려 호스트 글꼴을 따른다. */
@Composable
internal fun ThemedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    color: ColorProducer? = null,
) {
    BasicText(text, modifier, style = themed(style), overflow = overflow, maxLines = maxLines, color = color)
}

/**
 * [style]에 [LocalKineticaTextStyle]의 글꼴을 입힌다. [style]이 글꼴을 직접 정했으면 그 값이 우선한다. 입력란이나 직접 측정하는 글자에 쓴다.
 * 문단 방향은 [withContentDirection]으로 내용에 맞춘다.
 */
@Composable
@ReadOnlyComposable
internal fun themed(style: TextStyle): TextStyle = fontOf(LocalKineticaTextStyle.current).merge(style).withContentDirection()

/**
 * 방향을 정하지 않은 글자는 첫 강한 문자로 문단 방향을 정한다. Compose는 Unspecified를 레이아웃 방향 그대로 풀기 때문에
 * RTL 화면에서 한국어 문장을 그리면 끝의 마침표가 앞으로 간다. 강한 문자가 없으면 레이아웃 방향을 따른다.
 */
internal fun TextStyle.withContentDirection(): TextStyle =
    if (textDirection == TextDirection.Unspecified) copy(textDirection = TextDirection.Content) else this

internal fun fontOf(base: TextStyle): TextStyle = if (base == TextStyle.Default) {
    base
} else {
    TextStyle(
        fontFamily = base.fontFamily,
        fontFeatureSettings = base.fontFeatureSettings,
        fontSynthesis = base.fontSynthesis,
        localeList = base.localeList,
        platformStyle = base.platformStyle,
    )
}

private const val DEFAULT_ACCENT = 0xFF0A84FF
private const val DEFAULT_POSITIVE = 0xFF34C759
private const val DEFAULT_DESTRUCTIVE = 0xFFFF3B30
