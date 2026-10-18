// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KActionSheet
import com.naury.kinetica.ui.KAlertDialog
import com.naury.kinetica.ui.KAutocomplete
import com.naury.kinetica.ui.KBottomAppBar
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KDialogAction
import com.naury.kinetica.ui.KDialogActionStyle
import com.naury.kinetica.ui.KDrawer
import com.naury.kinetica.ui.KDrawerMode
import com.naury.kinetica.ui.KDropdownMenu
import com.naury.kinetica.ui.KFloatingToolbar
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KMenuEntry
import com.naury.kinetica.ui.KSearchView
import com.naury.kinetica.ui.KSegment
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KSelect
import com.naury.kinetica.ui.KTab
import com.naury.kinetica.ui.KTabIndicator
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KTopAppBar
import com.naury.kinetica.ui.KTopAppBarVariant
import com.naury.kinetica.ui.KTopTabs
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.kRichTooltip
import com.naury.kinetica.ui.rememberKDrawerState
import com.naury.kinetica.ui.rememberKFloatingToolbarState
import com.naury.kinetica.ui.rememberKTopAppBarState
import kotlinx.coroutines.launch

/** 내비게이션과 오버레이 탭: 앱 바, 탭 인디케이터, 메뉴, 선택 상자, 다이얼로그, 동작 시트, 검색 화면, 모달 서랍. */
@Composable
internal fun NavOverlayTab(padding: PaddingValues, connection: NestedScrollConnection) {
    ComponentTabList(padding, connection) {
        item { AppBarsCard() }
        item { TabsCard() }
        item { MenusCard() }
        item { DialogsCard() }
        item { SearchCard() }
        item { DrawerCard() }
    }
}

@Composable
private fun AppBarsCard() {
    var variant by rememberSaveable { mutableIntStateOf(0) }
    val toolbar = rememberKFloatingToolbarState()
    val scroll = rememberScrollState()
    val bar = rememberKTopAppBarState(scroll)
    DemoCard("앱 바", "맨 위에서는 배경 없이 화면과 이어지다가, 목록이 바 아래로 지나가면 배경이 나타납니다. 안쪽 목록을 스크롤해 보세요.") {
        KSegmentedControl(listOf("작게", "가운데", "중간"), variant, { variant = it }, Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().height(320.dp).clip(innerShape)) {
            // 첫 줄이 바 바로 아래에서 시작하도록 바 높이(한 줄 바 56dp, 두 줄 바는 제목 줄까지 약 110dp)만큼 띄운다.
            val top = if (KTopAppBarVariant.entries[variant] == KTopAppBarVariant.Medium) 128.dp else 64.dp
            Column(Modifier.fillMaxSize().nestedScroll(toolbar.connection).verticalScroll(scroll).padding(top = top, bottom = 96.dp)) {
                repeat(16) { Label("메모 ${it + 1}", 15, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) }
            }
            KTopAppBar(
                "메모",
                variant = KTopAppBarVariant.entries[variant],
                state = bar,
                onBack = {},
                subtitle = if (variant == 2) "16개" else null,
                actions = { KIconButton(KIcons.Edit, "편집", {}, variant = KVariant.Text, tone = KTone.Neutral) },
                windowInsets = WindowInsets(0),
            )
            KFloatingToolbar(
                Modifier.align(Alignment.BottomCenter),
                expanded = toolbar.expanded,
                floatingActionButton = { KIconButton(KIcons.Plus, "새 메모", {}, variant = KVariant.Filled) },
                windowInsets = WindowInsets(0),
            ) {
                KIconButton(KIcons.Edit, "쓰기", {}, variant = KVariant.Text, tone = KTone.Neutral)
                KIconButton(KIcons.Archive, "보관", {}, variant = KVariant.Text, tone = KTone.Neutral)
                KIconButton(KIcons.Trash, "삭제", {}, variant = KVariant.Text, tone = KTone.Neutral)
            }
        }
        KBottomAppBar(
            Modifier.clip(innerShape),
            floatingActionButton = { KIconButton(KIcons.Send, "보내기", {}, variant = KVariant.Filled) },
            windowInsets = WindowInsets(0),
        ) {
            KIconButton(KIcons.Link, "링크", {}, variant = KVariant.Text, tone = KTone.Neutral)
            KIconButton(KIcons.Download, "받기", {}, variant = KVariant.Text, tone = KTone.Neutral)
        }
    }
}

@Composable
private fun TabsCard() {
    val pager = rememberPagerState { 3 }
    var view by rememberSaveable { mutableIntStateOf(0) }
    var days by rememberSaveable { mutableStateOf(setOf(0, 2)) }
    val tabs = listOf(KTab("전체"), KTab("사진", badge = 4), KTab("동영상", dot = true))
    DemoCard("탭과 세그먼트", "인디케이터 모양을 캡슐, 밑줄, 세그먼트 중에서 고르고 탭에 배지를 붙입니다. 아래 칸을 옆으로 넘기면 세 탭이 함께 움직입니다.") {
        KTopTabs(tabs, pager, indicator = KTabIndicator.Pill)
        KTopTabs(tabs, pager, indicator = KTabIndicator.Underline)
        KTopTabs(tabs, pager, indicator = KTabIndicator.Segmented)
        HorizontalPager(pager, Modifier.fillMaxWidth().height(56.dp)) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label("${page + 1}번째 페이지", 14) }
        }
        KSegmentedControl(
            listOf(
                KSegment("목록", KIcons.Inbox),
                KSegment("격자", KIcons.Grid),
                KSegment(icon = KIcons.Location, contentDescription = "지도"),
            ),
            view,
            { view = it },
            Modifier.fillMaxWidth(),
        )
        KSegmentedControl(listOf("월", "화", "수", "목", "금").map { KSegment(it) }, days, { days = it }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun MenusCard() {
    var menu by remember { mutableStateOf(false) }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("이름") }
    var city by rememberSaveable { mutableIntStateOf(-1) }
    var query by rememberSaveable { mutableStateOf("") }
    DemoCard("메뉴와 선택", "메뉴는 아래 공간이 모자라면 위로 뒤집히고, 키보드 방향키로 항목을 옮깁니다. 하위 메뉴는 같은 판 안에서 넘어갑니다.") {
        Box {
            KButton("정렬: $sort", { menu = true }, variant = KVariant.Tonal)
            KDropdownMenu(
                menu,
                { menu = false },
                listOf(
                    KMenuEntry.Section("보기"),
                    KMenuEntry.Check("숨긴 메모 보기", hidden) { hidden = it },
                    KMenuEntry.Divider,
                    KMenuEntry.Submenu("정렬 기준", listOf("이름", "날짜", "크기").map { KMenuEntry.Action(it) { sort = it } }),
                    KMenuEntry.Action("복사", KIcons.Link, trailing = "Ctrl+C") {},
                    KMenuEntry.Action("삭제", KIcons.Trash, destructive = true) {},
                ),
            )
        }
        KSelect(CITIES, city, { city = it }, "지역", Modifier.fillMaxWidth())
        KAutocomplete(
            query,
            { query = it },
            CITIES.filter { query.isNotBlank() && it.contains(query.trim()) },
            { query = it },
            "도시 검색",
            Modifier.fillMaxWidth(),
            placeholder = "예: 서",
        )
    }
}

@Composable
private fun DialogsCard() {
    var alert by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf(false) }
    DemoCard("다이얼로그와 시트", "알림 다이얼로그는 버튼 글자가 길거나 셋이면 세로로 쌓습니다. 동작 시트는 넓은 화면에서 버튼 옆 팝오버로 바뀝니다.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("삭제 확인") { alert = 1 }
            Pill("버튼 셋") { alert = 2 }
            Box {
                Pill("동작 시트") { sheet = true }
                KActionSheet(
                    sheet,
                    { sheet = false },
                    listOf(
                        KDialogAction("링크 복사", icon = KIcons.Link),
                        KDialogAction("앨범에 추가", KDialogActionStyle.Preferred, icon = KIcons.Plus),
                        KDialogAction("삭제", KDialogActionStyle.Destructive, icon = KIcons.Trash),
                    ),
                    title = "사진 3장",
                    message = "선택한 사진으로 할 일을 고르세요.",
                )
            }
            Pill("도움말", Modifier.kRichTooltip(tip, { tip = false }, "기간과 장소로 사진을 걸러 볼 수 있어요.", title = "새 필터", actionLabel = "알겠어요")) {
                tip = true
            }
        }
    }
    KAlertDialog(
        alert != 0,
        { alert = 0 },
        if (alert == 1) "사진을 삭제할까요?" else "변경 사항을 저장할까요?",
        if (alert == 1) {
            listOf(KDialogAction("취소", KDialogActionStyle.Cancel), KDialogAction("삭제", KDialogActionStyle.Destructive) {})
        } else {
            listOf(
                KDialogAction("저장", KDialogActionStyle.Preferred),
                KDialogAction("저장하지 않음", KDialogActionStyle.Destructive),
                KDialogAction("취소", KDialogActionStyle.Cancel),
            )
        },
        message = if (alert == 1) "휴지통에서 30일 뒤 완전히 지워집니다." else "저장하지 않으면 지금까지 쓴 내용이 사라집니다.",
        icon = if (alert == 1) KIcons.Trash else null,
    )
}

@Composable
private fun SearchCard() {
    var open by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var recent by rememberSaveable { mutableStateOf(listOf("강아지", "제주 여행", "노을")) }
    DemoCard("검색 화면", "누르면 검색창이 커지며 검색 화면이 됩니다. 비어 있으면 최근 검색을, 글자를 치면 제안을 보여 줍니다.") {
        KSearchView(
            open,
            { open = it },
            query,
            { query = it },
            { searched ->
                recent = (listOf(searched) + recent).distinct().take(5)
                open = false
            },
            Modifier.fillMaxWidth(),
            suggestions = SUGGESTIONS.filter { query.isNotBlank() && it.contains(query.trim()) },
            recent = recent,
            onRemoveRecent = { removed -> recent = recent - removed },
            onClearRecent = { recent = emptyList() },
            placeholder = "사진 검색",
        )
    }
}

@Composable
private fun DrawerCard() {
    val drawer = rememberKDrawerState()
    val scope = rememberCoroutineScope()
    DemoCard("모달 서랍", "기본 서랍은 뒤 화면을 밀어내고, 모달 서랍은 뒤 화면을 그 자리에서 어둡게 덮습니다. 가장자리에서 끌어도 열립니다.") {
        Box(Modifier.fillMaxWidth().height(260.dp).clip(innerShape)) {
            KDrawer(
                drawerContent = {
                    Label("메뉴", 18, FontWeight.Bold, Modifier.padding(20.dp))
                    listOf("받은편지함", "보낸편지함", "휴지통").forEach {
                        Label(it, 15, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                    }
                },
                state = drawer,
                mode = KDrawerMode.Modal,
            ) {
                Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.Top) {
                    Pill("서랍 열기") { scope.launch { drawer.open() } }
                }
            }
        }
    }
}

private val CITIES = listOf("서울", "부산", "제주", "서귀포", "서산", "대구", "대전", "광주")
private val SUGGESTIONS = listOf("바다 사진", "바다 영상", "바닷가 산책", "노을 사진", "제주 바다")
