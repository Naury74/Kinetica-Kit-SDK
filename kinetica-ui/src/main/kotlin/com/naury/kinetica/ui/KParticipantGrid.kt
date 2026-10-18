package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** [KParticipantGrid]가 타일에 맡긴 역할. 타일은 역할에 따라 모서리와 누를 때 동작을 바꾼다. */
public enum class KTileRole {
    /** 모두 같은 크기인 격자 칸 */
    Grid,

    /** 크게 보는 사람(고정, 발표자, 화면 공유) */
    Focus,

    /** 크게 보는 사람 옆 한 줄 띠의 작은 칸 */
    Strip,

    /** 1:1 통화에서 화면 전체를 채운 상대 */
    Full,

    /** 1:1 통화에서 모서리에 떠 있는 내 화면. 끌어서 다른 모서리로 옮긴다. */
    Floating,
}

/** 화면 모서리. 시작과 끝은 RTL에서 바뀐다. */
public enum class KCorner {
    TopStart,
    TopEnd,
    BottomStart,
    BottomEnd,
}

/** 타일 하나의 움직임. 두 층([KCallLayer.Video], [KCallLayer.Overlay])이 같은 값을 읽어 같은 자리에 그린다. */
@Stable
internal class TileMotion(rect: Rect) {
    val rect = Animatable(rect, Rect.VectorConverter)
    val appear = Animatable(0f)
    val press = TilePress()
}

/**
 * [KParticipantGrid]의 상태. 1:1 통화에서 내 화면이 붙은 모서리, 넘긴 쪽, 띠 스크롤 위치와 타일마다의 움직임을 들고 있다.
 * 같은 격자를 [KCallLayer.Video]와 [KCallLayer.Overlay] 두 층으로 그릴 때는 이 상태 하나를 함께 넘겨야 두 층이 같은 자리에 그려진다.
 */
@Stable
public class KParticipantGridState internal constructor(corner: KCorner) {
    /** 1:1 통화에서 내 화면이 붙은 모서리. 바꾸면 그 모서리로 미끄러져 간다. */
    public var selfCorner: KCorner by mutableStateOf(corner)

    /** 지금 보이는 쪽(0부터). 9명이 넘는 갤러리에서 쪽을 넘길 때 바뀐다. */
    public val page: Int get() = if (pageWidth <= 0f) 0 else (scroll.value / pageWidth).roundToInt()

    internal val scroll: Animatable<Float, AnimationVector1D> = Animatable(0f)
    internal var pageWidth: Float = 0f
    internal var drag: Offset by mutableStateOf(Offset.Zero)
    internal val motions: MutableMap<Any, TileMotion> = HashMap()

    internal fun motion(key: Any, rect: Rect): TileMotion = motions.getOrPut(key) { TileMotion(rect) }

    internal companion object {
        val Saver: Saver<KParticipantGridState, Int> = Saver({ it.selfCorner.ordinal }, { KParticipantGridState(KCorner.entries[it]) })
    }
}

/** [KParticipantGridState]를 만든다. 내 화면 모서리는 화면을 돌리거나 접었다 펴도 남는다. */
@Composable
public fun rememberKParticipantGridState(initialCorner: KCorner = KCorner.BottomEnd): KParticipantGridState =
    rememberSaveable(saver = KParticipantGridState.Saver) { KParticipantGridState(initialCorner) }

/**
 * 말하는 사람이 바뀌어도 [delayMillis] 동안 그대로 말하면 그때 바꾼 값을 돌려준다. 발표자 보기에서 짧은 맞장구에 큰 화면이 흔들리지 않게 한다.
 * 처음 말한 사람은 바로 정한다. [speakingKey]가 null이 되어도 마지막 사람을 남긴다.
 */
@Composable
public fun <K : Any> rememberKStableSpeaker(speakingKey: K?, delayMillis: Long = 1_500L): K? {
    var stable by remember { mutableStateOf<K?>(null) }
    LaunchedEffect(speakingKey) {
        if (speakingKey == null || speakingKey == stable) return@LaunchedEffect
        if (stable != null) delay(delayMillis)
        stable = speakingKey
    }
    return stable
}

/**
 * 참가자 수와 화면 비율에 맞춰 타일을 놓는 영상 무대. 참가자 데이터는 호스트 것을 그대로 받고, 타일은 [tile]이 그린다.
 *
 * - 1명은 화면 가득, 여럿은 영상이 가장 크게 보이는 열 수로 격자를 짠다. 세로 화면은 3:4, 가로 화면은 16:9 영상을 칸에 맞춰 넣었을 때의
 *   넓이를 비교하고, 차이가 25% 안쪽이면 행과 열이 고른 쪽을 고른다. 세로 폰은 2명 위아래, 3~4명 2×2, 5~6명 2×3, 가로 화면은 2명 좌우,
 *   6명 3×2다. 다르게 짜려면 [columns]를 넘긴다.
 *   마지막 줄이 덜 차면 가운데로 모은다. [maxPerPage]명이 넘으면 쪽을 나누고 옆으로 넘긴다. 아래 점이 쪽 위치를 보여 준다.
 * - [selfKey]와 다른 한 명뿐이면 상대가 화면 전체를([KTileRole.Full]), 나는 모서리의 작은 창([KTileRole.Floating])이 된다.
 *   작은 창은 끌어서 놓으면 가까운 모서리에 붙는다.
 * - [focusKey]가 있으면 그 사람이 크게, 나머지는 한 줄 띠가 된다. 세로 화면은 아래 띠를 가로로, 가로 화면은 끝쪽 띠를 세로로 넘긴다.
 *   "+N" 칸 없이 모두 넘겨 볼 수 있고, 보이는 칸 근처의 타일만 그려 영상 렌더 부하를 보이는 만큼으로 줄인다.
 *
 * 배치가 바뀌면 같은 [key]의 타일은 다시 만들어지지 않고(영상이 끊기지 않고) 새 자리로 스프링처럼 옮겨 간다. '애니메이션 삭제'면 바로 옮긴다.
 *
 * 글래스 바 아래에 둘 때는 같은 [state]로 `background`에 [KCallLayer.Video], `content`에 [KCallLayer.Overlay]를 하나씩 그리고
 * [tile]에서 [KParticipantTile]에 같은 층을 넘긴다. 그래야 글로우 테두리가 돌 때 바 블러를 다시 하지 않는다.
 *
 * ```kotlin
 * KParticipantGrid(people, key = { it.id }, focusKey = pinnedId, selfKey = me.id) { p, role ->
 *     KParticipantTile(p.name, video = p.video, speaking = p.speaking, onClick = { pinnedId = p.id })
 * }
 * ```
 *
 * @param contentPadding 무대 둘레 여백. 상단 바와 조작 막대 높이, 시스템 바 인셋을 넣는다. 1:1의 상대 화면은 이 여백을 무시하고 화면 끝까지 채운다.
 * @param spacing 타일 사이 간격
 * @param maxPerPage 갤러리 한 쪽의 최대 인원. 1 미만이면 [IllegalArgumentException]을 던진다.
 * @param columns 격자 열 수를 직접 정한다. 한 쪽 인원과 무대 폭, 높이(여백을 뺀 값)를 받는다. 결과는 1..인원으로 맞춘다.
 *   `null`이면 위 규칙을 쓴다. 고정 보기와 1:1 배치에는 쓰지 않는다.
 * @param layer 그릴 층. [KCallLayer.Overlay]는 같은 [state]의 [KCallLayer.Video]와 함께 써야 타일이 움직인다.
 * @param tile 타일 하나. 받은 상자를 꽉 채워 그린다.
 */
@Composable
public fun <T> KParticipantGrid(
    participants: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    state: KParticipantGridState = rememberKParticipantGridState(),
    focusKey: Any? = null,
    selfKey: Any? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    spacing: Dp = 8.dp,
    maxPerPage: Int = 9,
    columns: ((count: Int, width: Dp, height: Dp) -> Int)? = null,
    layer: KCallLayer = KCallLayer.All,
    tile: @Composable BoxScope.(participant: T, role: KTileRole) -> Unit,
) {
    require(maxPerPage >= 1) { "maxPerPage는 1 이상이어야 합니다." }
    val direction = LocalLayoutDirection.current
    val density = LocalDensity.current
    val byKey = participants.associateBy(key)
    BoxWithConstraints(modifier) {
        val layout = GridLayout(
            participants.map(key),
            focusKey,
            selfKey,
            maxWidth >= WIDE_WINDOW,
            spacing,
            state.selfCorner,
            maxPerPage,
            columns,
        )
        val plan = with(density) { gridPlan(layout, constraints, contentPadding, direction) }
        state.pageWidth = plan.pageWidth
        val driver = layer != KCallLayer.Overlay
        val touch = layer != KCallLayer.Video
        ScrollBounds(state, plan, driver)
        // 스크롤 값을 컴포지션에서 바로 읽으면 넘기는 동안 매 프레임 다시 구성하므로, 보이는 칸이 바뀔 때만 다시 구성한다.
        val visible by remember(plan, state) {
            derivedStateOf { plan.spots.filter { plan.isNearlyVisible(it, state.scroll.value) }.map { it.key }.toSet() }
        }
        // 크기를 주지 않으면 상자가 가장 큰 자식(고정 시 큰 화면)만 해져서, 그 밖에 놓인 띠와 다음 쪽 위 끌기를 받지 못한다.
        val tiles = rememberMovableTiles(state)
        SideEffect { tiles.retain(byKey.keys) }
        val placeTiles = @Composable { strip: Boolean ->
            plan.spots.forEachIndexed { index, spot ->
                val item = byKey[spot.key] ?: return@forEachIndexed
                if ((spot.role == KTileRole.Strip) != strip || spot.key !in visible) return@forEachIndexed
                key(spot.key) { tiles.of(spot.key)(spot, plan, GridTileFlags(driver, touch, index)) { tile(item, spot.role) } }
            }
        }
        // 띠 칸은 띠만 담은 상자에 둔다. 큰 화면과 같은 스크롤 노드에 있으면 TalkBack이 띠 끝에서 띠를 넘기지 않고 큰 화면으로 건너뛴다.
        // 역할이 바뀌면 타일이 두 상자를 오가는데, movableContentOf로 옮기므로 다시 만들어지지 않는다.
        // 띠 상자는 띠 자리만 차지해야 TalkBack이 상단 바, 큰 화면, 띠, 조작 막대처럼 화면 위치 순서로 읽는다.
        Box(Modifier.fillMaxSize().then(if (touch) Modifier.gridScroll(state, plan) else Modifier)) {
            placeTiles(false)
            plan.strip?.let { strip ->
                Box(
                    Modifier
                        .zIndex(KTileRole.Strip.z)
                        .stripBox(strip.area)
                        .semantics { isTraversalGroup = true }
                        .then(if (touch) Modifier.stripScrollSemantics(state, plan) else Modifier),
                ) { placeTiles(true) }
            }
        }
        if (touch && plan.pages > 1) PageDots(state, plan)
    }
}

/** 배치에 쓸 참가자 정보와 설정 */
@Suppress("LongParameterList") // 공개 함수의 파라미터를 배치 계산에 한 덩어리로 넘기려고 묶는다.
private class GridLayout(
    val keys: List<Any>,
    val focus: Any?,
    val self: Any?,
    val wide: Boolean,
    val spacing: Dp,
    val corner: KCorner,
    val pageSize: Int,
    val columns: ((Int, Dp, Dp) -> Int)?,
)

/** 이 층이 움직임을 이끄는지([driver]), 터치를 받는지([touch]), TalkBack이 몇 번째로 읽는지([order]) */
private class GridTileFlags(val driver: Boolean, val touch: Boolean, val order: Int)

/** 띠 자리([area], 논리 좌표)만 차지하는 상자. 칸은 이 상자 밖으로 넘쳐 그려질 수 있다. */
private fun Modifier.stripBox(area: Rect): Modifier = this
    .offset { IntOffset(area.left.roundToInt(), area.top.roundToInt()) }
    .layout { measurable, _ ->
        val w = area.width.roundToInt()
        val h = area.height.roundToInt()
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(w, h) { placeable.place(0, 0) }
    }

/** 참가자마다 하나씩 두는 옮길 수 있는 타일. 띠 상자와 바깥 상자를 오가도 상태와 영상이 그대로 남는다. */
private class MovableTiles(private val state: KParticipantGridState) {
    private val tiles = HashMap<Any, @Composable (Spot, GridPlan, GridTileFlags, @Composable BoxScope.() -> Unit) -> Unit>()

    fun of(key: Any): @Composable (Spot, GridPlan, GridTileFlags, @Composable BoxScope.() -> Unit) -> Unit = tiles.getOrPut(key) {
        movableContentOf { spot: Spot, plan: GridPlan, flags: GridTileFlags, content: @Composable BoxScope.() -> Unit ->
            GridTile(spot, state, plan, flags, content)
        }
    }

    fun retain(keys: Set<Any>) {
        tiles.keys.retainAll(keys)
    }
}

@Composable
private fun rememberMovableTiles(state: KParticipantGridState): MovableTiles = remember(state) { MovableTiles(state) }

/** [constraints]와 여백으로 배치를 계산한다. */
private fun Density.gridPlan(
    layout: GridLayout,
    constraints: Constraints,
    padding: PaddingValues,
    direction: androidx.compose.ui.unit.LayoutDirection,
): GridPlan {
    val w = constraints.maxWidth.toFloat()
    val h = constraints.maxHeight.toFloat()
    val area = Rect(
        padding.calculateStartPadding(direction).toPx(),
        padding.calculateTopPadding().toPx(),
        w - padding.calculateEndPadding(direction).toPx(),
        h - padding.calculateBottomPadding().toPx(),
    )
    return planGrid(
        GridInput(
            keys = layout.keys,
            focus = layout.focus,
            self = layout.self,
            area = area,
            full = Rect(0f, 0f, w, h),
            gap = layout.spacing.toPx(),
            strip = minOf(STRIP_MAX.toPx(), (if (w > h) area.width else area.height) * STRIP_FRACTION),
            floatingWidth = (if (layout.wide) FLOATING_WIDE else FLOATING).toPx(),
            margin = FLOATING_MARGIN.toPx(),
            corner = layout.corner,
            pageSize = layout.pageSize,
            pageDots = PAGE_DOTS.toPx(),
            columns = layout.columns?.let { pick -> { count, width, height -> pick(count, width.toDp(), height.toDp()) } },
        ),
    )
}

/** 타일 하나의 상자. 자리와 크기는 [TileMotion]을 따라 배치와 그리기 단계에서 바뀐다. */
@Composable
private fun GridTile(
    spot: Spot,
    state: KParticipantGridState,
    plan: GridPlan,
    flags: GridTileFlags,
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = state.motion(spot.key, spot.rect)
    if (flags.driver) TileDriver(motion, spot.rect)
    val drag = if (flags.touch && spot.role == KTileRole.Floating) Modifier.floatingDrag(state, motion, plan.full) else Modifier
    val clip = if (spot.role == KTileRole.Strip) Modifier.clipToStrip(motion, state, plan) else Modifier
    // 띠 칸은 띠 상자 안에 놓이므로 띠 자리만큼 당겨 놓는다. 넘겨서 화면 밖에 걸친 칸은 자리가 엉뚱하게 잡혀
    // TalkBack 읽는 순서가 뒤섞이므로, 띠 안에서는 배치 순서로 못 박는다.
    val strip = spot.role == KTileRole.Strip && plan.strip != null
    val origin = if (strip) plan.strip!!.area.topLeft else Offset.Zero
    val order = if (strip) Modifier.semantics { traversalIndex = flags.order.toFloat() } else Modifier
    Box(Modifier.zIndex(spot.role.z).placeTile(motion, state, plan, spot.role, origin).then(clip).then(drag).then(order)) {
        CompositionLocalProvider(LocalTilePress provides motion.press) { content() }
    }
}

/** 띠와 쪽 넘김의 스크롤 범위를 배치에 맞춘다. 배치가 바뀌어 범위를 넘으면 끝으로 당긴다. */
@Composable
private fun ScrollBounds(state: KParticipantGridState, plan: GridPlan, driver: Boolean) {
    if (!driver) return
    val max = plan.maxScroll
    LaunchedEffect(max, plan.strip?.horizontal) {
        state.scroll.updateBounds(0f, max)
        if (state.scroll.value > max) state.scroll.snapTo(max)
    }
}

/** 타일 자리로 스프링처럼 옮긴다. 처음 나타날 때는 살짝 커지며 서서히 보인다. */
@Composable
private fun TileDriver(motion: TileMotion, target: Rect) {
    val still = reducedMotion()
    LaunchedEffect(target, still) {
        if (still) {
            motion.rect.snapTo(
                target,
            )
        } else {
            motion.rect.animateTo(target, spring(dampingRatio = MOVE_DAMPING, stiffness = Spring.StiffnessMediumLow))
        }
    }
    LaunchedEffect(still) { if (still) motion.appear.snapTo(1f) else motion.appear.animateTo(1f, tween(APPEAR_MS)) }
}

/** [motion]의 자리와 크기로 놓는다. 값은 배치와 그리기 단계에서 읽어 움직이는 동안 다시 구성하지 않는다. */
private fun Modifier.placeTile(
    motion: TileMotion,
    state: KParticipantGridState,
    plan: GridPlan,
    role: KTileRole,
    origin: Offset,
): Modifier = this
    .offset {
        val r = motion.rect.value
        val shift = plan.shift(role, state.scroll.value)
        val drag = if (role == KTileRole.Floating) state.drag else Offset.Zero
        IntOffset((r.left + shift.x + drag.x - origin.x).roundToInt(), (r.top + shift.y + drag.y - origin.y).roundToInt())
    }
    .layout { measurable, _ ->
        val r = motion.rect.value
        val w = r.width.roundToInt().coerceAtLeast(1)
        val h = r.height.roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(w, h) { placeable.place(0, 0) }
    }
    .graphicsLayer {
        val a = motion.appear.value
        alpha = a
        val s = APPEAR_SCALE + (1f - APPEAR_SCALE) * a
        scaleX = s
        scaleY = s
    }

/**
 * 띠 칸을 띠 영역 안에서만 보이게 자른다. 넘기다 띠 밖으로 나간 칸이 큰 화면이나 조작 막대 위에 겹쳐 보이지 않게 한다.
 * 가로지르는 방향으로는 글로우 테두리가 잘리지 않게 조금 넓힌다. 자리를 정하는 같은 부모를 쓰므로 역할이 바뀌어도 타일이 다시 만들어지지 않는다.
 */
private fun Modifier.clipToStrip(motion: TileMotion, state: KParticipantGridState, plan: GridPlan): Modifier = drawWithContent {
    val strip = plan.strip ?: return@drawWithContent drawContent()
    val r = motion.rect.value
    val shift = plan.shift(KTileRole.Strip, state.scroll.value)
    val left = r.left + shift.x
    val top = r.top + shift.y
    val spare = STRIP_GLOW.toPx()
    val area = if (strip.horizontal) {
        Rect(strip.area.left, strip.area.top - spare, strip.area.right, strip.area.bottom + spare)
    } else {
        Rect(strip.area.left - spare, strip.area.top, strip.area.right + spare, strip.area.bottom)
    }
    // 논리 좌표(시작 쪽 x = 0)를 이 칸 안의 물리 좌표로 바꾼다. RTL이면 좌우가 뒤집힌다.
    val (x0, x1) = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) {
        (left + size.width - area.right) to (left + size.width - area.left)
    } else {
        (area.left - left) to (area.right - left)
    }
    clipRect(x0, area.top - top, x1, area.bottom - top) { this@drawWithContent.drawContent() }
}

private val KTileRole.z: Float
    get() = when (this) {
        KTileRole.Grid, KTileRole.Focus, KTileRole.Full -> 0f
        KTileRole.Strip -> 1f
        KTileRole.Floating -> 2f
    }

/** 격자 안 타일이 누름 배율을 두 층에서 함께 쓰도록 넘긴다. */
internal val LocalTilePress = androidx.compose.runtime.staticCompositionLocalOf<TilePress?> { null }

/**
 * 배치 계산에 쓰는 값(px, 시작 쪽이 x = 0인 논리 좌표).
 *
 * @property area 여백을 뺀 무대
 * @property full 화면 전체
 * @property strip 띠 두께(세로 화면은 높이, 가로 화면은 너비)
 * @property floatingWidth 내 작은 창 너비
 * @property pageDots 쪽이 여럿일 때 아래에 비워 둘 점 줄 높이
 */
@Suppress("LongParameterList") // 배치 계산에 드는 값을 한 번에 넘기려고 묶는다.
internal class GridInput(
    val keys: List<Any>,
    val focus: Any?,
    val self: Any?,
    val area: Rect,
    val full: Rect,
    val gap: Float,
    val strip: Float,
    val floatingWidth: Float,
    val margin: Float,
    val corner: KCorner,
    val pageSize: Int,
    val pageDots: Float = 0f,
    val columns: ((Int, Float, Float) -> Int)? = null,
)

/** 타일 하나의 자리 */
internal class Spot(val key: Any, val rect: Rect, val role: KTileRole)

/** 한 줄 띠. [length]는 모든 칸을 늘어놓은 길이다. */
internal class StripArea(val area: Rect, val length: Float, val horizontal: Boolean) {
    val maxScroll: Float get() = maxOf(0f, length - if (horizontal) area.width else area.height)
}

/** 배치 결과. [pages]가 1보다 크면 쪽마다 [pageWidth]만큼 옆에 놓인다. */
internal class GridPlan(val spots: List<Spot>, val full: Rect, val area: Rect, val strip: StripArea? = null, val pages: Int = 1) {
    val pageWidth: Float get() = full.width
    val maxScroll: Float get() = strip?.maxScroll ?: ((pages - 1) * pageWidth)

    /** 스크롤 [scroll]만큼 옮길 양 */
    fun shift(role: KTileRole, scroll: Float): Offset = when {
        strip != null && role == KTileRole.Strip -> if (strip.horizontal) Offset(-scroll, 0f) else Offset(0f, -scroll)
        pages > 1 && role == KTileRole.Grid -> Offset(-scroll, 0f)
        else -> Offset.Zero
    }

    /** 옮긴 뒤 보이는 칸과 겹치거나 한 칸 안쪽으로 가까운지 */
    fun isNearlyVisible(spot: Spot, scroll: Float): Boolean {
        val moved = spot.rect.translate(shift(spot.role, scroll))
        val box = if (spot.role == KTileRole.Strip) strip?.area ?: full else full
        return moved.right > box.left - moved.width && moved.left < box.right + moved.width &&
            moved.bottom > box.top - moved.height && moved.top < box.bottom + moved.height
    }
}

/** 배치를 정한다. 규칙은 [KParticipantGrid] 설명과 같다. */
internal fun planGrid(input: GridInput): GridPlan {
    val keys = input.keys
    val area = input.area
    val focus = input.focus?.takeIf { it in keys }
    return when {
        keys.isEmpty() -> GridPlan(emptyList(), input.full, area)
        focus != null -> focusPlan(input, focus)
        keys.size == 2 && input.self != null && input.self in keys -> oneOnOnePlan(input)
        keys.size <= input.pageSize -> GridPlan(gridSpots(keys, area, input, 0f), input.full, area)
        else -> pagedPlan(input)
    }
}

/** [GridInput.pageSize]씩 쪽을 나눠 화면 너비만큼씩 옆에 놓는다. 아래에는 쪽 점 자리를 비운다. */
private fun pagedPlan(input: GridInput): GridPlan {
    val area = input.area
    val paged = Rect(area.left, area.top, area.right, area.bottom - input.pageDots)
    val pages = input.keys.chunked(input.pageSize)
    val spots = pages.flatMapIndexed { page, chunk -> gridSpots(chunk, paged, input, page * input.full.width) }
    return GridPlan(spots, input.full, paged, pages = pages.size)
}

/**
 * [count]명을 [width]×[height]에 놓을 때 영상이 가장 크게 보이는 열 수. 세로 무대는 3:4, 가로 무대는 16:9, 정사각형에 가까우면 1:1 영상을 칸에 맞춰 넣은 넓이를 비교한다.
 * 정사각형으로 비교하면 세로 폰 4명이 납작한 1열 4단이 되어 세로 영상이 거의 잘린다.
 */
internal fun gridColumns(count: Int, width: Float, height: Float, gap: Float = 0f): Int {
    if (count <= 1) return 1
    // 정사각형에 가까운 무대(태블릿 가로 분할 등)는 어느 쪽 영상이 올지 모르므로 정사각형으로 비교한다.
    val aspect = when {
        height > width * SQUARE_BAND -> PORTRAIT_TILE
        width > height * SQUARE_BAND -> LANDSCAPE_TILE
        else -> 1f
    }
    val rowsOf = { columns: Int -> ceil(count / columns.toFloat()).toInt() }
    val area = { columns: Int ->
        val cellW = (width - gap * (columns - 1)) / columns
        val cellH = (height - gap * (rowsOf(columns) - 1)) / rowsOf(columns)
        val fitW = minOf(cellW, cellH * aspect)
        fitW * (fitW / aspect)
    }
    val best = (1..count).maxOf(area)
    // 넓이 차가 크지 않으면(긴 폰의 3명은 1열 3단이 2×2보다 약 20% 크다) 고른 격자가 덜 납작하고 보기 좋다. 그다음은 빈 칸이 적은 쪽, 열이 많은 쪽.
    return (1..count).filter { area(it) >= best * TIE_RATIO }
        .minWith(compareBy({ abs(rowsOf(it) - it) }, { it * rowsOf(it) - count }, { -it }))
}

private fun gridSpots(keys: List<Any>, area: Rect, input: GridInput, offsetX: Float): List<Spot> {
    val gap = input.gap
    val columns = input.columns?.invoke(keys.size, area.width, area.height)?.coerceIn(1, keys.size)
        ?: gridColumns(keys.size, area.width, area.height, gap)
    val rows = ceil(keys.size / columns.toFloat()).toInt()
    val cellW = (area.width - gap * (columns - 1)) / columns
    val cellH = (area.height - gap * (rows - 1)) / rows
    return keys.mapIndexed { index, key ->
        val row = index / columns
        val col = index % columns
        val inRow = minOf(columns, keys.size - row * columns)
        val center = (columns - inRow) * (cellW + gap) / 2f
        val x = offsetX + area.left + center + col * (cellW + gap)
        val y = area.top + row * (cellH + gap)
        Spot(key, Rect(x, y, x + cellW, y + cellH), KTileRole.Grid)
    }
}

private fun oneOnOnePlan(input: GridInput): GridPlan {
    val other = input.keys.first { it != input.self }
    val area = input.area
    val landscape = input.full.width > input.full.height
    val w = input.floatingWidth
    val h = if (landscape) w * SHORT_SIDE else w / SHORT_SIDE
    val left = area.left + input.margin
    val right = area.right - input.margin - w
    val top = area.top + input.margin
    val bottom = area.bottom - input.margin - h
    val (x, y) = when (input.corner) {
        KCorner.TopStart -> left to top
        KCorner.TopEnd -> right to top
        KCorner.BottomStart -> left to bottom
        KCorner.BottomEnd -> right to bottom
    }
    return GridPlan(
        listOf(Spot(other, input.full, KTileRole.Full), Spot(input.self!!, Rect(x, y, x + w, y + h), KTileRole.Floating)),
        input.full,
        area,
    )
}

private fun focusPlan(input: GridInput, focus: Any): GridPlan {
    val area = input.area
    val others = input.keys.filter { it != focus }
    if (others.isEmpty()) return GridPlan(listOf(Spot(focus, area, KTileRole.Focus)), input.full, area)
    val landscape = input.full.width > input.full.height
    val gap = input.gap
    val s = input.strip
    val focusRect = if (landscape) {
        Rect(area.left, area.top, area.right - s - gap, area.bottom)
    } else {
        Rect(
            area.left,
            area.top,
            area.right,
            area.bottom - s - gap,
        )
    }
    val stripRect = if (landscape) {
        Rect(
            area.right - s,
            area.top,
            area.right,
            area.bottom,
        )
    } else {
        Rect(area.left, area.bottom - s, area.right, area.bottom)
    }
    val thumbW = if (landscape) s else s * SHORT_SIDE
    val thumbH = if (landscape) s * SHORT_SIDE else s
    val step = (if (landscape) thumbH else thumbW) + gap
    val spots = listOf(Spot(focus, focusRect, KTileRole.Focus)) + others.mapIndexed { i, key ->
        val x = if (landscape) stripRect.left else stripRect.left + i * step
        val y = if (landscape) stripRect.top + i * step else stripRect.top
        Spot(key, Rect(x, y, x + thumbW, y + thumbH), KTileRole.Strip)
    }
    return GridPlan(spots, input.full, area, StripArea(stripRect, others.size * step - gap, horizontal = !landscape))
}

/** 놓은 자리 [center]에서 가장 가까운 모서리. 논리 좌표(시작 쪽 x = 0)다. */
internal fun nearestCorner(center: Offset, full: Rect): KCorner {
    val end = center.x > full.center.x
    val lower = center.y > full.center.y
    return when {
        !end && !lower -> KCorner.TopStart
        end && !lower -> KCorner.TopEnd
        !end -> KCorner.BottomStart
        else -> KCorner.BottomEnd
    }
}

private val STRIP_MAX = 136.dp
private val STRIP_GLOW = 12.dp
private const val STRIP_FRACTION = 0.2f
private val WIDE_WINDOW = 600.dp
private val FLOATING = 104.dp
private val FLOATING_WIDE = 160.dp
private val FLOATING_MARGIN = 8.dp
private val PAGE_DOTS = 24.dp
private const val SHORT_SIDE = 0.75f
private const val TIE_RATIO = 0.75f
private const val PORTRAIT_TILE = 3f / 4f
private const val LANDSCAPE_TILE = 16f / 9f
private const val SQUARE_BAND = 1.2f
private const val MOVE_DAMPING = 0.82f
private const val APPEAR_MS = 240
private const val APPEAR_SCALE = 0.94f
