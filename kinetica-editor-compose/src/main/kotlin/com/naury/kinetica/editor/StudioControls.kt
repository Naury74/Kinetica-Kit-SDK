package com.naury.kinetica.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** 편집기 안의 공용 컨트롤이 쓰는 색. [KineticaEditor]가 넣는다. */
internal val LocalStudioTheme = staticCompositionLocalOf { StudioTheme() }

/** 편집기 공용 눌림 표시. 모양 안에만 글자색을 옅게 덮는다. */
@Composable
internal fun studioIndication(shape: Shape): StudioIndication {
    val color = LocalStudioTheme.current.onPanel
    return remember(shape, color) { StudioIndication(shape, color) }
}

/**
 * 글자 버튼. Material TextButton과 크기는 같고(최소 높이 40dp, 좌우 12dp) 눌림 표시만 캡슐 모양으로 바꿨다.
 * 비활성이면 내용 전체를 흐리게 그린다.
 */
@Composable
internal fun StudioTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val theme = LocalStudioTheme.current
    val source = remember { MutableInteractionSource() }
    // 누르는 영역은 호출한 쪽 크기(예: 48dp)를 따르고, 눌림 표시는 그 안의 40dp 캡슐에만 그린다.
    Box(
        modifier.clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .heightIn(min = BUTTON_MIN_HEIGHT)
                .indication(source, studioIndication(CapsuleShape))
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .padding(BUTTON_PADDING),
            horizontalArrangement = Arrangement.spacedBy(ICON_GAP, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides theme.accent) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge, content = { content() })
            }
        }
    }
}

/**
 * 켜고 끄는 칩. 보이는 높이는 32dp이고 터치 영역은 위아래로 늘려 44dp를 맞춘다.
 * 접근성 의미는 Material FilterChip과 같다(선택 가능, 체크박스 역할).
 */
@Composable
internal fun StudioChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    theme: StudioTheme = LocalStudioTheme.current,
) {
    val source = remember { MutableInteractionSource() }
    val container by animateColorAsState(
        if (selected) theme.accent.copy(alpha = CHIP_SELECTED_ALPHA) else Color.Transparent,
        tween(CHIP_FADE_MS),
    )
    val outline = if (selected) theme.accent.copy(alpha = CHIP_SELECTED_BORDER_ALPHA) else theme.onPanel.copy(alpha = OUTLINE_ALPHA)
    Box(
        modifier
            .heightIn(min = CHIP_TOUCH)
            .selectable(selected, source, indication = null, role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .heightIn(min = CHIP_HEIGHT)
                .background(container, ChipShape)
                .border(1.dp, outline, ChipShape)
                .indication(source, remember(theme.onPanel) { StudioIndication(ChipShape, theme.onPanel) })
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = theme.onPanel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** 문자열 입력란. 나머지는 [TextFieldValue]를 받는 쪽과 같다. */
@Composable
internal fun StudioTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supporting: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    FieldColors {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            textStyle = fieldTextStyle(),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            interactionSource = source,
            cursorBrush = SolidColor(LocalStudioTheme.current.accent),
            decorationBox = { inner -> FieldDecoration(label, focused, enabled, isError, supporting, singleLine, inner) },
        )
    }
}

/**
 * 편집기 입력란. 위에 작은 라벨, 아래에 둥근 상자(모서리 10dp, 최소 높이 44dp)를 둔다.
 * 초점이 오면 테두리가 강조색으로, 오류면 오류색으로 부드럽게 바뀐다. 커서와 선택 영역은 강조색이다.
 * 라벨과 안내 문구는 입력란 의미에 합쳐지므로 라벨 글자로 입력란을 찾을 수 있다.
 */
@Composable
internal fun StudioTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supporting: String? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    FieldColors {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            textStyle = fieldTextStyle(),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else MULTI_LINE_MIN,
            visualTransformation = visualTransformation,
            interactionSource = source,
            cursorBrush = SolidColor(LocalStudioTheme.current.accent),
            decorationBox = { inner -> FieldDecoration(label, focused, true, isError, supporting, singleLine, inner) },
        )
    }
}

@Composable
private fun FieldColors(content: @Composable () -> Unit) {
    val accent = LocalStudioTheme.current.accent
    val selection = remember(accent) { TextSelectionColors(handleColor = accent, backgroundColor = accent.copy(alpha = SELECTION_ALPHA)) }
    CompositionLocalProvider(LocalTextSelectionColors provides selection, content = content)
}

@Composable
private fun fieldTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(color = LocalStudioTheme.current.onPanel)

@Composable
@Suppress("LongParameterList")
private fun FieldDecoration(
    label: String?,
    focused: Boolean,
    enabled: Boolean,
    isError: Boolean,
    supporting: String?,
    singleLine: Boolean,
    inner: @Composable () -> Unit,
) {
    val theme = LocalStudioTheme.current
    val error = MaterialTheme.colorScheme.error
    val border by animateColorAsState(
        when {
            isError -> error
            focused -> theme.accent
            else -> theme.onPanel.copy(alpha = FIELD_BORDER_ALPHA)
        },
        tween(FIELD_FADE_MS),
    )
    val fill = theme.onPanel.copy(alpha = if (focused) FIELD_FOCUSED_FILL_ALPHA else FIELD_FILL_ALPHA)
    Column(Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) {
            Text(label, color = theme.onPanel.copy(alpha = LABEL_ALPHA), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = FIELD_MIN_HEIGHT)
                .background(fill, FieldShape)
                .border(if (focused || isError) 1.5.dp else 1.dp, border, FieldShape)
                .padding(horizontal = 12.dp, vertical = if (singleLine) 0.dp else 10.dp),
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
        ) { inner() }
        if (supporting != null) {
            Text(
                supporting,
                color = if (isError) error else theme.onPanel.copy(alpha = LABEL_ALPHA),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

internal val CapsuleShape = RoundedCornerShape(percent = 50)
internal val ChipShape = RoundedCornerShape(8.dp)
internal val FieldShape = RoundedCornerShape(10.dp)
internal val TOUCH_TARGET = 48.dp
private val BUTTON_MIN_HEIGHT = 40.dp
private val BUTTON_PADDING = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
private val ICON_GAP = 6.dp
private val CHIP_HEIGHT = 32.dp
private val CHIP_TOUCH = 44.dp
private val FIELD_MIN_HEIGHT = 44.dp
private const val DISABLED_ALPHA = 0.38f
private const val CHIP_SELECTED_ALPHA = 0.22f
private const val CHIP_SELECTED_BORDER_ALPHA = 0.6f
private const val OUTLINE_ALPHA = 0.22f
private const val CHIP_FADE_MS = 140
private const val FIELD_FADE_MS = 160
private const val FIELD_BORDER_ALPHA = 0.16f
private const val FIELD_FILL_ALPHA = 0.05f
private const val FIELD_FOCUSED_FILL_ALPHA = 0.08f
private const val LABEL_ALPHA = 0.72f
private const val SELECTION_ALPHA = 0.32f
private const val MULTI_LINE_MIN = 3

/**
 * 가로로 스크롤되는 줄의 양 끝을 흐리게 해 내용이 더 있다는 것을 보여 준다. 끝까지 스크롤한 쪽은 흐리게 하지 않는다.
 * 스크롤 modifier보다 앞에 둔다.
 */
internal fun Modifier.fadingEdges(scroll: ScrollState, width: Dp = FADE_WIDTH): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = width.toPx().coerceAtMost(size.width / 2)
        val ltr = layoutDirection == LayoutDirection.Ltr
        // 스크롤 위치 0은 시작 쪽이다. RTL에서는 화면 오른쪽이 시작이다.
        val startOpen = scroll.value > 0
        val endOpen = scroll.value < scroll.maxValue
        val leftOpen = if (ltr) startOpen else endOpen
        val rightOpen = if (ltr) endOpen else startOpen
        if (leftOpen) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, fade),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (rightOpen) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), size.width - fade, size.width),
                topLeft = Offset(size.width - fade, 0f),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

private val FADE_WIDTH = 24.dp
