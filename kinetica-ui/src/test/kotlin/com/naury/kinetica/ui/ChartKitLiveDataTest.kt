package com.naury.kinetica.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChartKitLiveDataTest {
    @get:Rule
    val compose = createComposeRule()

    private fun base() = mutableStateListOf(
        Candle(10f, 12f, 8f, 11f, 100f, "D1"),
        Candle(11f, 14f, 10f, 12f, 200f, "D2"),
        Candle(12f, 15f, 11f, 13f, 300f, "D3"),
    )

    @Test
    fun `같은 목록에 캔들을 더하면 요약의 최고가와 기간 수가 따라온다`() {
        val candles = base()
        compose.setContent { KCandlestickChart(candles, Modifier.size(320.dp, 220.dp)) }
        compose.runOnIdle { candles += Candle(13f, 20f, 12f, 19f, 400f, "D4") }
        compose.onNodeWithContentDescription("Candlestick chart, 4 periods, D1 to D4. Open 10, High 20, Low 8, Close 19").assertExists()
    }

    @Test
    fun `같은 목록에 항목을 더하면 트리맵 칸과 합계가 따라온다`() {
        val items = mutableStateListOf(TreemapItem("a", "Food", 30f), TreemapItem("b", "Rent", 70f))
        compose.setContent { KTreemap(items, Modifier.width(240.dp)) }
        compose.runOnIdle { items += TreemapItem("c", "Bus", 100f) }
        compose.onNodeWithContentDescription("Treemap, 3 items, total 200").assertExists()
    }

    @Test
    fun `같은 목록의 분포를 바꾸면 리뷰 비율이 따라온다`() {
        val distribution = mutableStateListOf(1, 0, 0, 0, 0)
        compose.setContent { KReviewSummary(5f, 1, distribution, Modifier.width(360.dp)) }
        compose.onNodeWithContentDescription("100%", substring = true).assertExists()
        compose.runOnIdle { distribution[4] = 1 }
        compose.onNodeWithContentDescription("50%", substring = true).assertExists()
    }

    @Test
    fun `캔들 설명은 시세가 바뀌어도 알리지 않고 동작으로 옮겼을 때만 알린다`() {
        val candles = base()
        compose.setContent { KCandlestickChart(candles, Modifier.size(320.dp, 220.dp)) }
        val live = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)
        val quiet = SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion)
        compose.onNodeWithContentDescription("D3:", substring = true).assert(quiet)
        compose.runOnIdle { candles[2] = Candle(12f, 16f, 11f, 15f, 300f, "D3") }
        compose.onNodeWithContentDescription("D3: Open 12, High 16", substring = true).assert(quiet)
        val actions = compose.onNodeWithContentDescription(
            "D3:",
            substring = true,
        ).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.first { it.label == "Previous item" }.action() }
        compose.onNodeWithContentDescription("D2:", substring = true).assert(live)
        compose.runOnIdle { candles[1] = Candle(11f, 14f, 9f, 12f, 200f, "D2") }
        compose.onNodeWithContentDescription("D2: Open 11, High 14, Low 9", substring = true).assert(quiet)
    }

    @Test
    fun `가로 페이저 안에서 차트를 가로로 끌면 페이지는 그대로이고 차트만 옮겨진다`() {
        val many = List(200) { Candle(10f, 12f, 8f, 11f) }
        val chart = KCandlestickState(30)
        lateinit var pager: PagerState
        var pagerMoved = false
        compose.setContent {
            pager = rememberPagerState { 2 }
            // 끌기가 시작됐다 취소되면 페이지가 잠깐 움직였다 돌아오므로, 끝 상태가 아니라 움직인 적이 있는지 본다.
            LaunchedEffect(pager) { snapshotFlow { pager.isScrollInProgress }.collect { if (it) pagerMoved = true } }
            HorizontalPager(pager, Modifier.size(320.dp, 220.dp)) { page ->
                if (page == 0) KCandlestickChart(many, Modifier.fillMaxSize().testTag("candles"), state = chart)
            }
        }
        compose.onNodeWithTag("candles").performTouchInput { swipe(Offset(right - 10f, centerY), Offset(left + 10f, centerY), 300) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertThat(pagerMoved).isFalse()
            assertThat(pager.currentPage).isEqualTo(0)
        }
        compose.onNodeWithTag("candles").performTouchInput { swipe(Offset(left + 10f, centerY), Offset(right - 10f, centerY), 300) }
        compose.runOnIdle { assertThat(chart.isAtEnd).isFalse() }
    }

    @Test
    fun `기간 이름 캐시는 같은 순번에 다른 이름이 오면 새 이름을 준다`() {
        lateinit var measurer: TextMeasurer
        compose.setContent { measurer = rememberTextMeasurer() }
        compose.runOnIdle {
            val cache = CandleTextCache(measurer)
            cache.prepare(TextStyle.Default, CandleFormat({ it.toString() }, { it.toString() }, names(), "%s %s"))
            assertThat(cache.label("Mon").layoutInput.text.text).isEqualTo("Mon")
            assertThat(cache.label("Week 1").layoutInput.text.text).isEqualTo("Week 1")
            assertThat(cache.label("Mon")).isSameInstanceAs(cache.label("Mon"))
        }
    }

    private fun names() = CandleNames("O", "H", "L", "C", "V")
}
