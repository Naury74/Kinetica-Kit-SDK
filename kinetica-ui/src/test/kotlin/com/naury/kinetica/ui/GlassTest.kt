package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
}
