package com.naury.kinetica.board

import com.naury.kinetica.core.geometry.Rect

/**
 * 저장 기준 폭 좌표와 현재 화면 폭 좌표를 서로 변환한다. 자유 배치는 가로만 비율대로 바꾸고, 격자 배치는 칸에 맞춘 뒤 현재 폭의 칸 크기로 바꾼다.
 * [mirrored]면 RTL 언어에 맞춰 화면 좌표를 좌우로 뒤집으므로 기본 배치, 분할 영역, 드래그가 모두 오른쪽부터 시작한다.
 * 저장 좌표는 레이아웃 방향과 상관없이 같다.
 */
internal class BoardMapping(
    private val arrangement: BoardArrangement,
    val displayWidth: Double,
    val gap: Double,
    val mirrored: Boolean = false,
) {
    private val scale = displayWidth / KineticaBoardState.REFERENCE_WIDTH

    /** 격자 열 수. 자유 배치면 0. */
    val columns: Int = (arrangement as? BoardArrangement.Grid)?.columns ?: 0

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

    /** 격자 배치에서 드래그 중인 사각형이 놓일 칸. 자유 배치면 `null`. */
    fun cellPreview(display: Rect): Rect? = (arrangement as? BoardArrangement.Grid)?.let { grid ->
        BoardGeometry.cellRect(BoardGeometry.nearestCell(display, displayWidth, grid, gap), displayWidth, grid, gap)
    }
}
