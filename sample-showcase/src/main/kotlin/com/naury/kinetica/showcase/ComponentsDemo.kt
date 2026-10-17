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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.GradientText
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KCarousel
import com.naury.kinetica.ui.KChipGroup
import com.naury.kinetica.ui.KChipSelection
import com.naury.kinetica.ui.KDatePicker
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KIslandContent
import com.naury.kinetica.ui.KIslandHost
import com.naury.kinetica.ui.KIslandState
import com.naury.kinetica.ui.KLargeTitleBar
import com.naury.kinetica.ui.KMenuItem
import com.naury.kinetica.ui.KPageIndicator
import com.naury.kinetica.ui.KProgressBar
import com.naury.kinetica.ui.KProgressRing
import com.naury.kinetica.ui.KSearchBar
import com.naury.kinetica.ui.KShapeToken
import com.naury.kinetica.ui.KSnackbarHost
import com.naury.kinetica.ui.KSnackbarResult
import com.naury.kinetica.ui.KSnackbarState
import com.naury.kinetica.ui.KSpeedDial
import com.naury.kinetica.ui.KStepper
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KTab
import com.naury.kinetica.ui.KTextField
import com.naury.kinetica.ui.KTimePicker
import com.naury.kinetica.ui.KTopTabs
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.MeshGradientBackground
import com.naury.kinetica.ui.MeshGradients
import com.naury.kinetica.ui.ScrambleText
import com.naury.kinetica.ui.ShimmerText
import com.naury.kinetica.ui.SwipeCardStack
import com.naury.kinetica.ui.SwipeDirection
import com.naury.kinetica.ui.kBadge
import com.naury.kinetica.ui.rememberKIslandState
import com.naury.kinetica.ui.rememberKLargeTitleState
import com.naury.kinetica.ui.rememberKSnackbarState
import com.naury.kinetica.ui.rememberSwipeCardStackState
import com.naury.kinetica.ui.shake
import com.naury.kinetica.ui.sparkle
import com.naury.kinetica.ui.spotlight
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * 컴포넌트 모음: kinetica-ui 컴포넌트를 종류별 탭(기본, 입력, 미디어, 효과, 버튼·칩·카드 등)으로 나눠 눌러 본다.
 * 탭 바는 큰 제목 아래에 붙어 있고, 탭마다 목록이 따로 스크롤된다. [initialTab]은 처음 열 탭의 순서다(UI 키트 허브에서 분류를 눌러 들어올 때).
 */
@Composable
internal fun ComponentsDemo(onBack: () -> Unit, initialTab: Int = 0) {
    val island = rememberKIslandState()
    val snackbar = rememberKSnackbarState()
    val pager = rememberPagerState(initialTab.coerceIn(0, COMPONENT_TABS.lastIndex)) { COMPONENT_TABS.size }
    // 탭마다 목록 스크롤 위치가 따로 있고, 큰 제목은 지금 보이는 탭 목록의 위치를 따른다.
    // 맨 위에 있는 탭으로 옮기면 큰 제목이 다시 펼쳐져 탭 줄 아래에 빈 공간이 생기지 않는다.
    val lists = List(COMPONENT_TABS.size) { rememberLazyListState() }
    val title = rememberKLargeTitleState(lists[pager.settledPage])
    var dial by rememberSaveable { mutableStateOf(false) }
    var onboarding by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { scope.launch { snackbar.show(it) } }
    Box(Modifier.fillMaxSize()) {
        KineticaScaffold(
            Modifier.fillMaxSize(),
            surface = demoSurface(COMPONENTS_DEMO_LOOK),
            background = { DemoBackdrop { MeshBackdrop(light = MeshGradients.Candy, dark = MeshGradients.Midnight) } },
            topBar = {
                // 탭 줄은 큰 제목과 함께 올라가 작은 바 밑에 붙고, 접히면 글래스 배경이 탭 줄까지 이어진다.
                KLargeTitleBar(title, "컴포넌트", onBack = onBack, contentMaxWidth = TITLE_MAX_WIDTH, actions = {
                    SurfacePickerButton()
                }, bottom = {
                    // 큰 글자에서는 네 칸을 똑같이 나누면 "미디어"가 잘리므로 가로로 스크롤하는 탭으로 바꾼다.
                    val scrollable = LocalDensity.current.fontScale > 1.3f || COMPONENT_TABS.size > 4
                    KTopTabs(
                        COMPONENT_TABS.map { KTab(it) },
                        pager,
                        // bottom 줄은 이미 contentMaxWidth 안에 놓이므로, 첫 탭을 큰 제목의 안쪽 여백(20dp)에 맞춘다.
                        // 스크롤 탭은 안쪽에 12dp를 따로 두므로 바깥 여백을 그만큼 줄여, 넘친 탭이 20dp 선에서 뚝 잘려 보이지 않게 한다.
                        Modifier.padding(horizontal = if (scrollable) 8.dp else 20.dp).fillMaxWidth().padding(bottom = 6.dp),
                        scrollable = scrollable,
                    )
                })
            },
        ) { padding ->
            val list = PaddingValues(
                start = readingPadding(),
                end = readingPadding(),
                top = padding.calculateTopPadding() + 8.dp,
                bottom =
                    navigationBarBottom() + 120.dp,
            )
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                CompositionLocalProvider(LocalTabListState provides lists[page]) {
                    when (page) {
                        0 -> BasicTab(list, title.connection, island, snackbar)
                        1 -> InputTab(list, title.connection)
                        2 -> MediaTab(list, title.connection) { onboarding = true }
                        4 -> ButtonsChipsCardsTab(list, title.connection)
                        5 -> NavOverlayTab(list, title.connection)
                        6 -> FeedbackTab(list, title.connection)
                        7 -> DataDomainTab(list, title.connection)
                        8, CALL_TAB -> SocialOrCallTab(page, list, title.connection)
                        else -> EffectsTab(list, title.connection, notify)
                    }
                }
            }
            if (pager.currentPage == 0) DemoDial(dial, { dial = it }, notify)
            KSnackbarHost(snackbar, Modifier.padding(bottom = 96.dp))
            KIslandHost(island)
        }
        if (onboarding) OnboardingAgain { onboarding = false }
    }
}

/** 탭 한 장의 목록 스크롤 상태. [ComponentsDemo]가 탭마다 넣어 준다. */
private val LocalTabListState = staticCompositionLocalOf<LazyListState> { error("ComponentsDemo 안에서만 씁니다.") }

/** 탭 한 장의 목록. 스크롤하면 큰 제목이 접힌다. */
@Composable
internal fun ComponentTabList(padding: PaddingValues, connection: NestedScrollConnection, content: LazyListScope.() -> Unit) {
    LazyColumn(
        // 입력란에 초점이 가면 키보드 높이만큼 목록이 줄어 입력란이 가려지지 않는다.
        Modifier.fillMaxSize().imePadding().nestedScroll(connection),
        state = LocalTabListState.current,
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun BasicTab(padding: PaddingValues, connection: NestedScrollConnection, island: KIslandState, snackbar: KSnackbarState) {
    val scope = rememberCoroutineScope()
    ComponentTabList(padding, connection) {
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
                        island.show(KIslandContent("새 메시지", "민지: 오늘 저녁 어때?", icon = KIcons.Bell, accent = Color(0xFF34C759)))
                    }
                },
                onTimer = {
                    scope.launch {
                        island.show(
                            KIslandContent(
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
                        if (result == KSnackbarResult.Action) snackbar.show("사진을 되돌렸어요")
                    }
                },
            )
        }
    }
}

/** 오른쪽 아래 펼침 버튼. 항목을 누르면 [notify]로 알린다. */
@Composable
private fun DemoDial(open: Boolean, onOpenChange: (Boolean) -> Unit, notify: (String) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        KSpeedDial(
            open,
            onOpenChange,
            listOf(
                KMenuItem("사진 찍기", ShowcaseIcons.Sparkle) { notify("카메라를 열어요") },
                KMenuItem("메모", KIcons.Edit) { notify("새 메모") },
                KMenuItem("공유", ShowcaseIcons.Cloud) { notify("공유할 곳을 골라요") },
            ),
            Modifier.navigationBarsPadding().padding(24.dp),
        )
    }
}

@Composable
private fun ButtonsCard() {
    var loading by remember { mutableStateOf(false) }
    DemoCard("버튼", "채움, 옅은 색, 글래스, 글자만 있는 버튼입니다. 누르면 젤리처럼 눌리고, 로딩 중에는 폭을 유지한 채 링이 돕니다.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton("결제하기", { loading = !loading }, loading = loading)
            KButton("장바구니", {}, variant = KVariant.Tonal)
            KButton("더 보기", {}, variant = KVariant.Glass)
            KButton("취소", {}, variant = KVariant.Text)
        }
        // 크기가 다른 버튼은 한 줄에 세로 가운데로 맞춰 따로 보여 준다.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            KButton("작게", {}, size = KButtonSize.Small, variant = KVariant.Tonal)
            KButton("보통", {}, variant = KVariant.Tonal)
            KButton("크게", {}, size = KButtonSize.Large)
        }
    }
}

@Composable
private fun StepperCard() {
    var count by rememberSaveable { mutableIntStateOf(2) }
    var chips by rememberSaveable { mutableStateOf(setOf(0)) }
    DemoCard("스테퍼, 칩, 배지", "숫자는 굴러가며 바뀌고, 길게 누르면 점점 빨라집니다. 배지는 숫자가 바뀔 때 톡 튑니다.") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            KStepper(count, { count = it }, range = 0..120)
            // 앱 아이콘처럼 그린 알림 아이콘에 배지를 붙인다.
            Box(
                Modifier
                    .size(44.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF7C8CFF), Color(0xFF4A5AE8))), kitShape { KShapeToken.Medium })
                    .kBadge(count.takeIf { it > 0 }),
                contentAlignment = Alignment.Center,
            ) { ShowcaseIcon(KIcons.Bell, 22.dp, Color.White) }
        }
        KChipGroup(listOf("전체", "사진", "동영상", "스크린샷", "즐겨찾기"), chips, { chips = it }, selection = KChipSelection.Multiple)
    }
}

@Composable
private fun ProgressCard() {
    var progress by remember { mutableFloatStateOf(0.35f) }
    DemoCard("진행 표시", "값이 바뀌면 용수철처럼 따라가고, 물결을 켜면 액체가 차오르듯 출렁입니다.") {
        KProgressBar(progress, Modifier.fillMaxWidth(), thickness = 12.dp, wave = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            KProgressRing(progress, Modifier.size(88.dp), wave = true) { Label("${(progress * 100).toInt()}%", 16, FontWeight.Bold) }
            KProgressRing(null, Modifier.size(48.dp))
            Pill("+ 20%") { progress = if (progress >= 1f) 0f else (progress + 0.2f).coerceAtMost(1f) }
        }
    }
}

@Composable
private fun InputCard() {
    var query by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var tries by rememberSaveable { mutableIntStateOf(0) }
    DemoCard("검색과 입력", "검색창을 누르면 취소 버튼이 밀려 들어옵니다. 이메일 형식이 틀리면 테두리가 빨개지며 흔들립니다.") {
        KSearchBar(query, { query = it }, {}, placeholder = "메뉴나 가게 이름")
        KTextField(
            email,
            {
                email = it
                error = null
            },
            "이메일",
            Modifier.fillMaxWidth().shake(tries),
            error = error,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            placeholder = "name@example.com",
            supportingText = "영수증을 받을 주소입니다",
            leadingIcon = { ShowcaseIcon(KIcons.Mail, 20.dp, LocalKineticaContentColor.current.copy(alpha = 0.6f)) },
        )
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
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var hour by rememberSaveable { mutableIntStateOf(9) }
    var minute by rememberSaveable { mutableIntStateOf(30) }
    DemoCard("휠 피커", "iOS처럼 원통에 붙은 휠입니다. 굴리면 항목마다 가볍게 진동하고 가운데에 맞춰 멈춥니다.") {
        KDatePicker(date, { date = it }, Modifier.fillMaxWidth())
        KTimePicker(hour, minute, { h, m ->
            hour = h
            minute = m
        }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun CarouselCard() {
    val pager = rememberPagerState { PHOTO_COLORS.size }
    DemoCard("캐러셀", "옆 카드가 살짝 보이고, 멀어질수록 작아지며 기웁니다. 아래 인디케이터가 액체처럼 이어집니다.") {
        KCarousel(PHOTO_COLORS.size, Modifier.fillMaxWidth().height(180.dp), state = pager) { page ->
            Box(Modifier.fillMaxSize().clip(kitShape { card }).background(Brush.linearGradient(PHOTO_COLORS[page])))
        }
        KPageIndicator(pager, Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun CardStackCard(notify: (String) -> Unit) {
    val stack = rememberSwipeCardStackState()
    val scope = rememberCoroutineScope()
    DemoCard("카드 더미", "카드를 끌어 좌우로 던집니다. 넘긴 카드는 되돌릴 수 있습니다.") {
        SwipeCardStack(
            PEOPLE,
            { name, direction -> notify(if (direction == SwipeDirection.Right) "$name 좋아요" else "$name 넘김") },
            // 넓은 화면에서 카드가 카드 폭만큼 커지면 한 장이 화면을 다 덮으므로 폰 크기로 묶어 가운데에 둔다.
            Modifier.widthIn(max = STACK_MAX_WIDTH).fillMaxWidth().aspectRatio(1.3f).align(Alignment.CenterHorizontally),
            state = stack,
            overlay = { direction ->
                Label(if (direction == SwipeDirection.Right) "LIKE" else "NOPE", 28, FontWeight.Black, Modifier.padding(20.dp), Color.White)
            },
        ) { name ->
            Box(
                Modifier.fillMaxSize().clip(innerShape).background(
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
        Pill("되돌리기", Modifier.align(Alignment.CenterHorizontally)) { scope.launch { stack.undo() } }
    }
}

@Composable
private fun TextEffectsCard() {
    var round by remember { mutableIntStateOf(0) }
    DemoCard("글자 효과", "빛이 흐르는 글자, 색이 흐르는 글자, 뒤섞였다 맞춰지는 글자. 카드에 손가락을 대고 움직여 보세요.") {
        KSurface(Modifier.fillMaxWidth().spotlight(shape = innerShape).sparkle(), shape = innerShape) {
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
    DemoCard("알림", "화면 위 캡슐이 부풀며 알림이 되고, 아래 스낵바는 되돌리기 버튼과 남은 시간을 보여 줍니다. 오른쪽 아래 버튼도 눌러 보세요.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("메시지 알림", onClick = onIsland)
            Pill("타이머", onClick = onTimer)
            Pill("삭제 후 되돌리기", onClick = onSnackbar)
        }
    }
}

internal val COMPONENT_TABS = listOf("기본", "입력", "미디어", "효과", "버튼·칩·카드", "내비게이션·오버레이", "피드백·로딩", "데이터·도메인", "소셜·모션", "통화")
private val PEOPLE = listOf("하늘", "바다", "숲", "노을", "별")
private val SCRAMBLE = listOf("ACCESS GRANTED", "SYNC COMPLETE", "HELLO, KINETICA")
internal val PHOTO_COLORS = listOf(
    listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
    listOf(Color(0xFFFF7E5F), Color(0xFFFEB47B)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFF8E54E9), Color(0xFF4776E6)),
    listOf(Color(0xFFFF5C8A), Color(0xFFFFC15E)),
)

private val STACK_MAX_WIDTH = 420.dp
private val COMPONENTS_DEMO_LOOK = DemoLook(GlassStyles.Liquid, GlassStyles.LiquidDark, defaultDark = false)
