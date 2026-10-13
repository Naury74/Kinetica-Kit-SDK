package com.naury.kinetica.core.animation

/**
 * 구간 진행률 `u`(0..1)를 보간 비율로 바꾸는 곡선의 정의. 계산은 `kinetica-motion`이 맡는다.
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
     * 구간을 [count]개의 같은 단계로 나누고 각 단계 끝에서 값을 바꾼다(CSS `steps(n, jump-end)`).
     * `count = 1`이면 구간 내내 시작 값을 유지한다.
     */
    public data class Steps(val count: Int) : EasingSpec

    /**
     * 질량 1인 감쇠 용수철의 계단 응답. 구간 길이(초)를 시간으로 쓰므로 같은 입력이면 항상 같은 값이다.
     * 구간 끝에서 다 멈추지 않았다면 끝 keyframe 시각에 끝 값으로 맞춘다.
     *
     * @property dampingRatio 0보다 크다. 1이면 임계 감쇠, 1보다 작으면 튕긴다.
     * @property stiffness 0보다 크다. 고유 진동수는 `sqrt(stiffness)` rad/s다.
     */
    public data class Spring(val dampingRatio: Double, val stiffness: Double) : EasingSpec

    public companion object {
        public val EaseIn: CubicBezier = CubicBezier(0.42, 0.0, 1.0, 1.0)
        public val EaseOut: CubicBezier = CubicBezier(0.0, 0.0, 0.58, 1.0)
        public val EaseInOut: CubicBezier = CubicBezier(0.42, 0.0, 0.58, 1.0)
    }
}
