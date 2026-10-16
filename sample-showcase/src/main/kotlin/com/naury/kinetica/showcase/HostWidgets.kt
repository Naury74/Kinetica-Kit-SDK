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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.editor.HostViewOption
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
        HostViewOption(CLOCK, "시계", 465.0, 300.0),
        HostViewOption(COUNTER, "카운터", 465.0, 220.0),
        HostViewOption(SALES_CHART, "매출 그래프", 960.0, 380.0),
        HostViewOption(WEATHER, "날씨", 465.0, 300.0),
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
private val cardLabel = Color(0xFF5B6270)
private val accent = Color(0xFF7C8CFF)

/** 장면 카드의 제목과 같은 모양의 작은 제목. */
@Composable
private fun WidgetLabel(text: String, color: Color = cardLabel) {
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
}

@Composable
private fun Clock() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val time = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    Column(Modifier.fillMaxSize().background(Color(0xFF262A33)).padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
        WidgetLabel("지금", Color.White.copy(alpha = 0.6f))
        Text(
            time.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            time.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREA)),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun Counter() {
    var count by rememberSaveable { mutableIntStateOf(0) }
    Row(
        Modifier.fillMaxSize().background(Color.White).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            WidgetLabel("방문자")
            Text("${count}명", color = cardText, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        KButton("+1", { count++ }, size = KButtonSize.Small)
    }
}

/** [sales]는 최근 7일 매출. 가장 큰 날을 꽉 채우는 높이로 맞추고 오늘 막대만 진하게 칠한다. */
@Composable
private fun SalesChart(sales: List<Float>) {
    val max = sales.maxOrNull()?.takeIf { it > 0f } ?: 1f
    Column(Modifier.fillMaxSize().background(Color.White).padding(12.dp)) {
        WidgetLabel("최근 7일 매출")
        Row(
            Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            sales.forEachIndexed { index, value ->
                val height by animateFloatAsState((value / max).coerceIn(0.05f, 1f), label = "bar")
                val color = if (index == sales.lastIndex) accent else accent.copy(alpha = 0.35f)
                Canvas(Modifier.weight(1f).fillMaxSize()) {
                    val barHeight = size.height * height
                    drawRoundRect(color, Offset(0f, size.height - barHeight), Size(size.width, barHeight), CornerRadius(4.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun Weather() {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF4FB8E8), Color(0xFF2F7FD0))))
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        WidgetLabel("서울", Color.White.copy(alpha = 0.8f))
        Text("21°", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light, maxLines = 1)
        Text("맑음, 최고 24° 최저 15°", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1)
    }
}
