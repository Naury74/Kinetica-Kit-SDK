package com.naury.kinetica.core.animation

/**
 * 구간 진행률 `u`(0..1)를 보간 비율로 바꾸는 easing 곡선의 정의. 실제 계산은 `kinetica-motion`이 맡는다.
 */
public sealed interface EasingSpec {
    public data object Linear : EasingSpec

    /**
     * CSS `cubic-bezier`와 같은 곡선. 시작점 `(0, 0)`과 끝점 `(1, 1)`은 고정이다.
     *
     * @property x1 0..1
     * @property x2 0..1
     */
    public data class CubicBezier(val x1: Double, val y1: Double, val x2: Double, val y2: Double) : EasingSpec

    /**
     * 구간을 [count]개의 같은 칸으로 나누고 [jump]가 정한 시점에 값을 바꾼다. CSS의 `steps(n, jump)`와 같다.
     * 기본값 [StepJump.End]에서 `count = 1`이면 구간 내내 시작 값을 유지한다.
     * 구간의 양 끝(진행률 0과 1)은 점프 방식과 상관없이 0과 1이다.
     *
     * @property count 칸 수. 1..10000이며, [StepJump.None]이면 2 이상이어야 한다. 벗어나면 timeline 검증에서 거부한다.
     */
    public data class Steps(val count: Int, val jump: StepJump = StepJump.End) : EasingSpec

    /**
     * 질량이 1인 감쇠 스프링의 계단 응답. 구간 길이(초)를 시간으로 쓰므로 입력이 같으면 항상 같은 값이 나온다.
     * 구간이 끝날 때까지 스프링이 멈추지 않았다면 끝 keyframe 시각에 끝 값으로 맞춘다.
     *
     * @property dampingRatio 감쇠비. 0보다 커야 하며, 1이면 임계 감쇠이고 1보다 작으면 튕긴다.
     * @property stiffness 강성. 0보다 커야 하며, 고유 진동수는 `sqrt(stiffness)` rad/s다.
     */
    public data class Spring(val dampingRatio: Double, val stiffness: Double) : EasingSpec

    public companion object {
        public val EaseIn: CubicBezier = CubicBezier(0.42, 0.0, 1.0, 1.0)
        public val EaseOut: CubicBezier = CubicBezier(0.0, 0.0, 0.58, 1.0)
        public val EaseInOut: CubicBezier = CubicBezier(0.42, 0.0, 0.58, 1.0)
    }
}
