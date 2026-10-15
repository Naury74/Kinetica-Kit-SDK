package com.naury.kinetica.board

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.naury.kinetica.core.scene.SceneDocument

/** 위젯 추가 버튼을 끝쪽 아래 모서리(RTL이면 왼쪽 아래)에 두고, 버튼과 메뉴의 글자는 호스트의 레이아웃 방향으로 그린다. */
@Composable
internal fun BoxScope.AddWidgetCorner(hostDirection: LayoutDirection, mirrored: Boolean, content: @Composable (Modifier) -> Unit) {
    val corner = Modifier.align(if (mirrored) Alignment.BottomStart else Alignment.BottomEnd).padding(16.dp)
    CompositionLocalProvider(LocalLayoutDirection provides hostDirection) { content(corner) }
}

@Composable
internal fun AddWidgetButton(state: KineticaBoardState, document: SceneDocument, colors: BoardColors, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    // document를 받아 배치가 바뀔 때마다 다시 계산한다.
    val available = remember(document) { state.availableWidgets }
    Box(modifier) {
        Row(
            Modifier
                .heightIn(min = ADD_BUTTON_HEIGHT)
                .shadow(ADD_BUTTON_ELEVATION, CapsuleShape)
                .background(colors.accent, CapsuleShape)
                .clickable(
                    interactionSource = null,
                    indication = remember(colors.onAccent) { BoardIndication(CapsuleShape, colors.onAccent) },
                    role = Role.Button,
                ) { open = true }
                .padding(start = 16.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { AddLabel(stringResource(R.string.kinetica_board_add), colors.onAccent) }
        BoardMenu(expanded = open, onDismissRequest = { open = false }) {
            if (available.isEmpty()) {
                BoardMenuItem(stringResource(R.string.kinetica_board_nothing_to_add), enabled = false) { open = false }
            }
            available.forEach { widget ->
                BoardMenuItem(widget.label) {
                    open = false
                    state.add(widget.key)
                }
            }
        }
    }
}

/**
 * 위젯 추가 메뉴. 호스트 MaterialTheme의 표면색과 글자색을 따르고, 버튼의 끝 가장자리에 맞춰 위나 아래로 열린다.
 * 초점을 가져가므로 뒤로 가기나 바깥 누르기로 닫히고 키보드로 항목을 옮겨 다닐 수 있다.
 */
@Composable
internal fun BoardMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (!expanded) return
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val position = remember(density) { EndAlignedPosition(density) }
    Popup(popupPositionProvider = position, onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
        val shown = remember { Animatable(if (ValueAnimator.areAnimatorsEnabled()) 0f else 1f) }
        LaunchedEffect(Unit) { shown.animateTo(1f, tween(MENU_APPEAR_MS)) }
        Column(
            Modifier
                .padding(MENU_SHADOW_ROOM)
                .graphicsLayer {
                    alpha = shown.value
                    scaleX = MENU_APPEAR_SCALE + (1f - MENU_APPEAR_SCALE) * shown.value
                    scaleY = scaleX
                }
                .shadow(MENU_ELEVATION, MenuShape)
                .background(scheme.surfaceContainerHigh, MenuShape)
                .border(1.dp, scheme.onSurface.copy(alpha = MENU_EDGE_ALPHA), MenuShape)
                .widthIn(min = MENU_MIN_WIDTH, max = MENU_MAX_WIDTH)
                .heightIn(max = MENU_MAX_HEIGHT)
                .verticalScroll(rememberScrollState())
                .padding(MENU_INSET),
            content = content,
        )
    }
}

@Composable
private fun BoardMenuItem(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val content = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MENU_ITEM_HEIGHT)
            .clickable(
                interactionSource = null,
                indication = remember(content) { BoardIndication(MenuItemShape, content) },
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = content.copy(alpha = if (enabled) 1f else MENU_DISABLED_ALPHA), style = MaterialTheme.typography.bodyLarge)
    }
}

// 메뉴의 끝 가장자리를 버튼 끝 가장자리에 맞추고, 아래 공간이 모자라면 버튼 위로 연다.
private class EndAlignedPosition(density: Density) : PopupPositionProvider {
    private val gap = with(density) { MENU_GAP.roundToPx() }
    private val margin = with(density) { MENU_SCREEN_MARGIN.roundToPx() }
    private val room = with(density) { MENU_SHADOW_ROOM.roundToPx() }

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val width = popupContentSize.width
        val height = popupContentSize.height
        val end = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - width + room else anchorBounds.left - room
        val x = end.coerceIn(margin - room, (windowSize.width - width - margin + room).coerceAtLeast(margin - room))
        val below = anchorBounds.bottom + gap - room
        val above = anchorBounds.top - gap - height + room
        val y = when {
            below + height <= windowSize.height - margin + room -> below
            above >= margin - room -> above
            else -> (windowSize.height - height - margin + room).coerceAtLeast(margin - room)
        }
        return IntOffset(x, y)
    }
}

// 기호와 글자를 따로 두어야 RTL 언어에서도 + 기호가 시작쪽에 온다.
@Composable
private fun AddLabel(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlusMark(color)
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

// 글꼴에 따라 굵기가 달라지지 않도록 직접 그린다.
@Composable
private fun PlusMark(color: Color) {
    Canvas(Modifier.size(MARK_SIZE)) {
        val stroke = MARK_STROKE.toPx()
        drawRoundRect(color, Offset(0f, (size.height - stroke) / 2), Size(size.width, stroke), CornerRadius(stroke / 2))
        drawRoundRect(color, Offset((size.width - stroke) / 2, 0f), Size(stroke, size.height), CornerRadius(stroke / 2))
    }
}

/** 삭제 버튼의 − 기호. 끝을 둥글게 한 막대 하나로 그려 가운데에 맞춘다. */
@Composable
internal fun RemoveMark(color: Color) {
    Canvas(Modifier.size(MARK_SIZE)) {
        val stroke = MARK_STROKE.toPx()
        drawRoundRect(color, Offset(0f, (size.height - stroke) / 2), Size(size.width, stroke), CornerRadius(stroke / 2))
    }
}

internal val CapsuleShape = RoundedCornerShape(percent = 50)
private val MenuShape = RoundedCornerShape(16.dp)

// 메뉴 모서리 16dp에서 안쪽 여백 6dp를 뺀 값.
private val MenuItemShape = RoundedCornerShape(10.dp)
private val ADD_BUTTON_HEIGHT = 48.dp
private val ADD_BUTTON_ELEVATION = 6.dp
private val MENU_ELEVATION = 12.dp
private val MENU_INSET = 6.dp
private val MENU_GAP = 6.dp
private val MENU_SCREEN_MARGIN = 8.dp
private val MENU_SHADOW_ROOM = 12.dp
private val MENU_MIN_WIDTH = 180.dp
private val MENU_MAX_WIDTH = 300.dp
private val MENU_MAX_HEIGHT = 420.dp
private val MENU_ITEM_HEIGHT = 48.dp
private const val MENU_APPEAR_MS = 140
private const val MENU_APPEAR_SCALE = 0.94f
private const val MENU_EDGE_ALPHA = 0.08f
private const val MENU_DISABLED_ALPHA = 0.45f
private val MARK_SIZE = 12.dp
private val MARK_STROKE = 2.dp
