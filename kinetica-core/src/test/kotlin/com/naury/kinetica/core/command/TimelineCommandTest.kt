package com.naury.kinetica.core.command

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.testing.TestScenes
import com.naury.kinetica.core.testing.TestScenes.id
import org.junit.Test

class TimelineCommandTest {
    private val reducer = SceneReducer()
    private val property = AnimatableProperty.TranslationX
    private val base = TestScenes.sample().copy(timelines = listOf(Timeline("main", durationMs = 1_000)))

    private fun SceneDocument.after(command: SceneCommand): SceneDocument = (reducer.apply(this, command) as CommandResult.Applied).document

    private fun key(time: Long, value: Double) = Keyframe(time, KeyValue.Scalar(value))

    private fun set(time: Long, value: Double) = SceneCommand.SetKeyframe("main", id("a"), property, key(time, value))

    private fun SceneDocument.times(): List<Long> = timelines.single().track(id("a"), property)?.keyframes?.map { it.timeMs }.orEmpty()

    @Test
    fun `keyframe을 시간 순서로 넣고 같은 시간은 바꾼다`() {
        val document = base.after(set(500, 1.0)).after(set(100, 2.0)).after(set(500, 3.0))

        assertThat(document.times()).containsExactly(100L, 500L).inOrder()
        assertThat(document.timelines.single().track(id("a"), property)!!.keyframes.last().value).isEqualTo(KeyValue.Scalar(3.0))
    }

    @Test
    fun `keyframe을 옮기면 순서를 다시 맞추고 겹치면 거부한다`() {
        val document = base.after(set(100, 1.0)).after(set(500, 2.0))

        assertThat(
            document.after(SceneCommand.MoveKeyframe("main", id("a"), property, 100, 800)).times(),
        ).containsExactly(500L, 800L).inOrder()
        assertThat(reducer.apply(document, SceneCommand.MoveKeyframe("main", id("a"), property, 100, 500)))
            .isEqualTo(CommandResult.Rejected(CommandError.KeyframeCollision(id("a"), property, 500)))
    }

    @Test
    fun `timeline 밖으로 옮기면 검증에서 거부한다`() {
        val document = base.after(set(100, 1.0))

        val result = reducer.apply(document, SceneCommand.MoveKeyframe("main", id("a"), property, 100, 2_000))

        assertThat((result as CommandResult.Rejected).error).isInstanceOf(CommandError.InvalidResult::class.java)
    }

    @Test
    fun `마지막 keyframe을 지우면 track도 지운다`() {
        val document = base.after(set(100, 1.0)).after(set(200, 1.0))

        val partly = document.after(SceneCommand.DeleteKeyframes("main", id("a"), property, setOf(100)))
        val fully = partly.after(SceneCommand.DeleteKeyframes("main", id("a"), property, setOf(200)))

        assertThat(partly.times()).containsExactly(200L)
        assertThat(fully.timelines.single().tracks).isEmpty()
    }

    @Test
    fun `없는 timeline·track·keyframe은 거부한다`() {
        val document = base.after(set(100, 1.0))

        assertThat(reducer.apply(document, SceneCommand.SetKeyframe("nope", id("a"), property, key(0, 0.0))))
            .isEqualTo(CommandResult.Rejected(CommandError.TimelineNotFound("nope")))
        assertThat(reducer.apply(document, SceneCommand.DeleteKeyframes("main", id("b"), property, setOf(0))))
            .isEqualTo(CommandResult.Rejected(CommandError.TrackNotFound(id("b"), property)))
        assertThat(reducer.apply(document, SceneCommand.SetKeyframeEasing("main", id("a"), property, 999, EasingSpec.EaseIn)))
            .isEqualTo(CommandResult.Rejected(CommandError.KeyframeNotFound(id("a"), property, 999)))
    }

    @Test
    fun `keyframe easing을 바꾼다`() {
        val document = base.after(set(100, 1.0)).after(SceneCommand.SetKeyframeEasing("main", id("a"), property, 100, EasingSpec.EaseOut))

        assertThat(document.timelines.single().track(id("a"), property)!!.keyframes.single().easing).isEqualTo(EasingSpec.EaseOut)
    }

    @Test
    fun `timeline을 더하고 바꾸고 지운다`() {
        val added = base.after(SceneCommand.PutTimeline(Timeline("intro", 300)))
        val replaced = added.after(SceneCommand.PutTimeline(Timeline("intro", 600)))
        val removed = replaced.after(SceneCommand.RemoveTimeline("intro"))

        assertThat(added.timelines.map { it.id }).containsExactly("main", "intro").inOrder()
        assertThat(replaced.timelines.last().durationMs).isEqualTo(600)
        assertThat(removed).isEqualTo(base)
    }
}
