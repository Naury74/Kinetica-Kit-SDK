package com.naury.kinetica.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
 * 글래스 도넛 차트. 처음 보일 때 조각이 12시 방향에서 시계 방향으로 펼쳐지고, 조각 사이에는 끝이 둥근 틈이 있다.
 * 조각을 누르면 그 조각이 바깥으로 살짝 튀어나오고 나머지는 옅어지며, [center] 슬롯에 고른 번호가 전달된다.
 * 같은 조각이나 가운데, 바깥을 누르면 선택이 풀린다. '애니메이션 삭제' 접근성 설정이 켜져 있으면 처음부터 최종 모양을 그린다.
 *
 * 시계 방향은 오른쪽에서 왼쪽으로 쓰는 언어에서도 바꾸지 않는다. 원 그래프의 방향은 읽는 방향이 아니라 시계를 따르기 때문이다.
 *
 * TalkBack은 전체 요약(항목 수와 합계)을 읽고, 조각마다 이름과 값, 비율을 차례로 탐색할 수 있다. 조각에서 두 번 탭하면 그 조각을 고른다.
 * 크기를 정하지 않으면 지름 180dp인 정사각형이다.
 *
 * ```kotlin
 * KDonutChart(listOf(ChartSlice(42f, "식비", KChartDefaults.Palette[0]), ChartSlice(18f, "교통", KChartDefaults.Palette[1])))
 * ```
 *
 * @param slices 그릴 조각. 모든 값이 0이면 빈 트랙만 그린다.
 * @param thickness 링 두께. 0 이하이면 [IllegalArgumentException]을 던진다.
 * @param valueFormatter 가운데 기본 내용과 TalkBack에 쓸 값 문자열
 * @param onSelect 고른 조각이 바뀔 때 부른다. 선택이 풀리면 `null`을 넘긴다.
 * @param center 링 안쪽에 둘 내용. 고른 조각 번호를 받고, 고른 것이 없으면 `null`을 받는다. 기본값은 고른 조각의 이름과 값,
 *   고른 것이 없으면 합계를 보인다.
 */
@Composable
public fun KDonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    thickness: Dp = 26.dp,
    valueFormatter: (Float) -> String = KChartDefaults::formatValue,
    onSelect: ((Int?) -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
    center: @Composable BoxScope.(selected: Int?) -> Unit = { DonutCenter(slices, it, valueFormatter) },
) {
    require(thickness > 0.dp) { "thickness는 0보다 커야 합니다." }
    val arcs = remember(slices) { DonutArcs(slices.map { it.value }) }
    val still = reducedMotion()
    val reveal = rememberChartReveal(DONUT_REVEAL_MS)
    var picked by remember { mutableIntStateOf(-1) }
    val selected = picked.takeIf { it in slices.indices && slices[it].value > 0f } ?: -1
    val latestSelect = rememberUpdatedState(onSelect)
    val choose: (Int) -> Unit = { index ->
        picked = if (index == picked) -1 else index
        latestSelect.value?.invoke(picked.takeIf { it >= 0 })
    }
    val pops = rememberPops(slices.size, selected, still)
    val dim = animateFloatAsState(if (selected >= 0) 1f else 0f, spring(), label = "donut-dim")
    val plot = remember { DonutPlot() }
    with(LocalDensity.current) {
        plot.thickness = thickness.toPx()
        plot.pop = POP_DISTANCE.toPx()
        plot.gap = SLICE_GAP.toPx()
    }
    val summary = donutSummary(slices, arcs.total, valueFormatter)
    val held = remember { ChartIndexHolder() }
    Box(
        modifier
            .defaultMinSize(DONUT_SIZE, DONUT_SIZE)
            .aspectRatio(1f)
            .chartSummary(summary)
            .onSizeChanged { plot.size = it }
            .donutTaps(plot, arcs, choose)
            .drawWithCache {
                val stroke = Stroke(plot.thickness, cap = StrokeCap.Round)
                onDrawBehind {
                    if (selected >= 0) held.value = selected
                    val look = DonutLook(surface.contentColor, stroke, held.value, dim.value, reveal.value)
                    drawDonut(slices, arcs, plot, pops, look)
                }
            },
    ) {
        SliceTargets(slices, arcs, plot, selected, valueFormatter, choose)
        Box(
            Modifier.matchParentSize().padding(thickness + POP_DISTANCE + CENTER_PAD),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor) { center(selected.takeIf { it >= 0 }) }
        }
    }
}

/** 조각마다 시작 각도와 크기(도). 12시 방향이 0이고 시계 방향으로 커진다. */
private class DonutArcs(values: List<Float>) {
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

    /** 12시 기준 시계 방향 각도 [angle]이 들어가는 조각. 없으면 -1이다. */
    fun indexAt(angle: Float): Int = sweeps.indices.firstOrNull { angle >= starts[it] && angle < starts[it] + sweeps[it] } ?: -1

    fun percent(index: Int): Int = (sweeps[index] / CHART_FULL_TURN * PERCENT).roundToInt()
}

private class DonutPlot {
    var size by mutableStateOf(IntSize.Zero)
    var thickness = 0f
    var pop = 0f
    var gap = 0f

    val center: Offset get() = Offset(size.width / 2f, size.height / 2f)

    val radius: Float get() = (minOf(size.width, size.height) / 2f - thickness / 2 - pop).coerceAtLeast(1f)

    /** 12시 기준 시계 방향 [angle](도) 위치의 링 가운데 점 */
    fun pointAt(angle: Float): Offset {
        val radians = (angle - CHART_QUARTER_TURN) / DEGREES * PI.toFloat()
        return center + Offset(cos(radians), sin(radians)) * radius
    }
}

private class DonutLook(val ink: Color, val stroke: Stroke, val held: Int, val dim: Float, val reveal: Float)

@Composable
private fun rememberPops(count: Int, selected: Int, still: Boolean): List<Animatable<Float, *>> {
    val pops = remember(count) { List(count) { Animatable(0f) } }
    LaunchedEffect(pops, selected) {
        pops.forEachIndexed { i, pop ->
            val target = if (i == selected) 1f else 0f
            launch {
                if (still) {
                    pop.snapTo(target)
                } else {
                    pop.animateTo(target, spring(dampingRatio = POP_DAMPING, stiffness = Spring.StiffnessMediumLow))
                }
            }
        }
    }
    return pops
}

@Composable
private fun Modifier.donutTaps(plot: DonutPlot, arcs: DonutArcs, choose: (Int) -> Unit): Modifier {
    val haptic = kHapticFeedback()
    val latestArcs = rememberUpdatedState(arcs)
    val latestChoose = rememberUpdatedState(choose)
    return pointerInput(plot) {
        detectTapGestures { offset ->
            val delta = offset - plot.center
            val distance = hypot(delta.x, delta.y)
            val onRing = distance in (plot.radius - plot.thickness)..(plot.radius + plot.thickness)
            val angle = ((atan2(delta.y, delta.x) / PI.toFloat() * DEGREES + CHART_QUARTER_TURN) + CHART_FULL_TURN) % CHART_FULL_TURN
            val index = if (onRing) latestArcs.value.indexAt(angle) else -1
            if (index >= 0) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            // 링 밖을 누르면 고른 조각과 다른 번호(-1)를 넘겨 선택을 푼다.
            latestChoose.value(index)
        }
    }
}

private fun DrawScope.drawDonut(
    slices: List<ChartSlice>,
    arcs: DonutArcs,
    plot: DonutPlot,
    pops: List<Animatable<Float, *>>,
    look: DonutLook,
) {
    val radius = plot.radius
    val topLeft = plot.center - Offset(radius, radius)
    val box = Size(radius * 2, radius * 2)
    drawCircle(look.ink.copy(alpha = CHART_GRID_ALPHA), radius, plot.center, style = look.stroke)
    val stage = chartStage(look.reveal, 0f, 1f)
    val limit = CHART_FULL_TURN * stage
    // 펼쳐지는 동안 시작점이 조금 뒤에서 12시로 돌아와 감기는 느낌을 준다.
    val turn = -CHART_QUARTER_TURN - (1f - stage) * REVEAL_TURN
    val gapAngle = if (arcs.visible > 1) (plot.thickness + plot.gap) / radius * DEGREES / PI.toFloat() else 0f
    slices.forEachIndexed { i, slice ->
        val sweep = arcs.sweeps[i]
        val drawn = (limit - arcs.starts[i]).coerceIn(0f, sweep)
        if (drawn <= 0f) return@forEachIndexed
        val pop = pops.getOrNull(i)?.value ?: 0f
        val middle = (arcs.starts[i] + sweep / 2 - CHART_QUARTER_TURN) / DEGREES * PI.toFloat()
        val shift = Offset(cos(middle), sin(middle)) * (plot.pop * pop)
        val alpha = if (i == look.held) 1f else lerp(1f, CHART_DIM_ALPHA, look.dim)
        val visibleSweep = drawn - gapAngle
        translate(shift.x, shift.y) {
            if (visibleSweep > 0f) {
                drawArc(slice.color, turn + arcs.starts[i] + gapAngle / 2, visibleSweep, false, topLeft, box, alpha, look.stroke)
            } else {
                // 틈보다 작은 조각은 이웃과 겹치지 않게 남은 길이만 한 점으로 그린다.
                val room = (drawn / DEGREES * PI.toFloat() * radius - plot.gap) / 2
                val dot = room.coerceIn(plot.gap, plot.thickness / 2)
                val angle = (turn + arcs.starts[i] + drawn / 2) / DEGREES * PI.toFloat()
                drawCircle(slice.color, dot, plot.center + Offset(cos(angle), sin(angle)) * radius, alpha)
            }
        }
    }
}

/** 조각마다 TalkBack이 탐색할 투명한 점. 링 위 조각 가운데에 놓고 차례대로 읽힌다. */
@Composable
private fun BoxScope.SliceTargets(
    slices: List<ChartSlice>,
    arcs: DonutArcs,
    plot: DonutPlot,
    selected: Int,
    valueFormatter: (Float) -> String,
    choose: (Int) -> Unit,
) {
    val thickness = with(LocalDensity.current) { plot.thickness.toDp() }
    Layout(
        content = {
            slices.forEachIndexed { i, slice ->
                val description =
                    stringResource(R.string.kinetica_ui_chart_slice, slice.label, valueFormatter(slice.value), arcs.percent(i))
                Box(
                    Modifier.size(thickness).semantics(mergeDescendants = true) {
                        contentDescription = description
                        this.selected = i == selected
                        traversalIndex = i.toFloat()
                        onClick {
                            choose(i)
                            true
                        }
                    },
                )
            }
        },
        modifier = Modifier.matchParentSize().semantics { isTraversalGroup = true },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, target ->
                if (arcs.sweeps[i] <= 0f) return@forEachIndexed
                val point = plot.pointAt(arcs.starts[i] + arcs.sweeps[i] / 2)
                target.place((point.x - target.width / 2f).roundToInt(), (point.y - target.height / 2f).roundToInt())
            }
        }
    }
}

/** 기본 가운데 내용. 고른 조각이 있으면 이름, 값, 비율을, 없으면 합계를 보인다. */
@Composable
private fun DonutCenter(slices: List<ChartSlice>, selected: Int?, valueFormatter: (Float) -> String) {
    val ink = LocalKineticaContentColor.current
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    AnimatedContent(
        targetState = selected?.takeIf { it in slices.indices },
        transitionSpec = {
            (fadeIn(tween(CENTER_FADE_MS)) + scaleIn(initialScale = CENTER_SCALE)) togetherWith
                fadeOut(tween(CENTER_FADE_MS))
        },
        // 합계는 차트 요약이, 고른 조각은 조각 칸이 읽으므로 기본 가운데 글자는 차트 요약에 다시 합치지 않는다.
        modifier = Modifier.clearAndSetSemantics {},
        label = "donut-center",
    ) { index ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val title = index?.let { slices[it].label } ?: stringResource(R.string.kinetica_ui_chart_total)
            val value = valueFormatter(index?.let { slices[it].value } ?: total)
            val small = TextStyle(color = ink.copy(alpha = CENTER_MUTED), fontSize = 12.sp, textAlign = TextAlign.Center)
            ThemedText(title, style = small, maxLines = 1)
            // 가운데 구멍 폭이 정해져 있어 큰 글자 설정에서도 한 줄에 들어가도록 글자를 줄인다. 줄 바꿈되면 고리와 겹친다.
            BasicText(
                value,
                style = themed(TextStyle(color = ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = CENTER_MIN_FONT, maxFontSize = 22.sp),
            )
            if (index != null && total > 0f) ThemedText("${(slices[index].value / total * PERCENT).roundToInt()}%", style = small)
        }
    }
}

@Composable
private fun donutSummary(slices: List<ChartSlice>, total: Float, valueFormatter: (Float) -> String): String =
    pluralStringResource(R.plurals.kinetica_ui_chart_donut, slices.size, slices.size, valueFormatter(total))

private val CENTER_MIN_FONT = 10.sp
private const val DONUT_REVEAL_MS = 1_000
private const val REVEAL_TURN = 60f
private const val DEGREES = 180f
private const val PERCENT = 100f
private const val POP_DAMPING = 0.55f
private const val CENTER_FADE_MS = 180
private const val CENTER_SCALE = 0.92f
private const val CENTER_MUTED = 0.66f
private val POP_DISTANCE = 8.dp
private val SLICE_GAP = 3.dp
private val CENTER_PAD = 6.dp
private val DONUT_SIZE = 180.dp
