package com.naury.kinetica.render

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.scene.AppearEffect
import com.naury.kinetica.core.scene.AppearKind
import com.naury.kinetica.core.scene.NodeEffects
import com.naury.kinetica.core.scene.PressEffect
import com.naury.kinetica.core.scene.PressKind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class ViewEffectsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun touch(view: View, action: Int) {
        val now = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(now, now, action, 10f, 10f, 0))
    }

    @Test
    fun `누름 효과는 터치 리스너를 쓰지 않고 눌림 상태로 움직이며 떼면 원래대로 돌린다`() {
        // 창에 붙지 않은 View는 클릭 콜백이 미뤄지므로, 터치가 View 자신에게 전달되는지로 확인한다.
        var received = 0
        val view = object : View(context) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                received++
                return true
            }
        }.apply { layout(0, 0, 100, 100) }

        var hostListener = 0
        view.setOnTouchListener { _, _ ->
            hostListener++
            false
        }
        ViewEffects.apply(view, NodeEffects(press = listOf(PressEffect(PressKind.Ripple), PressEffect(PressKind.Scale, amount = 0.9))), 12f)
        view.isPressed = true
        ShadowLooper.idleMainLooper(1_000, java.util.concurrent.TimeUnit.MILLISECONDS)
        val pressedScale = view.scaleX
        view.isPressed = false
        touch(view, MotionEvent.ACTION_DOWN)
        touch(view, MotionEvent.ACTION_UP)
        ShadowLooper.idleMainLooper(1_000, java.util.concurrent.TimeUnit.MILLISECONDS)

        assertThat(pressedScale).isWithin(0.01f).of(0.9f)
        assertThat(view.scaleX).isWithin(0.01f).of(1f)
        assertThat(hostListener).isEqualTo(2)
        assertThat(received).isEqualTo(2)
        assertThat(view.foreground).isNotNull()
        ViewEffects.clear(view)
        assertThat(view.foreground).isNull()
        assertThat(view.scaleX).isEqualTo(1f)
    }

    @Test
    fun `나타나기는 투명하게 시작해 시간이 지나면 보인다`() {
        val parent = FrameLayout(context)
        val view = View(context)
        parent.addView(view)

        ViewEffects.apply(view, NodeEffects(appear = AppearEffect(AppearKind.SlideUp, durationMs = 300)))
        val start = view.alpha
        ShadowLooper.idleMainLooper(1_000, java.util.concurrent.TimeUnit.MILLISECONDS)

        assertThat(start).isEqualTo(0f)
        assertThat(view.alpha).isEqualTo(1f)
        assertThat(view.translationY).isEqualTo(0f)
    }
}
