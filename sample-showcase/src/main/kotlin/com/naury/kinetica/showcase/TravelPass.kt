// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

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

/** 예약을 마친 뒤 받는 탑승권. 끌거나 버튼을 누르면 뒤집혀 탑승 안내가 나온다. */
@Composable
internal fun BoardingPass(ticket: Ticket, onDone: () -> Unit) {
    var flipped by rememberSaveable { mutableStateOf(false) }
    val toaster = rememberKToasterState()
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        background = { TravelSky() },
        topBar = { KTopAppBar("예약 완료", onBack = onDone) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
                start = 24.dp,
                end = 24.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = navigationBarBottom() + 32.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Label("탑승권이 나왔어요. 끌어서 뒤집으면 안내가 보여요.", 14, color = LocalKineticaContentColor.current.copy(alpha = 0.8f))
            FlipCard(
                flipped,
                { flipped = it },
                Modifier.widthIn(max = 380.dp).fillMaxWidth().aspectRatio(0.62f),
                front = { PassFront(ticket) },
                back = { PassBack() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = 380.dp)) {
                KButton("뒤집기", { flipped = !flipped }, Modifier.weight(1f), variant = KVariant.Tonal)
                KButton("지갑에 추가", { toaster.show("지갑에 탑승권을 넣었어요", KStatus.Success) }, Modifier.weight(1f))
            }
        }
    }
    KToaster(toaster)
}

@Composable
private fun PassFront(ticket: Ticket) {
    val from = ticket.from
    val to = ticket.to
    val flight = ticket.flight
    val ink = Color(0xFF1B2333)
    Column(Modifier.fillMaxSize().background(Color.White, RoundedCornerShape(28.dp))) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF2F6BFF), Color(0xFF5BC0F8))),
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ).padding(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label(flight.airline, 13, FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label(TRAVEL_AIRPORTS[from].first, 34, FontWeight.Black, color = Color.White)
                    Box(Modifier.weight(1f), Alignment.Center) { ShowcaseIcon(AppIcons.Plane, 22.dp, Color.White) }
                    Label(TRAVEL_AIRPORTS[to].first, 34, FontWeight.Black, color = Color.White)
                }
                Row {
                    Label(TRAVEL_AIRPORTS[from].second, 13, modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.85f))
                    Label(TRAVEL_AIRPORTS[to].second, 13, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row {
                PassField("승객", "김하늘", ink, Modifier.weight(1f))
                PassField("날짜", TRAVEL_DAY.format(ticket.day), ink, Modifier.weight(1f))
            }
            Row {
                PassField("탑승", flight.boarding, ink, Modifier.weight(1f))
                PassField("게이트", "12", ink, Modifier.weight(1f))
                PassField("좌석", ticket.seat.ifEmpty { "–" }, ink, Modifier.weight(1f))
            }
            // 점선으로 절취선을 그린다.
            Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                drawLine(
                    ink.copy(alpha = 0.25f),
                    Offset.Zero,
                    Offset(size.width, 0f),
                    3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) { FakeCode(ink, Modifier.fillMaxHeight(0.95f).aspectRatio(1f)) }
        }
    }
}

@Composable
private fun PassField(label: String, value: String, ink: Color, modifier: Modifier) {
    Column(modifier) {
        Label(label, 11, color = ink.copy(alpha = 0.55f))
        Label(value, 17, FontWeight.Bold, color = ink)
    }
}

/** 실제 코드가 아닌 무늬. 탑승권 아래 2차원 코드 자리를 채운다. */
@Composable
private fun FakeCode(ink: Color, modifier: Modifier) {
    Canvas(modifier) {
        val cell = size.minDimension / CODE_CELLS
        for (y in 0 until CODE_CELLS) {
            for (x in 0 until CODE_CELLS) {
                if (codeCell(x, y)) drawRect(ink, Offset(x * cell, y * cell), Size(cell, cell))
            }
        }
    }
}

/** 칸 ([x], [y])를 칠할지. 세 귀퉁이에는 위치 찾기 무늬(바깥 테두리와 가운데 3×3 칸)를, 나머지에는 고정된 점 무늬를 둔다. */
private fun codeCell(x: Int, y: Int): Boolean {
    val n = CODE_CELLS
    val inFinder = (x < 7 || x >= n - 7) && (y < 7 || (y >= n - 7 && x < 7))
    if (!inFinder) return (x * 31 + y * 17 + x * y) % 3 == 0
    val lx = if (x >= n - 7) x - (n - 7) else x
    val ly = if (y >= n - 7) y - (n - 7) else y
    val ring = lx == 0 || ly == 0 || lx == 6 || ly == 6
    return ring || (lx in 2..4 && ly in 2..4)
}

private const val CODE_CELLS = 21

@Composable
private fun PassBack() {
    val ink = Color(0xFF1B2333)
    Column(
        Modifier.fillMaxSize().background(Color.White, RoundedCornerShape(28.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label("탑승 안내", 20, FontWeight.Bold, color = ink)
        listOf(
            "탑승은 출발 30분 전에 시작하고 10분 전에 마감합니다.",
            "기내에 들고 탈 수 있는 짐은 10kg 이하 한 개입니다.",
            "보조 배터리는 맡기는 짐에 넣을 수 없습니다.",
            "출발 1시간 전까지 앱에서 좌석을 바꿀 수 있습니다.",
        ).forEach { Label("• $it", 14, color = ink.copy(alpha = 0.8f)) }
        Spacer(Modifier.weight(1f))
        Label("예약 번호 HN7Q2K", 13, FontWeight.SemiBold, color = ink.copy(alpha = 0.6f))
    }
}
