package com.naury.kinetica.core.scene

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.PropertyTrack
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.animation.TimelineMarker
import com.naury.kinetica.core.testing.TestScenes
import com.naury.kinetica.core.testing.TestScenes.id
import org.junit.Test

class TimelineValidationTest {
    private fun codes(
        vararg tracks: PropertyTrack,
        durationMs: Long = 1_000,
        limits: SceneLimits = SceneLimits.Default,
    ): List<ViolationCode> {
        val doc = TestScenes.sample().copy(timelines = listOf(Timeline("main", durationMs, tracks.toList())))
        return SceneValidator(limits).validate(doc).map { it.code }
    }

    private fun scalar(time: Long, value: Double, easing: EasingSpec = EasingSpec.Linear) = Keyframe(time, KeyValue.Scalar(value), easing)

    private fun track(property: AnimatableProperty, vararg keys: Keyframe, node: String = "a") =
        PropertyTrack(id(node), property, keys.toList())

    @Test
    fun `올바른 track은 문제가 없다`() {
        val opacity = track(AnimatableProperty.Opacity, scalar(0, 0.0), scalar(500, 1.0, EasingSpec.EaseInOut))
        val fill = track(AnimatableProperty.Fill, Keyframe(0, KeyValue.Color(ArgbColor(0xFF000000.toInt()))))

        assertThat(codes(opacity, fill)).isEmpty()
    }

    @Test
    fun `순서가 어긋나거나 같은 시간의 keyframe을 거부한다`() {
        assertThat(
            codes(track(AnimatableProperty.TranslationX, scalar(500, 0.0), scalar(100, 1.0))),
        ).containsExactly(ViolationCode.InvalidKeyframe)
        assertThat(
            codes(track(AnimatableProperty.TranslationX, scalar(100, 0.0), scalar(100, 1.0))),
        ).containsExactly(ViolationCode.InvalidKeyframe)
    }

    @Test
    fun `timeline 범위를 벗어난 keyframe을 거부한다`() {
        assertThat(
            codes(track(AnimatableProperty.TranslationX, scalar(0, 0.0), scalar(1_001, 1.0))),
        ).containsExactly(ViolationCode.InvalidKeyframe)
    }

    @Test
    fun `속성과 다른 값 종류와 범위 밖 값을 거부한다`() {
        val colorOnScalar = track(AnimatableProperty.ScaleX, Keyframe(0, KeyValue.Color(ArgbColor(0))))
        val opacityTooHigh = track(AnimatableProperty.Opacity, scalar(0, 2.0))
        val nan = track(AnimatableProperty.TranslationY, scalar(0, Double.NaN))
        val negativeScale = track(AnimatableProperty.ScaleY, scalar(0, -1.0))

        assertThat(codes(colorOnScalar, opacityTooHigh, nan, negativeScale)).containsExactly(
            ViolationCode.InvalidKeyframe,
            ViolationCode.InvalidKeyframe,
            ViolationCode.InvalidKeyframe,
            ViolationCode.InvalidKeyframe,
        )
    }

    @Test
    fun `잘못된 easing 매개변수를 거부한다`() {
        val invalid = listOf(
            EasingSpec.CubicBezier(-0.1, 0.0, 1.0, 1.0),
            EasingSpec.CubicBezier(0.5, Double.NaN, 0.5, 1.0),
            EasingSpec.Steps(0),
            EasingSpec.Spring(dampingRatio = 0.0, stiffness = 100.0),
            EasingSpec.Spring(dampingRatio = 0.5, stiffness = Double.POSITIVE_INFINITY),
        )
        invalid.forEach { easing ->
            assertThat(
                codes(track(AnimatableProperty.TranslationX, scalar(0, 0.0, easing), scalar(10, 1.0))),
            ).containsExactly(ViolationCode.InvalidEasing)
        }
    }

    @Test
    fun `같은 노드·속성 track 중복과 없는 노드를 거부한다`() {
        val first = track(AnimatableProperty.Opacity, scalar(0, 0.0))
        val second = track(AnimatableProperty.Opacity, scalar(10, 1.0))
        val ghost = track(AnimatableProperty.Opacity, scalar(0, 1.0), node = "ghost")

        assertThat(codes(first, second)).containsExactly(ViolationCode.InvalidTrack)
        assertThat(codes(ghost)).containsExactly(ViolationCode.MissingNode)
    }

    @Test
    fun `keyframe 총수 상한을 넘으면 거부한다`() {
        val keys = (0 until 11).map { scalar(it.toLong(), 0.0) }.toTypedArray()

        assertThat(codes(track(AnimatableProperty.TranslationX, *keys), limits = SceneLimits(maxTotalKeyframes = 10)))
            .containsExactly(ViolationCode.TooManyKeyframes)
    }

    @Test
    fun `길이 상한을 넘는 timeline과 범위 밖 marker를 거부한다`() {
        assertThat(codes(durationMs = 1_000_000_000)).containsExactly(ViolationCode.InvalidTimeline)
        assertThat(codes(durationMs = -1)).containsExactly(ViolationCode.InvalidTimeline)
        val withMarker = TestScenes.sample().copy(timelines = listOf(Timeline("t", 100, markers = listOf(TimelineMarker("end", 200)))))
        assertThat(SceneValidator().validate(withMarker).map { it.code }).containsExactly(ViolationCode.InvalidTimeline)
    }
}
