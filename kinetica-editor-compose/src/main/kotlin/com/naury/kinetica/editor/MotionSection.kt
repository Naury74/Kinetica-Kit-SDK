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
import com.naury.kinetica.core.scene.AppearKind
import com.naury.kinetica.core.scene.LoopKind
import com.naury.kinetica.core.scene.PressKind
import com.naury.kinetica.core.scene.SceneNode

/** 미리 만든 움직임 효과. 편집 캔버스에서는 움직이지 않고 미리보기·재생기에서 보인다. */
@Composable
internal fun MotionSection(node: SceneNode, edits: MotionEdits, theme: StudioTheme) {
    val effects = node.effects
    Text(stringResource(R.string.kinetica_section_motion_effects), color = theme.onPanel.copy(alpha = MUTED))
    Text(stringResource(R.string.kinetica_motion_press), color = theme.onPanel.copy(alpha = MUTED))
    Chips {
        PRESS.forEach { (kind, label) ->
            ToggleChip(label, effects.press.any { it.kind == kind }, theme) { edits.togglePress(node.id, kind) }
        }
    }
    Text(stringResource(R.string.kinetica_motion_appear), color = theme.onPanel.copy(alpha = MUTED))
    Chips {
        ToggleChip(R.string.kinetica_motion_none, effects.appear == null, theme) { edits.setAppear(node.id, null) }
        APPEAR.forEach { (kind, label) -> ToggleChip(label, effects.appear?.kind == kind, theme) { edits.setAppear(node.id, kind) } }
    }
    effects.appear?.let { appear ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MsField(
                node.id.value + "appear-d" + appear.durationMs,
                appear.durationMs,
                R.string.kinetica_field_duration_ms,
                Modifier.weight(1f),
            ) {
                edits.setAppearTiming(node.id, durationText = it)
            }
            MsField(node.id.value + "appear-w" + appear.delayMs, appear.delayMs, R.string.kinetica_field_delay_ms, Modifier.weight(1f)) {
                edits.setAppearTiming(node.id, delayText = it)
            }
        }
    }
    Text(stringResource(R.string.kinetica_motion_loop), color = theme.onPanel.copy(alpha = MUTED))
    Chips {
        ToggleChip(R.string.kinetica_motion_none, effects.loop == null, theme) { edits.setLoop(node.id, null) }
        LOOP.forEach { (kind, label) -> ToggleChip(label, effects.loop?.kind == kind, theme) { edits.setLoop(node.id, kind) } }
    }
    effects.loop?.let { loop ->
        MsField(node.id.value + "loop" + loop.periodMs, loop.periodMs, R.string.kinetica_field_period_ms, Modifier) {
            edits.setLoopPeriod(node.id, it)
        }
    }
}

@Composable
private fun MsField(key: String, value: Long, label: Int, modifier: Modifier, onCommit: (String) -> Boolean) {
    CommitTextField(
        current = value.toString(),
        key = key,
        label = stringResource(label),
        keyboardType = KeyboardType.Number,
        enabled = true,
        modifier = modifier,
        onCommit = onCommit,
    )
}

@Composable
private fun Chips(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

private val PRESS = listOf(
    PressKind.Ripple to R.string.kinetica_press_ripple,
    PressKind.Scale to R.string.kinetica_press_scale,
    PressKind.Tilt to R.string.kinetica_press_tilt,
    PressKind.Dim to R.string.kinetica_press_dim,
    PressKind.Lift to R.string.kinetica_press_lift,
    PressKind.Burst to R.string.kinetica_press_burst,
)

private val APPEAR = listOf(
    AppearKind.Fade to R.string.kinetica_appear_fade,
    AppearKind.SlideUp to R.string.kinetica_appear_slide_up,
    AppearKind.SlideDown to R.string.kinetica_appear_slide_down,
    AppearKind.SlideLeft to R.string.kinetica_appear_slide_left,
    AppearKind.SlideRight to R.string.kinetica_appear_slide_right,
    AppearKind.Pop to R.string.kinetica_appear_pop,
    AppearKind.Zoom to R.string.kinetica_appear_zoom,
    AppearKind.Flip to R.string.kinetica_appear_flip,
    AppearKind.Blur to R.string.kinetica_appear_blur,
)

private val LOOP = listOf(
    LoopKind.Pulse to R.string.kinetica_loop_pulse,
    LoopKind.Breathe to R.string.kinetica_loop_breathe,
    LoopKind.Float to R.string.kinetica_loop_float,
    LoopKind.Bounce to R.string.kinetica_loop_bounce,
    LoopKind.Shake to R.string.kinetica_loop_shake,
    LoopKind.Shimmer to R.string.kinetica_loop_shimmer,
    LoopKind.Glow to R.string.kinetica_loop_glow,
    LoopKind.Spin to R.string.kinetica_loop_spin,
    LoopKind.Heartbeat to R.string.kinetica_loop_heartbeat,
    LoopKind.Wiggle to R.string.kinetica_loop_wiggle,
    LoopKind.Ping to R.string.kinetica_loop_ping,
)

private const val MUTED = 0.6f
