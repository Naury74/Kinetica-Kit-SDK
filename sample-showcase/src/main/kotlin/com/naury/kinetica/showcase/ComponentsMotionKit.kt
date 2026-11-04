// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.ui.KAnimatedBeam
import com.naury.kinetica.ui.KBeam
import com.naury.kinetica.ui.KBlurTextReveal
import com.naury.kinetica.ui.KBubblePicker
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KDock
import com.naury.kinetica.ui.KDockItem
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KMarquee
import com.naury.kinetica.ui.KMarqueeDirection
import com.naury.kinetica.ui.KOrbit
import com.naury.kinetica.ui.KOrbitingIcons
import com.naury.kinetica.ui.KSwitch
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalKineticaContentColor
import kotlinx.coroutines.delay

/** 움직임 묶음: 흐림 글, 거품 선택기, 마키, 빛줄기, 독, 궤도 아이콘. */
@Composable
internal fun MotionDemos() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BlurRevealCard()
        BubbleCard()
        MarqueeCard()
        BeamCard()
        DockCard()
        OrbitCard()
    }
}

private const val ANSWER = "오늘 서울은 오후부터 구름이 많아지고 저녁에는 비가 올 수 있어요. 우산을 챙기고, 퇴근길에는 대중교통을 타는 편이 좋겠어요."

@Composable
private fun BlurRevealCard() {
    var run by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(run) {
        // 답변이 몇 글자씩 스트리밍되는 모습을 흉내 낸다.
        shown = ""
        var at = 0
        while (at < ANSWER.length) {
            at = (at + 4).coerceAtMost(ANSWER.length)
            shown = ANSWER.take(at)
            delay(90)
        }
    }
    DemoCard("흐림 글", "단어마다 블러가 풀리며 떠오릅니다. 글이 스트리밍으로 늘어나도 새 단어만 이어서 나타납니다.") {
        KBlurTextReveal(shown, Modifier.fillMaxWidth().height(96.dp), style = TextStyle(fontSize = 17.sp, lineHeight = 26.sp))
        KButton("다시 받기", { run++ }, variant = KVariant.Tonal)
    }
}

private val GENRES = listOf("팝", "재즈", "힙합", "록", "클래식", "R&B", "인디", "일렉트로닉", "발라드", "K-팝", "어쿠스틱", "로파이")

@Composable
private fun BubbleCard() {
    var picked by rememberSaveable { mutableStateOf(setOf("재즈", "인디")) }
    DemoCard("거품 선택기", "좋아하는 장르를 눌러 보세요. 고른 거품이 커지며 둘레 거품을 밀어냅니다. 여러 개를 고를 수 있습니다.") {
        KBubblePicker(GENRES, picked, { picked = it }, Modifier.fillMaxWidth().height(380.dp))
        Label("고른 장르: " + picked.joinToString(", ").ifEmpty { "없음" }, 13)
    }
}

private val TAGS = listOf("디자인", "모션", "글래스", "접근성", "RTL", "폴더블", "태블릿", "TalkBack", "성능", "테마")

@Composable
private fun MarqueeCard() {
    var fade by rememberSaveable { mutableStateOf(true) }
    DemoCard("마키", "내용이 끝없이 흐릅니다. 두 줄은 반대로 흐르고, 누르고 있으면 멈춥니다.") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("끝 흐림", 14, modifier = Modifier.weight(1f))
            KSwitch(fade, { fade = it })
        }
        val edge = if (fade) 40.dp else 0.dp
        KMarquee(Modifier.fillMaxWidth(), speed = 36.dp, edgeFade = edge, pauseOnTouch = true) { TagRow(TAGS) }
        KMarquee(Modifier.fillMaxWidth(), direction = KMarqueeDirection.End, speed = 28.dp, edgeFade = edge, pauseOnTouch = true) {
            TagRow(TAGS.reversed())
        }
    }
}

@Composable
private fun TagRow(tags: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { tag ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(LocalKineticaContentColor.current.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Label(tag, 14, FontWeight.Medium) }
        }
    }
}

@Composable
private fun BeamCard() {
    val beams = listOf(
        KBeam("mail", "hub", curvature = 36.dp),
        KBeam("chat", "hub"),
        KBeam("cal", "hub", curvature = (-36).dp, delayMs = 400),
        KBeam("hub", "phone", delayMs = 800),
    )
    DemoCard("빛줄기", "여러 서비스가 가운데로 모이고 휴대전화로 이어지는 흐름을 빛이 따라갑니다.") {
        KAnimatedBeam(beams, Modifier.fillMaxWidth().height(200.dp)) {
            // Column 안에서는 바깥 범위의 수정자를 바로 부를 수 없어 미리 만든다.
            val mail = Modifier.beamAnchor("mail")
            val chat = Modifier.beamAnchor("chat")
            val cal = Modifier.beamAnchor("cal")
            Column(Modifier.align(Alignment.CenterStart).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                BeamNode(KIcons.Mail, mail)
                BeamNode(KIcons.Message, chat)
                BeamNode(KIcons.Calendar, cal)
            }
            BeamNode(KIcons.Grid, Modifier.align(Alignment.Center).beamAnchor("hub"), size = 64.dp)
            BeamNode(KIcons.Call, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).beamAnchor("phone"))
        }
    }
}

@Composable
private fun BeamNode(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(KineticaTheme.surface.contentColor.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) { ShowcaseIcon(icon) }
}

private class DockApp(val name: String, val icon: ImageVector, val color: Color)

private val DOCK_APPS = listOf(
    DockApp("홈", KIcons.Home, Color(0xFF2A78D6)),
    DockApp("메일", KIcons.Mail, Color(0xFF1BAF7A)),
    DockApp("사진", KIcons.Image, Color(0xFFEB6834)),
    DockApp("음악", KIcons.Heart, Color(0xFFE34948)),
    DockApp("달력", KIcons.Calendar, Color(0xFF6250D6)),
    DockApp("지도", KIcons.Location, Color(0xFF008300)),
    DockApp("설정", KIcons.Settings, Color(0xFF6B7280)),
)

@Composable
private fun DockCard() {
    var running by rememberSaveable { mutableStateOf(setOf("메일")) }
    var last by rememberSaveable { mutableStateOf("") }
    DemoCard("독", "아이콘 위를 손가락으로 훑으면 가까운 아이콘이 커지고, 뗀 곳의 앱이 실행됩니다. 태블릿에서는 마우스를 올려 보세요.") {
        KDock(
            DOCK_APPS.map { app ->
                KDockItem(app.name, app.name in running, onClick = {
                    running = running + app.name
                    last = app.name
                }) { DockTile(app) }
            },
            Modifier.align(Alignment.CenterHorizontally),
            iconSize = 44.dp,
        )
        Label(if (last.isEmpty()) "실행한 앱이 없어요" else "$last 실행", 13)
    }
}

@Composable
private fun DockTile(app: DockApp) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24)).background(app.color), contentAlignment = Alignment.Center) {
        ShowcaseIcon(app.icon, color = Color.White)
    }
}

@Composable
private fun OrbitCard() {
    val inner = listOf(KIcons.Mail, KIcons.Message, KIcons.Calendar).map { orbitIcon(it, 36.dp) }
    val outer = listOf(KIcons.Camera, KIcons.Image, KIcons.Location, KIcons.Bell, KIcons.Star).map { orbitIcon(it, 40.dp) }
    DemoCard("궤도 아이콘", "가운데 로고 둘레를 아이콘들이 서로 다른 빠르기와 방향으로 돕니다.") {
        KOrbitingIcons(
            listOf(KOrbit(64.dp, periodMs = 14_000, icons = inner), KOrbit(120.dp, periodMs = 26_000, reverse = true, icons = outer)),
            Modifier.align(Alignment.CenterHorizontally),
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(KineticaTheme.colors.accent),
                contentAlignment = Alignment.Center,
            ) { Label("K", 28, FontWeight.Black, color = KineticaTheme.colors.onAccent) }
        }
    }
}

private fun orbitIcon(icon: ImageVector, size: Dp): @Composable () -> Unit = {
    Box(
        Modifier.size(size).clip(CircleShape).background(KineticaTheme.surface.contentColor.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) { ShowcaseIcon(icon, size = size * 0.5f) }
}
