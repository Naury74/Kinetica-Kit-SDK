package com.naury.kinetica.ui.view

import android.app.Application
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.SurfaceStyles
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Fragment, ViewPager2, 인셋, 설정 변경을 직접 처리하는 액티비티처럼 XML 앱에서 흔한 구성에 글래스 View와 창을 넣었을 때의 동작. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
class KViewHostsTest {
    // 액티비티는 직접 만들지만 Compose 프레임은 테스트 시계로 돌린다. 기본 프레임 시계는 다른 테스트 뒤에 실행되면 프레임을 받지 못한다.
    @get:Rule
    val compose = createEmptyComposeRule()

    private val controller = run {
        // android:configChanges로 설정 변경을 직접 처리하는 액티비티처럼, 다시 만들지 않고 onConfigurationChanged만 받게 한다.
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).addOrUpdateActivity(
            ActivityInfo().apply {
                name = FragmentActivity::class.java.name
                packageName = app.packageName
                configChanges = CONFIG_CHANGES
            },
        )
        Robolectric.buildActivity(FragmentActivity::class.java).setup()
    }
    private val activity = controller.get()

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
    }

    class SurfaceFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            FrameLayout(inflater.context).apply {
                setBackgroundColor(RED)
                addView(KSurfaceView(inflater.context).apply { tag = GLASS }, FrameLayout.LayoutParams(MATCH, GLASS_PX))
            }
    }

    private fun settle(ms: Long = SETTLE_MS) {
        repeat((ms / FRAME_MS).toInt()) {
            shadowOf(Looper.getMainLooper()).idleFor(FRAME_MS, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(FRAME_MS)
        }
        drawFrame()
    }

    /** Robolectric은 창을 그리지 않아 Compose가 그리기 단계에서 하는 배치가 밀린다. 기기처럼 한 번 그린 뒤 배치를 마무리한다. */
    private fun drawFrame() {
        val decor = activity.window.decorView
        if (decor.width <= 0 || decor.height <= 0) return
        decor.draw(
            android.graphics.Canvas(
                android.graphics.Bitmap.createBitmap(decor.width, decor.height, android.graphics.Bitmap.Config.ARGB_8888),
            ),
        )
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun preDrawListeners(): Int {
        val observer = activity.window.decorView.viewTreeObserver
        val field = ViewTreeObserver::class.java.getDeclaredField("mOnPreDrawListeners").apply { isAccessible = true }
        val listeners = field.get(observer) ?: return 0
        return listeners.javaClass.getDeclaredMethod("size").apply { isAccessible = true }.invoke(listeners) as Int
    }

    private fun content(): ViewGroup = activity.findViewById(android.R.id.content)

    private fun replaceWithBackStack(fragment: Fragment) {
        activity.supportFragmentManager.beginTransaction().replace(CONTAINER_ID, fragment).addToBackStack(null).commit()
        settle()
    }

    @Test
    fun `뒤로 가기는 Fragment back stack보다 시트를 먼저 닫는다`() {
        activity.setContentView(FrameLayout(activity).apply { id = CONTAINER_ID })
        activity.supportFragmentManager.beginTransaction().add(CONTAINER_ID, SurfaceFragment()).commitNow()
        val second = SurfaceFragment()
        replaceWithBackStack(second)
        val sheet = KSheets.show(activity, TextView(activity), KPopupOptions(lifecycleOwner = second.viewLifecycleOwner))
        settle()

        activity.onBackPressedDispatcher.onBackPressed()
        settle()
        assertThat(sheet.isShowing).isFalse()
        assertThat(activity.supportFragmentManager.backStackEntryCount).isEqualTo(1)

        activity.onBackPressedDispatcher.onBackPressed()
        settle()
        assertThat(activity.supportFragmentManager.backStackEntryCount).isEqualTo(0)
    }

    @Test
    fun `시트를 연 Fragment가 back stack으로 들어가면 시트가 정리되고 돌아와도 다시 뜨지 않는다`() {
        activity.setContentView(FrameLayout(activity).apply { id = CONTAINER_ID })
        val first = SurfaceFragment()
        activity.supportFragmentManager.beginTransaction().add(CONTAINER_ID, first).commitNow()
        settle()
        val sheet = KSheets.show(activity, TextView(activity), KPopupOptions(lifecycleOwner = first.viewLifecycleOwner))
        settle()

        replaceWithBackStack(SurfaceFragment())
        assertThat(sheet.isShowing).isFalse()
        assertThat(KViewHost.find(activity)).isNull()
        activity.supportFragmentManager.popBackStackImmediate()
        settle()

        assertThat(KViewHost.find(activity)).isNull()
        assertThat(first.requireView().findViewWithTag<KSurfaceView>(GLASS).isAttachedToWindow).isTrue()
    }

    @Test
    fun `android_R_id_content에 Fragment를 넣어도 열린 시트가 맨 위에 남고 새 화면은 TalkBack에서 가린다`() {
        activity.setContentView(FrameLayout(activity))
        val sheet = KSheets.show(activity, TextView(activity))
        settle()

        val fragment = SurfaceFragment()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        settle()

        val host = KViewHost.find(activity)!!
        assertThat(content().indexOfChild(host)).isEqualTo(content().childCount - 1)
        assertThat(fragment.requireView().importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
        assertThat(sheet.isShowing).isTrue()

        sheet.dismiss()
        settle()
        assertThat(fragment.requireView().importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO)
    }

    @Test
    fun `ViewPager2 페이지의 글래스는 페이지를 오가도 기록 리스너가 늘지 않는다`() {
        val pager = ViewPager2(activity).apply {
            id = CONTAINER_ID
            adapter = object : FragmentStateAdapter(activity) {
                override fun getItemCount(): Int = PAGES

                override fun createFragment(position: Int): Fragment = SurfaceFragment()
            }
        }
        activity.setContentView(pager)
        settle()
        val baseline = preDrawListeners()

        repeat(PAGES * 2) {
            pager.setCurrentItem((it + 1) % PAGES, false)
            settle()
        }

        // 화면에 붙어 있는 페이지 수만큼만 기록한다. ViewPager2는 앞뒤 한 페이지까지 붙여 둘 수 있다.
        assertThat(preDrawListeners()).isAtMost(baseline + 2)
    }

    @Test
    fun `edge-to-edge 화면의 루트가 fitsSystemWindows로 인셋을 써도 시트 입력창은 키보드 위로 올라온다`() = imeAboveKeyboardWithFittingRoot()

    private fun imeAboveKeyboardWithFittingRoot() {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.setContentView(FrameLayout(activity).apply { fitsSystemWindows = true })
        val edit = EditText(activity)
        KSheets.show(activity, edit)
        settle()
        val before = bottomOf(edit)

        dispatchIme()

        assertThat(before - bottomOf(edit)).isAtLeast(IME_PX - IME_SLACK)
        assertThat(bottomOf(edit)).isAtMost(screenHeight() - IME_PX)
    }

    @Test
    fun `시스템이 창을 줄이는 adjustResize 화면에서는 키보드 높이를 두 번 피하지 않는다`() {
        activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        activity.setContentView(FrameLayout(activity))
        val edit = EditText(activity)
        KSheets.show(activity, edit)
        settle()
        val before = bottomOf(edit)

        dispatchIme()

        val moved = before - bottomOf(edit)
        assertThat(moved).isAtLeast(IME_PX - IME_SLACK)
        assertThat(moved).isAtMost(IME_PX + IME_SLACK)
    }

    @Test
    fun `설정 변경을 직접 처리하는 액티비티에서 다크 모드와 화면 크기가 바뀌면 글래스와 열린 시트가 따라 바뀐다`() {
        val glass = KSurfaceView(activity)
        val colors = mutableListOf<Int>()
        glass.setOnContentColorChangeListener { colors += it }
        activity.setContentView(FrameLayout(activity).apply { addView(glass, FrameLayout.LayoutParams(MATCH, GLASS_PX)) })
        val inner = TextView(activity).apply { layoutParams = ViewGroup.LayoutParams(MATCH, GLASS_PX) }
        val sheet = KSheets.show(activity, inner)
        settle()
        assertThat(glass.drawnStyle).isEqualTo(SurfaceStyles.Liquid)
        val narrow = inner.width

        RuntimeEnvironment.setQualifiers("+w640dp-h360dp-land-night")
        val config = Configuration(activity.resources.configuration)
        controller.configurationChange(config)
        val decor = activity.window.decorView
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(WIDE_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(SHORT_PX, View.MeasureSpec.EXACTLY),
        )
        decor.layout(0, 0, WIDE_PX, SHORT_PX)
        settle()

        assertThat(activity.isFinishing).isFalse()
        assertThat(sheet.isShowing).isTrue()
        assertThat(glass.drawnStyle).isEqualTo(SurfaceStyles.LiquidDark)
        assertThat(KViewHost.find(activity)!!.drawnFallbackStyle).isEqualTo(SurfaceStyles.LiquidDark)
        assertThat(colors).containsExactly(GlassStyles.LiquidDark.onGlass.toArgb())
        assertThat(inner.width).isNotEqualTo(narrow)
    }

    private fun dispatchIme() {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, IME_PX))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        activity.window.decorView.dispatchApplyWindowInsets(insets.toWindowInsets())
        settle()
    }

    private fun bottomOf(view: View): Int = IntArray(2).also(view::getLocationInWindow)[1] + view.height

    private fun screenHeight(): Int = activity.window.decorView.height

    companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val RED = 0xFFFF0000.toInt()
        const val GLASS = "glass"
        const val GLASS_PX = 120
        const val CONTAINER_ID = 0x7f0b3001
        const val PAGES = 4
        const val FRAME_MS = 16L
        const val SETTLE_MS = 640L
        const val IME_PX = 260
        const val IME_SLACK = 40
        const val WIDE_PX = 640
        const val SHORT_PX = 360
        const val CONFIG_CHANGES = ActivityInfo.CONFIG_UI_MODE or ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_SCREEN_SIZE or
            ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or ActivityInfo.CONFIG_SCREEN_LAYOUT
    }
}
