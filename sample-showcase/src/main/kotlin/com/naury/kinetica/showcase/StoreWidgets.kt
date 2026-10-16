// 위젯의 색·크기 값은 시연용 꾸밈이라 이름 붙인 상수로 빼지 않는다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.board.BoardWidget
import com.naury.kinetica.board.KineticaBoardState
import com.naury.kinetica.presets.EffectPresets
import com.naury.kinetica.render.HOST_VIEW_UNITS_PER_DP
import com.naury.kinetica.render.animateNumberAsState
import com.naury.kinetica.ui.GlassButton
import com.naury.kinetica.ui.GlassButtonSize
import com.naury.kinetica.ui.GlassTextField
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.delay

/**
 * 이 앱이 보드에 제공하는 위젯 목록. 키와 이름, 처음 크기만 알려 주고 내용은 [StoreWidget]이 그린다.
 * 자유 배치의 처음 너비는 보드 기준 폭을 두 칸([BOARD_HALF_DP])이나 한 줄 전체([BOARD_FULL_DP])로 나눈 값이라, 위젯 끝이 서로 맞고
 * 상단 바 가장자리와도 맞는다. 같은 줄에 놓이는 위젯은 높이도 같게 둔다.
 */
val storeCatalog = listOf(
    BoardWidget("sales", "오늘 매출", widthDp = BOARD_HALF_DP, heightDp = 156f, effects = EffectPresets.card),
    BoardWidget("orders", "최근 주문", widthDp = BOARD_HALF_DP, heightDp = 156f, rowSpan = 2, effects = EffectPresets.card),
    BoardWidget("chart", "주간 매출", widthDp = BOARD_FULL_DP, heightDp = 196f, columnSpan = 2),
    BoardWidget("clock", "시계", widthDp = BOARD_HALF_DP, heightDp = 136f),
    BoardWidget("memo", "메모", widthDp = BOARD_HALF_DP, heightDp = 136f, multiple = true),
    BoardWidget(
        "notice",
        "공지",
        widthDp = BOARD_FULL_DP,
        heightDp = 110f,
        columnSpan = 2,
        placedByDefault = false,
        effects = EffectPresets.attention,
    ),
)

/** [key]는 위젯 키, [id]는 보드에 놓인 슬롯 id다. 메모처럼 여러 개 놓을 수 있는 위젯은 슬롯마다 id가 다르다. */
@Composable
fun StoreWidget(key: String, id: String, data: StoreData) {
    when (key) {
        "sales" -> Sales(data)
        "orders" -> Orders(data.orders)
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

/** 카드 맨 위 줄. 왼쪽에 제목, 오른쪽에 [trailing]을 둔다. */
@Composable
private fun CardHeader(title: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
        trailing()
    }
}

private val ink = Color(0xFF1A1D24)
private val muted = Color(0xFF6B7280)
private val brand = Color(0xFF5B6CFF)
private val positive = Color(0xFF0E9F6E)
private val won = NumberFormat.getNumberInstance(Locale.KOREA)

@Composable
private fun Sales(data: StoreData) = Card {
    CardHeader("오늘 매출") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (data.open) positive else muted))
            Text(if (data.open) " 영업 중" else " 준비 중", color = if (data.open) positive else muted, fontSize = 12.sp)
        }
    }
    // 값이 바뀔 때 숫자가 올라가거나 내려가며 바뀐다.
    val sales by animateNumberAsState(data.sales.toDouble())
    // 위젯 크기는 dp로 정해져 있으므로, 글자 크기를 키운 사용자에게는 숫자를 줄여 한 줄에 다 보이게 한다.
    FitText(won.format(sales.roundToLong()) + "원", ink, 26.sp)
    Spacer(Modifier.weight(1f))
    val goal = (sales / SALES_GOAL).toFloat().coerceIn(0f, 1f)
    Text("목표 ${won.format(SALES_GOAL)}원의 ${(goal * 100).roundToInt()}%", color = muted, fontSize = 12.sp, maxLines = 1)
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(brand.copy(alpha = 0.14f))) {
        Box(Modifier.fillMaxWidth(goal).fillMaxHeight().clip(CircleShape).background(brand))
    }
}

/** 최근 주문. 위젯 높이에 들어가는 만큼 보여 주므로, 키를 키우면 더 많은 주문이 보인다. */
@Composable
private fun Orders(orders: List<StoreOrder>) = Card {
    CardHeader("최근 주문")
    // 좁은 위젯에서는 메뉴 이름이 잘리지 않게 시각을 뺀다.
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val showTime = maxWidth >= 200.dp
        FitColumn(Modifier.fillMaxSize().clipToBounds()) {
            orders.forEach { order -> OrderRow(order, showTime) }
        }
    }
}

@Composable
private fun OrderRow(order: StoreOrder, showTime: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showTime) Text(order.time, color = muted, fontSize = 12.sp, modifier = Modifier.width(40.dp))
        Text(order.menu, color = ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            won.format(order.price) + "원",
            color = ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** 위에서부터 쌓되, 다 들어가지 않는 줄은 반쯤 잘려 보이지 않게 아예 놓지 않는다. */
@Composable
private fun FitColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        var used = 0
        val shown = placeables.takeWhile { placeable ->
            (used + placeable.height <= constraints.maxHeight).also { fits -> if (fits) used += placeable.height }
        }
        layout(constraints.maxWidth, used.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = 0
            shown.forEach {
                it.place(0, y)
                y += it.height
            }
        }
    }
}

/** 지난 6일과 오늘의 매출 막대. 가장 큰 날을 꽉 채우는 높이로 맞추고, 오늘 막대만 진하게 칠한다. */
@Composable
private fun Chart(week: List<Int>) = Card {
    val total = week.sum()
    CardHeader("주간 매출") { Text("합계 ${won.format(total / 10_000)}만원", color = muted, fontSize = 12.sp) }
    val max = week.maxOrNull()?.coerceAtLeast(1) ?: 1
    val today = LocalDate.now()
    Row(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        week.forEachIndexed { index, value ->
            val isToday = index == week.lastIndex
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                val height by animateFloatAsState((value.toFloat() / max).coerceIn(0.04f, 1f), label = "bar")
                val color = if (isToday) brand else brand.copy(alpha = 0.32f)
                Canvas(Modifier.weight(1f).fillMaxWidth()) {
                    val bar = size.height * height
                    drawRoundRect(color, Offset(0f, size.height - bar), Size(size.width, bar), CornerRadius(6.dp.toPx()))
                }
                val day = today.minusDays((week.lastIndex - index).toLong())
                Text(
                    if (isToday) "오늘" else day.dayOfWeek.getDisplayName(DayStyle.SHORT, Locale.KOREA),
                    color = if (isToday) ink else muted,
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp),
                )
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
    val time = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(ink).padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FitText(time.format(DateTimeFormatter.ofPattern("HH:mm:ss")), Color.White, 30.sp)
        Text(
            time.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREA)),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 한 줄에 [max] 크기까지 키우고, 공간이 모자라면 줄이는 굵은 글자. */
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

/** 배치를 바꿔도 입력 중인 글이 그대로 남는지 보여 준다. */
@Composable
private fun Memo(id: String) = Card(Color(0xFFFFF7D6)) {
    var text by rememberSaveable { mutableStateOf("") }
    CardHeader("메모 " + id.substringAfter("memo.", "1"))
    GlassTextField(text, { text = it }, "오늘 할 일", Modifier.fillMaxWidth().weight(1f), singleLine = false)
}

@Composable
private fun Notice() = Card(Color(0xFFEFF2FF)) {
    var read by rememberSaveable { mutableIntStateOf(0) }
    CardHeader("공지")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("다음 주 월요일은 정기 휴무입니다", color = ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        GlassButton(if (read == 0) "확인" else "확인 $read", { read++ }, size = GlassButtonSize.Small)
    }
}

private const val SALES_GOAL = 2_000_000.0

/** 보드 기준 폭(dp). 자유 배치는 이 폭을 화면 폭에 맞춰 늘린다. */
private const val REFERENCE_DP = (KineticaBoardState.REFERENCE_WIDTH / HOST_VIEW_UNITS_PER_DP).toFloat()
private const val BOARD_GAP_DP = 12f

/** 자유 배치에서 한 줄에 둘씩 놓는 위젯의 처음 너비(dp). 반올림 때문에 오른쪽 간격을 넘지 않도록 아주 조금 줄였다. */
internal const val BOARD_HALF_DP = (REFERENCE_DP - BOARD_GAP_DP * 3) / 2 - 0.01f

/** 자유 배치에서 한 줄을 다 쓰는 위젯의 처음 너비(dp). */
internal const val BOARD_FULL_DP = REFERENCE_DP - BOARD_GAP_DP * 2 - 0.01f
