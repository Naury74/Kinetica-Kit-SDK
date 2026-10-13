package com.naury.kinetica.motion

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.EasingSpec
import org.junit.Test

class EasingTest {
    private val all = listOf(
        EasingSpec.Linear,
        EasingSpec.EaseIn,
        EasingSpec.EaseOut,
        EasingSpec.EaseInOut,
        EasingSpec.CubicBezier(0.68, -0.6, 0.32, 1.6),
        EasingSpec.Steps(4),
        EasingSpec.Spring(dampingRatio = 0.3, stiffness = 200.0),
        EasingSpec.Spring(dampingRatio = 1.0, stiffness = 200.0),
        EasingSpec.Spring(dampingRatio = 2.5, stiffness = 200.0),
    )

    @Test
    fun `모든 곡선은 0에서 0, 1에서 1이다`() {
        all.forEach { spec ->
            assertThat(Easing.evaluate(spec, 0.0, 500.0)).isEqualTo(0.0)
            assertThat(Easing.evaluate(spec, 1.0, 500.0)).isEqualTo(1.0)
        }
    }

    @Test
    fun `범위 밖과 NaN 진행률은 0과 1 사이로 맞춘다`() {
        assertThat(Easing.evaluate(EasingSpec.EaseIn, -3.0, 100.0)).isEqualTo(0.0)
        assertThat(Easing.evaluate(EasingSpec.EaseIn, 7.0, 100.0)).isEqualTo(1.0)
        assertThat(Easing.evaluate(EasingSpec.EaseIn, Double.NaN, 100.0)).isEqualTo(0.0)
    }

    @Test
    fun `CSS ease 곡선의 알려진 값과 같다`() {
        val ease = EasingSpec.CubicBezier(0.25, 0.1, 0.25, 1.0)

        assertThat(Easing.evaluate(ease, 0.5, 0.0)).isWithin(1e-6).of(0.8024033877)
        assertThat(Easing.evaluate(EasingSpec.EaseInOut, 0.5, 0.0)).isWithin(1e-9).of(0.5)
    }

    @Test
    fun `기울기가 0에 가까운 Bezier도 이분법으로 푼다`() {
        val flat = EasingSpec.CubicBezier(1.0, 0.0, 0.0, 1.0)

        (1..99).forEach { step ->
            val value = Easing.evaluate(flat, step / 100.0, 0.0)
            assertThat(value).isAtLeast(0.0)
            assertThat(value).isAtMost(1.0)
        }
        assertThat(Easing.evaluate(flat, 0.5, 0.0)).isWithin(1e-6).of(0.5)
    }

    @Test
    fun `단조 곡선은 진행률이 늘면 줄지 않는다`() {
        listOf(
            EasingSpec.Linear,
            EasingSpec.EaseIn,
            EasingSpec.EaseOut,
            EasingSpec.EaseInOut,
            EasingSpec.Spring(1.0, 300.0),
        ).forEach { spec ->
            val values = (0..1_000).map { Easing.evaluate(spec, it / 1_000.0, 400.0) }
            values.zipWithNext().forEach { (a, b) -> assertThat(b).isAtLeast(a - 1e-12) }
        }
    }

    @Test
    fun `연속 곡선은 작은 진행률 변화에 값이 크게 뛰지 않는다`() {
        all.filterNot { it is EasingSpec.Steps }.forEach { spec ->
            (0 until 10_000).forEach { step ->
                val a = Easing.evaluate(spec, step / 10_000.0, 400.0)
                val b = Easing.evaluate(spec, (step + 1) / 10_000.0, 400.0)
                if (step + 1 < 10_000) assertThat(kotlin.math.abs(b - a)).isLessThan(0.01)
            }
        }
    }

    @Test
    fun `steps는 구간 끝에서 값을 바꾼다`() {
        val steps = EasingSpec.Steps(4)

        assertThat(Easing.evaluate(steps, 0.24, 0.0)).isEqualTo(0.0)
        assertThat(Easing.evaluate(steps, 0.25, 0.0)).isEqualTo(0.25)
        assertThat(Easing.evaluate(steps, 0.99, 0.0)).isEqualTo(0.75)
        assertThat(Easing.evaluate(EasingSpec.Steps(1), 0.99, 0.0)).isEqualTo(0.0)
    }

    @Test
    fun `약한 감쇠 용수철은 넘쳤다 돌아오고 임계 감쇠는 넘치지 않는다`() {
        val bouncy = (1..999).map { Easing.evaluate(EasingSpec.Spring(0.3, 200.0), it / 1_000.0, 1_000.0) }
        val critical = (1..999).map { Easing.evaluate(EasingSpec.Spring(1.0, 200.0), it / 1_000.0, 1_000.0) }

        assertThat(bouncy.max()).isGreaterThan(1.0)
        assertThat(critical.max()).isAtMost(1.0)
        assertThat(critical.last()).isWithin(1e-3).of(1.0)
    }

    @Test
    fun `용수철은 같은 입력에 항상 같은 값이다`() {
        val spec = EasingSpec.Spring(0.45, 320.0)

        val first = (0..100).map { Easing.evaluate(spec, it / 100.0, 750.0) }
        val second = (0..100).reversed().map { Easing.evaluate(spec, it / 100.0, 750.0) }.reversed()

        assertThat(second).isEqualTo(first)
    }
}
