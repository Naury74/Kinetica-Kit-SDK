package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** [SwipeCardStack]에서 카드를 넘긴 방향. 화면 기준이며, RTL 레이아웃에서도 왼쪽은 왼쪽이다. */
public enum class SwipeDirection { Left, Right, Up }

/**
 * [SwipeCardStack]의 상태. 넘긴 카드의 키와 방향 기록을 가지고 있어 [undo]로 되돌릴 수 있다. 화면을 회전해도 유지되며,
 * 키를 저장할 수 없으면(Bundle에 넣을 수 없는 키) 넘긴 카드 수만 저장해 위치로 복원한다.
 * [swipe]와 [undo]는 애니메이션이 끝날 때까지 일시 중단한다. 다른 애니메이션이 진행 중이면 아무 일도 하지 않고 `false`를 돌려준다.
 */
@Stable
public class SwipeCardStackState internal constructor(history: List<SwipeDirection>, swipedKeys: List<Any?>) {
    internal val history = mutableStateListOf<SwipeDirection>().apply { addAll(history) }

    // history와 같은 순서로 넘긴 카드의 키를 담는다. null은 키를 저장하지 못하고 복원된 카드로, 목록 앞쪽부터 위치로 센다.
    internal val swipedKeys = mutableStateListOf<Any?>().apply { addAll(swipedKeys) }

    // 지금 items의 키. 컴포지션에서 items가 바뀔 때마다 맞춘다.
    internal var keys: List<Any?> by mutableStateOf(emptyList())

    /**
     * 맨 위 카드의 위치. 맨 위 카드는 `items[swipedCount]`이고, 이 값이 `items.size`이면 남은 카드가 없다.
     * 넘긴 카드를 items에서 지우거나 앞에 새 카드를 넣어도 키로 다시 찾으므로 넘기지 않은 카드를 건너뛰지 않는다.
     */
    public val swipedCount: Int
        get() {
            val known = swipedKeys.filterNotNullTo(HashSet())
            val start = swipedKeys.count { it == null }.coerceAtMost(keys.size)
            val top = (start until keys.size).firstOrNull { keys[it] !in known }
            return top ?: keys.size
        }

    internal val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    internal var size by mutableStateOf(IntSize.Zero)
    internal var grabbedTop = true
    internal var flingPx = 1f
    internal var onSwiped: (index: Int, direction: SwipeDirection) -> Unit = { _, _ -> }
    private var busy by mutableStateOf(false)

    /** 손을 뗀 뒤 카드가 제자리에도, 날아가는 중에도 있지 않으면 true다. */
    internal val needsSettle: Boolean get() = !busy && !offset.isRunning && offset.value != Offset.Zero

    /** [undo]할 카드가 있는지. 애니메이션 중에는 `false`다. */
    public val canUndo: Boolean get() = history.isNotEmpty() && !busy

    /**
     * 맨 위 카드를 [direction]으로 날려 보낸다. 다 날아가면 `onSwipe`를 호출하고 다음 카드가 맨 위가 된다.
     * 남은 카드가 없으면 `false`를 돌려준다.
     */
    public suspend fun swipe(direction: SwipeDirection): Boolean = fly(direction, Offset.Zero)

    /**
     * 마지막으로 넘긴 카드를 넘긴 방향에서 다시 날아오게 해 맨 위로 되돌린다. `onSwipe`는 다시 호출하지 않는다.
     * 그 카드가 이미 items에서 빠졌으면 기록만 지우고 `false`를 돌려준다.
     */
    public suspend fun undo(): Boolean {
        if (busy || history.isEmpty()) return false
        busy = true
        try {
            val last = swipedKeys.lastIndex
            val key = swipedKeys[last]
            val direction = history.removeAt(history.lastIndex)
            swipedKeys.removeAt(last)
            if (key != null && key !in keys) return false
            offset.snapTo(flyTarget(direction, Offset.Zero))
            offset.animateTo(Offset.Zero, spring(dampingRatio = RETURN_DAMPING, stiffness = Spring.StiffnessMediumLow))
        } finally {
            busy = false
        }
        return true
    }

    internal suspend fun fly(direction: SwipeDirection, velocity: Offset): Boolean {
        if (busy || swipedCount >= keys.size) return false
        busy = true
        try {
            offset.animateTo(flyTarget(direction, offset.value), spring(stiffness = FLY_STIFFNESS), initialVelocity = velocity)
            val index = swipedCount
            // 날아가는 동안 items가 바뀌었을 수 있으므로 날아간 뒤의 맨 위 카드를 넘긴 것으로 기록한다.
            if (index >= keys.size) return false
            history += direction
            swipedKeys += keys[index]
            offset.snapTo(Offset.Zero)
            onSwiped(index, direction)
        } finally {
            busy = false
        }
        return true
    }

    /** 손을 뗐을 때 충분히 끌었거나 빠르게 던졌으면 날려 보내고, 아니면 제자리로 돌아온다. */
    internal suspend fun settle(velocity: Offset, directions: Set<SwipeDirection>) {
        val direction = directions.firstOrNull { passed(it, velocity) }
        if (direction != null && fly(direction, velocity)) return
        offset.animateTo(
            Offset.Zero,
            spring(dampingRatio = RETURN_DAMPING, stiffness = Spring.StiffnessMediumLow),
            initialVelocity = velocity,
        )
    }

    private fun passed(direction: SwipeDirection, velocity: Offset): Boolean {
        val threshold = size.width * SWIPE_FRACTION
        val o = offset.value
        return when (direction) {
            SwipeDirection.Right -> o.x > threshold || (velocity.x > flingPx && o.x > 0f)
            SwipeDirection.Left -> o.x < -threshold || (velocity.x < -flingPx && o.x < 0f)
            SwipeDirection.Up -> o.y < -threshold || (velocity.y < -flingPx && o.y < 0f)
        }
    }

    /** [direction]으로 끈 정도(0..1). 1이면 손을 뗐을 때 날아간다. */
    internal fun progress(direction: SwipeDirection): Float {
        val threshold = (size.width * SWIPE_FRACTION).coerceAtLeast(1f)
        val o = offset.value
        val amount = when (direction) {
            SwipeDirection.Right -> o.x
            SwipeDirection.Left -> -o.x
            SwipeDirection.Up -> -o.y
        }
        return (amount / threshold).coerceIn(0f, 1f)
    }

    internal fun progress(): Float {
        val threshold = (size.width * SWIPE_FRACTION).coerceAtLeast(1f)
        return (maxOf(abs(offset.value.x), abs(offset.value.y)) / threshold).coerceIn(0f, 1f)
    }

    private fun flyTarget(direction: SwipeDirection, from: Offset): Offset {
        val w = size.width * FLY_DISTANCE
        val h = size.height * FLY_DISTANCE
        return when (direction) {
            SwipeDirection.Right -> Offset(w, from.y * FLY_DRIFT)
            SwipeDirection.Left -> Offset(-w, from.y * FLY_DRIFT)
            SwipeDirection.Up -> Offset(from.x * FLY_DRIFT, -h)
        }
    }

    internal companion object {
        val Saver: Saver<SwipeCardStackState, Any> = listSaver(
            save = { state ->
                val directions = ArrayList(state.history.map(SwipeDirection::ordinal))
                val keys = state.swipedKeys.toList()
                // 키 하나라도 저장할 수 없으면 모두 null로 저장해 위치로 복원한다.
                val saved = if (keys.all { it == null || canBeSaved(it) }) ArrayList(keys) else ArrayList(keys.map { null })
                listOf(directions, saved)
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                val directions = (saved[0] as List<Int>).map { SwipeDirection.entries[it] }
                SwipeCardStackState(directions, saved[1] as List<Any?>)
            },
        )
    }
}

/** [SwipeCardStack]의 상태를 만든다. */
@Composable
public fun rememberSwipeCardStackState(): SwipeCardStackState =
    rememberSaveable(saver = SwipeCardStackState.Saver) { SwipeCardStackState(emptyList(), emptyList()) }

/**
 * 데이팅 앱처럼 넘기는 카드 더미. 맨 위 카드를 끌면 손가락을 따라오며 기울고, 충분히 끌거나 빠르게 던지면 날아간다.
 * 그러면 뒤 카드가 커지며 올라와 맨 위가 된다. 카드가 다 날아가면 [onSwipe]를 호출한다. 기준을 넘는 순간 햅틱이 울린다.
 *
 * 넘긴 카드는 [state]가 [key]로 기억하므로, [items]를 그대로 두어도 되고 [onSwipe]에서 넘긴 카드를 지워도 된다.
 * `state.undo()`로 마지막 카드를 되돌릴 수 있다(지운 카드는 되돌리지 않는다).
 * TalkBack에서는 맨 위 카드의 사용자 지정 동작으로 각 방향 넘기기와 되돌리기를 할 수 있다.
 *
 * ```kotlin
 * val stack = rememberSwipeCardStackState()
 * SwipeCardStack(
 *     people,
 *     onSwipe = { person, direction -> if (direction == SwipeDirection.Right) like(person) },
 *     overlay = { direction -> LikeStamp(direction) },
 * ) { person -> ProfileCard(person) }
 * Button(onClick = { scope.launch { stack.undo() } }, enabled = stack.canUndo) { ... }
 * ```
 *
 * @param directions 넘길 수 있는 방향. 화면 기준이다. 비어 있으면 끌어도 항상 제자리로 돌아온다.
 * @param visibleCount 겹쳐 보여 줄 카드 수(맨 위 포함). 1보다 작으면 1로 맞춘다.
 * @param key 카드를 구별하는 키. 같은 목록 안에서 겹치면 안 되고, 화면 회전 뒤에도 기억하려면 Bundle에 넣을 수 있는 값이어야 한다.
 *   기본값은 항목 자체다.
 * @param overlay 끄는 동안 카드 위에 겹쳐 보일 표시. 방향마다 한 번씩 만들고, 그 방향으로 끈 정도에 따라 투명도만 바뀐다.
 */
@Composable
public fun <T> SwipeCardStack(
    items: List<T>,
    onSwipe: (item: T, direction: SwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
    state: SwipeCardStackState = rememberSwipeCardStackState(),
    directions: Set<SwipeDirection> = setOf(SwipeDirection.Left, SwipeDirection.Right),
    visibleCount: Int = 3,
    key: (item: T) -> Any = { it ?: NullItemKey },
    overlay: @Composable BoxScope.(direction: SwipeDirection) -> Unit = {},
    content: @Composable BoxScope.(item: T) -> Unit,
) {
    val latestItems by rememberUpdatedState(items)
    val latestOnSwipe by rememberUpdatedState(onSwipe)
    // items가 같은 인스턴스의 상태 목록일 수 있으므로 매번 키를 다시 만든다. 내용이 같으면 상태가 바뀌지 않아 다시 구성을 일으키지 않는다.
    val keys = items.map(key)
    state.keys = keys
    state.flingPx = with(LocalDensity.current) { FLING_VELOCITY.toPx() }
    state.onSwiped = { index, direction -> latestItems.getOrNull(index)?.let { latestOnSwipe(it, direction) } }
    val depthCount = visibleCount.coerceAtLeast(1)
    Box(modifier.onSizeChanged { state.size = it }) {
        val top = state.swipedCount
        for (depth in depthCount - 1 downTo 0) {
            val index = top + depth
            if (index >= items.size) continue
            key(keys[index]) {
                val cardModifier = if (depth == 0) Modifier.topCard(state, directions) else Modifier.clearAndSetSemantics {}
                Box(
                    cardModifier
                        .fillMaxSize()
                        .graphicsLayer { stackTransform(state, depth) },
                ) {
                    content(items[index])
                    if (depth == 0) {
                        directions.forEach { direction ->
                            Box(Modifier.matchParentSize().graphicsLayer { alpha = state.progress(direction) }) { overlay(direction) }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.GraphicsLayerScope.stackTransform(state: SwipeCardStackState, depth: Int) {
    if (depth == 0) {
        val o = state.offset.value
        translationX = o.x
        translationY = o.y
        // 위쪽을 잡으면 끄는 쪽으로, 아래쪽을 잡으면 반대로 기운다. 손에 쥔 카드처럼 보이게 하려는 것이다.
        val tilt = (o.x / size.width.coerceAtLeast(1f)) * MAX_ROTATION
        rotationZ = if (state.grabbedTop) tilt else -tilt
        return
    }
    // 맨 위 카드를 끄는 만큼 뒤 카드들이 한 칸씩 앞으로 다가온다.
    val step = depth - state.progress()
    val scale = 1f - STACK_SCALE_STEP * step
    scaleX = scale
    scaleY = scale
    translationY = STACK_OFFSET_STEP.toPx() * step
}

@Composable
private fun Modifier.topCard(state: SwipeCardStackState, directions: Set<SwipeDirection>): Modifier {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val labels = mapOf(
        SwipeDirection.Left to stringResource(R.string.kinetica_ui_swipe_left),
        SwipeDirection.Right to stringResource(R.string.kinetica_ui_swipe_right),
        SwipeDirection.Up to stringResource(R.string.kinetica_ui_swipe_up),
    )
    val undoLabel = stringResource(R.string.kinetica_ui_swipe_undo)
    val canUndo = state.canUndo
    return this
        .semantics {
            customActions = directions.map { direction ->
                CustomAccessibilityAction(labels.getValue(direction)) {
                    scope.launch { state.swipe(direction) }
                    true
                }
            } + if (canUndo) {
                listOf(
                    CustomAccessibilityAction(undoLabel) {
                        scope.launch { state.undo() }
                        true
                    },
                )
            } else {
                emptyList()
            }
        }
        // 그리기 변환보다 바깥에 두어야 카드가 움직여도 손가락 좌표가 흔들리지 않는다.
        .pointerInput(state, directions) {
            dragCard(state, directions, scope) { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
        }
        .settleAfterRelease({ state.needsSettle }) { scope.launch { state.settle(Offset.Zero, emptySet()) } }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.dragCard(
    state: SwipeCardStackState,
    directions: Set<SwipeDirection>,
    scope: CoroutineScope,
    onThreshold: () -> Unit,
) {
    val tracker = VelocityTracker()
    var past = false
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // 위로 넘기기를 쓰지 않으면 세로로 시작한 끌기는 카드가 잡지 않고 바깥 목록이 스크롤하게 둔다.
        // 카드가 화면 대부분을 덮는 목록 안에서 스크롤이 막히지 않게 하려는 것이다.
        val start = awaitTouchSlopOrCancellation(down.id) { change, over ->
            val vertical = abs(over.y) > abs(over.x)
            if (!vertical || (SwipeDirection.Up in directions && over.y < 0f)) change.consume()
        } ?: return@awaitEachGesture
        state.grabbedTop = down.position.y < size.height / 2
        tracker.resetTracking()
        tracker.addPosition(start.uptimeMillis, start.position)
        past = false
        val first = start.position - down.position
        scope.launch { state.offset.snapTo(state.offset.value + first) }
        val ended = drag(start.id) { change ->
            val amount = change.positionChange()
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            val next = state.offset.value + amount
            scope.launch { state.offset.snapTo(next) }
            val nowPast = directions.any { state.progress(it) >= 1f }
            if (nowPast && !past) onThreshold()
            past = nowPast
        }
        if (ended) {
            val velocity = tracker.calculateVelocity()
            scope.launch { state.settle(Offset(velocity.x, velocity.y), directions) }
        } else {
            scope.launch { state.settle(Offset.Zero, emptySet()) }
        }
    }
}

/** `null` 항목의 기본 키 */
private object NullItemKey

private const val SWIPE_FRACTION = 0.35f
private const val FLY_DISTANCE = 1.5f
private const val FLY_DRIFT = 1.5f
private const val FLY_STIFFNESS = 260f
private const val RETURN_DAMPING = 0.62f
private const val MAX_ROTATION = 14f
private const val STACK_SCALE_STEP = 0.05f
private val STACK_OFFSET_STEP = 12.dp
private val FLING_VELOCITY = 1000.dp
