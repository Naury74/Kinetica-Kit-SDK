package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * 제목, 설명, 동작 버튼이 있는 리치 툴팁을 이 요소 위나 아래에 띄운다. 새 기능을 처음 알려 주거나 아이콘 버튼의 뜻을 길게
 * 설명할 때 쓴다. [kTooltip]과 달리 저절로 닫히지 않고, 바깥을 누르거나 뒤로 가기를 하거나 동작 버튼을 누르면 [onDismiss]를 부른다.
 * 원하는 쪽에 공간이 모자라면 반대쪽에 띄우고, 화면 가장자리와 시스템 바를 피한다. 너비는 최대 320dp다.
 *
 * TalkBack은 나타날 때 제목과 설명을 읽는다. [KineticaScaffold] 안에서는 오버레이 레이어에, 밖에서는 팝업 창에 그린다.
 *
 * ```kotlin
 * KIconButton(
 *     FilterIcon, "필터", {},
 *     Modifier.kRichTooltip(hint, { hint = false }, "기간과 장소로 사진을 걸러 볼 수 있어요.", title = "새 필터", actionLabel = "알겠어요"),
 * )
 * ```
 *
 * @param text 설명 글
 * @param title 굵은 제목. null이면 설명만 보인다.
 * @param actionLabel 끝 쪽 글자 버튼. null이면 버튼이 없다. 누르면 [onAction]을 부른 뒤 [onDismiss]를 부른다.
 */
@Composable
public fun Modifier.kRichTooltip(
    visible: Boolean,
    onDismiss: () -> Unit,
    text: String,
    title: String? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    placement: KTooltipPlacement = KTooltipPlacement.Auto,
    surface: SurfaceStyle = KineticaTheme.surface,
): Modifier {
    val anchor = remember { PopupAnchor() }
    val dismiss by rememberUpdatedState(onDismiss)
    val action by rememberUpdatedState(onAction)
    val spec = RichTooltipSpec(title, text, actionLabel, placement, surface)
    val hosted = LocalKineticaHost.current != null
    KOverlay(visible, OverlayKind.InPlace) { transition ->
        BackHandler(enabled = transition.targetState) { dismiss() }
        val layer: @Composable () -> Unit = {
            RichLayer(spec, anchor.bounds, transition, { dismiss() }) {
                action()
                dismiss()
            }
        }
        if (hosted) {
            layer()
        } else {
            Popup(popupPositionProvider = TopLeftOfWindow, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
                layer()
            }
        }
    }
    return onGloballyPositioned { anchor.bounds = Rect(it.positionOnScreen(), it.size.toSize()) }
}

private class RichTooltipSpec(
    val title: String?,
    val text: String,
    val actionLabel: String?,
    val placement: KTooltipPlacement,
    val surface: SurfaceStyle,
)

/** 배치 단계에서 정한 말풍선 위치(이 레이어 좌표, px)와 방향 */
@Stable
private class RichGeometry {
    var bounds by mutableStateOf(Rect.Zero)
    var above by mutableStateOf(true)
}

@Composable
private fun RichLayer(spec: RichTooltipSpec, anchor: Rect, transition: Transition<Boolean>, onDismiss: () -> Unit, onAction: () -> Unit) {
    val still = reducedMotion()
    val progress by transition.animateFloat(
        transitionSpec = {
            when {
                still -> tween(0)
                targetState -> spring(dampingRatio = POP_DAMPING, stiffness = POP_STIFFNESS)
                else -> tween(HIDE_MS)
            }
        },
        label = "rich-tooltip",
    ) { if (it) 1f else 0f }
    val geometry = remember { RichGeometry() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val safe = WindowInsets.safeDrawing
    val latestDismiss by rememberUpdatedState(onDismiss)
    Layout(
        content = { RichBubble(spec, geometry, { progress }, onDismiss, onAction) },
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionOnScreen() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    // 말풍선 안을 누르면 동작 버튼이 받도록 그대로 두고, 바깥만 막고 닫는다.
                    if (!geometry.bounds.contains(down.position)) {
                        down.consume()
                        latestDismiss()
                    }
                }
            },
    ) { measurables, constraints ->
        val margin = EDGE_MARGIN.roundToPx()
        val insets = safe.toIntRect(this)
        val maxWidth = (constraints.maxWidth - insets.left - insets.right - margin * 2).coerceIn(0, MAX_WIDTH.roundToPx())
        val bubble = measurables.first().measure(Constraints(maxWidth = maxWidth))
        val placed = placeTooltip(
            anchor.translate(-origin),
            IntSize(constraints.maxWidth, constraints.maxHeight),
            IntSize(bubble.width, bubble.height),
            spec.placement,
            TooltipSpacing(margin.toFloat(), GAP.toPx(), 0f, insets),
        )
        geometry.above = placed.above
        geometry.bounds =
            Rect(placed.x.toFloat(), placed.y.toFloat(), (placed.x + bubble.width).toFloat(), (placed.y + bubble.height).toFloat())
        layout(constraints.maxWidth, constraints.maxHeight) { bubble.place(placed.x, placed.y) }
    }
}

@Composable
private fun RichBubble(spec: RichTooltipSpec, geometry: RichGeometry, progress: () -> Float, onDismiss: () -> Unit, onAction: () -> Unit) {
    val surface = spec.surface
    Column(
        Modifier
            .graphicsLayer {
                val p = progress()
                val scale = START_SCALE + (1f - START_SCALE) * p
                scaleX = scale
                scaleY = scale
                alpha = p.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(CENTER, if (geometry.above) 1f else 0f)
            }
            .kSurface(surface.forPopup(), RichBubbleShape)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                dismiss {
                    onDismiss()
                    true
                }
            }
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = if (spec.actionLabel != null) 6.dp else 14.dp),
    ) {
        CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor, LocalGlassLayered provides true) {
            spec.title?.let {
                ThemedText(
                    it,
                    Modifier.padding(bottom = 4.dp).semantics { heading() },
                    style = TextStyle(color = surface.contentColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                )
            }
            ThemedText(
                spec.text,
                style = TextStyle(color = surface.contentColor.copy(alpha = TEXT_ALPHA), fontSize = 14.sp, lineHeight = 20.sp),
            )
            spec.actionLabel?.let {
                Box(Modifier.align(Alignment.End).padding(top = 6.dp)) {
                    KButton(it, onAction, variant = KVariant.Text, size = KButtonSize.Small, surface = surface)
                }
            }
        }
    }
}

/** 팝업 창을 부모 창의 왼쪽 위에 둔다. 말풍선 위치는 창 안에서 화면 좌표로 직접 계산한다. */
private object TopLeftOfWindow : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

private val RichBubbleShape = RoundedCornerShape(16.dp)
private val EDGE_MARGIN = 12.dp
private val MAX_WIDTH = 320.dp
private val GAP = 8.dp
private const val START_SCALE = 0.8f
private const val CENTER = 0.5f
private const val TEXT_ALPHA = 0.8f
private const val POP_DAMPING = 0.7f
private const val POP_STIFFNESS = 520f
private const val HIDE_MS = 120
