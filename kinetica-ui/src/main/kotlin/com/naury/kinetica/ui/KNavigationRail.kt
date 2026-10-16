package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 태블릿과 펼친 폴더블에서 화면 시작 쪽에 세우는 글래스 내비게이션 레일. 탭 항목은 세로 캡슐에 모여 남은 높이의 가운데에 놓이고,
 * 선택한 항목의 인디케이터는 [KTabBar]처럼 이동하는 방향의 가장자리가 먼저 가고 반대쪽이 따라와 세로로 늘어났다 줄어든다.
 * 상태 표시줄, 내비게이션 바, 시작 쪽 컷아웃 여백은 레일이 넣는다. 폰처럼 좁은 화면까지 함께 다루려면 [KAdaptiveNavigation]을 쓴다.
 *
 * ```kotlin
 * KNavigationRail(tabs, tab, onSelect = { tab = it }, header = { ComposeButton() })
 * ```
 *
 * @param items 탭 목록. 비어 있으면 [IllegalArgumentException]을 던진다.
 * @param selectedIndex 선택된 탭의 위치(0부터). 범위 밖이면 인디케이터를 그리지 않는다.
 * @param header 레일 맨 위에 놓을 내용(로고, 새로 만들기 버튼 등). 캡슐 밖에 그린다.
 */
@Composable
public fun KNavigationRail(
    items: List<KTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    surface: SurfaceStyle = KineticaTheme.surface,
    header: (@Composable () -> Unit)? = null,
) {
    require(items.isNotEmpty()) { "items가 비어 있으면 안 됩니다." }
    val indicator = remember { RailIndicator() }
    val bounds = remember { mutableStateMapOf<Int, ClosedFloatingPointRange<Float>>() }
    val still = reducedMotion()
    val target = bounds[selectedIndex]
    LaunchedEffect(target, still) { target?.let { indicator.moveTo(it, animate = !still) } }
    // 큰 글꼴에서 탭 이름이 글자 중간에서 잘리지 않게 레일도 함께 넓힌다.
    val railWidth = RAIL_WIDTH * LocalDensity.current.fontScale.coerceIn(1f, RAIL_MAX_GROW)
    Column(
        modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
            .padding(RAIL_MARGIN),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (header != null) Box(Modifier.padding(bottom = RAIL_MARGIN)) { header() }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier
                .width(railWidth)
                .kSurface(surface, RoundedCornerShape(railWidth / 2))
                .padding(RAIL_INSET)
                .drawBehind {
                    val top = indicator.top.value
                    val bottom = indicator.bottom.value
                    if (target != null && bottom > top) {
                        drawRoundRect(
                            surface.contentColor.copy(alpha = RAIL_INDICATOR_ALPHA),
                            topLeft = Offset(0f, top),
                            size = Size(size.width, bottom - top),
                            cornerRadius = CornerRadius(minOf(size.width, bottom - top) / 2f),
                        )
                    }
                }
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(RAIL_ITEM_GAP),
        ) {
            items.forEachIndexed { index, item ->
                RailItem(
                    item,
                    index == selectedIndex,
                    surface,
                    Modifier.onPlaced {
                        val y = it.positionInParent().y
                        bounds[index] = y..(y + it.size.height)
                    },
                ) { onSelect(index) }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun RailItem(item: KTab, selected: Boolean, surface: SurfaceStyle, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) RAIL_PRESSED_SCALE else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "rail-item",
    )
    val color = surface.contentColor.copy(alpha = if (selected) 1f else RAIL_UNSELECTED_ALPHA)
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = RAIL_ITEM_HEIGHT)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .selectable(selected, interaction, KIndication(CircleShape), role = Role.Tab, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        item.icon?.let {
            Image(
                rememberVectorPainter(it),
                contentDescription = null,
                Modifier.size(KMetrics.TabIcon),
                colorFilter = ColorFilter.tint(color),
            )
        }
        ThemedText(
            item.label,
            Modifier.padding(top = 4.dp),
            style = TextStyle(
                color = color,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
    }
}

/** 레일 인디케이터의 위아래 가장자리(px, 캡슐 안쪽 기준). 아래로 갈 때는 아래 가장자리가 먼저 가고 위 가장자리가 늦게 따라온다. */
@Stable
private class RailIndicator {
    val top = Animatable(0f)
    val bottom = Animatable(0f)
    private var placed = false

    suspend fun moveTo(target: ClosedFloatingPointRange<Float>, animate: Boolean) = coroutineScope {
        if (!placed || !animate) {
            placed = true
            top.snapTo(target.start)
            bottom.snapTo(target.endInclusive)
            return@coroutineScope
        }
        val down = target.start > top.value
        val fast = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
        val slow = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
        launch { top.animateTo(target.start, if (down) slow else fast) }
        launch { bottom.animateTo(target.endInclusive, if (down) fast else slow) }
    }
}

/**
 * 화면 폭에 맞춰 내비게이션을 바꾸는 컨테이너. 폭이 [KAdaptiveDefaults.TwoPaneMinWidth]보다 좁으면 아래에 [KTabBar]를,
 * 넓으면 시작 쪽에 [KNavigationRail]을 놓는다. 접고 펼치거나 멀티 윈도우로 크기가 바뀌어도 [content]는 다시 만들지 않으므로
 * 스크롤 위치 같은 상태가 그대로다.
 *
 * [content]는 탭 바 뒤까지 그려지고, 탭 바와 레일은 그 내용과 바깥 [KineticaScaffold]의 배경을 함께 블러로 비춘다.
 * [content]가 받는 여백만큼 안쪽에 목록의 `contentPadding`을 주면 마지막 항목이 탭 바에 가리지 않는다.
 *
 * ```kotlin
 * KAdaptiveNavigation(tabs, tab, onSelect = { tab = it }) { padding -> Screen(tab, contentPadding = padding) }
 * ```
 *
 * @param tabs 탭 목록. 비어 있으면 [IllegalArgumentException]을 던진다.
 * @param railHeader 레일로 보일 때 맨 위에 놓을 내용. 탭 바로 보일 때는 그리지 않는다.
 * @param content 화면 내용. 탭 바나 레일이 차지하는 여백(아래 또는 시작 쪽)을 받는다.
 */
@Composable
public fun KAdaptiveNavigation(
    tabs: List<KTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    surface: SurfaceStyle = KineticaTheme.surface,
    railHeader: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    require(tabs.isNotEmpty()) { "tabs가 비어 있으면 안 됩니다." }
    val parent = LocalGlassBackdrop.current
    val backdrop = rememberGlassBackdrop()
    var railWidth by remember { mutableIntStateOf(0) }
    var barHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val wide = maxWidth >= KAdaptiveDefaults.TwoPaneMinWidth
        val padding = with(density) {
            if (wide) PaddingValues(start = railWidth.toDp()) else PaddingValues(bottom = barHeight.toDp())
        }
        // 내용은 배치가 바뀌어도 같은 자리에 두어야 다시 만들어지지 않는다.
        Box(Modifier.fillMaxSize().glassBackdrop(backdrop).withParentBackdrop(backdrop, parent)) { content(padding) }
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            if (wide) {
                KNavigationRail(
                    tabs,
                    selectedIndex,
                    onSelect,
                    Modifier.align(Alignment.CenterStart).onSizeChanged { railWidth = it.width },
                    surface,
                    railHeader,
                )
            } else {
                KTabBar(
                    tabs,
                    selectedIndex,
                    onSelect,
                    Modifier.align(Alignment.BottomCenter).onSizeChanged {
                        barHeight = it.height
                    },
                    surface,
                )
            }
        }
    }
}

/**
 * [own]을 기록할 때만 [parent]의 기록을 먼저 깔아, [own]을 비추는 바가 내용 뒤의 배경까지 함께 블러로 비추게 한다.
 * 화면에는 그리지 않으므로 배경이 두 번 칠해지지 않는다.
 */
internal fun Modifier.withParentBackdrop(own: GlassBackdrop, parent: GlassBackdrop?): Modifier = if (parent == null) {
    this
} else {
    drawBehind {
        if (own.recording && !parent.rootRelative) {
            val offset = parent.origin - own.origin
            translate(offset.x, offset.y) { drawLayer(parent.layer) }
        }
    }
}

private val RAIL_WIDTH = 80.dp
private const val RAIL_MAX_GROW = 1.5f
private val RAIL_MARGIN = 12.dp
private val RAIL_INSET = 6.dp
private val RAIL_ITEM_HEIGHT = 64.dp
private val RAIL_ITEM_GAP = 4.dp
private const val RAIL_INDICATOR_ALPHA = 0.12f
private const val RAIL_UNSELECTED_ALPHA = 0.55f
private const val RAIL_PRESSED_SCALE = 0.9f
