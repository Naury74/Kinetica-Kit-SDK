package com.naury.kinetica.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
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

/** 시간 캡슐이 표면 글자색을 따르고, 막대형이 글자 폭을 잃지 않는지. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TimePillsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `막대형 카운트다운 캡슐은 숫자 폭만큼 넓다`() {
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                KCountdownPill(System.currentTimeMillis() + 45_000L, 45_000L, Modifier.testTag("pill"), progress = KPillProgress.Bar)
            }
        }
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("pill").getBoundsInRoot()
        // "00:45" 다섯 글자가 14sp로 들어갈 폭이다. 굴러가는 글자의 고유 폭으로 재면 0에 가깝게 찌그러졌다.
        assertThat((bounds.right - bounds.left).value).isGreaterThan(30f)
    }

    @Test
    fun `어두운 표면의 시간 캡슐은 밝은 글자로 그린다`() {
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                KTimePill(System.currentTimeMillis(), Modifier.testTag("pill"), surface = KIslandDefaults.Black)
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("pill").captureToImage().asAndroidBitmap()
        var brightest = 0f
        // 둥근 끝 바깥은 창 바탕이 보이므로 글자가 있는 가운데만 본다.
        for (x in bitmap.width / 4 until bitmap.width * 3 / 4) {
            for (y in bitmap.height / 4 until bitmap.height * 3 / 4) {
                val c = Color(bitmap.getPixel(x, y))
                brightest = maxOf(brightest, (c.red + c.green + c.blue) / 3f)
            }
        }
        assertThat(brightest).isGreaterThan(0.7f)
    }
}
