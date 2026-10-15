package com.naury.kinetica.render

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.scene.AppearEffect
import com.naury.kinetica.core.scene.AppearKind
import com.naury.kinetica.core.scene.LoopEffect
import com.naury.kinetica.core.scene.LoopKind
import com.naury.kinetica.core.scene.NodeEffects
import com.naury.kinetica.core.scene.PressEffect
import com.naury.kinetica.core.scene.PressKind
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-mdpi")
class ViewEffectsRecyclerViewTest {
    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    /** 짝수 항목만 누름 효과와 반복 애니메이션이 있는 목록. 효과가 없는 항목은 [ViewEffects.clear]로 정리한다. */
    private inner class Adapter(val count: Int = ITEMS) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        val clicks = mutableListOf<Int>()
        var binds = 0

        override fun getItemCount(): Int = count

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(TextView(parent.context).apply { layoutParams = ViewGroup.LayoutParams(MATCH, ITEM_PX) }) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            binds++
            val view = holder.itemView as TextView
            view.text = "item $position"
            view.tag = position
            view.setOnClickListener { clicks += position }
            if (position % 2 == 0) ViewEffects.apply(view, EFFECTS, CORNER_DP) else ViewEffects.clear(view)
        }
    }

    private fun list(adapter: Adapter): RecyclerView {
        val list = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            this.adapter = adapter
        }
        activity.setContentView(FrameLayout(activity).apply { addView(list, FrameLayout.LayoutParams(MATCH, LIST_PX)) })
        idle()
        return list
    }

    private fun idle(ms: Long = 0) {
        shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun RecyclerView.items(): List<View> = (0 until childCount).map(::getChildAt)

    @Test
    fun `빠르게 스크롤해 항목 View를 재사용해도 효과가 다른 항목으로 새지 않는다`() {
        val adapter = Adapter()
        val list = list(adapter)
        val pressedItem = list.getChildAt(0)
        pressedItem.isPressed = true
        idle(PRESS_SETTLE_MS)
        assertThat(pressedItem.scaleX).isWithin(TOLERANCE).of(PRESSED_SCALE)

        repeat(SCROLLS) {
            list.scrollBy(0, ITEM_PX * 3 + 7)
            idle(FRAME_MS)
        }
        list.scrollBy(0, -ITEM_PX * 5)
        idle(PRESS_SETTLE_MS)

        assertThat(adapter.binds).isGreaterThan(list.childCount * 3)
        list.items().forEach { item ->
            val position = item.tag as Int
            val attached = item.getTag(R.id.kinetica_view_effects) as AttachedEffects?
            if (position % 2 == 0) {
                assertThat(attached).isNotNull()
                assertThat(attached!!.looping).isTrue()
            } else {
                assertThat(attached).isNull()
                assertThat(item.foreground).isNull()
                assertThat(item.scaleX).isEqualTo(1f)
                assertThat(item.scaleY).isEqualTo(1f)
            }
        }
    }

    @Test
    fun `목록에서 떨어진 항목은 반복 애니메이션을 멈추고 클릭은 그대로 전달된다`() {
        val adapter = Adapter()
        val list = list(adapter)
        val first = list.getChildAt(0)
        val effects = first.getTag(R.id.kinetica_view_effects) as AttachedEffects
        assertThat(effects.looping).isTrue()

        list.scrollBy(0, ITEM_PX * ITEMS / 2)
        idle(FRAME_MS)

        assertThat(first.isAttachedToWindow).isFalse()
        assertThat(effects.looping).isFalse()

        list.scrollToPosition(0)
        idle(FRAME_MS)
        list.getChildAt(0).performClick()
        list.getChildAt(1).performClick()
        assertThat(adapter.clicks).containsExactly(0, 1).inOrder()
    }

    @Test
    fun `notifyItemChanged 애니메이션이 끝나면 항목은 원래 모습이고 효과도 남아 있다`() {
        val adapter = Adapter()
        val list = list(adapter)
        (list.itemAnimator as DefaultItemAnimator).changeDuration = CHANGE_MS

        adapter.notifyItemChanged(0)
        adapter.notifyItemChanged(1)
        idle(CHANGE_MS + APPEAR_MS)

        assertThat(list.itemAnimator!!.isRunning).isFalse()
        list.items().filter { (it.tag as Int) < 2 }.forEach { item ->
            assertThat(item.alpha).isEqualTo(1f)
            assertThat(item.translationY).isEqualTo(0f)
            assertThat(item.scaleX).isEqualTo(1f)
        }
        assertThat(list.getChildAt(0).getTag(R.id.kinetica_view_effects)).isNotNull()
    }

    @Test
    fun `등장 대기 시간이 다음 누름 애니메이션이나 호스트 애니메이션을 늦추지 않는다`() {
        val view = TextView(activity).apply { isClickable = true }
        activity.setContentView(FrameLayout(activity).apply { addView(view, FrameLayout.LayoutParams(ITEM_PX, ITEM_PX)) })
        ViewEffects.apply(
            view,
            NodeEffects(
                press = listOf(PressEffect(PressKind.Scale, amount = 0.9)),
                appear = AppearEffect(AppearKind.Fade, 200, delayMs = 300),
            ),
        )
        assertThat(view.alpha).isEqualTo(0f)
        idle(APPEAR_MS + 300)
        assertThat(view.alpha).isEqualTo(1f)

        view.isPressed = true
        // 누름 애니메이션(160ms)이 등장 대기 시간(300ms)만큼 늦춰졌다면 아직 줄어들지 않았을 시점이다.
        idle(PRESS_ONLY_MS)
        view.animate().translationX(10f).setDuration(100).start()
        idle(PRESS_ONLY_MS)

        assertThat(view.scaleX).isWithin(TOLERANCE).of(PRESSED_SCALE)
        assertThat(view.translationX).isEqualTo(10f)
    }

    @Test
    fun `효과를 지우면 효과가 바꾼 속성만 원래 값으로 돌리고 호스트 애니메이션은 취소하지 않는다`() {
        val view = View(activity).apply {
            alpha = 0.5f
            rotation = 15f
        }
        activity.setContentView(FrameLayout(activity).apply { addView(view, FrameLayout.LayoutParams(ITEM_PX, ITEM_PX)) })
        ViewEffects.apply(view, NodeEffects(loop = LoopEffect(LoopKind.Pulse)))
        idle(FRAME_MS * 3)
        view.animate().translationY(30f).setDuration(200).start()
        assertThat(ViewEffects.effectsOf(view)).isEqualTo(NodeEffects(loop = LoopEffect(LoopKind.Pulse)))

        ViewEffects.clear(view)
        idle(300)

        assertThat(ViewEffects.effectsOf(view)).isNull()
        assertThat(view.scaleX).isEqualTo(1f)
        assertThat(view.alpha).isEqualTo(0.5f)
        assertThat(view.rotation).isEqualTo(15f)
        assertThat(view.translationY).isEqualTo(30f)
    }

    private companion object {
        val EFFECTS = NodeEffects(
            press = listOf(PressEffect(PressKind.Scale, amount = 0.9), PressEffect(PressKind.Ripple)),
            loop = LoopEffect(LoopKind.Shimmer),
        )
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val ITEMS = 60
        const val ITEM_PX = 80
        const val LIST_PX = 480
        const val CORNER_DP = 12f
        const val SCROLLS = 12
        const val FRAME_MS = 16L
        const val PRESS_SETTLE_MS = 400L
        const val CHANGE_MS = 120L
        const val PRESS_ONLY_MS = 200L
        const val APPEAR_MS = 400L
        const val PRESSED_SCALE = 0.9f
        const val TOLERANCE = 0.01f
    }
}
