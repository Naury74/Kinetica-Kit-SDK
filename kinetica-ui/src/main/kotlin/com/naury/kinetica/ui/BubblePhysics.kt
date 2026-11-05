package com.naury.kinetica.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * [KBubblePicker]의 거품 물리. 거품은 상자 가운데로 끌리고, 겹치면 서로 밀어낸다. 위치를 먼저 옮기고 겹침을 풀어 낸 뒤 옮긴 거리로
 * 속도를 다시 정하므로 거품이 많아도 튀지 않는다. 값은 픽셀이다.
 */
internal class BubblePhysics {
    var count: Int = 0
        private set
    var x: FloatArray = FloatArray(0)
        private set
    var y: FloatArray = FloatArray(0)
        private set
    var radius: FloatArray = FloatArray(0)
        private set
    private var vx = FloatArray(0)
    private var vy = FloatArray(0)
    private var px = FloatArray(0)
    private var py = FloatArray(0)
    private var goal = FloatArray(0)
    private var width = 0f

    /** 고르지 않은 거품의 반지름 */
    var baseRadius: Float = 0f
        private set
    private var height = 0f

    private var keys: List<String> = emptyList()

    /**
     * 거품을 [newKeys] 순서로 맞춘다. 상태는 이름에 묶여 있어 가운데 거품을 지우거나 순서를 바꿔도 남은 거품은 제자리에 있다.
     * 새 거품은 나선을 따라 상자 바깥쪽에 놓여 가운데로 모여든다. 목록이 바뀌었으면 true를 돌려준다.
     */
    fun sync(newKeys: List<String>, baseRadius: Float): Boolean {
        this.baseRadius = baseRadius
        if (newKeys == keys) return false
        val oldIndex = HashMap<String, Int>(keys.size * 2)
        keys.forEachIndexed { i, key -> oldIndex[key] = i }
        val size = newKeys.size
        val nx = FloatArray(size)
        val ny = FloatArray(size)
        val nvx = FloatArray(size)
        val nvy = FloatArray(size)
        val nr = FloatArray(size)
        val ng = FloatArray(size)
        newKeys.forEachIndexed { i, key ->
            val o = oldIndex[key]
            if (o != null) {
                nx[i] = x[o]
                ny[i] = y[o]
                nvx[i] = vx[o]
                nvy[i] = vy[o]
                nr[i] = radius[o]
                ng[i] = goal[o]
            } else {
                val angle = i * GOLDEN_ANGLE
                val distance = baseRadius * (SPAWN_BASE + SPAWN_STEP * sqrt(i.toFloat()))
                nx[i] = width / 2f + cos(angle).toFloat() * distance
                ny[i] = height / 2f + sin(angle).toFloat() * distance
                nr[i] = baseRadius
                ng[i] = baseRadius
            }
        }
        x = nx
        y = ny
        vx = nvx
        vy = nvy
        radius = nr
        goal = ng
        px = FloatArray(size)
        py = FloatArray(size)
        keys = newKeys.toList()
        count = size
        return true
    }

    /** 상자 크기. 처음 정해질 때는 거품을 새 가운데로 옮긴다. */
    fun bounds(newWidth: Float, newHeight: Float) {
        if (width == 0f && height == 0f) {
            for (i in 0 until count) {
                x[i] += newWidth / 2f
                y[i] += newHeight / 2f
            }
        }
        width = newWidth
        height = newHeight
    }

    fun goalOf(index: Int): Float = goal[index]

    fun speedOf(index: Int): Float = sqrt(vx[index] * vx[index] + vy[index] * vy[index])

    fun target(index: Int, value: Float) {
        goal[index] = value
    }

    /** 반지름을 목표로 바로 맞춘다. '애니메이션 삭제'에서 쓴다. */
    fun snapRadii() {
        goal.copyInto(radius, endIndex = count)
    }

    /** [dt]초만큼 움직인다. 다 멈췄으면 false를 돌려준다. */
    fun step(dt: Float): Boolean {
        if (count == 0 || width <= 0f || height <= 0f) return false
        val cx = width / 2f
        val cy = height / 2f
        // 넓은 상자에서는 가로로, 높은 상자에서는 세로로 퍼지도록 끌림을 축마다 다르게 준다.
        val aspect = (width / height).coerceIn(MIN_ASPECT, MAX_ASPECT)
        val damping = exp(-DAMPING * dt)
        val grow = 1f - exp(-GROW_RATE * dt)
        var moving = false
        for (i in 0 until count) {
            px[i] = x[i]
            py[i] = y[i]
            val dr = goal[i] - radius[i]
            if (kotlin.math.abs(dr) > SETTLED_PX) moving = true
            radius[i] += dr * grow
            vx[i] = (vx[i] - PULL * (x[i] - cx) / aspect * dt) * damping
            vy[i] = (vy[i] - PULL * (y[i] - cy) * aspect * dt) * damping
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
        }
        repeat(RELAX_PASSES) { separate() }
        // 겹침을 푼 거리는 dt와 상관없이 정해지므로, 아주 짧은 걸음에서 dt로 나누면 속도가 터무니없이 커진다.
        val inverse = 1f / dt.coerceAtLeast(MIN_VELOCITY_SECONDS)
        for (i in 0 until count) {
            x[i] = x[i].coerceIn(radius[i].coerceAtMost(cx), (width - radius[i]).coerceAtLeast(cx))
            y[i] = y[i].coerceIn(radius[i].coerceAtMost(cy), (height - radius[i]).coerceAtLeast(cy))
            vx[i] = (x[i] - px[i]) * inverse
            vy[i] = (y[i] - py[i]) * inverse
            if (vx[i] * vx[i] + vy[i] * vy[i] > SETTLED_SPEED * SETTLED_SPEED) moving = true
        }
        return moving
    }

    /** 겹친 두 거품을 크기에 반비례해 밀어낸다. 큰 거품은 덜 밀린다. */
    private fun separate() {
        for (i in 0 until count) {
            for (j in i + 1 until count) {
                val dx = x[j] - x[i]
                val dy = y[j] - y[i]
                val min = radius[i] + radius[j] + GAP_PX
                val distanceSq = dx * dx + dy * dy
                if (distanceSq >= min * min) continue
                val distance = sqrt(distanceSq)
                val nx = if (distance > 0f) dx / distance else 1f
                val ny = if (distance > 0f) dy / distance else 0f
                val overlap = (min - distance) * PUSH
                val mi = radius[i] * radius[i]
                val mj = radius[j] * radius[j]
                val share = mj / (mi + mj)
                x[i] -= nx * overlap * share
                y[i] -= ny * overlap * share
                x[j] += nx * overlap * (1f - share)
                y[j] += ny * overlap * (1f - share)
            }
        }
    }

    /** 멈출 때까지 한꺼번에 돌린다. 프레임을 기다리지 않고 정적 배치를 얻을 때 쓴다. */
    fun settle() {
        snapRadii()
        var steps = 0
        while (step(STEP_SECONDS) && steps < MAX_SETTLE_STEPS) steps++
    }
}

private val GOLDEN_ANGLE = PI * (3.0 - sqrt(5.0))
private const val SPAWN_BASE = 1.5f
private const val SPAWN_STEP = 1.2f
private const val PULL = 9f
private const val DAMPING = 5f
private const val GROW_RATE = 14f
private const val PUSH = 0.6f
private const val GAP_PX = 4f
private const val RELAX_PASSES = 3
private const val MIN_ASPECT = 0.5f
private const val MAX_ASPECT = 2f
private const val SETTLED_PX = 0.4f
private const val SETTLED_SPEED = 4f

/** 물리 한 걸음의 길이(초). 프레임이 길어도 이만큼씩 나눠 돌려야 거품이 서로 뚫고 지나가지 않는다. */
internal const val STEP_SECONDS = 1f / 120f

/** 이보다 짧은 걸음은 속도 계산에서 이 길이로 본다. 프레임 루프는 이보다 짧은 나머지를 다음 프레임으로 넘긴다. */
internal const val MIN_VELOCITY_SECONDS = STEP_SECONDS / 2f
private const val MAX_SETTLE_STEPS = 2_400
