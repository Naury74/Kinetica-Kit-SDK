package com.naury.kinetica.core.geometry

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class Transform2DTest {
    @Test
    fun `pivot은 회전과 확대에도 제자리에 남는다`() {
        val transform = Transform2D(scaleX = 3.0, scaleY = 0.5, rotationDegrees = 37.0, pivotX = 40.0, pivotY = 10.0)

        val mappedPivot = transform.matrix().map(transform.pivot)

        assertThat(mappedPivot.distanceTo(transform.pivot)).isLessThan(1e-9)
    }

    @Test
    fun `이동은 pivot 위치에 더해진다`() {
        val transform = Transform2D(translationX = 100.0, translationY = 50.0, rotationDegrees = 90.0, pivotX = 10.0, pivotY = 10.0)

        val mapped = transform.matrix().map(Vec2(20.0, 10.0))

        assertThat(mapped.x).isWithin(1e-9).of(110.0)
        assertThat(mapped.y).isWithin(1e-9).of(70.0)
    }

    @Test
    fun `중심이 아닌 pivot과 비등방 배율에서 회전한 점을 계산한다`() {
        val transform = Transform2D(scaleX = 2.0, scaleY = 1.0, rotationDegrees = 90.0, pivotX = 0.0, pivotY = 0.0)

        val mapped = transform.matrix().map(Vec2(10.0, 5.0))

        assertThat(mapped.x).isWithin(1e-9).of(-5.0)
        assertThat(mapped.y).isWithin(1e-9).of(20.0)
    }

    @Test
    fun `배율이 1e-6 이하이면 퇴화한 변환이다`() {
        assertThat(Transform2D(scaleX = 1e-6).isDegenerate).isTrue()
        assertThat(Transform2D(scaleY = -1e-7).isDegenerate).isTrue()
        assertThat(Transform2D(scaleX = 1e-3).isDegenerate).isFalse()
    }

    @Test
    fun `음수 배율은 반전으로 표시된다`() {
        assertThat(Transform2D(scaleX = -1.0).isFlipped).isTrue()
        assertThat(Transform2D().isFlipped).isFalse()
    }

    @Test
    fun `유한하지 않은 값은 거부한다`() {
        assertThrows(IllegalArgumentException::class.java) { Transform2D(rotationDegrees = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { Transform2D(pivotX = Double.NEGATIVE_INFINITY) }
    }

    @Test
    fun `각도 정규화는 -180 초과 180 이하로 맞춘다`() {
        assertThat(normalizeDegrees(180.0)).isEqualTo(180.0)
        assertThat(normalizeDegrees(-180.0)).isEqualTo(180.0)
        assertThat(normalizeDegrees(540.0)).isEqualTo(180.0)
        assertThat(normalizeDegrees(-190.0)).isEqualTo(170.0)
        assertThat(shortestDeltaDegrees(170.0, -170.0)).isEqualTo(20.0)
        assertThat(shortestDeltaDegrees(-170.0, 170.0)).isEqualTo(-20.0)
    }
}
