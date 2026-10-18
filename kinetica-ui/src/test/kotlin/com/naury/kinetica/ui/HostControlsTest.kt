package com.naury.kinetica.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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

    @Test
    fun `버튼, 아이콘 버튼, 탭은 drawable 같은 Painter 아이콘을 받는다`() {
        var clicks = 0
        val painter = ColorPainter(Color.Red)
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                KButton("자르기", { clicks++ }, icon = painter, variant = KVariant.Tonal)
                KIconButton(painter, "실행 취소", { clicks++ })
                KTabBar(listOf(KTab("홈", painter = painter), KTab("설정", KIcons.Settings)), 0, {})
            }
        }
        compose.onNodeWithText("자르기").performClick()
        compose.onNodeWithContentDescription("실행 취소").performClick()
        compose.waitForIdle()
        assertThat(clicks).isEqualTo(2)
        compose.onNodeWithText("홈").assertExists()
    }

    @Test
    fun `중립 톤 버튼 바탕은 표면의 올라온 면 색을 쓴다`() {
        val raised = Color(0xFFEDEDF1)
        val surface = SurfaceStyle.Solid(Color.White, Color(0xFF1B1E26), Color(0xFFF3F4F8), raised = raised)
        assertThat(surface.raisedFill(0.16f)).isEqualTo(raised)
        assertThat(SurfaceStyles.CleanLight.raisedFill(0.16f).alpha).isWithin(0.001f).of(0.16f)
    }

    @Test
    fun `하나만 고르는 칩 묶음에서 이미 고른 칩을 다시 누르면 진동하지 않는다`() {
        val recording = Recording()
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides recording) {
                KChipGroup(listOf("사진", "영상"), setOf(0), {})
            }
        }
        compose.onNodeWithText("사진").performClick()
        compose.waitForIdle()
        assertThat(recording.count).isEqualTo(0)
        compose.onNodeWithText("영상").performClick()
        compose.waitForIdle()
        assertThat(recording.count).isEqualTo(1)
    }

    @Test
    fun `minTouchTarget이면 칩이 차지하는 높이가 48dp 이상이다`() {
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.testTag("small")) { KFilterChip("작게", false, {}) }
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.testTag("big")) {
                    KFilterChip("크게", false, {}, minTouchTarget = true)
                }
            }
        }
        compose.onNodeWithTag("small").assertHeightIsAtLeast(30.dp)
        compose.onNodeWithTag("big").assertHeightIsAtLeast(48.dp)
    }
}
