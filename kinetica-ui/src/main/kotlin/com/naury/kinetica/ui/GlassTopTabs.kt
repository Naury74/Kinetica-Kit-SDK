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
import androidx.compose.ui.graphics.ColorFilter
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

/**
 * [PagerState]와 함께 쓰는 상단 탭. 페이지를 넘기는 동안 인디케이터가 손가락을 따라 실시간으로 움직이며, 앞쪽 끝이 먼저 가고
 * 뒤쪽 끝이 따라와 액체처럼 늘어났다 줄어든다. 탭 글자 색도 넘어간 비율만큼 섞인다. 탭을 누르면 그 페이지로 이동한다.
 *
 * [scrollable]이면 탭이 글자 너비만큼만 차지하고 가로로 스크롤되며, 선택한 탭이 가운데로 오도록 따라 스크롤한다.
 * 위치는 그리기 단계에서만 읽으므로 넘기는 동안 탭을 다시 컴포지션하지 않는다. 배경은 그리지 않으니 글래스 위에 두려면
 * [GlassSurface]나 [GlassScaffold]의 `topBar` 안에 넣는다.
 *
 * ```kotlin
 * val pager = rememberPagerState { tabs.size }
 * Column {
 *     GlassTopTabs(tabs, pager)
 *     HorizontalPager(pager) { page -> Page(page) }
 * }
 * ```
 *
 * @param tabs 탭 목록. 비어 있으면 [IllegalArgumentException]을 던진다. 탭 수는 [pagerState]의 페이지 수와 같아야 하며,
 *   다르면 짧은 쪽에 맞춰 그린다.
 * @param scrollable true면 가로 스크롤 탭, false면 너비를 똑같이 나눈 고정 탭. 기본값은 탭이 5개 이상일 때 true다.
 */
@Composable
public fun GlassTopTabs(
    tabs: List<GlassTab>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    scrollable: Boolean = tabs.size > MAX_FIXED_TABS,
    style: GlassStyle = LocalGlassStyle.current,
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
    Box(modifier.onSizeChanged { bounds.setViewport(it.width.toFloat()) }.padding(vertical = 6.dp)) {
        Row(
            rowModifier
                .onSizeChanged { bounds.setContent(it.width.toFloat()) }
                .drawBehind { drawLiquidIndicator(bounds, position(), style) }
                .selectableGroup(),
            horizontalArrangement = if (scrollable) Arrangement.spacedBy(2.dp) else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val selected = pagerState.currentPage
            tabs.forEachIndexed { index, tab ->
                TopTab(
                    tab,
                    TopTabLook(index, index == selected, scrollable, style),
                    { 1f - abs(position() - index).coerceAtMost(1f) },
                    Modifier.onPlaced { bounds.set(index, it.positionInParent().x, it.size.width.toFloat()) },
                ) { scope.launch { pagerState.animateScrollToPage(index) } }
            }
        }
    }
}

/** 탭 하나를 그리는 데 필요한 고정 값. 파라미터 수를 줄이려고 묶었다. */
private class TopTabLook(val index: Int, val selected: Boolean, val scrollable: Boolean, val style: GlassStyle)

@Composable
private fun RowScope.TopTab(tab: GlassTab, look: TopTabLook, activeness: () -> Float, modifier: Modifier, onClick: () -> Unit) {
    val style = look.style
    val idle = style.onGlass.copy(alpha = IDLE_ALPHA)
    Row(
        modifier
            .then(if (look.scrollable) Modifier.widthIn(min = SCROLL_MIN_WIDTH) else Modifier.weight(1f))
            .heightIn(min = TAB_HEIGHT)
            .selectable(look.selected, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                onClick()
            }
            .padding(horizontal = TAB_PADDING, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tab.icon?.let { icon ->
            val painter = rememberVectorPainter(icon)
            val filters = remember(idle, style.onGlass) { ColorFilter.tint(idle) to ColorFilter.tint(style.onGlass) }
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
            style = TextStyle(fontSize = 15.sp, fontWeight = if (look.selected) FontWeight.SemiBold else FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = { lerp(idle, style.onGlass, activeness()) },
        )
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

/**
 * [position]에 맞춰 캡슐 인디케이터를 그린다. 페이지 사이에서 앞쪽 끝은 빨리, 뒤쪽 끝은 늦게 움직여 늘어났다 줄어든다.
 * 왼쪽 끝과 오른쪽 끝을 따로 보간하므로 RTL처럼 다음 탭이 왼쪽에 있어도 같은 식으로 그려진다.
 */
private fun DrawScope.drawLiquidIndicator(bounds: TabBounds, position: Float, style: GlassStyle) {
    val count = bounds.left.size
    val from = floor(position).toInt().coerceIn(0, count - 1)
    val to = (from + 1).coerceAtMost(count - 1)
    val f = position - from
    if (bounds.width[from] <= 0f) return
    val fromLeft = bounds.left[from]
    val fromRight = fromLeft + bounds.width[from]
    val toLeft = bounds.left[to]
    val toRight = toLeft + bounds.width[to]
    val forward = toLeft >= fromLeft
    val fast = 1f - (1f - f) * (1f - f)
    val slow = f * f
    val left = lerpFloat(fromLeft, toLeft, if (forward) slow else fast)
    val right = lerpFloat(fromRight, toRight, if (forward) fast else slow)
    val inset = INDICATOR_INSET.toPx()
    val height = size.height - inset * 2
    drawRoundRect(
        style.onGlass.copy(alpha = INDICATOR_ALPHA),
        topLeft = Offset(left, inset),
        size = Size(right - left, height),
        cornerRadius = CornerRadius(height / 2),
    )
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
