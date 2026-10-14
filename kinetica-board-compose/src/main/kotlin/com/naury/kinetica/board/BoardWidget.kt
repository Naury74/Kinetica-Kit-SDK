package com.naury.kinetica.board

import com.naury.kinetica.core.scene.isValidDataKey

/**
 * 보드에 놓을 수 있는 호스트 위젯 하나. 보드는 [key]와 배치만 저장하고, 내용은 호스트가 [key]로 그린다.
 *
 * @property key 위젯 키. 영문자·숫자와 `.` `_` `-`로 된 1..128자. [multiple]이 아니면 보드 안에서 하나만 놓인다.
 * @property label 위젯 추가 목록에 보이는 이름
 * @property widthDp 처음 놓을 때 너비(dp, 0보다 큼). 격자 배치에서는 [columnSpan]을 쓴다.
 * @property heightDp 처음 놓을 때 높이(dp, 0보다 큼). 격자 배치에서는 [rowSpan]을 쓴다.
 * @property columnSpan 격자 배치에서 차지하는 열 수(1 이상)
 * @property rowSpan 격자 배치에서 차지하는 행 수(1 이상)
 * @property placedByDefault 저장된 배치가 없거나 앱 업데이트로 새로 생긴 위젯일 때 보드에 바로 놓을지
 * @property removable `false`면 사용자가 보드에서 뺄 수 없다(필수 위젯)
 * @property multiple `true`면 같은 위젯을 여러 개 놓을 수 있다(메모 등). 놓인 자리마다 id가 다르다([KineticaBoardState.placedKeys]).
 * @throws IllegalArgumentException 키 형식이나 크기가 범위를 벗어날 때
 */
public data class BoardWidget @JvmOverloads constructor(
    val key: String,
    val label: String,
    val widthDp: Float = 160f,
    val heightDp: Float = 120f,
    val columnSpan: Int = 1,
    val rowSpan: Int = 1,
    val placedByDefault: Boolean = true,
    val removable: Boolean = true,
    val multiple: Boolean = false,
) {
    init {
        require(isValidDataKey(key)) { "위젯 키 형식이 올바르지 않습니다." }
        require(widthDp.isFinite() && heightDp.isFinite() && widthDp > 0f && heightDp > 0f) { "위젯 크기는 유한한 양수여야 합니다." }
        require(columnSpan >= 1 && rowSpan >= 1) { "격자 칸 수는 1 이상이어야 합니다." }
    }
}

/** 보드에서 위젯을 놓는 방식. */
public sealed interface BoardArrangement {
    /**
     * 자유 배치. 위치·크기를 마음대로 정하고, 다른 위젯·보드 가장자리에 붙으며, 화면 가장자리·모서리로 끌어 놓으면
     * 반·4분의 1·전체 화면으로 분할 배치된다(데스크톱 창 분할처럼). 화면 폭이 바뀌면 가로 비율을 유지한다.
     */
    public data class Free @JvmOverloads constructor(val gapDp: Float = 12f) : BoardArrangement {
        init {
            require(gapDp.isFinite() && gapDp >= 0f) { "간격은 0 이상이어야 합니다." }
        }
    }

    /**
     * 격자 배치(대시보드 타일). 위젯은 칸 단위로 놓이고 겹치면 아래로 밀린다. 칸 높이는 칸 너비 × [rowHeightRatio]다.
     *
     * @throws IllegalArgumentException 열 수가 1..24 밖이거나 간격·비율이 범위를 벗어날 때
     */
    public data class Grid @JvmOverloads constructor(val columns: Int = 4, val gapDp: Float = 12f, val rowHeightRatio: Float = 1f) :
        BoardArrangement {
        init {
            require(columns in 1..MAX_COLUMNS) { "열 수는 1..$MAX_COLUMNS 범위여야 합니다." }
            require(gapDp.isFinite() && gapDp >= 0f) { "간격은 0 이상이어야 합니다." }
            require(rowHeightRatio.isFinite() && rowHeightRatio > 0f) { "칸 높이 비율은 양수여야 합니다." }
        }

        private companion object {
            const val MAX_COLUMNS = 24
        }
    }
}

/** 편집 모드에서 사용자에게 허용할 일. 호스트가 화면마다 정한다. */
public data class BoardPermissions @JvmOverloads constructor(
    val move: Boolean = true,
    val resize: Boolean = true,
    val add: Boolean = true,
    val remove: Boolean = true,
)
