package com.naury.kinetica.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 당겨서 새로 고침 컨테이너. 목록 맨 위에서 더 당기면 콘텐츠가 고무줄처럼 버티며 내려오고, 위쪽에 글래스 물방울([indicator]로 바꿀 수 있다)이 차오른다.
 * 당긴 거리가 [threshold]를 넘으면 햅틱과 함께 물방울이 길쭉해지고, 이때 손을 떼면 [onRefresh]를 호출한다.
 * 물방울은 [refreshing]이 `false`가 될 때까지 돈다. TalkBack에서는 "새로 고침" 사용자 지정 동작으로 실행할 수 있다.
 *
 * [content]에는 LazyColumn처럼 세로로 스크롤되는 목록을 넣는다. 스크롤이 맨 위에 있지 않으면 당겨지지 않는다.
 *
 * @param refreshing 새로 고침이 진행 중이면 `true`. 작업이 끝나면 호출하는 쪽에서 `false`로 바꾼다.
 * @param threshold 손을 뗐을 때 새로 고침이 실행되는 최소 당김 거리
 * @param indicatorTop 이 컨테이너 위쪽 끝에서 물방울까지의 거리. [KineticaScaffold] 안에서는 상단 바 높이
 *   (`padding.calculateTopPadding()`)를 넘겨야 물방울이 바에 가리지 않는다.
 * @param indicator 당길 때 보이는 표시. [KRefreshIndicator.Drop](기본값, 글래스 물방울), [KRefreshIndicator.Arrow](원 화살표),
 *   [KRefreshIndicator.Dots](점 세 개)
 */
@Composable
public fun KRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    surface: SurfaceStyle = KineticaTheme.surface,
    threshold: Dp = 80.dp,
    indicatorTop: Dp = 0.dp,
    indicator: KRefreshIndicator = KRefreshIndicator.Drop,
    content: @Composable BoxScope.() -> Unit,
) {
    val latestRefresh by rememberUpdatedState(onRefresh)
    val latestRefreshing by rememberUpdatedState(refreshing)
    val haptics = rememberKHaptics()
    val scope = rememberCoroutineScope()
    val trigger = with(LocalDensity.current) { threshold.toPx() }
    // 드래그 중에는 한 프레임에 스크롤이 여러 번 들어오므로 상태에 바로 더하고, 손을 뗀 뒤의 복귀만 애니메이션으로 처리한다.
    val pull = remember { SettleOffset() }
    LaunchedEffect(refreshing, trigger) {
        pull.settle(scope, if (refreshing) trigger * REST_FRACTION else 0f, spring(Spring.DampingRatioLowBouncy))
    }
    fun release() {
        if (pull.value >= trigger) {
            latestRefresh()
            pull.settle(scope, trigger * REST_FRACTION, spring(Spring.DampingRatioLowBouncy))
            // 호스트가 refreshing을 켜지 않았거나 같은 프레임에 바로 껐으면 LaunchedEffect가 돌지 않으므로 여기서 되돌린다.
            scope.launch {
                withFrameNanos { }
                if (!latestRefreshing) pull.settle(scope, 0f, spring(Spring.DampingRatioNoBouncy))
            }
        } else {
            pull.settle(scope, 0f, spring(Spring.DampingRatioNoBouncy))
        }
    }
    val connection = remember(trigger) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 당긴 상태에서 다시 위로 밀면 목록을 스크롤하기 전에 당긴 거리부터 되돌린다.
                if (available.y >= 0f || pull.value <= 0f || latestRefreshing) return Offset.Zero
                val used = maxOf(available.y, -pull.value)
                return Offset(0f, pull.drag(used))
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f || source != NestedScrollSource.UserInput || latestRefreshing) return Offset.Zero
                // 멀리 당길수록 덜 따라오게 해서 고무줄처럼 버틴다.
                val resistance = (1f - pull.value / (trigger * MAX_FRACTION)).coerceIn(0f, 1f) * DRAG_RATE
                val before = pull.value
                pull.drag(available.y * resistance)
                if (before < trigger && pull.value >= trigger) haptics.perform(KHapticType.Edge)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull.value <= 0f || latestRefreshing) return Velocity.Zero
                release()
                return available
            }
        }
    }
    val refreshLabel = stringResource(R.string.kinetica_ui_refresh)
    val refreshingLabel = stringResource(R.string.kinetica_ui_refreshing)
    var wheelRelease by remember { mutableStateOf<Job?>(null) }
    Box(
        modifier
            .nestedScroll(connection)
            // 목록이 플링을 보내지 않고 끝나도 당긴 채 멈추지 않게 한다. 새로 고치는 중이면 물방울 자리로 보낸다.
            .settleAfterRelease({ !pull.isSettling && pull.value != if (latestRefreshing) trigger * REST_FRACTION else 0f }) {
                if (latestRefreshing) pull.settle(scope, trigger * REST_FRACTION, spring(Spring.DampingRatioLowBouncy)) else release()
            }
            .pointerInput(trigger) {
                // 마우스 휠·터치패드 스크롤은 플링이 없어 onPreFling이 오지 않는다. 휠이 잠시 멈추면 손을 뗀 것으로 본다.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Scroll) {
                            wheelRelease?.cancel()
                            wheelRelease = scope.launch {
                                delay(WHEEL_RELEASE_MS)
                                if (pull.value > 0f && !latestRefreshing) release()
                            }
                        }
                    }
                }
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(refreshLabel) {
                        if (!latestRefreshing) latestRefresh()
                        true
                    },
                )
            },
    ) {
        Box(Modifier.graphicsLayer { translationY = pull.value }, propagateMinConstraints = true, content = content)
        val place = Modifier.align(Alignment.TopCenter).padding(top = indicatorTop).graphicsLayer { translationY = pull.value / 2 }
        RefreshIndicator(indicator, { pull.value / trigger }, refreshing, surface, refreshingLabel, place)
    }
}

/** 당긴 정도([level], 1이면 임계값)만큼 차오르는 글래스 물방울. 임계값을 넘으면 세로로 길쭉해지고, 새로 고침 중에는 링이 돈다. */
@Composable
internal fun RefreshDrop(level: Float, refreshing: Boolean, surface: SurfaceStyle, label: String, modifier: Modifier) {
    if (level <= 0f && !refreshing) return
    val spin by rememberInfiniteTransition(label = "refresh-spin").animateFloat(
        0f,
        FULL_TURN,
        infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "refresh-angle",
    )
    val grow = level.coerceIn(0f, 1f)
    val over = (level - 1f).coerceAtLeast(0f)
    Box(
        modifier
            .size(DROP_SIZE)
            .graphicsLayer {
                translationY = -DROP_SIZE.toPx() / 2
                scaleX = (DROP_MIN_SCALE + (1f - DROP_MIN_SCALE) * grow) * (1f - over * DROP_SQUEEZE)
                scaleY = (DROP_MIN_SCALE + (1f - DROP_MIN_SCALE) * grow) * (1f + over * DROP_STRETCH)
                alpha = grow.coerceAtLeast(if (refreshing) 1f else 0f)
            }
            .kSurface(surface, CircleShape)
            .semantics { if (refreshing) contentDescription = label }
            .drawWithContent {
                drawContent()
                val stroke = RING_WIDTH.toPx()
                val inset = size.minDimension * RING_INSET
                val sweep = if (refreshing) RING_SPIN_SWEEP else FULL_TURN * grow * RING_FILL
                drawArc(
                    surface.contentColor.copy(alpha = RING_ALPHA),
                    startAngle = if (refreshing) spin else -QUARTER_TURN,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            },
    )
}

private const val WHEEL_RELEASE_MS = 200L
private const val DRAG_RATE = 0.55f
private const val MAX_FRACTION = 2.2f
private const val REST_FRACTION = 0.75f
private val DROP_SIZE = 40.dp
private const val DROP_MIN_SCALE = 0.4f
private const val DROP_SQUEEZE = 0.25f
private const val DROP_STRETCH = 0.45f
private val RING_WIDTH = 2.5.dp
private const val RING_INSET = 0.28f
private const val RING_ALPHA = 0.85f
private const val RING_FILL = 0.85f
private const val RING_SPIN_SWEEP = 270f
private const val FULL_TURN = 360f
private const val QUARTER_TURN = 90f
private const val SPIN_MS = 900
