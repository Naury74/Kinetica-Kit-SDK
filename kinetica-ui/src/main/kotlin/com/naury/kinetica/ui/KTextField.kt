package com.naury.kinetica.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 입력란. 포커스를 받거나 글자가 있으면 [label]이 위로 작게 떠오른다. 포커스를 받으면 안쪽이 살짝 밝아지고
 * 강조색 테두리와 옅은 바깥 링이 부드럽게 나타난다. 커서와 선택 손잡이도 강조색이다.
 *
 * [error]가 `null`이 아니면 테두리와 라벨이 빨갛게 바뀌고 입력란 아래에 오류 문구가 나타난다. 오류가 새로 생기거나 문구가 바뀔 때
 * 입력란이 좌우로 한 번 살짝 흔들린다. '애니메이션 삭제' 접근성 설정이 켜져 있으면 흔들리지 않는다.
 * TalkBack은 입력란의 오류를 읽고, 오류 문구가 나타나면 바로 알린다.
 *
 * 포커스를 받은 동안 키보드가 올라오면 입력란과 그 아래 문구까지 보이도록 스크롤한다. 스크롤 컨테이너 안에 있어야 한다.
 *
 * @param label 입력란 이름. 비어 있을 때는 입력란 가운데에, 그 밖에는 위쪽에 작게 보인다.
 * @param error 오류 문구. `null`이면 오류가 없는 상태다.
 * @param keyboardOptions 키보드 종류와 IME 동작
 * @param visualTransformation 화면에 보이는 글자 변환. 비밀번호는 `PasswordVisualTransformation()`을 넘긴다.
 * @param keyboardActions IME 동작 키(완료, 검색 등)를 눌렀을 때 할 일
 * @param enabled `false`면 포커스와 입력을 받지 않고 흐리게 그린다.
 * @param readOnly `true`면 글자를 고칠 수 없지만 포커스, 선택, 복사는 된다.
 * @param singleLine `true`면 한 줄로 가로 스크롤하고 Enter 키로 줄을 바꾸지 않는다. 이때 [maxLines]는 무시한다.
 * @param maxLines [singleLine]이 `false`일 때 보이는 최대 줄 수. 1 미만이면 [IllegalArgumentException]을 던진다.
 * @param interactionSource 포커스, 누름 같은 상호작용을 바깥에서 관찰할 때 넘긴다. `null`이면 안에서 만든다.
 * @param placeholder 포커스를 받았는데 비어 있을 때 라벨 아래에 옅게 보이는 예시 글. `null`이면 없다.
 * @param supportingText 입력란 아래 도움말. 오류가 있으면 오류 문구가 대신 보인다.
 * @param clearable `true`면 포커스를 받고 글자가 있을 때 끝에 지우기 버튼이 나타난다. [readOnly]이거나 [enabled]가 `false`면 나오지 않는다.
 * @param leadingIcon 앞쪽 아이콘. 18~20dp 크기로 넣는다.
 * @param trailingIcon 끝쪽 아이콘이나 버튼. 지우기 버튼이 보일 때는 그 뒤에 놓인다.
 * @param minLines [singleLine]이 `false`일 때 비어 있어도 차지하는 최소 줄 수. 1 미만이거나 [maxLines]보다 크면
 *   [IllegalArgumentException]을 던진다. 여러 줄 입력은 [KTextArea]가 더 편하다.
 * @param variant 입력란 모양. 기본값 [KTextFieldVariant.Surface]는 표면 스타일로 칠한 상자다.
 * @param prefix 입력한 글자 앞에 붙는 고정 글자(예: "₩", "https://"). 라벨이 떠올라 있을 때만 보인다. 값에는 들어가지 않는다.
 * @param suffix 입력한 글자 뒤에 붙는 고정 글자(예: "kg", "@gmail.com"). [prefix]와 같은 규칙을 따른다.
 * @param maxLength 최대 글자 수. 넘는 입력은 잘라서 [onValueChange]에 넘기고, 입력란 아래 끝에 '지금 글자 수/최대'를 보여 준다.
 *   `null`이면 제한도 표시도 없다. 1 미만이면 [IllegalArgumentException]을 던진다.
 */
@Composable
public fun KTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    interactionSource: MutableInteractionSource? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
    placeholder: String? = null,
    supportingText: String? = null,
    clearable: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    minLines: Int = 1,
    variant: KTextFieldVariant = KTextFieldVariant.Surface,
    prefix: String? = null,
    suffix: String? = null,
    maxLength: Int? = null,
) {
    val config = FieldConfig(
        variant,
        FieldTexts(label, placeholder, prefix, suffix, error, supportingText),
        FieldLines(singleLine, minLines, maxLines),
        maxLength,
        FieldSlots(leadingIcon, trailingIcon),
        FieldFlags(clearable, enabled, readOnly),
    ).checked()
    val source = FieldSource.Value(value, onValueChange, keyboardActions, visualTransformation, maxLength)
    TextFieldFrame(source, config, FieldInput(keyboardOptions, interactionSource), surface, modifier)
}

/**
 * [TextFieldState]를 받는 입력란. 글자를 상태 객체가 들고 있어서, 통화 화면처럼 자주 다시 그려지는 곳에서도 입력 중인 글자나
 * 커서를 잃지 않는다. 모양, 라벨, 오류, 지우기 버튼, 글자 수 표시는 String을 받는 [KTextField]와 같고, 여기 적지 않은 파라미터도
 * 그쪽과 뜻이 같다. 비밀번호처럼 글자를 가려야 하면 String을 받는 쪽에 `PasswordVisualTransformation()`을 넘긴다.
 *
 * ```kotlin
 * val name = rememberTextFieldState()
 * KTextField(name, "이름", maxLength = 20, onKeyboardAction = { submit(name.text) })
 * ```
 *
 * @param state 입력란 글자와 선택 범위. 바깥에서 `state.edit { … }`나 `state.clearText()`로 바꿀 수 있다.
 * @param onKeyboardAction IME 동작 키(완료, 검색 등)를 눌렀을 때 할 일. `null`이면 키의 기본 동작(포커스 이동, 키보드 내리기)을 한다.
 * @param inputTransformation 사용자 입력을 거르거나 고치는 변환. [maxLength]가 있으면 이 변환 뒤에 글자 수 제한을 이어 붙인다.
 * @param outputTransformation 화면에 보이는 글자만 바꾸는 변환(예: 전화번호 하이픈). 상태 안 글자는 그대로다.
 * @param maxLength 최대 글자 수. 꽉 찬 뒤의 입력은 받지 않고, 여유가 있을 때 길게 붙여 넣으면 넘치는 끝부분을 버린다.
 *   입력란 아래 끝에 '지금 글자 수/최대'를 보여 준다. 바깥에서 [state]에 직접 넣은 글자는 자르지 않고 글자 수를 빨갛게 표시한다.
 *   `null`이면 제한도 표시도 없다. 1 미만이면 [IllegalArgumentException]을 던진다.
 */
@Composable
public fun KTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    interactionSource: MutableInteractionSource? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
    placeholder: String? = null,
    supportingText: String? = null,
    clearable: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    minLines: Int = 1,
    variant: KTextFieldVariant = KTextFieldVariant.Surface,
    prefix: String? = null,
    suffix: String? = null,
    maxLength: Int? = null,
) {
    val config = FieldConfig(
        variant,
        FieldTexts(label, placeholder, prefix, suffix, error, supportingText),
        FieldLines(singleLine, minLines, maxLines),
        maxLength,
        FieldSlots(leadingIcon, trailingIcon),
        FieldFlags(clearable, enabled, readOnly),
    ).checked()
    val source = remember(state, inputTransformation, outputTransformation, onKeyboardAction, maxLength) {
        FieldSource.State(state, inputTransformation, outputTransformation, onKeyboardAction, maxLength)
    }
    TextFieldFrame(source, config, FieldInput(keyboardOptions, interactionSource), surface, modifier)
}

/** 두 오버로드가 같이 쓰는 파라미터 검사 */
private fun FieldConfig.checked(): FieldConfig {
    require(lines.max >= 1) { "maxLines는 1 이상이어야 합니다." }
    require(lines.min in 1..(if (lines.single) 1 else lines.max)) { "minLines는 1 이상, maxLines 이하여야 합니다." }
    require(maxLength == null || maxLength >= 1) { "maxLength는 1 이상이어야 합니다." }
    return this
}

@Composable
private fun TextFieldFrame(source: FieldSource, config: FieldConfig, input: FieldInput, surface: SurfaceStyle, modifier: Modifier) {
    if (config.variant == KTextFieldVariant.Surface) {
        SurfaceTextField(source, config, input, surface, modifier)
    } else {
        VariantTextField(source, config, input, surface, modifier)
    }
}

/** 표면 스타일로 칠한 기본 입력란([KTextFieldVariant.Surface]) */
@Composable
private fun SurfaceTextField(source: FieldSource, config: FieldConfig, input: FieldInput, surface: SurfaceStyle, modifier: Modifier) {
    val texts = config.texts
    val error = texts.error
    val flags = config.flags
    val lines = config.lines
    var focused by remember { mutableStateOf(false) }
    val empty = source.text.isEmpty()
    val motion = remember { FieldMotion(!empty, error) }
    val colors = KineticaTheme.colors
    motion.accent = colors.accent
    motion.errorColor = colors.destructive
    val still = reducedMotion()
    LaunchedEffect(focused, empty) {
        motion.float.animateTo(if (focused || !empty) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(focused) { motion.levels.focusTo(focused) }
    LaunchedEffect(error) { motion.onError(error, still) }
    val parts = FieldParts(
        texts.label,
        texts.placeholder,
        config.slots,
        FieldAffix(texts.prefix, texts.suffix),
        !lines.single && lines.min > 1,
    ) {
        val show = flags.clearable && flags.enabled && !flags.readOnly && focused && !empty
        FieldClearButton(show, surface.contentColor) { source.clear() }
    }
    val basics = FieldBasics(
        enabled = flags.enabled,
        readOnly = flags.readOnly,
        lines = lines,
        textStyle = themed(TextStyle(color = surface.contentColor, fontSize = FIELD_FONT_SIZE)),
        cursorBrush = SolidColor(colors.accent),
        input = input,
    )
    Column(modifier.keepVisibleWhileFocused(focused)) {
        ProvideFieldSelection(colors.accent) {
            SourceTextField(
                source,
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = motion.shake.value * density
                        alpha = if (flags.enabled) 1f else DISABLED_ALPHA
                        // 화면 밖 버퍼를 쓰면 그림자가 요소 경계에서 잘려 모서리가 각져 보인다.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .kSurface(surface.forField(), fieldShape)
                    .fieldFocusFrame(fieldShape, motion.levels, colors.accent, colors.destructive)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { if (error != null) error(error) },
                basics,
            ) { field -> FieldDecoration(parts, surface, motion, empty, field) }
        }
        FieldSupportRow(error, texts.supporting, surface, config.maxLength?.let { source.text.length to it }, FIELD_PADDING)
    }
}

/** 입력란 장식에 들어가는 이름과 슬롯. 파라미터 수를 줄이려고 묶었다. */
internal class FieldParts(
    val label: String,
    val placeholder: String?,
    val slots: FieldSlots,
    val affix: FieldAffix,
    val multiLine: Boolean,
    val clear: @Composable () -> Unit,
)

/** 입력란 아래 오류나 도움말 문구. 둘이 바뀔 때 높이가 튀지 않게 부드럽게 늘고 준다. */
@Composable
internal fun FieldSupportText(error: String?, helper: String?, surface: SurfaceStyle) {
    val colors = KineticaTheme.colors
    val text = error ?: helper
    Box(Modifier.fillMaxWidth().animateContentSize(tween(SUPPORT_MS))) {
        if (text != null) {
            ThemedText(
                text,
                Modifier
                    .padding(start = FIELD_PADDING, top = ERROR_GAP, end = FIELD_PADDING)
                    .then(if (error != null) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
                style = TextStyle(
                    color = if (error != null) colors.destructive else surface.contentColor.copy(alpha = HELPER_ALPHA),
                    fontSize = ERROR_FONT_SIZE,
                ),
            )
        }
    }
}

@Composable
private fun FieldDecoration(parts: FieldParts, surface: SurfaceStyle, motion: FieldMotion, empty: Boolean, field: @Composable () -> Unit) {
    // 글자 크기를 키우면 라벨 자리와 입력 줄 간격도 같이 늘려야 라벨이 입력한 글자와 겹치지 않는다.
    val grow = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Row(
        Modifier.heightIn(min = KineticaTheme.design.density.height(FIELD_HEIGHT) * grow).padding(
            start = if (parts.slots.leading !=
                null
            ) {
                ICON_START
            } else {
                fieldStartPadding
            },
            end = TRAILING_END,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        parts.slots.leading?.let {
            CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor.copy(alpha = ICON_ALPHA)) { it() }
            Spacer(Modifier.width(ICON_GAP))
        }
        Box(Modifier.weight(1f).padding(end = FIELD_PADDING - TRAILING_END)) {
            FloatingLabelArea(parts, surface, motion, empty, grow, field)
        }
        parts.clear()
        parts.slots.trailing?.let { it() }
    }
}

@Composable
private fun FloatingLabelArea(
    parts: FieldParts,
    surface: SurfaceStyle,
    motion: FieldMotion,
    empty: Boolean,
    grow: Float,
    field: @Composable () -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val centering = remember { LabelCentering() }
    Box(Modifier.fillMaxWidth().onSizeChanged { centering.area = it.height }) {
        ThemedText(
            parts.label,
            Modifier.onSizeChanged { centering.label = it.height }.graphicsLayer {
                val up = motion.float.value
                transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0f)
                scaleX = 1f - (1f - LABEL_SCALE) * up
                scaleY = scaleX
                // 여러 줄 입력란은 라벨을 가운데가 아니라 첫 줄 자리에 둔다.
                val rest = if (parts.multiLine) {
                    (INPUT_TOP * grow).toPx() + LABEL_NUDGE.toPx()
                } else {
                    centering.restPx(LABEL_REST * density * grow, LABEL_NUDGE.toPx())
                }
                translationY = rest + (LABEL_FLOAT * density * grow - rest) * up
            },
            style = TextStyle(fontSize = FIELD_FONT_SIZE),
            color = {
                val idle = surface.contentColor.copy(alpha = FIELD_HINT_ALPHA)
                val focus = motion.levels.focus.value * motion.float.value
                lerp(lerp(idle, motion.accent, focus), motion.errorColor, motion.levels.error.value)
            },
            maxLines = 1,
        )
        FieldAffixes(
            parts.affix.prefix,
            parts.affix.suffix,
            surface.contentColor,
            { motion.float.value },
            Modifier.fillMaxWidth().padding(top = INPUT_TOP * grow, bottom = INPUT_BOTTOM).align(Alignment.TopStart),
        ) {
            if (empty && parts.placeholder != null) {
                ThemedText(
                    parts.placeholder,
                    Modifier.graphicsLayer { alpha = motion.levels.focus.value },
                    style = TextStyle(color = surface.contentColor.copy(alpha = PLACEHOLDER_ALPHA), fontSize = FIELD_FONT_SIZE),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            field()
        }
    }
}

/** 라벨 위치, 포커스, 오류 표시, 흔들림 애니메이션 값. */
internal class FieldMotion(filled: Boolean, initialError: String?) {
    val float = Animatable(if (filled) 1f else 0f)
    val levels = FieldLevels(focused = false, error = initialError != null)

    /** 가로로 흔들리는 거리(dp). */
    val shake = Animatable(0f)

    // 테마 색. 그리기 단계에서 읽는다.
    var accent: Color = Color.Unspecified
    var errorColor: Color = Color.Unspecified
    private var lastError: String? = initialError

    suspend fun onError(next: String?, still: Boolean) = coroutineScope {
        val changed = next != null && next != lastError
        lastError = next
        launch { levels.errorTo(next != null) }
        if (changed && !still) {
            launch {
                shake.animateTo(
                    0f,
                    keyframes {
                        durationMillis = SHAKE_MS
                        SHAKE_FIRST at SHAKE_AT_1
                        -SHAKE_FIRST at SHAKE_AT_2
                        SHAKE_SECOND at SHAKE_AT_3
                        -SHAKE_SECOND at SHAKE_AT_4
                    },
                )
            }
        }
    }
}

private val FIELD_HEIGHT = 52.dp
private val FIELD_PADDING = 16.dp
private val ICON_START = 14.dp
private val ICON_GAP = 10.dp
private val TRAILING_END = 4.dp
private val INPUT_TOP = 22.dp
private val INPUT_BOTTOM = 6.dp
private val ERROR_GAP = 6.dp
private val FIELD_FONT_SIZE = 16.sp
private val ERROR_FONT_SIZE = 13.sp

/** 라벨이 입력란 가운데에 있을 때와 떠올랐을 때의 위쪽 거리(dp). 가운데 값은 높이를 재기 전에만 쓴다. */
private const val LABEL_REST = 15f
private const val LABEL_FLOAT = 6f

/** 쉬는 라벨을 입력 영역 세로 가운데보다 이만큼 내려 둔다. 1배 글자에서 예전 위치(15dp)와 같게 하는 값이다. */
internal val LABEL_NUDGE = 1.dp
private const val LABEL_SCALE = 0.75f
private const val PLACEHOLDER_ALPHA = 0.38f
private const val HELPER_ALPHA = 0.6f
private const val ICON_ALPHA = 0.6f
private const val SUPPORT_MS = 160
private const val DISABLED_ALPHA = 0.5f
private const val SHAKE_MS = 360
private const val SHAKE_AT_1 = 60
private const val SHAKE_AT_2 = 120
private const val SHAKE_AT_3 = 180
private const val SHAKE_AT_4 = 240
private const val SHAKE_FIRST = 8f
private const val SHAKE_SECOND = 4f

/**
 * 비어 있는 입력란의 라벨(쉬는 라벨)을 입력 영역의 세로 가운데에 두기 위해 잰 높이(px).
 * 글자 크기 배율에 따라 입력 줄과 라벨 줄 높이가 따로 바뀌므로 고정 값 대신 실제로 잰 높이를 쓴다.
 */
internal class LabelCentering {
    var area by mutableIntStateOf(0)
    var label by mutableIntStateOf(0)

    /** 쉬는 라벨의 위쪽 위치(px). 아직 재지 않았으면 [fallback]을 쓴다. */
    fun restPx(fallback: Float, nudge: Float): Float = if (area > 0 && label > 0) (area - label) / 2f + nudge else fallback
}
