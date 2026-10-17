// 파일 이름은 대표 함수인 kListSection을 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * [KListSection]을 `LazyColumn` 안에 행 단위로 펼친다. 머리글, 행 하나하나, 꼬리글이 각각 지연 항목이 되므로
 * 행이 수백 개여도 화면에 보이는 행만 구성한다. 모양은 [KListSection]과 같다. [KListSectionStyle.InsetGrouped]면
 * 행마다 표면을 따로 그리지만, 위아래로 이어지는 가장자리는 그리지 않아 표면 하나처럼 보이고 첫 행 위와 끝 행 아래만 둥글다.
 *
 * 행 사이가 붙어 있어야 하므로 `LazyColumn`의 `verticalArrangement`에 간격을 주지 않는다. 섹션 사이 여백은 [header]의 위 여백이나
 * 별도 `item { Spacer(…) }`로 준다. 좌우 여백은 `contentPadding`이나 항목 바깥 여백으로 준다.
 *
 * ```kotlin
 * LazyColumn {
 *     kListSection(contacts, key = { it.id }, header = "연락처", sectionKey = "contacts") { contact ->
 *         KListItem(contact.name, leading = KListLeading.Avatar(contact.initial), onClick = { open(contact) })
 *     }
 * }
 * ```
 *
 * @param items 행 데이터
 * @param key 행을 구분하는 값. 지연 목록의 항목 key로 쓰므로 목록 전체에서 겹치지 않아야 하고, Android에서는 Bundle에 저장할 수 있는
 *   타입(문자열, 숫자, Parcelable)이어야 한다. `null`이면 위치로 구분한다.
 * @param sectionKey 머리글과 꼬리글 항목 key를 만들 값. 문자열로 바꿔 쓰므로 한 목록에 섹션이 여럿이면 섹션마다 다르게 준다.
 *   `null`이면 위치로 구분한다. 행 [key]처럼 Bundle에 저장할 수 있어야 한다는 제약은 없다.
 * @param surface 표면 스타일. `null`이면 [KineticaTheme.surface]를 쓴다.
 * @param itemContent 행 하나. 보통 [KListItem]이나 설정 행 프리셋을 넣는다.
 */
@Suppress("LongParameterList") // LazyListScope.items와 섹션 설정을 함께 받는다.
public fun <T> LazyListScope.kListSection(
    items: List<T>,
    key: ((T) -> Any)? = null,
    header: String? = null,
    footer: String? = null,
    style: KListSectionStyle = KListSectionStyle.InsetGrouped,
    surface: SurfaceStyle? = null,
    sectionKey: Any? = null,
    itemContent: @Composable (T) -> Unit,
) {
    val grouped = style == KListSectionStyle.InsetGrouped
    if (header != null) {
        item(key = sectionKey?.let { "$KEY_PREFIX$it:header" }) { ListSectionHeader(header, grouped, surface ?: KineticaTheme.surface) }
    }
    itemsIndexed(items, key = key?.let { k -> { _: Int, item: T -> k(item) } }) { index, item ->
        LazySectionRow(index == 0, index == items.lastIndex, grouped, surface ?: KineticaTheme.surface) { itemContent(item) }
    }
    if (footer != null) {
        item(key = sectionKey?.let { "$KEY_PREFIX$it:footer" }) { ListSectionFooter(footer, surface ?: KineticaTheme.surface) }
    }
}

@Composable
private fun LazySectionRow(first: Boolean, last: Boolean, grouped: Boolean, surface: SurfaceStyle, content: @Composable () -> Unit) {
    val shape = if (grouped) listSegmentShape(first, last) else RectangleShape
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val row = remember(shape, rtl) { KListRowContext(shape, rtl) }
    val separator = KineticaTheme.design.listSeparator
    val gap = grouped && separator == KListSeparator.Gap
    val base = shapeFor { listSection }
    val panel = remember(first, last, base) { ContinuedPanelShape(first, last, base) }
    Box(
        Modifier
            .then(if (gap && !last) Modifier.padding(bottom = LIST_ROW_GAP) else Modifier)
            .fillMaxWidth()
            .then(
                when {
                    gap -> Modifier.kSurface(surface, shape)
                    grouped -> Modifier.clipContinuedEdges(first, last).kSurface(surface, panel)
                    else -> Modifier
                },
            )
            .onPlaced { row.box = it }
            .then(if (last) Modifier else Modifier.listRowDivider(row, surface.contentColor.copy(alpha = LIST_DIVIDER_ALPHA), separator)),
    ) {
        CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor, LocalKListRow provides row, content = content)
    }
}

/**
 * 이어지는 쪽(첫 행이 아니면 위, 끝 행이 아니면 아래)으로 행 높이 밖에 그려진 것을 자른다. 좌우와 섹션 끝 쪽은 자르지 않아
 * 그림자가 보인다.
 */
private fun Modifier.clipContinuedEdges(first: Boolean, last: Boolean): Modifier = drawWithContent {
    val far = size.width + size.height + OVERFLOW.toPx()
    clipRect(-far, if (first) -far else 0f, size.width + far, if (last) size.height + far else size.height) {
        this@drawWithContent.drawContent()
    }
}

/**
 * 행 표면 모양. 이어지는 쪽으로 [CONTINUE]만큼 늘린 [base] 모양이라, 늘어난 쪽의 모서리와 테두리는 행 밖에 놓여 잘린다.
 * 그래서 행마다 따로 그려도 행 사이에 이음매나 가로 테두리가 생기지 않는다.
 */
private class ContinuedPanelShape(private val first: Boolean, private val last: Boolean, private val base: Shape) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val extend = with(density) { CONTINUE.toPx() }
        val top = if (first) 0f else -extend
        val bottom = if (last) size.height else size.height + extend
        val inner = base.createOutline(Size(size.width, bottom - top), layoutDirection, density)
        val offset = Offset(0f, top)
        return when (inner) {
            is Outline.Rectangle -> Outline.Rectangle(inner.rect.translate(offset))
            is Outline.Rounded -> Outline.Rounded(inner.roundRect.translate(offset))
            is Outline.Generic -> Outline.Generic(Path().apply { addPath(inner.path, offset) })
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ContinuedPanelShape && first == other.first && last == other.last && base == other.base

    override fun hashCode(): Int = (first.hashCode() * HASH + last.hashCode()) * HASH + base.hashCode()
}

// 지연 목록 key는 Bundle에 저장할 수 있어야 하므로 문자열로 만든다. 행 key와 겹치지 않게 앞에 붙인다.
private const val KEY_PREFIX = "kListSection:"
private const val HASH = 31

// 가장 큰 모서리(캡슐 토큰이면 행 높이의 절반)도 행 밖으로 밀어낼 만큼 늘린다.
private val CONTINUE: Dp = 120.dp
private val OVERFLOW = 64.dp
