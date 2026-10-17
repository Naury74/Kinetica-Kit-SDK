// 파일 이름은 대표 API인 KStoryViewer을(를) 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [KStoryViewer]의 페이지와 진행 상태. [rememberKStoryViewerState]로 만든다. 지금 페이지는 화면 회전 뒤에도 남는다.
 *
 * @param pageCount 페이지 수. 1보다 작으면 [IllegalArgumentException]을 던진다.
 */
@Stable
public class KStoryViewerState(public val pageCount: Int, initialPage: Int = 0) {
    init {
        require(pageCount >= 1) { "pageCount는 1 이상이어야 합니다." }
    }

    /** 지금 페이지(0부터). */
    public var page: Int by mutableIntStateOf(initialPage.coerceIn(0, pageCount - 1))
        private set

    /** 지금 페이지가 흐른 정도(0..1). 그리기 단계에서 읽으면 프레임마다 재구성하지 않는다. */
    public val progress: Float get() = elapsed.floatValue

    /** 호출한 쪽이 멈춘 상태. 답장 입력란에 초점이 있는 동안처럼 넘김을 멈출 때 `true`로 둔다. 길게 누르는 동안의 멈춤과는 따로다. */
    public var paused: Boolean by mutableStateOf(false)

    internal var held by mutableStateOf(false)
    internal val elapsed = mutableFloatStateOf(0f)

    /** 다음 페이지로 넘긴다. 마지막 페이지면 넘기지 않고 `false`를 돌려준다. */
    public fun next(): Boolean {
        if (page >= pageCount - 1) return false
        page++
        elapsed.floatValue = 0f
        return true
    }

    /** 이전 페이지로 넘긴다. 첫 페이지면 그 페이지를 처음부터 다시 보여 준다. */
    public fun previous() {
        if (page > 0) page--
        elapsed.floatValue = 0f
    }

    /** [target] 페이지로 바로 간다. 범위를 벗어나면 가장 가까운 페이지로 맞춘다. */
    public fun jumpTo(target: Int) {
        page = target.coerceIn(0, pageCount - 1)
        elapsed.floatValue = 0f
    }

    internal companion object {
        fun saver(pageCount: Int): Saver<KStoryViewerState, Int> = Saver({ it.page }, { KStoryViewerState(pageCount, it) })
    }
}

/** [KStoryViewerState]를 만든다. [pageCount]가 바뀌면 새로 만든다. */
@Composable
public fun rememberKStoryViewerState(pageCount: Int, initialPage: Int = 0): KStoryViewerState =
    rememberSaveable(pageCount, saver = KStoryViewerState.saver(pageCount)) { KStoryViewerState(pageCount, initialPage) }

/**
 * 인스타그램 방식의 전체 화면 스토리 뷰어. 위쪽 분할 진행 막대가 [durationMs]마다 차며 다음 페이지로 넘어가고, 마지막 페이지가 끝나면
 * [onDismissRequest]를 부른다. 화면 시작 쪽 3분의 1을 누르면 이전, 나머지를 누르면 다음 페이지로 가고(RTL에서는 좌우가 바뀐다),
 * 길게 누르는 동안 멈춘다. 아래로 끌어 놓으면 닫히고, 덜 끌면 제자리로 돌아온다. 예측형 뒤로 가기 제스처를 하면 손가락을 따라 화면이
 * 작아지다가 놓으면 닫힌다. 열릴 때는 커지며 나타난다.
 *
 * 접근성 설정의 '조치를 취할 시간'이 길면 페이지 시간도 그만큼 늘어난다. TalkBack에서는 '스토리 5개 중 2번째'를 상태로 읽고,
 * 다음, 이전, 일시정지, 닫기를 사용자 지정 동작으로 제공한다.
 * '애니메이션 삭제' 설정에서는 열고 닫는 전환 없이 바로 나타나고 사라진다. 자동 넘김은 그대로다.
 *
 * ```kotlin
 * val story = rememberKStoryViewerState(pages.size)
 * KStoryViewer(open, { open = false }, story, header = { StoryHeader(user) }) { page -> Image(pages[page], null, Modifier.fillMaxSize()) }
 * ```
 *
 * @param durationMs 한 페이지를 보여 주는 시간(ms). 0 이하면 [IllegalArgumentException]을 던진다.
 * @param header 진행 막대 아래 줄. 보통 아바타와 이름, 올린 시간을 놓는다. 끝 쪽 닫기 버튼은 뷰어가 붙인다.
 * @param footer 화면 아래 줄. 답장 입력란을 놓으면 키보드 위로 올라간다. 입력란에 초점이 있는 동안에는 [KStoryViewerState.paused]를 켠다.
 * @param content 페이지 내용. 화면 전체를 채운다. 글자는 흰색 위주로 쓴다.
 */
@Composable
public fun KStoryViewer(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    state: KStoryViewerState,
    modifier: Modifier = Modifier,
    durationMs: Long = 5_000L,
    header: @Composable (page: Int) -> Unit = {},
    footer: (@Composable (page: Int) -> Unit)? = null,
    content: @Composable (page: Int) -> Unit,
) {
    require(durationMs > 0) { "durationMs는 0보다 커야 합니다." }
    val timeout = rememberAccessibleTimeout(withControls = true)(durationMs)
    val dismiss = rememberUpdatedState(onDismissRequest)
    KOverlay(visible) { transition ->
        val gesture = remember { StoryGesture() }
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
        StoryClock(state, visible, timeout) { dismiss.value() }
        StoryFrame(transition, gesture, state, modifier, header, footer, content) { dismiss.value() }
    }
}

/** 끌어 닫기와 뒤로 가기 진행 값. 둘 다 그리기 단계에서 읽는다. */
private class StoryGesture {
    val drag = Animatable(0f)
    val back = Animatable(0f)

    suspend fun reset() {
        drag.snapTo(0f)
        back.snapTo(0f)
    }
}

/** 멈추지 않은 동안 프레임마다 진행을 더하고, 다 차면 다음 페이지로 넘기거나 닫는다. */
@Composable
private fun StoryClock(state: KStoryViewerState, visible: Boolean, durationMs: Long, onEnd: () -> Unit) {
    val running = visible && !state.paused && !state.held
    LaunchedEffect(state, state.page, running, durationMs) {
        if (!running) return@LaunchedEffect
        var last = -1L
        while (state.elapsed.floatValue < 1f) {
            withFrameNanos { now ->
                if (last >=
                    0
                ) {
                    state.elapsed.floatValue = (state.elapsed.floatValue + (now - last) / (durationMs * NANOS_PER_MS)).coerceAtMost(1f)
                }
                last = now
            }
        }
        if (!state.next()) onEnd()
    }
}

@Composable
private fun StoryFrame(
    transition: Transition<Boolean>,
    gesture: StoryGesture,
    state: KStoryViewerState,
    modifier: Modifier,
    header: @Composable (Int) -> Unit,
    footer: (@Composable (Int) -> Unit)?,
    content: @Composable (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val still = reducedMotion()
    val open by transition.animateFloat(
        transitionSpec = {
            if (still) {
                tween(0)
            } else if (targetState) {
                spring(dampingRatio = OPEN_DAMPING, stiffness = OPEN_STIFFNESS)
            } else {
                tween(CLOSE_MS)
            }
        },
        label = "story-open",
    ) { if (it) 1f else 0f }
    val position = stringResource(R.string.kinetica_ui_story_position, state.page + 1, state.pageCount)
    val actions = storyActions(state, onDismiss)
    val scope = rememberCoroutineScope()
    val shapes = currentShapes
    val corner = StoryCorner(shapes.cornerOf(shapes.sheet), shapes.radiusOr(shapes.sheet, CORNER))
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = open.coerceIn(0f, 1f) }
            .drawBehind { drawRect(Color.Black.copy(alpha = 1f - dragShare(gesture, size.height) * DRAG_FADE)) },
    ) {
        Box(
            modifier
                .fillMaxSize()
                .storyLayer(gesture, corner) { open }
                .background(Color.Black)
                .semantics {
                    paneTitle = position
                    stateDescription = position
                    customActions = actions
                }
                .storyGestures(state, gesture, scope, LocalLayoutDirection.current == LayoutDirection.Rtl, onDismiss),
        ) {
            key(state.page) { content(state.page) }
            CompositionLocalProvider(LocalKineticaContentColor provides Color.White) {
                StoryChrome(state, header, footer, onDismiss)
            }
        }
    }
}

/** 줄어든 페이지의 모서리. 바텀 시트 모양 토큰을 따른다. */
private class StoryCorner(val style: KCornerStyle, val radius: Dp)

/** 열림 정도와 끌기, 뒤로 가기에 따라 페이지를 키우고 줄이며 모서리를 둥글게 한다. */

private fun Modifier.storyLayer(gesture: StoryGesture, corner: StoryCorner, open: () -> Float): Modifier = graphicsLayer {
    val o = open().coerceIn(0f, 1f)
    val drag = dragShare(gesture, size.height)
    val back = gesture.back.value
    val scale = (OPEN_START_SCALE + (1f - OPEN_START_SCALE) * o) * (1f - back * BACK_SHRINK) * (1f - drag * DRAG_SHRINK)
    scaleX = scale
    scaleY = scale
    translationY = gesture.drag.value * DRAG_FOLLOW
    val rounded = maxOf(back, drag * DRAG_ROUND, 1f - o)
    shape = shapeOf(corner.style, corner.radius * rounded.coerceIn(0f, 1f))
    clip = rounded > 0f
}

private fun dragShare(gesture: StoryGesture, height: Float): Float = (gesture.drag.value / height.coerceAtLeast(1f)).coerceIn(0f, 1f)

/** 시작 쪽 3분의 1 누르기는 이전, 나머지는 다음, 길게 누르면 멈춤, 아래로 끌면 닫기. */
private fun Modifier.storyGestures(
    state: KStoryViewerState,
    gesture: StoryGesture,
    scope: CoroutineScope,
    rtl: Boolean,
    onDismiss: () -> Unit,
): Modifier = pointerInput(state, rtl) {
    detectTapGestures(
        onPress = {
            state.held = true
            tryAwaitRelease()
            state.held = false
        },
        onTap = { offset ->
            val back = if (rtl) offset.x > size.width * (1f - BACK_ZONE) else offset.x < size.width * BACK_ZONE
            if (back) {
                state.previous()
            } else if (!state.next()) {
                onDismiss()
            }
        },
    )
}.pointerInput(state) {
    detectVerticalDragGestures(
        onDragStart = { state.held = true },
        onDragEnd = {
            state.held = false
            if (gesture.drag.value > size.height * DISMISS_SHARE) {
                onDismiss()
            } else {
                scope.launch { gesture.drag.animateTo(0f, spring(dampingRatio = SETTLE_DAMPING)) }
            }
        },
        onDragCancel = {
            state.held = false
            scope.launch { gesture.drag.animateTo(0f) }
        },
    ) { change, amount ->
        change.consume()
        scope.launch { gesture.drag.snapTo((gesture.drag.value + amount).coerceAtLeast(0f)) }
    }
}

@Composable
private fun storyActions(state: KStoryViewerState, onDismiss: () -> Unit): List<CustomAccessibilityAction> {
    val next = stringResource(R.string.kinetica_ui_story_next)
    val previous = stringResource(R.string.kinetica_ui_story_previous)
    val pause = stringResource(if (state.paused) R.string.kinetica_ui_story_resume else R.string.kinetica_ui_story_pause)
    val close = stringResource(R.string.kinetica_ui_close)
    return listOf(
        CustomAccessibilityAction(next) {
            if (!state.next()) onDismiss()
            true
        },
        CustomAccessibilityAction(previous) {
            state.previous()
            true
        },
        CustomAccessibilityAction(pause) {
            state.paused = !state.paused
            true
        },
        CustomAccessibilityAction(close) {
            onDismiss()
            true
        },
    )
}

@Composable
private fun StoryChrome(
    state: KStoryViewerState,
    header: @Composable (Int) -> Unit,
    footer: (@Composable (Int) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .height(SEGMENT_HEIGHT)
                .drawBehind { drawSegments(state.pageCount, state.page, state.progress, rtl) },
        )
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { header(state.page) }
            StoryCloseButton(onDismiss)
        }
        Spacer(Modifier.weight(1f))
        footer?.let { Box(Modifier.fillMaxWidth().padding(12.dp)) { it(state.page) } }
    }
}

@Composable
private fun StoryCloseButton(onDismiss: () -> Unit) {
    val label = stringResource(R.string.kinetica_ui_close)
    val shape = shapeFor { button }
    Box(
        Modifier
            .size(KMetrics.IconButton)
            .clip(shape)
            .kClickable(shape, onClick = onDismiss)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            rememberVectorPainter(KGlyphs.Close),
            null,
            Modifier.size(KMetrics.IconButtonIcon),
            colorFilter = ColorFilter.tint(Color.White),
        )
    }
}

/** 분할 진행 막대. 지난 페이지는 꽉 차고 지금 페이지는 [progress]만큼 찬다. RTL이면 끝에서 시작 쪽으로 찬다. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSegments(count: Int, page: Int, progress: Float, rtl: Boolean) {
    val gap = SEGMENT_GAP.toPx()
    val width = (size.width - gap * (count - 1)) / count
    val radius = CornerRadius(size.height / 2)
    for (i in 0 until count) {
        val slot = if (rtl) count - 1 - i else i
        val left = slot * (width + gap)
        drawRoundRect(Color.White.copy(alpha = TRACK_ALPHA), Offset(left, 0f), Size(width, size.height), radius)
        val fill = when {
            i < page -> 1f
            i == page -> progress.coerceIn(0f, 1f)
            else -> 0f
        }
        if (fill > 0f) {
            val filled = width * fill
            val start = if (rtl) left + width - filled else left
            drawRoundRect(Color.White, Offset(start, 0f), Size(filled, size.height), radius)
        }
    }
}

private val SEGMENT_HEIGHT = 2.5.dp
private val SEGMENT_GAP = 4.dp
private val CORNER = 28.dp
private const val TRACK_ALPHA = 0.35f
private const val NANOS_PER_MS = 1_000_000f
private const val OPEN_DAMPING = 0.82f
private const val OPEN_STIFFNESS = 380f
private const val CLOSE_MS = 200
private const val OPEN_START_SCALE = 0.86f
private const val BACK_SHRINK = 0.12f
private const val DRAG_SHRINK = 0.25f
private const val DRAG_FOLLOW = 0.85f
private const val DRAG_FADE = 0.7f
private const val DRAG_ROUND = 4f
private const val BACK_ZONE = 1f / 3f
private const val DISMISS_SHARE = 0.18f
private const val SETTLE_DAMPING = 0.8f
