package com.naury.kinetica.ui

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
import androidx.compose.ui.text.style.TextOverflow

/**
 * kinetica-ui 컴포넌트가 기본값으로 쓰는 색. [GlassTheme]로 한 번에 바꾼다.
 *
 * Material 3 앱이라면 `GlassColors(accent = colorScheme.primary, onAccent = colorScheme.onPrimary, destructive = colorScheme.error)`처럼
 * 호스트 색을 그대로 넘긴다.
 *
 * @property accent 버튼, 칩, 슬라이더, 진행 표시, 입력란 포커스, 달력 선택처럼 선택되거나 활성인 상태를 칠하는 색
 * @property onAccent [accent] 위에 올리는 글자와 아이콘 색
 * @property positive 스위치가 켜진 상태와 밀어서 확인하기처럼 긍정적인 동작을 칠하는 색
 * @property destructive 배지와 삭제 메뉴처럼 주의를 끌거나 되돌리기 어려운 동작을 칠하는 색
 */
@Immutable
public data class GlassColors(
    val accent: Color = Color(DEFAULT_ACCENT),
    val onAccent: Color = Color.White,
    val positive: Color = Color(DEFAULT_POSITIVE),
    val destructive: Color = Color(DEFAULT_DESTRUCTIVE),
)

/** 지금 적용된 [GlassColors]. [GlassTheme] 밖에서는 iOS 시스템 색에 가까운 기본값이다. */
public val LocalGlassColors: ProvidableCompositionLocal<GlassColors> = staticCompositionLocalOf { GlassColors() }

/**
 * kinetica-ui 컴포넌트의 글자가 따를 글꼴. 이 스타일에서 글꼴 관련 값(fontFamily, fontFeatureSettings, fontSynthesis,
 * localeList, platformStyle)만 가져오고, 글자 크기와 굵기, 줄 높이, 색은 컴포넌트가 정한다. 그래서 `MaterialTheme.typography.bodyLarge`를
 * 그대로 넘겨도 작은 배지나 칩의 크기가 바뀌지 않는다. 기본값은 [TextStyle.Default]다.
 */
public val LocalGlassTextStyle: ProvidableCompositionLocal<TextStyle> = staticCompositionLocalOf { TextStyle.Default }

/**
 * 글래스 스타일, 강조색, 글꼴을 한 번에 지정한다. 화면 루트(보통 `MaterialTheme` 바로 안쪽)에 한 번 감싸면
 * 그 안의 모든 kinetica-ui 컴포넌트와 [GlassScaffold]가 이 값을 기본값으로 쓴다. 컴포넌트에 직접 넘긴 값이 있으면 그 값이 우선한다.
 *
 * kinetica-ui는 Material 3에 의존하지 않으므로 Material 테마 값은 호스트가 넘긴다.
 *
 * ```kotlin
 * MaterialTheme(colorScheme = scheme, typography = typography) {
 *     GlassTheme(
 *         colors = GlassColors(accent = MaterialTheme.colorScheme.primary, onAccent = MaterialTheme.colorScheme.onPrimary),
 *         textStyle = MaterialTheme.typography.bodyLarge,
 *     ) { App() }
 * }
 * ```
 *
 * @param darkTheme 다크 테마인지. 기본값은 시스템 설정이다. 앱이 직접 다크 모드를 정한다면 그 값을 넘긴다.
 * @param style 기본 글래스 스타일. 기본값은 [darkTheme]에 따라 [GlassStyles.Liquid] 또는 [GlassStyles.LiquidDark]다.
 * @param colors 강조색 등 컴포넌트 기본 색
 * @param textStyle 컴포넌트 글자가 따를 글꼴. 글꼴 관련 값만 쓰인다([LocalGlassTextStyle] 참고).
 */
@Composable
public fun GlassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    style: GlassStyle = if (darkTheme) GlassStyles.LiquidDark else GlassStyles.Liquid,
    colors: GlassColors = LocalGlassColors.current,
    textStyle: TextStyle = LocalGlassTextStyle.current,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalGlassStyle provides style,
        LocalGlassContentColor provides style.onGlass,
        LocalGlassColors provides colors,
        LocalGlassTextStyle provides textStyle,
        content = content,
    )
}

/** [GlassTheme]가 지금 제공하는 값. 직접 만든 컴포넌트를 kinetica-ui와 같은 색과 글꼴로 맞출 때 읽는다. */
public object GlassTheme {
    /** 지금 적용된 글래스 스타일 */
    public val style: GlassStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassStyle.current

    /** 지금 적용된 컴포넌트 색 */
    public val colors: GlassColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassColors.current

    /** 지금 적용된 글자 바탕 스타일 */
    public val textStyle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassTextStyle.current
}

/** [LocalGlassTextStyle] 위에 [style]을 덮어 그리는 글자. 컴포넌트 안 글자는 모두 이것으로 그려 호스트 글꼴을 따른다. */
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

/** [style]에 [LocalGlassTextStyle]의 글꼴을 입힌다. [style]이 글꼴을 직접 정했으면 그 값이 우선한다. 입력란이나 직접 측정하는 글자에 쓴다. */
@Composable
@ReadOnlyComposable
internal fun themed(style: TextStyle): TextStyle = fontOf(LocalGlassTextStyle.current).merge(style)

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
