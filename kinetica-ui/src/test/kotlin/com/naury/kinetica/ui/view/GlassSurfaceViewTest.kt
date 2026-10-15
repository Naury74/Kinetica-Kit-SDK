package com.naury.kinetica.ui.view

import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
class GlassSurfaceViewTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `XML 속성으로 스타일과 모서리를 정하고 잘못된 반경은 거부한다`() {
        val activity = compose.activity
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.kinetica_glassStyle, "acrylic")
            .addAttribute(R.attr.kinetica_cornerRadius, "12dp")
            .build()
        val view = GlassSurfaceView(activity, attrs)

        assertThat(view.glassStyle).isEqualTo(GlassStyles.Acrylic)
        assertThat(view.cornerRadius).isEqualTo(12f * activity.resources.displayMetrics.density)
        assertThat(view.contentColor).isEqualTo(GlassStyles.Acrylic.onGlass.toArgb())
        assertThat(runCatching { view.cornerRadius = -1f }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)

        val auto = GlassSurfaceView(activity)
        assertThat(auto.glassStyle).isNull()
        assertThat(auto.contentColor).isEqualTo(GlassStyles.Liquid.onGlass.toArgb())
    }

    @Test
    @Config(qualifiers = "night")
    fun `auto 스타일은 다크 모드에서 LiquidDark를 쓴다`() {
        assertThat(GlassSurfaceView(compose.activity).contentColor).isEqualTo(GlassStyles.LiquidDark.onGlass.toArgb())
    }

    @Test
    fun `앞선 형제 View를 블러로 비추고 자식은 모서리 모양으로 잘린다`() {
        lateinit var glass: GlassSurfaceView
        compose.runOnUiThread {
            val activity = compose.activity
            val split = LinearLayout(activity).apply {
                addView(View(activity).apply { setBackgroundColor(RED) }, LinearLayout.LayoutParams(0, MATCH, 1f))
                addView(View(activity).apply { setBackgroundColor(BLUE) }, LinearLayout.LayoutParams(0, MATCH, 1f))
            }
            glass = GlassSurfaceView(activity).apply {
                glassStyle = GlassStyles.Liquid
                addView(TextView(activity).apply { text = "글래스 카드" })
            }
            activity.setContentView(
                FrameLayout(activity).apply {
                    addView(split, FrameLayout.LayoutParams(MATCH, MATCH))
                    addView(glass, FrameLayout.LayoutParams(GLASS_PX, GLASS_PX / 2, android.view.Gravity.CENTER))
                },
            )
        }
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().toPixelMap()

        assertThat(isMixed(image, image.width / 2, image.height / 2)).isTrue()
        // 모서리 밖은 글래스 없이 뒤의 빨강이 그대로 보인다.
        assertThat(image[0, 0].toArgb()).isEqualTo(RED)
        assertThat(glass.usesFallback).isFalse()
    }

    @Test
    fun `뒤의 목록이 스크롤되면 글래스도 새 내용을 비춘다`() {
        lateinit var scroll: ScrollView
        compose.runOnUiThread {
            val activity = compose.activity
            val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            repeat(ROWS) { column.addView(View(activity).apply { setBackgroundColor(if (it < ROWS / 2) RED else BLUE) }, -1, ROW_PX) }
            scroll = ScrollView(activity).apply { addView(column) }
            activity.setContentView(
                FrameLayout(activity).apply {
                    addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
                    addView(GlassSurfaceView(activity), FrameLayout.LayoutParams(GLASS_PX, GLASS_PX / 2, android.view.Gravity.CENTER))
                },
            )
        }
        compose.waitForIdle()
        assertThat(center(compose.onRoot().captureToImage().toPixelMap()).red).isGreaterThan(MOSTLY)

        compose.runOnUiThread { scroll.scrollTo(0, ROW_PX * ROWS / 2) }
        compose.waitForIdle()
        val after = center(compose.onRoot().captureToImage().toPixelMap())
        assertThat(after.blue).isGreaterThan(MOSTLY)
        assertThat(after.red).isLessThan(1f - MOSTLY)
    }

    @Test
    fun `뒤에 SurfaceView가 있으면 fallback 색을 칠한다`() {
        lateinit var glass: GlassSurfaceView
        compose.runOnUiThread {
            val activity = compose.activity
            glass = GlassSurfaceView(activity)
            activity.setContentView(
                FrameLayout(activity).apply {
                    addView(SurfaceView(activity), FrameLayout.LayoutParams(MATCH, MATCH))
                    addView(glass, FrameLayout.LayoutParams(GLASS_PX, GLASS_PX / 2, android.view.Gravity.CENTER))
                },
            )
        }
        compose.waitForIdle()
        assertThat(glass.usesFallback).isTrue()
    }

    private fun center(image: PixelMap) = image[image.width / 2, image.height / 2]

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val RED = 0xFFFF0000.toInt()
        const val BLUE = 0xFF0000FF.toInt()
        const val GLASS_PX = 240
        const val ROWS = 20
        const val ROW_PX = 100
        const val MOSTLY = 0.6f
    }
}
