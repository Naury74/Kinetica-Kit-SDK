package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 지금 보이는 몸체. 활동이 바뀌거나 펼침이 바뀌면 잠깐 흐려졌다가 새 몸체로 바뀐다. */
private data class BodyKey(val id: String, val expanded: Boolean)

/**
 * 큰 캡슐. 크기는 몸체의 자연 크기를 용수철로 따라가고, 덜 부푼 동안은 지금 크기의 캡슐로 몸체를 잘라 바깥으로 삐져나오지 않게 한다.
 * [KIslandHost]의 아일랜드와 같은 방식이다.
 */
@Composable
internal fun LiveCapsule(state: KLiveActivityState, activity: KLiveActivity, expanded: Boolean, look: LiveLook) {
    var natural by remember { mutableStateOf(IntSize.Zero) }
    val (width, height) = rememberMorphSize(natural)
    val shown = rememberShownBody(BodyKey(activity.id, expanded))
    val body = state.find(shown.key.id) ?: activity
    val shape = remember { CapsuleShape(LIVE_MAX_CORNER) }
    val clip = remember { Path() }
    val maxWidth = with(LocalDensity.current) { LIVE_MAX_WIDTH.roundToPx() }
    val barProgress = activity.progress.takeIf { !look.top && !expanded }
    val accent = if (activity.accent.isSpecified) activity.accent else KineticaTheme.colors.accent
    Layout(
        content = {
            Box(
                Modifier.kSurface(look.surface, shape).drawWithContent {
                    drawContent()
                    drawBottomBar(barProgress, accent, look)
                },
            )
            Box(
                Modifier
                    .onSizeChanged { natural = it }
                    .drawWithContent {
                        val w = width.value
                        val h = height.value
                        val left = (size.width - w) / 2
                        val top = (size.height - h) / 2
                        clip.reset()
                        clip.addRoundRect(RoundRect(left, top, left + w, top + h, CornerRadius(min(h / 2, LIVE_MAX_CORNER.toPx()))))
                        clipPath(clip) { this@drawWithContent.drawContent() }
                    }
                    .graphicsLayer { alpha = shown.alpha.value },
            ) {
                CompositionLocalProvider(LocalKineticaContentColor provides look.surface.contentColor) {
                    if (shown.key.expanded) ExpandedBody(body) else CompactBody(body, look)
                }
            }
        },
        modifier = Modifier.capsuleGestures(state, activity.id, expanded, look).capsuleSemantics(state, activity, expanded),
    ) { measurables, constraints ->
        val w = width.value.roundToInt().coerceAtLeast(0)
        val h = height.value.roundToInt().coerceAtLeast(0)
        val bodyPlaceable = measurables[1].measure(Constraints(maxWidth = min(constraints.maxWidth, maxWidth)))
        val background = measurables[0].measure(Constraints.fixed(w, h))
        layout(w, h) {
            background.place(0, 0)
            bodyPlaceable.place((w - bodyPlaceable.width) / 2, (h - bodyPlaceable.height) / 2)
        }
    }
}

/** 몸체의 자연 크기를 용수철로 따라가는 너비와 높이(px). 처음과 '애니메이션 삭제'에서는 바로 맞춘다. */
@Composable
private fun rememberMorphSize(natural: IntSize): Pair<Animatable<Float, *>, Animatable<Float, *>> {
    val width = remember { Animatable(0f) }
    val height = remember { Animatable(0f) }
    val still = reducedMotion()
    LaunchedEffect(natural, still) {
        if (natural == IntSize.Zero) return@LaunchedEffect
        if (still || width.value == 0f) {
            width.snapTo(natural.width.toFloat())
            height.snapTo(natural.height.toFloat())
        } else {
            val spec = spring<Float>(dampingRatio = LIVE_MORPH_DAMPING, stiffness = LIVE_MORPH_STIFFNESS)
            launch { width.animateTo(natural.width.toFloat(), spec) }
            height.animateTo(natural.height.toFloat(), spec)
        }
    }
    return remember(width, height) { width to height }
}

/** 보이는 몸체와 그 불투명도. */
private class ShownBody(initial: BodyKey) {
    var key by mutableStateOf(initial)
    val alpha = Animatable(0f)
}

@Composable
private fun rememberShownBody(target: BodyKey): ShownBody {
    val shown = remember { ShownBody(target) }
    val still = reducedMotion()
    LaunchedEffect(target, still) {
        if (shown.key != target) {
            if (!still) shown.alpha.animateTo(0f, tween(LIVE_FADE_OUT_MS))
            shown.key = target
        }
        if (still) shown.alpha.snapTo(1f) else shown.alpha.animateTo(1f, tween(LIVE_FADE_IN_MS, LIVE_FADE_IN_DELAY))
    }
    return shown
}

/** 아래 위치 compact 캡슐 바닥의 가는 진행 막대. 양 끝은 캡슐 곡선을 피해 들여 그린다. */
private fun DrawScope.drawBottomBar(progress: (() -> Float)?, accent: Color, look: LiveLook) {
    if (progress == null) return
    val inset = size.height / 2
    val thickness = LIVE_BAR.toPx()
    val y = size.height - thickness - LIVE_BAR_BOTTOM.toPx()
    val track = size.width - inset * 2
    if (track <= 0f) return
    val radius = CornerRadius(thickness / 2)
    drawRoundRect(look.surface.contentColor.copy(alpha = LIVE_TRACK_ALPHA), Offset(inset, y), Size(track, thickness), radius)
    val w = track * progress().coerceIn(0f, 1f)
    if (w <= 0f) return
    val x = if (layoutDirection == LayoutDirection.Rtl) inset + track - w else inset
    drawRoundRect(accent, Offset(x, y), Size(w, thickness), radius)
}

@Composable
private fun CompactBody(activity: KLiveActivity, look: LiveLook) {
    if (look.top) {
        Row(
            Modifier.height(LIVE_TOP_HEIGHT).widthIn(min = LIVE_COMPACT_MIN).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.heightIn(max = LIVE_TOP_SLOT), contentAlignment = Alignment.Center) { activity.leading() }
            // 가운데는 비워 카메라 홀을 피한다.
            Spacer(Modifier.widthIn(min = LIVE_CAMERA_GAP))
            Box(Modifier.heightIn(max = LIVE_TOP_SLOT), contentAlignment = Alignment.Center) { activity.trailing() }
        }
    } else {
        NowBarBody(activity, look)
    }
}

@Composable
private fun NowBarBody(activity: KLiveActivity, look: LiveLook) {
    Row(
        Modifier
            .heightIn(min = LIVE_BOTTOM_HEIGHT)
            .widthIn(min = LIVE_NOW_MIN, max = LIVE_NOW_MAX)
            .padding(start = 10.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(LIVE_BOTTOM_SLOT).clip(CircleShape), contentAlignment = Alignment.Center) { activity.leading() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            ThemedText(
                activity.title,
                style = TextStyle(color = look.surface.muted(LIVE_TITLE_ALPHA), fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ThemedText(
                activity.status,
                style = TextStyle(color = look.surface.contentColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        activity.trailing()
    }
}

@Composable
private fun ExpandedBody(activity: KLiveActivity) {
    Box(Modifier.widthIn(max = LIVE_MAX_WIDTH).fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) { activity.expanded() }
}

/** 누르면 펼치거나 접고, 길게 누르면 펼친다. 펼친 뒤 가장자리 쪽으로 밀면 접는다. */
@Composable
private fun Modifier.capsuleGestures(state: KLiveActivityState, id: String, expanded: Boolean, look: LiveLook): Modifier {
    val scope = rememberCoroutineScope()
    val haptic = kHapticFeedback()
    val drag = remember { SettleOffset() }
    val threshold = with(LocalDensity.current) { LIVE_SWIPE.toPx() }
    val toward = if (look.top) -1f else 1f
    val latestId by rememberUpdatedState(id)
    val latestExpanded by rememberUpdatedState(expanded)
    val release: suspend (Float) -> Unit = { velocity ->
        if (latestExpanded && (drag.value * toward > threshold || velocity * toward > LIVE_FLING)) state.collapse()
        drag.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), velocity)
    }
    return this
        .graphicsLayer { translationY = drag.value }
        .draggable(
            rememberDraggableState { delta ->
                // 가장자리 반대쪽으로는 조금만 따라오게 해서 붙잡힌 느낌을 준다.
                drag.drag(if ((drag.value + delta) * toward < 0f) delta * LIVE_RESIST else delta)
            },
            Orientation.Vertical,
            enabled = expanded,
            onDragStopped = { velocity -> release(velocity) },
        )
        .settleAfterRelease({ drag.value != 0f && !drag.isSettling }) { scope.launch { release(0f) } }
        .pointerInput(state) {
            detectTapGestures(
                onLongPress = {
                    if (!latestExpanded) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        state.expand(latestId)
                    }
                },
                onTap = { if (latestExpanded) state.collapse() else state.expand(latestId) },
            )
        }
}

@Composable
private fun Modifier.capsuleSemantics(state: KLiveActivityState, activity: KLiveActivity, expanded: Boolean): Modifier {
    val expandLabel = stringResource(R.string.kinetica_ui_notify_expand)
    val collapseLabel = stringResource(R.string.kinetica_ui_sheet_collapse)
    if (expanded) {
        // 누를 수 있는 노드라 TalkBack이 안쪽 글자를 묶어 읽고, 안쪽 버튼은 따로 초점을 받는다.
        return semantics {
            onClick(collapseLabel) {
                state.collapse()
                true
            }
            collapse(collapseLabel) {
                state.collapse()
                true
            }
        }
    }
    return clearAndSetSemantics {
        contentDescription = activity.spoken
        onClick(expandLabel) { state.expand(activity.id) }
        expand(expandLabel) { state.expand(activity.id) }
    }
}

/** 두 번째 활동의 원. [separation]이 0이면 캡슐 끝에 숨어 있고 1이면 떨어져 나와 있다. */
@Composable
internal fun LiveBubble(state: KLiveActivityState, activity: KLiveActivity, visible: Boolean, look: LiveLook, separation: () -> Float) {
    val haptic = kHapticFeedback()
    val accent = if (activity.accent.isSpecified) activity.accent else KineticaTheme.colors.accent
    val latestId by rememberUpdatedState(activity.id)
    val expandLabel = stringResource(R.string.kinetica_ui_notify_expand)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        Modifier
            .size(look.compactHeight)
            .graphicsLayer {
                val s = separation()
                alpha = s.coerceIn(0f, 1f)
                val scale = LIVE_BUBBLE_MIN_SCALE + (1f - LIVE_BUBBLE_MIN_SCALE) * s
                scaleX = scale
                scaleY = scale
                // 캡슐 쪽 가장자리를 축으로 커져야 캡슐에서 빠져나오는 것처럼 보인다.
                transformOrigin = if (rtl) LIVE_ORIGIN_END_RTL else LIVE_ORIGIN_START
            }
            .then(
                if (visible) {
                    Modifier
                        .clearAndSetSemantics {
                            contentDescription = activity.spoken
                            onClick(expandLabel) { state.expand(activity.id) }
                            expand(expandLabel) { state.expand(activity.id) }
                        }
                        .pointerInput(state) {
                            detectTapGestures(
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    state.expand(latestId)
                                },
                                onTap = { state.expand(latestId) },
                            )
                        }
                } else {
                    Modifier.clearAndSetSemantics { }
                },
            )
            .kSurface(look.surface, CircleShape)
            .drawWithContent {
                drawContent()
                activity.progress?.let { drawBubbleRing(it().coerceIn(0f, 1f), accent, look.surface.contentColor) }
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalKineticaContentColor provides look.surface.contentColor) {
            val inner = if (look.top) LIVE_TOP_SLOT else LIVE_BOTTOM_SLOT
            Box(Modifier.size(inner).clip(CircleShape), contentAlignment = Alignment.Center) { (activity.minimal ?: activity.leading)() }
        }
    }
}

private fun DrawScope.drawBubbleRing(progress: Float, accent: Color, ink: Color) {
    val stroke = LIVE_RING.toPx()
    val inset = stroke / 2 + LIVE_RING_INSET.toPx()
    val arc = Size(size.width - inset * 2, size.height - inset * 2)
    drawArc(ink.copy(alpha = LIVE_TRACK_ALPHA), 0f, LIVE_FULL_TURN, false, Offset(inset, inset), arc, style = Stroke(stroke))
    if (progress > 0f) drawArc(accent, LIVE_TWELVE, LIVE_FULL_TURN * progress, false, Offset(inset, inset), arc, style = Stroke(stroke))
}

private const val LIVE_MORPH_DAMPING = 0.7f
private const val LIVE_FULL_TURN = 360f
private const val LIVE_TWELVE = -90f
private val LIVE_ORIGIN_START = TransformOrigin(0f, 0.5f)
private val LIVE_ORIGIN_END_RTL = TransformOrigin(1f, 0.5f)
private const val LIVE_MORPH_STIFFNESS = 300f
private const val LIVE_FADE_OUT_MS = 90
private const val LIVE_FADE_IN_MS = 220
private const val LIVE_FADE_IN_DELAY = 60
private const val LIVE_TRACK_ALPHA = 0.22f
private const val LIVE_TITLE_ALPHA = 0.65f
private const val LIVE_RESIST = 0.3f
private const val LIVE_FLING = 900f
private const val LIVE_BUBBLE_MIN_SCALE = 0.4f
private val LIVE_MAX_WIDTH = 420.dp
private val LIVE_MAX_CORNER = 32.dp
private val LIVE_COMPACT_MIN = 168.dp
private val LIVE_CAMERA_GAP = 48.dp
private val LIVE_TOP_SLOT = 24.dp
private val LIVE_BOTTOM_SLOT = 32.dp
private val LIVE_NOW_MIN = 240.dp
private val LIVE_NOW_MAX = 360.dp
private val LIVE_BAR = 2.dp
private val LIVE_BAR_BOTTOM = 4.dp
private val LIVE_RING = 2.5.dp
private val LIVE_RING_INSET = 1.dp
private val LIVE_SWIPE = 24.dp
