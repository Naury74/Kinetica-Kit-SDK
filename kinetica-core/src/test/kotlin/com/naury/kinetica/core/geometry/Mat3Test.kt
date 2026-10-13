package com.naury.kinetica.core.geometry

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Assert.assertThrows
import org.junit.Test

class Mat3Test {
    @Test
    fun `world-local 왕복이 1만 번 모두 허용 오차 안에 있다`() {
        val random = Random(seed = 101)
        repeat(10_000) {
            val parent = randomTransform(random).matrix()
            val child = randomTransform(random).matrix()
            val world = parent * child
            val local = Vec2(random.nextDouble(-2_000.0, 2_000.0), random.nextDouble(-2_000.0, 2_000.0))
            val inverse = checkNotNull(world.inverseOrNull())

            val roundTrip = inverse.map(world.map(local))

            assertThat(roundTrip.x).isWithin(TOLERANCE).of(local.x)
            assertThat(roundTrip.y).isWithin(TOLERANCE).of(local.y)
        }
    }

    @Test
    fun `곱셈은 오른쪽 행렬을 먼저 적용한다`() {
        val moveThenScale = Mat3.scale(2.0, 3.0) * Mat3.translation(10.0, 20.0)

        assertThat(moveThenScale.map(Vec2(1.0, 1.0))).isEqualTo(Vec2(22.0, 63.0))
    }

    @Test
    fun `곱셈은 결합 법칙을 따른다`() {
        val random = Random(seed = 7)
        repeat(100) {
            val a = randomTransform(random).matrix()
            val b = randomTransform(random).matrix()
            val c = randomTransform(random).matrix()
            val point = Vec2(random.nextDouble(-500.0, 500.0), random.nextDouble(-500.0, 500.0))

            val left = ((a * b) * c).map(point)
            val right = (a * (b * c)).map(point)

            assertThat(left.distanceTo(right)).isLessThan(TOLERANCE)
        }
    }

    @Test
    fun `양의 회전은 y가 아래인 화면에서 시계 방향이다`() {
        val rotated = Mat3.rotationDegrees(90.0).map(Vec2(1.0, 0.0))

        assertThat(rotated.x).isWithin(1e-12).of(0.0)
        assertThat(rotated.y).isWithin(1e-12).of(1.0)
    }

    @Test
    fun `mapVector는 이동을 무시한다`() {
        val matrix = Mat3.translation(100.0, 100.0) * Mat3.scale(2.0, 2.0)

        assertThat(matrix.mapVector(Vec2(1.0, 1.0))).isEqualTo(Vec2(2.0, 2.0))
    }

    @Test
    fun `특이 행렬은 역행렬이 없다`() {
        assertThat(Mat3.scale(0.0, 1.0).inverseOrNull()).isNull()
        assertThat(Mat3(1.0, 2.0, 2.0, 4.0, 0.0, 0.0).inverseOrNull()).isNull()
    }

    @Test
    fun `반전된 행렬도 역변환된다`() {
        val flipped = Transform2D(translationX = 5.0, scaleX = -1.0, scaleY = 2.0, rotationDegrees = 30.0).matrix()
        val point = Vec2(12.0, -7.0)

        val roundTrip = checkNotNull(flipped.inverseOrNull()).map(flipped.map(point))

        assertThat(roundTrip.distanceTo(point)).isLessThan(TOLERANCE)
    }

    @Test
    fun `유한하지 않은 성분은 거부한다`() {
        assertThrows(IllegalArgumentException::class.java) { Mat3(Double.NaN, 0.0, 0.0, 1.0, 0.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { Vec2(Double.POSITIVE_INFINITY, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { Vec2(0.0, "1e309".toDouble()) }
    }

    @Test
    fun `mapBounds는 회전한 꼭짓점을 모두 감싼다`() {
        val bounds = Mat3.rotationDegrees(45.0).mapBounds(Rect(-1.0, -1.0, 1.0, 1.0))
        val half = Math.sqrt(2.0)

        assertThat(bounds.left).isWithin(1e-12).of(-half)
        assertThat(bounds.right).isWithin(1e-12).of(half)
        assertThat(bounds.top).isWithin(1e-12).of(-half)
        assertThat(bounds.bottom).isWithin(1e-12).of(half)
    }

    private fun randomTransform(random: Random): Transform2D {
        fun scale(): Double = random.nextDouble(0.05, 8.0) * if (random.nextInt(4) == 0) -1.0 else 1.0
        return Transform2D(
            translationX = random.nextDouble(-3_000.0, 3_000.0),
            translationY = random.nextDouble(-3_000.0, 3_000.0),
            scaleX = scale(),
            scaleY = scale(),
            rotationDegrees = random.nextDouble(-720.0, 720.0),
            pivotX = random.nextDouble(-500.0, 500.0),
            pivotY = random.nextDouble(-500.0, 500.0),
        )
    }

    private companion object {
        const val TOLERANCE = 1e-4
    }
}
