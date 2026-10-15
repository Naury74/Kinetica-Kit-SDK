package com.naury.kinetica.player

import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** ViewPager2 페이지와 Fragment back stack 안의 [KineticaPlayerView]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-mdpi")
class KineticaPlayerViewFragmentTest {
    // 액티비티는 직접 만들지만 Compose 프레임은 테스트 시계로 돌린다. 기본 프레임 시계는 다른 테스트 뒤에 실행되면 프레임을 받지 못한다.
    @get:Rule
    val compose = createEmptyComposeRule()

    class PlayerFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            KineticaPlayerView(inflater.context).apply { id = PLAYER_ID }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            val page = requireArguments().getInt(PAGE)
            (view as KineticaPlayerView).apply {
                setHostViews { key, context ->
                    created += "$page:$key"
                    TextView(context).apply {
                        text = "$page:$key"
                        tag = "$page:$key"
                    }
                }
                setOnActionListener { actions += "$page:$it" }
                setDocument(DOCUMENT)
            }
        }
    }

    private val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
    private val activity = controller.get()

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
        created.clear()
        actions.clear()
    }

    private fun idle(ms: Long = FRAME_MS) {
        repeat((ms / FRAME_MS).toInt().coerceAtLeast(1)) {
            shadowOf(Looper.getMainLooper()).idleFor(FRAME_MS, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(FRAME_MS)
        }
    }

    private fun tap(view: View, x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + TAP_MS, MotionEvent.ACTION_UP, x, y, 0)
        view.dispatchTouchEvent(down)
        view.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        idle()
    }

    private fun page(index: Int): Fragment = PlayerFragment().apply { arguments = Bundle().apply { putInt(PAGE, index) } }

    @Test
    fun `ViewPager2 페이지마다 호스트 View를 넣고 페이지를 넘겼다 돌아와도 보인다`() {
        val pager = ViewPager2(activity).apply {
            id = PAGER_ID
            adapter = object : FragmentStateAdapter(activity) {
                override fun getItemCount(): Int = PAGES

                override fun createFragment(position: Int): Fragment = page(position)
            }
        }
        activity.setContentView(pager)
        idle(SETTLE_MS)
        assertThat(activity.window.decorView.findViewWithTag<View>("0:chart")).isNotNull()

        pager.setCurrentItem(2, false)
        idle(SETTLE_MS)
        assertThat(activity.window.decorView.findViewWithTag<View>("2:chart")).isNotNull()
        pager.setCurrentItem(0, false)
        idle(SETTLE_MS)

        val chart = activity.window.decorView.findViewWithTag<View>("0:chart")
        assertThat(chart).isNotNull()
        assertThat(chart.isAttachedToWindow).isTrue()
        // Robolectric은 다시 붙은 페이지를 그리지 않아, Compose가 그리기 단계에서 하는 배치가 일어나지 않는다. 기기처럼 한 번 그린다.
        val root = activity.window.decorView
        root.draw(
            android.graphics.Canvas(
                android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888),
            ),
        )
        assertThat(chart.width).isGreaterThan(0)
    }

    @Test
    fun `Fragment를 back stack으로 바꿨다 돌아오면 새 View로 다시 그리고 동작을 알린다`() {
        val container = FrameLayout(activity).apply { id = CONTAINER_ID }
        activity.setContentView(container)
        val first = page(0)
        activity.supportFragmentManager.beginTransaction().add(CONTAINER_ID, first).commitNow()
        idle(SETTLE_MS)
        val oldChart = activity.window.decorView.findViewWithTag<View>("0:chart")

        activity.supportFragmentManager.beginTransaction().replace(CONTAINER_ID, page(1)).addToBackStack(null).commit()
        idle(SETTLE_MS)
        assertThat(oldChart.isAttachedToWindow).isFalse()
        activity.supportFragmentManager.popBackStackImmediate()
        idle(SETTLE_MS)

        val player = first.requireView() as KineticaPlayerView
        val chart = player.findViewWithTag<View>("0:chart")
        assertThat(chart).isNotSameInstanceAs(oldChart)
        assertThat(created.count { it == "0:chart" }).isEqualTo(2)
        tap(player, player.width / 2f, player.height * BUTTON_Y)
        assertThat(actions).containsExactly("0:open")
    }

    companion object {
        private val slot = SceneNode(NodeId("chart"), NodeContent.HostSlot(Size(500.0, 300.0), "chart"))
        private val button = SceneNode(
            NodeId("buy"),
            NodeContent.Rectangle(Size(500.0, 300.0)),
            transform = Transform2D(translationY = 400.0),
            action = "open",
        )
        val DOCUMENT = SceneDocument("x", Artboard(500.0, 700.0), listOf(slot, button).associateBy { it.id }, listOf(slot.id, button.id))
        val created = mutableListOf<String>()
        val actions = mutableListOf<String>()
        const val PAGE = "page"
        const val PAGES = 3
        const val PLAYER_ID = 0x7f0a2001
        const val PAGER_ID = 0x7f0a2002
        const val CONTAINER_ID = 0x7f0a2003
        const val FRAME_MS = 16L
        const val SETTLE_MS = 400L
        const val TAP_MS = 50L
        const val BUTTON_Y = 550f / 700f
    }
}
