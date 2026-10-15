// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.presets.EffectPresets
import com.naury.kinetica.render.kineticaEffects
import com.naury.kinetica.ui.ConfettiHost
import com.naury.kinetica.ui.GlassScaffold
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.RollingText
import com.naury.kinetica.ui.TypewriterText
import com.naury.kinetica.ui.glowBorder
import com.naury.kinetica.ui.magnetic
import com.naury.kinetica.ui.motionTilt
import com.naury.kinetica.ui.rememberConfettiState
import java.text.NumberFormat
import java.util.Locale
import kotlin.random.Random

/** 모션 효과: kinetica-ui의 고급 효과와 kinetica-render의 누름·나타남·반복 효과를 한곳에서 눌러 본다. */
@Composable
internal fun MotionDemo(onBack: () -> Unit) {
    var siri by remember { mutableStateOf(false) }
    val confetti = rememberConfettiState()
    GlassScaffold(
        Modifier.fillMaxSize().then(if (siri) Modifier.glowBorder(RectangleShape, width = 4.dp, glow = 28.dp) else Modifier),
        style = GlassStyles.OneUi,
        background = { Aurora(Modifier.fillMaxSize(), dark = false) },
        topBar = { DemoTopBar("모션 효과", "한 줄로 붙이는 움직임 — 눌러 보세요", onBack) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { GlowCard(siri) { siri = !siri } }
            item {
                DemoCard("젤리 누름 · 자석", "Modifier.jellyPress · Modifier.magnetic — 누르면 납작해지고, 끌면 손가락을 따라온다.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Pill("젤리 버튼", filled = true) {}
                        Box(
                            Modifier.magnetic(0.35f).size(width = 140.dp, height = 46.dp).background(Color(0xFF15161A), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Label("자석 버튼", 14, FontWeight.SemiBold, color = Color.White) }
                    }
                }
            }
            item {
                DemoCard("기울이면 3D", "Modifier.motionTilt — 휴대폰을 기울이면 카드가 따라 기울고 빛이 흐른다(실기기).") {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1.7f).motionTilt(12f, 0.35f).clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF8E54E9), Color(0xFF4776E6), Color(0xFF00C6FF)))),
                        contentAlignment = Alignment.BottomStart,
                    ) { Label("Kinetica Card", 22, FontWeight.Bold, Modifier.padding(20.dp), Color.White) }
                }
            }
            item { RollingCard() }
            item { TypewriterCard() }
            item {
                var origin by remember { mutableStateOf(Offset.Zero) }
                DemoCard("색종이", "ConfettiHost — 결제·달성 순간에 터뜨린다.") {
                    Pill(
                        "축하하기",
                        Modifier.onGloballyPositioned {
                            origin = it.boundsInRoot().center
                        },
                        filled = true,
                    ) { confetti.burst(origin) }
                }
            }
            item { PresetsCard() }
        }
        ConfettiHost(confetti)
    }
}

@Composable
private fun GlowCard(on: Boolean, toggle: () -> Unit) {
    DemoCard("빛 테두리", "Modifier.glowBorder — Siri처럼 가장자리를 도는 빛. 화면 전체에도 붙인다.") {
        Box(
            Modifier.fillMaxWidth().height(
                90.dp,
            ).glowBorder(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) { Label("AI가 답을 쓰는 중…", 15, FontWeight.Medium) }
        Pill(if (on) "화면 빛 끄기" else "화면 가장자리에 켜기", filled = on, onClick = toggle)
    }
}

@Composable
private fun RollingCard() {
    var sales by remember { mutableLongStateOf(1_284_000L) }
    DemoCard("숫자 굴리기", "RollingText — 바뀐 자리만 위아래로 굴러간다(주행 거리계).") {
        RollingText(
            NumberFormat.getNumberInstance(Locale.KOREA).format(sales) + "원",
            style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15161A)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("+ 주문") { sales += Random.nextLong(3_000, 60_000) }
            Pill("− 환불") { sales = (sales - Random.nextLong(3_000, 30_000)).coerceAtLeast(0) }
        }
    }
}

@Composable
private fun TypewriterCard() {
    var round by remember { mutableIntStateOf(0) }
    val lines = listOf("오늘 매출이 어제보다 12% 늘었어요.", "가장 많이 팔린 메뉴는 아메리카노예요.", "오후 3시에 손님이 가장 많아요.")
    DemoCard("타자기", "TypewriterText — 한 글자씩 써 내려가는 안내·AI 답변.") {
        TypewriterText(lines[round % lines.size], style = TextStyle(fontSize = 17.sp, color = Color(0xFF15161A)))
        Pill("다음 문장") { round++ }
    }
}

@Composable
private fun PresetsCard() {
    var taps by remember { mutableIntStateOf(0) }
    DemoCard("누름 · 나타남 · 반복", "Modifier.kineticaEffects(EffectPresets.…) — 호스트 뷰 어디에나 한 줄. 눌린 수 $taps") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PRESETS.forEach { (name, label, color) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(width = 96.dp, height = 56.dp)
                            .kineticaEffects(EffectPresets.all.getValue(name), RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(color)
                            .clickable { taps++ },
                    )
                    Label(label, 11, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

private val PRESETS = listOf(
    Triple("button", "리플+눌림", Color(0xFF5B6CFF)),
    Triple("like", "좋아요 터지기", Color(0xFFFF5C8A)),
    Triple("liftCard", "떠오르기", Color.White),
    Triple("callToAction", "빛 번짐", Color(0xFFFF7A59)),
    Triple("heartbeat", "콩닥", Color(0xFFE5484D)),
    Triple("live", "실시간 고리", Color(0xFF22C55E)),
    Triple("attention", "흔들기", Color(0xFFFFD166)),
    Triple("floating", "둥실", Color(0xFF34C3A0)),
    Triple("skeleton", "반짝임", Color(0xFFE5E7EB)),
)
