// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
// 예시 데이터 클래스는 필드를 생성자에 나란히 두는 편이 읽기 쉬워 인자가 많다.
@file:Suppress("MagicNumber", "LongParameterList")

package com.naury.kinetica.showcase

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.FlipCard
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KChipGroup
import com.naury.kinetica.ui.KDateRangeDialog
import com.naury.kinetica.ui.KDateRangePreset
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KPriceSize
import com.naury.kinetica.ui.KPriceTag
import com.naury.kinetica.ui.KQuantityStepper
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KStatus
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KToaster
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KTopAppBar
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KWeekStrip
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.PushNavigation
import com.naury.kinetica.ui.SurfaceStyle
import com.naury.kinetica.ui.kClickable
import com.naury.kinetica.ui.rememberKToasterState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 여행 예약 앱: 항공권 검색, 항공편 고르기, 좌석 선택, 탑승권 네 화면. 하늘색 그라디언트 위에 Vision 글래스를 얹어 밝고 가벼운 분위기를 낸다.
 * 출발지와 도착지는 바꾸기 버튼으로 뒤집히고, 날짜는 기간 다이얼로그로, 좌석은 기내 배치도에서 고른다. 탑승권은 끌어서 뒤집으면 안내가 나온다.
 */
@Composable
internal fun TravelApp(onBack: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(listOf("search")) }
    var from by rememberSaveable { mutableIntStateOf(0) }
    var to by rememberSaveable { mutableIntStateOf(2) }
    var depart by rememberSaveable { mutableLongStateOf(LocalDate.now().plusDays(10).toEpochDay()) }
    var back by rememberSaveable { mutableLongStateOf(LocalDate.now().plusDays(13).toEpochDay()) }
    var people by rememberSaveable { mutableIntStateOf(1) }
    var flight by rememberSaveable { mutableIntStateOf(0) }
    var seat by rememberSaveable { mutableStateOf("") }
    val pop = { stack = stack.dropLast(1) }
    AppTheme(TRAVEL_LOOK) {
        PushNavigation(stack, onBack = pop) { screen ->
            when (screen) {
                "search" -> TripSearch(
                    TripQuery(from, to, LocalDate.ofEpochDay(depart), LocalDate.ofEpochDay(back), people),
                    onBack,
                    onChange = {
                        from = it.from
                        to = it.to
                        depart = it.depart.toEpochDay()
                        back = it.back.toEpochDay()
                        people = it.people
                    },
                ) { stack = stack + "flights" }

                "flights" -> FlightList(from, to, LocalDate.ofEpochDay(depart), { depart = it.toEpochDay() }, pop) {
                    flight = it
                    stack = stack + "seat"
                }

                "seat" -> SeatMap(seat, { seat = it }, pop) { stack = stack + "pass" }

                else -> BoardingPass(Ticket(from, to, LocalDate.ofEpochDay(depart), FLIGHTS[flight], seat)) { stack = listOf("search") }
            }
        }
    }
}

private data class TripQuery(val from: Int, val to: Int, val depart: LocalDate, val back: LocalDate, val people: Int)

@Composable
internal fun TravelSky() {
    val dark = TRAVEL_LOOK.isDark()
    val colors = if (dark) {
        listOf(Color(0xFF0B1026), Color(0xFF1D2A5C), Color(0xFF4A3466))
    } else {
        listOf(Color(0xFF2F7BE0), Color(0xFF6FA9EC), Color(0xFFE99A7A))
    }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(colors))
        val cloud = Color.White.copy(alpha = if (dark) 0.05f else 0.18f)
        drawCircle(cloud, size.width * 0.22f, Offset(size.width * 0.15f, size.height * 0.28f))
        drawCircle(cloud, size.width * 0.16f, Offset(size.width * 0.32f, size.height * 0.3f))
        drawCircle(cloud, size.width * 0.25f, Offset(size.width * 0.9f, size.height * 0.62f))
    }
}

@Composable
private fun TripSearch(query: TripQuery, onBack: () -> Unit, onChange: (TripQuery) -> Unit, onSearch: () -> Unit) {
    var dates by rememberSaveable { mutableStateOf(false) }
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        background = { TravelSky() },
        topBar = { KTopAppBar("어디로 떠나세요?", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
                start = appPadding(20.dp, 560.dp),
                end = appPadding(20.dp, 560.dp),
                top = padding.calculateTopPadding() + 12.dp,
                bottom = navigationBarBottom() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RouteCard(query, onChange)
            DatesCard(query) { dates = true }
            PassengerCard(query, onChange)
            KButton("항공편 검색", onSearch, size = KButtonSize.Large, fullWidth = true, icon = KIcons.Search)
            Label("최근 검색", 14, FontWeight.SemiBold, Modifier.padding(top = 8.dp, start = 4.dp))
            KChipGroup(listOf("김포 → 제주", "인천 → 오사카", "김해 → 방콕"), emptySet(), {}, wrap = true, tone = KTone.Neutral)
        }
    }
    KDateRangeDialog(
        dates,
        { dates = false },
        query.depart,
        query.back,
        onConfirm = { start, end -> onChange(query.copy(depart = start, back = end)) },
        title = "여행 날짜",
        presets = tripPresets(),
        range = LocalDate.now()..LocalDate.now().plusYears(1),
    )
}

/** 출발지와 도착지. 바꾸기 버튼을 누를 때마다 버튼이 반 바퀴 돈다. */
@Composable
private fun RouteCard(query: TripQuery, onChange: (TripQuery) -> Unit) {
    var swaps by rememberSaveable { mutableIntStateOf(0) }
    val turn by animateFloatAsState(swaps * 180f, label = "swap")
    KSurface(Modifier.fillMaxWidth(), shape = kitShape { card }) {
        Box {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AirportLine("출발", TRAVEL_AIRPORTS[query.from])
                Box(Modifier.fillMaxWidth().height(1.dp).background(LocalKineticaContentColor.current.copy(alpha = 0.15f)))
                AirportLine("도착", TRAVEL_AIRPORTS[query.to])
            }
            KIconButton(
                AppIcons.Swap,
                "출발지와 도착지 바꾸기",
                {
                    swaps++
                    onChange(query.copy(from = query.to, to = query.from))
                },
                Modifier.align(Alignment.CenterEnd).padding(end = 20.dp).rotate(turn),
                variant = KVariant.Filled,
            )
        }
    }
}

@Composable
private fun DatesCard(query: TripQuery, onClick: () -> Unit) {
    KSurface(onClick, Modifier.fillMaxWidth(), shape = kitShape { card }, onClickLabel = "날짜 고르기") {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ShowcaseIcon(KIcons.Calendar, 22.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Label("가는 날 – 오는 날", 12, color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
                Label("${TRAVEL_DAY.format(query.depart)} – ${TRAVEL_DAY.format(query.back)}", 17, FontWeight.SemiBold)
            }
            Label("${query.back.toEpochDay() - query.depart.toEpochDay()}박", 14, FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PassengerCard(query: TripQuery, onChange: (TripQuery) -> Unit) {
    var cabin by rememberSaveable { mutableIntStateOf(0) }
    KSurface(Modifier.fillMaxWidth(), shape = kitShape { card }) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShowcaseIcon(KIcons.Users, 22.dp)
                Label("승객", 16, FontWeight.SemiBold, Modifier.weight(1f).padding(start = 14.dp))
                KQuantityStepper(query.people, { onChange(query.copy(people = it)) }, range = 1..9, label = "승객 수")
            }
            KSegmentedControl(listOf("일반석", "프리미엄", "비즈니스"), cabin, { cabin = it }, Modifier.fillMaxWidth())
        }
    }
}

private fun tripPresets(today: LocalDate = LocalDate.now()): List<KDateRangePreset> {
    val saturday = today.plusDays(((6 - today.dayOfWeek.value + 7) % 7).toLong().let { if (it == 0L) 7 else it })
    return listOf(
        KDateRangePreset("이번 주말", saturday, saturday.plusDays(1)),
        KDateRangePreset("다음 주말", saturday.plusDays(7), saturday.plusDays(8)),
        KDateRangePreset("다음 달 3박", today.plusMonths(1), today.plusMonths(1).plusDays(3)),
    )
}

@Composable
private fun AirportLine(label: String, airport: Pair<String, String>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Label(label, 12, color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
            Row(verticalAlignment = Alignment.Bottom) {
                Label(airport.first, 30, FontWeight.Bold)
                Label(airport.second, 15, modifier = Modifier.padding(start = 10.dp, bottom = 5.dp))
            }
        }
        Spacer(Modifier.width(56.dp))
    }
}

@Composable
private fun FlightList(from: Int, to: Int, day: LocalDate, onDay: (LocalDate) -> Unit, onBack: () -> Unit, onPick: (Int) -> Unit) {
    var sort by rememberSaveable { mutableIntStateOf(0) }
    val sorted = FLIGHTS.withIndex().let { all -> if (sort == 1) all.sortedBy { it.value.price } else all.toList() }
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        background = { TravelSky() },
        topBar = {
            KTopAppBar(
                "${TRAVEL_AIRPORTS[from].second} → ${TRAVEL_AIRPORTS[to].second}",
                onBack = onBack,
                subtitle = "가는 편 · ${TRAVEL_DAY.format(day)}",
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = appPadding(16.dp, 640.dp),
                end = appPadding(16.dp, 640.dp),
                top = padding.calculateTopPadding() + 8.dp,
                bottom = navigationBarBottom() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("week") {
                KSurface(Modifier.fillMaxWidth(), shape = kitShape { card }) {
                    KWeekStrip(day, onDay, Modifier.padding(vertical = 8.dp), range = LocalDate.now()..LocalDate.now().plusYears(1))
                }
            }
            item("sort") {
                KChipGroup(listOf("추천순", "가격 낮은 순", "출발 빠른 순"), setOf(sort), {
                    sort = it.firstOrNull() ?: 0
                }, wrap = false, tone = KTone.Neutral)
            }
            items(sorted, key = { it.index }) { (index, f) ->
                FlightCard(f, from, to, Modifier.animateItem()) { onPick(index) }
            }
        }
    }
}

@Composable
private fun FlightCard(f: Flight, from: Int, to: Int, modifier: Modifier, onClick: () -> Unit) {
    KSurface(onClick, modifier.fillMaxWidth(), shape = kitShape { card }, onClickLabel = "좌석 고르기") {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(f.airline, 13, FontWeight.SemiBold, Modifier.weight(1f))
                if (f.seatsLeft <= 5) Label("${f.seatsLeft}석 남음", 12, FontWeight.SemiBold, color = Color(0xFFFFD166))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeBlock(f.departs, TRAVEL_AIRPORTS[from].first)
                FlightPath(f.duration, Modifier.weight(1f).padding(horizontal = 12.dp))
                TimeBlock(f.arrives, TRAVEL_AIRPORTS[to].first, end = true)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(
                    if (f.direct) "직항" else "1회 경유",
                    13,
                    modifier = Modifier.weight(1f),
                    color = LocalKineticaContentColor.current.copy(alpha = 0.75f),
                )
                KPriceTag(won(f.price), size = KPriceSize.Medium, period = "1인")
            }
        }
    }
}

@Composable
private fun TimeBlock(time: String, code: String, end: Boolean = false) {
    Column(horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Label(time, 22, FontWeight.Bold)
        Label(code, 13, color = LocalKineticaContentColor.current.copy(alpha = 0.75f))
    }
}

@Composable
private fun FlightPath(duration: String, modifier: Modifier) {
    val ink = LocalKineticaContentColor.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(22.dp), Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                drawLine(
                    ink.copy(alpha = 0.5f),
                    Offset(0f, 0f),
                    Offset(size.width, 0f),
                    3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                )
            }
            Box(Modifier.size(28.dp).background(KineticaTheme.colors.accent, CircleShape), Alignment.Center) {
                ShowcaseIcon(AppIcons.Plane, 16.dp, Color.White)
            }
        }
        Label(duration, 12, color = ink.copy(alpha = 0.75f))
    }
}

@Composable
private fun SeatMap(seat: String, onSeat: (String) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        background = { TravelSky() },
        topBar = { KTopAppBar("좌석 선택", onBack = onBack, subtitle = "일반석 · 3-3 배열") },
        bottomBar = {
            KSurface(Modifier.fillMaxWidth(), shape = kitTopShape()) {
                Row(
                    Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Label(if (seat.isEmpty()) "좌석을 골라 주세요" else "선택한 좌석 $seat", 15, FontWeight.SemiBold)
                        Label(
                            if (seat.dropLast(1).toIntOrNull() in 1..2) {
                                "앞쪽 좌석 +15,000원"
                            } else {
                                "추가 요금 없음"
                            },
                            12,
                            color = LocalKineticaContentColor.current.copy(alpha = 0.7f),
                        )
                    }
                    KButton("다음", onNext, enabled = seat.isNotEmpty(), size = KButtonSize.Large)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item("legend") {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Legend("선택 가능", SeatState.Free)
                    Legend("선택함", SeatState.Picked)
                    Legend("예약됨", SeatState.Taken)
                }
            }
            items(ROWS, key = { it }) { row ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    "ABC".forEach { col -> SeatBox(row, col, seat, onSeat) }
                    Label(
                        "$row",
                        13,
                        FontWeight.SemiBold,
                        Modifier.width(28.dp).padding(start = 6.dp),
                        color = LocalKineticaContentColor.current.copy(alpha = 0.8f),
                    )
                    "DEF".forEach { col -> SeatBox(row, col, seat, onSeat) }
                }
            }
        }
    }
}

private enum class SeatState { Free, Picked, Taken }

@Composable
private fun Legend(label: String, state: SeatState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SeatShape(state, Modifier.size(18.dp))
        Label(label, 12, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun SeatShape(state: SeatState, modifier: Modifier) {
    val ink = LocalKineticaContentColor.current
    // 좌석 등받이를 흉내 낸 모양이라 형태 설정을 따르지 않는다.
    val shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    Box(
        modifier
            .background(
                when (state) {
                    SeatState.Free -> ink.copy(alpha = 0.55f)
                    SeatState.Picked -> KineticaTheme.colors.accent
                    SeatState.Taken -> ink.copy(alpha = 0.1f)
                },
                shape,
            )
            .border(
                1.dp,
                if (state ==
                    SeatState.Picked
                ) {
                    Color.White.copy(alpha = 0.9f)
                } else {
                    ink.copy(
                        alpha = if (state ==
                            SeatState.Taken
                        ) {
                            0.12f
                        } else {
                            0.7f
                        },
                    )
                },
                shape,
            ),
    )
}

@Composable
private fun SeatBox(row: Int, col: Char, picked: String, onSeat: (String) -> Unit) {
    val id = "$row$col"
    val taken = remember(id) { (row * 7 + col.code * 3) % 5 == 0 || (row + col.code) % 7 == 0 }
    val state = when {
        id == picked -> SeatState.Picked
        taken -> SeatState.Taken
        else -> SeatState.Free
    }
    Box(
        Modifier
            .size(40.dp, 44.dp)
            // 눌림 표시는 좌석 등받이 모양과 맞춘다.
            .kClickable(RoundedCornerShape(10.dp), enabled = !taken, role = Role.RadioButton) { onSeat(id) }
            .semantics {
                contentDescription = "$id 좌석, " + if (taken) {
                    "예약됨"
                } else if (id == picked) {
                    "선택함"
                } else {
                    "선택 가능"
                }
            },
        Alignment.Center,
    ) {
        SeatShape(state, Modifier.size(34.dp, 38.dp))
        if (state == SeatState.Picked) Label(id, 11, FontWeight.Bold, color = KineticaTheme.colors.onAccent)
    }
}

/** 탑승권에 찍을 내용. [from], [to]는 [TRAVEL_AIRPORTS] 순번이다. */
internal data class Ticket(val from: Int, val to: Int, val day: LocalDate, val flight: Flight, val seat: String)

internal class Flight(
    val airline: String,
    val departs: String,
    val arrives: String,
    val duration: String,
    val price: Int,
    val direct: Boolean,
    val seatsLeft: Int,
    val boarding: String,
)

internal val TRAVEL_AIRPORTS = listOf(
    "GMP" to "김포",
    "ICN" to "인천",
    "CJU" to "제주",
    "PUS" to "김해",
    "KIX" to "오사카",
)
private val FLIGHTS = listOf(
    Flight("하늘항공 HN 1203", "07:10", "08:20", "1시간 10분", 58_900, true, 4, "06:40"),
    Flight("구름에어 CL 311", "09:45", "10:55", "1시간 10분", 64_300, true, 12, "09:15"),
    Flight("하늘항공 HN 1217", "13:30", "14:40", "1시간 10분", 49_800, true, 2, "13:00"),
    Flight("바람항공 WD 88", "16:05", "18:35", "2시간 30분", 41_200, false, 9, "15:35"),
    Flight("구름에어 CL 327", "19:20", "20:30", "1시간 10분", 71_000, true, 20, "18:50"),
)
private val ROWS = (1..18).toList()
internal val TRAVEL_DAY = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

internal val TRAVEL_LOOK = AppLook(
    SurfaceStyle.Glass(GlassStyles.Vision),
    SurfaceStyle.Glass(GlassStyles.Obsidian),
    defaultDark = false,
    accent = Color(0xFF2F6BFF),
    accentDark = Color(0xFF6E9BFF),
)
