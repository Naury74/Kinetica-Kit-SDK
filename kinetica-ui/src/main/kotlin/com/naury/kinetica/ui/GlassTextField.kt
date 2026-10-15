package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 글래스 입력란. 포커스를 받거나 글자가 있으면 [label]이 위로 작게 떠오른다. 포커스를 받으면 테두리가 강조색으로 바뀐다.
 *
 * [error]가 `null`이 아니면 테두리와 라벨이 빨갛게 바뀌고 입력란 아래에 오류 문구가 나타난다. 오류가 새로 생기거나 문구가 바뀔 때
 * 입력란이 좌우로 한 번 살짝 흔들린다. '애니메이션 삭제' 접근성 설정이 켜져 있으면 흔들리지 않는다.
 * TalkBack은 입력란의 오류를 읽고, 오류 문구가 나타나면 바로 알린다.
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
 */
@Composable
public fun GlassTextField(
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
    style: GlassStyle = LocalGlassStyle.current,
) {
    require(maxLines >= 1) { "maxLines는 1 이상이어야 합니다." }
    var focused by remember { mutableStateOf(false) }
    val motion = remember { FieldMotion(value.isNotEmpty(), error) }
    val still = reducedMotion()
    LaunchedEffect(focused, value.isEmpty()) {
        motion.float.animateTo(if (focused || value.isNotEmpty()) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(focused) { motion.focus.animateTo(if (focused) 1f else 0f) }
    LaunchedEffect(error) { motion.onError(error, still) }
    Column(modifier) {
        BasicTextField(
            value,
            onValueChange,
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = motion.shake.value * density
                    alpha = if (enabled) 1f else DISABLED_ALPHA
                }
                .glass(style, FIELD_SHAPE)
                .fieldBorder(motion)
                .onFocusChanged { focused = it.isFocused }
                .semantics { if (error != null) error(error) },
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else maxLines,
            interactionSource = interactionSource,
            keyboardActions = keyboardActions,
            textStyle = TextStyle(color = style.onGlass, fontSize = FIELD_FONT_SIZE),
            cursorBrush = SolidColor(Color(FIELD_ACCENT)),
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            decorationBox = { field -> FieldDecoration(label, style, motion, field) },
        )
        if (error != null) {
            BasicText(
                error,
                Modifier
                    .padding(start = FIELD_PADDING, top = ERROR_GAP, end = FIELD_PADDING)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = TextStyle(color = Color(FIELD_ERROR), fontSize = ERROR_FONT_SIZE),
            )
        }
    }
}

@Composable
private fun FieldDecoration(label: String, style: GlassStyle, motion: FieldMotion, field: @Composable () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(Modifier.heightIn(min = FIELD_HEIGHT).padding(horizontal = FIELD_PADDING)) {
        BasicText(
            label,
            Modifier.graphicsLayer {
                val up = motion.float.value
                transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0f)
                scaleX = 1f - (1f - LABEL_SCALE) * up
                scaleY = scaleX
                translationY = (LABEL_REST + (LABEL_FLOAT - LABEL_REST) * up) * density
            },
            style = TextStyle(fontSize = FIELD_FONT_SIZE),
            color = {
                val idle = style.onGlass.copy(alpha = LABEL_ALPHA)
                lerp(lerp(idle, Color(FIELD_ACCENT), motion.focus.value * motion.float.value), Color(FIELD_ERROR), motion.error.value)
            },
            maxLines = 1,
        )
        Box(Modifier.fillMaxWidth().padding(top = INPUT_TOP, bottom = INPUT_BOTTOM).align(Alignment.TopStart)) { field() }
    }
}

/** 포커스와 오류 상태를 테두리 색으로 그린다. 색은 그리기 단계에서 읽어 애니메이션 중에 재구성하지 않는다. */
private fun Modifier.fieldBorder(motion: FieldMotion): Modifier = drawWithCache {
    val width = BORDER_WIDTH.toPx()
    val stroke = Stroke(width)
    val corner = CornerRadius(FIELD_CORNER.toPx() - width / 2)
    val inset = Offset(width / 2, width / 2)
    val box = Size(size.width - width, size.height - width)
    onDrawWithContent {
        drawContent()
        val color = lerp(Color(FIELD_ACCENT).copy(alpha = motion.focus.value), Color(FIELD_ERROR), motion.error.value)
        if (color.alpha > 0f) drawRoundRect(color, inset, box, corner, style = stroke)
    }
}

/** 라벨 위치, 포커스, 오류 표시, 흔들림 애니메이션 값. */
private class FieldMotion(filled: Boolean, initialError: String?) {
    val float = Animatable(if (filled) 1f else 0f)
    val focus = Animatable(0f)
    val error = Animatable(if (initialError != null) 1f else 0f)

    /** 가로로 흔들리는 거리(dp). */
    val shake = Animatable(0f)
    private var lastError: String? = initialError

    suspend fun onError(next: String?, still: Boolean) = coroutineScope {
        val changed = next != null && next != lastError
        lastError = next
        launch { error.animateTo(if (next != null) 1f else 0f) }
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

private val FIELD_SHAPE = RoundedCornerShape(14.dp)
private val FIELD_CORNER = 14.dp
private val FIELD_HEIGHT = 56.dp
private val FIELD_PADDING = 16.dp
private val INPUT_TOP = 24.dp
private val INPUT_BOTTOM = 8.dp
private val BORDER_WIDTH = 1.5.dp
private val ERROR_GAP = 6.dp
private val FIELD_FONT_SIZE = 16.sp
private val ERROR_FONT_SIZE = 13.sp

/** 라벨이 입력란 가운데에 있을 때와 떠올랐을 때의 위쪽 거리(dp). */
private const val LABEL_REST = 17f
private const val LABEL_FLOAT = 7f
private const val LABEL_SCALE = 0.75f
private const val LABEL_ALPHA = 0.55f
private const val DISABLED_ALPHA = 0.5f
private const val SHAKE_MS = 360
private const val SHAKE_AT_1 = 60
private const val SHAKE_AT_2 = 120
private const val SHAKE_AT_3 = 180
private const val SHAKE_AT_4 = 240
private const val SHAKE_FIRST = 8f
private const val SHAKE_SECOND = 4f
private const val FIELD_ACCENT = 0xFF0A84FF
private const val FIELD_ERROR = 0xFFFF453A
