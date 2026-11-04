package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * [KBentoGrid] 칸이 차지하는 열 수와 줄 수. 둘 다 1 이상이어야 하며 아니면 [IllegalArgumentException]을 던진다.
 * 열 수가 격자 열 수보다 크면 격자 열 수로 줄여 놓는다.
 */
@Immutable
public data class KBentoSpan(val columns: Int, val rows: Int) {
    init {
        require(columns >= 1 && rows >= 1) { "columns와 rows는 1 이상이어야 합니다." }
    }

    public companion object {
        /** 1열 1줄 */
        @JvmField public val Small: KBentoSpan = KBentoSpan(1, 1)

        /** 2열 1줄. 가로로 긴 칸 */
        @JvmField public val Wide: KBentoSpan = KBentoSpan(2, 1)

        /** 1열 2줄. 세로로 긴 칸 */
        @JvmField public val Tall: KBentoSpan = KBentoSpan(1, 2)

        /** 2열 2줄. 대표 칸 */
        @JvmField public val Large: KBentoSpan = KBentoSpan(2, 2)
    }
}

/** [KBentoGrid] 안에서 쓰는 범위. 칸 크기를 정하는 [bentoSpan]과 [KBentoCell]을 준다. */
@LayoutScopeMarker
@Immutable
public interface KBentoGridScope {
    /** 이 요소가 격자에서 [span]만큼 차지하게 한다. [KBentoCell]을 쓰지 않고 직접 만든 칸에 붙인다. */
    public fun Modifier.bentoSpan(span: KBentoSpan): Modifier = this.then(BentoSpanData(span))
}

private object BentoGridScopeInstance : KBentoGridScope

private data class BentoSpanData(val span: KBentoSpan) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@BentoSpanData
}

/**
 * 크기가 다른 칸을 빈틈 없이 맞춰 놓는 소개 격자. 기능 소개, 대시보드 첫 화면, 요금제 장점처럼 칸이 많지 않은 화면에 쓴다.
 * 게으른 목록이 아니라 모든 칸을 한 번에 구성하므로 칸이 수십 개를 넘으면 `LazyVerticalStaggeredGrid`가 낫다.
 *
 * 칸은 선언한 순서대로 앞쪽 빈 자리부터 채운다(dense 배치). 그래서 큰 칸 뒤에 생긴 구멍에 뒤의 작은 칸이 들어간다.
 * [fillGaps]가 true면 그래도 남은 빈 자리를 옆이나 위 칸을 늘려 메운다. 열 수는 받은 폭에 [minCellWidth] 칸이 몇 개 들어가는지로 정하므로
 * 폰에서는 2열, 펼친 폴더블이나 태블릿에서는 3~4열이 된다. RTL에서는 좌우가 바뀐다.
 *
 * 한 줄 높이는 [rowHeight]이고, 칸 내용이 더 길면(큰 글꼴 등) 그 줄이 늘어난다. 여러 줄 칸은 마지막 줄을 늘린다.
 * [animateAppear]가 true면 칸이 위에서부터 차례로 떠오르며 나타난다. 진행 값은 배치 층에서만 읽으므로 등장하는 동안 다시 구성하지 않는다.
 * '애니메이션 삭제' 설정에서는 바로 보인다.
 *
 * ```kotlin
 * KBentoGrid {
 *     KBentoCell(span = KBentoSpan.Large) { Text("이번 달 매출") }
 *     KBentoCell { Text("주문") }
 *     KBentoCell(span = KBentoSpan.Tall) { Text("재고") }
 * }
 * ```
 *
 * @param columns 열 수를 직접 정한다. `null`이면 폭에 맞춘다. 1 미만이면 [IllegalArgumentException]을 던진다.
 * @param minCellWidth 열 수를 정할 때 쓰는 한 열의 최소 폭. 0보다 커야 한다.
 * @param maxColumns 폭에 맞춘 열 수의 최댓값. 1 이상이어야 한다.
 * @param rowHeight 한 줄의 기본 높이. 칸 안에 `BoxWithConstraints`나 게으른 목록을 바로 넣으면 내용 높이를 잴 수 없어 줄이 늘어나지 않는다.
 * @param spacing 칸 사이 간격. 가로와 세로가 같다.
 */
@Composable
public fun KBentoGrid(
    modifier: Modifier = Modifier,
    columns: Int? = null,
    minCellWidth: Dp = 150.dp,
    maxColumns: Int = 4,
    rowHeight: Dp = 120.dp,
    spacing: Dp = 12.dp,
    fillGaps: Boolean = true,
    animateAppear: Boolean = true,
    content: @Composable KBentoGridScope.() -> Unit,
) {
    require(columns == null || columns >= 1) { "columns는 1 이상이어야 합니다." }
    require(minCellWidth > 0.dp) { "minCellWidth는 0보다 커야 합니다." }
    require(maxColumns >= 1) { "maxColumns는 1 이상이어야 합니다." }
    val skip = reducedMotion() || !animateAppear
    val clock = remember { Animatable(if (skip) APPEAR_END_MS else 0f) }
    LaunchedEffect(skip) {
        if (skip) clock.snapTo(APPEAR_END_MS) else clock.animateTo(APPEAR_END_MS, tween(APPEAR_END_MS.toInt(), easing = LinearEasing))
    }
    val grid = BentoGridSpec(columns, minCellWidth, maxColumns, rowHeight, spacing, fillGaps)
    Layout(content = { BentoGridScopeInstance.content() }, modifier = modifier) { measurables, constraints ->
        measureBento(measurables, constraints, grid) { rank -> appearAt(clock.value, rank) }
    }
}

/**
 * 표면 스타일을 입힌 [KBentoGrid] 칸. 받은 칸 크기를 모두 채우고, 안쪽 여백 [contentPadding] 안에 [content]를 놓는다.
 *
 * @param span 이 칸이 차지하는 열 수와 줄 수
 * @param onClick `null`이 아니면 칸 전체를 누를 수 있는 버튼으로 만든다.
 * @param onClickLabel TalkBack이 누르기 동작 이름으로 읽는 글
 */
@Composable
public fun KBentoGridScope.KBentoCell(
    modifier: Modifier = Modifier,
    span: KBentoSpan = KBentoSpan.Small,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    shape: Shape = shapeFor { card },
    contentPadding: Dp = KineticaTheme.design.density.padding(CELL_PADDING),
    surface: SurfaceStyle = KineticaTheme.surface,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .bentoSpan(span)
            .kSurface(surface, shape)
            .clip(shape)
            .then(if (onClick != null) Modifier.kClickable(shape, onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor) { content() }
    }
}

/** 격자 모양 값 */
private class BentoGridSpec(
    val columns: Int?,
    val minCellWidth: Dp,
    val maxColumns: Int,
    val rowHeight: Dp,
    val spacing: Dp,
    val fillGaps: Boolean,
)

/** 격자 안 칸 하나의 자리. 열과 줄 번호는 0부터, 크기는 칸 수다. */
internal data class BentoPlacement(val column: Int, val row: Int, val columns: Int, val rows: Int)

private fun MeasureScope.measureBento(
    measurables: List<Measurable>,
    constraints: Constraints,
    grid: BentoGridSpec,
    appear: (rank: Int) -> Float,
): MeasureResult {
    val spacing = grid.spacing.toPx()
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
    val count = grid.columns ?: bentoColumns(width.toFloat(), grid.minCellWidth.toPx(), spacing, grid.maxColumns)
    val spans = measurables.map { (it.parentData as? BentoSpanData)?.span ?: KBentoSpan.Small }
    val cells = packBento(spans, count, grid.fillGaps)
    // 열 하나와 간격 하나를 더한 폭. 칸 끝을 반올림해 두면 칸 사이 간격이 화소 단위로 고르게 남는다.
    val step = (width + spacing) / count
    val xs = IntArray(count) { (it * step).roundToInt() }
    fun cellWidth(c: BentoPlacement) = ((c.column + c.columns) * step - spacing).roundToInt().minus(xs[c.column]).coerceAtLeast(0)
    val heights =
        bentoRowHeights(cells, grid.rowHeight.roundToPx(), spacing.roundToInt()) { i -> contentHeight(measurables[i], cellWidth(cells[i])) }
    val ys = IntArray(heights.size + 1)
    for (r in heights.indices) ys[r + 1] = ys[r] + heights[r] + spacing.roundToInt()
    val placeables = measurables.mapIndexed { i, m ->
        val c = cells[i]
        m.measure(Constraints.fixed(cellWidth(c), ys[c.row + c.rows] - ys[c.row] - spacing.roundToInt()))
    }
    val ranks = cells.indices.sortedWith(compareBy({ cells[it].row }, { cells[it].column }))
    val rankOf = IntArray(cells.size).also { out -> ranks.forEachIndexed { rank, i -> out[i] = rank } }
    val total = (ys.last() - if (heights.isEmpty()) 0 else spacing.roundToInt()).coerceAtLeast(0)
    val distance = APPEAR_DISTANCE.toPx()
    return layout(width, constraints.constrainHeight(total)) {
        placeables.forEachIndexed { i, p ->
            val rank = rankOf[i]
            p.placeRelativeWithLayer(xs[cells[i].column], ys[cells[i].row]) {
                val t = appear(rank)
                alpha = t
                translationY = (1f - t) * distance
                val scale = APPEAR_SCALE + (1f - APPEAR_SCALE) * t
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

/**
 * 칸 내용이 폭 [width]에서 필요한 높이. `BoxWithConstraints`나 게으른 목록처럼 고유 크기를 잴 수 없는 내용이면 0을 돌려
 * 기본 줄 높이를 쓰게 한다. 그런 내용은 줄 높이를 넘으면 잘린다.
 */
private fun contentHeight(measurable: Measurable, width: Int): Int = runCatching { measurable.minIntrinsicHeight(width) }.getOrDefault(0)

/** 폭 [width]에 최소 폭 [minCell]인 열이 간격 [spacing]으로 몇 개 들어가는지. 1 이상 [maxColumns] 이하다. */
internal fun bentoColumns(width: Float, minCell: Float, spacing: Float, maxColumns: Int): Int =
    ((width + spacing) / (minCell + spacing)).toInt().coerceIn(1, maxColumns)

/**
 * 줄마다 높이(px)를 정한다. 기본은 [base]이고, 한 줄 칸의 내용 높이가 더 크면 그 줄을 늘린다.
 * 여러 줄 칸은 맡은 줄 높이와 간격을 더한 값이 모자라면 마지막 줄을 늘린다.
 */
private fun bentoRowHeights(cells: List<BentoPlacement>, base: Int, spacing: Int, need: (Int) -> Int): IntArray {
    val rows = cells.maxOfOrNull { it.row + it.rows } ?: 0
    val heights = IntArray(rows) { base }
    val order = cells.indices.sortedBy { cells[it].rows }
    for (i in order) {
        val c = cells[i]
        val have = (c.row until c.row + c.rows).sumOf { heights[it] } + spacing * (c.rows - 1)
        val want = need(i)
        if (want > have) heights[c.row + c.rows - 1] += want - have
    }
    return heights
}

/**
 * [spans]를 [columns]열 격자에 앞쪽 빈 자리부터 채워 놓는다. 열 수가 격자보다 큰 칸은 격자 폭으로 줄인다.
 * [fillGaps]가 true면 남은 빈 자리를 왼쪽(시작 쪽) 칸을 넓히거나 위 칸을 늘려 메운다. 격자 전체 줄 수는 늘리지 않는다.
 */
internal fun packBento(spans: List<KBentoSpan>, columns: Int, fillGaps: Boolean): List<BentoPlacement> {
    val grid = BentoOccupancy(columns)
    val cells = spans.map { span ->
        val w = span.columns.coerceAtMost(columns)
        val (c, r) = grid.firstFit(w, span.rows)
        grid.mark(c, r, w, span.rows)
        BentoPlacement(c, r, w, span.rows)
    }.toMutableList()
    if (fillGaps) fillBentoGaps(cells, grid)
    return cells
}

private fun fillBentoGaps(cells: MutableList<BentoPlacement>, grid: BentoOccupancy) {
    // 칸 하나를 늘리면 그 옆 구멍을 다른 칸이 메울 수 있게 되므로 더 바뀌지 않을 때까지 되풀이한다.
    while (fillBentoPass(cells, grid)) Unit
}

private fun fillBentoPass(cells: MutableList<BentoPlacement>, grid: BentoOccupancy): Boolean {
    var changed = false
    for (r in 0 until grid.rowCount) {
        for (c in 0 until grid.columns) {
            if (grid.isFree(c, r) && (widenInto(cells, grid, c, r) || lengthenInto(cells, grid, c, r))) changed = true
        }
    }
    return changed
}

/** (c, r) 빈 자리 바로 앞 칸이 자기 모든 줄에서 c열이 비어 있으면 한 열 넓힌다. */
private fun widenInto(cells: MutableList<BentoPlacement>, grid: BentoOccupancy, c: Int, r: Int): Boolean {
    val i = cells.indexOfFirst { it.column + it.columns == c && r in it.row until it.row + it.rows }
    if (i < 0) return false
    val cell = cells[i]
    if ((cell.row until cell.row + cell.rows).any { !grid.isFree(c, it) }) return false
    grid.mark(c, cell.row, 1, cell.rows)
    cells[i] = cell.copy(columns = cell.columns + 1)
    return true
}

/** (c, r) 빈 자리 바로 위 칸이 자기 모든 열에서 r줄이 비어 있으면 한 줄 늘린다. */
private fun lengthenInto(cells: MutableList<BentoPlacement>, grid: BentoOccupancy, c: Int, r: Int): Boolean {
    val i = cells.indexOfFirst { it.row + it.rows == r && c in it.column until it.column + it.columns }
    if (i < 0) return false
    val cell = cells[i]
    if ((cell.column until cell.column + cell.columns).any { !grid.isFree(it, r) }) return false
    grid.mark(cell.column, r, cell.columns, 1)
    cells[i] = cell.copy(rows = cell.rows + 1)
    return true
}

/** 칸이 차지한 자리. 줄은 필요한 만큼 늘어난다. */
private class BentoOccupancy(val columns: Int) {
    private val rows = ArrayList<BooleanArray>()
    val rowCount: Int get() = rows.size

    fun isFree(c: Int, r: Int): Boolean = r >= rows.size || !rows[r][c]

    fun firstFit(w: Int, h: Int): Pair<Int, Int> {
        var r = 0
        while (true) {
            for (c in 0..columns - w) {
                if (fits(c, r, w, h)) return c to r
            }
            r++
        }
    }

    private fun fits(c: Int, r: Int, w: Int, h: Int): Boolean {
        for (y in r until r + h) for (x in c until c + w) if (!isFree(x, y)) return false
        return true
    }

    fun mark(c: Int, r: Int, w: Int, h: Int) {
        while (rows.size < r + h) rows += BooleanArray(columns)
        for (y in r until r + h) for (x in c until c + w) rows[y][x] = true
    }
}

/** 등장 시계 [clockMs]에서 [rank]번째 칸의 진행(0..1). */
private fun appearAt(clockMs: Float, rank: Int): Float {
    val delay = minOf(rank * APPEAR_STAGGER_MS, APPEAR_MAX_DELAY_MS)
    return FastOutSlowInEasing.transform(((clockMs - delay) / APPEAR_DURATION_MS).coerceIn(0f, 1f))
}

private val CELL_PADDING = 16.dp
private val APPEAR_DISTANCE = 16.dp
private const val APPEAR_SCALE = 0.96f
private const val APPEAR_STAGGER_MS = 50f
private const val APPEAR_MAX_DELAY_MS = 400f
private const val APPEAR_DURATION_MS = 420f
private const val APPEAR_END_MS = APPEAR_MAX_DELAY_MS + APPEAR_DURATION_MS
