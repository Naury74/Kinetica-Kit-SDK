package com.naury.kinetica.ui.view;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.naury.kinetica.ui.GlassStyles;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Java 앱이 GlassSurfaceView를 코드로 만들고 리스너를 람다로 다는 모양이 그대로 컴파일되고 동작하는지 확인한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GlassSurfaceViewJavaTest {
    @Test
    public void javaCreatesViewWithDefaultsAndListensForContentColor() {
        Context context = ApplicationProvider.getApplicationContext();
        GlassSurfaceView view = new GlassSurfaceView(context);
        List<Integer> colors = new ArrayList<>();
        view.setOnContentColorChangeListener(colors::add);

        assertThat(view.getGlassStyle()).isNull();
        assertThat(view.getCornerRadius()).isEqualTo(24f * context.getResources().getDisplayMetrics().density);

        view.setGlassStyle(GlassStyles.LiquidDark);
        view.setGlassStyle(GlassStyles.LiquidDark);
        view.setOnContentColorChangeListener(null);
        view.setGlassStyle(GlassStyles.Liquid);

        // 같은 스타일을 다시 넣거나 리스너를 해제한 뒤의 변경은 알리지 않는다.
        assertThat(colors).hasSize(1);
    }
}
