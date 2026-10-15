// 파일 이름은 대표 컴포저블을 따른다. 함께 쓰는 데이터 클래스/상태 클래스가 하나뿐이라 detekt가 그 이름을 요구한다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * [GlassMiniPlayer]가 막대로 접혀 있는지 전체 화면으로 펼쳐져 있는지를 담는다. [rememberMiniPlayerState]로 만든다.
 */
@Stable
public class MiniPlayerState internal constructor(initiallyExpanded: Boolean) {
    private val motion = Animatable(if (initiallyExpanded) 1f else 0f)

    /** 펼쳐진 정도. 0이면 막대, 1이면 전체 화면이다. 스프링 때문에 잠깐 0..1을 조금 벗어날 수 있다. */
    public val progress: Float get() = motion.value

    /** 펼치기로 정해졌거나 펼쳐져 있으면 true. 끄는 동안에는 손을 떼기 전 상태를 유지한다. */
    public var isExpanded: Boolean by mutableStateOf(initiallyExpanded)
        private set

    /** 전체 화면으로 펼친다. 애니메이션이 끝날 때 돌아오고, 그 전에 다른 동작이 끼어들면 취소된다. */
    public suspend fun expand() {
        settleTo(true, 0f)
    }

    /** 막대로 접는다. 애니메이션이 끝날 때 돌아오고, 그 전에 다른 동작이 끼어들면 취소된다. */
    public suspend fun collapse() {
        settleTo(false, 0f)
    }

    internal suspend fun dragBy(delta: Float) {
        motion.snapTo((motion.value + delta).coerceIn(0f, 1f))
    }

    /** 손을 뗀 속도([velocity], 초당 진행량, 양수면 펼치는 쪽)를 보고 펼칠지 접을지 정한다. */
    internal suspend fun release(velocity: Float) {
        val open = when {
            velocity > FLING -> true
            velocity < -FLING -> false
            else -> motion.value > HALF
        }
        settleTo(open, velocity)
    }

    private suspend fun settleTo(open: Boolean, velocity: Float) {
        isExpanded = open
        motion.animateTo(
            if (open) 1f else 0f,
            spring(dampingRatio = if (open) OPEN_DAMPING else CLOSE_DAMPING, stiffness = Spring.StiffnessMediumLow),
            initialVelocity = velocity,
        )
    }
}

/** 화면 회전이나 프로세스 재시작 뒤에도 펼침 여부를 기억하는 [MiniPlayerState]를 만든다. */
@Composable
public fun rememberMiniPlayerState(initiallyExpanded: Boolean = false): MiniPlayerState =
    rememberSaveable(saver = MiniPlayerSaver) { MiniPlayerState(initiallyExpanded) }

private val MiniPlayerSaver = Saver<MiniPlayerState, Boolean>(save = { it.isExpanded }, restore = { MiniPlayerState(it) })

/**
 * 화면 아래에 떠 있는 글래스 캡슐 모양의 미니 플레이어. 막대를 누르거나 위로 끌면 전체 화면 플레이어로 커지고,
 * 아래로 끌거나 뒤로 가기를 하면 다시 막대로 접힌다. 손을 뗄 때 끄는 속도가 빠르면 거리가 짧아도 그 방향으로 넘어간다.
 * 펼칠수록 [GlassScaffold]의 콘텐츠는 뒤로 물러나고 블러 처리된다.
 *
 * [artwork]를 주면 앨범 아트를 플레이어가 직접 배치한다. 막대에서는 왼쪽(RTL이면 오른쪽) 작은 사각형에, 펼치면 위쪽 가운데 큰 사각형에
 * 놓이고 그 사이를 공유 요소처럼 커지며 이동한다. 이때 [collapsed]는 아트 옆 남은 공간에, [expanded]는 아트 아래 공간에 그린다.
 * 아트를 직접 그리려면 [artwork]를 비우고 두 람다가 받는 진행도(0..1)로 위치를 맞춘다. 진행도는 매 프레임 바뀌므로 함수로 넘기며,
 * 슬롯이 매 프레임 다시 구성되지 않도록 `Modifier.graphicsLayer { }`나 `Modifier.offset { }`처럼 그리기, 배치 단계의 람다 안에서 읽는다.
 *
 * [GlassScaffold] 안에서 부르면 오버레이 레이어에 그린다. 밖에서 부르면 호출한 자리를 화면 전체로 보고 그린다.
 *
 * ```kotlin
 * val player = rememberMiniPlayerState()
 * GlassMiniPlayer(
 *     player,
 *     artwork = { Image(cover, null, contentScale = ContentScale.Crop) },
 *     barBottomPadding = 72.dp,
 *     collapsed = { Text("Song title") },
 *     expanded = { PlayerControls() },
 * )
 * ```
 *
 * @param barHeight 글꼴 배율이 1일 때의 막대 높이. 글꼴을 키우면 최대 1.4배까지 함께 커진다. 아트는 막대 높이에서 위아래 10dp씩 뺀 크기다.
 * @param barBottomPadding 막대 아래 여백. 탭 바 위에 띄우려면 탭 바 높이를 준다. 내비게이션 바 높이는 따로 더한다.
 * @param collapsed 막대 내용. 펼침 진행도를 읽는 함수를 받으며, 펼쳐지는 동안 흐려지다가 반 넘게 펼치면 그리지 않는다.
 * @param expanded 전체 화면 내용. 펼침 진행도를 읽는 함수를 받으며, 펼치기 시작하면 그리기 시작해 서서히 나타난다.
 */
@Composable
public fun GlassMiniPlayer(
    state: MiniPlayerState,
    modifier: Modifier = Modifier,
    style: GlassStyle = LocalGlassStyle.current,
    artwork: (@Composable () -> Unit)? = null,
    barHeight: Dp = 64.dp,
    barBottomPadding: Dp = 0.dp,
    collapsed: @Composable (progress: () -> Float) -> Unit,
    expanded: @Composable (progress: () -> Float) -> Unit,
) {
    val scope = rememberCoroutineScope()
    GlassOverlay(visible = true, kind = OverlayKind.InPlace) { _ ->
        BackHandler(enabled = state.isExpanded) { scope.launch { state.collapse() } }
        // 진행도는 매 프레임 바뀌므로 읽는 곳을 작은 컴포저블로 나눠 그 범위만 다시 구성되게 한다.
        PlayerPushBack(state)
        BoxWithConstraints(modifier.fillMaxSize()) {
            val bar = barHeight * LocalDensity.current.fontScale.coerceIn(1f, MAX_BAR_GROW)
            val geometry = playerGeometry(constraints, bar, barBottomPadding, artwork != null)
            PlayerScrim(state, scope)
            val progress = remember(state) { { state.progress } }
            val slots = remember(scope, collapsed, expanded, artwork != null, progress) {
                PlayerSlots(scope, collapsed, expanded, artwork != null, progress)
            }
            PlayerFrame(state, geometry, style.forPopup(), slots)
            if (artwork != null) Artwork(state, geometry, artwork)
        }
    }
}

@Composable
private fun PlayerPushBack(state: MiniPlayerState) {
    ReportPushBack(state.progress.coerceIn(0f, 1f))
}

@Composable
private fun BoxScope.PlayerScrim(state: MiniPlayerState, scope: CoroutineScope) {
    val level = state.progress.coerceIn(0f, 1f)
    if (level > 0f) Scrim(level) { scope.launch { state.collapse() } }
}

@Stable
private class PlayerSlots(
    // 막대와 펼친 화면은 진행도에 따라 컴포지션에서 빠지므로, 그 안에서 만든 스코프로 애니메이션을 돌리면 도중에 취소된다.
    val scope: CoroutineScope,
    val collapsed: @Composable (() -> Float) -> Unit,
    val expanded: @Composable (() -> Float) -> Unit,
    val hasArtwork: Boolean,
    val progress: () -> Float,
)

/** 막대와 전체 화면, 두 아트 위치(px, 오버레이 기준). 아트의 x는 LTR 기준이며 RTL에서는 배치할 때 뒤집힌다. */
@Stable
private class PlayerGeometry(val screen: IntSize, val bar: Rect, val full: Rect, val barArt: Rect, val fullArt: Rect, val barCorner: Dp) {
    val travel: Float get() = (bar.top - full.top).coerceAtLeast(1f)
}

@Composable
private fun playerGeometry(constraints: Constraints, barHeight: Dp, barBottomPadding: Dp, hasArtwork: Boolean): PlayerGeometry {
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    return with(density) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val barBottom = h - navBottom - barBottomPadding.toPx() - BAR_GAP.toPx()
        val bar = Rect(BAR_MARGIN.toPx(), barBottom - barHeight.toPx(), w - BAR_MARGIN.toPx(), barBottom)
        val full = Rect(0f, statusTop + FULL_TOP_GAP.toPx(), w, h)
        val inset = ART_INSET.toPx()
        val small = (barHeight.toPx() - inset * 2).coerceAtLeast(0f)
        val barArt = Rect(bar.left + inset, bar.top + inset, bar.left + inset + small, bar.top + inset + small)
        val big = minOf(w - ART_SIDE.toPx() * 2, full.height * ART_MAX_HEIGHT, ART_MAX.toPx()).coerceAtLeast(small)
        val artTop = full.top + HANDLE_AREA.toPx()
        val fullArt = Rect((w - big) / 2f, artTop, (w + big) / 2f, artTop + big)
        PlayerGeometry(
            IntSize(constraints.maxWidth, constraints.maxHeight),
            bar,
            full,
            if (hasArtwork) barArt else Rect.Zero,
            if (hasArtwork) fullArt else Rect.Zero,
            barHeight / 2,
        )
    }
}

@Composable
private fun PlayerFrame(state: MiniPlayerState, geometry: PlayerGeometry, style: GlassStyle, slots: PlayerSlots) {
    val level = state.progress.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(lerp(geometry.barCorner, FULL_CORNER, level))
    // 이 함수는 모양 때문에 매 프레임 다시 구성되지만, 아래 슬롯은 인자가 그대로라 건너뛴다.
    val showCollapsed = level < COLLAPSED_GONE
    val showExpanded = level > 0f
    Box(
        Modifier
            .placeAt(geometry.screen, relative = false) { lerp(geometry.bar, geometry.full, state.progress) }
            // 화면 가장자리에 닿으면 블러가 화면 밖의 빈 픽셀을 끌어와 테두리가 어두워진다. 펼칠수록 바탕색을 깔아 가린다.
            .background(style.fallback.copy(alpha = style.fallback.alpha * level), shape)
            .glass(style, shape)
            .clip(shape)
            .playerDrag(state, geometry.travel),
    ) {
        CompositionLocalProvider(LocalGlassContentColor provides style.onGlass) {
            if (showCollapsed) CollapsedBar(state, geometry, slots)
            if (showExpanded) ExpandedPanel(state, geometry, slots)
        }
    }
}

@Composable
private fun CollapsedBar(state: MiniPlayerState, geometry: PlayerGeometry, slots: PlayerSlots) {
    val scope = slots.scope
    val density = LocalDensity.current
    val start = if (slots.hasArtwork) with(density) { (geometry.barArt.right - geometry.bar.left).toDp() } + ART_INSET else BAR_PADDING
    Box(
        Modifier
            .fillMaxWidth()
            .height(with(density) { geometry.bar.height.toDp() })
            .graphicsLayer { alpha = (1f - state.progress / COLLAPSED_GONE).coerceIn(0f, 1f) }
            .clickable(onClickLabel = stringResource(R.string.kinetica_ui_player_expand), role = Role.Button) {
                scope.launch { state.expand() }
            }
            .padding(start = start, end = BAR_PADDING),
        contentAlignment = Alignment.CenterStart,
    ) {
        slots.collapsed(slots.progress)
    }
}

@Composable
private fun ExpandedPanel(state: MiniPlayerState, geometry: PlayerGeometry, slots: PlayerSlots) {
    val scope = slots.scope
    val density = LocalDensity.current
    val title = stringResource(R.string.kinetica_ui_player_now_playing)
    val collapseLabel = stringResource(R.string.kinetica_ui_player_collapse)
    val collapse = {
        scope.launch { state.collapse() }
        true
    }
    val top = with(density) {
        if (slots.hasArtwork) (geometry.fullArt.bottom - geometry.full.top).toDp() + CONTENT_GAP else HANDLE_AREA
    }
    Box(
        Modifier
            .fixedSize(geometry.full.width.roundToInt(), geometry.full.height.roundToInt())
            .graphicsLayer {
                val progress = state.progress
                alpha = ((progress - PANEL_FADE_START) / (1f - PANEL_FADE_START)).coerceIn(0f, 1f)
                translationY = PANEL_RISE.toPx() * (1f - progress)
            }
            .semantics {
                paneTitle = title
                dismiss { collapse() }
                customActions = listOf(CustomAccessibilityAction(collapseLabel) { collapse() })
            },
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .clickable(onClickLabel = collapseLabel, role = Role.Button) { collapse() }
                .padding(horizontal = HANDLE_TOUCH, vertical = HANDLE_PAD)
                .size(HANDLE_WIDTH, HANDLE_HEIGHT)
                .background(LocalGlassContentColor.current.copy(alpha = HANDLE_ALPHA), RoundedCornerShape(HANDLE_HEIGHT)),
        )
        Box(Modifier.fillMaxSize().padding(top = top)) { slots.expanded(slots.progress) }
    }
}

@Composable
private fun Artwork(state: MiniPlayerState, geometry: PlayerGeometry, artwork: @Composable () -> Unit) {
    val level = state.progress.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(lerp(ART_CORNER_SMALL, ART_CORNER_BIG, level))
    Box(
        Modifier
            .placeAt(geometry.screen, relative = true) { lerp(geometry.barArt, geometry.fullArt, state.progress) }
            .shadow(lerp(ART_SHADOW_SMALL, ART_SHADOW_BIG, level), shape)
            .clip(shape),
    ) { artwork() }
}

@Composable
private fun Modifier.playerDrag(state: MiniPlayerState, travel: Float): Modifier {
    val scope = rememberCoroutineScope()
    return draggable(
        rememberDraggableState { delta -> scope.launch { state.dragBy(-delta / travel) } },
        Orientation.Vertical,
        onDragStopped = { velocity -> state.release(-velocity / travel) },
    )
}

/** 화면 크기([screen]) 안의 [frameOf] 자리에 그 크기로 배치한다. [relative]면 RTL에서 x를 뒤집는다. [frameOf]는 배치 단계에서 읽는다. */
private fun Modifier.placeAt(screen: IntSize, relative: Boolean, frameOf: () -> Rect): Modifier = layout { measurable, _ ->
    val frame = frameOf()
    val placeable = measurable.measure(
        Constraints.fixed(frame.width.roundToInt().coerceAtLeast(1), frame.height.roundToInt().coerceAtLeast(1)),
    )
    layout(screen.width, screen.height) {
        if (relative) {
            placeable.placeRelative(frame.left.roundToInt(), frame.top.roundToInt())
        } else {
            placeable.place(frame.left.roundToInt(), frame.top.roundToInt())
        }
    }
}

/** 부모 크기와 상관없이 [width]×[height]로 재고 부모 크기 안에서 위쪽 시작점에 놓는다. 넘치는 부분은 부모가 잘라 낸다. */
private fun Modifier.fixedSize(width: Int, height: Int): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints.fixed(width.coerceAtLeast(0), height.coerceAtLeast(0)))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.placeRelative(0, 0) }
}

private const val FLING = 1.4f
private const val MAX_BAR_GROW = 1.4f
private const val HALF = 0.5f
private const val OPEN_DAMPING = 0.86f
private const val CLOSE_DAMPING = 0.9f
private const val COLLAPSED_GONE = 0.5f
private const val PANEL_FADE_START = 0.25f
private const val HANDLE_ALPHA = 0.3f
private val BAR_MARGIN = 12.dp
private val BAR_GAP = 8.dp
private val BAR_PADDING = 16.dp
private val FULL_TOP_GAP = 12.dp
private val FULL_CORNER = 32.dp
private val ART_INSET = 10.dp
private val ART_SIDE = 32.dp
private val ART_MAX = 420.dp
private const val ART_MAX_HEIGHT = 0.46f
private val ART_CORNER_SMALL = 10.dp
private val ART_CORNER_BIG = 20.dp
private val ART_SHADOW_SMALL = 2.dp
private val ART_SHADOW_BIG = 24.dp
private val HANDLE_AREA = 44.dp
private val HANDLE_WIDTH = 40.dp
private val HANDLE_HEIGHT = 5.dp
private val HANDLE_PAD = 12.dp
private val HANDLE_TOUCH = 20.dp
private val CONTENT_GAP = 28.dp
private val PANEL_RISE = 40.dp
