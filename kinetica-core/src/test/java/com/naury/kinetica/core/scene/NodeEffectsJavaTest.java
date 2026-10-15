package com.naury.kinetica.core.scene;

import static com.google.common.truth.Truth.assertThat;

import java.util.Collections;
import org.junit.Test;

/** Java 호스트가 효과 객체를 직접 만들 수 있는지 확인한다. */
public class NodeEffectsJavaTest {
    @Test
    public void javaBuildsEffects() {
        PressEffect press = PressEffect.of(PressKind.Ripple, 0xFFFF0000);
        LoopEffect loop = LoopEffect.of(LoopKind.Pulse, 1200L);
        NodeEffects effects = new NodeEffects(Collections.singletonList(press), new AppearEffect(AppearKind.Fade), loop);

        assertThat(effects.isEmpty()).isFalse();
        assertThat(press.getResolvedAmount()).isEqualTo(PressKind.Ripple.getDefaultAmount());
        assertThat(NodeEffects.None.isEmpty()).isTrue();
        assertThat(new NodeEffects().isEmpty()).isTrue();
    }
}
