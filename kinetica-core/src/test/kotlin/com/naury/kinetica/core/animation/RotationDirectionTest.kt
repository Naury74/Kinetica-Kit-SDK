package com.naury.kinetica.core.animation

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.shortestDeltaDegrees
import kotlin.random.Random
import org.junit.Test

class RotationDirectionTest {
    @Test
    fun `가장 짧은 방향은 기존 계산과 같다`() {
        val random = Random(seed = 7)
        repeat(500) {
            val from = random.nextDouble(-1_000.0, 1_000.0)
            val to = random.nextDouble(-1_000.0, 1_000.0)
            assertThat(RotationDirection.Shortest.deltaDegrees(from, to)).isWithin(1e-9).of(shortestDeltaDegrees(from, to))
        }
    }

    @Test
    fun `시계와 반시계는 방향을 지키고 같은 값이면 돌지 않는다`() {
        assertThat(RotationDirection.Clockwise.deltaDegrees(0.0, -90.0)).isEqualTo(270.0)
        assertThat(RotationDirection.CounterClockwise.deltaDegrees(0.0, 90.0)).isEqualTo(-270.0)
        assertThat(RotationDirection.Clockwise.deltaDegrees(30.0, 30.0)).isEqualTo(0.0)
        assertThat(RotationDirection.CounterClockwise.deltaDegrees(30.0, 30.0)).isEqualTo(0.0)
        assertThat(RotationDirection.Clockwise.deltaDegrees(0.0, 360.0)).isEqualTo(360.0)
        assertThat(RotationDirection.CounterClockwise.deltaDegrees(0.0, 360.0)).isEqualTo(-360.0)
    }

    @Test
    fun `값 그대로는 여러 바퀴를 돌고 이름으로 찾을 수 있다`() {
        assertThat(RotationDirection.Direct.deltaDegrees(0.0, 720.0)).isEqualTo(720.0)
        assertThat(RotationDirection.Direct.deltaDegrees(90.0, -270.0)).isEqualTo(-360.0)
        assertThat(RotationDirection.fromWireName("counterClockwise")).isEqualTo(RotationDirection.CounterClockwise)
        assertThat(RotationDirection.fromWireName("left")).isNull()
        assertThat(Keyframe(0, KeyValue.Scalar(0.0)).rotation).isEqualTo(RotationDirection.Shortest)
    }
}
