package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * AI 채팅의 프롬프트 입력창. 위에서부터 첨부 칩 줄, 내용에 따라 [maxLines]줄까지 높아지는 입력란, 아래 도구 줄로 이뤄진다.
 * 도구 줄 시작 쪽 [leading]에는 모델 선택 칩이나 첨부 버튼을 두고, 끝에는 보내기 버튼이 있다.
 *
 * [generating]이 true면 보내기 화살표가 돌며 사라지고 정지 사각형이 떠올라 멈춤 버튼이 되며, 누르면 [onStop]을 부른다.
 * 그동안 테두리를 따라 빛 한 줄기가 돈다. 빛은 입력창이 화면 밖에 있거나 '애니메이션 삭제' 설정이면 돌지 않는다.
 * 글이 비었으면 보내기 버튼이 흐려지고 눌리지 않는다. 생성 중에는 글이 있어도 보내지 않는다.
 * 하드웨어 키보드에서는 Enter로 보내고 Shift+Enter로 줄을 바꾼다. 보낸 뒤 입력란을 비우는 것은 호출한 쪽이 `state.clearText()`로 한다.
 *
 * ```kotlin
 * val prompt = rememberTextFieldState()
 * KPromptInput(prompt, onSend = { ask(prompt.text.toString()); prompt.clearText() }, generating = busy, onStop = ::cancel)
 * ```
 *
 * @param state 입력란 글자와 선택 범위
 * @param onStop 생성 중 멈춤 버튼을 눌렀을 때
 * @param placeholder 비어 있을 때 보이는 안내. null이면 '무엇이든 물어보세요'다. [label]이 없으면 TalkBack도 이 글을 입력란 이름으로 읽는다.
 * @param attachments 첨부 칩. 비면 칩 줄을 그리지 않는다. 칩이 많으면 가로로 스크롤한다.
 * @param onRemoveAttachment 칩 끝 X 버튼을 눌렀을 때. null이면 버튼을 그리지 않는다.
 * @param leading 도구 줄 시작 쪽 내용
 * @param maxLines 입력란이 커지는 최대 줄 수. 넘으면 입력란 안에서 스크롤한다. 1보다 작으면 [IllegalArgumentException]을 던진다.
 * @param enabled `false`면 입력과 보내기, 멈춤을 모두 받지 않는다.
 * @param imeSend `true`면 화면 키보드의 Enter 키가 보내기 키가 된다. 기본값은 줄 바꿈 키다.
 * @param label TalkBack이 읽는 입력란 이름. null이면 [placeholder]를 쓴다.
 */
@Composable
public fun KPromptInput(
    state: TextFieldState,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    generating: Boolean = false,
    onStop: () -> Unit = {},
    placeholder: String? = null,
    attachments: List<KPromptAttachment> = emptyList(),
    onRemoveAttachment: ((KPromptAttachment) -> Unit)? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    maxLines: Int = 8,
    enabled: Boolean = true,
    imeSend: Boolean = false,
    label: String? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(maxLines >= 1) { "maxLines는 1 이상이어야 합니다." }
    val send = promptSend(onSend)
    val canSend = enabled && !generating && state.text.isNotBlank()
    val source = FieldSource.State(state, onKeyboardAction = { if (canSend) send() })
    val parts = PromptParts(attachments, onRemoveAttachment, leading)
    val action = PromptAction(canSend, generating, enabled, send, onStop)
    PromptFrame(source, action, PromptText(placeholder, label, maxLines, imeSend), parts, surface, modifier)
}

/**
 * String 값을 받는 [KPromptInput]. 자주 다시 그려지는 화면에서는 입력 중인 글자를 잃지 않도록 [TextFieldState] 오버로드를 쓴다.
 *
 * @see KPromptInput 파라미터의 뜻과 실패 조건
 */
@Composable
public fun KPromptInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    generating: Boolean = false,
    onStop: () -> Unit = {},
    placeholder: String? = null,
    attachments: List<KPromptAttachment> = emptyList(),
    onRemoveAttachment: ((KPromptAttachment) -> Unit)? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    maxLines: Int = 8,
    enabled: Boolean = true,
    imeSend: Boolean = false,
    label: String? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(maxLines >= 1) { "maxLines는 1 이상이어야 합니다." }
    val send = promptSend(onSend)
    val canSend = enabled && !generating && value.isNotBlank()
    val source = FieldSource.Value(value, onValueChange, keyboardActions = KeyboardActions(onSend = { if (canSend) send() }))
    val parts = PromptParts(attachments, onRemoveAttachment, leading)
    val action = PromptAction(canSend, generating, enabled, send, onStop)
    PromptFrame(source, action, PromptText(placeholder, label, maxLines, imeSend), parts, surface, modifier)
}

private class PromptAction(val canSend: Boolean, val generating: Boolean, val enabled: Boolean, val send: () -> Unit, val stop: () -> Unit)

private class PromptText(val placeholder: String?, val label: String?, val maxLines: Int, val imeSend: Boolean)

private class PromptParts(
    val attachments: List<KPromptAttachment>,
    val onRemove: ((KPromptAttachment) -> Unit)?,
    val leading: (@Composable RowScope.() -> Unit)?,
)

@Composable
private fun promptSend(onSend: () -> Unit): () -> Unit {
    val haptic = kHapticFeedback()
    return {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        onSend()
    }
}

@Composable
private fun PromptFrame(
    source: FieldSource,
    action: PromptAction,
    text: PromptText,
    parts: PromptParts,
    surface: SurfaceStyle,
    modifier: Modifier,
) {
    val shape = roundedShapeFor(FRAME_RADIUS) { card }
    Column(
        modifier
            .kSurface(surface.forField(), shape)
            .borderSpinner(action.generating && action.enabled, shape, KineticaTheme.colors.accent, SPINNER_WIDTH),
    ) {
        if (parts.attachments.isNotEmpty()) PromptAttachments(parts, action.enabled, surface)
        PromptField(source, action, text, surface)
        Row(
            Modifier.fillMaxWidth().padding(start = TOOL_PADDING, end = TOOL_PADDING, bottom = TOOL_PADDING),
            horizontalArrangement = Arrangement.spacedBy(TOOL_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            parts.leading?.invoke(this)
            Spacer(Modifier.weight(1f))
            PromptSendButton(action, surface)
        }
    }
}

@Composable
private fun PromptAttachments(parts: PromptParts, enabled: Boolean, surface: SurfaceStyle) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = TOOL_PADDING, end = TOOL_PADDING, top = TOOL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(TOOL_GAP),
    ) {
        for (item in parts.attachments) {
            key(item.id) {
                val remove = parts.onRemove?.takeIf { enabled }
                KAttachmentChip(
                    item.name,
                    detail = item.detail,
                    status = item.status,
                    progress = item.progress,
                    onRemove = remove?.let { { it(item) } },
                    surface = surface,
                )
            }
        }
    }
}

@Composable
private fun PromptField(source: FieldSource, action: PromptAction, text: PromptText, surface: SurfaceStyle) {
    val style = themed(TextStyle(color = surface.contentColor, fontSize = 16.sp, lineHeight = 22.sp))
    val placeholder = text.placeholder ?: stringResource(R.string.kinetica_ui_ai_prompt_hint)
    val name = text.label ?: placeholder
    val basics = FieldBasics(
        enabled = action.enabled,
        readOnly = false,
        lines = FieldLines(single = false, min = 1, max = text.maxLines),
        textStyle = style,
        cursorBrush = SolidColor(KineticaTheme.colors.accent),
        input = FieldInput(if (text.imeSend) SEND_KEYBOARD else KeyboardOptions.Default, null),
    )
    val fieldModifier = Modifier
        .fillMaxWidth()
        .onPreviewKeyEvent { event ->
            val enter = event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed
            if (enter && action.canSend) action.send()
            // 보낼 수 없을 때도, 빈 글이어도 Enter로 줄을 바꾸지 않는다. 줄 바꿈은 Shift+Enter다.
            enter
        }
    SourceTextField(source, fieldModifier, basics) { inner ->
        Box(Modifier.fillMaxWidth().padding(horizontal = FIELD_PADDING_H, vertical = FIELD_PADDING_V)) {
            val hint = style.copy(color = surface.muted(PLACEHOLDER_ALPHA))
            if (name != placeholder) {
                FieldNameText(name, showing = false, hint)
                if (source.text.isEmpty()) ThemedText(placeholder, style = hint, maxLines = 1)
            } else {
                FieldNameText(name, source.text.isEmpty(), hint)
            }
            inner()
        }
    }
}

/** 보내기와 멈춤 사이를 모핑하는 버튼. 모핑 값은 그리기 단계에서만 읽는다. */
@Composable
private fun PromptSendButton(action: PromptAction, surface: SurfaceStyle) {
    val still = reducedMotion()
    val morph = animateFloatAsState(
        if (action.generating) 1f else 0f,
        if (still) snap() else spring(dampingRatio = MORPH_DAMPING, stiffness = MORPH_STIFFNESS),
        label = "prompt-send",
    )
    val active = action.enabled && (action.generating || action.canSend)
    val lit = animateFloatAsState(if (active) 1f else 0f, if (still) snap() else spring(stiffness = MORPH_STIFFNESS), label = "prompt-lit")
    val colors = KineticaTheme.colors
    val idle = surface.raisedFill(IDLE_FILL)
    val label = stringResource(if (action.generating) R.string.kinetica_ui_ai_stop else R.string.kinetica_ui_chat_send)
    Box(
        Modifier
            .size(KMetrics.MinTouch)
            .clip(CircleShape)
            .clickable(null, KIndication(CircleShape, colors.onAccent), enabled = active, role = Role.Button) {
                if (action.generating) action.stop() else action.send()
            }
            .semantics { contentDescription = label }
            .padding(BUTTON_INSET)
            .drawBehind {
                drawCircle(idle)
                drawCircle(colors.accent.copy(alpha = colors.accent.alpha * lit.value))
                val p = morph.value.coerceIn(0f, 1f)
                if (p <= 0f) return@drawBehind
                val side = size.minDimension * STOP_SIDE * (STOP_MIN + (1f - STOP_MIN) * p)
                drawRoundRect(
                    colors.onAccent.copy(alpha = p),
                    Offset((size.width - side) / 2, (size.height - side) / 2),
                    Size(side, side),
                    CornerRadius(side * STOP_CORNER),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        PromptArrow(morph, lit, surface)
    }
}

@Composable
private fun PromptArrow(morph: State<Float>, lit: State<Float>, surface: SurfaceStyle) {
    val painter = rememberVectorPainter(SocialGlyphs.ArrowUp)
    Box(
        Modifier.size(ARROW).graphicsLayer {
            val p = morph.value.coerceIn(0f, 1f)
            alpha = 1f - p
            val scale = 1f - p * (1f - STOP_MIN)
            scaleX = scale
            scaleY = scale
            rotationZ = QUARTER_TURN * p
        },
    ) {
        // 색을 그리기 단계에서 섞으려고 두 색의 화살표를 겹쳐 두고 투명도만 바꾼다.
        Image(
            painter,
            null,
            Modifier.matchParentSize().graphicsLayer { alpha = 1f - lit.value },
            colorFilter = ColorFilter.tint(surface.muted(IDLE_INK)),
        )
        Image(
            painter,
            null,
            Modifier.matchParentSize().graphicsLayer { alpha = lit.value },
            colorFilter = ColorFilter.tint(KineticaTheme.colors.onAccent),
        )
    }
}

private val SEND_KEYBOARD = KeyboardOptions(imeAction = ImeAction.Send)
private val FRAME_RADIUS = 26.dp
private val SPINNER_WIDTH = 1.5.dp
private val TOOL_PADDING = 8.dp
private val TOOL_GAP = 8.dp
private val FIELD_PADDING_H = 16.dp
private val FIELD_PADDING_V = 12.dp
private val BUTTON_INSET = 4.dp
private val ARROW = 20.dp
private const val PLACEHOLDER_ALPHA = 0.45f
private const val IDLE_FILL = 0.1f
private const val IDLE_INK = 0.4f
private const val STOP_SIDE = 0.34f
private const val STOP_MIN = 0.4f
private const val STOP_CORNER = 0.22f
private const val MORPH_DAMPING = 0.62f
private const val MORPH_STIFFNESS = 520f
private const val QUARTER_TURN = 90f
