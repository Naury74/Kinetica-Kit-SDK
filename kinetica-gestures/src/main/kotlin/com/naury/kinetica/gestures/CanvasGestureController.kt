package com.naury.kinetica.gestures

import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.geometry.shortestDeltaDegrees
import com.naury.kinetica.core.scene.HitTester
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.core.scene.SelectionFrame
import com.naury.kinetica.core.scene.SelectionHandle
import com.naury.kinetica.core.scene.TransformOps
import kotlin.math.atan2
import kotlin.math.exp

/**
 * 캔버스 포인터 입력을 편집 동작으로 바꾸는 상태 기계(docs 06).
 *
 * - 한 손가락: 손잡이 → 크기 조절·회전, 노드 → 옮기기(안 고른 노드면 먼저 고름), 빈 곳 → 사각 선택. 끌지 않고 떼면 고르기만 한다.
 * - 두 손가락: 캔버스 이동·확대. 한 손가락 동작 중 두 번째 손가락이 닿으면 그때까지의 변경을 확정하고 캔버스 조작으로 바꾼다.
 * - 끌기 하나는 트랜잭션 하나라 실행 취소 한 단계다. 취소되면 시작 전 문서로 되돌린다.
 * - 움직임은 시작 시점 문서에서 절대값으로 계산하고, 노드는 최상위 단위로 고른다. 잠긴·숨긴 노드는 잡지 않는다.
 *
 * 좌표는 모두 viewport 픽셀이다. main 스레드에서만 부른다.
 *
 * @param snapping 옮기기에서 이동량을 고칠 때 쓴다(안내선 스냅). `null`이면 손가락을 그대로 따라간다.
 */
public class CanvasGestureController(
    private val host: GestureHost,
    private val config: GestureConfig,
    private val snapping: DragSnapFactory? = null,
    private val uptimeMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {
    private val pointers = LinkedHashMap<Long, Vec2>()
    private var press: Press? = null
    private var active: ActiveGesture? = null
    private var canvasStart: CanvasStart? = null

    public var mode: InteractionMode = InteractionMode.Idle
        private set

    /** 사각 선택 중인 영역(viewport 픽셀). 없으면 `null`. */
    public var marquee: Rect? = null
        private set

    /** 상태가 바뀔 때 불린다. 화면은 이때 [mode]·[marquee]를 다시 읽는다. */
    public var onChange: (() -> Unit)? = null

    /**
     * 같은 노드를 짧은 간격([DOUBLE_TAP_MILLIS])으로 두 번 눌렀을 때 불린다. 그룹 안이어도 실제로 누른 노드(맨 아래 노드)를 넘긴다.
     * 글자 편집 등에 쓴다.
     */
    public var onDoubleTap: ((NodeId) -> Unit)? = null

    private var lastTap: LastTap? = null

    public fun onDown(pointerId: Long, viewportPoint: Vec2) {
        pointers[pointerId] = viewportPoint
        when {
            pointers.size == 1 && mode == InteractionMode.Idle -> startPress(pointerId, viewportPoint)
            pointers.size == 2 -> startCanvasTransform()
        }
        onChange?.invoke()
    }

    public fun onMove(pointerId: Long, viewportPoint: Vec2) {
        if (!pointers.containsKey(pointerId)) return
        pointers[pointerId] = viewportPoint
        when (mode) {
            InteractionMode.PressPending -> press?.takeIf { it.pointerId == pointerId }?.let { maybeStartDrag(it, viewportPoint) }
            InteractionMode.TransformCanvas -> transformCanvas()
            InteractionMode.Idle -> Unit
            else -> active?.takeIf { it.pointerId == pointerId }?.let { update(it, viewportPoint) }
        }
        onChange?.invoke()
    }

    /**
     * 손가락을 뗐다. [eventUptimeMillis]는 입력 이벤트가 생긴 시각(밀리초, 단조 증가)이며, 주면 두 번 누르기 간격을 이 값으로 잰다.
     * 화면이 바빠 이벤트 처리가 늦어져도 실제 누른 간격으로 판단하기 위해서다. 없으면 지금 시각을 쓴다.
     */
    public fun onUp(pointerId: Long, eventUptimeMillis: Long? = null) {
        pointers.remove(pointerId) ?: return
        when (mode) {
            InteractionMode.PressPending -> press?.let { tap(it, eventUptimeMillis ?: uptimeMillis()) }
            InteractionMode.DragNode, InteractionMode.ResizeNode, InteractionMode.RotateNode -> host.commitTransaction()
            InteractionMode.SelectMarquee -> finishMarquee()
            else -> Unit
        }
        active?.snap?.finish()
        if (mode != InteractionMode.TransformCanvas || pointers.isEmpty()) reset()
        onChange?.invoke()
    }

    /** 시스템이 제스처를 가져가거나 화면이 멈출 때 부른다. 확정하지 않은 변경을 되돌린다. */
    public fun onCancel() {
        if (active?.transactional == true) host.rollbackTransaction()
        active?.snap?.finish()
        pointers.clear()
        reset()
        onChange?.invoke()
    }

    /** 마우스 휠. [scrollDelta]가 양수면 축소, 음수면 확대한다. 포인터 아래 문서 점은 제자리에 있다. */
    public fun onScroll(viewportPoint: Vec2, scrollDelta: Double) {
        if (mode != InteractionMode.Idle || !scrollDelta.isFinite()) return
        val viewport = host.viewport
        host.viewport = viewport.zoomedAround(viewportPoint, viewport.zoom * exp(-scrollDelta * WHEEL_ZOOM_STEP))
        onChange?.invoke()
    }

    private fun startPress(pointerId: Long, viewportPoint: Vec2) {
        val graph = SceneGraph(host.document)
        val viewport = host.viewport
        val documentPoint = viewport.toDocument(viewportPoint)
        val selected = host.selection
        val frame = selected.singleOrNull()?.takeIf { !graph.isEffectivelyLocked(it) }?.let { SelectionFrame.of(graph, listOf(it)) }
        val handle = frame?.let { pickHandle(it, viewport, viewportPoint, config) }
        val leaf = HitTester(graph).hitTest(documentPoint, config.hitSlopPx / viewport.zoom)
        press = Press(pointerId, viewportPoint, graph, viewport, handle, leaf)
        mode = InteractionMode.PressPending
    }

    private fun maybeStartDrag(press: Press, viewportPoint: Vec2) {
        if (press.start.distanceTo(viewportPoint) < config.touchSlopPx) return
        val selected = host.selection
        val (nextMode, label) = when {
            press.handle == SelectionHandle.Rotate -> InteractionMode.RotateNode to "rotate"
            press.handle != null -> InteractionMode.ResizeNode to "resize"
            press.hit != null -> InteractionMode.DragNode to "move"
            else -> InteractionMode.SelectMarquee to "select"
        }
        if (nextMode == InteractionMode.SelectMarquee) {
            mode = nextMode
            active = ActiveGesture(press, emptyList(), transactional = false).also { update(it, viewportPoint) }
            return
        }
        val targets = if (nextMode == InteractionMode.DragNode && press.hit !in selected) {
            listOfNotNull(press.hit).also(host::select)
        } else {
            selected
        }.filterNot(press.graph::isEffectivelyLocked)
        if (targets.isEmpty() || !host.beginTransaction(label)) {
            reset()
            return
        }
        mode = nextMode
        val snap = if (nextMode == InteractionMode.DragNode) snapping?.start(press.graph, targets) else null
        active = ActiveGesture(press, targets, transactional = true, snap).also { update(it, viewportPoint) }
    }

    private fun update(gesture: ActiveGesture, viewportPoint: Vec2) {
        val press = gesture.press
        val graph = press.graph
        val documentPoint = press.viewport.toDocument(viewportPoint)
        when (mode) {
            InteractionMode.DragNode -> {
                val raw = documentPoint - press.viewport.toDocument(press.start)
                val delta = gesture.snap?.adjust(raw, press.viewport.zoom) ?: raw
                host.execute(SceneCommand.TransformNodes(TransformOps.move(graph, gesture.targets, delta)))
            }

            InteractionMode.ResizeNode -> {
                val handle = press.handle ?: return
                TransformOps.resize(graph, gesture.targets.single(), handle, documentPoint)
                    ?.let { host.execute(SceneCommand.UpdateProperties(it)) }
            }

            InteractionMode.RotateNode -> rotate(gesture, documentPoint)

            InteractionMode.SelectMarquee -> marquee = Rect.enclosing(listOf(press.start, viewportPoint))

            else -> Unit
        }
    }

    private fun rotate(gesture: ActiveGesture, documentPoint: Vec2) {
        val id = gesture.targets.single()
        val center = SelectionFrame.of(gesture.press.graph, listOf(id))?.center ?: return
        fun angleTo(point: Vec2) = Math.toDegrees(atan2(point.y - center.y, point.x - center.x))
        val angle = angleTo(documentPoint)
        // 끌기 기준 거리를 넘기 전 움직임도 회전에 넣도록 처음 누른 점에서 잰다.
        val previous = gesture.lastAngle ?: angleTo(gesture.press.viewport.toDocument(gesture.press.start))
        // ±180°를 넘을 때 끊기지 않도록 프레임 사이 변화량을 이어 붙인다.
        gesture.totalRotation += shortestDeltaDegrees(previous, angle)
        gesture.lastAngle = angle
        TransformOps.rotate(gesture.press.graph, id, gesture.totalRotation, config.rotationSnapDegrees)
            ?.let { host.execute(SceneCommand.TransformNodes(mapOf(id to it))) }
    }

    private fun tap(press: Press, now: Long) {
        val previous = lastTap
        val leaf = press.leaf
        val isDouble = leaf != null && previous != null && previous.leaf == leaf &&
            now - previous.time <= DOUBLE_TAP_MILLIS && previous.point.distanceTo(press.start) <= config.handleTouchRadiusPx * 2
        lastTap = if (isDouble) null else leaf?.let { LastTap(it, now, press.start) }
        when {
            isDouble -> onDoubleTap?.invoke(checkNotNull(leaf))
            press.handle != null -> Unit
            press.hit != null -> host.select(listOf(press.hit))
            else -> host.select(emptyList())
        }
    }

    private fun finishMarquee() {
        val area = marquee ?: return
        val press = press ?: return
        val documentArea = press.viewport.viewportToDocument().mapBounds(area)
        val graph = press.graph
        host.select(HitTester(graph).nodesIntersecting(documentArea).mapNotNull(graph::topLevelAncestor).distinct())
    }

    private fun startCanvasTransform() {
        when (mode) {
            InteractionMode.DragNode, InteractionMode.ResizeNode, InteractionMode.RotateNode -> host.commitTransaction()
            else -> Unit
        }
        active?.snap?.finish()
        active = null
        press = null
        marquee = null
        val (first, second) = pointers.values.take(2)
        canvasStart = CanvasStart(host.viewport, (first + second) * HALF, first.distanceTo(second))
        mode = InteractionMode.TransformCanvas
    }

    private fun transformCanvas() {
        val start = canvasStart ?: return
        if (pointers.size < 2) return
        val (first, second) = pointers.values.take(2)
        val centroid = (first + second) * HALF
        val distance = first.distanceTo(second)
        val zoom = if (start.distance > 0.0) Viewport.clampZoom(start.viewport.zoom * distance / start.distance) else start.viewport.zoom
        val anchoredDocument = start.viewport.toDocument(start.centroid)
        host.viewport = Viewport(panX = centroid.x - anchoredDocument.x * zoom, panY = centroid.y - anchoredDocument.y * zoom, zoom = zoom)
    }

    private fun reset() {
        press = null
        active = null
        canvasStart = null
        marquee = null
        mode = InteractionMode.Idle
    }

    private class Press(
        val pointerId: Long,
        val start: Vec2,
        val graph: SceneGraph,
        val viewport: Viewport,
        val handle: SelectionHandle?,
        val leaf: NodeId?,
    ) {
        /** 고르기·옮기기 단위인 최상위 노드. */
        val hit: NodeId? = leaf?.let(graph::topLevelAncestor)
    }

    private class LastTap(val leaf: NodeId, val time: Long, val point: Vec2)

    private class ActiveGesture(val press: Press, val targets: List<NodeId>, val transactional: Boolean, val snap: DragSnap? = null) {
        val pointerId: Long get() = press.pointerId
        var lastAngle: Double? = null
        var totalRotation: Double = 0.0
    }

    private class CanvasStart(val viewport: Viewport, val centroid: Vec2, val distance: Double)

    public companion object {
        /** 두 번 누르기로 보는 최대 간격(ms). */
        public const val DOUBLE_TAP_MILLIS: Long = 300L
        private const val HALF = 0.5
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val WHEEL_ZOOM_STEP = 0.1
    }
}

private const val INSIDE_HANDLE_FACTOR = 0.5
private const val INSIDE_SIDE_FACTOR = 0.25

/**
 * 테두리 안에서는 손잡이 가까이(터치 반경의 절반, 그리고 짧은 변의 1/4 이하)를 누를 때만 손잡이로 본다.
 * 작은 도형에서 손잡이 영역이 몸통을 덮어 옮기려 해도 크기가 바뀌는 일을 막는다. 테두리 밖에서는 터치 반경 전체를 쓴다.
 */
private fun pickHandle(frame: SelectionFrame, viewport: Viewport, viewportPoint: Vec2, config: GestureConfig): SelectionHandle? {
    val handles = frame.handles(viewport, config.rotateHandleOffsetPx)
    val radius = if (frame.contains(viewport, viewportPoint)) {
        val corners = frame.corners.map(viewport::toViewport)
        val shortSide = minOf(corners[0].distanceTo(corners[1]), corners[1].distanceTo(corners[2]))
        minOf(config.handleTouchRadiusPx * INSIDE_HANDLE_FACTOR, shortSide * INSIDE_SIDE_FACTOR)
    } else {
        config.handleTouchRadiusPx
    }
    return SelectionHandle.at(handles, viewportPoint, radius)
}
