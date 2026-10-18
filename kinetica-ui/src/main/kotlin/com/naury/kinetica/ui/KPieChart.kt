package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * 파이 차트. [KDonutChart]와 같은 [ChartSlice]를 받아 가운데까지 채운 조각으로 그린다. 가운데에 합계나 고른 값을 크게 보여야 하면
 * [KDonutChart]를, 비율 자체가 중요하고 조각이 다섯 개 안팎이면 이 차트를 쓴다. 조각이 많으면 막대 그래프가 더 읽기 쉽다.
 *
 * 조각 사이에는 2dp 틈이 있고, 큰 조각 안에는 비율을 적는다. 처음 보일 때 12시 방향에서 시계 방향으로 펼쳐지고,
 * 조각을 누르면 그 조각이 바깥으로 튀어나오고 나머지는 옅어진다. 시계 방향은 RTL에서도 바꾸지 않는다([KDonutChart]와 같은 정책).
 * '애니메이션 삭제' 설정에서는 바로 최종 모양을 그린다. [legend]면 아래에 범례를 둔다.
 *
 * TalkBack은 전체 요약을 읽고, 조각마다 표의 한 행처럼 '식비: 42, 30%'를 읽는다. 크기를 정하지 않으면 지름 180dp이고, 넓은 자리에서도 320dp를 넘지 않는다.
 *
 * ```kotlin
 * KPieChart(listOf(ChartSlice(42f, "식비", colors[0]), ChartSlice(18f, "교통", colors[1])), Modifier.width(220.dp))
 * ```
 *
 * @param slices 조각. 모두 0이면 빈 원만 그린다.
 * @param showPercent 조각 안에 비율을 적을지. 전체의 8% 미만 조각에는 적지 않는다.
 * @param legend 아래에 이름 범례를 둘지
 * @param onSelect 고른 조각이 바뀔 때 부른다. 선택이 풀리면 `null`
 */
@Composable
public fun KPieChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    showPercent: Boolean = true,
    legend: Boolean = true,
    valueFormatter: (Float) -> String = KChartDefaults::formatValue,
    onSelect: ((Int?) -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val arcs = remember(slices) { PieArcs(slices.map { it.value }) }
    val still = reducedMotion()
    val reveal = rememberChartReveal(PIE_REVEAL_MS)
    var picked by remember { mutableIntStateOf(-1) }
    val selected = picked.takeIf { it in slices.indices && slices[it].value > 0f } ?: -1
    val latestSelect = rememberUpdatedState(onSelect)
    val choose: (Int) -> Unit = { index ->
        picked = if (index == picked) -1 else index
        latestSelect.value?.invoke(picked.takeIf { it >= 0 })
    }
    val pops = rememberPiePops(slices.size, selected, still)
    val dim by animateFloatAsState(if (selected >= 0) 1f else 0f, spring(), label = "pie-dim")
    val plot = remember { PiePlot() }
    with(LocalDensity.current) {
        plot.pop = POP_DISTANCE.toPx()
        plot.gap = SLICE_GAP.toPx()
    }
    val summary = pluralStringResource(R.plurals.kinetica_ui_chart_pie, slices.size, slices.size, valueFormatter(arcs.total))
    val haptic = LocalHapticFeedback.current
    val outer = LocalLayoutDirection.current
    Column(modifier.chartSummary(summary), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .widthIn(max = PIE_MAX)
                .defaultMinSize(PIE_SIZE, PIE_SIZE)
                .fillMaxWidth()
                .aspectRatio(1f)
                .onSizeChanged { plot.size = it }
                .pointerInput(plot) {
                    detectTapGestures { offset ->
                        val index = plot.indexAt(offset, arcs)
                        if (index >= 0) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        choose(index)
                    }
                }.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    onDrawBehind {
                        if (selected >= 0) plot.held = selected
                        drawPie(slices, arcs, plot, PieMotion(pops, chartStage(reveal.value, 0f, 1f), dim), surface.contentColor)
                    }
                },
        ) {
            PieLabels(slices, arcs, plot, pops, PieLabelSpec(showPercent, selected, valueFormatter, choose))
        }
        if (legend && slices.isNotEmpty()) {
            CompositionLocalProvider(LocalChartTextDirection provides outer) {
                ChartLegend(slices.map { it.label }, slices.map { it.color }, surface.contentColor, Modifier.padding(top = LEGEND_GAP))
            }
        }
    }
}

@Composable
private fun rememberPiePops(count: Int, selected: Int, still: Boolean): List<Animatable<Float, AnimationVector1D>> {
    val pops = remember(count) { List(count) { Animatable(0f) } }
    LaunchedEffect(pops, selected) {
        pops.forEachIndexed { i, pop ->
            val target = if (i == selected) 1f else 0f
            if (still) pop.snapTo(target) else launch { pop.animateTo(target, spring(POP_DAMPING, Spring.StiffnessMediumLow)) }
        }
    }
    return pops
}

private class PieMotion(val pops: List<Animatable<Float, AnimationVector1D>>, val stage: Float, val dim: Float)

private fun DrawScope.drawPie(slices: List<ChartSlice>, arcs: PieArcs, plot: PiePlot, motion: PieMotion, ink: Color) {
    val limit = CHART_FULL_TURN * motion.stage
    if (arcs.total <= 0f) drawCircle(ink.copy(alpha = CHART_GRID_ALPHA), plot.radius, plot.center)
    val box = Size(plot.radius * 2, plot.radius * 2)
    slices.forEachIndexed { i, slice ->
        val drawn = (limit - arcs.starts[i]).coerceIn(0f, arcs.sweeps[i])
        if (drawn <= 0f) return@forEachIndexed
        val shift = plot.direction(arcs.starts[i] + arcs.sweeps[i] / 2) * (plot.pop * motion.pops[i].value)
        val alpha = if (i == plot.held) 1f else lerp(1f, CHART_DIM_ALPHA, motion.dim)
        drawArc(
            slice.color,
            arcs.starts[i] - CHART_QUARTER_TURN,
            drawn,
            true,
            plot.center - Offset(plot.radius, plot.radius) + shift,
            box,
            alpha,
        )
    }
    if (arcs.visible <= 1) return
    // 조각 경계마다 가운데에서 바깥으로 선을 지워 틈을 낸다.
    for (i in slices.indices) {
        if (arcs.sweeps[i] <= 0f || arcs.starts[i] > limit) continue
        val shift = plot.direction(arcs.starts[i] + arcs.sweeps[i] / 2) * (plot.pop * motion.pops[i].value)
        val edge = plot.direction(arcs.starts[i]) * (plot.radius + plot.pop)
        drawLine(Color.Black, plot.center + shift, plot.center + shift + edge, plot.gap, blendMode = BlendMode.Clear)
    }
}

/** 조각마다 시작 각도와 크기(도). 12시 방향이 0이고 시계 방향으로 커진다. */
private class PieArcs(values: List<Float>) {
    val total = values.sum()
    val sweeps = FloatArray(values.size) { if (total > 0f) values[it] / total * CHART_FULL_TURN else 0f }
    val starts = FloatArray(values.size).also { starts ->
        var acc = 0f
        sweeps.forEachIndexed { i, sweep ->
            starts[i] = acc
            acc += sweep
        }
    }
    val visible = sweeps.count { it > 0f }

    fun percent(index: Int): Int = (sweeps[index] / CHART_FULL_TURN * PERCENT).roundToInt()
}

private class PiePlot {
    var size by mutableStateOf(IntSize.Zero)
    var pop = 0f
    var gap = 0f
    var held = -1

    val center: Offset get() = Offset(size.width / 2f, size.height / 2f)

    val radius: Float get() = (minOf(size.width, size.height) / 2f - pop).coerceAtLeast(1f)

    /** 12시 기준 시계 방향 [angle](도) 쪽 단위 벡터 */
    fun direction(angle: Float): Offset {
        val radians = (angle - CHART_QUARTER_TURN) / DEGREES * PI.toFloat()
        return Offset(cos(radians), sin(radians))
    }

    fun indexAt(offset: Offset, arcs: PieArcs): Int {
        val delta = offset - center
        if (hypot(delta.x, delta.y) > radius + pop) return -1
        val angle = ((atan2(delta.y, delta.x) / PI.toFloat() * DEGREES + CHART_QUARTER_TURN) + CHART_FULL_TURN) % CHART_FULL_TURN
        return arcs.sweeps.indices.firstOrNull { angle >= arcs.starts[it] && angle < arcs.starts[it] + arcs.sweeps[it] } ?: -1
    }
}

private class PieLabelSpec(val showPercent: Boolean, val selected: Int, val valueFormatter: (Float) -> String, val choose: (Int) -> Unit)

/** 조각 안 비율 글자이자 TalkBack이 표의 행처럼 읽는 칸. 조각 가운데(반지름 62%)에 놓는다. */
@Composable
private fun PieLabels(
    slices: List<ChartSlice>,
    arcs: PieArcs,
    plot: PiePlot,
    pops: List<Animatable<Float, AnimationVector1D>>,
    spec: PieLabelSpec,
) {
    Layout(
        content = {
            slices.forEachIndexed { i, slice ->
                val percent = arcs.percent(i)
                val description =
                    stringResource(R.string.kinetica_ui_chart_slice, slice.label, spec.valueFormatter(slice.value), percent)
                Box(
                    Modifier.size(LABEL_BOX).chartCell(i, 0).semantics(mergeDescendants = true) {
                        contentDescription = description
                        selected = i == spec.selected
                        traversalIndex = i.toFloat()
                        onClick {
                            spec.choose(i)
                            true
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    if (spec.showPercent && percent >= MIN_LABEL_PERCENT) {
                        val ink = if (slice.color.luminance() > LIGHT_SLICE) Color.Black.copy(alpha = DARK_INK) else Color.White
                        // 조각 이름에 비율이 이미 있어 두 번 읽지 않는다.
                        ThemedText(
                            "$percent%",
                            Modifier.clearAndSetSemantics {},
                            style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        },
        modifier = Modifier.chartTable(slices.size, 1),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, label ->
                if (arcs.sweeps[i] <= 0f) return@forEachIndexed
                val direction = plot.direction(arcs.starts[i] + arcs.sweeps[i] / 2)
                val point = plot.center + direction * (plot.radius * LABEL_RADIUS + plot.pop * pops[i].value)
                label.place((point.x - label.width / 2f).roundToInt(), (point.y - label.height / 2f).roundToInt())
            }
        }
    }
}

/** 조각 안에 비율을 적는 최소 비율(%) */
private const val MIN_LABEL_PERCENT = 8
private const val PIE_REVEAL_MS = 900
private const val DEGREES = 180f
private const val PERCENT = 100f
private const val POP_DAMPING = 0.55f
private const val LABEL_RADIUS = 0.62f
private const val LIGHT_SLICE = 0.5f
private const val DARK_INK = 0.85f
private val POP_DISTANCE = 8.dp
private val SLICE_GAP = 2.dp
private val LABEL_BOX = 40.dp
private val LEGEND_GAP = 12.dp
private val PIE_SIZE = 180.dp
private val PIE_MAX = 320.dp
