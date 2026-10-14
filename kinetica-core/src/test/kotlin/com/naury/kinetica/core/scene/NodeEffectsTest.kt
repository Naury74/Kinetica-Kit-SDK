package com.naury.kinetica.core.scene

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.rect
import org.junit.Test

class NodeEffectsTest {
    private fun violations(effects: NodeEffects) = SceneValidator().validate(document(listOf(rect("a").copy(effects = effects))))

    @Test
    fun `범위 안의 효과는 받고 같은 누름 종류 두 번·범위 밖 세기·너무 짧은 반복은 거부한다`() {
        val ok = NodeEffects(
            press = listOf(PressEffect(PressKind.Ripple), PressEffect(PressKind.Scale, amount = 0.9)),
            appear = AppearEffect(AppearKind.Pop, delayMs = 120),
            loop = LoopEffect(LoopKind.Pulse, periodMs = 1_000, amount = 1.1),
        )

        assertThat(violations(ok)).isEmpty()
        assertThat(violations(NodeEffects(press = listOf(PressEffect(PressKind.Dim), PressEffect(PressKind.Dim))))).isNotEmpty()
        assertThat(violations(NodeEffects(press = listOf(PressEffect(PressKind.Scale, amount = 3.0))))).isNotEmpty()
        assertThat(violations(NodeEffects(loop = LoopEffect(LoopKind.Spin, periodMs = 10)))).isNotEmpty()
        assertThat(violations(NodeEffects(appear = AppearEffect(AppearKind.Fade, durationMs = -1)))).isNotEmpty()
    }
}
