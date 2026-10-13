package com.naury.kinetica.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.motion.PlaybackController
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 모션 편집 패널(docs 08·24). 고른 레이어 하나의 속성마다 줄을 두고 keyframe을 보여 준다.
 *
 * - 눈금자를 누르거나 끌면 재생 위치가 옮겨진다(캔버스 제스처와 따로 처리).
 * - 줄의 + 버튼은 지금 보이는 값으로 재생 위치에 키를 넣는다.
 * - 키를 끌어 시간을 옮기고(놓을 때 한 번 적용, 겹치면 거부), 누르면 골라서 easing을 바꾸거나 지운다.
 */
@Composable
internal fun TimelinePanel(state: EditorState, theme: StudioTheme, modifier: Modifier = Modifier) {
    val document by state.session.document.collectAsStateWithLifecycle()
    val selection by state.session.selection.collectAsStateWithLifecycle()
    val timeline = document.timelines.firstOrNull { it.id == EditorState.MAIN_TIMELINE }
    val durationMs = timeline?.durationMs ?: EditorState.DEFAULT_DURATION_MS
    var playing by remember { mutableStateOf(false) }
    var selectedKey by remember { mutableStateOf<SelectedKey?>(null) }
    PlaybackLoop(state, timeline, playing) { playing = false }
    Column(modifier.background(theme.panel).testTag(TIMELINE_TAG)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            TextButton(onClick = { playing = !playing }) {
                Text(stringResource(if (playing) R.string.kinetica_pause else R.string.kinetica_play), color = theme.accent)
            }
            Text(
                "${seconds(state.playheadMs)} / ${seconds(durationMs.toDouble())}",
                color = theme.onPanel,
                modifier = Modifier.weight(1f),
            )
            selectedKey?.let { key -> KeyActions(state, key, theme) { selectedKey = null } }
        }
        val nodeId = selection.selectedIds.singleOrNull()
        if (nodeId == null) {
            Text(
                stringResource(R.string.kinetica_motion_select_hint),
                color = theme.onPanel.copy(alpha = HINT_ALPHA),
                modifier = Modifier.padding(12.dp),
            )
            return@Column
        }
        Row {
            Column(Modifier.width(LABEL_WIDTH)) {
                Box(Modifier.height(RULER_HEIGHT))
                ANIMATED.forEach { (property, label) -> TrackLabel(state, nodeId, property, label, theme) }
            }
            val scroll = rememberScrollState()
            Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                val laneWidth = MS_WIDTH * durationMs.toFloat() + LANE_END_PADDING
                Column(Modifier.width(laneWidth)) {
                    Ruler(state, durationMs, theme)
                    ANIMATED.forEach { (property, label) ->
                        val track = timeline?.track(nodeId, property)
                        KeyLane(state, nodeId, property, track?.keyframes?.map { it.timeMs }.orEmpty(), label, selectedKey, theme) {
                            selectedKey =
                                it
                        }
                    }
                }
                Playhead(state.playheadMs, theme, Modifier.matchParentSize())
            }
        }
    }
}

@Composable
private fun PlaybackLoop(state: EditorState, timeline: Timeline?, playing: Boolean, onFinished: () -> Unit) {
    LaunchedEffect(playing, timeline) {
        if (!playing || timeline == null) return@LaunchedEffect
        val controller = PlaybackController(timeline)
        controller.seek(if (state.playheadMs >= timeline.durationMs) 0.0 else state.playheadMs)
        controller.play()
        while (true) {
            withFrameNanos { }
            val update = controller.onFrame()
            state.playheadMs = update.state.timeMs
            if (!update.state.isPlaying) break
        }
        onFinished()
    }
}

@Composable
private fun TrackLabel(state: EditorState, nodeId: NodeId, property: AnimatableProperty, label: Int, theme: StudioTheme) {
    val name = stringResource(label)
    val description = stringResource(R.string.kinetica_add_key, name)
    Row(Modifier.height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = theme.onPanel, modifier = Modifier.weight(1f).padding(start = 8.dp))
        TextButton(onClick = { state.addKey(nodeId, property) }, modifier = Modifier.semantics { contentDescription = description }) {
            Text("+", color = theme.accent)
        }
    }
}

@Composable
private fun Ruler(state: EditorState, durationMs: Long, theme: StudioTheme) {
    val msWidthPx = with(LocalDensity.current) { MS_WIDTH.toPx() }
    fun seek(x: Float) {
        state.playheadMs = (x / msWidthPx).toDouble().coerceIn(0.0, durationMs.toDouble())
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(RULER_HEIGHT)
            .testTag(RULER_TAG)
            .pointerInput(durationMs) { detectTapGestures { seek(it.x) } }
            .pointerInput(durationMs) { detectHorizontalDragGestures { change, _ -> seek(change.position.x) } },
    ) {
        var time = 0L
        while (time <= durationMs) {
            val x = time * msWidthPx
            val major = time % MAJOR_TICK_MS == 0L
            drawLine(theme.onPanel.copy(alpha = HINT_ALPHA), Offset(x, if (major) 0f else size.height / 2), Offset(x, size.height))
            time += MINOR_TICK_MS
        }
    }
}

@Composable
private fun KeyLane(
    state: EditorState,
    nodeId: NodeId,
    property: AnimatableProperty,
    times: List<Long>,
    label: Int,
    selected: SelectedKey?,
    theme: StudioTheme,
    onSelect: (SelectedKey) -> Unit,
) {
    val msWidthPx = with(LocalDensity.current) { MS_WIDTH.toPx() }
    val diamondPx = with(LocalDensity.current) { DIAMOND_TOUCH.toPx() }
    val name = stringResource(label)
    Box(Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
        times.forEach { time ->
            var dragPx by remember(time) { mutableFloatStateOf(0f) }
            val isSelected = selected == SelectedKey(nodeId, property, time)
            val description = stringResource(R.string.kinetica_keyframe_description, name, seconds(time.toDouble()))
            Box(
                Modifier
                    .offset { IntOffset((time * msWidthPx + dragPx - diamondPx / 2).roundToInt(), 0) }
                    .size(DIAMOND_TOUCH, ROW_HEIGHT)
                    .semantics { contentDescription = description }
                    .pointerInput(time) {
                        detectTapGestures {
                            onSelect(SelectedKey(nodeId, property, time))
                            state.playheadMs = time.toDouble()
                        }
                    }
                    .pointerInput(time) {
                        detectDragGestures(
                            onDragEnd = {
                                val target = (time + dragPx / msWidthPx).roundToLong().coerceAtLeast(0L)
                                dragPx = 0f
                                if (target != time) {
                                    state.session.commands.execute(
                                        SceneCommand.MoveKeyframe(EditorState.MAIN_TIMELINE, nodeId, property, time, target),
                                    )
                                }
                            },
                            onDragCancel = { dragPx = 0f },
                        ) { change, amount ->
                            change.consume()
                            dragPx += amount.x
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(DIAMOND_SIZE)
                        .rotate(DIAMOND_ROTATION)
                        .background(if (isSelected) theme.selection else theme.accent),
                )
            }
        }
    }
}

@Composable
private fun Playhead(timeMs: Double, theme: StudioTheme, modifier: Modifier) {
    val msWidthPx = with(LocalDensity.current) { MS_WIDTH.toPx() }
    Canvas(modifier) {
        val x = (timeMs * msWidthPx).toFloat()
        drawLine(theme.accent, Offset(x, 0f), Offset(x, size.height), strokeWidth = PLAYHEAD_WIDTH.toPx())
    }
}

@Composable
private fun KeyActions(state: EditorState, key: SelectedKey, theme: StudioTheme, onDeleted: () -> Unit) {
    EASINGS.forEach { (easing, label) ->
        TextButton(onClick = {
            state.session.commands.execute(
                SceneCommand.SetKeyframeEasing(EditorState.MAIN_TIMELINE, key.nodeId, key.property, key.timeMs, easing),
            )
        }) { Text(stringResource(label), color = theme.onPanel) }
    }
    TextButton(onClick = {
        state.session.commands.execute(SceneCommand.DeleteKeyframes(EditorState.MAIN_TIMELINE, key.nodeId, key.property, setOf(key.timeMs)))
        onDeleted()
    }) { Text(stringResource(R.string.kinetica_delete_key), color = theme.warning) }
}

private fun seconds(ms: Double): String = String.format(Locale.ROOT, "%.2f", ms / MS_PER_SECOND)

/** 테스트에서 패널을 찾을 때 쓰는 tag. */
internal const val TIMELINE_TAG = "kinetica-timeline"
internal const val RULER_TAG = "kinetica-timeline-ruler"

private val ANIMATED = listOf(
    AnimatableProperty.TranslationX to R.string.kinetica_property_x,
    AnimatableProperty.TranslationY to R.string.kinetica_property_y,
    AnimatableProperty.RotationDegrees to R.string.kinetica_property_rotation,
    AnimatableProperty.ScaleX to R.string.kinetica_property_scale_x,
    AnimatableProperty.ScaleY to R.string.kinetica_property_scale_y,
    AnimatableProperty.Opacity to R.string.kinetica_property_opacity,
)
private val EASINGS = listOf(
    EasingSpec.Linear to R.string.kinetica_easing_linear,
    EasingSpec.EaseIn to R.string.kinetica_easing_in,
    EasingSpec.EaseOut to R.string.kinetica_easing_out,
    EasingSpec.EaseInOut to R.string.kinetica_easing_in_out,
    EasingSpec.Spring(dampingRatio = 0.5, stiffness = 300.0) to R.string.kinetica_easing_spring,
)

/** docs 24: 1초 80dp. */
private val MS_WIDTH = 0.08.dp
private val LABEL_WIDTH = 132.dp
private val RULER_HEIGHT = 32.dp
private val ROW_HEIGHT = 44.dp
private val DIAMOND_SIZE = 10.dp
private val DIAMOND_TOUCH = 32.dp
private val LANE_END_PADDING = 24.dp
private val PLAYHEAD_WIDTH = 2.dp
private const val DIAMOND_ROTATION = 45f
private const val HINT_ALPHA = 0.6f
private const val MS_PER_SECOND = 1_000.0
private const val MAJOR_TICK_MS = 1_000L
private const val MINOR_TICK_MS = 250L
