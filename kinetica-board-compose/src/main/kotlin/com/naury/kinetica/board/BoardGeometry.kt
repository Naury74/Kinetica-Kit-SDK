package com.naury.kinetica.board

import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Vec2
import kotlin.math.abs
import kotlin.math.roundToInt

/** 화면 가장자리로 끌어 놓을 때의 분할 자리. */
public enum class SnapZone { LeftHalf, RightHalf, TopLeft, TopRight, BottomLeft, BottomRight, Maximize }

/** 격자 위치(칸). 열·행은 0부터, 칸 수는 1 이상. */
public data class GridCell(val column: Int, val row: Int, val columnSpan: Int, val rowSpan: Int) {
    internal fun overlaps(other: GridCell): Boolean = column < other.column + other.columnSpan && other.column < column + columnSpan &&
        row < other.row + other.rowSpan && other.row < row + rowSpan
}

/** 보드 배치 계산. 모든 값은 같은 단위(문서 단위)이며 상태가 없다. */
internal object BoardGeometry {
    /** [pointer]가 보이는 영역 [visible]의 가장자리 [edge] 안에 있으면 그 분할 자리. 모서리가 변보다 먼저다. */
    fun zoneAt(pointer: Vec2, visible: Rect, edge: Double): SnapZone? {
        val left = pointer.x - visible.left < edge
        val right = visible.right - pointer.x < edge
        val top = pointer.y - visible.top < edge
        val bottom = visible.bottom - pointer.y < edge
        return when {
            left && top -> SnapZone.TopLeft
            right && top -> SnapZone.TopRight
            left && bottom -> SnapZone.BottomLeft
            right && bottom -> SnapZone.BottomRight
            left -> SnapZone.LeftHalf
            right -> SnapZone.RightHalf
            top -> SnapZone.Maximize
            else -> null
        }
    }

    /** 분할 자리가 차지하는 사각형. 가장자리와 나뉜 칸 사이에 [gap]을 둔다. */
    fun zoneRect(zone: SnapZone, visible: Rect, gap: Double): Rect {
        val inner = Rect(visible.left + gap, visible.top + gap, visible.right - gap, visible.bottom - gap)
        val midX = (inner.left + inner.right) / 2.0
        val midY = (inner.top + inner.bottom) / 2.0
        val half = gap / 2.0
        return when (zone) {
            SnapZone.Maximize -> inner
            SnapZone.LeftHalf -> Rect(inner.left, inner.top, midX - half, inner.bottom)
            SnapZone.RightHalf -> Rect(midX + half, inner.top, inner.right, inner.bottom)
            SnapZone.TopLeft -> Rect(inner.left, inner.top, midX - half, midY - half)
            SnapZone.TopRight -> Rect(midX + half, inner.top, inner.right, midY - half)
            SnapZone.BottomLeft -> Rect(inner.left, midY + half, midX - half, inner.bottom)
            SnapZone.BottomRight -> Rect(midX + half, midY + half, inner.right, inner.bottom)
        }
    }

    /**
     * 옮기는 사각형 [moving]을 보드 가장자리(간격 [gap] 안쪽)와 다른 위젯 [others]의 변·간격 자리에 [threshold] 안이면 붙인다.
     * 가로·세로를 따로 붙이며, 가장 가까운 후보 하나씩만 쓴다.
     */
    fun snapMove(moving: Rect, others: List<Rect>, boardWidth: Double, gap: Double, threshold: Double): Rect {
        val xs = listOf(gap, boardWidth - gap) + others.flatMap { listOf(it.left, it.right, it.right + gap, it.left - gap) }
        val ys = listOf(gap) + others.flatMap { listOf(it.top, it.bottom, it.bottom + gap, it.top - gap) }
        val dx = nearestShift(listOf(moving.left, moving.right), xs, threshold)
        val dy = nearestShift(listOf(moving.top, moving.bottom), ys, threshold)
        return Rect(moving.left + dx, moving.top + dy, moving.right + dx, moving.bottom + dy)
    }

    /** 크기 조절 중인 오른쪽·아래 변을 같은 방식으로 붙인다. 왼쪽 위는 그대로다. */
    fun snapResize(resizing: Rect, others: List<Rect>, boardWidth: Double, gap: Double, threshold: Double): Rect {
        val xs = listOf(boardWidth - gap) + others.flatMap { listOf(it.right, it.left - gap) }
        val ys = others.flatMap { listOf(it.bottom, it.top - gap) }
        return Rect(
            resizing.left,
            resizing.top,
            resizing.right + nearestShift(listOf(resizing.right), xs, threshold),
            resizing.bottom + nearestShift(listOf(resizing.bottom), ys, threshold),
        )
    }

    private fun nearestShift(edges: List<Double>, targets: List<Double>, threshold: Double): Double = edges
        .flatMap { edge -> targets.map { it - edge } }
        .filter { abs(it) <= threshold }
        .minByOrNull(::abs) ?: 0.0

    /** 격자 한 칸의 너비. 가장자리와 칸 사이에 간격이 있다. */
    fun cellWidth(boardWidth: Double, columns: Int, gap: Double): Double = ((boardWidth - gap * (columns + 1)) / columns).coerceAtLeast(1.0)

    fun cellRect(cell: GridCell, boardWidth: Double, grid: BoardArrangement.Grid, gap: Double): Rect {
        val width = cellWidth(boardWidth, grid.columns, gap)
        val height = width * grid.rowHeightRatio
        val left = gap + cell.column * (width + gap)
        val top = gap + cell.row * (height + gap)
        return Rect(
            left,
            top,
            left + cell.columnSpan * width + (cell.columnSpan - 1) * gap,
            top + cell.rowSpan * height + (cell.rowSpan - 1) * gap,
        )
    }

    /** 자유 좌표 사각형에 가장 가까운 칸. 열은 격자 안으로 맞춘다. */
    fun nearestCell(rect: Rect, boardWidth: Double, grid: BoardArrangement.Grid, gap: Double): GridCell {
        val width = cellWidth(boardWidth, grid.columns, gap)
        val height = width * grid.rowHeightRatio
        val columnSpan = ((rect.width + gap) / (width + gap)).roundToInt().coerceIn(1, grid.columns)
        val rowSpan = ((rect.height + gap) / (height + gap)).roundToInt().coerceAtLeast(1)
        val column = ((rect.left - gap) / (width + gap)).roundToInt().coerceIn(0, grid.columns - columnSpan)
        val row = ((rect.top - gap) / (height + gap)).roundToInt().coerceAtLeast(0)
        return GridCell(column, row, columnSpan, rowSpan)
    }

    /**
     * [fixed] 키의 칸은 그대로 두고, 겹치는 다른 위젯을 겹치지 않는 가장 가까운 아래 행으로 민다. 민 위젯이 또 겹치면 이어서 민다.
     * 위에서부터 차례로 정하므로 결과는 입력 순서와 상관없이 같다.
     */
    fun resolveOverlaps(cells: Map<String, GridCell>, fixed: String): Map<String, GridCell> {
        val placed = linkedMapOf(fixed to cells.getValue(fixed))
        cells.filterKeys {
            it != fixed
        }.entries.sortedWith(compareBy({ it.value.row }, { it.value.column }, { it.key })).forEach { (key, cell) ->
            var candidate = cell
            while (placed.values.any { it.overlaps(candidate) }) candidate = candidate.copy(row = candidate.row + 1)
            placed[key] = candidate
        }
        return cells.keys.associateWith { placed.getValue(it) }
    }

    /**
     * 자유 배치에서 [fixed]를 놓은 뒤 겹치는 다른 위젯을 겹치지 않을 때까지 아래로 민다(간격 [gap]). 민 위젯이 또 겹치면 이어서 민다.
     * 위에 있는 위젯부터 정하므로 결과는 입력 순서와 상관없다.
     */
    fun pushOverlaps(rects: Map<String, Rect>, fixed: String, gap: Double): Map<String, Rect> {
        val placed = linkedMapOf(fixed to rects.getValue(fixed))
        rects.filterKeys {
            it != fixed
        }.entries.sortedWith(compareBy({ it.value.top }, { it.value.left }, { it.key })).forEach { (key, rect) ->
            var candidate = rect
            while (true) {
                val blocking = placed.values.filter { it.intersectsStrictly(candidate) }
                if (blocking.isEmpty()) break
                val top = blocking.maxOf { it.bottom } + gap
                candidate = Rect(candidate.left, top, candidate.right, top + candidate.height)
            }
            placed[key] = candidate
        }
        return rects.keys.associateWith { placed.getValue(it) }
    }

    /**
     * 자유 배치에서 [width]×[height] 위젯이 처음 놓일 자리: 보드 왼쪽 위와 이미 놓인 위젯의 오른쪽·아래 자리 중
     * 겹치지 않고 보드 폭 안에 드는 가장 위(같으면 가장 왼쪽) 자리. 넓은 화면에서는 옆으로 채운다.
     */
    fun firstFreeSpot(width: Double, height: Double, placed: List<Rect>, boardWidth: Double, gap: Double): Rect {
        val candidates =
            listOf(gap to gap) + placed.flatMap { listOf(it.right + gap to it.top, gap to it.bottom + gap, it.left to it.bottom + gap) }
        return candidates
            .map { (left, top) -> Rect(left, top, left + width, top + height) }
            .filter { it.right <= boardWidth - gap + EPSILON && placed.none { other -> other.intersectsStrictly(it) } }
            .minWithOrNull(compareBy({ it.top }, { it.left }))
            ?: Rect(
                gap,
                (placed.maxOfOrNull { it.bottom } ?: 0.0) + gap,
                gap + width,
                (placed.maxOfOrNull { it.bottom } ?: 0.0) + gap + height,
            )
    }

    private const val EPSILON = 1e-6

    private fun Rect.intersectsStrictly(other: Rect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    /** 위젯이 처음 놓일 칸: 위에서부터 왼쪽부터 비어 있는 첫 자리. */
    fun firstFreeCell(occupied: Collection<GridCell>, columnSpan: Int, rowSpan: Int, columns: Int): GridCell {
        val span = columnSpan.coerceAtMost(columns)
        var row = 0
        while (true) {
            for (column in 0..columns - span) {
                val candidate = GridCell(column, row, span, rowSpan)
                if (occupied.none { it.overlaps(candidate) }) return candidate
            }
            row++
        }
    }
}
