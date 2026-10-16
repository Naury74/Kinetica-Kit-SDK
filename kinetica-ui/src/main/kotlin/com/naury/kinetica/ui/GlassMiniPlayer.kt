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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * [GlassMiniPlayer]가 막대로 접혀 있는지 전체 화면으로 펼쳐져 있는지를 담는다. [rememberGlassMiniPlayerState]로 만든다.
 */
@Stable
public class GlassMiniPlayerState internal constructor(initiallyExpanded: Boolean) {
    private val motion = Animatable(if (initiallyExpanded) 1f else 0f)

    /** 펼쳐진 정도. 0이면 막대, 1이면 전체 화면이다. 스프링 때문에 잠깐 0..1을 조금 벗어날 수 있다. */
    public val progress: Float get() = motion.value

    /** 펼치기로 정해졌거나 펼쳐져 있으면 true. 끄는 동안에는 손을 떼기 전 상태를 유지한다. */
    public var isExpanded: Boolean by mutableStateOf(initiallyExpanded)
        private set

    // GlassMiniPlayerBar가 놓인 자리와 그 안 아트 자리(창 기준 px). 바깥 막대가 없으면 비어 있다. 배치 단계에서만 읽는다.
    internal var barBounds: Rect by mutableStateOf(Rect.Zero)
    internal var barArtBounds: Rect by mutableStateOf(Rect.Zero)

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

/** 화면 회전이나 프로세스 재시작 뒤에도 펼침 여부를 기억하는 [GlassMiniPlayerState]를 만든다. */
@Composable
public fun rememberGlassMiniPlayerState(initiallyExpanded: Boolean = false): GlassMiniPlayerState =
    rememberSaveable(saver = MiniPlayerSaver) { GlassMiniPlayerState(initiallyExpanded) }

private val MiniPlayerSaver = Saver<GlassMiniPlayerState, Boolean>(save = { it.isExpanded }, restore = { GlassMiniPlayerState(it) })

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
 * 폭이 600dp 이상이고 가로로 넓은 화면(펼친 폴더블, 태블릿 가로)에서는 펼친 화면의 아트를 시작 쪽 절반 가운데에, [expanded]를 끝 쪽 절반에 둔다.
 * 세로 접힌 선이 있으면 그 선으로 나눈다. 탁자 모드에서는 아트를 접힌 선 위 칸에, [expanded]를 아래 칸에 둔다. 그 밖의 배치에서 [expanded] 폭은 560dp를 넘지 않는다.
 *
 * [GlassScaffold] 안에서 부르면 오버레이 레이어에 그린다. 밖에서 부르면 호출한 자리를 화면 전체로 보고 그린다.
 * 막대를 [GlassFloatingTabBar]의 `accessory`처럼 다른 컴포넌트 안에 두려면 이 함수 대신 [GlassMiniPlayerBar]와
 * [GlassMiniPlayerFullScreen]을 같은 상태로 함께 쓴다.
 *
 * ```kotlin
 * val player = rememberGlassMiniPlayerState()
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
    state: GlassMiniPlayerState,
    modifier: Modifier = Modifier,
    style: GlassStyle = LocalGlassStyle.current,
    artwork: (@Composable () -> Unit)? = null,
    barHeight: Dp = 64.dp,
    barBottomPadding: Dp = 0.dp,
    collapsed: @Composable (progress: () -> Float) -> Unit,
    expanded: @Composable (progress: () -> Float) -> Unit,
) {
    MiniPlayerHost(state, modifier, style, artwork, PlayerLayout(barHeight, barBottomPadding, detached = false), collapsed, expanded)
}

/**
 * [GlassMiniPlayer]의 막대 내용만 그린다. 글래스 캡슐은 그리지 않으므로 [GlassFloatingTabBar]의 `accessory`처럼 이미 글래스 바탕이 있는
 * 슬롯에 넣는다. 받은 공간을 모두 채우며, 누르거나 위로 끌면 [state]를 펼친다. 펼친 화면은 같은 [state]로 부른
 * [GlassMiniPlayerFullScreen]이 그리고, 이 막대가 놓인 자리에서 커지기 시작한다.
 *
 * [artwork]를 주면 시작 쪽에 막대 높이에서 위아래 8dp씩 뺀 정사각형으로 그린다. [GlassMiniPlayerFullScreen]에도 같은 아트를 주면
 * 펼칠 때 그 자리에서 큰 아트로 이어지며 커진다. 펼치는 동안 막대 안의 아트는 숨기고 [content]는 흐려진다.
 *
 * ```kotlin
 * val player = rememberGlassMiniPlayerState()
 * GlassScaffold(
 *     bottomBar = {
 *         GlassFloatingTabBar(tabs, tab, { tab = it }, accessory = {
 *             GlassMiniPlayerBar(player, artwork = { Cover() }) { Title() }
 *         })
 *     },
 * ) { padding ->
 *     Songs(padding)
 *     GlassMiniPlayerFullScreen(player, artwork = { Cover() }) { PlayerControls() }
 * }
 * ```
 *
 * @param content 막대 내용. 펼침 진행도를 읽는 함수를 받는다. 재생 버튼처럼 따로 누를 수 있는 요소를 넣어도 된다.
 */
@Composable
public fun GlassMiniPlayerBar(
    state: GlassMiniPlayerState,
    modifier: Modifier = Modifier,
    artwork: (@Composable () -> Unit)? = null,
    content: @Composable (progress: () -> Float) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val fullTop = WindowInsets.statusBars.getTop(density) + with(density) { FULL_TOP_GAP.toPx() }
    val progress = remember(state) { { state.progress } }
    DisposableEffect(state) {
        onDispose {
            state.barBounds = Rect.Zero
            state.barArtBounds = Rect.Zero
        }
    }
    Row(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { state.barBounds = it.windowRect() }
            .glassClickable(CircleShape, onClickLabel = stringResource(R.string.kinetica_ui_player_expand)) {
                scope.launch { state.expand() }
            }
            .playerDrag(state) { state.barBounds.top - fullTop }
            .padding(horizontal = DETACHED_ART_INSET),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (artwork != null) {
            Box(
                Modifier
                    .padding(vertical = DETACHED_ART_INSET)
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .onGloballyPositioned { state.barArtBounds = it.windowRect() }
                    .graphicsLayer { alpha = if (state.progress > 0f) 0f else 1f }
                    .clip(RoundedCornerShape(ART_CORNER_SMALL)),
            ) { artwork() }
        }
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = DETACHED_CONTENT_GAP)
                .graphicsLayer { alpha = (1f - state.progress / COLLAPSED_GONE).coerceIn(0f, 1f) },
            contentAlignment = Alignment.CenterStart,
        ) {
            // 탭 바 액세서리 밖에 둘 때도 글자가 글래스 위에서 보이도록 스타일의 글자색을 준다.
            CompositionLocalProvider(LocalGlassContentColor provides LocalGlassStyle.current.onGlass) { content(progress) }
        }
    }
}

/**
 * [GlassMiniPlayerBar]와 짝을 이루는 전체 화면 플레이어. 막대는 그리지 않고, [state]가 펼쳐지는 동안에만 막대가 놓인 자리에서
 * 전체 화면으로 커진다. 아래로 끌거나 뒤로 가기, 바깥 누르기로 접으면 막대 자리로 돌아가며 줄어든다. 펼칠수록 [GlassScaffold]의
 * 콘텐츠가 뒤로 물러나고 블러 처리되는 것은 [GlassMiniPlayer]와 같다.
 *
 * 막대가 화면에 없을 때 펼치면 [GlassMiniPlayer]의 기본 막대 자리(화면 아래)에서 커진다. [GlassScaffold] 안에서는 오버레이 레이어에 그리고,
 * 밖에서는 호출한 자리를 화면 전체로 보고 그린다. 막대와 같은 창 안에서 불러야 위치가 맞는다.
 *
 * 펼친 화면의 배치는 [GlassMiniPlayer]와 같다. 폭이 600dp 이상이고 가로로 넓은 화면에서는 아트를 시작 쪽, 내용을 끝 쪽에 나란히 두고,
 * 탁자 모드(가로로 반쯤 접은 폴더블)에서는 아트를 접힌 선 위, 내용을 아래에 둔다. 그 밖에는 아트 아래에 내용을 두며 내용 폭은 560dp를 넘지 않는다.
 *
 * @param artwork 큰 앨범 아트. [GlassMiniPlayerBar]에 준 아트와 같은 내용을 주면 막대의 작은 아트에서 이어지며 커진다.
 * @param content 전체 화면 내용. 펼침 진행도를 읽는 함수를 받으며, 펼치기 시작하면 그리기 시작해 서서히 나타난다.
 */
@Composable
public fun GlassMiniPlayerFullScreen(
    state: GlassMiniPlayerState,
    modifier: Modifier = Modifier,
    style: GlassStyle = LocalGlassStyle.current,
    artwork: (@Composable () -> Unit)? = null,
    content: @Composable (progress: () -> Float) -> Unit,
) {
    MiniPlayerHost(state, modifier, style, artwork, PlayerLayout(DEFAULT_BAR, 0.dp, detached = true), null, content)
}

/** 막대 높이와 아래 여백. [detached]면 막대를 [GlassMiniPlayerBar]가 그리고, 이 값은 막대가 없을 때 대신 쓰는 자리에만 쓴다. */
private class PlayerLayout(val barHeight: Dp, val barBottomPadding: Dp, val detached: Boolean)

@Composable
private fun MiniPlayerHost(
    state: GlassMiniPlayerState,
    modifier: Modifier,
    style: GlassStyle,
    artwork: (@Composable () -> Unit)?,
    layout: PlayerLayout,
    collapsed: (@Composable (() -> Float) -> Unit)?,
    expanded: @Composable (() -> Float) -> Unit,
) {
    val scope = rememberCoroutineScope()
    GlassOverlay(visible = true, kind = OverlayKind.InPlace) { _ ->
        BackHandler(enabled = state.isExpanded) { scope.launch { state.collapse() } }
        // 진행도는 매 프레임 바뀌므로 읽는 곳을 작은 컴포저블로 나눠 그 범위만 다시 구성되게 한다.
        PlayerPushBack(state)
        PlayerBarIcons(state, style)
        val origin = remember { mutableStateOf(Offset.Zero) }
        val window = rememberGlassWindowLayout()
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .onGloballyPositioned { origin.value = it.positionInWindow() },
        ) {
            val bar = layout.barHeight * LocalDensity.current.fontScale.coerceIn(1f, MAX_BAR_GROW)
            // 접힌 선은 창 좌표라 이 오버레이 기준으로 옮긴다. 위치가 바뀔 때만 다시 구성된다.
            val fold = window.hinge?.let { PlayerFold(it.translate(-origin.value), window.posture) }
            val own = playerGeometry(constraints, bar, layout.barBottomPadding, artwork != null, fold)
            val geometry = if (layout.detached) remember(own, state, rtl) { own.attachedTo(state, rtl) { origin.value } } else own
            PlayerScrim(state, scope)
            val progress = remember(state) { { state.progress } }
            val slots = remember(scope, collapsed, expanded, artwork != null, progress) {
                PlayerSlots(scope, collapsed, expanded, artwork != null, progress)
            }
            if (layout.detached) {
                DetachedPlayer(state, geometry, style.forPopup(), slots, artwork)
            } else {
                PlayerFrame(state, geometry, style.forPopup(), slots)
                if (artwork != null) Artwork(state, geometry, artwork)
            }
        }
    }
}

/** 절반 넘게 펼쳐진 동안 시스템 바 아이콘을 펼친 화면의 글자 색에 맞춘다. 진행도는 참/거짓으로 줄여 읽는다. */
@Composable
private fun PlayerBarIcons(state: GlassMiniPlayerState, style: GlassStyle) {
    val expanded by remember(state) { derivedStateOf { state.progress > HALF } }
    SystemBarIcons(light = if (expanded) style.forPopup().onGlass.luminance() > HALF else null)
}

/** 접혀 쉬는 동안에는 바깥 막대가 보이므로 아무것도 그리지 않는다. */
@Composable
private fun DetachedPlayer(
    state: GlassMiniPlayerState,
    geometry: PlayerGeometry,
    style: GlassStyle,
    slots: PlayerSlots,
    artwork: (@Composable () -> Unit)?,
) {
    if (state.isExpanded || state.progress > 0f) {
        PlayerFrame(state, geometry, style, slots)
        if (artwork != null) Artwork(state, geometry, artwork)
    }
}

@Composable
private fun PlayerPushBack(state: GlassMiniPlayerState) {
    ReportPushBack(state.progress.coerceIn(0f, 1f))
}

@Composable
private fun BoxScope.PlayerScrim(state: GlassMiniPlayerState, scope: CoroutineScope) {
    val level = state.progress.coerceIn(0f, 1f)
    if (level > 0f) Scrim(level) { scope.launch { state.collapse() } }
}

@Stable
private class PlayerSlots(
    // 막대와 펼친 화면은 진행도에 따라 컴포지션에서 빠지므로, 그 안에서 만든 스코프로 애니메이션을 돌리면 도중에 취소된다.
    val scope: CoroutineScope,
    val collapsed: (@Composable (() -> Float) -> Unit)?,
    val expanded: @Composable (() -> Float) -> Unit,
    val hasArtwork: Boolean,
    val progress: () -> Float,
)

/**
 * 막대와 전체 화면, 두 아트 위치(px, 오버레이 기준). 막대 자리는 바깥 막대를 따라 움직일 수 있어 배치, 그리기 단계에서 읽는다.
 * [artRelative]면 아트의 x가 LTR 기준이라 RTL에서 배치할 때 뒤집는다. 바깥 막대 자리는 이미 창 좌표라 뒤집지 않는다.
 */
@Stable
private class PlayerGeometry(
    val screen: IntSize,
    val full: Rect,
    private val placed: FullPlayerLayout,
    val artRelative: Boolean,
    private val barOf: () -> Rect,
    private val barArtOf: () -> Rect,
) {
    val fullArt: Rect get() = placed.art
    val content: Rect get() = placed.content
    val bar: Rect get() = barOf()
    val barArt: Rect get() = barArtOf()
    val travel: Float get() = (bar.top - full.top).coerceAtLeast(1f)

    /**
     * 막대 자리를 [state]에 기록된 바깥 막대로 바꾼 사본. 바깥 막대가 놓인 적이 없으면 이 기본 자리를 쓴다.
     * 바깥 막대는 창 좌표라 뒤집지 않고 배치하므로, LTR 기준인 펼친 아트 자리는 [rtl]이면 여기서 미리 뒤집는다.
     */
    fun attachedTo(state: GlassMiniPlayerState, rtl: Boolean, origin: () -> Offset): PlayerGeometry {
        val ownBar = bar
        val ownArt = barArt
        val art = if (rtl &&
            !fullArt.isEmpty
        ) {
            Rect(screen.width - fullArt.right, fullArt.top, screen.width - fullArt.left, fullArt.bottom)
        } else {
            fullArt
        }
        return PlayerGeometry(
            screen,
            full,
            FullPlayerLayout(art, content),
            artRelative = false,
            barOf = { state.barBounds.takeUnless { it.isEmpty }?.translate(-origin()) ?: ownBar },
            barArtOf = {
                val barArt = state.barArtBounds
                when {
                    art.isEmpty -> Rect.Zero

                    !barArt.isEmpty -> barArt.translate(-origin())

                    state.barBounds.isEmpty -> ownArt

                    // 막대에 아트가 없으면 막대 시작 쪽 가운데의 한 점에서 커진다.
                    else -> state.barBounds.translate(-origin()).let { Rect(it.centerLeft, it.centerLeft) }
                }
            },
        )
    }
}

@Composable
private fun playerGeometry(
    constraints: Constraints,
    barHeight: Dp,
    barBottomPadding: Dp,
    hasArtwork: Boolean,
    fold: PlayerFold?,
): PlayerGeometry {
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    return with(density) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val barBottom = h - navBottom - barBottomPadding.toPx() - BAR_GAP.toPx()
        // 넓은 화면에서는 탭 바처럼 최대 폭으로 묶어 가운데에 둔다.
        val side = maxOf(BAR_MARGIN.toPx(), (w - BAR_MAX_WIDTH.toPx()) / 2f)
        val bar = Rect(side, barBottom - barHeight.toPx(), w - side, barBottom)
        val full = Rect(0f, statusTop + FULL_TOP_GAP.toPx(), w, h)
        val inset = ART_INSET.toPx()
        val small = (barHeight.toPx() - inset * 2).coerceAtLeast(0f)
        val barArt = Rect(bar.left + inset, bar.top + inset, bar.left + inset + small, bar.top + inset + small)
        val metrics = PlayerMetrics(
            small = small,
            handle = HANDLE_AREA.toPx(),
            gap = CONTENT_GAP.toPx(),
            side = ART_SIDE.toPx(),
            artMax = ART_MAX.toPx(),
            contentMax = CONTENT_MAX.toPx(),
            wideMin = GlassWindowDefaults.MediumMinWidth.toPx(),
            minPane = MIN_FOLD_PANE.toPx(),
            pairGap = PAIR_GAP.toPx(),
        )
        val placed = fullPlayerLayout(w, full, hasArtwork, fold, metrics)
        val art = if (hasArtwork) barArt else Rect.Zero
        PlayerGeometry(
            IntSize(constraints.maxWidth, constraints.maxHeight),
            full,
            placed,
            artRelative = true,
            barOf = { bar },
            barArtOf = { art },
        )
    }
}

@Composable
private fun PlayerFrame(state: GlassMiniPlayerState, geometry: PlayerGeometry, style: GlassStyle, slots: PlayerSlots) {
    val level = state.progress.coerceIn(0f, 1f)
    val barCorner = with(LocalDensity.current) { (geometry.bar.height / 2f).toDp() }
    val shape = RoundedCornerShape(lerp(barCorner, FULL_CORNER, level))
    // 이 함수는 모양 때문에 매 프레임 다시 구성되지만, 아래 슬롯은 인자가 그대로라 건너뛴다.
    val showCollapsed = level < COLLAPSED_GONE && slots.collapsed != null
    val showExpanded = level > 0f
    Box(
        Modifier
            .placeAt(geometry.screen, relative = false) { lerp(geometry.bar, geometry.full, state.progress) }
            // 화면 가장자리에 닿으면 블러가 화면 밖의 빈 픽셀을 끌어와 테두리가 어두워진다. 펼칠수록 바탕색을 깔아 가린다.
            .background(style.fallback.copy(alpha = style.fallback.alpha * level), shape)
            .glass(style, shape)
            .clip(shape)
            .playerDrag(state) { geometry.travel },
    ) {
        CompositionLocalProvider(LocalGlassContentColor provides style.onGlass, LocalGlassLayered provides true) {
            if (showCollapsed) CollapsedBar(state, geometry, slots)
            if (showExpanded) ExpandedPanel(state, geometry, slots)
        }
    }
}

@Composable
private fun CollapsedBar(state: GlassMiniPlayerState, geometry: PlayerGeometry, slots: PlayerSlots) {
    val scope = slots.scope
    val density = LocalDensity.current
    val bar = geometry.bar
    val start = if (slots.hasArtwork) with(density) { (geometry.barArt.right - bar.left).toDp() } + ART_INSET else BAR_PADDING
    Box(
        Modifier
            .fillMaxWidth()
            .height(with(density) { bar.height.toDp() })
            .graphicsLayer { alpha = (1f - state.progress / COLLAPSED_GONE).coerceIn(0f, 1f) }
            // 바깥 틀이 막대 모양으로 잘라 주므로 눌림 표시는 사각형으로 둔다.
            .glassClickable(onClickLabel = stringResource(R.string.kinetica_ui_player_expand)) {
                scope.launch { state.expand() }
            }
            .padding(start = start, end = BAR_PADDING),
        contentAlignment = Alignment.CenterStart,
    ) {
        slots.collapsed?.invoke(slots.progress)
    }
}

@Composable
private fun ExpandedPanel(state: GlassMiniPlayerState, geometry: PlayerGeometry, slots: PlayerSlots) {
    val scope = slots.scope
    val density = LocalDensity.current
    val title = stringResource(R.string.kinetica_ui_player_now_playing)
    val collapseLabel = stringResource(R.string.kinetica_ui_player_collapse)
    val collapse = {
        scope.launch { state.collapse() }
        true
    }
    val content = with(density) {
        val area = geometry.content
        PaddingValues(
            start = area.left.toDp(),
            top = area.top.toDp(),
            end = (geometry.full.width - area.right).coerceAtLeast(0f).toDp(),
            bottom = (geometry.full.height - area.bottom).coerceAtLeast(0f).toDp(),
        )
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
                .glassClickable(CircleShape, onClickLabel = collapseLabel) { collapse() }
                .padding(horizontal = HANDLE_TOUCH, vertical = HANDLE_PAD)
                .size(HANDLE_WIDTH, HANDLE_HEIGHT)
                .background(LocalGlassContentColor.current.copy(alpha = HANDLE_ALPHA), RoundedCornerShape(HANDLE_HEIGHT)),
        )
        Box(Modifier.fillMaxSize().padding(content)) { slots.expanded(slots.progress) }
    }
}

@Composable
private fun Artwork(state: GlassMiniPlayerState, geometry: PlayerGeometry, artwork: @Composable () -> Unit) {
    val level = state.progress.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(lerp(ART_CORNER_SMALL, ART_CORNER_BIG, level))
    Box(
        Modifier
            .placeAt(geometry.screen, relative = geometry.artRelative) { lerp(geometry.barArt, geometry.fullArt, state.progress) }
            .shadow(lerp(ART_SHADOW_SMALL, ART_SHADOW_BIG, level), shape)
            .clip(shape),
    ) { artwork() }
}

/** [travel]은 막대에서 전체 화면까지 움직이는 거리(px)다. 끄는 동안 막대가 움직일 수 있어 끌 때마다 읽는다. */
@Composable
private fun Modifier.playerDrag(state: GlassMiniPlayerState, travel: () -> Float): Modifier {
    val scope = rememberCoroutineScope()
    return draggable(
        rememberDraggableState { delta -> scope.launch { state.dragBy(-delta / travel().coerceAtLeast(1f)) } },
        Orientation.Vertical,
        onDragStopped = { velocity -> state.release(-velocity / travel().coerceAtLeast(1f)) },
    )
}

private fun LayoutCoordinates.windowRect(): Rect = Rect(positionInWindow(), size.toSize())

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
private val BAR_MAX_WIDTH = 560.dp
private val DEFAULT_BAR = 64.dp
private val DETACHED_ART_INSET = 8.dp
private val DETACHED_CONTENT_GAP = 12.dp
private val BAR_GAP = 8.dp
private val BAR_PADDING = 16.dp
private val FULL_TOP_GAP = 12.dp
private val FULL_CORNER = 32.dp
private val ART_INSET = 10.dp
private val ART_SIDE = 32.dp
private val ART_MAX = 420.dp
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
private val PAIR_GAP = 48.dp
private val CONTENT_MAX = 560.dp
private val MIN_FOLD_PANE = 200.dp
private val PANEL_RISE = 40.dp
