// 파일 이름은 대표 컴포저블인 KListSection을 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** [KListSection]의 모양 */
public enum class KListSectionStyle {
    /** 설정 화면처럼 행들을 둥근 표면 하나에 담는다. 첫 행은 위 모서리, 끝 행은 아래 모서리만 둥글다. */
    InsetGrouped,

    /** 표면 없이 화면 바탕 위에 행을 놓는다. 구분선은 행 글자 시작부터 끝까지 그린다. */
    Plain,
}

/** [KListSection]에 행을 더하는 범위 */
public interface KListSectionScope {
    /**
     * 행 하나를 더한다. 보통 [KListItem]이나 [KSwitchRow] 같은 설정 행을 넣는다.
     *
     * @param key 행을 구분하는 값. 행이 더해지거나 빠질 때 상태가 다른 행으로 옮겨 가지 않게 한다. `null`이면 위치로 구분한다.
     */
    public fun item(key: Any? = null, content: @Composable () -> Unit)
}

/**
 * 목록 섹션. 머리글과 꼬리글, 그 사이 행들을 묶는다. 행 사이에는 행의 글자 시작 위치부터 구분선을 자동으로 긋는다.
 *
 * [KListSectionStyle.InsetGrouped]면 행들을 [surface] 표면 하나(모서리 20dp)에 담는다. 글래스, 단색, 톤 어느 표면이든
 * 판이 하나이므로 행 사이에 이음매가 없고, 행 눌림 표시는 첫 행 위 모서리와 끝 행 아래 모서리를 판과 같게 둥글린다.
 * 좌우 바깥 여백은 넣지 않으므로 `Modifier.padding(horizontal = 16.dp)`처럼 호출한 쪽이 준다.
 *
 * 행이 많아 스크롤해야 하면 섹션마다 `LazyColumn`의 `item { }` 하나에 넣는다. 섹션 안 행은 한꺼번에 구성된다.
 *
 * ```kotlin
 * KListSection(header = "네트워크", footer = "비행기 모드를 켜면 모든 무선 연결이 끊깁니다.") {
 *     item { KSwitchRow("비행기 모드", airplane, { airplane = it }) }
 *     item { KDisclosureRow("Wi-Fi", onClick = ::openWifi, value = "Home") }
 * }
 * ```
 *
 * @param header 섹션 위 작은 제목. TalkBack에 제목으로 알린다.
 * @param footer 섹션 아래 설명
 */
@Composable
public fun KListSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    style: KListSectionStyle = KListSectionStyle.InsetGrouped,
    surface: SurfaceStyle = KineticaTheme.surface,
    content: KListSectionScope.() -> Unit,
) {
    val items = SectionScope().apply(content).items
    val grouped = style == KListSectionStyle.InsetGrouped
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier) {
        header?.let { ListSectionHeader(it, grouped, surface) }
        Column(
            if (grouped) Modifier.fillMaxWidth().kSurface(surface, RoundedCornerShape(LIST_SECTION_CORNER)) else Modifier.fillMaxWidth(),
        ) {
            CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor) {
                val divider = surface.contentColor.copy(alpha = LIST_DIVIDER_ALPHA)
                items.forEachIndexed { index, item ->
                    key(item.key ?: index) {
                        val shape = if (grouped) listSegmentShape(index == 0, index == items.lastIndex) else RectangleShape
                        val row = remember(shape, rtl) { KListRowContext(shape, rtl) }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .onPlaced { row.box = it }
                                .then(if (index < items.lastIndex) Modifier.listRowDivider(row, divider) else Modifier),
                        ) { CompositionLocalProvider(LocalKListRow provides row) { item.content() } }
                    }
                }
            }
        }
        footer?.let { ListSectionFooter(it, surface) }
    }
}

/** 첫 행은 위, 끝 행은 아래 모서리만 판과 같게 둥글린 모양. 행이 하나면 네 모서리 모두 둥글다. */
internal fun listSegmentShape(first: Boolean, last: Boolean): Shape = RoundedCornerShape(
    topStart = if (first) LIST_SECTION_CORNER else 0.dp,
    topEnd = if (first) LIST_SECTION_CORNER else 0.dp,
    bottomStart = if (last) LIST_SECTION_CORNER else 0.dp,
    bottomEnd = if (last) LIST_SECTION_CORNER else 0.dp,
)

/** 행 아래 구분선. 행 글자가 시작하는 위치는 그리기 단계에서 읽어, 행을 다시 구성하지 않고 맞춘다. */
internal fun Modifier.listRowDivider(row: KListRowContext, color: Color): Modifier = drawWithContent {
    drawContent()
    val inset = if (row.textStart >= 0f) row.textStart else DEFAULT_DIVIDER_INSET.toPx()
    val stroke = HAIRLINE.toPx().coerceAtLeast(1f)
    val y = size.height - stroke / 2
    val from = if (row.rtl) 0f else inset
    val to = if (row.rtl) size.width - inset else size.width
    drawLine(color, Offset(from, y), Offset(to, y), stroke)
}

@Composable
internal fun ListSectionHeader(text: String, grouped: Boolean, surface: SurfaceStyle) {
    ThemedText(
        text,
        Modifier
            .padding(start = SECTION_TEXT_INSET, end = SECTION_TEXT_INSET, bottom = HEADER_BOTTOM, top = if (grouped) 0.dp else HEADER_TOP)
            .semantics { heading() },
        style = TextStyle(
            color = surface.contentColor.copy(alpha = HEADER_ALPHA),
            fontSize = if (grouped) 13.sp else 14.sp,
            fontWeight = if (grouped) FontWeight.Medium else FontWeight.SemiBold,
        ),
    )
}

@Composable
internal fun ListSectionFooter(text: String, surface: SurfaceStyle) {
    ThemedText(
        text,
        Modifier.padding(start = SECTION_TEXT_INSET, end = SECTION_TEXT_INSET, top = FOOTER_TOP),
        style = TextStyle(color = surface.contentColor.copy(alpha = FOOTER_ALPHA), fontSize = 13.sp, lineHeight = 18.sp),
    )
}

private class SectionScope : KListSectionScope {
    val items = mutableListOf<SectionItem>()

    override fun item(key: Any?, content: @Composable () -> Unit) {
        items += SectionItem(key, content)
    }
}

private class SectionItem(val key: Any?, val content: @Composable () -> Unit)

/**
 * 섹션이 행에 알려 주는 것. [shape]은 눌림 표시 모양이고, 행은 글자 시작 위치를 [textStart]에 적어 구분선이 그 위치부터 그려지게 한다.
 */
internal class KListRowContext(val shape: Shape, val rtl: Boolean) {
    var box: LayoutCoordinates? = null

    /** 행 시작 가장자리에서 글자까지 거리(px). 아직 재지 않았으면 음수다. */
    var textStart by mutableFloatStateOf(-1f)
}

/** 글자 칸에 붙여 [row]의 행 시작 가장자리에서 글자까지 거리를 잰다. */
internal fun Modifier.textStartIn(row: KListRowContext): Modifier = onPlaced { coords ->
    val parent = row.box ?: return@onPlaced
    if (!parent.isAttached || !coords.isAttached) return@onPlaced
    val x = parent.localPositionOf(coords, Offset.Zero).x
    row.textStart = if (row.rtl) parent.size.width - (x + coords.size.width) else x
}

/** 지금 그리는 행이 놓인 섹션 정보. 섹션 밖에서는 null이다. */
internal val LocalKListRow = staticCompositionLocalOf<KListRowContext?> { null }

/**
 * 가로 구분선. 두께가 [Dp.Hairline]이면 화면 픽셀 하나 두께로 그린다.
 *
 * @param startInset 시작 쪽 들여쓰기. 목록 행의 글자 시작에 맞출 때 쓴다.
 * @param color 선 색. [Color.Unspecified]면 [surface] 글자 색을 12%로 쓴다.
 */
@Composable
public fun KDivider(
    modifier: Modifier = Modifier,
    startInset: Dp = 0.dp,
    endInset: Dp = 0.dp,
    thickness: Dp = Dp.Hairline,
    color: Color = Color.Unspecified,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val line = if (color.isSpecified) color else surface.contentColor.copy(alpha = LIST_DIVIDER_ALPHA)
    val height = if (thickness == Dp.Hairline) with(LocalDensity.current) { 1f.toDp() } else thickness
    Box(modifier.fillMaxWidth().padding(start = startInset, end = endInset).height(height).background(line))
}

/** 세로 구분선. 툴바 버튼 묶음이나 두 칸 사이에 쓴다. 높이는 부모를 채운다. */
@Composable
public fun KVerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = Dp.Hairline,
    color: Color = Color.Unspecified,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val line = if (color.isSpecified) color else surface.contentColor.copy(alpha = LIST_DIVIDER_ALPHA)
    val width = if (thickness == Dp.Hairline) with(LocalDensity.current) { 1f.toDp() } else thickness
    Box(modifier.fillMaxHeight().width(width).background(line))
}

/** 목록 행 끝의 꺾쇠. 오른쪽에서 왼쪽 언어에서는 왼쪽을 가리킨다. */
@Composable
internal fun DisclosureChevron(color: Color, modifier: Modifier) {
    Box(
        modifier.drawBehind {
            val stroke = CHEVRON_STROKE.toPx()
            val w = size.width * CHEVRON_WIDTH
            val h = size.height * CHEVRON_HEIGHT
            val cx = size.width / 2
            val cy = size.height / 2
            val dir = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
            val tip = Offset(cx + dir * w / 2, cy)
            drawLine(color, Offset(cx - dir * w / 2, cy - h / 2), tip, stroke, StrokeCap.Round)
            drawLine(color, Offset(cx - dir * w / 2, cy + h / 2), tip, stroke, StrokeCap.Round)
        },
    )
}

internal val LIST_SECTION_CORNER = 20.dp
private val SECTION_TEXT_INSET = 16.dp
private val HEADER_TOP = 8.dp
private val HEADER_BOTTOM = 6.dp
private val FOOTER_TOP = 6.dp
private val DEFAULT_DIVIDER_INSET = 16.dp
private val HAIRLINE = 0.5.dp
internal const val LIST_DIVIDER_ALPHA = 0.12f
private const val HEADER_ALPHA = 0.6f
private const val FOOTER_ALPHA = 0.55f
private val CHEVRON_STROKE = 1.8.dp
private const val CHEVRON_WIDTH = 0.36f
private const val CHEVRON_HEIGHT = 0.72f
