package com.naury.kinetica.board

import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
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
 * 좁은 배치를 읽는 순서대로 흘려 넣는다. [gap]은 문서 단위 간격이다.
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
 * [columns]열 배치. [stored]에 칸이 있는 위젯은 그 칸에 두고, 나머지는 [order] 순서대로 앞 위젯 다음의 첫 빈칸에 흘려 넣는다.
 * 그래서 아직 편집하지 않은 열 수에서도 좁은 배치를 읽는 순서(위에서 아래, 왼쪽에서 오른쪽)가 유지된다. 차지하는 열 수는 [columns]를 넘지 않는다.
 *
 * @param order 흘려 넣을 순서의 (id, 좁은 배치 칸)
 */
internal fun flowCells(order: List<Pair<String, GridCell>>, stored: Map<String, GridCell>, columns: Int): Map<String, GridCell> {
    val occupied = stored.values.toMutableList()
    var cursorRow = 0
    var cursorColumn = 0
    val flowed = order.filter { it.first !in stored }.associate { (id, base) ->
        val span = base.columnSpan.coerceAtMost(columns)
        val cell = firstFreeCellFrom(occupied, GridCell(cursorColumn, cursorRow, span, base.rowSpan), columns)
        occupied += cell
        cursorRow = cell.row
        cursorColumn = cell.column + span
        id to cell
    }
    return order.associate { (id, _) -> id to (stored[id] ?: flowed.getValue(id)) }
}

// [start]의 행과 열부터 찾고, 없으면 다음 행의 처음부터 찾는다. 크기는 [start]와 같다.
private fun firstFreeCellFrom(occupied: List<GridCell>, start: GridCell, columns: Int): GridCell {
    var row = start.row
    var from = start.column
    while (true) {
        for (column in from..columns - start.columnSpan) {
            val candidate = start.copy(column = column, row = row)
            if (occupied.none { it.overlaps(candidate) }) return candidate
        }
        row++
        from = 0
    }
}
