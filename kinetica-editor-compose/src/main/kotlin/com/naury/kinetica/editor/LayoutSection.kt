package com.naury.kinetica.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.scene.AutoLayout
import com.naury.kinetica.core.scene.CrossAlign
import com.naury.kinetica.core.scene.LayoutDirection
import com.naury.kinetica.core.scene.MainAlign
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.Pin
import com.naury.kinetica.core.scene.SceneNode

/** Frame 자동 배치(방향·간격·여백·정렬·내용 맞춤). 끄면 자식은 지금 자리에 남는다. */
@Composable
internal fun AutoLayoutSection(node: SceneNode, frame: NodeContent.Frame, edits: LayoutEdits, theme: StudioTheme) {
    val layout = frame.layout
    Text(stringResource(R.string.kinetica_section_auto_layout), color = theme.onPanel.copy(alpha = SECTION_ALPHA))
    ChipRow {
        ToggleChip(R.string.kinetica_layout_none, layout == null, theme) { edits.setLayout(node.id) { null } }
        ToggleChip(R.string.kinetica_layout_row, layout?.direction == LayoutDirection.Horizontal, theme) {
            edits.setLayout(node.id) { (it ?: AutoLayout()).copy(direction = LayoutDirection.Horizontal) }
        }
        ToggleChip(R.string.kinetica_layout_column, layout?.direction == LayoutDirection.Vertical, theme) {
            edits.setLayout(node.id) { (it ?: AutoLayout()).copy(direction = LayoutDirection.Vertical) }
        }
    }
    if (layout == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpacingInput(node.id.value + "gap" + layout.gap, layout.gap, R.string.kinetica_field_gap, Modifier.weight(1f)) {
            edits.setGap(node.id, it)
        }
        SpacingInput(node.id.value + "pad" + layout.padding, layout.padding.left, R.string.kinetica_field_padding, Modifier.weight(1f)) {
            edits.setPadding(node.id, it)
        }
    }
    ChipRow {
        MAIN_ALIGNS.forEach { (align, label) ->
            ToggleChip(label, layout.mainAlign == align, theme) { edits.setLayout(node.id) { it?.copy(mainAlign = align) } }
        }
    }
    ChipRow {
        CROSS_ALIGNS.forEach { (align, label) ->
            ToggleChip(label, layout.crossAlign == align, theme) { edits.setLayout(node.id) { it?.copy(crossAlign = align) } }
        }
    }
    ChipRow {
        ToggleChip(R.string.kinetica_layout_hug_width, layout.hugWidth, theme) {
            edits.setLayout(node.id) { it?.copy(hugWidth = !it.hugWidth) }
        }
        ToggleChip(R.string.kinetica_layout_hug_height, layout.hugHeight, theme) {
            edits.setLayout(node.id) { it?.copy(hugHeight = !it.hugHeight) }
        }
    }
}

/** 부모(Frame·artboard) 크기가 바뀔 때 따라가는 방식. 화면 크기 대응 재생([com.naury.kinetica.core.scene.Layouts.resizeArtboard])에 쓰인다. */
@Composable
internal fun ConstraintsSection(node: SceneNode, edits: LayoutEdits, theme: StudioTheme) {
    Text(stringResource(R.string.kinetica_section_constraints), color = theme.onPanel.copy(alpha = SECTION_ALPHA))
    ChipRow {
        HORIZONTAL_PINS.forEach { (pin, label) ->
            ToggleChip(label, node.constraints.horizontal == pin, theme) {
                edits.setConstraints(node.id, node.constraints.copy(horizontal = pin))
            }
        }
    }
    ChipRow {
        VERTICAL_PINS.forEach { (pin, label) ->
            ToggleChip(label, node.constraints.vertical == pin, theme) {
                edits.setConstraints(node.id, node.constraints.copy(vertical = pin))
            }
        }
    }
}

/** 자동 배치 Frame의 자식. 위치는 배치가 정하고, 남는 공간을 채울지만 고른다. */
@Composable
internal fun LayoutChildSection(node: SceneNode, edits: LayoutEdits, theme: StudioTheme) {
    Text(stringResource(R.string.kinetica_section_layout_child), color = theme.onPanel.copy(alpha = SECTION_ALPHA))
    ToggleChip(R.string.kinetica_layout_fill, node.layoutFill, theme) { edits.toggleFill(node.id) }
}

@Composable
private fun SpacingInput(key: String, value: Double, label: Int, modifier: Modifier, onCommit: (String) -> Boolean) {
    CommitTextField(
        current = formatNumber(value),
        key = key,
        label = stringResource(label),
        keyboardType = KeyboardType.Decimal,
        enabled = true,
        modifier = modifier,
        onCommit = onCommit,
    )
}

// 좁은 패널에서도 선택지가 옆으로 숨지 않게 줄을 바꿔 놓는다.
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

private val MAIN_ALIGNS = listOf(
    MainAlign.Start to R.string.kinetica_align_start,
    MainAlign.Center to R.string.kinetica_align_center,
    MainAlign.End to R.string.kinetica_align_end,
    MainAlign.SpaceBetween to R.string.kinetica_align_between,
)

private val CROSS_ALIGNS = listOf(
    CrossAlign.Start to R.string.kinetica_cross_start,
    CrossAlign.Center to R.string.kinetica_cross_center,
    CrossAlign.End to R.string.kinetica_cross_end,
    CrossAlign.Stretch to R.string.kinetica_cross_stretch,
)

private val HORIZONTAL_PINS = listOf(
    Pin.Start to R.string.kinetica_pin_left,
    Pin.End to R.string.kinetica_pin_right,
    Pin.Both to R.string.kinetica_pin_left_right,
    Pin.Center to R.string.kinetica_pin_center_h,
    Pin.Scale to R.string.kinetica_pin_scale,
)

private val VERTICAL_PINS = listOf(
    Pin.Start to R.string.kinetica_pin_top,
    Pin.End to R.string.kinetica_pin_bottom,
    Pin.Both to R.string.kinetica_pin_top_bottom,
    Pin.Center to R.string.kinetica_pin_center_v,
    Pin.Scale to R.string.kinetica_pin_scale,
)

private const val SECTION_ALPHA = 0.6f
