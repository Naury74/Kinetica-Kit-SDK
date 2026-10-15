package com.naury.kinetica.editor

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * 편집기 다이얼로그. 둥근 패널(모서리 24dp, 안쪽 여백 20dp)에 제목, 내용, 버튼 줄을 둔다.
 * 뒤로 가기와 바깥 누르기로 [onDismissRequest]를 부른다. 내용은 화면보다 길면 스크롤된다.
 */
@Composable
internal fun StudioDialog(
    onDismissRequest: () -> Unit,
    title: String,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalStudioTheme.current
    Dialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false)) {
        val shown = rememberAppear()
        Column(
            Modifier
                .padding(horizontal = DIALOG_MARGIN, vertical = DIALOG_MARGIN)
                .widthIn(max = DIALOG_MAX_WIDTH)
                .fillMaxWidth()
                .graphicsLayer {
                    val t = shown.value
                    alpha = t
                    scaleX = APPEAR_SCALE + (1f - APPEAR_SCALE) * t
                    scaleY = scaleX
                }
                .shadow(DIALOG_ELEVATION, DialogShape)
                .background(elevated(theme), DialogShape)
                .border(1.dp, theme.onPanel.copy(alpha = EDGE_ALPHA), DialogShape)
                .padding(top = DIALOG_PADDING, start = DIALOG_PADDING, end = DIALOG_PADDING, bottom = 12.dp),
        ) {
            Text(title, color = theme.onPanel, style = MaterialTheme.typography.titleLarge)
            Column(
                Modifier.padding(top = 16.dp).weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = buttons,
            )
        }
    }
}

/**
 * 버튼에 붙는 메뉴. 감싼 상자 아래에 열리고 아래 공간이 모자라면 위로 열린다. 화면 가장자리에서 8dp 안쪽으로 맞춘다.
 * 초점을 가져가므로 뒤로 가기나 바깥 누르기로 닫히고 키보드로 항목을 옮겨 다닐 수 있다.
 */
@Composable
internal fun StudioMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (!expanded) return
    val theme = LocalStudioTheme.current
    val density = LocalDensity.current
    val position = remember(density) { MenuPosition(density) }
    Popup(popupPositionProvider = position, onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
        val shown = rememberAppear()
        Column(
            Modifier
                .padding(MENU_SHADOW_ROOM)
                .graphicsLayer {
                    val t = shown.value
                    alpha = t
                    scaleX = APPEAR_SCALE + (1f - APPEAR_SCALE) * t
                    scaleY = scaleX
                    transformOrigin = MenuOrigin
                }
                .shadow(MENU_ELEVATION, MenuShape)
                .background(elevated(theme), MenuShape)
                .border(1.dp, theme.onPanel.copy(alpha = EDGE_ALPHA), MenuShape)
                .widthIn(min = MENU_MIN_WIDTH, max = MENU_MAX_WIDTH)
                .verticalScroll(rememberScrollState())
                .padding(MENU_INSET),
            content = content,
        )
    }
}

/** [StudioMenu]의 항목. 눌림 표시는 메뉴 모서리와 동심원인 둥근 사각형이다. */
@Composable
internal fun StudioMenuItem(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val theme = LocalStudioTheme.current
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MENU_ITEM_HEIGHT)
            .clickable(
                remember {
                    MutableInteractionSource()
                },
                studioIndication(MenuItemShape),
                enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            color = theme.onPanel.copy(alpha = if (enabled) 1f else DISABLED_TEXT_ALPHA),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** 패널보다 한 단계 떠 있는 표면 색. 다크 테마에서는 조금 밝게 해 배경과 구분한다. */
internal fun elevated(theme: StudioTheme): Color =
    if (theme.isDark) theme.onPanel.copy(alpha = DARK_LIFT).compositeOver(theme.panel) else theme.panel

@Composable
private fun rememberAppear(): Animatable<Float, *> {
    val reduced = !ValueAnimator.areAnimatorsEnabled()
    val shown = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(APPEAR_MS)) }
    return shown
}

// 시작 가장자리를 버튼에 맞추고, 아래가 모자라면 위로 올린다.
private class MenuPosition(density: Density) : PopupPositionProvider {
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
        val start = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left - room else anchorBounds.right - width + room
        val x = start.coerceIn(margin - room, (windowSize.width - width - margin + room).coerceAtLeast(margin - room))
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

// 메뉴는 위 가운데에서 자라 나온다.
private val MenuOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0f)
private val DialogShape = RoundedCornerShape(24.dp)
private val MenuShape = RoundedCornerShape(14.dp)

// 메뉴 모서리 14dp에서 안쪽 여백 6dp를 뺀 값.
private val MenuItemShape = RoundedCornerShape(8.dp)
private val DIALOG_MARGIN = 24.dp
private val DIALOG_PADDING = 20.dp
private val DIALOG_MAX_WIDTH = 520.dp
private val DIALOG_ELEVATION = 24.dp
private val MENU_ELEVATION = 12.dp
private val MENU_INSET = 6.dp
private val MENU_GAP = 4.dp
private val MENU_SCREEN_MARGIN = 8.dp
private val MENU_SHADOW_ROOM = 12.dp
private val MENU_MIN_WIDTH = 168.dp
private val MENU_MAX_WIDTH = 300.dp
private val MENU_ITEM_HEIGHT = 48.dp
private const val APPEAR_SCALE = 0.94f
private const val APPEAR_MS = 140
private const val EDGE_ALPHA = 0.1f
private const val DARK_LIFT = 0.06f
private const val DISABLED_TEXT_ALPHA = 0.4f
