package com.naury.kinetica.editor

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.id
import com.naury.kinetica.core.testing.TestScenes.rect
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import com.naury.kinetica.motion.TimelineEvaluator
import org.junit.Test

class AutoKeyTest {
    private val scene = document(listOf(rect("a", transform = Transform2D(translationX = 10.0, translationY = 20.0))))
    private val session = (KineticaSession.create(scene) as SessionOpenResult.Opened).session
    private val state = EditorState(session).apply { motionEnabled = true }

    private fun keys(property: AnimatableProperty) =
        session.document.value.timelines.single().track(id("a"), property)?.keyframes?.map { it.timeMs to it.value }.orEmpty()

    @Test
    fun `키를 넣고 다른 시각에서 옮기면 그 시각에 키가 생기고 기본값은 그대로다`() {
        state.addKey(id("a"), AnimatableProperty.TranslationX)
        state.playheadMs = 1_000.0

        state.execute(SceneCommand.TransformNodes(mapOf(id("a") to Transform2D(translationX = 300.0, translationY = 50.0))))

        assertThat(
            keys(AnimatableProperty.TranslationX),
        ).containsExactly(0L to KeyValue.Scalar(10.0), 1_000L to KeyValue.Scalar(300.0)).inOrder()
        val base = session.document.value.node(id("a"))!!
        assertThat(base.transform.translationX).isEqualTo(10.0)
        assertThat(base.transform.translationY).isEqualTo(50.0)
        val timeline = session.document.value.timelines.single()
        assertThat(
            TimelineEvaluator.evaluate(session.document.value, timeline, 500.0).node(id("a"))!!.transform.translationX,
        ).isEqualTo(155.0)
    }

    @Test
    fun `여러 명령으로 나뉜 편집도 실행 취소 한 단계다`() {
        state.addKey(id("a"), AnimatableProperty.TranslationX)
        state.playheadMs = 1_000.0
        val beforeEdit = session.document.value

        state.execute(SceneCommand.TransformNodes(mapOf(id("a") to Transform2D(translationX = 300.0, translationY = 50.0))))
        session.history.undo()

        assertThat(session.document.value).isEqualTo(beforeEdit)
    }

    @Test
    fun `이미 있는 키를 고치면 easing을 유지한다`() {
        state.addKey(id("a"), AnimatableProperty.Opacity)
        session.commands.execute(
            SceneCommand.SetKeyframeEasing(EditorState.MAIN_TIMELINE, id("a"), AnimatableProperty.Opacity, 0, EasingSpec.EaseIn),
        )
        val shown = state.document.node(id("a"))!!

        state.execute(SceneCommand.UpdateProperties(shown.copy(style = NodeStyle(fill = shown.style.fill, opacity = 0.25))))

        val key = session.document.value.timelines.single().track(id("a"), AnimatableProperty.Opacity)!!.keyframes.single()
        assertThat(key.value).isEqualTo(KeyValue.Scalar(0.25))
        assertThat(key.easing).isEqualTo(EasingSpec.EaseIn)
        assertThat(session.document.value.node(id("a"))!!.style.opacity).isEqualTo(1.0)
    }

    @Test
    fun `모션 편집이 꺼져 있으면 기본 문서를 고친다`() {
        state.addKey(id("a"), AnimatableProperty.TranslationX)
        state.motionEnabled = false

        state.execute(SceneCommand.TransformNodes(mapOf(id("a") to Transform2D(translationX = 77.0))))

        assertThat(session.document.value.node(id("a"))!!.transform.translationX).isEqualTo(77.0)
        assertThat(keys(AnimatableProperty.TranslationX)).hasSize(1)
    }
}
