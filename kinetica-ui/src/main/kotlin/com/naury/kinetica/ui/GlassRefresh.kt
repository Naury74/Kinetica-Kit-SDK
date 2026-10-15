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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * 당겨서 새로 고침. 맨 위에서 더 당기면 내용이 고무줄처럼 버티며 내려오고, 위에 유리 물방울이 차오르다가 [threshold]를 넘으면
 * 톡 하고(진동) 길쭉해진다. 그때 놓으면 [onRefresh]를 부르고, [refreshing]이 `false`가 될 때까지 물방울이 돈다.
 * 화면 읽기에는 "새로 고침" 동작으로 보인다.
 *
 * [content]에는 세로로 스크롤되는 목록(LazyColumn 등)을 넣는다. 스크롤이 맨 위가 아니면 당겨지지 않는다.
 *
 * @param refreshing 새로 고치는 중이면 `true`. 호스트가 일을 마치면 `false`로 바꾼다.
 * @param threshold 놓았을 때 새로 고침이 되는 당김 거리
 * @param indicatorTop 물방울이 나오는 위치(이 상자 위에서부터). [GlassScaffold] 안이면 위 막대 높이(`padding.calculateTopPadding()`)를 넘겨 막대 아래에 보이게 한다.
 */
@Composable
public fun GlassRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassStyle = LocalGlassStyle.current,
    threshold: Dp = 80.dp,
    indicatorTop: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val latestRefresh by rememberUpdatedState(onRefresh)
    val latestRefreshing by rememberUpdatedState(refreshing)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val trigger = with(LocalDensity.current) { threshold.toPx() }
    // 끄는 동안은 여러 번의 스크롤이 한 프레임 안에 쌓이므로 상태에 바로 더하고, 놓은 뒤의 움직임만 애니메이션으로 한다.
    val pull = remember { DragOffset() }
    LaunchedEffect(refreshing, trigger) {
        pull.settle(scope, if (refreshing) trigger * REST_FRACTION else 0f, Spring.DampingRatioLowBouncy)
    }
    val connection = remember(trigger) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 당긴 채 위로 밀면 목록보다 먼저 당김을 거둔다.
                if (available.y >= 0f || pull.value <= 0f || latestRefreshing) return Offset.Zero
                val used = maxOf(available.y, -pull.value)
                pull.drag(used)
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f || source != NestedScrollSource.UserInput || latestRefreshing) return Offset.Zero
                // 멀리 당길수록 덜 따라온다(고무줄).
                val resistance = (1f - pull.value / (trigger * MAX_FRACTION)).coerceIn(0f, 1f) * DRAG_RATE
                val before = pull.value
                pull.drag(available.y * resistance)
                if (before < trigger && pull.value >= trigger) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull.value <= 0f || latestRefreshing) return Velocity.Zero
                if (pull.value >= trigger) {
                    latestRefresh()
                    pull.settle(scope, trigger * REST_FRACTION, Spring.DampingRatioLowBouncy)
                } else {
                    pull.settle(scope, 0f, Spring.DampingRatioNoBouncy)
                }
                return available
            }
        }
    }
    Box(
        modifier
            .nestedScroll(connection)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("새로 고침") {
                        if (!latestRefreshing) latestRefresh()
                        true
                    },
                )
            },
    ) {
        Box(Modifier.graphicsLayer { translationY = pull.value }, propagateMinConstraints = true, content = content)
        RefreshDrop(
            pull.value / trigger,
            refreshing,
            style,
            Modifier.align(Alignment.TopCenter).padding(top = indicatorTop).graphicsLayer { translationY = pull.value / 2 },
        )
    }
}

/** 당긴 정도([level], 1이면 문턱)만큼 차오르는 유리 물방울. 문턱을 넘으면 세로로 길쭉해지고, 새로 고치는 중이면 고리가 돈다. */
@Composable
private fun RefreshDrop(level: Float, refreshing: Boolean, style: GlassStyle, modifier: Modifier) {
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
            .glass(style, CircleShape)
            .semantics { if (refreshing) contentDescription = "새로 고치는 중" }
            .drawWithContent {
                drawContent()
                val stroke = RING_WIDTH.toPx()
                val inset = size.minDimension * RING_INSET
                val sweep = if (refreshing) RING_SPIN_SWEEP else FULL_TURN * grow * RING_FILL
                drawArc(
                    style.onGlass.copy(alpha = RING_ALPHA),
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
