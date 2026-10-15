package com.naury.kinetica.core.geometry

import kotlin.math.hypot

/**
 * 2차원 점 또는 벡터.
 *
 * 어느 좌표 공간의 값인지는 타입이 아니라 `documentPoint`, `viewportPoint`, `localPoint` 같은 변수 이름으로 드러낸다.
 *
 * @throws IllegalArgumentException 성분이 NaN이거나 무한대인 경우
 */
public data class Vec2(val x: Double, val y: Double) {
    init {
        require(x.isFinite() && y.isFinite()) { "Vec2 성분은 유한해야 합니다." }
    }

    public operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)

    public operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)

    public operator fun times(factor: Double): Vec2 = Vec2(x * factor, y * factor)

    public operator fun unaryMinus(): Vec2 = Vec2(-x, -y)

    public fun length(): Double = hypot(x, y)

    public fun distanceTo(other: Vec2): Double = hypot(x - other.x, y - other.y)

    public companion object {
        public val Zero: Vec2 = Vec2(0.0, 0.0)
    }
}
