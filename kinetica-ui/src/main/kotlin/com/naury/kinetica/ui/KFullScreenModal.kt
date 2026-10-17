package com.naury.kinetica.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 아래에서 올라와 화면을 덮는 화면. 열리는 동안 [KineticaScaffold]의 콘텐츠는 살짝 작아지며 뒤로 물러난다.
 * [dragToDismiss]면 위쪽에 작은 손잡이가 보이고, 아래로 끌거나(안쪽 목록이 맨 위에 있을 때 이어서 끌어도 된다) 빠르게 던지면 닫힌다.
 * 덜 끌고 놓으면 제자리로 스프링 정착한다. 예측형 뒤로 가기 제스처를 하면 손가락을 따라 화면이 작아지며 모서리가 둥글어지고, 놓으면 닫힌다.
 * 키보드와 TalkBack 초점은 [KDialog]와 같은 규칙으로 안으로 들어가고, 닫히면 원래 자리로 돌아온다.
 * '애니메이션 삭제' 설정에서는 바로 나타나고 사라진다.
 *
 * 바탕은 표면 스타일의 화면 바탕색이다. 글래스 표면은 바탕이 투명하므로 밝기에 맞춘 흰색이나 짙은 회색을 칠한다.
 * 시스템 바 인셋은 [content]가 직접 넣는다.
 *
 * ```kotlin
 * KFullScreenModal(composing, { composing = false }, title = "새 게시물") { ComposeScreen(onClose = { composing = false }) }
 * ```
 *
 * @param title 화면 이름. TalkBack이 창 제목으로 읽는다.
 */
@Composable
public fun KFullScreenModal(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dragToDismiss: Boolean = true,
    title: String? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
    content: @Composable () -> Unit,
) {
    val dismiss = rememberUpdatedState(onDismissRequest)
    KOverlay(visible) { transition ->
        val scope = rememberCoroutineScope()
        val gesture = remember { ModalGesture(scope) { dismiss.value() } }
        LaunchedEffect(Unit) { gesture.reset() }
        PredictiveBackHandler(enabled = visible) { events ->
            try {
                events.collect { gesture.back.snapTo(it.progress) }
                dismiss.value()
            } catch (cancelled: CancellationException) {
                // 제스처를 취소하면 이 코루틴도 취소되므로 되돌리는 애니메이션은 취소되지 않게 돌린다.
                withContext(NonCancellable) { gesture.back.animateTo(0f) }
                throw cancelled
            }
        }
        ModalPanel(transition, gesture, dragToDismiss, title, surface, modifier, content)
    }
}

/**
 * 끌기 거리(px)와 뒤로 가기 진행. 안쪽 스크롤이 맨 위에서 남긴 아래 방향 스크롤도 끌기로 받는다.
 * 놓을 때 화면 높이의 [DISMISS_SHARE]를 넘었거나 아래로 빠르게 던졌으면 닫는다.
 */
private class ModalGesture(private val scope: CoroutineScope, private val onDismiss: () -> Unit) : NestedScrollConnection {
    var drag by mutableFloatStateOf(0f)
    var height = 1f
    var flingThreshold = 1f
    val back = Animatable(0f)

    suspend fun reset() {
        drag = 0f
        back.snapTo(0f)
    }

    fun dragBy(delta: Float) {
        drag = (drag + delta).coerceAtLeast(0f)
    }

    fun settle(velocity: Float) {
        if (drag <= 0f) return
        if (drag > height * DISMISS_SHARE || velocity > flingThreshold) {
            onDismiss()
        } else {
            scope.launch { animate(drag, 0f, animationSpec = spring(dampingRatio = SETTLE_DAMPING)) { value, _ -> drag = value } }
        }
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y >= 0f || drag <= 0f) return Offset.Zero
        val used = maxOf(available.y, -drag)
        dragBy(used)
        return Offset(0f, used)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        dragBy(available.y)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (drag <= 0f) return Velocity.Zero
        settle(available.y)
        return available
    }
}

@Composable
private fun ModalPanel(
    transition: Transition<Boolean>,
    gesture: ModalGesture,
    dragToDismiss: Boolean,
    title: String?,
    surface: SurfaceStyle,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val still = reducedMotion()
    val openState = transition.animateFloat(
        transitionSpec = {
            if (still) {
                tween(0)
            } else if (targetState) {
                spring(dampingRatio = 1f, stiffness = OPEN_STIFFNESS)
            } else {
                tween(CLOSE_MS)
            }
        },
        label = "modal-open",
    ) { if (it) 1f else 0f }
    ModalPushBack { openState.value.coerceIn(0f, 1f) * (1f - (gesture.drag / gesture.height).coerceIn(0f, 1f)) }
    val page = if (surface.background.alpha > 0f) surface.background else surface.inkBackground()
    val density = LocalDensity.current
    gesture.flingThreshold = with(density) { FLING.toPx() }
    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { gesture.height = it.height.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                val open = openState.value
                val shown = (1f - open).coerceIn(0f, 1f)
                val dragged = (gesture.drag / gesture.height).coerceIn(0f, 1f)
                translationY = size.height * maxOf(shown, dragged)
                val back = gesture.back.value
                val scale = 1f - back * BACK_SHRINK
                scaleX = scale
                scaleY = scale
                val rounded = maxOf(back, (dragged * DRAG_ROUND).coerceAtMost(1f), if (open < 1f) 1f else 0f)
                shape = RoundedCornerShape(CORNER.toPx() * rounded)
                clip = rounded > 0f
            }
            .semantics { title?.let { paneTitle = it } }
            .background(page)
            .then(
                if (dragToDismiss) {
                    Modifier
                        .nestedScroll(gesture)
                        .draggable(
                            rememberDraggableState { gesture.dragBy(it) },
                            Orientation.Vertical,
                            onDragStopped = { velocity -> gesture.settle(velocity) },
                        )
                } else {
                    Modifier
                },
            ),
    ) {
        CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor) { content() }
        if (dragToDismiss) Grabber(surface, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun Grabber(surface: SurfaceStyle, modifier: Modifier) {
    Box(
        modifier
            .statusBarsPadding()
            .padding(top = 6.dp)
            .size(GRABBER_WIDTH, GRABBER_HEIGHT)
            .background(surface.contentColor.copy(alpha = GRABBER_ALPHA), RoundedCornerShape(GRABBER_HEIGHT)),
    )
}

/** 뒤 화면을 물러나게 하는 값은 이 안에서만 읽어, 끄는 동안 패널 전체가 다시 구성되지 않게 한다. */
@Composable
private fun ModalPushBack(level: () -> Float) {
    ReportPushBack(level())
}

private val CORNER = 28.dp
private val FLING = 1_000.dp
private val GRABBER_WIDTH = 36.dp
private val GRABBER_HEIGHT = 5.dp
private const val GRABBER_ALPHA = 0.25f
private const val OPEN_STIFFNESS = 320f
private const val CLOSE_MS = 260
private const val DISMISS_SHARE = 0.25f
private const val SETTLE_DAMPING = 0.85f
private const val BACK_SHRINK = 0.1f
private const val DRAG_ROUND = 6f
