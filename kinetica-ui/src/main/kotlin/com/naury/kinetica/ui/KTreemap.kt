package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/**
 * 트리맵. 값에 비례한 넓이의 사각형으로 영역을 나눈다. squarified 배치라 칸이 되도록 정사각형에 가깝고, 큰 항목이 시작 쪽 위에 온다.
 * RTL에서는 좌우를 뒤집어 큰 항목이 오른쪽 위에 온다. 처음 보일 때 큰 칸부터 차례로 제자리에서 커지며 나타나고,
 * '애니메이션 삭제' 설정에서는 바로 최종 모양을 그린다. 값이 바뀌면 바로 새 배치로 바뀐다.
 *
 * 칸 안에는 이름과 값이 들어갈 때만 적는다. 둘 다 들어가지 않으면 작은 글자로 이름만, 그것도 들어가지 않으면 비운다.
 * [selectedKey] 칸은 테두리로 강조하고 나머지 칸은 옅게 한다.
 *
 * TalkBack은 전체 요약(항목 수와 합계)을 읽고, 큰 항목부터 목록처럼 칸마다 '식비: 420,000, 32%'를 읽는다.
 * 크기를 정하지 않으면 최소 240×160dp이고, 높이를 정하지 않으면 폭의 62%를 높이로 쓴다.
 *
 * ```kotlin
 * KTreemap(
 *     listOf(TreemapItem("food", "식비", 420_000f), TreemapItem("rent", "주거", 650_000f)),
 *     Modifier.fillMaxWidth().height(220.dp),
 *     selectedKey = picked,
 *     onItemClick = { picked = if (picked == it.key) null else it.key },
 * )
 * ```
 *
 * @param items 항목. [TreemapItem.key]가 겹치면 [IllegalArgumentException]을 던진다. 비어 있거나 합이 0이면 빈 영역만 차지한다.
 * @param selectedKey 강조할 항목의 키. 목록에 없는 키면 아무것도 강조하지 않는다.
 * @param onItemClick 칸을 누르면 부른다. `null`이면 누를 수 없다.
 * @param colors 색을 정하지 않은 항목에 큰 것부터 차례로 쓸 색. 모자라면 처음부터 돌려 쓴다. 비어 있으면 [IllegalArgumentException]을 던진다.
 * @param valueFormatter 칸과 TalkBack에 쓸 값 문자열
 */
@Composable
public fun KTreemap(
    items: List<TreemapItem>,
    modifier: Modifier = Modifier,
    selectedKey: Any? = null,
    onItemClick: ((TreemapItem) -> Unit)? = null,
    colors: List<Color> = defaultChartColors(),
    valueFormatter: (Float) -> String = KChartDefaults::formatValue,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(colors.isNotEmpty()) { "colors는 하나 이상이어야 합니다." }
    val model = remember(items) { TreemapModel(items) }
    val reveal = rememberChartReveal(TREEMAP_REVEAL_MS)
    val selectedRank = model.rankOf(selectedKey)
    val dim = animateFloatAsState(if (selectedRank >= 0) 1f else 0f, spring(), label = "treemap-dim")
    val count = model.ranked.size
    val summary = pluralStringResource(R.plurals.kinetica_ui_treemap_summary, count, count, valueFormatter(model.total))
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val latestClick = rememberUpdatedState(onItemClick)
    val look = TreemapLook(colors, surface.contentColor, valueFormatter, reveal, dim)
    Layout(
        content = {
            model.ranked.forEachIndexed { rank, item ->
                key(item.key) {
                    val tap = remember(item) { { latestClick.value?.invoke(item) ?: Unit } }
                    val click = if (onItemClick == null) null else tap
                    TreemapTile(item, rank, TileState(model, rank == selectedRank), look, click)
                }
            }
        },
        modifier = modifier.defaultMinSize(TREEMAP_MIN_WIDTH, TREEMAP_MIN_HEIGHT).chartSummary(summary).chartTable(model.ranked.size, 1),
    ) { measurables, constraints -> placeTiles(model, measurables, constraints, rtl) }
}

/** 칸마다 [squarify] 결과 크기로 재고 놓는다. 이웃 칸 경계가 같은 화소에 오도록 반올림한 좌표에서 틈을 뺀다. */
private fun MeasureScope.placeTiles(
    model: TreemapModel,
    measurables: List<Measurable>,
    constraints: Constraints,
    rtl: Boolean,
): MeasureResult {
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
    // 높이를 정해 주면 그 높이를 채우고, 아니면 폭의 일정 비율을 받은 범위 안에 맞춘다.
    val height = (width * DEFAULT_ASPECT).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
    val rects = model.rects(width, height)
    val gap = TILE_GAP.roundToPx()
    val xs = IntArray(measurables.size)
    val ys = IntArray(measurables.size)
    val placeables = measurables.mapIndexed { rank, measurable ->
        val at = rank * RECT_FIELDS
        val left = rects[at + RECT_LEFT].roundToInt()
        val right = rects[at + RECT_RIGHT].roundToInt()
        val top = rects[at + RECT_TOP].roundToInt()
        val bottom = rects[at + RECT_BOTTOM].roundToInt()
        xs[rank] = (if (rtl) width - right else left) + gap / 2
        ys[rank] = top + gap / 2
        measurable.measure(Constraints.fixed((right - left - gap).coerceAtLeast(0), (bottom - top - gap).coerceAtLeast(0)))
    }
    return layout(width, height) { placeables.forEachIndexed { rank, tile -> tile.place(xs[rank], ys[rank]) } }
}

/** 큰 것부터 줄 세운 양수 항목과 비율. 배치 결과는 크기가 같으면 다시 계산하지 않는다. */
private class TreemapModel(items: List<TreemapItem>) {
    init {
        require(items.map { it.key }.toSet().size == items.size) { "items의 key가 겹칩니다." }
    }

    val ranked: List<TreemapItem> = items.filter { it.value > 0f }.sortedByDescending { it.value }
    val total: Float = ranked.sumOf { it.value.toDouble() }.toFloat()
    private val values = FloatArray(ranked.size) { ranked[it].value }

    val percents: IntArray = roundedPercents(DoubleArray(ranked.size) { ranked[it].value.toDouble() })
    private var cachedWidth = -1
    private var cachedHeight = -1
    private var cached = FloatArray(0)

    fun rankOf(key: Any?): Int = if (key == null) -1 else ranked.indexOfFirst { it.key == key }

    fun rects(width: Int, height: Int): FloatArray {
        if (width != cachedWidth || height != cachedHeight) {
            cached = squarify(values, width.toFloat(), height.toFloat())
            cachedWidth = width
            cachedHeight = height
        }
        return cached
    }

    /** 순번 [rank]가 나타나기 시작하는 진행 값. 큰 칸부터 차례로 나타난다. */
    fun revealStart(rank: Int): Float = if (ranked.size <= 1) 0f else STAGGER_SPAN * rank / (ranked.size - 1)
}

private class TreemapLook(
    val colors: List<Color>,
    val ink: Color,
    val valueFormatter: (Float) -> String,
    val reveal: Animatable<Float, AnimationVector1D>,
    val dim: State<Float>,
)

private class TileState(val model: TreemapModel, val selected: Boolean)

@Composable
private fun TreemapTile(item: TreemapItem, rank: Int, state: TileState, look: TreemapLook, onClick: (() -> Unit)?) {
    val model = state.model
    val color = item.color ?: look.colors[rank % look.colors.size]
    val description =
        stringResource(R.string.kinetica_ui_chart_slice, item.label, look.valueFormatter(item.value), model.percents[rank])
    val haptic = kHapticFeedback()
    val selected = state.selected
    val press = if (onClick == null) {
        Modifier
    } else {
        Modifier.pointerInput(onClick) {
            detectTapGestures {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
        }
    }
    Layout(
        content = { TileLabel(item.label, look.valueFormatter(item.value), labelInk(color)) },
        modifier = Modifier
            .semantics(mergeDescendants = true) {
                contentDescription = description
                this.selected = selected
                traversalIndex = rank.toFloat()
                collectionItemInfo = CollectionItemInfo(rank, 1, 0, 1)
                if (onClick != null) {
                    onClick {
                        onClick()
                        true
                    }
                }
            }.then(press)
            .tileLook(color, selected, model.revealStart(rank), look),
    ) { measurables, constraints ->
        val pad = TILE_PAD.roundToPx()
        val label = fitTileLabel(measurables, constraints, pad)
        layout(constraints.maxWidth, constraints.maxHeight) { label?.place(pad, pad) }
    }
}

/** 처음 보일 때 [start]부터 제자리에서 커지며 나타나는 둥근 칸. 고른 칸은 테두리를 두르고, 고른 칸이 있으면 나머지는 옅어진다. */
private fun Modifier.tileLook(color: Color, selected: Boolean, start: Float, look: TreemapLook): Modifier = graphicsLayer {
    val grow = chartStage(look.reveal.value, start, start + TILE_GROW_SPAN)
    scaleX = lerp(TILE_START_SCALE, 1f, grow)
    scaleY = scaleX
    alpha = grow
}.drawWithCache {
    val corner = CornerRadius(minOf(TILE_CORNER.toPx(), size.minDimension / 2))
    val ring = Stroke(SELECT_RING.toPx())
    val inset = ring.width / 2
    onDrawBehind {
        val alpha = if (selected) 1f else lerp(1f, CHART_DIM_ALPHA, look.dim.value)
        drawRoundRect(color, cornerRadius = corner, alpha = alpha)
        if (selected && size.minDimension > ring.width * 2) {
            drawRoundRect(look.ink, Offset(inset, inset), Size(size.width - ring.width, size.height - ring.width), corner, style = ring)
        }
    }
}

/** 칸 안 글자 후보 두 가지(이름과 값, 작은 이름). TalkBack은 칸 이름으로 읽으므로 글자는 읽지 않는다. */
@Composable
private fun TileLabel(label: String, value: String, ink: Color) {
    val name = TextStyle(color = ink, fontSize = LABEL_TEXT, fontWeight = FontWeight.SemiBold)
    Column(Modifier.clearAndSetSemantics { }) {
        ThemedText(label, style = name, maxLines = 2, overflow = TextOverflow.Ellipsis)
        ThemedText(value, style = TextStyle(color = ink.copy(alpha = VALUE_ALPHA), fontSize = SMALL_TEXT), maxLines = 1)
    }
    ThemedText(
        label,
        Modifier.clearAndSetSemantics { },
        style = name.copy(fontSize = SMALL_TEXT),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 칸 안에 들어가는 첫 후보. 폭이 너무 좁거나 높이가 모자라면 `null`이라 글자를 비운다. */
private fun MeasureScope.fitTileLabel(measurables: List<Measurable>, constraints: Constraints, pad: Int): Placeable? {
    val width = constraints.maxWidth - pad * 2
    val height = constraints.maxHeight - pad * 2
    if (width < MIN_LABEL_WIDTH.roundToPx() || height <= 0) return null
    val box = Constraints(maxWidth = width)
    val full = measurables.first()
    // 가장 긴 낱말이 칸보다 넓으면 낱말 중간에서 줄이 바뀌므로 작은 이름으로 넘어간다.
    if (full.minIntrinsicWidth(Constraints.Infinity) <= width) {
        full.measure(box).takeIf { it.height <= height }?.let { return it }
    }
    return measurables.getOrNull(1)?.measure(box)?.takeIf { it.height <= height }
}

private fun labelInk(color: Color): Color = if (color.luminance() > LIGHT_TILE) Color.Black.copy(alpha = DARK_INK) else Color.White

private const val TREEMAP_REVEAL_MS = 900
private const val STAGGER_SPAN = 0.45f
private const val TILE_GROW_SPAN = 0.55f
private const val TILE_START_SCALE = 0.6f
private const val DEFAULT_ASPECT = 0.62f
private const val LIGHT_TILE = 0.5f
private const val DARK_INK = 0.85f
private const val VALUE_ALPHA = 0.8f
private val TILE_GAP = 2.dp
private val TILE_CORNER = 8.dp
private val TILE_PAD = 8.dp
private val SELECT_RING = 2.dp
private val MIN_LABEL_WIDTH = 36.dp
private val LABEL_TEXT = 13.sp
private val SMALL_TEXT = 11.sp
private val TREEMAP_MIN_WIDTH = 240.dp
private val TREEMAP_MIN_HEIGHT = 160.dp
