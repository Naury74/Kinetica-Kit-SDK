// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KChipGroup
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KRevealShape
import com.naury.kinetica.ui.KScreenTransition
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KTransitionContent
import com.naury.kinetica.ui.KTransitionEdge
import com.naury.kinetica.ui.KTransitions
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.PushNavigation
import com.naury.kinetica.ui.kClickable
import com.naury.kinetica.ui.kOrigin

/**
 * 화면 이동 데모. 첫 화면에서 전환을 고르고 목록 항목을 누르면 그 전환으로 다음 화면이 들어온다. 예시 칸은 실제 앱에서 어울리는 전환 짝을
 * 보여 주고, 아래쪽에는 탭 내용을 바꾸는 [KTransitionContent]와 작은 요소가 화면으로 커지는 컨테이너 변환이 있다.
 * 어느 화면에서든 시작 쪽 가장자리를 밀거나 예측형 뒤로 가기를 하면 그 화면이 들어온 전환이 손가락을 따라 되감긴다.
 */
@Composable
internal fun NavigationDemo(onBack: () -> Unit) {
    val nav = rememberSaveable(saver = NavDemoState.Saver) { NavDemoState() }
    val transition = remember(nav.active, nav.chip) { nav.transitionFor(nav.active) }
    var overlayDark by remember { mutableStateOf<Boolean?>(null) }
    val surfaceDark = demoSurface(NAVIGATION_LOOK).isDark
    // 사라지는 화면이 늦게 정리되며 값을 덮지 않도록 맨 위 화면 기준으로 여기서 한 번만 정한다.
    val top = nav.stack.last()
    StatusBarIcons(
        darkBackground = when {
            top == ROOT -> overlayDark ?: surfaceDark
            top == THEME_SCREEN -> nav.themeDark
            else -> EXAMPLE_PAGES[top]?.art != null || surfaceDark
        },
    )
    PushNavigation(nav.stack, onBack = nav::pop, transition = transition, paneTitle = { titleOf(it) }) { screen ->
        // 빠진 화면이 전환을 마치고 사라지면 다음 뒤로 가기에 쓸 전환을 지금 맨 위 화면 것으로 돌린다.
        DisposableEffect(screen) { onDispose { nav.onScreenGone(screen) } }
        when {
            screen == ROOT -> NavigationHome(nav, onBack) { overlayDark = it }
            screen.startsWith(MENU_PREFIX) -> MenuScreen(screen, nav)
            else -> ExampleScreen(screen, nav)
        }
    }
}

/**
 * 화면 스택과 지금 쓰는 전환. [PushNavigation]은 넘긴 전환을 바로 쓰므로, 화면이 빠지는 동안에는 그 화면이 들어올 때의 전환을 유지하고
 * 빠진 화면이 사라진 뒤에 맨 위 화면의 전환으로 돌린다.
 */
@Stable
internal class NavDemoState(stack: List<String> = listOf(ROOT), active: String = ROOT, chip: Int = 0, themeDark: Boolean = false) {
    var stack by mutableStateOf(stack)
        private set

    /** 지금 전환을 정하는 화면. 보통 맨 위 화면이고, 화면이 빠지는 동안에는 빠지는 화면이다. */
    var active by mutableStateOf(active)
        private set

    /** 첫 화면에서 고른 전환([NAV_TRANSITIONS]의 순서). 목록 화면들이 이 전환을 쓴다. */
    var chip by mutableIntStateOf(chip)

    /** 테마 전환 예시 화면이 지금 어두운 테마인지 */
    var themeDark by mutableStateOf(themeDark)

    /** 퍼지는 전환이 시작할 점(화면 좌표, px). 그리기 단계에서 읽으므로 상태로 두지 않는다. */
    var origin: Offset? = null

    fun push(screen: String, from: Offset? = null) {
        if (screen in stack) return
        origin = from
        active = screen
        stack = stack + screen
    }

    fun pop() {
        if (stack.size < 2) return
        active = stack.last()
        stack = stack.dropLast(1)
    }

    fun onScreenGone(screen: String) {
        if (screen !in stack) active = stack.last()
    }

    fun transitionFor(screen: String): KScreenTransition = when {
        screen == ROOT -> KTransitions.Push
        screen.startsWith(MENU_PREFIX) -> NAV_TRANSITIONS[chip].make { origin }
        else -> EXAMPLE_PAGES[screen]?.transition?.invoke { origin } ?: KTransitions.Push
    }

    companion object {
        val Saver: Saver<NavDemoState, Any> = Saver(
            save = { arrayListOf(ArrayList(it.stack), it.stack.last(), it.chip, it.themeDark) },
            restore = {
                val list = it as List<*>
                @Suppress("UNCHECKED_CAST")
                NavDemoState(list[0] as List<String>, list[1] as String, list[2] as Int, list[3] as Boolean)
            },
        )
    }
}

/** 칩으로 고르는 전환. 퍼지는 전환은 누른 항목의 중심([make]가 받는 함수)에서 시작한다. */
internal class NamedTransition(val name: String, val make: (origin: () -> Offset?) -> KScreenTransition)

internal val NAV_TRANSITIONS = listOf(
    NamedTransition("Push") { KTransitions.Push },
    NamedTransition("Parallax") { KTransitions.Parallax },
    NamedTransition("Slide") { KTransitions.Slide },
    NamedTransition("Cover") { KTransitions.Cover },
    NamedTransition("Cover ↑") { KTransitions.cover(KTransitionEdge.Bottom) },
    NamedTransition("Uncover") { KTransitions.Uncover },
    NamedTransition("Zoom") { KTransitions.Zoom },
    NamedTransition("Fade") { KTransitions.Fade },
    NamedTransition("FadeThrough") { KTransitions.FadeThrough },
    NamedTransition("SharedAxisX") { KTransitions.SharedAxisX },
    NamedTransition("SharedAxisY") { KTransitions.SharedAxisY },
    NamedTransition("SharedAxisZ") { KTransitions.SharedAxisZ },
    NamedTransition("TabSlide") { KTransitions.TabSlide },
    NamedTransition("SheetStack") { KTransitions.SheetStack },
    NamedTransition("CardStack") { KTransitions.CardStack },
    NamedTransition("Cube") { KTransitions.Cube },
    NamedTransition("Flip") { KTransitions.Flip },
    NamedTransition("BlurDissolve") { KTransitions.BlurDissolve },
    NamedTransition("Wipe") { KTransitions.Wipe },
    NamedTransition("Circle") { KTransitions.reveal(KRevealShape.Circle, it) },
    NamedTransition("Diamond") { KTransitions.reveal(KRevealShape.Diamond, it) },
    NamedTransition("Star") { KTransitions.reveal(KRevealShape.Star, it) },
)

@Composable
private fun NavigationHome(nav: NavDemoState, onBack: () -> Unit, onOverlayDark: (Boolean?) -> Unit) {
    KineticaScaffold(
        Modifier.fillMaxSize(),
        surface = demoSurface(NAVIGATION_LOOK),
        background = { DemoBackdrop() },
        topBar = { DemoTopBar("화면 이동", "전환을 고르고 항목을 눌러 보세요", onBack) },
    ) { padding ->
        val transforms = rememberTransformDemo()
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
            item("picker") { PickerCard(nav) }
            item("back") { BackGestureCard() }
            exampleItems(nav)
            item("tabs") { TabContentCard() }
            transformItems(transforms)
        }
        TransformHosts(transforms, onOverlayDark)
    }
}

@Composable
private fun PickerCard(nav: NavDemoState) {
    DemoCard(
        "전환 고르기",
        "고른 전환으로 아래 목록의 다음 화면이 들어옵니다. 상세 화면에서 더 깊이 들어가도 같은 전환을 쓰고, 뒤로 가면 같은 전환이 되감깁니다.",
        Modifier.widthIn(max = READING_WIDTH),
    ) {
        KChipGroup(NAV_TRANSITIONS.map { it.name }, setOf(nav.chip), { nav.chip = it.first() })
        MenuRows(MENU.map { "$MENU_PREFIX$it" }, nav, Modifier.padding(top = 6.dp))
    }
}

/** 메뉴 행. 누른 행의 중심을 기억해 두었다가 퍼지는 전환의 시작점으로 넘긴다. */
@Composable
internal fun MenuRows(items: List<String>, nav: NavDemoState, modifier: Modifier = Modifier) {
    val centers = remember { HashMap<String, Offset>() }
    Column(modifier.fillMaxWidth()) {
        items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .kOrigin { centers[item] = it }
                    .clip(INNER_SHAPE)
                    .kClickable(INNER_SHAPE) { nav.push(item, centers[item]) }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label(item.substringAfterLast('/'), 16, modifier = Modifier.weight(1f))
                ShowcaseIcon(KIcons.ChevronForward, 18.dp, LocalKineticaContentColor.current.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun BackGestureCard() {
    DemoCard(
        "뒤로 가기는 되감기",
        "시작 쪽 가장자리에서 안쪽으로 끌거나 Android 14 이상에서 예측형 뒤로 가기를 하면, 화면이 들어올 때의 전환이 손가락을 따라 거꾸로 움직입니다. " +
            "충분히 끌고 놓으면 돌아가고, 조금만 끌고 놓으면 제자리로 돌아옵니다.",
        Modifier.widthIn(max = READING_WIDTH),
    ) {}
}

/** 탭처럼 좌우 순서가 있는 내용을 [KTransitionContent]로 바꾼다. 왼쪽 탭으로 가면 반대 방향으로 밀린다. */
@Composable
private fun TabContentCard() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var kind by rememberSaveable { mutableIntStateOf(0) }
    val transition = TAB_TRANSITIONS[kind].second
    DemoCard(
        "탭 내용 바꾸기",
        "KTransitionContent는 값이 바뀌면 고른 전환으로 내용을 바꾸고, 앞 탭으로 돌아가면 같은 전환을 거꾸로 재생합니다.",
        Modifier.widthIn(max = READING_WIDTH),
    ) {
        KSegmentedControl(TAB_TRANSITIONS.map { it.first }, kind, { kind = it }, Modifier.fillMaxWidth())
        KSegmentedControl(TAB_PAGES.map { it.first }, tab, { tab = it }, Modifier.fillMaxWidth())
        KTransitionContent(
            tab,
            Modifier.fillMaxWidth().height(150.dp).clip(INNER_SHAPE),
            transition = transition,
            isForward = { from, to -> to > from },
        ) { page ->
            val (title, colors) = TAB_PAGES[page]
            Box(Modifier.fillMaxSize().clip(INNER_SHAPE).paintGradient(colors), contentAlignment = Alignment.BottomStart) {
                Column(Modifier.padding(16.dp)) {
                    Label("${page + 1} / ${TAB_PAGES.size}", 13, color = Color.White.copy(alpha = 0.8f))
                    Label(title, 24, FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

/** 예시 칸. 넓은 화면에서는 한 줄에 셋, 좁은 화면에서는 둘씩 놓는다. */
private fun LazyListScope.exampleItems(nav: NavDemoState) {
    item("examples-title") {
        Column(Modifier.widthIn(max = READING_WIDTH).fillMaxWidth().padding(top = 8.dp)) {
            Label("예시", 20, FontWeight.Bold)
            Label(
                "앱에서 자주 쓰는 화면 이동과 어울리는 전환입니다. 칸을 누르면 첫 화면이 열리고, 그 안의 버튼이 짝이 되는 전환으로 다음 화면을 엽니다.",
                13,
                color = LocalKineticaContentColor.current.copy(alpha = 0.7f),
            )
        }
    }
    item("examples") { ExampleGrid(nav) }
}

@Composable
private fun ExampleGrid(nav: NavDemoState) {
    BoxWithConstraints(Modifier.widthIn(max = READING_WIDTH).fillMaxWidth()) {
        // 큰 글자에서는 칸 이름이 여러 줄로 꺾이지 않게 글자 배율만큼 좁은 화면으로 보고 열 수를 줄인다.
        val width = maxWidth / LocalDensity.current.fontScale
        val columns = when {
            width >= 520.dp -> 3
            width >= 260.dp -> 2
            else -> 1
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EXAMPLES.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { example -> ExampleTile(example, nav, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ExampleTile(example: NavExample, nav: NavDemoState, modifier: Modifier) {
    var center by remember { mutableStateOf<Offset?>(null) }
    KSurface({
        nav.push(example.entry, center)
    }, modifier.kOrigin { center = it }, shape = TILE_SHAPE, onClickLabel = "${example.title} 열기") {
        Column(Modifier.padding(8.dp)) {
            Box(
                Modifier.fillMaxWidth().height(64.dp).clip(TILE_INNER_SHAPE).paintGradient(example.colors),
                contentAlignment = Alignment.Center,
            ) {
                ShowcaseIcon(example.icon, 24.dp, Color.White)
            }
            Label(example.title, 15, FontWeight.SemiBold, Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp))
            Label(
                example.pair,
                12,
                modifier = Modifier.padding(horizontal = 6.dp).padding(bottom = 4.dp),
                color = LocalKineticaContentColor.current.copy(alpha = 0.6f),
            )
        }
    }
}

/** [colors] 대각선 그라디언트를 깐다. */
internal fun Modifier.paintGradient(colors: List<Color>): Modifier = background(Brush.linearGradient(colors))

/** TalkBack이 화면이 바뀔 때 읽을 이름 */
private fun titleOf(screen: String): String = when {
    screen == ROOT -> "화면 이동"
    screen.startsWith(MENU_PREFIX) -> screen.substringAfterLast('/')
    else -> EXAMPLE_PAGES[screen]?.title ?: screen
}

internal const val ROOT = "home"
internal const val MENU_PREFIX = "menu/"
private val MENU = listOf("알림", "화면 표시", "개인정보", "배터리")
private val TILE_SHAPE = RoundedCornerShape(24.dp)

// 바깥 24dp 모서리 안에 8dp 여백이라 16dp로 같은 중심을 맞춘다.
private val TILE_INNER_SHAPE = RoundedCornerShape(16.dp)
private val TAB_TRANSITIONS = listOf(
    "TabSlide" to KTransitions.TabSlide,
    "SharedAxisX" to KTransitions.SharedAxisX,
    "FadeThrough" to KTransitions.FadeThrough,
)
private val TAB_PAGES = listOf(
    "피드" to listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
    "탐색" to listOf(Color(0xFF43E97B), Color(0xFF38F9D7)),
    "저장함" to listOf(Color(0xFFFA709A), Color(0xFFFEE140)),
)

internal val NAVIGATION_LOOK = DemoLook(GlassStyles.Liquid, GlassStyles.LiquidDark, defaultDark = false)
