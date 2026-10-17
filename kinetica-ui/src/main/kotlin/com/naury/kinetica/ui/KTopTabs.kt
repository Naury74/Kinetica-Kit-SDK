// 파일 이름은 대표 컴포저블인 KTopTabs를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.coroutines.launch

/** [KTopTabs]의 선택 인디케이터 모양 */
public enum class KTabIndicator {
    /** 고른 탭 뒤에 옅은 캡슐을 깐다. */
    Pill,

    /** 고른 탭 이름 아래에 강조색 밑줄을 긋고, 고른 탭 이름도 강조색으로 쓴다. */
    Underline,

    /** 탭 줄 전체에 옅은 트랙을 깔고, 고른 탭 자리에 밝은 칸이 미끄러진다. [KSegmentedControl]과 같은 모양이다. */
    Segmented,

    /** 고른 탭 뒤에 강조색으로 채운 칸을 깔고, 고른 탭 이름을 강조색 위 글자 색으로 쓴다. */
    Filled,
}

/**
 * [PagerState]와 함께 쓰는 상단 탭. 페이지를 넘기는 동안 인디케이터가 손가락을 따라 실시간으로 움직이며, 앞쪽 끝이 먼저 가고
 * 뒤쪽 끝이 따라와 액체처럼 늘어났다 줄어든다. 탭 글자 색도 넘어간 비율만큼 섞인다. 탭을 누르면 그 페이지로 이동한다.
 *
 * [scrollable]이면 탭이 글자 너비만큼만 차지하고 가로로 스크롤되며, 선택한 탭이 가운데로 오도록 따라 스크롤한다.
 * 위치는 그리기 단계에서만 읽으므로 넘기는 동안 탭을 다시 컴포지션하지 않는다. 배경은 그리지 않으니 글래스 위에 두려면
 * [KSurface]나 [KineticaScaffold]의 `topBar` 안에 넣는다.
 *
 * ```kotlin
 * val pager = rememberPagerState { tabs.size }
 * Column {
 *     KTopTabs(tabs, pager)
 *     HorizontalPager(pager) { page -> Page(page) }
 * }
 * ```
 *
 * @param tabs 탭 목록. 비어 있으면 [IllegalArgumentException]을 던진다. 탭 수는 [pagerState]의 페이지 수와 같아야 하며,
 *   다르면 짧은 쪽에 맞춰 그린다.
 * @param scrollable true면 가로 스크롤 탭, false면 너비를 똑같이 나눈 고정 탭. 기본값은 탭이 5개 이상일 때 true다.
 * @param indicator 선택 인디케이터 모양. 어느 모양이든 페이지를 넘기는 동안 손가락을 따라 늘어났다 줄어든다.
 *
 * [KTab.badge]나 [KTab.dot]을 주면 탭 이름 옆에 배지가 붙는다.
 */
@Composable
public fun KTopTabs(
    tabs: List<KTab>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    scrollable: Boolean = tabs.size > MAX_FIXED_TABS,
    surface: SurfaceStyle = KineticaTheme.surface,
    indicator: KTabIndicator = KineticaTheme.design.indicator,
) {
    require(tabs.isNotEmpty()) { "tabs가 비어 있으면 안 됩니다." }
    val bounds = remember(tabs.size) { TabBounds(tabs.size) }
    val scope = rememberCoroutineScope()
    val position = { (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, (tabs.size - 1).toFloat()) }
    val scroll = rememberScrollState()
    val edge = with(LocalDensity.current) { SCROLL_EDGE.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    if (scrollable) {
        LaunchedEffect(pagerState, bounds, rtl) {
            // 탭 위치가 재어지기 전(복원 직후 등)에 고른 탭은 잰 뒤에 다시 맞추도록 측정 횟수도 함께 본다.
            snapshotFlow { pagerState.targetPage.coerceIn(0, tabs.size - 1) to bounds.version }.collect { (page, _) ->
                if (bounds.width[page] <= 0f || bounds.viewport <= 0f) return@collect
                // RTL에서는 스크롤 0이 오른쪽 끝이므로 오른쪽에서 잰 거리로 바꾼다.
                val center = if (rtl) bounds.content - bounds.centerOf(page) else bounds.centerOf(page)
                val target = edge + center - bounds.viewport / 2f
                scroll.animateScrollTo(target.toInt().coerceIn(0, scroll.maxValue))
            }
        }
    }
    val rowModifier = if (scrollable) {
        Modifier.horizontalScroll(scroll).padding(horizontal = SCROLL_EDGE)
    } else {
        Modifier.fillMaxWidth()
    }
    val design = KineticaTheme.design
    val look = indicatorLook(indicator, surface)
    Box(modifier.onSizeChanged { bounds.setViewport(it.width.toFloat()) }.padding(vertical = 6.dp)) {
        Row(
            rowModifier
                .onSizeChanged { bounds.setContent(it.width.toFloat()) }
                .drawBehind { drawTabIndicator(bounds, position(), look) }
                .selectableGroup(),
            horizontalArrangement = if (scrollable) Arrangement.spacedBy(2.dp) else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val selected = pagerState.currentPage
            tabs.forEachIndexed { index, tab ->
                TopTab(
                    tab,
                    TopTabLook(index == selected, scrollable, surface, look.activeColor, look.shapes.pill, design),
                    { 1f - abs(position() - index).coerceAtMost(1f) },
                    Modifier.onPlaced { bounds.set(index, it.positionInParent().x, it.size.width.toFloat()) },
                ) { scope.launch { pagerState.animateScrollToPage(index) } }
            }
        }
    }
}

/** 탭 하나를 그리는 데 필요한 고정 값. 파라미터 수를 줄이려고 묶었다. */
private class TopTabLook(
    val selected: Boolean,
    val scrollable: Boolean,
    val surface: SurfaceStyle,
    val activeColor: Color,
    val shape: Shape,
    val design: KineticaDesign,
)

/** 디자인 언어에 맞춘 인디케이터 모양과 색 */
@Composable
private fun indicatorLook(indicator: KTabIndicator, surface: SurfaceStyle): IndicatorLook {
    val shapes = KineticaTheme.shapes
    val colors = KineticaTheme.colors
    val active = when (indicator) {
        KTabIndicator.Underline -> colors.accent
        KTabIndicator.Filled -> colors.onAccent
        else -> surface.contentColor
    }
    return IndicatorLook(
        indicator,
        surface,
        colors.accent,
        active,
        IndicatorShapes(shapes.shape(shapes.indicator), shapes.shape(shapes.tabBar), shapes.inner(shapes.tabBar, INDICATOR_INSET + 1.dp)),
    )
}

/** [pill]은 인디케이터, [track]은 세그먼트 트랙, [thumb]은 그 안 칸 모양이다. */
private class IndicatorShapes(val pill: Shape, val track: Shape, val thumb: Shape)

/** 인디케이터를 그리는 데 필요한 값. [activeColor]는 고른 탭 글자 색이다. */
private class IndicatorLook(
    val kind: KTabIndicator,
    val surface: SurfaceStyle,
    val accent: Color,
    val activeColor: Color,
    val shapes: IndicatorShapes,
)

@Composable
private fun RowScope.TopTab(tab: KTab, look: TopTabLook, activeness: () -> Float, modifier: Modifier, onClick: () -> Unit) {
    val surface = look.surface
    val idle = surface.contentColor.copy(alpha = IDLE_ALPHA)
    Row(
        modifier
            .then(if (look.scrollable) Modifier.widthIn(min = SCROLL_MIN_WIDTH) else Modifier.weight(1f))
            .heightIn(min = TAB_HEIGHT)
            .selectable(look.selected, null, KIndication(InsetShape(look.shape, vertical = INDICATOR_INSET)), role = Role.Tab) {
                onClick()
            }
            .padding(horizontal = TAB_PADDING, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tab.icon?.let { icon ->
            val painter = rememberVectorPainter(icon)
            val filters = remember(idle, look.activeColor) { ColorFilter.tint(idle) to ColorFilter.tint(look.activeColor) }
            // 두 색으로 겹쳐 그리고 투명도만 바꿔 프레임마다 ColorFilter를 만들지 않는다.
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .size(ICON_SIZE)
                    .drawBehind {
                        val t = activeness()
                        with(painter) {
                            if (t < 1f) draw(size, alpha = 1f - t, colorFilter = filters.first)
                            if (t > 0f) draw(size, alpha = t, colorFilter = filters.second)
                        }
                    },
            )
        }
        ThemedText(
            tab.label,
            style = TextStyle(fontSize = 15.sp, fontWeight = if (look.selected) look.design.labelWeight else look.design.idleWeight),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = { lerp(idle, look.activeColor, activeness()) },
        )
        if (tab.dot || (tab.badge ?: 0) > 0) KBadge(tab.badge, Modifier.padding(start = 6.dp), dot = tab.dot)
    }
}

/** 탭마다 x 시작과 너비(px, 부모 기준). 배치 단계에서 쓰고 그리기 단계에서 읽는다. */
@Stable
private class TabBounds(count: Int) {
    val left = FloatArray(count)
    val width = FloatArray(count)
    var viewport = 0f
        private set
    var content = 0f
        private set

    /** 잰 값이 바뀔 때마다 1씩 는다. 스크롤 위치를 다시 맞출 때를 알리는 데만 쓴다. */
    var version by mutableIntStateOf(0)
        private set

    fun set(index: Int, x: Float, w: Float) {
        if (left[index] == x && width[index] == w) return
        left[index] = x
        width[index] = w
        version++
    }

    fun setViewport(value: Float) {
        if (viewport == value) return
        viewport = value
        version++
    }

    fun setContent(value: Float) {
        if (content == value) return
        content = value
        version++
    }

    fun centerOf(index: Int): Float = left[index] + width[index] / 2f
}

/** [look]의 모양으로 인디케이터를 그린다. 세그먼트 모양은 탭 줄 전체에 트랙을 먼저 깐다. */
private fun DrawScope.drawTabIndicator(bounds: TabBounds, position: Float, look: IndicatorLook) {
    when (look.kind) {
        KTabIndicator.Pill -> drawLiquidIndicator(
            bounds,
            position,
            look.shapes.pill,
            look.surface.contentColor.copy(alpha = INDICATOR_ALPHA),
        )

        KTabIndicator.Filled -> drawLiquidIndicator(bounds, position, look.shapes.pill, look.accent)

        KTabIndicator.Underline -> liquidSpan(bounds, position)?.let { (left, right) ->
            val inset = UNDERLINE_INSET.toPx().coerceAtMost((right - left) / UNDERLINE_MAX_INSET)
            val height = UNDERLINE_HEIGHT.toPx()
            drawRoundRect(
                look.accent,
                topLeft = Offset(left + inset, size.height - height),
                size = Size(right - left - inset * 2, height),
                cornerRadius = CornerRadius(height / 2),
            )
        }

        KTabIndicator.Segmented -> {
            val ink = look.surface.contentColor
            drawShape(look.shapes.track, ink.copy(alpha = TRACK_ALPHA), Offset.Zero, size)
            liquidSpan(bounds, position)?.let { (left, right) ->
                val inset = INDICATOR_INSET.toPx() + 1.dp.toPx()
                val height = size.height - inset * 2
                val thumb = if (look.surface.isDark) Color.White.copy(alpha = DARK_THUMB_ALPHA) else Color.White
                val box = Size(right - left - inset * 2, height)
                // 밝은 칸이 트랙 위에 떠 보이도록 아래로 살짝 비낀 옅은 그림자를 먼저 깐다.
                drawShape(look.shapes.thumb, Color.Black.copy(alpha = THUMB_SHADOW_ALPHA), Offset(left + inset, inset + 1.dp.toPx()), box)
                drawShape(look.shapes.thumb, thumb, Offset(left + inset, inset), box)
            }
        }
    }
}

/** [position] 사이에서 앞쪽 끝은 빨리, 뒤쪽 끝은 늦게 움직이는 인디케이터의 왼쪽과 오른쪽 끝(px). 탭을 아직 재지 않았으면 null이다. */
private fun liquidSpan(bounds: TabBounds, position: Float): Pair<Float, Float>? {
    val count = bounds.left.size
    val from = floor(position).toInt().coerceIn(0, count - 1)
    val to = (from + 1).coerceAtMost(count - 1)
    val f = position - from
    if (bounds.width[from] <= 0f) return null
    val fromLeft = bounds.left[from]
    val toLeft = bounds.left[to]
    val forward = toLeft >= fromLeft
    val fast = 1f - (1f - f) * (1f - f)
    val slow = f * f
    val left = lerpFloat(fromLeft, toLeft, if (forward) slow else fast)
    val right = lerpFloat(fromLeft + bounds.width[from], toLeft + bounds.width[to], if (forward) fast else slow)
    return left to right
}

/**
 * [position]에 맞춰 [shape] 인디케이터를 그린다. 페이지 사이에서 앞쪽 끝은 빨리, 뒤쪽 끝은 늦게 움직여 늘어났다 줄어든다.
 * 왼쪽 끝과 오른쪽 끝을 따로 보간하므로 RTL처럼 다음 탭이 왼쪽에 있어도 같은 식으로 그려진다.
 */
private fun DrawScope.drawLiquidIndicator(bounds: TabBounds, position: Float, shape: Shape, color: Color) {
    val (left, right) = liquidSpan(bounds, position) ?: return
    val inset = INDICATOR_INSET.toPx()
    drawShape(shape, color, Offset(left, inset), Size(right - left, size.height - inset * 2))
}

private fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private const val MAX_FIXED_TABS = 4
private const val IDLE_ALPHA = 0.55f
private const val INDICATOR_ALPHA = 0.12f
private val INDICATOR_INSET = 2.dp
private val TAB_HEIGHT = 40.dp
private val TAB_PADDING = 14.dp
private val SCROLL_MIN_WIDTH = 56.dp
private val SCROLL_EDGE = 12.dp
private val ICON_SIZE = 18.dp
private val UNDERLINE_HEIGHT = 3.dp
private val UNDERLINE_INSET = 12.dp
private const val UNDERLINE_MAX_INSET = 4f
private const val TRACK_ALPHA = 0.07f
private const val DARK_THUMB_ALPHA = 0.18f
private const val THUMB_SHADOW_ALPHA = 0.06f
