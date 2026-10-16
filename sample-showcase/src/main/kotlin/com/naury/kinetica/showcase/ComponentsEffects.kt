// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.FlipCard
import com.naury.kinetica.ui.KBlobButton
import com.naury.kinetica.ui.KBlobGroup
import com.naury.kinetica.ui.KChipGroup
import com.naury.kinetica.ui.KIndication
import com.naury.kinetica.ui.KSwitch
import com.naury.kinetica.ui.ParticleField
import com.naury.kinetica.ui.ParticlePreset
import com.naury.kinetica.ui.holographic
import com.naury.kinetica.ui.rememberParticleFieldState
import com.naury.kinetica.ui.rippleDistortion

/** 효과 탭: 물결 왜곡, 파티클, 뒤집는 카드, 액체 버튼과 방울 묶음, 홀로그램 카드. */
@Composable
internal fun EffectsTab(padding: PaddingValues, connection: NestedScrollConnection, notify: (String) -> Unit) {
    ComponentTabList(padding, connection) {
        item {
            DemoCard("물결 왜곡", "그림을 누르면 그 자리에서 물결이 퍼지며 그림이 일렁입니다. 여러 번 누르면 물결이 겹칩니다. Android 13보다 낮으면 빛 고리로 대신합니다.") {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(INNER_SHAPE).rippleDistortion()) {
                    Landscape(vivid = true, Modifier.fillMaxSize())
                }
            }
        }
        item { ParticleCard() }
        item { FlipCardCard() }
        item { BlobCard(notify) }
        item { HoloCard() }
    }
}

@Composable
private fun ParticleCard() {
    var preset by rememberSaveable { mutableStateOf(ParticlePreset.Snow) }
    val particles = rememberParticleFieldState()
    DemoCard("파티클", "칩으로 프리셋을 바꿉니다. 버튼을 누르면 불꽃놀이로 바꾸고 바로 하나를 터뜨립니다.") {
        KChipGroup(PARTICLE_NAMES, setOf(preset.ordinal), { picked ->
            picked.firstOrNull()?.let { preset = ParticlePreset.entries[it] }
        })
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0B1026), Color(0xFF2B2F77)))),
        ) { ParticleField(preset, Modifier.fillMaxSize(), state = particles) }
        Pill("불꽃놀이 터뜨리기", filled = true) {
            if (preset != ParticlePreset.Fireworks && preset != ParticlePreset.Sparks) preset = ParticlePreset.Fireworks
            particles.launch()
        }
    }
}

@Composable
private fun FlipCardCard() {
    var flipped by rememberSaveable { mutableStateOf(false) }
    DemoCard("뒤집는 카드", "카드를 좌우로 끌면 손을 따라 돌고, 놓으면 가까운 면에 멈춥니다. 버튼으로도 뒤집을 수 있습니다.") {
        FlipCard(
            flipped,
            { flipped = it },
            Modifier.fillMaxWidth().aspectRatio(1.586f),
            // 실물 카드처럼 크기가 정해진 면이라 큰 글자 설정에서 카드 번호가 두 줄로 넘어가지 않게 배율을 제한한다.
            front = { CappedFontScale(1.3f) { PaymentCardFront() } },
            back = { CappedFontScale(1.3f) { PaymentCardBack() } },
        )
        Pill(if (flipped) "앞면 보기" else "뒷면 보기") { flipped = !flipped }
    }
}

@Composable
private fun PaymentCardFront() {
    Column(
        Modifier
            .fillMaxSize()
            .clip(INNER_SHAPE)
            .background(Brush.linearGradient(listOf(Color(0xFF232526), Color(0xFF414345))))
            .padding(22.dp),
    ) {
        Label("Kinetica Card", 18, FontWeight.Bold, color = Color.White)
        Spacer(Modifier.weight(1f))
        Label("1234  5678  9012  3456", 20, FontWeight.SemiBold, color = Color.White)
        Label("NAURY KIM   12/29", 13, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun PaymentCardBack() {
    Column(
        Modifier
            .fillMaxSize()
            .clip(INNER_SHAPE)
            .background(Brush.linearGradient(listOf(Color(0xFF414345), Color(0xFF232526)))),
    ) {
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth().height(44.dp).background(Color.Black))
        Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(36.dp).background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(6.dp)))
            Label("CVC 123", 15, FontWeight.Bold, Modifier.padding(start = 12.dp), Color.White)
        }
    }
}

@Composable
private fun BlobCard(notify: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val gap by animateDpAsState(if (open) 20.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 300f), label = "blob-gap")
    DemoCard("액체 버튼과 방울 묶음", "버튼을 누르면 누른 자리가 오목하게 들어갔다가 출렁이며 돌아옵니다. 방울은 가까워지면 녹아 붙고 멀어지면 떨어집니다.") {
        KBlobButton("구매하기", { notify("구매 버튼을 눌렀어요") }, Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            KBlobGroup(Modifier.padding(16.dp), color = Color(0xFF7B61FF)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    BLOB_ICONS.forEach { (name, icon) ->
                        Box(
                            Modifier
                                .blob()
                                .size(52.dp)
                                .clickable(null, KIndication(CircleShape, Color.White), role = Role.Button) { open = !open }
                                .semantics { contentDescription = name },
                            contentAlignment = Alignment.Center,
                        ) { Image(rememberVectorPainter(icon), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White)) }
                    }
                }
            }
            Label(if (open) "방울을 누르면 모입니다" else "방울을 누르면 펼쳐집니다", 13, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HoloCard() {
    var sensor by remember { mutableStateOf(false) }
    DemoCard("홀로그램 카드", "카드를 누른 채 움직이면 무지개빛 광택이 따라옵니다. 센서를 켜면 기기를 기울여도 움직입니다.") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("기울기 센서", 15, FontWeight.SemiBold, Modifier.weight(1f))
            KSwitch(sensor, { sensor = it })
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(220.dp)
                    .aspectRatio(220f / 308f)
                    .holographic(RoundedCornerShape(20.dp), useSensor = sensor)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1B1F3B), Color(0xFF3A1C71), Color(0xFF0F3443))))
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Label("LEGENDARY", 12, FontWeight.Black, color = Color.White.copy(alpha = 0.8f))
                Image(
                    rememberVectorPainter(ShowcaseIcons.Sparkle),
                    null,
                    Modifier.size(96.dp).align(Alignment.CenterHorizontally),
                    colorFilter = ColorFilter.tint(Color.White),
                )
                Label("Kinetica", 22, FontWeight.Bold, color = Color.White)
            }
        }
    }
}

private val PARTICLE_NAMES = listOf("눈", "불꽃놀이", "거품", "불꽃")
private val BLOB_ICONS = listOf("공유" to ShowcaseIcons.Cloud, "메모" to ShowcaseIcons.Pen, "알림" to ShowcaseIcons.Bell)
