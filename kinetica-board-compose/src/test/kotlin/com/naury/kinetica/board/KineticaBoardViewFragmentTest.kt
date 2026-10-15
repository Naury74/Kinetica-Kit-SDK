package com.naury.kinetica.board

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
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.common.truth.Truth.assertThat
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

/** Fragment와 ViewPager2 페이지 안의 [KineticaBoardView]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-mdpi")
class KineticaBoardViewFragmentTest {
    // 액티비티는 직접 만들지만, Compose 프레임은 테스트 시계로 돌린다. Robolectric은 테스트마다 Choreographer를 새로 만드는데
    // Compose의 기본 프레임 시계는 처음 만든 Choreographer에 묶여 두 번째 테스트부터 프레임이 오지 않는다.
    @get:Rule
    val compose = createEmptyComposeRule()

    /** 저장 횟수를 세는 저장소. 페이지마다 하나씩 둔다. */
    class CountingStorage : BoardStorage {
        val saves = mutableListOf<String>()

        override fun load(): String? = null

        override fun save(json: String) {
            saves += json
        }
    }

    /** onViewCreated마다 [BoardStates.obtain]으로 상태를 얻는 흔한 Fragment 구성. */
    class BoardFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            KineticaBoardView(inflater.context).apply { id = BOARD_ID }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            val page = requireArguments().getInt(PAGE)
            val state = BoardStates.obtain(this, CATALOG, storages.getOrPut(page) { CountingStorage() })
            (view as KineticaBoardView).apply {
                isEditing = editing
                setBoard(state) { id, context -> TextView(context).apply { text = "$page:$id" } }
            }
        }
    }

    private val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
    private val activity = controller.get()

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
    }
    private var downTime = 0L

    private fun idle(ms: Long = FRAME_MS) {
        repeat((ms / FRAME_MS).toInt().coerceAtLeast(1)) {
            shadowOf(Looper.getMainLooper()).idleFor(FRAME_MS, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(FRAME_MS)
        }
    }

    private fun send(action: Int, x: Float, y: Float) {
        // 이벤트 시각은 시스템 시계와 맞춘다. Compose는 길게 누르기와 속도 계산에 이 시각을 쓴다.
        val now = android.os.SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = now
        val event = MotionEvent.obtain(downTime, now, action, x, y, 0)
        activity.window.decorView.dispatchTouchEvent(event)
        event.recycle()
        idle()
    }

    private fun swipe(fromX: Float, toX: Float, y: Float) {
        send(MotionEvent.ACTION_DOWN, fromX, y)
        repeat(STEPS) { send(MotionEvent.ACTION_MOVE, fromX + (toX - fromX) * (it + 1) / STEPS, y) }
        send(MotionEvent.ACTION_UP, toX, y)
        idle(SETTLE_MS)
    }

    private fun pager(): ViewPager2 {
        val pager = ViewPager2(activity).apply {
            id = PAGER_ID
            adapter = object : FragmentStateAdapter(activity) {
                override fun getItemCount(): Int = PAGES

                override fun createFragment(position: Int): Fragment = BoardFragment().apply {
                    arguments =
                        Bundle().apply { putInt(PAGE, position) }
                }
            }
        }
        activity.setContentView(pager, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        idle(SETTLE_MS)
        return pager
    }

    @Test
    fun `ViewPager2 페이지에서 편집 중 위젯을 가로로 끌어도 페이지가 넘어가지 않고 사용 모드에서는 넘어간다`() {
        storages.clear()
        editing = true
        val pager = pager()
        val y = WIDGET_CENTER_PX

        swipe(300f, 60f, y)
        assertThat(pager.currentItem).isEqualTo(0)
        assertThat(storages.getValue(0).saves).isEmpty()

        val page = activity.supportFragmentManager.fragments.first { it.requireArguments().getInt(PAGE) == 0 }
        (page.requireView() as KineticaBoardView).isEditing = false
        idle(SETTLE_MS)
        swipe(300f, 20f, y)
        assertThat(pager.currentItem).isEqualTo(1)
    }

    @Test
    fun `Fragment를 back stack으로 바꿨다 돌아와도 보드 상태가 남고 가려질 때 한 번만 저장한다`() {
        storages.clear()
        editing = false
        val container = FrameLayout(activity).apply { id = CONTAINER_ID }
        activity.setContentView(container)
        val first = BoardFragment().apply { arguments = Bundle().apply { putInt(PAGE, 0) } }
        activity.supportFragmentManager.beginTransaction().add(CONTAINER_ID, first).commitNow()
        idle(SETTLE_MS)
        val firstBoard = first.requireView() as KineticaBoardView
        firstBoard.isEditing = true

        repeat(BACK_AND_FORTH) {
            activity.supportFragmentManager.beginTransaction()
                .replace(CONTAINER_ID, BoardFragment().apply { arguments = Bundle().apply { putInt(PAGE, 1) } })
                .addToBackStack(null)
                .commit()
            idle(SETTLE_MS)
            assertThat(first.view).isNull()
            activity.supportFragmentManager.popBackStackImmediate()
            idle(SETTLE_MS)
        }
        val restored = first.requireView() as KineticaBoardView
        // Fragment가 저장해 둔 View 상태로 편집 모드를 되살린다.
        assertThat(restored).isNotSameInstanceAs(firstBoard)
        assertThat(restored.isEditing).isTrue()
        val savesBefore = storages.getValue(0).saves.size

        activity.supportFragmentManager.beginTransaction().setMaxLifecycle(first, Lifecycle.State.CREATED).commitNow()

        // onViewCreated를 여러 번 거쳤어도 가려질 때 저장은 한 번이다.
        assertThat(storages.getValue(0).saves.size - savesBefore).isEqualTo(1)
    }

    companion object {
        val CATALOG = listOf(BoardWidget("a", "A", widthDp = 360f, heightDp = 200f), BoardWidget("b", "B", widthDp = 360f, heightDp = 200f))
        val storages = HashMap<Int, CountingStorage>()
        var editing = false
        const val PAGE = "page"
        const val PAGES = 3
        const val BOARD_ID = 0x7f0a1001
        const val PAGER_ID = 0x7f0a1002
        const val CONTAINER_ID = 0x7f0a1003
        const val FRAME_MS = 16L
        const val SETTLE_MS = 600L
        const val STEPS = 12
        const val BACK_AND_FORTH = 3
        const val WIDGET_CENTER_PX = 120f
    }
}
