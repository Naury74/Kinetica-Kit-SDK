package com.naury.kinetica.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** 튀는 불꽃. 아래에서 솟았다가 중력에 끌려 호를 그리고, 꼬리를 끌며 사그라든다. */
internal class SparkSystem(capacity: Int, colors: List<Color>) : ParticleSystem(capacity, colors) {
    private val hot = hotColors(palette)
    private var cursor = 0

    override fun reset() {
        for (i in 0 until capacity) {
            spawn(i)
            // 처음에 한꺼번에 솟지 않도록 출발을 흩어 놓는다.
            age[i] = -random.nextFloat() * life[i]
        }
    }

    private fun spawn(i: Int) {
        x[i] = width * rand(SPARK_EDGE, 1f - SPARK_EDGE)
        y[i] = height + SPARK_START_DP * unit
        vx[i] = rand(-SPARK_SIDE_DP, SPARK_SIDE_DP) * unit
        vy[i] = -rand(SPARK_RISE_MIN_DP, SPARK_RISE_MAX_DP) * unit * minOf(1f, height / (SPARK_REF_HEIGHT_DP * unit))
        life[i] = rand(SPARK_LIFE_MIN, SPARK_LIFE_MAX)
        age[i] = 0f
        radius[i] = rand(SPARK_MIN_DP, SPARK_MAX_DP) * unit
        phase[i] = angle()
        tone[i] = pickTone()
    }

    override fun launch(px: Float, py: Float) {
        val cx = if (px.isNaN()) width * random.nextFloat() else px
        val cy = if (py.isNaN()) height * rand(BURST_TOP, BURST_BOTTOM) else py
        repeat(minOf(SPARK_BURST, capacity)) {
            val i = cursor
            cursor = (cursor + 1) % capacity
            val a = angle()
            val speed = rand(SPARK_BURST_MIN_DP, SPARK_BURST_MAX_DP) * unit
            x[i] = cx
            y[i] = cy
            vx[i] = cos(a) * speed
            vy[i] = sin(a) * speed - SPARK_BURST_LIFT_DP * unit
            life[i] = rand(SPARK_BURST_LIFE_MIN, SPARK_BURST_LIFE_MAX)
            age[i] = 0f
            radius[i] = rand(SPARK_MIN_DP, SPARK_MAX_DP) * unit
            tone[i] = pickTone()
        }
    }

    override fun step(dt: Float, autoLaunch: Boolean) {
        val drag = 1f - SPARK_DRAG * dt
        val fall = SPARK_GRAVITY_DP * unit * dt
        for (i in 0 until capacity) {
            age[i] += dt
            if (age[i] < 0f) continue
            vx[i] *= drag
            vy[i] = vy[i] * drag + fall
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            if (age[i] > life[i]) spawn(i)
        }
    }

    override fun draw(scope: DrawScope) {
        for (i in 0 until capacity) {
            if (age[i] >= 0f) drawSpark(scope, i)
        }
    }

    private fun drawSpark(scope: DrawScope, i: Int) {
        val k = age[i] / life[i]
        val alpha = (1f - k) * (FLICKER_BASE + FLICKER * sin(age[i] * FLICKER_SPEED + phase[i]))
        if (alpha <= 0f) return
        val head = Offset(x[i], y[i])
        val tail = Offset(x[i] - vx[i] * SPARK_TRAIL, y[i] - vy[i] * SPARK_TRAIL)
        val color = palette[tone[i]]
        scope.drawCircle(color, radius[i] * GLOW_SIZE, head, alpha = alpha * GLOW_ALPHA, blendMode = BlendMode.Plus)
        scope.drawLine(color, tail, head, radius[i], StrokeCap.Round, alpha = alpha, blendMode = BlendMode.Plus)
        scope.drawCircle(hot[tone[i]], radius[i] * CORE_SIZE, head, alpha = alpha)
    }
}

/**
 * 불꽃놀이. 입자 하나가 폭죽으로 올라가 꼭대기에서 불꽃 여러 개로 갈라진다.
 * 불꽃은 빈 칸을 찾아 쓰며, 칸이 모자라면 그만큼 적게 터진다.
 */
internal class FireworkSystem(private val rate: Float, colors: List<Color>) :
    ParticleSystem(((SHELLS * rate).roundToInt().coerceAtLeast(MIN_SHELLS)) * (SHELL_SPARKS + 1), colors) {
    private val kind = IntArray(capacity)
    private val hot = hotColors(palette)
    private var timer = 0f
    private var free = 0

    override fun reset() {
        kind.fill(DEAD)
        timer = FIRST_LAUNCH
    }

    override fun step(dt: Float, autoLaunch: Boolean) {
        if (autoLaunch) {
            timer -= dt
            if (timer <= 0f) {
                rocket()
                timer = rand(LAUNCH_MIN, LAUNCH_MAX) / rate
            }
        }
        val drag = 1f - SHELL_DRAG * dt
        for (i in 0 until capacity) {
            when (kind[i]) {
                ROCKET -> {
                    vy[i] += ROCKET_GRAVITY_DP * unit * dt
                    x[i] += vx[i] * dt
                    y[i] += vy[i] * dt
                    age[i] += dt
                    if (age[i] >= life[i]) {
                        kind[i] = DEAD
                        explode(x[i], y[i], tone[i])
                    }
                }

                LIVE -> {
                    vx[i] *= drag
                    vy[i] = vy[i] * drag + SHELL_GRAVITY_DP * unit * dt
                    x[i] += vx[i] * dt
                    y[i] += vy[i] * dt
                    age[i] += dt
                    if (age[i] >= life[i]) kind[i] = DEAD
                }
            }
        }
    }

    override fun launch(px: Float, py: Float) {
        val cx = if (px.isNaN()) width * rand(BURST_SIDE, 1f - BURST_SIDE) else px
        val cy = if (py.isNaN()) height * rand(BURST_TOP, BURST_BOTTOM) else py
        explode(cx, cy, pickTone())
    }

    private fun rocket() {
        val i = nextFree()
        if (i < 0) return
        val target = height * rand(BURST_TOP, BURST_BOTTOM)
        val gravity = ROCKET_GRAVITY_DP * unit
        kind[i] = ROCKET
        x[i] = width * rand(BURST_SIDE, 1f - BURST_SIDE)
        y[i] = height
        vx[i] = rand(-ROCKET_SIDE_DP, ROCKET_SIDE_DP) * unit
        vy[i] = -sqrt(2f * gravity * (height - target))
        life[i] = -vy[i] / gravity
        age[i] = 0f
        radius[i] = ROCKET_DP * unit
        tone[i] = pickTone()
    }

    private fun explode(cx: Float, cy: Float, shell: Int) {
        // 작은 영역에서는 불꽃이 밖으로 다 빠져나가지 않도록 퍼지는 속도를 줄인다.
        val speed = (minOf(width, height) * SPREAD_RATIO).coerceIn(SPREAD_MIN_DP * unit, SPREAD_MAX_DP * unit)
        for (k in 0 until SHELL_SPARKS) {
            val i = nextFree()
            if (i < 0) return
            val a = k * TWO_PI_F / SHELL_SPARKS + rand(-JITTER, JITTER)
            // 절반은 바깥 고리, 나머지는 안쪽을 채워 꽃처럼 보이게 한다.
            val s = if (k % 2 == 0) speed * rand(RING_MIN, 1f) else speed * rand(INNER_MIN, INNER_MAX)
            kind[i] = LIVE
            x[i] = cx
            y[i] = cy
            vx[i] = cos(a) * s
            vy[i] = sin(a) * s
            life[i] = rand(SHELL_LIFE_MIN, SHELL_LIFE_MAX)
            age[i] = 0f
            radius[i] = rand(SHELL_MIN_DP, SHELL_MAX_DP) * unit
            phase[i] = angle()
            tone[i] = if (random.nextFloat() < ACCENT_CHANCE) palette.lastIndex else shell
        }
    }

    private fun nextFree(): Int {
        repeat(capacity) {
            val i = free
            free = (free + 1) % capacity
            if (kind[i] == DEAD) return i
        }
        return -1
    }

    override fun draw(scope: DrawScope) {
        for (i in 0 until capacity) {
            when (kind[i]) {
                ROCKET -> drawRocket(scope, i)
                LIVE -> drawShell(scope, i)
            }
        }
    }

    private fun drawRocket(scope: DrawScope, i: Int) {
        val tail = Offset(x[i] - vx[i] * ROCKET_TRAIL, y[i] - vy[i] * ROCKET_TRAIL)
        scope.drawLine(hot[tone[i]], tail, Offset(x[i], y[i]), radius[i], StrokeCap.Round, alpha = ROCKET_ALPHA, blendMode = BlendMode.Plus)
    }

    private fun drawShell(scope: DrawScope, i: Int) {
        val head = Offset(x[i], y[i])
        val color = palette[tone[i]]
        val k = age[i] / life[i]
        var alpha = 1f - k * k
        if (k > FLICKER_FROM) alpha *= FLICKER_BASE + FLICKER * sin(age[i] * FLICKER_SPEED + phase[i])
        val tail = Offset(x[i] - vx[i] * SHELL_TRAIL, y[i] - vy[i] * SHELL_TRAIL)
        scope.drawCircle(color, radius[i] * GLOW_SIZE, head, alpha = alpha * GLOW_ALPHA, blendMode = BlendMode.Plus)
        scope.drawLine(color, tail, head, radius[i], StrokeCap.Round, alpha = alpha * TRAIL_ALPHA, blendMode = BlendMode.Plus)
        scope.drawCircle(hot[tone[i]], radius[i] * CORE_SIZE, head, alpha = alpha)
    }
}

/** 불꽃 한가운데의 뜨거운 색. 원래 색을 흰색 쪽으로 당겨 빛나 보이게 한다. */
private fun hotColors(palette: Array<Color>): Array<Color> = Array(palette.size) { lerp(palette[it], Color.White, HOT_MIX) }

private const val DEAD = 0
private const val LIVE = 1
private const val ROCKET = 2
private const val TWO_PI_F = 6.2831855f
private const val SPARK_EDGE = 0.05f
private const val SPARK_START_DP = 4f
private const val SPARK_SIDE_DP = 50f
private const val SPARK_RISE_MIN_DP = 180f
private const val SPARK_RISE_MAX_DP = 400f
private const val SPARK_REF_HEIGHT_DP = 420f
private const val SPARK_LIFE_MIN = 0.9f
private const val SPARK_LIFE_MAX = 1.9f
private const val SPARK_MIN_DP = 1f
private const val SPARK_MAX_DP = 2.4f
private const val SPARK_BURST = 28
private const val SPARK_BURST_MIN_DP = 60f
private const val SPARK_BURST_MAX_DP = 260f
private const val SPARK_BURST_LIFT_DP = 80f
private const val SPARK_BURST_LIFE_MIN = 0.5f
private const val SPARK_BURST_LIFE_MAX = 1.1f
private const val SPARK_DRAG = 0.8f
private const val SPARK_GRAVITY_DP = 260f
private const val SPARK_TRAIL = 0.06f
private const val FLICKER_BASE = 0.7f
private const val FLICKER = 0.3f
private const val FLICKER_SPEED = 38f
private const val FLICKER_FROM = 0.55f
private const val GLOW_SIZE = 1.8f
private const val CORE_SIZE = 0.75f
private const val HOT_MIX = 0.6f
private const val GLOW_ALPHA = 0.2f
private const val TRAIL_ALPHA = 0.55f
private const val SHELLS = 6
private const val MIN_SHELLS = 2
private const val SHELL_SPARKS = 56
private const val FIRST_LAUNCH = 0.2f
private const val LAUNCH_MIN = 0.7f
private const val LAUNCH_MAX = 1.5f
private const val BURST_SIDE = 0.15f
private const val BURST_TOP = 0.15f
private const val BURST_BOTTOM = 0.45f
private const val ROCKET_GRAVITY_DP = 420f
private const val ROCKET_SIDE_DP = 14f
private const val ROCKET_DP = 2.2f
private const val ROCKET_TRAIL = 0.07f
private const val ROCKET_ALPHA = 0.9f
private const val SPREAD_RATIO = 0.42f
private const val SPREAD_MIN_DP = 50f
private const val SPREAD_MAX_DP = 170f
private const val JITTER = 0.08f
private const val RING_MIN = 0.85f
private const val INNER_MIN = 0.25f
private const val INNER_MAX = 0.75f
private const val SHELL_DRAG = 1.3f
private const val SHELL_GRAVITY_DP = 70f
private const val SHELL_LIFE_MIN = 1.1f
private const val SHELL_LIFE_MAX = 1.7f
private const val SHELL_MIN_DP = 1.3f
private const val SHELL_MAX_DP = 2.2f
private const val SHELL_TRAIL = 0.12f
private const val ACCENT_CHANCE = 0.15f
