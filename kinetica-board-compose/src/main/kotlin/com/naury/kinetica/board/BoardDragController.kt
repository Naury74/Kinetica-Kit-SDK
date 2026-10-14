package com.naury.kinetica.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    private var lastLocal: Offset = Offset.Zero
    private var pointerY: Double? = null

    /**
     * 끌기 시작. [local]은 위젯 편집 틀(끄는 동안 제자리에 있다) 안의 손가락 위치(픽셀)다.
     * 이후 [move]·[resize]도 같은 틀 기준 위치를 받으므로, 끌기 감지 여유(touch slop)와 상관없이 손가락을 정확히 따라간다.
     */
    fun start(key: String, local: Offset) {
        val rect = frame?.rects?.get(key) ?: return
        origin = rect
        startLocal = local
        lastLocal = local
        pointerY = null
        drag = BoardDrag(key, rect, rect, null)
    }

    fun move(key: String, local: Offset) {
        val frame = frame ?: return
        val start = origin ?: return
        val dx = (local.x - startLocal.x) / frame.zoom
        val dy = (local.y - startLocal.y) / frame.zoom
        // 위젯은 보드 왼쪽·오른쪽·위 밖으로 나가지 않는다. 손가락은 계속 따라가므로 가장자리 분할은 그대로 감지된다.
        val left = (start.left + dx).coerceIn(0.0, (frame.mapping.displayWidth - start.width).coerceAtLeast(0.0))
        val top = (start.top + dy).coerceAtLeast(0.0)
        val raw = Rect(left, top, left + start.width, top + start.height)
        val others = frame.rects.filterKeys { it != key }.values.toList()
        val pointer = Vec2(start.left + local.x / frame.zoom, start.top + local.y / frame.zoom)
        lastLocal = local
        pointerY = pointer.y
        val zone = if (state.arrangement is BoardArrangement.Free) BoardGeometry.zoneAt(pointer, frame.visible, frame.edge) else null
        val snapped = BoardGeometry.snapMove(raw, others, frame.mapping.displayWidth, frame.mapping.gap, frame.snap)
        val target = zone?.let { BoardGeometry.zoneRect(it, frame.visible, frame.mapping.gap) } ?: frame.mapping.cellPreview(raw) ?: snapped
        drag = BoardDrag(key, if (zone == null) snapped else raw, target, zone)
        scrollDirection = directionNow()
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

    /**
     * 끄는 중 손가락이 보이는 영역 위·아래 자동 스크롤 띠에 있으면 -1(위)·1(아래), 아니면 0. 옮길 때마다 갱신한다.
     * 띠는 가장자리 분할 자리 바로 안쪽이라, 맨 끝에 놓으면 분할이고 그 조금 안쪽에 머물면 스크롤된다.
     */
    var scrollDirection by mutableIntStateOf(0)
        private set

    private fun directionNow(): Int {
        val frame = frame ?: return 0
        val y = pointerY ?: return 0
        if (drag?.zone != null) return 0
        // 위쪽 가장자리는 화면 가득 분할 자리라 그 안에서는 이미 위에서 0을 돌려준다. 아래 가운데는 분할 자리가 없다.
        return when {
            y - frame.visible.top <= frame.edge + frame.scrollBand -> -1
            frame.visible.bottom - y <= frame.edge + frame.scrollBand -> 1
            else -> 0
        }
    }

    /** 보드가 [dy]픽셀 스크롤되었다. 틀이 함께 움직였으므로 손가락의 틀 기준 위치를 그만큼 옮겨 다시 계산한다. */
    fun scrolled(key: String, dy: Float) {
        if (drag?.key != key) return
        move(key, lastLocal + Offset(0f, dy))
    }

    fun end() {
        scrollDirection = 0
        val current = drag ?: return
        val frame = frame
        drag = null
        origin = null
        if (frame != null && current.target != frame.rects[current.key]) state.place(current.key, frame.mapping.toReference(current.target))
    }

    fun cancel() {
        scrollDirection = 0
        drag = null
        origin = null
    }
}

/**
 * 한 번 그릴 때의 화면 정보(문서 단위). [visible]은 스크롤된 보이는 영역, [edge]는 분할 자리로 보는 가장자리 폭,
 * [snap]은 붙는 거리, [minSize]는 가장 작은 위젯 크기, [scrollBand]는 가장자리 안쪽 자동 스크롤 띠 폭이다.
 */
internal data class BoardFrame(
    val mapping: BoardMapping,
    val zoom: Float,
    val rects: Map<String, Rect>,
    val visible: Rect,
    val edge: Double,
    val snap: Double,
    val minSize: Double,
    val scrollBand: Double = 0.0,
)
