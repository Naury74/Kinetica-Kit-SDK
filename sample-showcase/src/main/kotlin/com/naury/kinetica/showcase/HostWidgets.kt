// 위젯의 색·크기 값은 시연용 꾸밈이라 이름 붙인 상수로 빼지 않는다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.editor.HostViewOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * 호스트 앱이 Kinetica에 넘기는 실제 화면 조각. Kinetica는 이 조각을 키로만 알고 위치, 크기, 회전만 정한다.
 * 안쪽 상태와 동작은 호스트가 가지고 있어서 편집하는 동안에도 그대로 유지된다.
 */
object HostWidgets {
    const val CLOCK = "widget.clock"
    const val COUNTER = "widget.counter"
    const val SALES_CHART = "widget.sales_chart"
    const val WEATHER = "widget.weather"

    val options = listOf(
        HostViewOption(CLOCK, "시계", 470.0, 200.0),
        HostViewOption(COUNTER, "카운터", 470.0, 260.0),
        HostViewOption(SALES_CHART, "매출 그래프", 960.0, 380.0),
        HostViewOption(WEATHER, "날씨", 470.0, 290.0),
    )

    /** [sales]는 최근 매출(막대 높이 비율 0..1). 바뀌면 막대가 움직인다. */
    fun slots(sales: List<Float>): Map<String, @Composable () -> Unit> = mapOf(
        CLOCK to { Clock() },
        COUNTER to { Counter() },
        SALES_CHART to { SalesChart(sales) },
        WEATHER to { Weather() },
    )
}

private val cardText = Color(0xFF1A1D24)
private val accent = Color(0xFF7C8CFF)

@Composable
private fun Clock() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val time = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    Box(Modifier.fillMaxSize().background(Color(0xFF101216)), contentAlignment = Alignment.Center) {
        Text(time, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Counter() {
    var count by rememberSaveable { mutableIntStateOf(0) }
    Row(
        Modifier.fillMaxSize().background(Color(0xFFF2F4F8)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("방문자", color = cardText.copy(alpha = 0.6f), fontSize = 13.sp)
            Text("$count", color = cardText, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        Button(onClick = { count++ }) { Text("+1") }
    }
}

@Composable
private fun SalesChart(sales: List<Float>) {
    Column(Modifier.fillMaxSize().background(Color.White).padding(16.dp)) {
        Text("최근 7일 매출", color = cardText, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            sales.forEach { value ->
                val height by animateFloatAsState(value.coerceIn(0.05f, 1f), label = "bar")
                Canvas(Modifier.weight(1f).fillMaxSize()) {
                    val barHeight = size.height * height
                    drawRoundRect(accent, Offset(0f, size.height - barHeight), Size(size.width, barHeight), CornerRadius(12f))
                }
            }
        }
    }
}

@Composable
private fun Weather() {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF3EC1D3)).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("서울", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("21°", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Text("맑음 · 최고 24° 최저 15°", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
    }
}
