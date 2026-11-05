package com.naury.kinetica.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * [KDataTable]의 열 하나.
 *
 * @property title 머리 칸 글. TalkBack은 이 글과 정렬 상태를 함께 읽는다.
 * @property width 열 폭. 0보다 커야 하며 아니면 [IllegalArgumentException]을 던진다. [weight]가 있으면 최소 폭이다.
 * @property weight 0보다 크면, 모든 열 폭을 더해도 표 폭이 남을 때 남은 폭을 가중치 비율로 나눠 받는다. 음수면 [IllegalArgumentException]을 던진다.
 * @property numeric 숫자 열. 머리와 칸을 끝 쪽에 붙이고 정렬 화살표를 제목 앞에 둔다.
 * @property comparator 오름차순 비교자. `null`이면 이 열로는 정렬할 수 없다.
 * @property cell 행 하나의 칸 내용. 칸 높이 가운데, [numeric]이면 끝 쪽, 아니면 시작 쪽에 놓인다.
 */
@Immutable
public data class KTableColumn<T>(
    val title: String,
    val width: Dp = 120.dp,
    val weight: Float = 0f,
    val numeric: Boolean = false,
    val comparator: Comparator<in T>? = null,
    val cell: @Composable (T) -> Unit,
) {
    init {
        require(width > 0.dp) { "width는 0보다 커야 합니다." }
        require(weight >= 0f) { "weight는 0 이상이어야 합니다." }
    }

    public companion object {
        /**
         * 글을 보이는 열. [value]가 칸 글을 정하고, 기본으로 그 글 순서로 정렬한다.
         * 숫자처럼 글과 다른 순서로 정렬해야 하면 [comparator]를 넘기고, 정렬하지 않을 열이면 `null`을 넘긴다.
         * 가중치는 `.copy(weight = 1f)`로 준다.
         */
        public fun <T> text(
            title: String,
            value: (T) -> String,
            width: Dp = 120.dp,
            numeric: Boolean = false,
            comparator: Comparator<in T>? = compareBy(value),
        ): KTableColumn<T> = KTableColumn(title, width, numeric = numeric, comparator = comparator) { row ->
            TableText(value(row), numeric)
        }
    }
}

/** 정렬 상태. [column]은 [KDataTable]에 넘긴 열 목록의 번호다. */
@Immutable
public data class KTableSort(val column: Int, val ascending: Boolean = true)

/** [KDataTable]의 정렬과 선택 상태. [rememberKDataTableState]로 만든다. 선택은 행 키로 기억한다. */
@Stable
public class KDataTableState(initialSort: KTableSort? = null, initialSelection: Set<Any> = emptySet()) {
    /** 지금 정렬. `null`이면 받은 순서 그대로다. */
    public var sort: KTableSort? by mutableStateOf(initialSort)

    /** 고른 행의 키 */
    public var selection: Set<Any> by mutableStateOf(initialSelection)

    /** [column]으로 정렬한다. 이미 그 열로 정렬 중이면 방향을 바꾸고, 아니면 오름차순으로 시작한다. 열 머리를 누르면 부른다. */
    public fun toggleSort(column: Int) {
        val now = sort
        sort = if (now?.column == column) now.copy(ascending = !now.ascending) else KTableSort(column, ascending = true)
    }

    public fun isSelected(key: Any): Boolean = key in selection

    public fun setSelected(key: Any, selected: Boolean) {
        selection = if (selected) selection + key else selection - key
    }

    /** 목록에서 빠진 행의 키를 선택에서 뺀다. 남겨 두면 머리 체크박스는 모두 고른 것처럼 보이는데 숨은 행까지 함께 처리된다. */
    internal fun retainSelection(present: Set<Any>) {
        val now = selection
        if (now.all { it in present }) return
        selection = now.filterTo(HashSet()) { it in present }
    }
}

/** 정렬과 함께, 번들에 담을 수 있는 선택 키만 저장한다. 담을 수 없는 키를 쓰면 회전 뒤 선택은 비고 정렬만 남는다. */
private val TableStateSaver = Saver<KDataTableState, ArrayList<Any?>>(
    save = { state ->
        val sort = state.sort
        arrayListOf(sort?.column ?: NO_SORT, sort?.ascending ?: true, ArrayList(state.selection.filter { canBeSaved(it) }))
    },
    restore = { saved ->
        val column = saved[0] as Int
        val sort = if (column == NO_SORT) null else KTableSort(column, saved[1] as Boolean)
        KDataTableState(sort, (saved[2] as List<*>).filterNotNullTo(HashSet()))
    },
)

private const val NO_SORT = -1

/** [KDataTableState]를 만든다. */
@Composable
public fun rememberKDataTableState(initialSort: KTableSort? = null): KDataTableState =
    rememberSaveable(saver = TableStateSaver) { KDataTableState(initialSort) }

/**
 * 정렬과 행 선택이 되는 데이터 표. 행은 `LazyColumn`으로 보이는 것만 구성하므로 수천 행도 괜찮다.
 *
 * 정렬할 수 있는 열 머리를 누르면 오름차순, 한 번 더 누르면 내림차순으로 정렬하고 머리에 화살표를 보인다.
 * [frozenFirstColumn]이 true면 첫 열은 제자리에 두고 나머지 열만 가로로 스크롤한다. 머리와 모든 행은 같은 스크롤 위치를 쓰므로 함께 움직이고,
 * 스크롤하면 첫 열 끝에 그림자가 진다. [selectable]이면 맨 앞에 체크박스 열을 두고, 머리의 체크박스로 모두 고르거나 모두 푼다.
 * 행이 없으면 머리 아래에 [empty]를 보인다. RTL에서는 열 순서와 스크롤 방향이 바뀐다.
 *
 * TalkBack은 표로 읽는다. 칸마다 몇 행 몇 열인지 알리고, 정렬할 수 있는 머리는 정렬 상태를 함께 읽는다.
 * [onRowClick]을 주면 행 전체가 버튼 하나가 되어 칸을 한 번에 읽는다.
 *
 * 세로 높이가 정해진 곳에 둔다. 세로로 스크롤되는 부모 안에 넣으려면 `Modifier.height`로 높이를 준다. 높이가 무한하면 `LazyColumn`이 실패한다.
 *
 * ```kotlin
 * KDataTable(
 *     orders,
 *     listOf(
 *         KTableColumn.text("주문", { it.id }),
 *         KTableColumn.text("금액", { it.amountText }, numeric = true, comparator = compareBy { it.amount }),
 *     ),
 *     key = { it.id },
 *     selectable = true,
 * )
 * ```
 *
 * @param key 행을 가리는 키. 선택과 `LazyColumn` 항목 키로 쓴다. 행마다 달라야 한다.
 * @param rowLabel 체크박스를 TalkBack에 읽을 때 쓸 행 이름. `null`이면 "3행"처럼 순번으로 읽는다.
 * @param rowHeight 행의 최소 높이. 큰 글꼴에서는 더 커진다.
 * @param empty 행이 없을 때 머리 아래에 보일 내용
 */
@Composable
public fun <T> KDataTable(
    rows: List<T>,
    columns: List<KTableColumn<T>>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    state: KDataTableState = rememberKDataTableState(),
    selectable: Boolean = false,
    frozenFirstColumn: Boolean = true,
    onRowClick: ((T) -> Unit)? = null,
    rowLabel: ((T) -> String)? = null,
    rowHeight: Dp = 52.dp,
    surface: SurfaceStyle = KineticaTheme.surface,
    empty: @Composable () -> Unit = { KEmptyState(stringResource(R.string.kinetica_ui_table_empty), surface = surface) },
) {
    require(columns.isNotEmpty()) { "columns가 비어 있습니다." }
    val sort = state.sort
    val sorted = remember(rows, sort, columns) { sortRows(rows, columns, sort) }
    val keys = remember(sorted) { sorted.map(key) }
    val present = remember(keys) { keys.toHashSet<Any>() }
    DisposableEffect(state, present) {
        state.retainSelection(present)
        onDispose {}
    }
    val scroll = rememberScrollState()
    val frozen = frozenFirstColumn && columns.size > 1
    val totalColumns = columns.size + if (selectable) 1 else 0
    val tableInfo = CollectionInfo(sorted.size + 1, totalColumns)
    BoxWithConstraints(modifier.semantics { collectionInfo = tableInfo }) {
        val layout = TableLayout(columns, frozen, selectable, maxWidth, scroll, surface)
        Column(Modifier.fillMaxWidth()) {
            TableHeader(layout, state, keys)
            TableDivider(surface)
            if (sorted.isEmpty()) {
                TableEmpty(Modifier.weight(1f, fill = false), empty)
            } else {
                // 몸통 칸은 LazyColumn의 목록 정보에 묶이므로, 머리와 같은 표 크기로 덮어써 TalkBack이 몸통도 표로 읽게 한다.
                LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth().semantics { collectionInfo = tableInfo }) {
                    itemsIndexed(sorted, key = { _, row -> key(row) }) { index, row ->
                        val rowKey = key(row)
                        TableBodyRow(
                            layout,
                            BodyRowSpec(index, state.isSelected(rowKey), rowHeight, rowLabel?.invoke(row)),
                            { checked -> state.setSelected(rowKey, checked) },
                            onRowClick?.let { click -> { click(row) } },
                        ) { column -> columns[column].cell(row) }
                    }
                }
            }
        }
    }
}

/** 정렬 상태대로 늘어놓은 행. 정렬이 없거나 그 열에 비교자가 없으면 받은 순서 그대로다. 같은 값끼리는 원래 순서를 지킨다. */
internal fun <T> sortRows(rows: List<T>, columns: List<KTableColumn<T>>, sort: KTableSort?): List<T> {
    val base = sort?.let { columns.getOrNull(it.column)?.comparator } ?: return rows
    val ascending = Comparator<T> { a, b -> base.compare(a, b) }
    return rows.sortedWith(if (sort.ascending) ascending else Comparator { a, b -> ascending.compare(b, a) })
}

/**
 * 열 폭(px)을 정한다. [widths]의 합이 [available]보다 작으면 남은 폭을 [weights] 비율로 나눠 더한다.
 * 가중치가 모두 0이거나 남는 폭이 없으면 [widths] 그대로다.
 */
internal fun resolveColumnWidths(widths: List<Float>, weights: List<Float>, available: Float): List<Float> {
    val extra = available - widths.sum()
    val total = weights.sum()
    if (extra <= 0f || total <= 0f) return widths
    return widths.mapIndexed { i, w -> w + extra * weights[i] / total }
}
