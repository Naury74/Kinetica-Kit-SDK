package com.naury.kinetica.board

import com.naury.kinetica.core.geometry.Rect

/**
 * 저장 기준 폭 좌표와 지금 화면 폭 좌표 사이 변환. 자유 배치는 가로만 비율대로, 격자 배치는 칸으로 맞춘 뒤 그 폭의 칸 크기로 바꾼다.
 * [mirrored]면(오른쪽에서 왼쪽으로 쓰는 언어) 화면 좌표를 좌우로 뒤집어,
 * 처음 배치·분할 자리·끌기가 모두 오른쪽부터다. 저장 좌표는 방향과 상관없이 같다.
 */
internal class BoardMapping(
    private val arrangement: BoardArrangement,
    val displayWidth: Double,
    val gap: Double,
    val mirrored: Boolean = false,
) {
    private val scale = displayWidth / KineticaBoardState.REFERENCE_WIDTH

    fun toDisplay(reference: Rect): Rect = mirror(ltrDisplay(reference))

    fun toReference(display: Rect): Rect = ltrReference(mirror(display))

    private fun mirror(rect: Rect): Rect =
        if (mirrored) Rect(displayWidth - rect.right, rect.top, displayWidth - rect.left, rect.bottom) else rect

    private fun ltrDisplay(reference: Rect): Rect = when (arrangement) {
        is BoardArrangement.Free -> Rect(reference.left * scale, reference.top, reference.right * scale, reference.bottom)

        is BoardArrangement.Grid -> BoardGeometry.cellRect(
            BoardGeometry.nearestCell(reference, KineticaBoardState.REFERENCE_WIDTH, arrangement, gap),
            displayWidth,
            arrangement,
            gap,
        )
    }

    private fun ltrReference(display: Rect): Rect = when (arrangement) {
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
