package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * [KMentionInput]의 글과 멘션. [rememberKMentionInputState]로 만든다. 글을 고칠 때 멘션 위치도 함께 옮기므로 글은 이 상태를 통해서만 바꾼다.
 */
@Stable
public class KMentionInputState(initialText: String = "", initialMentions: List<KMention> = emptyList()) {
    init {
        require(initialMentions.all { it.start >= 0 && it.end <= initialText.length && it.start < it.end }) {
            "initialMentions의 위치가 initialText 범위를 벗어납니다."
        }
    }

    /** 입력란 값. 글과 커서 위치다. */
    public var value: TextFieldValue by mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
        private set

    /** 지금 글 */
    public val text: String get() = value.text

    /** 글에 들어간 멘션. 앞에서부터의 순서다. */
    public var mentions: List<KMention> by mutableStateOf(initialMentions.sortedBy { it.start })
        private set

    /** 글과 멘션을 모두 지운다. 보낸 뒤에 부른다. */
    public fun clear() {
        value = TextFieldValue()
        mentions = emptyList()
    }

    /** Esc로 닫은 제안 팝업의 트리거 위치. 같은 자리에서 다시 열지 않는다. */
    internal var dismissedAt by mutableIntStateOf(-1)

    internal fun edit(new: TextFieldValue) = apply(applyMentionEdit(value, mentions, new))

    internal fun pick(query: MentionQuery, candidate: KMentionCandidate) = apply(insertMention(value, mentions, query, candidate))

    private fun apply(edit: MentionEdit) {
        value = edit.value
        mentions = edit.mentions
    }

    internal companion object {
        /** 글, 커서, 멘션을 저장한다. 멘션은 필드마다 나눠 담는다. */
        val Saver: Saver<KMentionInputState, ArrayList<Any>> = Saver(
            save = { state ->
                arrayListOf(
                    state.text,
                    state.value.selection.start,
                    state.value.selection.end,
                    ArrayList(state.mentions.flatMap { listOf(it.id, it.label, it.trigger.toString(), it.start, it.end) }),
                )
            },
            restore = { saved ->
                val text = saved[0] as String
                val fields = saved[3] as List<*>
                val mentions = fields.chunked(MENTION_FIELDS).map {
                    KMention(
                        it[F_ID] as String,
                        it[F_LABEL] as String,
                        (it[F_TRIGGER] as String).first(),
                        it[F_START] as Int,
                        it[F_END] as Int,
                    )
                }
                KMentionInputState(text, mentions).apply {
                    value = TextFieldValue(text, TextRange(saved[1] as Int, saved[2] as Int))
                }
            },
        )
    }
}

private const val MENTION_FIELDS = 5
private const val F_ID = 0
private const val F_LABEL = 1
private const val F_TRIGGER = 2
private const val F_START = 3
private const val F_END = 4

/** [KMentionInputState]를 만든다. 회전과 프로세스 재시작 뒤에도 글과 멘션이 남는다. */
@Composable
public fun rememberKMentionInputState(initialText: String = ""): KMentionInputState =
    rememberSaveable(saver = KMentionInputState.Saver) { KMentionInputState(initialText) }

/**
 * 멘션을 넣을 수 있는 글 입력란. [triggers]의 문자(기본 @)를 글 맨 앞이나 공백 뒤에 치면 그 뒤 글로 [suggestions]를 불러 커서 근처에
 * 제안 팝업을 띄운다. 후보를 누르거나 하드웨어 키보드에서 위아래 방향키로 고르고 Enter나 Tab을 누르면 그 자리에 `@이름`을 강조색 토큰으로
 * 넣고 뒤에 공백을 붙인다. Esc는 팝업을 닫는다. 토큰을 지우기 키로 건드리면 토큰 전체가 한 번에 지워지고, 토큰 안에 글을 넣으면
 * 토큰은 평범한 글이 된다. 멘션 목록이 바뀔 때마다 [onMentionsChange]를 부른다.
 *
 * 해시태그처럼 다른 문자를 함께 쓰려면 `triggers = setOf('@', '#')`로 주고 [suggestions]에서 트리거마다 다른 후보를 돌려준다.
 * 팝업은 입력란의 키보드 초점을 가져가지 않는다. [KineticaScaffold] 안이면 오버레이 레이어에, 밖이면 팝업 창에 그린다.
 *
 * ```kotlin
 * val state = rememberKMentionInputState()
 * KMentionInput(state, suggestions = { _, q -> people.filter { it.label.contains(q) } }, placeholder = "댓글 남기기")
 * ```
 *
 * @param suggestions 트리거 문자와 그 뒤 글(빈 글일 수 있다)로 후보를 돌려준다. 입력이 바뀔 때마다 부르므로 가볍게 만든다.
 * @param modifier 입력란 자체에 붙는다. `testTag`나 `focusRequester`를 여기에 준다.
 * @param triggers 제안을 여는 문자. 비어 있거나 공백 문자가 있으면 [IllegalArgumentException]을 던진다.
 * @param placeholder 비어 있을 때의 안내 글이자 TalkBack이 읽는 입력란 이름. 비어 있으면 이름은 '메시지'다.
 * @param maxLines 늘어날 수 있는 최대 줄 수. 1 이상이어야 한다.
 * @param maxSuggestions 팝업에 보일 후보 수. 1 이상이어야 한다.
 */
@Composable
public fun KMentionInput(
    state: KMentionInputState,
    suggestions: (trigger: Char, query: String) -> List<KMentionCandidate>,
    modifier: Modifier = Modifier,
    triggers: Set<Char> = DefaultTriggers,
    placeholder: String = "",
    onMentionsChange: (List<KMention>) -> Unit = {},
    maxLines: Int = 5,
    maxSuggestions: Int = 6,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(triggers.isNotEmpty() && triggers.none { it.isWhitespace() }) { "triggers는 비어 있지 않고 공백 문자가 없어야 합니다." }
    require(maxLines >= 1) { "maxLines는 1 이상이어야 합니다." }
    require(maxSuggestions >= 1) { "maxSuggestions는 1 이상이어야 합니다." }
    var focused by remember { mutableStateOf(false) }
    val value = state.value
    val query = remember(value, state.mentions, triggers) {
        if (value.selection.collapsed) findMentionQuery(value.text, value.selection.start, triggers, state.mentions) else null
    }
    val currentSuggestions by rememberUpdatedState(suggestions)
    val candidates = remember(query) { query?.let { currentSuggestions(it.trigger, it.query).take(maxSuggestions) }.orEmpty() }
    val popup = remember { MentionPopupModel() }
    popup.sync(candidates)
    val open = focused && query != null && candidates.isNotEmpty() && state.dismissedAt != query.start
    val notify by rememberUpdatedState(onMentionsChange)
    val change: (() -> Unit) -> Unit = { edit ->
        val before = state.mentions
        edit()
        if (state.mentions != before) notify(state.mentions)
    }
    val pick: (KMentionCandidate) -> Unit = { candidate -> query?.let { q -> change { state.pick(q, candidate) } } }
    val session = MentionSession(query, candidates, popup, open, pick) { state.dismissedAt = query?.start ?: -1 }
    MentionField(
        state,
        session,
        MentionFieldLook(placeholder, maxLines, keyboardOptions, surface, triggers.first()),
        modifier.onFocusChanged { focused = it.isFocused },
    ) { new -> change { state.edit(new) } }
}

/** 지금 입력 중인 멘션과 제안 팝업 상태. */
private class MentionSession(
    val query: MentionQuery?,
    val candidates: List<KMentionCandidate>,
    val popup: MentionPopupModel,
    val open: Boolean,
    val pick: (KMentionCandidate) -> Unit,
    val dismiss: () -> Unit,
)

private class MentionFieldLook(
    val placeholder: String,
    val maxLines: Int,
    val keyboardOptions: KeyboardOptions,
    val surface: SurfaceStyle,
    val defaultTrigger: Char,
)

@Composable
private fun MentionField(
    state: KMentionInputState,
    session: MentionSession,
    look: MentionFieldLook,
    modifier: Modifier,
    onEdit: (TextFieldValue) -> Unit,
) {
    val layout = remember { TextLayoutHolder() }
    val accent = KineticaTheme.colors.accent
    val transformation = remember(state.mentions, accent) {
        MentionTransformation(
            state.mentions,
            SpanStyle(color = accent, fontWeight = FontWeight.SemiBold, background = accent.copy(alpha = TOKEN_FILL)),
        )
    }
    val surface = look.surface
    val name = look.placeholder.ifEmpty { stringResource(R.string.kinetica_ui_mention_field) }
    val value = state.value
    ProvideFieldSelection(accent) {
        BasicTextField(
            value,
            onEdit,
            modifier
                .fillMaxWidth()
                .heightIn(min = FIELD_MIN_HEIGHT)
                .onPreviewKeyEvent { event ->
                    session.open &&
                        mentionKey(event, session.popup, session.candidates.size, { session.pick(session.candidates[it]) }, session.dismiss)
                },
            textStyle = themed(TextStyle(color = surface.contentColor, fontSize = FONT_SIZE, lineHeight = LINE_HEIGHT)),
            cursorBrush = SolidColor(accent),
            keyboardOptions = look.keyboardOptions,
            maxLines = look.maxLines,
            visualTransformation = transformation,
            onTextLayout = { layout.result = it },
            decorationBox = { inner ->
                Box(Modifier.kSurface(surface.forField(), shapeFor { field }).padding(horizontal = PADDING_H, vertical = PADDING_V)) {
                    Box {
                        val hint = TextStyle(color = surface.muted(HINT_ALPHA), fontSize = FONT_SIZE)
                        FieldNameText(name, value.text.isEmpty() && look.placeholder.isNotEmpty(), hint)
                        inner()
                        MentionPopupAnchor(layout, session.query?.start ?: 0) {
                            val content = MentionPopupContent(session.popup, session.query?.trigger ?: look.defaultTrigger, surface)
                            MentionPopup(session.open, content, session.pick, session.dismiss)
                        }
                    }
                }
            },
        )
    }
}

/** 마지막 글 배치 결과. 배치 단계에서만 읽어 커서 위치에 팝업 기준점을 놓는다. */
@Stable
internal class TextLayoutHolder {
    var result: TextLayoutResult? by mutableStateOf(null)
}

/** 멘션 자리에 강조색 글과 옅은 바탕을 입힌다. 글자 수는 바꾸지 않는다. */
private data class MentionTransformation(val mentions: List<KMention>, val style: SpanStyle) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (mentions.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val styled = buildAnnotatedString {
            append(text)
            mentions.forEach { if (it.end <= text.length) addStyle(style, it.start, it.end) }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

private val DefaultTriggers = setOf('@')
private val FIELD_MIN_HEIGHT = 48.dp
private val PADDING_H = 16.dp
private val PADDING_V = 13.dp
private val FONT_SIZE = 16.sp
private val LINE_HEIGHT = 22.sp
private const val HINT_ALPHA = 0.45f
private const val TOKEN_FILL = 0.14f
