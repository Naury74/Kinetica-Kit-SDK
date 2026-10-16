package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos

/**
 * 인증번호나 PIN을 한 칸에 한 자리씩 받는 입력란. 칸마다 글래스 상자를 그리고, 입력할 칸에는 커서가 깜빡이며,
 * 숫자를 넣으면 그 칸에 숫자가 톡 튀어 들어온다. 숫자 키보드를 띄우고 숫자만 받는다.
 *
 * 여러 자리를 한 번에 붙여 넣거나 SMS 인증번호 자동 채우기로 들어온 값은 숫자만 골라 앞에서부터 [length]자리까지 채운다.
 * 지우기 키는 마지막 자리를 지운다. TalkBack은 "6자리 중 3자리 입력됨"처럼 진행 상황을 읽는다.
 *
 * [error]가 `true`로 바뀌면 칸 테두리가 빨갛게 바뀌고 입력란이 좌우로 흔들린다. '애니메이션 삭제' 접근성 설정이 켜져 있으면
 * 흔들림과 숫자가 튀는 애니메이션, 커서 깜빡임을 멈춘다.
 *
 * ```kotlin
 * var code by remember { mutableStateOf("") }
 * KCodeInput(code, { code = it }, onComplete = { verify(it) })
 * ```
 *
 * @param value 지금까지 입력한 숫자. 숫자가 아닌 글자나 [length]를 넘는 부분은 그리지 않는다.
 * @param onValueChange 숫자만 남기고 [length]자리 이하로 자른 새 값을 받는다.
 * @param length 칸 수(1..12). 범위를 벗어나면 [IllegalArgumentException]을 던진다.
 * @param mask `true`면 숫자 대신 점을 그리고 비밀번호용 숫자 키보드를 띄운다. PIN에 쓴다.
 * @param error 입력한 코드가 틀렸는지
 * @param onComplete 마지막 칸까지 채워지는 순간 완성된 코드로 한 번 호출된다.
 * @param contentDescription TalkBack이 읽는 입력란 이름
 * @param imeAction 키보드 동작 키의 종류. 코드 다음에 입력란이 더 있으면 [ImeAction.Next]로 둔다.
 * @param keyboardActions 동작 키를 눌렀을 때 할 일. [imeAction]에 맞는 콜백이 없으면 Next는 다음 입력란으로 포커스를 옮기고
 *   Done은 키보드를 내리는 기본 동작을 한다. 마지막 칸을 채울 때 할 일은 [onComplete]에 둔다.
 */
@Composable
public fun KCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    mask: Boolean = false,
    error: Boolean = false,
    onComplete: ((String) -> Unit)? = null,
    contentDescription: String = stringResource(R.string.kinetica_ui_code_label),
    surface: SurfaceStyle = KineticaTheme.surface,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    require(length in 1..MAX_LENGTH) { "length는 1..12여야 합니다." }
    val code = value.filter(Char::isDigit).take(length)
    val latestChange by rememberUpdatedState(onValueChange)
    val latestComplete by rememberUpdatedState(onComplete)
    // 거부한 입력도 매번 새 값으로 넘겨야 BasicTextField가 키보드 쪽 글자를 되돌린다.
    var field by remember { mutableStateOf(TextFieldValue(code, TextRange(code.length)), neverEqualPolicy()) }
    val shown = if (field.text == code) field else TextFieldValue(code, TextRange(code.length))
    var focused by remember { mutableStateOf(false) }
    val shakeTrigger = rememberErrorTrigger(error)
    val still = reducedMotion()
    val progress = pluralStringResource(R.plurals.kinetica_ui_code_progress, length, length, code.length)
    val errorText = stringResource(R.string.kinetica_ui_code_error)
    val clock = rememberLoopClock(running = focused && code.length < length && !still)
    ProvideFieldSelection(KineticaTheme.colors.accent) {
        BasicTextField(
            value = shown,
            onValueChange = { next ->
                val accepted = acceptCode(code, next.text, length)
                field = TextFieldValue(accepted, TextRange(accepted.length))
                if (accepted != code) {
                    latestChange(accepted)
                    if (accepted.length == length) latestComplete?.invoke(accepted)
                }
            },
            modifier = modifier
                .keepVisibleWhileFocused(focused)
                .shake(shakeTrigger)
                .loopVisibility(clock)
                .onFocusChanged { focused = it.isFocused }
                .semantics {
                    this.contentDescription = contentDescription
                    stateDescription = progress
                    contentType = ContentType.SmsOtpCode
                    if (error) error(errorText)
                },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (mask) KeyboardType.NumberPassword else KeyboardType.Number,
                imeAction = imeAction,
            ),
            keyboardActions = keyboardActions,
            visualTransformation = if (mask) PasswordVisualTransformation() else VisualTransformation.None,
            singleLine = true,
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            decorationBox = { inner ->
                Box {
                    // 실제 입력란은 보이지 않게 두고 칸은 아래 Row가 그린다. 길게 눌러 붙여 넣기는 그대로 동작한다.
                    Box(Modifier.size(1.dp).graphicsLayer { alpha = 0f }) { inner() }
                    val look = CodeLook(surface.forField(), KineticaTheme.colors.accent, KineticaTheme.colors.destructive, mask, still)
                    CodeCells(code, length, focused, error, look, clock)
                }
            },
        )
    }
}

/**
 * [previous]에서 입력란이 [incoming]으로 바뀌었을 때 받아들일 코드. 숫자만 남기고, 한 번에 [length]자리 이상이 들어왔으면
 * 새로 들어온 코드 전체로 본다(이미 몇 자리 입력한 뒤에 붙여 넣은 경우).
 */
internal fun acceptCode(previous: String, incoming: String, length: Int): String {
    val digits = incoming.filter(Char::isDigit)
    val added = digits.length - previous.length
    return if (added >= length) digits.takeLast(length) else digits.take(length)
}

/** [error]가 `false`에서 `true`로 바뀔 때마다 1씩 오르는 값. 처음부터 오류 상태로 나타날 때는 흔들지 않는다. */
@Composable
internal fun rememberErrorTrigger(error: Boolean): Int {
    var count by remember { mutableIntStateOf(0) }
    var last by remember { mutableStateOf(error) }
    LaunchedEffect(error) {
        if (error && !last) count++
        last = error
    }
    return count
}

@Composable
private fun CodeCells(code: String, length: Int, focused: Boolean, error: Boolean, look: CodeLook, clock: LoopClock) {
    // 인증번호는 RTL 언어에서도 왼쪽부터 읽는다.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CELL_GAP, Alignment.CenterHorizontally)) {
            repeat(length) { index ->
                val state = when {
                    index < code.length -> CodeCellState.Filled
                    focused && index == code.length -> CodeCellState.Active
                    else -> CodeCellState.Empty
                }
                CodeCell(code.getOrNull(index), state, error, look, clock)
            }
        }
    }
}

private enum class CodeCellState { Empty, Active, Filled }

private class CodeLook(val surface: SurfaceStyle, val accent: Color, val errorColor: Color, val mask: Boolean, val still: Boolean)

@Composable
private fun RowScope.CodeCell(char: Char?, state: CodeCellState, error: Boolean, look: CodeLook, clock: LoopClock) {
    val pop = remember { Animatable(1f) }
    val active = remember { Animatable(if (state == CodeCellState.Active) 1f else 0f) }
    val errorLevel = remember { Animatable(if (error) 1f else 0f) }
    val first = remember { BooleanArray(1) { true } }
    LaunchedEffect(char) {
        if (first[0]) {
            first[0] = false
        } else if (char != null && !look.still) {
            pop.snapTo(0f)
            pop.animateTo(1f, spring(dampingRatio = POP_DAMPING, stiffness = POP_STIFFNESS))
        }
    }
    LaunchedEffect(state) { active.animateTo(if (state == CodeCellState.Active) 1f else 0f) }
    LaunchedEffect(error) { errorLevel.animateTo(if (error) 1f else 0f) }
    val grow = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val ink = look.surface.contentColor
    Box(
        Modifier
            // fill = true면 weight가 폭을 몫만큼 고정해 아래 최대 폭이 먹지 않는다. 넓은 화면에서 칸이 가로로 길쭉해진다.
            .weight(1f, fill = false)
            .widthIn(max = CELL_MAX_WIDTH * grow)
            .fillMaxWidth()
            .heightIn(min = CELL_HEIGHT)
            .kSurface(look.surface, CELL_SHAPE)
            .cellChrome(state, look, CellAnim(active, errorLevel, clock)),
        contentAlignment = Alignment.Center,
    ) {
        if (char != null) {
            ThemedText(
                if (look.mask) MASK_DOT else char.toString(),
                Modifier
                    .padding(vertical = CELL_TEXT_PADDING)
                    .graphicsLayer {
                        val level = pop.value
                        scaleX = POP_FROM + (1f - POP_FROM) * level
                        scaleY = scaleX
                        translationY = (1f - level) * POP_RISE.toPx()
                        alpha = level.coerceIn(0f, 1f)
                    },
                style = TextStyle(
                    color = ink,
                    fontSize = if (look.mask) MASK_FONT_SIZE else CELL_FONT_SIZE,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

private class CellAnim(val active: Animatable<Float, *>, val error: Animatable<Float, *>, val clock: LoopClock)

/** 칸 테두리와 깜빡이는 커서. 애니메이션 값은 그리기 단계에서 읽는다. */
private fun Modifier.cellChrome(state: CodeCellState, look: CodeLook, anim: CellAnim): Modifier = drawWithCache {
    val width = CELL_BORDER.toPx()
    val stroke = Stroke(width)
    val corner = CornerRadius(CELL_CORNER.toPx() - width / 2)
    val inset = Offset(width / 2, width / 2)
    val box = Size(size.width - width, size.height - width)
    val caretWidth = CARET_WIDTH.toPx()
    val caretHeight = minOf(CARET_HEIGHT.toPx() * fontScale.coerceAtLeast(1f), size.height * CARET_MAX_RATIO)
    val idle = look.surface.contentColor.copy(alpha = if (state == CodeCellState.Filled) FILLED_BORDER_ALPHA else IDLE_BORDER_ALPHA)
    onDrawWithContent {
        drawContent()
        val active = anim.active.value
        val color = lerp(lerp(idle, look.accent, active), look.errorColor, anim.error.value)
        drawRoundRect(color, inset, box, corner, style = stroke)
        if (state == CodeCellState.Active) {
            val blink = if (look.still) 1f else (cos(anim.clock.seconds * 2f * PI.toFloat() / BLINK_SECONDS) + 1f) / 2f
            val alpha = active * (CARET_MIN_ALPHA + (1f - CARET_MIN_ALPHA) * blink)
            drawRoundRect(
                look.accent.copy(alpha = alpha),
                Offset((size.width - caretWidth) / 2, (size.height - caretHeight) / 2),
                Size(caretWidth, caretHeight),
                CornerRadius(caretWidth / 2),
            )
        }
    }
}

private const val MAX_LENGTH = 12
private const val MASK_DOT = "●"
private val CELL_SHAPE = RoundedCornerShape(14.dp)
private val CELL_CORNER = 14.dp
private val CELL_GAP = 8.dp
private val CELL_MAX_WIDTH = 52.dp
private val CELL_HEIGHT = 60.dp
private val CELL_BORDER = 1.5.dp
private val CELL_FONT_SIZE = 26.sp
private val MASK_FONT_SIZE = 16.sp
private val CELL_TEXT_PADDING = 10.dp
private val CARET_WIDTH = 2.dp
private val CARET_HEIGHT = 26.dp
private val POP_RISE = 10.dp
private const val CARET_MAX_RATIO = 0.6f
private const val CARET_MIN_ALPHA = 0.1f
private const val BLINK_SECONDS = 1.05f
private const val POP_FROM = 0.4f
private const val POP_DAMPING = 0.45f
private const val POP_STIFFNESS = 700f
private const val IDLE_BORDER_ALPHA = 0.10f
private const val FILLED_BORDER_ALPHA = 0.22f
