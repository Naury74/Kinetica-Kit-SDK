// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.jellyPress

/**
 * UI 키트 허브. 분류(표면과 디자인, 앱 화면 예, 컴포넌트, 모션과 화면 전환)마다 제목을 두고 그 아래에 데모를 격자로 놓는다.
 * 큰 칸은 폭 [FEATURE_CELL], 작은 칸은 폭 [TILE_CELL]을 기준으로 열 수를 정하고, 글자를 키우면 기준 폭도 함께 늘어난다.
 * 스크롤 위치는 rememberSaveable이라 데모에서 돌아오거나 회전, 폴드 전환을 해도 남는다.
 */
@Composable
internal fun KitHub(onBack: () -> Unit, open: (String) -> Unit) {
    val list = rememberLazyListState()
    KineticaScaffold(
        Modifier.fillMaxSize(),
        surface = demoSurface(HUB_LOOK),
        background = { DemoBackdrop() },
        topBar = { DemoTopBar("UI 키트", "분류를 골라 데모로 들어갑니다", onBack, demoContentWidth(), scrolled = { list.canScrollBackward }) },
    ) { padding ->
        val side = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
        val layout = LocalLayoutDirection.current
        val scale = LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val inner = maxWidth - side.calculateStartPadding(layout) - side.calculateEndPadding(layout)
            val gutter = maxOf(20.dp, (inner - demoContentWidth()) / 2)
            val content = inner - gutter * 2
            LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
                contentPadding = PaddingValues(
                    start = gutter + side.calculateStartPadding(layout),
                    end = gutter + side.calculateEndPadding(layout),
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = navigationBarBottom() + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(HUB_GAP),
            ) {
                kitSections().forEach { section ->
                    if (section.title != null) item("title-${section.id}") { SectionTitle(section.title, section.summary) }
                    val cell = (if (section.featured) FEATURE_CELL else TILE_CELL) * scale
                    val columns = columnsFor(content, cell, minOf(section.entries.size, MAX_COLUMNS))
                    hubRows(section, columns, open)
                }
            }
        }
    }
}

/** 칸 폭 [cell]과 간격으로 [width]에 들어가는 열 수. 칸이 [count]개보다 많은 열은 만들지 않는다. */
internal fun columnsFor(width: Dp, cell: Dp, count: Int): Int = ((width + HUB_GAP) / (cell + HUB_GAP)).toInt().coerceIn(1, maxOf(1, count))

/** 허브의 한 분류 */
internal class HubSection(val id: String, val title: String?, val summary: String, val featured: Boolean, val entries: List<HubEntry>)

/** 허브 칸 하나. [route]는 [ShowcaseRoutes] 경로다. */
internal class HubEntry(val route: String, val title: String, val summary: String, val icon: ImageVector, val accent: Color)

private fun Demo.entry() = HubEntry(ShowcaseRoutes.demo(this), title, summary, icon, accent)

/**
 * 허브 분류 목록. 새 데모는 알맞은 분류의 entries에 더한다.
 * - 디자인 언어(형태) 비교 데모가 생기면 "featured" 분류의 표면과 디자인 다음 칸으로 넣는다.
 * - 화면 이동과 장면 전환 종류를 따로 보여 줄 데모가 생기면 "motion" 분류에 넣는다.
 * - 앱 화면 예가 많아지면 앱별 칸을 모은 "apps" 분류를 따로 만들 수 있다. 지금은 앱 목록 화면 하나로 들어간다.
 */
internal fun kitSections(): List<HubSection> = listOf(
    // 맨 위 두 칸은 제목 없이 크게 둔다. 칸 제목이 곧 분류 이름이라 위에 제목을 또 달면 같은 말이 두 번 보인다.
    HubSection(
        "featured",
        title = null,
        summary = "",
        featured = true,
        entries = listOf(Demo.Surfaces.entry(), Demo.Apps.entry()),
    ),
    HubSection(
        "components",
        "컴포넌트",
        "종류를 고르면 컴포넌트 화면이 그 탭으로 열립니다.",
        featured = false,
        entries = COMPONENT_TABS.mapIndexed { index, tab ->
            val (summary, icon) = COMPONENT_TAB_INFO[tab] ?: ("" to ShowcaseIcons.Layers)
            HubEntry(ShowcaseRoutes.componentsTab(index), tab, summary, icon, TILE_ACCENTS[index % TILE_ACCENTS.size])
        },
    ),
    HubSection(
        "motion",
        "모션과 화면 전환",
        "효과 수정자, 화면 이동 전환 22가지와 컨테이너 변환, 서버 장면 전환 14가지입니다.",
        featured = false,
        entries = listOf(Demo.Motion.entry(), Demo.Navigation.entry(), Demo.Transition.entry()),
    ),
)

private val COMPONENT_TAB_INFO = mapOf(
    "기본" to ("버튼, 검색, 휠 피커, 캐러셀, 카드 더미, 알림" to KIcons.Home),
    "입력" to ("입력란 모양, 인증번호, 태그, 색 선택, 서명, 비밀번호" to KIcons.Edit),
    "미디어" to ("사진 뷰어, 전후 비교, 온보딩, 빈 화면" to KIcons.Download),
    "효과" to ("물결 왜곡, 파티클, 뒤집는 카드, 액체 버튼, 홀로그램" to ShowcaseIcons.Sparkle),
    "버튼·칩·카드" to ("버튼 변형과 색 역할, 칩, 카드, 배지와 아바타" to KIcons.Plus),
    "내비게이션·오버레이" to ("앱 바, 탭, 메뉴, 다이얼로그와 시트, 검색 화면, 서랍" to ShowcaseIcons.Layers),
    "피드백·로딩" to ("토스트, 스낵바, 배너, 스켈레톤, 진행 막대, 햅틱" to KIcons.Bell),
    "데이터·도메인" to ("대시보드, 배송 추적, 결제 단계, 요금제, 송금, 히트맵" to KIcons.Grid),
    "소셜·모션" to ("채팅, 스토리, 연락처 색인, 지도 핀, 마이크로 인터랙션" to KIcons.Message),
)

private val TILE_ACCENTS = listOf(
    Color(0xFFFFA8E0),
    Color(0xFF7AA2FF),
    Color(0xFFFFD36E),
    Color(0xFFFF7EB3),
    Color(0xFF5EF0C8),
    Color(0xFFB98CFF),
    Color(0xFFFFC56E),
    Color(0xFF7AE0FF),
    Color(0xFFA0F08C),
)

@Composable
private fun SectionTitle(title: String, summary: String) {
    Column(Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Label(title, 20, FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Label(summary, 13, color = LocalKineticaContentColor.current.copy(alpha = 0.68f))
    }
}

/** [section]의 칸을 [columns]열로 놓는다. 같은 줄 칸은 설명 길이가 달라도 높이를 맞춘다. */
private fun LazyListScope.hubRows(section: HubSection, columns: Int, open: (String) -> Unit) {
    section.entries.chunked(columns).forEach { row ->
        item(row.first().route) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(HUB_GAP)) {
                row.forEach { entry ->
                    val modifier = Modifier.weight(1f).fillMaxHeight()
                    if (section.featured) {
                        FeatureEntry(entry, modifier) { open(entry.route) }
                    } else {
                        TileEntry(entry, modifier) { open(entry.route) }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 큰 칸: 색 원 안 아이콘, 제목, 두세 줄 설명 */
@Composable
private fun FeatureEntry(entry: HubEntry, modifier: Modifier, onClick: () -> Unit) {
    KSurface(onClick, modifier.jellyPress(0.03f), shape = RoundedCornerShape(28.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).background(entry.accent.copy(alpha = 0.9f), CircleShape), contentAlignment = Alignment.Center) {
                ShowcaseIcon(entry.icon, 24.dp, Color(0xFF15161A))
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(entry.title, 19, FontWeight.Bold)
                Label(entry.summary, 13, color = LocalKineticaContentColor.current.copy(alpha = 0.75f))
            }
            ShowcaseIcon(KIcons.ChevronForward, 20.dp, LocalKineticaContentColor.current.copy(alpha = 0.6f))
        }
    }
}

private val HUB_LOOK = DemoLook(GlassStyles.Liquid, GlassStyles.LiquidDark, defaultDark = true)
private val HUB_GAP = 12.dp
private val FEATURE_CELL = 340.dp
private val TILE_CELL = 150.dp
private const val MAX_COLUMNS = 4

/** 작은 칸: 위에 색 원 아이콘, 아래에 제목과 설명. 폰에서도 두 열로 놓인다. */
@Composable
private fun TileEntry(entry: HubEntry, modifier: Modifier, onClick: () -> Unit) {
    KSurface(onClick, modifier.jellyPress(0.04f), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(40.dp).background(entry.accent.copy(alpha = 0.9f), CircleShape), contentAlignment = Alignment.Center) {
                ShowcaseIcon(entry.icon, 20.dp, Color(0xFF15161A))
            }
            Spacer(Modifier.height(8.dp))
            Label(entry.title, 16, FontWeight.Bold)
            Label(entry.summary, 13, color = LocalKineticaContentColor.current.copy(alpha = 0.72f))
        }
    }
}
