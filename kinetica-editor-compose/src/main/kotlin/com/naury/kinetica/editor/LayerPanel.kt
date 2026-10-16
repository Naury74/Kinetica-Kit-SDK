package com.naury.kinetica.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeKind

/**
 * 레이어 목록. 한 줄 높이는 48dp이고 그룹 깊이만큼 들여 쓰며, 줄 끝에 표시(눈)와 잠금(자물쇠) 아이콘 토글을 둔다.
 * 줄을 누르면 그 노드 하나만 선택하고, 선택한 줄은 접근성 서비스에 선택됨으로 알린다.
 */
@Composable
internal fun LayerPanel(
    rows: List<LayerRow>,
    selected: List<NodeId>,
    theme: StudioTheme,
    onSelect: (NodeId) -> Unit,
    onToggleVisible: (NodeId) -> Unit,
    onToggleLocked: (NodeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) {
        Box(modifier.padding(16.dp)) {
            Text(stringResource(R.string.kinetica_layers_empty), color = theme.onPanel.copy(alpha = MUTED_ALPHA))
        }
        return
    }
    LazyColumn(modifier) {
        items(rows, key = { it.node.id.value }) { row ->
            val node = row.node
            val isSelected = node.id in selected
            val label = node.name.ifEmpty { stringResource(node.kind.labelRes()) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ROW_INSET)
                    .heightIn(min = ROW_HEIGHT)
                    .background(if (isSelected) theme.selection.copy(alpha = SELECTED_ALPHA) else Color.Transparent, RowShape)
                    .semantics { this.selected = isSelected }
                    .clickable(indication = studioIndication(RowShape), interactionSource = null) { onSelect(node.id) }
                    .padding(start = INDENT * row.depth + 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(
                    label,
                    color = theme.onPanel.copy(alpha = if (node.visible) 1f else MUTED_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                LayerToggle(VisibleToggle, node.visible, theme) { onToggleVisible(node.id) }
                LayerToggle(LockedToggle, node.locked, theme) { onToggleLocked(node.id) }
            }
        }
    }
}

// 표시와 잠금 토글. 아이콘만 보이고 접근성 이름은 라벨 문자열이다. 꺼진 쪽(숨김, 잠금)이 눈에 띄도록 그쪽 아이콘을 진하게 그린다.
@Composable
private fun LayerToggle(spec: LayerToggleSpec, checked: Boolean, theme: StudioTheme, onToggle: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val label = stringResource(spec.label)
    val emphasized = checked == spec.emphasizeWhen
    val tint = when {
        emphasized && spec.accentWhenEmphasized -> theme.accent
        emphasized -> theme.onPanel
        else -> theme.onPanel.copy(alpha = ICON_IDLE_ALPHA)
    }
    Box(
        Modifier
            .size(width = TOGGLE_WIDTH, height = ROW_HEIGHT)
            .toggleable(value = checked, interactionSource = source, indication = null, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(TOGGLE_FACE).indication(source, studioIndication(CircleShape)), contentAlignment = Alignment.Center) {
            Icon(if (checked) spec.on else spec.off, contentDescription = null, tint = tint, modifier = Modifier.size(TOGGLE_ICON))
        }
    }
}

private class LayerToggleSpec(
    val label: Int,
    val on: ImageVector,
    val off: ImageVector,
    val emphasizeWhen: Boolean,
    val accentWhenEmphasized: Boolean,
)

private val VisibleToggle = LayerToggleSpec(R.string.kinetica_visible, StudioIcons.Eye, StudioIcons.EyeOff, false, false)
private val LockedToggle = LayerToggleSpec(R.string.kinetica_locked, StudioIcons.Lock, StudioIcons.LockOpen, true, true)

internal fun NodeKind.labelRes(): Int = when (this) {
    NodeKind.Group -> R.string.kinetica_kind_group
    NodeKind.Rectangle -> R.string.kinetica_kind_rectangle
    NodeKind.Ellipse -> R.string.kinetica_kind_ellipse
    NodeKind.Polygon -> R.string.kinetica_kind_polygon
    NodeKind.Path -> R.string.kinetica_kind_path
    NodeKind.Text -> R.string.kinetica_kind_text
    NodeKind.Image -> R.string.kinetica_kind_image
    NodeKind.Frame -> R.string.kinetica_kind_frame
    NodeKind.HostSlot -> R.string.kinetica_kind_host
}

private val ROW_HEIGHT = 48.dp
private val ROW_INSET = 6.dp
private val RowShape = RoundedCornerShape(10.dp)
private val INDENT = 16.dp
private const val MUTED_ALPHA = 0.5f
private const val SELECTED_ALPHA = 0.18f
private const val ICON_IDLE_ALPHA = 0.45f
private val TOGGLE_WIDTH = 40.dp
private val TOGGLE_FACE = 34.dp
private val TOGGLE_ICON = 20.dp
