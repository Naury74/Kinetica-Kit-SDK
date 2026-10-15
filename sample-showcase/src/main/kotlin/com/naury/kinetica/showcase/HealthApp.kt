// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.ActivityRing
import com.naury.kinetica.ui.ActivityRings
import com.naury.kinetica.ui.ChartSeries
import com.naury.kinetica.ui.ChartSlice
import com.naury.kinetica.ui.FlipDigits
import com.naury.kinetica.ui.GlassBarChart
import com.naury.kinetica.ui.GlassCalendar
import com.naury.kinetica.ui.GlassCalendarColors
import com.naury.kinetica.ui.GlassCalendarSelection
import com.naury.kinetica.ui.GlassChartDefaults
import com.naury.kinetica.ui.GlassDonutChart
import com.naury.kinetica.ui.GlassLargeTitleBar
import com.naury.kinetica.ui.GlassLineChart
import com.naury.kinetica.ui.GlassRangeSlider
import com.naury.kinetica.ui.GlassScaffold
import com.naury.kinetica.ui.GlassSlideToConfirm
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.MeshGradientBackground
import com.naury.kinetica.ui.MeshGradients
import com.naury.kinetica.ui.countdownText
import com.naury.kinetica.ui.rememberCountdown
import com.naury.kinetica.ui.rememberGlassCalendarState
import com.naury.kinetica.ui.rememberGlassLargeTitleState
import java.time.LocalDate
import kotlin.math.roundToInt

/** 건강 앱: 활동 링, 주간 걸음 수, 수면·지출 차트, 운동 달력, 다음 운동까지 플립 카운트다운, 심박 범위. */
@Composable
internal fun HealthApp(onBack: () -> Unit) {
    val title = rememberGlassLargeTitleState()
    GlassScaffold(
        Modifier.fillMaxSize(),
        style = GlassStyles.LiquidDark,
        background = { MeshGradientBackground(Modifier.fillMaxSize(), palette = MeshGradients.Ocean) },
        topBar = { GlassLargeTitleBar(title, "건강", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().nestedScroll(title.connection),
            contentPadding = PaddingValues(
                start = readingPadding(),
                end = readingPadding(),
                top = padding.calculateTopPadding() + 8.dp,
                bottom =
                    navigationBarBottom() + 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { RingsCard() }
            item { StepsCard() }
            item { SpendCard() }
            item { HeartRateCard() }
            item { WorkoutCalendarCard() }
            item { NextWorkoutCard() }
        }
    }
}

@Composable
private fun RingsCard() {
    DemoCard("오늘의 활동", "링이 차례로 차오르고, 목표를 넘으면 한 바퀴 더 겹쳐 돕니다.") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ActivityRings(
                listOf(
                    ActivityRing(0.82f, GlassChartDefaults.Move, "움직이기"),
                    ActivityRing(1.24f, GlassChartDefaults.Exercise, "운동하기"),
                    ActivityRing(0.58f, GlassChartDefaults.Stand, "일어서기"),
                ),
                Modifier.size(132.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 링 색 글자는 파란 배경에서 대비가 낮아, 색은 앞 점으로만 보이고 글자는 흰색으로 쓴다.
                RingLegend("움직이기", "410/500kcal", GlassChartDefaults.Move)
                RingLegend("운동하기", "37/30분", GlassChartDefaults.Exercise)
                RingLegend("일어서기", "7/12시간", GlassChartDefaults.Stand)
            }
        }
    }
}

@Composable
private fun RingLegend(name: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Column(Modifier.padding(start = 10.dp)) {
            Label(name, 13, color = Color.White.copy(alpha = 0.75f))
            Label(value, 16, FontWeight.SemiBold, color = Color.White)
        }
    }
}

@Composable
private fun StepsCard() {
    DemoCard("걸음 수", "선이 그려지며 나타납니다. 손가락을 대고 옆으로 움직이면 그날 값이 보입니다.") {
        GlassLineChart(
            listOf(
                ChartSeries(
                    listOf(6.2f, 8.1f, 7.4f, 9.8f, 5.6f, 11.2f, 10.4f),
                    GlassChartDefaults.Blue,
                    "이번 주",
                    GlassChartDefaults.fill(GlassChartDefaults.Blue),
                ),
                ChartSeries(listOf(5.1f, 6.4f, 7.9f, 6.8f, 7.2f, 8.5f, 6.1f), GlassChartDefaults.Palette[3], "지난주"),
            ),
            Modifier.fillMaxWidth().height(200.dp),
            labels = DAYS,
            valueFormatter = { "${(it * 1000).roundToInt()}걸음" },
        )
        GlassBarChart(
            listOf(
                7.2f,
                6.5f,
                8.1f,
                5.4f,
                7.8f,
                9.1f,
                6.9f,
            ),
            Modifier.fillMaxWidth().height(140.dp),
            labels = DAYS,
            valueFormatter = {
                "${it}시간"
            },
        )
    }
}

@Composable
private fun SpendCard() {
    DemoCard("이번 달 지출", "조각을 누르면 튀어나오고 가운데에 금액이 나옵니다.") {
        GlassDonutChart(
            listOf(
                ChartSlice(42f, "식비", GlassChartDefaults.Palette[0]),
                ChartSlice(18f, "교통", GlassChartDefaults.Palette[1]),
                ChartSlice(25f, "운동", GlassChartDefaults.Palette[2]),
                ChartSlice(15f, "기타", GlassChartDefaults.Palette[4]),
            ),
            Modifier.size(200.dp).align(Alignment.CenterHorizontally),
            valueFormatter = { "${it.roundToInt()}만 원" },
        )
    }
}

@Composable
private fun HeartRateCard() {
    var range by remember { mutableStateOf(100f..150f) }
    DemoCard("목표 심박 범위", "두 손잡이를 끌어 운동할 때 유지할 심박 범위를 정합니다.") {
        Label("${range.start.roundToInt()}–${range.endInclusive.roundToInt()} bpm", 22, FontWeight.Bold)
        GlassRangeSlider(range, { range = it }, valueRange = 40f..200f, valueLabel = { "${it.roundToInt()}" })
    }
}

@Composable
private fun WorkoutCalendarCard() {
    val calendar = rememberGlassCalendarState()
    var selection by remember { mutableStateOf<GlassCalendarSelection>(GlassCalendarSelection.Range()) }
    val today = remember { LocalDate.now() }
    DemoCard("운동 기록", "운동한 날에는 점이 찍혀 있습니다. 두 날을 누르면 그 기간이 이어진 띠로 칠해집니다.") {
        GlassCalendar(
            calendar,
            selection,
            { selection = it },
            events = { day -> if (day.isBefore(today) && day.dayOfMonth % 3 != 0) listOf(GlassChartDefaults.Exercise) else emptyList() },
            // 파란 배경에서도 주말 글자가 읽히도록 밝은 색을 쓴다.
            colors = GlassCalendarColors(sunday = Color(0xFFFF9A8F), saturday = Color(0xFFA8DCFF)),
        )
    }
}

@Composable
private fun NextWorkoutCard() {
    val target = remember { System.currentTimeMillis() + 2 * 60 * 60 * 1000L + 15 * 60 * 1000L }
    val left by rememberCountdown(target)
    var started by remember { mutableStateOf(false) }
    DemoCard("다음 운동까지", "숫자가 바뀔 때마다 위 반쪽이 접혀 내려갑니다.") {
        FlipDigits(countdownText(left), Modifier.align(Alignment.CenterHorizontally))
        GlassSlideToConfirm(if (started) "운동 시작됨" else "밀어서 지금 시작", { started = true }, Modifier.fillMaxWidth(), confirmed = started)
    }
}

private val DAYS = listOf("월", "화", "수", "목", "금", "토", "일")
