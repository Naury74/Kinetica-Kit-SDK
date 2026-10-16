package com.naury.kinetica.ui.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.ui.Detent
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KDetentSheet
import com.naury.kinetica.ui.KDialog
import com.naury.kinetica.ui.KSheet
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.SurfaceStyles
import com.naury.kinetica.ui.glassBackdrop
import com.naury.kinetica.ui.rememberGlassBackdrop
import com.naury.kinetica.ui.rememberKDetentSheetState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose 글래스 컴포넌트 안팎에 호스트 앱의 View를 AndroidView로 넣었을 때를 확인한다.
 * Robolectric은 RenderEffect 블러까지 그려 주지만 SurfaceView가 있는 창은 캡처하지 못하므로, SurfaceView는 그려지는지만 본다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
class AndroidViewInSurfaceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `KineticaScaffold 배경의 AndroidView도 블러로 비치고 View가 바뀌면 글래스도 따라 바뀐다`() {
        lateinit var left: View
        compose.setContent {
            Box(Modifier.size(SIDE.dp).testTag("root")) {
                KineticaScaffold(
                    Modifier.fillMaxSize(),
                    surface = SurfaceStyles.Liquid,
                    background = {
                        Row(Modifier.fillMaxSize()) {
                            AndroidView(
                                { context -> View(context).apply { setBackgroundColor(RED) }.also { left = it } },
                                Modifier.weight(1f).fillMaxHeight(),
                            )
                            Box(Modifier.weight(1f).fillMaxHeight().background(Color.Blue))
                        }
                    },
                ) {
                    Box(Modifier.fillMaxSize()) { KSurface(Modifier.align(Alignment.Center).size(GLASS_W.dp, GLASS_H.dp)) {} }
                }
            }
        }
        val before = compose.onNodeWithTag("root").captureToImage().toPixelMap()
        assertThat(isMixed(before, SIDE / 2, SIDE / 2)).isTrue()

        compose.runOnUiThread { left.setBackgroundColor(GREEN) }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("root").captureToImage().toPixelMap()
        val inside = after[SIDE / 2 - GLASS_W / 4, SIDE / 2]
        assertThat(inside.green).isGreaterThan(inside.red)
        assertThat(inside.green).isGreaterThan(GREEN_MIN)
    }

    @Test
    fun `다른 창(Popup)에 띄운 글래스도 화면 좌표로 배경의 같은 위치를 비춘다`() {
        compose.setContent {
            val backdrop = rememberGlassBackdrop()
            Box(Modifier.padding(top = POPUP_TOP.dp).size(SIDE.dp)) {
                Row(Modifier.fillMaxSize().glassBackdrop(backdrop)) {
                    AndroidView({ View(it).apply { setBackgroundColor(RED) } }, Modifier.weight(1f).fillMaxHeight())
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color.Blue))
                }
                Popup(alignment = Alignment.Center) {
                    KSurface(Modifier.size(GLASS_H.dp).testTag("popup-glass"), backdrop = backdrop) {}
                }
            }
        }
        val image = compose.onNodeWithTag("popup-glass").captureToImage().toPixelMap()
        assertThat(isMixed(image, image.width / 2, image.height / 2)).isTrue()
    }

    @Test
    fun `시트와 다이얼로그 안의 입력창과 스크롤 View가 입력을 받는다`() {
        val sheet = mutableStateOf(true)
        val dialog = mutableStateOf(false)
        lateinit var edit: EditText
        lateinit var scroll: ScrollView
        compose.setContent {
            KineticaScaffold(Modifier.fillMaxSize()) {
                KSheet(sheet.value, { sheet.value = false }) {
                    AndroidView({ EditText(it).also { view -> edit = view } }, Modifier.fillMaxWidth())
                }
                KDialog(dialog.value, { dialog.value = false }) {
                    AndroidView({ context -> tallScroll(context).also { scroll = it } }, Modifier.height(SCROLL_H.dp).testTag("scroll"))
                }
            }
        }
        compose.runOnUiThread {
            assertThat(edit.requestFocus()).isTrue()
            edit.onCreateInputConnection(EditorInfo())!!.commitText("글래스", 1)
        }
        assertThat(edit.text.toString()).isEqualTo("글래스")

        sheet.value = false
        dialog.value = true
        compose.waitForIdle()
        compose.onNodeWithTag("scroll").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertThat(scroll.scrollY).isGreaterThan(0)
    }

    @Test
    fun `디텐트 시트와 스캐폴드 콘텐츠에 SurfaceView, TextureView, WebView를 넣어도 그려진다`() {
        val views = mutableListOf<View>()
        compose.setContent {
            KineticaScaffold(
                Modifier.fillMaxSize(),
                background = { AndroidView({ SurfaceView(it).also(views::add) }, Modifier.fillMaxSize()) },
            ) {
                Column(Modifier.fillMaxSize()) {
                    AndroidView({ TextureView(it).also(views::add) }, Modifier.fillMaxWidth().height(SCROLL_H.dp))
                    KSurface(Modifier.fillMaxWidth().height(GLASS_H.dp)) {
                        AndroidView({ WebView(it).also(views::add) }, Modifier.fillMaxSize())
                    }
                }
                KDetentSheet(rememberKDetentSheetState(initialDetent = Detent.Full)) {
                    AndroidView({ context -> tallScroll(context).also(views::add) }, Modifier.fillMaxSize())
                }
            }
        }
        compose.waitForIdle()
        assertThat(views).hasSize(VIEW_COUNT)
        views.forEach { assertThat(it.isAttachedToWindow).isTrue() }
    }

    @Test
    fun `소프트웨어 캔버스에서도 AndroidView를 비추는 글래스를 그린다`() {
        lateinit var root: View
        compose.setContent {
            root = LocalView.current
            KineticaScaffold(
                Modifier.fillMaxSize(),
                background = { AndroidView({ View(it).apply { setBackgroundColor(RED) } }, Modifier.fillMaxSize()) },
            ) {
                Box(Modifier.fillMaxSize()) { KSurface(Modifier.align(Alignment.Center).size(GLASS_W.dp, GLASS_H.dp)) {} }
            }
        }
        compose.runOnUiThread {
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val center = bitmap.getPixel(root.width / 2, root.height / 2)
            assertThat(android.graphics.Color.red(center)).isGreaterThan(RED_MIN)
        }
    }

    private fun tallScroll(context: android.content.Context): ScrollView {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        repeat(ROWS) { column.addView(TextView(context).apply { text = "행 $it" }, LinearLayout.LayoutParams(-1, ROW_PX)) }
        return ScrollView(context).apply { addView(column) }
    }

    private companion object {
        const val SIDE = 300
        const val POPUP_TOP = 120
        const val GLASS_W = 200
        const val GLASS_H = 100
        const val SCROLL_H = 240
        const val ROWS = 40
        const val ROW_PX = 100
        const val VIEW_COUNT = 4
        const val RED = 0xFFFF0000.toInt()
        const val GREEN = 0xFF00C000.toInt()
        const val GREEN_MIN = 0.4f
        const val RED_MIN = 150
    }
}
