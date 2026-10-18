package com.naury.kinetica.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 칩 줄의 스크롤 상태 공개와 칩 색 직접 지정(편집 SDK 요청 9). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ChipRowColorsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `한 줄 칩 묶음은 넘겨준 스크롤 상태로 스크롤한다`() {
        val scroll = ScrollState(0)
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                KChipGroup(
                    List(12) { "비율 $it" },
                    setOf(0),
                    {},
                    Modifier.width(200.dp),
                    wrap = false,
                    scrollState = scroll,
                    fadeEdges = true,
                )
            }
        }
        compose.waitForIdle()
        assertThat(scroll.maxValue).isGreaterThan(0)
    }

    @Test
    fun `선택된 칩은 지정한 색으로 꽉 채운다`() {
        val picked = Color(0xFF00AA55)
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                KFilterChip(
                    "원본",
                    true,
                    {},
                    Modifier.testTag("chip").size(120.dp, 40.dp),
                    colors = KChipColors(selected = picked, onSelected = Color.White),
                )
            }
        }
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("chip").captureToImage().asAndroidBitmap()
        // 칩 가장자리 근처 바탕(글자와 체크를 피한 곳)이 지정한 색이다.
        assertThat(pixels.getPixel(pixels.width - 8, pixels.height / 2)).isEqualTo(picked.toArgb())
    }
}
