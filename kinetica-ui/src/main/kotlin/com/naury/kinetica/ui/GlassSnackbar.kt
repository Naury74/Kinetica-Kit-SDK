package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** [GlassSnackbarState.show]가 끝난 이유. */
public enum class GlassSnackbarResult {
    /** 사용자가 동작 버튼을 눌렀다. */
    Action,

    /** 시간이 지났거나, 옆으로 쓸어 닫았거나, 닫기 버튼이나 [GlassSnackbarState.dismiss]로 닫았다. */
    Dismissed,
}

/** [GlassSnackbarHost]에 스낵바를 띄우는 상태. 여러 개를 요청하면 한 번에 하나씩 순서대로 보여 준다. */
@Stable
public class GlassSnackbarState {
    internal var current by mutableStateOf<SnackbarData?>(null)
        private set
    internal var timeoutAdjust: (Long) -> Long = { it }
    private val queue = Mutex()

    /** 지금 보여 주는 스낵바의 문구. 없으면 `null`이다. */
    public val currentMessage: String? get() = current?.message

    /**
     * [message]를 보여 주고 스낵바가 닫힐 때까지 일시 중단한 뒤 닫힌 이유를 돌려준다. 앞선 스낵바가 있으면 그 뒤에 보여 준다.
     * 호출한 코루틴이 취소되면 스낵바도 바로 닫는다.
     *
     * @param actionLabel 오른쪽 동작 버튼 문구(예: "되돌리기"). `null`이면 버튼을 두지 않는다.
     * @param durationMs 표시 시간(ms). TalkBack 사용자가 접근성 설정에서 시간을 늘렸으면 그 값을 따른다.
     *   0 이하이면 [IllegalArgumentException]을 던진다.
     */
    public suspend fun show(message: String, actionLabel: String? = null, durationMs: Long = DEFAULT_SNACKBAR_MS): GlassSnackbarResult {
        require(durationMs > 0) { "durationMs는 0보다 커야 합니다." }
        return queue.withLock {
            val data = SnackbarData(message, actionLabel, timeoutAdjust(durationMs))
            current = data
            val result = try {
                withTimeoutOrNull(data.durationMs) { data.result.await() } ?: GlassSnackbarResult.Dismissed
            } finally {
                current = null
            }
            // 사라지는 애니메이션이 끝난 뒤 다음 스낵바를 올린다.
            delay(SNACKBAR_GAP_MS)
            result
        }
    }

    /** 보여 주는 중인 스낵바를 닫는다. [show]는 [GlassSnackbarResult.Dismissed]를 돌려준다. */
    public fun dismiss() {
        current?.result?.complete(GlassSnackbarResult.Dismissed)
    }
}

@Composable
public fun rememberGlassSnackbarState(): GlassSnackbarState = remember { GlassSnackbarState() }

internal class SnackbarData(val message: String, val actionLabel: String?, val durationMs: Long) {
    val result = CompletableDeferred<GlassSnackbarResult>()
}

/**
 * 화면 아래에서 떠오르는 글래스 스낵바. 오른쪽에 동작 버튼과, 남은 시간만큼 줄어드는 원형 카운트다운 링이 있다. 링을 누르면 닫힌다.
 * 옆으로 쓸어 넘기면 닫히고, 내비게이션 바와 키보드가 올라와 있으면 그 위에 뜬다. [GlassScaffold] 안에서는 오버레이 레이어에 그린다.
 * 밖에서는 내용 크기의 팝업 창에 띄우므로 호출한 자리의 배치에 끼어들지 않고 뒤 화면의 터치도 막지 않는다.
 * TalkBack은 새 스낵바를 바로 읽어 준다.
 *
 * [GlassScaffold] 안이면 `bottomBar`(탭 바) 위에 저절로 뜬다. 밖에서 Material `NavigationBar` 같은 하단 막대가 있으면
 * [bottomOffset]에 그 높이(예: 80.dp)를 준다.
 *
 * @param bottomOffset 화면 아래(내비게이션 바·키보드 위)에서 더 띄울 거리
 */
@Composable
public fun GlassSnackbarHost(
    state: GlassSnackbarState,
    modifier: Modifier = Modifier,
    bottomOffset: Dp = 0.dp,
    style: GlassStyle = LocalGlassStyle.current,
) {
    val adjust = rememberAccessibleTimeout(withControls = true)
    SideEffect { state.timeoutAdjust = adjust }
    var last by remember { mutableStateOf<SnackbarData?>(null) }
    state.current?.let { last = it }
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    GlassOverlay(state.current != null, OverlayKind.Bottom, insets) { transition ->
        val appear = transition.animateFloat(
            transitionSpec = { if (targetState) spring(SNACKBAR_DAMPING, Spring.StiffnessMediumLow) else tween(SNACKBAR_OUT_MS) },
            label = "glass-snackbar",
        ) { if (it) 1f else 0f }
        // 스캐폴드 탭 바는 내비게이션 바 여백까지 포함하므로, 이미 피한 아래 인셋만큼 빼고 띄운다.
        val density = LocalDensity.current
        val navBottom = with(density) { insets.getBottom(density).toDp() }
        val bar = LocalGlassHost.current?.bottomBarPx?.let { with(density) { it.toDp() } } ?: 0.dp
        val lift = maxOf(bottomOffset, bar - navBottom).coerceAtLeast(0.dp)
        last?.let { data -> key(data) { SnackbarPill(data, style, appear, modifier.padding(bottom = lift)) } }
    }
}

@Composable
private fun SnackbarPill(data: SnackbarData, style: GlassStyle, appear: State<Float>, modifier: Modifier) {
    val remaining = remember { Animatable(1f) }
    LaunchedEffect(data) {
        remaining.animateTo(0f, tween(data.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), easing = LinearEasing))
    }
    val travel = with(LocalDensity.current) { SNACKBAR_TRAVEL.toPx() }
    val dismissLabel = stringResource(R.string.kinetica_ui_notify_dismiss)
    Row(
        modifier
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .widthIn(max = SNACKBAR_MAX_WIDTH)
            .swipeToDismiss(data)
            .graphicsLayer {
                val p = appear.value
                translationY = (size.height + travel) * (1f - p)
                val scale = SNACKBAR_START_SCALE + (1f - SNACKBAR_START_SCALE) * p
                scaleX = scale
                scaleY = scale
                alpha = p.coerceIn(0f, 1f)
            }
            .glass(style, CapsuleShape(SNACKBAR_CORNER))
            .semantics {
                liveRegion = LiveRegionMode.Polite
                dismiss(dismissLabel) {
                    data.result.complete(GlassSnackbarResult.Dismissed)
                }
            }
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemedText(
            data.message,
            Modifier.weight(1f, fill = false).padding(vertical = 6.dp),
            style = TextStyle(color = style.onGlass, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(12.dp))
        data.actionLabel?.let { label ->
            ThemedText(
                label,
                Modifier
                    .background(style.onGlass.copy(alpha = ACTION_BACK_ALPHA), CircleShape)
                    .clickable(role = Role.Button) { data.result.complete(GlassSnackbarResult.Action) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = TextStyle(color = style.onGlass, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Spacer(Modifier.size(4.dp))
        }
        CountdownRing(remaining, style, dismissLabel) { data.result.complete(GlassSnackbarResult.Dismissed) }
    }
}

/** 옆으로 쓸어 닫는 동작. 너비의 35% 넘게 끌거나 빠르게 플링하면 그 방향으로 날아가며 닫히고, 아니면 제자리로 돌아온다. */
@Composable
private fun Modifier.swipeToDismiss(data: SnackbarData): Modifier {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    return this
        .onSizeChanged { width = it.width.coerceAtLeast(1) }
        .graphicsLayer {
            translationX = offset.value
            alpha = 1f - (abs(offset.value) / width).coerceIn(0f, 1f) * SWIPE_FADE
        }
        .draggable(
            rememberDraggableState { delta -> scope.launch { offset.snapTo(offset.value + delta) } },
            Orientation.Horizontal,
            onDragStopped = { velocity ->
                val flung = abs(velocity) > SWIPE_FLING_VELOCITY
                if (abs(offset.value) > width * SWIPE_FRACTION || flung) {
                    val direction = if (flung) sign(velocity) else sign(offset.value)
                    data.result.complete(GlassSnackbarResult.Dismissed)
                    offset.animateTo(direction * width * SWIPE_OUT, tween(SNACKBAR_OUT_MS))
                } else {
                    offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
            },
        )
}

@Composable
private fun CountdownRing(remaining: Animatable<Float, *>, style: GlassStyle, label: String, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val stroke = remember(density) { with(density) { Stroke(width = RING_STROKE.toPx(), cap = StrokeCap.Round) } }
    Box(
        Modifier
            .size(RING_TOUCH)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onDismiss)
            .semantics { contentDescription = label }
            .drawBehind {
                val inset = RING_INSET.toPx()
                val ringSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                drawArc(style.onGlass.copy(alpha = TRACK_ALPHA), 0f, FULL_TURN, false, topLeft, ringSize, style = stroke)
                drawArc(style.onGlass, START_ANGLE, FULL_TURN * remaining.value, false, topLeft, ringSize, style = stroke)
                val cross = CROSS_HALF.toPx()
                val c = center
                val color = style.onGlass.copy(alpha = CROSS_ALPHA)
                drawLine(color, c + Offset(-cross, -cross), c + Offset(cross, cross), stroke.width, StrokeCap.Round)
                drawLine(color, c + Offset(-cross, cross), c + Offset(cross, -cross), stroke.width, StrokeCap.Round)
            },
    )
}

private const val DEFAULT_SNACKBAR_MS = 4_000L
private const val SNACKBAR_GAP_MS = 260L
private const val SNACKBAR_OUT_MS = 200
private const val SNACKBAR_DAMPING = 0.7f
private const val SNACKBAR_START_SCALE = 0.92f
private const val ACTION_BACK_ALPHA = 0.12f
private const val SWIPE_FADE = 0.8f
private const val SWIPE_FRACTION = 0.35f
private const val SWIPE_OUT = 1.2f
private const val SWIPE_FLING_VELOCITY = 1_200f
private const val TRACK_ALPHA = 0.18f
private const val CROSS_ALPHA = 0.7f
private const val FULL_TURN = 360f
private const val START_ANGLE = -90f
private val SNACKBAR_TRAVEL = 32.dp
private val SNACKBAR_MAX_WIDTH = 560.dp
private val SNACKBAR_CORNER = 28.dp
private val RING_TOUCH = 40.dp
private val RING_INSET = 7.dp
private val RING_STROKE = 2.dp
private val CROSS_HALF = 4.dp
