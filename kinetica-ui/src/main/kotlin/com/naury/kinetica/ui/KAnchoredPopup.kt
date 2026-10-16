package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/** 앵커 요소의 화면 좌표 경계(px). 오버레이 레이어에 그릴 때 패널 위치를 정하는 데 쓴다. */
@Stable
internal class PopupAnchor {
    var bounds by mutableStateOf(Rect.Zero)
}

/** 크기 0인 요소를 놓고 부모 레이아웃의 화면 경계를 [anchor]에 기록한다. 드롭다운처럼 버튼과 같은 Box 안에서 부를 때 쓴다. */
@Composable
internal fun ParentAnchor(anchor: PopupAnchor) {
    Box(Modifier.onGloballyPositioned { anchor.bounds = (it.parentLayoutCoordinates ?: it).boundsOnScreen() })
}

/** 배치 단계에서 정한 패널 방향과 쓸 수 있는 높이. 패널 애니메이션 기준점과 최대 높이가 읽는다. */
@Stable
internal class AnchoredGeometry {
    var below by mutableStateOf(true)
    var alignRight by mutableStateOf(false)

    /** 팝업 창으로 띄울 때 다음 측정에 쓰는 최대 높이(px). 오버레이 레이어에서는 배치가 직접 제한한다. */
    var room by mutableIntStateOf(Int.MAX_VALUE)
}

/**
 * 앵커에 붙는 팝업의 고정 설정.
 *
 * @property focusable true면 키보드 초점과 뒤로 가기를 가져가고 바깥을 누르면 [onDismiss]를 부른다(메뉴). false면 뒤 화면 입력을 막지 않는다(자동 완성).
 * @property alignEnd true면 패널 끝 가장자리를 앵커 끝 가장자리에 맞춘다.
 */
internal class AnchoredFrame(val focusable: Boolean, val onDismiss: () -> Unit, val alignEnd: Boolean = false)

/**
 * [anchor] 옆에 패널을 띄운다. 아래에 공간이 있으면 아래, 없으면 위에 놓고(키보드가 올라와 있으면 키보드 위까지를 공간으로 본다),
 * 가로는 화면 밖으로 나가지 않게 밀어 넣는다. [KineticaScaffold] 안이면 오버레이 레이어에 그리고, 밖이면 팝업 창에 그린다.
 * 패널을 부른 자리의 부모 레이아웃이 앵커다.
 */
@Composable
internal fun AnchoredPopup(
    visible: Boolean,
    anchor: PopupAnchor,
    frame: AnchoredFrame,
    panel: @Composable (Transition<Boolean>, AnchoredGeometry) -> Unit,
) {
    if (visible) ParentAnchor(anchor)
    val hosted = LocalKineticaHost.current != null
    val kind = if (hosted && frame.focusable) OverlayKind.Modal else OverlayKind.InPlace
    KOverlay(visible, kind) { transition ->
        val geometry = remember { AnchoredGeometry() }
        if (hosted) {
            if (frame.focusable) BackHandler(enabled = transition.targetState, onBack = frame.onDismiss)
            HostedAnchoredLayer(anchor.bounds, frame, geometry) { panel(transition, geometry) }
        } else {
            WindowAnchoredPopup(frame, geometry) { panel(transition, geometry) }
        }
    }
}

@Composable
private fun HostedAnchoredLayer(anchor: Rect, frame: AnchoredFrame, geometry: AnchoredGeometry, panel: @Composable () -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val safe = WindowInsets.safeDrawing
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() }) {
        if (frame.focusable) OutsideCatcher(frame.onDismiss)
        Layout(content = panel, modifier = Modifier.matchParentSize()) { measurables, constraints ->
            val insets = safe.toIntRect(this)
            val margin = POPUP_EDGE.roundToPx()
            val area = IntRect(
                insets.left + margin,
                insets.top + margin,
                constraints.maxWidth - insets.right - margin,
                constraints.maxHeight - insets.bottom - margin,
            )
            val local = anchor.translate(-origin)
            val gap = POPUP_GAP.toPx()
            val placeable = measurables.first().measure(
                Constraints(maxWidth = area.width.coerceAtLeast(0), maxHeight = anchoredRoom(local, area, gap)),
            )
            val place = placeAnchored(local, area, IntSize(placeable.width, placeable.height), gap, rtl != frame.alignEnd)
            geometry.below = place.below
            geometry.alignRight = rtl != frame.alignEnd
            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(place.x, place.y) }
        }
    }
}

/** 팝업 창 하나에 패널만 담아 띄운다. 패널 크기의 창이라 뒤 화면의 다른 곳은 그대로 누를 수 있다. */
@Composable
private fun WindowAnchoredPopup(frame: AnchoredFrame, geometry: AnchoredGeometry, panel: @Composable () -> Unit) {
    val density = LocalDensity.current
    val room = with(density) { SHADOW_ROOM.roundToPx() }
    val margin = with(density) { POPUP_EDGE.roundToPx() }
    val gap = with(density) { POPUP_GAP.toPx() }
    val provider = remember(frame.alignEnd, room, margin, gap) { AnchoredPositionProvider(frame.alignEnd, room, margin, gap, geometry) }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = if (frame.focusable) frame.onDismiss else null,
        properties = PopupProperties(
            focusable = frame.focusable,
            dismissOnBackPress = frame.focusable,
            dismissOnClickOutside = frame.focusable,
            clippingEnabled = false,
        ),
    ) {
        val requester = remember { FocusRequester() }
        val inputMode = LocalInputModeManager.current
        if (frame.focusable) {
            LaunchedEffect(Unit) {
                // 첫 배치가 끝나야 초점 탐색이 항목 위치를 안다. 터치로 연 메뉴는 초점을 옮기지 않는다.
                withFrameNanos { }
                if (inputMode.inputMode == InputMode.Keyboard) requester.requestFocus(FocusDirection.Enter)
            }
        }
        // 그림자가 창 가장자리에서 잘리지 않게 둘레에 여유를 둔다. 위치 계산은 이 여유를 빼고 한다.
        val maxHeight = with(density) { geometry.room.coerceAtLeast(0).let { if (it == Int.MAX_VALUE) Dp.Infinity else it.toDp() } }
        Box(Modifier.padding(SHADOW_ROOM).heightIn(max = maxHeight).focusRequester(requester).focusGroup()) { panel() }
    }
}

/** 팝업 창 위치. 창이 보이는 영역(키보드 제외) 안에서 [placeAnchored]로 정하고, 그림자 여유 [room](px)만큼 바깥으로 옮긴다. */
internal class AnchoredPositionProvider(
    private val alignEnd: Boolean,
    private val room: Int,
    private val margin: Int,
    private val gap: Float,
    private val geometry: AnchoredGeometry,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val area = IntRect(margin, margin, windowSize.width - margin, windowSize.height - margin)
        val anchor = Rect(
            anchorBounds.left.toFloat(),
            anchorBounds.top.toFloat(),
            anchorBounds.right.toFloat(),
            anchorBounds.bottom.toFloat(),
        )
        val panel = IntSize((popupContentSize.width - room * 2).coerceAtLeast(0), (popupContentSize.height - room * 2).coerceAtLeast(0))
        val alignRight = (layoutDirection == LayoutDirection.Rtl) != alignEnd
        val place = placeAnchored(anchor, area, panel, gap, alignRight)
        geometry.below = place.below
        geometry.alignRight = alignRight
        geometry.room = anchoredRoom(anchor, area, gap)
        return IntOffset(place.x - room, place.y - room)
    }
}

/** 패널 왼쪽 위 위치(px)와 앵커 아래에 놓였는지. */
internal class AnchoredPosition(val x: Int, val y: Int, val below: Boolean)

/** 앵커 위와 아래 중 넓은 쪽의 높이(px). 패널은 이보다 높아지지 않는다. */
internal fun anchoredRoom(anchor: Rect, area: IntRect, gap: Float): Int {
    val below = area.bottom - (anchor.bottom + gap)
    val above = (anchor.top - gap) - area.top
    val room = maxOf(below, above, 0f)
    // 앵커가 화면 대부분을 차지해 위아래 어디에도 자리가 없으면 앵커 위에 겹쳐 띄우므로 화면 높이를 다 쓴다.
    return if (room < area.height * MIN_SIDE_ROOM) area.height else room.roundToInt()
}

/**
 * [anchor] 옆에 [panel] 크기의 패널을 [area] 안에 놓을 위치를 구한다. 아래에 들어가면 아래, 아니면 위, 둘 다 안 들어가면
 * 넓은 쪽에 놓는다. 가로는 [alignRight]면 패널 오른쪽 가장자리를 앵커 오른쪽에, 아니면 왼쪽끼리 맞추고 [area] 밖으로 나가지 않게 민다.
 */
internal fun placeAnchored(anchor: Rect, area: IntRect, panel: IntSize, gap: Float, alignRight: Boolean): AnchoredPosition {
    val roomBelow = area.bottom - (anchor.bottom + gap)
    val roomAbove = (anchor.top - gap) - area.top
    val height = panel.height.toFloat()
    val below = height <= roomBelow || (height > roomAbove && roomBelow >= roomAbove)
    val overlap = height > maxOf(roomBelow, roomAbove)
    val rawY = when {
        // 위아래 어디에도 들어가지 않으면 앵커 가운데에 겹쳐 놓는다.
        overlap -> anchor.center.y - height / 2

        below -> anchor.bottom + gap

        else -> anchor.top - gap - height
    }
    val y = rawY.coerceIn(area.top.toFloat(), maxOf(area.top.toFloat(), area.bottom - height))
    val rawX = if (alignRight) anchor.right - panel.width else anchor.left
    val x = rawX.coerceIn(area.left.toFloat(), maxOf(area.left.toFloat(), (area.right - panel.width).toFloat()))
    return AnchoredPosition(x.roundToInt(), y.roundToInt(), below)
}

/**
 * 메뉴 바깥을 누르면 닫는 투명한 판. 누르기 시작하는 순간 닫아 뒤 화면 요소가 눌리지 않게 한다.
 * 키보드 초점은 받지 않고, TalkBack에는 '닫기' 동작으로 남는다.
 */
@Composable
private fun OutsideCatcher(onDismiss: () -> Unit) {
    val label = stringResource(R.string.kinetica_ui_close)
    Box(
        Modifier
            .fillMaxSize()
            .focusProperties { canFocus = false }
            .pointerInput(onDismiss) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    onDismiss()
                }
            }
            .semantics {
                contentDescription = label
                onClick(label) {
                    onDismiss()
                    true
                }
            },
    )
}

/**
 * 앵커 팝업의 판. [AnchoredGeometry]가 정한 쪽(앵커 아래면 위 가장자리)에서 커지며 나타난다. 안쪽 글자 색은 [surface]를 따른다.
 */
@Composable
internal fun PopupPanel(
    transition: Transition<Boolean>,
    geometry: AnchoredGeometry,
    surface: SurfaceStyle,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val still = reducedMotion()
    val progress by transition.animateFloat(
        transitionSpec = {
            when {
                still -> tween(0)
                targetState -> spring(dampingRatio = PANEL_DAMPING, stiffness = PANEL_STIFFNESS)
                else -> tween(PANEL_CLOSE_MS)
            }
        },
        label = "popup-panel",
    ) { if (it) 1f else 0f }
    Column(
        modifier
            .graphicsLayer {
                val scale = PANEL_START_SCALE + (1f - PANEL_START_SCALE) * progress
                scaleX = scale
                scaleY = scale
                alpha = progress.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(if (geometry.alignRight) 1f else 0f, if (geometry.below) 0f else 1f)
            }
            .kSurface(surface.forPopup(), PopupPanelShape)
            .padding(vertical = PopupPanelPadding),
    ) {
        CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor, LocalGlassLayered provides true) { content() }
    }
}

/** 팝업 판 모서리 */
internal val PopupPanelCorner: Dp = 18.dp
internal val PopupPanelShape = RoundedCornerShape(PopupPanelCorner)

/** 팝업 판 위아래 여백. 항목 눌림 표시 모서리는 이만큼 작은 반지름을 쓴다. */
internal val PopupPanelPadding: Dp = 6.dp

private val POPUP_EDGE = 12.dp
private const val MIN_SIDE_ROOM = 0.3f
private val POPUP_GAP = 6.dp
private val SHADOW_ROOM = 16.dp
private const val PANEL_START_SCALE = 0.86f
private const val PANEL_DAMPING = 0.8f
private const val PANEL_STIFFNESS = 700f
private const val PANEL_CLOSE_MS = 120
