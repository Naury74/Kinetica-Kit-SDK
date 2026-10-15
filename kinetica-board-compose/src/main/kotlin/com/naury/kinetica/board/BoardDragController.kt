package com.naury.kinetica.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Vec2

/**
 * 드래그 중인 위젯. [rect]는 손가락을 따라가는 위치, [target]은 놓았을 때 들어갈 위치로 분할 영역, 격자 칸, 스냅 결과 중 하나다.
 * 모두 화면 폭 기준 문서 단위다. [stackOnto]가 있으면 놓을 때 그 위젯과 스택이 된다. [picker]가 있으면 레이아웃 선택 패널이 떠 있다.
 */
internal data class BoardDrag(
    val key: String,
    val rect: Rect,
    val target: Rect,
    val zone: SnapZone?,
    val stackOnto: String? = null,
    val picker: PickerHover? = null,
)

/** 편집 모드의 이동과 크기 조절. 드래그하는 동안에는 문서를 바꾸지 않고, 손을 뗄 때 한 번만 [KineticaBoardState.place]를 호출한다. */
internal class BoardDragController(private val state: KineticaBoardState) {
    var drag by mutableStateOf<BoardDrag?>(null)
        private set

    /** 그릴 때마다 갱신하는 화면 정보. */
    var frame: BoardFrame? = null

    private var origin: Rect? = null
    private var startLocal: Offset = Offset.Zero
    private var lastLocal: Offset = Offset.Zero
    private var pointerY: Double? = null

    /** 위쪽 가장자리에 닿아 레이아웃 선택 패널이 열려 있는지. 손가락이 패널 아래로 내려가면 닫힌다. */
    private var pickerOpen = false

    /** 현재 화면에 맞춘 레이아웃 선택 패널 모양. */
    fun picker(): SnapLayouts.Picker? = frame?.let { SnapLayouts.picker(it.visible, it.mapping.gap, it.unit) }

    /**
     * 드래그를 시작한다. [local]은 위젯 편집 테두리 안에서의 손가락 위치(픽셀)이며, 테두리는 드래그하는 동안 움직이지 않는다.
     * 이후 [move]와 [resize]도 같은 테두리 기준 위치를 받으므로 touch slop과 상관없이 손가락을 정확히 따라간다.
     */
    fun start(key: String, local: Offset) {
        val rect = frame?.rects?.get(key) ?: return
        origin = rect
        startLocal = local
        lastLocal = local
        pointerY = null
        pickerOpen = false
        drag = BoardDrag(key, rect, rect, null)
    }

    fun move(key: String, local: Offset) {
        val frame = frame ?: return
        val start = origin ?: return
        val dx = (local.x - startLocal.x) / frame.zoom
        val dy = (local.y - startLocal.y) / frame.zoom
        // 위젯은 보드의 왼쪽, 오른쪽, 위쪽 밖으로 나가지 않는다. 손가락 위치는 계속 따라가므로 가장자리 분할은 그대로 감지된다.
        val left = (start.left + dx).coerceIn(0.0, (frame.mapping.displayWidth - start.width).coerceAtLeast(0.0))
        val top = (start.top + dy).coerceAtLeast(0.0)
        val raw = Rect(left, top, left + start.width, top + start.height)
        val others = frame.rects.filterKeys { it != key }.values.toList()
        val pointer = Vec2(start.left + local.x / frame.zoom, start.top + local.y / frame.zoom)
        lastLocal = local
        pointerY = pointer.y
        val zone = if (state.arrangement is BoardArrangement.Free) BoardGeometry.zoneAt(pointer, frame.visible, frame.edge) else null
        if (pickFromLayouts(key, raw, pointer, zone)) return
        val snapped = BoardGeometry.snapMove(raw, others, frame.mapping.displayWidth, frame.mapping.gap, frame.snap)
        // 손가락이 다른 위젯의 가운데에 있으면 그 위젯과 스택으로 묶는다. 가장자리 근처에서는 평소처럼 스냅하거나 밀어낸다.
        val onto = if (zone ==
            null
        ) {
            frame.rects.entries.firstOrNull { (id, rect) -> id != key && rect.center(STACK_AREA).contains(pointer) }?.key
        } else {
            null
        }
        if (onto != null) {
            drag = BoardDrag(key, raw, frame.rects.getValue(onto), null, stackOnto = onto)
            scrollDirection = directionNow()
            return
        }
        val target = zone?.let { BoardGeometry.zoneRect(it, frame.visible, frame.mapping.gap) } ?: frame.mapping.cellPreview(raw) ?: snapped
        drag = BoardDrag(key, if (zone == null) snapped else raw, target, zone)
        scrollDirection = directionNow()
    }

    /**
     * 크기를 조절한다. [local]은 핸들 안에서의 손가락 위치다. 위쪽 변과 시작쪽 변(RTL이면 오른쪽)은 그대로 두며,
     * [frame]의 최소 크기보다 작아지지 않는다.
     */
    fun resize(key: String, local: Offset) {
        val frame = frame ?: return
        val start = origin ?: return
        val width = frame.mapping.displayWidth
        // RTL 화면은 좌우를 뒤집어 오른쪽 아래 핸들과 같은 계산을 하고 결과를 다시 뒤집는다.
        val flip = { rect: Rect ->
            if (frame.mapping.mirrored) Rect(width - rect.right, rect.top, width - rect.left, rect.bottom) else rect
        }
        val dx = (local.x - startLocal.x) / frame.zoom * if (frame.mapping.mirrored) -1 else 1
        val base = flip(start)
        val raw = Rect(
            base.left,
            base.top,
            maxOf(base.left + frame.minSize, base.right + dx).coerceAtMost(width),
            maxOf(base.top + frame.minSize, base.bottom + (local.y - startLocal.y) / frame.zoom),
        )
        val others = frame.rects.filterKeys { it != key }.values.map(flip)
        val snapped = flip(BoardGeometry.snapResize(raw, others, width, frame.mapping.gap, frame.snap))
        drag = BoardDrag(key, snapped, frame.mapping.cellPreview(flip(raw)) ?: snapped, null)
    }

    /**
     * 전체 화면 분할 영역인 위쪽 가장자리에 닿으면 레이아웃 선택 패널을 연다. 열려 있는 동안에는 손가락이 올라간 칸에 위젯을 놓는다.
     * 손가락이 패널 아래로 [BoardFrame.edge]만큼 내려가면 패널을 닫고 일반 드래그로 돌아간다. 패널이 입력을 처리했으면 `true`.
     */
    private fun pickFromLayouts(key: String, raw: Rect, pointer: Vec2, zone: SnapZone?): Boolean {
        if (zone == SnapZone.Maximize) pickerOpen = true
        val frame = frame?.takeIf { pickerOpen } ?: return false
        val picker = picker()?.takeIf { pointer.y <= it.panel.bottom + frame.edge }
        if (picker == null) {
            pickerOpen = false
            return false
        }
        val slot = picker.slotAt(pointer.x, pointer.y)
        val target = slot?.let { SnapLayouts.zoneRect(SnapLayouts.layouts[it.layout][it.zone], frame.visible, frame.mapping.gap) } ?: raw
        drag = BoardDrag(key, raw, target, null, picker = PickerHover(slot?.layout, slot?.zone))
        scrollDirection = 0
        return true
    }

    /**
     * 드래그 중 손가락이 보이는 영역의 위쪽 자동 스크롤 영역에 있으면 -1, 아래쪽이면 1, 그 밖이면 0이다. 이동할 때마다 갱신한다.
     * 자동 스크롤 영역은 가장자리 분할 영역 바로 안쪽에 있어서, 맨 끝에 놓으면 분할 배치되고 조금 안쪽에 머물면 스크롤된다.
     */
    var scrollDirection by mutableIntStateOf(0)
        private set

    private fun directionNow(): Int {
        val frame = frame ?: return 0
        val y = pointerY ?: return 0
        if (drag?.zone != null || drag?.picker != null) return 0
        // 위쪽 가장자리는 전체 화면 분할 영역이라 위에서 이미 0을 돌려준다. 아래쪽 가운데에는 분할 영역이 없다.
        return when {
            y - frame.visible.top <= frame.edge + frame.scrollBand -> -1
            frame.visible.bottom - y <= frame.edge + frame.scrollBand -> 1
            else -> 0
        }
    }

    /** 보드가 [dy]픽셀 스크롤되었을 때 부른다. 테두리도 함께 움직였으므로 테두리 기준 손가락 위치를 그만큼 옮겨 다시 계산한다. */
    fun scrolled(key: String, dy: Float) {
        if (drag?.key != key) return
        move(key, lastLocal + Offset(0f, dy))
    }

    fun end() {
        scrollDirection = 0
        pickerOpen = false
        val current = drag ?: return
        val frame = frame
        drag = null
        origin = null
        when {
            current.stackOnto != null -> state.stack(current.stackOnto, current.key)

            frame != null && current.target != frame.rects[current.key] -> state.place(
                current.key,
                frame.mapping.toReference(current.target),
            )
        }
    }

    fun cancel() {
        scrollDirection = 0
        pickerOpen = false
        drag = null
        origin = null
    }
}

/**
 * 한 번 그릴 때의 화면 정보이며 값은 문서 단위다. [visible]은 스크롤된 보이는 영역, [edge]는 분할 영역으로 보는 가장자리 폭,
 * [snap]은 스냅 거리, [minSize]는 위젯 최소 크기, [scrollBand]는 가장자리 안쪽 자동 스크롤 영역의 폭이다.
 *
 * 스크롤 위치는 드래그 중 필요할 때마다 [scrollTop]에서 읽는다. 컴포지션 단계에서 읽으면 스크롤할 때마다 보드 전체가 재구성된다.
 */
internal data class BoardFrame(
    val mapping: BoardMapping,
    val zoom: Float,
    val rects: Map<String, Rect>,
    val edge: Double,
    val snap: Double,
    val minSize: Double,
    val scrollBand: Double = 0.0,
    val viewportHeight: Double = 0.0,
    val bottomInset: Double = 0.0,
    val scrollTop: () -> Double = { 0.0 },
    /** 보드 바깥 상자에서 창에 실제로 보이는 세로 범위(문서 단위). `null`이면 전부 보인다고 본다. 컴포지션 밖에서 필요할 때 읽는다. */
    val clip: () -> ClosedFloatingPointRange<Double>? = { null },
    /** 1dp에 해당하는 문서 단위. */
    val unit: Double = KineticaBoardState.UNITS,
) {
    val visible: Rect
        get() {
            val top = scrollTop()
            val shown = clip()
            val visibleTop = top + (shown?.start ?: 0.0)
            val visibleBottom = top + minOf(viewportHeight - bottomInset, shown?.endInclusive ?: Double.MAX_VALUE)
            return Rect(0.0, visibleTop, mapping.displayWidth, maxOf(visibleTop, visibleBottom))
        }
}

/** 가운데 [fraction] 비율 영역. */
private fun Rect.center(fraction: Double): Rect {
    val insetX = width * (1 - fraction) / 2
    val insetY = height * (1 - fraction) / 2
    return Rect(left + insetX, top + insetY, right - insetX, bottom - insetY)
}

/** 스택으로 묶는 가운데 영역의 비율. */
private const val STACK_AREA = 0.4
