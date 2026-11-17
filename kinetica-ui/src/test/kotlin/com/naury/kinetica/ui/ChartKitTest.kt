package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ChartKitTest {
    @get:Rule
    val compose = createComposeRule()

    private val red = Color(0xFFFF0000)
    private val candles = listOf(
        Candle(10f, 12f, 8f, 11f, 100f, "D1"),
        Candle(11f, 14f, 10f, 12f, 200f, "D2"),
        Candle(12f, 15f, 11f, 13f, 300f, "D3"),
    )

    private fun cell(row: Int) = SemanticsMatcher("cell $row") { node ->
        node.config.getOrElseNullable(SemanticsProperties.CollectionItemInfo) { null }?.rowIndex == row
    }

    private fun action(label: String) = SemanticsMatcher("action $label") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
    }

    private fun near(a: Color, b: Color): Boolean =
        kotlin.math.abs(a.red - b.red) < 0.2f && kotlin.math.abs(a.green - b.green) < 0.2f && kotlin.math.abs(a.blue - b.blue) < 0.2f

    /** [tag] 안에서 [color]에 가까운 화소 수 */
    private fun count(tag: String, color: Color): Int {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        var hits = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val p = pixels[x, y]
                if (p.alpha > 0.5f && near(p, color)) {
                    hits++
                }
            }
        }
        return hits
    }

    /** 시계를 멈추고 '애니메이션 삭제'를 켠 쪽(still)과 끈 쪽(moving)을 나란히 그려 첫 프레임을 비교한다. */
    private fun firstFrame(content: @Composable (Modifier) -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Row {
                CompositionLocalProvider(LocalKineticaReducedMotion provides true) { content(Modifier.testTag("still")) }
                CompositionLocalProvider(LocalKineticaReducedMotion provides false) { content(Modifier.testTag("moving")) }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `방사형 차트는 요약과 계열마다 값과 가장 높은 축을 읽는다`() {
        compose.setContent {
            KRadarChart(listOf("A", "B", "C"), listOf(RadarSeries(listOf(1f, 3f, 2f), red, "Me")), Modifier.size(300.dp), maxValue = 5f)
        }
        compose.onNodeWithContentDescription("Radar chart, 3 axes, 1 series").assertExists()
        compose.onNodeWithContentDescription("Me: A 1, B 3, C 2, highest B").assert(cell(0))
    }

    @Test
    fun `방사형 차트 축 근처를 누르면 그 축을 고르고 다시 누르면 푼다`() {
        var picked: Int? = -1
        compose.setContent {
            KRadarChart(
                listOf("A", "B", "C", "D"),
                listOf(RadarSeries(listOf(4f, 3f, 2f, 1f), red, "Me")),
                Modifier.size(300.dp).testTag("radar"),
                maxValue = 5f,
                onSelectAxis = { picked = it },
            )
        }
        compose.onNodeWithTag("radar").performTouchInput { click(Offset(centerX, centerY - 60.dp.toPx())) }
        assertThat(picked).isEqualTo(0)
        compose.onNodeWithText("4", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("radar").performTouchInput { click(Offset(centerX, centerY - 60.dp.toPx())) }
        assertThat(picked).isNull()
    }

    @Test
    fun `방사형 차트는 축 수와 값 개수가 맞지 않으면 거부한다`() {
        assertThrows(IllegalArgumentException::class.java) {
            compose.setContent { KRadarChart(listOf("A", "B"), emptyList()) }
        }
    }

    @Test
    fun `방사형 차트 값 개수가 축 수와 다르면 거부한다`() {
        assertThrows(IllegalArgumentException::class.java) {
            compose.setContent { KRadarChart(listOf("A", "B", "C"), listOf(RadarSeries(listOf(1f, 2f), red))) }
        }
    }

    @Test
    fun `방사형 차트는 애니메이션 삭제면 첫 프레임부터 계열을 다 그린다`() {
        firstFrame { modifier ->
            KRadarChart(listOf("A", "B", "C"), listOf(RadarSeries(listOf(5f, 5f, 5f), red)), modifier.size(160.dp), maxValue = 5f)
        }
        assertThat(count("still", red)).isGreaterThan(100)
        assertThat(count("moving", red)).isLessThan(10)
    }

    @Test
    fun `트리맵은 큰 항목부터 목록으로 이름과 값과 비율을 읽고 0인 항목은 뺀다`() {
        compose.setContent {
            KTreemap(
                listOf(
                    TreemapItem("a", "Food", 30f),
                    TreemapItem("b", "Rent", 50f),
                    TreemapItem("c", "Gift", 0f),
                    TreemapItem("d", "Bus", 20f),
                ),
                Modifier.size(300.dp, 200.dp),
            )
        }
        compose.onNodeWithContentDescription("Treemap, 3 items, total 100").assertExists()
        compose.onNodeWithContentDescription("Rent: 50, 50%").assert(cell(0))
        compose.onNodeWithContentDescription("Food: 30, 30%").assert(cell(1))
        compose.onNodeWithContentDescription("Bus: 20, 20%").assert(cell(2))
        compose.onNodeWithContentDescription("Gift", substring = true).assertDoesNotExist()
    }

    @Test
    fun `트리맵 칸을 누르면 그 항목을 넘기고 고른 키는 선택 상태다`() {
        var selected by mutableStateOf<Any?>(null)
        compose.setContent {
            KTreemap(
                listOf(TreemapItem("a", "Food", 30f), TreemapItem("b", "Rent", 70f)),
                Modifier.size(300.dp, 200.dp),
                selectedKey = selected,
                onItemClick = { selected = if (selected == it.key) null else it.key },
            )
        }
        compose.onNodeWithContentDescription("Food: 30, 30%").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Food: 30, 30%").assertIsSelected()
        compose.onNodeWithContentDescription("Rent: 70, 70%").assertIsNotSelected()
        compose.onNodeWithContentDescription("Food: 30, 30%").performTouchInput { click() }
        assertThat(selected).isNull()
    }

    @Test
    fun `트리맵은 빈 목록과 높이 제약 없음에도 그리고 겹친 키는 거부한다`() {
        compose.setContent { KTreemap(emptyList(), Modifier.width(200.dp)) }
        compose.onNodeWithContentDescription("Treemap, 0 items, total 0").assertExists()
        assertThrows(IllegalArgumentException::class.java) { TreemapItem("x", "X", -1f) }
    }

    @Test
    fun `트리맵 키가 겹치면 거부한다`() {
        assertThrows(IllegalArgumentException::class.java) {
            compose.setContent { KTreemap(listOf(TreemapItem("a", "A", 1f), TreemapItem("a", "B", 2f))) }
        }
    }

    @Test
    fun `트리맵은 애니메이션 삭제면 첫 프레임부터 칸을 다 그린다`() {
        firstFrame { modifier -> KTreemap(listOf(TreemapItem("a", "", 1f, red)), modifier.size(120.dp, 80.dp)) }
        assertThat(count("still", red)).isGreaterThan(1_000)
        assertThat(count("moving", red)).isLessThan(10)
    }

    @Test
    fun `캔들 차트는 기간 요약을 읽고 동작으로 이전과 다음 기간을 옮긴다`() {
        compose.setContent { KCandlestickChart(candles, Modifier.size(320.dp, 220.dp)) }
        compose.onNodeWithContentDescription("Candlestick chart, 3 periods, D1 to D3. Open 10, High 15, Low 8, Close 13").assertExists()
        val last = "D3: Open 12, High 15, Low 11, Close 13, Volume 300"
        compose.onNodeWithContentDescription(last).assert(action("Previous item")).assert(action("Next item"))
        val previous = compose.onNodeWithContentDescription(last).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { assertThat(previous.first { it.label == "Next item" }.action()).isFalse() }
        compose.runOnIdle { assertThat(previous.first { it.label == "Previous item" }.action()).isTrue() }
        compose.onNodeWithContentDescription("D2: Open 11, High 14, Low 10, Close 12, Volume 200").assertExists()
        // 동작으로 옮기면 화면에도 십자선 말풍선을 띄운다.
        compose.onNodeWithText("Volume", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `캔들을 길게 누르면 십자선 말풍선이 뜨고 터치가 취소되면 사라진다`() {
        compose.setContent { KCandlestickChart(candles, Modifier.size(320.dp, 220.dp).testTag("candles")) }
        compose.onNodeWithTag("candles").performTouchInput {
            down(Offset(width * 0.2f, centerY))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(1f, 0f))
        }
        compose.onNodeWithText("Open", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("candles").performTouchInput { cancel() }
        compose.waitForIdle()
        compose.onNodeWithText("Open", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `캔들 차트를 가로로 끌면 끝에서 벗어나고 세로로 끌면 그대로다`() {
        val many = List(200) { Candle(10f, 12f, 8f, 11f) }
        val state = KCandlestickState(30)
        compose.setContent { KCandlestickChart(many, Modifier.size(320.dp, 220.dp).testTag("candles"), state = state) }
        compose.onNodeWithTag("candles").performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX, top + 2f), 200) }
        compose.runOnIdle { assertThat(state.isAtEnd).isTrue() }
        compose.onNodeWithTag("candles").performTouchInput { swipe(Offset(left + 10f, centerY), Offset(right - 10f, centerY), 200) }
        compose.runOnIdle { assertThat(state.isAtEnd).isFalse() }
    }

    @Test
    fun `캔들 차트는 빈 목록에서도 그리고 잘못된 캔들은 거부한다`() {
        compose.setContent { KCandlestickChart(emptyList(), Modifier.size(300.dp, 200.dp)) }
        compose.onNodeWithContentDescription("No data").assertExists()
        // 고가와 저가가 시가, 종가를 감싸지 않는 줄은 거부하지 않고 네 값을 감싸게 그린다.
        val odd = Candle(10f, 9f, 8f, 11f)
        assertThat(odd.top).isEqualTo(11f)
        assertThat(odd.bottom).isEqualTo(8f)
        assertThrows(IllegalArgumentException::class.java) { Candle(10f, 12f, 8f, 11f, volume = -1f) }
    }

    @Test
    fun `캔들 차트는 애니메이션 삭제면 첫 프레임부터 캔들을 다 그린다`() {
        val rising = List(10) { Candle(10f, 20f, 5f, 18f) }
        firstFrame { modifier -> KCandlestickChart(rising, modifier.size(150.dp, 160.dp), risingColor = red, showVolume = false) }
        assertThat(count("still", red)).isGreaterThan(200)
        assertThat(count("moving", red)).isLessThan(10)
    }

    @Test
    fun `리뷰 요약은 누를 수 없으면 한 번에 평균과 리뷰 수와 분포를 읽는다`() {
        compose.setContent { KReviewSummary(4.6f, 1_204, listOf(869, 181, 84, 36, 34), Modifier.width(360.dp)) }
        compose.onNodeWithContentDescription(
            "Average 4.6 stars, 1,204 reviews, 5 stars 72%, 4 stars 15%, 3 stars 7%, 2 stars 3%, 1 star 3%",
        ).assertExists()
    }

    @Test
    fun `리뷰 막대 줄을 누르면 그 점수로 거르고 다시 누르면 푼다`() {
        var stars by mutableStateOf<Int?>(null)
        compose.setContent {
            KReviewSummary(4.6f, 1_204, listOf(869, 181, 84, 36, 34), Modifier.width(240.dp), selectedStars = stars, onFilter = {
                stars = it
            })
        }
        compose.onNodeWithContentDescription("Average 4.6 stars, 1,204 reviews").assertExists()
        compose.onNodeWithContentDescription("4 stars 15%, 181 reviews").assert(cell(1)).performTouchInput { click() }
        assertThat(stars).isEqualTo(4)
        compose.onNodeWithContentDescription("4 stars 15%, 181 reviews").assertIsSelected()
        compose.onNodeWithContentDescription("4 stars 15%, 181 reviews").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(stars).isNull()
    }

    @Test
    fun `리뷰 요약은 잘못된 평균과 음수 분포를 거부한다`() {
        assertThrows(IllegalArgumentException::class.java) {
            compose.setContent { KReviewSummary(5.5f, 10, listOf(1, 2, 3, 4, 0)) }
        }
    }

    @Test
    fun `리뷰 요약은 음수 분포를 거부한다`() {
        assertThrows(IllegalArgumentException::class.java) {
            compose.setContent { KReviewSummary(3f, 10, listOf(1, -2, 3, 4, 0)) }
        }
    }

    @Test
    fun `리뷰 막대는 애니메이션 삭제면 첫 프레임부터 다 차 있다`() {
        firstFrame { modifier -> KReviewSummary(1f, 3, listOf(0, 0, 0, 0, 3), modifier.width(150.dp), color = red) }
        // 별 하나는 두 쪽 모두 같게 그리므로 막대만큼 차이가 난다.
        assertThat(count("still", red) - count("moving", red)).isGreaterThan(300)
    }

    @Test
    fun `고가와 저가 순서가 어긋난 시세가 섞여도 차트가 그려진다`() {
        compose.setContent {
            KCandlestickChart(List(30) { Candle(10f + it % 5, 12f + it % 5, 9f, 11f + it % 3, 100f) }, Modifier.size(300.dp, 200.dp))
        }
        compose.waitForIdle()
    }
}
