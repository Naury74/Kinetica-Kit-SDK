package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** [GlassTabBar]의 탭 항목. [icon]이 없으면 텍스트만 표시한다. */
@Immutable
public data class GlassTab(val label: String, val icon: ImageVector? = null)

/**
 * 화면 아래에 떠 있는 글래스 탭 바. 선택한 탭의 인디케이터가 이동할 때 앞쪽 가장자리가 먼저 가고 뒤쪽이 따라와,
 * 액체처럼 늘어났다 줄어든다. 탭을 누르면 살짝 작아졌다가 돌아온다. [GlassScaffold]의 `bottomBar`에 넣어 쓴다.
 * 폭은 최대 560dp이고, 더 넓은 화면에서는 가운데에 놓인다.
 *
 * @param tabs 탭 목록. 비어 있으면 [IllegalArgumentException]을 던진다.
 * @param selectedIndex 선택된 탭의 위치(0부터)
 */
@Composable
public fun GlassTabBar(
    tabs: List<GlassTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: GlassStyle = LocalGlassStyle.current,
) {
    require(tabs.isNotEmpty()) { "tabs가 비어 있으면 안 됩니다." }
    var width by remember { mutableIntStateOf(0) }
    val indicator = remember { LiquidIndicator() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LaunchedEffect(selectedIndex, width, tabs.size, rtl) { indicator.moveTo(selectedIndex, tabs.size, width.toFloat(), rtl) }
    val compact = compactHeight()
    Box(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = if (compact) COMPACT_BAR_MARGIN else 12.dp)
            // 태블릿처럼 넓은 화면에서 탭 사이가 너무 벌어지지 않게 폭을 제한한다.
            .widthIn(max = TAB_BAR_MAX_WIDTH)
            .fillMaxWidth()
            .height(if (compact) COMPACT_BAR_HEIGHT else TAB_BAR_HEIGHT)
            .glass(style, CircleShape)
            .padding(TAB_INSET),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { width = it.width }
                .drawBehind {
                    val left = indicator.left.value
                    val right = indicator.right.value
                    if (right > left) {
                        drawRoundRect(
                            style.onGlass.copy(alpha = INDICATOR_ALPHA),
                            topLeft = Offset(left, 0f),
                            size = Size(right - left, size.height),
                            cornerRadius = CornerRadius(size.height / 2),
                        )
                    }
                },
        )
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
            tabs.forEachIndexed { index, tab -> TabItem(tab, index == selectedIndex, style, Modifier.weight(1f)) { onSelect(index) } }
        }
    }
}

@Composable
private fun TabItem(tab: GlassTab, selected: Boolean, style: GlassStyle, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) PRESSED_SCALE else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tab",
    )
    val color = style.onGlass.copy(alpha = if (selected) 1f else UNSELECTED_ALPHA)
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .selectable(selected, interaction, GlassIndication(CircleShape), role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { TabContent(tab, color, selected, compactHeight()) }
}

/** 가로 화면처럼 세로가 낮은 창인지. 이때 탭 바는 낮아지고 아이콘과 이름을 옆으로 놓는다. */
@Composable
internal fun compactHeight(): Boolean = LocalConfiguration.current.screenHeightDp < COMPACT_HEIGHT_DP

/** 탭 하나의 아이콘과 이름. 보통은 위아래로, [compact]면 iOS 가로 화면처럼 옆으로 놓는다. */
@Composable
internal fun TabContent(tab: GlassTab, color: Color, selected: Boolean, compact: Boolean) {
    val icon = tab.icon
    val weight = if (selected) FontWeight.SemiBold else FontWeight.Medium
    if (compact && icon != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                rememberVectorPainter(icon),
                contentDescription = null,
                Modifier.size(COMPACT_TAB_ICON),
                colorFilter = ColorFilter.tint(color),
            )
            Spacer(Modifier.width(COMPACT_TAB_GAP))
            ThemedText(tab.label, style = TextStyle(color = color, fontSize = 13.sp, fontWeight = weight), maxLines = 1)
        }
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        icon?.let {
            Image(rememberVectorPainter(it), contentDescription = null, Modifier.size(TAB_ICON), colorFilter = ColorFilter.tint(color))
            Spacer(Modifier.height(GlassMetrics.TabIconGap))
        }
        ThemedText(tab.label, style = TextStyle(color = color, fontSize = 11.sp, fontWeight = weight), maxLines = 1)
    }
}

/** [GlassToastHost]에 토스트를 띄우는 상태. 여러 개를 요청하면 한 번에 하나씩 순서대로 보여 준다. */
@Stable
public class GlassToastState {
    internal var message by mutableStateOf<String?>(null)
    private val queue = Mutex()

    /** [text]를 [durationMs](ms) 동안 보여 주고, 토스트가 사라질 때까지 일시 중단한다. 앞선 토스트가 있으면 그 뒤에 보여 준다. */
    public suspend fun show(text: String, durationMs: Long = DEFAULT_TOAST_MS) {
        queue.withLock {
            message = text
            delay(durationMs)
            message = null
            delay(TOAST_GAP_MS)
        }
    }
}

@Composable
public fun rememberGlassToastState(): GlassToastState = remember { GlassToastState() }

/**
 * 화면 위쪽에서 미끄러져 내려오는 글래스 토스트. 컴포지션 어디에 두어도 되고, [GlassToastState.show]로 띄운다. TalkBack이 내용을 읽어 준다.
 * 상태 표시줄과 화면 컷아웃 아래에 뜬다. [GlassScaffold] 밖에서는 내용 크기의 팝업 창에 띄우므로 뒤 화면의 터치를 막지 않는다.
 *
 * @param modifier 토스트 말풍선에 적용한다.
 */
@Composable
public fun GlassToastHost(state: GlassToastState, modifier: Modifier = Modifier, style: GlassStyle = LocalGlassStyle.current) {
    var last by remember { mutableStateOf("") }
    state.message?.let { last = it }
    // GlassScaffold에 상단 바가 있으면 바 제목을 가리지 않게 바 아래에 띄운다. 바 높이에 상태 표시줄이 이미 들어 있다.
    val topBar = LocalGlassHost.current?.topBarPx ?: 0
    val insets = if (topBar > 0) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).add(WindowInsets(top = topBar))
    } else {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    }
    GlassOverlay(state.message != null, OverlayKind.Top, insets) { transition ->
        val progress by transition.animateFloat(
            transitionSpec = {
                if (targetState) spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow) else tween(TOAST_OUT_MS)
            },
            label = "glass-toast",
        ) { if (it) 1f else 0f }
        Box(
            Modifier
                .padding(top = 8.dp)
                .then(modifier)
                .graphicsLayer {
                    translationY = -(size.height + TOAST_TRAVEL.toPx()) * (1f - progress)
                    // 내려오는 동안 가로로 살짝 좁았다가 원래 너비로 돌아온다.
                    scaleX = TOAST_START_SCALE + (1f - TOAST_START_SCALE) * progress
                    alpha = progress.coerceIn(0f, 1f)
                }
                .glass(style, CircleShape)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            ThemedText(last, style = TextStyle(color = style.onGlass, fontSize = 14.sp, fontWeight = FontWeight.Medium))
        }
    }
}

private val TAB_BAR_HEIGHT = 64.dp
private val TAB_BAR_MAX_WIDTH = 560.dp
private val TAB_INSET = 6.dp
private val TAB_ICON = 22.dp
private val COMPACT_TAB_ICON = 20.dp
private val COMPACT_TAB_GAP = 6.dp
internal val COMPACT_BAR_HEIGHT = 48.dp
internal val COMPACT_BAR_MARGIN = 6.dp
private const val COMPACT_HEIGHT_DP = 480
private const val INDICATOR_ALPHA = 0.12f
private const val UNSELECTED_ALPHA = 0.55f
private const val PRESSED_SCALE = 0.9f
private const val DEFAULT_TOAST_MS = 2_200L
private const val TOAST_GAP_MS = 250L
private const val TOAST_OUT_MS = 200
private val TOAST_TRAVEL = 24.dp
private const val TOAST_START_SCALE = 0.9f
