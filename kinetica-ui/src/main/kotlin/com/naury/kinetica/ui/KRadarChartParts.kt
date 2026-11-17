package com.naury.kinetica.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** 축 방향과 반지름. 반지름은 배치 단계에서 정하고 그리기 단계와 말풍선 위치가 읽는다. */
internal class RadarPlot(count: Int) {
    var radius by mutableFloatStateOf(0f)

    /** 차트 영역 크기(px). 가운데가 이 크기의 절반이다. */
    var size by mutableStateOf(IntSize.Zero)

    /** 축마다 12시 기준 시계 방향 단위 벡터 */
    val cos = FloatArray(count) { cos(axisAngle(it, count)).toFloat() }
    val sin = FloatArray(count) { sin(axisAngle(it, count)).toFloat() }

    /** 가운데에서 ([dx], [dy])만큼 떨어진 곳에서 가장 가까운 축. 가운데 근처나 차트 바깥이면 -1 */
    fun axisAt(dx: Float, dy: Float): Int {
        val distance = hypot(dx, dy)
        if (radius <= 0f || distance > radius * TAP_REACH || distance < radius * TAP_DEAD_ZONE) return -1
        val turn = 2 * PI
        val fromTop = ((atan2(dy.toDouble(), dx.toDouble()) + PI / 2) % turn + turn) % turn
        return (fromTop / (turn / cos.size)).roundToInt() % cos.size
    }

    /** [axis]에서 가장 바깥 계열 점 위치에 붙이는 말풍선 자리 */
    fun anchorFor(frame: RadarFrame, axis: Int): ChartBubbleAnchor? {
        if (axis !in cos.indices || radius <= 0f) return null
        var reach = 0f
        frame.morph.target.indices.forEach { s ->
            if (axis < frame.morph.target[s].size) reach = maxOf(reach, frame.fraction(s, axis))
        }
        val x = size.width / 2f + cos[axis] * radius * reach
        val y = size.height / 2f + sin[axis] * radius * reach
        return ChartBubbleAnchor(x, y - DOT_REACH, y + DOT_REACH, preferBelow = sin[axis] > CHART_HALF, sideways = true)
    }
}

internal class RadarMotion(val reveal: () -> Float, val selected: Int)

internal class RadarFrame(
    val axes: List<String>,
    val series: List<RadarSeries>,
    val morph: ChartMorph,
    val plot: RadarPlot,
    val motion: RadarMotion,
    val rings: Int,
) {
    /** 계열 [s]의 축 [i] 값이 가운데(0)에서 바깥 고리(1) 사이 어디인지. 범위 밖이면 끝에 붙인다. */
    fun fraction(s: Int, i: Int): Float = morph.fraction(morph.value(s, i)).coerceIn(0f, 1f)
}

/** 그릴 때마다 다시 쓰는 계열 다각형 */
internal class RadarBuffers(count: Int) {
    val paths = List(count) { Path() }
}

/** 크기가 바뀔 때만 다시 만드는 눈금 고리와 선 모양 */
internal class RadarLook(val ink: Color, val rings: List<Path>, val grid: Stroke, val line: Stroke, val dot: Float, val ring: Float)

internal fun Density.radarLook(ink: Color, plot: RadarPlot, rings: Int, cx: Float, cy: Float): RadarLook {
    val count = plot.cos.size
    val paths = List(rings) { k ->
        val r = plot.radius * (k + 1) / rings
        Path().apply {
            for (i in 0 until count) {
                val x = cx + plot.cos[i] * r
                val y = cy + plot.sin[i] * r
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }
    return RadarLook(
        ink,
        paths,
        Stroke(GRID_WIDTH.toPx()),
        Stroke(LINE_WIDTH.toPx(), join = StrokeJoin.Round),
        DOT_RADIUS.toPx(),
        DOT_RING.toPx(),
    )
}

internal fun DrawScope.drawRadar(frame: RadarFrame, look: RadarLook, buffers: RadarBuffers) {
    val plot = frame.plot
    val reveal = frame.motion.reveal()
    val gridAlpha = chartStage(reveal, 0f, GRID_STAGE_END)
    val cx = size.width / 2f
    val cy = size.height / 2f
    look.rings.forEach { drawPath(it, look.ink, alpha = RING_ALPHA * gridAlpha, style = look.grid) }
    val selected = frame.motion.selected
    for (i in plot.cos.indices) {
        val alpha = if (i == selected) SELECTED_SPOKE_ALPHA else RING_ALPHA
        val end = Offset(cx + plot.cos[i] * plot.radius, cy + plot.sin[i] * plot.radius)
        drawLine(look.ink, Offset(cx, cy), end, look.grid.width, alpha = alpha * gridAlpha)
    }
    val count = minOf(frame.series.size, frame.morph.target.size, buffers.paths.size)
    for (s in 0 until count) {
        val start = s * SERIES_STAGGER
        val stage = chartStage(reveal, start, start + SERIES_SPAN)
        if (stage <= 0f) continue
        val axes = minOf(plot.cos.size, frame.morph.target[s].size)
        val path = buffers.paths[s]
        path.reset()
        for (i in 0 until axes) {
            val r = plot.radius * frame.fraction(s, i) * stage
            if (i == 0) path.moveTo(cx + plot.cos[i] * r, cy + plot.sin[i] * r) else path.lineTo(cx + plot.cos[i] * r, cy + plot.sin[i] * r)
        }
        path.close()
        val color = frame.series[s].color
        drawPath(path, color, alpha = FILL_ALPHA)
        drawPath(path, color, style = look.line)
        for (i in 0 until axes) {
            val r = plot.radius * frame.fraction(s, i) * stage
            val point = Offset(cx + plot.cos[i] * r, cy + plot.sin[i] * r)
            if (i == selected) drawCircle(Color.White, look.dot + look.ring, point)
            drawCircle(color, if (i == selected) look.dot else look.dot * SMALL_DOT, point)
        }
    }
}

private fun axisAngle(index: Int, count: Int): Double = -PI / 2 + 2 * PI * index / count

private const val TAP_REACH = 1.2f
private const val TAP_DEAD_ZONE = 0.12f
private const val DOT_REACH = 6f
private const val GRID_STAGE_END = 0.35f
private const val SERIES_STAGGER = 0.12f
private const val SERIES_SPAN = 0.7f
private const val RING_ALPHA = 0.14f
private const val SELECTED_SPOKE_ALPHA = 0.5f
private const val FILL_ALPHA = 0.22f
private const val SMALL_DOT = 0.6f
private val GRID_WIDTH = 1.dp
private val LINE_WIDTH = 2.dp
private val DOT_RADIUS = 4.dp
private val DOT_RING = 2.dp
