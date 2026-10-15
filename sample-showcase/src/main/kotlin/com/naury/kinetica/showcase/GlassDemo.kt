// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.ui.GlassDialog
import com.naury.kinetica.ui.GlassScaffold
import com.naury.kinetica.ui.GlassSheet
import com.naury.kinetica.ui.GlassStyle
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.GlassSurface
import com.naury.kinetica.ui.GlassTab
import com.naury.kinetica.ui.GlassTabBar
import com.naury.kinetica.ui.GlassToastHost
import com.naury.kinetica.ui.LocalGlassContentColor
import com.naury.kinetica.ui.glassBackdrop
import com.naury.kinetica.ui.glassOrigin
import com.naury.kinetica.ui.rememberGlassBackdrop
import com.naury.kinetica.ui.rememberGlassToastState
import kotlinx.coroutines.launch

/** 글래스 UI 키트: 스타일을 바꿔 가며 팝업·시트·알림·탭 막대가 뒤 화면을 어떻게 비추는지 본다. */
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
        topBar = { DemoTopBar("글래스 UI · ${option.name}", "뒤를 흐리게 비추는 유리 표면·팝업·시트·알림", onBack) },
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
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
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
                Chips(listOf("밝게", "어둡게"), if (dark) 1 else 0) { dark = it == 1 }
            }
            item { PopupCard({ origin = it }, { dialog = true }, { sheet = true }) { scope.launch { toast.show("저장했어요") } } }
            items(PHOTOS.size) { index -> Photo(PHOTOS[index], index) }
        }
        DemoPopups(dialog, sheet, origin, { dialog = false }, { sheet = false }) { scope.launch { toast.show(it) } }
        GlassToastHost(toast)
    }
}

@Composable
private fun PopupCard(onOrigin: (Offset) -> Unit, openDialog: () -> Unit, openSheet: () -> Unit, notify: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Label("팝업 열기", 18, FontWeight.Bold)
            Label("누른 버튼에서 커져 나오고 뒤 화면이 흐려집니다.", 14)
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("취소", onClick = closeDialog)
            Pill("보내기", filled = true) {
                closeDialog()
                notify("주문을 보냈어요")
            }
        }
    }
    GlassSheet(sheet, closeSheet, title = "공유") {
        Label("공유하기", 20, FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        listOf("메시지", "메일", "링크 복사", "저장").forEach { item ->
            Box(Modifier.fillMaxWidth().clickable(onClick = closeSheet).padding(vertical = 14.dp)) { Label(item, 16) }
        }
    }
}

private class StyleOption(val name: String, val light: GlassStyle, val dark: GlassStyle)

private val STYLES = listOf(
    StyleOption("Liquid", GlassStyles.Liquid, GlassStyles.LiquidDark),
    StyleOption("Acrylic", GlassStyles.Acrylic, GlassStyles.AcrylicDark),
    StyleOption("One UI", GlassStyles.OneUi, GlassStyles.OneUiDark),
    StyleOption("Vision", GlassStyles.Vision, GlassStyles.Vision),
)

@Composable
private fun Intro(dark: Boolean) {
    val ink = if (dark) Color.White else Color(0xFF15161A)
    Column(Modifier.padding(top = 8.dp)) {
        BasicText("뒤를 비추는 유리", style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = ink))
        Spacer(Modifier.height(6.dp))
        BasicText(
            "GlassScaffold 하나로 막대·팝업·시트·알림이 뒤 화면을 흐리게 비춥니다. 스타일만 바꿔 보세요.",
            style = TextStyle(fontSize = 15.sp, color = ink.copy(alpha = 0.8f)),
        )
    }
}

@Composable
private fun Chips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label -> Pill(label, filled = i == selected) { onSelect(i) } }
    }
}

/** 사진 대신 색 카드. 유리 막대 뒤로 지나가며 비친다. */
@Composable
private fun Photo(colors: List<Color>, index: Int) {
    // 사진 위 유리는 그 사진을 비춰야 하므로 사진에 따로 뒤 화면을 둔다.
    val photo = rememberGlassBackdrop()
    Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(28.dp)), contentAlignment = Alignment.BottomStart) {
        Box(Modifier.matchParentSize().glassBackdrop(photo).background(Brush.linearGradient(colors)))
        GlassSurface(Modifier.padding(14.dp), shape = RoundedCornerShape(18.dp), backdrop = photo) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Label(TITLES[index % TITLES.size], 16, FontWeight.SemiBold)
                Label("사진 위 유리", 13)
            }
        }
    }
}

private val TITLES = listOf("제주 바다", "노을", "숲길", "도시의 밤", "라벤더")
private val PHOTOS = listOf(
    listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
    listOf(Color(0xFFFF7E5F), Color(0xFFFEB47B)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFF141E30), Color(0xFF8E54E9)),
    listOf(Color(0xFFB993D6), Color(0xFF8CA6DB)),
)
