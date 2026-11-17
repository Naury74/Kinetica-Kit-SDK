package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 마크다운 일부를 그린다. AI 답변처럼 글이 조금씩 늘어나는 스트리밍에 맞춰 만들었다.
 *
 * 아는 문법은 제목(#에서 ###까지, 더 깊은 제목은 ### 모양), 문단, 굵게, 기울임, 취소선, 인라인 코드, 링크, 순서 목록과 비순서 목록(2단계까지),
 * 인용, 수평선, 코드 블록이다. 코드 블록은 [KCodeBlock]으로 그린다. 문단 안 줄 바꿈은 그대로 줄을 바꾼다.
 * 닫히지 않은 코드 울타리는 글 끝까지 코드로 본다. [streaming]인 동안에는 마지막 블록의 닫히지 않은 강조 기호와 인라인 코드도
 * 글 끝까지 열린 것으로 보므로 생성 도중에도 모양이 깨지지 않는다. 완성된 글에서 짝이 없는 기호는 글자 그대로 보인다.
 *
 * 글이 뒤로만 늘어나면 이미 끝난 앞 블록은 다시 파싱하지 않고, 블록마다 따로 구성해 바뀐 마지막 블록만 다시 그린다.
 * 그래서 긴 답변도 `LazyColumn`의 `item` 하나에 그대로 넣을 수 있다. 앞부분이 바뀌면 처음부터 다시 파싱한다.
 * 링크는 접근성 링크로 붙어 TalkBack이 링크로 읽고 열 수 있다. 제목은 제목으로, 목록은 목록으로 읽는다.
 * 글을 고를 수 있게 하려면 호출한 쪽에서 `SelectionContainer`로 감싼다.
 *
 * ```kotlin
 * KMarkdownText(answer, streaming = generating, onLinkClick = { url -> openInBrowser(url) })
 * ```
 *
 * @param markdown 그릴 마크다운
 * @param onLinkClick 링크를 눌렀을 때 주소를 받는다. null이면 시스템이 주소를 연다.
 * @param style 본문 글자 모양. 키트 본문 모양 위에 덮어쓴다.
 * @param color 글자 색. 지정하지 않으면 [LocalKineticaContentColor]다.
 * @param codeDark 코드 블록을 어두운 색으로 그릴지. 기본값은 지금 표면이 어두운지다.
 * @param codeLineNumbers 코드 블록에 줄 번호를 둔다.
 * @param streaming 글이 아직 생성되는 중인지. 생성이 끝나면 false로 바꿔야 `O(n*m)`의 별표 같은 짝 없는 기호가 글자로 보인다.
 */
@Composable
public fun KMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    onLinkClick: ((url: String) -> Unit)? = null,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
    codeDark: Boolean = KineticaTheme.surface.isDark,
    codeLineNumbers: Boolean = false,
    streaming: Boolean = false,
) {
    val cache = remember { MarkdownCache() }
    val blocks = remember(markdown) { cache.update(markdown) }
    val ink = color.takeOrElse { LocalKineticaContentColor.current }
    val accent = KineticaTheme.colors.accent
    val body = themed(KineticaTheme.typography.body.merge(TextStyle(color = ink)).merge(style))
    val latest by rememberUpdatedState(onLinkClick)
    val hasHandler = onLinkClick != null
    val handler = remember(hasHandler) { if (hasHandler) MdLinkHandler { url -> latest?.invoke(url) } else null }
    val context = remember(body, ink, accent, handler, codeDark, codeLineNumbers) {
        MdContext(body, ink, accent, handler, MdCodeOptions(codeDark, codeLineNumbers))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
        blocks.forEachIndexed { index, block -> MdBlockView(block, context, streaming && index == blocks.lastIndex) }
    }
}

/** 코드 블록 설정 */
@Immutable
internal data class MdCodeOptions(val dark: Boolean, val lineNumbers: Boolean)

/** 블록을 그릴 때 함께 쓰는 값. 블록 구성이 건너뛰어지도록 한 번 만들어 같은 객체를 넘긴다. */
@Immutable
internal class MdContext(val body: TextStyle, val ink: Color, val accent: Color, val handler: MdLinkHandler?, val code: MdCodeOptions) {
    val inline: MdInlineStyles = MdInlineStyles(
        code = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = CODE_EM.em, background = ink.copy(alpha = CODE_FILL)),
        link = TextLinkStyles(
            style = SpanStyle(color = accent, textDecoration = TextDecoration.Underline),
            pressedStyle = SpanStyle(color = accent, background = accent.copy(alpha = LINK_PRESS)),
        ),
    )
    val muted: Color = ink.copy(alpha = MUTED)

    /** 인용 안에서 쓸 흐린 글자 값 */
    fun quoted(): MdContext = MdContext(body.copy(color = muted), muted, accent, handler, code)
}

@Composable
private fun MdBlockView(block: MdBlock, context: MdContext, streaming: Boolean) {
    when (block) {
        is MdBlock.Heading -> MdInlineText(
            block.text,
            context.body.merge(headingStyle(block.level)),
            context,
            streaming,
            Modifier.padding(top = HEADING_TOP).semantics { heading() },
        )

        is MdBlock.Paragraph -> MdInlineText(block.text, context.body, context, streaming)

        is MdBlock.Code -> KCodeBlock(
            block.code,
            Modifier.fillMaxWidth(),
            language = block.language,
            showLineNumbers = context.code.lineNumbers,
            dark = context.code.dark,
        )

        is MdBlock.Quote -> MdQuote(block, context, streaming)

        is MdBlock.Bullets -> MdBullets(block, context, streaming)

        MdBlock.Rule -> Box(
            Modifier.fillMaxWidth().padding(vertical = RULE_GAP).height(RULE).background(context.ink.copy(alpha = RULE_ALPHA)),
        )
    }
}

@Composable
private fun MdInlineText(text: String, style: TextStyle, context: MdContext, streaming: Boolean, modifier: Modifier = Modifier) {
    val annotated = remember(text, context, streaming) { buildInline(parseInline(text, streaming), context.inline, context.handler) }
    BasicText(annotated, modifier, style = style)
}

@Composable
private fun MdQuote(block: MdBlock.Quote, context: MdContext, streaming: Boolean) {
    val inner = remember(context) { context.quoted() }
    val bar = context.ink.copy(alpha = QUOTE_BAR_ALPHA)
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val width = QUOTE_BAR.toPx()
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                drawRoundRect(bar, Offset(x, 0f), Size(width, size.height), CornerRadius(width / 2))
            }
            .padding(start = QUOTE_INDENT),
        verticalArrangement = Arrangement.spacedBy(BLOCK_GAP),
    ) {
        block.blocks.forEachIndexed { index, child -> MdBlockView(child, inner, streaming && index == block.blocks.lastIndex) }
    }
}

@Composable
private fun MdBullets(block: MdBlock.Bullets, context: MdContext, streaming: Boolean) {
    val items = block.items
    Column(
        Modifier.fillMaxWidth().semantics { collectionInfo = CollectionInfo(items.size, 1) },
        verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
    ) {
        items.forEachIndexed { index, item ->
            // 번호와 글을 한 항목으로 읽도록 묶는다. 링크는 따로 누를 수 있는 노드로 남는다.
            Row(
                Modifier
                    .padding(start = NEST * item.level)
                    .semantics(mergeDescendants = true) { collectionItemInfo = CollectionItemInfo(index, 1, 0, 1) },
            ) {
                val marker = if (item.ordered) {
                    "${item.number}."
                } else if (item.level == 0) {
                    "•"
                } else {
                    "◦"
                }
                BasicText(
                    marker,
                    Modifier
                        .widthIn(min = MARKER_WIDTH)
                        .padding(end = MARKER_GAP)
                        .then(if (item.ordered) Modifier else Modifier.clearAndSetSemantics { }),
                    style = context.body.copy(color = if (item.ordered) context.ink else context.muted),
                )
                MdInlineText(item.text, context.body, context, streaming && index == items.lastIndex, Modifier.weight(1f))
            }
        }
    }
}

private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> TextStyle(fontSize = H1.sp, lineHeight = (H1 * HEADING_LINE).sp, fontWeight = FontWeight.Bold)
    2 -> TextStyle(fontSize = H2.sp, lineHeight = (H2 * HEADING_LINE).sp, fontWeight = FontWeight.Bold)
    else -> TextStyle(fontSize = H3.sp, lineHeight = (H3 * HEADING_LINE).sp, fontWeight = FontWeight.SemiBold)
}

private val BLOCK_GAP = 10.dp
private val ITEM_GAP = 4.dp
private val HEADING_TOP = 4.dp
private val RULE = 1.dp
private val RULE_GAP = 6.dp
private const val RULE_ALPHA = 0.14f
private val QUOTE_BAR = 3.dp
private val QUOTE_INDENT = 14.dp
private const val QUOTE_BAR_ALPHA = 0.25f
private val NEST = 20.dp
private val MARKER_WIDTH = 20.dp
private val MARKER_GAP = 4.dp
private const val MUTED = 0.7f
private const val CODE_EM = 0.88f
private const val CODE_FILL = 0.08f
private const val LINK_PRESS = 0.16f
private const val H1 = 22
private const val H2 = 19
private const val H3 = 17
private const val HEADING_LINE = 1.35f
