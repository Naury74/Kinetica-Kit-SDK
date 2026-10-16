// 파일 이름은 대표 컴포저블인 KFab를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * [KFab]의 크기. 지름과 아이콘 크기, 모서리 반지름이 함께 바뀐다.
 * Small은 40dp, Regular는 56dp, Large는 96dp다.
 */
public enum class KFabSize(internal val size: Dp, internal val icon: Dp, internal val radius: Dp) {
    Small(40.dp, 20.dp, 12.dp),
    Regular(56.dp, 24.dp, 16.dp),
    Large(96.dp, 36.dp, 28.dp),
}

/**
 * 화면 구석에 떠 있는 주 동작 버튼. 기본 모양은 둥근 사각형이고 `shape = CircleShape`로 원을 쓸 수 있다.
 * [KVariant.Filled]와 [KVariant.Tonal]은 떠 보이도록 그림자를 드리우고, [KVariant.Glass]와 [KVariant.Elevated]는 표면 그림자를 쓴다.
 * 누르면 젤리처럼 눌리고 햅틱을 준다. 위치는 호출한 쪽이 정하며, 내비게이션 바 위에 두려면 `navigationBarsPadding()`을 붙인다.
 * 펼쳐지는 동작 목록이 필요하면 [KSpeedDial]을 쓴다.
 *
 * ```kotlin
 * KFab(Icons.Add, "새 메모", onClick = ::compose, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp))
 * ```
 *
 * @param contentDescription TalkBack이 읽을 이름. 아이콘만 있는 버튼이라 null을 받지 않는다.
 */
@Composable
public fun KFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: KFabSize = KFabSize.Regular,
    variant: KVariant = KVariant.Filled,
    tone: KTone = KTone.Accent,
    shape: Shape = RoundedCornerShape(size.radius),
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val colors = tone.colors(surface)
    val ink = buttonInk(variant, tone, colors, surface)
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .jellyPress(JELLY)
            .size(size.size)
            .fabBackground(variant, colors, surface, shape)
            .clip(shape)
            .clickable(null, KIndication(shape, ink, PRESS_ALPHA), role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Image(rememberVectorPainter(icon), null, Modifier.size(size.icon), colorFilter = ColorFilter.tint(ink))
    }
}

/**
 * 아이콘과 글자가 함께 있는 떠 있는 버튼. [expanded]가 `false`가 되면 글자가 사라지며 아이콘만 남은 56dp 버튼으로 줄어든다.
 * 줄어드는 동안 끝 쪽(LTR이면 오른쪽) 가장자리를 기준으로 접히도록 부모에서 끝 쪽에 정렬해 두면 자연스럽다.
 * 목록을 내리면 접고 올리면 펼치려면 [rememberFabExpanded]를 넘긴다. 접혀도 TalkBack은 [text]를 읽는다.
 *
 * ```kotlin
 * val list = rememberLazyListState()
 * KExtendedFab("새 메일", Icons.Edit, onClick = ::compose, expanded = rememberFabExpanded(list))
 * ```
 */
@Composable
public fun KExtendedFab(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    variant: KVariant = KVariant.Filled,
    tone: KTone = KTone.Accent,
    shape: Shape = RoundedCornerShape(KFabSize.Regular.radius),
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val colors = tone.colors(surface)
    val ink = buttonInk(variant, tone, colors, surface)
    val haptic = LocalHapticFeedback.current
    val open = rememberExpandLevel(expanded)
    val collapsed = with(LocalDensity.current) { KFabSize.Regular.size.roundToPx() }
    Box(
        modifier
            .jellyPress(JELLY)
            .height(KFabSize.Regular.size)
            .fabBackground(variant, colors, surface, shape)
            .clip(shape)
            .clickable(null, KIndication(shape, ink, PRESS_ALPHA), role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .layout { measurable, constraints ->
                    // 글자까지 잰 폭과 아이콘만 남은 폭 사이를 레이아웃 단계에서 보간해, 접히는 동안 재구성하지 않는다.
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Int.MAX_VALUE))
                    val full = maxOf(placeable.width, collapsed)
                    val width = (collapsed + (full - collapsed) * open.value.coerceIn(0f, 1f)).roundToInt()
                        .coerceIn(constraints.minWidth, maxOf(constraints.minWidth, constraints.maxWidth))
                    layout(width, placeable.height) { placeable.placeRelative(0, 0) }
                }
                .padding(start = ICON_INSET, end = TEXT_END),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(rememberVectorPainter(icon), null, Modifier.size(KFabSize.Regular.icon), colorFilter = ColorFilter.tint(ink))
            Box(Modifier.width(KMetrics.IconTextGap + GAP_EXTRA))
            ThemedText(
                text,
                Modifier.graphicsLayer { alpha = ((open.value - TEXT_FADE) / (1f - TEXT_FADE)).coerceIn(0f, 1f) },
                style = TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
        }
    }
}

/**
 * 목록을 내리면 `false`, 올리거나 맨 위에 있으면 `true`를 돌려준다. [KExtendedFab]의 `expanded`에 넘긴다.
 * 스크롤 위치는 코루틴에서 지켜보므로 스크롤하는 동안 재구성하지 않고, 값이 바뀔 때만 재구성한다.
 */
@Composable
public fun rememberFabExpanded(state: LazyListState): Boolean {
    var expanded by remember(state) { mutableStateOf(true) }
    val slop = with(LocalDensity.current) { SCROLL_SLOP.toPx() }
    LaunchedEffect(state) {
        var lastIndex = state.firstVisibleItemIndex
        var lastOffset = state.firstVisibleItemScrollOffset
        // 위치와 오프셋을 Long 하나에 담아 프레임마다 Pair를 만들지 않는다.
        snapshotFlow { state.firstVisibleItemIndex.toLong() shl INDEX_SHIFT or state.firstVisibleItemScrollOffset.toLong() }
            .collect { packed ->
                val index = (packed shr INDEX_SHIFT).toInt()
                val offset = (packed and OFFSET_MASK).toInt()
                val delta = if (index == lastIndex) offset - lastOffset else (index - lastIndex) * slop.toInt() * 2
                when {
                    index == 0 && offset == 0 -> expanded = true
                    delta > slop -> expanded = false
                    delta < -slop -> expanded = true
                    else -> return@collect
                }
                lastIndex = index
                lastOffset = offset
            }
    }
    return expanded
}

/** [ScrollState]를 쓰는 화면용 [rememberFabExpanded]. */
@Composable
public fun rememberFabExpanded(state: ScrollState): Boolean {
    var expanded by remember(state) { mutableStateOf(true) }
    val slop = with(LocalDensity.current) { SCROLL_SLOP.toPx() }
    LaunchedEffect(state) {
        var last = state.value
        snapshotFlow { state.value }.collect { value ->
            when {
                value == 0 -> expanded = true
                value - last > slop -> expanded = false
                last - value > slop -> expanded = true
                else -> return@collect
            }
            last = value
        }
    }
    return expanded
}

/** 떠 있는 버튼 배경. 채운 모양은 표면 그림자가 없으므로 따로 그림자를 드리운다. */
@Composable
private fun Modifier.fabBackground(variant: KVariant, colors: ToneColors, surface: SurfaceStyle, shape: Shape): Modifier {
    val lifted = variant == KVariant.Filled || variant == KVariant.Tonal
    val shadowColor = if (surface.isDark) Color.Black else colors.color.copy(alpha = SHADOW_TINT)
    return this
        .then(if (lifted) Modifier.shadow(FAB_SHADOW, shape, ambientColor = shadowColor, spotColor = shadowColor) else Modifier)
        // 옅은 톤은 그림자가 비쳐 탁해 보이므로 표면 색을 먼저 깐다.
        .then(if (variant == KVariant.Tonal) Modifier.kSurface(surface.withoutShadow(), shape) else Modifier)
        .variantBackground(if (variant == KVariant.Elevated) KVariant.Elevated else variant, colors, surface, shape)
}

@Composable
private fun rememberExpandLevel(expanded: Boolean): Animatable<Float, *> {
    val level = remember { Animatable(if (expanded) 1f else 0f) }
    val still = reducedMotion()
    LaunchedEffect(expanded, still) {
        val target = if (expanded) 1f else 0f
        if (still) level.snapTo(target) else level.animateTo(target, spring(dampingRatio = EXPAND_DAMPING, stiffness = EXPAND_STIFFNESS))
    }
    return level
}

private val FAB_SHADOW = 8.dp
private val ICON_INSET = 16.dp
private val TEXT_END = 20.dp
private val GAP_EXTRA = 4.dp
private val SCROLL_SLOP = 8.dp
private const val SHADOW_TINT = 0.6f
private const val JELLY = 0.06f
private const val PRESS_ALPHA = 0.12f
private const val TEXT_FADE = 0.4f
private const val EXPAND_DAMPING = 0.85f
private const val EXPAND_STIFFNESS = 420f
private const val INDEX_SHIFT = 32
private const val OFFSET_MASK = 0xFFFFFFFFL
