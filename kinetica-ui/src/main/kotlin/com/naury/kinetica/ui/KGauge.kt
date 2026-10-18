package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** [KGauge]의 모양 */
public enum class KGaugeShape(internal val sweep: Float, internal val aspect: Float) {
    /** 아래가 열린 반원. 속도계나 점수처럼 가로로 넓은 자리에 쓴다. */
    Semicircle(SEMI_SWEEP, SEMI_ASPECT),

    /** 아래가 4분의 1 열린 원호(270도). 정사각형 자리에 쓴다. */
    Arc(ARC_SWEEP, 1f),
}

/**
 * 게이지 구간. 값이 [until] 이하인 첫 구간의 색과 이름을 쓴다. 트랙에는 구간마다 옅은 색을 깔아 범위를 미리 보인다.
 * NaN이나 무한대면 [IllegalArgumentException]을 던진다.
 *
 * @property until 구간 끝 값(포함)
 * @property label 구간 이름. TalkBack이 값 뒤에 읽는다. 예: "좋음"
 */
@Immutable
public data class KGaugeSegment(val until: Float, val color: Color, val label: String? = null) {
    init {
        require(until.isFinite()) { "until은 유한한 값이어야 합니다." }
    }
}

/**
 * 반원이나 원호 게이지. 속도, 신용 점수, 배터리, 공기질처럼 정해진 범위 안의 한 값을 보인다.
 * 값이 바뀌면 호가 스프링으로 따라가고, 끝에 표면 테두리를 두른 손잡이 점이 붙는다. '애니메이션 삭제' 설정에서는 바로 바뀐다.
 * [segments]를 주면 트랙에 구간 색을 옅게 깔고, 채운 호는 지금 값이 속한 구간의 색으로 칠한다.
 * 가운데에는 값과 [label]을, 양 끝 아래에는 최솟값과 최댓값을 적는다. 넓은 화면에서도 폭은 360dp를 넘지 않는다. 원호 방향은 RTL에서도 바꾸지 않는다(계기판 관례).
 *
 * TalkBack은 진행 표시로 읽고, 상태로 '72, 좋음'처럼 값과 구간 이름을 알린다.
 *
 * ```kotlin
 * KGauge(72f, Modifier.width(220.dp), label = "신용 점수", segments = listOf(KGaugeSegment(40f, bad, "낮음"), KGaugeSegment(100f, good, "좋음")))
 * ```
 *
 * @param value 값. [range] 밖이면 끝 값으로 그린다. NaN이면 [IllegalArgumentException]을 던진다.
 * @param range 값 범위. 비어 있으면 [IllegalArgumentException]을 던진다.
 * @param color 구간이 없을 때 채운 호 색
 * @param thickness 호 두께. 0 이하면 [IllegalArgumentException]을 던진다.
 * @param showRange 양 끝에 최솟값과 최댓값을 적을지
 * @param center 가운데 내용을 바꾼다. `null`이면 값과 [label]을 쓴다.
 */
@Composable
public fun KGauge(
    value: Float,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..DEFAULT_MAX,
    label: String? = null,
    shape: KGaugeShape = KGaugeShape.Semicircle,
    segments: List<KGaugeSegment> = emptyList(),
    color: Color = KineticaTheme.colors.accent,
    thickness: Dp = 14.dp,
    showRange: Boolean = true,
    valueFormatter: (Float) -> String = KChartDefaults::formatValue,
    surface: SurfaceStyle = KineticaTheme.surface,
    center: (@Composable () -> Unit)? = null,
) {
    require(!value.isNaN()) { "value에 NaN을 넣을 수 없습니다." }
    require(range.endInclusive > range.start) { "range가 비어 있습니다." }
    require(thickness > 0.dp) { "thickness는 0보다 커야 합니다." }
    val shown = value.coerceIn(range)
    val fraction = (shown - range.start) / (range.endInclusive - range.start)
    val still = reducedMotion()
    val progress = remember { Animatable(if (still) fraction else 0f) }
    LaunchedEffect(fraction, still) {
        if (still) progress.snapTo(fraction) else progress.animateTo(fraction, spring(GAUGE_DAMPING, Spring.StiffnessLow))
    }
    val sorted = remember(segments) { segments.sortedBy { it.until } }
    val segment = sorted.firstOrNull { shown <= it.until } ?: sorted.lastOrNull()
    val fill = segment?.color ?: color
    val state = joinChartParts(listOfNotNull(valueFormatter(shown), segment?.label))
    val ink = surface.contentColor
    BoxWithConstraints(
        modifier
            .widthIn(max = GAUGE_MAX)
            .defaultMinSize(GAUGE_MIN, GAUGE_MIN / shape.aspect)
            .aspectRatio(shape.aspect)
            .clearAndSetSemantics {
                label?.let { contentDescription = it }
                stateDescription = state
                progressBarRangeInfo = ProgressBarRangeInfo(shown, range)
            },
    ) {
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val geometry = GaugeGeometry(size, shape, thickness.toPx())
                val stroke = Stroke(thickness.toPx(), cap = StrokeCap.Round)
                val knob = KNOB_RING.toPx()
                onDrawBehind {
                    drawGaugeTrack(geometry, sorted, range, surface.raisedFill(TRACK_ALPHA), stroke)
                    val sweep = geometry.sweep * progress.value
                    if (sweep > 0f) drawArc(fill, geometry.start, sweep, false, geometry.topLeft, geometry.box, style = stroke)
                    val tip = geometry.point(geometry.start + sweep)
                    drawCircle(surface.inkBackground(), stroke.width / 2 + knob, tip)
                    drawCircle(fill, stroke.width / 2 - knob / 2, tip)
                }
            },
        )
        val textTop = if (shape == KGaugeShape.Semicircle) maxHeight * SEMI_TEXT_TOP else maxHeight * ARC_TEXT_TOP
        Column(
            Modifier.fillMaxWidth().padding(top = textTop, start = thickness * 2, end = thickness * 2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (center !=
                null
            ) {
                center()
            } else {
                GaugeCenter(valueFormatter(shown), joinChartParts(listOfNotNull(label, segment?.label), " · "), ink, surface.muted(MUTED))
            }
        }
        if (showRange) GaugeEnds(range, valueFormatter, ink, shape, thickness)
    }
}

/** 기본 가운데 내용. 큰 값과 그 아래 이름·구간 글자 */
@Composable
private fun GaugeCenter(value: String, caption: String, ink: Color, muted: Color) {
    ThemedText(
        value,
        style = TextStyle(color = ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
        maxLines = 1,
    )
    if (caption.isNotEmpty()) {
        ThemedText(caption, style = TextStyle(color = muted, fontSize = 13.sp, textAlign = TextAlign.Center))
    }
}

/** 최솟값과 최댓값. 호 양 끝 바로 아래에 가운데를 맞춰 놓고, 판 밖으로 나가지 않게 안으로 민다. */
@Composable
private fun GaugeEnds(
    range: ClosedFloatingPointRange<Float>,
    valueFormatter: (Float) -> String,
    ink: Color,
    shape: KGaugeShape,
    thickness: Dp,
) {
    val style = chartAxisText(ink)
    Layout(
        content = {
            ThemedText(valueFormatter(range.start), style = style, maxLines = 1)
            ThemedText(valueFormatter(range.endInclusive), style = style, maxLines = 1)
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val stroke = thickness.toPx()
        val geometry = GaugeGeometry(Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()), shape, stroke)
        val labels = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val ends = listOf(geometry.point(geometry.start), geometry.point(geometry.start + geometry.sweep))
            labels.forEachIndexed { i, label ->
                val x = (ends[i].x - label.width / 2f).roundToInt().coerceIn(0, (constraints.maxWidth - label.width).coerceAtLeast(0))
                val y = (ends[i].y + stroke / 2 + END_GAP.toPx()).roundToInt().coerceAtMost(
                    (constraints.maxHeight - label.height).coerceAtLeast(0),
                )
                label.place(x, y)
            }
        }
    }
}

/** 호를 그릴 사각형과 시작 각도. 각도는 3시 방향이 0이고 시계 방향으로 커진다. */
private class GaugeGeometry(size: Size, shape: KGaugeShape, stroke: Float) {
    val sweep = shape.sweep
    val start = HALF_TURN_DEGREES + (HALF_TURN_DEGREES - sweep) / 2
    val radius: Float
    val center: Offset

    init {
        val labelRoom = if (shape == KGaugeShape.Semicircle) size.height * SEMI_LABEL_ROOM else 0f
        radius = if (shape == KGaugeShape.Semicircle) {
            minOf(size.width / 2, size.height - labelRoom) - stroke / 2
        } else {
            minOf(size.width, size.height) / 2 - stroke / 2
        }
        center =
            if (shape ==
                KGaugeShape.Semicircle
            ) {
                Offset(size.width / 2, size.height - labelRoom)
            } else {
                Offset(size.width / 2, size.height / 2)
            }
    }

    val topLeft: Offset get() = center - Offset(radius, radius)
    val box: Size get() = Size(radius * 2, radius * 2)

    fun point(angle: Float): Offset {
        val radians = angle / HALF_TURN_DEGREES * PI.toFloat()
        return center + Offset(cos(radians), sin(radians)) * radius
    }
}

private fun DrawScope.drawGaugeTrack(
    geometry: GaugeGeometry,
    segments: List<KGaugeSegment>,
    range: ClosedFloatingPointRange<Float>,
    track: Color,
    stroke: Stroke,
) {
    drawArc(track, geometry.start, geometry.sweep, false, geometry.topLeft, geometry.box, style = stroke)
    if (segments.isEmpty()) return
    val span = range.endInclusive - range.start
    var from = range.start
    // 구간 띠는 끝을 둥글게 하면 이웃 구간과 겹쳐 보이므로 각지게 그린다.
    val band = Stroke(stroke.width, cap = StrokeCap.Butt)
    segments.forEach { segment ->
        val to = segment.until.coerceIn(range)
        if (to > from) {
            val a = geometry.start + geometry.sweep * (from - range.start) / span
            val b = geometry.start + geometry.sweep * (to - range.start) / span
            drawArc(segment.color.copy(alpha = SEGMENT_ALPHA), a, b - a, false, geometry.topLeft, geometry.box, style = band)
        }
        from = maxOf(from, to)
    }
}

private const val HALF_TURN_DEGREES = 180f
private const val SEMI_SWEEP = 180f
private const val SEMI_ASPECT = 1.5f
private const val ARC_SWEEP = 270f
private const val DEFAULT_MAX = 100f
private const val GAUGE_DAMPING = 0.8f
private const val TRACK_ALPHA = 0.1f
private const val SEGMENT_ALPHA = 0.28f
private const val MUTED = 0.66f
private const val SEMI_LABEL_ROOM = 0.2f
private const val SEMI_TEXT_TOP = 0.36f
private const val ARC_TEXT_TOP = 0.34f
private val KNOB_RING = 3.dp
private val GAUGE_MIN = 160.dp
private val GAUGE_MAX = 360.dp
private val END_GAP = 2.dp
