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
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeoutOrNull

/** [kContextMenu]의 항목. 삭제처럼 되돌리기 어려운 동작은 [destructive]를 true로 두면 빨간색으로 표시한다. */
@Immutable
public data class KMenuItem(val label: String, val icon: ImageVector? = null, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * 길게 누르면 열리는 컨텍스트 메뉴를 붙인다. 햅틱과 함께 이 요소가 살짝 떠오르고 배경이 흐려지며,
 * 요소 아래에 메뉴가 뜬다. 아래에 공간이 없으면 위에 뜬다. 메뉴 바깥을 누르거나 뒤로 가기를 하면 닫히고,
 * 항목을 누르면 메뉴를 닫은 뒤 그 항목의 동작을 실행한다. 짧은 탭과 스크롤은 요소가 그대로 받는다.
 * TalkBack에서는 같은 항목이 요소의 사용자 지정 동작으로 노출된다. [KineticaScaffold] 안에서 쓴다.
 */
@Composable
public fun Modifier.kContextMenu(items: List<KMenuItem>, surface: SurfaceStyle = KineticaTheme.surface): Modifier {
    var open by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val layer = rememberGraphicsLayer()
    val haptic = LocalHapticFeedback.current
    val latestItems by rememberUpdatedState(items)
    KOverlay(open) { transition ->
        val progress by transition.animateFloat(
            transitionSpec = { if (targetState) spring(dampingRatio = MENU_DAMPING, stiffness = MENU_STIFFNESS) else tween(CLOSE_MS) },
            label = "glass-menu",
        ) { if (it) 1f else 0f }
        androidx.activity.compose.BackHandler(enabled = open) { open = false }
        ContextMenuLayer(bounds, progress, transition.targetState, surface, latestItems, { open = false }) { drawLayer(layer) }
    }
    return this
        .onGloballyPositioned { bounds = it.boundsOnScreen() }
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
            }.fitted()
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
    rootBounds: Rect,
    progress: Float,
    active: Boolean,
    surface: SurfaceStyle,
    items: List<KMenuItem>,
    close: () -> Unit,
    drawTarget: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    val level = progress.coerceIn(0f, 1f)
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // 오버레이는 다른 창(대화 상자)이거나 화면 (0,0)에 있지 않을 수 있으므로 화면 좌표를 이 상자 좌표로 옮긴다.
    var origin by remember { mutableStateOf(Offset.Zero) }
    var menuHeight by remember { mutableIntStateOf(0) }
    val panel = shapeFor { menu }
    val liftShape = shapeFor { card }
    val bounds = rootBounds.translate(-origin)
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() }) {
        Scrim(level, close)
        // 떠오른 요소는 원래 위치에서 살짝 커진다. RTL에서도 물리 좌표 그대로 놓는다.
        Canvas(
            Modifier
                .absoluteOffset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                .graphicsLayer {
                    val scale = 1f + LIFT_SCALE * progress
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = LIFT_ELEVATION.toPx() * level
                    shape = liftShape
                    clip = false
                },
            onDraw = drawTarget,
        )
        val place = with(density) {
            menuPlacement(bounds, rtl, Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()), menuHeight, items.size)
        }
        Column(
            Modifier
                .absoluteOffset { IntOffset(place.left.roundToInt(), place.top.roundToInt()) }
                .width(MENU_WIDTH)
                .heightIn(max = with(density) { (constraints.maxHeight - EDGE.toPx() * 2).coerceAtLeast(0f).toDp() })
                .onSizeChanged { menuHeight = it.height }
                .graphicsLayer {
                    val scale = MENU_START_SCALE + (1f - MENU_START_SCALE) * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = level
                    transformOrigin = TransformOrigin(if (rtl) 1f - MENU_ORIGIN_X else MENU_ORIGIN_X, if (place.below) 0f else 1f)
                }
                .kSurface(surface.forPopup(), panel)
                .verticalScroll(rememberScrollState())
                .padding(vertical = MENU_PADDING),
        ) {
            items.forEach { item ->
                MenuRow(item, surface) {
                    // 닫히는 애니메이션 동안에는 항목이 다시 눌려 동작이 두 번 실행되지 않게 한다.
                    if (active) {
                        close()
                        item.onClick()
                    }
                }
            }
        }
    }
}

private class MenuPlace(val left: Float, val top: Float, val below: Boolean)

/**
 * 메뉴 위치. 요소 아래에 들어가면 아래, 아니면 위, 둘 다 안 되면 화면 안에 들어가도록 맞춘다. RTL이면 요소의 끝(오른쪽)에 맞춘다.
 * [measured]가 0이면(첫 프레임) 항목 수로 높이를 어림한다.
 */
private fun androidx.compose.ui.unit.Density.menuPlacement(
    bounds: Rect,
    rtl: Boolean,
    screen: Size,
    measured: Int,
    count: Int,
): MenuPlace {
    val width = screen.width
    val height = screen.height
    val menuWidth = MENU_WIDTH.toPx()
    val gap = MENU_GAP.toPx()
    val edge = EDGE.toPx()
    val menuHeight = if (measured > 0) measured.toFloat() else (ITEM_HEIGHT * count + MENU_PADDING * 2).toPx()
    val below = bounds.bottom + gap
    val above = bounds.top - gap - menuHeight
    val top = when {
        below + menuHeight <= height - edge -> below
        above >= edge -> above
        else -> (height - edge - menuHeight).coerceAtLeast(edge)
    }
    val start = if (rtl) bounds.right - menuWidth else bounds.left
    val left = start.coerceIn(edge, (width - menuWidth - edge).coerceAtLeast(edge))
    return MenuPlace(left, top, top >= bounds.bottom)
}

@Composable
private fun MenuRow(item: KMenuItem, surface: SurfaceStyle, onClick: () -> Unit) {
    val color = if (item.destructive) KineticaTheme.colors.destructive else surface.contentColor
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MENU_PADDING)
            .heightIn(min = KMetrics.MinTouch)
            .kClickable(ITEM_SHAPE, onClick = onClick)
            .padding(horizontal = ITEM_PADDING, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemedText(item.label, Modifier.weight(1f), style = TextStyle(color = color, fontSize = 16.sp))
        item.icon?.let {
            Image(rememberVectorPainter(it), null, Modifier.size(KMetrics.ButtonIcon), colorFilter = ColorFilter.tint(color))
        }
    }
}

private const val MENU_DAMPING = 0.75f
private const val MENU_STIFFNESS = 500f
private const val CLOSE_MS = 160
private const val LIFT_SCALE = 0.04f
private val LIFT_ELEVATION = 24.dp
private val MENU_WIDTH = 240.dp
private val MENU_GAP = 12.dp
private val MENU_PADDING = 6.dp
private val ITEM_HEIGHT = 46.dp
private val ITEM_PADDING = 12.dp

// 항목 눌림 표시는 메뉴 모서리와 같은 중심을 쓰도록 메뉴 안쪽 여백만큼 작은 반지름을 쓴다.
private val ITEM_SHAPE: Shape
    @Composable
    @ReadOnlyComposable
    get() = currentShapes.inner(currentShapes.menu, MENU_PADDING)
private val EDGE = 16.dp
private const val MENU_START_SCALE = 0.7f
private const val MENU_ORIGIN_X = 0.1f
