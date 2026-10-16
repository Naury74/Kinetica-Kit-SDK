package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
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
class KDataChartsTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ChartSeries(listOf(3f, 4f, 5f), Color.Blue, "App")
    private val web = ChartSeries(listOf(2f, 1f, 2f), Color.Red, "Web")

    private fun collection(rows: Int, columns: Int) = SemanticsMatcher("collection $rows×$columns") { node ->
        val info = node.config.getOrElseNullable(SemanticsProperties.CollectionInfo) { null }
        info?.rowCount == rows && info.columnCount == columns
    }

    private fun cell(row: Int, column: Int) = SemanticsMatcher("cell $row,$column") { node ->
        val info = node.config.getOrElseNullable(SemanticsProperties.CollectionItemInfo) { null }
        info?.rowIndex == row && info.columnIndex == column
    }

    @Test
    fun `영역 차트는 가로 항목마다 표의 한 행으로 계열 값과 합계를 읽고 누르면 고른다`() {
        var picked: Int? = -1
        compose.setContent {
            KAreaChart(listOf(app, web), Modifier.size(300.dp, 200.dp).testTag("chart"), labels = listOf("Mon", "Tue", "Wed"), onSelect = {
                picked =
                    it
            })
        }
        compose.onNodeWithContentDescription("Mon: App 3, Web 2, Total 5").assert(cell(0, 0))
        compose.onNodeWithContentDescription("Area chart, 2 series, 3 points").assertExists()
        compose.onNodeWithContentDescription("Tue: App 4, Web 1, Total 5").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(picked).isEqualTo(1)
        compose.onNodeWithContentDescription("Tue: App 4, Web 1, Total 5").assertIsSelected()
    }

    @Test
    fun `쌓는 차트에 음수를 넣으면 거부하고 묶음 막대는 받는다`() {
        val negative = ChartSeries(listOf(1f, -2f))
        assertThrows(IllegalArgumentException::class.java) {
            compose.setContent { KMultiBarChart(listOf(negative), mode = KBarMode.Stacked) }
        }
    }

    @Test
    fun `묶음 막대는 음수를 받고 항목마다 표의 행으로 읽는다`() {
        compose.setContent {
            KMultiBarChart(
                listOf(ChartSeries(listOf(1f, -2f), Color.Blue, "A"), ChartSeries(listOf(3f, 4f), Color.Red, "B")),
                Modifier.size(300.dp, 200.dp),
                labels = listOf("Q1", "Q2"),
                mode = KBarMode.Grouped,
            )
        }
        compose.onNodeWithContentDescription("Q2: A -2, B 4").assert(cell(1, 0))
        compose.onNodeWithContentDescription("Grouped bar chart, 2 series, 2 groups").assertExists()
    }

    @Test
    fun `누적 막대는 합계까지 읽는다`() {
        compose.setContent { KMultiBarChart(listOf(app, web), Modifier.size(300.dp, 200.dp), labels = listOf("Jan", "Feb", "Mar")) }
        compose.onNodeWithContentDescription("Mar: App 5, Web 2, Total 7").performClick()
        compose.onNodeWithContentDescription("Mar: App 5, Web 2, Total 7").assertIsSelected()
    }

    @Test
    fun `파이 차트는 조각마다 값과 비율을 읽고 누르면 고른다`() {
        var picked: Int? = null
        compose.setContent {
            KPieChart(
                listOf(ChartSlice(30f, "Food", Color.Blue), ChartSlice(10f, "Bus", Color.Red)),
                Modifier.width(240.dp),
                onSelect = { picked = it },
            )
        }
        compose.onNodeWithContentDescription("Pie chart, 2 slices, total 40").assertExists()
        compose.onNodeWithContentDescription("Food: 30, 75%").assert(cell(0, 0)).performSemanticsAction(SemanticsActions.OnClick)
        assertThat(picked).isEqualTo(0)
    }

    @Test
    fun `히트맵은 행과 열을 알리는 표이고 값이 없는 칸은 기록 없음으로 읽는다`() {
        compose.setContent {
            KHeatmap(
                listOf(listOf(1f, 2f, null), listOf(4f, 5f, 6f)),
                Modifier.width(300.dp).testTag("heat"),
                rowLabels = listOf("Mon", "Tue"),
                columnLabels = listOf("0h", "1h", "2h"),
            )
        }
        compose.onNode(collection(2, 3)).assertExists()
        compose.onNodeWithContentDescription("Tue, 1h: 5").assert(cell(1, 1))
        compose.onNodeWithContentDescription("Mon, 2h: No data").assertExists()
    }

    @Test
    fun `달력 히트맵은 기간 밖 칸을 읽지 않고 날짜로 읽는다`() {
        val end = LocalDate.of(2026, 10, 16)
        var picked: LocalDate? = null
        compose.setContent {
            KCalendarHeatmap(mapOf(end to 3f), end.minusDays(9), end, Modifier.width(360.dp), onSelect = { picked = it })
        }
        val today = compose.onNodeWithContentDescription("Friday, October 16, 2026: 3")
        today.assertExists()
        today.performSemanticsAction(SemanticsActions.OnClick)
        assertThat(picked).isEqualTo(end)
        assertThat(compose.onAllNodesWithContentDescription("No data", substring = true).fetchSemanticsNodes()).hasSize(9)
    }

    @Test
    fun `팔레트는 표면 밝기에 맞추고 크기 단계는 어두운 표면에서 짙은 색부터 시작한다`() {
        assertThat(KChartPalettes.categorical(SurfaceStyles.CleanLight)).isEqualTo(KChartPalettes.Categorical)
        assertThat(KChartPalettes.categorical(SurfaceStyles.CleanDark)).isEqualTo(KChartPalettes.CategoricalDark)
        val light = KChartPalettes.sequential(SurfaceStyles.CleanLight)
        val dark = KChartPalettes.sequential(SurfaceStyles.CleanDark)
        assertThat(light).hasSize(5)
        assertThat(light.first()).isEqualTo(KChartPalettes.SequentialBlue[1])
        assertThat(dark.first()).isEqualTo(KChartPalettes.SequentialBlue[11])
        assertThrows(IllegalArgumentException::class.java) { KChartPalettes.sequential(SurfaceStyles.CleanLight, 1) }
    }

    @Test
    fun `통계 카드는 제목과 값과 변화를 한 번에 읽는다`() {
        compose.setContent { KStatCard("Sales", "$1,240", change = -0.031f, caption = "vs last week", increaseIsGood = true) }
        compose.onNodeWithContentDescription("Sales, $1,240, Down 3.1%, vs last week").assertExists()
    }

    @Test
    fun `게이지는 진행 표시로 읽고 상태에 구간 이름을 붙인다`() {
        compose.setContent {
            KGauge(
                120f,
                Modifier.testTag("gauge"),
                label = "Score",
                segments = listOf(KGaugeSegment(50f, Color.Red, "Low"), KGaugeSegment(100f, Color.Green, "Good")),
            )
        }
        compose.onNodeWithTag("gauge")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "100, Good"))
            .assert(SemanticsMatcher("range") { it.config[SemanticsProperties.ProgressBarRangeInfo].current == 100f })
    }

    @Test
    fun `차트 300개 목록은 시간이 흘러도 항목을 다시 구성하지 않는다`() {
        var compositions = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                LazyColumn(Modifier.size(360.dp, 640.dp)) {
                    items(300) { i ->
                        SideEffect { compositions++ }
                        Row {
                            KStatCard("S$i", "$i", Modifier.width(160.dp), change = 0.1f, trend = listOf(1f, 3f, 2f))
                            KGauge(i.toFloat() % 100, Modifier.width(120.dp))
                            KMultiBarChart(listOf(app, web), Modifier.size(120.dp, 80.dp))
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        val settled = compositions
        assertThat(settled).isGreaterThan(0)
        compose.mainClock.advanceTimeBy(2_000)
        assertThat(compositions).isEqualTo(settled)
    }

    @Test
    fun `단색 표면 차트는 배경을 기록하지 않는다`() {
        var backdrop: GlassBackdrop? = null
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                backdrop = LocalGlassBackdrop.current
                KAreaChart(listOf(app), Modifier.height(120.dp))
                KPieChart(listOf(ChartSlice(1f, "A", Color.Blue)))
            }
        }
        compose.waitForIdle()
        assertThat(backdrop).isNull()
    }
}
