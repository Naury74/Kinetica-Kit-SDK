package com.naury.kinetica.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 원형 슬라이더의 각도와 값 계산. 각도는 3시 방향에서 시계 방향으로 잰 도 단위이고, '상대 각도'는 [startAngle]부터 잰 0..[sweep] 값이다.
 * [periodic]이면 범위의 두 끝이 원 위 같은 자리라 값이 한 바퀴 돌아 이어진다.
 */
@Immutable
internal class CircleDial(
    val range: ClosedFloatingPointRange<Float>,
    val startAngle: Float,
    val sweep: Float,
    val step: Float,
    val ticks: Int,
    val periodic: Boolean,
) {
    init {
        require(range.start.isFinite() && range.endInclusive.isFinite() && range.endInclusive > range.start) {
            "valueRange는 끝이 시작보다 큰 유한한 범위여야 합니다."
        }
        require(sweep > 0f && sweep <= FULL_TURN) { "sweepAngle은 0보다 크고 360 이하여야 합니다." }
        require(step >= 0f && step.isFinite()) { "step은 0 이상의 유한한 수여야 합니다." }
        require(ticks >= 0) { "ticks는 0 이상이어야 합니다." }
    }

    val span: Float get() = range.endInclusive - range.start

    /** 방향키와 TalkBack 한 번에 움직이는 값 */
    val keyStep: Float get() = when {
        step > 0f -> step
        ticks > 0 -> span / ticks
        else -> span / DEFAULT_KEY_STEPS
    }

    fun relOf(value: Float): Float = (value - range.start) / span * sweep

    fun valueOfRel(rel: Float): Float = range.start + rel / sweep * span

    /** 범위 안으로 맞춘다. [periodic]이면 한 바퀴 돌려 맞추고, 아니면 가까운 끝으로 자른다. */
    fun fit(value: Float): Float = if (periodic) wrap(value) else value.coerceIn(range.start, range.endInclusive)

    fun wrap(value: Float): Float {
        val m = (value - range.start) % span
        return range.start + if (m < 0f) m + span else m
    }

    /** [step] 간격으로 붙인다. 붙인 값도 [fit]으로 맞춘다. */
    fun snap(value: Float): Float {
        if (step <= 0f) return fit(value)
        return fit(range.start + ((value - range.start) / step).roundToInt() * step)
    }

    /** 값이 들어 있는 눈금 칸 번호. [ticks]가 0이면 늘 0이다. */
    fun tickOf(value: Float): Int {
        if (ticks <= 0) return 0
        val raw = floor((value - range.start) / span * ticks + EPSILON).toInt()
        return if (periodic) Math.floorMod(raw, ticks) else raw
    }

    /** [point]가 [center]에서 보이는 상대 각도(0..360) */
    fun relAt(point: Offset, center: Offset): Float {
        val deg = Math.toDegrees(atan2(point.y - center.y, point.x - center.x).toDouble()).toFloat()
        return normalizeDegrees(deg - startAngle)
    }

    /**
     * 끌기 중 손가락 각도 [rel]을 값의 상대 각도로 바꾼다. 원호 밖(남은 각도)은 가까운 끝으로 보내고, 끝을 지나 반대쪽 끝으로
     * 건너뛰려 하면 [current] 쪽 끝에 붙여 둔다. 한 바퀴 원에서 0을 넘어 360으로 튀지 않게 하는 규칙이다.
     */
    fun follow(rel: Float, current: Float): Float {
        var target = if (rel <= sweep) {
            rel
        } else if (rel - sweep < FULL_TURN - rel) {
            sweep
        } else {
            0f
        }
        if (abs(target - current) > HALF_TURN) target = if (current > sweep / 2) sweep else 0f
        return target
    }

    /** 한 바퀴 원에서 바늘 끝 각도(3시 기준) */
    fun angleOf(value: Float): Float = startAngle + relOf(value)
}

internal fun normalizeDegrees(deg: Float): Float {
    val m = deg % FULL_TURN
    return if (m < 0f) m + FULL_TURN else m
}

/** -180..180으로 맞춘 각도 차이 */
internal fun shortestDelta(deg: Float): Float {
    val m = normalizeDegrees(deg)
    return if (m > HALF_TURN) m - FULL_TURN else m
}

/** 반지름 [radius] 원 위 [angle]도 자리 */
internal fun pointOn(center: Offset, radius: Float, angle: Float): Offset {
    val rad = Math.toRadians(angle.toDouble())
    return Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat())
}

/** 눈금과 끝을 지날 때 햅틱을 낸다. 너무 촘촘하면 [MIN_TICK_INTERVAL_MS]만큼 거른다. */
internal class CircleTicker(private val haptic: HapticFeedback) {
    private var lastTick = 0
    private var lastAt = Long.MIN_VALUE / 2

    fun reset(dial: CircleDial, value: Float) {
        lastTick = dial.tickOf(value)
    }

    fun step(dial: CircleDial, previous: Float, next: Float, now: Long) {
        if (next == previous) return
        val edge = !dial.periodic && (next == dial.range.start || next == dial.range.endInclusive)
        val tick = dial.tickOf(next)
        when {
            edge -> haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

            dial.ticks > 0 && tick != lastTick && now - lastAt >= MIN_TICK_INTERVAL_MS -> {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                lastAt = now
            }
        }
        lastTick = tick
    }
}

/** 원형 슬라이더의 크기 값(px). */
internal class CircleMetrics(val center: Offset, val radius: Float, val stroke: Float, val hit: Float) {
    /** [point]가 트랙 띠 위에 있는지. 손가락 크기를 생각해 띠보다 조금 넓게 본다. */
    fun onBand(point: Offset): Boolean = abs(hypot(point.x - center.x, point.y - center.y) - radius) <= hit
}

internal fun circleMetrics(size: Size, stroke: Float, slack: Float): CircleMetrics {
    val side = minOf(size.width, size.height)
    return CircleMetrics(Offset(size.width / 2, size.height / 2), (side - stroke) / 2, stroke, stroke / 2 + slack)
}

/** 원형 슬라이더 그림 색 */
internal class CircleInk(val accent: Color, val track: Color, val tick: Color, val thumb: Color, val focus: Color)

/** 트랙, 눈금, 진행 원호. 진행 원호는 [fromAngle]부터 [sweep]도다. */
internal fun DrawScope.drawCircleTrack(dial: CircleDial, m: CircleMetrics, ink: CircleInk, fromAngle: Float, sweep: Float) {
    val topLeft = Offset(m.center.x - m.radius, m.center.y - m.radius)
    val arc = Size(m.radius * 2, m.radius * 2)
    val full = dial.sweep >= FULL_TURN
    drawArc(
        ink.track,
        dial.startAngle,
        dial.sweep,
        false,
        topLeft,
        arc,
        style = Stroke(m.stroke, cap = if (full) StrokeCap.Butt else StrokeCap.Round),
    )
    if (dial.ticks > 0) drawCircleTicks(dial, m, ink.tick)
    if (sweep > 0f) drawArc(ink.accent, fromAngle, sweep, false, topLeft, arc, style = Stroke(m.stroke, cap = StrokeCap.Round))
}

private fun DrawScope.drawCircleTicks(dial: CircleDial, m: CircleMetrics, color: Color) {
    val inner = m.radius - m.stroke / 2 - TICK_INSET.toPx()
    val length = TICK_LENGTH.toPx()
    val count = if (dial.sweep >= FULL_TURN) dial.ticks else dial.ticks + 1
    for (i in 0 until count) {
        val angle = dial.startAngle + dial.sweep * i / dial.ticks
        drawLine(color, pointOn(m.center, inner, angle), pointOn(m.center, inner - length, angle), TICK_WIDTH.toPx(), StrokeCap.Round)
    }
}

/** 손잡이. [icon]이 있으면 가운데에 강조색으로 그린다. [focused]면 바깥에 초점 고리를 두른다. */
internal fun DrawScope.drawCircleThumb(m: CircleMetrics, angle: Float, ink: CircleInk, icon: VectorPainter?, focused: Boolean) {
    val c = pointOn(m.center, m.radius, angle)
    val r = m.stroke / 2 * THUMB_SCALE
    if (focused) drawCircle(ink.focus, r + FOCUS_GAP.toPx(), c, style = Stroke(FOCUS_WIDTH.toPx()))
    drawCircle(Color.Black.copy(alpha = SHADOW_ALPHA), r + SHADOW_SPREAD.toPx(), c.copy(y = c.y + SHADOW_DROP.toPx()))
    drawCircle(ink.thumb, r, c)
    if (icon != null) {
        val side = r * ICON_SCALE
        translate(c.x - side / 2, c.y - side / 2) {
            with(icon) { draw(Size(side, side), colorFilter = ColorFilter.tint(ink.accent)) }
        }
    }
}

/**
 * 원 위에서 끄는 동작. [onDown]이 이 손가락을 맡겠다고 하면(손잡이나 띠 위) 누르자마자 잡고, 움직일 때마다 [onMove], 뗄 때 [onEnd]를 부른다.
 * 맡지 않은 손가락은 소비하지 않아 가운데 내용이나 부모 스크롤로 넘어간다.
 */
internal fun Modifier.circleDrag(
    key: Any,
    onDown: AwaitPointerEventScope.(Offset) -> Boolean,
    onMove: AwaitPointerEventScope.(PointerInputChange) -> Unit,
    onEnd: () -> Unit,
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!onDown(down.position)) return@awaitEachGesture
        down.consume()
        drag(down.id) { change ->
            onMove(change)
            change.consume()
        }
        onEnd()
    }
}

/** 방향키를 값 증감으로 바꾼다. 오른쪽과 위가 증가다. 원은 시계 방향이라 RTL에서도 같다. */
internal fun circleKey(key: Key): Int? = when (key) {
    Key.DirectionRight, Key.DirectionUp -> 1
    Key.DirectionLeft, Key.DirectionDown -> -1
    else -> null
}

private const val FULL_TURN = 360f
private const val HALF_TURN = 180f
private const val EPSILON = 0.0001f
private const val DEFAULT_KEY_STEPS = 100f
private const val MIN_TICK_INTERVAL_MS = 18L
private val TICK_INSET = 6.dp
private val TICK_LENGTH = 4.dp
private val TICK_WIDTH = 1.5.dp
private val FOCUS_GAP = 3.dp
private val FOCUS_WIDTH = 2.dp
private val SHADOW_SPREAD = 1.dp
private val SHADOW_DROP = 1.dp
private const val SHADOW_ALPHA = 0.18f
private const val THUMB_SCALE = 0.84f
private const val ICON_SCALE = 1.1f

/** 손가락 굵기를 생각해 트랙 띠보다 넓게 받는 여유 */
internal val CIRCLE_TOUCH_SLACK: Dp = 14.dp
