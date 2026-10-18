package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 호스트가 [SurfaceStyle.Solid]에 준 보조 글자 색과 올라온 면 색을 컴포넌트가 그대로 쓰는지, 주지 않으면 전처럼 옅게 그리는지. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SurfaceMutedColorTest {
    @get:Rule
    val compose = createComposeRule()

    // 제목 글자의 가장자리 보간이 회색 화소로 섞이지 않도록 제목은 빈칸으로 둔다.
    private val plain = SurfaceStyle.Solid(color = Color.White, contentColor = Color.Black, background = Color.White)

    @Test
    fun listItemSupportingUsesHostMutedColor() {
        val image = capture { KListItem(" ", supporting = SUPPORTING, surface = plain.copy(contentMuted = HOST_MUTED)) }
        assertThat(image.count(HOST_MUTED)).isGreaterThan(MIN_PIXELS)
        assertThat(image.count(FADED_INK)).isEqualTo(0)
    }

    @Test
    fun listItemSupportingKeepsFadedInkWithoutHostColor() {
        val image = capture { KListItem(" ", supporting = SUPPORTING, surface = plain) }
        assertThat(image.count(FADED_INK)).isGreaterThan(MIN_PIXELS)
        assertThat(image.count(HOST_MUTED)).isEqualTo(0)
    }

    @Test
    fun progressTrackUsesHostRaisedColor() {
        val image = capture { KProgressBar(0f, surface = plain.copy(raised = HOST_RAISED)) }
        assertThat(image.count(HOST_RAISED)).isGreaterThan(MIN_PIXELS)
    }

    @Test
    fun progressTrackKeepsFadedInkWithoutHostColor() {
        val image = capture { KProgressBar(0f, surface = plain) }
        assertThat(image.count(HOST_RAISED)).isEqualTo(0)
        assertThat(image.count(Color.White)).isLessThan(image.width * image.height)
    }

    private fun capture(content: @Composable () -> Unit): ImageBitmap {
        compose.setContent {
            // 글자 획 안쪽이 경계 보간 없이 한 색으로 칠해지도록 크게 그린다.
            CompositionLocalProvider(LocalDensity provides Density(4f)) {
                KineticaTheme {
                    Box(Modifier.testTag(TAG).width(200.dp).background(Color.White)) { content() }
                }
            }
        }
        compose.waitForIdle()
        return compose.onNodeWithTag(TAG).captureToImage()
    }

    private fun ImageBitmap.count(color: Color): Int {
        val pixels = toPixelMap()
        var hits = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val p = pixels[x, y]
                if (abs(p.red - color.red) < TOLERANCE && abs(p.green - color.green) < TOLERANCE &&
                    abs(p.blue - color.blue) < TOLERANCE
                ) {
                    hits++
                }
            }
        }
        return hits
    }

    private companion object {
        const val TAG = "subject"
        const val SUPPORTING = "MMMMMM"
        const val TOLERANCE = 0.03f
        const val MIN_PIXELS = 50
        val HOST_MUTED = Color(0xFFE0301E)
        val HOST_RAISED = Color(0xFF1E8FE0)

        // KListItem의 보조 글자는 검정을 0.62만큼 흰 바탕에 얹은 회색이다.
        val FADED_INK = Color(red = 0.38f, green = 0.38f, blue = 0.38f)
    }
}
