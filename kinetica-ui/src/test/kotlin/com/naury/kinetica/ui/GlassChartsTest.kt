package com.naury.kinetica.ui

import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
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
class GlassChartsTest {
    @get:Rule
    val compose = createComposeRule()

    private val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")

    @Test
    fun `선 그래프는 요약과 점마다 값을 읽히고 점을 고르면 말풍선을 띄운다`() {
        compose.setContent { GlassLineChart(listOf(3f, 5f, 4f, 8f, 7f), Modifier.size(300.dp, 180.dp), labels = days) }

        compose.onNodeWithContentDescription("Line chart, 5 points. lowest 3, highest 8, last 7").assertExists()
        compose.onNodeWithText("8", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithContentDescription("Thu: 8").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("8", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `선 그래프를 가로로 끌면 지나는 점의 값이 보이고 손을 떼면 사라진다`() {
        compose.setContent {
            GlassLineChart(
                listOf(ChartSeries(listOf(1f, 2f, 3f, 4f, 5f), label = "A"), ChartSeries(listOf(9f, 8f, 7f, 6f, 5f), Color.Red, "B")),
                Modifier.size(300.dp, 180.dp).testTag("line"),
            )
        }

        compose.onNodeWithTag("line").performTouchInput {
            down(Offset(10f, centerY))
            moveTo(Offset(width * 0.3f, centerY))
            moveTo(Offset(width - 10f, centerY))
        }
        compose.waitForIdle()
        compose.onNodeWithText("9", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("B", useUnmergedTree = true).assertExists()

        compose.onNodeWithTag("line").performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithText("B", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `막대를 고르면 선택 상태가 되고 콜백에 번호를 넘기며 다시 고르면 풀린다`() {
        val picks = mutableListOf<Int?>()
        compose.setContent {
            GlassBarChart(listOf(4f, -2f, 6f), Modifier.size(240.dp, 160.dp), labels = listOf("A", "B", "C"), onSelect = { picks += it })
        }

        compose.onNodeWithContentDescription("Bar chart, 3 bars. lowest -2, highest 6").assertExists()
        compose.onNodeWithContentDescription("B: -2").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("B: -2").assertIsSelected()
        compose.onNodeWithContentDescription("B: -2").performSemanticsAction(SemanticsActions.OnClick)

        assertThat(picks).containsExactly(1, null).inOrder()
    }

    @Test
    fun `도넛 조각은 비율을 읽히고 고르면 가운데에 이름과 값을 보인다`() {
        var picked: Int? = -1
        compose.setContent {
            GlassDonutChart(
                listOf(ChartSlice(30f, "Food", Color.Blue), ChartSlice(10f, "Bus", Color.Green)),
                Modifier.size(200.dp),
                onSelect = { picked = it },
            )
        }

        compose.onNodeWithContentDescription("Donut chart, 2 slices, total 40").assertExists()
        compose.onNodeWithText("Total", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Bus: 10, 25%").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertThat(picked).isEqualTo(1)
        compose.onNodeWithText("25%", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Bus", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `활동 링은 링마다 비율을 읽고 애니메이션 삭제면 처음부터 다 찬 모양이다`() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(100.dp).testTag("rings")) {
                ActivityRings(listOf(ActivityRing(1f, Color.Red, "Move"), ActivityRing(0.5f, Color.Green)), Modifier.size(100.dp))
            }
        }
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithContentDescription("Activity rings, Move 100%, Ring 2 50%").assertExists()
        val pixels = compose.onNodeWithTag("rings").captureToImage().toPixelMap()
        // 바깥 링의 오른쪽 끝(3시)은 다 찼다면 트랙보다 진한 빨강이다.
        val right = pixels[pixels.width - 4, pixels.height / 2]
        assertThat(right.red).isGreaterThan(0.6f)
        assertThat(right.alpha).isGreaterThan(0.9f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @Test
    fun `스파크라인은 처음과 마지막 최저 최고를 읽는다`() {
        compose.setContent { Sparkline(listOf(5f, 2f, 9f, 7f)) }

        compose.onNodeWithContentDescription("Trend from 5 to 7, lowest 2, highest 9").assertExists()
    }

    @Test
    fun `곡선은 실제 최고점과 최저점을 넘지 않는다`() {
        val buffer = ChartLineBuffer()
        val ys = floatArrayOf(50f, 0f, 100f, 100f, 20f, 60f)
        buffer.ensure(ys.size)
        ys.forEachIndexed { i, y ->
            buffer.xs[i] = i * 40f
            buffer.ys[i] = y
        }
        buffer.build(ys.size, smooth = true, bottom = 100f)

        val bounds = buffer.line.getBounds()
        assertThat(bounds.top).isAtLeast(-0.01f)
        assertThat(bounds.bottom).isAtMost(100.01f)
    }

    @Test
    fun `값 범위는 0을 넣거나 여유를 두고 잘못된 값은 거부한다`() {
        val bars = chartRange(listOf(floatArrayOf(2f, 6f)), includeZero = true, padding = 0.1f)
        val flat = chartRange(listOf(floatArrayOf(3f, 3f)), includeZero = false, padding = 0.1f)

        assertThat(bars.min).isEqualTo(0f)
        assertThat(bars.max).isWithin(0.001f).of(6.6f)
        assertThat(flat.fraction(3f)).isEqualTo(0.5f)
        assertThat(runCatching { ChartSlice(-1f, "x", Color.Red) }.isFailure).isTrue()
        assertThat(runCatching { ActivityRing(Float.NaN, Color.Red) }.isFailure).isTrue()
        assertThat(runCatching { ChartSeries(listOf(Float.POSITIVE_INFINITY)) }.isFailure).isTrue()
    }
}
