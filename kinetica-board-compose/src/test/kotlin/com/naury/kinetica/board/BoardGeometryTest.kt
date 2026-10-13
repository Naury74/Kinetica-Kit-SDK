package com.naury.kinetica.board

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Vec2
import org.junit.Test

class BoardGeometryTest {
    private val screen = Rect(0.0, 1000.0, 1000.0, 3000.0)

    @Test
    fun `가장자리·모서리로 끌면 분할 자리가 정해진다`() {
        assertThat(BoardGeometry.zoneAt(Vec2(10.0, 2000.0), screen, 50.0)).isEqualTo(SnapZone.LeftHalf)
        assertThat(BoardGeometry.zoneAt(Vec2(990.0, 2000.0), screen, 50.0)).isEqualTo(SnapZone.RightHalf)
        assertThat(BoardGeometry.zoneAt(Vec2(500.0, 1010.0), screen, 50.0)).isEqualTo(SnapZone.Maximize)
        assertThat(BoardGeometry.zoneAt(Vec2(10.0, 1010.0), screen, 50.0)).isEqualTo(SnapZone.TopLeft)
        assertThat(BoardGeometry.zoneAt(Vec2(990.0, 2990.0), screen, 50.0)).isEqualTo(SnapZone.BottomRight)
        assertThat(BoardGeometry.zoneAt(Vec2(500.0, 2000.0), screen, 50.0)).isNull()
    }

    @Test
    fun `분할 자리는 보이는 영역을 간격을 두고 나눈다`() {
        assertThat(BoardGeometry.zoneRect(SnapZone.LeftHalf, screen, 20.0)).isEqualTo(Rect(20.0, 1020.0, 490.0, 2980.0))
        assertThat(BoardGeometry.zoneRect(SnapZone.RightHalf, screen, 20.0)).isEqualTo(Rect(510.0, 1020.0, 980.0, 2980.0))
        assertThat(BoardGeometry.zoneRect(SnapZone.BottomRight, screen, 20.0)).isEqualTo(Rect(510.0, 2010.0, 980.0, 2980.0))
        assertThat(BoardGeometry.zoneRect(SnapZone.Maximize, screen, 20.0)).isEqualTo(Rect(20.0, 1020.0, 980.0, 2980.0))
    }

    @Test
    fun `옮기면 다른 위젯 옆 간격 자리와 보드 가장자리에 붙는다`() {
        val other = Rect(20.0, 20.0, 300.0, 200.0)

        val snapped = BoardGeometry.snapMove(Rect(326.0, 25.0, 526.0, 125.0), listOf(other), 1000.0, 20.0, 10.0)
        val edge = BoardGeometry.snapMove(Rect(785.0, 400.0, 985.0, 500.0), listOf(other), 1000.0, 20.0, 10.0)

        assertThat(snapped).isEqualTo(Rect(320.0, 20.0, 520.0, 120.0))
        assertThat(edge.right).isEqualTo(980.0)
    }

    @Test
    fun `격자 칸 위치와 가장 가까운 칸`() {
        val grid = BoardArrangement.Grid(columns = 4)
        val cell = GridCell(1, 2, 2, 1)

        val rect = BoardGeometry.cellRect(cell, 1000.0, grid, 20.0)

        assertThat(rect).isEqualTo(Rect(265.0, 510.0, 735.0, 735.0))
        assertThat(BoardGeometry.nearestCell(Rect(270.0, 500.0, 720.0, 740.0), 1000.0, grid, 20.0)).isEqualTo(cell)
    }

    @Test
    fun `격자에서 겹치는 위젯은 겹치지 않을 때까지 아래로 밀린다`() {
        val cells = mapOf(
            "moved" to GridCell(0, 0, 2, 1),
            "a" to GridCell(1, 0, 1, 1),
            "b" to GridCell(1, 1, 1, 1),
            "far" to GridCell(3, 0, 1, 1),
        )

        val resolved = BoardGeometry.resolveOverlaps(cells, "moved")

        assertThat(resolved.getValue("a")).isEqualTo(GridCell(1, 1, 1, 1))
        assertThat(resolved.getValue("b")).isEqualTo(GridCell(1, 2, 1, 1))
        assertThat(resolved.getValue("far")).isEqualTo(GridCell(3, 0, 1, 1))
    }

    @Test
    fun `자유 배치에서 놓은 위젯과 겹치는 위젯은 아래로 이어서 밀린다`() {
        val rects = mapOf(
            "half" to Rect(500.0, 0.0, 1000.0, 600.0),
            "chart" to Rect(0.0, 400.0, 1000.0, 700.0),
            "below" to Rect(0.0, 720.0, 300.0, 800.0),
            "left" to Rect(0.0, 0.0, 480.0, 300.0),
        )

        val pushed = BoardGeometry.pushOverlaps(rects, "half", 20.0)

        assertThat(pushed.getValue("chart")).isEqualTo(Rect(0.0, 620.0, 1000.0, 920.0))
        assertThat(pushed.getValue("below")).isEqualTo(Rect(0.0, 940.0, 300.0, 1020.0))
        assertThat(pushed.getValue("left")).isEqualTo(rects.getValue("left"))
    }

    @Test
    fun `첫 빈칸은 위에서 왼쪽부터 찾는다`() {
        val occupied = listOf(GridCell(0, 0, 2, 1), GridCell(2, 0, 2, 1))

        assertThat(BoardGeometry.firstFreeCell(occupied, 1, 1, 4)).isEqualTo(GridCell(0, 1, 1, 1))
        assertThat(BoardGeometry.firstFreeCell(occupied, 6, 1, 4)).isEqualTo(GridCell(0, 1, 4, 1))
    }
}
