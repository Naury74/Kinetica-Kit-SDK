// 파일 이름은 대표 컴포저블을 따른다. 함께 쓰는 상태 클래스가 하나뿐이라 detekt가 그 이름을 요구한다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * [GlassFloatingTabBar]가 접혔는지와 스크롤 연결. 목록에 [nestedScrollConnection]을 붙이면 아래로 스크롤할 때 접히고 위로 스크롤하면 펼쳐진다.
 * 접힘 여부는 화면 회전 뒤에도 유지된다([rememberGlassFloatingTabBarState]).
 */
@Stable
public class GlassFloatingTabBarState internal constructor(initiallyCollapsed: Boolean) {
    /** 탭 바가 선택한 탭 아이콘만 보이는 작은 캡슐로 접혔는지 */
    public var isCollapsed: Boolean by mutableStateOf(initiallyCollapsed)
        private set

    // 0이면 펼쳐져 있고 1이면 다 접혔다. 레이아웃과 그리기 단계에서만 읽는다.
    internal val progress = Animatable(if (initiallyCollapsed) 1f else 0f)
    internal var thresholdPx = 0f
    private var travel = 0f

    /** 탭 바를 펼친다. */
    public fun expand() {
        isCollapsed = false
        travel = 0f
    }

    /** 탭 바를 접는다. */
    public fun collapse() {
        isCollapsed = true
        travel = 0f
    }

    /**
     * 스크롤 목록에 `Modifier.nestedScroll(…)`로 붙이는 연결. 스크롤을 소비하지 않고 지켜보기만 한다.
     * 같은 방향으로 32dp 넘게 스크롤하면 접거나 펼치고, 목록 맨 위에서 더 당기면 펼친다.
     */
    public val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val dy = consumed.y
            if (dy == 0f) {
                if (available.y > 0f && isCollapsed) expand()
                return Offset.Zero
            }
            if (sign(dy) != sign(travel)) travel = 0f
            travel += dy
            if (travel < -thresholdPx && !isCollapsed) {
                collapse()
            } else if (travel > thresholdPx && isCollapsed) {
                expand()
            }
            return Offset.Zero
        }
    }

    internal companion object {
        val Saver: Saver<GlassFloatingTabBarState, Boolean> = Saver(save = { it.isCollapsed }, restore = { GlassFloatingTabBarState(it) })
    }
}

/** 펼친 상태로 시작하는 [GlassFloatingTabBarState]를 만든다. 접힘 여부는 화면 회전과 프로세스 재시작 뒤에도 유지된다. */
@Composable
public fun rememberGlassFloatingTabBarState(initiallyCollapsed: Boolean = false): GlassFloatingTabBarState =
    rememberSaveable(saver = GlassFloatingTabBarState.Saver) { GlassFloatingTabBarState(initiallyCollapsed) }

/**
 * iOS 26처럼 화면 아래에 떠 있다가 스크롤하면 줄어드는 글래스 탭 바. 목록을 아래로 스크롤하면 시작 쪽의 작은 캡슐로 줄어들어
 * 선택한 탭 아이콘만 보이고, 위로 스크롤하거나 캡슐을 누르면 다시 펼쳐진다. [accessory]가 있으면 펼친 동안에는 탭 바 위에 붙어 있다가,
 * 접히면 캡슐 옆으로 내려와 한 줄이 된다. [GlassScaffold]의 `bottomBar`에 넣으면 뒤로 지나가는 내용을 블러로 비춘다.
 *
 * 접힌 동안에도 TalkBack에서는 캡슐의 사용자 지정 동작으로 모든 탭에 바로 갈 수 있다. 접혀도 차지하는 높이는 그대로라
 * `GlassScaffold`가 넘기는 아래쪽 여백이 바뀌지 않는다. '애니메이션 삭제' 설정이 켜져 있으면 움직임 없이 바로 바뀐다.
 *
 * ```kotlin
 * val bar = rememberGlassFloatingTabBarState()
 * GlassScaffold(bottomBar = { GlassFloatingTabBar(tabs, tab, { tab = it }, state = bar, accessory = { NowPlaying() }) }) { padding ->
 *     LazyColumn(Modifier.nestedScroll(bar.nestedScrollConnection), contentPadding = padding) { … }
 * }
 * ```
 *
 * @param tabs 탭 목록. 비어 있으면 [IllegalArgumentException]을 던진다.
 * @param selectedIndex 선택된 탭의 위치(0부터). 접힌 캡슐은 이 탭의 아이콘을 보여 준다.
 * @param accessory 탭 바에 붙여 함께 움직일 내용. 글래스 캡슐 안에 그리며, 펼친 동안 높이는 56dp다.
 *   접히면 캡슐과 같은 높이로 줄어든다. [GlassMiniPlayerBar]를 넣으면 누를 때 이 자리에서 전체 화면 플레이어로 커진다.
 */
@Composable
public fun GlassFloatingTabBar(
    tabs: List<GlassTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    state: GlassFloatingTabBarState = rememberGlassFloatingTabBarState(),
    style: GlassStyle = LocalGlassStyle.current,
    accessory: (@Composable () -> Unit)? = null,
) {
    require(tabs.isNotEmpty()) { "tabs가 비어 있으면 안 됩니다." }
    val density = LocalDensity.current
    state.thresholdPx = with(density) { COLLAPSE_DISTANCE.toPx() }
    val still = reducedMotion()
    LaunchedEffect(state, state.isCollapsed, still) {
        val target = if (state.isCollapsed) 1f else 0f
        if (still) state.progress.snapTo(target) else state.progress.animateTo(target, spring(COLLAPSE_DAMPING, Spring.StiffnessMediumLow))
    }
    val grow = density.fontScale.coerceIn(1f, MAX_GROW)
    val compact = compactHeight()
    val geometry = remember { FloatingGeometry() }
    Layout(
        content = {
            Box(Modifier.layoutId(BAR_ID).glass(style, CircleShape).clip(CircleShape)) {
                ExpandedTabs(tabs, selectedIndex, onSelect, state, style, geometry)
                CollapsedCapsule(tabs, selectedIndex, onSelect, state, style)
            }
            if (accessory != null) {
                Box(Modifier.layoutId(ACCESSORY_ID).glass(style, CircleShape).clip(CircleShape), contentAlignment = Alignment.CenterStart) {
                    CompositionLocalProvider(LocalGlassContentColor provides style.onGlass, content = accessory)
                }
            }
        },
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = if (compact) COMPACT_BAR_MARGIN else 12.dp)
            .widthIn(max = FLOATING_MAX_WIDTH)
            .fillMaxWidth(),
    ) { measurables, constraints ->
        val p = state.progress.value
        val w = constraints.maxWidth
        val bar = ((if (compact) COMPACT_BAR_HEIGHT else BAR_HEIGHT) * grow).roundToPx()
        val small = (COLLAPSED_SIZE * grow).roundToPx()
        val gap = ITEM_GAP.roundToPx()
        val accessoryMeasurable = measurables.firstOrNull { it.layoutId == ACCESSORY_ID }
        val acc = if (accessoryMeasurable != null) (ACCESSORY_HEIGHT * grow).roundToPx() else 0
        val total = if (accessoryMeasurable != null) acc + gap + bar else bar
        geometry.fullWidth = w
        geometry.fullHeight = bar
        val barW = lerp(w.toFloat(), small.toFloat(), p).roundToInt()
        val barH = lerp(bar.toFloat(), small.toFloat(), p).roundToInt()
        val barPlaceable = measurables.first { it.layoutId == BAR_ID }.measure(Constraints.fixed(barW, barH))
        val accPlaceable = accessoryMeasurable?.measure(
            Constraints.fixed(
                lerp(w.toFloat(), (w - small - gap).toFloat(), p).roundToInt().coerceAtLeast(0),
                lerp(acc.toFloat(), small.toFloat(), p).roundToInt(),
            ),
        )
        layout(w, total) {
            barPlaceable.placeRelative(0, total - barH)
            accPlaceable?.placeRelative(
                lerp(0f, (small + gap).toFloat(), p).roundToInt(),
                lerp(0f, (total - small).toFloat(), p).roundToInt(),
            )
        }
    }
}

/** 펼친 탭 줄을 측정할 크기(px). 바깥 레이아웃이 매번 정하고, 탭 줄은 탭 바가 줄어드는 동안에도 이 크기로 측정해 글자가 다시 줄 바꿈되지 않게 한다. */
private class FloatingGeometry {
    var fullWidth = 0
    var fullHeight = 0
}

@Composable
private fun ExpandedTabs(
    tabs: List<GlassTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    state: GlassFloatingTabBarState,
    style: GlassStyle,
    geometry: FloatingGeometry,
) {
    var width by remember { mutableIntStateOf(0) }
    val indicator = remember { LiquidIndicator() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LaunchedEffect(selectedIndex, width, tabs.size, rtl) { indicator.moveTo(selectedIndex, tabs.size, width.toFloat(), rtl) }
    Row(
        Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints.fixed(geometry.fullWidth.coerceAtLeast(0), geometry.fullHeight.coerceAtLeast(0)),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.placeRelative(0, (constraints.maxHeight - placeable.height) / 2)
                }
            }
            .graphicsLayer { alpha = (1f - state.progress.value * 2f).coerceIn(0f, 1f) }
            .padding(TAB_INSET)
            .onSizeChanged { width = it.width }
            .drawBehind {
                val left = indicator.left.value
                val right = indicator.right.value
                if (right > left) {
                    drawRoundRect(
                        style.onGlass.copy(alpha = FLOATING_INDICATOR_ALPHA),
                        topLeft = Offset(left, 0f),
                        size = Size(right - left, size.height),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                }
            }
            .then(if (state.isCollapsed) Modifier.clearAndSetSemantics { } else Modifier.selectableGroup()),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEachIndexed { index, tab ->
            FloatingTabItem(tab, index == selectedIndex, style, enabled = !state.isCollapsed, Modifier.weight(1f)) { onSelect(index) }
        }
    }
}

@Composable
private fun FloatingTabItem(
    tab: GlassTab,
    selected: Boolean,
    style: GlassStyle,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) FLOATING_PRESSED_SCALE else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "floating-tab",
    )
    val color = style.onGlass.copy(alpha = if (selected) 1f else FLOATING_UNSELECTED_ALPHA)
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .selectable(
                selected,
                interactionSource = interaction,
                indication = GlassIndication(CircleShape),
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { TabContent(tab, color, selected, compactHeight()) }
}

/** 접힌 캡슐. 선택한 탭의 아이콘(없으면 이름 첫 글자)을 보여 주고, 누르면 펼친다. TalkBack에는 탭마다 이동 동작을 준다. */
@Composable
private fun CollapsedCapsule(
    tabs: List<GlassTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    state: GlassFloatingTabBarState,
    style: GlassStyle,
) {
    val tab = tabs.getOrNull(selectedIndex) ?: tabs.first()
    val collapsedLabel = stringResource(R.string.kinetica_ui_tabbar_collapsed)
    val expandLabel = stringResource(R.string.kinetica_ui_tabbar_expand)
    val actions = tabs.mapIndexed { index, item ->
        CustomAccessibilityAction(stringResource(R.string.kinetica_ui_tabbar_go_to, item.label)) {
            onSelect(index)
            true
        }
    }
    val collapsed = state.isCollapsed
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val shown = (state.progress.value * 2f - 1f).coerceIn(0f, 1f)
                alpha = shown
                scaleX = lerp(CAPSULE_START_SCALE, 1f, shown)
                scaleY = scaleX
            }
            .then(
                if (collapsed) {
                    Modifier
                        .glassClickable(CircleShape, onClickLabel = expandLabel) { state.expand() }
                        .clearAndSetSemantics {
                            role = Role.Button
                            contentDescription = tab.label
                            stateDescription = collapsedLabel
                            onClick(expandLabel) {
                                state.expand()
                                true
                            }
                            customActions = actions
                        }
                } else {
                    Modifier.clearAndSetSemantics { }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val icon = tab.icon
        if (icon != null) {
            Image(
                rememberVectorPainter(icon),
                contentDescription = null,
                Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(style.onGlass),
            )
        } else {
            ThemedText(
                tab.label.take(1),
                style = TextStyle(color = style.onGlass, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

private const val BAR_ID = "bar"
private const val ACCESSORY_ID = "accessory"
private val BAR_HEIGHT = 64.dp
private val COLLAPSED_SIZE = 56.dp
private val ACCESSORY_HEIGHT = 56.dp
private val ITEM_GAP = 8.dp
private val TAB_INSET = 6.dp
private val FLOATING_MAX_WIDTH = 560.dp
private val COLLAPSE_DISTANCE = 32.dp
private const val MAX_GROW = 1.35f
private const val COLLAPSE_DAMPING = 0.82f
private const val CAPSULE_START_SCALE = 0.6f
private const val FLOATING_INDICATOR_ALPHA = 0.12f
private const val FLOATING_UNSELECTED_ALPHA = 0.55f
private const val FLOATING_PRESSED_SCALE = 0.9f
