// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.ui.GlassDialog
import com.naury.kinetica.ui.GlassMenuItem
import com.naury.kinetica.ui.GlassRefreshBox
import com.naury.kinetica.ui.GlassScaffold
import com.naury.kinetica.ui.GlassSegmentedControl
import com.naury.kinetica.ui.GlassSheet
import com.naury.kinetica.ui.GlassSlider
import com.naury.kinetica.ui.GlassStyle
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.GlassSurface
import com.naury.kinetica.ui.GlassSwitch
import com.naury.kinetica.ui.GlassTab
import com.naury.kinetica.ui.GlassTabBar
import com.naury.kinetica.ui.GlassTextField
import com.naury.kinetica.ui.GlassToastHost
import com.naury.kinetica.ui.LocalGlassContentColor
import com.naury.kinetica.ui.glassBackdrop
import com.naury.kinetica.ui.glassClickable
import com.naury.kinetica.ui.glassContextMenu
import com.naury.kinetica.ui.glassOrigin
import com.naury.kinetica.ui.rememberGlassBackdrop
import com.naury.kinetica.ui.rememberGlassToastState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 글래스 UI 데모. 스타일을 바꿔 가며 다이얼로그, 바텀 시트, 토스트, 탭 바에 배경이 어떻게 비치는지 본다. */
@Composable
internal fun GlassDemo(onBack: () -> Unit) {
    var styleIndex by rememberSaveable { mutableIntStateOf(0) }
    var dark by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf<Offset?>(null) }
    val toast = rememberGlassToastState()
    val scope = rememberCoroutineScope()
    val option = STYLES[styleIndex]
    val style = if (dark) option.dark else option.light
    GlassScaffold(
        Modifier.fillMaxSize(),
        style = style,
        background = { Aurora(Modifier.fillMaxSize(), dark) },
        topBar = { DemoTopBar("글래스 UI · ${option.name}", "배경이 비치는 글래스 표면과 다이얼로그, 시트, 토스트", onBack) },
        bottomBar = {
            GlassTabBar(
                listOf(
                    GlassTab("홈", ShowcaseIcons.Home),
                    GlassTab("효과", ShowcaseIcons.Sparkle),
                    GlassTab("보드", ShowcaseIcons.Grid),
                    GlassTab("알림", ShowcaseIcons.Bell),
                ),
                tab,
                { tab = it },
            )
        },
    ) { padding ->
        // 맨 위에서 당기면 글래스 인디케이터가 물방울처럼 차오르고, 놓으면 새로 고침한다.
        DemoRefresh(padding.calculateTopPadding(), { toast.show("새로 고쳤어요") }) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = readingPadding(),
                    end = readingPadding(),
                    top =
                        padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Intro(dark) }
                item {
                    Chips(STYLES.map { it.name }, styleIndex) { styleIndex = it }
                    Spacer(Modifier.height(8.dp))
                    Chips(listOf("라이트", "다크"), if (dark) 1 else 0) { dark = it == 1 }
                }
                item { PopupCard({ origin = it }, { dialog = true }, { sheet = true }) { scope.launch { toast.show("저장했어요") } } }
                item { ControlsCard() }
                items(PHOTOS.size) { index -> Photo(PHOTOS[index], index) { scope.launch { toast.show(it) } } }
            }
        }
        DemoPopups(dialog, sheet, origin, { dialog = false }, { sheet = false }) { scope.launch { toast.show(it) } }
        GlassToastHost(toast)
    }
}

/** 1.5초 동안 새로 고침을 흉내 낸 뒤 [onDone]을 부른다. */
@Composable
private fun DemoRefresh(top: Dp, onDone: suspend () -> Unit, content: @Composable () -> Unit) {
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (!refreshing) return@LaunchedEffect
        delay(1500)
        refreshing = false
        onDone()
    }
    GlassRefreshBox(refreshing, { refreshing = true }, Modifier.fillMaxSize(), indicatorTop = top) { content() }
}

@Composable
private fun PopupCard(onOrigin: (Offset) -> Unit, openDialog: () -> Unit, openSheet: () -> Unit, notify: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Label("팝업 띄우기", 18, FontWeight.Bold)
            Label("다이얼로그는 누른 버튼 위치에서 커지며 열리고, 뒤쪽 화면은 흐려집니다.", 14)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("팝업", Modifier.glassOrigin(onOrigin), onClick = openDialog)
                Pill("시트", onClick = openSheet)
                Pill("알림", onClick = notify)
            }
        }
    }
}

@Composable
private fun DemoPopups(
    dialog: Boolean,
    sheet: Boolean,
    origin: Offset?,
    closeDialog: () -> Unit,
    closeSheet: () -> Unit,
    notify: (String) -> Unit,
) {
    GlassDialog(dialog, closeDialog, origin = origin, title = "주문 확인") {
        Label("주문을 보낼까요?", 20, FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Label("아메리카노 2잔 · 9,000원", 15)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("취소", Modifier.weight(1f), onClick = closeDialog)
            Pill("보내기", Modifier.weight(1f), filled = true) {
                closeDialog()
                notify("주문을 보냈어요")
            }
        }
    }
    GlassSheet(sheet, closeSheet, title = "공유") {
        Label("공유하기", 20, FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        // 입력란을 누르면 시트가 키보드 위로 올라온다(contentWindowInsets 기본값).
        var note by remember { mutableStateOf("") }
        GlassTextField(note, { note = it }, "함께 보낼 메모", Modifier.fillMaxWidth(), placeholder = "받는 사람에게 남길 말")
        Spacer(Modifier.height(8.dp))
        SHARE_TARGETS.forEach { (item, icon) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(ROW_SHAPE)
                    .glassClickable(ROW_SHAPE, onClick = closeSheet)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShowcaseIcon(icon, 22.dp)
                Label(item, 16, modifier = Modifier.padding(start = 14.dp))
            }
        }
    }
}

@Composable
private fun ControlsCard() {
    var view by remember { mutableIntStateOf(0) }
    var notify by remember { mutableStateOf(true) }
    var dark by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.6f) }
    var size by remember { mutableFloatStateOf(2f) }
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Label("컨트롤", 18, FontWeight.Bold)
            GlassSegmentedControl(listOf("일", "주", "월", "년"), view, { view = it }, Modifier.fillMaxWidth())
            SwitchRow("알림 받기", notify) { notify = it }
            SwitchRow("방해 금지", dark) { dark = it }
            Label("음량 ${(volume * 100).toInt()}%, 끝에서 더 당겨 보세요", 14)
            GlassSlider(volume, { volume = it })
            Label("글자 크기 ${size.toInt() + 1}단계", 14)
            GlassSlider(size, { size = it }, valueRange = 0f..4f, steps = 3)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(label, 16, modifier = Modifier.weight(1f))
        GlassSwitch(checked, onChange)
    }
}

private class StyleOption(val name: String, val light: GlassStyle, val dark: GlassStyle)

private val STYLES = listOf(
    StyleOption("Liquid", GlassStyles.Liquid, GlassStyles.LiquidDark),
    StyleOption("Acrylic", GlassStyles.Acrylic, GlassStyles.AcrylicDark),
    StyleOption("One UI", GlassStyles.OneUi, GlassStyles.OneUiDark),
    StyleOption("Vision", GlassStyles.Vision, GlassStyles.Vision),
    StyleOption("Material", GlassStyles.Material, GlassStyles.MaterialDark),
    StyleOption("Mica", GlassStyles.Mica, GlassStyles.MicaDark),
    StyleOption("HarmonyOS", GlassStyles.HarmonyOs, GlassStyles.HarmonyOs),
    StyleOption("Obsidian", GlassStyles.Obsidian, GlassStyles.Obsidian),
    StyleOption("Pastel", GlassStyles.Pastel, GlassStyles.Pastel),
    StyleOption("Frosted", GlassStyles.Frosted, GlassStyles.Frosted.copy(tint = Color.Black.copy(alpha = 0.35f), onGlass = Color.White)),
)

@Composable
private fun Intro(dark: Boolean) {
    val ink = if (dark) Color.White else Color(0xFF15161A)
    Column(Modifier.padding(top = 8.dp)) {
        BasicText("배경이 비치는 글래스", style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = ink))
        Spacer(Modifier.height(6.dp))
        BasicText(
            "GlassScaffold 하나만 쓰면 상단 바와 팝업, 시트, 토스트 뒤로 배경이 흐리게 비칩니다. 아래에서 스타일을 바꿔 보세요.",
            style = TextStyle(fontSize = 15.sp, color = ink.copy(alpha = 0.8f)),
        )
    }
}

@Composable
private fun Chips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label -> Pill(label, filled = i == selected) { onSelect(i) } }
    }
}

/** 사진 대신 쓰는 그라디언트 카드. 스크롤하면 글래스 바 뒤로 지나가며 비친다. */
@Composable
private fun Photo(colors: List<Color>, index: Int, notify: (String) -> Unit) {
    // 사진 위 글래스는 그 사진을 비춰야 하므로 사진마다 백드롭을 따로 둔다.
    val photo = rememberGlassBackdrop()
    val title = TITLES[index % TITLES.size]
    val menu = listOf(
        GlassMenuItem("공유", ShowcaseIcons.Cloud) { notify("$title 공유했어요") },
        GlassMenuItem("즐겨찾기", ShowcaseIcons.Sparkle) { notify("$title 즐겨찾기에 넣었어요") },
        GlassMenuItem("삭제", destructive = true) { notify("$title 삭제했어요") },
    )
    // 길게 누르면 사진이 떠오르고 배경이 흐려지며 메뉴가 열린다.
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.6f).glassContextMenu(menu).clip(RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.BottomStart,
    ) {
        Box(Modifier.matchParentSize().glassBackdrop(photo).background(Brush.linearGradient(colors)))
        GlassSurface(Modifier.padding(14.dp), shape = RoundedCornerShape(14.dp), backdrop = photo) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Label(TITLES[index % TITLES.size], 16, FontWeight.SemiBold)
                Label("길게 누르면 메뉴가 열려요", 13)
            }
        }
    }
}

private val ROW_SHAPE = RoundedCornerShape(14.dp)
private val SHARE_TARGETS = listOf(
    "메시지" to ShowcaseIcons.Message,
    "메일" to ShowcaseIcons.Mail,
    "링크 복사" to ShowcaseIcons.Link,
    "저장" to ShowcaseIcons.Download,
)
private val TITLES = listOf("제주 바다", "노을", "숲길", "도시의 밤", "라벤더")
private val PHOTOS = listOf(
    listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
    listOf(Color(0xFFFF7E5F), Color(0xFFFEB47B)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFF141E30), Color(0xFF8E54E9)),
    listOf(Color(0xFFB993D6), Color(0xFF8CA6DB)),
)
