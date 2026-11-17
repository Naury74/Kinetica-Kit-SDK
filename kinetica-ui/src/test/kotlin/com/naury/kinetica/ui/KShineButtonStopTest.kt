package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
class KShineButtonStopTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `빛 띠가 지나가는 중에 비활성으로 바뀌면 띠가 버튼 위에 남지 않는다`() {
        var enabled by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            // 배경이 위치마다 다르면 두 버튼 화소가 달라지므로 단색 위에 둔다.
            Column(Modifier.background(Color.White)) {
                KShineButton("시작하기", onClick = {}, modifier = Modifier.testTag("live"), enabled = enabled)
                KShineButton("시작하기", onClick = {}, modifier = Modifier.testTag("idle"), enabled = false)
            }
        }
        // 띠는 주기의 앞 38% 동안 지나가므로 0.5초 무렵에는 버튼 가운데쯤에 있다.
        compose.mainClock.advanceTimeBy(SWEEP_MIDDLE_MS)
        enabled = false
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
        val live = compose.onNodeWithTag("live").captureToImage().toPixelMap()
        val idle = compose.onNodeWithTag("idle").captureToImage().toPixelMap()
        assertThat(live.width).isEqualTo(idle.width)
        var differing = 0
        for (x in 0 until live.width) {
            for (y in 0 until live.height) {
                if (live[x, y] != idle[x, y]) differing++
            }
        }
        assertThat(differing).isEqualTo(0)
    }
}

private const val SWEEP_MIDDLE_MS = 500L
private const val SETTLE_MS = 1000L
