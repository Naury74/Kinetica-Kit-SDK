package com.naury.kinetica.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 액체처럼 이어지는 페이지 인디케이터. 현재 페이지는 긴 캡슐로 보이고, 넘기는 동안 [pageOffset]에 따라 앞쪽 끝이 먼저
 * 다음 점으로 늘어났다가 뒤쪽 끝이 따라오며 줄어든다. 페이지가 [maxVisible]보다 많으면 양 끝 점이 작아지며 밀려난다.
 *
 * [onSelect]가 있으면 점을 눌러 그 페이지로 이동할 수 있고, TalkBack에서는 볼륨 키나 위아래 스와이프로 값을 조절하는 컨트롤로 읽힌다.
 * Pager와 함께 쓸 때는 [PagerState]를 받는 오버로드가 스와이프 중 재컴포지션 없이 그려 더 가볍다.
 *
 * @param pageCount 페이지 수. 0 이하이면 아무것도 그리지 않는다.
 * @param currentPage 현재 페이지(0부터)
 * @param pageOffset 현재 페이지에서 다음 페이지 쪽으로 넘어간 정도(-1..1). `PagerState.currentPageOffsetFraction`을 그대로 넘기면 된다.
 * @param maxVisible 한 번에 보여 줄 점 수. 3보다 작으면 3으로 맞춘다.
 */
@Composable
public fun KPageIndicator(
    pageCount: Int,
    currentPage: Int,
    pageOffset: Float,
    modifier: Modifier = Modifier,
    onSelect: ((Int) -> Unit)? = null,
    maxVisible: Int = DEFAULT_MAX_VISIBLE,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val position by rememberUpdatedState(currentPage + pageOffset)
    PageDots(pageCount, { position }, currentPage, modifier, onSelect, maxVisible, surface)
}

/**
 * [state]의 위치를 따라가는 [KPageIndicator]. 위치는 그리기 단계에서만 읽는다.
 * 점을 누르면 그 페이지로 애니메이션과 함께 이동한다.
 */
@Composable
public fun KPageIndicator(
    state: PagerState,
    modifier: Modifier = Modifier,
    maxVisible: Int = DEFAULT_MAX_VISIBLE,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val scope = rememberCoroutineScope()
    PageDots(
        state.pageCount,
        { state.currentPage + state.currentPageOffsetFraction },
        state.currentPage,
        modifier,
        { page -> scope.launch { state.animateScrollToPage(page) } },
        maxVisible,
        surface,
    )
}

@Composable
private fun PageDots(
    pageCount: Int,
    position: () -> Float,
    currentPage: Int,
    modifier: Modifier,
    onSelect: ((Int) -> Unit)?,
    maxVisible: Int,
    surface: SurfaceStyle,
) {
    if (pageCount <= 0) return
    val visible = maxVisible.coerceAtLeast(MIN_VISIBLE).coerceAtMost(pageCount)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val stateText = stringResource(R.string.kinetica_ui_page_state, currentPage + 1, pageCount)
    val select by rememberUpdatedState(onSelect)
    val dots = DotGeometry(visible, pageCount, rtl)
    Box(
        modifier
            .padding(DOT_PADDING)
            .width(dots.width)
            .height(PILL_HEIGHT)
            .semantics {
                stateDescription = stateText
                progressBarRangeInfo =
                    ProgressBarRangeInfo(currentPage.toFloat(), 0f..(pageCount - 1).toFloat(), (pageCount - 2).coerceAtLeast(0))
                if (onSelect != null) {
                    setProgress { target ->
                        select?.invoke(target.roundToInt().coerceIn(0, pageCount - 1))
                        true
                    }
                }
            }
            .then(
                if (onSelect == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(pageCount, visible, rtl) {
                        detectTapGestures { tap ->
                            val x = if (rtl) size.width - tap.x else tap.x
                            select?.invoke(dots.pageAt(this, x, position()))
                        }
                    }
                },
            )
            .drawBehind { with(dots) { draw(position(), surface.contentColor) } },
    )
}

/** 점들의 배치를 계산한다. 위치마다 점 너비가 바뀌므로 매 프레임 계산하지만 객체는 만들지 않는다. */
private class DotGeometry(private val visible: Int, private val count: Int, private val rtl: Boolean) {
    val width: Dp = (DOT_SIZE + DOT_GAP) * visible + (PILL_WIDTH - DOT_SIZE) - DOT_GAP

    /** 보이는 창의 첫 위치. 현재 페이지가 가운데에 오도록 미끄러진다. */
    private fun windowStart(position: Float): Float = (position - (visible - 1) / 2f).coerceIn(0f, (count - visible).toFloat())

    private fun activeness(index: Int, position: Float): Float = (1f - abs(position - index)).coerceIn(0f, 1f)

    private fun DrawScope.slotWidth(index: Int, position: Float): Float =
        DOT_SIZE.toPx() + (PILL_WIDTH - DOT_SIZE).toPx() * activeness(index, position)

    /** 창 기준으로 [index]번째 점의 시작 x. 창 시작이 소수이면 그 사이를 보간해 부드럽게 밀린다. */
    private fun DrawScope.slotStart(index: Int, position: Float, start: Float): Float {
        val gap = DOT_GAP.toPx()
        val first = floor(start).toInt()
        var x = 0f
        for (i in first until index) x += slotWidth(i, position) + gap
        for (i in index until first) x -= slotWidth(i, position) + gap
        return x - (start - first) * (slotWidth(first, position) + gap)
    }

    /** 창 가장자리 점은 바깥에 숨은 페이지가 있을수록 작아진다. */
    private fun edgeScale(index: Int, start: Float): Float {
        val fromStart = index - start
        val fromEnd = start + visible - 1 - index
        return edge(fromStart, start) * edge(fromEnd, count - visible - start)
    }

    private fun edge(distance: Float, hidden: Float): Float {
        val amount = hidden.coerceIn(0f, 1f)
        val base = ((distance + 1f) / 2f).coerceIn(0f, 1f)
        return 1f - amount * (1f - base)
    }

    fun DrawScope.draw(position: Float, color: Color) {
        val p = position.coerceIn(0f, (count - 1).toFloat())
        val start = windowStart(p)
        val dot = DOT_SIZE.toPx()
        val idle = color.copy(alpha = IDLE_ALPHA)
        val from = floor(p).toInt().coerceAtMost(count - 1)
        val to = (from + 1).coerceAtMost(count - 1)
        val moving = p > from
        val first = floor(start).toInt().coerceAtLeast(0)
        val last = (first + visible).coerceAtMost(count - 1)
        for (i in first..last) {
            val scale = edgeScale(i, start)
            // 현재 캡슐이 지나가는 두 점은 그리지 않는다. 캡슐 뒤로 점이 비치면 액체처럼 이어져 보이지 않는다.
            val covered = i == from || (moving && i == to)
            if (scale <= 0f || covered) continue
            val w = slotWidth(i, p)
            val left = slotStart(i, p, start)
            val h = dot * scale
            val sw = (w - dot) + dot * scale
            drawPill(left + (w - sw) / 2, (size.height - h) / 2, sw, h, idle)
        }
        // 현재 캡슐: 앞쪽 끝은 빨리, 뒤쪽 끝은 늦게 따라가 액체처럼 늘어난다.
        val f = p - from
        val trailing = lerp(slotStart(from, p, start), slotStart(to, p, start), f * f)
        val leading = lerp(
            slotStart(from, p, start) + slotWidth(from, p),
            slotStart(to, p, start) + slotWidth(to, p),
            1f - (1f - f) * (1f - f),
        )
        drawPill(trailing, 0f, leading - trailing, size.height, color)
    }

    private fun DrawScope.drawPill(left: Float, top: Float, width: Float, height: Float, color: Color) {
        val x = if (rtl) size.width - left - width else left
        drawRoundRect(color, Offset(x, top), Size(width, height), CornerRadius(height / 2))
    }

    /** 누른 x(시작 쪽 기준, px)에 가장 가까운 페이지. */
    fun pageAt(density: androidx.compose.ui.unit.Density, x: Float, position: Float): Int = with(density) {
        val pitch = (DOT_SIZE + DOT_GAP).toPx()
        val start = windowStart(position.coerceIn(0f, (count - 1).toFloat()))
        (start + x / pitch).toInt().coerceIn(0, count - 1)
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * 옆 카드가 살짝 보이는 가로 캐러셀. 가운데에서 멀어진 카드는 작아지고, 바깥쪽으로 살짝 기울며 어두워진다.
 * 페이지를 넘길 때 카드의 크기, 기울기, 밝기가 손가락을 따라 함께 바뀐다. 이 값들은 그리기 단계에서만 읽는다.
 *
 * ```kotlin
 * val pager = rememberPagerState { photos.size }
 * Column {
 *     KCarousel(photos.size, state = pager) { page -> PhotoCard(photos[page]) }
 *     KPageIndicator(pager, Modifier.align(Alignment.CenterHorizontally))
 * }
 * ```
 *
 * @param pageCount 페이지 수
 * @param peek 양옆 카드가 보이는 너비
 * @param pageSpacing 카드 사이 간격
 */
@Composable
public fun KCarousel(
    pageCount: Int,
    modifier: Modifier = Modifier,
    peek: Dp = 32.dp,
    state: PagerState = rememberPagerState { pageCount },
    pageSpacing: Dp = 12.dp,
    content: @Composable (page: Int) -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    HorizontalPager(
        state = state,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = peek),
        pageSpacing = pageSpacing,
    ) { page ->
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val offset = state.getOffsetDistanceInPages(page).coerceIn(-1f, 1f)
                    val distance = abs(offset)
                    val scale = 1f - CAROUSEL_SHRINK * distance
                    scaleX = scale
                    scaleY = scale
                    // 줄어든 만큼 가운데 쪽으로 당겨야 양옆 카드가 peek 너비만큼 보인다.
                    val pull = size.width * (1f - scale) / 2f * if (offset > 0f) -1f else 1f
                    translationX = if (rtl) -pull else pull
                    // 오른쪽 카드는 오른쪽 가장자리가 멀어지게 돌린다. RTL은 화면 방향이 반대다.
                    rotationY = CAROUSEL_TILT * offset * if (rtl) 1f else -1f
                    cameraDistance = CAMERA_DISTANCE * density
                    // 어둡게 덮는 색이 카드 모양 밖으로 나가지 않도록 레이어 안에서 SrcAtop으로 칠한다.
                    compositingStrategy = if (distance > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                }
                .drawWithContent {
                    drawContent()
                    val dim = abs(state.getOffsetDistanceInPages(page)).coerceIn(0f, 1f) * CAROUSEL_DIM
                    if (dim > 0f) drawRect(Color.Black.copy(alpha = dim), blendMode = BlendMode.SrcAtop)
                },
        ) { content(page) }
    }
}

private val DOT_SIZE = 7.dp
private val DOT_GAP = 8.dp
private val PILL_WIDTH = 22.dp
private val PILL_HEIGHT = 7.dp
private val DOT_PADDING = 8.dp
private const val IDLE_ALPHA = 0.32f
private const val DEFAULT_MAX_VISIBLE = 7
private const val MIN_VISIBLE = 3
private const val CAROUSEL_SHRINK = 0.12f
private const val CAROUSEL_TILT = 12f
private const val CAROUSEL_DIM = 0.28f
private const val CAMERA_DISTANCE = 12f
