package com.naury.kinetica.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.kinetica.core.animation.AnimatableProperties
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.motion.PlaybackController
import com.naury.kinetica.presets.PresetRegistry
import java.util.Locale
import kotlin.math.floor

/**
 * 모션 편집 패널. 선택한 레이어 하나의 속성마다 트랙 줄을 두고 keyframe을 보여 준다.
 *
 * - 눈금자를 누르거나 끌면 재생 위치가 바뀐다. 캔버스 제스처와는 따로 처리한다.
 * - 줄의 + 버튼은 지금 보이는 값으로 재생 위치에 keyframe을 넣는다.
 * - 시간 축은 두 손가락, Ctrl+휠, +/− 버튼으로 확대하고, **맞춤**으로 전체를 한 화면에 넣는다. 눈금 간격은 배율에 따라 바뀐다.
 * - 키 선택과 끌기는 [KeyLanes], 고른 키의 easing 곡선은 [EasingEditor]가 맡는다. 키 편집은 동작 하나가 실행 취소 한 단계다.
 */
@Composable
internal fun TimelinePanel(state: EditorState, theme: StudioTheme, modifier: Modifier = Modifier) {
    val document by state.session.document.collectAsStateWithLifecycle()
    val selection by state.session.selection.collectAsStateWithLifecycle()
    val timeline = document.timelines.firstOrNull { it.id == EditorState.MAIN_TIMELINE }
    val durationMs = timeline?.durationMs ?: EditorState.DEFAULT_DURATION_MS
    val controls = rememberControls(state, durationMs)
    SideEffect { controls.view.durationMs = durationMs }
    var playing by remember { mutableStateOf(false) }
    var curveOpen by remember { mutableStateOf(false) }
    PlaybackLoop(state, timeline, playing) { playing = false }
    FollowPlayhead(state, controls.view)
    val nodeId = selection.selectedIds.singleOrNull()
    LaunchedEffect(nodeId) { controls.selection.clear() }
    LaunchedEffect(timeline) { controls.selection.retain(timeline) }
    Column(modifier.background(theme.panel).timelineShortcuts(state).testTag(TIMELINE_TAG)) {
        TimelineToolbar(controls, theme, durationMs, playing, presetsEnabled = selection.selectedIds.isNotEmpty()) { playing = !playing }
        if (nodeId == null) {
            Text(
                stringResource(R.string.kinetica_motion_select_hint),
                color = theme.onPanel.copy(alpha = HINT_ALPHA),
                modifier = Modifier.padding(12.dp),
            )
            return@Column
        }
        KeyToolbar(controls, nodeId, theme, curveOpen) { curveOpen = !curveOpen }
        TimelineContent(controls, nodeId, animatedRows(document, timeline, nodeId), theme, curveOpen)
    }
}

@Composable
private fun rememberControls(state: EditorState, durationMs: Long): TimelineControls {
    val density = LocalDensity.current
    return remember(state, density) {
        val view = with(density) { TimelineView(MS_WIDTH.toPx(), LANE_START_PADDING.toPx(), LANE_END_PADDING.toPx()) }
        view.durationMs = durationMs
        TimelineControls(state, view, state.keySelection, state.keyEdits)
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
private fun FollowPlayhead(state: EditorState, view: TimelineView) {
    LaunchedEffect(view) {
        snapshotFlow { Triple(state.playheadMs, view.followPlayhead, view.viewportPx) }.collect { (time, follow, _) ->
            if (follow) view.reveal(time)
        }
    }
}

@Composable
private fun TimelineToolbar(
    controls: TimelineControls,
    theme: StudioTheme,
    durationMs: Long,
    playing: Boolean,
    presetsEnabled: Boolean,
    onPlayToggle: () -> Unit,
) {
    val view = controls.view
    val follow = stringResource(R.string.kinetica_follow_playhead_description)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
    ) {
        TextButton(onClick = onPlayToggle) {
            Text(stringResource(if (playing) R.string.kinetica_pause else R.string.kinetica_play), color = theme.accent)
        }
        Text("${seconds(controls.state.playheadMs)} / ${seconds(durationMs.toDouble())}", color = theme.onPanel)
        BarButton("−", theme, description = stringResource(R.string.kinetica_zoom_out)) { view.zoomBy(1f / ZOOM_STEP) }
        BarButton("+", theme, description = stringResource(R.string.kinetica_zoom_in)) { view.zoomBy(ZOOM_STEP) }
        BarButton(stringResource(R.string.kinetica_zoom_fit), theme) { view.fit() }
        Box(Modifier.semantics { contentDescription = follow }) {
            ToggleChip(stringResource(R.string.kinetica_follow_playhead), view.followPlayhead, theme) {
                view.followPlayhead = !view.followPlayhead
            }
        }
        PresetMenu(controls.state, theme, enabled = presetsEnabled)
    }
}

@Composable
internal fun BarButton(
    text: String,
    theme: StudioTheme,
    enabled: Boolean = true,
    description: String? = null,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val color = when {
        !enabled -> theme.onPanel.copy(alpha = HINT_ALPHA)
        highlighted -> theme.accent
        else -> theme.onPanel
    }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = if (description == null) Modifier else Modifier.semantics { contentDescription = description },
    ) { Text(text, color = color) }
}

@Composable
private fun TimelineContent(controls: TimelineControls, nodeId: NodeId, rows: List<LaneRow>, theme: StudioTheme, curveOpen: Boolean) {
    val target = if (curveOpen) curveTarget(controls) else null
    BoxWithConstraints {
        val wide = maxWidth >= WIDE_CURVE_LAYOUT
        if (target != null && !wide) {
            CurvePanel(controls, target, theme, Modifier.fillMaxWidth())
        } else {
            Row {
                TimelineTracks(controls, nodeId, rows, theme, Modifier.weight(1f))
                if (target != null) CurvePanel(controls, target, theme, Modifier.width(CURVE_PANEL_WIDTH))
            }
        }
    }
}

@Composable
private fun TimelineTracks(controls: TimelineControls, nodeId: NodeId, rows: List<LaneRow>, theme: StudioTheme, modifier: Modifier) {
    // 눈금자는 고정하고 속성 줄만 세로로 스크롤한다. 가로 위치는 TimelineView 하나를 함께 쓴다.
    Column(modifier) {
        Row {
            Box(Modifier.width(LABEL_WIDTH).height(RULER_HEIGHT))
            Ruler(controls, theme, Modifier.weight(1f))
        }
        Row(Modifier.verticalScroll(rememberScrollState())) {
            Column(Modifier.width(LABEL_WIDTH)) {
                rows.forEach { row -> TrackLabel(controls.state, nodeId, row.property, row.label, theme) }
            }
            KeyLanes(controls, nodeId, rows, theme, Modifier.weight(1f))
        }
    }
}

/** easing 편집기가 보여 줄 키. 고른 키 가운데 가장 이른 키이고, 구간 길이는 같은 track의 다음 키까지다. */
private data class CurveTarget(val key: SelectedKey, val easing: EasingSpec, val segmentMs: Double)

private fun curveTarget(controls: TimelineControls): CurveTarget? {
    val key = controls.selection.keys.minByOrNull { it.timeMs } ?: return null
    val keys = controls.state.timeline?.track(key.nodeId, key.property)?.keyframes ?: return null
    val index = keys.indexOfFirst { it.timeMs == key.timeMs }.takeIf { it >= 0 } ?: return null
    val segment = keys.getOrNull(index + 1)?.let { it.timeMs - key.timeMs }?.toDouble() ?: DEFAULT_SEGMENT_MS
    return CurveTarget(key, keys[index].easing, segment)
}

@Composable
private fun CurvePanel(controls: TimelineControls, target: CurveTarget, theme: StudioTheme, modifier: Modifier) {
    EasingEditor(target.easing, target.segmentMs, theme, onChange = {
        controls.edits.setEasing(controls.selection.keys, it)
    }, modifier = modifier)
}

@Composable
private fun TrackLabel(state: EditorState, nodeId: NodeId, property: AnimatableProperty, label: Int, theme: StudioTheme) {
    val name = stringResource(label)
    val description = stringResource(R.string.kinetica_add_key, name)
    Row(Modifier.height(LANE_ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            color = theme.onPanel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        TextButton(onClick = { state.addKey(nodeId, property) }, modifier = Modifier.semantics { contentDescription = description }) {
            Text("+", color = theme.accent)
        }
    }
}

@Composable
private fun Ruler(controls: TimelineControls, theme: StudioTheme, modifier: Modifier) {
    val view = controls.view
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val minMinorPx = with(density) { MIN_MINOR_TICK.toPx() }
    val minMajorPx = with(density) { MIN_MAJOR_TICK.toPx() }
    val labelStyle = TextStyle(color = theme.onPanel.copy(alpha = HINT_ALPHA), fontSize = TICK_LABEL_SIZE)
    fun seek(x: Float) {
        controls.state.playheadMs = view.timeAt(x).coerceIn(0.0, view.durationMs.toDouble())
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(RULER_HEIGHT)
            .clipToBounds()
            .onSizeChanged { view.viewportPx = it.width.toFloat() }
            .testTag(RULER_TAG)
            .timelineWheel(view)
            .pointerInput(view) { detectTapGestures { seek(it.x) } }
            .pointerInput(view) { detectHorizontalDragGestures { change, _ -> seek(change.position.x) } },
    ) {
        val steps = view.ticks(minMinorPx, minMajorPx)
        val first = (floor(view.timeAt(0f).coerceAtLeast(0.0) / steps.minorMs) * steps.minorMs).toLong()
        val last = minOf(view.durationMs, view.timeAt(size.width).toLong())
        val tickColor = theme.onPanel.copy(alpha = HINT_ALPHA)
        var time = first
        while (time <= last) {
            val x = view.viewX(time.toDouble())
            val major = time % steps.majorMs == 0L
            drawLine(tickColor, Offset(x, if (major) 0f else size.height / 2), Offset(x, size.height))
            if (major) drawText(measurer, tickLabel(time), Offset(x + TICK_LABEL_GAP.toPx(), 0f), labelStyle)
            time += steps.minorMs
        }
        val playhead = view.viewX(controls.state.playheadMs)
        drawLine(theme.accent, Offset(playhead, 0f), Offset(playhead, size.height), strokeWidth = PLAYHEAD_STROKE.toPx())
    }
}

@Composable
private fun PresetMenu(state: EditorState, theme: StudioTheme, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = {
            expanded = true
        }, enabled = enabled) { Text(stringResource(R.string.kinetica_presets), color = theme.onPanel) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PRESETS.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        expanded = false
                        state.applyPreset(PresetRegistry.Default, id)
                    },
                )
            }
        }
    }
}

internal fun seconds(ms: Double): String = String.format(Locale.ROOT, "%.2f", ms / MS_PER_SECOND)

/** 눈금자 시각 표시. 1초 단위면 "2s", 아니면 "0.25s"처럼 쓴다. */
internal fun tickLabel(ms: Long): String =
    if (ms % MS_PER_SECOND.toLong() == 0L) "${ms / MS_PER_SECOND.toLong()}s" else seconds(ms.toDouble()).trimEnd('0') + "s"

/** 테스트에서 패널을 찾을 때 쓰는 test tag. */
internal const val TIMELINE_TAG = "kinetica-timeline"
internal const val RULER_TAG = "kinetica-timeline-ruler"

// 노드에 없는 속성(그림자가 없는 노드의 그림자 등)은 keyframe을 넣어도 바뀌지 않으므로 숨긴다. 이미 keyframe이 있으면 지울 수 있게 보여 준다.
private fun animatedRows(document: SceneDocument, timeline: Timeline?, nodeId: NodeId): List<LaneRow> {
    val node = document.node(nodeId) ?: return emptyList()
    return ANIMATED.mapNotNull { (property, label) ->
        val track = timeline?.track(nodeId, property)
        if (AnimatableProperties.read(node, property) == null && track == null) return@mapNotNull null
        LaneRow(property, label, track?.keyframes?.map { it.timeMs }.orEmpty())
    }
}

private val ANIMATED = listOf(
    AnimatableProperty.TranslationX to R.string.kinetica_property_x,
    AnimatableProperty.TranslationY to R.string.kinetica_property_y,
    AnimatableProperty.RotationDegrees to R.string.kinetica_property_rotation,
    AnimatableProperty.ScaleX to R.string.kinetica_property_scale_x,
    AnimatableProperty.ScaleY to R.string.kinetica_property_scale_y,
    AnimatableProperty.Opacity to R.string.kinetica_property_opacity,
    AnimatableProperty.Fill to R.string.kinetica_property_fill,
    AnimatableProperty.Width to R.string.kinetica_property_width,
    AnimatableProperty.Height to R.string.kinetica_property_height,
    AnimatableProperty.CornerRadius to R.string.kinetica_property_corner,
    AnimatableProperty.Stroke to R.string.kinetica_property_stroke,
    AnimatableProperty.Blur to R.string.kinetica_property_blur,
    AnimatableProperty.ShadowBlur to R.string.kinetica_property_shadow_blur,
    AnimatableProperty.ShadowOffsetY to R.string.kinetica_property_shadow_y,
    AnimatableProperty.ShadowOffsetX to R.string.kinetica_property_shadow_x,
    AnimatableProperty.ShadowSpread to R.string.kinetica_property_shadow_spread,
    AnimatableProperty.ShadowColor to R.string.kinetica_property_shadow_color,
    AnimatableProperty.CornerTopLeft to R.string.kinetica_property_corner_top_left,
    AnimatableProperty.CornerTopRight to R.string.kinetica_property_corner_top_right,
    AnimatableProperty.CornerBottomRight to R.string.kinetica_property_corner_bottom_right,
    AnimatableProperty.CornerBottomLeft to R.string.kinetica_property_corner_bottom_left,
    AnimatableProperty.GradientStart to R.string.kinetica_property_gradient_start,
    AnimatableProperty.GradientEnd to R.string.kinetica_property_gradient_end,
    AnimatableProperty.GradientAngle to R.string.kinetica_property_gradient_angle,
)
private val PRESETS = listOf(
    "fade.in" to R.string.kinetica_preset_fade_in,
    "card.expand" to R.string.kinetica_preset_card_expand,
    "stagger.entrance" to R.string.kinetica_preset_stagger_entrance,
    "elastic.pop" to R.string.kinetica_preset_elastic_pop,
    "slide.in.left" to R.string.kinetica_preset_slide_in_left,
    "parallax" to R.string.kinetica_preset_parallax,
)

// 처음 배율은 1초를 80dp로 그린다.
private val MS_WIDTH = 0.08.dp
private val LABEL_WIDTH = 168.dp
private val RULER_HEIGHT = 32.dp
private val LANE_START_PADDING = 16.dp
private val LANE_END_PADDING = 24.dp
private val MIN_MINOR_TICK = 12.dp
private val MIN_MAJOR_TICK = 64.dp
private val TICK_LABEL_GAP = 3.dp
private val TICK_LABEL_SIZE = 10.sp
private val WIDE_CURVE_LAYOUT = 720.dp
private val CURVE_PANEL_WIDTH = 260.dp
private const val HINT_ALPHA = 0.6f
private const val MS_PER_SECOND = 1_000.0
private const val ZOOM_STEP = 1.5f
private const val DEFAULT_SEGMENT_MS = 1_000.0
