package com.naury.kinetica.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.scene.NodeId
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** 타임라인의 속성 한 줄. [times]는 그 속성 track의 keyframe 시각이다. */
internal data class LaneRow(val property: AnimatableProperty, val label: Int, val times: List<Long>)

/** 키를 끄는 중의 이동량과 사각 선택 영역. 손을 떼면 비운다. */
internal class LaneDrag {
    var deltaMs by mutableLongStateOf(0L)
    var moving by mutableStateOf(false)
    var marquee by mutableStateOf<Rect?>(null)

    fun reset() {
        deltaMs = 0L
        moving = false
        marquee = null
    }
}

/** 타임라인 패널이 줄과 제스처에 넘기는 상태 묶음. */
internal class TimelineControls(val state: EditorState, val view: TimelineView, val selection: KeySelection, val edits: KeyframeEdits) {
    val drag = LaneDrag()
}

/**
 * keyframe 줄. 보이는 너비만큼만 차지하고, 키는 [TimelineView]의 배율과 스크롤에 맞춰 놓는다.
 *
 * - 탭: 키 하나를 고르고 재생 위치를 그 키로 옮긴다. Shift를 누르고 있거나 선택 모드면 선택에 넣거나 뺀다.
 * - 길게 누르기: 선택 모드를 켜고 그 키를 선택에 넣는다.
 * - 키에서 끌기: 고른 키를 모두 같은 양만큼 옮긴다. 손을 뗄 때 한 번에 적용한다.
 * - 빈 곳에서 끌기: 사각 선택. 두 손가락: 확대와 가로 스크롤.
 */
@Composable
internal fun KeyLanes(controls: TimelineControls, nodeId: NodeId, rows: List<LaneRow>, theme: StudioTheme, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val gesture = remember(controls) { LaneGesture(controls) }
    gesture.nodeId = nodeId
    gesture.rows = rows
    gesture.rowPx = with(density) { LANE_ROW_HEIGHT.toPx() }
    gesture.touchPx = with(density) { DIAMOND_TOUCH.toPx() }
    val view = controls.view
    val drag = controls.drag
    Box(
        modifier
            .height(LANE_ROW_HEIGHT * rows.size)
            .clipToBounds()
            .testTag(LANES_TAG)
            .timelineWheel(view)
            .pointerInput(gesture) { laneGestures(gesture, viewConfiguration.touchSlop, viewConfiguration.longPressTimeoutMillis) },
    ) {
        rows.forEachIndexed { index, row ->
            val name = stringResource(row.label)
            row.times.forEach { time ->
                val key = SelectedKey(nodeId, row.property, time)
                val isSelected = key in controls.selection.keys
                val shown = if (isSelected && drag.moving) time + drag.deltaMs else time
                Diamond(key, name, isSelected, theme, gesture) {
                    IntOffset((view.viewX(shown.toDouble()) - gesture.touchPx / 2).roundToInt(), (index * gesture.rowPx).roundToInt())
                }
            }
        }
        Canvas(Modifier.matchParentSize()) {
            val x = view.viewX(controls.state.playheadMs)
            drawLine(theme.accent, Offset(x, 0f), Offset(x, size.height), strokeWidth = PLAYHEAD_STROKE.toPx())
            drag.marquee?.let { rect ->
                drawRect(theme.selection.copy(alpha = MARQUEE_FILL_ALPHA), rect.topLeft, rect.size)
                drawRect(theme.selection, rect.topLeft, rect.size, style = Stroke(1.dp.toPx()))
            }
        }
    }
}

@Composable
private fun Diamond(
    key: SelectedKey,
    name: String,
    isSelected: Boolean,
    theme: StudioTheme,
    gesture: LaneGesture,
    position: () -> IntOffset,
) {
    val description = stringResource(R.string.kinetica_keyframe_description, name, seconds(key.timeMs.toDouble()))
    Box(
        Modifier
            .offset { position() }
            .size(DIAMOND_TOUCH, LANE_ROW_HEIGHT)
            .semantics {
                contentDescription = description
                selected = isSelected
                onClick {
                    gesture.tap(key, gesture.controls.selection.selectMode)
                    true
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

/** 줄 제스처의 판단과 적용. 좌표는 줄 영역 기준이며 x는 보기 x다. */
internal class LaneGesture(val controls: TimelineControls) {
    var nodeId: NodeId = NodeId("none")
    var rows: List<LaneRow> = emptyList()
    var rowPx = 1f
    var touchPx = 1f
    private var marqueeBase: Set<SelectedKey> = emptySet()
    private val selection get() = controls.selection
    private val view get() = controls.view

    /** [position]에 있는 키. 손가락 폭 안에서 가장 가까운 키를 고른다. */
    fun hit(position: Offset): SelectedKey? {
        if (position.y < 0f) return null
        val row = rows.getOrNull((position.y / rowPx).toInt()) ?: return null
        val time = row.times.minByOrNull { abs(view.viewX(it.toDouble()) - position.x) } ?: return null
        return SelectedKey(nodeId, row.property, time).takeIf { abs(view.viewX(time.toDouble()) - position.x) <= touchPx / 2 }
    }

    /** 가운데가 [rect] 안에 있는 키. */
    fun keysIn(rect: Rect): Set<SelectedKey> = rows.flatMapIndexed { index, row ->
        val centerY = (index + HALF) * rowPx
        if (centerY !in rect.top..rect.bottom) return@flatMapIndexed emptyList()
        row.times.filter { view.viewX(it.toDouble()) in rect.left..rect.right }.map { SelectedKey(nodeId, row.property, it) }
    }.toSet()

    fun tap(key: SelectedKey?, additive: Boolean) {
        if (key == null) {
            if (!additive) selection.keys = emptySet()
            return
        }
        selection.tap(key, additive)
        if (!additive) controls.state.playheadMs = key.timeMs.toDouble()
    }

    fun longPress(key: SelectedKey?) {
        selection.selectMode = true
        if (key != null && key !in selection.keys) selection.keys += key
    }

    fun startDrag(key: SelectedKey?, additive: Boolean) {
        if (key == null) {
            marqueeBase = selection.keys
            return
        }
        if (key !in selection.keys) selection.tap(key, additive)
        controls.drag.moving = true
    }

    fun drag(key: SelectedKey?, start: Offset, current: Offset, additive: Boolean) {
        if (key == null) {
            val rect = Rect(minOf(start.x, current.x), minOf(start.y, current.y), maxOf(start.x, current.x), maxOf(start.y, current.y))
            controls.drag.marquee = rect
            selection.marquee(keysIn(rect), additive, marqueeBase)
        } else {
            controls.drag.deltaMs = controls.edits.clampDelta(selection.keys, ((current.x - start.x) / view.pxPerMs).roundToLong())
        }
    }

    fun endDrag() {
        val drag = controls.drag
        if (drag.moving) controls.edits.move(selection.keys, drag.deltaMs)?.let { selection.keys = it }
        drag.reset()
    }

    /** 두 손가락으로 벌리거나 오므리면 확대, 옆으로 밀면 가로 스크롤. */
    fun pinch(event: PointerEvent) {
        val centroid = event.calculateCentroid(useCurrent = true)
        if (centroid != Offset.Unspecified) view.zoomBy(event.calculateZoom(), centroid.x)
        view.scrollBy(-event.calculatePan().x)
        event.changes.forEach { it.consume() }
    }

    private companion object {
        const val HALF = 0.5f
    }
}

private sealed interface Pending {
    data object Up : Pending

    data object Pinch : Pending

    data object Moved : Pending
}

private suspend fun PointerInputScope.laneGestures(gesture: LaneGesture, slop: Float, longPressMs: Long) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val additive = currentEvent.keyboardModifiers.isShiftPressed || gesture.controls.selection.selectMode
    val key = gesture.hit(down.position)
    var longPressed = false
    var outcome = withTimeoutOrNull(longPressMs) { awaitPending(down, slop) }
    if (outcome == null) {
        longPressed = true
        gesture.longPress(key)
        outcome = awaitPending(down, slop)
    }
    when (outcome) {
        Pending.Up -> if (!longPressed) gesture.tap(key, additive)
        Pending.Pinch -> awaitPinch(gesture)
        Pending.Moved -> awaitDrag(gesture, down, key, additive || longPressed)
    }
}

/** 손가락이 [slop]보다 움직이거나, 떼어지거나, 두 번째 손가락이 닿을 때까지 기다린다. */
private suspend fun AwaitPointerEventScope.awaitPending(down: PointerInputChange, slop: Float): Pending {
    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        if (pressed.size >= 2) return Pending.Pinch
        val change = event.changes.firstOrNull { it.id == down.id }
        if (change == null || !change.pressed) return Pending.Up
        if ((change.position - down.position).getDistance() > slop) return Pending.Moved
    }
}

private suspend fun AwaitPointerEventScope.awaitPinch(gesture: LaneGesture) {
    var event = currentEvent
    while (event.changes.any { it.pressed }) {
        gesture.pinch(event)
        event = awaitPointerEvent()
    }
}

private suspend fun AwaitPointerEventScope.awaitDrag(gesture: LaneGesture, down: PointerInputChange, key: SelectedKey?, additive: Boolean) {
    gesture.startDrag(key, additive)
    var change: PointerInputChange? = currentEvent.changes.firstOrNull { it.id == down.id }
    while (change != null && change.pressed) {
        gesture.drag(key, down.position, change.position, additive)
        change.consume()
        change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
    }
    gesture.endDrag()
}

/** 휠: Ctrl(또는 Cmd)과 함께 돌리면 확대, Shift와 함께 돌리거나 가로로 밀면 가로 스크롤. 그 밖의 휠은 세로 스크롤에 넘긴다. */
internal fun Modifier.timelineWheel(view: TimelineView): Modifier = pointerInput(view) {
    val stepPx = WHEEL_STEP.toPx()
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull()
            if (event.type == PointerEventType.Scroll && change != null && wheel(view, event, change, stepPx)) change.consume()
        }
    }
}

/** 휠 한 번을 처리한다. 처리했으면 `true`. */
private fun wheel(view: TimelineView, event: PointerEvent, change: PointerInputChange, stepPx: Float): Boolean {
    val delta = change.scrollDelta
    val modifiers = event.keyboardModifiers
    when {
        modifiers.isCtrlPressed || modifiers.isMetaPressed ->
            view.zoomBy(
                if (delta.y <
                    0f
                ) {
                    WHEEL_ZOOM
                } else {
                    1f / WHEEL_ZOOM
                },
                change.position.x,
            )

        delta.x != 0f -> view.scrollBy(delta.x * stepPx)

        modifiers.isShiftPressed -> view.scrollBy(delta.y * stepPx)

        else -> return false
    }
    return true
}

/** 테스트에서 keyframe 줄을 찾을 때 쓰는 test tag. */
internal const val LANES_TAG = "kinetica-timeline-lanes"

internal val LANE_ROW_HEIGHT = 44.dp
internal val PLAYHEAD_STROKE = 2.dp
private val DIAMOND_SIZE = 10.dp
private val DIAMOND_TOUCH = 32.dp
private val WHEEL_STEP = 48.dp
private const val DIAMOND_ROTATION = 45f
private const val MARQUEE_FILL_ALPHA = 0.15f
private const val WHEEL_ZOOM = 1.2f
