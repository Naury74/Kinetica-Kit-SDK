package com.naury.kinetica.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.BoundProperty
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.TextDecoration
import com.naury.kinetica.core.scene.TextSpan
import com.naury.kinetica.core.scene.TextSpans

/**
 * 글자 노드의 내용과 리치 텍스트를 고치는 창. 고른 부분(고른 곳이 없으면 전체)에 굵게·기울임·밑줄·취소선·크기·색·글꼴을 준다.
 * 입력란에 모양이 그대로 보이며, 글자를 고치면 구간이 따라 움직인다. 확인을 누르면 실행 취소 한 단계로 적용한다.
 */
@Composable
internal fun TextEditDialog(state: EditorState, edits: PropertyEdits, id: NodeId, onDismiss: () -> Unit) {
    val node = state.document.node(id)
    val content = node?.content as? NodeContent.Text ?: return onDismiss()
    // 내용이 데이터에 묶여 있으면 화면에는 앱 데이터가 보여 여기서 고친 글자가 안 보인다. 그 사실을 알리고 묶음을 풀 수 있게 한다.
    val boundKey = node.bindings[BoundProperty.Text]
    val draft = remember(id) { RichTextDraft(content) }
    val focus = remember { FocusRequester() }
    fun confirm(unbind: Boolean) {
        if (edits.setText(id, draft.value.text, unbind, draft.spans)) onDismiss() else draft.invalid = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.kinetica_edit_text)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StyleToolbar(draft, content, node.style.fill)
                OutlinedTextField(
                    value = draft.value,
                    onValueChange = draft::change,
                    isError = draft.invalid,
                    visualTransformation = SpanPreview(draft.spans),
                    supportingText = when {
                        draft.invalid -> {
                            { Text(stringResource(R.string.kinetica_invalid_value)) }
                        }

                        boundKey != null -> {
                            { Text(stringResource(R.string.kinetica_text_bound, boundKey)) }
                        }

                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).focusRequester(focus).testTag(TEXT_EDIT_TAG),
                )
            }
        },
        confirmButton = { TextButton(onClick = { confirm(unbind = false) }) { Text(stringResource(R.string.kinetica_ok)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.kinetica_cancel)) }
                if (boundKey !=
                    null
                ) {
                    TextButton(onClick = { confirm(unbind = true) }) { Text(stringResource(R.string.kinetica_unbind_and_save)) }
                }
            }
        },
    )
    LaunchedEffect(id) { focus.requestFocus() }
}

/** 창 안에서 고치는 중인 글자·구간과 모양을 줄 범위. */
private class RichTextDraft(content: NodeContent.Text) {
    var value by mutableStateOf(TextFieldValue(content.text, TextRange(content.text.length)))
    var spans by mutableStateOf(content.spans)
    var invalid by mutableStateOf(false)

    // 크기·색 입력칸을 누르면 글자 입력칸의 고른 부분이 풀리므로, 마지막으로 고른 부분을 기억해 모양을 준다. 없으면 전체에 준다.
    private var selected by mutableStateOf(TextRange.Zero)

    val range: IntRange
        get() = selected.let { if (it.collapsed) 0 until value.text.length else it.min until it.max.coerceAtMost(value.text.length) }

    fun change(next: TextFieldValue) {
        spans = TextSpans.remap(spans, value.text, next.text)
        // 글자를 고치면 고른 부분을 잊고, 고른 부분이 있을 때만 새로 기억한다. 다른 입력칸을 눌러 풀린 선택은 무시한다.
        if (next.text != value.text) {
            selected = TextRange.Zero
        } else if (!next.selection.collapsed) {
            selected = next.selection
        }
        value = next
        invalid = false
    }

    fun styleAt(): TextSpan = TextSpans.styleAt(spans, range.first)

    fun apply(style: TextSpan) {
        spans = TextSpans.apply(spans, range.first, range.last + 1, style)
    }
}

@Composable
private fun StyleToolbar(draft: RichTextDraft, content: NodeContent.Text, fill: ArgbColor?) {
    val at = draft.styleAt()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val bold = (at.fontWeight ?: content.fontWeight) >= BOLD
        val italic = at.italic ?: content.italic
        val decoration = at.decoration ?: content.decoration
        StyleChip(R.string.kinetica_text_bold, bold) { draft.apply(TextSpan(0, 0, fontWeight = if (bold) REGULAR else BOLD)) }
        StyleChip(R.string.kinetica_text_italic, italic) { draft.apply(TextSpan(0, 0, italic = !italic)) }
        DecorationChip(R.string.kinetica_text_underline, TextDecoration.Underline, decoration, draft)
        DecorationChip(R.string.kinetica_text_strike, TextDecoration.Strikethrough, decoration, draft)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FONT_CHOICES.forEach { (family, label) ->
            StyleChip(label, (at.fontFamily ?: content.fontFamily) == family) {
                draft.apply(
                    TextSpan(
                        0,
                        0,
                        fontFamily =
                            family ?: DEFAULT_FAMILY,
                    ),
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpanField(R.string.kinetica_field_font_size, formatNumber(at.fontSize ?: content.fontSize), KeyboardType.Decimal) { text ->
            parseNumber(text)?.takeIf { it > 0.0 }?.let { draft.apply(TextSpan(0, 0, fontSize = it)) } != null
        }
        SpanField(R.string.kinetica_field_color, (at.color ?: fill)?.toHex().orEmpty(), KeyboardType.Ascii) { text ->
            parseColor(text)?.let { draft.apply(TextSpan(0, 0, color = it)) } != null
        }
    }
}

@Composable
private fun DecorationChip(label: Int, decoration: TextDecoration, current: TextDecoration, draft: RichTextDraft) {
    StyleChip(label, current == decoration) {
        draft.apply(
            TextSpan(
                0,
                0,
                decoration = if (current ==
                    decoration
                ) {
                    TextDecoration.None
                } else {
                    decoration
                },
            ),
        )
    }
}

@Composable
private fun StyleChip(label: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(label)) })
}

@Composable
private fun SpanField(label: Int, current: String, keyboard: KeyboardType, onCommit: (String) -> Boolean) {
    CommitTextField(
        current = current,
        key = label.toString() + current,
        label = stringResource(label),
        keyboardType = keyboard,
        enabled = true,
        modifier = Modifier.width(SPAN_FIELD_WIDTH),
        onCommit = onCommit,
    )
}

/** 테스트에서 글자 편집 입력칸을 찾을 때 쓰는 tag. */
internal const val TEXT_EDIT_TAG = "kinetica-text-edit"

/** 글꼴 선택. `null`은 노드 글꼴을 따른다(구간에는 기본 글꼴 이름을 준다). */
internal val FONT_CHOICES = listOf(
    null to R.string.kinetica_font_default,
    "sans" to R.string.kinetica_font_sans,
    "serif" to R.string.kinetica_font_serif,
    "monospace" to R.string.kinetica_font_mono,
    "cursive" to R.string.kinetica_font_cursive,
)

private const val DEFAULT_FAMILY = "sans"
private const val BOLD = 700
private const val REGULAR = 400
private val SPAN_FIELD_WIDTH = 140.dp
