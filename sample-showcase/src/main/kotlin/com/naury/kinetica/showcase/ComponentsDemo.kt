// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.ui.GlassButton
import com.naury.kinetica.ui.GlassButtonKind
import com.naury.kinetica.ui.GlassButtonSize
import com.naury.kinetica.ui.GlassCarousel
import com.naury.kinetica.ui.GlassChipGroup
import com.naury.kinetica.ui.GlassChipSelection
import com.naury.kinetica.ui.GlassDatePicker
import com.naury.kinetica.ui.GlassIslandContent
import com.naury.kinetica.ui.GlassIslandHost
import com.naury.kinetica.ui.GlassLargeTitleBar
import com.naury.kinetica.ui.GlassMenuItem
import com.naury.kinetica.ui.GlassPageIndicator
import com.naury.kinetica.ui.GlassProgressBar
import com.naury.kinetica.ui.GlassProgressRing
import com.naury.kinetica.ui.GlassScaffold
import com.naury.kinetica.ui.GlassSearchBar
import com.naury.kinetica.ui.GlassSnackbarHost
import com.naury.kinetica.ui.GlassSnackbarResult
import com.naury.kinetica.ui.GlassSpeedDial
import com.naury.kinetica.ui.GlassStepper
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.GlassSurface
import com.naury.kinetica.ui.GlassTextField
import com.naury.kinetica.ui.GlassTimePicker
import com.naury.kinetica.ui.GradientText
import com.naury.kinetica.ui.MeshGradientBackground
import com.naury.kinetica.ui.MeshGradients
import com.naury.kinetica.ui.ScrambleText
import com.naury.kinetica.ui.ShimmerText
import com.naury.kinetica.ui.SwipeCardStack
import com.naury.kinetica.ui.SwipeDirection
import com.naury.kinetica.ui.glassBadge
import com.naury.kinetica.ui.rememberGlassIslandState
import com.naury.kinetica.ui.rememberGlassSnackbarState
import com.naury.kinetica.ui.rememberLargeTitleState
import com.naury.kinetica.ui.rememberSwipeCardStackState
import com.naury.kinetica.ui.shake
import com.naury.kinetica.ui.sparkle
import com.naury.kinetica.ui.spotlight
import java.time.LocalDate
import kotlinx.coroutines.launch

/** 컴포넌트 모음: kinetica-ui의 버튼, 입력, 피커, 캐러셀, 카드 더미, 알림, 글자 효과를 한 화면에서 눌러 본다. */
@Composable
internal fun ComponentsDemo(onBack: () -> Unit) {
    val title = rememberLargeTitleState()
    val island = rememberGlassIslandState()
    val snackbar = rememberGlassSnackbarState()
    var dial by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    GlassScaffold(
        Modifier.fillMaxSize(),
        style = GlassStyles.Liquid,
        background = { MeshGradientBackground(Modifier.fillMaxSize(), palette = MeshGradients.Candy) },
        topBar = { GlassLargeTitleBar(title, "컴포넌트", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().nestedScroll(title.connection),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ButtonsCard() }
            item { StepperCard() }
            item { ProgressCard() }
            item { InputCard() }
            item { PickerCard() }
            item { CarouselCard() }
            item { CardStackCard { scope.launch { snackbar.show(it) } } }
            item { TextEffectsCard() }
            item {
                NotifyCard(
                    onIsland = {
                        scope.launch {
                            island.show(GlassIslandContent("새 메시지", "민지: 오늘 저녁 어때?", icon = ShowcaseIcons.Bell, accent = Color(0xFF34C759)))
                        }
                    },
                    onTimer = {
                        scope.launch {
                            island.show(
                                GlassIslandContent(
                                    "타이머",
                                    "남은 시간 00:42",
                                    icon = ShowcaseIcons.Sparkle,
                                    accent = Color(0xFFFF9F0A),
                                    compactText = "00:42",
                                ),
                            )
                        }
                    },
                    onSnackbar = {
                        scope.launch {
                            val result = snackbar.show("사진을 지웠어요", "되돌리기")
                            if (result == GlassSnackbarResult.Action) snackbar.show("사진을 되돌렸어요")
                        }
                    },
                )
            }
        }
        DemoDial(dial, { dial = it }) { scope.launch { snackbar.show(it) } }
        GlassSnackbarHost(snackbar, Modifier.padding(bottom = 96.dp))
        GlassIslandHost(island)
    }
}

/** 오른쪽 아래 펼침 버튼. 항목을 누르면 [notify]로 알린다. */
@Composable
private fun DemoDial(open: Boolean, onOpenChange: (Boolean) -> Unit, notify: (String) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        GlassSpeedDial(
            open,
            onOpenChange,
            listOf(
                GlassMenuItem("사진 찍기", ShowcaseIcons.Sparkle) { notify("카메라를 열어요") },
                GlassMenuItem("메모", ShowcaseIcons.Pen) { notify("새 메모") },
                GlassMenuItem("공유", ShowcaseIcons.Cloud) { notify("공유할 곳을 골라요") },
            ),
            Modifier.navigationBarsPadding().padding(24.dp),
        )
    }
}

@Composable
private fun ButtonsCard() {
    var loading by remember { mutableStateOf(false) }
    DemoCard("버튼", "채움, 옅은 색, 글래스, 글자만. 누르면 젤리처럼 눌리고, 로딩 중에는 폭을 유지한 채 링이 돈다.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton("결제하기", { loading = !loading }, loading = loading)
            GlassButton("장바구니", {}, kind = GlassButtonKind.Tinted)
            GlassButton("더 보기", {}, kind = GlassButtonKind.Glass)
            GlassButton("취소", {}, kind = GlassButtonKind.Plain)
            GlassButton("작게", {}, size = GlassButtonSize.Small, kind = GlassButtonKind.Tinted)
            GlassButton("크게", {}, size = GlassButtonSize.Large)
        }
    }
}

@Composable
private fun StepperCard() {
    var count by remember { mutableIntStateOf(2) }
    var chips by remember { mutableStateOf(setOf(0)) }
    DemoCard("스테퍼, 칩, 배지", "숫자는 굴러가며 바뀌고, 길게 누르면 점점 빨라진다. 배지는 숫자가 바뀔 때 톡 튄다.") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            GlassStepper(count, { count = it }, range = 0..120)
            Box(Modifier.size(44.dp).background(Color(0xFF5B6CFF), RoundedCornerShape(12.dp)).glassBadge(count.takeIf { it > 0 }))
        }
        GlassChipGroup(listOf("전체", "사진", "동영상", "스크린샷", "즐겨찾기"), chips, { chips = it }, selection = GlassChipSelection.Multiple)
    }
}

@Composable
private fun ProgressCard() {
    var progress by remember { mutableFloatStateOf(0.35f) }
    DemoCard("진행 표시", "값이 바뀌면 용수철로 따라가고, 물결을 켜면 액체가 차오르듯 출렁인다.") {
        GlassProgressBar(progress, Modifier.fillMaxWidth(), thickness = 12.dp, wave = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GlassProgressRing(progress, Modifier.size(88.dp), wave = true) { Label("${(progress * 100).toInt()}%", 16, FontWeight.Bold) }
            GlassProgressRing(null, Modifier.size(48.dp))
            Pill("+ 20%") { progress = if (progress >= 1f) 0f else (progress + 0.2f).coerceAtMost(1f) }
        }
    }
}

@Composable
private fun InputCard() {
    var query by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var tries by remember { mutableIntStateOf(0) }
    DemoCard("검색과 입력", "검색창을 누르면 취소 버튼이 밀려 들어온다. 이메일이 틀리면 테두리가 빨개지며 흔들린다.") {
        GlassSearchBar(query, { query = it }, {})
        GlassTextField(email, {
            email = it
            error = null
        }, "이메일", Modifier.fillMaxWidth().shake(tries), error = error)
        Pill("확인") {
            if ("@" !in email) {
                error = "이메일 주소를 확인해 주세요"
                tries++
            }
        }
    }
}

@Composable
private fun PickerCard() {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var hour by remember { mutableIntStateOf(9) }
    var minute by remember { mutableIntStateOf(30) }
    DemoCard("휠 피커", "iOS처럼 원통에 붙은 휠. 굴리면 항목마다 가볍게 진동하고 가운데에 맞춰 멈춘다.") {
        GlassDatePicker(date, { date = it }, Modifier.fillMaxWidth())
        GlassTimePicker(hour, minute, { h, m ->
            hour = h
            minute = m
        }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun CarouselCard() {
    val pager = rememberPagerState { PHOTO_COLORS.size }
    DemoCard("캐러셀", "옆 카드가 살짝 보이고, 멀어질수록 작아지며 기운다. 아래 점이 액체처럼 이어진다.") {
        GlassCarousel(PHOTO_COLORS.size, Modifier.fillMaxWidth().height(180.dp), state = pager) { page ->
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(PHOTO_COLORS[page])))
        }
        GlassPageIndicator(pager, Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun CardStackCard(notify: (String) -> Unit) {
    val stack = rememberSwipeCardStackState()
    val scope = rememberCoroutineScope()
    DemoCard("카드 더미", "끌어서 좌우로 던진다. 넘긴 카드는 되돌릴 수 있다.") {
        SwipeCardStack(
            PEOPLE,
            { name, direction -> notify(if (direction == SwipeDirection.Right) "$name 좋아요" else "$name 넘김") },
            Modifier.fillMaxWidth().aspectRatio(1.3f),
            state = stack,
            overlay = { direction ->
                Label(if (direction == SwipeDirection.Right) "LIKE" else "NOPE", 28, FontWeight.Black, Modifier.padding(20.dp), Color.White)
            },
        ) { name ->
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(
                    Brush.linearGradient(
                        PHOTO_COLORS[
                            PEOPLE.indexOf(name) %
                                PHOTO_COLORS.size,
                        ],
                    ),
                ),
                contentAlignment = Alignment.BottomStart,
            ) { Label(name, 26, FontWeight.Bold, Modifier.padding(20.dp), Color.White) }
        }
        Pill("되돌리기") { scope.launch { stack.undo() } }
    }
}

@Composable
private fun TextEffectsCard() {
    var round by remember { mutableIntStateOf(0) }
    DemoCard("글자 효과", "빛이 흐르는 글자, 색이 흐르는 글자, 뒤섞였다 맞춰지는 글자. 카드에 손가락을 대고 움직여 보세요.") {
        GlassSurface(Modifier.fillMaxWidth().spotlight(shape = RoundedCornerShape(24.dp)).sparkle(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerText("밀어서 잠금 해제", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
                GradientText("Kinetica", style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Black))
                ScrambleText(SCRAMBLE[round % SCRAMBLE.size], style = TextStyle(fontSize = 20.sp, fontFamily = FontFamily.Monospace))
            }
        }
        Pill("다른 문구") { round++ }
    }
}

@Composable
private fun NotifyCard(onIsland: () -> Unit, onTimer: () -> Unit, onSnackbar: () -> Unit) {
    DemoCard("알림", "화면 위 캡슐이 부풀며 알림이 되고, 아래 스낵바는 되돌리기와 남은 시간을 보여 준다. 오른쪽 아래 버튼도 눌러 보세요.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("메시지 알림", onClick = onIsland)
            Pill("타이머", onClick = onTimer)
            Pill("삭제 후 되돌리기", onClick = onSnackbar)
        }
    }
}

private val PEOPLE = listOf("하늘", "바다", "숲", "노을", "별")
private val SCRAMBLE = listOf("ACCESS GRANTED", "SYNC COMPLETE", "HELLO, KINETICA")
private val PHOTO_COLORS = listOf(
    listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
    listOf(Color(0xFFFF7E5F), Color(0xFFFEB47B)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFF8E54E9), Color(0xFF4776E6)),
    listOf(Color(0xFFFF5C8A), Color(0xFFFFC15E)),
)
