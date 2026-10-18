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
import androidx.compose.ui.layout.Layout
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
import androidx.compose.ui.unit.Constraints
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

/** [KSnackbarState.show]가 끝난 이유. */
public enum class KSnackbarResult {
    /** 사용자가 동작 버튼을 눌렀다. */
    Action,

    /** 시간이 지났거나, 옆으로 쓸어 닫았거나, 닫기 버튼이나 [KSnackbarState.dismiss]로 닫았다. */
    Dismissed,
}

/** [KSnackbarHost]에 스낵바를 띄우는 상태. 여러 개를 요청하면 한 번에 하나씩 순서대로 보여 준다. */
@Stable
public class KSnackbarState {
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
     * @param status 종류. [KStatus.Neutral]이 아니면 글자 앞에 상태 아이콘([KStatusIcon])을 달고, [KStatus.Error]는 TalkBack이 읽던 것을 끊고 바로 읽는다.
     */
    public suspend fun show(
        message: String,
        actionLabel: String? = null,
        durationMs: Long = DEFAULT_SNACKBAR_MS,
        status: KStatus = KStatus.Neutral,
    ): KSnackbarResult {
        require(durationMs > 0) { "durationMs는 0보다 커야 합니다." }
        return queue.withLock {
            val data = SnackbarData(message, actionLabel, timeoutAdjust(durationMs), status)
            current = data
            val result = try {
                withTimeoutOrNull(data.durationMs) { data.result.await() } ?: KSnackbarResult.Dismissed
            } finally {
                current = null
            }
            // 사라지는 애니메이션이 끝난 뒤 다음 스낵바를 올린다.
            delay(SNACKBAR_GAP_MS)
            result
        }
    }

    /** 보여 주는 중인 스낵바를 닫는다. [show]는 [KSnackbarResult.Dismissed]를 돌려준다. */
    public fun dismiss() {
        current?.result?.complete(KSnackbarResult.Dismissed)
    }
}

@Composable
public fun rememberKSnackbarState(): KSnackbarState = remember { KSnackbarState() }

internal class SnackbarData(val message: String, val actionLabel: String?, val durationMs: Long, val status: KStatus = KStatus.Neutral) {
    val result = CompletableDeferred<KSnackbarResult>()
}

/**
 * 화면 아래에서 떠오르는 스낵바. 오른쪽에 동작 버튼과, 남은 시간만큼 줄어드는 원형 카운트다운 링이 있다. 링이 닫기 버튼이다.
 * 문구는 두 줄까지 보이고, 동작 버튼 글자가 길거나 문구 자리가 너무 좁으면 버튼을 문구 아래 줄 끝으로 내린다.
 * 옆으로 쓸어 넘기면 닫히고, 내비게이션 바와 키보드가 올라와 있으면 그 위에 뜬다. [KineticaScaffold] 안에서는 오버레이 레이어에 그린다.
 * 밖에서는 내용 크기의 팝업 창에 띄우므로 호출한 자리의 배치에 끼어들지 않고 뒤 화면의 터치도 막지 않는다.
 * TalkBack은 새 스낵바를 바로 읽어 준다.
 *
 * [KineticaScaffold] 안이면 `bottomBar`(탭 바) 위에 저절로 뜬다. 밖에서 Material `NavigationBar` 같은 하단 막대가 있으면
 * [bottomOffset]에 그 높이(예: 80.dp)를 준다.
 *
 * @param bottomOffset 화면 아래(내비게이션 바·키보드 위)에서 더 띄울 거리
 */
@Composable
public fun KSnackbarHost(
    state: KSnackbarState,
    modifier: Modifier = Modifier,
    bottomOffset: Dp = 0.dp,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val adjust = rememberAccessibleTimeout(withControls = true)
    SideEffect { state.timeoutAdjust = adjust }
    var last by remember { mutableStateOf<SnackbarData?>(null) }
    state.current?.let { last = it }
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    KOverlay(state.current != null, OverlayKind.Bottom, insets) { transition ->
        val appear = transition.animateFloat(
            transitionSpec = { if (targetState) spring(SNACKBAR_DAMPING, Spring.StiffnessMediumLow) else tween(SNACKBAR_OUT_MS) },
            label = "glass-snackbar",
        ) { if (it) 1f else 0f }
        // 스캐폴드 탭 바는 내비게이션 바 여백까지 포함하므로, 이미 피한 아래 인셋만큼 빼고 띄운다.
        val density = LocalDensity.current
        val navBottom = with(density) { insets.getBottom(density).toDp() }
        val bar = LocalKineticaHost.current?.bottomBarPx?.let { with(density) { it.toDp() } } ?: 0.dp
        val lift = maxOf(bottomOffset, bar - navBottom).coerceAtLeast(0.dp)
        last?.let { data -> key(data) { SnackbarPill(data, surface, appear, modifier.padding(bottom = lift)) } }
    }
}

@Suppress("LongMethod") // 디자인 언어 토큰(모양, 밀도, 굵기)을 읽는 줄이 더해졌을 뿐 한 덩어리 배치라 나누면 오히려 읽기 어렵다.
@Composable
private fun SnackbarPill(data: SnackbarData, surface: SurfaceStyle, appear: State<Float>, modifier: Modifier) {
    val remaining = remember { Animatable(1f) }
    LaunchedEffect(data) {
        remaining.animateTo(0f, tween(data.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), easing = LinearEasing))
    }
    val travel = with(LocalDensity.current) { SNACKBAR_TRAVEL.toPx() }
    val dismissLabel = stringResource(R.string.kinetica_ui_notify_dismiss)
    val ink = surface.contentColor
    SnackbarLayout(
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
            .kSurface(surface, shapeFor { toast })
            .semantics {
                liveRegion = if (data.status == KStatus.Error) LiveRegionMode.Assertive else LiveRegionMode.Polite
                dismiss(dismissLabel) {
                    data.result.complete(KSnackbarResult.Dismissed)
                }
            }
            .padding(start = if (data.status == KStatus.Neutral) 20.dp else 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        message = {
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (data.status != KStatus.Neutral) {
                    KStatusIcon(data.status, Modifier.padding(end = 10.dp), statusLabel(data.status), STATUS_ICON, surface = surface)
                }
                ThemedText(
                    data.message,
                    style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        action = data.actionLabel?.let { label ->
            {
                val actionShape = shapeFor { button }
                ThemedText(
                    label,
                    Modifier
                        .background(surface.raisedFill(ACTION_BACK_ALPHA), actionShape)
                        .clickable(null, KIndication(actionShape), role = Role.Button) { data.result.complete(KSnackbarResult.Action) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = KineticaTheme.design.label(FontWeight.SemiBold)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        close = { CountdownRing(remaining, surface, dismissLabel) { data.result.complete(KSnackbarResult.Dismissed) } },
    )
}

/**
 * 문구, 동작 버튼, 닫기 링을 한 줄에 놓는다. 동작 버튼이 전체 폭의 [LONG_ACTION_FRACTION]보다 넓거나 문구 자리가
 * [MIN_MESSAGE_WIDTH]보다 좁아지면 버튼을 문구 아래 줄 끝으로 내린다. 닫기 링은 늘 첫 줄 끝에 있다.
 */
@Composable
private fun SnackbarLayout(
    modifier: Modifier,
    message: @Composable () -> Unit,
    action: (@Composable () -> Unit)?,
    close: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(message, action ?: {}, close),
        modifier = modifier,
    ) { (messageM, actionM, closeM), constraints ->
        val maxW = constraints.maxWidth
        val gap = SLOT_GAP.roundToPx()
        val closeP = closeM.first().measure(Constraints())
        val actionP = actionM.firstOrNull()?.measure(Constraints(maxWidth = (maxW - closeP.width - gap).coerceAtLeast(0)))
        val actionW = actionP?.width ?: 0
        val actionSlot = if (actionP == null) 0 else actionW + gap
        val inline = maxW - closeP.width - gap - actionSlot
        val stacked = actionP != null && (actionW > maxW * LONG_ACTION_FRACTION || inline < MIN_MESSAGE_WIDTH.roundToPx())
        val messageW = if (stacked) maxW - closeP.width - gap else inline
        val messageP = messageM.first().measure(Constraints(maxWidth = messageW.coerceAtLeast(0)))
        val belowH = if (stacked) actionP?.height ?: 0 else 0
        val rowH = maxOf(messageP.height, closeP.height, if (stacked) 0 else actionP?.height ?: 0)
        val height = rowH + belowH
        val width = if (stacked) maxW else messageP.width + closeP.width + gap + actionSlot
        layout(width.coerceIn(constraints.minWidth, maxW), height) {
            messageP.placeRelative(0, (rowH - messageP.height) / 2)
            closeP.placeRelative(width - closeP.width, (rowH - closeP.height) / 2)
            if (actionP != null) {
                if (stacked) {
                    actionP.placeRelative(width - actionP.width, rowH)
                } else {
                    actionP.placeRelative(width - closeP.width - gap - actionP.width, (rowH - actionP.height) / 2)
                }
            }
        }
    }
}

/** 옆으로 쓸어 닫는 동작. 너비의 35% 넘게 끌거나 빠르게 플링하면 그 방향으로 날아가며 닫히고, 아니면 제자리로 돌아온다. */
@Composable
private fun Modifier.swipeToDismiss(data: SnackbarData): Modifier {
    val scope = rememberCoroutineScope()
    val offset = remember { SettleOffset() }
    var width by remember { mutableIntStateOf(1) }
    // 끌기는 상태에 바로 쓴다. snapTo를 launch하면 손 떼기와 같은 프레임의 마지막 움직임이 복귀 애니메이션을 취소해 중간에 멈춘다.
    val release: suspend (Float) -> Unit = { velocity ->
        val flung = abs(velocity) > SWIPE_FLING_VELOCITY
        if (abs(offset.value) > width * SWIPE_FRACTION || flung) {
            val direction = if (flung) sign(velocity) else sign(offset.value)
            data.result.complete(KSnackbarResult.Dismissed)
            offset.animateTo(direction * width * SWIPE_OUT, tween(SNACKBAR_OUT_MS), velocity)
        } else {
            offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), velocity)
        }
    }
    return this
        .onSizeChanged { width = it.width.coerceAtLeast(1) }
        .graphicsLayer {
            translationX = offset.value
            alpha = 1f - (abs(offset.value) / width).coerceIn(0f, 1f) * SWIPE_FADE
        }
        .draggable(
            rememberDraggableState { delta -> offset.drag(delta) },
            Orientation.Horizontal,
            onDragStopped = { velocity -> release(velocity) },
        )
        .settleAfterRelease({ offset.value != 0f && !offset.isSettling && !data.result.isCompleted }) { scope.launch { release(0f) } }
}

@Composable
private fun CountdownRing(remaining: Animatable<Float, *>, surface: SurfaceStyle, label: String, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val stroke = remember(density) { with(density) { Stroke(width = RING_STROKE.toPx(), cap = StrokeCap.Round) } }
    Box(
        Modifier
            .size(RING_TOUCH)
            .clickable(null, KIndication(CircleShape), role = Role.Button, onClickLabel = label, onClick = onDismiss)
            .semantics { contentDescription = label }
            .drawBehind {
                val inset = RING_INSET.toPx()
                val ringSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                drawArc(surface.raisedFill(TRACK_ALPHA), 0f, FULL_TURN, false, topLeft, ringSize, style = stroke)
                drawArc(surface.contentColor, START_ANGLE, FULL_TURN * remaining.value, false, topLeft, ringSize, style = stroke)
                val cross = CROSS_HALF.toPx()
                val c = center
                val color = surface.muted(CROSS_ALPHA)
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
private val RING_TOUCH = 40.dp
private val RING_INSET = 7.dp
private val RING_STROKE = 2.dp
private val CROSS_HALF = 4.dp
private val STATUS_ICON = 20.dp
private val SLOT_GAP = 8.dp
private val MIN_MESSAGE_WIDTH = 120.dp
private const val LONG_ACTION_FRACTION = 0.34f
