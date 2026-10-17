package com.naury.kinetica.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class KIconsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun allListsEveryIconOnceWithItsPropertyName() {
        val all = KIcons.all
        assertThat(all).hasSize(63)
        assertThat(all.map { it.first }.toSet()).hasSize(all.size)
        assertThat(all.map { it.second }.toSet()).hasSize(all.size)
        all.forEach { (name, icon) ->
            assertThat(icon.viewportWidth).isEqualTo(24f)
            assertThat(icon.viewportHeight).isEqualTo(24f)
            assertThat(icon.defaultWidth).isEqualTo(24.dp)
            assertThat(icon.name).isNotEmpty()
            assertThat(name).isNotEmpty()
        }
        // 같은 아이콘을 두 번 읽어도 한 번 만든 것을 돌려준다.
        assertThat(KIcons.Search).isSameInstanceAs(KIcons.Search)
    }

    @Test
    fun onlyDirectionalIconsMirrorInRtl() {
        val mirrored = KIcons.all.filter { it.second.autoMirror }.map { it.first }
        assertThat(mirrored).containsExactly("ChevronBack", "ChevronForward", "ArrowBack", "ArrowForward", "Send")
    }

    @Test
    fun docExampleShowsIconsInsideKitButtons() {
        compose.setContent {
            KineticaTheme {
                androidx.compose.foundation.layout.Column {
                    KIconButton(KIcons.Share, "공유", onClick = {})
                    KButton("장바구니에 담기", onClick = {}, icon = KIcons.Cart)
                }
            }
        }
        compose.onNodeWithContentDescription("공유").assertExists()
        compose.onNodeWithText("장바구니에 담기").assertExists()
    }

    @Test
    fun forwardArrowPointsToTheEndSideInBothDirections() {
        val ltr = render(KIcons.ArrowForward, LayoutDirection.Ltr)
        val rtl = render(KIcons.ArrowForward, LayoutDirection.Rtl)
        // 화살촉이 있는 쪽 3분의 1에 잉크가 더 많다.
        assertThat(ink(ltr, end = true)).isGreaterThan(ink(ltr, end = false))
        assertThat(ink(rtl, end = false)).isGreaterThan(ink(rtl, end = true))
    }

    @Test
    fun everyIconDrawsInkInsideItsBox() {
        KIcons.all.forEach { (name, icon) ->
            val bitmap = render(icon, LayoutDirection.Ltr)
            val total = ink(bitmap, end = true) + ink(bitmap, end = false) + inkMiddle(bitmap)
            assertWithMessage(name).that(total).isGreaterThan(0)
            // 선이 상자 가장자리 한 줄에 닿지 않을 만큼 여백이 있다.
            assertWithMessage(name).that(edgeInk(bitmap)).isEqualTo(0)
        }
    }

    private val current = mutableStateOf(KIcons.Home to LayoutDirection.Ltr)
    private var started = false

    private fun render(icon: ImageVector, direction: LayoutDirection): Bitmap {
        current.value = icon to direction
        if (!started) {
            started = true
            compose.setContent {
                val (vector, dir) = current.value
                CompositionLocalProvider(LocalLayoutDirection provides dir) {
                    Box(Modifier.testTag("icon").background(Color.White)) {
                        Image(vector, null, Modifier.size(48.dp), colorFilter = ColorFilter.tint(Color.Black))
                    }
                }
            }
        }
        compose.waitForIdle()
        return compose.onNodeWithTag("icon").captureToImage().asAndroidBitmap()
    }

    private fun isInk(pixel: Int): Boolean = android.graphics.Color.red(pixel) < INK_THRESHOLD

    private fun ink(bitmap: Bitmap, end: Boolean): Int {
        val third = bitmap.width / 3
        val xs = if (end) (bitmap.width - third) until bitmap.width else 0 until third
        return xs.sumOf { x -> (0 until bitmap.height).count { y -> isInk(bitmap.getPixel(x, y)) } }
    }

    private fun inkMiddle(bitmap: Bitmap): Int {
        val third = bitmap.width / 3
        return (third until bitmap.width - third).sumOf { x -> (0 until bitmap.height).count { y -> isInk(bitmap.getPixel(x, y)) } }
    }

    private fun edgeInk(bitmap: Bitmap): Int {
        val w = bitmap.width
        val h = bitmap.height
        val border = (0 until w).flatMap { listOf(it to 0, it to h - 1) } + (0 until h).flatMap { listOf(0 to it, w - 1 to it) }
        return border.count { (x, y) -> isInk(bitmap.getPixel(x, y)) }
    }

    private companion object {
        const val INK_THRESHOLD = 128
    }
}
