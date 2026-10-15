package com.naury.kinetica.ui.view

import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.ui.GlassStyles
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
class GlassRecyclerViewTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** 항목마다 글래스 카드인 목록. 짝수 항목은 Acrylic, 홀수 항목은 auto 스타일이다. */
    private class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        val clicks = mutableListOf<Int>()

        override fun getItemCount(): Int = ITEMS

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val glass = GlassSurfaceView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(MATCH, ITEM_PX)
                addView(TextView(parent.context))
            }
            return object : RecyclerView.ViewHolder(glass) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val glass = holder.itemView as GlassSurfaceView
            glass.tag = position
            glass.glassStyle = if (position % 2 == 0) GlassStyles.Acrylic else null
            (glass.getChildAt(1) as TextView).apply {
                text = "item $position"
                setTextColor(glass.contentColor)
            }
            glass.setOnClickListener { clicks += position }
        }
    }

    private lateinit var list: RecyclerView
    private val adapter = Adapter()

    private fun showList() {
        compose.runOnUiThread {
            val activity = compose.activity
            list = RecyclerView(activity).apply {
                layoutManager = LinearLayoutManager(activity)
                adapter = this@GlassRecyclerViewTest.adapter
            }
            activity.setContentView(
                FrameLayout(activity).apply {
                    setBackgroundColor(RED)
                    addView(list, FrameLayout.LayoutParams(MATCH, MATCH))
                },
            )
        }
        compose.waitForIdle()
    }

    private fun preDrawListeners(): Int {
        val observer = compose.activity.window.decorView.viewTreeObserver
        val field = ViewTreeObserver::class.java.getDeclaredField("mOnPreDrawListeners").apply { isAccessible = true }
        val listeners = field.get(observer) ?: return 0
        return listeners.javaClass.getDeclaredMethod("size").apply { isAccessible = true }.invoke(listeners) as Int
    }

    @Test
    fun `빠르게 스크롤해 재사용해도 글래스가 프레임마다 기록하는 리스너가 늘지 않고 스타일이 항목을 따른다`() {
        showList()
        val visible = list.childCount
        val baseline = preDrawListeners()

        repeat(SCROLLS) {
            compose.runOnUiThread { list.scrollBy(0, ITEM_PX * 2 + 13) }
            compose.waitForIdle()
        }

        // 풀에 들어가 창에서 떨어진 글래스는 기록을 멈춘다. 화면에 보이는 항목 수만큼만 남는다.
        assertThat(preDrawListeners()).isAtMost(baseline + 2)
        assertThat(list.childCount).isAtMost(visible + 1)
        (0 until list.childCount).map { list.getChildAt(it) as GlassSurfaceView }.forEach { glass ->
            val position = glass.tag as Int
            assertThat(glass.glassStyle).isEqualTo(if (position % 2 == 0) GlassStyles.Acrylic else null)
        }

        compose.runOnUiThread { compose.activity.setContentView(FrameLayout(compose.activity)) }
        compose.waitForIdle()
        assertThat(preDrawListeners()).isAtMost(baseline - visible + 2)
    }

    @Test
    fun `목록 위 글래스 카드는 뒤 배경을 비추고 클릭을 그대로 받는다`() {
        showList()
        compose.runOnUiThread {
            list.getChildAt(0).performClick()
            list.getChildAt(2).performClick()
        }
        assertThat(adapter.clicks).containsExactly(0, 2).inOrder()

        val image = compose.onRoot().captureToImage().toPixelMap()
        val pixel = image[image.width / 2, ITEM_PX + ITEM_PX / 2]
        // 빨간 배경이 글래스 너머로 비친다.
        assertThat(pixel.red).isGreaterThan(pixel.green)
        assertThat(pixel.toArgb()).isNotEqualTo(RED)
    }

    @Test
    fun `조상 View의 배경만 있는 화면에서도 첫 프레임부터 그 배경을 비춘다`() {
        compose.runOnUiThread {
            val activity = compose.activity
            activity.setContentView(
                FrameLayout(activity).apply {
                    setBackgroundColor(RED)
                    addView(GlassSurfaceView(activity), FrameLayout.LayoutParams(MATCH, ITEM_PX))
                },
            )
        }
        compose.waitForIdle()

        val pixel = compose.onRoot().captureToImage().toPixelMap().let { it[it.width / 2, ITEM_PX / 2] }
        // 배경 크기가 정해지기 전에 기록하면 창의 흰 배경이 비친다.
        assertThat(pixel.red).isGreaterThan(pixel.green + 0.5f)
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val RED = 0xFFFF0000.toInt()
        const val ITEMS = 80
        const val ITEM_PX = 120
        const val SCROLLS = 20
    }
}
