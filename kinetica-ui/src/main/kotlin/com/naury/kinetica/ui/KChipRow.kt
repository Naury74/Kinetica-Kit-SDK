package com.naury.kinetica.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 칩을 한 줄로 늘어놓고 가로로 스크롤한다. 칩이 많아 줄을 넘기면 화면이 길어지는 필터 줄에 쓴다.
 * 화면 가장자리까지 스크롤되게 하려면 이 줄을 화면 폭에 두고 [contentPadding]에 본문 여백을 준다.
 *
 * ```kotlin
 * KChipRow(contentPadding = PaddingValues(horizontal = 20.dp), fadeEdges = true) { filters.forEach { KFilterChip(it.name, it.on, { … }) } }
 * ```
 *
 * @param state 줄의 스크롤 상태. 선택한 칩으로 스크롤하거나 끝에 닿았는지 볼 때 넘긴다.
 * @param fadeEdges true면 화면 밖에 칩이 더 있는 쪽 끝을 흐리게 한다([horizontalFadingEdges]).
 * @param shadowRoom true면 칩 그림자가 스크롤 영역 경계에서 잘리지 않게 위아래에 6dp씩 여유를 둔다. 그림자 없는 칩(미선택 색 지정,
 *   단색 표면)만 놓거나 칩마다 `minTouchTarget` 여유가 있으면 false로 꺼서 줄 높이를 칩 높이에 맞춘다.
 */
@Composable
public fun KChipRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    state: ScrollState = rememberScrollState(),
    fadeEdges: Boolean = false,
    shadowRoom: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        // 스크롤 영역은 내용을 잘라 그리므로, 칩 그림자가 경계에서 끊겨 띠처럼 보이지 않게 위아래 여유를 둔다.
        modifier
            .then(if (fadeEdges) Modifier.horizontalFadingEdges(state) else Modifier)
            .horizontalScroll(state)
            .padding(contentPadding)
            .then(if (shadowRoom) Modifier.padding(vertical = CHIP_SHADOW_ROOM) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(ChipRowGap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 1.0.0-alpha04까지의 시그니처. 컴파일된 호출자를 위해 남긴다. */
@Deprecated("바이너리 호환용", level = DeprecationLevel.HIDDEN)
@Composable
public fun KChipRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    state: ScrollState = rememberScrollState(),
    fadeEdges: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    KChipRow(modifier, contentPadding, state, fadeEdges, true, content)
}

@Deprecated("바이너리 호환용", level = DeprecationLevel.HIDDEN)
@Composable
public fun KChipRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable RowScope.() -> Unit,
) {
    KChipRow(modifier, contentPadding, rememberScrollState(), false, true, content)
}

/**
 * 필터 칩의 색을 직접 정한다. [Color.Unspecified]인 값은 기본 규칙(선택은 [KTone] 색으로 물들임, 미선택은 `variant` 모양)을 따른다.
 *
 * @property selected 선택된 칩을 채울 색. 주면 옅게 물들이지 않고 이 색으로 꽉 채운다.
 * @property onSelected 선택된 칩의 글자와 체크 색. 지정하지 않으면 [selected] 위에서 읽히는 색을 고른다.
 * @property unselected 선택되지 않은 칩의 바탕색. 주면 `variant` 모양 대신 이 색으로 채운다.
 * @property onUnselected 선택되지 않은 칩의 글자 색
 */
@Immutable
public class KChipColors(
    public val selected: Color = Color.Unspecified,
    public val onSelected: Color = Color.Unspecified,
    public val unselected: Color = Color.Unspecified,
    public val onUnselected: Color = Color.Unspecified,
) {
    override fun equals(other: Any?): Boolean = other is KChipColors && selected == other.selected &&
        onSelected == other.onSelected && unselected == other.unselected && onUnselected == other.onUnselected

    override fun hashCode(): Int =
        ((selected.hashCode() * 31 + onSelected.hashCode()) * 31 + unselected.hashCode()) * 31 + onUnselected.hashCode()
}
