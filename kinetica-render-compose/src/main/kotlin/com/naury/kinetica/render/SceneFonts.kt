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
 * 글꼴 이름을 Compose 글꼴로 바꾼다. 문서에는 이름만 저장하므로, 호스트가 앱의 글꼴(번들·다운로드)을 이름으로 알려 준다.
 * `null`을 돌려주면 `sans`·`serif`·`monospace`·`cursive`는 시스템 글꼴로, 그 밖에는 기본 글꼴로 그린다.
 */
public fun interface SceneFonts {
    public fun family(name: String): FontFamily?

    public companion object {
        public val Default: SceneFonts = SceneFonts { null }

        /** 이름 → 글꼴 표로 만든다. */
        public fun of(families: Map<String, FontFamily>): SceneFonts = SceneFonts { families[it] }
    }
}

/** 화면의 모든 Kinetica 렌더러가 쓰는 글꼴. 앱 맨 위에서 `CompositionLocalProvider`로 한 번 준다. */
public val LocalSceneFonts: androidx.compose.runtime.ProvidableCompositionLocal<SceneFonts> =
    staticCompositionLocalOf { SceneFonts.Default }

/** 시스템 글꼴 이름. 편집기 글꼴 목록에 쓴다. */
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

/** 글자와 구간 모양. 글자 길이를 넘는 구간은 잘라 쓴다(데이터로 글자가 바뀐 경우). [sp]는 문서 단위를 글자 단위로 바꾼다. */
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

/** 글자 노드 전체 모양. 화면·내보내기·측정이 같은 값을 써야 크기와 그림이 맞는다. [sp]는 문서 단위를 글자 단위로 바꾼다. */
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
