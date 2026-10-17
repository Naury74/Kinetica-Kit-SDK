// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.MeshGradients
import com.naury.kinetica.ui.PushNavigation
import com.naury.kinetica.ui.jellyPress

/** 앱 화면 예: kinetica-ui 컴포넌트를 조합해 만든 실제 앱 같은 화면을 분류별 격자로 보여 준다. 고르면 옆에서 밀려 들어온다. */
@Composable
internal fun AppsDemo(onBack: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(listOf(APPS_HOME)) }
    val back = { stack = stack.dropLast(1) }
    // 쌓인 화면마다 따로 정하면 사라지는 화면이 늦게 정리되며 값을 덮으므로, 맨 위 화면 기준으로 한 번만 정한다.
    StatusBarIcons(darkBackground = stack.last() in DARK_SCREENS || appLookOf(stack.last())?.surface()?.isDark == true)
    PushNavigation(stack, onBack = back) { screen ->
        val app = APP_SCREENS[screen]
        if (app != null) app(back) else AppsHome(onBack) { stack = stack + it }
    }
}

@Composable
private fun AppsHome(onBack: () -> Unit, open: (String) -> Unit) {
    val grid = rememberLazyGridState()
    val side = readingPadding(16.dp).coerceAtMost(appPadding(16.dp, 1100.dp))
    // 칸 하나가 170dp(글자를 키우면 그만큼 넓게)는 되도록 열 수를 정한다. 폰 2열, 펼친 폴더블 3열, 태블릿 4열.
    val tile = 170.dp * LocalDensity.current.fontScale.coerceIn(1f, 2f)
    val width = LocalConfiguration.current.screenWidthDp.dp - side * 2
    val columns = (width / tile).toInt().coerceIn(1, 4)
    KineticaScaffold(
        Modifier.fillMaxSize(),
        surface = demoSurface(APPS_DEMO_LOOK),
        background = { DemoBackdrop { MeshBackdrop(light = MeshGradients.Candy, dark = MeshGradients.Midnight) } },
        topBar = { DemoTopBar("앱 화면 예", "컴포넌트를 조합해 만든 실제 앱 같은 화면", onBack, scrolled = { grid.canScrollBackward }) },
    ) { padding ->
        LazyVerticalGrid(
            GridCells.Fixed(columns),
            Modifier.fillMaxSize(),
            state = grid,
            contentPadding = PaddingValues(
                start = side,
                end = side,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = navigationBarBottom() + 32.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "surface", span = { GridItemSpan(maxLineSpan) }) { AppSurfaceRow() }
            CATEGORIES.forEach { (category, apps) ->
                item(key = "c-$category", span = { GridItemSpan(maxLineSpan) }) {
                    Label(category, 15, FontWeight.Bold, Modifier.padding(start = 6.dp, top = 12.dp))
                }
                items(apps, key = { it.id }) { app -> AppTile(app) { open(app.id) } }
            }
        }
    }
}

/** 앱 화면 예의 표면을 설정에서 가져올지 앱 추천을 쓸지 고르는 줄. 형태는 어느 쪽이든 설정을 따른다. */
@Composable
private fun AppSurfaceRow() {
    val look = LocalShowcaseLook.current
    val ink = LocalKineticaContentColor.current
    Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KSegmentedControl(
            AppSurface.entries.map { it.label },
            look.appSurface.ordinal,
            { look.appSurface = AppSurface.entries[it] },
            Modifier.fillMaxWidth(),
        )
        Label(
            if (look.appSurface == AppSurface.FollowLook) {
                "모든 앱이 모양 설정의 표면(${look.family.label})과 형태(${look.designName})를 따릅니다. 칸 오른쪽 위는 앱마다 추천하는 표면입니다."
            } else {
                "앱마다 어울리는 표면을 씁니다. 형태(${look.designName})와 밝기는 모양 설정을 따릅니다."
            },
            13,
            color = ink.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun AppTile(app: AppInfo, onClick: () -> Unit) {
    val ink = LocalKineticaContentColor.current
    KSurface(onClick, Modifier.fillMaxWidth().jellyPress(0.04f), shape = RoundedCornerShape(28.dp), onClickLabel = "열기") {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 바깥 28dp 모서리에서 여백 16dp를 뺀 12dp로 아이콘 칸 모서리를 맞춘다.
                Box(Modifier.size(44.dp).background(app.color, RoundedCornerShape(12.dp)), Alignment.Center) {
                    ShowcaseIcon(app.icon, 24.dp, Color.White)
                }
                Spacer(Modifier.weight(1f))
                Label(app.surfaceLabel(), 11, FontWeight.SemiBold, color = ink.copy(alpha = 0.6f))
            }
            Label(app.title, 17, FontWeight.Bold)
            Label(app.summary, 13, color = ink.copy(alpha = 0.72f))
        }
    }
}

private class AppInfo(val id: String, val title: String, val summary: String, val icon: ImageVector, val color: Color)

/** 칸 오른쪽 위에 보여 줄 표면 안내. 추천 표면이 없는 앱은 설정을 따르고, 표면을 고정한 앱은 그렇게 적는다. */
private fun AppInfo.surfaceLabel(): String {
    val app = appLookOf(id) ?: return "설정 따름"
    return if (app.fixed) "${app.family.label} 고정" else "추천: ${app.family.label}"
}

private const val APPS_HOME = "apps"

/** 앱 id → 그 앱 화면. 앱마다 뒤로 가기 콜백 하나만 받는다. */
private val APP_SCREENS: Map<String, @Composable (() -> Unit) -> Unit> = mapOf(
    "music" to { back -> MusicApp(back) },
    "map" to { back -> MapApp(back) },
    "mail" to { back -> MailApp(back) },
    "health" to { back -> HealthApp(back) },
    "shop" to { back -> ShopApp(back) },
    "bank" to { back -> BankApp(back) },
    "chat" to { back -> ChatApp(back) },
    "feed" to { back -> FeedApp(back) },
    "delivery" to { back -> DeliveryApp(back) },
    "travel" to { back -> TravelApp(back) },
    "smarthome" to { back -> SmartHomeApp(back) },
    "tasks" to { back -> TasksApp(back) },
    "settings" to { back -> SettingsApp(back) },
    "call" to { back -> CallApp(back) },
)
private val DARK_SCREENS = setOf(APPS_HOME, "music", "health")
private val CATEGORIES = listOf(
    "소통" to listOf(
        AppInfo("chat", "메신저", "보내면 상대가 입력 중 표시 뒤에 답하고, 사진과 파일을 올릴 수 있습니다.", KIcons.Message, Color(0xFF6E56CF)),
        AppInfo("call", "전화", "걸려 오는 전화, 음성 통화와 아일랜드, 그룹 영상 통화까지 이어집니다.", ShowcaseIcons.Phone, Color(0xFF30C85E)),
        AppInfo("mail", "메일", "밀어서 보관하고 길게 눌러 순서를 바꿉니다. 넓은 화면에서는 두 칸입니다.", KIcons.Mail, Color(0xFF0A84FF)),
        AppInfo("feed", "소셜 피드", "스토리 링, 두 번 눌러 좋아요, 댓글 바텀 시트와 프로필이 있습니다.", KIcons.Heart, Color(0xFFFF4D6D)),
    ),
    "쇼핑·생활" to listOf(
        AppInfo("shop", "쇼핑", "담으면 상품이 장바구니로 날아가고, 결제 단계를 거쳐 주문을 마칩니다.", KIcons.Bag, Color(0xFF1F1F1F)),
        AppInfo("delivery", "음식 배달", "메뉴를 담아 주문하면 타임라인과 라이더 핀이 단계마다 움직입니다.", AppIcons.Bike, Color(0xFFF46A00)),
        AppInfo("travel", "여행 예약", "날짜와 항공편, 좌석을 고르면 뒤집히는 탑승권이 나옵니다.", AppIcons.Plane, Color(0xFF2F6BFF)),
        AppInfo("bank", "송금", "키패드로 금액을 넣고 비밀번호를 누르면 잔액과 내역이 바뀝니다.", KIcons.Card, Color(0xFF0F9D84)),
        AppInfo("map", "지도", "여러 높이에 멈추는 바텀 시트와 검색, 상단 탭이 있습니다.", KIcons.Location, Color(0xFF34C759)),
    ),
    "미디어·건강" to listOf(
        AppInfo("music", "음악", "미니 플레이어를 위로 끌면 전체 화면 플레이어로 펼쳐집니다.", ShowcaseIcons.Wave, Color(0xFFFF2D55)),
        AppInfo("health", "건강", "활동 링과 그려지며 나타나는 차트, 달력, 카운트다운이 있습니다.", KIcons.Heart, Color(0xFFFF6B3D)),
    ),
    "업무·도구" to listOf(
        AppInfo("tasks", "할 일", "체크하면 진행 링이 차오르고, 밀어서 지운 일은 되돌릴 수 있습니다.", KIcons.Check, Color(0xFF6750A4)),
        AppInfo("smarthome", "스마트 홈", "장면을 누르면 기기가 한꺼번에 바뀌고, 조명과 냉난방을 조절합니다.", AppIcons.Bulb, Color(0xFFE08A1E)),
        AppInfo("settings", "설정", "스위치, 라디오, 슬라이더와 Wi-Fi 연결 다이얼로그가 있습니다.", KIcons.Settings, Color(0xFF8E8E93)),
    ),
)

private val APPS_DEMO_LOOK = DemoLook(GlassStyles.Liquid, GlassStyles.LiquidDark, defaultDark = true)
