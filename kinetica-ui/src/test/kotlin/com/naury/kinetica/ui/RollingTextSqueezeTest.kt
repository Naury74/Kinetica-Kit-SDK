package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 자리가 모자라 폭이 줄어든 [RollingText]가 Text처럼 넘쳐 보이는지. 호스트 앱(영상 통화 상단 알약)에서 연결 상태 점이 나타나 알약이
 * 최대 폭에 닿자 인원수 자리의 폭이 0이 되고, 바깥을 잘라 숫자가 통째로 사라졌다. 같은 자리의 Text는 넘쳐 그려져 보였다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RollingTextSqueezeTest {
    @get:Rule
    val compose = createComposeRule()

    private var count by mutableStateOf("1")
    private var dot by mutableStateOf(true)
    private var plain by mutableStateOf(false)
    private var limit by mutableStateOf(Dp.Unspecified)
    private var natural by mutableIntStateOf(0)
    private var countWidth by mutableIntStateOf(0)

    private val label = TextStyle(color = Color.Black, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    private val caption = TextStyle(color = Color.Black, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)

    /** 호스트 상단 바와 같은 구조: 글래스 스캐폴드 콘텐츠 안 글래스 알약 > Row[점, 시간, Row(clearAndSetSemantics)[아이콘, 인원수]]. */
    private fun showPill() {
        compose.setContent {
            KineticaScaffold(
                Modifier.size(360.dp, 200.dp),
                surface = SurfaceStyles.Liquid,
                edgeFade = false,
                background = { Box(Modifier.fillMaxSize().background(Color.White)) },
            ) {
                Row(Modifier.padding(16.dp)) {
                    Box(
                        Modifier
                            .testTag("pill")
                            .glass(shape = CircleShape)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            Modifier.widthIn(max = limit).onSizeChanged { natural = it.width },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (dot) Box(Modifier.size(8.dp).background(Color.Red, CircleShape))
                            RollingText("0:04", style = label)
                            Row(Modifier.clearAndSetSemantics { contentDescription = "$count people" }) {
                                Box(Modifier.size(14.dp).background(Color.Red))
                                val sized = Modifier.testTag("count").onSizeChanged { countWidth = it.width }
                                if (plain) BasicText(count, sized, style = caption) else RollingText(count, sized, style = caption)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** 알약 안 검은 글자 픽셀 수. 넘쳐 그린 숫자도 알약 여백 안에 들어온다. */
    private fun pillInk(): Int {
        val map = compose.onNodeWithTag("pill").captureToImage().toPixelMap()
        var ink = 0
        for (x in 0 until map.width) {
            for (y in 0 until map.height) if (map[x, y].let { it.red < INK && it.green < INK }) ink++
        }
        return ink
    }

    /** 점과 숫자 9가 있을 때의 폭에서 인원수 자리만큼 뺀 폭으로 알약을 묶는다. 점이 나타나면 인원수 자리의 폭이 0이 된다. */
    private fun squeezeToCountWidth(): Int {
        count = "9"
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        val full = pillInk()
        val px = compose.runOnIdle { natural - countWidth }
        limit = with(compose.density) { px.toDp() }
        compose.waitForIdle()
        return full
    }

    @Test
    fun `점이 나타나 인원수 자리 폭이 0이 되어도 숫자는 Text처럼 넘쳐 그려진다`() {
        showPill()
        compose.mainClock.autoAdvance = false
        val full = squeezeToCountWidth()
        dot = false
        count = "1"
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        // 느린 프레임: 250ms씩 한 번에 건너뛰며 1에서 9까지 올린다.
        for (n in 2..9) {
            count = n.toString()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(STEP_MS, ignoreFrameDuration = true)
        }
        dot = true
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)

        assertThat(countWidth).isEqualTo(0)
        compose.onNodeWithContentDescription("9 people").assertExists()
        val rolled = pillInk()
        plain = true
        compose.waitForIdle()
        val text = pillInk()
        assertThat(rolled).isGreaterThan(0)
        assertThat(rolled).isEqualTo(text)
        assertThat(rolled).isEqualTo(full)
    }

    @Test
    fun `폭이 모자라면 RollingText는 Text와 같은 폭을 차지하고, 넉넉하면 숫자 폭 그대로다`() {
        var maxWidth by mutableStateOf(Dp.Infinity)
        val widths = IntArray(2)
        compose.setContent {
            Box(Modifier.background(Color.White).padding(8.dp)) {
                Row(Modifier.widthIn(max = maxWidth)) {
                    RollingText("12", Modifier.onSizeChanged { widths[0] = it.width }, caption)
                }
                Row(Modifier.widthIn(max = maxWidth)) {
                    BasicText("12", Modifier.onSizeChanged { widths[1] = it.width }, style = caption)
                }
            }
        }
        compose.waitForIdle()
        assertThat(widths[0]).isGreaterThan(0)
        assertThat(widths[0]).isEqualTo(widths[1])
        for (max in listOf(5.dp, 1.dp, 0.dp)) {
            maxWidth = max
            compose.waitForIdle()
            val expected = with(compose.density) { max.roundToPx() }
            assertThat(widths[0]).isEqualTo(expected)
            assertThat(widths[1]).isEqualTo(expected)
        }
    }

    private companion object {
        const val STEP_MS = 250L
        const val SETTLE_MS = 3_000L
        const val INK = 0.5f
    }
}
