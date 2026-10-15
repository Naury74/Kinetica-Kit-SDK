// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
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
import com.naury.kinetica.ui.GlassScaffold
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.GlassSurface
import com.naury.kinetica.ui.TypewriterText
import com.naury.kinetica.ui.glassClickable
import com.naury.kinetica.ui.jellyPress

/** Kinetica 쇼케이스: SDK로 무엇을 만들 수 있는지 한 앱에서 보여 준다. 첫 화면에서 데모를 고른다. */
class ShowcaseActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(showcaseContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Showcase() }
    }
}

/** 쇼케이스 데모 목록. 선언한 순서대로 첫 화면에 나온다. */
internal enum class Demo(val title: String, val summary: String, val icon: ImageVector, val accent: Color) {
    Glass("글래스 UI", "배경이 비치는 글래스 UI입니다. 스타일을 바꿔 가며 다이얼로그, 시트, 토스트, 탭 바를 살펴보세요.", ShowcaseIcons.Sparkle, Color(0xFF7AA2FF)),
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
        "기본, 입력, 미디어, 효과 탭으로 나눴습니다. 인증번호 입력, 색 선택, 서명, 사진 뷰어, 전후 비교, 물결 왜곡, 파티클, 홀로그램 카드까지 눌러 보세요.",
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
private fun Showcase() {
    var current by rememberSaveable { mutableStateOf<Demo?>(null) }
    BackHandler(enabled = current != null) { current = null }
    // 첫 화면은 배경이 어두워 상태 표시줄 아이콘을 밝게 두고, 나머지는 배경이 밝아 어둡게 둔다. 편집기는 시스템 테마를 따른다.
    val view = LocalView.current
    val darkScreen = isDarkScreen(current, isSystemInDarkTheme())
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkScreen
    }
    AnimatedContent(
        current,
        transitionSpec = { ContentTransitions.sharedAxisZ(forward = targetState != null) },
        label = "showcase",
    ) { demo ->
        if (demo == null) Home { current = it } else DemoScreen(demo) { current = null }
    }
}

@Composable
private fun DemoScreen(demo: Demo, back: () -> Unit) {
    when (demo) {
        Demo.Glass -> GlassDemo(back)
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

/** 첫 화면, 장면 전환, 앱 화면 예는 어두운 바탕이고 편집기는 시스템 테마를 따른다. */
private fun isDarkScreen(demo: Demo?, systemDark: Boolean): Boolean =
    demo == null || demo == Demo.Transition || demo == Demo.Apps || (demo == Demo.Studio && systemDark)

@Composable
private fun Home(open: (Demo) -> Unit) {
    GlassScaffold(Modifier.fillMaxSize(), style = GlassStyles.LiquidDark, background = {
        Aurora(Modifier.fillMaxSize(), dark = true)
    }) { padding ->
        // 폰에서는 한 줄, 가로 화면이나 펼친 폴더블, 태블릿에서는 카드 폭에 맞춰 두세 줄로 놓는다.
        val side = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
        val layout = LocalLayoutDirection.current
        LazyVerticalGrid(
            GridCells.Adaptive(340.dp),
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp + side.calculateStartPadding(layout),
                end = 20.dp + side.calculateEndPadding(layout),
                top = padding.calculateTopPadding(),
                bottom = navigationBarBottom() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Hero() }
            Demo.entries.forEach { demo -> item(demo.name) { DemoEntry(demo) { open(demo) } } }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val context = LocalContext.current
                Row(
                    Modifier
                        .clip(CircleShape)
                        .glassClickable(CircleShape) { context.startActivity(Intent(context, XmlActivity::class.java)) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label("XML 레이아웃으로 만든 화면 보기", 15, FontWeight.SemiBold, color = Color.White)
                    ShowcaseIcon(ShowcaseIcons.Chevron, 18.dp, Color.White, Modifier.padding(start = 4.dp))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Label(
                    "모든 화면은 SDK 모듈을 그대로 사용합니다. 코드는 sample-showcase 모듈에서 볼 수 있습니다.",
                    12,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Hero() {
    Column(Modifier.statusBarsPadding().padding(top = 40.dp, bottom = 16.dp)) {
        BasicText("Kinetica", style = TextStyle(color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black))
        Spacer(Modifier.height(8.dp))
        TypewriterText(
            "앱에 바로 붙여 쓰는 모션 UI 키트",
            style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun DemoEntry(demo: Demo, onClick: () -> Unit) {
    GlassSurface(onClick, Modifier.fillMaxWidth().fillMaxHeight().jellyPress(0.04f)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).background(demo.accent.copy(alpha = 0.9f), CircleShape), contentAlignment = Alignment.Center) {
                ShowcaseIcon(demo.icon, 24.dp, Color(0xFF15161A))
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(demo.title, 19, FontWeight.Bold)
                Label(demo.summary, 13, color = Color.White.copy(alpha = 0.75f))
            }
            ShowcaseIcon(ShowcaseIcons.Chevron, 20.dp, Color.White.copy(alpha = 0.6f))
        }
    }
}
