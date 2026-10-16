package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 값이 굴러가는 도중에 또 바뀌어도 마지막 숫자가 보이는지. 호스트 앱 3차 피드백. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RollingTextTest {
    @get:Rule
    val compose = createComposeRule()

    private var value by mutableStateOf("2")

    private fun show() {
        compose.setContent {
            Box(Modifier.background(Color.White).padding(8.dp).testTag("box")) {
                RollingText(value, style = TextStyle(color = Color.Black, fontSize = 32.sp))
            }
        }
    }

    /** 검은 글자 픽셀 수. 숫자가 그려지지 않았으면 0이다. */
    private fun inkPixels(): Int {
        val map = compose.onNodeWithTag("box").captureToImage().toPixelMap()
        var ink = 0
        for (x in 0 until map.width) {
            for (y in 0 until map.height) if (map[x, y].red < INK) ink++
        }
        return ink
    }

    /** 시계가 멈춰 있으면 상태를 바꾼 것만으로는 재구성되지 않으므로 먼저 재구성을 돌린다. */
    private fun set(text: String) {
        value = text
        compose.waitForIdle()
    }

    private fun settledInk(text: String): Int {
        set(text)
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        return inkPixels()
    }

    @Test
    fun `250ms 간격으로 2에서 3, 4로 바뀌면 마지막 숫자 4가 끝까지 그려진다`() {
        show()
        compose.mainClock.autoAdvance = false
        val four = settledInk("4")
        set("2")
        compose.mainClock.advanceTimeBy(SETTLE_MS)

        set("3")
        compose.mainClock.advanceTimeBy(STEP_MS)
        set("4")
        compose.mainClock.advanceTimeBy(STEP_MS)
        compose.mainClock.advanceTimeBy(SETTLE_MS)

        compose.onNodeWithContentDescription("4").assertExists()
        assertThat(inkPixels()).isEqualTo(four)
    }

    @Test
    fun `프레임마다 값이 바뀌어도 마지막 값이 그려진다`() {
        show()
        compose.mainClock.autoAdvance = false
        val last = settledInk("19")
        set("2")
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        for (n in 3..19) {
            set(n.toString())
            compose.mainClock.advanceTimeBy(FRAME_MS)
        }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        assertThat(inkPixels()).isEqualTo(last)
    }

    @Test
    fun `내려가는 도중 다시 올라가도 마지막 값이 그려진다`() {
        show()
        compose.mainClock.autoAdvance = false
        val last = settledInk("7")
        set("5")
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        set("4")
        compose.mainClock.advanceTimeBy(STEP_MS / 2)
        set("7")
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        assertThat(inkPixels()).isEqualTo(last)
    }

    @Test
    fun `99에서 100으로 자리가 올라가도 마지막 값으로 그려지고 TalkBack은 전체 값을 읽는다`() {
        show()
        compose.mainClock.autoAdvance = false
        val last = settledInk("100")
        set("99")
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        set("100")
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.onNodeWithContentDescription("100").assertExists()
        assertThat(inkPixels()).isEqualTo(last)
    }

    @Test
    fun `숫자 띠는 올라가면 앞으로, 내려가면 뒤로 가장 가까운 칸을 고르고 이전 목표에서 이어 간다`() {
        val wheel = DigitWheel(2)
        assertThat(wheel.goalFor(3, up = true)).isEqualTo(3f)
        assertThat(wheel.goalFor(4, up = true)).isEqualTo(4f)
        assertThat(wheel.goalFor(0, up = true)).isEqualTo(10f)
        assertThat(wheel.goalFor(9, up = false)).isEqualTo(9f)
        assertThat(wheel.goalFor(7, up = false)).isEqualTo(7f)
        assertThat(wheel.goalFor(7, up = true)).isEqualTo(7f)
    }

    /** [values]를 [gaps] 간격으로 차례로 넣고 멈춘 뒤 마지막 값이 처음부터 그 값을 그린 것과 같은 픽셀인지 본다. */
    private fun assertSequence(values: List<String>, gaps: List<Long>) {
        show()
        compose.mainClock.autoAdvance = false
        val last = settledInk(values.last())
        set(values.first())
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        values.drop(1).forEachIndexed { i, v ->
            set(v)
            compose.mainClock.advanceTimeBy(gaps[i % gaps.size])
        }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.onNodeWithContentDescription(values.last()).assertExists()
        assertThat(inkPixels()).isEqualTo(last)
    }

    private fun oneToNine() = (1..9).map { it.toString() }

    @Test
    fun `1부터 9까지 250ms 간격`() = assertSequence(oneToNine(), listOf(250L))

    @Test
    fun `1부터 9까지 280ms 간격`() = assertSequence(oneToNine(), listOf(280L))

    @Test
    fun `1부터 9까지 300ms 간격`() = assertSequence(oneToNine(), listOf(300L))

    @Test
    fun `1부터 9까지 50ms 간격`() = assertSequence(oneToNine(), listOf(50L))

    @Test
    fun `1부터 9까지 무작위 간격`() {
        val random = java.util.Random(SEED)
        assertSequence(oneToNine(), List(8) { 16L + random.nextInt(400) })
    }

    @Test
    fun `9에서 1로 내려가기`() = assertSequence(oneToNine().reversed(), listOf(250L))

    @Test
    fun `8에서 12로 두 자리`() = assertSequence((8..12).map { it.toString() }, listOf(250L))

    @Test
    fun `멈춘 숫자는 같은 글자를 그냥 그린 것과 같은 픽셀이다`() {
        var plain by mutableStateOf(false)
        compose.setContent {
            Box(Modifier.background(Color.White).padding(8.dp).testTag("box")) {
                val style = TextStyle(color = Color.Black, fontSize = 32.sp)
                if (plain) androidx.compose.foundation.text.BasicText(value, style = style) else RollingText(value, style = style)
            }
        }
        compose.mainClock.autoAdvance = false
        val rolled = settledInk("9")
        plain = true
        compose.waitForIdle()
        assertThat(rolled).isGreaterThan(0)
        assertThat(inkPixels()).isEqualTo(rolled)
    }

    @Test
    fun `다 구른 띠는 0에서 9 사이 정수 칸으로 되돌아가 보이는 숫자 그대로 멈춘다`() = kotlinx.coroutines.runBlocking {
        val wheel = DigitWheel(1)
        for (n in 2..9) wheel.goalFor(n, up = true)
        for (n in 0..5) wheel.goalFor(n, up = true)
        assertThat(wheel.goalFor(5, up = true)).isEqualTo(15f)
        wheel.settle()
        assertThat(wheel.position.value).isEqualTo(5f)
        assertThat(wheel.goalFor(3, up = false)).isEqualTo(3f)
        assertThat(wheel.goalFor(9, up = false)).isEqualTo(-1f)
        wheel.settle()
        assertThat(wheel.position.value).isEqualTo(9f)
    }

    private companion object {
        const val STEP_MS = 250L
        const val FRAME_MS = 16L
        const val SETTLE_MS = 3_000L
        const val INK = 0.5f
        const val SEED = 7L
    }
}
