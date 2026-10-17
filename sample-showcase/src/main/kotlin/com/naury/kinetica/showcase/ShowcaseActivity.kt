// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.naury.kinetica.render.ContentTransitions
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.TypewriterText
import com.naury.kinetica.ui.jellyPress
import com.naury.kinetica.ui.kClickable

/** Kinetica 쇼케이스: SDK로 무엇을 만들 수 있는지 한 앱에서 보여 준다. 첫 화면에서 데모를 고른다. */
class ShowcaseActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(showcaseContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showcaseEdgeToEdge()
        setContent { Showcase() }
    }
}

/**
 * 시스템 바를 투명하게 두고 내용을 화면 끝까지 그린다. 기본 [enableEdgeToEdge]는 제스처 내비게이션에서도
 * 내비게이션 바 뒤에 반투명 흰 막을 깔아(삼성 기기 등) 글래스 배경 아래쪽에 띠가 생긴다. 아이콘 색은 화면마다 정한다.
 */
internal fun ComponentActivity.showcaseEdgeToEdge() {
    val transparent = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
    enableEdgeToEdge(statusBarStyle = transparent, navigationBarStyle = transparent)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
}

/** 첫 화면에서 데모가 놓이는 묶음. */
internal enum class DemoGroup {
    /** UI 키트 허브 안에 분류별로 놓인다. */
    Kit,

    /** 첫 화면 아래 고급 기능 목록에 놓인다. */
    Advanced,
}

/** 쇼케이스 데모. [group]이 놓일 곳을 정하고, 같은 묶음 안에서는 선언한 순서대로 나온다. */
internal enum class Demo(val title: String, val summary: String, val icon: ImageVector, val accent: Color, val group: DemoGroup) {
    Surfaces(
        "표면과 디자인",
        "같은 컴포넌트를 글래스, 단색, 톤 프리셋으로 나란히 비교하고, 누른 프리셋을 앱 전체에 바로 적용합니다.",
        ShowcaseIcons.Contrast,
        Color(0xFF7AA2FF),
        DemoGroup.Kit,
    ),
    Apps(
        "앱 화면 예",
        "컴포넌트를 조합해 만든 실제 앱 같은 화면입니다. 스크롤하면 줄어드는 탭 바, 여러 높이에 멈추는 시트, 폴더블에서 두 칸이 되는 화면을 써 보세요.",
        ShowcaseIcons.Phone,
        Color(0xFFFFD36E),
        DemoGroup.Kit,
    ),
    Components(
        "컴포넌트",
        "버튼, 입력, 오버레이, 피드백, 차트, 채팅까지 종류별 탭에 나눠 담았습니다.",
        ShowcaseIcons.Layers,
        Color(0xFFFFA8E0),
        DemoGroup.Kit,
    ),
    Motion(
        "모션 효과",
        "글로우 테두리, 젤리 버튼, 기울이면 움직이는 3D 카드, 숫자 롤링, 컨페티와 프리셋 애니메이션입니다.",
        ShowcaseIcons.Wave,
        Color(0xFFFF7EB3),
        DemoGroup.Kit,
    ),
    Navigation(
        "화면 이동",
        "카드가 제자리에서 커지며 열리고, 목록은 옆에서 밀려 들어옵니다. 가장자리를 밀면 뒤로 갑니다.",
        ShowcaseIcons.Forward,
        Color(0xFFA0F08C),
        DemoGroup.Kit,
    ),
    Transition(
        "장면 전환",
        "카드가 자연스럽게 상세 화면으로 이어지고, 아래로 끌면 닫힙니다.",
        ShowcaseIcons.Expand,
        Color(0xFF7AE0FF),
        DemoGroup.Kit,
    ),
    Board(
        "대시보드 보드",
        "사용자가 위젯을 직접 옮기고 크기를 바꾸는 홈 화면입니다.",
        ShowcaseIcons.Grid,
        Color(0xFF5EF0C8),
        DemoGroup.Advanced,
    ),
    Studio("화면 만들기", "운영자가 앱 안에서 화면을 디자인하는 편집기입니다.", ShowcaseIcons.Pen, Color(0xFFB98CFF), DemoGroup.Advanced),
    Server("서버 화면", "앱을 업데이트하지 않고 서버 JSON으로 화면을 바꿉니다.", ShowcaseIcons.Cloud, Color(0xFFFFC56E), DemoGroup.Advanced),
}

/**
 * 쇼케이스 화면 경로. 첫 화면([HOME]), UI 키트 허브([KIT]), 데모([Demo] 이름), 컴포넌트의 특정 탭("Components:3")을 문자열로 적어
 * 뒤로 가기 스택을 그대로 저장한다. 회전이나 폴드 전환으로 액티비티가 다시 만들어져도 스택과 각 화면의 스크롤 위치가 남는다.
 */
internal object ShowcaseRoutes {
    const val HOME = "home"
    const val KIT = "kit"

    fun demo(demo: Demo): String = demo.name

    fun componentsTab(tab: Int): String = "${Demo.Components.name}:$tab"

    /** 경로가 가리키는 데모. 첫 화면과 허브면 null이다. */
    fun demoOf(route: String): Demo? = Demo.entries.firstOrNull { it.name == route.substringBefore(':') }

    /** 컴포넌트 탭 경로면 그 탭 순서, 아니면 0이다. */
    fun tabOf(route: String): Int = route.substringAfter(':', "0").toIntOrNull() ?: 0

    /** 화면 깊이. 전환 방향(들어가기, 나오기)을 정할 때 쓴다. */
    fun depth(route: String): Int = when {
        route == HOME -> 0
        route == KIT -> 1
        demoOf(route)?.group == DemoGroup.Kit -> 2
        else -> 1
    }
}

@Composable
internal fun Showcase(look: ShowcaseLook = rememberShowcaseLook()) {
    var stack by rememberSaveable { mutableStateOf(listOf(ShowcaseRoutes.HOME)) }
    // 첫 화면과 허브의 스크롤 위치처럼 화면 안에서 rememberSaveable로 둔 상태를 경로별로 보관한다.
    // 데모는 나올 때 지워서 다시 들어가면 처음부터 시작한다.
    val states = rememberSaveableStateHolder()
    val top = stack.last()
    val open: (String) -> Unit = { stack = stack + it }
    val back: () -> Unit = {
        val leaving = stack.last()
        stack = stack.dropLast(1)
        if (leaving != ShowcaseRoutes.HOME && leaving != ShowcaseRoutes.KIT) states.removeState(leaving)
    }
    BackHandler(enabled = stack.size > 1, onBack = back)
    // 상태 표시줄 아이콘은 고른 밝기를 따른다. '화면 기본'이면 첫 화면, 허브, 장면 전환, 앱 화면 예는 어두운 바탕이라 밝게 둔다.
    // 편집기는 시스템 테마를 따른다.
    val view = LocalView.current
    val darkScreen = isDarkScreen(ShowcaseRoutes.demoOf(top), isSystemInDarkTheme(), look)
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkScreen
    }
    CompositionLocalProvider(LocalShowcaseLook provides look) {
        AnimatedContent(
            top,
            transitionSpec = {
                ContentTransitions.sharedAxisZ(forward = ShowcaseRoutes.depth(targetState) > ShowcaseRoutes.depth(initialState))
            },
            label = "showcase",
        ) { route ->
            states.SaveableStateProvider(route) {
                when (route) {
                    ShowcaseRoutes.HOME -> Home(open)
                    ShowcaseRoutes.KIT -> KitHub(back, open)
                    else -> DemoScreen(route, back)
                }
            }
        }
    }
}

@Composable
private fun DemoScreen(route: String, back: () -> Unit) {
    when (ShowcaseRoutes.demoOf(route)) {
        Demo.Surfaces -> UiKitDemo(back)
        Demo.Motion -> MotionDemo(back)
        Demo.Board -> BoardDemo(back)
        Demo.Apps -> AppsDemo(back)
        Demo.Components -> ComponentsDemo(back, ShowcaseRoutes.tabOf(route))
        Demo.Navigation -> NavigationDemo(back)
        Demo.Transition -> TransitionDemo(back)
        Demo.Server -> ServerDemo(back)
        Demo.Studio -> StudioDemo(back)
        null -> Unit
    }
}

/** 첫 화면, 허브, 장면 전환, 앱 화면 예는 처음 디자인이 어두운 바탕이다. 편집기는 시스템 테마를 따른다. */
private fun isDarkScreen(demo: Demo?, systemDark: Boolean, look: ShowcaseLook): Boolean = when {
    demo == Demo.Studio -> systemDark
    demo == null || demo == Demo.Transition || demo == Demo.Apps -> look.resolvedDark(defaultDark = true)
    else -> look.resolvedDark(defaultDark = false)
}

private val HOME_LOOK = DemoLook(GlassStyles.Liquid, GlassStyles.LiquidDark, defaultDark = true)

@Composable
private fun Home(open: (String) -> Unit) {
    val list = rememberLazyListState()
    var lookSheet by rememberSaveable { mutableStateOf(false) }
    KineticaScaffold(Modifier.fillMaxSize(), surface = demoSurface(HOME_LOOK), background = { DemoBackdrop() }) { padding ->
        val side = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
        val layout = LocalLayoutDirection.current
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 넓은 화면에서는 본문을 가운데 [HOME_MAX_WIDTH] 안에 둔다. 고급 기능은 칸 폭에 맞춰 두세 줄로 놓는다.
            val inner = maxWidth - side.calculateStartPadding(layout) - side.calculateEndPadding(layout)
            val gutter = maxOf(20.dp, (inner - HOME_MAX_WIDTH) / 2)
            val content = inner - gutter * 2
            val cell = HOME_ADVANCED_CELL * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
            val columns = ((content + HOME_GAP) / (cell + HOME_GAP)).toInt().coerceIn(1, ADVANCED_ENTRIES)
            LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
                contentPadding = PaddingValues(
                    start = gutter + side.calculateStartPadding(layout),
                    end = gutter + side.calculateEndPadding(layout),
                    top = padding.calculateTopPadding(),
                    bottom = navigationBarBottom() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(HOME_GAP),
            ) {
                item("hero") { Hero() }
                item("look") { LookRow { lookSheet = true } }
                item("kit") { KitCard { open(ShowcaseRoutes.KIT) } }
                item("advanced-title") { AdvancedTitle() }
                advancedEntries(columns, open)
                homeFooter()
            }
            // 카드가 상태 표시줄 아래로 지나갈 때만 위쪽을 어둡게 해 시계와 아이콘이 카드 글자와 겹쳐 보이지 않게 한다.
            StatusBarScrim(scrolled = { list.canScrollBackward })
        }
        // 스캐폴드 안에 두어야 시트가 별도 창이 아니라 글래스 오버레이로 뜨고 뒤쪽 화면이 블러 처리된다.
        LookSheet(lookSheet) { lookSheet = false }
    }
}

private val HOME_MAX_WIDTH = 1120.dp
private val HOME_ADVANCED_CELL = 260.dp
private val HOME_GAP = 14.dp

/** 고급 기능 칸 수: 데모 셋과 XML 화면 */
private val ADVANCED_ENTRIES = Demo.entries.count { it.group == DemoGroup.Advanced } + 1

@Composable
private fun Hero() {
    Column(Modifier.statusBarsPadding().padding(top = 40.dp, bottom = 8.dp)) {
        val ink = LocalKineticaContentColor.current
        BasicText("Kinetica", style = TextStyle(color = ink, fontSize = 44.sp, fontWeight = FontWeight.Black))
        Spacer(Modifier.height(8.dp))
        TypewriterText(
            "앱에 바로 붙여 쓰는 모션 UI 키트",
            style = TextStyle(color = ink.copy(alpha = 0.85f), fontSize = 18.sp, fontWeight = FontWeight.Medium),
        )
    }
}

/** 지금 고른 모양을 한 줄로 보여 주고, 누르면 모든 화면 상단 바의 모양 버튼과 같은 시트를 연다. */
@Composable
private fun LookRow(onClick: () -> Unit) {
    val look = LocalShowcaseLook.current
    val ink = LocalKineticaContentColor.current
    KSurface(onClick, Modifier.fillMaxWidth(), shape = CircleShape, onClickLabel = "모양 바꾸기") {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ShowcaseIcon(ShowcaseIcons.Contrast, 20.dp)
            Label("모양", 15, FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
            Label(
                look.summary(),
                15,
                color = ink.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            ShowcaseIcon(ShowcaseIcons.Chevron, 18.dp, ink.copy(alpha = 0.6f))
        }
    }
}

/** 첫 화면에서 가장 크게 보이는 UI 키트 카드. 누르면 분류별 허브로 들어간다. */
@Composable
private fun KitCard(onClick: () -> Unit) {
    val ink = LocalKineticaContentColor.current
    KSurface(onClick, Modifier.fillMaxWidth().jellyPress(0.03f), shape = RoundedCornerShape(32.dp)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                KIT_PREVIEW.forEach { demo ->
                    Box(
                        Modifier.size(48.dp).background(demo.accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { ShowcaseIcon(demo.icon, 22.dp, Color(0xFF15161A)) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label("UI 키트", 30, FontWeight.Black, modifier = Modifier.semantics { heading() })
                Label(
                    "표면을 골라 쓰는 컴포넌트와 모션 효과입니다. 글래스, 단색, 톤 중 하나를 고르면 다이얼로그부터 탭 바까지 같은 모양을 따릅니다.",
                    15,
                    color = ink.copy(alpha = 0.8f),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KIT_TOPICS.forEach { topic ->
                    Box(Modifier.background(ink.copy(alpha = 0.1f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Label(topic, 13, FontWeight.Medium)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("둘러보기", 16, FontWeight.Bold)
                ShowcaseIcon(ShowcaseIcons.Chevron, 18.dp, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

private val KIT_PREVIEW = Demo.entries.filter { it.group == DemoGroup.Kit }
private val KIT_TOPICS = listOf("표면과 디자인", "앱 화면 예", "컴포넌트", "모션 효과", "화면 이동", "장면 전환")

@Composable
private fun AdvancedTitle() {
    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Label("고급 기능", 17, FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Label(
            "UI 키트와 함께 제공하는 보드, 편집기, 재생기 모듈입니다.",
            13,
            color = LocalKineticaContentColor.current.copy(alpha = 0.65f),
        )
    }
}

/** 고급 기능 데모와 XML 화면을 [columns]열로 놓는다. 같은 줄 칸은 설명 길이가 달라도 높이를 맞춘다. */
private fun LazyListScope.advancedEntries(columns: Int, open: (String) -> Unit) {
    val entries: List<AdvancedEntry> = Demo.entries.filter { it.group == DemoGroup.Advanced }.map { AdvancedEntry.Screen(it) } +
        AdvancedEntry.Xml
    entries.chunked(columns).forEach { row ->
        item(row.first().key) {
            val context = LocalContext.current
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(HOME_GAP)) {
                row.forEach { entry ->
                    val modifier = Modifier.weight(1f).fillMaxHeight()
                    when (entry) {
                        is AdvancedEntry.Screen ->
                            CompactEntry(entry.demo.title, entry.demo.summary, entry.demo.icon, entry.demo.accent, modifier) {
                                open(ShowcaseRoutes.demo(entry.demo))
                            }

                        AdvancedEntry.Xml -> CompactEntry(
                            "XML 화면",
                            "Compose 없이 XML 레이아웃과 View로 만든 화면입니다.",
                            ShowcaseIcons.Inbox,
                            Color(0xFFB0C4DE),
                            modifier,
                        ) { context.startActivity(Intent(context, XmlActivity::class.java)) }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private sealed interface AdvancedEntry {
    val key: String

    class Screen(val demo: Demo) : AdvancedEntry {
        override val key: String get() = demo.name
    }

    data object Xml : AdvancedEntry {
        override val key: String = "xml"
    }
}

private fun LazyListScope.homeFooter() {
    item("footer") {
        Label(
            "모든 화면은 SDK 모듈을 그대로 사용합니다. 코드는 sample-showcase 모듈에서 볼 수 있습니다.",
            12,
            color = LocalKineticaContentColor.current.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 작은 데모 항목: 색 원 안 아이콘, 제목, 한 줄 설명. 첫 화면 고급 기능과 허브의 분류 칸에 쓴다. */
@Composable
internal fun CompactEntry(
    title: String,
    summary: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    KSurface(onClick, modifier.jellyPress(0.04f), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(accent.copy(alpha = 0.9f), CircleShape), contentAlignment = Alignment.Center) {
                ShowcaseIcon(icon, 20.dp, Color(0xFF15161A))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(title, 16, FontWeight.Bold)
                Label(summary, 13, color = LocalKineticaContentColor.current.copy(alpha = 0.72f))
            }
        }
    }
}
