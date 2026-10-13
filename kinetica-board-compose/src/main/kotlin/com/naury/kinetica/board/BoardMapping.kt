package com.naury.kinetica.board

import com.naury.kinetica.core.geometry.Rect

/**
 * 저장 기준 폭 좌표와 지금 화면 폭 좌표 사이 변환. 자유 배치는 가로만 비율대로, 격자 배치는 칸으로 맞춘 뒤 그 폭의 칸 크기로 바꾼다.
 */
internal class BoardMapping(private val arrangement: BoardArrangement, val displayWidth: Double, val gap: Double) {
    private val scale = displayWidth / KineticaBoardState.REFERENCE_WIDTH

    fun toDisplay(reference: Rect): Rect = when (arrangement) {
        is BoardArrangement.Free -> Rect(reference.left * scale, reference.top, reference.right * scale, reference.bottom)

        is BoardArrangement.Grid -> BoardGeometry.cellRect(
            BoardGeometry.nearestCell(reference, KineticaBoardState.REFERENCE_WIDTH, arrangement, gap),
            displayWidth,
            arrangement,
            gap,
        )
    }

    fun toReference(display: Rect): Rect = when (arrangement) {
        is BoardArrangement.Free -> Rect(display.left / scale, display.top, display.right / scale, display.bottom)

        is BoardArrangement.Grid -> BoardGeometry.cellRect(
            BoardGeometry.nearestCell(display, displayWidth, arrangement, gap),
            KineticaBoardState.REFERENCE_WIDTH,
            arrangement,
            gap,
        )
    }

    /** 격자 배치에서 끌고 있는 사각형이 놓일 칸. 자유 배치는 `null`. */
    fun cellPreview(display: Rect): Rect? = (arrangement as? BoardArrangement.Grid)?.let { grid ->
        BoardGeometry.cellRect(BoardGeometry.nearestCell(display, displayWidth, grid, gap), displayWidth, grid, gap)
    }
}
