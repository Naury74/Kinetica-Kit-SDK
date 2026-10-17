// 예시 화면 문구와 색은 시연용 데이터라 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KRevealShape
import com.naury.kinetica.ui.KScreenTransition
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KTransitionContent
import com.naury.kinetica.ui.KTransitionEdge
import com.naury.kinetica.ui.KTransitions
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.kClickable
import com.naury.kinetica.ui.kOrigin

/** 예시 칸 하나. 누르면 [entry] 화면이 열린다. [pair]는 이 예시가 보여 주는 전환 이름이다. */
internal class NavExample(val entry: String, val title: String, val pair: String, val icon: ImageVector, val colors: List<Color>)

/** 다음 화면으로 가는 버튼. [colors]가 있으면 그림 칸으로, 없으면 목록 행으로 그린다. */
internal class PageAction(val label: String, val target: String, val colors: List<Color>? = null)

/**
 * 예시 화면 하나. [art]가 있으면 그 그라디언트를 화면 전체에 깔고 흰 글자로 그리고, 없으면 쇼케이스 표면 위에 상단 바와 함께 그린다.
 * [transition]은 이 화면이 들어올 때 쓰는 전환이고, 뒤로 갈 때도 같은 전환이 되감긴다.
 */
internal class ExamplePage(
    val title: String,
    val transitionName: String,
    val body: String,
    val transition: (origin: () -> Offset?) -> KScreenTransition,
    val art: List<Color>? = null,
    val actions: List<PageAction> = emptyList(),
)

internal val EXAMPLES = listOf(
    NavExample("shop", "쇼핑 상세", "Push, Parallax", KIcons.Grid, listOf(Color(0xFFFFB199), Color(0xFFFF0844))),
    NavExample("settings", "설정 하위 화면", "CardStack", KIcons.Bell, listOf(Color(0xFF8E9EAB), Color(0xFF5B6576))),
    NavExample("compose", "새 글 작성", "SheetStack 두 겹", KIcons.Edit, listOf(Color(0xFF4FACFE), Color(0xFF00C6FB))),
    NavExample("modal", "전체 화면 다이얼로그", "Cover, 아래에서", KIcons.Plus, listOf(Color(0xFF30CFD0), Color(0xFF330867))),
    NavExample("onboarding", "온보딩에서 홈", "Uncover", KIcons.Home, listOf(Color(0xFFF6D365), Color(0xFFFDA085))),
    NavExample("app", "앱 열기", "Zoom", ShowcaseIcons.Cloud, listOf(Color(0xFF56CCF2), Color(0xFF2F80ED))),
    NavExample("story/1", "스토리", "Cube", ShowcaseIcons.Expand, listOf(Color(0xFFFF7EB3), Color(0xFFFF758C))),
    NavExample("card", "카드 뒤집기", "Flip", KIcons.Card, listOf(Color(0xFF232526), Color(0xFF414345))),
    NavExample("compare", "전후 비교", "Wipe", ShowcaseIcons.Layers, listOf(Color(0xFF9CA3AF), Color(0xFF43E97B))),
    NavExample("media", "미디어", "BlurDissolve", ShowcaseIcons.Wave, listOf(Color(0xFF654EA3), Color(0xFFEAAFC8))),
    NavExample(THEME_SCREEN, "테마 전환", "Circle, 누른 버튼에서", ShowcaseIcons.Contrast, listOf(Color(0xFF141E30), Color(0xFF243B55))),
    NavExample("checkout", "결제 축하", "Star", ShowcaseIcons.Sparkle, listOf(Color(0xFFFAD961), Color(0xFFF76B1C))),
)

private val SHOE = listOf(Color(0xFFFFB199), Color(0xFFFF0844))
private val BAG = listOf(Color(0xFF84FAB0), Color(0xFF8FD3F4))
private val STORY = listOf(
    listOf(Color(0xFFFF7EB3), Color(0xFFFF758C)),
    listOf(Color(0xFF7F7FD5), Color(0xFF86A8E7)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
)
private val push: (() -> Offset?) -> KScreenTransition = { KTransitions.Push }

internal val EXAMPLE_PAGES: Map<String, ExamplePage> = mapOf(
    "shop" to ExamplePage(
        "쇼핑",
        "Push",
        "목록에서 상세로 갈 때는 옆에서 밀려 들어오는 Push가 자연스럽습니다. 상품을 누르면 아래 화면이 더 깊이 물러나는 Parallax로 열립니다.",
        push,
        actions = listOf(PageAction("에어 러너", "shop/shoe", SHOE), PageAction("데이 팩", "shop/bag", BAG)),
    ),
    "shop/shoe" to ExamplePage("에어 러너", "Parallax", "129,000원. 가볍고 탄탄한 러닝화입니다. 가장자리를 밀면 목록이 다시 앞으로 나옵니다.", { KTransitions.Parallax }, SHOE),
    "shop/bag" to ExamplePage("데이 팩", "Parallax", "89,000원. 노트북 칸이 따로 있는 가벼운 배낭입니다.", { KTransitions.Parallax }, BAG),
    "settings" to ExamplePage(
        "설정",
        "CardStack",
        "하위 설정이 카드처럼 위에 쌓이고, 아래 카드는 작아지며 뒤로 물러납니다. 몇 단계를 들어가도 어디쯤인지 보입니다.",
        { KTransitions.CardStack },
        actions = listOf(PageAction("알림", "settings/notify"), PageAction("화면 표시", "settings/display")),
    ),
    "settings/notify" to ExamplePage(
        "알림",
        "CardStack",
        "한 단계 더 들어가면 카드가 하나 더 쌓입니다.",
        { KTransitions.CardStack },
        actions = listOf(PageAction("방해 금지", "settings/notify/dnd")),
    ),
    "settings/notify/dnd" to ExamplePage("방해 금지", "CardStack", "세 번째 카드입니다. 뒤로 가면 한 장씩 걷힙니다.", { KTransitions.CardStack }),
    "settings/display" to ExamplePage("화면 표시", "CardStack", "글자 크기와 밝기를 고르는 화면입니다.", { KTransitions.CardStack }),
    "compose" to ExamplePage(
        "새 글",
        "SheetStack",
        "새 글 화면이 시트처럼 아래에서 올라오고, 뒤 화면은 작아지며 위쪽 모서리가 둥글어집니다. 첨부를 고르면 시트가 한 겹 더 쌓입니다.",
        { KTransitions.SheetStack },
        actions = listOf(PageAction("사진 첨부", "compose/attach")),
    ),
    "compose/attach" to ExamplePage("사진 고르기", "SheetStack", "두 번째 시트입니다. 아래로 내려가면 새 글 시트가 다시 보입니다.", { KTransitions.SheetStack }),
    "modal" to ExamplePage(
        "일정 만들기",
        "Cover, 아래에서",
        "전체 화면 다이얼로그는 아래에서 올라와 이전 화면을 덮습니다. 이전 화면은 움직이지 않아 잠깐 들른 화면이라는 느낌이 납니다.",
        { KTransitions.cover(KTransitionEdge.Bottom) },
        actions = listOf(PageAction("저장", BACK)),
    ),
    "onboarding" to ExamplePage(
        "환영합니다",
        "Fade",
        "온보딩을 마치면 온보딩 화면이 옆으로 비켜나며 그 아래 있던 홈을 드러냅니다.",
        { KTransitions.Fade },
        listOf(Color(0xFFF6D365), Color(0xFFFDA085)),
        listOf(PageAction("시작하기", "onboarding/home")),
    ),
    "onboarding/home" to ExamplePage("홈", "Uncover", "온보딩 화면이 걷히고 홈이 드러났습니다.", { KTransitions.Uncover }),
    "app" to
        ExamplePage("날씨", "Zoom", "서울 18°, 맑음. 앱 아이콘을 누른 것처럼 화면이 작은 크기에서 커지며 열립니다.", {
            KTransitions.Zoom
        }, listOf(Color(0xFF56CCF2), Color(0xFF2F80ED))),
    "story/1" to
        ExamplePage("첫 번째 이야기", "Cube", "정육면체를 돌리듯 다음 이야기로 넘어갑니다.", {
            KTransitions.Cube
        }, STORY[0], listOf(PageAction("다음 이야기", "story/2"))),
    "story/2" to
        ExamplePage("두 번째 이야기", "Cube", "가장자리를 밀면 정육면체가 거꾸로 돕니다.", {
            KTransitions.Cube
        }, STORY[1], listOf(PageAction("다음 이야기", "story/3"))),
    "story/3" to ExamplePage("마지막 이야기", "Cube", "이야기가 끝났습니다.", { KTransitions.Cube }, STORY[2]),
    "card" to ExamplePage(
        "체크카드",
        "Push",
        "카드 앞면입니다. 뒤집기를 누르면 화면 전체가 카드처럼 뒤집혀 뒷면이 나옵니다.",
        push,
        listOf(Color(0xFF232526), Color(0xFF414345)),
        listOf(PageAction("뒤집기", "card/back")),
    ),
    "card/back" to
        ExamplePage("카드 뒷면", "Flip", "카드 번호와 유효 기간이 있는 뒷면입니다. 뒤로 가면 다시 뒤집힙니다.", {
            KTransitions.Flip
        }, listOf(Color(0xFF3A3D40), Color(0xFF181719))),
    "compare" to ExamplePage(
        "보정 전",
        "Push",
        "보정하기 전 사진입니다. 버튼을 누르면 경계선이 쓸고 지나가며 보정한 사진을 드러냅니다.",
        push,
        listOf(Color(0xFF9CA3AF), Color(0xFF6B7280)),
        listOf(PageAction("보정 후 보기", "compare/after")),
    ),
    "compare/after" to
        ExamplePage("보정 후", "Wipe", "가장자리를 천천히 밀면 경계선이 손가락을 따라와 전후를 견줄 수 있습니다.", {
            KTransitions.Wipe
        }, listOf(Color(0xFF43E97B), Color(0xFF38F9D7))),
    "media" to ExamplePage(
        "앨범",
        "Push",
        "앨범을 고르고 재생하면 화면이 흐려졌다가 재생 화면으로 바뀝니다.",
        push,
        listOf(Color(0xFF654EA3), Color(0xFFEAAFC8)),
        listOf(PageAction("재생", "media/player")),
    ),
    "media/player" to
        ExamplePage("지금 재생 중", "BlurDissolve", "밤의 산책, 2:14 / 3:48", {
            KTransitions.BlurDissolve
        }, listOf(Color(0xFF0F2027), Color(0xFF2C5364))),
    THEME_SCREEN to ExamplePage("테마 전환", "Push", "", push),
    "checkout" to ExamplePage(
        "결제",
        "Push",
        "에어 러너 129,000원. 결제하기를 누르면 누른 버튼에서 별 모양이 퍼지며 완료 화면이 나옵니다.",
        push,
        actions = listOf(PageAction("결제하기", "checkout/done")),
    ),
    "checkout/done" to ExamplePage(
        "결제 완료",
        "Star, 누른 버튼에서",
        "내일 도착 예정입니다. 축하할 일에는 별 모양 전환이 어울립니다.",
        { KTransitions.reveal(KRevealShape.Star, it) },
        listOf(Color(0xFFFAD961), Color(0xFFF76B1C)),
    ),
)

internal const val THEME_SCREEN = "theme"

/** 이 동작은 다음 화면 대신 지금 화면을 닫는다. */
private const val BACK = "back"

@Composable
internal fun ExampleScreen(screen: String, nav: NavDemoState) {
    val page = EXAMPLE_PAGES[screen] ?: return
    when {
        screen == THEME_SCREEN -> ThemeScreen(nav)
        page.art != null -> ArtScreen(page, page.art, nav)
        else -> PlainScreen(page, nav)
    }
}

/** 그라디언트를 화면 전체에 깐 예시 화면. 뒤로 버튼은 위에, 제목과 버튼은 엄지가 닿는 아래쪽에 둔다. */
@Composable
private fun ArtScreen(page: ExamplePage, art: List<Color>, nav: NavDemoState) {
    CompositionLocalProvider(LocalKineticaContentColor provides Color.White) {
        BoxWithConstraints(Modifier.fillMaxSize().paintGradient(art)) {
            val height = maxHeight
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = height)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                RoundBack(nav)
                Column(
                    Modifier.align(
                        Alignment.CenterHorizontally,
                    ).widthIn(max = READING_WIDTH).fillMaxWidth().padding(top = 40.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Label(page.transitionName, 14, FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f))
                    Label(page.title, 34, FontWeight.Bold)
                    Label(page.body, 16, color = Color.White.copy(alpha = 0.88f))
                    ActionButtons(page.actions, nav, Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun RoundBack(nav: NavDemoState) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f))
            .kClickable(CircleShape) { nav.pop() }
            .semantics { contentDescription = "뒤로" },
        contentAlignment = Alignment.Center,
    ) { ShowcaseIcon(KIcons.ChevronBack, 22.dp, Color.White) }
}

/** 채운 캡슐 버튼들. 누른 버튼의 중심을 퍼지는 전환의 시작점으로 넘긴다. */
@Composable
private fun ActionButtons(actions: List<PageAction>, nav: NavDemoState, modifier: Modifier = Modifier) {
    val centers = remember { HashMap<String, Offset>() }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.forEach { action ->
            Pill(action.label, Modifier.kOrigin { centers[action.target] = it }, filled = true) { go(action, nav, centers[action.target]) }
        }
    }
}

private fun go(action: PageAction, nav: NavDemoState, origin: Offset?) {
    if (action.target == BACK) nav.pop() else nav.push(action.target, origin)
}

/** 쇼케이스 표면 위에 그리는 예시 화면. 그림 칸이 있는 동작은 두 열 그림으로, 나머지는 목록 행으로 둔다. */
@Composable
private fun PlainScreen(page: ExamplePage, nav: NavDemoState) {
    KineticaScaffold(
        Modifier.fillMaxSize(),
        surface = demoSurface(NAVIGATION_LOOK),
        background = { DemoBackdrop() },
        topBar = { DemoTopBar(page.title, "${page.transitionName} 전환으로 열렸습니다", nav::pop) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = readingPadding(),
                end = readingPadding(),
                top = padding.calculateTopPadding() + 8.dp,
                bottom = navigationBarBottom() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                KSurface(Modifier.widthIn(max = READING_WIDTH).fillMaxWidth(), shape = CARD_SHAPE) {
                    Label(page.body, 15, modifier = Modifier.padding(CARD_PADDING))
                }
            }
            val tiles = page.actions.filter { it.colors != null }
            val rows = page.actions.filter { it.colors == null }
            if (tiles.isNotEmpty()) item { ArtTiles(tiles, nav) }
            if (rows.isNotEmpty()) item { ActionRows(rows, nav) }
        }
    }
}

@Composable
private fun ArtTiles(tiles: List<PageAction>, nav: NavDemoState) {
    Row(Modifier.widthIn(max = READING_WIDTH).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.forEach { tile ->
            KSurface({ go(tile, nav, null) }, Modifier.weight(1f), shape = CARD_SHAPE, onClickLabel = "${tile.label} 보기") {
                Column(Modifier.padding(8.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.9f).clip(SHOP_IMAGE_SHAPE).paintGradient(tile.colors.orEmpty()))
                    Label(tile.label, 15, FontWeight.SemiBold, Modifier.padding(start = 8.dp, top = 10.dp, bottom = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun ActionRows(rows: List<PageAction>, nav: NavDemoState) {
    val centers = remember { HashMap<String, Offset>() }
    KSurface(Modifier.widthIn(max = READING_WIDTH).fillMaxWidth(), shape = CARD_SHAPE) {
        Column(Modifier.padding(6.dp)) {
            rows.forEach { action ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .kOrigin { centers[action.target] = it }
                        .clip(ROW_SHAPE)
                        .kClickable(ROW_SHAPE) { go(action, nav, centers[action.target]) }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label(action.label, 16, modifier = Modifier.weight(1f))
                    ShowcaseIcon(KIcons.ChevronForward, 18.dp, LocalKineticaContentColor.current.copy(alpha = 0.5f))
                }
            }
        }
    }
}

/** 첫 화면 목록에서 들어온 화면. 단계마다 색이 다른 띠를 둬 어느 화면이 들어오고 나가는지 잘 보이게 한다. */
@Composable
internal fun MenuScreen(screen: String, nav: NavDemoState) {
    val depth = screen.removePrefix(MENU_PREFIX).count { it == '/' } + 1
    KineticaScaffold(
        Modifier.fillMaxSize(),
        surface = demoSurface(NAVIGATION_LOOK),
        background = { DemoBackdrop() },
        topBar = { DemoTopBar(screen.substringAfterLast('/'), "${depth}단계, ${NAV_TRANSITIONS[nav.chip].name}", nav::pop) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = readingPadding(),
                end = readingPadding(),
                top = padding.calculateTopPadding() + 8.dp,
                bottom = navigationBarBottom() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Box(
                    Modifier.widthIn(max = READING_WIDTH).fillMaxWidth().aspectRatio(2.2f).clip(CARD_SHAPE)
                        .paintGradient(DEPTH_COLORS[(depth - 1) % DEPTH_COLORS.size]),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Label("${depth}단계", 28, FontWeight.Bold, Modifier.padding(CARD_PADDING), color = Color.White)
                }
            }
            if (depth < MAX_DEPTH) {
                item {
                    KSurface(Modifier.widthIn(max = READING_WIDTH).fillMaxWidth(), shape = CARD_SHAPE) {
                        MenuRows(SUB_MENU.map { "$screen/$it" }, nav, Modifier.padding(6.dp))
                    }
                }
            } else {
                item { Label("가장 깊은 화면입니다. 가장자리를 밀어 한 단계씩 돌아가 보세요.", 15, modifier = Modifier.widthIn(max = READING_WIDTH)) }
            }
        }
    }
}

/**
 * 테마 전환. 버튼을 누르면 그 버튼에서 원이 퍼지며 새 테마의 화면이 드러난다. 화면 이동이 아니라 같은 화면의 내용이 바뀌는 것이라
 * [KTransitionContent]로 그리고, 늘 앞으로 가는 방향으로 둬 어느 쪽으로 바꾸든 원이 퍼진다.
 */
@Composable
private fun ThemeScreen(nav: NavDemoState) {
    val origin = remember { arrayOfNulls<Offset>(1) }
    val reveal = remember { KTransitions.reveal(KRevealShape.Circle) { origin[0] } }
    KTransitionContent(nav.themeDark, Modifier.fillMaxSize(), transition = reveal) { dark ->
        val ink = if (dark) Color(0xFFF2F4F8) else Color(0xFF15161A)
        CompositionLocalProvider(LocalKineticaContentColor provides ink) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(if (dark) Color(0xFF111318) else Color(0xFFF5F6FA))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ThemeBack(ink, nav)
                Column(
                    Modifier.align(Alignment.CenterHorizontally).widthIn(max = READING_WIDTH).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Label("테마 전환", 34, FontWeight.Bold)
                    Label("아래 버튼을 누르면 그 자리에서 원이 퍼지며 새 테마가 드러납니다.", 16, color = ink.copy(alpha = 0.7f))
                    ThemeRows(dark)
                    Pill(if (dark) "밝게 보기" else "어둡게 보기", Modifier.kOrigin { origin[0] = it }, filled = true) { nav.themeDark = !dark }
                }
            }
        }
    }
}

@Composable
private fun ThemeBack(ink: Color, nav: NavDemoState) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.08f))
            .kClickable(CircleShape) { nav.pop() }
            .semantics { contentDescription = "뒤로" },
        contentAlignment = Alignment.Center,
    ) { ShowcaseIcon(KIcons.ChevronBack, 22.dp, ink) }
}

@Composable
private fun ThemeRows(dark: Boolean) {
    val card = if (dark) Color(0xFF1C1F26) else Color.White
    Column(Modifier.fillMaxWidth().clip(CARD_SHAPE).background(card).padding(vertical = 6.dp)) {
        listOf("Wi-Fi" to "집", "블루투스" to "켜짐", "화면 모드" to if (dark) "어둡게" else "밝게").forEach { (name, value) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                Label(name, 16, modifier = Modifier.weight(1f))
                Label(value, 16, color = LocalKineticaContentColor.current.copy(alpha = 0.55f))
            }
        }
    }
}

private const val MAX_DEPTH = 4
private val SUB_MENU = listOf("세부 설정", "고급", "도움말")
private val DEPTH_COLORS = listOf(
    listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
    listOf(Color(0xFFFF7E5F), Color(0xFFFEB47B)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFFFC466B), Color(0xFF3F5EFB)),
)
private val ROW_SHAPE = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)

// 카드 32dp 모서리 안에 8dp 여백이라 24dp로 같은 중심을 맞춘다.
private val SHOP_IMAGE_SHAPE = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
