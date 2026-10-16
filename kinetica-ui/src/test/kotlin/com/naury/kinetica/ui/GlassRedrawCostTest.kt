package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 영상 통화 화면처럼 배경은 그대로이고 바 위 요소만 매 프레임 움직일 때, 글래스가 자기 레이어를 다시 기록하지 않는지 본다.
 * 다시 기록하면 HWUI가 블러 레이어를 처음부터 다시 그려 프레임마다 블러를 한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GlassRedrawCostTest {
    @get:Rule
    val compose = createComposeRule()

    private val phase = mutableFloatStateOf(0f)
    private val stageColor = mutableStateOf(Color(0xFF3B6CFF))
    private var background: GlassBackdrop? = null
    private var barBackdrop: GlassBackdrop? = null

    private fun callScreen(barsSeeBackground: Boolean = false) {
        compose.setContent {
            KineticaScaffold(
                Modifier.size(320.dp, 460.dp),
                surface = SurfaceStyles.Liquid,
                edgeFade = false,
                background = { Box(Modifier.fillMaxSize().background(stageColor.value)) },
                topBar = {
                    Row(Modifier.fillMaxWidth().padding(16.dp)) {
                        repeat(3) { Box(Modifier.padding(end = 8.dp).size(44.dp).glass(shape = CircleShape)) }
                        // 숫자 굴림이나 테두리처럼 자기 레이어 없이 그리기 단계에서 매 프레임 바뀌는 요소. 바 전체를 다시 그리게 한다.
                        Box(
                            Modifier.size(80.dp, 32.dp).glass(shape = RoundedCornerShape(16.dp)).drawBehind {
                                drawRect(Color.White, size = size.copy(width = size.width * phase.floatValue))
                            },
                        )
                    }
                },
                bottomBar = {
                    val dock = if (barsSeeBackground) LocalScaffoldBackground.current else LocalGlassBackdrop.current
                    CompositionLocalProvider(LocalGlassBackdrop provides dock) {
                        barBackdrop = LocalGlassBackdrop.current
                        Box(Modifier.fillMaxWidth().padding(16.dp).height(72.dp).testTag("dock").glass(shape = RoundedCornerShape(36.dp)))
                    }
                },
            ) {
                background = LocalGlassBackdrop.current
                Box(Modifier.padding(top = 160.dp, start = 24.dp).size(120.dp).glass(shape = RoundedCornerShape(20.dp)))
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        repeat(2) { frame() }
    }

    private fun frame() {
        // 시계를 멈춘 상태에서는 바꾼 상태가 다음 프레임 전에 재구성되도록 먼저 돌린다.
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.onRoot().captureToImage()
    }

    @Test
    fun `바 위 요소만 움직이면 글래스 레이어도 배경 기록도 다시 기록하지 않는다`() {
        callScreen()
        val draws = GlassStats.glassDraws
        val records = GlassStats.layerRecords
        val stage = requireNotNull(background).records
        val bars = requireNotNull(barBackdrop).records
        val effects = GlassStats.effectBuilds
        repeat(FRAMES) {
            phase.floatValue = (it + 1) / FRAMES.toFloat()
            frame()
        }
        // 글래스 그리기는 매 프레임 다시 불리지만, 레이어는 그대로 두므로 HWUI가 블러 결과를 그대로 쓴다.
        assertThat(GlassStats.glassDraws - draws).isAtLeast(FRAMES)
        assertThat(GlassStats.layerRecords).isEqualTo(records)
        // 바가 비추는 배경(스캐폴드 배경과 콘텐츠)은 그대로다. 바까지 담는 화면 기록만 다시 하고, 그것은 열린 오버레이가 있을 때만 비춘다.
        assertThat(requireNotNull(background).records).isEqualTo(stage)
        assertThat(requireNotNull(barBackdrop).records).isEqualTo(bars)
        assertThat(GlassStats.effectBuilds).isEqualTo(effects)
    }

    @Test
    fun `레이어를 다시 기록하지 않아도 배경이 바뀌면 글래스에 비친다`() {
        callScreen()
        fun dockBlue(): Float {
            val image = compose.onNodeWithTag("dock").captureToImage().toPixelMap()
            return image[image.width / 2, image.height / 2].blue
        }
        val blue = dockBlue()
        val records = GlassStats.layerRecords
        stageColor.value = Color(0xFFFF4F6D)
        frame()
        assertThat(GlassStats.layerRecords).isEqualTo(records)
        assertThat(dockBlue()).isLessThan(blue - 0.2f)
    }

    @Test
    fun `바가 스캐폴드 배경만 비추면 콘텐츠 글래스와 블러 기록을 함께 쓴다`() {
        callScreen(barsSeeBackground = true)
        val shared = requireNotNull(background?.shared)
        assertThat(barBackdrop).isSameInstanceAs(background)
        // 콘텐츠 카드와 독이 같은 스타일이라 블러 기록 하나를 함께 쓰고, 독은 굴절 지도에 자리를 잡는다.
        assertThat(shared.count).isEqualTo(1)
        assertThat(shared.regions).isEqualTo(2)
    }

    private companion object {
        const val FRAMES = 8
    }
}
