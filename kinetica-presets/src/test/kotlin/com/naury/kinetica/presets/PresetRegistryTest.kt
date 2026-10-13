package com.naury.kinetica.presets

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.command.SceneReducer
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneValidator
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.id
import com.naury.kinetica.core.testing.TestScenes.rect
import com.naury.kinetica.motion.TimelineEvaluator
import org.junit.Test

class PresetRegistryTest {
    private val scene = document(
        listOf("a", "b", "c").mapIndexed { i, name ->
            rect(name, transform = Transform2D(translationX = 100.0 * i, translationY = 300.0, pivotX = 50.0, pivotY = 25.0))
        },
    )
    private val registry = PresetRegistry.Default

    private fun applied(id: String, params: PresetParams = PresetParams(), document: SceneDocument = scene): SceneDocument {
        val result = registry.apply(id, document, listOf(id("a"), id("b"), id("c")), params) as PresetResult.Commands
        return result.commands.fold(document) { current, command ->
            (SceneReducer().apply(current, command) as CommandResult.Applied).document
        }
    }

    @Test
    fun `모든 기본 프리셋은 올바른 문서를 만들고 끝 장면은 원래 모습이다`() {
        registry.ids.filterNot { it == "parallax" }.forEach { preset ->
            val document = applied(preset)
            val timeline = document.timelines.single()

            assertThat(SceneValidator().validate(document)).isEmpty()
            val end = TimelineEvaluator.evaluate(document, timeline, timeline.durationMs.toDouble())
            listOf("a", "b", "c").forEach { assertThat(end.node(id(it))).isEqualTo(document.node(id(it))) }
        }
    }

    @Test
    fun `같은 입력이면 같은 명령을 만든다`() {
        assertThat(registry.apply("stagger.entrance", scene, listOf(id("a"), id("b"))))
            .isEqualTo(registry.apply("stagger.entrance", scene, listOf(id("a"), id("b"))))
    }

    @Test
    fun `순차 등장은 고른 순서대로 늦게 시작한다`() {
        val document = applied("stagger.entrance", PresetParams(startMs = 100, durationMs = 400))
        val timeline = document.timelines.single()

        listOf("a" to 100L, "b" to 180L, "c" to 260L).forEach { (name, start) ->
            assertThat(timeline.track(id(name), AnimatableProperty.Opacity)!!.keyframes.map { it.timeMs }).containsExactly(
                start,
                start + 400,
            ).inOrder()
        }
        assertThat(timeline.durationMs).isEqualTo(660)
    }

    @Test
    fun `있던 timeline은 짧을 때만 늘리고 다른 track은 그대로 둔다`() {
        val withTimeline = applied("fade.in", PresetParams(durationMs = 2_000))
        val again = applied("slide.in.left", PresetParams(durationMs = 300), withTimeline)

        assertThat(again.timelines.single().durationMs).isEqualTo(2_000)
        assertThat(again.timelines.single().track(id("a"), AnimatableProperty.Opacity)).isNotNull()
        assertThat(again.timelines.single().track(id("a"), AnimatableProperty.TranslationX)).isNotNull()
    }

    @Test
    fun `프리셋이 만든 키는 일반 명령으로 고칠 수 있다`() {
        val document = applied("fade.in")

        val moved = SceneReducer().apply(document, SceneCommand.MoveKeyframe("main", id("a"), AnimatableProperty.Opacity, 450, 300))

        assertThat(moved).isInstanceOf(CommandResult.Applied::class.java)
    }

    @Test
    fun `없는 프리셋과 대상이 없으면 알린다`() {
        assertThat(registry.apply("nope", scene, listOf(id("a")))).isEqualTo(PresetResult.UnknownPreset("nope"))
        assertThat(registry.apply("fade.in", scene, listOf(id("ghost")))).isEqualTo(PresetResult.NoTargets)
        assertThat(scene.timelines).isEqualTo(emptyList<Timeline>())
    }
}
