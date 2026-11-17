package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 인디케이터가 미끄러져 오는 탭 칸은 누른 칸에 덮개를 칠하지 않아 같은 모양 두 개가 겹쳐 보이지 않는다. 초점 표시는 남는다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SelectionIndicationTest {
    @get:Rule
    val compose = createComposeRule()

    private fun centerAfter(
        indication: androidx.compose.foundation.IndicationNodeFactory,
        emit: suspend (MutableInteractionSource) -> Unit,
    ): Int {
        val source = MutableInteractionSource()
        compose.setContent {
            Box(Modifier.size(80.dp).background(Color.White).indication(source, indication).testTag("cell"))
        }
        compose.waitForIdle()
        runBlocking { emit(source) }
        compose.mainClock.advanceTimeBy(500)
        val pixels = compose.onNodeWithTag("cell").captureToImage().asAndroidBitmap()
        return android.graphics.Color.red(pixels.getPixel(pixels.width / 2, pixels.height / 2))
    }

    @Test
    fun `일반 눌림 표시는 누르면 칸을 덮는다`() {
        assertThat(centerAfter(KIndication(RectangleShape)) { it.emit(PressInteraction.Press(Offset.Zero)) }).isLessThan(250)
    }

    @Test
    fun `탭 칸 눌림 표시는 누를 때 덮개를 칠하지 않는다`() {
        assertThat(centerAfter(SelectionIndication(RectangleShape)) { it.emit(PressInteraction.Press(Offset.Zero)) }).isEqualTo(255)
    }

    @Test
    fun `탭 칸 눌림 표시도 키보드 초점은 보여 준다`() {
        assertThat(centerAfter(SelectionIndication(RectangleShape)) { it.emit(FocusInteraction.Focus()) }).isLessThan(255)
    }
}
