package com.naury.kinetica.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration as ComposeDecoration
import com.naury.kinetica.core.scene.TextDecoration
import com.naury.kinetica.core.scene.TextSpan
import com.naury.kinetica.core.scene.TextSpans

// 텍스트 입력란에 구간 스타일(굵기, 기울임, 밑줄·취소선, 색)을 보여 준다. 크기와 폰트까지 바꾸면 입력하기 불편해서 반영하지 않는다.
internal class SpanPreview(private val spans: List<TextSpan>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text.text)
            TextSpans.clamp(spans, text.length).forEach { span ->
                addStyle(
                    SpanStyle(
                        color = span.color?.let { Color(it.argb) } ?: Color.Unspecified,
                        fontWeight = span.fontWeight?.let(::FontWeight),
                        fontStyle = span.italic?.let { if (it) FontStyle.Italic else FontStyle.Normal },
                        textDecoration = when (span.decoration) {
                            TextDecoration.Underline -> ComposeDecoration.Underline
                            TextDecoration.Strikethrough -> ComposeDecoration.LineThrough
                            TextDecoration.None -> ComposeDecoration.None
                            null -> null
                        },
                    ),
                    span.start,
                    span.end,
                )
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean = other is SpanPreview && other.spans == spans

    override fun hashCode(): Int = spans.hashCode()
}
