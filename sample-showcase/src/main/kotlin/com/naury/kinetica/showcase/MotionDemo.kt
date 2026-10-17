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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.LocalKineticaSurface
import com.naury.kinetica.ui.RollingText
import com.naury.kinetica.ui.SurfaceStyles
import com.naury.kinetica.ui.TypewriterText
import com.naury.kinetica.ui.glowBorder
import com.naury.kinetica.ui.kClickable
import com.naury.kinetica.ui.magnetic
import com.naury.kinetica.ui.motionTilt
import com.naury.kinetica.ui.rememberConfettiState
import java.text.NumberFormat
import java.util.Locale
import kotlin.random.Random

/** 모션 효과 데모. kinetica-ui의 효과와 kinetica-render의 누름, 등장, 반복 프리셋을 한 화면에서 눌러 본다. */
@Composable
internal fun MotionDemo(onBack: () -> Unit) {
    var siri by remember { mutableStateOf(false) }
    val confetti = rememberConfettiState()
    KineticaScaffold(
        Modifier.fillMaxSize().then(if (siri) Modifier.glowBorder(RectangleShape, width = 4.dp, glow = 28.dp) else Modifier),
        surface = demoSurface(MOTION_DEMO_LOOK),
        background = { DemoBackdrop() },
        topBar = { DemoTopBar("모션 효과", "Modifier 한 줄로 붙이는 애니메이션", onBack, demoContentWidth()) },
    ) { padding ->
        DemoCardList(padding.calculateTopPadding() + 8.dp, navigationBarBottom() + 40.dp) {
            item { GlowCard(siri) { siri = !siri } }
            item { JellyCard() }
            item {
                DemoCard("기울이는 3D 카드", "Modifier.motionTilt입니다. 휴대폰을 기울이면 카드가 함께 기울고 빛이 흐릅니다. 실기기에서 확인해 보세요.") {
                    TiltCard()
                }
            }
            item { RollingCard() }
            item { TypewriterCard() }
            item {
                var origin by remember { mutableStateOf(Offset.Zero) }
                DemoCard("컨페티", "ConfettiHost입니다. 결제를 마치거나 목표를 달성한 순간에 터뜨려 보세요.") {
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
private fun JellyCard() {
    DemoCard("젤리 버튼과 자석 버튼", "Modifier.jellyPress와 Modifier.magnetic입니다. 누르면 젤리처럼 납작해지고, 끌면 손가락을 따라옵니다.") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill("젤리 버튼", Modifier.heightIn(min = 44.dp), filled = true) {}
            Box(
                Modifier.magnetic(
                    0.35f,
                ).heightIn(
                    min = 44.dp,
                ).background(LocalKineticaContentColor.current, CircleShape).padding(horizontal = 22.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Label(
                    "자석 버튼",
                    14,
                    FontWeight.SemiBold,
                    color = if (LocalKineticaContentColor.current.isLight()) Color.Black else Color.White,
                )
            }
        }
    }
}

@Composable
private fun GlowCard(on: Boolean, toggle: () -> Unit) {
    DemoCard("글로우 테두리", "Modifier.glowBorder입니다. Siri처럼 가장자리를 따라 빛이 돌고, 화면 전체에도 붙일 수 있습니다.") {
        Box(
            Modifier.fillMaxWidth().height(
                90.dp,
            ).glowBorder(innerShape).background(LocalKineticaSurface.current.background.copy(alpha = 0.6f), innerShape),
            contentAlignment = Alignment.Center,
        ) { Label("AI가 답변을 쓰고 있어요…", 15, FontWeight.Medium) }
        Pill(if (on) "화면 글로우 끄기" else "화면 전체에 켜기", filled = on, onClick = toggle)
    }
}

@Composable
private fun RollingCard() {
    var sales by rememberSaveable { mutableLongStateOf(1_284_000L) }
    DemoCard("숫자 롤링", "RollingText입니다. 주행 거리계처럼 바뀐 자릿수만 위아래로 굴러갑니다.") {
        RollingText(
            NumberFormat.getNumberInstance(Locale.KOREA).format(sales) + "원",
            style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = LocalKineticaContentColor.current),
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
    DemoCard("타자기 효과", "TypewriterText입니다. 안내 문구나 AI 답변을 한 글자씩 써 내려갑니다.") {
        TypewriterText(lines[round % lines.size], style = TextStyle(fontSize = 17.sp, color = LocalKineticaContentColor.current))
        Pill("다음 문장") { round++ }
    }
}

@Composable
private fun PresetsCard() {
    var taps by rememberSaveable { mutableIntStateOf(0) }
    DemoCard("프리셋 효과", "Modifier.kineticaEffects(EffectPresets.…) 한 줄로 어떤 뷰에든 누름, 등장, 반복 애니메이션을 붙입니다. 누른 횟수 $taps") {
        // 세 칸씩 나눠 카드 폭을 고르게 채운다.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PRESETS.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (name, label, color) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.fillMaxWidth().height(56.dp)
                                    .kineticaEffects(EffectPresets.all.getValue(name), innerShape)
                                    .clip(innerShape)
                                    .background(color)
                                    .kClickable(innerShape) { taps++ },
                            )
                            Label(label, 12, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

private val PRESETS = listOf(
    Triple("button", "리플", Color(0xFF5B6CFF)),
    Triple("like", "좋아요 터지기", Color(0xFFFF5C8A)),
    Triple("liftCard", "떠오르기", Color.White),
    Triple("callToAction", "글로우", Color(0xFFFF7A59)),
    Triple("heartbeat", "두근두근", Color(0xFFE5484D)),
    Triple("live", "라이브 링", Color(0xFF22C55E)),
    Triple("attention", "흔들기", Color(0xFFFFD166)),
    Triple("floating", "둥실", Color(0xFF34C3A0)),
    Triple("skeleton", "스켈레톤", Color(0xFFE5E7EB)),
)

/** 신용카드 비율의 카드. 넓은 화면에서 지나치게 커지지 않도록 폭을 360dp로 묶고 가운데 둔다. */
@Composable
private fun TiltCard() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .aspectRatio(1.586f)
                .motionTilt(12f, 0.35f)
                .clip(innerShape)
                .background(Brush.linearGradient(listOf(Color(0xFF8E54E9), Color(0xFF4776E6), Color(0xFF00C6FF))))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Kinetica", 18, FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                ShowcaseIcon(ShowcaseIcons.Wave, 22.dp, Color.White.copy(alpha = 0.85f))
            }
            // IC 칩 자리. 실물 카드 칩을 흉내 낸 것이라 형태 설정을 따르지 않는다.
            Box(
                Modifier
                    .padding(top = 18.dp)
                    .size(40.dp, 30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFF6E7B0), Color(0xFFD9B969)))),
            )
            Spacer(Modifier.weight(1f))
            Label("1234  5678  9012  2048", 17, FontWeight.Medium, color = Color.White)
            Row(Modifier.padding(top = 6.dp)) {
                Label("KIM KINETICA", 12, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
                Label("12/30", 12, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

private val MOTION_DEMO_LOOK =
    DemoLook(
        GlassStyles.OneUi,
        GlassStyles.OneUiDark,
        defaultDark = false,
        solidLight = SurfaceStyles.OneUi,
        solidDark = SurfaceStyles.OneUiDark,
    )
