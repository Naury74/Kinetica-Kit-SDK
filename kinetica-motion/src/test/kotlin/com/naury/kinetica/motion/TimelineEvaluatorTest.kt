package com.naury.kinetica.motion

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.PropertyTrack
import com.naury.kinetica.core.animation.RotationDirection
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.testing.TestScenes
import com.naury.kinetica.core.testing.TestScenes.id
import kotlin.random.Random
import org.junit.Test

class TimelineEvaluatorTest {
    private fun scalar(time: Long, value: Double, easing: EasingSpec = EasingSpec.Linear) = Keyframe(time, KeyValue.Scalar(value), easing)

    private fun track(property: AnimatableProperty, vararg keys: Keyframe) = PropertyTrack(id("a"), property, keys.toList())

    private fun PropertyTrack.at(time: Double): Double = (TimelineEvaluator.valueAt(this, time) as KeyValue.Scalar).value

    private val moveX = track(
        AnimatableProperty.TranslationX,
        scalar(0, 0.0),
        scalar(500, 100.0, EasingSpec.EaseInOut),
        scalar(1_000, 300.0),
    )

    @Test
    fun `keyframe 시각에서는 그 값을 그대로 돌려준다`() {
        assertThat(moveX.at(0.0)).isEqualTo(0.0)
        assertThat(moveX.at(500.0)).isEqualTo(100.0)
        assertThat(moveX.at(1_000.0)).isEqualTo(300.0)
    }

    @Test
    fun `첫 keyframe 전과 마지막 뒤는 값을 유지한다`() {
        assertThat(moveX.at(-50.0)).isEqualTo(0.0)
        assertThat(moveX.at(1e9)).isEqualTo(300.0)
        assertThat(moveX.at(Double.MAX_VALUE)).isEqualTo(300.0)
        assertThat(moveX.at(Double.NaN)).isEqualTo(0.0)
    }

    @Test
    fun `구간 시작 keyframe의 easing으로 보간한다`() {
        assertThat(moveX.at(250.0)).isWithin(1e-9).of(50.0)
        assertThat(moveX.at(750.0)).isWithin(1e-9).of(200.0)
        assertThat(moveX.at(600.0)).isLessThan(140.0)
    }

    @Test
    fun `임의 순서로 건너뛰어도 같은 시각은 같은 값이다`() {
        val reference = moveX.at(500.0 - 123.4)
        val random = Random(seed = 301)
        repeat(1_000) { moveX.at(random.nextDouble(-100.0, 2_000.0)) }

        assertThat(moveX.at(500.0 - 123.4)).isEqualTo(reference)
    }

    @Test
    fun `회전은 가장 짧은 방향으로 보간한다`() {
        val rotation = track(AnimatableProperty.RotationDegrees, scalar(0, 170.0), scalar(100, -170.0))

        assertThat(rotation.at(50.0)).isWithin(1e-9).of(180.0)
        assertThat(rotation.at(100.0)).isEqualTo(-170.0)
    }

    @Test
    fun `회전 방향을 정하면 그 방향으로 돌고 값 그대로는 여러 바퀴를 돈다`() {
        fun spin(from: Double, to: Double, direction: RotationDirection) =
            track(AnimatableProperty.RotationDegrees, Keyframe(0, KeyValue.Scalar(from), rotation = direction), scalar(100, to))

        assertThat(spin(0.0, 90.0, RotationDirection.CounterClockwise).at(50.0)).isWithin(1e-9).of(-135.0)
        assertThat(spin(0.0, -90.0, RotationDirection.Clockwise).at(50.0)).isWithin(1e-9).of(135.0)
        assertThat(spin(0.0, 720.0, RotationDirection.Direct).at(25.0)).isWithin(1e-9).of(180.0)
        assertThat(spin(0.0, 720.0, RotationDirection.Shortest).at(25.0)).isWithin(1e-9).of(0.0)
        assertThat(spin(10.0, 370.0, RotationDirection.Clockwise).at(50.0)).isWithin(1e-9).of(190.0)
        assertThat(spin(170.0, -170.0, RotationDirection.Shortest).at(50.0)).isWithin(1e-9).of(180.0)
        // 끝 keyframe 시각에는 끝 값을 그대로 돌려준다.
        assertThat(spin(0.0, 720.0, RotationDirection.Clockwise).at(100.0)).isEqualTo(720.0)
    }

    @Test
    fun `회전 방향은 회전이 아닌 속성에서는 무시한다`() {
        val move = track(
            AnimatableProperty.TranslationX,
            Keyframe(0, KeyValue.Scalar(0.0), rotation = RotationDirection.CounterClockwise),
            scalar(100, 300.0),
        )

        assertThat(move.at(50.0)).isWithin(1e-9).of(150.0)
    }

    @Test
    fun `색은 sRGB 채널별로 보간한다`() {
        val fill = PropertyTrack(
            id("a"),
            AnimatableProperty.Fill,
            listOf(Keyframe(0, KeyValue.Color(ArgbColor(0xFF000000.toInt()))), Keyframe(100, KeyValue.Color(ArgbColor(0x00FFFFFF)))),
        )

        val middle = (TimelineEvaluator.valueAt(fill, 50.0) as KeyValue.Color).color

        assertThat(middle.toHex()).isEqualTo("#80808080")
    }

    @Test
    fun `평가한 문서는 원래 문서를 바꾸지 않고 넘치는 opacity를 맞춘다`() {
        val opacity = track(AnimatableProperty.Opacity, scalar(0, 0.0, EasingSpec.Spring(0.2, 400.0)), scalar(1_000, 1.0))
        val timeline = Timeline("t", 1_000, listOf(moveX, opacity))
        val document = TestScenes.sample().copy(timelines = listOf(timeline))

        val samples = (0..1_000 step 10).map { TimelineEvaluator.evaluate(document, timeline, it.toDouble()) }

        assertThat(document).isEqualTo(TestScenes.sample().copy(timelines = listOf(timeline)))
        samples.forEach { assertThat(it.node(id("a"))!!.style.opacity).isIn(com.google.common.collect.Range.closed(0.0, 1.0)) }
        assertThat(samples.last().node(id("a"))!!.transform.translationX).isEqualTo(300.0)
        assertThat(samples.first().node(id("b"))).isEqualTo(document.node(id("b")))
    }

    @Test
    fun `지워진 노드를 가리키는 track은 무시한다`() {
        val ghost = PropertyTrack(id("ghost"), AnimatableProperty.Opacity, listOf(scalar(0, 0.5)))
        val document = TestScenes.sample()

        assertThat(TimelineEvaluator.evaluate(document, Timeline("t", 10, listOf(ghost)), 5.0)).isEqualTo(document)
    }
}
