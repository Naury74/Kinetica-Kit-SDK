package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
public fun rememberKToasterState(): KToasterState = remember { KToasterState() }

/**
 * [KToasterState]의 토스트를 화면 위나 아래 가장자리에 겹쳐 쌓는다. 새 토스트가 앞에 오고, 뒤의 토스트는 조금씩 작아지며
 * 가장자리 반대쪽으로 끝만 비친다. 쌓인 토스트를 누르면 겹침이 풀려 모두 펼쳐지고, 다시 누르면 겹친다. 펼친 동안에는 사라지기 타이머가 멈춘다.
 * 옆으로 쓸면 그 토스트만 닫힌다. 펼친 상태에서는 토스트마다 실제 높이로 늘어놓으므로 큰 글자나 RTL에서도 겹치지 않는다.
 *
 * 종류([KStatus])마다 아이콘과 색이 다르고, 로딩 토스트는 돌아가는 아이콘을 단다('애니메이션 삭제'면 멈춘 모래시계).
 * TalkBack은 새 토스트와 바뀐 내용을 읽어 준다. 오류는 읽던 것을 끊고 바로 읽히고, 나머지는 읽던 것이 끝난 뒤 읽힌다.
 * [KineticaScaffold] 밖에서는 내용 크기의 팝업 창에 띄우므로 뒤 화면의 터치를 막지 않는다.
 *
 * @param maxVisible 한 번에 보이는 토스트 수(1 이상). 넘는 토스트는 기다렸다가 앞의 것이 닫히면 나타난다. 1 미만이면 [IllegalArgumentException]을 던진다.
 * @param richColors `true`면 토스트 바탕에 종류 색을 옅게 깐다. 기본값은 표면 그대로 두고 아이콘만 칠한다.
 * @param modifier 쌓인 토스트 묶음에 적용한다.
 */
@Composable
public fun KToaster(
    state: KToasterState,
    modifier: Modifier = Modifier,
    position: KToastPosition = KToastPosition.Top,
    maxVisible: Int = 3,
    richColors: Boolean = false,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(maxVisible >= 1) { "maxVisible은 1 이상이어야 합니다." }
    val live = state.entries.filter { !it.leaving }
    val keep = live.takeLast(maxVisible).toSet()
    val rendered = state.entries.filter { it.leaving || it in keep }
    val top = position == KToastPosition.Top
    KOverlay(rendered.isNotEmpty(), if (top) OverlayKind.Top else OverlayKind.Bottom, toastInsets(top)) {
        val lift = if (top) 0.dp else bottomLift()
        ToastStack(
            state,
            rendered,
            StackLook(top, maxVisible, richColors, surface),
            modifier.padding(bottom = lift),
        )
    }
}

/** 쌓기 모양을 정하는 값 묶음 */
private class StackLook(val top: Boolean, val maxVisible: Int, val richColors: Boolean, val surface: SurfaceStyle)

@Composable
private fun toastInsets(top: Boolean): WindowInsets {
    if (!top) return WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    // KineticaScaffold에 상단 바가 있으면 바 제목을 가리지 않게 바 아래에 띄운다. 바 높이에 상태 표시줄이 이미 들어 있다.
    val topBar = LocalKineticaHost.current?.topBarPx ?: 0
    return if (topBar > 0) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).add(WindowInsets(top = topBar))
    } else {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    }
}

/** 스캐폴드 하단 바 위로 띄울 거리. 하단 바 높이에 내비게이션 바가 들어 있으므로 이미 피한 만큼 뺀다. */
@Composable
private fun bottomLift(): Dp {
    val density = LocalDensity.current
    val nav = with(density) { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).getBottom(density).toDp() }
    val bar = LocalKineticaHost.current?.bottomBarPx?.let { with(density) { it.toDp() } } ?: 0.dp
    return (bar - nav).coerceAtLeast(0.dp)
}

/** [index]번 토스트 앞에 놓인 토스트 수. 나타나고 사라지는 중인 토스트는 그 정도만큼 센다. 0이면 맨 앞이다. */
private fun depthOf(rendered: List<ToastEntry>, index: Int): Float {
    var depth = 0f
    for (j in index + 1 until rendered.size) depth += rendered[j].presence.value
    return depth
}

@Composable
private fun ToastStack(state: KToasterState, rendered: List<ToastEntry>, look: StackLook, modifier: Modifier) {
    val still = reducedMotion()
    val expand = remember { Animatable(0f) }
    LaunchedEffect(state.expanded, still) {
        val target = if (state.expanded) 1f else 0f
        if (still) expand.snapTo(target) else expand.animateTo(target, spring(EXPAND_DAMPING, Spring.StiffnessMediumLow))
    }
    val density = LocalDensity.current
    // 겹친 토스트를 자르는 모서리도 토스트 모양 토큰 반지름을 따른다. 캡슐이면 높이의 절반으로 줄여 쓴다.
    val toastCorner = currentShapes.let { it.radius(it.toast) }
    val metrics = remember(density, toastCorner) { StackMetrics(density, toastCorner) }
    val currentRendered by rememberUpdatedState(rendered)
    Layout(
        content = {
            rendered.forEachIndexed { index, entry ->
                key(entry.id) {
                    ToastCard(entry, state, look, still) {
                        // 뒤쪽 토스트의 글자는 앞 토스트에 가려지므로 숨기고, 펼치는 만큼 드러낸다.
                        val at = currentRendered.indexOf(entry)
                        val depth = depthOf(currentRendered, if (at < 0) index else at)
                        maxOf(expand.value, 1f - depth.coerceIn(0f, 1f))
                    }
                }
            }
        },
        modifier = modifier
            .padding(horizontal = STACK_SIDE, vertical = STACK_EDGE)
            .widthIn(max = STACK_MAX_WIDTH)
            .fillMaxWidth(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints(constraints.maxWidth, constraints.maxWidth, 0, Constraints.Infinity)) }
        val frames = stackFrames(rendered, placeables.map { it.height }, expand.value, look, metrics)
        val height = frames.maxOfOrNull { it.y + it.clip }?.roundToInt() ?: 0
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { i, placeable ->
                val f = frames[i]
                val y = if (look.top) f.y else height - f.y - f.clip
                placeable.placeWithLayer(0, y.roundToInt()) {
                    scaleX = f.scale
                    scaleY = f.scale
                    alpha = f.alpha
                    transformOrigin = TransformOrigin(HALF, if (look.top && placeable.height > 0) f.clip / placeable.height else 0f)
                    if (f.clip < placeable.height - 1f) {
                        clip = true
                        shape = TopClipShape(f.clip, metrics.corner)
                    }
                }
            }
        }
    }
}

/** 쌓기에 쓰는 px 치수 */
private class StackMetrics(density: Density, radius: Dp) {
    val peek = with(density) { STACK_PEEK.toPx() }
    val gap = with(density) { STACK_GAP.toPx() }
    val corner = if (radius == Dp.Infinity) Float.POSITIVE_INFINITY else with(density) { radius.toPx() }
}

/** 토스트 하나의 자리. [y]는 쌓는 가장자리에서 보이는 부분 시작까지 거리, [clip]은 보이는 높이다. */
private class StackFrame(val y: Float, val clip: Float, val scale: Float, val alpha: Float)

/**
 * 겹친 자리와 펼친 자리를 [expand]만큼 섞는다. 겹쳤을 때 뒤 토스트는 맨 앞 토스트 높이로 잘라 그 끝이 [StackMetrics.peek]씩 비치게 하고,
 * 펼쳤을 때는 앞 토스트들의 실제 높이와 간격만큼 떨어뜨린다.
 */
private fun stackFrames(rendered: List<ToastEntry>, heights: List<Int>, expand: Float, look: StackLook, m: StackMetrics): List<StackFrame> {
    val n = rendered.size
    if (n == 0) return emptyList()
    val newest = heights[n - 1].toFloat()
    val front = if (n >= 2) heights[n - 2] + (newest - heights[n - 2]) * rendered[n - 1].presence.value else newest
    var expandedOffset = 0f
    val frames = arrayOfNulls<StackFrame>(n)
    for (i in n - 1 downTo 0) {
        val h = heights[i].toFloat()
        val depth = depthOf(rendered, i)
        val visible = minOf(h, front)
        val collapsedY = depth.coerceAtMost((look.maxVisible - 1).toFloat()) * m.peek + front - visible
        val y = collapsedY + (expandedOffset - collapsedY) * expand
        val clip = visible + (h - visible) * expand
        val shrink = 1f - depth.coerceAtMost(look.maxVisible.toFloat()) * SCALE_STEP
        val scale = shrink + (1f - shrink) * expand
        val alpha = (look.maxVisible - depth).coerceIn(0f, 1f)
        frames[i] = StackFrame(y, clip, scale, alpha)
        expandedOffset += (h + m.gap) * rendered[i].presence.value
    }
    return frames.map { requireNotNull(it) }
}

/** 위에서부터 [height]만큼만 남기는 둥근 사각형 */
private class TopClipShape(private val height: Float, private val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val h = height.coerceIn(0f, size.height)
        val r = minOf(corner, h / 2)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, h, CornerRadius(r)))
    }
}

@Composable
private fun ToastCard(entry: ToastEntry, state: KToasterState, look: StackLook, still: Boolean, contentAlpha: () -> Float) {
    ToastEffects(entry, state, still)
    val travel = with(LocalDensity.current) { TOAST_TRAVEL.toPx() }
    val shape = shapeFor { toast }
    Row(
        Modifier
            .swipeAway(entry, state) { p, swipe ->
                translationY = (if (look.top) -1f else 1f) * travel * (1f - p)
                val s = ENTER_SCALE + (1f - ENTER_SCALE) * p
                scaleX = s
                scaleY = s
                alpha *= p.coerceIn(0f, 1f)
                translationX = swipe
            }
            .toastSurface(entry, look, shape)
            .toastSemantics(entry, state)
            .heightIn(min = TOAST_MIN_HEIGHT)
            .padding(start = 14.dp, end = if (entry.action != null) 8.dp else 18.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { ToastBody(entry, state, look, contentAlpha) }
}

/** 나타나고 사라지는 애니메이션, 사라지기 타이머, 결과 햅틱 */
@Composable
private fun ToastEffects(entry: ToastEntry, state: KToasterState, still: Boolean) {
    val adjust = rememberAccessibleTimeout(withControls = entry.action != null)
    val haptics = rememberKHaptics()
    LaunchedEffect(entry.leaving) {
        if (entry.leaving) {
            if (still) entry.presence.snapTo(0f) else entry.presence.animateTo(0f, tween(TOAST_OUT_MS))
            state.remove(entry)
        } else if (still) {
            entry.presence.snapTo(1f)
        } else {
            entry.presence.animateTo(1f, spring(ENTER_DAMPING, Spring.StiffnessMediumLow))
        }
    }
    LaunchedEffect(entry.version, entry.loading, state.expanded, entry.leaving) {
        if (entry.loading || state.expanded || entry.leaving) return@LaunchedEffect
        delay(adjust(entry.durationMs))
        state.dismiss(entry.id)
    }
    // 로딩이 끝나 결과로 바뀐 순간에만 진동한다. 처음 뜰 때는 울리지 않는다.
    LaunchedEffect(entry.version) {
        if (entry.version > 0) haptics.perform(if (entry.status == KStatus.Error) KHapticType.Error else KHapticType.Success)
    }
}

/**
 * 옆으로 쓸어 닫는 동작. 너비의 35% 넘게 끌거나 빠르게 던지면 그 방향으로 날아가며 닫히고, 아니면 제자리로 돌아온다.
 * [layer]는 나타남 정도와 쓸어 간 거리(px)를 받아 레이어를 꾸민다. 투명도는 쓸어 간 만큼 이미 줄여 둔다.
 */
@Composable
private fun Modifier.swipeAway(
    entry: ToastEntry,
    state: KToasterState,
    layer: androidx.compose.ui.graphics.GraphicsLayerScope.(Float, Float) -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val swipe = remember { SettleOffset() }
    var width by remember { mutableIntStateOf(1) }
    val release: suspend (Float) -> Unit = { velocity ->
        val flung = abs(velocity) > SWIPE_FLING_VELOCITY
        if (abs(swipe.value) > width * SWIPE_FRACTION || flung) {
            val direction = if (flung) sign(velocity) else sign(swipe.value)
            state.dismiss(entry.id)
            swipe.animateTo(direction * width * SWIPE_OUT, tween(TOAST_OUT_MS), velocity)
        } else {
            swipe.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), velocity)
        }
    }
    return this
        .onSizeChanged { width = it.width.coerceAtLeast(1) }
        .graphicsLayer {
            alpha = 1f - (abs(swipe.value) / width).coerceIn(0f, 1f) * SWIPE_FADE
            layer(entry.presence.value, swipe.value)
        }
        .draggable(
            rememberDraggableState { delta -> swipe.drag(delta) },
            Orientation.Horizontal,
            onDragStopped = { velocity -> release(velocity) },
        )
        .settleAfterRelease({ swipe.value != 0f && !swipe.isSettling && !entry.leaving }) { scope.launch { release(0f) } }
}

@Composable
private fun Modifier.toastSurface(entry: ToastEntry, look: StackLook, shape: Shape): Modifier {
    val base = kSurface(look.surface, shape)
    if (!look.richColors || entry.status == KStatus.Neutral) return base
    return base.background(entry.status.feedbackColor(look.surface).copy(alpha = RICH_TINT), shape)
}

@Composable
private fun Modifier.toastSemantics(entry: ToastEntry, state: KToasterState): Modifier {
    val dismissLabel = stringResource(R.string.kinetica_ui_notify_dismiss)
    val toggleLabel = stringResource(if (state.expanded) R.string.kinetica_ui_feedback_collapse else R.string.kinetica_ui_feedback_expand)
    val several = state.entries.count { !it.leaving } > 1
    return this
        .then(
            if (several) {
                // 같은 노드에 live region이 있으므로 역할을 두지 않는다. 있으면 TalkBack이 알림 앞에 "버튼"부터 읽는다.
                Modifier.clickable(null, KIndication(shapeFor { toast }), onClickLabel = toggleLabel) {
                    state.expanded = !state.expanded
                }
            } else {
                Modifier
            },
        )
        .semantics(mergeDescendants = true) {
            liveRegion = if (entry.status == KStatus.Error) LiveRegionMode.Assertive else LiveRegionMode.Polite
            dismiss(dismissLabel) {
                state.dismiss(entry.id)
                true
            }
        }
}

@Composable
private fun RowScope.ToastBody(entry: ToastEntry, state: KToasterState, look: StackLook, contentAlpha: () -> Float) {
    val ink = look.surface.contentColor
    Row(Modifier.weight(1f, fill = false).graphicsLayer { alpha = contentAlpha() }, verticalAlignment = Alignment.CenterVertically) {
        if (entry.loading) {
            BusyGlyph(TOAST_ICON, KineticaTheme.colors.accent)
        } else if (entry.status != KStatus.Neutral) {
            KStatusIcon(entry.status, contentDescription = statusLabel(entry.status), size = TOAST_ICON, surface = look.surface)
        }
        Column(Modifier.padding(start = if (entry.loading || entry.status != KStatus.Neutral) 10.dp else 4.dp)) {
            ThemedText(
                entry.message,
                style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp),
            )
            entry.description?.let {
                ThemedText(it, style = TextStyle(color = look.surface.muted(DESCRIPTION_ALPHA), fontSize = 13.sp, lineHeight = 18.sp))
            }
        }
    }
    entry.action?.let { action ->
        val actionShape = shapeFor { button }
        ThemedText(
            action.label,
            Modifier
                .padding(start = 10.dp)
                .graphicsLayer { alpha = contentAlpha() }
                .background(look.surface.raisedFill(ACTION_BACK_ALPHA), actionShape)
                .clickable(null, KIndication(actionShape), role = Role.Button) {
                    action.onClick()
                    state.dismiss(entry.id)
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = KineticaTheme.design.label(FontWeight.SemiBold)),
            maxLines = 1,
        )
    }
}

private const val HALF = 0.5f
private val STACK_SIDE = 16.dp
private val STACK_EDGE = 8.dp
private val STACK_MAX_WIDTH = 440.dp
private val STACK_PEEK = 10.dp
private val STACK_GAP = 8.dp
private val TOAST_MIN_HEIGHT = 48.dp
private val TOAST_ICON = 20.dp
private val TOAST_TRAVEL = 28.dp
private const val SCALE_STEP = 0.05f
private const val EXPAND_DAMPING = 0.82f
private const val ENTER_DAMPING = 0.7f
private const val ENTER_SCALE = 0.92f
private const val TOAST_OUT_MS = 200
private const val RICH_TINT = 0.14f
private const val DESCRIPTION_ALPHA = 0.7f
private const val ACTION_BACK_ALPHA = 0.12f
private const val SWIPE_FRACTION = 0.35f
private const val SWIPE_OUT = 1.2f
private const val SWIPE_FADE = 0.8f
private const val SWIPE_FLING_VELOCITY = 1_200f
