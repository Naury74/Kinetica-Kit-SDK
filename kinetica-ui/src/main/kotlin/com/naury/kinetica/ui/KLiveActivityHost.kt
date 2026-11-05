package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * [KLiveActivityState]의 활동을 띄우는 호스트. 활동이 하나면 좌우로 나뉜 compact 캡슐이고, 둘이면 두 번째 활동이 원 하나(minimal)로
 * 떨어져 나와 옆에 붙는다. 캡슐이나 원을 누르거나 길게 누르면 그 활동의 큰 화면으로 모양이 이어서 부풀고, 다시 누르거나
 * 가장자리 쪽으로 밀면 접힌다. 펼친 화면 안의 버튼은 그대로 눌린다.
 *
 * [position]이 [KLiveActivityPosition.Top]이면 상태 표시줄 바로 아래 가운데에 놓고 가운데를 비워 카메라 홀을 피한다.
 * [KLiveActivityPosition.Bottom]이면 내비게이션 바 위에 이름과 상태가 보이는 넓은 캡슐로 놓는다. 아래에 탭 바가 있으면 [edgePadding]으로 띄운다.
 * [KineticaScaffold] 안에서는 오버레이 레이어에, 밖에서는 내용 크기의 팝업 창에 그려 뒤 화면의 터치를 막지 않는다.
 * 같은 위치에 [KIslandHost]를 함께 두면 겹치므로, 아일랜드 알림과 함께 쓸 때는 아래 위치를 권한다.
 *
 * 같은 [state]를 여러 호스트가 그리면 가장 나중에 화면에 들어온 호스트 하나만 그린다.
 * 진행 링과 막대는 [KLiveActivity.progress]를 그리기 단계에서만 읽는다. '애니메이션 삭제' 설정에서는 모양이 바로 바뀐다.
 * TalkBack은 접힌 캡슐과 원에서 활동 이름과 상태를 읽고 펼치기 동작을, 펼친 화면에서는 접기 동작을 내보낸다.
 *
 * ```kotlin
 * val live = rememberKLiveActivityState()
 * live.start(
 *     KLiveActivity(
 *         "timer",
 *         "타이머",
 *         "5분 남음",
 *         leading = { TimerIcon() },
 *         trailing = { KCountdownPill(endsAt, 300_000L) },
 *         expanded = { TimerPanel() },
 *     ),
 * )
 * KLiveActivityHost(live, position = KLiveActivityPosition.Bottom)
 * ```
 *
 * @param surface 캡슐과 원의 표면. 기본은 카메라 홀과 이어지는 검은 캡슐이다.
 * @param edgePadding 화면 가장자리(시스템 바 안쪽)와 캡슐 사이. 지정하지 않으면 위 위치는 6dp, 아래 위치는 12dp다.
 */
@Composable
public fun KLiveActivityHost(
    state: KLiveActivityState,
    modifier: Modifier = Modifier,
    position: KLiveActivityPosition = KLiveActivityPosition.Top,
    surface: SurfaceStyle = KIslandDefaults.Black,
    edgePadding: Dp = Dp.Unspecified,
) {
    val host = remember { Any() }
    DisposableEffect(state, host) {
        state.attach(host)
        onDispose { state.detach(host) }
    }
    val top = position == KLiveActivityPosition.Top
    val vertical = if (top) WindowInsetsSides.Top else WindowInsetsSides.Bottom
    val insets = WindowInsets.safeDrawing.only(vertical + WindowInsetsSides.Horizontal)
    val gap = when {
        edgePadding.isSpecified -> edgePadding
        !top -> BOTTOM_GAP
        WindowInsets.statusBars.getTop(LocalDensity.current) > 0 -> TOP_GAP
        else -> MIN_TOP
    }
    val visible = state.isDrawnBy(host) && state.activities.isNotEmpty()
    KOverlay(visible, if (top) OverlayKind.Top else OverlayKind.Bottom, insets) { transition ->
        val appear = transition.animateFloat(
            transitionSpec = { if (targetState) spring(LIVE_DAMPING, Spring.StiffnessMedium) else tween(HIDE_MS) },
            label = "live-activity",
        ) { if (it) 1f else 0f }
        val origin = if (top) TOP_CENTER else BOTTOM_CENTER
        Box(Modifier.padding(top = if (top) gap else 0.dp, bottom = if (top) 0.dp else gap, start = SIDE_MARGIN, end = SIDE_MARGIN)) {
            LiveCluster(
                state,
                LiveLook(position, surface),
                modifier.graphicsLayer {
                    val p = appear.value
                    val scale = APPEAR_SCALE + (1f - APPEAR_SCALE) * p
                    scaleX = scale
                    scaleY = scale
                    alpha = p.coerceIn(0f, 1f)
                    transformOrigin = origin
                },
            )
        }
    }
}

/** 위치에 따른 크기와 표면. */
internal class LiveLook(val position: KLiveActivityPosition, val surface: SurfaceStyle) {
    val top: Boolean get() = position == KLiveActivityPosition.Top
    val compactHeight: Dp get() = if (top) LIVE_TOP_HEIGHT else LIVE_BOTTOM_HEIGHT
}

/** 화면에서 빠지는 동안에도 마지막 값을 그리기 위해 붙잡아 두는 칸. */
private class Last<T> {
    var value: T? = null
}

/** 캡슐과 원 사이 목을 그리려고 배치 단계에서 적어 두는 자리(px, 왼쪽 기준). */
private class NeckGeometry {
    var from = 0f
    var to = 0f
    var centerY = 0f
    var bubble = 0f
}

@Composable
private fun LiveCluster(state: KLiveActivityState, look: LiveLook, modifier: Modifier) {
    val expanded = state.find(state.expandedId)
    val main = expanded ?: state.activities.firstOrNull()
    val second = if (expanded == null) state.activities.getOrNull(1) else null
    val lastMain = remember { Last<KLiveActivity>() }
    val lastSecond = remember { Last<KLiveActivity>() }
    main?.let { lastMain.value = it }
    second?.let { lastSecond.value = it }
    val separation = remember { Animatable(0f) }
    val still = reducedMotion()
    LaunchedEffect(second != null, still) {
        val target = if (second != null) 1f else 0f
        if (still) separation.snapTo(target) else separation.animateTo(target, spring(SPLIT_DAMPING, SPLIT_STIFFNESS))
    }
    val neck = remember { NeckGeometry() }
    val neckColor = (look.surface as? SurfaceStyle.Solid)?.color
    Layout(
        content = {
            val shownMain = lastMain.value
            if (shownMain != null) LiveCapsule(state, shownMain, expanded != null, look) else Box(Modifier)
            val shownSecond = lastSecond.value
            if (shownSecond != null) LiveBubble(state, shownSecond, second != null, look) { separation.value } else Box(Modifier)
        },
        modifier = modifier.drawBehind { if (neckColor != null) drawNeck(neck, separation.value, neckColor) },
        measurePolicy = remember(look.top, separation, neck) { clusterPolicy(look.top, { separation.value }, neck) },
    )
}

/**
 * 큰 캡슐과 원을 놓는다. 원은 [separation]만큼 캡슐 끝에서 밀려 나오고, 목을 그릴 자리를 [neck]에 적어 둔다.
 * 위 위치는 큰 캡슐이 카메라 홀 위에 머물도록 양쪽에 같은 자리를 둔다.
 */
private fun clusterPolicy(top: Boolean, separation: () -> Float, neck: NeckGeometry) = MeasurePolicy { measurables, constraints ->
    val bubble = measurables[1].measure(Constraints())
    val s = separation().coerceIn(0f, 1f)
    val reach = ((BUBBLE_GAP.roundToPx() + bubble.width) * s).roundToInt()
    val reserved = if (top) reach * 2 else reach
    // 원이 나올 자리를 빼고 재야 넓게 늘어나는 아래 캡슐과 원이 함께 화면 안에 든다.
    val capsuleMax = if (constraints.hasBoundedWidth) (constraints.maxWidth - reserved).coerceAtLeast(0) else Constraints.Infinity
    val capsule = measurables[0].measure(Constraints(maxWidth = capsuleMax, maxHeight = constraints.maxHeight))
    val width = capsule.width + reserved
    val height = max(capsule.height, if (s > 0f) bubble.height else 0)
    layout(width, height) {
        val mainX = if (top) reach else 0
        val bubbleX = mainX + capsule.width - bubble.width + reach
        val bubbleY = if (top) 0 else height - bubble.height
        capsule.placeRelative(mainX, if (top) 0 else height - capsule.height)
        bubble.placeRelative(bubbleX, bubbleY)
        val half = bubble.width / 2f
        val from = mainX + capsule.width - half
        val to = bubbleX + half
        val rtl = layoutDirection == LayoutDirection.Rtl
        neck.from = if (rtl) width - to else from
        neck.to = if (rtl) width - from else to
        neck.centerY = bubbleY + bubble.height / 2f
        neck.bubble = bubble.height.toFloat()
    }
}

/** 원이 떨어져 나오는 동안 캡슐과 원 사이를 잇는 목. 멀어질수록 가늘어지다 끊어진다. */
private fun DrawScope.drawNeck(neck: NeckGeometry, separation: Float, color: Color) {
    val s = separation.coerceIn(0f, 1f)
    if (s <= 0f || s >= NECK_BREAK) return
    val thickness = neck.bubble * NECK_THICKNESS * (1f - s / NECK_BREAK)
    if (thickness <= 0f || neck.to <= neck.from) return
    drawRoundRect(
        color,
        Offset(neck.from, neck.centerY - thickness / 2),
        Size(neck.to - neck.from, thickness),
        CornerRadius(thickness / 2),
    )
}

private const val LIVE_DAMPING = 0.68f
private const val HIDE_MS = 220
private const val APPEAR_SCALE = 0.6f
private const val SPLIT_DAMPING = 0.62f
private const val SPLIT_STIFFNESS = 300f
private const val NECK_BREAK = 0.7f
private const val NECK_THICKNESS = 0.55f
private val TOP_CENTER = TransformOrigin(0.5f, 0f)
private val BOTTOM_CENTER = TransformOrigin(0.5f, 1f)
private val TOP_GAP = 6.dp
private val MIN_TOP = 8.dp
private val BOTTOM_GAP = 12.dp
private val SIDE_MARGIN = 12.dp
private val BUBBLE_GAP = 8.dp
internal val LIVE_TOP_HEIGHT = 36.dp
internal val LIVE_BOTTOM_HEIGHT = 52.dp
