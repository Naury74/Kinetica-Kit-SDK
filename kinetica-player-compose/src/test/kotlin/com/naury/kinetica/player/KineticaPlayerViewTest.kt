package com.naury.kinetica.player

import android.os.Looper
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-mdpi")
class KineticaPlayerViewTest {
    // Compose 프레임을 테스트 시계로 돌린다. 기본 프레임 시계는 다른 테스트 뒤에 실행되면 프레임을 받지 못한다.
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun `XML 화면 재생기가 호스트 View를 넣고 동작을 알린다`() {
        val slot = SceneNode(NodeId("chart"), NodeContent.HostSlot(Size(500.0, 300.0), "chart"))
        val button = SceneNode(
            NodeId("buy"),
            NodeContent.Rectangle(Size(500.0, 300.0)),
            transform = Transform2D(translationY = 400.0),
            action = "open_order?id=7",
        )
        val document = SceneDocument("x", Artboard(500.0, 700.0), listOf(slot, button).associateBy { it.id }, listOf(slot.id, button.id))
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val actions = mutableListOf<String>()
                val player = KineticaPlayerView(activity)
                activity.setContentView(player, ViewGroup.LayoutParams(400 * 2, 560 * 2))
                player.setHostViews { key, context ->
                    TextView(context).apply {
                        text = "host $key"
                        tag = key
                    }
                }
                player.setOnActionListener { actions += it }
                player.setDocument(document)
                shadowOf(Looper.getMainLooper()).idle()
                compose.waitForIdle()

                val chart = player.findViewWithTag<TextView>("chart")
                assertThat(chart.text.toString()).isEqualTo("host chart")
                val location = IntArray(2).also(player::getLocationInWindow)
                val y = player.height * (550f / 700f)
                val down = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, player.width / 2f, y, 0)
                val up = android.view.MotionEvent.obtain(0, 50, android.view.MotionEvent.ACTION_UP, player.width / 2f, y, 0)
                player.dispatchTouchEvent(down)
                player.dispatchTouchEvent(up)
                shadowOf(Looper.getMainLooper()).idle()

                assertThat(location).isNotNull()
                assertThat(actions).containsExactly("open_order?id=7")
            }
        }
    }
}
