package com.naury.kinetica.core.scene

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.command.SceneReducer
import com.naury.kinetica.core.testing.TestScenes
import org.junit.Test

class SceneTransitionSpecTest {
    private val reducer = SceneReducer()

    @Test
    fun `전환을 정하고 지우는 명령은 문서의 transition만 바꾼다`() {
        val sample = TestScenes.sample()
        val spec = SceneTransitionSpec(SceneTransitionKind.Wipe, SceneTransitionEdge.Start)

        val set = (reducer.apply(sample, SceneCommand.SetTransition(spec)) as CommandResult.Applied).document
        val cleared = (reducer.apply(set, SceneCommand.SetTransition(null)) as CommandResult.Applied).document

        assertThat(set.transition).isEqualTo(spec)
        assertThat(set.nodes).isEqualTo(sample.nodes)
        assertThat(cleared.transition).isNull()
    }

    @Test
    fun `easing 범위를 벗어난 전환은 거부한다`() {
        val bad = SceneTransitionSpec(SceneTransitionKind.Fade, easing = EasingSpec.Spring(-1.0, 100.0))

        assertThat(reducer.apply(TestScenes.sample(), SceneCommand.SetTransition(bad))).isInstanceOf(CommandResult.Rejected::class.java)
    }

    @Test
    fun `시간과 중심 범위를 생성할 때 확인한다`() {
        assertThat(runCatching { SceneTransitionSpec(SceneTransitionKind.Fade, durationMs = -1) }.isFailure).isTrue()
        assertThat(runCatching { SceneTransitionSpec(SceneTransitionKind.Iris, originY = 1.1) }.isFailure).isTrue()
    }

    @Test
    fun `반대 가장자리와 이름으로 찾기`() {
        assertThat(SceneTransitionEdge.entries.map { it.opposite }).containsExactly(
            SceneTransitionEdge.Start,
            SceneTransitionEdge.End,
            SceneTransitionEdge.Bottom,
            SceneTransitionEdge.Top,
        ).inOrder()
        assertThat(SceneTransitionKind.entries.map { SceneTransitionKind.fromJsonName(it.jsonName) }).isEqualTo(SceneTransitionKind.entries)
        assertThat(SceneTransitionKind.fromJsonName("Wipe")).isNull()
    }
}
