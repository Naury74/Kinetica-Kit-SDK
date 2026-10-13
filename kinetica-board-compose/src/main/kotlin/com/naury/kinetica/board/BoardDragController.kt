package com.naury.kinetica.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Vec2

/** 끌고 있는 위젯. [rect]는 손가락을 따라가는 자리, [target]은 놓으면 들어갈 자리(분할·칸·붙임). 모두 화면 폭 문서 단위. */
internal data class BoardDrag(val key: String, val rect: Rect, val target: Rect, val zone: SnapZone?)

/** 편집 모드의 옮기기·크기 바꾸기. 끄는 동안은 문서를 바꾸지 않고, 놓을 때 한 번 [KineticaBoardState.place]한다. */
internal class BoardDragController(private val state: KineticaBoardState) {
    var drag by mutableStateOf<BoardDrag?>(null)
        private set

    /** 그릴 때마다 갱신하는 화면 정보. */
    var frame: BoardFrame? = null

    private var origin: Rect? = null
    private var startLocal: Offset = Offset.Zero

    /**
     * 끌기 시작. [local]은 위젯 편집 틀(끄는 동안 제자리에 있다) 안의 손가락 위치(픽셀)다.
     * 이후 [move]·[resize]도 같은 틀 기준 위치를 받으므로, 끌기 감지 여유(touch slop)와 상관없이 손가락을 정확히 따라간다.
     */
    fun start(key: String, local: Offset) {
        val rect = frame?.rects?.get(key) ?: return
        origin = rect
        startLocal = local
        drag = BoardDrag(key, rect, rect, null)
    }

    fun move(key: String, local: Offset) {
        val frame = frame ?: return
        val start = origin ?: return
        val dx = (local.x - startLocal.x) / frame.zoom
        val dy = (local.y - startLocal.y) / frame.zoom
        val raw = Rect(start.left + dx, start.top + dy, start.right + dx, start.bottom + dy)
        val others = frame.rects.filterKeys { it != key }.values.toList()
        val pointer = Vec2(start.left + local.x / frame.zoom, start.top + local.y / frame.zoom)
        val zone = if (state.arrangement is BoardArrangement.Free) BoardGeometry.zoneAt(pointer, frame.visible, frame.edge) else null
        val snapped = BoardGeometry.snapMove(raw, others, frame.mapping.displayWidth, frame.mapping.gap, frame.snap)
        val target = zone?.let { BoardGeometry.zoneRect(it, frame.visible, frame.mapping.gap) } ?: frame.mapping.cellPreview(raw) ?: snapped
        drag = BoardDrag(key, if (zone == null) snapped else raw, target, zone)
    }

    /** 크기 바꾸기. [local]은 손잡이 안의 손가락 위치다. 왼쪽 위는 그대로이고 [frame]의 최소 크기보다 작아지지 않는다. */
    fun resize(key: String, local: Offset) {
        val frame = frame ?: return
        val start = origin ?: return
        val raw = Rect(
            start.left,
            start.top,
            maxOf(start.left + frame.minSize, start.right + (local.x - startLocal.x) / frame.zoom).coerceAtMost(frame.mapping.displayWidth),
            maxOf(start.top + frame.minSize, start.bottom + (local.y - startLocal.y) / frame.zoom),
        )
        val others = frame.rects.filterKeys { it != key }.values.toList()
        val snapped = BoardGeometry.snapResize(raw, others, frame.mapping.displayWidth, frame.mapping.gap, frame.snap)
        drag = BoardDrag(key, snapped, frame.mapping.cellPreview(raw) ?: snapped, null)
    }

    fun end() {
        val current = drag ?: return
        val frame = frame
        drag = null
        origin = null
        if (frame != null && current.target != frame.rects[current.key]) state.place(current.key, frame.mapping.toReference(current.target))
    }

    fun cancel() {
        drag = null
        origin = null
    }
}

/**
 * 한 번 그릴 때의 화면 정보(문서 단위). [visible]은 스크롤된 보이는 영역, [edge]는 분할 자리로 보는 가장자리 폭,
 * [snap]은 붙는 거리, [minSize]는 가장 작은 위젯 크기다.
 */
internal data class BoardFrame(
    val mapping: BoardMapping,
    val zoom: Float,
    val rects: Map<String, Rect>,
    val visible: Rect,
    val edge: Double,
    val snap: Double,
    val minSize: Double,
)
