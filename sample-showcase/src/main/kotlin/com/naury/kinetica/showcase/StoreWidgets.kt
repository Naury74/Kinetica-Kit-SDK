// 위젯의 색·크기 값은 시연용 꾸밈이라 이름 붙인 상수로 빼지 않는다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.board.BoardWidget
import com.naury.kinetica.presets.EffectPresets
import com.naury.kinetica.render.animateNumberAsState
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.delay

/** 이 앱이 보드에 내놓는 위젯 목록. 키·이름·처음 크기만 알려 주고, 내용은 [StoreWidget]이 그린다. */
val storeCatalog = listOf(
    BoardWidget("sales", "오늘 매출", widthDp = 176f, heightDp = 120f, effects = EffectPresets.card),
    BoardWidget("orders", "최근 주문", widthDp = 176f, heightDp = 150f, rowSpan = 2, effects = EffectPresets.card),
    BoardWidget("chart", "주간 매출", widthDp = 360f, heightDp = 170f, columnSpan = 2),
    BoardWidget("clock", "시계", widthDp = 176f, heightDp = 96f),
    BoardWidget("memo", "메모", widthDp = 176f, heightDp = 150f, placedByDefault = false, multiple = true),
    BoardWidget(
        "notice",
        "공지",
        widthDp = 360f,
        heightDp = 110f,
        columnSpan = 2,
        placedByDefault = false,
        effects = EffectPresets.attention,
    ),
)

/** [key]는 위젯 키, [id]는 놓인 자리 id(메모처럼 여러 개 놓는 위젯은 자리마다 다름). */
@Composable
fun StoreWidget(key: String, id: String, data: StoreData) {
    when (key) {
        "sales" -> Sales(data)
        "orders" -> Orders(data)
        "chart" -> Chart(data.week)
        "clock" -> Clock()
        "memo" -> Memo(id)
        "notice" -> Notice()
    }
}

@Composable
private fun Card(background: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

private val ink = Color(0xFF1A1D24)
private val muted = Color(0xFF6B7280)
private val brand = Color(0xFF5B6CFF)

@Composable
private fun Sales(data: StoreData) = Card {
    Text("오늘 매출", color = muted, fontSize = 13.sp)
    // 값이 바뀔 때 숫자가 올라가거나 내려가며 바뀐다.
    val sales by animateNumberAsState(data.sales.toDouble())
    // 위젯 크기는 dp로 정해져 있으므로, 글자 크기를 키운 사용자에게는 숫자를 줄여 한 줄에 다 보이게 한다.
    FitText(NumberFormat.getNumberInstance(Locale.KOREA).format(sales.roundToLong()) + "원", ink, 24.sp)
    Text(if (data.open) "영업 중" else "준비 중", color = if (data.open) Color(0xFF0E9F6E) else muted, fontSize = 12.sp)
}

@Composable
private fun Orders(data: StoreData) = Card {
    Text("최근 주문", color = muted, fontSize = 13.sp)
    data.orders.forEach { Text(it, color = ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun Chart(week: List<Float>) = Card {
    Text("주간 매출", color = muted, fontSize = 13.sp)
    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        week.forEach { value ->
            val height by animateFloatAsState(value.coerceIn(0.06f, 1f), label = "bar")
            Canvas(Modifier.weight(1f).fillMaxSize()) {
                val bar = size.height * height
                drawRoundRect(brand, Offset(0f, size.height - bar), Size(size.width, bar), CornerRadius(10f))
            }
        }
    }
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
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(ink), contentAlignment = Alignment.Center) {
        FitText(
            Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            Color.White,
            28.sp,
            Modifier.padding(horizontal = 12.dp),
        )
    }
}

/** 한 줄에 [max]까지 키우되 자리가 모자라면 줄이는 굵은 글자. */
@Composable
private fun FitText(text: String, color: Color, max: TextUnit, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier,
        style = TextStyle(color = color, fontWeight = FontWeight.Bold),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = max),
    )
}

/** 입력 중인 글이 배치를 바꿔도 남아 있는지 보여 준다. */
@Composable
private fun Memo(id: String) = Card(Color(0xFFFFF7D6)) {
    var text by rememberSaveable { mutableStateOf("") }
    Text("메모 " + id.substringAfter("memo.", "1"), color = muted, fontSize = 13.sp)
    OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().weight(1f), placeholder = { Text("오늘 할 일") })
}

@Composable
private fun Notice() = Card(Color(0xFFEFF2FF)) {
    var read by rememberSaveable { mutableIntStateOf(0) }
    Text("공지", color = muted, fontSize = 13.sp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("다음 주 월요일은 정기 휴무입니다", color = ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Button(onClick = { read++ }) { Text(if (read == 0) "확인" else "확인 $read") }
    }
}
