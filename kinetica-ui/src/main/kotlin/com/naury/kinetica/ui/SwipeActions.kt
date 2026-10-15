package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * [SwipeActionsRow]를 밀었을 때 뒤에서 드러나는 버튼 하나.
 *
 * @property label 버튼 글자. TalkBack 사용자 지정 동작의 이름으로도 쓴다.
 * @property background 버튼 배경색
 * @property icon 글자 위에 그릴 아이콘. `null`이면 글자만 그린다.
 * @property contentColor 아이콘과 글자 색
 * @property removesRow `true`면 실행할 때 행 높이를 0으로 줄인 뒤 [onAction]을 부른다. 삭제처럼 행이 목록에서 빠지는 동작에 쓴다.
 *   [onAction]이 끝나고 1초가 지나도 행이 화면에 남아 있으면(삭제를 취소한 경우 등) 다시 펼친다.
 * @property onAction 버튼을 누르거나 끝까지 밀었을 때 부른다.
 */
@Stable
public class SwipeAction(
    public val label: String,
    public val background: Color,
    public val icon: ImageVector? = null,
    public val contentColor: Color = Color.White,
    public val removesRow: Boolean = false,
    public val onAction: () -> Unit,
)

/**
 * 한 번에 한 행만 열리도록 묶는 그룹. 같은 그룹의 다른 행을 밀기 시작하면 열려 있던 행이 닫힌다.
 * [rememberSwipeActionsGroup]으로 만들고 [rememberSwipeActionsState]에 넘긴다.
 */
@Stable
public class SwipeActionsGroup internal constructor() {
    internal var open: SwipeActionsState? by mutableStateOf(null)

    /** 그룹에서 열려 있는 행을 닫는다. */
    public fun closeAll() {
        open = null
    }
}

/** 화면에 있는 동안 유지되는 [SwipeActionsGroup]을 만든다. */
@Composable
public fun rememberSwipeActionsGroup(): SwipeActionsGroup = remember { SwipeActionsGroup() }

/**
 * [SwipeActionsRow] 한 행의 상태. 오프셋은 읽는 방향 기준이며 양수면 시작 쪽 버튼, 음수면 끝 쪽 버튼이 드러난다.
 */
@Stable
public class SwipeActionsState internal constructor(internal val group: SwipeActionsGroup?) {
    internal val offset = Animatable(0f)
    internal val fill = Animatable(0f)
    internal val height = Animatable(1f)

    // 끝까지 밀어 첫 버튼이 행을 채운 쪽. 시작 쪽이면 1, 끝 쪽이면 -1, 아니면 0이다.
    internal var armedSide by mutableIntStateOf(0)

    // 동작을 실행하는 중에는 그룹이 닫으라고 해도 무시해야 onAction이 취소되지 않는다.
    internal var busy = false

    /** 버튼이 드러나 있거나 드러나는 중이면 `true`다. */
    public val isOpen: Boolean get() = offset.targetValue != 0f

    /** 드러난 버튼을 숨기며 행을 제자리로 돌린다. 동작을 실행하는 중이면 아무것도 하지 않는다. */
    public suspend fun close() {
        if (busy) return
        armedSide = 0
        fill.snapTo(0f)
        settle(0f)
    }

    /** [target]으로 용수철처럼 움직이되, 0을 지나 반대쪽 버튼이 비치지 않도록 지금 쪽에서 멈춘다. */
    internal suspend fun settle(target: Float, velocity: Float = 0f) {
        val side = sign(offset.value)
        if (side > 0f) {
            offset.updateBounds(lowerBound = 0f)
        } else if (side < 0f) {
            offset.updateBounds(upperBound = 0f)
        }
        try {
            offset.animateTo(target, SETTLE_SPEC, velocity)
        } finally {
            offset.updateBounds(null, null)
        }
    }
}

/**
 * [SwipeActionsRow] 한 행의 상태를 만든다. [group]을 넘기면 같은 그룹에서 한 번에 한 행만 열린다.
 */
@Composable
public fun rememberSwipeActionsState(group: SwipeActionsGroup? = null): SwipeActionsState = remember(group) { SwipeActionsState(group) }

/**
 * iOS 메일처럼 옆으로 밀면 뒤에서 색 버튼이 드러나는 행. 시작 쪽으로 밀면 [endActions], 끝 쪽으로 밀면 [startActions]가 보인다.
 * 버튼은 목록 순서대로 바깥 가장자리부터 안쪽으로 놓인다. [fullSwipe]가 `true`면 버튼이 모두 드러난 뒤에도 계속 밀어
 * 행 너비의 절반을 넘겼을 때 가장 바깥 버튼이 드러난 영역을 채우고 햅틱을 준다. 그 상태로 놓으면 그 동작을 실행한다.
 * RTL에서는 시작과 끝이 뒤집힌다.
 *
 * 가로로 터치 슬롭을 넘게 움직였을 때만 행을 잡으므로 세로 스크롤과 충돌하지 않는다. 열린 행의 내용을 누르면 닫힌다.
 * TalkBack은 모든 버튼을 사용자 지정 동작으로 제공한다. 행을 누를 수 있게 하려면 [content] 안이 아니라 [modifier]에
 * `clickable`을 걸어야 TalkBack이 행과 동작을 한 요소로 읽는다.
 *
 * [content]가 버튼 위를 덮어야 하므로 배경이 투명하지 않아야 한다. `LazyColumn`에서는 항목에 `key`를 주어야
 * 삭제한 행의 상태가 다음 행으로 넘어가지 않는다.
 *
 * 행이 둥근 카드라면 [shape]에 같은 모양을 넘겨야 밀었을 때 드러나는 버튼도 그 모서리를 따른다.
 *
 * ```kotlin
 * SwipeActionsRow(
 *     endActions = listOf(SwipeAction("삭제", Color.Red, removesRow = true) { mails.remove(mail) }),
 *     state = rememberSwipeActionsState(group),
 * ) { MailRow(mail) }
 * ```
 */
@Composable
public fun SwipeActionsRow(
    endActions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    startActions: List<SwipeAction> = emptyList(),
    fullSwipe: Boolean = true,
    state: SwipeActionsState = rememberSwipeActionsState(),
    shape: Shape = RectangleShape,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val actionWidth = with(LocalDensity.current) { ACTION_WIDTH.toPx() }
    var width by remember { mutableIntStateOf(0) }
    val swipe = SwipeSetup(startActions, endActions, fullSwipe, actionWidth) { width.toFloat() }
    val perform: (SwipeAction, Int, Boolean) -> Unit = { action, side, full ->
        scope.launch { state.perform(action, side, if (full) width.toFloat() else 0f) }
    }
    CloseWhenOthersOpen(state)
    Box(
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val height = (placeable.height * state.height.value.coerceIn(0f, 1f)).roundToInt()
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
            // 뒤 버튼이 행 모서리 밖으로 비치지 않게 행 모양으로 자른다.
            .clip(shape)
            .onSizeChanged { width = it.width }
            .semantics(mergeDescendants = true) {
                customActions = (startActions.map { it to 1 } + endActions.map { it to -1 }).map { (action, side) ->
                    CustomAccessibilityAction(action.label) {
                        perform(action, side, false)
                        true
                    }
                }
            }
            .draggable(
                rememberDraggableState { delta ->
                    val armed = state.dragBy(delta, swipe, scope)
                    if (armed) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                },
                Orientation.Horizontal,
                reverseDirection = rtl,
                onDragStarted = { state.group?.open = state },
                onDragStopped = { velocity ->
                    if (state.busy) return@draggable
                    val side = state.armedSide
                    val action = if (side > 0) startActions.firstOrNull() else endActions.firstOrNull()
                    if (side != 0 && action != null) perform(action, side, true) else state.release(velocity, swipe)
                },
            )
            .settleAfterRelease({ state.needsSettle(swipe) }) { scope.launch { state.release(0f, swipe) } },
    ) {
        ActionStrip(startActions, 1, state, perform, Modifier.matchParentSize())
        ActionStrip(endActions, -1, state, perform, Modifier.matchParentSize())
        Box(Modifier.graphicsLayer { translationX = state.offset.value * if (rtl) -1f else 1f }) { content() }
        if (state.isOpen) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { translationX = state.offset.value * if (rtl) -1f else 1f }
                    .clearAndSetSemantics {}
                    .clickable(remember { MutableInteractionSource() }, indication = null) { scope.launch { state.close() } },
            )
        }
    }
}

/** 한 행을 미는 데 필요한 값. [width]는 측정이 끝난 뒤에 알 수 있어 함수로 받는다. */
internal class SwipeSetup(
    val start: List<SwipeAction>,
    val end: List<SwipeAction>,
    val fullSwipe: Boolean,
    val actionWidth: Float,
    val width: () -> Float,
) {
    fun reveal(side: Int): Float = (if (side > 0) start.size else end.size) * actionWidth

    /** 이 거리 넘게 밀면 가장 바깥 버튼이 행을 채운다. */
    fun armDistance(side: Int): Float = maxOf(width() * FULL_SWIPE_FRACTION, reveal(side) + actionWidth * ARM_EXTRA)
}

/** 손가락이 움직인 만큼 행을 옮긴다. 끝까지 미는 상태가 새로 시작되면 `true`를 돌려준다. */
private fun SwipeActionsState.dragBy(delta: Float, setup: SwipeSetup, scope: CoroutineScope): Boolean {
    // 동작을 실행하는 동안 다시 끌면 밀어 내는 애니메이션이 끊기므로 손가락을 무시한다.
    if (busy) return false
    val current = offset.value
    var next = current + delta
    if (setup.start.isEmpty()) next = next.coerceAtMost(0f)
    if (setup.end.isEmpty()) next = next.coerceAtLeast(0f)
    val side = sign(next).toInt()
    val reveal = setup.reveal(side)
    if (setup.fullSwipe) {
        next = next.coerceIn(-setup.width(), setup.width())
    } else if (abs(next) > reveal && abs(next) > abs(current)) {
        // 끝까지 밀 수 없으면 버튼 너비를 넘어선 만큼 저항을 준다.
        next = (current + delta * OVERDRAG_RESISTANCE).coerceIn(-setup.reveal(-1) * MAX_OVERDRAG, setup.reveal(1) * MAX_OVERDRAG)
    }
    scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(next) }
    val armNow = if (setup.fullSwipe && reveal > 0f && abs(next) >= setup.armDistance(side)) side else 0
    if (armNow == armedSide) return false
    armedSide = armNow
    scope.launch { fill.animateTo(if (armNow != 0) 1f else 0f, FILL_SPEC) }
    return armNow != 0
}

/** 손을 뗀 뒤 행이 닫힌 자리에도, 버튼이 다 드러난 자리에도 있지 않고 움직이는 중도 아니면 true다. */
private fun SwipeActionsState.needsSettle(setup: SwipeSetup): Boolean {
    if (busy || offset.isRunning || armedSide != 0) return false
    val value = offset.value
    return value != 0f && abs(abs(value) - setup.reveal(sign(value).toInt())) > HALF_PX
}

/** 손을 놓았을 때 버튼이 보이게 열어 두거나 닫는다. [velocity]는 읽는 방향 기준(px/s)이다. */
private suspend fun SwipeActionsState.release(velocity: Float, setup: SwipeSetup) {
    val current = offset.value
    val side = sign(current).toInt()
    val reveal = setup.reveal(side)
    val toward = velocity * side
    val fling = setup.actionWidth * FLING_PER_ACTION
    val open = side != 0 && reveal > 0f && (toward > fling || (abs(current) > reveal / 2 && toward > -fling))
    if (open) group?.open = this
    settle(if (open) reveal * side else 0f, velocity)
}

/**
 * [action]을 실행한다. [travel]이 0보다 크면 먼저 행을 그 거리만큼 끝까지 밀어 낸다.
 * 행을 지우는 동작이면 높이를 0으로 줄인 뒤 실행하고, 잠시 뒤에도 행이 남아 있으면 다시 펼친다.
 * 밀어 내거나 줄이는 애니메이션이 중간에 취소되어도 [SwipeAction.onAction]은 한 번 부른다.
 */
private suspend fun SwipeActionsState.perform(action: SwipeAction, side: Int, travel: Float) {
    if (busy) return
    busy = true
    try {
        try {
            if (travel > 0f) offset.animateTo(travel * side, tween(FULL_SWIPE_MS))
            if (action.removesRow) height.animateTo(0f, tween(REMOVE_MS))
        } finally {
            action.onAction()
        }
        if (action.removesRow) {
            delay(RESTORE_DELAY_MS)
            offset.snapTo(0f)
            fill.snapTo(0f)
            armedSide = 0
            height.animateTo(1f, SETTLE_SPEC)
        } else {
            armedSide = 0
            settle(0f)
            fill.snapTo(0f)
        }
    } finally {
        busy = false
    }
}

/** 같은 그룹의 다른 행이 열리면 이 행을 닫는다. */
@Composable
private fun CloseWhenOthersOpen(state: SwipeActionsState) {
    val group = state.group ?: return
    LaunchedEffect(group, state) {
        snapshotFlow { group.open }.collect { open ->
            if (open !== state && state.offset.targetValue != 0f) state.close()
        }
    }
}

/**
 * 한쪽 버튼 줄. 드러난 너비를 버튼 수로 나누어 바깥 가장자리부터 놓고, 끝까지 밀면 첫 버튼이 전체를 차지한다.
 * 너비는 매 프레임 바뀌므로 재구성 없이 측정 단계에서 계산한다.
 */
@Composable
private fun ActionStrip(
    actions: List<SwipeAction>,
    side: Int,
    state: SwipeActionsState,
    perform: (SwipeAction, Int, Boolean) -> Unit,
    modifier: Modifier,
) {
    if (actions.isEmpty()) return
    val shown = state.offset.targetValue * side > 0f || state.offset.value * side > 0f
    Layout(
        content = {
            actions.forEachIndexed { index, action ->
                ActionButton(action, if (index == 0) side else 0, state) { perform(action, side, false) }
            }
        },
        modifier = if (shown) modifier else modifier.clearAndSetSemantics {},
    ) { measurables, constraints ->
        val revealed = (state.offset.value * side).coerceAtLeast(0f)
        val grow = if (state.armedSide == side) state.fill.value else 0f
        val base = revealed / measurables.size
        val edges = FloatArray(measurables.size + 1)
        measurables.indices.forEach { i ->
            val w = if (i == 0) base + (revealed - base) * grow else base * (1f - grow)
            edges[i + 1] = edges[i] + w
        }
        val height = constraints.maxHeight
        val placeables = measurables.mapIndexed { i, m ->
            val w = edges[i + 1].roundToInt() - edges[i].roundToInt()
            m.measure(Constraints.fixed(w.coerceAtLeast(0), height))
        }
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { i, p ->
                val x = if (side > 0) edges[i].roundToInt() else constraints.maxWidth - edges[i + 1].roundToInt()
                p.placeRelative(x, 0)
            }
        }
    }
}

/**
 * 버튼 하나. 글자 묶음은 버튼이 좁아도 줄어들지 않고 잘린다. [pinSide]가 0이 아니면(가장 바깥 버튼) 행을 채울수록
 * 글자가 내용 쪽 가장자리로 따라간다.
 */
@Composable
private fun ActionButton(action: SwipeAction, pinSide: Int, state: SwipeActionsState, onClick: () -> Unit) {
    Box(
        Modifier
            .background(action.background)
            .clipToBounds()
            .clickable(
                null,
                GlassIndication(color = Color.White, pressedAlpha = ACTION_PRESS),
                role = Role.Button,
                onClickLabel = action.label,
                onClick = onClick,
            ),
    ) {
        Column(
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints.fixed(ACTION_WIDTH.roundToPx(), constraints.maxHeight))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val center = (constraints.maxWidth - placeable.width) / 2f
                    // 시작 쪽 버튼이면 내용은 끝 쪽에 있다.
                    val edge = if (pinSide > 0) (constraints.maxWidth - placeable.width).toFloat() else 0f
                    val pin = if (pinSide != 0 && state.armedSide == pinSide) state.fill.value else 0f
                    placeable.placeRelative(lerp(center, edge, pin).roundToInt(), 0)
                }
            },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            action.icon?.let {
                Image(rememberVectorPainter(it), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(action.contentColor))
            }
            ThemedText(
                action.label,
                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = TextStyle(
                    color = action.contentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val ACTION_WIDTH = 76.dp
private val SETTLE_SPEC = spring<Float>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
private val FILL_SPEC = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
private const val FULL_SWIPE_FRACTION = 0.55f
private const val ARM_EXTRA = 0.6f
private const val OVERDRAG_RESISTANCE = 0.3f
private const val MAX_OVERDRAG = 1.6f
private const val FLING_PER_ACTION = 10f
private const val FULL_SWIPE_MS = 180
private const val REMOVE_MS = 240
private const val RESTORE_DELAY_MS = 1_000L
private const val HALF_PX = 0.5f
private const val ACTION_PRESS = 0.22f
