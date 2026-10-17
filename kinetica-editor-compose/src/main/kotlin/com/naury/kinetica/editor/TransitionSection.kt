package com.naury.kinetica.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneTransitionEdge
import com.naury.kinetica.core.scene.SceneTransitionKind

// 다른 장면에서 이 장면으로 바뀔 때의 전환. 재생기(AnimatedScene, RemoteKineticaPlayer)가 읽는다.
@Composable
internal fun TransitionSection(document: SceneDocument, edits: TransitionEdits, theme: StudioTheme) {
    val transition = document.transition
    Text(stringResource(R.string.kinetica_section_transition), color = theme.onPanel.copy(alpha = MUTED))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ToggleChip(R.string.kinetica_motion_none, transition == null, theme) { edits.setKind(null) }
        KINDS.forEach { (kind, label) -> ToggleChip(label, transition?.kind == kind, theme) { edits.setKind(kind) } }
    }
    if (transition == null) return
    if (transition.kind.hasDirection) {
        Text(stringResource(R.string.kinetica_transition_from), color = theme.onPanel.copy(alpha = MUTED))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EDGES.forEach { (edge, label) -> ToggleChip(label, transition.from == edge, theme) { edits.setFrom(edge) } }
        }
    }
    CommitTextField(
        current = transition.durationMs.toString(),
        key = "transition-duration" + transition.durationMs,
        label = stringResource(R.string.kinetica_field_duration_ms),
        keyboardType = KeyboardType.Number,
        enabled = true,
        modifier = Modifier.fillMaxWidth(),
        onCommit = edits::setDuration,
    )
}

private val KINDS = listOf(
    SceneTransitionKind.Fade to R.string.kinetica_transition_fade,
    SceneTransitionKind.Morph to R.string.kinetica_transition_morph,
    SceneTransitionKind.Dissolve to R.string.kinetica_transition_dissolve,
    SceneTransitionKind.Slide to R.string.kinetica_transition_slide,
    SceneTransitionKind.Push to R.string.kinetica_transition_push,
    SceneTransitionKind.Cover to R.string.kinetica_transition_cover,
    SceneTransitionKind.Uncover to R.string.kinetica_transition_uncover,
    SceneTransitionKind.Wipe to R.string.kinetica_transition_wipe,
    SceneTransitionKind.Iris to R.string.kinetica_transition_iris,
    SceneTransitionKind.Zoom to R.string.kinetica_transition_zoom,
    SceneTransitionKind.ZoomBlur to R.string.kinetica_transition_zoom_blur,
    SceneTransitionKind.Rotate to R.string.kinetica_transition_rotate,
    SceneTransitionKind.Flip to R.string.kinetica_transition_flip,
    SceneTransitionKind.Cube to R.string.kinetica_transition_cube,
)

private val EDGES = listOf(
    SceneTransitionEdge.End to R.string.kinetica_transition_from_end,
    SceneTransitionEdge.Start to R.string.kinetica_transition_from_start,
    SceneTransitionEdge.Top to R.string.kinetica_transition_from_top,
    SceneTransitionEdge.Bottom to R.string.kinetica_transition_from_bottom,
)

private const val MUTED = 0.6f
