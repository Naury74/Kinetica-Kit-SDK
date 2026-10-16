package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 펼친 폴더블과 태블릿 폭에서 입력 컴포넌트가 끝없이 늘어나지 않는지, 캡슐 입력란 글자 여백이 맞는지 본다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1000dp-h800dp-mdpi")
class WideLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `채팅 입력란의 글자는 캡슐 끝 곡선에서 20dp 떨어져 시작한다`() {
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                Box(Modifier.padding(start = 40.dp).requiredWidth(360.dp)) {
                    KChatInput("", {}, onSend = {}, modifier = Modifier.fillMaxWidth(), placeholder = "메시지")
                }
            }
        }
        val text = compose.onNodeWithText("메시지", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat(text.left - 40.dp).isAtLeast(KMetrics.CapsuleFieldPadding)
    }

    @Test
    fun `넓은 화면에서 인증번호 칸은 칸 최대 폭을 넘지 않고 가운데에 모인다`() {
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                Box(Modifier.testTag("box").requiredWidth(900.dp).background(Color.Black)) {
                    KCodeInput("", {}, Modifier.fillMaxWidth())
                }
            }
        }
        val map = compose.onNodeWithTag("box").captureToImage().toPixelMap()
        val row = map.height / 2
        val lit = (0 until map.width).filter { map[it, row].red > LIT }
        // 칸 6개(최대 52dp)와 간격 5개(8dp)를 합친 352dp 안팎이어야 한다. 늘어나면 900dp 가까이 된다.
        assertThat(lit.last() - lit.first()).isLessThan(380)
        val center = (lit.first() + lit.last()) / 2
        assertThat(center).isIn(440..460)
    }

    @Test
    fun `넓은 화면에서 날짜 휠은 400dp 안에 모인다`() {
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                Box(Modifier.requiredWidth(900.dp)) { KDatePicker(LocalDate.of(2026, 10, 16), {}, Modifier.fillMaxWidth()) }
            }
        }
        val year = compose.onNodeWithContentDescription("Year", substring = true).getUnclippedBoundsInRoot()
        val day = compose.onNodeWithContentDescription("Day", substring = true).getUnclippedBoundsInRoot()
        assertThat(maxOf(year.right, day.right) - minOf(year.left, day.left)).isAtMost(400.dp)
    }

    private companion object {
        const val LIT = 0.5f
    }
}
