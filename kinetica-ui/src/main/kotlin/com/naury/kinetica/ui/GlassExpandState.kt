package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/**
 * 카드에서 상세 화면으로 커지며 이어지는 전환(App Store 오늘 탭 카드처럼)의 상태. [Modifier.expandSource]로 카드 자리를 알리고
 * [expand]로 연다. 열린 상세는 [GlassExpandHost]가 그린다.
 */
@Stable
public class GlassExpandState internal constructor() {
    internal val sources = mutableStateMapOf<Any, Rect>()

    /** 열려 있거나 열려 가는 카드의 열쇠. 닫는 중이면 `null`이다. */
    public var expandedKey: Any? by mutableStateOf(null)
        private set

    /** 화면에 그려지고 있는 카드의 열쇠(닫히는 움직임 포함). 이 카드의 원래 자리는 숨긴다. */
    public var activeKey: Any? by mutableStateOf(null)
        internal set

    /** [key] 카드를 연다. 그 카드에 [Modifier.expandSource]가 붙어 있지 않으면 화면 가운데에서 커진다. */
    public fun expand(key: Any) {
        expandedKey = key
        activeKey = key
    }

    /** 열린 카드를 닫는다. 카드 자리로 줄어들며 돌아간다. */
    public fun collapse() {
        expandedKey = null
    }
}

@Composable
public fun rememberGlassExpandState(): GlassExpandState = remember { GlassExpandState() }

/** 이 요소를 [key] 카드의 자리로 알린다. 그 카드가 열려 있는 동안 이 자리는 비워 둔다(커진 카드가 대신 보인다). */
public fun Modifier.expandSource(state: GlassExpandState, key: Any): Modifier = onGloballyPositioned {
    val bounds = it.boundsInRoot()
    if (state.sources[key] != bounds) state.sources[key] = bounds
}
    .graphicsLayer { alpha = if (state.activeKey == key) 0f else 1f }

/**
 * 열린 카드의 상세 화면을 그린다. 카드 자리에서 화면 가득 커지고 모서리가 펴지며, 뒤 화면은 흐려진다.
 * 상세를 아래로 끌면(스크롤이 맨 위일 때) 작아지며 둥글어지고, 충분히 끌거나 뒤로 가기를 하면 카드 자리로 돌아간다.
 *
 * [content]는 화면 크기로 그려진 뒤 카드 폭에 맞게 줄여 보이므로, 상세 위쪽을 카드와 같은 모습으로 두면 끊김 없이 이어진다.
 * [GlassScaffold] 안에 두면 위층에 그린다.
 *
 * @param cornerRadius 카드의 모서리 반지름. 커지며 0으로 펴진다.
 */
@Composable
public fun GlassExpandHost(
    state: GlassExpandState,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    title: String? = null,
    content: @Composable (key: Any) -> Unit,
) {
    val active = state.activeKey ?: return
    val open = state.expandedKey != null
    val progress = remember(active) { Animatable(0f) }
    val drag = remember(active) { DragOffset() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(active, open) {
        val damping = if (open) EXPAND_DAMPING else COLLAPSE_DAMPING
        progress.animateTo(if (open) 1f else 0f, spring(dampingRatio = damping, stiffness = Spring.StiffnessMediumLow))
        if (!open) state.activeKey = null
    }
    BackHandler(enabled = open) { state.collapse() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val dismissDistance = with(LocalDensity.current) { DISMISS_DISTANCE.toPx() }
    fun release() {
        if (drag.value > dismissDistance * DISMISS_FRACTION) {
            state.collapse()
        } else {
            drag.settle(scope, 0f, Spring.DampingRatioMediumBouncy)
        }
    }
    val connection = remember(active) { dismissConnection(drag, ::release) }
    GlassOverlay(visible = true) { _ ->
        Box(
            modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    origin = it.positionInRoot()
                    bounds = Rect(Offset.Zero, Size(it.size.width.toFloat(), it.size.height.toFloat()))
                },
        ) {
            val level = progress.value.coerceIn(0f, 1f)
            val dragged = (drag.value / dismissDistance).coerceIn(0f, 1f)
            Scrim(level * (1f - dragged * SCRIM_DRAG)) { state.collapse() }
            if (bounds.width <= 0f) return@Box
            val from = state.sources[active]?.translate(-origin) ?: bounds.deflate(bounds.minDimension * FALLBACK_INSET)
            // 끌수록 화면이 작아지며 가운데로 모인다.
            val full = bounds.scaleAround(1f - dragged * DRAG_SHRINK)
            val frame = lerp(from, full, progress.value)
            val radius = lerp(cornerRadius.value, cornerRadius.value * dragged, level).dp
            val fullWidth = bounds.width.roundToInt()
            val fullHeight = bounds.height.roundToInt()
            Box(
                Modifier
                    .placeAt(frame, fullWidth, fullHeight)
                    .clip(RoundedCornerShape(radius))
                    .semantics {
                        title?.let { paneTitle = it }
                        dismiss {
                            state.collapse()
                            true
                        }
                    }
                    .draggable(
                        rememberDraggableState { delta -> drag.drag(delta * DRAG_RATE) },
                        Orientation.Vertical,
                        onDragStopped = { release() },
                    )
                    .nestedScroll(connection),
            ) {
                ScaledToFrame(frame.width / bounds.width.coerceAtLeast(1f), fullWidth, fullHeight) { content(active) }
            }
        }
    }
}

/** 화면 크기([fullWidth]×[fullHeight]) 안 [frame] 자리에 그 크기로 놓는다. */
private fun Modifier.placeAt(frame: Rect, fullWidth: Int, fullHeight: Int): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(
        Constraints.fixed(frame.width.roundToInt().coerceAtLeast(1), frame.height.roundToInt().coerceAtLeast(1)),
    )
    layout(fullWidth, fullHeight) { placeable.place(frame.left.roundToInt(), frame.top.roundToInt()) }
}

/** 상세는 화면 크기([fullWidth]×[fullHeight])로 그리고 [scale]만큼 줄여 카드 폭에 맞춘다. 카드 높이 밖은 잘린다. */
@Composable
private fun ScaledToFrame(scale: Float, fullWidth: Int, fullHeight: Int, content: @Composable () -> Unit) {
    Box(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(Constraints.fixed(fullWidth, fullHeight))
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        },
    ) { content() }
}

/** 상세 안 스크롤이 맨 위에서 더 내려가려는 만큼을 끌어 닫기로 받는다. 위로 밀면 끈 만큼을 먼저 거둔다. */
private fun dismissConnection(drag: DragOffset, release: () -> Unit): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y >= 0f || drag.value <= 0f) return Offset.Zero
        val used = maxOf(available.y, -drag.value)
        drag.drag(used)
        return Offset(0f, used)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
        drag.drag(available.y * DRAG_RATE)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (drag.value <= 0f) return Velocity.Zero
        release()
        return available
    }
}

private fun Rect.scaleAround(scale: Float): Rect {
    val c = center
    return Rect(c.x - width * scale / 2, c.y - height * scale / 2, c.x + width * scale / 2, c.y + height * scale / 2)
}

private const val FALLBACK_INSET = 0.25f
private const val EXPAND_DAMPING = 0.82f
private const val COLLAPSE_DAMPING = 0.9f
private val DISMISS_DISTANCE = 240.dp
private const val DISMISS_FRACTION = 0.45f
private const val DRAG_RATE = 0.6f
private const val DRAG_SHRINK = 0.18f
private const val SCRIM_DRAG = 0.5f
