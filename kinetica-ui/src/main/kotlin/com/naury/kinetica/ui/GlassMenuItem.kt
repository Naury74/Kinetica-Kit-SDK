package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeoutOrNull

/** [glassContextMenu]의 항목. 삭제처럼 되돌리기 어려운 동작은 [destructive]를 true로 두면 빨간색으로 표시한다. */
@Immutable
public data class GlassMenuItem(val label: String, val icon: ImageVector? = null, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * 길게 누르면 iOS 컨텍스트 메뉴처럼 열리는 글래스 메뉴를 붙인다. 햅틱과 함께 이 요소가 살짝 떠오르고 배경이 흐려지며,
 * 요소 아래에 메뉴가 뜬다. 아래에 공간이 없으면 위에 뜬다. 메뉴 바깥을 누르거나 뒤로 가기를 하면 닫히고,
 * 항목을 누르면 메뉴를 닫은 뒤 그 항목의 동작을 실행한다. 짧은 탭과 스크롤은 요소가 그대로 받는다.
 * TalkBack에서는 같은 항목이 요소의 사용자 지정 동작으로 노출된다. [GlassScaffold] 안에서 쓴다.
 */
@Composable
public fun Modifier.glassContextMenu(items: List<GlassMenuItem>, style: GlassStyle = LocalGlassStyle.current): Modifier {
    var open by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val layer = rememberGraphicsLayer()
    val haptic = LocalHapticFeedback.current
    val latestItems by rememberUpdatedState(items)
    GlassOverlay(open) { transition ->
        val progress by transition.animateFloat(
            transitionSpec = { if (targetState) spring(dampingRatio = MENU_DAMPING, stiffness = MENU_STIFFNESS) else tween(CLOSE_MS) },
            label = "glass-menu",
        ) { if (it) 1f else 0f }
        androidx.activity.compose.BackHandler(enabled = open) { open = false }
        ContextMenuLayer(bounds, progress, style, latestItems, { open = false }) { drawLayer(layer) }
    }
    return this
        .onGloballyPositioned { bounds = it.boundsInRoot() }
        .drawWithContent {
            // 메뉴가 열렸을 때 오버레이 레이어에 이 요소를 똑같이 다시 그리려고 그리기 내용을 기록해 둔다.
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
        .semantics {
            customActions = items.map { item ->
                CustomAccessibilityAction(item.label) {
                    item.onClick()
                    true
                }
            }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // 길게 누르기 시간 안에 손을 떼거나 움직이면 일반 탭이나 스크롤로 넘긴다.
                val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var holding = true
                    while (holding) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                        holding =
                            change != null && change.pressed &&
                            (change.position - down.position).getDistance() <= viewConfiguration.touchSlop
                    }
                    true
                }
                if (released == null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    open = true
                    // 손을 뗄 때까지 이벤트를 소비해서, 요소가 이를 탭으로 처리하지 않게 한다.
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
        }
}

@Composable
private fun ContextMenuLayer(
    bounds: Rect,
    progress: Float,
    style: GlassStyle,
    items: List<GlassMenuItem>,
    close: () -> Unit,
    drawTarget: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    val level = progress.coerceIn(0f, 1f)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Scrim(level, close)
        // 떠오른 요소는 원래 위치에서 살짝 커진다.
        Canvas(
            Modifier
                .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                .graphicsLayer {
                    val scale = 1f + LIFT_SCALE * progress
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = LIFT_ELEVATION.toPx() * level
                    shape = RoundedCornerShape(LIFT_CORNER)
                    clip = false
                },
            onDraw = drawTarget,
        )
        val menuWidthPx = with(density) { MENU_WIDTH.toPx() }
        val screenWidth = constraints.maxWidth.toFloat()
        val below = bounds.bottom + with(density) { MENU_GAP.toPx() }
        val estimatedHeight = with(density) { (ITEM_HEIGHT * items.size + MENU_PADDING * 2).toPx() }
        val top = if (below + estimatedHeight <=
            constraints.maxHeight
        ) {
            below
        } else {
            bounds.top - with(density) { MENU_GAP.toPx() } - estimatedHeight
        }
        val left = bounds.left.coerceIn(
            with(density) {
                EDGE.toPx()
            },
            (screenWidth - menuWidthPx - with(density) { EDGE.toPx() }).coerceAtLeast(0f),
        )
        Column(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.coerceAtLeast(0f).roundToInt()) }
                .width(MENU_WIDTH)
                .graphicsLayer {
                    val scale = MENU_START_SCALE + (1f - MENU_START_SCALE) * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = level
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(MENU_ORIGIN_X, if (top >= bounds.bottom) 0f else 1f)
                }
                .glass(style.forPopup(), RoundedCornerShape(MENU_CORNER))
                .padding(vertical = MENU_PADDING),
        ) {
            items.forEach { item ->
                MenuRow(item, style) {
                    close()
                    item.onClick()
                }
            }
        }
    }
}

@Composable
private fun MenuRow(item: GlassMenuItem, style: GlassStyle, onClick: () -> Unit) {
    val color = if (item.destructive) Color(DESTRUCTIVE) else style.onGlass
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(item.label, Modifier.weight(1f), style = TextStyle(color = color, fontSize = 16.sp))
        item.icon?.let { Image(rememberVectorPainter(it), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(color)) }
    }
}

private const val MENU_DAMPING = 0.75f
private const val MENU_STIFFNESS = 500f
private const val CLOSE_MS = 160
private const val LIFT_SCALE = 0.04f
private val LIFT_ELEVATION = 24.dp
private val LIFT_CORNER = 20.dp
private val MENU_WIDTH = 240.dp
private val MENU_GAP = 12.dp
private val MENU_PADDING = 6.dp
private val MENU_CORNER = 22.dp
private val ITEM_HEIGHT = 46.dp
private val EDGE = 16.dp
private const val MENU_START_SCALE = 0.7f
private const val DESTRUCTIVE = 0xFFFF3B30
private const val MENU_ORIGIN_X = 0.1f
