package com.naury.kinetica.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * iOS 내비게이션 스택처럼 화면을 밀어 넣는 컨테이너. [stack]에 화면을 추가하면 새 화면이 옆에서 밀려 들어오고,
 * 아래 화면은 조금 물러나며 어두워진다. 화면을 빼면 반대로 움직인다. 화면 시작 쪽 가장자리에서 가로로 드래그하거나
 * 예측형 뒤로 가기(Android 14 이상) 제스처를 하면 손가락을 따라 이전 화면으로 돌아가고, 충분히 드래그하면 [onBack]을 호출한다.
 *
 * 아래 화면의 `rememberSaveable` 상태는 다시 보일 때까지 유지되고, [stack]에서 빠진 화면의 상태는 지운다.
 * 모든 화면이 이 컴포저블을 부른 곳의 ViewModelStoreOwner와 Lifecycle을 함께 쓴다. 화면마다 따로 ViewModel이 필요하면
 * `viewModel(key = …)`로 화면별 키를 준다.
 *
 * ```kotlin
 * var stack by rememberSaveable { mutableStateOf(listOf("list")) }
 * PushNavigation(stack, onBack = { stack = stack.dropLast(1) }) { screen -> ... }
 * ```
 *
 * @param stack 아래부터 위 순서로 쌓인 화면. 비어 있거나 같은 항목이 두 번 들어 있으면 [IllegalArgumentException]을 던진다.
 *   항목은 화면을 구분하는 키로도 쓰므로 `rememberSaveable`에 저장할 수 있는 값이어야 한다.
 * @param onBack 맨 위 화면을 빼 달라는 요청. [stack]에 화면이 하나뿐이면 호출하지 않고, 뒤로 가기는 호스트가 처리한다.
 *   호스트가 [stack]을 바꾸지 않으면(예: 저장 확인 다이얼로그를 띄움) 맨 위 화면이 제자리로 돌아온다.
 * @param swipeBack 가장자리에서 드래그해 뒤로 가는 동작을 쓸지 여부
 */
@Composable
public fun <T : Any> PushNavigation(
    stack: List<T>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    swipeBack: Boolean = true,
    content: @Composable (T) -> Unit,
) {
    require(stack.isNotEmpty()) { "stack이 비어 있으면 안 됩니다." }
    require(stack.toSet().size == stack.size) { "stack에 같은 화면이 두 번 들어 있습니다: $stack" }
    val holder = rememberSaveableStateHolder()
    val nav = remember { PushState(stack) }
    nav.latestStack = stack
    nav.latestBack = rememberUpdatedState(onBack).value
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    LaunchedEffect(stack) { nav.follow(stack, holder) }
    val idle = nav.shown == stack
    val canGoBack = idle && stack.size > 1
    PredictiveBackHandler(enabled = canGoBack) { events ->
        try {
            events.collect { nav.progress.snapTo(1f - it.progress * BACK_PEEK) }
            nav.finishBack()
        } catch (cancelled: CancellationException) {
            nav.progress.animateTo(1f, spring(dampingRatio = PUSH_DAMPING))
            throw cancelled
        }
    }
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { nav.width = it.width.toFloat().coerceAtLeast(1f) }
            .then(if (swipeBack && canGoBack) Modifier.edgeSwipe(nav, direction, scope) else Modifier),
    ) {
        val top = nav.shown.last()
        val below = nav.shown.getOrNull(nav.shown.size - 2)
        // 진행률은 그리기 단계에서 읽고, 아래 화면을 보일지만 컴포지션에서 정해 애니메이션 중 매 프레임 재구성하지 않는다.
        val moving by remember { derivedStateOf { nav.progress.value < 1f } }
        val visible = if (below != null && moving) listOf(below, top) else listOf(top)
        // 맨 위 화면이 아래 화면으로 바뀔 때 새로 만들지 않고 그대로 옮겨지도록 같은 위치에서 key로 그린다. 그래야 스크롤 같은 상태가 유지된다.
        visible.forEach { screen ->
            key(screen) {
                val layer = if (screen == top) {
                    Modifier.pushedLayer(direction, { nav.width }, { nav.progress.value })
                } else {
                    Modifier.coveredLayer(direction, { nav.width }, { nav.progress.value })
                }
                Box(Modifier.fillMaxSize().then(layer)) { holder.SaveableStateProvider(screen) { content(screen) } }
            }
        }
    }
}

/** 화면에 그리는 스택([shown])과 전환 진행률. [shown]은 빼기 애니메이션이 끝날 때까지 이전 스택을 유지한다. */
private class PushState<T : Any>(initial: List<T>) {
    var shown by mutableStateOf(initial)
    var width by mutableFloatStateOf(1f)

    // 1이면 맨 위 화면이 제자리에 있고, 0이면 옆으로 완전히 빠져 아래 화면만 보인다.
    val progress = Animatable(1f)
    var latestStack: List<T> = initial
    var latestBack: () -> Unit = {}

    suspend fun follow(stack: List<T>, holder: SaveableStateHolder) {
        val previous = shown
        if (stack == previous) {
            // 빼는 도중 원래 스택으로 돌아오거나 호스트가 뒤로 가기를 거절한 경우다. 반쯤 빠진 채 멈추지 않게 되돌린다.
            progress.animateTo(1f, spring(dampingRatio = PUSH_DAMPING, stiffness = Spring.StiffnessMediumLow))
            return
        }
        val popped = stack.size < previous.size && previous.take(stack.size) == stack
        if (popped) {
            progress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            shown = stack
            progress.snapTo(1f)
        } else {
            shown = stack
            if (stack.size > 1 && stack.last() != previous.last()) {
                progress.snapTo(0f)
                progress.animateTo(1f, spring(dampingRatio = PUSH_DAMPING, stiffness = Spring.StiffnessMediumLow))
            } else {
                progress.snapTo(1f)
            }
        }
        (previous - stack.toSet()).forEach { holder.removeState(it) }
    }

    /** 맨 위 화면을 끝까지 밀고 [latestBack]을 부른다. 다음 두 프레임 안에 스택이 그대로면 호스트가 거절한 것으로 보고 되돌린다. */
    suspend fun finishBack() {
        val before = latestStack
        progress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        latestBack()
        withFrameNanos { }
        withFrameNanos { }
        if (latestStack == before) progress.animateTo(1f, spring(dampingRatio = PUSH_DAMPING))
    }
}

/**
 * 시작 쪽 가장자리([EDGE_WIDTH])에서 시작한 가로 드래그만 가로챈다. 가로로 움직이기 전에 손을 떼면 탭으로 보고 그대로 아래로 넘기므로
 * 가장자리 근처의 뒤로 가기 버튼도 눌린다.
 */
private fun Modifier.edgeSwipe(nav: PushState<*>, direction: Float, scope: CoroutineScope): Modifier = pointerInput(direction) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val edge = EDGE_WIDTH.toPx()
        val fromEdge = if (direction > 0f) down.position.x <= edge else down.position.x >= size.width - edge
        if (!fromEdge) return@awaitEachGesture
        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            if (direction * over > 0f) change.consume()
        } ?: return@awaitEachGesture
        val tracker = VelocityTracker()
        var target = nav.progress.value
        horizontalDrag(start.id) { change ->
            tracker.addPosition(change.uptimeMillis, change.position)
            target = (target - direction * (change.position.x - change.previousPosition.x) / nav.width).coerceIn(0f, 1f)
            val value = target
            scope.launch { nav.progress.snapTo(value) }
            change.consume()
        }
        val velocity = direction * tracker.calculateVelocity().x
        scope.launch {
            if (target < BACK_THRESHOLD || velocity > BACK_FLING) {
                nav.finishBack()
            } else {
                nav.progress.animateTo(1f, spring(dampingRatio = PUSH_DAMPING))
            }
        }
    }
}

/** 밀려 들어오는 화면. 옆에서 들어오면서 앞쪽 가장자리에 그림자를 드리워 겹쳐 있는 깊이감을 준다. */
private fun Modifier.pushedLayer(direction: Float, width: () -> Float, progress: () -> Float): Modifier =
    graphicsLayer { translationX = direction * width() * (1f - progress()) }
        .drawWithContent {
            val p = progress()
            if (p < 1f) {
                val edge = EDGE_SHADOW.toPx()
                val x = if (direction > 0f) -edge else size.width
                val colors = listOf(Color.Transparent, Color.Black.copy(alpha = SHADOW_ALPHA * p))
                val brush = Brush.horizontalGradient(if (direction > 0f) colors else colors.reversed(), x, x + edge)
                drawRect(brush, Offset(x, 0f), Size(edge, size.height))
            }
            drawContent()
        }

/**
 * 가려지는 아래 화면. 조금 물러나며 어두워지고, TalkBack에서는 숨긴다. 전환 중에 위 화면의 빈 곳을 눌러도
 * 아래 화면 버튼이 눌리지 않도록 터치를 모두 소비한다.
 */
private fun Modifier.coveredLayer(direction: Float, width: () -> Float, progress: () -> Float): Modifier =
    graphicsLayer { translationX = -direction * width() * PARALLAX * progress() }
        .drawWithContent {
            drawContent()
            drawRect(Color.Black.copy(alpha = DIM * progress()))
        }
        .clearAndSetSemantics { }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }

private const val PARALLAX = 0.3f
private const val DIM = 0.18f
private const val SHADOW_ALPHA = 0.22f
private val EDGE_SHADOW = 16.dp
private val EDGE_WIDTH = 24.dp
private const val PUSH_DAMPING = 0.92f
private const val BACK_PEEK = 0.6f
private const val BACK_THRESHOLD = 0.6f
private const val BACK_FLING = 1200f
