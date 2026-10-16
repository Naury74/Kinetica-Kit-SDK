@file:Suppress("ktlint", "MaxLineLength", "FunctionOnlyReturningConstant", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED_ANONYMOUS_PARAMETER", "unused")

package com.naury.kinetica.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.util.Currency
import kotlinx.coroutines.launch
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * docs/35-ui-kit.md의 코드 예가 지금 공개 API로 컴파일되고 그려지는지 확인한다. 각 Block 함수의 본문은 문서 예를 그대로 옮겼고,
 * 앞쪽 몇 줄만 예에서 쓰는 변수를 마련한다. 문서 예를 고치면 여기도 함께 고친다(XML 진입점 예는 레이아웃 리소스가 필요해 뺐다).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class UiKitDocExamplesTest {
    @get:Rule
    val compose = createComposeRule()

    private val blocks: List<@Composable () -> Unit> = listOf({ Block01() }, { Block02() }, { Block03() }, { Block04() }, { Block05() }, { Block06() }, { Block07() }, { Block08() }, { Block09() }, { Block10() }, { Block11() }, { Block12() }, { Block13() }, { Block14() }, { Block15() }, { Block16() }, { Block17() }, { Block18() }, { Block19() }, { Block20() }, { Block21() }, { Block22() }, { Block23() }, { Block24() }, { Block25() }, { Block26() }, { Block27() }, { Block28() })

    @Test
    fun `UI 키트 문서의 예가 모두 그려진다`() {
        // 반복 애니메이션이 있는 예가 많아 시계를 멈추고 직접 넘긴다.
        compose.mainClock.autoAdvance = false
        var index by mutableIntStateOf(0)
        val shown = mutableSetOf<Int>()
        compose.setContent {
            KineticaTheme {
                Box(Modifier.size(400.dp, 800.dp)) {
                    blocks[index]()
                    SideEffect { shown += index }
                }
            }
        }
        for (i in blocks.indices) {
            compose.runOnIdle { index = i }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(600)
        }
        assertThat(shown).containsExactlyElementsIn(blocks.indices)
        // 마지막 예(직접 만든 글래스 판)의 글자가 보이는지 본다.
        compose.onNodeWithText("카드").assertExists()
    }

    @Composable
    private fun Block01() {
        var selected by rememberSaveable { mutableIntStateOf(0) }
        val listState = rememberLazyListState()
        KineticaScaffold(
            background = { Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF8EC5FC), Color(0xFFE0C3FC))))) },
            topBar = { KTopAppBar("사진", state = rememberKTopAppBarState(listState)) },
            bottomBar = { KTabBar(listOf(KTab("홈", Icons.Rounded.Home), KTab("검색", Icons.Rounded.Search)), selected, onSelect = { selected = it }) },
        ) { padding ->
            LazyColumn(state = listState, contentPadding = padding) {
                items(30) { i -> KCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { Text("사진 $i") } }
            }
        }
    }

    @Composable
    private fun Block02() {
        val scheme = lightColorScheme()
        val dark = false
        MaterialTheme(colorScheme = scheme) {
            KineticaTheme(
                darkTheme = dark,
                surface = if (dark) SurfaceStyles.CleanDark else SurfaceStyles.CleanLight,
                colors = KineticaColors(
                    accent = MaterialTheme.colorScheme.primary,
                    onAccent = MaterialTheme.colorScheme.onPrimary,
                    destructive = MaterialTheme.colorScheme.error,
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
            ) { App() }
        }
    }

    @Composable
    private fun Block03() {
        var open by remember { mutableStateOf(false) }
        // 컴포넌트 하나만 다른 표면으로
        KSheet(open, onDismissRequest = { open = false }, surface = SurfaceStyles.tonal(Color(0xFF2E7D32))) { Text("배송 정보") }

        // 직접 만든 요소도 같은 표면으로
        Box(Modifier.kSurface(shape = RoundedCornerShape(20.dp)).padding(16.dp)) { Text("직접 만든 카드") }

        // 프리셋에 없는 단색 표면
        val paper = SurfaceStyle.Solid(
            color = Color.White, contentColor = Color(0xFF1B1E26), background = Color(0xFFF3F4F8),
            border = Color(0x14000000), borderWidth = 1.dp, elevation = 4.dp,
        )
    }

    @Composable
    private fun Block04() {
        val painter = ColorPainter(Color.Gray)
        val photo = rememberGlassBackdrop()
        Box(Modifier.fillMaxWidth().height(240.dp)) {
            Image(painter, null, Modifier.fillMaxSize().glassBackdrop(photo), contentScale = ContentScale.Crop)
            KSurface(Modifier.align(Alignment.BottomCenter).padding(16.dp), surface = SurfaceStyles.Liquid, backdrop = photo) {
                Text("사진 위 글래스", Modifier.padding(16.dp))
            }
        }
    }

    @Composable
    private fun Block05() {
        var filter by remember { mutableStateOf(setOf(0)) }
        val scope = rememberCoroutineScope()
        val toast = rememberKToastState()
        KButton("결제하기", onClick = ::pay)                                              // Filled + Accent
        KButton("삭제", onClick = ::delete, variant = KVariant.Outlined, tone = KTone.Destructive)
        KIconButton(Icons.Rounded.Share, "공유", onClick = ::share)                        // 기본값 Glass
        KChipGroup(listOf("전체", "사진", "동영상"), filter, { filter = it }, variant = KVariant.Tonal)
        KSurface(onClick = ::open, variant = KVariant.Elevated) { Text("열기", Modifier.padding(16.dp)) }
        KButton("저장", onClick = { scope.launch { toast.show("저장했어요", tone = KTone.Positive) } }) // 글자 앞에 성공 아이콘
    }

    @Composable
    private fun Block06() {
        var tab by remember { mutableIntStateOf(0) }
        var checked by remember { mutableStateOf(false) }
        val shape = RoundedCornerShape(16.dp)
        // 모양을 알려 주면 모서리 밖은 칠하지 않는다
        Row(Modifier.clip(shape).kClickable(shape, onClickLabel = "열기") { open() }.padding(16.dp)) { Text("설정") }
        Box(Modifier.size(48.dp).clickable(interactionSource = null, indication = KIndication(CircleShape)) { open() })

        // 길게 누르기, 선택, 토글
        Box(Modifier.kCombinedClickable(shape, onLongClick = ::showMenu) { open() })
        Box(Modifier.kSelectable(selected = tab == 0, shape = CircleShape, role = Role.Tab) { tab = 0 })
        Box(Modifier.kToggleable(checked, shape, role = Role.Checkbox) { checked = it })
    }

    @Composable
    private fun Block07() {
        val adding = false
        val added = false
        var liked by remember { mutableStateOf(false) }
        val listState = rememberLazyListState()
        var period by remember { mutableIntStateOf(0) }
        KButton("장바구니에 담기", onClick = ::add, loading = adding, success = added, fullWidth = true, size = KButtonSize.Large)
        KIconToggleButton(liked, { liked = it }, Icons.Rounded.Heart, "좋아요", checkedIcon = Icons.Rounded.HeartFilled)
        KExtendedFab("새 글", Icons.Rounded.Add, onClick = ::newPost, expanded = rememberFabExpanded(listState))
        KToggleGroup(listOf(KGroupItem("일"), KGroupItem("주"), KGroupItem("월")), setOf(period), { period = it.first() }, Modifier.fillMaxWidth())
    }

    @Composable
    private fun Block08() {
        var wifi by remember { mutableStateOf(true) }
        var mode by remember { mutableIntStateOf(0) }
        val tags = listOf("카페", "식당")
        var picked by remember { mutableStateOf(setOf<String>()) }
        var volume by remember { mutableFloatStateOf(0.4f) }
        var price by remember { mutableStateOf(10f..80f) }
        var count by remember { mutableIntStateOf(0) }
        KSwitch(wifi, { wifi = it })
        KSegmentedControl(listOf("지도", "위성"), mode, { mode = it })
        KChipRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
            tags.forEach { tag -> KFilterChip(tag, tag in picked, { on -> picked = if (on) picked + tag else picked - tag }) }
        }
        KSlider(volume, { volume = it }, Modifier.fillMaxWidth())
        KRangeSlider(price, { price = it }, valueRange = 0f..100f)
        KQuantityStepper(count, { count = it }, style = KQuantityStyle.Collapsible)
    }

    @Composable
    private fun Block09() {
        var email by remember { mutableStateOf("") }
        val emailError: String? = null
        var price by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        KTextField(email, { email = it }, "이메일", error = emailError, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        KTextField(price, { price = it }, "가격", variant = KTextFieldVariant.Outlined, prefix = "₩", maxLength = 10)
        KPasswordField(password, { password = it }, "비밀번호", showChecklist = true)
        KCodeInput(code, { code = it }, onComplete = ::verify)
        KAmountInput(amount, { amount = it }, Currency.getInstance("KRW"), label = "보낼 금액")
    }

    @Composable
    private fun Block10() {
        var birthday by remember { mutableStateOf(LocalDate.of(2000, 1, 1)) }
        var picking by remember { mutableStateOf(false) }
        var start by remember { mutableStateOf<LocalDate?>(null) }
        var end by remember { mutableStateOf<LocalDate?>(null) }
        var selection by remember { mutableStateOf<KCalendarSelection>(KCalendarSelection.Range()) }
        KCalendar(rememberKCalendarState(), selection, onSelectionChange = { selection = it })
        KDatePicker(birthday, { birthday = it })
        KDateRangeDialog(picking, { picking = false }, start, end, onConfirm = { s, e -> start = s; end = e })
    }

    @Composable
    private fun Block11() {
        var airplane by remember { mutableStateOf(false) }
        val mails = sampleMails
        val listState = rememberLazyListState()
        var todos by remember { mutableStateOf(listOf("우유", "빵", "달걀")) }
        KListSection(header = "연결", footer = "비행기 모드를 켜면 모든 무선 연결이 끊깁니다.") {
            item { KSwitchRow("비행기 모드", airplane, { airplane = it }, leading = KListLeading.Icon(Icons.Rounded.Plane, container = Color(0xFFFF9500))) }
            item { KDisclosureRow("Wi-Fi", onClick = ::openWifi, value = "Home") }
        }

        LazyColumn {
            kListSection(mails, key = { it.id }, header = "받은편지함") { mail ->
                SwipeActionsRow(endActions = listOf(SwipeAction("삭제", Color(0xFFFF3B30), removesRow = true) { delete(mail) })) {
                    KListItem(mail.from, supporting = mail.subject, onClick = { open(mail) })
                }
            }
        }

        val reorder = rememberReorderableListState(listState) { from, to -> todos = todos.toMutableList().apply { add(to, removeAt(from)) } }
        LazyColumn(state = listState) {
            items(todos, key = { it }) { todo -> ReorderableItem(reorder, key = todo) { dragging -> KListItem(todo) } }
        }
    }

    @Composable
    private fun Block12() {
        var plan by remember { mutableStateOf("monthly") }
        KCard(onClick = ::openPlan, variant = KVariant.Elevated) { Text("이번 달 요약") }
        KSelectableCard(plan == "yearly", { plan = "yearly" }, role = Role.RadioButton) { Text("연간 결제") }
        val pager = rememberPagerState { 5 }
        KCarousel(5, Modifier.height(180.dp), state = pager) { page -> KCard { Text("배너 $page") } }
        KPageIndicator(pager)
    }

    @Composable
    private fun Block13() {
        val unread = 3
        KAvatar(KAvatar("민지"), size = KAvatarSize.Large, status = KAvatarStatus.Online)
        AvatarStack(listOf(KAvatar("민지"), KAvatar("준호"), KAvatar("서연")), max = 3)
        KIconButton(Icons.Rounded.Bell, "알림", onClick = ::openInbox, modifier = Modifier.kBadge(unread))
        KStatusBadge("배송 중", KStatus.Info)
    }

    @Composable
    private fun Block14() {
        val listState = rememberLazyListState()
        var query by remember { mutableStateOf("") }
        val bar = rememberKTopAppBarState(listState)
        KTopAppBar("사진", state = bar, onBack = ::finish, actions = { KIconButton(Icons.Rounded.Share, "공유", onClick = ::share) })

        val title = rememberKLargeTitleState()
        Column {
            KLargeTitleBar(title, "설정", bottom = { KSearchBar(query, { query = it }, onSearch = {}) })
            LazyColumn(Modifier.nestedScroll(title.connection)) { items(40) { KListItem("항목 $it") } }
        }

        val pages = rememberPagerState { 3 }
        KTopTabs(listOf(KTab("추천"), KTab("인기"), KTab("새 글", badge = 3)), pages, indicator = KTabIndicator.Underline)
    }

    @Composable
    private fun Block15() {
        var stack by rememberSaveable { mutableStateOf(listOf("home")) }
        PushNavigation(stack, onBack = { stack = stack.dropLast(1) }) { screen ->
            when (screen) {
                "home" -> KButton("상세 열기", onClick = { stack = stack + "detail" })
                else -> Text("상세")
            }
        }

        val expand = rememberKExpandState()
        KCard(Modifier.expandSource(expand, "today"), onClick = { expand.expand("today") }) { Text("오늘의 추천") }
        KExpandHost(expand) { key -> Text("$key 상세", Modifier.statusBarsPadding()) }
    }

    @Composable
    private fun Block16() {
        var confirm by remember { mutableStateOf(false) }
        var asking by remember { mutableStateOf(false) }
        var sharing by remember { mutableStateOf(false) }
        var origin by remember { mutableStateOf<Offset?>(null) }
        KButton("주문", onClick = { confirm = true }, modifier = Modifier.kOrigin { origin = it })
        KDialog(confirm, { confirm = false }, origin = origin) {
            Text("결제할까요?")
            KButton("결제", onClick = { confirm = false }, fullWidth = true)
        }
        KAlertDialog(asking, { asking = false }, "삭제할까요?", listOf(
            KDialogAction("취소", KDialogActionStyle.Cancel) { asking = false },
            KDialogAction("삭제", KDialogActionStyle.Destructive, onClick = ::delete),
        ))
        KSheet(sharing, { sharing = false }, title = "공유") { KListItem("링크 복사", onClick = { sharing = false }) }

        // 화면을 채우는 곳(스크롤 목록 밖)에 둔다
        val places = rememberKDetentSheetState(listOf(Detent.Peek(120.dp), Detent.Fraction(0.5f), Detent.Full))
        KDetentSheet(places, title = "주변 장소") { Text("카페 12곳") }
    }

    @Composable
    private fun Block17() {
        var menu by remember { mutableStateOf(false) }
        var hidden by remember { mutableStateOf(false) }
        Box {
            KIconButton(Icons.Rounded.More, "더 보기", onClick = { menu = true })
            KDropdownMenu(menu, { menu = false }, listOf(
                KMenuEntry.Check("숨긴 파일 보기", hidden) { hidden = it },
                KMenuEntry.Divider,
                KMenuEntry.Action("삭제", destructive = true) { delete() },
            ))
        }
        KCard(Modifier.kContextMenu(listOf(KMenuItem("공유") { share() }, KMenuItem("삭제", destructive = true) { delete() }))) { Text("사진") }
        KIconButton(Icons.Rounded.Info, "도움말", onClick = {}, modifier = Modifier.tooltipOnLongPress("길게 누르면 설명이 보여요"))
    }

    @Composable
    private fun Block18() {
        val offline = true
        val scope = rememberCoroutineScope()
        val toast = rememberKToastState()
        val toaster = rememberKToasterState()
        val snackbar = rememberKSnackbarState()
        KineticaScaffold { padding ->
            Column(Modifier.padding(padding)) {
                KBanner(offline, "인터넷에 연결되어 있지 않아요", status = KStatus.Warning, actions = listOf(KBannerAction("다시 시도", ::retry)))
                KButton("저장", onClick = { scope.launch { toast.show("저장했어요", tone = KTone.Positive) } })
                KButton("올리기", onClick = {
                    scope.launch { toaster.promise("사진을 올리는 중…", success = { n: Int -> "사진 ${n}장을 올렸어요" }, error = { "올리지 못했어요" }) { upload() } }
                })
                KButton("삭제", onClick = {
                    scope.launch { if (snackbar.show("메일을 삭제했어요", actionLabel = "되돌리기") == KSnackbarResult.Action) restore() }
                })
            }
            KToastHost(toast)
            KToaster(toaster)
            KSnackbarHost(snackbar)
        }
    }

    @Composable
    private fun Block19() {
        val refreshing = false
        val loading = true
        val syncing = true
        val mails = sampleMails
        KRefreshBox(refreshing, onRefresh = ::reload) {
            LazyColumn {
                if (loading) items(6) { KSkeletonListItem() } else items(mails) { KListItem(it.subject) }
            }
        }
        KProgressRing(0.62f, Modifier.size(64.dp), wave = true) { Text("62%") }
        KCard(Modifier.borderSpinner(active = syncing, shape = RoundedCornerShape(24.dp))) { Text("동기화 중") }
    }

    @Composable
    private fun Block20() {
        var paid by remember { mutableStateOf(false) }
        KResultDialog(paid, KResult.Success, "결제했어요", onDismissRequest = { paid = false })
        KEmptyState("검색 결과가 없어요", message = "다른 단어로 찾아보세요", art = { KEmptyIcon(KEmptyIconKind.Search) })
        val haptics = rememberKHaptics()
        KButton("완료", onClick = { haptics.perform(KHapticType.Success) })
    }

    @Composable
    private fun Block21() {
        val app = listOf(3f, 5f, 4f, 8f)
        val web = listOf(2f, 4f, 6f, 5f)
        val days = listOf("월", "화", "수", "목")
        val colors = KChartPalettes.categorical(KineticaTheme.surface)
        KLineChart(points = listOf(3f, 5f, 4f, 8f, 6f), Modifier.fillMaxWidth().height(160.dp), labels = listOf("월", "화", "수", "목", "금"))
        KAreaChart(listOf(ChartSeries(app, colors[0], "앱"), ChartSeries(web, colors[1], "웹")), Modifier.height(200.dp), labels = days)
        KMultiBarChart(listOf(ChartSeries(app, colors[0], "앱"), ChartSeries(web, colors[1], "웹")), Modifier.height(200.dp), labels = days, mode = KBarMode.Grouped)
        KDonutChart(listOf(ChartSlice(42f, "식비", colors[0]), ChartSlice(18f, "교통", colors[1])), Modifier.size(200.dp))
        KStatCard("이번 주 방문", "12,480", change = 0.124f, trend = app, caption = "지난주 대비")
        KGauge(72f, label = "배터리", segments = listOf(KGaugeSegment(20f, KChartPalettes.Critical), KGaugeSegment(100f, KChartPalettes.Good)))
    }

    @Composable
    private fun Block22() {
        val saleEndsAt = System.currentTimeMillis() + 3_600_000L
        var plan by remember { mutableStateOf("basic") }
        KTimeline(listOf(
            KTimelineItem("주문 접수", time = "10:02", status = KTimelineStatus.Done),
            KTimelineItem("배송 중", status = KTimelineStatus.Current),
            KTimelineItem("도착"),
        ))
        val left by rememberCountdown(saleEndsAt)                       // 남은 ms
        FlipDigits(countdownText(left))
        KPricingCard("프로", "₩9,900", selected = plan == "pro", onSelect = { plan = "pro" }, period = "월", recommendedLabel = "추천")
    }

    @Composable
    private fun Block23() {
        val photos = listOf<Painter>(ColorPainter(Color.Red), ColorPainter(Color.Blue))
        val playing = true
        val viewer = rememberKImageViewerState()
        Row { photos.forEachIndexed { i, p -> Image(p, null, Modifier.size(96.dp).imageViewerSource(viewer, i).clickable { viewer.open(i) }) } }
        KImageViewer(viewer, photos)

        val player = rememberKMiniPlayerState()
        Box(Modifier.fillMaxSize()) {
            KMiniPlayer(
                player,
                artwork = { Box(Modifier.fillMaxSize().background(Color(0xFFFF7A45))) },
                collapsed = { Text("지금 재생 중") },
                expanded = { PlayPauseIcon(playing, Modifier.size(48.dp)) },
            )
        }
    }

    @Composable
    private fun Block24() {
        var draft by remember { mutableStateOf("") }
        var story by remember { mutableStateOf(false) }
        KChatBubble("내일 몇 시에 볼까요?", KChatSide.Received, position = chatGroupPosition(sameAsPrevious = false, sameAsNext = true))
        KChatBubble("2시 어때요?", KChatSide.Sent, time = "오후 2:14", status = KChatStatus.Read)
        KTypingIndicator()
        KChatInput(draft, { draft = it }, onSend = { draft = "" }, onAttach = ::pickFile, onVoice = ::record)
        KStoryRing(seen = false, onClick = { story = true }) { KAvatar(KAvatar("민지"), size = KAvatarSize.Large) }
        KAttachmentChip("견적서_최종.pdf", detail = "2.4MB", status = KAttachmentStatus.Uploading, progress = 0.4f, onRemove = {})
        KMapPin(label = "₩12,000", selected = true)
        // 같은 절 아래의 글자 수와 TalkBack 이름 예
        KChatInput(draft, { draft = it }, onSend = { draft = "" }, placeholder = "메시지 보내기", label = "민지에게 메시지", maxLength = 500, counterThreshold = 50)
    }

    @Composable
    private fun Block25() {
        val total = 12_000
        val listState = rememberLazyListState()
        val todos = listOf("우유", "빵")
        KButton("구매", onClick = ::buy, modifier = Modifier.jellyPress().glowBorder(CircleShape))
        RollingText("₩$total", style = TextStyle(fontSize = 32.sp))
        ShimmerText("새 기능이 생겼어요")

        val confetti = rememberConfettiState()
        Box(Modifier.fillMaxSize()) {
            MeshGradientBackground(Modifier.fillMaxSize(), palette = MeshGradients.Ocean)
            KButton("축하하기", onClick = { confetti.burst(Offset(540f, 1200f)) })
            ConfettiHost(confetti, Modifier.fillMaxSize())
        }

        val stagger = rememberKStaggerState()
        LazyColumn(state = listState) {
            itemsIndexed(todos) { i, todo -> KListItem(todo, Modifier.staggerIn(i, stagger).scrollTransform(listState, i)) }
        }
    }

    @Composable
    private fun Block26() {
        val shape = RoundedCornerShape(20.dp)
        Box(
            Modifier
                .jellyPress()          // 1. 움직임: jellyPress, magnetic, motionTilt, shake, kineticaEffects
                .glowBorder(shape)     // 2. 겉에 그리는 효과: glowBorder, sparkle, kBadge, skeleton. 잘리지 않게 clip보다 앞에 둔다
                .glass(shape = shape)  // 3. 배경: glass, kSurface, background
                .clip(shape)           // 4. 자르기
                .clickable { open() }  // 5. 입력: clickable, kClickable, kContextMenu, tooltipOnLongPress
                .padding(16.dp),
        ) { Text("항목") }
    }

    @Composable
    private fun Block27() {
        val mails = sampleMails
        var open by rememberSaveable { mutableStateOf<String?>(null) }
        KListDetail(open, onBack = { open = null }, list = {
            LazyColumn { items(mails) { KListItem(it.subject, onClick = { open = it.id }) } }
        }) { id -> Text("메일 $id", Modifier.kReadableWidth()) }

        val window = rememberKWindowLayout()
        LazyVerticalGrid(GridCells.Fixed(window.columns(minCellWidth = 320.dp))) { items(20) { KCard { Text("카드 $it") } } }
    }

    @Composable
    private fun Block28() {
        var mode by remember { mutableIntStateOf(0) }
        Box(Modifier.glass(shape = RoundedCornerShape(28.dp)).padding(16.dp)) {
            KGlassLayer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.glass(shape = RoundedCornerShape(16.dp)).padding(12.dp)) { Text("카드") }
                    KSegmentedControl(listOf("켜기", "끄기"), mode, { mode = it })
                }
            }
        }
    }

}

private data class DocMail(val id: String, val from: String, val subject: String)

private val sampleMails = listOf(DocMail("1", "민지", "회의 자료"), DocMail("2", "준호", "주말 일정"))

@Composable
private fun App() {
    Text("앱")
}

private object Icons {
    object Rounded {
        val Home = docIcon("home")
        val Search = docIcon("search")
        val Share = docIcon("share")
        val Heart = docIcon("heart")
        val HeartFilled = docIcon("heartFilled")
        val Add = docIcon("add")
        val Plane = docIcon("plane")
        val Bell = docIcon("bell")
        val More = docIcon("more")
        val Info = docIcon("info")
    }
}

private fun docIcon(name: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .path(fill = SolidColor(Color.Black)) {
        moveTo(4f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 20f)
        close()
    }.build()

private fun pay() = Unit
private fun delete() = Unit
private fun delete(mail: DocMail) = Unit
private fun share() = Unit
private fun open() = Unit
private fun open(mail: DocMail) = Unit
private fun showMenu() = Unit
private fun add() = Unit
private fun newPost() = Unit
private fun verify(code: String) = Unit
private fun openWifi() = Unit
private fun openPlan() = Unit
private fun openInbox() = Unit
private fun finish() = Unit
private fun retry() = Unit
private suspend fun upload(): Int = 3
private fun restore() = Unit
private fun reload() = Unit
private fun pickFile() = Unit
private fun record() = Unit
private fun buy() = Unit
