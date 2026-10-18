package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** [KChipGroup]의 선택 방식. */
public enum class KChipSelection {
    /** 하나만 고른다. 다른 칩을 누르면 선택이 옮겨 가고, 고른 칩을 다시 눌러도 해제되지 않는다. TalkBack은 라디오 버튼으로 읽는다. */
    Single,

    /** 여러 개를 고르고, 누를 때마다 켜고 끈다. TalkBack은 체크박스로 읽는다. */
    Multiple,
}

/**
 * 필터나 태그 선택에 쓰는 칩 묶음. 기본은 폭이 모자라면 다음 줄로 넘기고, [wrap]이 `false`면 한 줄로 두고 가로로 스크롤한다.
 * 선택된 칩은 강조색으로 물들고, 앞쪽에 체크 표시가 그려지며 나타나는 동안 칩 폭이 부드럽게 늘어난다. 누르면 햅틱을 준다.
 * 칩 하나하나는 [KFilterChip]과 같은 모양이다. 칩마다 아이콘을 달거나 다른 종류의 칩을 섞으려면 [KChipRow]에 칩을 직접 놓는다.
 *
 * ```kotlin
 * KChipGroup(listOf("전체", "사진", "동영상"), selected, { selected = it })
 * KChipGroup(categories, picked, { picked = it }, wrap = false, contentPadding = PaddingValues(horizontal = 20.dp))
 * ```
 *
 * @param chips 칩 이름. 순서대로 놓는다.
 * @param selected 선택된 칩의 위치(0부터) 집합. [chips] 범위 밖의 값은 무시한다.
 * @param onSelectedChange 새 선택 집합을 받는다. [KChipSelection.Single]이면 항상 원소가 하나다.
 * @param enabled `false`면 누를 수 없고 흐리게 그린다.
 * @param variant 선택되지 않은 칩의 모양. 기본값 [KVariant.Glass]는 [surface]로 그리고, [KVariant.Tonal]은 옅은 회색 칩,
 *   [KVariant.Outlined]는 테두리 칩이다. [KVariant.Filled]는 [KVariant.Tonal]과 같게 그린다.
 * @param tone 선택된 칩을 물들일 색 역할. 기본값은 [KTone.Accent]다.
 * @param wrap `false`면 줄을 넘기지 않고 가로로 스크롤한다. 줄을 넘기는 [KChipSelection.Multiple] 묶음은 칩을 켜고 꺼도 폭이 바뀌지 않아,
 *   연달아 누르는 동안 뒤 칩들이 다른 줄로 옮겨 가지 않는다. 하나만 고르는 묶음은 체크 자리만큼 폭이 늘었다 줄어든다.
 * @param contentPadding [wrap]이 `false`일 때 스크롤 영역 안쪽 여백. 화면 폭에 둔 줄에서 본문 여백을 맞출 때 쓴다.
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
    wrap: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val accent = tone.colors(surface).color
    val haptic = LocalHapticFeedback.current
    val group = modifier.then(if (selection == KChipSelection.Single) Modifier.selectableGroup() else Modifier)
    val chipsContent: @Composable () -> Unit = {
        chips.forEachIndexed { index, label ->
            val isSelected = index in selected
            val onTap = {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSelectedChange(nextSelection(selection, selected, index, chips.size))
            }
            FilterChipBody(
                ChipSpec(label, isSelected, enabled, accent, surface, variant),
                Modifier,
                reserveCheck =
                    wrap && selection == KChipSelection.Multiple,
            ) {
                when (selection) {
                    KChipSelection.Single ->
                        Modifier.selectable(isSelected, null, KIndication(chipShape), enabled, Role.RadioButton, onTap)

                    KChipSelection.Multiple ->
                        Modifier.toggleable(isSelected, null, KIndication(chipShape), enabled, Role.Checkbox) { onTap() }
                }
            }
        }
    }
    if (wrap) {
        FlowRow(
            group,
            horizontalArrangement = Arrangement.spacedBy(ChipRowGap),
            verticalArrangement = Arrangement.spacedBy(ChipRowGap),
        ) { chipsContent() }
    } else {
        KChipRow(group, contentPadding) { chipsContent() }
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
