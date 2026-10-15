package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
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
 * App Store의 투데이 탭 카드처럼 카드가 커지며 상세 화면으로 이어지는 전환의 상태. [Modifier.expandSource]로 카드 위치를 등록하고
 * [expand]로 연다. 열린 상세 화면은 [GlassExpandHost]가 그린다.
 */
@Stable
public class GlassExpandState internal constructor() {
    internal val sources = mutableStateMapOf<Any, Rect>()

    // 오버레이의 루트 좌표. 다음에 열 때 첫 프레임부터 바로 쓰도록 남겨 둔다.
    internal var hostOrigin by mutableStateOf(Offset.Zero)

    // 상태를 복원해 다시 열린 경우 애니메이션 없이 열린 채로 보인다.
    internal var restoredOpen = false

    /** 열려 있거나 열리는 중인 카드의 키. 닫히는 중이면 `null`이다. */
    public var expandedKey: Any? by mutableStateOf(null)
        private set

    /** 지금 화면에 그려지는 카드의 키. 닫힘 애니메이션이 진행 중일 때도 값이 남아 있다. 이 카드의 원래 위치는 숨긴다. */
    public var activeKey: Any? by mutableStateOf(null)
        internal set

    /** [key] 카드를 연다. 그 카드에 [Modifier.expandSource]가 없으면 화면 가운데에서 커진다. */
    public fun expand(key: Any) {
        expandedKey = key
        activeKey = key
    }

    /** 열린 카드를 닫는다. 상세 화면이 원래 카드 위치로 줄어들며 돌아간다. */
    public fun collapse() {
        expandedKey = null
    }
}

/**
 * 화면 회전이나 프로세스 재시작 뒤에도 열린 카드를 기억하는 [GlassExpandState]를 만든다.
 * 열린 카드의 키가 `Bundle`에 담을 수 없는 값이면 기억하지 않고 닫힌 상태로 돌아온다.
 */
@Composable
public fun rememberGlassExpandState(): GlassExpandState = rememberSaveable(saver = ExpandSaver) { GlassExpandState() }

private val ExpandSaver = Saver<GlassExpandState, Any>(
    save = { state -> state.expandedKey?.takeIf { canBeSaved(it) } ?: NOTHING_OPEN },
    restore = { key ->
        GlassExpandState().apply {
            if (key != NOTHING_OPEN) {
                restoredOpen = true
                expand(key)
            }
        }
    },
)

private const val NOTHING_OPEN = "kinetica.expand.none"

/**
 * 이 요소를 [key] 카드의 원래 위치로 등록한다. 카드가 열려 있는 동안에는 커진 카드가 대신 보이므로 이 요소를 숨긴다.
 * 요소가 화면에서 빠지면(목록에서 스크롤되어 사라지는 등) 등록을 지우고, 그때 닫으면 화면 가운데로 줄어든다.
 */
public fun Modifier.expandSource(state: GlassExpandState, key: Any): Modifier =
    this then ExpandSourceElement(state, key) then Modifier.graphicsLayer { alpha = if (state.activeKey == key) 0f else 1f }

private data class ExpandSourceElement(val state: GlassExpandState, val key: Any) : ModifierNodeElement<ExpandSourceNode>() {
    override fun create(): ExpandSourceNode = ExpandSourceNode(state, key)

    override fun update(node: ExpandSourceNode) {
        if (node.key != key || node.state !== state) node.forget()
        node.state = state
        node.key = key
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "expandSource"
    }
}

private class ExpandSourceNode(var state: GlassExpandState, var key: Any) :
    Modifier.Node(),
    GlobalPositionAwareModifierNode {
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val bounds = coordinates.boundsOnScreen()
        if (state.sources[key] != bounds) state.sources[key] = bounds
    }

    override fun onDetach() = forget()

    fun forget() {
        state.sources.remove(key)
    }
}

/**
 * 열린 카드의 상세 화면을 그린다. 카드 위치에서 화면 전체로 커지면서 모서리가 펴지고, 배경은 흐려진다.
 * 스크롤이 맨 위일 때 상세 화면을 아래로 끌면 작아지며 모서리가 둥글어지고, 충분히 끌거나 뒤로 가기를 하면 카드 위치로 돌아간다.
 *
 * [content]는 화면 크기로 그린 뒤 카드 너비에 맞게 축소해 보여 준다. 상세 화면 위쪽을 카드와 똑같이 구성하면 전환이 자연스럽게 이어진다.
 * [GlassScaffold] 안에 두면 오버레이 레이어에 그린다.
 *
 * @param cornerRadius 카드의 모서리 반경. 상세 화면이 커지면서 0이 된다.
 * @param title 열린 상세 화면의 이름. TalkBack이 창 제목으로 읽는다.
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
    val progress = remember(active) { Animatable(if (state.restoredOpen) 1f else 0f).also { state.restoredOpen = false } }
    val drag = remember(active) { SettleOffset() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(active, open) {
        val damping = if (open) EXPAND_DAMPING else COLLAPSE_DAMPING
        progress.animateTo(if (open) 1f else 0f, spring(dampingRatio = damping, stiffness = Spring.StiffnessMediumLow))
        if (!open) state.activeKey = null
    }
    val dismissDistance = with(LocalDensity.current) { DISMISS_DISTANCE.toPx() }
    fun release() {
        if (drag.value > dismissDistance * DISMISS_FRACTION) {
            state.collapse()
        } else {
            drag.settle(scope, 0f, spring(Spring.DampingRatioMediumBouncy))
        }
    }
    val connection = remember(active) { dismissConnection(drag, ::release) }
    GlassOverlay(visible = true) { _ ->
        // GlassScaffold 밖에서는 오버레이가 Dialog 창에 뜨고, 그 창의 뒤로 가기는 창 안의 BackHandler만 받으므로 오버레이 안에 둔다.
        BackHandler(enabled = open) { state.collapse() }
        // 크기는 제약에서 바로 얻어 처음 여는 프레임부터 그린다(측정 뒤에 알게 되면 첫 프레임에 카드만 사라져 깜빡인다).
        BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { state.hostOrigin = it.positionOnScreen() }) {
            val bounds = Rect(Offset.Zero, Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()))
            val origin = state.hostOrigin
            val level = progress.value.coerceIn(0f, 1f)
            val dragged = (drag.value / dismissDistance).coerceIn(0f, 1f)
            Scrim(level * (1f - dragged * SCRIM_DRAG)) { state.collapse() }
            if (bounds.width <= 0f) return@BoxWithConstraints
            val from = state.sources[active]?.translate(-origin) ?: bounds.deflate(bounds.minDimension * FALLBACK_INSET)
            // 많이 끌수록 화면이 작아지며 가운데로 모인다.
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
                    .nestedScroll(connection)
                    .returnAfterRelease(drag, open, ::release),
            ) {
                ScaledToFrame(frame.width / bounds.width.coerceAtLeast(1f), fullWidth, fullHeight) { content(active) }
            }
        }
    }
}

/** 끌어 내린 채 플링 없이 손을 뗐으면 [release]로 닫을지 되돌릴지 정한다. */
@Composable
private fun Modifier.returnAfterRelease(drag: SettleOffset, open: Boolean, release: () -> Unit): Modifier =
    settleAfterRelease({ open && drag.value > 0f && !drag.isSettling }, release)

/** 화면 크기([fullWidth]×[fullHeight]) 안의 [frame] 위치에 [frame] 크기로 배치한다. */
private fun Modifier.placeAt(frame: Rect, fullWidth: Int, fullHeight: Int): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(
        Constraints.fixed(frame.width.roundToInt().coerceAtLeast(1), frame.height.roundToInt().coerceAtLeast(1)),
    )
    layout(fullWidth, fullHeight) { placeable.place(frame.left.roundToInt(), frame.top.roundToInt()) }
}

/** 상세 화면을 화면 크기([fullWidth]×[fullHeight])로 그린 뒤 [scale]만큼 축소해 카드 너비에 맞춘다. 카드 높이를 넘는 부분은 잘린다. */
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

/** 상세 화면의 스크롤이 맨 위에서 더 내려가려는 만큼을 드래그해 닫기로 받는다. 다시 위로 밀면 끌어 내린 거리부터 되돌린다. */
private fun dismissConnection(drag: SettleOffset, release: () -> Unit): NestedScrollConnection = object : NestedScrollConnection {
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
