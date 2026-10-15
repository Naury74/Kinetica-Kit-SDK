package com.naury.kinetica.presets

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.scene.SceneValidator
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.rect
import org.junit.Test

class EffectPresetsTest {
    @Test
    fun `모든 효과 묶음은 검증을 통과한다`() {
        val nodes = (EffectPresets.all.values + EffectPresets.listItem(50)).mapIndexed { i, effects -> rect("n$i").copy(effects = effects) }

        assertThat(SceneValidator().validate(document(nodes))).isEmpty()
        assertThat(EffectPresets.listItem(3).appear!!.delayMs).isEqualTo(180L)
        assertThat(EffectPresets.listItem(50).appear!!.delayMs).isEqualTo(600L)
    }
}
