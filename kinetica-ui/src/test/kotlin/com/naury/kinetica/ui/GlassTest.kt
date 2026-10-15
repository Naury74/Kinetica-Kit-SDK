package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GlassTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `팝업용 모양은 더 흐리고 진하며 잘못된 값은 거부한다`() {
        val popup = GlassStyles.Liquid.forPopup()

        assertThat(popup.blur).isAtLeast(28.dp)
        assertThat(popup.tint.alpha).isGreaterThan(GlassStyles.Liquid.tint.alpha)
        assertThat(runCatching { GlassStyle(saturation = -1f) }.isFailure).isTrue()
        assertThat(runCatching { GlassStyle(refraction = 2f) }.isFailure).isTrue()
    }

    @Test
    fun `팝업은 열리면 위층에 보이고 바깥을 누르면 닫는다`() {
        val open = mutableStateOf(false)
        compose.setContent {
            GlassScaffold(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) { BasicText("내용") }
                GlassDialog(open.value, { open.value = false }) { BasicText("팝업 안") }
            }
        }
        compose.onNodeWithText("팝업 안").assertDoesNotExist()

        open.value = true
        compose.waitForIdle()
        compose.onNodeWithText("팝업 안").assertExists()

        open.value = false
        compose.waitForIdle()
        compose.onNodeWithText("팝업 안").assertDoesNotExist()
    }

    @Test
    fun `세그먼트·스위치를 누르면 값이 바뀌고 화면 읽기에 선택 상태가 있다`() {
        val view = androidx.compose.runtime.mutableIntStateOf(0)
        val on = mutableStateOf(false)
        compose.setContent {
            GlassScaffold(Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Column {
                    GlassSegmentedControl(listOf("일", "주"), view.intValue, { view.intValue = it }, Modifier.size(200.dp, 44.dp))
                    GlassSwitch(on.value, { on.value = it }, Modifier.testTag("switch"))
                }
            }
        }

        compose.onNodeWithText("주").performClick()
        compose.onNodeWithTag("switch").performClick()

        assertThat(view.intValue).isEqualTo(1)
        assertThat(on.value).isTrue()
        compose.onNodeWithText("주").assertIsSelected()
        compose.onNodeWithTag("switch").assertIsOn()
    }

    @Test
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    fun `길게 눌러 여는 메뉴 항목은 화면 읽기 동작으로도 있다`() {
        var picked = ""
        compose.setContent {
            GlassScaffold(Modifier.fillMaxSize()) {
                Box(
                    Modifier.size(100.dp).testTag("photo").glassContextMenu(listOf(GlassMenuItem("공유") { picked = "공유" })),
                )
            }
        }

        compose.onNodeWithTag("photo").performCustomAccessibilityActionWithLabel("공유")

        assertThat(picked).isEqualTo("공유")
    }

    @Test
    fun `팝업용 모양은 블러를 못 쓸 때도 판 뒤가 비치지 않을 만큼 불투명하다`() {
        val popup = GlassStyles.Liquid.copy(fallback = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)).forPopup()

        assertThat(popup.fallback.alpha).isAtLeast(0.95f)
    }
}
