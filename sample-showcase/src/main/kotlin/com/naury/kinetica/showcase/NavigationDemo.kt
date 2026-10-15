// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.naury.kinetica.ui.GlassExpandHost
import com.naury.kinetica.ui.GlassExpandState
import com.naury.kinetica.ui.GlassIconButton
import com.naury.kinetica.ui.GlassScaffold
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.GlassSurface
import com.naury.kinetica.ui.LocalGlassContentColor
import com.naury.kinetica.ui.PushNavigation
import com.naury.kinetica.ui.expandSource
import com.naury.kinetica.ui.glassClickable
import com.naury.kinetica.ui.jellyPress
import com.naury.kinetica.ui.rememberGlassExpandState

/**
 * 화면 이동 데모. 카드를 누르면 그 자리에서 상세 화면으로 커지고, 아래로 끌면 닫힌다. 목록 항목을 누르면 iOS처럼 다음 화면이
 * 옆에서 밀려 들어오고, 왼쪽 가장자리에서 끌거나 뒤로 가기를 누르면 돌아간다.
 */
@Composable
internal fun NavigationDemo(onBack: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(listOf(ROOT)) }
    // 하위 화면은 단계마다 밝고 어두운 배경이 번갈아 나온다. 맨 위 화면 기준으로 한 번만 정한다.
    StatusBarIcons(darkBackground = stack.size > 1 && (stack.size - 1) % 2 == 0)
    PushNavigation(stack, onBack = { stack = stack.dropLast(1) }) { screen ->
        if (screen == ROOT) {
            NavigationHome(onBack) { stack = stack + it }
        } else {
            NavigationDetail(screen, depth = stack.indexOf(screen), { stack = stack.dropLast(1) }) { stack = stack + it }
        }
    }
}

@Composable
private fun NavigationHome(onBack: () -> Unit, push: (String) -> Unit) {
    val expand = rememberGlassExpandState()
    GlassScaffold(
        Modifier.fillMaxSize(),
        style = GlassStyles.Liquid,
        background = { Aurora(Modifier.fillMaxSize(), dark = false) },
        topBar = { DemoTopBar("화면 이동", "카드는 제자리에서 열리고, 목록은 옆에서 밀려 들어옵니다", onBack) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = readingPadding(),
                end = readingPadding(),
                top = padding.calculateTopPadding() + 8.dp,
                bottom =
                    navigationBarBottom() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Label("카드를 눌러 열어 보세요", 15, FontWeight.SemiBold, Modifier.widthIn(max = 520.dp).fillMaxWidth()) }
            STORIES.forEachIndexed { index, story -> item("story$index") { StoryCard(story, expand, index) } }
            item { Label("항목을 눌러 들어간 뒤, 왼쪽 가장자리를 밀어 돌아와 보세요", 15, FontWeight.SemiBold, Modifier.widthIn(max = 520.dp).fillMaxWidth()) }
            item { MenuList(MENU, push) }
        }
        GlassExpandHost(expand, cornerRadius = 28.dp, title = "이야기") { key -> StoryDetail(STORIES[key as Int], expand) }
        // 상세 위쪽은 어두운 사진이라, 열려 있는 동안 상태 표시줄 아이콘을 밝게 바꾼다.
        val view = LocalView.current
        val expanded = expand.expandedKey != null
        DisposableEffect(expanded) {
            val window = (view.context as? android.app.Activity)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val before = controller?.isAppearanceLightStatusBars
            if (expanded) controller?.isAppearanceLightStatusBars = false
            onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
        }
    }
}

@Composable
private fun StoryCard(story: Story, expand: GlassExpandState, index: Int) {
    Box(
        Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .expandSource(expand, index)
            .jellyPress(0.03f)
            .clip(STORY_SHAPE)
            .glassClickable(STORY_SHAPE) { expand.expand(index) }
            .semantics { contentDescription = "${story.title} 열기" },
    ) { StoryCover(story) }
}

/** 카드와 상세 화면 윗부분이 똑같이 생겨야 커지는 동안 화면이 튀지 않는다. */
@Composable
private fun StoryCover(story: Story) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.15f).background(Brush.linearGradient(story.colors)),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(Modifier.padding(24.dp)) {
            Label(story.kicker, 13, FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
            Label(story.title, 28, FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun StoryDetail(story: Story, expand: GlassExpandState) {
    Box(Modifier.fillMaxSize().background(Color(0xFFF7F7FA))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            StoryCover(story)
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Label("아래로 끌면 작아지면서 원래 카드로 돌아갑니다.", 15, FontWeight.SemiBold, color = Color(0xFF15161A))
                repeat(6) { Label(story.body, 16, color = Color(0xFF3A3B40)) }
            }
        }
        GlassIconButton(
            ShowcaseIcons.Close,
            "닫기",
            { expand.collapse() },
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                .padding(16.dp),
        )
    }
}

@Composable
private fun NavigationDetail(screen: String, depth: Int, back: () -> Unit, push: (String) -> Unit) {
    GlassScaffold(
        Modifier.fillMaxSize(),
        style = GlassStyles.Liquid,
        background = { Aurora(Modifier.fillMaxSize(), dark = depth % 2 == 0) },
        topBar = { DemoTopBar(screen.substringAfterLast('/'), "${depth}단계 화면, 왼쪽 가장자리를 밀면 뒤로 갑니다", back) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = readingPadding(),
                end = readingPadding(),
                top = padding.calculateTopPadding() + 8.dp,
                bottom =
                    navigationBarBottom() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { MenuList(MENU.map { "$screen/$it" }.take(3), push) }
        }
    }
}

@Composable
private fun MenuList(items: List<String>, push: (String) -> Unit) {
    // 행 눌림 표시가 카드 모서리 밖으로 나가지 않도록 6dp 안쪽에 18dp(바깥 24dp − 6dp) 모서리로 그린다.
    GlassSurface(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
        Column(Modifier.padding(6.dp)) {
            items.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MENU_ROW_SHAPE)
                        .glassClickable(MENU_ROW_SHAPE) { push(item) }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label(item.substringAfterLast('/'), 16, modifier = Modifier.weight(1f))
                    ShowcaseIcon(ShowcaseIcons.Chevron, 18.dp, LocalGlassContentColor.current.copy(alpha = 0.5f))
                }
            }
        }
    }
}

private class Story(val kicker: String, val title: String, val colors: List<Color>, val body: String)

private const val ROOT = "home"
private val STORY_SHAPE = RoundedCornerShape(28.dp)
private val MENU_ROW_SHAPE = RoundedCornerShape(18.dp)
private val MENU = listOf("알림", "화면 표시", "개인정보", "배터리")
private val STORIES = listOf(
    Story(
        "오늘의 앱",
        "빛으로 그린 도시",
        listOf(Color(0xFF141E30), Color(0xFF8E54E9)),
        "밤의 도시를 빛의 줄기로 담는 사진 앱입니다. 셔터를 오래 열어 차의 불빛을 길게 남깁니다.",
    ),
    Story(
        "새 기능",
        "바다를 담는 법",
        listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
        "물결이 멈춘 듯한 순간을 잡으려면 빠른 셔터와 낮은 시선이 필요합니다.",
    ),
    Story(
        "함께 보기",
        "노을 산책",
        listOf(Color(0xFFFF7E5F), Color(0xFFFEB47B)),
        "해가 지기 30분 전이 가장 좋습니다. 하늘이 주황에서 보라로 바뀌는 동안 천천히 걸어 보세요.",
    ),
)
