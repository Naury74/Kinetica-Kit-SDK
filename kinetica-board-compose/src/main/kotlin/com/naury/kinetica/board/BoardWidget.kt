package com.naury.kinetica.board

import com.naury.kinetica.core.scene.NodeEffects
import com.naury.kinetica.core.scene.isValidDataKey

/**
 * 보드에 놓을 수 있는 호스트 위젯 하나. 보드는 [key]와 배치만 저장하고, 위젯 내용은 호스트가 [key]를 보고 그린다.
 *
 * @property key 위젯 키. 영문자·숫자와 `.` `_` `-`로 된 1..128자. [multiple]이 아니면 보드 안에서 하나만 놓인다.
 * @property label 위젯 추가 메뉴에 보이는 이름
 * @property widthDp 처음 놓을 때 너비(dp, 0보다 큼). 격자 배치에서는 [columnSpan]을 쓴다.
 * @property heightDp 처음 놓을 때 높이(dp, 0보다 큼). 격자 배치에서는 [rowSpan]을 쓴다.
 * @property columnSpan 격자 배치에서 차지하는 열 수(1 이상)
 * @property rowSpan 격자 배치에서 차지하는 행 수(1 이상)
 * @property placedByDefault 저장된 배치가 없거나 앱 업데이트로 새로 생긴 위젯일 때 보드에 바로 놓을지
 * @property removable `false`면 사용자가 보드에서 삭제할 수 없는 필수 위젯이다
 * @property effects 사용 모드에서 위젯에 적용할 모션 효과. 누를 때의 리플과 pressed 효과, 등장 애니메이션, 반복 애니메이션을 정한다.
 *   편집 중에는 재생하지 않는다. 거리 값은 보드 문서 단위(약 2.75단위 = 1dp)다. 저장하지 않으므로 앱을 업데이트해 바꾸면 바로 반영된다.
 * @property multiple `true`면 메모처럼 같은 위젯을 여러 개 놓을 수 있다. 슬롯마다 id가 다르다([KineticaBoardState.placedKeys]).
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
    val effects: NodeEffects = NodeEffects.None,
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
     * 자유 배치. 위치와 크기를 마음대로 정하고, 다른 위젯이나 보드 가장자리에 스냅된다. 데스크톱의 창 분할처럼 화면 가장자리나
     * 모서리로 끌어 놓으면 화면의 절반, 4분의 1, 전체를 차지한다. 화면 폭이 바뀌면 가로 비율을 유지한다.
     */
    public data class Free @JvmOverloads constructor(val gapDp: Float = 12f) : BoardArrangement {
        init {
            require(gapDp.isFinite() && gapDp >= 0f) { "간격은 0 이상이어야 합니다." }
        }
    }

    /**
     * 대시보드 타일 같은 격자 배치. 위젯은 칸 단위로 놓이고, 겹치면 아래로 밀린다. 칸 높이는 칸 너비 × [rowHeightRatio]다.
     *
     * [columnsByWidth]를 주면 펼친 폴더블이나 태블릿처럼 보드가 넓어질 때 열 수를 늘린다. 열 수마다 배치를 따로 기억하므로
     * 넓은 화면에서 옮긴 위젯은 넓은 배치에만 반영되고, 접어서 [columns]열로 돌아오면 좁은 배치가 그대로 보인다. 어떤 열 수에서
     * 아직 편집한 적이 없으면 좁은 배치를 위에서부터 읽는 순서대로 흘려 넣어 보여 준다.
     *
     * @property columns 가장 좁은 화면의 열 수. [columnsByWidth]가 없으면 늘 이 열 수다.
     * @property columnsByWidth 보드 폭에 따라 열 수를 정하는 규칙. `null`이면 열 수를 바꾸지 않는다.
     * @throws IllegalArgumentException 열 수가 1..24 밖이거나 간격·비율이 범위를 벗어날 때
     */
    public data class Grid @JvmOverloads constructor(
        val columns: Int = 4,
        val gapDp: Float = 12f,
        val rowHeightRatio: Float = 1f,
        val columnsByWidth: GridColumns? = null,
    ) : BoardArrangement {
        init {
            require(columns in 1..GridColumns.MAX_COLUMNS) { "열 수는 1..${GridColumns.MAX_COLUMNS} 범위여야 합니다." }
            require(gapDp.isFinite() && gapDp >= 0f) { "간격은 0 이상이어야 합니다." }
            require(rowHeightRatio.isFinite() && rowHeightRatio > 0f) { "칸 높이 비율은 양수여야 합니다." }
        }

        /** 보드 폭 [widthDp](dp)에서 쓸 열 수. [columnsByWidth]가 없거나 폭이 유한한 양수가 아니면 [columns]다. 결과는 1..24다. */
        public fun columnsFor(widthDp: Float): Int {
            val rule = columnsByWidth
            if (rule == null || !widthDp.isFinite() || widthDp <= 0f) return columns
            val count = when (rule) {
                is GridColumns.Adaptive -> {
                    // 칸 n개와 간격 n+1개가 폭 안에 들어가는 가장 큰 n.
                    val fit = ((widthDp - gapDp) / (rule.minCellWidthDp + gapDp)).toInt()
                    fit.coerceIn(columns, maxOf(columns, rule.maxColumns))
                }

                is GridColumns.Breakpoints -> rule.steps.filter { widthDp >= it.minWidthDp }.maxByOrNull { it.minWidthDp }?.columns
                    ?: columns
            }
            return count.coerceIn(1, GridColumns.MAX_COLUMNS)
        }

        internal fun withColumns(count: Int): Grid = if (count == columns) this else copy(columns = count)
    }
}

/** 격자 보드의 폭에 따른 열 수 규칙. [BoardArrangement.Grid.columnsByWidth]에 준다. */
public sealed interface GridColumns {
    /**
     * 칸 폭이 [minCellWidthDp]보다 좁아지지 않는 만큼 열을 늘린다. [BoardArrangement.Grid.columns]보다 적거나 [maxColumns]보다 많아지지 않는다.
     * 예를 들어 `Adaptive(160f, maxColumns = 6)`이면 393dp 폰은 2열, 펼친 폴드(약 850dp)는 4열, 1280dp 태블릿은 6열이다(간격 12dp).
     *
     * @throws IllegalArgumentException [minCellWidthDp]가 유한한 양수가 아니거나 [maxColumns]가 1..24 밖일 때
     */
    public data class Adaptive @JvmOverloads constructor(val minCellWidthDp: Float, val maxColumns: Int = MAX_COLUMNS) : GridColumns {
        init {
            require(minCellWidthDp.isFinite() && minCellWidthDp > 0f) { "최소 칸 폭은 유한한 양수여야 합니다." }
            require(maxColumns in 1..MAX_COLUMNS) { "최대 열 수는 1..$MAX_COLUMNS 범위여야 합니다." }
        }
    }

    /**
     * 보드 폭 구간마다 열 수를 정한다. 보드 폭이 [Breakpoint.minWidthDp] 이상인 구간 가운데 가장 넓은 구간의 열 수를 쓰고,
     * 어느 구간에도 들지 않으면 [BoardArrangement.Grid.columns]를 쓴다.
     *
     * @throws IllegalArgumentException [steps]가 비었거나 같은 폭이 두 번 있을 때
     */
    public data class Breakpoints(val steps: List<Breakpoint>) : GridColumns {
        public constructor(vararg steps: Breakpoint) : this(steps.toList())

        init {
            require(steps.isNotEmpty()) { "구간이 하나 이상 있어야 합니다." }
            require(steps.map { it.minWidthDp }.toSet().size == steps.size) { "같은 폭의 구간이 두 번 있습니다." }
        }
    }

    public companion object {
        internal const val MAX_COLUMNS = 24

        /** `GridColumns.breakpoints(600f to 4, 840f to 6)`처럼 (최소 폭 dp, 열 수) 쌍으로 [Breakpoints]를 만든다. */
        @JvmStatic
        public fun breakpoints(vararg steps: Pair<Float, Int>): Breakpoints =
            Breakpoints(steps.map { (width, columns) -> Breakpoint(width, columns) })
    }
}

/**
 * 보드 폭이 [minWidthDp](dp) 이상일 때 쓸 열 수 [columns].
 *
 * @throws IllegalArgumentException [minWidthDp]가 0 이상의 유한한 값이 아니거나 [columns]가 1..24 밖일 때
 */
public data class Breakpoint(val minWidthDp: Float, val columns: Int) {
    init {
        require(minWidthDp.isFinite() && minWidthDp >= 0f) { "구간 폭은 0 이상이어야 합니다." }
        require(columns in 1..GridColumns.MAX_COLUMNS) { "열 수는 1..${GridColumns.MAX_COLUMNS} 범위여야 합니다." }
    }
}

/** 편집 모드에서 사용자에게 허용할 작업. 호스트가 화면마다 정한다. */
public data class BoardPermissions @JvmOverloads constructor(
    val move: Boolean = true,
    val resize: Boolean = true,
    val add: Boolean = true,
    val remove: Boolean = true,
)
