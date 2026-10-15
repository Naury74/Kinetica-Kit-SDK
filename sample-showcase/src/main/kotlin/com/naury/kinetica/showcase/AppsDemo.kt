// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.GlassScaffold
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.GlassSurface
import com.naury.kinetica.ui.LocalGlassContentColor
import com.naury.kinetica.ui.MeshGradientBackground
import com.naury.kinetica.ui.MeshGradients
import com.naury.kinetica.ui.PushNavigation
import com.naury.kinetica.ui.jellyPress

/** 앱 화면 예: kinetica-ui 컴포넌트를 조합해 만든 음악, 지도, 메일, 건강 앱 화면. 고르면 옆에서 밀려 들어온다. */
@Composable
internal fun AppsDemo(onBack: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(listOf(APPS_HOME)) }
    val back = { stack = stack.dropLast(1) }
    PushNavigation(stack, onBack = back) { screen ->
        StatusBarIcons(darkBackground = screen in DARK_SCREENS)
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
    GlassScaffold(
        Modifier.fillMaxSize(),
        style = GlassStyles.LiquidDark,
        background = { MeshGradientBackground(Modifier.fillMaxSize(), palette = MeshGradients.Midnight) },
        topBar = { DemoTopBar("앱 화면 예", "컴포넌트를 조합해 만든 실제 앱 같은 화면", onBack) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            APPS.forEach { app -> item(app.id) { AppEntry(app) { open(app.id) } } }
        }
    }
}

@Composable
private fun AppEntry(app: AppInfo, onClick: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth().jellyPress(0.04f).clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(app.title, 19, FontWeight.Bold)
                Label(app.summary, 13, color = LocalGlassContentColor.current.copy(alpha = 0.72f))
            }
            Label("›", 26, FontWeight.Bold)
        }
    }
}

private class AppInfo(val id: String, val title: String, val summary: String)

private const val APPS_HOME = "apps"
private val DARK_SCREENS = setOf(APPS_HOME, "music", "health")
private val APPS = listOf(
    AppInfo("music", "음악", "미니 플레이어를 누르거나 위로 끌면 전체 화면 플레이어가 됩니다. 재생 버튼 모양 바꾸기, 좋아요, 별점, 불러오는 동안 스켈레톤."),
    AppInfo("map", "지도", "Apple 지도처럼 여러 높이에 멈추는 바텀 시트와 검색, 상단 탭, 툴팁."),
    AppInfo("mail", "메일", "밀어서 보관·삭제, 길게 눌러 순서 바꾸기, 서랍 메뉴, 처음 쓰는 사람을 위한 기능 안내."),
    AppInfo("health", "건강", "활동 링, 그려지며 나타나는 선 그래프, 막대·도넛 차트, 달력, 플립 카운트다운, 범위 슬라이더."),
)
