package com.naury.kinetica.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.render.SceneLayout

/**
 * 선택한 노드의 모션 효과를 보여 주는 작은 미리보기. 편집 캔버스에서는 효과가 재생되지 않으므로 여기서 눌러 보거나 다시 재생해 확인한다.
 * 노드와 자손만 그리고 문서는 바꾸지 않는다.
 */
@Composable
internal fun EffectPreview(document: SceneDocument, node: SceneNode, theme: StudioTheme) {
    if (node.effects.isEmpty) return
    var replay by remember { mutableIntStateOf(0) }
    var size by remember { mutableStateOf(0 to 0) }
    val preview = remember(document, node) { previewDocument(document, node) } ?: return
    Column {
        Box(
            Modifier.fillMaxWidth().height(PREVIEW_HEIGHT.dp).clip(RoundedCornerShape(12.dp)).background(theme.canvasBackground)
                .onSizeChanged { size = it.width to it.height }.testTag("kinetica-effect-preview"),
        ) {
            val bounds = Rect(0.0, 0.0, preview.artboard.width, preview.artboard.height)
            // 글로우, 버스트, 핑은 노드 바깥까지 그리므로 여백을 둔다.
            val viewport = Viewport.fitting(bounds, size.first.toDouble(), size.second.toDouble(), paddingPx = size.second * PADDING)
            // 다시 재생을 누르면 새로 구성해 등장 효과를 처음부터 보여 준다.
            key(replay, node.effects) { SceneLayout(preview, viewport, Modifier.matchParentSize(), interactive = true) }
        }
        StudioTextButton(onClick = { replay++ }) { Text(stringResource(R.string.kinetica_motion_replay), color = theme.accent) }
    }
}

/** [node]와 자손만 담은 문서. 노드를 원점에 두고 회전과 스케일은 뺀다. 크기를 알 수 없으면 `null`이다. */
private fun previewDocument(document: SceneDocument, node: SceneNode): SceneDocument? {
    val bounds = node.localBounds?.takeIf { it.width > 0.0 && it.height > 0.0 } ?: return null
    val nodes = LinkedHashMap<NodeId, SceneNode>()
    val pending = ArrayDeque(listOf(node.id))
    while (pending.isNotEmpty() && nodes.size < MAX_NODES) {
        val id = pending.removeFirst()
        val current = document.node(id) ?: continue
        nodes[id] = current
        pending += document.childrenOf(id)
    }
    nodes[node.id] = node.copy(parentId = null, transform = Transform2D(translationX = -bounds.left, translationY = -bounds.top))
    return SceneDocument("effect-preview", Artboard(bounds.width, bounds.height), nodes, listOf(node.id), tokens = document.tokens)
}

private const val PREVIEW_HEIGHT = 140
private const val PADDING = 0.2
private const val MAX_NODES = 500
