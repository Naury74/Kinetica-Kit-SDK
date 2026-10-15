package com.naury.kinetica.render;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import com.naury.kinetica.core.scene.NodeEffects;
import com.naury.kinetica.core.scene.AppearEffect;
import com.naury.kinetica.core.scene.AppearKind;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Java의 RecyclerView 어댑터가 ViewEffects를 부르는 모양이 그대로 컴파일되고 동작하는지 확인한다. */
@RunWith(RobolectricTestRunner.class)
public class ViewEffectsJavaTest {
    @Test
    public void javaAppliesAndClearsEffects() {
        Context context = ApplicationProvider.getApplicationContext();
        View item = new View(context);
        // PressEffect와 LoopEffect는 색에 value class(ArgbColor)를 받아 Java에서 생성자를 부를 수 없다. 등장 효과로 확인한다.
        NodeEffects effects = new NodeEffects(Collections.emptyList(), new AppearEffect(AppearKind.Fade, 300L, 0L), null);

        ViewEffects.apply(item, effects);
        ViewEffects.apply(item, effects, 16f);
        assertThat(ViewEffects.effectsOf(item)).isEqualTo(effects);

        ViewEffects.clear(item);
        assertThat(ViewEffects.effectsOf(item)).isNull();
        assertThat(item.getAlpha()).isEqualTo(1f);
    }
}
