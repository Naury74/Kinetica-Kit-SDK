package com.naury.kinetica.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 내비게이션 스택처럼 화면을 쌓는 컨테이너. [stack]에 화면을 추가하면 [transition]으로 새 화면이 들어오고, 화면을 빼면 같은 전환을 되감는다.
 * 기본 전환은 옆으로 밀기([KTransitions.Push])라 새 화면이 옆에서 밀려 들어오고 아래 화면은 조금 물러나며 어두워진다.
 * 화면 시작 쪽 가장자리에서 가로로 드래그하거나 예측형 뒤로 가기(Android 14 이상) 제스처를 하면 손가락을 따라 전환이 되감기고,
 * 충분히 드래그하면 [onBack]을 호출한다. '애니메이션 삭제' 설정이 켜져 있으면 손을 뗀 뒤나 화면을 바꿀 때 전환 없이 바로 바꾼다.
 *
 * 아래 화면의 `rememberSaveable` 상태는 다시 보일 때까지 유지되고, [stack]에서 빠진 화면의 상태는 지운다.
 * 모든 화면이 이 컴포저블을 부른 곳의 ViewModelStoreOwner와 Lifecycle을 함께 쓴다. 화면마다 따로 ViewModel이 필요하면
 * `viewModel(key = …)`로 화면별 키를 준다. 전환하는 동안 가려지는 화면은 터치를 받지 않고 TalkBack에도 드러나지 않는다.
 *
 * ```kotlin
 * var stack by rememberSaveable { mutableStateOf(listOf("list")) }
 * PushNavigation(stack, onBack = { stack = stack.dropLast(1) }, transition = KTransitions.SheetStack) { screen -> ... }
 * ```
 *
 * @param stack 아래부터 위 순서로 쌓인 화면. 비어 있거나 같은 항목이 두 번 들어 있으면 [IllegalArgumentException]을 던진다.
 *   항목은 화면을 구분하는 키로도 쓰므로 `rememberSaveable`에 저장할 수 있는 값이어야 한다.
 * @param onBack 맨 위 화면을 빼 달라는 요청. [stack]에 화면이 하나뿐이면 호출하지 않고, 뒤로 가기는 호스트가 처리한다.
 *   호스트가 [stack]을 바꾸지 않으면(예: 저장 확인 다이얼로그를 띄움) 맨 위 화면이 제자리로 돌아온다.
 * @param swipeBack 가장자리에서 드래그해 뒤로 가는 동작을 쓸지 여부
 * @param transition 화면을 넣고 뺄 때의 전환. 바꾸면 다음 전환부터 적용된다.
 * @param paneTitle 화면의 이름. 주면 화면이 바뀔 때 TalkBack이 새 화면의 이름을 읽는다.
 */
@Composable
public fun <T : Any> PushNavigation(
    stack: List<T>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    swipeBack: Boolean = true,
    transition: KScreenTransition = KTransitions.Push,
    paneTitle: ((T) -> String?)? = null,
    content: @Composable (T) -> Unit,
) {
    require(stack.isNotEmpty()) { "stack이 비어 있으면 안 됩니다." }
    require(stack.toSet().size == stack.size) { "stack에 같은 화면이 두 번 들어 있습니다: $stack" }
    val holder = rememberSaveableStateHolder()
    val nav = remember { PushState(stack) }
    nav.latestStack = stack
    nav.latestBack = rememberUpdatedState(onBack).value
    nav.spec = transitionSpecOf(transition)
    nav.still = reducedMotion()
    val host = remember { TransitionHost() }
    host.endSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    host.shapes = currentShapes
    LaunchedEffect(stack) { nav.follow(stack, holder) }
    val idle = nav.shown == stack
    val canGoBack = idle && stack.size > 1
    PredictiveBackHandler(enabled = canGoBack) { events ->
        try {
            events.collect { nav.progress.snapTo(1f - it.progress * BACK_PEEK) }
            nav.finishBack()
        } catch (cancelled: CancellationException) {
            // 제스처를 취소하면 이 코루틴도 취소되므로 되돌리는 애니메이션은 취소되지 않게 돌린다.
            withContext(NonCancellable) { nav.settle(1f) }
            throw cancelled
        }
    }
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                nav.width = it.size.width.toFloat().coerceAtLeast(1f)
                host.screenOrigin = it.positionOnScreen()
            }
            .then(if (swipeBack && canGoBack) Modifier.edgeSwipe(nav, host.endSign, scope) else Modifier)
            // 가장자리 끌기 도중 스택이 바뀌어 제스처가 끊기면 반쯤 밀린 채 남으므로 제자리로 돌린다.
            .settleAfterRelease({ nav.progress.value.let { it > 0f && it < 1f } && !nav.progress.isRunning }) {
                scope.launch { nav.settle(1f) }
            },
    ) {
        val top = nav.shown.last()
        val below = nav.shown.getOrNull(nav.shown.size - 2)
        // 진행률은 그리기 단계에서 읽고, 아래 화면을 보일지만 컴포지션에서 정해 애니메이션 중 매 프레임 재구성하지 않는다.
        val moving by remember { derivedStateOf { nav.progress.value < 1f } }
        val visible = if (below != null && moving) listOf(below, top) else listOf(top)
        // 맨 위 화면이 아래 화면으로 바뀔 때 새로 만들지 않고 그대로 옮겨지도록 같은 위치에서 key로 그린다. 그래야 스크롤 같은 상태가 유지된다.
        visible.forEach { screen ->
            key(screen) {
                val title = if (screen == top) paneTitle?.invoke(screen) else null
                val layer = Modifier.stackLayer(transition, screen == top, host, title) { nav.progress.value }
                Box(Modifier.fillMaxSize().then(layer)) { holder.SaveableStateProvider(screen) { content(screen) } }
            }
        }
    }
}

/** 맨 위 화면이면 들어오는 화면으로, 아니면 가려지는 화면으로 [transition]을 적용한다. [title]이 있으면 창 제목으로 알린다. */
private fun Modifier.stackLayer(
    transition: KScreenTransition,
    isTop: Boolean,
    host: TransitionHost,
    title: String?,
    progress: () -> Float,
): Modifier = if (isTop) {
    screenTransition(transition, TransitionRole.Entering, host, progress)
        .then(if (title != null) Modifier.semantics { paneTitle = title } else Modifier)
} else {
    zIndex(if (transition.enteringOnTop) 0f else 1f)
        .screenTransition(transition, TransitionRole.Exiting, host, progress)
        .coveredLayer()
}

/** 화면에 그리는 스택([shown])과 전환 진행률. [shown]은 빼기 애니메이션이 끝날 때까지 이전 스택을 유지한다. */
private class PushState<T : Any>(initial: List<T>) {
    var shown by mutableStateOf(initial)
    var width by mutableFloatStateOf(1f)

    // 1이면 맨 위 화면이 제자리에 있고, 0이면 완전히 빠져 아래 화면만 보인다.
    val progress = Animatable(1f)
    var latestStack: List<T> = initial
    var latestBack: () -> Unit = {}
    var spec: AnimationSpec<Float> = DefaultTransitionSpring
    var still = false

    /** 진행률을 [target]으로 옮긴다. '애니메이션 삭제' 설정이면 바로 옮긴다. */
    suspend fun settle(target: Float) {
        if (still) progress.snapTo(target) else progress.animateTo(target, spec)
    }

    suspend fun follow(stack: List<T>, holder: SaveableStateHolder) {
        val previous = shown
        if (stack == previous) {
            // 빼는 도중 원래 스택으로 돌아오거나 호스트가 뒤로 가기를 거절한 경우다. 반쯤 빠진 채 멈추지 않게 되돌린다.
            settle(1f)
            return
        }
        val popped = stack.size < previous.size && previous.take(stack.size) == stack
        if (popped) {
            settle(0f)
            shown = stack
            progress.snapTo(1f)
        } else {
            shown = stack
            if (stack.size > 1 && stack.last() != previous.last()) {
                progress.snapTo(0f)
                settle(1f)
            } else {
                progress.snapTo(1f)
            }
        }
        (previous - stack.toSet()).forEach { holder.removeState(it) }
    }

    /** 맨 위 화면을 끝까지 빼고 [latestBack]을 부른다. 다음 두 프레임 안에 스택이 그대로면 호스트가 거절한 것으로 보고 되돌린다. */
    suspend fun finishBack() {
        val before = latestStack
        settle(0f)
        latestBack()
        withFrameNanos { }
        withFrameNanos { }
        if (latestStack == before) settle(1f)
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
            if (target < BACK_THRESHOLD || velocity > BACK_FLING) nav.finishBack() else nav.settle(1f)
        }
    }
}

/**
 * 가려지는 아래 화면은 TalkBack에서 숨긴다. 전환 중에 위 화면의 빈 곳을 눌러도 아래 화면 버튼이 눌리지 않도록 터치를 모두 소비한다.
 */
internal fun Modifier.coveredLayer(): Modifier = clearAndSetSemantics { }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

private val EDGE_WIDTH = 24.dp
private const val BACK_PEEK = 0.6f
private const val BACK_THRESHOLD = 0.6f
private const val BACK_FLING = 1200f
