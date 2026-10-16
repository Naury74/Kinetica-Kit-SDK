// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.MeshGradientBackground
import com.naury.kinetica.ui.MeshGradients
import com.naury.kinetica.ui.PushNavigation
import com.naury.kinetica.ui.jellyPress

/** 앱 화면 예: kinetica-ui 컴포넌트를 조합해 만든 음악, 지도, 메일, 건강 앱 화면. 고르면 옆에서 밀려 들어온다. */
@Composable
internal fun AppsDemo(onBack: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(listOf(APPS_HOME)) }
    val back = { stack = stack.dropLast(1) }
    // 쌓인 화면마다 따로 정하면 사라지는 화면이 늦게 정리되며 값을 덮으므로, 맨 위 화면 기준으로 한 번만 정한다.
    StatusBarIcons(darkBackground = stack.last() in DARK_SCREENS)
    PushNavigation(stack, onBack = back) { screen ->
        when (screen) {
            "music" -> MusicApp(back)
            "map" -> MapApp(back)
            "mail" -> MailApp(back)
            "health" -> HealthApp(back)
            else -> AppsHome(onBack) { stack = stack + it }
        }
    }
}

@Composable
private fun AppsHome(onBack: () -> Unit, open: (String) -> Unit) {
    val list = rememberLazyListState()
    KineticaScaffold(
        Modifier.fillMaxSize(),
        surface = demoSurface(APPS_DEMO_LOOK),
        background = { DemoBackdrop { MeshBackdrop(light = MeshGradients.Candy, dark = MeshGradients.Midnight) } },
        topBar = { DemoTopBar("앱 화면 예", "컴포넌트를 조합해 만든 실제 앱 같은 화면", onBack, scrolled = { list.canScrollBackward }) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(
                start = readingPadding(),
                end = readingPadding(),
                top = padding.calculateTopPadding() + 8.dp,
                bottom =
                    navigationBarBottom() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            APPS.forEach { app -> item(app.id) { AppEntry(app) { open(app.id) } } }
        }
    }
}

@Composable
private fun AppEntry(app: AppInfo, onClick: () -> Unit) {
    KSurface(onClick, Modifier.fillMaxWidth().jellyPress(0.04f)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(app.title, 19, FontWeight.Bold)
                Label(app.summary, 13, color = LocalKineticaContentColor.current.copy(alpha = 0.72f))
            }
            ShowcaseIcon(ShowcaseIcons.Chevron, 20.dp, LocalKineticaContentColor.current.copy(alpha = 0.6f))
        }
    }
}

private class AppInfo(val id: String, val title: String, val summary: String)

private const val APPS_HOME = "apps"
private val DARK_SCREENS = setOf(APPS_HOME, "music", "health")
private val APPS = listOf(
    AppInfo("music", "음악", "목록을 내리면 아래 탭 바가 작은 캡슐로 줄어듭니다. 탭 바 위 미니 플레이어를 누르거나 위로 끌면 앨범 아트가 커지며 전체 화면 플레이어로 펼쳐지고, 불러오는 동안에는 스켈레톤이 보입니다."),
    AppInfo("map", "지도", "Apple 지도처럼 여러 높이에 멈추는 바텀 시트와 검색, 상단 탭, 툴팁."),
    AppInfo("mail", "메일", "메일을 누르면 본문이 열리고, 펼친 폴더블이나 태블릿에서는 목록과 본문이 두 칸으로 나란히 보입니다. 밀어서 보관·삭제, 길게 눌러 순서 바꾸기, 서랍 메뉴도 있습니다."),
    AppInfo("health", "건강", "활동 링, 그려지며 나타나는 선 그래프, 막대·도넛 차트, 달력, 플립 카운트다운, 범위 슬라이더."),
)

private val APPS_DEMO_LOOK = DemoLook(GlassStyles.Liquid, GlassStyles.LiquidDark, defaultDark = true)
