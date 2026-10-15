package com.naury.kinetica.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.RotationDirection
import com.naury.kinetica.core.scene.NodeId

/**
 * 고른 키에 대한 동작 줄. 키를 고르거나, 복사해 둔 키가 있거나, 선택 모드일 때만 보인다.
 * 고른 키에 회전 키가 있으면 아래 줄에 회전 방향을 고르는 칩을 둔다.
 */
@Composable
internal fun KeyToolbar(controls: TimelineControls, nodeId: NodeId, theme: StudioTheme, curveOpen: Boolean, onCurveToggle: () -> Unit) {
    val selection = controls.selection
    val edits = controls.edits
    val keys = selection.keys
    if (keys.isEmpty() && edits.clipboard.isEmpty() && !selection.selectMode) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
    ) {
        Text(stringResource(R.string.kinetica_keys_selected, keys.size), color = theme.onPanel)
        ToggleChip(R.string.kinetica_select_mode, selection.selectMode, theme) { selection.selectMode = !selection.selectMode }
        BarButton(
            stringResource(R.string.kinetica_key_curve),
            theme,
            enabled = keys.isNotEmpty(),
            highlighted = curveOpen,
            onClick = onCurveToggle,
        )
        BarButton(stringResource(R.string.kinetica_copy_keys), theme, enabled = keys.isNotEmpty()) { edits.copy(keys) }
        BarButton(stringResource(R.string.kinetica_paste_keys), theme, enabled = edits.clipboard.isNotEmpty()) {
            val pasted = edits.paste(controls.state.keyTimeMs, nodeId)
            if (pasted.isNotEmpty()) selection.keys = pasted
        }
        BarButton(stringResource(R.string.kinetica_delete_keys), theme, enabled = keys.isNotEmpty()) {
            if (edits.delete(keys)) selection.keys = emptySet()
        }
    }
    val rotations = keys.filter { it.property == AnimatableProperty.RotationDegrees }
    if (rotations.isNotEmpty()) RotationChips(controls, rotations.minBy { it.timeMs }, theme)
}

@Composable
private fun RotationChips(controls: TimelineControls, first: SelectedKey, theme: StudioTheme) {
    val current = controls.edits.keyframe(first)?.rotation ?: RotationDirection.Shortest
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
    ) {
        Text(stringResource(R.string.kinetica_rotation_direction), color = theme.onPanel)
        ROTATIONS.forEach { (direction, label) ->
            ToggleChip(label, current == direction, theme) { controls.edits.setRotation(controls.selection.keys, direction) }
        }
    }
}

private val ROTATIONS = listOf(
    RotationDirection.Shortest to R.string.kinetica_rotation_shortest,
    RotationDirection.Clockwise to R.string.kinetica_rotation_clockwise,
    RotationDirection.CounterClockwise to R.string.kinetica_rotation_counter_clockwise,
    RotationDirection.Direct to R.string.kinetica_rotation_direct,
)
