package com.naury.kinetica.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * iOS 내비게이션 스택처럼 화면을 밀어 넣는 컨테이너. [stack]에 화면을 추가하면 새 화면이 옆에서 밀려 들어오고,
 * 아래 화면은 조금 물러나며 어두워진다. 화면을 빼면 반대로 움직인다. 화면 가장자리에서 드래그하거나
 * 예측형 뒤로 가기(Android 14 이상) 제스처를 하면 손가락을 따라 이전 화면으로 돌아가고, 충분히 드래그하면 [onBack]을 호출한다.
 * 아래 화면의 `rememberSaveable` 상태는 다시 보일 때까지 유지된다.
 *
 * ```kotlin
 * var stack by rememberSaveable { mutableStateOf(listOf("list")) }
 * PushNavigation(stack, onBack = { stack = stack.dropLast(1) }) { screen -> ... }
 * ```
 *
 * @param stack 아래부터 위 순서로 쌓인 화면. 비어 있으면 [IllegalArgumentException]을 던진다. 항목은 화면을 구분하는 키로도 쓰므로
 *   `rememberSaveable`에 저장할 수 있는 값이어야 한다.
 * @param onBack 맨 위 화면을 빼 달라는 요청. [stack]에 화면이 하나뿐이면 호출하지 않고, 뒤로 가기는 호스트가 처리한다.
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
    val latestBack by rememberUpdatedState(onBack)
    val holder = rememberSaveableStateHolder()
    // 1이면 맨 위 화면이 제자리에 있고, 0이면 옆으로 완전히 빠져 아래 화면만 보인다.
    val progress = remember { Animatable(1f) }
    var shown by remember { mutableStateOf(stack) }
    var width by remember { mutableFloatStateOf(1f) }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    LaunchedEffect(stack) {
        if (stack == shown) return@LaunchedEffect
        val popped = stack.size < shown.size && shown.take(stack.size) == stack
        if (popped) {
            progress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            shown = stack
            progress.snapTo(1f)
        } else {
            shown = stack
            if (stack.size > 1) {
                progress.snapTo(0f)
                progress.animateTo(1f, spring(dampingRatio = PUSH_DAMPING, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }
    val canGoBack = shown.size > 1 && stack.size > 1
    PushBackHandler(canGoBack, progress) { latestBack() }
    Box(modifier.fillMaxSize().onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }) {
        val top = shown.last()
        val below = shown.getOrNull(shown.size - 2)
        val p = progress.value
        val visible = if (below != null && p < 1f) listOf(below, top) else listOf(top)
        // 맨 위 화면이 아래 화면으로 바뀔 때 새로 만들지 않고 그대로 옮겨지도록 같은 위치에서 key로 그린다. 그래야 스크롤 같은 상태가 유지된다.
        visible.forEach { screen ->
            key(screen) {
                val isTop = screen == top
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (isTop) {
                                Modifier.pushedLayer(direction, {
                                    width
                                }, { progress.value })
                            } else {
                                Modifier.coveredLayer(direction, { width }, { progress.value })
                            },
                        ),
                ) { holder.SaveableStateProvider(screen) { content(screen) } }
            }
        }
        if (swipeBack && canGoBack) EdgeSwipe(direction, { width }, progress, { latestBack() })
    }
}

/** 뒤로 가기 제스처를 따라 맨 위 화면을 옆으로 밀고, 제스처가 끝나면 [onBack]을 호출한다. 제스처를 취소하면 제자리로 돌아온다. */
@Composable
private fun PushBackHandler(enabled: Boolean, progress: Animatable<Float, *>, onBack: () -> Unit) {
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { progress.snapTo(1f - it.progress * BACK_PEEK) }
            progress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            onBack()
        } catch (cancelled: CancellationException) {
            progress.animateTo(1f, spring(dampingRatio = PUSH_DAMPING))
            throw cancelled
        }
    }
}

/** 시작 쪽 가장자리의 드래그 영역. 드래그하면 맨 위 화면이 손가락을 따라 옆으로 빠지고, 충분히 끌거나 플링하면 [onBack]을 호출한다. */
@Composable
private fun BoxScope.EdgeSwipe(direction: Float, width: () -> Float, progress: Animatable<Float, *>, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    // 드래그 값을 따로 누적해 그 값으로 맞춘다. Animatable 값을 읽어서 더하면 한 프레임에 드래그가 여러 번 들어올 때 값이 빠진다.
    val dragTarget = remember { floatArrayOf(1f) }
    Box(
        Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .width(EDGE_WIDTH)
            .draggable(
                rememberDraggableState { delta ->
                    dragTarget[0] = (dragTarget[0] - direction * delta / width()).coerceIn(0f, 1f)
                    val target = dragTarget[0]
                    scope.launch { progress.snapTo(target) }
                },
                Orientation.Horizontal,
                onDragStarted = { dragTarget[0] = progress.value },
                onDragStopped = { velocity ->
                    if (progress.value < BACK_THRESHOLD || direction * velocity > BACK_FLING) {
                        progress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        onBack()
                    } else {
                        progress.animateTo(1f, spring(dampingRatio = PUSH_DAMPING))
                    }
                },
            ),
    )
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
                drawRect(
                    Brush.horizontalGradient(
                        if (direction >
                            0f
                        ) {
                            colors
                        } else {
                            colors.reversed()
                        },
                        x,
                        x + edge,
                    ),
                    Offset(x, 0f),
                    Size(edge, size.height),
                )
            }
            drawContent()
        }

/** 가려지는 아래 화면. 조금 물러나며 어두워지고, TalkBack에서는 숨긴다. */
private fun Modifier.coveredLayer(direction: Float, width: () -> Float, progress: () -> Float): Modifier =
    graphicsLayer { translationX = -direction * width() * PARALLAX * progress() }
        .drawWithContent {
            drawContent()
            drawRect(Color.Black.copy(alpha = DIM * progress()))
        }
        .clearAndSetSemantics { }

private const val PARALLAX = 0.3f
private const val DIM = 0.18f
private const val SHADOW_ALPHA = 0.22f
private val EDGE_SHADOW = 16.dp
private val EDGE_WIDTH = 24.dp
private const val PUSH_DAMPING = 0.92f
private const val BACK_PEEK = 0.6f
private const val BACK_THRESHOLD = 0.6f
private const val BACK_FLING = 1200f
