// 파일 이름은 대표 API인 KPagination을(를) 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** [KPagination]의 모양 */
public enum class KPaginationStyle {
    /** 이전, 숫자 칸, 다음. 페이지가 많으면 처음과 끝 사이를 말줄임표로 줄인다. */
    Numbers,

    /** 이전, `5 / 20`, 다음. 좁은 자리에 쓴다. */
    Compact,
}

/**
 * 페이지 번호 이동. [KPaginationStyle.Numbers]는 칸 수가 늘 같아서 페이지를 넘겨도 폭이 바뀌지 않는다. 고른 칸은 강조색 원이 튀어나오듯 커진다.
 * 이전과 다음 화살표는 RTL에서 뒤집히고, 숫자는 뒤집지 않는다. 첫 페이지와 끝 페이지에서는 해당 화살표가 흐려지고 눌리지 않는다.
 * TalkBack은 숫자 칸을 '5페이지'로 읽고 고른 칸에 선택 상태를 붙인다.
 *
 * ```kotlin
 * KPagination(page, pageCount = 20, onPageChange = { page = it })
 * ```
 *
 * @param page 지금 페이지(0부터). 범위를 벗어나면 가장 가까운 페이지로 맞춘다.
 * @param pageCount 페이지 수. 1보다 작으면 [IllegalArgumentException]을 던진다.
 * @param siblings 지금 페이지 양옆에 늘 보여 줄 번호 수. 받은 폭에 다 들어가지 않으면(좁은 화면, 큰 글자) 0까지 줄여 그린다. 음수면 [IllegalArgumentException]을 던진다.
 */
@Composable
public fun KPagination(
    page: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: KPaginationStyle = KPaginationStyle.Numbers,
    siblings: Int = 1,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(pageCount >= 1) { "pageCount는 1 이상이어야 합니다." }
    require(siblings >= 0) { "siblings는 0 이상이어야 합니다." }
    val current = page.coerceIn(0, pageCount - 1)
    val ink = surface.contentColor
    BoxWithConstraints(modifier) {
        val cell = CELL * LocalDensity.current.fontScale.coerceAtLeast(1f)
        val shown = fittingSiblings(siblings, maxWidth, cell)
        PaginationRow(current, pageCount, onPageChange, style, shown, ink)
    }
}

/** 받은 폭에 칸이 다 들어가도록 [siblings]를 줄인다. 0까지 줄여도 넘치면 0이다. */
private fun fittingSiblings(siblings: Int, width: Dp, cell: Dp): Int {
    var s = siblings
    while (s > 0 && (cell + GAP) * (s * 2 + EDGE_SLOTS) + (KMetrics.MinTouch + GAP) * 2 > width) s--
    return s
}

@Composable
private fun PaginationRow(current: Int, pageCount: Int, onPageChange: (Int) -> Unit, style: KPaginationStyle, siblings: Int, ink: Color) {
    Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GAP)) {
        PageArrow(SocialGlyphs.ChevronBack, stringResource(R.string.kinetica_ui_page_previous), current > 0, ink) {
            onPageChange(current - 1)
        }
        when (style) {
            KPaginationStyle.Numbers -> pageSlots(current, pageCount, siblings).forEach { slot ->
                if (slot == null) Ellipsis(ink) else PageCell(slot, slot == current, ink) { onPageChange(slot) }
            }

            KPaginationStyle.Compact -> {
                val label = stringResource(R.string.kinetica_ui_page_state, current + 1, pageCount)
                ThemedText(
                    "${current + 1} / $pageCount",
                    Modifier.padding(horizontal = 12.dp).clearAndSetSemantics { contentDescription = label },
                    style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = KineticaTheme.design.label(FontWeight.SemiBold)),
                )
            }
        }
        PageArrow(KGlyphs.ChevronForward, stringResource(R.string.kinetica_ui_page_next), current < pageCount - 1, ink) {
            onPageChange(current + 1)
        }
    }
}

/**
 * 보여 줄 칸. null은 말줄임표다. 페이지가 많으면 칸 수가 늘 `siblings * 2 + 5`가 되도록 처음과 끝을 고정하고 사이를 줄인다.
 */
internal fun pageSlots(page: Int, count: Int, siblings: Int): List<Int?> {
    val total = siblings * 2 + EDGE_SLOTS
    if (count <= total) return List(count) { it }
    val inner = total - 2
    return when {
        page - siblings <= 2 -> List(inner) { it } + listOf(null, count - 1)
        page + siblings >= count - EDGE_SLOTS + 2 -> listOf(0, null) + List(inner) { count - inner + it }
        else -> listOf(0, null) + List(siblings * 2 + 1) { page - siblings + it } + listOf(null, count - 1)
    }
}

@Composable
private fun PageCell(index: Int, selected: Boolean, ink: Color, onClick: () -> Unit) {
    val colors = KineticaTheme.colors
    val still = reducedMotion()
    val pop =
        animateFloatAsState(
            if (selected) 1f else 0f,
            if (still) snap() else spring(dampingRatio = POP_DAMPING, stiffness = POP_STIFFNESS),
            label = "page",
        )
    val label = stringResource(R.string.kinetica_ui_page_number, index + 1)
    val shape = shapeFor { indicator }
    val weight = KineticaTheme.design.label(FontWeight.Medium)
    Box(
        Modifier
            .heightIn(min = KMetrics.MinTouch)
            .widthIn(min = CELL)
            .drawBehind {
                val p = pop.value
                if (p <= 0f) return@drawBehind
                val h = minOf(size.height, PILL.toPx()) * (PILL_START + (1f - PILL_START) * p)
                val w = maxOf(h, size.width - PILL_INSET.toPx() * 2) * (PILL_START + (1f - PILL_START) * p)
                val topLeft = Offset((size.width - w) / 2, (size.height - h) / 2)
                drawShape(shape, colors.accent.copy(alpha = p.coerceIn(0f, 1f)), topLeft, Size(w, h))
            }
            .clip(shape)
            .kClickable(shape, enabled = !selected, role = null, onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
                this.selected = selected
            }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        ThemedText(
            "${index + 1}",
            style = TextStyle(
                color = if (selected) colors.onAccent else ink,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else weight,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun Ellipsis(ink: Color) {
    Box(Modifier.size(CELL, KMetrics.MinTouch).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        ThemedText("…", style = TextStyle(color = ink.copy(alpha = FAINT), fontSize = 15.sp))
    }
}

@Composable
private fun PageArrow(icon: ImageVector, label: String, enabled: Boolean, ink: Color, onClick: () -> Unit) {
    val shape = shapeFor { button }
    Box(
        Modifier
            .size(KMetrics.MinTouch)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED }
            .clip(shape)
            .kClickable(shape, enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Image(rememberVectorPainter(icon), null, Modifier.size(KMetrics.ButtonIcon), colorFilter = ColorFilter.tint(ink))
    }
}

/**
 * 파일 경로나 카테고리 위치를 보여 주는 이동 경로. 마지막 항목은 지금 위치라 굵게 쓰고 누를 수 없으며, 앞 항목은 강조색으로 눌러 이동한다.
 * 항목이 [maxVisible]보다 많으면 첫 항목과 끝 쪽 항목만 남기고 사이를 '…' 버튼으로 접는다. 누르면 모두 펼친다.
 * 줄이 넘치면 가로로 스크롤하고, 항목이 바뀌면 끝(지금 위치)이 보이게 스크롤한다. 구분 꺾쇠는 RTL에서 뒤집힌다.
 *
 * ```kotlin
 * KBreadcrumb(listOf("내 드라이브", "디자인", "2026", "아이콘"), onItemClick = { i -> path = path.take(i + 1) })
 * ```
 *
 * @param items 위치 이름. 비어 있으면 [IllegalArgumentException]을 던진다.
 * @param maxVisible 접지 않고 보여 줄 최대 항목 수. 3보다 작으면 3으로 본다.
 */
@Composable
public fun KBreadcrumb(
    items: List<String>,
    onItemClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    maxVisible: Int = 4,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(items.isNotEmpty()) { "items가 비어 있으면 안 됩니다." }
    var expanded by rememberSaveable(items) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(items, expanded) { scroll.animateScrollTo(scroll.maxValue) }
    val keep = maxVisible.coerceAtLeast(MIN_VISIBLE)
    val collapsed = !expanded && items.size > keep
    val shown: List<Int?> = if (collapsed) listOf(0, null) + (items.size - (keep - 2) until items.size).toList() else items.indices.toList()
    val ink = surface.contentColor
    Row(
        modifier.semantics { isTraversalGroup = true }.horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEachIndexed { position, index ->
            if (position > 0) {
                Image(
                    rememberVectorPainter(KGlyphs.ChevronForward),
                    null,
                    Modifier.size(SEPARATOR),
                    colorFilter = ColorFilter.tint(ink.copy(alpha = FAINT)),
                )
            }
            if (index == null) {
                CrumbItem("…", stringResource(R.string.kinetica_ui_breadcrumb_more), current = false, ink) { expanded = true }
            } else {
                CrumbItem(items[index], null, current = index == items.lastIndex, ink) { onItemClick(index) }
            }
        }
    }
}

@Composable
private fun CrumbItem(text: String, label: String?, current: Boolean, ink: Color, onClick: () -> Unit) {
    val accent = KineticaTheme.colors.accent
    val here = stringResource(R.string.kinetica_ui_breadcrumb_current)
    val shape = roundedShapeFor(CRUMB_CORNER) { button }
    ThemedText(
        text,
        Modifier
            .heightIn(min = KMetrics.MinTouch)
            .clip(shape)
            .then(if (current) Modifier else Modifier.kClickable(shape, onClick = onClick))
            .semantics {
                if (current) stateDescription = here
                label?.let { contentDescription = it }
            }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        style = TextStyle(
            color = if (current) ink else accent,
            fontSize = 15.sp,
            fontWeight = KineticaTheme.design.label(if (current) FontWeight.SemiBold else FontWeight.Medium),
        ),
        maxLines = 1,
    )
}

private val CELL = 40.dp
private val GAP = 2.dp
private val PILL = 36.dp
private val PILL_INSET = 2.dp
private val SEPARATOR = 14.dp
private val CRUMB_CORNER = 10.dp
private const val EDGE_SLOTS = 5
private const val MIN_VISIBLE = 3
private const val PILL_START = 0.6f
private const val POP_DAMPING = 0.55f
private const val POP_STIFFNESS = 600f
private const val FAINT = 0.4f
private const val DISABLED = 0.3f
