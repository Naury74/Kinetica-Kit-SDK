// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KCircularRangeSlider
import com.naury.kinetica.ui.KCircularSlider
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KMorphShape
import com.naury.kinetica.ui.KScratchCard
import com.naury.kinetica.ui.KShapeMorphButton
import com.naury.kinetica.ui.KShapeToggle
import com.naury.kinetica.ui.KStackedCards
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KWavyProgressBar
import com.naury.kinetica.ui.KWavyProgressRing
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.rememberKScratchCardState
import com.naury.kinetica.ui.rememberKStackedCardsState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** 컨트롤 묶음 데모: 물결 진행 표시, 원형 슬라이더, 모양 바뀜 버튼, 쌓인 카드, 긁는 카드. */
internal fun LazyListScope.controlsDemos() {
    item { WavyProgressCard() }
    item { CircularSliderCard() }
    item { SleepRangeCard() }
    item { ShapeMorphCard() }
    item { StackedCardsCard() }
    item { ScratchCardCard() }
}

@Composable
private fun WavyProgressCard() {
    var running by rememberSaveable { mutableStateOf(true) }
    var target by rememberSaveable { mutableFloatStateOf(0.1f) }
    LaunchedEffect(running) {
        while (running) {
            delay(700)
            target = if (target >= 1f) 0f else (target + 0.08f).coerceAtMost(1f)
        }
    }
    val progress by animateFloatAsState(target, tween(600), label = "download")
    DemoCard(
        "물결 진행 표시",
        "받는 동안에는 진행된 부분이 물결치고, 멈추면 1.5초 뒤 물결이 잦아들어 평평해집니다. 멈춤을 눌러 보세요.",
    ) {
        KWavyProgressBar({ progress }, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                KWavyProgressRing({ progress }, Modifier.size(64.dp))
                Label("${(progress * 100).roundToInt()}%", 13, FontWeight.SemiBold)
            }
            KWavyProgressRing(Modifier.size(48.dp))
            KButton(if (running) "멈춤" else "이어 받기", { running = !running }, variant = KVariant.Tonal)
        }
        Label("진행 정도를 모를 때", 13, color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
        KWavyProgressBar(Modifier.fillMaxWidth())
    }
}

@Composable
private fun CircularSliderCard() {
    var minutes by rememberSaveable { mutableFloatStateOf(25f) }
    DemoCard("원형 슬라이더", "손잡이나 트랙을 잡고 돌리면 5분 눈금마다 햅틱이 옵니다. 60분 끝에서는 더 돌려도 0으로 넘어가지 않습니다.") {
        KCircularSlider(
            minutes,
            { minutes = it },
            "집중 타이머",
            Modifier.widthIn(max = 260.dp).align(Alignment.CenterHorizontally),
            valueRange = 0f..60f,
            step = 1f,
            ticks = 12,
            formatValue = { "${it.roundToInt()}분" },
        )
    }
}

@Composable
private fun SleepRangeCard() {
    var bed by rememberSaveable { mutableFloatStateOf(23f) }
    var wake by rememberSaveable { mutableFloatStateOf(7f) }
    DemoCard("수면 시간", "취침과 기상 손잡이를 따로 옮기거나, 두 손잡이 사이 원호를 끌어 길이는 그대로 둔 채 함께 옮깁니다. 자정을 넘는 범위도 됩니다.") {
        KCircularRangeSlider(
            bed,
            wake,
            { s, e ->
                bed = s
                wake = e
            },
            "취침",
            "기상",
            Modifier.widthIn(max = 280.dp).align(Alignment.CenterHorizontally),
            step = 0.25f,
            ticks = 24,
            formatValue = ::clockText,
            startIcon = KIcons.Moon,
            endIcon = KIcons.Sun,
        ) { s, e ->
            val span = ((e - s) % 24f + 24f) % 24f
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Label("${clockText(s)} ~ ${clockText(e)}", 14, color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
                Label("${span.toInt()}시간 ${((span % 1f) * 60).roundToInt()}분", 24, FontWeight.Bold)
            }
        }
    }
}

private fun clockText(hours: Float): String {
    val total = (hours * 60).roundToInt() % (24 * 60)
    return "%02d:%02d".format(total / 60, total % 60)
}

@Composable
private fun ShapeMorphCard() {
    var liked by rememberSaveable { mutableStateOf(false) }
    var starred by rememberSaveable { mutableStateOf(true) }
    var saved by rememberSaveable { mutableStateOf(false) }
    DemoCard("모양 바뀜 버튼", "누르는 동안 원이 둥근 사각형이나 쿠키 모양으로 이어서 바뀝니다. 오른쪽 셋은 켜면 모양과 색이 함께 바뀝니다.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            KShapeMorphButton(KIcons.Send, "보내기", {})
            KShapeMorphButton(KIcons.Plus, "추가", {}, pressedShape = KMorphShape.Cookie9, tone = KTone.Positive)
            KShapeToggle(liked, { liked = it }, KIcons.Heart, "좋아요", checkedShape = KMorphShape.Cookie4, tone = KTone.Destructive)
            KShapeToggle(starred, { starred = it }, KIcons.Star, "즐겨찾기", checkedShape = KMorphShape.Clover)
            KShapeToggle(saved, { saved = it }, KIcons.Bookmark, "저장", checkedShape = KMorphShape.Flower, size = 64.dp)
        }
    }
}

private class WalletCard(val name: String, val number: String, val colors: List<Color>)

private val walletCards = listOf(
    WalletCard("교통카드", "•••• 2041", listOf(Color(0xFF1F7AE0), Color(0xFF46B1F5))),
    WalletCard("체크카드", "•••• 8812", listOf(Color(0xFF6E3AE6), Color(0xFFB45CF0))),
    WalletCard("마일리지", "•••• 0093", listOf(Color(0xFF0E8F6B), Color(0xFF3CCB8E))),
    WalletCard("멤버십", "•••• 5170", listOf(Color(0xFFE0592A), Color(0xFFF5A23C))),
    WalletCard("탑승권", "ICN → HND", listOf(Color(0xFF2A2F3A), Color(0xFF59627A))),
)

@Composable
private fun StackedCardsCard() {
    val state = rememberKStackedCardsState()
    DemoCard("쌓인 카드", "누르면 펼쳐지고, 한 장을 누르면 그 카드만 앞으로 나오고 나머지는 아래로 접힙니다. 다시 누르거나 뒤로 가기를 하면 돌아갑니다.") {
        KStackedCards(walletCards.size, Modifier.fillMaxWidth(), state = state, cardHeight = 190.dp) { i ->
            val card = walletCards[i]
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(card.colors))
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Label(card.name, 17, FontWeight.Bold, color = Color.White)
                Label(card.number, 15, color = Color.White.copy(alpha = 0.85f))
            }
        }
        if (state.expanded) KButton("다시 쌓기", state::collapse, variant = KVariant.Tonal)
    }
}

@Composable
private fun ScratchCardCard() {
    var round by rememberSaveable { mutableIntStateOf(0) }
    val state = rememberKScratchCardState()
    val prizes = remember { listOf("아메리카노 쿠폰", "다음 기회에", "500 포인트", "무료 배송") }
    DemoCard("긁는 카드", "손가락으로 긁으면 아래가 드러나고, 반쯤 긁으면 나머지가 사라지며 다 열립니다.") {
        KScratchCard(
            Modifier.fillMaxWidth().height(150.dp),
            state = state,
            coverBrush = Brush.linearGradient(listOf(Color(0xFFC9CED6), Color(0xFF9EA5B1), Color(0xFFD9DDE3))),
            shape = RoundedCornerShape(20.dp),
            cover = {
                Label("여기를 긁어 보세요", 16, FontWeight.SemiBold, Modifier.align(Alignment.Center), color = Color(0xFF4A505B))
            },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFFFFF2C9), Color(0xFFFFD27A)))),
                contentAlignment = Alignment.Center,
            ) {
                Label(prizes[round % prizes.size], 22, FontWeight.Bold, color = Color(0xFF5A3A00))
            }
        }
        if (state.isRevealed) {
            KButton("한 장 더", {
                round++
                state.reset()
            }, variant = KVariant.Tonal)
        }
    }
}
