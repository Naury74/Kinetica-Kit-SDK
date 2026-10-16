// 세그먼트 컨트롤의 칸 모델과 오버로드를 한 파일에 둔다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * [KSegmentedControl]의 칸 하나. 글자만, 아이콘만, 또는 아이콘과 글자를 함께 쓸 수 있다.
 * [label]과 [icon]이 둘 다 null이면 [IllegalArgumentException]을 던진다.
 *
 * @property contentDescription 아이콘만 있는 칸을 TalkBack이 읽을 이름. [label]이 있으면 무시한다.
 */
@Immutable
public data class KSegment(val label: String? = null, val icon: ImageVector? = null, val contentDescription: String? = null) {
    init {
        require(label != null || icon != null) { "label과 icon 중 하나는 있어야 합니다." }
    }
}

/** [KSegmentedControl]이 칸 폭을 나누는 방식. */
public enum class KSegmentSizing {
    /** 모든 칸을 같은 폭으로 나눈다. 이름 길이가 비슷할 때 쓴다. */
    Equal,

    /**
     * 칸 내용의 폭에 비례해 나눈다. 폭이 남으면 칸마다 똑같이 더하고, 모자라면 내용 폭 비율대로 줄인다.
     * "토큰 서버 / 기존 토큰 / 엔드포인트"처럼 이름 길이가 크게 다를 때 짧은 칸에 남는 폭을 긴 칸에 준다.
     */
    Proportional,
}

/**
 * 세그먼트 버튼. 보기 방식이나 설정 값 하나를 고르는 곳에 쓴다. 선택 인디케이터가 [KTabBar]처럼 늘어났다 줄어들며 이동하고,
 * TalkBack은 항목마다 선택 상태를 읽는다. 아이콘을 넣으려면 [KSegment]를 받는 오버로드를 쓴다.
 *
 * 이름은 [maxLines] 줄까지 줄을 바꾸고, 큰 글꼴에서는 컨트롤 높이가 그만큼 늘어난다. 그래도 넘치면 말줄임표로 줄인다.
 *
 * @param options 항목 이름. 두 개 미만이면 [IllegalArgumentException]을 던진다.
 * @param selectedIndex 선택된 항목의 위치(0부터)
 * @param enabled `false`면 누를 수 없고 흐리게 그린다.
 * @param tone 선택 인디케이터 색. [KTone.Neutral]이면 표면 글자 색을 옅게 깔고, 나머지는 그 색으로 채우고 선택된 이름을 그 위 글자 색으로 쓴다.
 * @param role TalkBack이 읽을 칸의 역할. 기본값은 [Role.RadioButton]이다. 여러 값 중 하나를 고르는 컨트롤이므로 "라디오 버튼, 3개 중 1번째"처럼
 *   읽히는 쪽이 설정 화면에 맞다. 컨트롤 아래 내용 영역을 통째로 바꾸는 탭처럼 쓰면 [Role.Tab]을 넘긴다.
 * @param maxLines 이름 한 칸의 최대 줄 수. 1보다 작으면 [IllegalArgumentException]을 던진다.
 * @param sizing 칸 폭을 나누는 방식
 */
@Composable
public fun KSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
    tone: KTone = KTone.Neutral,
    role: Role = Role.RadioButton,
    maxLines: Int = DEFAULT_MAX_LINES,
    sizing: KSegmentSizing = KSegmentSizing.Equal,
) {
    require(options.size >= 2) { "options는 두 개 이상이어야 합니다." }
    KSegmentedControl(options.map { KSegment(it) }, selectedIndex, onSelect, modifier, enabled, surface, tone, role, maxLines, sizing)
}

/**
 * 아이콘을 넣을 수 있는 세그먼트 버튼. 하나만 고른다. 동작과 파라미터는 글자만 받는 [KSegmentedControl]과 같다.
 *
 * ```kotlin
 * val segments = listOf(KSegment(icon = ListIcon, contentDescription = "목록"), KSegment(icon = GridIcon, contentDescription = "격자"))
 * KSegmentedControl(segments, view, { view = it }, tone = KTone.Accent)
 * ```
 *
 * @param segments 칸 목록. 두 개 미만이면 [IllegalArgumentException]을 던진다.
 * @param maxLines 이름 한 칸의 최대 줄 수. 1보다 작으면 [IllegalArgumentException]을 던진다.
 */
@JvmName("KSegmentedControlOfSegments")
@Composable
public fun KSegmentedControl(
    segments: List<KSegment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
    tone: KTone = KTone.Neutral,
    role: Role = Role.RadioButton,
    maxLines: Int = DEFAULT_MAX_LINES,
    sizing: KSegmentSizing = KSegmentSizing.Equal,
) {
    require(segments.size >= 2) { "segments는 두 개 이상이어야 합니다." }
    require(maxLines >= 1) { "maxLines는 1 이상이어야 합니다." }
    var edges by remember { mutableStateOf(SegmentEdges.Empty) }
    val indicator = remember { LiquidIndicator() }
    LaunchedEffect(selectedIndex, edges) { edges.of(selectedIndex)?.let { indicator.moveTo(it.first, it.second) } }
    val paint = segmentInk(tone, surface)
    Box(modifier.heightIn(min = SEGMENT_HEIGHT).kSurface(surface, SegmentShape).padding(SEGMENT_INSET)) {
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val left = indicator.left.value
                    val right = indicator.right.value
                    if (right > left) {
                        drawRoundRect(paint.indicator, Offset(left, 0f), Size(right - left, size.height), cellCorner())
                    }
                },
        )
        SegmentRow(sizing, { if (it != edges) edges = it }) {
            segments.forEachIndexed { index, segment ->
                val selected = index == selectedIndex
                val ink by animateColorAsState(paint.content(selected, enabled), tween(COLOR_MS), label = "segment-ink")
                Box(
                    Modifier
                        .selectable(selected, null, KIndication(CellShape), enabled, role) { onSelect(index) }
                        .describe(segment),
                    contentAlignment = Alignment.Center,
                ) {
                    SegmentContent(segment, ink, selected, maxLines)
                }
            }
        }
    }
}

/** 칸마다 물리 좌표(px)의 왼쪽과 오른쪽 끝. RTL에서도 화면 왼쪽에서 잰다. */
@Immutable
private class SegmentEdges(private val left: FloatArray, private val right: FloatArray) {
    fun of(index: Int): Pair<Float, Float>? = if (index in left.indices) left[index] to right[index] else null

    override fun equals(other: Any?): Boolean = other is SegmentEdges && left.contentEquals(other.left) && right.contentEquals(other.right)

    override fun hashCode(): Int = 31 * left.contentHashCode() + right.contentHashCode()

    companion object {
        val Empty = SegmentEdges(FloatArray(0), FloatArray(0))
    }
}

/**
 * 칸을 [sizing]에 따라 나눠 놓는다. 높이는 가장 긴 칸의 줄 수에 맞추고 모든 칸을 그 높이로 채워, 큰 글꼴에서 이름이 두 줄이 되면
 * 컨트롤 전체가 함께 높아진다. 인디케이터를 그리도록 칸 끝 좌표를 [onEdges]로 알린다.
 */
@Composable
private fun SegmentRow(sizing: KSegmentSizing, onEdges: (SegmentEdges) -> Unit, content: @Composable () -> Unit) {
    Layout(content, Modifier.selectableGroup()) { measurables, constraints ->
        val natural = IntArray(measurables.size) { measurables[it].maxIntrinsicWidth(Constraints.Infinity) }
        val total = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            (if (sizing == KSegmentSizing.Equal) natural.max() * natural.size else natural.sum()).coerceAtLeast(constraints.minWidth)
        }
        val words = IntArray(measurables.size) { measurables[it].minIntrinsicWidth(Constraints.Infinity) }
        val widths = segmentWidths(natural, total, sizing, words)
        val minHeight = maxOf((SEGMENT_HEIGHT - SEGMENT_INSET * 2).roundToPx(), constraints.minHeight)
        val height = widths.indices.maxOf { measurables[it].maxIntrinsicHeight(widths[it]) }
            .coerceAtLeast(minHeight)
            .coerceAtMost(constraints.maxHeight)
        val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixed(widths[i], height)) }
        val left = FloatArray(widths.size)
        val right = FloatArray(widths.size)
        var start = 0
        widths.forEachIndexed { i, w ->
            // 인디케이터는 물리 좌표로 그리므로 RTL에서는 칸 순서를 뒤집어 잰다.
            val physical = if (layoutDirection == LayoutDirection.Rtl) total - start - w else start
            left[i] = physical.toFloat()
            right[i] = (physical + w).toFloat()
            start += w
        }
        onEdges(SegmentEdges(left, right))
        layout(total, height) {
            var x = 0
            placeables.forEach {
                it.placeRelative(x, 0)
                x += it.width
            }
        }
    }
}

/**
 * [natural] 내용 폭을 [total]에 맞춰 나눈 칸 폭. 합은 언제나 [total]이다.
 * [minimum](칸마다 가장 긴 단어 폭)보다 좁은 칸이 있으면 여유 있는 칸에서 폭을 덜어 준다. 그래야 큰 글꼴에서 단어 중간이 끊기지 않는다.
 * 모든 칸의 [minimum] 합이 [total]보다 크면 덜어 줄 수 없으므로 그대로 둔다.
 */
internal fun segmentWidths(natural: IntArray, total: Int, sizing: KSegmentSizing, minimum: IntArray = IntArray(natural.size)): IntArray {
    val count = natural.size
    val sum = natural.sum()
    val widths = when {
        sizing == KSegmentSizing.Equal || sum <= 0 -> IntArray(count) { total / count }
        sum <= total -> IntArray(count) { natural[it] + (total - sum) / count }
        else -> IntArray(count) { (total.toLong() * natural[it] / sum).toInt() }
    }
    if (minimum.sum() <= total) giveMinimum(widths, minimum)
    // 나누고 남은 몇 px은 앞 칸부터 하나씩 더한다.
    var rest = total - widths.sum()
    var i = 0
    while (rest > 0) {
        widths[i % count]++
        rest--
        i++
    }
    return widths
}

/** 모자란 칸을 [minimum]까지 넓히고, 그만큼을 여유 있는 칸에서 여유에 비례해 덜어 낸다. 합은 늘지 않는다. */
private fun giveMinimum(widths: IntArray, minimum: IntArray) {
    val lack = widths.indices.sumOf { (minimum[it] - widths[it]).coerceAtLeast(0) }
    if (lack == 0) return
    val spare = widths.indices.sumOf { (widths[it] - minimum[it]).coerceAtLeast(0) }
    widths.indices.forEach {
        val surplus = widths[it] - minimum[it]
        widths[it] =
            if (surplus < 0) minimum[it] else widths[it] - ((surplus.toLong() * lack + spare - 1) / spare).toInt().coerceAtMost(surplus)
    }
}

/** 선택 인디케이터와 칸 글자 색. */
@Immutable
private class SegmentInk(val indicator: Color, val selected: Color, val unselected: Color) {
    fun content(selected: Boolean, enabled: Boolean): Color {
        val color = if (selected) this.selected else unselected
        return if (enabled) color else color.copy(alpha = color.alpha * DISABLED)
    }
}

@Composable
private fun segmentInk(tone: KTone, surface: SurfaceStyle): SegmentInk {
    val ink = surface.contentColor
    val unselected = ink.copy(alpha = UNSELECTED)
    if (tone == KTone.Neutral) return SegmentInk(ink.copy(alpha = SEGMENT_ALPHA), ink, unselected)
    val colors = tone.colors(surface)
    return SegmentInk(colors.color, colors.onColor, unselected)
}

/**
 * 여러 칸을 함께 고르는 세그먼트 버튼. 칸마다 따로 켜고 끄며, 켜진 칸은 옅은 캡슐이 깔리고 이름 앞에 체크 표시가 밀려 들어온다.
 * 필터나 요일 고르기처럼 몇 개를 동시에 켤 때 쓴다. TalkBack은 칸마다 체크 상태를 읽는다.
 *
 * ```kotlin
 * KSegmentedControl(days.map { KSegment(it) }, selectedDays, { selectedDays = it })
 * ```
 *
 * @param segments 칸 목록. 두 개 미만이면 [IllegalArgumentException]을 던진다.
 * @param selected 켜진 칸 위치(0부터) 집합. 범위 밖 값은 무시한다.
 * @param onSelectedChange 바뀐 집합을 받는다.
 * @param requireSelection true면 마지막 남은 칸은 끌 수 없다.
 * @param tone 켜진 칸의 색. [KTone.Neutral]이면 표면 글자 색을 옅게 깔고, 나머지는 그 색으로 채운다.
 * @param maxLines 이름 한 칸의 최대 줄 수. 넘으면 말줄임표로 줄이고, 줄이 늘면 컨트롤이 함께 높아진다. 1보다 작으면 [IllegalArgumentException]을 던진다.
 */
@Composable
public fun KSegmentedControl(
    segments: List<KSegment>,
    selected: Set<Int>,
    onSelectedChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    requireSelection: Boolean = false,
    surface: SurfaceStyle = KineticaTheme.surface,
    tone: KTone = KTone.Neutral,
    maxLines: Int = DEFAULT_MAX_LINES,
) {
    require(segments.size >= 2) { "segments는 두 개 이상이어야 합니다." }
    require(maxLines >= 1) { "maxLines는 1 이상이어야 합니다." }
    val still = reducedMotion()
    val paint = segmentInk(tone, surface)
    Row(
        modifier
            .heightIn(min = SEGMENT_HEIGHT)
            .kSurface(surface, SegmentShape)
            .padding(SEGMENT_INSET)
            // 큰 글꼴에서 한 칸만 두 줄이 되어도 모든 칸의 캡슐 높이를 맞춘다.
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, segment ->
            val on = index in selected
            val locked = requireSelection && on && selected.size == 1
            MultiSegment(segment, MultiState(on, enabled && !locked, enabled, still, maxLines), paint) {
                onSelectedChange(if (on) selected - index else selected + index)
            }
        }
    }
}

/** 여러 칸 세그먼트의 칸 하나가 그릴 상태. */
@Immutable
private class MultiState(val on: Boolean, val clickable: Boolean, val enabled: Boolean, val still: Boolean, val maxLines: Int)

@Composable
private fun RowScope.MultiSegment(segment: KSegment, state: MultiState, paint: SegmentInk, onToggle: () -> Unit) {
    val on = state.on
    val fill by animateFloatAsState(if (on) 1f else 0f, if (state.still) tween(0) else tween(FILL_MS), label = "segment-fill")
    val ink by animateColorAsState(paint.content(on, true), if (state.still) tween(0) else tween(FILL_MS), label = "segment-ink")
    Row(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .heightIn(min = SEGMENT_HEIGHT - SEGMENT_INSET * 2)
            .drawBehind {
                if (fill > 0f) {
                    drawRoundRect(paint.indicator.copy(alpha = paint.indicator.alpha * fill), cornerRadius = cellCorner())
                }
            }
            .toggleable(on, null, KIndication(CellShape), state.clickable, Role.Checkbox) { onToggle() }
            .graphicsLayer { alpha = if (state.enabled) 1f else DISABLED }
            .describe(segment)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            on,
            enter = if (state.still) fadeIn(tween(0)) else expandHorizontally() + fadeIn(),
            exit = if (state.still) fadeOut(tween(0)) else shrinkHorizontally() + fadeOut(),
        ) {
            Row {
                Glyph(KGlyphs.Check, ink, size = CHECK_SIZE)
                Spacer(Modifier.width(4.dp))
            }
        }
        SegmentContent(segment, ink, on, state.maxLines)
    }
}

/** 아이콘만 있는 칸에 TalkBack 이름을 붙인다. */
private fun Modifier.describe(segment: KSegment): Modifier {
    val description = segment.contentDescription
    return if (segment.label == null && description != null) semantics { contentDescription = description } else this
}

@Composable
private fun SegmentContent(segment: KSegment, color: Color, selected: Boolean, maxLines: Int) {
    Row(Modifier.padding(horizontal = 6.dp, vertical = LABEL_VERTICAL), verticalAlignment = Alignment.CenterVertically) {
        segment.icon?.let {
            Glyph(it, color, size = SEGMENT_ICON)
            if (segment.label != null) Spacer(Modifier.width(6.dp))
        }
        segment.label?.let {
            ThemedText(
                it,
                style = TextStyle(
                    color = color,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    // 두 줄로 바뀔 때 줄 길이를 고르게 맞추고, 한국어도 어절 단위로 끊는다.
                    lineBreak = LineBreak.Heading,
                ),
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val SEGMENT_HEIGHT = 44.dp
private val SEGMENT_INSET = 4.dp
private val SEGMENT_ICON = 18.dp
private val CHECK_SIZE = 16.dp
private const val SEGMENT_ALPHA = 0.14f
private const val DISABLED = 0.4f
private const val UNSELECTED = 0.6f
private const val FILL_MS = 160
private const val COLOR_MS = 180
private const val DEFAULT_MAX_LINES = 2
private val LABEL_VERTICAL = 4.dp

// 한 줄일 때는 캡슐이고, 큰 글꼴로 높아지면 모서리를 이 반지름에서 멈춰 큰 타원이 되지 않게 한다.
private val SegmentShape = RoundedCornerShape(SEGMENT_HEIGHT / 2)
private val CELL_CORNER = KMetrics.concentric(SEGMENT_HEIGHT / 2, SEGMENT_INSET)
private val CellShape = RoundedCornerShape(CELL_CORNER)

private fun DrawScope.cellCorner(): CornerRadius = CornerRadius(minOf(size.height / 2, CELL_CORNER.toPx()))
