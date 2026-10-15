package com.naury.kinetica.motion

import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.StepJump
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * [EasingSpec]을 계산한다. 상태가 없으며 입력이 같으면 항상 같은 값을 돌려준다.
 *
 * 모든 곡선은 `progress = 0`에서 0, `progress = 1`에서 1이다. 베지어의 y 제어점이나 스프링 때문에 중간 값은 0..1을 벗어날 수 있다.
 */
public object Easing {
    private const val NEWTON_ITERATIONS = 8
    private const val NEWTON_MIN_SLOPE = 1e-6
    private const val BISECTION_ITERATIONS = 60
    private const val SOLVE_TOLERANCE = 1e-9
    private const val CRITICAL_DAMPING_TOLERANCE = 1e-9
    private const val MILLIS_PER_SECOND = 1_000.0

    /** 3차 번스타인 기저의 계수. */
    private const val CUBIC = 3.0

    /**
     * @param progress 구간 진행률. 0..1 밖은 0..1로 맞춘다.
     * @param segmentDurationMs 구간 길이(ms). 스프링에만 쓴다.
     */
    public fun evaluate(spec: EasingSpec, progress: Double, segmentDurationMs: Double): Double {
        val u = if (progress.isNaN()) 0.0 else progress.coerceIn(0.0, 1.0)
        if (u == 0.0 || u == 1.0) return u
        return when (spec) {
            EasingSpec.Linear -> u
            is EasingSpec.CubicBezier -> cubicBezier(spec, u)
            is EasingSpec.Steps -> steps(spec, u)
            is EasingSpec.Spring -> spring(spec, u * segmentDurationMs / MILLIS_PER_SECOND)
        }
    }

    /**
     * CSS `steps()` 계산과 같다. jump-start와 jump-both는 칸 번호를 하나 올리고, 오르는 횟수는 jump-none이 `n-1`, jump-both가 `n+1`이다.
     * 검증을 거치지 않아 오르는 횟수가 1보다 작으면 선형으로 계산한다.
     */
    private fun steps(spec: EasingSpec.Steps, u: Double): Double {
        val jumps = when (spec.jump) {
            StepJump.Start, StepJump.End -> spec.count
            StepJump.None -> spec.count - 1
            StepJump.Both -> spec.count + 1
        }
        if (jumps < 1) return u
        val raised = spec.jump == StepJump.Start || spec.jump == StepJump.Both
        val step = floor(u * spec.count).toInt() + if (raised) 1 else 0
        return step.coerceAtMost(jumps).toDouble() / jumps
    }

    private fun cubicBezier(spec: EasingSpec.CubicBezier, x: Double): Double {
        val t = solveCurveX(spec.x1, spec.x2, x)
        return bezier(spec.y1, spec.y2, t)
    }

    /** 단조 증가하는 x(t) = x에서 t를 구한다. 뉴턴 법이 기울기 0 근처에서 멈추거나 범위를 벗어나면 이분법으로 푼다. */
    private fun solveCurveX(x1: Double, x2: Double, x: Double): Double {
        var t = x
        var iteration = 0
        while (iteration < NEWTON_ITERATIONS && t in 0.0..1.0) {
            val error = bezier(x1, x2, t) - x
            val slope = bezierDerivative(x1, x2, t)
            if (abs(error) < SOLVE_TOLERANCE || abs(slope) < NEWTON_MIN_SLOPE) break
            t -= error / slope
            iteration++
        }
        val converged = t in 0.0..1.0 && abs(bezier(x1, x2, t) - x) < SOLVE_TOLERANCE
        return if (converged) t else bisect(x1, x2, x)
    }

    private fun bisect(x1: Double, x2: Double, x: Double): Double {
        var low = 0.0
        var high = 1.0
        var t = x
        repeat(BISECTION_ITERATIONS) {
            t = (low + high) / 2.0
            val value = bezier(x1, x2, t)
            if (abs(value - x) < SOLVE_TOLERANCE) return t
            if (value < x) low = t else high = t
        }
        return t
    }

    /** 끝점이 (0, 0), (1, 1)인 3차 베지어 곡선의 한 축 값. */
    private fun bezier(p1: Double, p2: Double, t: Double): Double {
        val inverse = 1.0 - t
        return CUBIC * inverse * inverse * t * p1 + CUBIC * inverse * t * t * p2 + t * t * t
    }

    private fun bezierDerivative(p1: Double, p2: Double, t: Double): Double {
        val inverse = 1.0 - t
        return CUBIC * inverse * inverse * p1 + 2.0 * CUBIC * inverse * t * (p2 - p1) + CUBIC * t * t * (1.0 - p2)
    }

    /** 질량 1, 초기 속도 0인 감쇠 스프링이 0에서 1로 가는 계단 응답. [seconds]는 구간 시작부터 지난 시간이다. */
    private fun spring(spec: EasingSpec.Spring, seconds: Double): Double {
        val omega = sqrt(spec.stiffness)
        val zeta = spec.dampingRatio
        return when {
            abs(zeta - 1.0) < CRITICAL_DAMPING_TOLERANCE -> 1.0 - exp(-omega * seconds) * (1.0 + omega * seconds)

            zeta < 1.0 -> {
                val damped = omega * sqrt(1.0 - zeta * zeta)
                val envelope = exp(-zeta * omega * seconds)
                1.0 - envelope * (cos(damped * seconds) + zeta * omega / damped * sin(damped * seconds))
            }

            else -> {
                val root = sqrt(zeta * zeta - 1.0)
                val r1 = -omega * (zeta - root)
                val r2 = -omega * (zeta + root)
                1.0 + (r2 * exp(r1 * seconds) - r1 * exp(r2 * seconds)) / (r1 - r2)
            }
        }
    }
}
