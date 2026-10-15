package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** 툴팁을 요소의 위와 아래 중 어디에 띄울지 정한다. 원하는 쪽에 공간이 모자라면 반대쪽에 띄운다. */
public enum class TooltipPlacement {
    /** 위에 공간이 있으면 위, 없으면 아래에 띄운다. */
    Auto,

    /** 요소 위에 띄운다. */
    Above,

    /** 요소 아래에 띄운다. */
    Below,
}

/** [Modifier.tooltipOnLongPress]의 표시 상태. [rememberTooltipState]로 만든다. */
@Stable
public class TooltipState internal constructor() {
    /** 툴팁이 보이는 중인지 나타낸다. */
    public var isVisible: Boolean by mutableStateOf(false)
        private set

    /** 툴팁을 띄운다. 이미 떠 있으면 자동으로 닫히는 시간을 다시 센다. */
    public fun show() {
        isVisible = true
        showCount++
    }

    /** 툴팁을 닫는다. */
    public fun dismiss() {
        isVisible = false
    }

    internal var showCount by mutableIntStateOf(0)
        private set
}

/** 컴포지션에 유지되는 [TooltipState]를 만든다. */
@Composable
public fun rememberTooltipState(): TooltipState = remember { TooltipState() }

/**
 * 이 요소 위나 아래에 화살표가 달린 글래스 말풍선을 띄운다. 말풍선은 화살표 끝에서 톡 튀어나오듯 커지고, 화면 가장자리에 닿으면
 * 안쪽으로 밀려나도 화살표는 계속 요소의 가운데를 가리킨다. 바깥이나 말풍선을 누르거나 뒤로 가기를 하면 [onDismiss]를 호출한다.
 * 떠 있는 동안 아래 화면의 터치는 막는다.
 *
 * [GlassScaffold] 안에서는 오버레이 레이어에 그려 배경이 블러 처리되어 비치고, 밖에서는 별도의 팝업 창에 그린다.
 * 팝업 창을 쓸 때는 보이는 동안 크기가 0인 레이아웃 하나가 이 요소 앞에 끼어든다. 간격을 주는 `Arrangement.spacedBy` 안에서는
 * 간격이 하나 더 생길 수 있으므로 그때는 [GlassScaffold] 안에서 쓴다.
 *
 * ```kotlin
 * var hint by remember { mutableStateOf(true) }
 * IconButton(Modifier.glassTooltip("여기서 필터를 바꿀 수 있어요", hint, onDismiss = { hint = false })) { ... }
 * ```
 *
 * @param text 말풍선 문구. TalkBack이 나타날 때 읽는다.
 * @param visible 보일지 여부. 바뀌면 나타남과 사라짐 애니메이션을 재생한다.
 */
@Composable
public fun Modifier.glassTooltip(
    text: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    placement: TooltipPlacement = TooltipPlacement.Auto,
    style: GlassStyle = LocalGlassStyle.current,
): Modifier {
    val anchor = remember { AnchorBounds() }
    val dismiss by rememberUpdatedState(onDismiss)
    TooltipLayer(TooltipSpec(text, placement, style, anchor) { dismiss() }, visible)
    return onGloballyPositioned { anchor.bounds = Rect(it.positionOnScreen(), it.size.toSize()) }
}

/**
 * 길게 누르면 [text] 툴팁을 띄운다. 요소의 탭 동작은 그대로 두고, 길게 누른 뒤 손을 뗄 때만 탭으로 처리되지 않게 막는다.
 * 툴팁은 2.5초 뒤 저절로 닫히며, 접근성 설정의 '조치를 취할 시간'이 더 길면 그 시간을 따른다.
 * TalkBack에서는 두 번 탭한 채 누르기로 띄울 수 있다.
 */
@Composable
public fun Modifier.tooltipOnLongPress(
    text: String,
    state: TooltipState = rememberTooltipState(),
    placement: TooltipPlacement = TooltipPlacement.Auto,
    style: GlassStyle = LocalGlassStyle.current,
): Modifier {
    val adjust = rememberAccessibleTimeout(withControls = false)
    LaunchedEffect(state, state.isVisible, state.showCount) {
        if (state.isVisible) {
            delay(adjust(AUTO_HIDE_MS))
            state.dismiss()
        }
    }
    val label = stringResource(R.string.kinetica_ui_tooltip_show)
    return this
        .semantics {
            onLongClick(label) {
                state.show()
                true
            }
        }
        .pointerInput(state) { watchLongPress(state::show) }
        .glassTooltip(text, state.isVisible, state::dismiss, placement, style)
}

/** 아래 요소의 터치를 가로채지 않고 길게 누르기만 알아챈다. 길게 누른 뒤의 이벤트는 소비해 손을 뗄 때 탭이 되지 않게 한다. */
private suspend fun PointerInputScope.watchLongPress(onLongPress: () -> Unit) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val slop = viewConfiguration.touchSlop
    val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        var change: PointerInputChange? = down
        while (change != null && change.pressed && (change.position - down.position).getDistance() <= slop) {
            change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
        }
        true
    }
    if (ended != null) return@awaitEachGesture
    onLongPress()
    do {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach { it.consume() }
    } while (event.changes.any { it.pressed })
}

/** 요소의 화면 좌표 경계(px). */
@Stable
private class AnchorBounds {
    var bounds by mutableStateOf(Rect.Zero)
}

private class TooltipSpec(
    val text: String,
    val placement: TooltipPlacement,
    val style: GlassStyle,
    val anchor: AnchorBounds,
    val onDismiss: () -> Unit,
)

@Composable
private fun TooltipLayer(spec: TooltipSpec, visible: Boolean) {
    val hosted = LocalGlassHost.current != null
    GlassOverlay(visible) { transition ->
        if (hosted) {
            BubbleLayer(spec, transition)
        } else {
            Popup(popupPositionProvider = WindowOrigin, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
                BubbleLayer(spec, transition)
            }
        }
    }
}

/** 팝업 창을 부모 창의 왼쪽 위에 둔다. 말풍선 위치는 창 안에서 화면 좌표로 직접 계산한다. */
private object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): androidx.compose.ui.unit.IntOffset = androidx.compose.ui.unit.IntOffset.Zero
}

@Composable
private fun BubbleLayer(spec: TooltipSpec, transition: Transition<Boolean>) {
    val progress by transition.animateFloat(
        transitionSpec = { if (targetState) spring(dampingRatio = POP_DAMPING, stiffness = POP_STIFFNESS) else tween(HIDE_MS) },
        label = "glass-tooltip",
    ) { if (it) 1f else 0f }
    BackHandler(enabled = transition.targetState, onBack = spec.onDismiss)
    val geometry = remember { BubbleGeometry() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Layout(
        content = { Bubble(spec, geometry) { progress } },
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionOnScreen() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial).consume()
                    spec.onDismiss()
                }
            },
    ) { measurables, constraints ->
        val margin = EDGE_MARGIN.roundToPx()
        val maxWidth = (constraints.maxWidth - margin * 2).coerceIn(0, MAX_WIDTH.roundToPx())
        val bubble = measurables.first().measure(Constraints(maxWidth = maxWidth))
        val spacing = TooltipSpacing(margin.toFloat(), GAP.toPx(), (CORNER + ARROW_HALF).toPx())
        val placed = placeTooltip(
            spec.anchor.bounds.translate(-origin),
            IntSize(constraints.maxWidth, constraints.maxHeight),
            IntSize(bubble.width, bubble.height),
            spec.placement,
            spacing,
        )
        geometry.above = placed.above
        geometry.arrowX = placed.arrowX
        layout(constraints.maxWidth, constraints.maxHeight) { bubble.place(placed.x, placed.y) }
    }
}

@Composable
private fun Bubble(spec: TooltipSpec, geometry: BubbleGeometry, progress: () -> Float) {
    val shape = remember(geometry) { BubbleShape(geometry) }
    Box(
        Modifier
            .graphicsLayer {
                val p = progress()
                val scale = START_SCALE + (1f - START_SCALE) * p
                scaleX = scale
                scaleY = scale
                alpha = p.coerceIn(0f, 1f)
                // 화살표 끝을 기준으로 커져야 요소에서 튀어나오는 것처럼 보인다.
                transformOrigin = TransformOrigin(geometry.arrowX / size.width.coerceAtLeast(1f), if (geometry.above) 1f else 0f)
            }
            .glass(spec.style.forPopup(), shape)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                dismiss {
                    spec.onDismiss()
                    true
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp + ARROW_HEIGHT),
    ) {
        BasicText(spec.text, style = TextStyle(color = spec.style.onGlass, fontSize = 14.sp, lineHeight = 19.sp))
    }
}

/** 배치 단계에서 정한 화살표 방향과 위치. 모양과 확대 기준점이 그리기 단계에서 읽는다. */
@Stable
private class BubbleGeometry {
    var above by mutableStateOf(true)
    var arrowX by mutableFloatStateOf(0f)
}

/**
 * 둥근 사각형에 화살표를 붙인 모양. 위아래에 화살표 높이만큼 빈 띠를 두고, 요소 쪽 띠에 화살표를 그린다.
 * 화살표가 경계 안에 있어야 글래스의 색 덮기와 대체 색이 화살표까지 칠해진다. Path는 크기나 화살표 위치가 바뀔 때만 다시 만든다.
 */
private class BubbleShape(private val geometry: BubbleGeometry) : Shape {
    private val body = Path()
    private val arrow = Path()
    private val result = Path()
    private var key: Triple<Size, Float, Boolean>? = null

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val above = geometry.above
        val x = geometry.arrowX
        val next = Triple(size, x, above)
        if (next != key) {
            key = next
            rebuild(size, x, above, density)
        }
        return Outline.Generic(result)
    }

    private fun rebuild(size: Size, x: Float, above: Boolean, density: Density) {
        val half = with(density) { ARROW_HALF.toPx() }
        val height = with(density) { ARROW_HEIGHT.toPx() }
        val corner = with(density) { CORNER.toPx() }
        body.rewind()
        body.addRoundRect(RoundRect(0f, height, size.width, size.height - height, corner, corner))
        // 밑변을 몸통 안쪽으로 조금 넣어 합칠 때 이음매가 보이지 않게 한다.
        val base = if (above) size.height - height - 1f else height + 1f
        val tip = if (above) size.height else 0f
        val round = height * TIP_ROUND
        val toward = if (above) -round else round
        arrow.rewind()
        arrow.moveTo(x - half, base)
        arrow.lineTo(x - half * TIP_ROUND, tip + toward)
        arrow.quadraticTo(x, tip, x + half * TIP_ROUND, tip + toward)
        arrow.lineTo(x + half, base)
        arrow.close()
        result.rewind()
        result.op(body, arrow, PathOperation.Union)
    }
}

/** 화면 가장자리 여백, 요소와 화살표 끝 사이 거리, 화살표가 모서리에 닿지 않게 둘 최소 거리(px). */
internal class TooltipSpacing(val margin: Float, val gap: Float, val arrowInset: Float)

/** 말풍선 왼쪽 위 위치(px), 요소 위에 놓였는지, 말풍선 왼쪽에서 화살표 끝까지 거리(px). */
internal class TooltipPosition(val x: Int, val y: Int, val above: Boolean, val arrowX: Float)

/**
 * [anchor] 요소 옆에 [bubble] 크기의 말풍선을 놓을 위치를 [container] 안에서 구한다. 원하는 쪽에 들어가지 않으면 반대쪽을 쓰고,
 * 가로는 화면 밖으로 나가지 않게 밀어 넣되 화살표는 요소 가운데를 가리키게 한다.
 */
internal fun placeTooltip(
    anchor: Rect,
    container: IntSize,
    bubble: IntSize,
    placement: TooltipPlacement,
    spacing: TooltipSpacing,
): TooltipPosition {
    val w = bubble.width.toFloat()
    val h = bubble.height.toFloat()
    val fitsAbove = anchor.top - spacing.gap - h >= spacing.margin
    val fitsBelow = anchor.bottom + spacing.gap + h <= container.height - spacing.margin
    val above = when (placement) {
        TooltipPlacement.Above -> fitsAbove || !fitsBelow
        TooltipPlacement.Below -> !fitsBelow && fitsAbove
        TooltipPlacement.Auto -> fitsAbove || (!fitsBelow && anchor.top > container.height - anchor.bottom)
    }
    val rawY = if (above) anchor.top - spacing.gap - h else anchor.bottom + spacing.gap
    val y = rawY.coerceIn(spacing.margin, (container.height - spacing.margin - h).coerceAtLeast(spacing.margin))
    val x = (anchor.center.x - w / 2f).coerceIn(spacing.margin, (container.width - spacing.margin - w).coerceAtLeast(spacing.margin))
    val arrowX = if (w <= spacing.arrowInset * 2) w / 2f else (anchor.center.x - x).coerceIn(spacing.arrowInset, w - spacing.arrowInset)
    return TooltipPosition(x.roundToInt(), y.roundToInt(), above, arrowX)
}

private const val AUTO_HIDE_MS = 2_500L
private const val POP_DAMPING = 0.55f
private const val POP_STIFFNESS = 520f
private const val HIDE_MS = 120
private const val START_SCALE = 0.5f
private const val TIP_ROUND = 0.22f
private val EDGE_MARGIN = 12.dp
private val MAX_WIDTH = 260.dp
private val ARROW_HEIGHT = 8.dp
private val ARROW_HALF = 9.dp
private val GAP = 4.dp
private val CORNER = 14.dp
