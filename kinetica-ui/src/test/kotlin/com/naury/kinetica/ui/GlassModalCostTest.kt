package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 시트와 다이얼로그가 열리고 닫히는 동안 글래스가 GPU 일을 프레임마다 새로 만들지 않는지 본다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GlassModalCostTest {
    @get:Rule
    val compose = createComposeRule()

    private val gradient = Brush.linearGradient(listOf(Color(0xFFFF4F6D), Color(0xFF3B6CFF), Color(0xFF29D3A0)))

    private fun assertFadeKeepsEffect(shared: Boolean) {
        val intensity = mutableFloatStateOf(0.05f)
        compose.setContent {
            val backdrop = rememberGlassBackdrop()
            if (shared) ShareBlurs(backdrop)
            Box(Modifier.size(200.dp)) {
                Box(Modifier.fillMaxSize().glassBackdrop(backdrop).background(gradient))
                Box(
                    Modifier.padding(
                        40.dp,
                    ).size(120.dp).testTag("glass").glass(GlassStyles.Clear, backdrop = backdrop, intensity = intensity.floatValue),
                )
            }
        }
        compose.onRoot().captureToImage()
        val before = GlassStats.effectBuilds
        for (step in 1..10) {
            intensity.floatValue = step / 10f
            compose.waitForIdle()
            compose.onRoot().captureToImage()
        }
        assertThat(GlassStats.effectBuilds).isEqualTo(before)
    }

    @Test
    fun `강도가 바뀌어도 혼자 거는 글래스는 효과를 다시 만들지 않는다`() = assertFadeKeepsEffect(shared = false)

    @Test
    fun `강도가 바뀌어도 함께 쓰는 블러를 그대로 쓴다`() = assertFadeKeepsEffect(shared = true)

    @Test
    fun `강도가 낮을수록 흐린 배경을 옅게 덮는다`() {
        // 왼쪽 빨강, 오른쪽 파랑 경계에 글래스를 걸치면 블러가 경계를 섞는다. 강도 0이면 블러가 없어 원래 색 그대로다.
        val intensity = mutableFloatStateOf(0f)
        compose.setContent {
            val backdrop = rememberGlassBackdrop()
            Box(Modifier.size(200.dp)) {
                Box(
                    Modifier.fillMaxSize().glassBackdrop(backdrop).drawWithContent {
                        drawRect(Color.Red)
                        drawRect(Color.Blue, topLeft = androidx.compose.ui.geometry.Offset(size.width / 2, 0f))
                    },
                )
                Box(
                    Modifier.padding(
                        40.dp,
                    ).size(120.dp).testTag("glass").glass(STRONG, backdrop = backdrop, intensity = intensity.floatValue),
                )
            }
        }
        fun edgeRed(): Float {
            val image = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
            // 경계 바로 오른쪽(파랑 쪽)에 번진 빨강.
            return image[image.width / 2 + image.width / 12, image.height / 2].red
        }
        val none = edgeRed()
        intensity.floatValue = 0.5f
        compose.waitForIdle()
        val half = edgeRed()
        intensity.floatValue = 1f
        compose.waitForIdle()
        val full = edgeRed()
        assertThat(none).isLessThan(0.05f)
        assertThat(half).isGreaterThan(none)
        assertThat(full).isGreaterThan(half)
    }

    @Test
    fun `시트와 다이얼로그가 열리고 닫히는 동안 글래스 효과를 다시 만들지 않는다`() {
        var mode by mutableStateOf(0)
        compose.setContent {
            KineticaScaffold(
                Modifier.size(360.dp, 640.dp),
                surface = SurfaceStyles.Liquid,
                background = { Box(Modifier.fillMaxSize().background(gradient)) },
            ) { padding ->
                Column(Modifier.padding(padding).padding(16.dp)) {
                    repeat(2) { KSurface(Modifier.padding(bottom = 12.dp).fillMaxWidth().height(90.dp)) { Text("카드 $it") } }
                }
                KSheet(mode == 1, { mode = 0 }) { Text("시트", Modifier.padding(vertical = 40.dp)) }
                KDialog(mode == 2, { mode = 0 }) { Text("다이얼로그") }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        for (m in listOf(1, 2)) {
            mode = m
            // 처음 보이는 프레임에서 시트 판과 스크림이 자기 효과를 한 번씩 만든다. 그 뒤로는 강도와 위치만 바뀐다.
            repeat(FIRST_FRAMES) {
                compose.mainClock.advanceTimeByFrame()
                compose.onRoot().captureToImage()
            }
            val opened = GlassStats.effectBuilds
            repeat(OPEN_FRAMES) {
                compose.mainClock.advanceTimeByFrame()
                compose.onRoot().captureToImage()
            }
            mode = 0
            repeat(CLOSE_FRAMES) {
                compose.mainClock.advanceTimeByFrame()
                compose.onRoot().captureToImage()
            }
            assertThat(GlassStats.effectBuilds).isEqualTo(opened)
            compose.mainClock.advanceTimeBy(1_000)
        }
    }

    @Test
    fun `모달이 열린 동안 글래스 스캐폴드의 움직이는 배경을 멈춘다`() {
        // 테스트 규칙은 무한 애니메이션 프레임을 돌리지 않으므로, 메시 배경이 시계를 멈추는 기준 값을 본다.
        var open by mutableStateOf(false)
        var held: Boolean? = null
        compose.setContent {
            KineticaScaffold(
                Modifier.size(360.dp, 640.dp),
                surface = SurfaceStyles.Liquid,
                background = {
                    held = shouldPauseBackground()
                    MeshGradientBackground()
                },
            ) {
                KSheet(open, { open = false }) { Text("시트", Modifier.padding(vertical = 40.dp)) }
            }
        }
        compose.waitForIdle()
        assertThat(held).isFalse()
        open = true
        compose.waitForIdle()
        assertThat(held).isTrue()
        open = false
        compose.waitForIdle()
        assertThat(held).isFalse()
    }

    @Test
    fun `단색 스캐폴드에서는 모달이 열려도 배경을 멈추지 않는다`() {
        var held: Boolean? = null
        compose.setContent {
            KineticaScaffold(Modifier.size(360.dp, 640.dp), surface = SurfaceStyles.CleanLight, background = {
                held =
                    shouldPauseBackground()
            }) {
                KSheet(true, {}) { Text("시트") }
            }
        }
        compose.waitForIdle()
        assertThat(held).isFalse()
    }

    @Test
    fun `배경 시계는 정한 초당 횟수만큼만 값을 바꾼다`() {
        // 120Hz 화면에서 1초(120프레임) 동안 값이 바뀐 횟수를 센다. 값이 바뀔 때만 배경과 그 블러를 다시 그린다.
        val clock = LoopClock()
        var frames = 0
        var changes = 0
        var last = clock.seconds
        val frameClock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                if (frames == FRAMES_PER_SECOND) throw CancellationException()
                val result = onFrame(frames++ * NANOS_PER_FRAME)
                if (clock.seconds != last) {
                    changes++
                    last = clock.seconds
                }
                return result
            }
        }
        runBlocking(frameClock) { runCatching { clock.run(BACKGROUND_FRAME_RATE) } }
        assertThat(changes).isAtMost(BACKGROUND_FRAME_RATE)
        assertThat(changes).isAtLeast(BACKGROUND_FRAME_RATE - 1)
    }

    private companion object {
        const val FRAMES_PER_SECOND = 120
        const val NANOS_PER_FRAME = 1_000_000_000L / FRAMES_PER_SECOND
        const val FIRST_FRAMES = 6
        const val OPEN_FRAMES = 30
        const val CLOSE_FRAMES = 14
        val STRONG = GlassStyle(blur = 24.dp, saturation = 1f, tint = Color.Transparent, edgeLight = Color.Transparent, shadow = 0.dp)
    }
}
