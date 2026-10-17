// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.ChartSeries
import com.naury.kinetica.ui.ChartSlice
import com.naury.kinetica.ui.KAmountInput
import com.naury.kinetica.ui.KAmountPreset
import com.naury.kinetica.ui.KAreaChart
import com.naury.kinetica.ui.KAvatar
import com.naury.kinetica.ui.KAvatarData
import com.naury.kinetica.ui.KBarMode
import com.naury.kinetica.ui.KBillingPeriodToggle
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KCalendarHeatmap
import com.naury.kinetica.ui.KChartPalettes
import com.naury.kinetica.ui.KDateRangeDialog
import com.naury.kinetica.ui.KGauge
import com.naury.kinetica.ui.KGaugeSegment
import com.naury.kinetica.ui.KGaugeShape
import com.naury.kinetica.ui.KHeatmap
import com.naury.kinetica.ui.KKeypad
import com.naury.kinetica.ui.KKeypadMode
import com.naury.kinetica.ui.KMultiBarChart
import com.naury.kinetica.ui.KPieChart
import com.naury.kinetica.ui.KPinDots
import com.naury.kinetica.ui.KPriceDefaults
import com.naury.kinetica.ui.KPriceTag
import com.naury.kinetica.ui.KPricingCard
import com.naury.kinetica.ui.KProfileHeader
import com.naury.kinetica.ui.KProfileStat
import com.naury.kinetica.ui.KQuantityStepper
import com.naury.kinetica.ui.KQuantityStyle
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KStatCard
import com.naury.kinetica.ui.KStepIndicator
import com.naury.kinetica.ui.KStepStyle
import com.naury.kinetica.ui.KTimeRangeDialog
import com.naury.kinetica.ui.KTimeline
import com.naury.kinetica.ui.KTimelineItem
import com.naury.kinetica.ui.KTimelineOrientation
import com.naury.kinetica.ui.KTimelineStatus
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KWeekStrip
import com.naury.kinetica.ui.KWizard
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.rememberKWizardState
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Currency

/** 데이터·도메인 탭: 대시보드, 배송 추적, 결제 단계, 요금제, 송금처럼 실제 화면에 놓아 본다. */
@Composable
internal fun DataDomainTab(padding: PaddingValues, connection: NestedScrollConnection) {
    ComponentTabList(padding, connection) {
        item { DashboardScene() }
        item { GaugePieScene() }
        item { HeatmapScene() }
        item { DeliveryScene() }
        item { CheckoutScene() }
        item { ProfileHeaderScene() }
        item { PricingScene() }
        item { TransferScene() }
        item { PinScene() }
        item { ScheduleScene() }
    }
}

private val DAYS = listOf("월", "화", "수", "목", "금", "토", "일")
private val MONTHS = listOf("1월", "2월", "3월", "4월", "5월", "6월")

@Composable
private fun DashboardScene() {
    var stacked by rememberSaveable { mutableIntStateOf(0) }
    val colors = KChartPalettes.categorical(KineticaTheme.surface)
    DemoCard("대시보드", "숫자 카드는 변화율과 흐름을 함께 보이고, 지출처럼 내려야 좋은 지표는 색이 뒤집힙니다. 차트를 누르거나 끌면 값이 뜹니다.") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KStatCard(
                "이번 주 방문",
                "12,480",
                Modifier.weight(1f),
                change = 0.124f,
                trend = listOf(8f, 9f, 8.5f, 10f, 11f, 10.5f, 12.4f),
                caption = "지난주 대비",
            )
            KStatCard(
                "이번 달 지출",
                "38만 원",
                Modifier.weight(1f),
                change = 0.06f,
                trend = listOf(30f, 32f, 31f, 36f, 38f),
                increaseIsGood = false,
            )
        }
        Label("채널별 방문", 13, FontWeight.SemiBold)
        KAreaChart(
            listOf(
                ChartSeries(listOf(4.1f, 4.8f, 4.4f, 5.6f, 6.2f, 5.1f, 6.8f), colors[0], "앱"),
                ChartSeries(listOf(2.2f, 2.4f, 3.1f, 2.8f, 3.6f, 3.0f, 3.4f), colors[1], "웹"),
                ChartSeries(listOf(0.8f, 1.1f, 0.9f, 1.4f, 1.2f, 1.6f, 1.5f), colors[2], "제휴"),
            ),
            Modifier.fillMaxWidth().height(200.dp),
            labels = DAYS,
            valueFormatter = { "%.1f천".format(it) },
        )
        Label("월별 매출", 13, FontWeight.SemiBold)
        KSegmentedControl(listOf("누적", "묶음"), stacked, { stacked = it }, Modifier.fillMaxWidth())
        KMultiBarChart(
            listOf(
                ChartSeries(listOf(32f, 41f, 38f, 52f, 61f, 58f), colors[0], "구독"),
                ChartSeries(listOf(18f, 22f, 30f, 24f, 28f, 35f), colors[1], "단건"),
            ),
            Modifier.fillMaxWidth().height(200.dp),
            labels = MONTHS,
            mode = if (stacked == 0) KBarMode.Stacked else KBarMode.Grouped,
            valueFormatter = { "${it.toInt()}만" },
        )
    }
}

@Composable
private fun GaugePieScene() {
    val colors = KChartPalettes.categorical(KineticaTheme.surface)
    DemoCard("게이지와 파이", "구간을 준 반원 게이지는 지금 값의 구간 색으로 채웁니다. 파이 조각을 누르면 튀어나옵니다.") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            KGauge(
                782f,
                Modifier.weight(1f),
                range = 0f..1000f,
                label = "신용 점수",
                segments = listOf(
                    KGaugeSegment(500f, KChartPalettes.Critical, "낮음"),
                    KGaugeSegment(700f, KChartPalettes.Warning, "보통"),
                    KGaugeSegment(1000f, KChartPalettes.Good, "좋음"),
                ),
                valueFormatter = { it.toInt().toString() },
            )
            KGauge(64f, Modifier.weight(1f), label = "배터리", shape = KGaugeShape.Arc, valueFormatter = { "${it.toInt()}%" })
        }
        KPieChart(
            listOf(
                ChartSlice(42f, "식비", colors[0]),
                ChartSlice(18f, "교통", colors[1]),
                ChartSlice(25f, "쇼핑", colors[2]),
                ChartSlice(9f, "기타", colors[3]),
            ),
            // 폭을 다 쓰면 휴대폰에서도 지름이 카드 폭만큼 커져 한 화면을 다 차지하므로 적당한 크기에서 멈춘다.
            Modifier.widthIn(max = 260.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
            valueFormatter = { "${it.toInt()}만" },
        )
    }
}

@Composable
private fun HeatmapScene() {
    val end = LocalDate.now()
    val workouts = (0 until 150).associate { end.minusDays(it.toLong()) to ((it * 37 + 11) % 7).toFloat() }.filterValues { it > 0f }
    DemoCard("히트맵", "요일과 시간대별 활동량, 그리고 최근 20주 운동 기록입니다. 시간 축이라 RTL에서도 오른쪽이 최근입니다.") {
        KHeatmap(
            List(5) { r -> List(8) { c -> ((r * 5 + c * 3 + r * c) % 9).toFloat() } },
            Modifier.fillMaxWidth(),
            rowLabels = DAYS.take(5),
            columnLabels = List(8) { "${it * 3}시" },
            valueFormatter = { "${it.toInt()}회" },
        )
        KCalendarHeatmap(workouts, end.minusWeeks(19), end, Modifier.fillMaxWidth(), valueFormatter = { "${it.toInt()}세트" })
    }
}

@Composable
private fun DeliveryScene() {
    DemoCard("배송 추적", "세로 타임라인은 지금 단계 점이 숨 쉬듯 퍼지고, 가로 타임라인은 짧은 단계를 한 줄로 보입니다.") {
        KTimeline(
            listOf(
                KTimelineItem("주문 접수", "결제가 끝났어요", "10:02", KTimelineStatus.Done),
                KTimelineItem("상품 준비", "판매자가 포장했어요", "11:30", KTimelineStatus.Done),
                KTimelineItem("배송 중", "오늘 18시 전에 도착해요", "15:10", KTimelineStatus.Current),
                KTimelineItem("배송 완료"),
            ),
        )
        KTimeline(
            listOf(
                KTimelineItem("접수", time = "10:02", status = KTimelineStatus.Done),
                KTimelineItem("출고", time = "13:40", status = KTimelineStatus.Done),
                KTimelineItem("배송", status = KTimelineStatus.Current),
                KTimelineItem("도착"),
            ),
            orientation = KTimelineOrientation.Horizontal,
        )
    }
}

@Composable
private fun CheckoutScene() {
    val wizard = rememberKWizardState(3)
    var shoes by rememberSaveable { mutableIntStateOf(1) }
    var socks by rememberSaveable { mutableIntStateOf(0) }
    var done by rememberSaveable { mutableStateOf(false) }
    DemoCard("결제 단계", "단계 마법사는 단계 표시, 넘어가는 내용, 이전과 다음 버튼을 묶습니다. 끝난 단계 번호를 눌러 되돌아갈 수 있습니다.") {
        KWizard(wizard, listOf("장바구니", "배송", "결제"), onFinish = { done = true }, nextEnabled = shoes + socks > 0) { step ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (step) {
                    0 -> {
                        CartLine("러닝화 에어 라이트", 89_000.0, 110_000.0) { KQuantityStepper(shoes, { shoes = it }, range = 0..5) }
                        CartLine("기능성 양말", 9_900.0, null) {
                            KQuantityStepper(socks, { socks = it }, style = KQuantityStyle.Collapsible, range = 0..10)
                        }
                    }

                    1 -> Label("서울시 송파구 올림픽로 300, 오늘 도착 보장", 15)

                    else -> Label(if (done) "주문했어요." else "카드 끝자리 4821로 결제합니다.", 15, FontWeight.SemiBold)
                }
            }
        }
        KStepIndicator(listOf("장바구니", "배송", "결제"), wizard.current, Modifier.fillMaxWidth(), style = KStepStyle.Bar)
        KStepIndicator(listOf("1", "2", "3"), wizard.current, style = KStepStyle.Dots)
    }
}

@Composable
private fun CartLine(name: String, price: Double, original: Double?, stepper: @Composable () -> Unit) {
    val won = Currency.getInstance("KRW")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(name, 15, FontWeight.Medium)
            KPriceTag(
                KPriceDefaults.format(price, won),
                originalPrice = original?.let { KPriceDefaults.format(it, won) },
                discountPercent = original?.let { KPriceDefaults.discountPercent(price, it) },
            )
        }
        stepper()
    }
}

@Composable
private fun ProfileHeaderScene() {
    var following by rememberSaveable { mutableStateOf(false) }
    DemoCard("프로필 머리", "커버 위에 아바타를 겹치고 숫자 칸과 버튼 줄을 놓습니다. 숫자 칸은 누를 수 있습니다.") {
        KProfileHeader(
            "김하늘",
            avatar = KAvatarData("김하늘"),
            subtitle = "@haneul · 사진 찍는 개발자",
            stats = listOf(
                KProfileStat("128", "게시물"),
                KProfileStat(if (following) "1.3만" else "1.2만", "팔로워") {
                },
                KProfileStat("318", "팔로잉") {},
            ),
            actions = {
                KButton(if (following) "팔로잉" else "팔로우", {
                    following = !following
                }, Modifier.weight(1f), variant = if (following) KVariant.Tonal else KVariant.Filled)
                KButton("메시지", {}, Modifier.weight(1f), variant = KVariant.Tonal, tone = KTone.Neutral)
            },
        )
    }
}

@Composable
private fun PricingScene() {
    var yearly by rememberSaveable { mutableStateOf(true) }
    var plan by rememberSaveable { mutableIntStateOf(1) }
    DemoCard("요금제", "결제 기간을 바꾸면 가격이 바뀌고, 추천 요금제에는 위 테두리에 배지가 걸립니다.") {
        KBillingPeriodToggle(yearly, { yearly = it }, yearlyBadge = "2개월 무료")
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KPricingCard(
                "베이직",
                if (yearly) "₩4,900" else "₩5,900",
                plan == 0,
                { plan = 0 },
                period = "/월",
                features = listOf("광고 없음", "기기 2대"),
            )
            KPricingCard(
                "프로", if (yearly) "₩8,250" else "₩9,900", plan == 1, { plan = 1 },
                period = "/월", originalPrice = if (yearly) "₩9,900" else null, description = "가장 많이 고르는 요금제",
                features = listOf("광고 없음", "기기 5대", "4K 내보내기"), recommendedLabel = "추천", ctaLabel = "7일 무료로 시작", onCta = {},
            )
        }
    }
}

@Composable
private fun TransferScene() {
    var amount by rememberSaveable { mutableStateOf("") }
    val over = (amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal(1_200_000)
    DemoCard("송금", "키패드로만 입력하고 금액은 통화 형식으로 굴러가며 바뀝니다. 지우기 키를 길게 누르면 모두 지웁니다.") {
        KAmountInput(
            amount,
            { amount = it },
            Currency.getInstance("KRW"),
            Modifier.fillMaxWidth(),
            label = "보낼 금액",
            hint = "잔액 1,200,000원",
            error = if (over) "잔액이 모자라요" else null,
            presets = listOf(
                KAmountPreset("+1만", BigDecimal(10_000)),
                KAmountPreset("+5만", BigDecimal(50_000)),
                KAmountPreset("+10만", BigDecimal(100_000)),
            ),
        )
    }
}

@Composable
private fun PinScene() {
    var pin by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }
    DemoCard("PIN 입력", "여섯 자리를 다 넣으면 확인합니다. 123456이 아니면 점이 빨갛게 흔들립니다. 지문 버튼은 바로 통과합니다.") {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 맞게 넣었을 때도 문구가 그대로면 점만 꽉 찬 채 멈춘 것처럼 보이므로 통과했다고 알린다.
            val passed = pin == "123456"
            Label(
                when {
                    wrong -> "다시 입력해 주세요"
                    passed -> "확인했어요"
                    else -> "PIN을 입력하세요"
                },
                15,
                FontWeight.Medium,
            )
            KPinDots(6, pin.length, error = wrong)
            KKeypad(
                onDigit = { d ->
                    if (pin.length == 6) pin = ""
                    wrong = false
                    pin += d
                    if (pin.length == 6) wrong = pin != "123456"
                },
                onBackspace = {
                    wrong = false
                    pin = pin.dropLast(1)
                },
                mode = KKeypadMode.Pin,
                onClear = { pin = "" },
                extraKey = { Pill("지문") { pin = "123456" } },
            )
        }
    }
}

@Composable
private fun ScheduleScene() {
    var day by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var dates by rememberSaveable { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    var hours by rememberSaveable { mutableStateOf(LocalTime.of(9, 0) to LocalTime.of(18, 0)) }
    var dateOpen by rememberSaveable { mutableStateOf(false) }
    var timeOpen by rememberSaveable { mutableStateOf(false) }
    val format = DateTimeFormatter.ofPattern("M월 d일")
    DemoCard("일정", "주간 띠를 넘겨 날짜를 고르고, 기간과 시간 범위는 빠른 선택이 있는 다이얼로그로 고릅니다.") {
        KWeekStrip(day, { day = it }, Modifier.fillMaxWidth(), events = {
            if (it.dayOfMonth % 4 ==
                0
            ) {
                listOf(KChartPalettes.Categorical[0])
            } else {
                emptyList()
            }
        })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KButton(
                dates?.let { "${format.format(it.first)} ~ ${format.format(it.second)}" } ?: "기간 고르기",
                { dateOpen = true },
                Modifier.weight(1f),
                variant = KVariant.Tonal,
            )
            KButton("${hours.first} ~ ${hours.second}", { timeOpen = true }, Modifier.weight(1f), variant = KVariant.Tonal)
        }
    }
    KDateRangeDialog(dateOpen, { dateOpen = false }, dates?.first, dates?.second, { s, e -> dates = s to e })
    KTimeRangeDialog(timeOpen, { timeOpen = false }, hours.first, hours.second, { s, e -> hours = s to e })
}
