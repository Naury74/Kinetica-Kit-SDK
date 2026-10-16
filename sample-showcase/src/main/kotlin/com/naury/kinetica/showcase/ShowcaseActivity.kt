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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
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

/** 쇼케이스 데모 목록. 선언한 순서대로 첫 화면에 나온다. */
internal enum class Demo(val title: String, val summary: String, val icon: ImageVector, val accent: Color) {
    UiKit(
        "UI 키트",
        "같은 다이얼로그, 시트, 토스트, 탭 바를 글래스, 단색, 톤 표면으로 바꿔 봅니다. 프리셋도 화면 안에서 고를 수 있습니다.",
        ShowcaseIcons.Sparkle,
        Color(0xFF7AA2FF),
    ),
    Motion("모션 효과", "글로우 테두리, 젤리 버튼, 기울이면 움직이는 3D 카드, 숫자 롤링, 컨페티와 프리셋 애니메이션을 직접 눌러 보세요.", ShowcaseIcons.Wave, Color(0xFFFF7EB3)),
    Board("대시보드 보드", "사용자가 위젯을 직접 배치하는 홈 화면입니다. 위로 끌면 스냅 레이아웃을 고르고, 가장자리로 끌면 절반이나 4분의 1에 붙습니다.", ShowcaseIcons.Grid, Color(0xFF5EF0C8)),
    Apps(
        "앱 화면 예",
        "컴포넌트를 조합한 음악, 지도, 메일, 건강 앱 화면입니다. 스크롤하면 줄어드는 탭 바, 여러 높이 시트, 폴더블에서 두 칸이 되는 메일, 차트를 실제처럼 써 보세요.",
        ShowcaseIcons.Phone,
        Color(0xFFFFD36E),
    ),
    Components(
        "컴포넌트",
        "버튼, 입력, 오버레이, 피드백, 차트, 채팅까지 아홉 개 탭에 나눠 담았습니다. 드롭다운, 다이얼로그, 토스트, 스토리, 키패드를 직접 눌러 보세요.",
        ShowcaseIcons.Layers,
        Color(0xFFFFA8E0),
    ),
    Navigation(
        "화면 이동",
        "App Store처럼 카드가 제자리에서 커지며 열리고, 목록은 iOS처럼 옆에서 밀려 들어옵니다. 가장자리를 밀면 뒤로 갑니다.",
        ShowcaseIcons.Forward,
        Color(0xFFA0F08C),
    ),
    Transition("장면 전환", "카드가 자연스럽게 상세 화면으로 이어지고, 아래로 끌면 닫힙니다. 재생기 모듈만 씁니다.", ShowcaseIcons.Expand, Color(0xFF7AE0FF)),
    Server("서버 화면", "앱을 업데이트하지 않고 서버 JSON으로 화면을 바꿉니다. 실시간 숫자와 버튼 동작도 앱과 연결됩니다.", ShowcaseIcons.Cloud, Color(0xFFFFC56E)),
    Studio("화면 만들기", "운영자가 피그마처럼 앱 안에서 바로 화면을 디자인하는 편집기입니다.", ShowcaseIcons.Pen, Color(0xFFB98CFF)),
}

@Composable
internal fun Showcase(look: ShowcaseLook = rememberShowcaseLook()) {
    var current by rememberSaveable { mutableStateOf<Demo?>(null) }
    // 데모에서 돌아왔을 때 첫 화면 스크롤 위치가 남도록 목록 상태를 화면 전환 바깥에 둔다.
    val homeList = rememberLazyListState()
    BackHandler(enabled = current != null) { current = null }
    // 상태 표시줄 아이콘은 고른 밝기를 따른다. '화면 기본'이면 첫 화면, 장면 전환, 앱 화면 예는 어두운 바탕이라 밝게 둔다.
    // 편집기는 시스템 테마를 따른다.
    val view = LocalView.current
    val darkScreen = isDarkScreen(current, isSystemInDarkTheme(), look)
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkScreen
    }
    CompositionLocalProvider(LocalShowcaseLook provides look) {
        AnimatedContent(
            current,
            transitionSpec = { ContentTransitions.sharedAxisZ(forward = targetState != null) },
            label = "showcase",
        ) { demo ->
            if (demo == null) Home(homeList) { current = it } else DemoScreen(demo) { current = null }
        }
    }
}

@Composable
private fun DemoScreen(demo: Demo, back: () -> Unit) {
    when (demo) {
        Demo.UiKit -> UiKitDemo(back)
        Demo.Motion -> MotionDemo(back)
        Demo.Board -> BoardDemo(back)
        Demo.Apps -> AppsDemo(back)
        Demo.Components -> ComponentsDemo(back)
        Demo.Navigation -> NavigationDemo(back)
        Demo.Transition -> TransitionDemo(back)
        Demo.Server -> ServerDemo(back)
        Demo.Studio -> StudioDemo(back)
    }
}

/** 첫 화면, 장면 전환, 앱 화면 예는 처음 디자인이 어두운 바탕이다. 편집기는 시스템 테마를 따른다. */
private fun isDarkScreen(demo: Demo?, systemDark: Boolean, look: ShowcaseLook): Boolean = when (demo) {
    Demo.Studio -> systemDark
    null, Demo.Transition, Demo.Apps -> look.isDark(defaultDark = true)
    else -> look.isDark(defaultDark = false)
}

private val HOME_LOOK = DemoLook(GlassStyles.Liquid, GlassStyles.LiquidDark, defaultDark = true)

@Composable
private fun Home(list: LazyListState, open: (Demo) -> Unit) {
    KineticaScaffold(Modifier.fillMaxSize(), surface = demoSurface(HOME_LOOK), background = { DemoBackdrop() }) { padding ->
        // 폰에서는 한 줄, 가로 화면이나 펼친 폴더블, 태블릿에서는 카드 폭에 맞춰 두세 줄로 놓는다.
        // 같은 줄 카드는 설명 길이가 달라도 높이를 맞춘다.
        val side = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
        val layout = LocalLayoutDirection.current
        val start = 20.dp + side.calculateStartPadding(layout)
        val end = 20.dp + side.calculateEndPadding(layout)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = ((maxWidth - start - end + HOME_GAP) / (HOME_CELL + HOME_GAP)).toInt().coerceIn(1, Demo.entries.size)
            LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
                contentPadding = PaddingValues(
                    start = start,
                    end = end,
                    top = padding.calculateTopPadding(),
                    bottom = navigationBarBottom() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(HOME_GAP),
            ) {
                item { Hero() }
                item { SurfaceCard() }
                Demo.entries.chunked(columns).forEach { row ->
                    item(row.first().name) {
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(HOME_GAP)) {
                            row.forEach { demo -> DemoEntry(demo, Modifier.weight(1f).fillMaxHeight()) { open(demo) } }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                homeFooter()
            }
            // 카드가 상태 표시줄 아래로 지나갈 때만 위쪽을 어둡게 해 시계와 아이콘이 카드 글자와 겹쳐 보이지 않게 한다.
            StatusBarScrim(scrolled = { list.canScrollBackward })
        }
    }
}

private val HOME_CELL = 340.dp
private val HOME_GAP = 14.dp

private fun LazyListScope.homeFooter() {
    item {
        val context = LocalContext.current
        Row(
            Modifier
                .clip(CircleShape)
                .kClickable(CircleShape) { context.startActivity(Intent(context, XmlActivity::class.java)) }
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("XML 레이아웃으로 만든 화면 보기", 15, FontWeight.SemiBold)
            ShowcaseIcon(ShowcaseIcons.Chevron, 18.dp, modifier = Modifier.padding(start = 4.dp))
        }
    }
    item {
        Label(
            "모든 화면은 SDK 모듈을 그대로 사용합니다. 코드는 sample-showcase 모듈에서 볼 수 있습니다.",
            12,
            color = LocalKineticaContentColor.current.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun Hero() {
    Column(Modifier.statusBarsPadding().padding(top = 40.dp, bottom = 16.dp)) {
        val ink = LocalKineticaContentColor.current
        BasicText("Kinetica", style = TextStyle(color = ink, fontSize = 44.sp, fontWeight = FontWeight.Black))
        Spacer(Modifier.height(8.dp))
        TypewriterText(
            "앱에 바로 붙여 쓰는 모션 UI 키트",
            style = TextStyle(color = ink.copy(alpha = 0.85f), fontSize = 18.sp, fontWeight = FontWeight.Medium),
        )
    }
}

/** 첫 화면의 표면 스타일 고르기. 고른 값은 모든 데모에 그대로 이어진다. */
@Composable
private fun SurfaceCard() {
    KSurface(Modifier.fillMaxWidth().widthIn(max = READING_WIDTH), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("표면 스타일", 17, FontWeight.Bold)
                Label(
                    "모든 데모가 여기서 고른 표면을 따릅니다. 데모 안에서도 상단 바의 버튼으로 바꿀 수 있습니다.",
                    13,
                    color = LocalKineticaContentColor.current.copy(alpha = 0.7f),
                )
            }
            SurfacePicker()
        }
    }
}

@Composable
private fun DemoEntry(demo: Demo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    KSurface(onClick, modifier.jellyPress(0.04f)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).background(demo.accent.copy(alpha = 0.9f), CircleShape), contentAlignment = Alignment.Center) {
                ShowcaseIcon(demo.icon, 24.dp, Color(0xFF15161A))
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(demo.title, 19, FontWeight.Bold)
                Label(demo.summary, 13, color = LocalKineticaContentColor.current.copy(alpha = 0.75f))
            }
            ShowcaseIcon(ShowcaseIcons.Chevron, 20.dp, LocalKineticaContentColor.current.copy(alpha = 0.6f))
        }
    }
}
