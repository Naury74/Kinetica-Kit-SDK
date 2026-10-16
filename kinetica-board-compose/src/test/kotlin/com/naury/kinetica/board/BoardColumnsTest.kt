package com.naury.kinetica.board

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BoardColumnsTest {
    private val grid = BoardArrangement.Grid(columns = 2, columnsByWidth = GridColumns.Adaptive(160f, maxColumns = 4))

    private val catalog = listOf("a", "b", "c", "d").map { BoardWidget(it, it.uppercase()) } +
        BoardWidget("e", "E", placedByDefault = false)

    private fun KineticaBoardState.cells(columns: Int): Map<String, GridCell> {
        activeColumns = columns
        val shown = grid.withColumns(columns)
        return placedKeys.associateWith { id ->
            BoardGeometry.nearestCell(rectOf(id)!!, KineticaBoardState.REFERENCE_WIDTH, shown, grid.gapUnits())
        }
    }

    private fun KineticaBoardState.placeCell(id: String, cell: GridCell): Boolean =
        place(id, BoardGeometry.cellRect(cell, KineticaBoardState.REFERENCE_WIDTH, grid.withColumns(activeColumns), grid.gapUnits()))

    private fun cell(column: Int, row: Int) = GridCell(column, row, 1, 1)

    @Test
    fun `최소 칸 폭과 폭 구간으로 열 수를 고른다`() {
        assertThat(listOf(300f, 393f, 600f, 852f, 1280f).map(grid::columnsFor)).containsExactly(2, 2, 3, 4, 4).inOrder()
        val wide = BoardArrangement.Grid(2, columnsByWidth = GridColumns.Adaptive(160f))
        assertThat(wide.columnsFor(1280f)).isEqualTo(7)

        val steps = BoardArrangement.Grid(2, columnsByWidth = GridColumns.breakpoints(600f to 4, 840f to 6))
        assertThat(listOf(599f, 600f, 839f, 900f).map(steps::columnsFor)).containsExactly(2, 4, 4, 6).inOrder()

        // 규칙이 없으면 예전처럼 열 수가 그대로다. 폭을 아직 모르면 가장 좁은 열 수를 쓴다.
        assertThat(BoardArrangement.Grid(4).columnsFor(2000f)).isEqualTo(4)
        assertThat(grid.columnsFor(Float.NaN)).isEqualTo(2)
        assertThat(grid.columnsFor(0f)).isEqualTo(2)
    }

    @Test
    fun `처음 넓어지면 좁은 배치를 읽는 순서대로 흘려 넣고 기록에는 남기지 않는다`() {
        val state = KineticaBoardState.create(catalog, grid)

        assertThat(state.cells(2)).containsExactly("a", cell(0, 0), "b", cell(1, 0), "c", cell(0, 1), "d", cell(1, 1))
        assertThat(state.cells(4)).containsExactly("a", cell(0, 0), "b", cell(1, 0), "c", cell(2, 0), "d", cell(3, 0))
        assertThat(state.history.state.value.canUndo).isFalse()
    }

    @Test
    fun `넓은 배치에서 옮기면 넓은 배치에만 남고 좁음 넓음 좁음을 오가도 두 배치가 그대로다`() {
        val state = KineticaBoardState.create(catalog, grid)
        val narrow = state.cells(2)
        val narrowDocument = state.document.value.nodes.mapValues { it.value.transform }

        state.activeColumns = 4
        assertThat(state.placeCell("a", cell(3, 1))).isTrue()
        val wide = state.cells(4)

        assertThat(wide).containsExactly("a", cell(3, 1), "b", cell(1, 0), "c", cell(2, 0), "d", cell(3, 0))
        assertThat(state.cells(2)).isEqualTo(narrow)
        assertThat(state.document.value.nodes.mapValues { it.value.transform }).isEqualTo(narrowDocument)
        assertThat(state.cells(4)).isEqualTo(wide)
        assertThat(state.cells(2)).isEqualTo(narrow)

        // 좁은 배치를 바꿔도 한 번 편집한 넓은 배치는 따라 움직이지 않는다.
        state.activeColumns = 2
        assertThat(state.placeCell("d", cell(0, 3))).isTrue()
        assertThat(state.cells(2).getValue("d")).isEqualTo(cell(0, 3))
        assertThat(state.cells(4)).isEqualTo(wide)
    }

    @Test
    fun `넓은 배치 편집과 키보드 이동도 한 단계씩 되돌린다`() {
        val state = KineticaBoardState.create(catalog, grid)
        val flowed = state.cells(4)

        state.placeCell("a", cell(3, 1))
        val moved = state.cells(4)
        // 넓은 배치에서는 키보드와 TalkBack 이동도 넓은 배치의 한 칸씩이다.
        assertThat(state.moveBy("b", -NUDGE_DP, 0f)).isTrue()
        assertThat(state.cells(4).getValue("b")).isEqualTo(cell(0, 0))

        state.history.undo()
        assertThat(state.cells(4)).isEqualTo(moved)
        state.history.undo()
        assertThat(state.cells(4)).isEqualTo(flowed)
    }

    @Test
    fun `저장했다 다시 열면 열 수마다의 배치를 되살리고 이전 형식 저장본도 읽는다`() {
        val state = KineticaBoardState.create(catalog, grid)
        state.activeColumns = 4
        state.placeCell("a", cell(3, 1))
        val wide = state.cells(4)
        val narrow = state.cells(2)

        val reopened = KineticaBoardState.create(catalog, grid, state.toJson())
        assertThat(reopened.restoreFailed).isFalse()
        assertThat(reopened.cells(2)).isEqualTo(narrow)
        assertThat(reopened.cells(4)).isEqualTo(wide)

        // 열 수 규칙이 없는 이전 버전으로 열어도 좁은 배치가 그대로다.
        val plain = BoardArrangement.Grid(columns = 2)
        val older = KineticaBoardState.create(catalog, plain, state.toJson())
        assertThat(older.restoreFailed).isFalse()
        reopened.activeColumns = 2
        assertThat(older.placedKeys.map(older::rectOf)).isEqualTo(reopened.placedKeys.map(reopened::rectOf))

        // 열 수 규칙이 없던 때의 저장본은 좁은 배치로 읽고, 넓은 배치는 흘려 넣는다.
        val legacy = KineticaBoardState.create(catalog, plain).apply { place("d", rectOf("a")!!) }.toJson()
        assertThat(legacy).doesNotContain(COLUMN_CELLS_KEY)
        val upgraded = KineticaBoardState.create(catalog, grid, legacy)
        assertThat(upgraded.cells(2).getValue("d")).isEqualTo(cell(0, 0))
        assertThat(upgraded.cells(4).values.toSet()).containsExactly(cell(0, 0), cell(1, 0), cell(2, 0), cell(3, 0))
        assertThat(upgraded.cells(4).getValue("d")).isEqualTo(cell(0, 0))
    }

    @Test
    fun `기억한 넓은 배치에 추가하면 두 배치 모두 빈칸에 놓인다`() {
        val state = KineticaBoardState.create(catalog, grid)
        state.activeColumns = 4
        state.placeCell("a", cell(3, 1))

        assertThat(state.add("e")).isTrue()

        assertThat(state.cells(4).getValue("e")).isEqualTo(cell(0, 0))
        assertThat(state.cells(2).getValue("e")).isEqualTo(cell(0, 2))
    }

    @Test
    fun `넓은 배치에서 묶으면 대상 자리를 열 수마다 물려받고 풀어도 겹치지 않는다`() {
        val state = KineticaBoardState.create(catalog, grid)
        state.activeColumns = 4
        state.placeCell("a", cell(3, 1))

        val stack = state.stack("c", "b")!!
        assertThat(state.cells(4).getValue(stack)).isEqualTo(cell(2, 0))
        assertThat(state.cells(2).getValue(stack)).isEqualTo(cell(0, 1))

        state.activeColumns = 4
        assertThat(state.unstack(stack)).isTrue()
        listOf(2, 4).forEach { columns ->
            val cells = state.cells(columns).values.toList()
            assertThat(cells.indices.none { i -> cells.indices.any { j -> i < j && cells[i].overlaps(cells[j]) } }).isTrue()
        }
        assertThat(state.cells(4).getValue("c")).isEqualTo(cell(2, 0))
    }

    @Test
    fun `여러 칸 위젯은 열 수를 넘지 않게 줄여 흘려 넣는다`() {
        val wideCatalog = listOf(BoardWidget("big", "Big", columnSpan = 4, rowSpan = 2), BoardWidget("x", "X"))
        val state = KineticaBoardState.create(wideCatalog, grid)

        assertThat(state.cells(2)).containsExactly("big", GridCell(0, 0, 2, 2), "x", cell(0, 2))
        assertThat(state.cells(4)).containsExactly("big", GridCell(0, 0, 2, 2), "x", cell(2, 0))
    }

    @Test
    fun `처음 넓어질 때 앞쪽 빈칸을 채워 빈칸을 남기지 않는다`() {
        // 쇼케이스 대시보드와 같은 구성: 2열에서 1x1, 1x2, 1x1, 2x1. 예전에는 3열에서 1x2 옆과 아래가 비었다.
        val dashboard = listOf(
            BoardWidget("sales", "Sales"),
            BoardWidget("orders", "Orders", rowSpan = 2),
            BoardWidget("chart", "Chart", columnSpan = 2),
            BoardWidget("clock", "Clock"),
        )
        val wide = BoardArrangement.Grid(columns = 2, columnsByWidth = GridColumns.Adaptive(160f, maxColumns = 6))
        val state = KineticaBoardState.create(dashboard, wide)
        listOf(3, 4).forEach { columns ->
            state.activeColumns = columns
            val cells = state.placedKeys.associateWith { id ->
                BoardGeometry.nearestCell(
                    state.rectOf(id)!!,
                    KineticaBoardState.REFERENCE_WIDTH,
                    wide.withColumns(columns),
                    wide.gapUnits(),
                )
            }
            val taken = cells.values.flatMap { c ->
                (c.row until c.row + c.rowSpan).flatMap { r ->
                    (c.column until c.column + c.columnSpan).map {
                        r *
                            columns +
                            it
                    }
                }
            }
            // 두 줄에 모두 들어가고, 마지막 위젯 앞에 빈칸이 없다.
            assertThat(cells.values.maxOf { it.row + it.rowSpan }).isEqualTo(2)
            assertThat(taken.toSet()).hasSize(taken.size)
            assertThat(taken.max() + 1).isEqualTo(taken.size)
        }
    }

    @Test
    fun `빈칸을 채울 때 순서는 바꿔도 크기와 겹치지 않음은 지키고 저장된 칸은 그대로 둔다`() {
        val order = listOf("a" to GridCell(0, 0, 1, 1), "wide" to GridCell(0, 1, 2, 1), "b" to GridCell(0, 2, 1, 1))
        // 3열에서 b가 wide보다 먼저 첫 줄 빈칸에 들어간다.
        assertThat(flowCells(order, emptyMap(), 3))
            .containsExactly("a", GridCell(0, 0, 1, 1), "wide", GridCell(1, 0, 2, 1), "b", GridCell(0, 1, 1, 1))
        // 저장된 칸이 있는 위젯은 움직이지 않고 나머지가 그 둘레의 빈칸을 채운다.
        val stored = mapOf("wide" to GridCell(0, 0, 2, 1))
        assertThat(flowCells(order, stored, 3))
            .containsExactly("a", GridCell(2, 0, 1, 1), "wide", GridCell(0, 0, 2, 1), "b", GridCell(0, 1, 1, 1))
        // 모두 1x1이면 읽는 순서 그대로다.
        val plain = (0 until 5).map { "w$it" to GridCell(it % 2, it / 2, 1, 1) }
        assertThat(flowCells(plain, emptyMap(), 4).values.map { it.row * 4 + it.column }).containsExactly(0, 1, 2, 3, 4).inOrder()
    }

    @Test
    fun `열 수는 Compose 상태라 읽은 곳이 바뀜을 알 수 있다`() {
        val state = KineticaBoardState.create(catalog, grid)
        val reads = mutableListOf<Any>()
        val snapshot = androidx.compose.runtime.snapshots.Snapshot.takeSnapshot(readObserver = { reads += it })
        try {
            snapshot.enter { state.activeColumns }
        } finally {
            snapshot.dispose()
        }
        assertThat(reads).isNotEmpty()

        val changed = mutableListOf<Any>()
        val handle = androidx.compose.runtime.snapshots.Snapshot.registerApplyObserver { objects, _ -> changed.addAll(objects) }
        try {
            state.activeColumns = 4
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        } finally {
            handle.dispose()
        }
        assertThat(changed).containsAnyIn(reads)
    }
}
