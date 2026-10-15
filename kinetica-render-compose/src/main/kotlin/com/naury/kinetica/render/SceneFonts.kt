package com.naury.kinetica.render

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.TextAlignment
import com.naury.kinetica.core.scene.TextDecoration
import com.naury.kinetica.core.scene.TextSpans

/**
 * 글꼴 이름을 Compose 글꼴로 바꾼다. 문서에는 글꼴 이름만 저장되므로, 앱에 번들하거나 다운로드한 글꼴은 호스트가 이름으로 찾아 준다.
 * `null`을 돌려주면 `sans`, `serif`, `monospace`, `cursive`는 시스템 글꼴로, 그 밖의 이름은 기본 글꼴로 그린다.
 */
public fun interface SceneFonts {
    public fun family(name: String): FontFamily?

    public companion object {
        public val Default: SceneFonts = SceneFonts { null }

        /** 이름과 글꼴을 짝지은 맵으로 만든다. */
        public fun of(families: Map<String, FontFamily>): SceneFonts = SceneFonts { families[it] }
    }
}

/** 화면 안의 모든 Kinetica 렌더러가 쓰는 글꼴. 앱 최상위에서 `CompositionLocalProvider`로 한 번만 제공한다. */
public val LocalSceneFonts: androidx.compose.runtime.ProvidableCompositionLocal<SceneFonts> =
    staticCompositionLocalOf { SceneFonts.Default }

/** 시스템 글꼴 이름. 편집기의 글꼴 목록에 쓴다. */
public val GENERIC_FONT_NAMES: List<String> = listOf("sans", "serif", "monospace", "cursive")

internal fun SceneFonts.resolve(name: String?): FontFamily? = name?.let { family(it) ?: generic(it) }

private fun generic(name: String): FontFamily? = when (name) {
    "sans" -> FontFamily.SansSerif
    "serif" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    else -> null
}

internal fun TextDecoration.toCompose(): androidx.compose.ui.text.style.TextDecoration? = when (this) {
    TextDecoration.None -> null
    TextDecoration.Underline -> androidx.compose.ui.text.style.TextDecoration.Underline
    TextDecoration.Strikethrough -> androidx.compose.ui.text.style.TextDecoration.LineThrough
}

/** 텍스트와 구간 스타일. 데이터 바인딩으로 텍스트가 짧아지면 텍스트 길이를 넘는 구간은 잘라서 쓴다. [sp]는 문서 단위를 sp로 바꾼다. */
internal fun NodeContent.Text.annotated(fonts: SceneFonts, sp: (Double) -> TextUnit): AnnotatedString = buildAnnotatedString {
    append(text)
    TextSpans.clamp(spans, text.length).forEach { span ->
        addStyle(
            SpanStyle(
                color = span.color?.toColor() ?: androidx.compose.ui.graphics.Color.Unspecified,
                fontSize = span.fontSize?.let(sp) ?: TextUnit.Unspecified,
                fontWeight = span.fontWeight?.let(::FontWeight),
                fontStyle = span.italic?.let { if (it) FontStyle.Italic else FontStyle.Normal },
                fontFamily = fonts.resolve(span.fontFamily),
                textDecoration = span.decoration?.let { it.toCompose() ?: androidx.compose.ui.text.style.TextDecoration.None },
            ),
            span.start,
            span.end,
        )
    }
}

/** 텍스트 노드 전체 스타일. 화면, 내보내기, 측정이 같은 값을 써야 크기와 결과 이미지가 맞는다. [sp]는 문서 단위를 sp로 바꾼다. */
internal fun NodeContent.Text.baseStyle(color: androidx.compose.ui.graphics.Color, fonts: SceneFonts, sp: (Double) -> TextUnit): TextStyle =
    TextStyle(
        color = color,
        fontSize = sp(fontSize),
        fontWeight = FontWeight(fontWeight),
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontFamily = fonts.resolve(fontFamily),
        textDecoration = decoration.toCompose(),
        lineHeight = if (lineHeight > 0.0) sp(lineHeight) else TextUnit.Unspecified,
        letterSpacing = sp(letterSpacing),
        textAlign = when (alignment) {
            TextAlignment.Start -> TextAlign.Start
            TextAlignment.Center -> TextAlign.Center
            TextAlignment.End -> TextAlign.End
        },
    )
