package com.naury.kinetica.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** [KVoiceRecorderButton]이 녹음하는 단계. */
public enum class KVoiceRecorderPhase {
    /** 녹음하지 않는다. */
    Idle,

    /** 손가락으로 누른 채 녹음 중이다. 떼면 보낸다. */
    Recording,

    /** 위로 밀어 잠갔다. 손을 떼도 녹음이 이어지고, 버튼을 다시 누르면 보낸다. */
    Locked,
}

/**
 * [KVoiceRecorderButton]의 녹음 단계와 시간. 같은 상태를 [KVoiceRecordingBar]에 넘기면 입력란 자리에 녹음 시간과 소리 막대,
 * 밀어서 취소 안내를 함께 보여 준다. 실제 녹음은 하지 않고 단계가 바뀔 때 버튼에 넘긴 콜백만 부른다. 메인 스레드에서 쓴다.
 */
@Stable
public class KVoiceRecorderState internal constructor(private val clock: () -> Long) {
    public constructor() : this(SystemClock::elapsedRealtime)

    /** 지금 단계. */
    public var phase: KVoiceRecorderPhase by mutableStateOf(KVoiceRecorderPhase.Idle)
        private set

    /** 녹음 중이거나 잠긴 녹음 중이면 true. */
    public val isActive: Boolean get() = phase != KVoiceRecorderPhase.Idle

    private var startedAt = 0L
    internal var callbacks: RecorderCallbacks = RecorderCallbacks.None

    // 손가락이 시작 쪽과 위쪽으로 민 거리(px). 그리기 단계에서만 읽는다.
    internal val slide = SettleOffset()
    internal val lift = SettleOffset()
    internal var cancelDistance = 1f
    internal var lockDistance = 1f
    internal var hint by mutableStateOf(0)

    /** 녹음을 시작한 뒤 흐른 시간(ms). 녹음 중이 아니면 0. 그리기 단계나 타이머에서 읽는다. */
    public fun elapsedMillis(): Long = if (isActive) (clock() - startedAt).coerceAtLeast(0L) else 0L

    internal fun start(): Boolean {
        if (phase != KVoiceRecorderPhase.Idle) return false
        startedAt = clock()
        phase = KVoiceRecorderPhase.Recording
        callbacks.onStart()
        return true
    }

    internal fun lock() {
        if (phase != KVoiceRecorderPhase.Recording) return
        phase = KVoiceRecorderPhase.Locked
        callbacks.onLock()
    }

    /** 녹음을 버린다. 녹음 중이 아니면 아무것도 하지 않는다. 버튼의 onCancel을 부른다. */
    public fun cancel() {
        if (!isActive) return
        phase = KVoiceRecorderPhase.Idle
        callbacks.onCancel()
    }

    /** 녹음을 끝내고 보낸다. 녹음 중이 아니면 아무것도 하지 않는다. 버튼의 onSend에 흐른 시간(ms)을 넘긴다. */
    public fun send() {
        if (!isActive) return
        val duration = elapsedMillis()
        phase = KVoiceRecorderPhase.Idle
        callbacks.onSend(duration)
    }
}

/** 버튼이 넘긴 콜백 묶음. */
internal class RecorderCallbacks(val onStart: () -> Unit, val onLock: () -> Unit, val onCancel: () -> Unit, val onSend: (Long) -> Unit) {
    companion object {
        val None = RecorderCallbacks({}, {}, {}, {})
    }
}

/** 화면이 다시 구성되어도 같은 [KVoiceRecorderState]를 돌려준다. 설정 변경(회전 등)에서는 녹음 단계를 지키지 않는다. */
@Composable
public fun rememberKVoiceRecorderState(): KVoiceRecorderState = remember { KVoiceRecorderState() }

/**
 * 메신저식 음성 녹음 버튼. 길게 누르면 녹음을 시작하고, 누른 채 위로 밀면 잠가서 손을 떼도 이어 녹음한다.
 * 시작 쪽으로 밀면 취소하고, 그냥 떼면 보낸다. 잠긴 뒤에는 버튼이 보내기 버튼으로 바뀐다. 누르는 동안 버튼 위에 잠금 레일이 나오고,
 * 짧게 누르면 '길게 눌러 녹음하세요' 안내가 잠깐 뜬다. 레일과 안내는 버튼 크기 밖에 그리므로 위쪽에 자리를 둔다. RTL에서는 취소 방향이 오른쪽이다.
 *
 * 실제 녹음은 하지 않는다. 단계가 바뀔 때 콜백을 부르고, [level]은 그리기 단계에서만 읽어 소리 크기만큼 버튼 둘레를 부풀린다.
 * TalkBack에서는 길게 누를 수 없으므로 두 번 탭하면 잠긴 녹음을 시작하고, 다시 두 번 탭하면 보낸다. 취소는 사용자 정의 동작이다.
 * 키보드 Enter와 Space도 같다. '애니메이션 삭제' 설정에서는 둘레가 부풀지 않는다.
 *
 * 입력란 자리에 녹음 시간과 소리 막대를 보이려면 같은 [state]를 [KVoiceRecordingBar]에 넘긴다.
 *
 * ```kotlin
 * val recorder = rememberKVoiceRecorderState()
 * KVoiceRecorderButton(
 *     onStart = mic::start,
 *     onSend = { ms -> mic.finish(ms) },
 *     onCancel = mic::discard,
 *     level = mic::level,
 *     state = recorder,
 * )
 * ```
 *
 * @param onStart 녹음을 시작할 때
 * @param onSend 녹음을 보낼 때. 녹음 시간(ms)을 받는다.
 * @param onLock 위로 밀어 잠갔을 때. TalkBack으로 시작하면 [onStart] 바로 뒤에 부른다.
 * @param onCancel 시작 쪽으로 밀었거나 [KVoiceRecorderState.cancel]을 불렀을 때. 녹음 중에 버튼이 화면에서 빠져도 부른다.
 * @param level 지금 소리 크기(0..1). 범위를 넘으면 잘라 쓴다.
 * @param accent 녹음 중 버튼 색. 지정하지 않으면 테마 강조색이다.
 */
@Composable
public fun KVoiceRecorderButton(
    onStart: () -> Unit,
    onSend: (durationMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    onLock: () -> Unit = {},
    onCancel: () -> Unit = {},
    level: () -> Float = { 0f },
    state: KVoiceRecorderState = rememberKVoiceRecorderState(),
    enabled: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
    accent: Color = Color.Unspecified,
) {
    val callbacks = RecorderCallbacks(onStart, onLock, onCancel, onSend)
    SideEffect { state.callbacks = callbacks }
    DisposableEffect(state) { onDispose { state.cancel() } }
    val density = LocalDensity.current
    state.cancelDistance = with(density) { CANCEL_DISTANCE.toPx() }
    state.lockDistance = with(density) { LOCK_DISTANCE.toPx() }
    val color = if (accent.isSpecified) accent else KineticaTheme.colors.accent
    val look = RecorderLook(surface, color, KineticaTheme.colors.onAccent)
    val latestLevel by rememberUpdatedState(level)
    val active = remember { Animatable(0f) }
    val still = reducedMotion()
    LaunchedEffect(state.isActive, still) {
        val target = if (state.isActive) 1f else 0f
        if (still) active.snapTo(target) else active.animateTo(target, spring(RECORD_DAMPING, Spring.StiffnessMedium))
    }
    HintTimer(state)
    Layout(
        content = {
            RecorderCore(state, look, active, { latestLevel() }, enabled)
            LockRail(state, look, active)
            HintBubble(state, surface)
        },
        // 이름과 누르기 동작은 호스트가 넘긴 수정자와 같은 노드에 둔다. 그래야 호스트가 붙인 testTag나 semantics가 버튼 노드에 붙는다.
        modifier = modifier.recorderSemantics(state, enabled),
    ) { measurables, constraints ->
        val loose = Constraints()
        val core = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val rail = measurables[1].measure(loose)
        val hint = measurables[2].measure(loose)
        val gap = RAIL_GAP.roundToPx()
        layout(core.width, core.height) {
            core.placeRelative(0, 0)
            // 레일과 안내는 버튼 크기에 넣지 않는다. 입력줄 높이가 녹음할 때 바뀌지 않게 하기 위해서다.
            rail.placeRelative((core.width - rail.width) / 2, -rail.height - gap)
            hint.placeRelative(core.width - hint.width, -hint.height - gap)
        }
    }
}

/** 버튼 색. */
private class RecorderLook(val surface: SurfaceStyle, val accent: Color, val onAccent: Color)

@Composable
private fun RecorderCore(
    state: KVoiceRecorderState,
    look: RecorderLook,
    active: Animatable<Float, *>,
    level: () -> Float,
    enabled: Boolean,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val still = reducedMotion()
    val clock = rememberLoopClock(running = state.isActive && !still)
    val haptic = kHapticFeedback()
    val scope = rememberCoroutineScope()
    val locked = state.phase == KVoiceRecorderPhase.Locked
    Box(
        Modifier
            .size(KMetrics.IconButton)
            .loopVisibility(clock)
            // 누르기는 움직이는 레이어보다 바깥에서 받아야 손가락 좌표가 버튼을 따라 흔들리지 않는다.
            .then(if (enabled) Modifier.recorderGesture(state, rtl, haptic, scope) else Modifier)
            .graphicsLayer {
                // 손가락을 따라오되 멀리 가지 않게 절반만 따른다.
                val dir = if (rtl) 1f else -1f
                translationX = dir * state.slide.value * FOLLOW
                translationY = -state.lift.value * FOLLOW
                val scale = 1f + (ACTIVE_SCALE - 1f) * active.value
                scaleX = scale
                scaleY = scale
            }
            .kSurface(look.surface, CircleShape, intensity = 1f - active.value.coerceIn(0f, 1f))
            .drawBehind {
                val p = active.value.coerceIn(0f, 1f)
                if (p <= 0f) return@drawBehind
                val radius = size.minDimension / 2
                if (!still && state.isActive) {
                    // 시계를 읽어 두어야 소리 크기가 바뀔 때마다 다시 그린다.
                    val swell = if (clock.seconds >= 0f) level().coerceIn(0f, 1f) else 0f
                    drawCircle(look.accent.copy(alpha = HALO_ALPHA * p), radius * (1f + HALO_GROW * swell))
                }
                drawCircle(look.accent, radius * p)
            },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (state.isActive) look.onAccent else look.surface.contentColor
        RecorderIcon(if (locked) SocialGlyphs.ArrowUp else SocialGlyphs.Mic, tint)
    }
}

@Composable
private fun RecorderIcon(icon: ImageVector, tint: Color) {
    Image(rememberVectorPainter(icon), null, Modifier.size(KMetrics.IconButtonIcon), colorFilter = ColorFilter.tint(tint))
}

/** 버튼이 TalkBack에 내보낼 이름. */
private class RecorderLabels(val record: String, val start: String, val send: String, val cancel: String, val recording: String)

@Composable
private fun rememberRecorderLabels(): RecorderLabels = RecorderLabels(
    stringResource(R.string.kinetica_ui_voice_record),
    stringResource(R.string.kinetica_ui_voice_start),
    stringResource(R.string.kinetica_ui_voice_send),
    stringResource(R.string.kinetica_ui_voice_cancel),
    stringResource(R.string.kinetica_ui_voice_recording),
)

/** TalkBack과 키보드의 누르기. 녹음 중이 아니면 잠긴 녹음을 시작하고, 녹음 중이면 보낸다. */
private fun KVoiceRecorderState.toggle(): Boolean {
    if (isActive) {
        send()
    } else if (start()) {
        lock()
    }
    return true
}

@Composable
private fun Modifier.recorderSemantics(state: KVoiceRecorderState, enabled: Boolean): Modifier {
    val labels = rememberRecorderLabels()
    val active = state.isActive
    val cancel = remember(state, labels.cancel) {
        listOf(
            CustomAccessibilityAction(labels.cancel) {
                state.cancel()
                true
            },
        )
    }
    return this
        .clearAndSetSemantics {
            role = Role.Button
            contentDescription = if (active) labels.send else labels.record
            if (active) stateDescription = labels.recording
            if (!enabled) disabled()
            onClick(if (active) labels.send else labels.start) { enabled && state.toggle() }
            if (active) customActions = cancel
        }
        .focusable(enabled)
        .onKeyEvent { event ->
            val press = enabled && event.key in PRESS_KEYS
            if (press && event.type == KeyEventType.KeyUp) state.toggle()
            press
        }
}

/**
 * 누르고 있다가 [android.view.ViewConfiguration]의 길게 누르기 시간이 지나면 녹음을 시작한다. 그 전에 떼면 안내를 띄우고,
 * 그 전에 부모가 스크롤로 가져가면 아무것도 하지 않는다. 잠긴 뒤의 탭은 보내기다.
 */
private fun Modifier.recorderGesture(state: KVoiceRecorderState, rtl: Boolean, haptic: HapticFeedback, scope: CoroutineScope): Modifier =
    pointerInput(state, rtl) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (state.phase == KVoiceRecorderPhase.Locked) {
                if (waitForUpOrCancellation() != null) {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    state.send()
                }
                return@awaitEachGesture
            }
            var released = false
            val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation().also { released = true }
            }
            if (released) {
                if (early != null) state.hint++
                return@awaitEachGesture
            }
            down.consume()
            if (!state.start()) return@awaitEachGesture
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            trackHold(state, down.id, down.position, rtl, haptic)
            state.slide.settle(scope, 0f, spring(Spring.DampingRatioMediumBouncy))
            state.lift.settle(scope, 0f, spring(Spring.DampingRatioMediumBouncy))
        }
    }

/** 녹음을 시작한 손가락을 따라가며 잠금, 취소, 보내기를 정한다. */
private suspend fun AwaitPointerEventScope.trackHold(
    state: KVoiceRecorderState,
    id: PointerId,
    start: Offset,
    rtl: Boolean,
    haptic: HapticFeedback,
) {
    var decided = false
    var change = awaitPointerEvent().changes.firstOrNull { it.id == id }
    while (change != null && change.pressed) {
        change.consume()
        if (!decided) decided = followHold(state, change.position - start, rtl, haptic)
        change = awaitPointerEvent().changes.firstOrNull { it.id == id }
    }
    if (!decided && state.phase == KVoiceRecorderPhase.Recording) {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        state.send()
    }
}

/** 처음 누른 자리에서 [moved]만큼 간 손가락으로 버튼을 옮기고, 잠그거나 취소했으면 true를 돌려준다. */
private fun followHold(state: KVoiceRecorderState, moved: Offset, rtl: Boolean, haptic: HapticFeedback): Boolean {
    val toStart = (if (rtl) moved.x else -moved.x).coerceAtLeast(0f)
    val up = (-moved.y).coerceAtLeast(0f)
    // 한 번에 한 방향만 따른다. 비스듬히 밀면 많이 간 쪽을 고른다.
    state.slide.snap(if (toStart >= up) toStart else 0f)
    state.lift.snap(if (toStart >= up) 0f else up)
    return when {
        state.slide.value >= state.cancelDistance -> {
            haptic.performHapticFeedback(HapticFeedbackType.Reject)
            state.cancel()
            true
        }

        state.lift.value >= state.lockDistance -> {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            state.lock()
            true
        }

        else -> false
    }
}

/** 버튼 위에 나오는 잠금 레일. 위로 밀수록 갈매기표가 자물쇠 쪽으로 올라간다. */
@Composable
private fun LockRail(state: KVoiceRecorderState, look: RecorderLook, active: Animatable<Float, *>) {
    val locked = state.phase == KVoiceRecorderPhase.Locked
    Column(
        Modifier
            .graphicsLayer {
                val p = active.value.coerceIn(0f, 1f)
                alpha = p
                translationY = (1f - p) * RAIL_RISE.toPx() - state.lift.value * FOLLOW
            }
            .clearAndSetSemantics { }
            .kSurface(look.surface.forPopup(), CircleShape)
            .width(RAIL_WIDTH)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RecorderIcon(KIcons.Lock, if (locked) look.accent else look.surface.contentColor)
        if (!locked) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .graphicsLayer {
                        val p = (state.lift.value / state.lockDistance).coerceIn(0f, 1f)
                        translationY = -p * CHEVRON_TRAVEL.toPx()
                        alpha = 1f - p * CHEVRON_FADE
                    },
            ) { RecorderIcon(KIcons.ChevronUp, look.surface.muted(MUTED_ALPHA)) }
        }
    }
}

/** 짧게 눌렀을 때 뜨는 안내. */
@Composable
private fun HintBubble(state: KVoiceRecorderState, surface: SurfaceStyle) {
    val shown = state.hint > 0 && !state.isActive
    if (!shown) {
        Box(Modifier)
        return
    }
    ThemedText(
        stringResource(R.string.kinetica_ui_voice_hold),
        Modifier
            .kSurface(surface.forPopup(), CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = TextStyle(color = surface.contentColor, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        maxLines = 1,
    )
}

@Composable
private fun HintTimer(state: KVoiceRecorderState) {
    val hint = state.hint
    LaunchedEffect(hint) {
        if (hint > 0) {
            delay(HINT_MS)
            state.hint = 0
        }
    }
}

private const val FOLLOW = 0.5f
private val PRESS_KEYS = setOf(Key.Enter, Key.NumPadEnter, Key.Spacebar)
private const val ACTIVE_SCALE = 1.3f
private const val HALO_ALPHA = 0.28f
private const val HALO_GROW = 0.5f
private const val RECORD_DAMPING = 0.7f
private const val MUTED_ALPHA = 0.55f
private const val CHEVRON_FADE = 0.8f
private const val HINT_MS = 1_600L
private val CANCEL_DISTANCE = 120.dp
private val LOCK_DISTANCE = 80.dp
private val RAIL_GAP = 16.dp
private val RAIL_WIDTH = 40.dp
private val RAIL_RISE = 24.dp
private val CHEVRON_TRAVEL = 6.dp
