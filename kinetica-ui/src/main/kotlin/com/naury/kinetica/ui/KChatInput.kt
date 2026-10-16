package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 채팅 입력줄. 시작 쪽 첨부 버튼, 내용에 따라 [maxLines]줄까지 높아지는 입력란, 끝 쪽 보내기 버튼으로 이뤄진다.
 *
 * 글자가 없고 [onVoice]가 있으면 끝 버튼은 표면 위 마이크 버튼이다. 글자를 치면 강조색 원이 가운데에서 차오르며 마이크가 돌아 사라지고
 * 위 화살표가 떠올라 보내기 버튼이 된다. [onVoice]가 없으면 글자가 없는 동안 보내기 버튼이 흐려지고 눌리지 않는다.
 * 하드웨어 키보드에서는 Enter로 보내고 Shift+Enter로 줄을 바꾼다. 보낸 뒤 입력란을 비우는 것은 호출한 쪽이 [onValueChange]로 한다.
 * '애니메이션 삭제' 설정에서는 버튼이 바로 바뀐다.
 *
 * 통화 화면처럼 자주 다시 그려지는 곳에서는 [TextFieldState]를 받는 오버로드를 쓴다. String 방식은 값이 늦게 돌아오면
 * 입력 중인 글자를 잃을 수 있다.
 *
 * ```kotlin
 * KChatInput(draft, { draft = it }, onSend = { send(draft); draft = "" }, onAttach = ::pickFile, onVoice = ::record)
 * ```
 *
 * @param placeholder 비어 있을 때 보이는 안내. null이면 '메시지'다.
 * @param maxLines 입력란이 커지는 최대 줄 수. 넘으면 입력란 안에서 스크롤한다. 1보다 작으면 [IllegalArgumentException]을 던진다.
 * @param maxLength 최대 글자 수. 넘는 입력은 잘라서 [onValueChange]에 넘긴다. `null`이면 제한이 없다. 1 미만이면 [IllegalArgumentException]을 던진다.
 * @param showCounter `true`이고 [maxLength]가 있으면 입력란 안 아래 끝에 '지금 글자 수/최대'를 작게 보여 준다.
 * @param imeSend `true`면 화면 키보드의 Enter 키가 보내기 키(`ImeAction.Send`)가 되어 누르면 보낸다. `false`면 줄 바꿈 키다.
 *   하드웨어 키보드의 Enter는 이 값과 관계없이 보낸다.
 */
@Composable
public fun KChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    onAttach: (() -> Unit)? = null,
    onVoice: (() -> Unit)? = null,
    placeholder: String? = null,
    maxLines: Int = 6,
    enabled: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
    maxLength: Int? = null,
    showCounter: Boolean = false,
    imeSend: Boolean = true,
) {
    val options = ChatOptions(onAttach, onVoice, placeholder, enabled, ChatTyping(maxLines, maxLength, showCounter, imeSend))
    val send = chatSend(onSend)
    val canSend = enabled && value.isNotBlank()
    val source = FieldSource.Value(
        value,
        onValueChange,
        keyboardActions = KeyboardActions(onSend = { if (canSend) send() }),
        maxLength = maxLength,
    )
    ChatInputRow(source, canSend, send, options, surface, modifier)
}

/**
 * [TextFieldState]를 받는 채팅 입력줄. 글자를 상태 객체가 들고 있어서 통화 화면처럼 자주 다시 그려지는 곳에서도 키보드 입력을 잃지 않는다.
 * 모양과 동작은 String을 받는 [KChatInput]과 같다. 보낸 뒤에는 호출한 쪽이 `state.clearText()`로 비운다.
 *
 * ```kotlin
 * val draft = rememberTextFieldState()
 * KChatInput(draft, onSend = { send(draft.text.toString()); draft.clearText() }, maxLength = 500, showCounter = true)
 * ```
 *
 * @param state 입력란 글자와 선택 범위
 * @param maxLength 최대 글자 수. 꽉 찬 뒤의 입력은 받지 않고, 여유가 있을 때 길게 붙여 넣으면 넘치는 끝부분을 버린다.
 *   바깥에서 [state]에 직접 넣은 글자는 자르지 않는다. `null`이면 제한이 없다. 1 미만이면 [IllegalArgumentException]을 던진다.
 * @param showCounter `true`이고 [maxLength]가 있으면 입력란 안 아래 끝에 '지금 글자 수/최대'를 작게 보여 준다.
 * @param imeSend `true`면 화면 키보드의 Enter 키가 보내기 키(`ImeAction.Send`)가 되어 누르면 보낸다. `false`면 줄 바꿈 키다.
 *   하드웨어 키보드의 Enter는 이 값과 관계없이 보낸다.
 */
@Composable
public fun KChatInput(
    state: TextFieldState,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    onAttach: (() -> Unit)? = null,
    onVoice: (() -> Unit)? = null,
    placeholder: String? = null,
    maxLines: Int = 6,
    enabled: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
    maxLength: Int? = null,
    showCounter: Boolean = false,
    imeSend: Boolean = true,
) {
    val options = ChatOptions(onAttach, onVoice, placeholder, enabled, ChatTyping(maxLines, maxLength, showCounter, imeSend))
    val send = chatSend(onSend)
    val canSend = enabled && state.text.isNotBlank()
    val source = FieldSource.State(
        state,
        onKeyboardAction = { if (canSend) send() },
        maxLength = maxLength,
    )
    ChatInputRow(source, canSend, send, options, surface, modifier)
}

/** 두 오버로드가 같이 쓰는 설정 */
private class ChatOptions(
    val onAttach: (() -> Unit)?,
    val onVoice: (() -> Unit)?,
    val placeholder: String?,
    val enabled: Boolean,
    val typing: ChatTyping,
)

/** 입력란 줄 수, 글자 수, 키보드 설정 */
private class ChatTyping(val maxLines: Int, val maxLength: Int?, val showCounter: Boolean, val imeSend: Boolean) {
    init {
        require(maxLines >= 1) { "maxLines는 1 이상이어야 합니다." }
        require(maxLength == null || maxLength >= 1) { "maxLength는 1 이상이어야 합니다." }
    }
}

@Composable
private fun chatSend(onSend: () -> Unit): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        onSend()
    }
}

@Composable
private fun ChatInputRow(
    source: FieldSource,
    canSend: Boolean,
    send: () -> Unit,
    options: ChatOptions,
    surface: SurfaceStyle,
    modifier: Modifier,
) {
    val enabled = options.enabled
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        if (options.onAttach != null) {
            KIconButton(
                SocialGlyphs.Plus,
                stringResource(R.string.kinetica_ui_chat_attach),
                options.onAttach,
                enabled = enabled,
                surface = surface,
            )
        }
        ChatField(
            source,
            options,
            surface,
            Modifier.weight(1f).onPreviewKeyEvent { event ->
                val enter = event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed
                if (enter && canSend) send()
                enter && canSend
            },
        )
        val voice = if (enabled) options.onVoice else null
        SendButton(canSend, voice, surface) { if (canSend) send() else voice?.invoke() }
    }
}

@Composable
private fun ChatField(source: FieldSource, options: ChatOptions, surface: SurfaceStyle, modifier: Modifier) {
    val ink = surface.contentColor
    val style = themed(TextStyle(color = ink, fontSize = 16.sp, lineHeight = 22.sp))
    val placeholder = options.placeholder ?: stringResource(R.string.kinetica_ui_chat_hint)
    val typing = options.typing
    val counter = typing.maxLength?.takeIf { typing.showCounter }
    val basics = FieldBasics(
        enabled = options.enabled,
        readOnly = false,
        lines = FieldLines(single = false, min = 1, max = typing.maxLines),
        textStyle = style,
        cursorBrush = SolidColor(KineticaTheme.colors.accent),
        input = FieldInput(if (typing.imeSend) SEND_KEYBOARD else KeyboardOptions.Default, null),
    )
    SourceTextField(
        source,
        modifier
            .heightIn(min = KMetrics.MinTouch)
            .kSurface(surface.forField(), FIELD_SHAPE),
        basics,
    ) { inner ->
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f).padding(vertical = 11.dp), contentAlignment = Alignment.CenterStart) {
                if (source.text.isEmpty()) {
                    ThemedText(placeholder, style = style.copy(color = ink.copy(alpha = PLACEHOLDER_ALPHA)), maxLines = 1)
                }
                inner()
            }
            if (counter != null) ChatCounter(source.text.length, counter, ink)
        }
    }
}

/** 입력란 안 아래 끝의 글자 수. 최대를 넘으면 빨갛게 그린다. */
@Composable
private fun ChatCounter(count: Int, max: Int, ink: Color) {
    val label = stringResource(R.string.kinetica_ui_char_count, count, max)
    ThemedText(
        "$count/$max",
        Modifier.padding(start = 8.dp, bottom = 13.dp).semantics { contentDescription = label },
        style = TextStyle(
            color = if (count > max) KineticaTheme.colors.destructive else ink.copy(alpha = COUNTER_ALPHA),
            fontSize = 12.sp,
            fontFeatureSettings = "tnum",
        ),
        maxLines = 1,
    )
}

/**
 * 마이크와 보내기 사이를 모핑하는 버튼. [voice]가 null이면 마이크 없이 흐린 보내기 버튼에서 시작한다.
 * 모핑 값은 그리기 단계에서만 읽는다.
 */
@Composable
private fun SendButton(canSend: Boolean, voice: (() -> Unit)?, surface: SurfaceStyle, onClick: () -> Unit) {
    val still = reducedMotion()
    val progress = animateFloatAsState(
        if (canSend) 1f else 0f,
        if (still) snap() else spring(dampingRatio = SEND_DAMPING, stiffness = SEND_STIFFNESS),
        label = "chat-send",
    )
    val hasVoice = voice != null
    val colors = KineticaTheme.colors
    val label = stringResource(if (canSend || !hasVoice) R.string.kinetica_ui_chat_send else R.string.kinetica_ui_chat_voice)
    Box(
        Modifier
            .size(KMetrics.IconButton)
            .then(if (hasVoice) Modifier.kSurface(surface, CircleShape, intensity = 1f) else Modifier)
            .drawBehind {
                val p = progress.value.coerceIn(0f, 1f)
                val radius = size.minDimension / 2 * (if (hasVoice) p else 1f)
                val alpha = if (hasVoice) 1f else DISABLED_SEND + (1f - DISABLED_SEND) * p
                if (radius > 0f) drawCircle(colors.accent.copy(alpha = alpha), radius)
            }
            .clip(CircleShape)
            .clickable(
                null,
                KIndication(CircleShape, colors.onAccent),
                enabled = canSend || hasVoice,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (hasVoice) MorphGlyphLayer(SocialGlyphs.Mic, surface.contentColor, progress, appearing = false)
        MorphGlyphLayer(SocialGlyphs.ArrowUp, colors.onAccent, progress, appearing = true, alwaysVisible = !hasVoice)
    }
}

/** 모핑하는 버튼 안 아이콘 한 겹. [appearing]이면 진행할수록 아래에서 떠오르고, 아니면 돌며 사라진다. */
@Composable
private fun MorphGlyphLayer(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    progress: State<Float>,
    appearing: Boolean,
    alwaysVisible: Boolean = false,
) {
    Image(
        rememberVectorPainter(icon),
        null,
        Modifier
            .size(KMetrics.IconButtonIcon)
            .graphicsLayer {
                val p = progress.value.coerceIn(0f, 1f)
                val shown = if (alwaysVisible) {
                    1f
                } else if (appearing) {
                    p
                } else {
                    1f - p
                }
                alpha = shown
                val scale = MORPH_MIN_SCALE + (1f - MORPH_MIN_SCALE) * shown
                scaleX = scale
                scaleY = scale
                if (appearing && !alwaysVisible) translationY = (1f - p) * size.height * RISE
                if (!appearing) rotationZ = -QUARTER_TURN * p
            },
        colorFilter = ColorFilter.tint(tint),
    )
}

private val FIELD_SHAPE = RoundedCornerShape(22.dp)
private const val PLACEHOLDER_ALPHA = 0.45f
private const val COUNTER_ALPHA = 0.55f
private val SEND_KEYBOARD = KeyboardOptions(imeAction = ImeAction.Send)
private const val DISABLED_SEND = 0.35f
private const val SEND_DAMPING = 0.62f
private const val SEND_STIFFNESS = 520f
private const val MORPH_MIN_SCALE = 0.4f
private const val RISE = 0.6f
private const val QUARTER_TURN = 90f
