package com.naury.kinetica.editor

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.RotationDirection
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.id
import com.naury.kinetica.core.testing.TestScenes.rect
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import org.junit.Test

class KeyframeEditsTest {
    private val session = (KineticaSession.create(document(listOf(rect("a"), rect("b")))) as SessionOpenResult.Opened).session
    private val state = EditorState(session).apply { motionEnabled = true }
    private val edits = KeyframeEdits(state)

    private val x = AnimatableProperty.TranslationX
    private val y = AnimatableProperty.TranslationY
    private val rotation = AnimatableProperty.RotationDegrees

    private fun put(node: String, property: AnimatableProperty, vararg times: Long) {
        if (session.document.value.timelines.isEmpty()) {
            session.commands.execute(SceneCommand.PutTimeline(Timeline(EditorState.MAIN_TIMELINE, 3_000)))
        }
        times.forEach { time ->
            val keyframe = Keyframe(time, KeyValue.Scalar(time.toDouble()))
            session.commands.execute(SceneCommand.SetKeyframe(EditorState.MAIN_TIMELINE, id(node), property, keyframe))
        }
    }

    private fun times(node: String, property: AnimatableProperty): List<Long> =
        session.document.value.timelines.single().track(id(node), property)?.keyframes?.map { it.timeMs }.orEmpty()

    private fun key(node: String, property: AnimatableProperty, time: Long) = SelectedKey(id(node), property, time)

    @Test
    fun `여러 track의 키를 같은 양만큼 옮기고 한 번에 실행 취소한다`() {
        put("a", x, 0, 500, 1_000)
        put("a", y, 0, 1_000)
        put("b", x, 200)
        val before = session.document.value
        val picked = setOf(key("a", x, 0), key("a", x, 500), key("a", y, 0), key("b", x, 200))

        val moved = edits.move(picked, 300)

        assertThat(moved).containsExactly(key("a", x, 300), key("a", x, 800), key("a", y, 300), key("b", x, 500))
        assertThat(times("a", x)).containsExactly(300L, 800L, 1_000L).inOrder()
        assertThat(times("a", y)).containsExactly(300L, 1_000L).inOrder()
        assertThat(times("b", x)).containsExactly(500L)
        session.history.undo()
        assertThat(session.document.value).isEqualTo(before)
    }

    @Test
    fun `고른 키끼리 자리를 이어받아도 부딪히지 않는다`() {
        put("a", x, 0, 100, 200)

        val moved = edits.move(setOf(key("a", x, 0), key("a", x, 100), key("a", x, 200)), -50)

        // 0보다 앞으로는 가지 않으므로 이동량이 0이 된다.
        assertThat(moved).containsExactly(key("a", x, 0), key("a", x, 100), key("a", x, 200))
        assertThat(edits.move(setOf(key("a", x, 100), key("a", x, 200)), 100)).isNotNull()
        assertThat(times("a", x)).containsExactly(0L, 200L, 300L).inOrder()
    }

    @Test
    fun `0 미만과 길이 초과는 막고 고르지 않은 키와 겹치면 아무것도 바꾸지 않는다`() {
        put("a", x, 100, 2_900)
        put("a", y, 0, 400)
        val picked = setOf(key("a", x, 100), key("a", x, 2_900))

        assertThat(edits.clampDelta(picked, 500)).isEqualTo(100)
        assertThat(edits.clampDelta(picked, -500)).isEqualTo(-100)
        val before = session.document.value
        assertThat(edits.move(setOf(key("a", x, 100), key("a", y, 0)), 400)).isNull()
        assertThat(session.document.value).isEqualTo(before)
    }

    @Test
    fun `고른 키를 지우고 복사한 키를 재생 위치에 붙여넣는다`() {
        put("a", x, 0, 500)
        put("a", y, 200)
        edits.copy(setOf(key("a", x, 500), key("a", y, 200)))
        state.playheadMs = 2_000.0

        val pasted = edits.paste(state.keyTimeMs, id("b"))

        assertThat(pasted).containsExactly(key("b", y, 2_000), key("b", x, 2_300))
        assertThat(times("b", x)).containsExactly(2_300L)
        assertThat(times("b", y)).containsExactly(2_000L)
        // 길이를 넘는 키는 버린다.
        assertThat(edits.paste(2_800, null)).containsExactly(key("a", y, 2_800))
        assertThat(edits.delete(setOf(key("a", x, 0), key("a", x, 500), key("a", y, 200)))).isTrue()
        assertThat(times("a", x)).isEmpty()
        assertThat(times("a", y)).containsExactly(2_800L)
    }

    @Test
    fun `easing과 회전 방향을 고른 키 모두에 정한다`() {
        put("a", x, 0, 1_000)
        put("a", rotation, 0, 1_000)
        val picked = setOf(key("a", x, 0), key("a", rotation, 0))

        assertThat(edits.setEasing(picked, EasingSpec.EaseOut)).isTrue()
        assertThat(edits.setRotation(picked, RotationDirection.Direct)).isTrue()

        assertThat(edits.keyframe(key("a", x, 0))?.easing).isEqualTo(EasingSpec.EaseOut)
        assertThat(edits.keyframe(key("a", rotation, 0))?.easing).isEqualTo(EasingSpec.EaseOut)
        assertThat(edits.keyframe(key("a", rotation, 0))?.rotation).isEqualTo(RotationDirection.Direct)
        assertThat(edits.keyframe(key("a", x, 0))?.rotation).isEqualTo(RotationDirection.Shortest)
        assertThat(edits.setRotation(setOf(key("a", x, 0)), RotationDirection.Clockwise)).isFalse()
    }

    @Test
    fun `자동 키는 값만 바꾸고 회전 방향과 easing은 그대로 둔다`() {
        put("a", rotation, 0, 1_000)
        edits.setRotation(setOf(key("a", rotation, 0)), RotationDirection.Clockwise)
        edits.setEasing(setOf(key("a", rotation, 0)), EasingSpec.EaseIn)

        val node = session.document.value.node(id("a"))!!
        state.execute(SceneCommand.TransformNodes(mapOf(id("a") to node.transform.copy(rotationDegrees = 45.0))))

        val keyframe = edits.keyframe(key("a", rotation, 0))!!
        assertThat(keyframe.value).isEqualTo(KeyValue.Scalar(45.0))
        assertThat(keyframe.rotation).isEqualTo(RotationDirection.Clockwise)
        assertThat(keyframe.easing).isEqualTo(EasingSpec.EaseIn)
    }
}
