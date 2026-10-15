package com.naury.kinetica.ui.view;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.test.core.app.ActivityScenario;
import com.naury.kinetica.ui.GlassStyles;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Java 앱이 XML 진입점을 부르는 모양 그대로 컴파일되고 동작하는지 확인한다. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class GlassPopupsJavaTest {
    @Test
    public void javaCallsShowAndDismiss() {
        try (ActivityScenario<ComponentActivity> scenario = ActivityScenario.launch(ComponentActivity.class)) {
            scenario.onActivity(activity -> {
                FrameLayout root = new FrameLayout(activity);
                GlassSurfaceView card = new GlassSurfaceView(activity);
                card.setGlassStyle(GlassStyles.Acrylic);
                card.setCornerRadius(32f);
                root.addView(card, new FrameLayout.LayoutParams(400, 200));
                activity.setContentView(root);

                AtomicInteger dismissed = new AtomicInteger();
                GlassPopupHandle sheet = GlassSheets.show(activity, new TextView(activity));
                sheet.setOnDismissListener(dismissed::incrementAndGet);
                GlassPopupHandle dialog =
                        GlassDialogs.show(activity, new TextView(activity), new GlassPopupOptions(GlassStyles.Vision, "확인"));
                GlassToasts.show(activity, "저장했습니다");
                shadowOf(Looper.getMainLooper()).idle();

                assertThat(sheet.isShowing()).isTrue();
                assertThat(dialog.isShowing()).isTrue();
                sheet.dismiss();
                dialog.dismiss();
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));

                assertThat(dismissed.get()).isEqualTo(1);
                assertThat(sheet.isShowing()).isFalse();
                assertThat(sheet.getContentView()).isNull();
            });
        }
    }
}
