package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** [KChipGroup]의 선택 방식. */
public enum class KChipSelection {
    /** 하나만 고른다. 다른 칩을 누르면 선택이 옮겨 가고, 고른 칩을 다시 눌러도 해제되지 않는다. TalkBack은 라디오 버튼으로 읽는다. */
    Single,

    /** 여러 개를 고르고, 누를 때마다 켜고 끈다. TalkBack은 체크박스로 읽는다. */
    Multiple,
}

/**
 * 필터나 태그 선택에 쓰는 칩 묶음. 폭이 모자라면 다음 줄로 넘긴다.
 * 선택된 칩은 강조색으로 물들고, 앞쪽에 체크 표시가 그려지며 나타나는 동안 칩 폭이 부드럽게 늘어난다. 누르면 햅틱을 준다.
 *
 * ```kotlin
 * KChipGroup(listOf("전체", "사진", "동영상"), selected, { selected = it })
 * ```
 *
 * @param chips 칩 이름. 순서대로 놓는다.
 * @param selected 선택된 칩의 위치(0부터) 집합. [chips] 범위 밖의 값은 무시한다.
 * @param onSelectedChange 새 선택 집합을 받는다. [KChipSelection.Single]이면 항상 원소가 하나다.
 * @param enabled `false`면 누를 수 없고 흐리게 그린다.
 * @param variant 선택되지 않은 칩의 모양. 기본값 [KVariant.Glass]는 [surface]로 그리고, [KVariant.Tonal]은 옅은 회색 칩,
 *   [KVariant.Outlined]는 테두리 칩이다. [KVariant.Filled]는 [KVariant.Tonal]과 같게 그린다.
 * @param tone 선택된 칩을 물들일 색 역할. 기본값은 [KTone.Accent]다.
 */
@Composable
public fun KChipGroup(
    chips: List<String>,
    selected: Set<Int>,
    onSelectedChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    selection: KChipSelection = KChipSelection.Single,
    enabled: Boolean = true,
    variant: KVariant = KVariant.Glass,
    tone: KTone = KTone.Accent,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val accent = tone.colors(surface).color
    val haptic = LocalHapticFeedback.current
    FlowRow(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .then(if (selection == KChipSelection.Single) Modifier.selectableGroup() else Modifier),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        // 칩은 채운 모양이 선택 표시와 겹치므로 Filled는 Tonal로 그린다.
        val chipVariant = if (variant == KVariant.Filled) KVariant.Tonal else variant
        chips.forEachIndexed { index, label ->
            val isSelected = index in selected
            val onTap = {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSelectedChange(nextSelection(selection, selected, index, chips.size))
            }
            KChip(label, ChipLook(isSelected, selection, enabled, accent, surface, chipVariant), onTap)
        }
    }
}

/** 칩을 눌렀을 때의 새 선택 집합. 범위 밖 위치는 걸러 낸다. */
internal fun nextSelection(selection: KChipSelection, selected: Set<Int>, index: Int, count: Int): Set<Int> {
    val valid = selected.filterTo(LinkedHashSet()) { it in 0 until count }
    return when (selection) {
        KChipSelection.Single -> setOf(index)
        KChipSelection.Multiple -> if (index in valid) valid - index else valid + index
    }
}

private class ChipLook(
    val selected: Boolean,
    val selection: KChipSelection,
    val enabled: Boolean,
    val accent: Color,
    val surface: SurfaceStyle,
    val variant: KVariant,
)

@Composable
private fun KChip(label: String, look: ChipLook, onTap: () -> Unit) {
    val shown = remember { Animatable(if (look.selected) 1f else 0f) }
    LaunchedEffect(look.selected) {
        shown.animateTo(if (look.selected) 1f else 0f, spring(dampingRatio = CHIP_DAMPING, stiffness = Spring.StiffnessMediumLow))
    }
    val interaction = remember { MutableInteractionSource() }
    val accent = look.accent
    val ink = look.surface.contentColor
    val pick = when (look.selection) {
        KChipSelection.Single ->
            Modifier.selectable(look.selected, interaction, KIndication(CircleShape), look.enabled, Role.RadioButton, onTap)

        KChipSelection.Multiple ->
            Modifier.toggleable(look.selected, interaction, KIndication(CircleShape), look.enabled, Role.Checkbox) { onTap() }
    }
    Row(
        Modifier
            .height(CHIP_HEIGHT)
            .variantBackground(look.variant, KTone.Neutral.colors(look.surface), look.surface, CircleShape)
            .drawBehind {
                val level = shown.value.coerceIn(0f, 1f)
                if (level > 0f) {
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(accent.copy(alpha = SELECTED_FILL * level), cornerRadius = radius)
                    val stroke = BORDER.toPx()
                    drawRoundRect(
                        accent.copy(alpha = SELECTED_BORDER * level),
                        Offset(stroke / 2, stroke / 2),
                        size.copy(size.width - stroke, size.height - stroke),
                        radius,
                        Stroke(stroke),
                    )
                }
            }
            .then(pick)
            .padding(horizontal = CHIP_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .checkSlot { shown.value }
                .drawBehind { drawCheck(accent, shown.value) },
        )
        ThemedText(
            label,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = { lerp(ink, accent, shown.value.coerceIn(0f, 1f)) },
            maxLines = 1,
        )
    }
}

/** 체크 표시 자리. 나타난 정도에 맞춰 폭이 0에서 아이콘과 간격을 더한 폭까지 늘어난다. 레이아웃 단계에서 읽으므로 재구성하지 않는다. */
private fun Modifier.checkSlot(level: () -> Float): Modifier = layout { measurable, constraints ->
    val icon = CHECK_SIZE.roundToPx()
    val full = icon + CHECK_GAP.roundToPx()
    val width = (full * level().coerceAtLeast(0f)).roundToInt()
    val placeable = measurable.measure(constraints.copy(minWidth = icon, maxWidth = icon, minHeight = icon, maxHeight = icon))
    layout(width, icon) { placeable.placeRelative(0, 0) }
}

/** 체크 표시를 [level]만큼 그린다. 짧은 획을 먼저 긋고 긴 획을 이어 그어, 펜으로 쓰듯 나타난다. RTL에서도 모양은 그대로 둔다. */
private fun DrawScope.drawCheck(color: Color, level: Float) {
    if (level <= 0f) return
    val w = size.width
    val h = size.height
    val start = Offset(w * CHECK_X0, h * CHECK_Y0)
    val corner = Offset(w * CHECK_X1, h * CHECK_Y1)
    val end = Offset(w * CHECK_X2, h * CHECK_Y2)
    val stroke = CHECK_STROKE.toPx()
    val first = (level / CHECK_SPLIT).coerceIn(0f, 1f)
    val second = ((level - CHECK_SPLIT) / (1f - CHECK_SPLIT)).coerceIn(0f, 1f)
    drawLine(color, start, start + (corner - start) * first, stroke, StrokeCap.Round)
    if (second > 0f) drawLine(color, corner, corner + (end - corner) * second, stroke, StrokeCap.Round)
}

private val CHIP_HEIGHT = 34.dp
private val CHIP_PADDING = 14.dp
private val CHIP_GAP = 8.dp
private val CHECK_SIZE = 16.dp
private val CHECK_GAP = 6.dp
private val CHECK_STROKE = 2.dp
private val BORDER = 1.dp
private const val CHECK_X0 = 0.15f
private const val CHECK_Y0 = 0.52f
private const val CHECK_X1 = 0.4f
private const val CHECK_Y1 = 0.76f
private const val CHECK_X2 = 0.86f
private const val CHECK_Y2 = 0.26f
private const val CHECK_SPLIT = 0.35f
private const val CHIP_DAMPING = 0.7f
private const val SELECTED_FILL = 0.16f
private const val SELECTED_BORDER = 0.45f
private const val DISABLED_ALPHA = 0.4f
