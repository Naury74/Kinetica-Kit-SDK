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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
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
    Box(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(TAB_BAR_HEIGHT)
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
    Column(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .selectable(selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        tab.icon?.let {
            Image(rememberVectorPainter(it), contentDescription = null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(color))
        }
        BasicText(
            tab.label,
            style = TextStyle(color = color, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
        )
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

/** 화면 위쪽에서 미끄러져 내려오는 글래스 토스트. 컴포지션 어디에 두어도 되고, [GlassToastState.show]로 띄운다. TalkBack이 내용을 읽어 준다. */
@Composable
public fun GlassToastHost(state: GlassToastState, style: GlassStyle = LocalGlassStyle.current) {
    var last by remember { mutableStateOf("") }
    state.message?.let { last = it }
    GlassOverlay(state.message != null) { transition ->
        val progress by transition.animateFloat(
            transitionSpec = {
                if (targetState) spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow) else tween(TOAST_OUT_MS)
            },
            label = "glass-toast",
        ) { if (it) 1f else 0f }
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .padding(top = 8.dp)
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
                BasicText(last, style = TextStyle(color = style.onGlass, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

private val TAB_BAR_HEIGHT = 64.dp
private val TAB_INSET = 6.dp
private const val INDICATOR_ALPHA = 0.12f
private const val UNSELECTED_ALPHA = 0.55f
private const val PRESSED_SCALE = 0.9f
private const val DEFAULT_TOAST_MS = 2_200L
private const val TOAST_GAP_MS = 250L
private const val TOAST_OUT_MS = 200
private val TOAST_TRAVEL = 24.dp
private const val TOAST_START_SCALE = 0.9f
