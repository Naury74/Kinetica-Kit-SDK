// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KDialog
import com.naury.kinetica.ui.KMenuItem
import com.naury.kinetica.ui.KRefreshBox
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KSheet
import com.naury.kinetica.ui.KSlider
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KSwitch
import com.naury.kinetica.ui.KTab
import com.naury.kinetica.ui.KTabBar
import com.naury.kinetica.ui.KTextField
import com.naury.kinetica.ui.KToastHost
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.SurfaceStyle
import com.naury.kinetica.ui.glassBackdrop
import com.naury.kinetica.ui.kClickable
import com.naury.kinetica.ui.kContextMenu
import com.naury.kinetica.ui.kOrigin
import com.naury.kinetica.ui.rememberGlassBackdrop
import com.naury.kinetica.ui.rememberKToastState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 표면과 디자인 데모. 같은 컴포넌트를 프리셋마다 나란히 그려 비교하고, 누른 프리셋을 앱 전체 모양으로 바로 적용한다.
 * 고른 값은 다른 모든 화면과 같은 [ShowcaseLook]에 들어가므로 이 화면에만 따로 있는 선택은 없다.
 * 아래쪽에서는 지금 모양으로 다이얼로그, 바텀 시트, 토스트, 탭 바, 컨트롤을 직접 눌러 본다.
 */
@Composable
internal fun UiKitDemo(onBack: () -> Unit) {
    val look = LocalShowcaseLook.current
    val dark = look.isDark(defaultDark = false)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dialog by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var origin by remember { mutableStateOf<Offset?>(null) }
    val toast = rememberKToastState()
    val scope = rememberCoroutineScope()
    KineticaScaffold(
        Modifier.fillMaxSize(),
        surface = demoSurface(UI_KIT_LOOK),
        background = { DemoBackdrop() },
        topBar = { DemoTopBar("표면과 디자인", look.summary(), onBack, demoContentWidth()) },
        bottomBar = {
            KTabBar(
                listOf(
                    KTab("홈", ShowcaseIcons.Home),
                    KTab("효과", ShowcaseIcons.Sparkle),
                    KTab("보드", ShowcaseIcons.Grid),
                    KTab("알림", ShowcaseIcons.Bell),
                ),
                tab,
                { tab = it },
            )
        },
    ) { padding ->
        // 맨 위에서 당기면 글래스 인디케이터가 물방울처럼 차오르고, 놓으면 새로 고침한다.
        DemoRefresh(padding.calculateTopPadding(), { toast.show("새로 고쳤어요") }) {
            DemoCardList(padding.calculateTopPadding() + 8.dp, padding.calculateBottomPadding() + 16.dp) {
                item(span = StaggeredGridItemSpan.FullLine) { Intro(look.family) }
                SurfaceFamily.entries.forEach { family ->
                    item(span = StaggeredGridItemSpan.FullLine, key = family.name) { PresetRow(family, dark) }
                }
                item { PopupCard({ origin = it }, { dialog = true }, { sheet = true }) { scope.launch { toast.show("저장했어요") } } }
                item { ControlsCard() }
                items(PHOTOS.size) { index -> Photo(PHOTOS[index], index) { scope.launch { toast.show(it) } } }
            }
        }
        DemoPopups(dialog, sheet, origin, { dialog = false }, { sheet = false }) { scope.launch { toast.show(it) } }
        KToastHost(toast)
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
    KRefreshBox(refreshing, { refreshing = true }, Modifier.fillMaxSize(), indicatorTop = top) { content() }
}

@Composable
private fun PopupCard(onOrigin: (Offset) -> Unit, openDialog: () -> Unit, openSheet: () -> Unit, notify: () -> Unit) {
    KSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Label("팝업 띄우기", 18, FontWeight.Bold)
            Label("다이얼로그는 누른 버튼 위치에서 커지며 열리고, 뒤쪽 화면은 흐려집니다.", 14)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("팝업", Modifier.kOrigin(onOrigin), onClick = openDialog)
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
    KDialog(dialog, closeDialog, origin = origin, title = "주문 확인") {
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
    KSheet(sheet, closeSheet, title = "공유") {
        Label("공유하기", 20, FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        // 입력란을 누르면 시트가 키보드 위로 올라온다(contentWindowInsets 기본값).
        var note by rememberSaveable { mutableStateOf("") }
        KTextField(note, { note = it }, "함께 보낼 메모", Modifier.fillMaxWidth(), placeholder = "받는 사람에게 남길 말")
        Spacer(Modifier.height(8.dp))
        SHARE_TARGETS.forEach { (item, icon) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(ROW_SHAPE)
                    .kClickable(ROW_SHAPE, onClick = closeSheet)
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
    var view by rememberSaveable { mutableIntStateOf(0) }
    var notify by rememberSaveable { mutableStateOf(true) }
    var dark by rememberSaveable { mutableStateOf(false) }
    var volume by rememberSaveable { mutableFloatStateOf(0.6f) }
    var size by rememberSaveable { mutableFloatStateOf(2f) }
    KSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Label("컨트롤", 18, FontWeight.Bold)
            KSegmentedControl(listOf("일", "주", "월", "년"), view, { view = it }, Modifier.fillMaxWidth())
            SwitchRow("알림 받기", notify) { notify = it }
            SwitchRow("방해 금지", dark) { dark = it }
            Label("음량 ${(volume * 100).toInt()}%, 끝에서 더 당겨 보세요", 14)
            KSlider(volume, { volume = it })
            Label("글자 크기 ${size.toInt() + 1}단계", 14)
            KSlider(size, { size = it }, valueRange = 0f..4f, steps = 3)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(label, 16, modifier = Modifier.weight(1f))
        KSwitch(checked, onChange)
    }
}

@Composable
private fun Intro(family: SurfaceFamily) {
    val ink = LocalKineticaContentColor.current
    val (title, body) = when (family) {
        SurfaceFamily.Glass ->
            "배경이 비치는 글래스" to
                "KineticaScaffold 하나만 쓰면 상단 바와 팝업, 시트, 토스트 뒤로 배경이 흐리게 비칩니다. 아래 프리셋을 누르면 모든 화면에 바로 적용됩니다."

        SurfaceFamily.Solid ->
            "깔끔한 단색 표면" to
                "같은 컴포넌트를 블러 없이 불투명한 표면으로 그립니다. 배경을 기록하지 않아 그리기 비용이 거의 없습니다."

        SurfaceFamily.Tonal ->
            "강조색을 섞은 톤 표면" to
                "앱 강조색을 옅게 섞은 표면입니다. SurfaceStyles.tonal(색)으로 앱 색에 맞출 수 있습니다."
    }
    Column(Modifier.padding(top = 8.dp)) {
        Label(title, 30, FontWeight.Bold, color = ink)
        Spacer(Modifier.height(6.dp))
        Label(body, 15, color = ink.copy(alpha = 0.8f))
    }
}

/** [family]의 프리셋을 같은 컴포넌트로 나란히 그린 줄. 누르면 그 프리셋이 앱 전체 모양이 된다. */
@Composable
private fun PresetRow(family: SurfaceFamily, dark: Boolean) {
    val look = LocalShowcaseLook.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("${family.label} 프리셋", 17, FontWeight.Bold)
        // 카드 목록의 여백 밖까지 이어지게 그리면 넓은 화면에서 줄 끝이 들쭉날쭉해지므로 목록 폭 안에서 가로로 스크롤한다.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val presets = presetsOf(family)
            items(presets.size, key = { presets[it].name }) { index ->
                val selected = look.family == family && look.preset == index
                PresetTile(presets[index], dark, selected) { look.select(family, index) }
            }
        }
    }
}

/** 프리셋 하나로 그린 작은 화면. 글래스는 비칠 그라디언트를 깔고, 단색과 톤은 그 표면의 바탕색을 깐다. */
@Composable
private fun PresetTile(preset: SurfacePreset, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    val surface = preset.surface(dark)
    val backdrop = rememberGlassBackdrop()
    val ring = LocalKineticaContentColor.current
    Box(
        Modifier
            .width(PRESET_TILE_WIDTH)
            .clip(PRESET_TILE_SHAPE)
            .border(if (selected) 2.5.dp else 0.dp, if (selected) ring else Color.Transparent, PRESET_TILE_SHAPE)
            .kClickable(PRESET_TILE_SHAPE, onClick = onClick)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            },
    ) {
        Box(
            Modifier.matchParentSize().glassBackdrop(backdrop).then(
                if (surface is SurfaceStyle.Glass) {
                    Modifier.background(Brush.linearGradient(if (surface.isDark) TILE_DARK else TILE_LIGHT))
                } else {
                    Modifier.background(surface.background)
                },
            ),
        )
        KineticaTheme(surface = surface) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 바깥 24dp 모서리에서 여백 10dp를 빼 안쪽 카드가 같은 중심으로 굽는다.
                KSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), backdrop = backdrop) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Label(preset.name, 15, FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Label("알림", 13, modifier = Modifier.weight(1f))
                            KSwitch(true, null)
                        }
                    }
                }
                KSurface(Modifier.fillMaxWidth().height(36.dp), shape = CircleShape, backdrop = backdrop) {
                    Label(if (selected) "사용 중" else "적용", 13, FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

/** 사진 대신 쓰는 그라디언트 카드. 스크롤하면 글래스 바 뒤로 지나가며 비친다. */
@Composable
private fun Photo(colors: List<Color>, index: Int, notify: (String) -> Unit) {
    // 사진 위 글래스는 그 사진을 비춰야 하므로 사진마다 백드롭을 따로 둔다.
    val photo = rememberGlassBackdrop()
    val title = TITLES[index % TITLES.size]
    val menu = listOf(
        KMenuItem("공유", ShowcaseIcons.Cloud) { notify("$title 공유했어요") },
        KMenuItem("즐겨찾기", ShowcaseIcons.Sparkle) { notify("$title 즐겨찾기에 넣었어요") },
        KMenuItem("삭제", destructive = true) { notify("$title 삭제했어요") },
    )
    // 길게 누르면 사진이 떠오르고 배경이 흐려지며 메뉴가 열린다.
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.6f).kContextMenu(menu).clip(RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.BottomStart,
    ) {
        Box(Modifier.matchParentSize().glassBackdrop(photo).background(Brush.linearGradient(colors)))
        KSurface(Modifier.padding(14.dp), shape = RoundedCornerShape(14.dp), backdrop = photo) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Label(TITLES[index % TITLES.size], 16, FontWeight.SemiBold)
                Label("길게 누르면 메뉴가 열려요", 13)
            }
        }
    }
}

private val ROW_SHAPE = RoundedCornerShape(14.dp)
private val UI_KIT_LOOK = DemoLook(GlassStyles.Liquid, GlassStyles.LiquidDark, defaultDark = false)
private val PRESET_TILE_WIDTH = 150.dp
private val PRESET_TILE_SHAPE = RoundedCornerShape(24.dp)
private val TILE_LIGHT = listOf(Color(0xFF8EC5FF), Color(0xFFFFB3D9), Color(0xFFFFE29A))
private val TILE_DARK = listOf(Color(0xFF1B2A6B), Color(0xFF5B2A86), Color(0xFF0E5E6F))
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
