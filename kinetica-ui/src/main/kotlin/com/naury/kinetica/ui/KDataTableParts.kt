package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 표를 그리는 데 필요한 열 모양. 열 폭은 표 폭 [maxWidth]에 맞춰 여기서 정한다. */
internal class TableLayout(
    columns: List<KTableColumn<*>>,
    val frozen: Boolean,
    val selectable: Boolean,
    maxWidth: Dp,
    val scroll: ScrollState,
    val surface: SurfaceStyle,
) {
    val count: Int = columns.size
    val titles: List<String> = columns.map { it.title }
    val numeric: List<Boolean> = columns.map { it.numeric }
    val sortable: List<Boolean> = columns.map { it.comparator != null }

    /** 표 체크박스 열이 있으면 1. TalkBack 열 번호에 더한다. */
    val offset: Int = if (selectable) 1 else 0

    /** 가로 스크롤 부분이 시작하는 열 */
    val firstScrolled: Int = if (frozen) 1 else 0

    val widths: List<Dp> = run {
        val fixed = columns.map { it.width.value }
        val lead = (if (selectable) CHECK_COLUMN.value else 0f) + if (frozen) fixed[0] else 0f
        val scrolled = resolveColumnWidths(
            fixed.drop(firstScrolled),
            columns.drop(firstScrolled).map { it.weight },
            maxWidth.value - lead,
        )
        (fixed.take(firstScrolled) + scrolled).map { it.dp }
    }
}

/** 행 하나의 고정 값. [index]는 정렬한 뒤 0부터 센 순번이다. */
internal class BodyRowSpec(val index: Int, val selected: Boolean, val height: Dp, val label: String?)

@Composable
internal fun TableHeader(layout: TableLayout, state: KDataTableState, keys: List<Any>) {
    val chosen = keys.count { state.isSelected(it) }
    val all = when {
        chosen == 0 -> ToggleableState.Off
        chosen == keys.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val selectAll = stringResource(R.string.kinetica_ui_table_select_all)
    TableRowFrame(
        layout,
        Modifier.heightIn(min = HEADER_HEIGHT),
        lead = {
            KTriStateCheckbox(
                all,
                onClick = { state.selection = if (all == ToggleableState.On) state.selection - keys.toSet() else state.selection + keys },
                modifier = Modifier.semantics {
                    contentDescription = selectAll
                    collectionItemInfo = CollectionItemInfo(0, 1, 0, 1)
                },
                enabled = keys.isNotEmpty(),
                surface = layout.surface,
            )
        },
    ) { column -> HeaderCell(layout, column, state) }
}

@Composable
private fun HeaderCell(layout: TableLayout, column: Int, state: KDataTableState) {
    val sortable = layout.sortable[column]
    val numeric = layout.numeric[column]
    val sort = state.sort
    val active = sortable && sort?.column == column
    val ascending = sort?.ascending != false
    val description = if (sortable) sortDescription(active, ascending) else null
    val sortLabel = stringResource(R.string.kinetica_ui_table_sort)
    val surface = layout.surface
    Row(
        Modifier
            .width(layout.widths[column])
            .heightIn(min = HEADER_HEIGHT)
            .then(if (sortable) Modifier.kClickable(onClickLabel = sortLabel) { state.toggleSort(column) } else Modifier)
            .semantics(mergeDescendants = true) {
                collectionItemInfo = CollectionItemInfo(0, 1, column + layout.offset, 1)
                description?.let { stateDescription = it }
            }
            .padding(horizontal = CELL_PADDING),
        horizontalArrangement = if (numeric) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (active) surface.contentColor else surface.muted(HEADER_ALPHA)
        if (numeric && sortable) SortArrow(active, ascending, ink)
        ThemedText(
            layout.titles[column],
            Modifier.weight(1f, fill = false),
            style = TextStyle(
                color = ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = if (numeric) TextAlign.End else TextAlign.Start,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!numeric && sortable) SortArrow(active, ascending, ink)
    }
}

/** 정렬할 수 있는 머리의 상태 글. TalkBack이 머리 이름 뒤에 읽는다. */
@Composable
private fun sortDescription(active: Boolean, ascending: Boolean): String = stringResource(
    when {
        !active -> R.string.kinetica_ui_table_not_sorted
        ascending -> R.string.kinetica_ui_table_sorted_ascending
        else -> R.string.kinetica_ui_table_sorted_descending
    },
)

/** 정렬 화살표. 정렬하지 않은 열은 옅게 위를 가리켜 누를 수 있다는 것만 알린다. 방향이 바뀌면 반 바퀴 돈다. */
@Composable
private fun SortArrow(active: Boolean, ascending: Boolean, color: Color) {
    val still = reducedMotion()
    val spec = tween<Float>(if (still) 0 else ARROW_MS)
    val turn by animateFloatAsState(if (active && !ascending) HALF_TURN else 0f, spec, label = "sort-turn")
    val shown by animateFloatAsState(if (active) 1f else IDLE_ARROW_ALPHA, spec, label = "sort-alpha")
    Glyph(
        KIcons.ArrowUp,
        color,
        Modifier.padding(horizontal = 4.dp).graphicsLayer {
            rotationZ = turn
            alpha = shown
        },
        size = ARROW_SIZE,
    )
}

@Composable
internal fun TableBodyRow(
    layout: TableLayout,
    spec: BodyRowSpec,
    onSelect: (Boolean) -> Unit,
    onClick: (() -> Unit)?,
    cell: @Composable (column: Int) -> Unit,
) {
    val ink = layout.surface.contentColor
    val accent = KineticaTheme.colors.accent
    val name = spec.label ?: stringResource(R.string.kinetica_ui_table_row, spec.index + 1)
    val selectLabel = stringResource(R.string.kinetica_ui_table_select_row, name)
    val row = spec.index + 1
    TableRowFrame(
        layout,
        Modifier
            .heightIn(min = spec.height)
            .background(if (spec.selected) accent.copy(alpha = SELECTED_ALPHA) else Color.Transparent)
            .then(if (onClick != null) Modifier.kClickable(onClickLabel = name, onClick = onClick) else Modifier)
            .rowDivider(ink.copy(alpha = LINE_ALPHA)),
        lead = {
            KCheckbox(
                spec.selected,
                onSelect,
                Modifier.semantics {
                    contentDescription = selectLabel
                    collectionItemInfo = CollectionItemInfo(row, 1, 0, 1)
                },
                surface = layout.surface,
            )
        },
    ) { column ->
        val numeric = layout.numeric[column]
        Box(
            Modifier
                .width(layout.widths[column])
                .padding(horizontal = CELL_PADDING, vertical = 8.dp)
                .semantics(mergeDescendants = true) { collectionItemInfo = CollectionItemInfo(row, 1, column + layout.offset, 1) },
            contentAlignment = if (numeric) Alignment.CenterEnd else Alignment.CenterStart,
        ) { cell(column) }
    }
}

/**
 * 머리와 몸통 행이 함께 쓰는 틀. 체크박스 열과 고정 열은 제자리에, 나머지 열은 표 전체가 공유하는 [TableLayout.scroll]로 가로 스크롤한다.
 * 모든 행이 같은 스크롤 상태를 읽으므로 한 행을 밀면 머리와 다른 행도 함께 움직인다.
 */
@Composable
private fun TableRowFrame(layout: TableLayout, modifier: Modifier, lead: @Composable () -> Unit, cell: @Composable (column: Int) -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (layout.selectable) Box(Modifier.width(CHECK_COLUMN), contentAlignment = Alignment.Center) { lead() }
        if (layout.frozen) cell(0)
        Box(Modifier.weight(1f).frozenEdgeShadow(layout)) {
            Row(Modifier.horizontalScroll(layout.scroll), verticalAlignment = Alignment.CenterVertically) {
                for (column in layout.firstScrolled until layout.count) cell(column)
            }
        }
    }
}

/** 가로로 스크롤한 만큼 고정 열 끝에 그림자를 그린다. 스크롤 위치는 그리기 단계에서만 읽는다. */
private fun Modifier.frozenEdgeShadow(layout: TableLayout): Modifier {
    if (!layout.frozen) return this
    val scroll = layout.scroll
    return drawWithCache {
        val w = SHADOW_WIDTH.toPx()
        val shade = Color.Black.copy(alpha = SHADOW_ALPHA)
        val startEdge = Brush.horizontalGradient(listOf(shade, Color.Transparent), startX = 0f, endX = w)
        val endEdge = Brush.horizontalGradient(listOf(Color.Transparent, shade), startX = size.width - w, endX = size.width)
        onDrawWithContent {
            drawContent()
            val amount = (scroll.value / w).coerceIn(0f, 1f)
            if (amount <= 0f) return@onDrawWithContent
            if (layoutDirection == LayoutDirection.Ltr) {
                drawRect(startEdge, size = Size(w, size.height), alpha = amount)
            } else {
                drawRect(endEdge, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height), alpha = amount)
            }
        }
    }
}

private fun Modifier.rowDivider(color: Color): Modifier = drawBehind {
    val y = size.height - LINE_WIDTH.toPx() / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), LINE_WIDTH.toPx())
}

@Composable
internal fun TableDivider(surface: SurfaceStyle) {
    Box(Modifier.fillMaxWidth().height(LINE_WIDTH).background(surface.contentColor.copy(alpha = HEADER_LINE_ALPHA)))
}

@Composable
internal fun TableEmpty(modifier: Modifier, empty: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { empty() }
}

/** 숫자 열이면 끝 쪽에 붙인 한 줄 글 */
@Composable
internal fun TableText(text: String, numeric: Boolean) {
    val ink = LocalKineticaContentColor.current
    val style = remember(ink, numeric) {
        TextStyle(color = ink, fontSize = 14.sp, textAlign = if (numeric) TextAlign.End else TextAlign.Start)
    }
    ThemedText(text, style = style, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

private val CHECK_COLUMN = 52.dp
private val HEADER_HEIGHT = 48.dp
private val CELL_PADDING = 12.dp
private val ARROW_SIZE = 14.dp
private val SHADOW_WIDTH = 10.dp
private val LINE_WIDTH = 0.5.dp
private const val HEADER_ALPHA = 0.65f
private const val SELECTED_ALPHA = 0.1f
private const val LINE_ALPHA = 0.08f
private const val HEADER_LINE_ALPHA = 0.14f
private const val SHADOW_ALPHA = 0.12f
private const val IDLE_ARROW_ALPHA = 0.3f
private const val HALF_TURN = 180f
private const val ARROW_MS = 180
