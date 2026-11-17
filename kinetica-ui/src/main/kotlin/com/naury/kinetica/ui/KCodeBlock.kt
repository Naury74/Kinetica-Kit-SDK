package com.naury.kinetica.ui

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 코드 블록. 머리줄에 언어 이름과 복사 버튼을 두고, 본문은 고정폭 글꼴로 간단한 구문 강조를 입혀 그린다.
 * 강조하는 언어는 Kotlin, Java, JavaScript, TypeScript, JSON, Python, Shell이고 키워드, 문자열, 숫자, 주석, 타입, 함수 이름 정도를 칠한다.
 * 모르는 언어는 강조 없이 그린다. 강조 결과는 [code]나 [language]가 바뀔 때만 다시 계산하므로 스트리밍으로 코드가 늘어나도 된다.
 *
 * 줄을 바꾸지 않으면 긴 줄은 블록 안에서 가로로 스크롤한다. 코드는 RTL 화면에서도 왼쪽에서 오른쪽으로 그린다.
 * TalkBack은 블록 전체를 한 번에 '언어 코드'와 코드 내용으로 읽고, 복사는 사용자 정의 동작으로 한다.
 *
 * ```kotlin
 * KCodeBlock("val x = 1", language = "kotlin", showLineNumbers = true)
 * ```
 *
 * @param language 코드 블록 언어 이름. `kt`, `js`, `ts`, `py`, `sh` 같은 줄임말도 안다. null이거나 비면 머리줄에 '코드'라고 쓴다.
 * @param showLineNumbers 앞에 줄 번호를 둔다. [wrapLines]와 함께 쓰면 번호는 원래 줄의 첫 화면 줄에 붙는다.
 * @param wrapLines `true`면 긴 줄을 블록 폭에서 접는다. `false`면 줄을 바꾸지 않고 가로로 스크롤한다.
 * @param dark 어두운 색으로 그릴지. 기본값은 지금 표면이 어두운지다.
 * @param copyable 머리줄에 복사 버튼을 두고 TalkBack 복사 동작을 더한다.
 * @param onCopy 복사한 뒤 부른다. 클립보드에 넣는 일은 블록이 한다.
 * @param fontSize 코드 글자 크기. 줄 높이는 이 크기의 1.5배다.
 */
@Composable
public fun KCodeBlock(
    code: String,
    modifier: Modifier = Modifier,
    language: String? = null,
    showLineNumbers: Boolean = false,
    wrapLines: Boolean = false,
    dark: Boolean = KineticaTheme.surface.isDark,
    copyable: Boolean = true,
    onCopy: (() -> Unit)? = null,
    fontSize: TextUnit = DEFAULT_FONT_SIZE,
) {
    val palette = if (dark) CodePalette.Dark else CodePalette.Light
    val known = remember(language) { codeLanguageOf(language) }
    val title = known?.title ?: language?.trim()?.takeIf { it.isNotEmpty() }
    val name = if (title == null) stringResource(R.string.kinetica_ui_ai_code) else stringResource(R.string.kinetica_ui_ai_code_in, title)
    val copyLabel = stringResource(R.string.kinetica_ui_copy_action)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copy = {
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(name, code))) }
        onCopy?.invoke()
        Unit
    }
    val shape = shapeFor { field }
    Column(
        modifier
            .clip(shape)
            .background(palette.background, shape)
            .border(BORDER, palette.border, shape)
            .clearAndSetSemantics {
                contentDescription = "$name\n$code"
                if (copyable) {
                    customActions = listOf(
                        CustomAccessibilityAction(copyLabel) {
                            copy()
                            true
                        },
                    )
                }
            },
    ) {
        CodeHeader(title ?: stringResource(R.string.kinetica_ui_ai_code), code, palette, copyable, onCopy)
        CodeBody(code, known, palette, CodeLayout(showLineNumbers, wrapLines, fontSize))
    }
}

/** 본문 배치 설정 */
private class CodeLayout(val lineNumbers: Boolean, val wrap: Boolean, val fontSize: TextUnit)

@Composable
private fun CodeHeader(title: String, code: String, palette: CodePalette, copyable: Boolean, onCopy: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = HEADER_HEIGHT)
            .background(palette.header)
            .padding(start = PADDING, end = HEADER_END),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemedText(
            title,
            Modifier.weight(1f),
            style = TextStyle(color = palette.comment, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (copyable) {
            KCopyButton(code, onCopied = { onCopy?.invoke() }, size = COPY_SIZE, surface = palette.buttonSurface)
        }
    }
}

@Composable
private fun CodeBody(code: String, language: CodeLanguage?, palette: CodePalette, layout: CodeLayout) {
    val tokens = remember(code, language) { highlightCode(code, language) }
    val highlighted = remember(code, tokens, palette) { buildHighlighted(code, tokens, palette) }
    val style = remember(palette, layout.fontSize) {
        TextStyle(
            color = palette.plain,
            fontFamily = FontFamily.Monospace,
            fontSize = layout.fontSize,
            lineHeight = layout.fontSize * LINE_HEIGHT,
            textDirection = TextDirection.Ltr,
        )
    }
    var visualStarts by remember(code, layout.wrap) { mutableStateOf<IntArray?>(null) }
    // 코드는 언어와 관계없이 왼쪽에서 오른쪽으로 읽으므로 본문 배치 방향을 고정한다.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth().padding(vertical = PADDING)) {
            if (layout.lineNumbers) {
                val gutter = remember(code, visualStarts) { gutterText(code, visualStarts) }
                BasicText(
                    gutter,
                    Modifier.padding(start = GUTTER_START, end = GUTTER_END),
                    style = style.copy(color = palette.lineNumber, textAlign = TextAlign.End),
                    softWrap = false,
                )
            }
            val scroll = rememberScrollState()
            Box(
                Modifier
                    .weight(1f)
                    .then(if (layout.wrap) Modifier else Modifier.horizontalScroll(scroll))
                    .padding(start = if (layout.lineNumbers) 0.dp else PADDING, end = PADDING),
            ) {
                BasicText(
                    highlighted,
                    style = style,
                    softWrap = layout.wrap,
                    onTextLayout = if (layout.wrap && layout.lineNumbers) {
                        { result: TextLayoutResult -> visualStarts = visualLineStarts(code, result) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** 원래 줄마다 그 줄이 시작하는 화면 줄 번호. 줄을 접을 때 줄 번호를 맞추는 데 쓴다. */
private fun visualLineStarts(code: String, layout: TextLayoutResult): IntArray {
    val starts = IntArray(code.count { it == '\n' } + 1)
    var offset = 0
    for (index in starts.indices) {
        starts[index] = layout.getLineForOffset(offset.coerceAtMost(code.length))
        val next = code.indexOf('\n', offset)
        offset = if (next < 0) code.length else next + 1
    }
    return starts
}

/** 줄 번호 글. [visualStarts]가 있으면 접힌 화면 줄만큼 빈 줄을 끼워 번호를 원래 줄의 첫 화면 줄에 맞춘다. */
internal fun gutterText(code: String, visualStarts: IntArray?): String {
    val lines = code.count { it == '\n' } + 1
    return buildString {
        for (index in 0 until lines) {
            if (index > 0) {
                val gap = if (visualStarts != null && index < visualStarts.size) visualStarts[index] - visualStarts[index - 1] else 1
                repeat(gap.coerceAtLeast(1)) { append('\n') }
            }
            append(index + 1)
        }
    }
}

/** [tokens]의 종류에 맞는 색을 입힌 코드 글 */
internal fun buildHighlighted(code: String, tokens: List<CodeToken>, palette: CodePalette): AnnotatedString {
    if (tokens.isEmpty()) return AnnotatedString(code)
    val builder = AnnotatedString.Builder(code)
    for (token in tokens) builder.addStyle(palette.spanOf(token.kind), token.start, token.end)
    return builder.toAnnotatedString()
}

/** 코드 블록 바탕 쪽 색 */
@Immutable
internal class CodeSurface(val background: Color, val header: Color, val border: Color, val plain: Color, val lineNumber: Color)

/** 코드 블록 색 */
@Immutable
internal class CodePalette(private val surface: CodeSurface, private val spans: Map<CodeTokenKind, SpanStyle>) {
    val background: Color get() = surface.background
    val header: Color get() = surface.header
    val border: Color get() = surface.border
    val plain: Color get() = surface.plain
    val lineNumber: Color get() = surface.lineNumber
    val comment: Color = spanOf(CodeTokenKind.Comment).color

    val buttonSurface: SurfaceStyle = SurfaceStyle.Solid(color = header, contentColor = plain, background = background)

    fun spanOf(kind: CodeTokenKind): SpanStyle = spans.getValue(kind)

    companion object {
        val Light: CodePalette = palette(
            CodeSurface(Color(0xFFF6F8FA), Color(0xFFEEF1F4), Color(0x1F1F2328), Color(0xFF1F2328), Color(0xFF8C959F)),
            ink = listOf(0xFFCF222E, 0xFF0A3069, 0xFF0550AE, 0xFF6E7781, 0xFF953800, 0xFF8250DF, 0xFF0550AE),
        )
        val Dark: CodePalette = palette(
            CodeSurface(Color(0xFF16181D), Color(0xFF1F232A), Color(0x29FFFFFF), Color(0xFFE6EDF3), Color(0xFF6E7681)),
            ink = listOf(0xFFFF7B72, 0xFFA5D6FF, 0xFF79C0FF, 0xFF8B949E, 0xFFFFA657, 0xFFD2A8FF, 0xFF79C0FF),
        )

        /** [ink]는 키워드, 문자열, 숫자, 주석, 타입, 함수, 속성 색이다. */
        private fun palette(surface: CodeSurface, ink: List<Long>): CodePalette {
            val kinds = listOf(
                CodeTokenKind.Keyword,
                CodeTokenKind.Text,
                CodeTokenKind.Number,
                CodeTokenKind.Comment,
                CodeTokenKind.Type,
                CodeTokenKind.Function,
                CodeTokenKind.Property,
            )
            val spans = kinds.zip(ink).associate { (kind, argb) ->
                kind to SpanStyle(
                    color = Color(argb),
                    fontWeight = if (kind == CodeTokenKind.Keyword) FontWeight.SemiBold else null,
                    fontStyle = if (kind == CodeTokenKind.Comment) FontStyle.Italic else null,
                )
            }
            return CodePalette(surface, spans)
        }
    }
}

private val DEFAULT_FONT_SIZE = 13.sp
private const val LINE_HEIGHT = 1.5f
private val BORDER = 1.dp
private val PADDING = 14.dp
private val HEADER_END = 4.dp
private val HEADER_HEIGHT = 40.dp
private val COPY_SIZE = 36.dp
private val GUTTER_START = 12.dp
private val GUTTER_END = 12.dp
