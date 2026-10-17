// 상태 클래스는 하나뿐이지만 이 파일의 중심은 KLargeTitleBar 함수라서 파일 이름을 그쪽에 맞춘다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * [KLargeTitleBar]의 접힘 상태. [connection]을 목록의 부모에 `Modifier.nestedScroll(state.connection)`로 붙이면
 * 목록 스크롤에 맞춰 큰 제목이 접힌다. 목록 스크롤을 가로채지 않으므로 목록은 평소처럼 움직인다.
 * 단, 맨 위에서 아래로 더 당긴 거리는 이 상태가 소비해 큰 제목을 키우는 데 쓴다.
 *
 * 인자 없는 [rememberKLargeTitleState]로 만들면 중첩 스크롤로 전해진 거리만 세므로, `scrollToItem`처럼 코드로 옮긴 스크롤은
 * 목록이 맨 위에 다시 닿을 때까지 접힘에 반영되지 않는다. 코드로도 스크롤하는 목록이면 `LazyListState`나 `ScrollState`를 받는
 * 오버로드를 쓴다. 이때 접힘은 목록의 실제 위치로 정하고, [connection]은 당겨서 키우는 효과에만 쓰인다.
 */
@Stable
public class KLargeTitleState internal constructor(
    initialScrolled: Float,
    private val position: (() -> Float)? = null,
    // 스크롤이 멈췄을 때 반쯤 접힌 큰 제목을 정착시키려고 목록을 조금 더 굴린다. 목록 상태를 받은 오버로드에서만 있다.
    internal val scrollable: ScrollableState? = null,
) {
    // 목록이 맨 위에서 내려간 거리(px). 목록이 맨 위에 닿을 때마다 0으로 맞춰 오차가 쌓이지 않게 한다.
    internal var scrolled by mutableFloatStateOf(initialScrolled)
    internal var range by mutableFloatStateOf(0f)
    internal var stretch by mutableFloatStateOf(0f)
    internal var stretchLimit = 1f

    /** 접힌 정도(0..1). 0이면 큰 제목이 모두 보이고, 1이면 큰 제목이 사라지고 바에 작은 제목이 보인다. 큰 제목을 아직 측정하지 못했으면 0이다. */
    public val collapsedFraction: Float get() = if (range <= 0f) 0f else (offset / range).coerceIn(0f, 1f)

    // 큰 제목이 올라간 거리(px). 목록 상태를 받았으면 실제 위치를, 아니면 중첩 스크롤로 센 거리를 쓴다.
    internal val offset: Float get() = position?.invoke() ?: scrolled

    /** 맨 위에서 아래로 더 당긴 정도(0..1). 큰 제목이 이 값에 비례해 커진다. */
    public val stretchFraction: Float get() = (stretch / stretchLimit).coerceIn(0f, 1f)

    /** 목록의 부모에 붙일 중첩 스크롤 연결. */
    public val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (stretch <= 0f || available.y >= 0f) return Offset.Zero
            // 당긴 상태에서 다시 위로 밀면 목록보다 먼저 늘어난 제목을 되돌린다.
            val used = maxOf(available.y, -stretch)
            stretch += used
            return Offset(0f, used)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            scrolled = (scrolled - consumed.y).coerceAtLeast(0f)
            if (available.y <= 0f) return Offset.Zero
            scrolled = 0f
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            val room = (1f - stretch / stretchLimit).coerceAtLeast(0f)
            stretch += available.y * STRETCH_RESISTANCE * room
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (stretch <= 0f) return Velocity.Zero
            animate(stretch, 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = RELEASE_STIFFNESS)) {
                    value,
                    _,
                ->
                stretch = value
            }
            return available
        }
    }

    internal companion object {
        val Saver: Saver<KLargeTitleState, Float> = Saver(save = { it.scrolled }, restore = { KLargeTitleState(it) })
    }
}

/** [KLargeTitleBar]의 상태를 만든다. 화면을 회전해도 접힌 정도가 유지된다. 접힘은 중첩 스크롤로 전해진 거리로만 정한다. */
@Composable
public fun rememberKLargeTitleState(): KLargeTitleState = rememberSaveable(saver = KLargeTitleState.Saver) {
    KLargeTitleState(0f)
}

/**
 * [listState]의 실제 스크롤 위치로 접힘을 정하는 상태를 만든다. 첫 항목이 보이면 그 항목이 가려진 거리(px)로, 첫 항목이 지나갔으면
 * 다 접힌 것으로 본다. `scrollToItem` 같은 코드 스크롤도 바로 반영된다. 첫 항목이 큰 제목 높이보다 낮으면 그 항목이 지나가는 순간
 * 남은 만큼 한꺼번에 접힌다.
 */
@Composable
public fun rememberKLargeTitleState(listState: LazyListState): KLargeTitleState = remember(listState) {
    KLargeTitleState(0f, {
        if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else Float.POSITIVE_INFINITY
    }, listState)
}

/** [scrollState]의 스크롤 위치(px)로 접힘을 정하는 상태를 만든다. `scrollTo` 같은 코드 스크롤도 바로 반영된다. */
@Composable
public fun rememberKLargeTitleState(scrollState: ScrollState): KLargeTitleState = remember(scrollState) {
    KLargeTitleState(0f, { scrollState.value.toFloat() }, scrollState)
}

/**
 * 큰 제목 바. 처음에는 바 아래에 34sp 큰 제목이 보이고, 목록을 스크롤하면 큰 제목이 콘텐츠와 함께 올라가며
 * 작아지고 흐려진다. 다 접히면 위쪽 글래스 바가 나타나고 가운데에 작은 제목이 보인다. 맨 위에서 아래로 더 당기면
 * 큰 제목이 고무줄처럼 살짝 커졌다가 손을 떼면 돌아온다.
 *
 * 바의 높이는 큰 제목까지 포함해 고정되어 있으므로 [KineticaScaffold]의 `topBar`에 두면 콘텐츠가 받는 위쪽 패딩이
 * 스크롤 중에 바뀌지 않는다. 접힌 바의 글래스 배경은 아래 가장자리가 끊기지 않고 콘텐츠 쪽으로 옅어진다.
 * 큰 제목 영역은 터치를 받지 않아, 접힌 뒤 그 아래로 지나가는 목록 항목을 그대로 누를 수 있다.
 *
 * ```kotlin
 * val titleState = rememberKLargeTitleState()
 * KineticaScaffold(topBar = { KLargeTitleBar(titleState, "설정", onBack = { finish() }) }) { padding ->
 *     LazyColumn(Modifier.nestedScroll(titleState.connection), contentPadding = padding) { ... }
 * }
 * ```
 *
 * @param onBack `null`이 아니면 시작 쪽에 뒤로 버튼을 두고, 누르면 호출한다. RTL에서는 화살표 방향이 바뀐다.
 * @param bottom 큰 제목 아래에 붙일 줄(탭이나 검색창). 큰 제목과 함께 올라가다가 다 접히면 작은 바 바로 아래에 붙어 남고,
 *   그때는 글래스 바 배경이 이 줄까지 이어진다. 바 전체 높이는 이 줄을 포함해 고정이다. [contentMaxWidth] 안에 가운데 정렬한다.
 * @param actions 끝 쪽에 놓을 버튼들
 * @param contentMaxWidth 뒤로 버튼, 버튼들, 큰 제목, [bottom]을 이 폭 안에 두고 가운데 정렬한다. 넓은 화면에서 본문을 읽기 폭으로 묶었을 때
 *   제목 줄을 본문과 같은 정렬선에 맞추는 데 쓴다. 글래스 배경은 그대로 화면 폭 전체에 깔린다. 기본값은 제한 없음.
 */
@Composable
public fun KLargeTitleBar(
    state: KLargeTitleState,
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
    bottom: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    contentMaxWidth: Dp = Dp.Unspecified,
) {
    val density = LocalDensity.current
    state.stretchLimit = with(density) { STRETCH_LIMIT.toPx() }
    // 바 배경은 접히는 막바지에만 바뀌므로, 단계를 나눠 그 사이에만 몇 번 다시 그린다.
    val barLevel by remember(state) {
        derivedStateOf {
            val level = ((state.collapsedFraction - BAR_FADE_START) / (1f - BAR_FADE_START)).coerceIn(0f, 1f)
            (level * BAR_STEPS).roundToInt() / BAR_STEPS
        }
    }
    var smallHeight by remember { mutableIntStateOf(0) }
    var bottomHeight by remember { mutableIntStateOf(0) }
    state.scrollable?.let { SettleHalfCollapsed(state, it) }
    ReportStatusBarCover(surface) { barLevel >= 1f }
    Box(modifier.fillMaxWidth()) {
        // 다 접히면 아래 줄이 작은 바 바로 밑에 오므로, 글래스 배경을 두 줄 높이만큼 깐다.
        BarBackground(surface, barLevel) { smallHeight + if (bottom != null) bottomHeight else 0 }
        Column(Modifier.fillMaxWidth()) {
            SmallBar(state, title, onBack, surface, Modifier.onSizeChanged { smallHeight = it.height }, contentMaxWidth, actions)
            LargeTitle(state, title, surface, contentMaxWidth)
            if (bottom != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { bottomHeight = it.height }
                        .graphicsLayer { translationY = -state.offset.coerceAtMost(state.range) },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    // 넓은 화면에서 탭 줄이 큰 제목, 본문과 같은 폭에 놓이게 한다.
                    Box(Modifier.widthIn(max = contentMaxWidth).fillMaxWidth()) { bottom() }
                }
            }
        }
    }
}

/**
 * 스크롤이 큰 제목을 반쯤 접은 채 멈추면, 절반 넘게 접혔으면 다 접고 아니면 다 펼치도록 목록을 마저 굴린다.
 * 손을 떼거나 키보드가 올라와 목록이 밀린 뒤에 제목이 잘린 채 남지 않게 하려는 것이다. 사용자가 다시 스크롤하면 그쪽이 이긴다.
 */
@Composable
private fun SettleHalfCollapsed(state: KLargeTitleState, scrollable: ScrollableState) {
    val still = reducedMotion()
    LaunchedEffect(state, scrollable, still) {
        // 코드로 한 번에 옮긴 스크롤은 진행 중 표시가 한 프레임도 켜지지 않을 수 있어 위치가 바뀔 때도 다시 본다.
        snapshotFlow { scrollable.isScrollInProgress to state.offset }.collect { (moving, _) ->
            if (moving) return@collect
            val range = state.range
            val offset = state.offset
            if (range <= 0f || offset <= 0f || offset >= range) return@collect
            val by = if (offset / range >= HALF) range - offset else -offset
            if (still) scrollable.scrollBy(by) else scrollable.animateScrollBy(by, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }
}

/**
 * 다 접힌 바가 상태 표시줄을 불투명하게 덮으면 [KineticaScaffold]에 알려, 그 아래에서 보이지 않는 가장자리 흐림을 그리지 않게 한다.
 * 스캐폴드의 topBar 칸에 놓였을 때만 알린다.
 */
@Composable
private fun ReportStatusBarCover(surface: SurfaceStyle, collapsed: () -> Boolean) {
    val host = LocalKineticaHost.current ?: return
    if (!LocalTopBarSlot.current) return
    // 글래스는 배경 기록을 블러 처리해 불투명하게 칠하고, 단색과 톤은 표면 색이 불투명할 때만 덮는다.
    val opaque = when (surface) {
        is SurfaceStyle.Glass -> LocalGlassBackdrop.current != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        else -> surface.fillColor.alpha >= 1f
    }
    val covered = opaque && collapsed()
    DisposableEffect(host, covered) {
        host.statusBarCovered = covered
        onDispose { host.statusBarCovered = false }
    }
}

/**
 * 접힌 바의 글래스 배경. 높이는 [height](px)이고, 그 아래로 [BAR_FADE]만큼 더 그리면서 점점 투명해져 콘텐츠와의 경계가 부드럽게 이어진다.
 * 늘어난 부분은 배치 크기에 넣지 않아 바 높이와 콘텐츠 패딩은 그대로다.
 */
@Composable
private fun BarBackground(surface: SurfaceStyle, level: Float, height: () -> Int) {
    // 펼친 동안에는 배경이 보이지 않으니 화면 밖 레이어도 만들지 않는다.
    if (level <= 0f) return
    Box(
        Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val own = height()
                val fade = BAR_FADE.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, own + fade))
                layout(constraints.maxWidth, own) { placeable.place(0, 0) }
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val solid = ((size.height - BAR_FADE.toPx()) / size.height).coerceIn(0f, 1f)
                val mask = Brush.verticalGradient(0f to Color.Black, solid to Color.Black, 1f to Color.Transparent)
                onDrawWithContent {
                    drawContent()
                    drawRect(mask, blendMode = BlendMode.DstIn)
                }
            }
            .barSurface(surface, level),
    )
}

@Composable
private fun Modifier.barSurface(surface: SurfaceStyle, level: Float): Modifier =
    kSurface(surface.withoutShadow(), RectangleShape, intensity = level)

@Composable
private fun SmallBar(
    state: KLargeTitleState,
    title: String,
    onBack: (() -> Unit)?,
    surface: SurfaceStyle,
    background: Modifier,
    maxWidth: Dp,
    actions: @Composable RowScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .then(background)
            .statusBarsPadding()
            .height(BAR_HEIGHT),
    ) {
        ThemedText(
            title,
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = SMALL_TITLE_INSET)
                .graphicsLayer {
                    val shown = ((state.collapsedFraction - SMALL_TITLE_START) / (1f - SMALL_TITLE_START)).coerceIn(0f, 1f)
                    alpha = shown
                    translationY = SMALL_TITLE_RISE.toPx() * (1f - shown)
                }
                // 큰 제목과 같은 글이므로 TalkBack이 두 번 읽지 않게 한다.
                .clearAndSetSemantics {},
            style = TextStyle(color = surface.contentColor, fontSize = 17.sp, fontWeight = KineticaTheme.design.title(FontWeight.SemiBold)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.align(Alignment.Center).widthIn(max = maxWidth).fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let { BackButton(surface, it) }
            Spacer(Modifier.weight(1f))
            actions()
        }
    }
}

@Composable
private fun LargeTitle(state: KLargeTitleState, title: String, surface: SurfaceStyle, maxWidth: Dp) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { state.range = it.height.toFloat() }
            .drawWithContent {
                // 위로 올라가는 제목이 작은 바를 덮지 않도록 위쪽만 자른다. 아래로 커질 때는 자르지 않는다.
                clipRect(left = -size.width, top = 0f, right = size.width * 2, bottom = size.height * 2) {
                    this@drawWithContent.drawContent()
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        ThemedText(
            title,
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .padding(start = LARGE_TITLE_INSET, end = LARGE_TITLE_INSET, top = 2.dp, bottom = 10.dp)
                .graphicsLayer {
                    val collapsed = state.collapsedFraction
                    val scale = 1f - LARGE_TITLE_SHRINK * collapsed + LARGE_TITLE_GROW * state.stretchFraction
                    scaleX = scale
                    scaleY = scale
                    translationY = -state.offset.coerceAtMost(state.range)
                    alpha = (1f - collapsed * LARGE_TITLE_FADE).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(if (rtl) 1f else 0f, CENTER)
                }
                .semantics { heading() },
            style = TextStyle(color = surface.contentColor, fontSize = 34.sp, fontWeight = KineticaTheme.design.title(FontWeight.Bold)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BackButton(surface: SurfaceStyle, onBack: () -> Unit) {
    val label = stringResource(R.string.kinetica_ui_large_title_back)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // 바의 다른 아이콘 버튼(KIconButton)과 같은 button 모양을 따른다. 기본 형태에서는 원이다.
    val shape = shapeFor { button }
    Box(
        Modifier
            .size(BACK_SIZE)
            .kSurface(surface.withoutShadow(), shape)
            .kClickable(shape, onClick = onBack)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(CHEVRON_SIZE)) {
            val stroke = CHEVRON_STROKE.toPx()
            // 시작 방향을 가리키는 꺾쇠. RTL에서는 오른쪽을 가리킨다.
            val tipX = if (rtl) size.width * CHEVRON_FAR else size.width * CHEVRON_NEAR
            val tailX = if (rtl) size.width * CHEVRON_NEAR else size.width * CHEVRON_FAR
            val center = Offset(tipX, size.height / 2)
            drawLine(surface.contentColor, center, Offset(tailX, size.height * CHEVRON_NEAR / 2), stroke, StrokeCap.Round)
            drawLine(surface.contentColor, center, Offset(tailX, size.height * (1f - CHEVRON_NEAR / 2)), stroke, StrokeCap.Round)
        }
    }
}

private const val CENTER = 0.5f
private val BAR_HEIGHT = 52.dp
private val BAR_FADE = 18.dp
private val BACK_SIZE = KMetrics.IconButton
private val CHEVRON_SIZE = 18.dp
private val CHEVRON_STROKE = 2.2.dp
private const val CHEVRON_NEAR = 0.3f
private const val CHEVRON_FAR = 0.65f
private val SMALL_TITLE_INSET = 64.dp
private val SMALL_TITLE_RISE = 6.dp
private const val SMALL_TITLE_START = 0.75f
private val LARGE_TITLE_INSET = 20.dp
private const val LARGE_TITLE_SHRINK = 0.12f
private const val LARGE_TITLE_GROW = 0.08f
private const val LARGE_TITLE_FADE = 1.4f
private const val BAR_FADE_START = 0.6f
private const val BAR_STEPS = 20f
private val STRETCH_LIMIT = 120.dp
private const val STRETCH_RESISTANCE = 0.5f
private const val RELEASE_STIFFNESS = 400f
private const val HALF = 0.5f
