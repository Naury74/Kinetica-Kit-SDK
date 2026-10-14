package com.naury.kinetica.motion

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.PropertyTrack
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.SceneValidator
import com.naury.kinetica.core.scene.Shadow
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.id
import org.junit.Test

class StyleAnimationTest {
    private val card = SceneNode(
        id("card"),
        NodeContent.Rectangle(Size(100.0, 50.0), cornerRadius = 0.0),
        style = NodeStyle(
            stroke = ArgbColor(0xFF000000.toInt()),
            shadows = listOf(Shadow(ArgbColor(0x33000000), offsetY = 0.0, blur = 0.0)),
        ),
    )

    private fun track(property: AnimatableProperty, from: KeyValue, to: KeyValue) =
        PropertyTrack(id("card"), property, listOf(Keyframe(0, from), Keyframe(1_000, to)))

    @Test
    fun `크기·모서리·선 색·흐림·그림자를 보간한다`() {
        val timeline = Timeline(
            "main",
            1_000,
            listOf(
                track(AnimatableProperty.Width, KeyValue.Scalar(100.0), KeyValue.Scalar(300.0)),
                track(AnimatableProperty.CornerRadius, KeyValue.Scalar(0.0), KeyValue.Scalar(20.0)),
                track(
                    AnimatableProperty.Stroke,
                    KeyValue.Color(ArgbColor(0xFF000000.toInt())),
                    KeyValue.Color(ArgbColor(0xFFFFFFFF.toInt())),
                ),
                track(AnimatableProperty.Blur, KeyValue.Scalar(0.0), KeyValue.Scalar(8.0)),
                track(AnimatableProperty.ShadowBlur, KeyValue.Scalar(0.0), KeyValue.Scalar(30.0)),
                track(AnimatableProperty.ShadowOffsetY, KeyValue.Scalar(0.0), KeyValue.Scalar(10.0)),
            ),
        )
        val scene = document(listOf(card), timelines = listOf(timeline))
        assertThat(SceneValidator().validate(scene)).isEmpty()

        val half = TimelineEvaluator.evaluate(scene, timeline, 500.0).node(id("card"))!!

        val content = half.content as NodeContent.Rectangle
        assertThat(content.size.width).isWithin(1e-9).of(200.0)
        assertThat(content.cornerRadius).isWithin(1e-9).of(10.0)
        assertThat(half.style.stroke!!.red).isIn(126..129)
        assertThat(half.style.blur).isWithin(1e-9).of(4.0)
        assertThat(half.style.shadows.single().blur).isWithin(1e-9).of(15.0)
        assertThat(half.style.shadows.single().offsetY).isWithin(1e-9).of(5.0)
    }

    @Test
    fun `너비는 0 이하로 둘 수 없다`() {
        val timeline = Timeline("main", 1_000, listOf(track(AnimatableProperty.Width, KeyValue.Scalar(0.0), KeyValue.Scalar(10.0))))

        assertThat(SceneValidator().validate(document(listOf(card), timelines = listOf(timeline)))).isNotEmpty()
    }

    @Test
    fun `그라디언트 시작·끝 색과 각도를 보간한다`() {
        val red = ArgbColor(0xFFFF0000.toInt())
        val blue = ArgbColor(0xFF0000FF.toInt())
        val shaded = card.copy(
            style = NodeStyle(
                gradient = com.naury.kinetica.core.scene.Gradient(
                    com.naury.kinetica.core.scene.GradientType.Linear,
                    listOf(com.naury.kinetica.core.scene.GradientStop(0.0, red), com.naury.kinetica.core.scene.GradientStop(1.0, blue)),
                    0.0,
                ),
            ),
        )
        val timeline = Timeline(
            "main",
            1_000,
            listOf(
                track(AnimatableProperty.GradientStart, KeyValue.Color(red), KeyValue.Color(blue)),
                track(AnimatableProperty.GradientEnd, KeyValue.Color(blue), KeyValue.Color(red)),
                track(AnimatableProperty.GradientAngle, KeyValue.Scalar(0.0), KeyValue.Scalar(90.0)),
            ),
        )
        val scene = document(listOf(shaded), timelines = listOf(timeline))

        val gradient = TimelineEvaluator.evaluate(scene, timeline, 500.0).node(id("card"))!!.style.gradient!!

        assertThat(gradient.stops.first().color.red).isIn(126..129)
        assertThat(gradient.stops.last().color.blue).isIn(126..129)
        assertThat(gradient.angleDegrees).isWithin(1e-9).of(45.0)
        // 그라디언트가 없는 노드에는 쓰지 않는다.
        val plain = TimelineEvaluator.evaluate(document(listOf(card), timelines = listOf(timeline)), timeline, 500.0)
        assertThat(plain.node(id("card"))!!.style.gradient).isNull()
    }
}
