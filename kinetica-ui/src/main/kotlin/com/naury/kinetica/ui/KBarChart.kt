package com.naury.kinetica.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.ceil

/**
 * 막대 그래프. 처음 보일 때 막대가 기준선에서 차례로 자라고, 막대를 누르면 그 막대만 진하게 남기고 값 말풍선을 띄운다.
 * 같은 막대를 다시 누르면 닫는다. 음수 값은 기준선(0) 반대쪽으로 자란다.
 * 값이 바뀌면 막대 수가 같을 때 이전 길이에서 새 길이로 부드럽게 바뀐다. '애니메이션 삭제' 접근성 설정이 켜져 있으면 처음부터 최종 모양을 그린다.
 *
 * 세로 막대는 오른쪽에서 왼쪽으로 쓰는 언어에서도 항목을 왼쪽부터 놓는다. 요일이나 월처럼 순서가 있는 항목은 대개 시간 축이고,
 * 시계열 그래프는 이런 언어에서도 왼쪽에서 오른쪽으로 읽기 때문이다. 가로 막대는 순서 축이 아니므로 시작 쪽(RTL이면 오른쪽)에서 자란다.
 *
 * TalkBack은 전체 요약(막대 수, 최저값, 최고값)을 읽고, 막대마다 이름과 값을 따로 탐색할 수 있다. 막대에서 두 번 탭하면 그 막대를 고른다.
 * 크기를 정하지 않으면 최소 240×160dp이다.
 *
 * ```kotlin
 * KBarChart(listOf(4f, 7f, -2f, 5f), Modifier.fillMaxWidth().height(180.dp), labels = listOf("월", "화", "수", "목"))
 * ```
 *
 * @param values 막대 값. NaN이나 무한대가 있으면 [IllegalArgumentException]을 던진다.
 * @param labels 막대마다 붙일 이름. 모자라면 그 막대는 번호로 읽는다.
 * @param colors 막대 색. 막대 수보다 적으면 처음부터 돌려 쓴다. 비어 있으면 [IllegalArgumentException]을 던진다.
 * @param valueFormatter 말풍선과 TalkBack에 쓸 값 문자열
 * @param onSelect 고른 막대가 바뀔 때 부른다. 선택이 풀리면 `null`을 넘긴다.
 */
@Composable
public fun KBarChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    labels: List<String>? = null,
    colors: List<Color> = listOf(KineticaTheme.colors.accent),
    orientation: KBarOrientation = KBarOrientation.Vertical,
    valueFormatter: (Float) -> String = KChartDefaults::formatValue,
    onSelect: ((Int?) -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(values.all { it.isFinite() }) { "values에 NaN이나 무한대를 넣을 수 없습니다." }
    require(colors.isNotEmpty()) { "colors는 하나 이상이어야 합니다." }
    val horizontal = orientation == KBarOrientation.Horizontal
    val arrays = remember(values) { listOf(values.toFloatArray()) }
    val still = reducedMotion()
    val timing = remember(values.size) { BarTiming(values.size) }
    val reveal = rememberChartReveal(timing.totalMs)
    val morph = remember { ChartMorph(arrays, ::barRange) }
    LaunchedEffect(arrays) { morph.retarget(arrays, !still) }
    val plot = remember { BarPlot() }
    with(LocalDensity.current) { plot.maxThickness = BAR_MAX_THICKNESS.toPx() }
    plot.horizontal = horizontal
    plot.mirrored = horizontal && LocalLayoutDirection.current == LayoutDirection.Rtl
    plot.count = values.size
    var picked by remember { mutableIntStateOf(-1) }
    val selected = picked.takeIf { it in values.indices } ?: -1
    val latestSelect = rememberUpdatedState(onSelect)
    val toggle: (Int) -> Unit = { index ->
        picked = if (picked == index) -1 else index
        latestSelect.value?.invoke(picked.takeIf { it >= 0 })
    }
    val dim = animateFloatAsState(if (selected >= 0) 1f else 0f, spring(), label = "bar-dim")
    val summary = barSummary(values, valueFormatter)
    val content = BarContent(values, labels, colors, valueFormatter, surface)
    val outer = LocalLayoutDirection.current
    val direction = if (horizontal) outer else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction, LocalChartTextDirection provides outer) {
        val frame = BarFrame(content, morph, plot, timing, BarMotion(reveal::value, dim, selected))
        val chart = modifier.defaultMinSize(BAR_MIN_WIDTH, BAR_MIN_HEIGHT).chartSummary(summary)
        if (horizontal) {
            Row(chart) {
                BarLabels(content, horizontal = true)
                BarPlotArea(frame, Modifier.weight(1f).fillMaxHeight(), toggle)
            }
        } else {
            Column(chart) {
                BarPlotArea(frame, Modifier.weight(1f).fillMaxWidth(), toggle)
                if (!labels.isNullOrEmpty()) BarLabels(content, horizontal = false)
            }
        }
    }
}

private class BarContent(
    val values: List<Float>,
    val labels: List<String>?,
    val colors: List<Color>,
    val valueFormatter: (Float) -> String,
    val surface: SurfaceStyle,
) {
    fun color(index: Int): Color = colors[index % colors.size]
}

private class BarMotion(val reveal: () -> Float, val dim: State<Float>, val selected: Int)

private class BarFrame(val content: BarContent, val morph: ChartMorph, val plot: BarPlot, val timing: BarTiming, val motion: BarMotion)

/** 막대가 차례로 자라는 시간. 막대가 많아도 전체가 [MAX_STAGGER_TOTAL_MS]를 넘게 늦어지지 않는다. */
private class BarTiming(count: Int) {
    private val stagger = if (count <= 1) 0 else minOf(STAGGER_MS, MAX_STAGGER_TOTAL_MS / (count - 1))
    val totalMs = GROW_MS + stagger * (count - 1).coerceAtLeast(0)

    fun grow(index: Int, reveal: Float): Float {
        val start = (index * stagger).toFloat() / totalMs
        val end = start + GROW_MS.toFloat() / totalMs
        return GrowEasing.transform(((reveal - start) / (end - start)).coerceIn(0f, 1f))
    }
}

/** 막대 위치 계산. 그리기와 누른 위치 계산, 말풍선 위치가 함께 쓴다. */
private class BarPlot {
    var size by mutableStateOf(IntSize.Zero)
    var horizontal = false
    var mirrored = false
    var count = 0
    var maxThickness = 0f

    private val categoryLength get() = (if (horizontal) size.height else size.width).toFloat()
    private val valueLength get() = (if (horizontal) size.width else size.height).toFloat()

    fun slot(): Float = if (count == 0) 0f else categoryLength / count

    fun thickness(): Float = minOf(slot() * BAR_FILL, maxThickness)

    fun center(index: Int): Float = slot() * (index + CHART_HALF)

    /** 값 [fraction](0..1)의 값 축 위치. 세로면 위가 큰 값이다. */
    fun position(fraction: Float): Float = if (horizontal) valueLength * fraction else valueLength * (1f - fraction)

    fun indexAt(offset: Offset): Int {
        val along = if (horizontal) offset.y else offset.x
        return if (slot() <= 0f) -1 else (along / slot()).toInt().coerceIn(0, count - 1)
    }
}

@Composable
private fun BarPlotArea(frame: BarFrame, modifier: Modifier, toggle: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val latestToggle = rememberUpdatedState(toggle)
    val plot = frame.plot
    val held = remember { BarScratch() }
    Box(
        modifier
            .onSizeChanged { plot.size = it }
            .pointerInput(plot) {
                detectTapGestures { offset ->
                    val index = plot.indexAt(offset)
                    if (index >= 0) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        latestToggle.value(index)
                    }
                }
            }.drawWithCache {
                val ink = frame.content.surface.contentColor
                onDrawBehind {
                    if (plot.mirrored) scale(-1f, 1f) { drawBars(frame, ink, held) } else drawBars(frame, ink, held)
                }
            },
    ) {
        BarTargets(frame.content, plot.horizontal, frame.motion.selected, toggle)
        val selected = frame.motion.selected
        ChartBubble(selected >= 0, { barAnchor(frame, selected) }, frame.content.surface) {
            BarBubble(frame.content, selected)
        }
    }
}

private fun barAnchor(frame: BarFrame, index: Int): ChartBubbleAnchor? {
    val plot = frame.plot
    if (index !in frame.content.values.indices || plot.size == IntSize.Zero) return null
    val value = frame.content.values[index]
    val base = plot.position(frame.morph.fraction(0f))
    val end = plot.position(frame.morph.fraction(value))
    if (!plot.horizontal) {
        return ChartBubbleAnchor(plot.center(index), minOf(base, end), maxOf(base, end), preferBelow = value < 0f)
    }
    // 가로 막대는 RTL에서 그림을 뒤집어 그리므로 말풍선 위치도 같이 뒤집는다.
    val x = if (plot.mirrored) plot.size.width - end else end
    return ChartBubbleAnchor(
        x,
        plot.center(index),
        plot.center(index),
        preferBelow = true,
        sideways = true,
        leftFirst =
            plot.mirrored != (value < 0f),
    )
}

private fun DrawScope.drawBars(frame: BarFrame, ink: Color, held: BarScratch) {
    val plot = frame.plot
    val count = frame.content.values.size
    if (count == 0) return
    val base = plot.position(frame.morph.fraction(0f))
    val baseStroke = 1.dp.toPx()
    if (plot.horizontal) {
        drawLine(ink.copy(alpha = BASELINE_ALPHA), Offset(base, 0f), Offset(base, size.height), baseStroke)
    } else {
        drawLine(ink.copy(alpha = BASELINE_ALPHA), Offset(0f, base), Offset(size.width, base), baseStroke)
    }
    val motion = frame.motion
    if (motion.selected >= 0) held.value = motion.selected
    val dim = motion.dim.value
    val reveal = motion.reveal()
    val thickness = plot.thickness()
    val corner = BAR_CORNER.toPx()
    for (i in 0 until count) {
        val value = if (i < (frame.morph.target.firstOrNull()?.size ?: 0)) frame.morph.value(0, i) else frame.content.values[i]
        val end = lerp(base, plot.position(frame.morph.fraction(value)), frame.timing.grow(i, reveal))
        val alpha = if (i == held.value) 1f else lerp(1f, CHART_DIM_ALPHA, dim)
        drawBar(BarShape(plot.center(i), base, end, thickness, corner), frame.content.color(i), alpha, plot.horizontal, held.path)
    }
}

private class BarShape(val center: Float, val base: Float, val end: Float, val thickness: Float, val corner: Float)

/** 마지막으로 고른 막대와 그릴 때 다시 쓰는 [Path] */
private class BarScratch {
    var value: Int = -1
    val path = Path()
}

/** 끝쪽 모서리만 둥근 막대. 기준선 쪽은 각지게 둔다. */
private fun DrawScope.drawBar(shape: BarShape, color: Color, alpha: Float, horizontal: Boolean, path: Path) {
    val length = abs(shape.end - shape.base)
    if (length < MIN_BAR_PX) return
    val radius = CornerRadius(minOf(shape.corner, shape.thickness / 2, length))
    val low = minOf(shape.base, shape.end)
    val high = maxOf(shape.base, shape.end)
    val side = shape.center - shape.thickness / 2
    val far = side + shape.thickness
    // 끝이 기준선보다 작은 좌표 쪽에 있으면 그쪽(세로는 위, 가로는 왼쪽) 모서리를 둥글게 한다.
    val roundLow = shape.end < shape.base
    val rect = if (horizontal) {
        RoundRect(
            low,
            side,
            high,
            far,
            topLeftCornerRadius = if (roundLow) radius else CornerRadius.Zero,
            bottomLeftCornerRadius = if (roundLow) radius else CornerRadius.Zero,
            topRightCornerRadius = if (roundLow) CornerRadius.Zero else radius,
            bottomRightCornerRadius = if (roundLow) CornerRadius.Zero else radius,
        )
    } else {
        RoundRect(
            side,
            low,
            far,
            high,
            topLeftCornerRadius = if (roundLow) radius else CornerRadius.Zero,
            topRightCornerRadius = if (roundLow) radius else CornerRadius.Zero,
            bottomLeftCornerRadius = if (roundLow) CornerRadius.Zero else radius,
            bottomRightCornerRadius = if (roundLow) CornerRadius.Zero else radius,
        )
    }
    path.reset()
    path.addRoundRect(rect)
    drawPath(path, color, alpha = alpha)
}

/** 막대마다 TalkBack이 탐색할 투명한 칸 */
@Composable
private fun BoxScope.BarTargets(content: BarContent, horizontal: Boolean, selected: Int, toggle: (Int) -> Unit) {
    val descriptions = content.values.indices.map { barDescription(content, it) }
    val cell: @Composable (Modifier, Int) -> Unit = { cellModifier, index ->
        Box(
            cellModifier.semantics(mergeDescendants = true) {
                contentDescription = descriptions[index]
                this.selected = index == selected
                onClick {
                    toggle(index)
                    true
                }
            },
        )
    }
    if (horizontal) {
        Column(Modifier.matchParentSize()) { content.values.indices.forEach { cell(Modifier.weight(1f).fillMaxWidth(), it) } }
    } else {
        Row(Modifier.matchParentSize()) { content.values.indices.forEach { cell(Modifier.weight(1f).fillMaxHeight(), it) } }
    }
}

@Composable
private fun BarBubble(content: BarContent, index: Int) {
    val shown = remember { ChartIndexHolder() }
    if (index >= 0) shown.value = index
    val at = shown.value
    if (at !in content.values.indices) return
    ChartBubbleColumn {
        content.labels?.getOrNull(at)?.let { ChartBubbleTitle(it) }
        ChartBubbleRow(content.color(at), null, content.valueFormatter(content.values[at]))
    }
}

/** 막대 이름. 막대가 많으면 몇 개씩 건너뛰어 [MAX_BAR_LABELS]개 안팎만 보인다. */
@Composable
private fun BarLabels(content: BarContent, horizontal: Boolean) {
    val labels = content.labels ?: return
    val count = content.values.size
    val every = ceil(count / MAX_BAR_LABELS.toFloat()).toInt().coerceAtLeast(1)
    val textStyle = chartAxisText(content.surface.contentColor)
    if (horizontal) {
        Column(Modifier.fillMaxHeight().widthIn(max = H_LABEL_MAX).padding(end = LABEL_GAP)) {
            repeat(count) { i ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    ThemedText(labels.getOrNull(i).orEmpty(), style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = LABEL_GAP)) {
            repeat(count) { i ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val text = if (i % every == 0) labels.getOrNull(i).orEmpty() else ""
                    ThemedText(text, style = textStyle.copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun barDescription(content: BarContent, index: Int): String {
    val name = content.labels?.getOrNull(index) ?: stringResource(R.string.kinetica_ui_chart_point, index + 1)
    return stringResource(R.string.kinetica_ui_chart_entry, name, content.valueFormatter(content.values[index]))
}

@Composable
private fun barSummary(values: List<Float>, valueFormatter: (Float) -> String): String {
    val head = pluralStringResource(R.plurals.kinetica_ui_chart_bar, values.size, values.size)
    if (values.isEmpty()) return head
    return head + ". " + stringResource(R.string.kinetica_ui_chart_range, valueFormatter(values.min()), valueFormatter(values.max()))
}

private fun barRange(arrays: List<FloatArray>): ChartRange = chartRange(arrays, includeZero = true, padding = BAR_RANGE_PADDING)

// 끝에서 살짝 넘쳤다가 돌아오는 곡선. 막대가 툭 솟는 느낌을 준다.
private val GrowEasing = CubicBezierEasing(0.34f, 1.36f, 0.64f, 1f)

private const val GROW_MS = 700
private const val STAGGER_MS = 70
private const val MAX_STAGGER_TOTAL_MS = 600
private const val BAR_FILL = 0.62f
private const val BAR_RANGE_PADDING = 0.08f
private const val BASELINE_ALPHA = 0.22f
private const val MAX_BAR_LABELS = 8
private const val MIN_BAR_PX = 0.5f
private val BAR_MAX_THICKNESS = 40.dp
private val BAR_CORNER = 8.dp
private val LABEL_GAP = 6.dp
private val H_LABEL_MAX = 120.dp
private val BAR_MIN_WIDTH = 240.dp
private val BAR_MIN_HEIGHT = 160.dp
