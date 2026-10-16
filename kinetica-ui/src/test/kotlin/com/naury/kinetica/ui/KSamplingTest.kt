package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 글래스가 배경 기록에서 읽을 위치 계산. 페이저, 두 칸 화면, 줄인 기록에서 같은 자리를 읽는지 본다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class KSamplingTest {
    @get:Rule
    val compose = createComposeRule()

    // 기록 좌표 (x, y)는 배경 좌표 (x / scale - translation / scale)를 읽는다.
    private fun readsBackdropAt(translation: Offset, scale: Float, recordX: Float, recordY: Float): Offset =
        Offset((recordX - translation.x) / scale, (recordY - translation.y) / scale)

    @Test
    fun `기록 원점은 글래스 왼쪽 위에서 여백만큼 바깥의 배경을 읽는다`() {
        val move = sampleTranslation(glass = Offset(146f, 560f), backdropOrigin = Offset.Zero, pad = 64f, scale = 1f)

        assertThat(readsBackdropAt(move, 1f, 0f, 0f)).isEqualTo(Offset(82f, 496f))
        assertThat(readsBackdropAt(move, 1f, 64f, 64f)).isEqualTo(Offset(146f, 560f))
    }

    @Test
    fun `낮은 해상도로 기록해도 같은 배경 위치를 읽는다`() {
        for (scale in listOf(0.5f, 1f / 3f)) {
            val pad = 120f
            val move = sampleTranslation(Offset(146f, 560f), Offset.Zero, pad, scale)
            val kTopLeft = readsBackdropAt(move, scale, pad * scale, pad * scale)

            assertThat(kTopLeft.x).isWithin(0.01f).of(146f)
            assertThat(kTopLeft.y).isWithin(0.01f).of(560f)
        }
    }

    @Test
    fun `페이저가 페이지를 옮기면 이동량도 같은 만큼 따라간다`() {
        // 페이지가 화면 오른쪽 밖(1686px)에 있을 때 그린 값을 그대로 쓰면 카드 왼쪽이 화면 오른쪽 끝 배경을 읽는다.
        val entering = sampleTranslation(Offset(146f + 1686f, 560f), Offset.Zero, 64f, 0.5f)
        val settled = sampleTranslation(Offset(146f, 560f), Offset.Zero, 64f, 0.5f)

        assertThat(settled.x - entering.x).isWithin(0.01f).of(1686f * 0.5f)
        assertThat(readsBackdropAt(settled, 0.5f, 32f, 32f).x).isWithin(0.01f).of(146f)
    }

    @Test
    fun `배경 원점이 옮겨져 있으면 그만큼 빼고 읽는다`() {
        // 두 칸 화면 오른쪽 칸이나 다른 창의 팝업처럼 배경 기록이 화면 왼쪽 위에서 시작하지 않는 경우.
        val move = sampleTranslation(Offset(1100f, 300f), backdropOrigin = Offset(984f, 120f), pad = 40f, scale = 1f)

        assertThat(readsBackdropAt(move, 1f, 40f, 40f)).isEqualTo(Offset(116f, 180f))
    }

    @Test
    fun `페이저가 글래스를 다시 그리지 않고 옮겨도 놓인 자리의 배경을 비춘다`() {
        // 블러와 색 덮기를 빼서 글래스가 읽은 배경 색이 그대로 보이게 한다.
        val clear = GlassStyle(blur = 0.dp, saturation = 1f, tint = Color.Transparent, edgeLight = Color.Transparent, shadow = 0.dp)
        lateinit var pager: PagerState
        compose.setContent {
            val backdrop = rememberGlassBackdrop()
            pager = rememberPagerState { 2 }
            Box(Modifier.size(400.dp, 200.dp)) {
                Row(Modifier.fillMaxSize().glassBackdrop(backdrop)) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color.Red))
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color.Blue))
                }
                HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                    Box(Modifier.fillMaxSize()) {
                        if (page == 1) Box(Modifier.padding(20.dp).size(40.dp).testTag("glass").glass(clear, backdrop = backdrop))
                    }
                }
            }
        }
        val width = compose.onRoot().captureToImage().width.toFloat()
        // 다음 페이지가 오른쪽(파란 쪽)에 들어와 있을 때 한 번 그리고, 나머지는 다시 그리지 않고 옮긴다.
        compose.runOnIdle { runBlocking { pager.scrollBy(width * 0.3f) } }
        compose.onRoot().captureToImage()
        compose.runOnIdle { runBlocking { pager.scrollBy(width * 0.7f) } }
        compose.waitForIdle()

        val image = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
        val center = image[image.width / 2, image.height / 2]
        assertThat(center.red).isGreaterThan(0.8f)
        assertThat(center.blue).isLessThan(0.2f)
    }

    @Test
    fun `이동 애니메이션이 레이어만 옮겨도 놓인 자리의 배경을 비춘다`() {
        // PushNavigation이나 AnimatedContent처럼 graphicsLayer 이동으로 화면이 들어오는 경우.
        val clear = GlassStyle(blur = 0.dp, saturation = 1f, tint = Color.Transparent, edgeLight = Color.Transparent, shadow = 0.dp)
        // 처음에는 오른쪽(파란 쪽)에 있다가 레이어 이동만으로 왼쪽(빨간 쪽)에 들어온다.
        val shift = mutableFloatStateOf(280f)
        compose.setContent {
            val backdrop = rememberGlassBackdrop()
            Box(Modifier.size(400.dp, 200.dp)) {
                Row(Modifier.fillMaxSize().glassBackdrop(backdrop)) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color.Red))
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color.Blue))
                }
                Box(Modifier.fillMaxSize().graphicsLayer { translationX = shift.floatValue * density }) {
                    Box(Modifier.padding(20.dp).size(40.dp).testTag("glass").glass(clear, backdrop = backdrop))
                }
            }
        }
        compose.onRoot().captureToImage()
        shift.floatValue = 0f
        compose.waitForIdle()
        compose.onRoot().captureToImage()

        val image = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
        val center = image[image.width / 2, image.height / 2]
        assertThat(center.red).isGreaterThan(0.8f)
        assertThat(center.blue).isLessThan(0.2f)
    }
}
