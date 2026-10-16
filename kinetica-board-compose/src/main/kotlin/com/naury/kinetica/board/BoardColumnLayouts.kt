package com.naury.kinetica.board

import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// 격자 보드는 가장 좁은 열 수의 배치를 노드 위치(변환과 크기)에 저장해 이전 저장본과 같은 형식을 유지한다.
// 다른 열 수에서 사용자가 편집한 배치는 노드 metadata에 열 수별 칸으로 따로 남긴다. 예: {"4":[0,0,2,1],"6":[2,0,2,1]}
internal const val COLUMN_CELLS_KEY = "kinetica.cells"
private const val CELL_FIELDS = 4

/**
 * [columns]열 배치에서 놓인 슬롯마다의 기준 폭 좌표 사각형이며 그리기 순서를 따른다. 가장 좁은 열 수나 자유 배치면 저장된 위치 그대로다.
 */
internal fun KineticaBoardState.layoutRects(document: SceneDocument, columns: Int): Map<String, Rect> {
    val base = baseGrid?.takeIf { columns != it.columns }
        ?: return document.rootOrder.associate { it.value to requireNotNull(KineticaBoardState.rectOf(document.node(it))) }
    val grid = base.withColumns(columns)
    val gap = base.gapUnits()
    return columnLayout(document, base, columns, gap).mapValues { (_, cell) ->
        BoardGeometry.cellRect(cell, KineticaBoardState.REFERENCE_WIDTH, grid, gap)
    }
}

/**
 * [columns]열 배치의 칸. [base]열(가장 좁은 열 수)이면 노드 위치에 가장 가까운 칸이고, 다른 열 수면 그 열 수의 칸을 저장하지 않은 위젯을
 * 좁은 배치를 읽는 순서를 바탕으로 빈칸을 채우며 흘려 넣는다([flowCells]). [gap]은 문서 단위 간격이다.
 */
internal fun columnLayout(document: SceneDocument, base: BoardArrangement.Grid, columns: Int, gap: Double): Map<String, GridCell> {
    val nodes = document.rootOrder.mapNotNull(document::node)
    val baseCells = nodes.associate { node ->
        val rect = KineticaBoardState.rectOf(node) ?: Rect(0.0, 0.0, 1.0, 1.0)
        node.id.value to BoardGeometry.nearestCell(rect, KineticaBoardState.REFERENCE_WIDTH, base, gap)
    }
    if (columns == base.columns) return baseCells
    val stored = nodes.mapNotNull { node -> node.columnCells()[columns]?.let { node.id.value to it } }.toMap()
    val order = nodes.withIndex()
        .sortedWith(
            compareBy({
                baseCells.getValue(it.value.id.value).row
            }, { baseCells.getValue(it.value.id.value).column }, { it.index }),
        )
        .map { it.value.id.value to baseCells.getValue(it.value.id.value) }
    return flowCells(order, stored, columns)
}

/**
 * 가장 좁은 열 수가 아닌 [columns]열 배치에서 [id]를 [cell]로 옮길 때 바꿀 노드. 그 열 수의 칸만 바꾸고 겹치는 위젯은 아래로 민다.
 * 지금 배치 [cells]를 모든 위젯에 함께 남기므로, 처음 편집하는 열 수여도 이후 좁은 배치가 바뀔 때 이 배치가 따라 움직이지 않는다.
 */
internal fun columnUpdates(
    document: SceneDocument,
    cells: Map<String, GridCell>,
    id: String,
    cell: GridCell,
    columns: Int,
): List<SceneNode> {
    val resolved = BoardGeometry.resolveOverlaps(cells + (id to cell), id)
    return document.rootOrder.mapNotNull(document::node).mapNotNull { node ->
        val target = resolved[node.id.value] ?: return@mapNotNull null
        node.takeIf { node.columnCells()[columns] != target }?.withColumnCell(columns, target)
    }
}

/** 보드 폭 [widthDp]에서 보여 줄 배치. 격자면 [override]나 [BoardArrangement.Grid.columnsByWidth]로 고른 열 수로 바꾼다. */
internal fun shownArrangement(arrangement: BoardArrangement, override: GridColumns?, widthDp: Float): BoardArrangement {
    val grid = arrangement as? BoardArrangement.Grid ?: return arrangement
    val rule = override ?: grid.columnsByWidth ?: return grid
    return grid.withColumns(grid.copy(columnsByWidth = rule).columnsFor(widthDp))
}

/** 노드에 저장된 열 수별 칸. 형식이 잘못된 항목은 버린다. */
internal fun SceneNode.columnCells(): Map<Int, GridCell> = metadata[COLUMN_CELLS_KEY]?.let { raw ->
    runCatching {
        Json.parseToJsonElement(raw).jsonObject.mapNotNull { (columns, value) ->
            val count = columns.toIntOrNull() ?: return@mapNotNull null
            val numbers = value.jsonArray.map { it.jsonPrimitive.intOrNull ?: return@mapNotNull null }
            if (numbers.size != CELL_FIELDS) return@mapNotNull null
            val (column, row, columnSpan) = numbers
            val cell = GridCell(column, row, columnSpan, numbers.last())
            val valid = cell.column >= 0 && cell.row >= 0 && cell.columnSpan >= 1 && cell.rowSpan >= 1 &&
                cell.column + cell.columnSpan <= count
            if (valid) count to cell else null
        }.toMap()
    }.getOrNull()
}.orEmpty()

/** [columns]열 배치의 칸을 [cell]로 바꾼 노드. 다른 열 수의 칸과 나머지 metadata는 그대로 둔다. */
internal fun SceneNode.withColumnCell(columns: Int, cell: GridCell): SceneNode {
    val cells = columnCells() + (columns to cell)
    val json = JsonObject(
        cells.toSortedMap().entries.associate { (count, value) ->
            count.toString() to JsonArray(listOf(value.column, value.row, value.columnSpan, value.rowSpan).map(::JsonPrimitive))
        },
    )
    return copy(metadata = metadata + (COLUMN_CELLS_KEY to json.toString()))
}

/** 스택을 만들거나 풀 때 위치를 물려받는 노드가 열 수별 칸도 함께 가져가도록 남길 metadata. */
internal fun SceneNode.columnCellsMetadata(): Map<String, String> = metadata.filterKeys { it == COLUMN_CELLS_KEY }

/**
 * [columns]열 배치. [stored]에 칸이 있는 위젯은 그 칸에 두고, 나머지는 빈칸을 앞에서부터 채우는 dense 방식으로 흘려 넣는다.
 * 위에서 아래, 왼쪽에서 오른쪽으로 빈칸을 훑으며 그 칸에 들어가는 위젯 중 [order]에서 가장 앞선 것을 놓는다. 이 순서로 놓은 결과와
 * 큰 위젯을 먼저 놓은 결과 몇 가지를 견주어 높이가 낮고, 마지막 위젯 앞의 빈칸이 적고, [order]에서 덜 벗어난 쪽을 고른다.
 * 그래서 빈칸이 줄어들 때만 순서가 바뀐다. 차지하는 열 수는 [columns]를 넘지 않는다.
 *
 * @param order 흘려 넣을 순서의 (id, 좁은 배치 칸)
 */
internal fun flowCells(order: List<Pair<String, GridCell>>, stored: Map<String, GridCell>, columns: Int): Map<String, GridCell> {
    val items = order.filter { it.first !in stored }.map { (id, base) ->
        id to
            GridCell(0, 0, base.columnSpan.coerceAtMost(columns), base.rowSpan)
    }
    val fixed = stored.filterKeys { id -> order.any { it.first == id } }.values.toList()
    val rank = items.withIndex().associate { it.value.first to it.index }
    val candidates = listOf<Comparator<Pair<String, GridCell>>?>(
        null,
        compareByDescending { it.second.columnSpan * it.second.rowSpan },
        compareByDescending { it.second.rowSpan },
        compareByDescending { it.second.columnSpan },
    ).map { comparator -> denseFill(comparator?.let(items::sortedWith) ?: items, fixed, columns) }.distinct()
    val flowed = candidates.minWithOrNull(
        compareBy<Map<String, GridCell>>({ layout -> (fixed + layout.values).maxOfOrNull { it.row + it.rowSpan } ?: 0 })
            .thenBy { layout -> gapsBeforeLast(fixed + layout.values, columns) }
            .thenBy { layout -> displacement(layout, rank) },
    ).orEmpty()
    return order.associate { (id, _) -> id to (stored[id] ?: flowed.getValue(id)) }
}

// 빈칸을 앞에서부터 훑으며 그 칸에 들어가는 첫 위젯을 놓는다. [items]의 칸은 크기만 쓴다.
private fun denseFill(items: List<Pair<String, GridCell>>, fixed: List<GridCell>, columns: Int): Map<String, GridCell> {
    val remaining = items.toMutableList()
    val occupied = fixed.toMutableList()
    val placed = LinkedHashMap<String, GridCell>()
    var index = 0
    while (remaining.isNotEmpty()) {
        val column = index % columns
        val row = index / columns
        val free = occupied.none { it.overlaps(GridCell(column, row, 1, 1)) }
        val fit = if (free) {
            remaining.firstOrNull { (_, size) ->
                val candidate = size.copy(column = column, row = row)
                column + size.columnSpan <= columns && occupied.none { it.overlaps(candidate) }
            }
        } else {
            null
        }
        if (fit == null) {
            index++
        } else {
            val cell = fit.second.copy(column = column, row = row)
            remaining.remove(fit)
            occupied += cell
            placed[fit.first] = cell
        }
    }
    return placed
}

// 위에서 아래, 왼쪽에서 오른쪽으로 읽을 때 마지막으로 차지한 칸보다 앞에 있는 빈칸 수.
private fun gapsBeforeLast(cells: List<GridCell>, columns: Int): Int {
    val taken = cells.flatMap { cell ->
        (cell.row until cell.row + cell.rowSpan).flatMap { row ->
            (cell.column until cell.column + cell.columnSpan).map { row * columns + it }
        }
    }.toSet()
    val last = taken.maxOrNull() ?: return 0
    return last + 1 - taken.size
}

// 읽는 순서로 놓인 차례가 [rank]에서 벗어난 정도.
private fun displacement(layout: Map<String, GridCell>, rank: Map<String, Int>): Int = layout.entries
    .sortedWith(compareBy({ it.value.row }, { it.value.column }))
    .withIndex()
    .sumOf { (position, entry) -> abs(position - rank.getValue(entry.key)) }
