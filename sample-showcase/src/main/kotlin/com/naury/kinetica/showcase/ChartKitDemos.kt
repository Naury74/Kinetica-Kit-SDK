package com.naury.kinetica.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.Candle
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KCandlestickChart
import com.naury.kinetica.ui.KChartPalettes
import com.naury.kinetica.ui.KRadarChart
import com.naury.kinetica.ui.KRating
import com.naury.kinetica.ui.KReviewSummary
import com.naury.kinetica.ui.KTreemap
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.RadarSeries
import com.naury.kinetica.ui.TreemapItem
import com.naury.kinetica.ui.rememberKCandlestickState
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** 방사형 차트, 트리맵, 캔들 차트, 리뷰 요약 데모 카드들. */
internal fun LazyListScope.chartKitDemos() {
    item { RadarCard() }
    item { TreemapCard() }
    item { CandlestickCard() }
    item { ReviewCard() }
}

@Composable
private fun RadarCard() {
    DemoCard("운동 능력 비교", "이번 시즌과 지난 시즌 측정값을 겹쳐 봅니다. 축 근처를 누르면 그 항목의 두 시즌 값이 뜹니다.") {
        val colors = KChartPalettes.categorical(KineticaTheme.surface)
        var axis by remember { mutableStateOf<Int?>(null) }
        KRadarChart(
            axes = RADAR_AXES,
            series = listOf(
                RadarSeries(THIS_SEASON, colors[0], "이번 시즌"),
                RadarSeries(LAST_SEASON, colors[1], "지난 시즌"),
            ),
            modifier = Modifier.fillMaxWidth().widthIn(max = WIDE_CHART).align(Alignment.CenterHorizontally),
            maxValue = MAX_SCORE,
            rings = RADAR_RINGS,
            onSelectAxis = { axis = it },
        )
        val picked = axis
        val note = if (picked == null) {
            "가장 많이 늘어난 항목은 회복력입니다. 지난 시즌보다 12점 올랐습니다."
        } else {
            val change = (THIS_SEASON[picked] - LAST_SEASON[picked]).roundToInt()
            val sign = if (change >= 0) "+" else ""
            "${RADAR_AXES[picked]}: 이번 시즌 ${THIS_SEASON[picked].roundToInt()}점, 지난 시즌보다 $sign${change}점"
        }
        Label(note, size = NOTE_TEXT, color = muted())
    }
}

@Composable
private fun TreemapCard() {
    DemoCard("11월 지출 분류", "넓이가 금액에 비례합니다. 칸을 누르면 그 분류를 강조하고, 좁은 칸은 이름만 적거나 비웁니다.") {
        var picked by rememberSaveable { mutableStateOf<String?>(null) }
        val total = SPENDING.sumOf { it.second }
        KTreemap(
            items = SPENDING.map { (name, won) -> TreemapItem(name, name, won.toFloat()) },
            modifier = Modifier.fillMaxWidth().height(TREEMAP_HEIGHT),
            selectedKey = picked,
            onItemClick = { picked = if (picked == it.key) null else it.key as String },
            valueFormatter = ::won,
        )
        val chosen = SPENDING.firstOrNull { it.first == picked }
        val note = if (chosen == null) {
            "이번 달 지출 ${won(total.toFloat())}. 주거비가 가장 큽니다."
        } else {
            "${chosen.first} ${won(chosen.second.toFloat())}, 이번 달 지출의 ${(chosen.second * PERCENT / total).roundToInt()}%"
        }
        Label(note, size = NOTE_TEXT, color = muted())
    }
}

@Composable
private fun CandlestickCard() {
    DemoCard(
        "키네틱전자 일봉",
        "거래일 240일 가운데 최근 40일을 보입니다. 옆으로 끌어 지난 날을 보고, 두 손가락으로 벌리면 확대됩니다. 길게 누르면 십자선이 뜹니다.",
    ) {
        val candles = remember { stockCandles() }
        val state = rememberKCandlestickState(initialVisibleCount = VISIBLE_DAYS)
        // 옮기는 동안 매 프레임 바뀌는 범위를 직접 읽으면 카드 전체가 다시 구성되므로 끝에 붙었는지만 본다.
        val atEnd by remember(state) { derivedStateOf { state.isAtEnd } }
        KCandlestickChart(
            candles = candles,
            modifier = Modifier.fillMaxWidth().height(CANDLE_HEIGHT),
            state = state,
            risingColor = RISING,
            fallingColor = FALLING,
            priceFormatter = ::won,
            volumeFormatter = { "${(it / TEN_THOUSAND).roundToInt()}만 주" },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val last = candles.last()
            val change = (last.close - candles[candles.lastIndex - 1].close) / candles[candles.lastIndex - 1].close * PERCENT
            Label("종가 ${won(last.close)} (${"%+.2f".format(change)}%)", size = NOTE_TEXT, weight = FontWeight.SemiBold)
            KButton("최근으로", onClick = { state.scrollToEnd() }, variant = KVariant.Tonal, size = KButtonSize.Small, enabled = !atEnd)
        }
    }
}

@Composable
private fun ReviewCard() {
    DemoCard("상품 리뷰", "분포 막대를 누르면 그 점수의 리뷰만 보입니다. 같은 막대를 다시 누르면 모두 보입니다.") {
        var stars by rememberSaveable { mutableStateOf<Int?>(null) }
        KReviewSummary(
            average = AVERAGE,
            reviewCount = REVIEW_COUNT,
            distribution = DISTRIBUTION,
            modifier = Modifier.fillMaxWidth(),
            selectedStars = stars,
            onFilter = { stars = it },
        )
        val shown = REVIEWS.filter { stars == null || it.stars == stars }
        Column(verticalArrangement = Arrangement.spacedBy(REVIEW_GAP)) {
            if (shown.isEmpty()) Label("이 점수의 리뷰를 아직 불러오지 않았습니다.", size = NOTE_TEXT, color = muted())
            shown.forEach { review ->
                Column(verticalArrangement = Arrangement.spacedBy(REVIEW_LINE_GAP)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(REVIEW_LINE_GAP)) {
                        KRating(review.stars.toFloat(), null, starSize = SMALL_STAR)
                        Label(review.author, size = META_TEXT, color = muted())
                    }
                    Label(review.text, size = NOTE_TEXT)
                }
            }
        }
    }
}

@Composable
private fun muted(): Color = LocalKineticaContentColor.current.copy(alpha = MUTED_ALPHA)

private fun won(value: Float): String = "%,d원".format(value.roundToInt())

/** 같은 화면이 늘 같도록 씨앗을 고정한 240거래일 가격. 주말은 건너뛴다. */
private fun stockCandles(): List<Candle> {
    val random = Random(STOCK_SEED)
    var day = LocalDate.of(START_YEAR, START_MONTH, START_DAY)
    var close = START_PRICE
    return List(TRADING_DAYS) {
        while (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) day = day.plusDays(1)
        val open = close * (1f + (random.nextFloat() - HALF) * GAP_SWING)
        val drift = (random.nextFloat() - DRIFT_CENTER) * DAY_SWING
        val next = (open * (1f + drift)).coerceAtLeast(MIN_PRICE)
        val high = maxOf(open, next) * (1f + random.nextFloat() * WICK_SWING)
        val low = minOf(open, next) * (1f - random.nextFloat() * WICK_SWING)
        val volume = (BASE_VOLUME + random.nextFloat() * BASE_VOLUME * 2f) * (1f + abs(drift) * VOLUME_BOOST)
        val label = "${day.monthValue}/${day.dayOfMonth}"
        day = day.plusDays(1)
        close = next
        val o = tick(open)
        val c = tick(next)
        // 호가 단위로 반올림하면 고가와 저가가 몸통 안으로 들어올 수 있어 다시 맞춘다.
        Candle(o, maxOf(tick(high), o, c), minOf(tick(low), o, c), c, volume, label)
    }
}

private fun tick(price: Float): Float = (price / PRICE_TICK).roundToInt() * PRICE_TICK

private class Review(val stars: Int, val author: String, val text: String)

private val RADAR_AXES = listOf("속도", "지구력", "근력", "유연성", "균형", "회복력")
private val THIS_SEASON = listOf(82f, 74f, 68f, 55f, 77f, 70f)
private val LAST_SEASON = listOf(76f, 69f, 72f, 49f, 71f, 58f)
private const val MAX_SCORE = 100f
private const val RADAR_RINGS = 5

private val SPENDING = listOf(
    "주거" to 850_000,
    "식비" to 420_000,
    "쇼핑" to 230_000,
    "교통" to 160_000,
    "경조사" to 100_000,
    "문화" to 95_000,
    "카페" to 72_000,
    "구독" to 59_000,
    "의료" to 48_000,
    "기타" to 31_000,
)

private const val AVERAGE = 4.6f
private const val REVIEW_COUNT = 1_204
private val DISTRIBUTION = listOf(869, 181, 84, 36, 34)
private val REVIEWS = listOf(
    Review(5, "민지", "소리가 깨끗하고 노이즈 캔슬링이 생각보다 강해요. 출퇴근길에 매일 씁니다."),
    Review(5, "도윤", "케이스가 작아서 주머니에 쏙 들어가요. 배터리도 하루 종일 갑니다."),
    Review(4, "서연", "음질은 만족하는데 귀가 작은 편이라 오래 끼면 조금 아파요."),
    Review(3, "하준", "통화 품질은 보통이에요. 바람 부는 날에는 상대가 잘 못 듣습니다."),
    Review(1, "지우", "한쪽이 가끔 끊겨서 교환 신청했어요."),
)

private val RISING = Color(0xFFE5484D)
private val FALLING = Color(0xFF2A78D6)
private const val STOCK_SEED = 7
private const val TRADING_DAYS = 240
private const val VISIBLE_DAYS = 40
private const val START_YEAR = 2025
private const val START_MONTH = 1
private const val START_DAY = 2
private const val START_PRICE = 58_000f
private const val MIN_PRICE = 20_000f
private const val PRICE_TICK = 100f
private const val GAP_SWING = 0.012f
private const val DAY_SWING = 0.045f
private const val DRIFT_CENTER = 0.48f
private const val WICK_SWING = 0.012f
private const val BASE_VOLUME = 400_000f
private const val VOLUME_BOOST = 12f
private const val TEN_THOUSAND = 10_000f
private const val PERCENT = 100f
private const val HALF = 0.5f
private const val MUTED_ALPHA = 0.7f
private const val NOTE_TEXT = 13
private const val META_TEXT = 12
private val WIDE_CHART = 420.dp
private val TREEMAP_HEIGHT = 240.dp
private val CANDLE_HEIGHT = 280.dp
private val REVIEW_GAP = 14.dp
private val REVIEW_LINE_GAP = 6.dp
private val SMALL_STAR = 14.dp
