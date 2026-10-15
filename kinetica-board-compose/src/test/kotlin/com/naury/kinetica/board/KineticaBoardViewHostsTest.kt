package com.naury.kinetica.board

import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.AppBarLayout
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

/** CoordinatorLayout, AppBarLayout, NestedScrollView 같은 XML 스크롤 컨테이너에 [KineticaBoardView]를 넣었을 때의 동작. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-mdpi")
class KineticaBoardViewHostsTest {
    // 액티비티는 직접 만들지만, Compose 프레임은 테스트 시계로 돌린다. Robolectric은 테스트마다 Choreographer를 새로 만드는데
    // Compose의 기본 프레임 시계는 처음 만든 Choreographer에 묶여 두 번째 테스트부터 프레임이 오지 않는다.
    @get:Rule
    val compose = createEmptyComposeRule()

    private val controller = run {
        // 설정 변경을 직접 처리하는 액티비티(android:configChanges)처럼 다시 만들지 않고 onConfigurationChanged만 받게 한다.
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app.packageManager).addOrUpdateActivity(
            android.content.pm.ActivityInfo().apply {
                name = ComponentActivity::class.java.name
                packageName = app.packageName
                configChanges = CONFIG_CHANGES
            },
        )
        Robolectric.buildActivity(ComponentActivity::class.java).setup()
    }
    private val activity = controller.get()
    private val state = KineticaBoardState.create(CATALOG)
    private var downTime = 0L

    @After
    fun tearDown() {
        // 컴포지션이 다음 테스트로 남지 않도록 액티비티를 끝낸다.
        controller.pause().stop().destroy()
        state.close()
    }

    private fun board(editing: Boolean = false) = KineticaBoardView(activity).apply {
        isEditing = editing
        setBoard(state) { id, context ->
            TextView(context).apply {
                text = id
                tag = id
            }
        }
    }

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

    /** 화면 좌표 ([x], [fromY])에서 ([x], [toY])까지 손가락을 끈다. [hold]면 떼지 않는다. */
    private fun drag(x: Float, fromY: Float, toY: Float, steps: Int = STEPS, hold: Boolean = false) {
        send(MotionEvent.ACTION_DOWN, x, fromY)
        repeat(steps) { send(MotionEvent.ACTION_MOVE, x, fromY + (toY - fromY) * (it + 1) / steps) }
        if (!hold) send(MotionEvent.ACTION_UP, x, toY)
    }

    private fun windowTop(view: View): Int = IntArray(2).also(view::getLocationInWindow)[1]

    /** 위젯 [id] 가운데의 창 y 좌표. 보드는 문서 단위를 화면 밀도에 맞춰 줄여 그린다. */
    private fun centerY(board: KineticaBoardView, id: String): Float {
        val rect = state.rectOf(id)!!
        val zoom = activity.resources.displayMetrics.density / KineticaBoardState.UNITS
        return windowTop(board) + ((rect.top + rect.bottom) / 2 * zoom).toFloat()
    }

    @Test
    fun `NestedScrollView 안에서는 내용 높이만큼 커지고 부모가 스크롤한다`() {
        val board = board()
        val scroll = NestedScrollView(activity).apply {
            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(activity).apply { text = "header" }, LinearLayout.LayoutParams(MATCH, HEADER_PX))
                    addView(board, LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT))
                },
            )
        }
        activity.setContentView(scroll)
        idle(SETTLE_MS)

        val last = board.findViewWithTag<View>("w5")
        assertThat(board.height).isGreaterThan(activity.window.decorView.height)
        assertThat(last).isNotNull()
        assertThat(last.height).isGreaterThan(0)

        drag(X, 700f, 200f)
        assertThat(scroll.scrollY).isGreaterThan(0)
    }

    @Test
    fun `NestedScrollView 안에서 편집 중 위젯을 세로로 끌면 부모가 가로채지 않고 아래 가장자리에서 부모를 자동 스크롤한다`() {
        val board = board(editing = true)
        val scroll = NestedScrollView(activity).apply {
            addView(board, FrameLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        activity.setContentView(scroll)
        idle(SETTLE_MS)
        val before = state.rectOf("w0")!!
        val startY = centerY(board, "w0")

        val document = state.document.value
        drag(X, startY, startY + MOVE_PX)
        idle(SETTLE_MS)

        assertThat(scroll.scrollY).isEqualTo(0)
        // 부모가 가로챘다면 드래그가 취소되어 배치가 그대로였을 것이다.
        assertThat(state.document.value).isNotEqualTo(document)
        assertThat(state.rectOf("w0")!!.top).isGreaterThan(before.top)
        val moved = state.rectOf("w1")!!

        // 화면 아래 가장자리 바로 안쪽(자동 스크롤 영역)에서 손가락을 멈추고 기다린다.
        val bottom = activity.window.decorView.height.toFloat()
        val fromY = centerY(board, "w1")
        drag(X, fromY, bottom - AUTO_SCROLL_INSET_PX, hold = true)
        idle(AUTO_SCROLL_MS)
        val scrolled = scroll.scrollY
        send(MotionEvent.ACTION_UP, X, bottom - AUTO_SCROLL_INSET_PX)
        idle(SETTLE_MS)

        assertThat(scrolled).isGreaterThan(0)
        assertThat(state.rectOf("w1")!!.top).isGreaterThan(moved.top)
    }

    @Test
    fun `CoordinatorLayout에서 보드를 스크롤하면 AppBarLayout이 함께 접힌다`() {
        val themed = ContextThemeWrapper(activity, com.google.android.material.R.style.Theme_Material3_Light_NoActionBar)
        val appBar = AppBarLayout(themed).apply {
            addView(
                TextView(themed).apply { text = "toolbar" },
                AppBarLayout.LayoutParams(MATCH, APP_BAR_PX).apply {
                    scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
                },
            )
        }
        val board = board()
        activity.setContentView(
            CoordinatorLayout(themed).apply {
                addView(appBar, CoordinatorLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(board, CoordinatorLayout.LayoutParams(MATCH, MATCH).apply { behavior = AppBarLayout.ScrollingViewBehavior() })
            },
        )
        idle(SETTLE_MS)
        assertThat(windowTop(board)).isEqualTo(APP_BAR_PX)

        drag(X, 600f, 300f)
        idle(SETTLE_MS)

        assertThat(windowTop(board)).isLessThan(APP_BAR_PX)
    }

    @Test
    fun `창 크기만 바뀌어도 다시 만들지 않고 새 폭에 맞춰 배치한다`() {
        val board = board()
        activity.setContentView(board)
        idle(SETTLE_MS)
        val widget = board.findViewWithTag<View>("w0")
        val before = widget.width

        val config = android.content.res.Configuration(activity.resources.configuration).apply { screenWidthDp = WIDE_DP }
        org.robolectric.RuntimeEnvironment.setQualifiers("w${WIDE_DP}dp-h800dp-mdpi")
        activity.window.decorView.layoutParams = activity.window.decorView.layoutParams
        controller.configurationChange(config)
        activity.window.decorView.measure(
            View.MeasureSpec.makeMeasureSpec(WIDE_DP, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY),
        )
        activity.window.decorView.layout(0, 0, WIDE_DP, HEIGHT_PX)
        idle(SETTLE_MS)

        assertThat(board.width).isEqualTo(WIDE_DP)
        assertThat(board.findViewWithTag<View>("w0").width).isGreaterThan(before)
    }

    private companion object {
        val CATALOG = (0 until 6).map { BoardWidget("w$it", "위젯 $it", widthDp = 360f, heightDp = 200f) }
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val CONFIG_CHANGES = android.content.pm.ActivityInfo.CONFIG_UI_MODE or android.content.pm.ActivityInfo.CONFIG_ORIENTATION or
            android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE or android.content.pm.ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
            android.content.pm.ActivityInfo.CONFIG_SCREEN_LAYOUT
        const val HEADER_PX = 200
        const val APP_BAR_PX = 160
        const val X = 100f
        const val STEPS = 10
        const val FRAME_MS = 16L
        const val SETTLE_MS = 500L
        const val MOVE_PX = 150f
        const val AUTO_SCROLL_INSET_PX = 60f
        const val AUTO_SCROLL_MS = 800L
        const val WIDE_DP = 700
        const val HEIGHT_PX = 800
    }
}
