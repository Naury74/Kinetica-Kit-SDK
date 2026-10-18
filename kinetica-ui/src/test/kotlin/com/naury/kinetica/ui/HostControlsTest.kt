package com.naury.kinetica.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 다른 SDK가 Kinetica를 안에 넣어 쓸 때 호스트 설정을 따르는지: 햅틱 끄기, 채운 면 대비 기준, 움직임 줄이기. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HostControlsTest {
    @get:Rule
    val compose = createComposeRule()

    private class Recording : HapticFeedback {
        var count = 0

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            count++
        }
    }

    private fun tapsWithHaptics(enabled: Boolean): Int {
        val recording = Recording()
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides recording, LocalKineticaHaptics provides enabled) {
                KButton("저장", {})
                KFilterChip("무료 배송", false, {})
            }
        }
        compose.onNodeWithText("저장").performClick()
        compose.onNodeWithText("무료 배송").performClick()
        compose.waitForIdle()
        return recording.count
    }

    @Test
    fun `햅틱을 켜 두면 버튼과 칩이 진동을 낸다`() {
        assertThat(tapsWithHaptics(true)).isGreaterThan(0)
    }

    @Test
    fun `LocalKineticaHaptics가 false면 버튼과 칩이 진동을 내지 않는다`() {
        assertThat(tapsWithHaptics(false)).isEqualTo(0)
    }

    @Test
    fun `채운 면 대비 기준을 낮추면 브랜드 색을 그대로 칠한다`() {
        val brand = Color(0xFF0A84FF)
        // 흰 글자와 3.6:1이라 기본 기준 4.5에서는 진하게 고친다.
        assertThat(ToneColors(brand, Color.White).fill).isNotEqualTo(brand)
        assertThat(ToneColors(brand, Color.White, 3f).fill).isEqualTo(brand)
        assertThat(ToneColors(brand, Color.White, 1f).fill).isEqualTo(brand)
    }

    @Test
    fun `LocalKineticaFillContrast가 버튼 색 역할에 전해진다`() {
        lateinit var colors: ToneColors
        compose.setContent {
            CompositionLocalProvider(LocalKineticaFillContrast provides 1f) {
                colors = KTone.Accent.colors(SurfaceStyles.CleanLight)
            }
        }
        compose.waitForIdle()
        assertThat(colors.fill).isEqualTo(colors.color)
    }

    @Test
    fun `움직임 줄이기는 호스트 값이 있으면 그 값을 따른다`() {
        var system: Boolean? = null
        var forced: Boolean? = null
        compose.setContent {
            system = isReducedMotion()
            CompositionLocalProvider(LocalKineticaReducedMotion provides true) { forced = isReducedMotion() }
        }
        compose.waitForIdle()
        assertThat(system).isFalse()
        assertThat(forced).isTrue()
    }
}
