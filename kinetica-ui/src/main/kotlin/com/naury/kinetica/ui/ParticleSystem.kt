package com.naury.kinetica.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** [preset]에 맞는 입자 시스템을 만든다. 입자 배열은 여기서 한 번만 만든다. */
internal fun particleSystem(preset: ParticlePreset, density: Float, colors: List<Color>): ParticleSystem {
    fun count(base: Int) = (base * density).roundToInt().coerceAtLeast(1)
    return when (preset) {
        ParticlePreset.Snow -> SnowSystem(count(SNOW_COUNT), colors)
        ParticlePreset.Bubbles -> BubbleSystem(count(BUBBLE_COUNT), colors)
        ParticlePreset.Sparks -> SparkSystem(count(SPARK_COUNT), colors)
        ParticlePreset.Fireworks -> FireworkSystem(density, colors)
    }
}

/**
 * 고정 크기 배열에 입자를 담아 그리기 단계에서 움직이고 그린다. 크기와 속도는 dp 기준 값에 [unit]을 곱해 px로 쓴다.
 * 영역 크기가 바뀌면 처음부터 다시 뿌리고 몇 초를 미리 돌려, 첫 프레임부터 입자가 고르게 퍼져 보이게 한다.
 */
internal abstract class ParticleSystem(val capacity: Int, colors: List<Color>) {
    protected val palette: Array<Color> = colors.toTypedArray()
    protected val random: Random = Random(capacity * SEED_SCALE + colors.size)
    val x: FloatArray = FloatArray(capacity)
    val y: FloatArray = FloatArray(capacity)
    protected val vx: FloatArray = FloatArray(capacity)
    protected val vy: FloatArray = FloatArray(capacity)
    protected val age: FloatArray = FloatArray(capacity)
    protected val life: FloatArray = FloatArray(capacity)
    protected val radius: FloatArray = FloatArray(capacity)
    protected val phase: FloatArray = FloatArray(capacity)
    protected val tone: IntArray = IntArray(capacity)
    protected var width: Float = 0f
    protected var height: Float = 0f
    protected var unit: Float = 1f
    private var last = Float.NaN

    /** [time]초 시점까지 움직이고 그린다. [time]이 NaN이면 움직이지 않고 미리 돌려 둔 모습을 그린다. */
    fun frame(scope: DrawScope, time: Float, state: ParticleFieldState) {
        val w = scope.size.width
        val h = scope.size.height
        if (w <= 0f || h <= 0f) return
        if (w != width || h != height) {
            width = w
            height = h
            unit = scope.density
            reset()
            repeat(WARM_STEPS) { step(STEP_DT, state.autoLaunch) }
        }
        if (time.isNaN()) {
            state.queued = 0
        } else {
            // 프레임이 밀려 시간이 크게 건너뛰어도 입자가 튀지 않도록 짧은 간격으로 나눠 움직인다.
            var left = if (last.isNaN()) 0f else (time - last).coerceIn(0f, MAX_CATCH_UP)
            last = time
            for (q in 0 until state.queued) launch(state.queue[q * 2], state.queue[q * 2 + 1])
            state.queued = 0
            while (left > 0f) {
                val dt = minOf(left, STEP_DT)
                step(dt, state.autoLaunch)
                left -= dt
            }
        }
        draw(scope)
    }

    protected fun rand(min: Float, max: Float): Float = min + random.nextFloat() * (max - min)

    protected fun pickTone(): Int = random.nextInt(palette.size)

    protected fun angle(): Float = random.nextFloat() * TWO_PI

    abstract fun reset()

    abstract fun step(dt: Float, autoLaunch: Boolean)

    abstract fun draw(scope: DrawScope)

    /** ([px], [py])에서 한 번 터뜨린다. 좌표가 NaN이면 위치를 고른다. 기본은 아무것도 하지 않는다. */
    open fun launch(px: Float, py: Float): Unit = Unit
}

/** 눈. 큰 눈송이일수록 빠르고 진하게 그려 앞쪽에 있는 것처럼 보이게 한다. */
private class SnowSystem(capacity: Int, colors: List<Color>) : ParticleSystem(capacity, colors) {
    private val alpha = FloatArray(capacity)

    override fun reset() {
        for (i in 0 until capacity) spawn(i, anywhere = true)
    }

    private fun spawn(i: Int, anywhere: Boolean) {
        val depth = random.nextFloat()
        radius[i] = (SNOW_MIN_DP + depth * SNOW_RANGE_DP) * unit
        alpha[i] = SNOW_MIN_ALPHA + depth * (1f - SNOW_MIN_ALPHA)
        vy[i] = (SNOW_FALL_DP + depth * SNOW_FALL_RANGE_DP) * unit
        vx[i] = rand(-SNOW_WIND_DP, SNOW_WIND_DP) * unit
        x[i] = random.nextFloat() * width
        y[i] = if (anywhere) random.nextFloat() * height else -radius[i]
        phase[i] = angle()
        life[i] = rand(SNOW_SWAY_MIN, SNOW_SWAY_MAX)
        tone[i] = pickTone()
    }

    override fun step(dt: Float, autoLaunch: Boolean) {
        for (i in 0 until capacity) {
            age[i] += dt
            y[i] += vy[i] * dt
            x[i] += (vx[i] + sin(age[i] * life[i] + phase[i]) * SNOW_SWAY_DP * unit) * dt
            if (x[i] < -radius[i]) x[i] += width + radius[i] * 2f
            if (x[i] > width + radius[i]) x[i] -= width + radius[i] * 2f
            if (y[i] - radius[i] > height) spawn(i, anywhere = false)
        }
    }

    override fun draw(scope: DrawScope) {
        for (i in 0 until capacity) scope.drawCircle(palette[tone[i]], radius[i], Offset(x[i], y[i]), alpha = alpha[i])
    }
}

/** 거품. 위로 흔들리며 떠오르다 수명이 다하거나 위쪽 끝에 닿으면 서서히 사라진다. */
private class BubbleSystem(capacity: Int, colors: List<Color>) : ParticleSystem(capacity, colors) {
    private var ring = Stroke(1f)

    override fun reset() {
        ring = Stroke(BUBBLE_RING_DP * unit)
        for (i in 0 until capacity) spawn(i, anywhere = true)
    }

    private fun spawn(i: Int, anywhere: Boolean) {
        radius[i] = rand(BUBBLE_MIN_DP, BUBBLE_MAX_DP) * unit
        vy[i] = -rand(BUBBLE_RISE_MIN_DP, BUBBLE_RISE_MAX_DP) * unit
        x[i] = random.nextFloat() * width
        y[i] = if (anywhere) random.nextFloat() * height else height + radius[i]
        life[i] = rand(BUBBLE_LIFE_MIN, BUBBLE_LIFE_MAX)
        age[i] = if (anywhere) random.nextFloat() * life[i] * HALF else 0f
        phase[i] = angle()
        tone[i] = pickTone()
    }

    override fun step(dt: Float, autoLaunch: Boolean) {
        for (i in 0 until capacity) {
            age[i] += dt
            y[i] += vy[i] * dt
            x[i] += sin(age[i] * BUBBLE_WOBBLE + phase[i]) * BUBBLE_DRIFT_DP * unit * dt
            if (y[i] + radius[i] < 0f || age[i] > life[i]) spawn(i, anywhere = false)
        }
    }

    override fun draw(scope: DrawScope) {
        val top = height * BUBBLE_TOP_FADE
        for (i in 0 until capacity) {
            val fade = minOf(age[i] / BUBBLE_FADE_IN, (life[i] - age[i]) / BUBBLE_FADE_OUT, (y[i] + radius[i]) / top).coerceIn(0f, 1f)
            if (fade <= 0f) continue
            val center = Offset(x[i], y[i])
            val color = palette[tone[i]]
            val r = radius[i]
            scope.drawCircle(color, r, center, alpha = BUBBLE_FILL_ALPHA * fade)
            scope.drawCircle(color, r, center, alpha = BUBBLE_RING_ALPHA * fade, style = ring)
            val shine = Offset(x[i] - r * BUBBLE_SHINE_AT, y[i] - r * BUBBLE_SHINE_AT)
            scope.drawCircle(Color.White, r * BUBBLE_SHINE_SIZE, shine, alpha = BUBBLE_SHINE_ALPHA * fade)
        }
    }
}

private const val TWO_PI = 6.2831855f
private const val HALF = 0.5f
private const val SEED_SCALE = 31
private const val WARM_STEPS = 90
private const val STEP_DT = 1f / 30f
private const val MAX_CATCH_UP = 0.5f
private const val SNOW_COUNT = 90
private const val BUBBLE_COUNT = 26
private const val SPARK_COUNT = 70
private const val SNOW_MIN_DP = 1.2f
private const val SNOW_RANGE_DP = 3f
private const val SNOW_MIN_ALPHA = 0.45f
private const val SNOW_FALL_DP = 14f
private const val SNOW_FALL_RANGE_DP = 42f
private const val SNOW_WIND_DP = 6f
private const val SNOW_SWAY_MIN = 0.6f
private const val SNOW_SWAY_MAX = 1.6f
private const val SNOW_SWAY_DP = 14f
private const val BUBBLE_RING_DP = 1.2f
private const val BUBBLE_MIN_DP = 4f
private const val BUBBLE_MAX_DP = 15f
private const val BUBBLE_RISE_MIN_DP = 18f
private const val BUBBLE_RISE_MAX_DP = 48f
private const val BUBBLE_LIFE_MIN = 4f
private const val BUBBLE_LIFE_MAX = 9f
private const val BUBBLE_WOBBLE = 1.7f
private const val BUBBLE_DRIFT_DP = 12f
private const val BUBBLE_TOP_FADE = 0.12f
private const val BUBBLE_FADE_IN = 0.4f
private const val BUBBLE_FADE_OUT = 0.5f
private const val BUBBLE_FILL_ALPHA = 0.1f
private const val BUBBLE_RING_ALPHA = 0.6f
private const val BUBBLE_SHINE_AT = 0.38f
private const val BUBBLE_SHINE_SIZE = 0.2f
private const val BUBBLE_SHINE_ALPHA = 0.75f
