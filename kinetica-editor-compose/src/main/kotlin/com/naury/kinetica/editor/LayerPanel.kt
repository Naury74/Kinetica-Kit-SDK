package com.naury.kinetica.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeKind

/**
 * 레이어 목록(docs 24). 줄 높이 48dp, 그룹 깊이만큼 들여 쓰고, 뒤쪽에 보임·잠금 전환을 둔다.
 * 줄을 누르면 그 노드 하나를 고른다. 고른 줄은 접근성에 선택됨으로 알린다.
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
                    .heightIn(min = ROW_HEIGHT)
                    .background(if (isSelected) theme.selection.copy(alpha = SELECTED_ALPHA) else theme.panel)
                    .semantics { this.selected = isSelected }
                    .clickable { onSelect(node.id) }
                    .padding(start = INDENT * row.depth + 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    label,
                    color = theme.onPanel.copy(alpha = if (node.visible) 1f else MUTED_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                LayerToggle(R.string.kinetica_visible, node.visible, theme) { onToggleVisible(node.id) }
                LayerToggle(R.string.kinetica_locked, node.locked, theme) { onToggleLocked(node.id) }
            }
        }
    }
}

@Composable
private fun LayerToggle(label: Int, checked: Boolean, theme: StudioTheme, onToggle: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = ROW_HEIGHT)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() })
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(label), color = if (checked) theme.accent else theme.onPanel.copy(alpha = MUTED_ALPHA))
    }
}

internal fun NodeKind.labelRes(): Int = when (this) {
    NodeKind.Group -> R.string.kinetica_kind_group
    NodeKind.Rectangle -> R.string.kinetica_kind_rectangle
    NodeKind.Ellipse -> R.string.kinetica_kind_ellipse
    NodeKind.Path -> R.string.kinetica_kind_path
    NodeKind.Text -> R.string.kinetica_kind_text
    NodeKind.Image -> R.string.kinetica_kind_image
    NodeKind.Frame -> R.string.kinetica_kind_frame
    NodeKind.HostSlot -> R.string.kinetica_kind_host
}

private val ROW_HEIGHT = 48.dp
private val INDENT = 16.dp
private const val MUTED_ALPHA = 0.5f
private const val SELECTED_ALPHA = 0.18f
