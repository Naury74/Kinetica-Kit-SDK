package com.naury.kinetica.ui.view

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.SurfaceStyles
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class KPopupsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val pane = SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)

    /** 왼쪽은 빨강, 오른쪽은 파랑인 XML 화면. 경계에서 색이 섞이면 블러가 된 것이다. */
    private fun setSplitContent(activity: Activity, extra: View? = null) {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(View(activity).apply { setBackgroundColor(RED) }, LinearLayout.LayoutParams(0, MATCH, 1f))
            addView(View(activity).apply { setBackgroundColor(BLUE) }, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
        val frame = FrameLayout(activity).apply {
            addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
            extra?.let { addView(it, FrameLayout.LayoutParams(SURFACE_PX, SURFACE_PX)) }
        }
        activity.setContentView(frame)
    }

    private fun label(activity: Activity, text: String, heightPx: Int = SHEET_PX) = TextView(activity).apply {
        this.text = text
        layoutParams = ViewGroup.LayoutParams(MATCH, heightPx)
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
    }

    @Test
    fun `시트는 아래 XML 화면을 블러 처리해 비추고 dismiss하면 리스너를 한 번 부른 뒤 정리된다`() {
        val activity = compose.activity
        lateinit var handle: KPopupHandle
        var dismissed = 0
        lateinit var inner: TextView
        compose.runOnUiThread {
            setSplitContent(activity)
            inner = label(activity, "시트 안")
            handle = KSheets.show(activity, inner, KPopupOptions(title = "공유"))
            handle.setOnDismissListener { dismissed++ }
        }
        settle()

        assertThat(handle.isShowing).isTrue()
        assertThat(inner.isAttachedToWindow).isTrue()
        val image = compose.onNode(pane).captureToImage().toPixelMap()
        val y = image.height - SHEET_PX / 2
        assertThat(isMixed(image, image.width / 2, y)).isTrue()
        assertThat(KViewHost.find(activity)!!.usesFallback).isFalse()

        compose.runOnUiThread {
            handle.dismiss()
            handle.dismiss()
        }
        assertThat(handle.isShowing).isFalse()
        settle()

        assertThat(dismissed).isEqualTo(1)
        assertThat(KViewHost.find(activity)).isNull()
        assertThat(handle.contentView).isNull()
        assertThat(inner.isAttachedToWindow).isFalse()
    }

    @Test
    fun `입력란에 포커스가 있는 채 시트를 닫으면 포커스를 거둬 키보드가 남지 않는다`() {
        val activity = compose.activity
        lateinit var handle: KPopupHandle
        lateinit var input: android.widget.EditText
        compose.runOnUiThread {
            setSplitContent(activity)
            input = android.widget.EditText(activity)
            handle = KSheets.show(activity, input)
        }
        settle()
        compose.runOnUiThread { input.requestFocus() }
        settle()
        assertThat(input.hasFocus()).isTrue()

        compose.runOnUiThread { handle.dismiss() }
        settle()

        assertThat(input.hasFocus()).isFalse()
    }

    @Test
    fun `뒤에 SurfaceView가 있으면 블러 대신 fallback 색을 칠한다`() {
        val activity = compose.activity
        compose.runOnUiThread {
            setSplitContent(activity, SurfaceView(activity))
            KSheets.show(activity, label(activity, "지도 위 시트"), KPopupOptions(title = "지도"))
        }
        settle()

        // SurfaceView가 있는 창은 Robolectric의 PixelCopy가 캡처하지 못하므로 상태로 확인한다.
        assertThat(KViewHost.find(activity)!!.usesFallback).isTrue()
    }

    @Test
    fun `뒤로 가기는 시트를 닫고, 시트를 연 뒤 등록한 콜백이 있으면 그 콜백이 먼저 받는다`() {
        val activity = compose.activity
        var before = 0
        var after = 0
        lateinit var first: KPopupHandle
        compose.runOnUiThread {
            setSplitContent(activity)
            activity.onBackPressedDispatcher.addCallback(activity, callback { before++ })
            first = KSheets.show(activity, label(activity, "첫 시트"))
        }
        settle()
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        assertThat(first.isShowing).isFalse()
        assertThat(before).isEqualTo(0)

        lateinit var second: KPopupHandle
        compose.runOnUiThread { second = KDialogs.show(activity, label(activity, "다이얼로그")) }
        settle()
        compose.runOnUiThread {
            activity.onBackPressedDispatcher.addCallback(activity, callback { after++ })
            activity.onBackPressedDispatcher.onBackPressed()
        }
        settle()
        assertThat(after).isEqualTo(1)
        assertThat(second.isShowing).isTrue()
    }

    @Test
    fun `cancelable이 false면 뒤로 가기와 바깥 누르기로 닫히지 않는다`() {
        val activity = compose.activity
        var before = 0
        lateinit var handle: KPopupHandle
        compose.runOnUiThread {
            setSplitContent(activity)
            activity.onBackPressedDispatcher.addCallback(activity, callback { before++ })
            handle = KDialogs.show(activity, label(activity, "필수 동의"), KPopupOptions(cancelable = false))
        }
        settle()
        compose.runOnUiThread {
            activity.onBackPressedDispatcher.onBackPressed()
            val decor = activity.window.decorView
            tap(decor, decor.width / 2f, TOP_TAP_PX)
        }
        settle()

        assertThat(handle.isShowing).isTrue()
        assertThat(before).isEqualTo(0)
    }

    @Test
    fun `회전으로 액티비티가 다시 만들어지면 리스너 없이 닫히고 새 액티비티에는 남지 않는다`() {
        val old = compose.activity
        var dismissed = 0
        lateinit var handle: KPopupHandle
        compose.runOnUiThread {
            setSplitContent(old)
            handle = KSheets.show(old, label(old, "회전 전"))
            handle.setOnDismissListener { dismissed++ }
            KToasts.show(old, "회전 전 토스트")
        }
        settle()

        compose.activityRule.scenario.recreate()
        settle()

        assertThat(handle.isShowing).isFalse()
        assertThat(handle.contentView).isNull()
        assertThat(dismissed).isEqualTo(0)
        assertThat(KViewHost.find(old)).isNull()
        assertThat(KViewHost.find(compose.activity)).isNull()
        assertThat(compose.activity).isNotSameInstanceAs(old)
    }

    @Test
    fun `Fragment처럼 넘긴 lifecycleOwner가 끝나면 시트를 정리한다`() {
        val activity = compose.activity
        val owner = FakeOwner()
        var dismissed = 0
        lateinit var handle: KPopupHandle
        compose.runOnUiThread {
            setSplitContent(activity)
            owner.registry.currentState = Lifecycle.State.RESUMED
            handle = KSheets.show(activity, label(activity, "프래그먼트 시트"), KPopupOptions(lifecycleOwner = owner))
            handle.setOnDismissListener { dismissed++ }
        }
        settle()
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.DESTROYED }
        settle()

        assertThat(handle.isShowing).isFalse()
        assertThat(dismissed).isEqualTo(0)
        assertThat(KViewHost.find(activity)).isNull()
        assertThat(owner.registry.observerCount).isEqualTo(0)
    }

    @Test
    fun `시트 안 입력창은 포커스와 IME 입력을 받고 다이얼로그 안 ScrollView는 스크롤된다`() {
        val activity = compose.activity
        lateinit var edit: EditText
        lateinit var scroll: ScrollView
        compose.runOnUiThread {
            setSplitContent(activity)
            edit = EditText(activity)
            KSheets.show(activity, edit, KPopupOptions(title = "입력"))
        }
        settle()
        compose.runOnUiThread {
            assertThat(edit.requestFocus()).isTrue()
            edit.onCreateInputConnection(EditorInfo())!!.commitText("안녕", 1)
        }
        assertThat(edit.isFocused).isTrue()
        assertThat(edit.text.toString()).isEqualTo("안녕")

        compose.runOnUiThread {
            val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            repeat(ROWS) { column.addView(label(activity, "행 $it", ROW_PX)) }
            scroll = ScrollView(activity).apply {
                addView(column)
                layoutParams = ViewGroup.LayoutParams(MATCH, SHEET_PX * 2)
            }
            KDialogs.show(activity, scroll, KPopupOptions(title = "목록"))
        }
        settle()
        compose.runOnUiThread {
            val decor = activity.window.decorView
            val at = IntArray(2).also(scroll::getLocationInWindow)
            drag(decor, at[0] + scroll.width / 2f, at[1] + scroll.height * DRAG_FROM, at[1] + scroll.height * DRAG_TO)
        }
        settle()
        assertThat(scroll.scrollY).isGreaterThan(0)
    }

    @Test
    fun `edge-to-edge 화면에서 키보드가 올라오면 시트가 키보드 위로 올라간다`() {
        val activity = compose.activity
        lateinit var edit: EditText
        compose.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            setSplitContent(activity)
            edit = EditText(activity)
            KSheets.show(activity, edit, KPopupOptions(title = "입력"))
        }
        settle()
        val before = IntArray(2).also(edit::getLocationInWindow)[1]
        compose.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, IME_PX))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            activity.window.decorView.dispatchApplyWindowInsets(insets.toWindowInsets())
        }
        settle()
        val after = IntArray(2).also(edit::getLocationInWindow)[1]

        assertThat(before - after).isAtLeast(IME_PX - IME_SLACK)
    }

    @Test
    fun `토스트는 차례로 보이고 터치를 막지 않으며 다 사라지면 오버레이가 떨어진다`() {
        val activity = compose.activity
        var clicks = 0
        compose.runOnUiThread {
            val under = View(activity).apply {
                setBackgroundColor(RED)
                setOnClickListener { clicks++ }
            }
            activity.setContentView(under)
            KToasts.show(activity, "첫째", TOAST_MS)
            KToasts.show(activity, "둘째", TOAST_MS)
        }
        compose.waitForIdle()
        compose.onNodeWithText("첫째").assertExists()
        compose.onNodeWithText("둘째").assertDoesNotExist()
        compose.runOnUiThread {
            val decor = activity.window.decorView
            tap(decor, decor.width / 2f, decor.height / 2f)
        }
        shadowIdle()
        assertThat(clicks).isEqualTo(1)

        compose.mainClock.advanceTimeBy(TOAST_MS + TOAST_PEEK_MS)
        compose.waitForIdle()
        compose.onNodeWithText("둘째").assertExists()
        compose.mainClock.advanceTimeBy(TOAST_MS + SETTLE_MS)
        shadowIdle()
        assertThat(KViewHost.find(activity)).isNull()
    }

    @Test
    fun `시트가 열린 화면을 소프트웨어 캔버스에 그려도 예외가 없다`() {
        val activity = compose.activity
        compose.runOnUiThread {
            setSplitContent(activity)
            KSheets.show(activity, label(activity, "캡처"))
        }
        settle()
        compose.runOnUiThread {
            val decor = activity.window.decorView
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            assertThat(bitmap.getPixel(1, 1)).isNotEqualTo(0)
        }
    }

    @Test
    fun `잘못된 호출은 계약대로 예외를 던진다`() {
        val activity = compose.activity
        compose.runOnUiThread {
            val plain = Robolectric.buildActivity(Activity::class.java).setup().get()
            assertThat(runCatching { KSheets.show(plain, View(plain)) }.exceptionOrNull())
                .isInstanceOf(IllegalArgumentException::class.java)
            val attached = View(activity)
            FrameLayout(activity).addView(attached)
            assertThat(runCatching { KDialogs.show(activity, attached) }.exceptionOrNull())
                .isInstanceOf(IllegalArgumentException::class.java)
            assertThat(runCatching { KToasts.show(activity, "x", 0) }.exceptionOrNull())
                .isInstanceOf(IllegalArgumentException::class.java)
        }
        var offMain: Throwable? = null
        Thread { offMain = runCatching { KToasts.show(activity, "x") }.exceptionOrNull() }.apply {
            start()
            join()
        }
        assertThat(offMain).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `앱이 setContentView를 다시 부르면 열린 창을 정리한다`() {
        val activity = compose.activity
        lateinit var handle: KPopupHandle
        compose.runOnUiThread {
            setSplitContent(activity)
            handle = KSheets.show(activity, label(activity, "교체 전"))
        }
        settle()
        compose.runOnUiThread { setSplitContent(activity) }
        settle()

        assertThat(handle.isShowing).isFalse()
        assertThat(KViewHost.find(activity)).isNull()
    }

    @Test
    fun `끝난 액티비티에 띄우면 닫힌 핸들을 돌려준다`() {
        val activity = compose.activity
        compose.runOnUiThread {
            activity.finish()
            val handle = KSheets.show(activity, View(activity), KPopupOptions(SurfaceStyles.Acrylic))
            assertThat(handle.isShowing).isFalse()
            assertThat(KViewHost.find(activity)).isNull()
        }
    }

    private fun callback(onBack: () -> Unit) = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = onBack()
    }

    private fun tap(target: View, x: Float, y: Float) {
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0, TAP_MS, MotionEvent.ACTION_UP, x, y, 0)
        target.dispatchTouchEvent(down)
        target.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun drag(target: View, x: Float, fromY: Float, toY: Float) {
        var time = 0L
        fun send(action: Int, y: Float) {
            val event = MotionEvent.obtain(0, time, action, x, y, 0)
            target.dispatchTouchEvent(event)
            event.recycle()
            time += FRAME_MS
        }
        send(MotionEvent.ACTION_DOWN, fromY)
        repeat(DRAG_STEPS) { send(MotionEvent.ACTION_MOVE, fromY + (toY - fromY) * (it + 1) / DRAG_STEPS) }
        send(MotionEvent.ACTION_UP, toY)
    }

    private fun shadowIdle() = org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()

    private class FakeOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val RED = 0xFFFF0000.toInt()
        const val BLUE = 0xFF0000FF.toInt()
        const val SHEET_PX = 240
        const val ROW_PX = 120
        const val ROWS = 30
        const val SURFACE_PX = 40
        const val EDGE_GAP = 30
        const val TOP_TAP_PX = 60f
        const val SETTLE_MS = 1_000L
        const val TOAST_MS = 3_000L
        const val TAP_MS = 50L
        const val IME_PX = 500
        const val IME_SLACK = 40
        const val TOAST_PEEK_MS = 600L
        const val FRAME_MS = 16L
        const val DRAG_STEPS = 10
        const val DRAG_FROM = 0.8f
        const val DRAG_TO = 0.2f
    }
}

/** 빨강과 파랑이 함께 비치는지. 블러가 경계를 넘어 색을 섞었는지 본다. */
internal fun isMixed(image: PixelMap, x: Int, y: Int): Boolean {
    val color = image[x, y]
    return color.red > MIX_MIN && color.blue > MIX_MIN && kotlin.math.abs(color.red - color.blue) < MIX_GAP
}

internal fun ImageBitmap.pixels(): PixelMap = toPixelMap()

private const val MIX_MIN = 0.3f
private const val MIX_GAP = 0.35f
