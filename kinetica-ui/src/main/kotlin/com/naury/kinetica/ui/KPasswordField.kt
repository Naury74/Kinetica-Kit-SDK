package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 글래스 비밀번호 입력란. 끝에 있는 눈 아이콘을 누르면 비밀번호를 보이거나 숨기고, 아이콘은 눈을 감았다 뜨듯 모양이 바뀐다.
 * [showStrength]가 `true`면 입력란 아래에 강도 막대가 약함은 빨강, 보통은 주황, 강함은 초록으로 차오른다([passwordStrength] 규칙).
 * [showChecklist]가 `true`면 8자 이상, 대문자, 숫자, 기호를 갖췄는지 체크 표시로 보여 준다.
 *
 * 비밀번호 자동 채우기를 받는다. 새 비밀번호를 만드는 화면이면 [newPassword]를 `true`로 두어 비밀번호 관리자가 새 비밀번호를 제안하게 한다.
 * TalkBack은 눈 아이콘을 "비밀번호 보기"나 "비밀번호 숨기기" 버튼으로 읽고, 강도가 바뀌면 알린다.
 *
 * ```kotlin
 * var password by remember { mutableStateOf("") }
 * KPasswordField(password, { password = it }, label = "새 비밀번호", showChecklist = true)
 * ```
 *
 * @param label 입력란 이름. 비어 있을 때는 가운데에, 그 밖에는 위쪽에 작게 보인다.
 * @param showStrength 강도 막대를 보일지
 * @param showChecklist 조건 체크리스트를 보일지
 * @param newPassword 새 비밀번호를 만드는 입력란인지. 자동 채우기 종류만 바꾼다.
 * @param error 오류 문구. `null`이 아니면 테두리가 빨개지고 입력란이 흔들리며 아래에 문구가 보인다.
 * @param keyboardActions IME 동작 키를 눌렀을 때 할 일. [imeAction]에 맞는 콜백을 채운다.
 * @param imeAction 키보드 동작 키의 종류. 아래에 입력란이 더 있으면 [ImeAction.Next]로 둔다. [keyboardActions]에 맞는 콜백이 없으면
 *   Next는 다음 입력란으로 포커스를 옮기고 Done은 키보드를 내리는 기본 동작을 한다.
 * @param fieldModifier 안쪽 입력란에 붙일 Modifier. `testTag`, `focusRequester`, 자동 채우기 설정처럼 입력란 자체에 걸어야 하는 것을 여기에 준다.
 *   [modifier]는 그대로 입력란, 강도 막대, 체크리스트를 감싼 바깥 묶음에 붙는다.
 */
@Composable
public fun KPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    showStrength: Boolean = true,
    showChecklist: Boolean = false,
    newPassword: Boolean = showStrength,
    error: String? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    surface: SurfaceStyle = KineticaTheme.surface,
    imeAction: ImeAction = ImeAction.Done,
    fieldModifier: Modifier = Modifier,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val colors = KineticaTheme.colors
    val float = remember { Animatable(if (value.isEmpty()) 0f else 1f) }
    val levels = remember { FieldLevels(focused = false, error = error != null) }
    LaunchedEffect(focused, value.isEmpty()) {
        float.animateTo(if (focused || value.isNotEmpty()) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(focused) { levels.focusTo(focused) }
    LaunchedEffect(error != null) { levels.errorTo(error != null) }
    val motion = PasswordMotion(float, levels.focus, levels.error, colors.accent, colors.destructive)
    Column(modifier.keepVisibleWhileFocused(focused)) {
        ProvideFieldSelection(colors.accent) {
            BasicTextField(
                value,
                onValueChange,
                Modifier
                    .fillMaxWidth()
                    .shake(rememberErrorTrigger(error != null))
                    .kSurface(surface.forField(), fieldShape)
                    .fieldFocusFrame(fieldShape, levels, colors.accent, colors.destructive)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics {
                        contentType = if (newPassword) ContentType.NewPassword else ContentType.Password
                        if (error != null) error(error)
                    }
                    .then(fieldModifier),
                singleLine = true,
                textStyle = themed(TextStyle(color = surface.contentColor, fontSize = FIELD_FONT_SIZE)),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction, autoCorrectEnabled = false),
                keyboardActions = keyboardActions,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                decorationBox = { field ->
                    PasswordDecoration(label, surface, motion, field) { EyeToggle(visible, surface.contentColor) { visible = !visible } }
                },
            )
        }
        FieldSupportText(error, null, surface)
        if (showStrength) StrengthMeter(value, surface)
        if (showChecklist) Checklist(value, surface)
    }
}

private class PasswordMotion(
    val float: Animatable<Float, *>,
    val focus: Animatable<Float, *>,
    val error: Animatable<Float, *>,
    val accent: Color,
    val errorColor: Color,
)

@Composable
private fun PasswordDecoration(
    label: String,
    surface: SurfaceStyle,
    motion: PasswordMotion,
    field: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val grow = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Row(
        Modifier.heightIn(
            min = KineticaTheme.design.density.height(FIELD_HEIGHT) * grow,
        ).padding(start = fieldStartPadding, end = TRAILING_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val centering = remember { LabelCentering() }
        Box(Modifier.weight(1f).onSizeChanged { centering.area = it.height }) {
            ThemedText(
                label,
                Modifier.onSizeChanged { centering.label = it.height }.graphicsLayer {
                    val up = motion.float.value
                    transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0f)
                    scaleX = 1f - (1f - LABEL_SCALE) * up
                    scaleY = scaleX
                    val rest = centering.restPx(LABEL_REST * density * grow, LABEL_NUDGE.toPx())
                    translationY = rest + (LABEL_FLOAT * density * grow - rest) * up
                },
                style = TextStyle(fontSize = FIELD_FONT_SIZE),
                color = {
                    val idle = surface.muted(FIELD_HINT_ALPHA)
                    lerp(lerp(idle, motion.accent, motion.focus.value * motion.float.value), motion.errorColor, motion.error.value)
                },
                maxLines = 1,
            )
            Box(Modifier.fillMaxWidth().padding(top = INPUT_TOP * grow, bottom = INPUT_BOTTOM)) { field() }
        }
        trailing()
    }
}

/**
 * 눈 아이콘 버튼. 숨김 상태에서는 눈꺼풀이 반쯤 내려오고 사선이 그어지며, 보이기 상태로 바꾸면 사선이 걷히고 눈이 떠진다.
 */
@Composable
private fun EyeToggle(visible: Boolean, color: Color, onToggle: () -> Unit) {
    val hidden = remember { Animatable(if (visible) 0f else 1f) }
    LaunchedEffect(visible) {
        hidden.animateTo(if (visible) 0f else 1f, spring(dampingRatio = EYE_DAMPING, stiffness = Spring.StiffnessMedium))
    }
    val label = stringResource(if (visible) R.string.kinetica_ui_password_hide else R.string.kinetica_ui_password_show)
    Box(
        Modifier
            .size(EYE_TOUCH)
            .clickable(null, KIndication(CircleShape), role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = label }
            .drawWithCache {
                val stroke = EYE_STROKE.toPx()
                val w = EYE_WIDTH.toPx()
                val h = EYE_HEIGHT.toPx()
                val center = Offset(size.width / 2, size.height / 2)
                val outline = Path()
                val line = Stroke(stroke, cap = StrokeCap.Round)
                onDrawBehind {
                    val shut = hidden.value.coerceIn(0f, 1f)
                    val open = 1f - EYE_SQUINT * shut
                    outline.reset()
                    outline.moveTo(center.x - w / 2, center.y)
                    outline.quadraticTo(center.x, center.y - h * open, center.x + w / 2, center.y)
                    outline.quadraticTo(center.x, center.y + h * open, center.x - w / 2, center.y)
                    val ink = color.copy(alpha = EYE_ALPHA)
                    drawPath(outline, ink, style = line)
                    drawCircle(ink, PUPIL_RADIUS.toPx() * (1f - PUPIL_SHRINK * shut), center)
                    if (shut > 0f) {
                        val half = SLASH_HALF.toPx()
                        val from = center + Offset(-half, -half)
                        val to = from + Offset(half * 2 * shut, half * 2 * shut)
                        drawLine(ink, from, to, stroke * SLASH_WEIGHT, StrokeCap.Round)
                    }
                }
            },
    )
}

@Composable
private fun StrengthMeter(value: String, surface: SurfaceStyle) {
    val strength = passwordStrength(value)
    val target = if (value.isEmpty()) 0f else strength.ordinal + 1f
    val level = remember { Animatable(target) }
    LaunchedEffect(target) { level.animateTo(target, spring(dampingRatio = METER_DAMPING, stiffness = Spring.StiffnessMediumLow)) }
    val colors = KineticaTheme.colors
    val weak = colors.destructive
    val strong = colors.positive
    val track = surface.raisedFill(TRACK_ALPHA)
    val name = stringResource(
        when (strength) {
            PasswordStrength.Weak -> R.string.kinetica_ui_password_weak
            PasswordStrength.Medium -> R.string.kinetica_ui_password_medium
            PasswordStrength.Strong -> R.string.kinetica_ui_password_strong
        },
    )
    val title = stringResource(R.string.kinetica_ui_password_strength)
    Row(
        Modifier
            .padding(start = FIELD_PADDING, top = METER_GAP, end = FIELD_PADDING)
            .semantics(mergeDescendants = true) {
                contentDescription = title
                stateDescription = if (value.isEmpty()) "" else name
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(METER_HEIGHT)
                .drawBehind {
                    val gap = METER_SEGMENT_GAP.toPx()
                    val segment = (size.width - gap * (SEGMENTS - 1)) / SEGMENTS
                    val radius = CornerRadius(size.height / 2)
                    val fill = meterColor(level.value, weak, Color(MEDIUM_COLOR), strong)
                    // RTL에서는 막대가 오른쪽부터 차오른다.
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    for (i in 0 until SEGMENTS) {
                        val left = if (rtl) size.width - (segment + gap) * i - segment else (segment + gap) * i
                        drawRoundRect(track, Offset(left, 0f), Size(segment, size.height), radius)
                        val part = (level.value - i).coerceIn(0f, 1f)
                        if (part > 0f) {
                            val start = if (rtl) left + segment * (1f - part) else left
                            drawRoundRect(fill, Offset(start, 0f), Size(segment * part, size.height), radius)
                        }
                    }
                },
        )
        // 강도는 stateDescription으로 읽으므로 글자는 다시 읽지 않게 지운다.
        Box(Modifier.padding(start = METER_LABEL_GAP).graphicsLayer { alpha = (level.value).coerceIn(0f, 1f) }.clearAndSetSemantics {}) {
            ThemedText(
                name,
                style = TextStyle(fontSize = SMALL_FONT_SIZE, fontWeight = FontWeight.SemiBold),
                color = { meterColor(level.value.coerceAtLeast(1f), weak, Color(MEDIUM_COLOR), strong) },
                maxLines = 1,
            )
        }
    }
}

/** 강도 막대가 찬 정도(0..3)에 맞는 색. 1까지는 [weak], 2에서 [medium], 3에서 [strong]으로 옮겨 간다. */
private fun meterColor(level: Float, weak: Color, medium: Color, strong: Color): Color = when {
    level <= 1f -> weak
    level <= 2f -> lerp(weak, medium, level - 1f)
    else -> lerp(medium, strong, (level - 2f).coerceAtMost(1f))
}

@Composable
private fun Checklist(value: String, surface: SurfaceStyle) {
    val rules = listOf(
        R.string.kinetica_ui_password_rule_length to (value.length >= PASSWORD_MIN_LENGTH),
        R.string.kinetica_ui_password_rule_upper to value.any(Char::isUpperCase),
        R.string.kinetica_ui_password_rule_number to value.any(Char::isDigit),
        R.string.kinetica_ui_password_rule_symbol to value.any(::isPasswordSymbol),
    )
    FlowRow(
        Modifier.padding(start = FIELD_PADDING, top = CHECKLIST_GAP, end = FIELD_PADDING),
        horizontalArrangement = Arrangement.spacedBy(CHECKLIST_ITEM_GAP),
        verticalArrangement = Arrangement.spacedBy(CHECKLIST_ROW_GAP),
    ) {
        rules.forEach { (label, met) -> RuleItem(stringResource(label), met, surface) }
    }
}

@Composable
private fun RuleItem(label: String, met: Boolean, surface: SurfaceStyle) {
    val level = remember { Animatable(if (met) 1f else 0f) }
    LaunchedEffect(met) { level.animateTo(if (met) 1f else 0f, spring(dampingRatio = METER_DAMPING)) }
    val positive = KineticaTheme.colors.positive
    val idle = surface.muted(RULE_IDLE_ALPHA)
    val state = stringResource(if (met) R.string.kinetica_ui_password_rule_met else R.string.kinetica_ui_password_rule_unmet)
    Row(
        Modifier.semantics(mergeDescendants = true) { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(RULE_ICON)
                .drawBehind {
                    val t = level.value.coerceIn(0f, 1f)
                    val radius = size.minDimension / 2
                    drawCircle(lerp(idle, positive, t).copy(alpha = RULE_RING_ALPHA + (1f - RULE_RING_ALPHA) * t), radius)
                    if (t > 0f) {
                        val w = size.width
                        val stroke = RULE_STROKE.toPx()
                        val a = Offset(w * CHECK_X0, w * CHECK_Y0)
                        val b = Offset(w * CHECK_X1, w * CHECK_Y1)
                        val c = Offset(w * CHECK_X2, w * CHECK_Y2)
                        val first = (t / CHECK_SPLIT).coerceIn(0f, 1f)
                        val second = ((t - CHECK_SPLIT) / (1f - CHECK_SPLIT)).coerceIn(0f, 1f)
                        drawLine(Color.White, a, a + (b - a) * first, stroke, StrokeCap.Round)
                        if (second > 0f) drawLine(Color.White, b, b + (c - b) * second, stroke, StrokeCap.Round)
                    }
                },
        )
        ThemedText(
            label,
            Modifier.padding(start = RULE_LABEL_GAP),
            style = TextStyle(fontSize = SMALL_FONT_SIZE),
            color = { lerp(surface.muted(RULE_TEXT_ALPHA), surface.contentColor, level.value.coerceIn(0f, 1f)) },
            maxLines = 1,
        )
    }
}

private val FIELD_HEIGHT = 52.dp
private val FIELD_PADDING = 16.dp
private val TRAILING_PADDING = 4.dp
private val INPUT_TOP = 22.dp
private val INPUT_BOTTOM = 6.dp
private val FIELD_FONT_SIZE = 16.sp
private val SMALL_FONT_SIZE = 13.sp
private const val LABEL_REST = 15f
private const val LABEL_FLOAT = 6f
private const val LABEL_SCALE = 0.75f

private val EYE_TOUCH = 44.dp
private val EYE_WIDTH = 22.dp
private val EYE_HEIGHT = 15.dp
private val EYE_STROKE = 1.8.dp
private val PUPIL_RADIUS = 3.2.dp
private val SLASH_HALF = 9.dp
private const val EYE_SQUINT = 0.45f
private const val PUPIL_SHRINK = 0.35f
private const val SLASH_WEIGHT = 1.1f
private const val EYE_ALPHA = 0.7f
private const val EYE_DAMPING = 0.6f

private const val SEGMENTS = 3
private const val MEDIUM_COLOR = 0xFFFF9F0A
private val METER_HEIGHT = 5.dp
private val METER_GAP = 10.dp
private val METER_SEGMENT_GAP = 4.dp
private val METER_LABEL_GAP = 10.dp
private const val METER_DAMPING = 0.7f
private const val TRACK_ALPHA = 0.14f

private val CHECKLIST_GAP = 10.dp
private val CHECKLIST_ITEM_GAP = 14.dp
private val CHECKLIST_ROW_GAP = 6.dp
private val RULE_ICON = 16.dp
private val RULE_STROKE = 1.8.dp
private val RULE_LABEL_GAP = 6.dp
private const val RULE_IDLE_ALPHA = 0.5f
private const val RULE_RING_ALPHA = 0.35f
private const val RULE_TEXT_ALPHA = 0.55f
private const val CHECK_X0 = 0.28f
private const val CHECK_Y0 = 0.52f
private const val CHECK_X1 = 0.44f
private const val CHECK_Y1 = 0.68f
private const val CHECK_X2 = 0.74f
private const val CHECK_Y2 = 0.36f
private const val CHECK_SPLIT = 0.4f
