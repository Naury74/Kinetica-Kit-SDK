package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * [KButtonGroup]과 [KToggleGroup]의 칸 하나. [label]과 [icon] 중 하나는 있어야 한다. 둘 다 null이면 [IllegalArgumentException]을 던진다.
 *
 * @property contentDescription TalkBack 이름. 아이콘만 있는 칸에는 꼭 준다. null이면 [label]을 읽는다.
 * @property enabled `false`면 이 칸만 누를 수 없고 흐리게 그린다.
 */
@Immutable
public data class KGroupItem(
    val label: String? = null,
    val icon: ImageVector? = null,
    val contentDescription: String? = null,
    val enabled: Boolean = true,
) {
    init {
        require(label != null || icon != null) { "label과 icon 중 하나는 있어야 합니다." }
    }
}

/** 버튼 묶음의 배치. */
public enum class KGroupStyle {
    /** 칸을 2dp 간격으로 맞붙이고, 바깥 모서리만 버튼 모양 토큰 반지름으로 둥글게 깎는다. 선택된 칸은 네 모서리가 모두 둥글어진다. */
    Connected,

    /** 버튼 모양 토큰의 버튼을 8dp 간격으로 떨어뜨려 놓는다. */
    Separated,
}

/** [KToggleGroup]의 선택 방식. */
public enum class KGroupSelection {
    /** 하나만 고른다. 고른 칸을 다시 눌러도 해제되지 않는다. TalkBack은 라디오 버튼으로 읽는다. */
    Single,

    /** 여러 칸을 켜고 끈다. TalkBack은 체크박스로 읽는다. */
    Multiple,
}

/**
 * 가로로 늘어놓은 버튼 묶음. 한 칸을 누르고 있으면 그 칸이 넓어지고 양옆 칸이 좁아지며 비켜 준다. 묶음 전체 폭은 바뀌지 않으므로
 * 주변 레이아웃은 움직이지 않는다. `Modifier.fillMaxWidth()`를 주면 칸을 같은 폭으로 나누고, 아니면 칸마다 내용 폭을 쓴다.
 * RTL에서는 첫 칸이 오른쪽에 놓인다. '애니메이션 삭제' 설정에서는 비켜 주는 모션을 끈다.
 *
 * ```kotlin
 * KButtonGroup(listOf(KGroupItem("복사"), KGroupItem("붙여넣기"), KGroupItem("잘라내기")), onClick = { edit(it) })
 * ```
 *
 * @param items 칸. 순서대로 놓는다.
 * @param onClick 누른 칸의 위치(0부터)를 받는다.
 * @param style 칸 배치. 기본값은 [KGroupStyle.Separated]다.
 * @param variant 칸 모양. 기본값은 [KVariant.Tonal]이다.
 */
@Composable
public fun KButtonGroup(
    items: List<KGroupItem>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: KGroupStyle = KGroupStyle.Separated,
    variant: KVariant = KVariant.Tonal,
    tone: KTone = KTone.Accent,
    size: KButtonSize = KButtonSize.Medium,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val colors = tone.colors(surface)
    val ink = buttonInk(variant, tone, colors, surface)
    val haptic = LocalHapticFeedback.current
    val motion = rememberGroupMotion(items.size, null)
    val round = buttonRoundFraction(KineticaTheme.design.density.height(size.height))
    GroupLayout(motion, style, size.cellPadding, modifier) {
        items.forEachIndexed { index, item ->
            val shape = segmentShape(style, index, items.size, round)
            val source = motion.sources[index]
            GroupCell(
                item,
                size,
                { ink },
                Modifier
                    .variantBackground(variant, colors, surface, shape)
                    .clip(shape)
                    .clickable(source, KIndication(shape, ink, PRESS_ALPHA), item.enabled, role = Role.Button) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onClick(index)
                    },
            )
        }
    }
}

/**
 * 고르는 버튼 묶음. 고른 칸은 [tone] 색으로 채워지고, [KGroupStyle.Connected]에서는 안쪽 모서리까지 둥글어져 버튼 하나의 모양이 된다.
 * 누르고 있는 칸은 [KButtonGroup]처럼 넓어지며 이웃이 비켜 준다. 정렬 기준, 보기 방식, 글자 정렬처럼 두세 개 중 고르는 곳에 쓴다.
 *
 * ```kotlin
 * KToggleGroup(listOf(KGroupItem("일"), KGroupItem("주"), KGroupItem("월")), setOf(period), { period = it.first() })
 * ```
 *
 * @param selected 고른 칸의 위치(0부터) 집합. 범위 밖 값은 무시한다.
 * @param onSelectedChange 새 선택 집합을 받는다. [KGroupSelection.Single]이면 늘 원소가 하나다.
 * @param style 칸 배치. 기본값은 [KGroupStyle.Connected]다.
 * @param variant 고르지 않은 칸의 모양. 기본값은 [KVariant.Tonal](옅은 회색 칸)이다.
 */
@Suppress("LongMethod") // 디자인 언어 토큰(모양, 밀도, 굵기)을 읽는 줄이 더해졌을 뿐 한 덩어리 배치라 나누면 오히려 읽기 어렵다.
@Composable
public fun KToggleGroup(
    items: List<KGroupItem>,
    selected: Set<Int>,
    onSelectedChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    selection: KGroupSelection = KGroupSelection.Single,
    style: KGroupStyle = KGroupStyle.Connected,
    variant: KVariant = KVariant.Tonal,
    tone: KTone = KTone.Accent,
    size: KButtonSize = KButtonSize.Medium,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val on = tone.colors(surface)
    val neutral = KTone.Neutral.colors(surface)
    val offInk = buttonInk(variant, KTone.Neutral, neutral, surface)
    val haptic = LocalHapticFeedback.current
    val motion = rememberGroupMotion(items.size, selected)
    val single = selection == KGroupSelection.Single
    val round = buttonRoundFraction(KineticaTheme.design.density.height(size.height))
    GroupLayout(motion, style, size.cellPadding, modifier.then(if (single) Modifier.selectableGroup() else Modifier)) {
        items.forEachIndexed { index, item ->
            val shape = segmentShape(style, index, items.size, round)
            val picked = index in selected
            val level = motion.selected[index]
            val change = {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSelectedChange(nextGroupSelection(selection, selected, index, items.size))
            }
            val pick = if (single) {
                Modifier.selectable(
                    picked,
                    motion.sources[index],
                    KIndication(shape, offInk, PRESS_ALPHA),
                    item.enabled,
                    Role.RadioButton,
                    change,
                )
            } else {
                Modifier.toggleable(picked, motion.sources[index], KIndication(shape, offInk, PRESS_ALPHA), item.enabled, Role.Checkbox) {
                    change()
                }
            }
            GroupCell(
                item,
                size,
                { lerp(offInk, on.onColor, level.value.coerceIn(0f, 1f)) },
                Modifier
                    // 표면 모양 변형만 정해진 모양으로 칠하고, 나머지는 고를 때 모서리가 함께 둥글어지도록 직접 그린다.
                    .then(if (variant.surfaceDrawn) Modifier.variantBackground(variant, neutral, surface, shape) else Modifier)
                    .selectedFill(
                        SegmentPaint(
                            style,
                            index,
                            items.size,
                            on.color,
                            round,
                            if (variant.surfaceDrawn) {
                                null
                            } else {
                                variant to
                                    neutral.color
                            },
                        ),
                    ) {
                        level.value
                    }
                    .clip(shape)
                    .then(pick),
            )
        }
    }
}

/** 칸을 눌렀을 때의 새 선택 집합. 범위 밖 위치는 걸러 낸다. */
internal fun nextGroupSelection(selection: KGroupSelection, selected: Set<Int>, index: Int, count: Int): Set<Int> {
    val valid = selected.filterTo(LinkedHashSet()) { it in 0 until count }
    return when (selection) {
        KGroupSelection.Single -> setOf(index)
        KGroupSelection.Multiple -> if (index in valid) valid - index else valid + index
    }
}

/** 칸마다 눌림에 따라 넓어지는 정도([grow])와 선택된 정도([selected]), 눌림을 받을 [sources]. */
private class GroupMotion(
    val sources: List<MutableInteractionSource>,
    val grow: List<Animatable<Float, AnimationVector1D>>,
    val selected: List<Animatable<Float, AnimationVector1D>>,
)

@Composable
private fun rememberGroupMotion(count: Int, selected: Set<Int>?): GroupMotion {
    val motion = remember(count) {
        GroupMotion(
            List(count) { MutableInteractionSource() },
            List(count) { Animatable(0f) },
            List(count) { Animatable(if (selected != null && it in selected) 1f else 0f) },
        )
    }
    val still = reducedMotion()
    motion.sources.forEachIndexed { index, source ->
        LaunchedEffect(source, still) {
            source.interactions.collect { interaction ->
                val target = when (interaction) {
                    is PressInteraction.Press -> 1f
                    is PressInteraction.Release, is PressInteraction.Cancel -> 0f
                    else -> return@collect
                }
                if (still) return@collect
                motion.grow[index].animateTo(target, spring(dampingRatio = GROW_DAMPING, stiffness = GROW_STIFFNESS))
            }
        }
    }
    if (selected != null) {
        motion.selected.forEachIndexed { index, level ->
            val target = if (index in selected) 1f else 0f
            LaunchedEffect(target, still) {
                if (still) {
                    level.snapTo(
                        target,
                    )
                } else {
                    level.animateTo(target, spring(dampingRatio = PICK_DAMPING, stiffness = PICK_STIFFNESS))
                }
            }
        }
    }
    return motion
}

/**
 * 칸을 가로로 놓는다. 고정 폭이면 같은 폭으로 나누고, 아니면 내용 폭을 쓴다. 누른 칸은 넓어지고 양옆 칸이 그만큼 좁아진다.
 * 넓어지는 정도는 측정 단계에서 읽으므로 눌림 애니메이션 동안 재구성하지 않는다.
 */
@Composable
private fun GroupLayout(motion: GroupMotion, style: KGroupStyle, pad: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    val gap = if (style == KGroupStyle.Connected) CONNECTED_GAP else SEPARATED_GAP
    Layout(content, modifier) { measurables, constraints ->
        val n = measurables.size
        if (n == 0) return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        val gapPx = gap.roundToPx()
        val fixed = constraints.hasBoundedWidth && constraints.minWidth == constraints.maxWidth
        val widths = FloatArray(n) { i ->
            if (fixed) {
                (constraints.maxWidth - gapPx * (n - 1)).toFloat() / n
            } else {
                // 칸 안 여백은 내용 바깥에 따로 더한다. 이웃이 비켜 줄 때 여백만 줄어 글자가 잘리지 않는다.
                measurables[i].maxIntrinsicWidth(Constraints.Infinity) + pad.toPx() * 2
            }
        }
        yieldToPressed(widths, motion, GROW_MAX.toPx())
        var edge = 0f
        val placeables = measurables.mapIndexed { i, m ->
            // 칸마다 따로 반올림하면 합이 1px씩 흔들리므로 누적 경계를 반올림해 전체 폭을 고정한다.
            val start = edge.roundToInt()
            edge += widths[i]
            val w = (edge.roundToInt() - start).coerceAtLeast(0)
            m.measure(Constraints(w, w, 0, constraints.maxHeight))
        }
        val height = placeables.maxOf { it.height }
        val total = placeables.sumOf { it.width } + gapPx * (n - 1)
        layout(total.coerceIn(constraints.minWidth, constraints.maxWidth), height) {
            var x = 0
            placeables.forEach {
                it.placeRelative(x, (height - it.height) / 2)
                x += it.width + gapPx
            }
        }
    }
}

/** 누른 칸을 넓히고 그만큼 양옆 칸을 줄인다. 이웃이 한쪽뿐이면 그 칸이 모두 줄어든다. 합은 바뀌지 않는다. */
private fun yieldToPressed(widths: FloatArray, motion: GroupMotion, maxGrow: Float) {
    val n = widths.size
    if (n < 2) return
    val base = widths.copyOf()
    for (i in 0 until n) {
        val extra = (motion.grow.getOrNull(i)?.value ?: 0f) * minOf(base[i] * GROW_RATIO, maxGrow)
        if (extra == 0f) continue
        widths[i] += extra
        when {
            i > 0 && i < n - 1 -> {
                widths[i - 1] -= extra / 2
                widths[i + 1] -= extra / 2
            }

            i > 0 -> widths[i - 1] -= extra

            else -> widths[i + 1] -= extra
        }
    }
}

/** 칸 하나의 내용. 글자와 아이콘 색은 그리기 단계에서 [ink]로 읽는다. */
@Composable
private fun GroupCell(item: KGroupItem, size: KButtonSize, ink: () -> Color, modifier: Modifier) {
    Row(
        modifier
            .height(KineticaTheme.design.density.height(size.height))
            .then(if (item.contentDescription != null) Modifier.semantics { contentDescription = item.contentDescription } else Modifier)
            .graphicsLayer { alpha = if (item.enabled) 1f else DISABLED },
        horizontalArrangement = Arrangement.spacedBy(size.gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.icon?.let { icon ->
            val painter = rememberVectorPainter(icon)
            Box(
                Modifier.size(size.icon).drawBehind {
                    with(painter) { draw(this@drawBehind.size, colorFilter = ColorFilter.tint(ink())) }
                },
            )
        }
        item.label?.let {
            ThemedText(
                it,
                style = TextStyle(fontSize = size.font, fontWeight = KineticaTheme.design.label(FontWeight.SemiBold)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = { ink() },
            )
        }
    }
}

/** 이어 붙인 묶음에서 칸 위치에 맞는 모양. 첫 칸과 끝 칸의 바깥 모서리만 버튼 토큰 반지름([round], 높이 비율)으로 둥글다. */
private fun segmentShape(style: KGroupStyle, index: Int, count: Int, round: Float): SideRoundShape {
    val inner = minOf(INNER, round)
    return when (style) {
        KGroupStyle.Separated -> SideRoundShape(round, round)
        KGroupStyle.Connected -> SideRoundShape(if (index == 0) round else inner, if (index == count - 1) round else inner)
    }
}

/** 고르는 칸을 그리는 값. [base]가 있으면 고르지 않은 바탕(모양 변형과 색)도 같은 모양으로 그린다. */
private class SegmentPaint(
    val style: KGroupStyle,
    val index: Int,
    val count: Int,
    val color: Color,
    val round: Float,
    val base: Pair<KVariant, Color>?,
)

/** 고르지 않은 칸 바탕을 [level]만큼 그린다. 고를수록 옅어져 채운 색 아래로 사라진다. */
private fun DrawScope.drawSegmentBase(path: Path, variant: KVariant, ink: Color, level: Float) {
    when (variant) {
        KVariant.Outlined -> drawPath(path, ink.copy(alpha = OUTLINE_ALPHA * level), style = Stroke(OUTLINE.toPx() * 2))
        KVariant.Text -> Unit
        else -> drawPath(path, ink.copy(alpha = TONAL_ALPHA * level))
    }
}

/** 표면 스타일로 그려야 해서 정해진 모양을 쓰는 변형인지 */
private val KVariant.surfaceDrawn: Boolean get() = this == KVariant.Glass || this == KVariant.Elevated

/**
 * 선택된 정도만큼 칸을 [SegmentPaint.color]로 채운다. 이어 붙인 묶음에서는 채워지면서 안쪽 모서리도 둥글어진다.
 * 모양은 그리기 단계에서 계산하므로 선택 애니메이션 동안 재구성하지 않는다.
 */
@Composable
private fun Modifier.selectedFill(paint: SegmentPaint, level: () -> Float): Modifier {
    val path = remember { Path() }
    val round = paint.round
    val inner = minOf(INNER, round)
    val startBase = if (paint.style == KGroupStyle.Separated || paint.index == 0) round else inner
    val endBase = if (paint.style == KGroupStyle.Separated || paint.index == paint.count - 1) round else inner
    return drawBehind {
        val v = level().coerceIn(0f, 1f)
        val base = paint.base
        if (v <= 0f && base == null) return@drawBehind
        val h = size.height
        val start = CornerRadius(h * (startBase + (round - startBase) * v))
        val end = CornerRadius(h * (endBase + (round - endBase) * v))
        val rtl = layoutDirection == LayoutDirection.Rtl
        val left = if (rtl) end else start
        val right = if (rtl) start else end
        path.rewind()
        path.addRoundRect(RoundRect(0f, 0f, size.width, h, left, right, right, left))
        if (base != null && v < 1f) drawSegmentBase(path, base.first, base.second, 1f - v)
        if (v > 0f) drawPath(path, paint.color.copy(alpha = v))
    }
}

private val CONNECTED_GAP: Dp = 2.dp
private val OUTLINE: Dp = 1.dp
private const val OUTLINE_ALPHA = 0.4f
private const val TONAL_ALPHA = 0.12f
private val SEPARATED_GAP: Dp = 8.dp
private val GROW_MAX: Dp = 14.dp
private val CELL_TRIM: Dp = 4.dp

/** 칸 안 좌우 여백. 아이콘만 있는 칸은 정사각형에 가깝게 줄인다. */
private val KButtonSize.cellPadding: Dp get() = padding - CELL_TRIM
private const val GROW_RATIO = 0.15f
private const val INNER = 0.16f
private const val DISABLED = 0.4f
private const val PRESS_ALPHA = 0.12f
private const val GROW_DAMPING = 0.6f
private const val GROW_STIFFNESS = 600f
private const val PICK_DAMPING = 0.75f
private const val PICK_STIFFNESS = 500f
