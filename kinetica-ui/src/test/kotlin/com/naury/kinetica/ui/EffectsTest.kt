package com.naury.kinetica.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EffectsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `메시 그라디언트는 바탕을 색으로 채우고 잘못된 색 수는 거부한다`() {
        compose.setContent {
            KineticaScaffold(Modifier.fillMaxSize(), background = {
                MeshGradientBackground(Modifier.testTag("mesh"), MeshGradients.Sunset)
            }) {
                BasicText("위 콘텐츠")
            }
        }
        compose.onNodeWithText("위 콘텐츠").assertExists()
        val pixels = compose.onNodeWithTag("mesh").captureToImage().toPixelMap()
        val center = pixels[pixels.width / 2, pixels.height / 2]
        assertThat(center.alpha).isWithin(0.01f).of(1f)
        assertThat(center).isNotEqualTo(Color.Black)

        assertThat(runCatching { MeshPalette(Color.Black, listOf(Color.Red)) }.isFailure).isTrue()
        assertThat(runCatching { MeshPalette(Color.Black, List(7) { Color.Red }) }.isFailure).isTrue()
    }

    @Test
    fun `새 글래스 스타일은 의도한 성격을 가진다`() {
        assertThat(GlassStyles.Mica.edgeLight).isEqualTo(Color.Transparent)
        assertThat(GlassStyles.Mica.blur).isGreaterThan(GlassStyles.Acrylic.blur)
        assertThat(GlassStyles.MicaDark.onGlass).isEqualTo(Color.White)
        assertThat(GlassStyles.Obsidian.brightness).isLessThan(0f)
        assertThat(GlassStyles.Obsidian.onGlass).isEqualTo(Color.White)
        assertThat(GlassStyles.HarmonyOs.brightness).isGreaterThan(0f)
        assertThat(GlassStyles.Pastel.tint.red).isGreaterThan(GlassStyles.Pastel.tint.green)
    }

    @Test
    fun `글자 효과는 화면 읽기에 최종 글자를 준다`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Column {
                ShimmerText("반짝이는 글자")
                GradientText("그라디언트 글자")
                ScrambleText("ACCESS GRANTED")
            }
        }
        compose.onNodeWithText("반짝이는 글자").assertExists()
        compose.onNodeWithText("그라디언트 글자").assertExists()
        compose.onNodeWithText("ACCESS GRANTED").assertExists()
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithText("ACCESS GRANTED").assertExists()
    }

    @Test
    fun `뒤섞기는 맞춘 글자와 공백을 그대로 두고 끝나면 원래 글자가 된다`() {
        val random = Random(1)
        val half = scrambleFrame("AB CD", 2, "#", random)

        assertThat(half).isEqualTo("AB ##")
        assertThat(scrambleFrame("AB CD", 5, "#", random)).isEqualTo("AB CD")
    }

    @Test
    fun `흔들기는 처음에는 가만히 있고 트리거가 바뀌면 움직였다가 돌아온다`() {
        val errors = mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.size(100.dp).shake(errors.intValue).testTag("field")) }
        compose.mainClock.advanceTimeBy(100)
        assertThat(compose.onNodeWithTag("field").getBoundsInRoot().left).isEqualTo(0.dp)

        errors.intValue = 1
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(80)
        assertThat(compose.onNodeWithTag("field").getBoundsInRoot().left).isNotEqualTo(0.dp)

        compose.mainClock.advanceTimeBy(600)
        assertThat(compose.onNodeWithTag("field").getBoundsInRoot().left).isEqualTo(0.dp)
    }

    @Test
    fun `반짝임과 스포트라이트는 클릭을 막지 않는다`() {
        var clicks = 0
        compose.setContent {
            Box(
                Modifier
                    .size(120.dp)
                    .sparkle()
                    .spotlight(shape = RoundedCornerShape(16.dp))
                    .clickable { clicks++ }
                    .testTag("card"),
            )
        }
        compose.onNodeWithTag("card").performClick()
        compose.waitForIdle()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `패럴랙스 헤더는 스크롤보다 느리게 올라간다`() {
        lateinit var scroll: androidx.compose.foundation.ScrollState
        var top = 0f
        compose.setContent {
            scroll = rememberScrollState()
            Column(Modifier.size(200.dp).verticalScroll(scroll)) {
                Box(
                    Modifier
                        .size(200.dp, 120.dp)
                        .parallaxScroll(scroll, rate = 0.5f)
                        .onGloballyPositioned { top = it.positionInRoot().y },
                )
                Box(Modifier.size(200.dp, 600.dp))
            }
        }
        compose.runOnIdle { scroll.dispatchRawDelta(200f) }
        compose.waitForIdle()
        assertThat(top).isWithin(1f).of(-100f)
    }

    @Test
    fun `당겨서 확대는 맨 위에서 더 끌어내린 거리를 기억하고 위로 밀면 먼저 줄인다`() {
        val state = ParallaxState()
        val connection = state.nestedScrollConnection

        connection.onPostScroll(Offset.Zero, Offset(0f, 100f), NestedScrollSource.UserInput)
        assertThat(state.pull).isEqualTo(50f)

        val used = connection.onPreScroll(Offset(0f, -30f), NestedScrollSource.UserInput)
        assertThat(used.y).isEqualTo(-30f)
        assertThat(state.pull).isEqualTo(35f)
    }
}
