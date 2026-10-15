package com.example.consumer.m3

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.FlipCard
import com.naury.kinetica.ui.GlassBlobButton
import com.naury.kinetica.ui.ParticleField
import com.naury.kinetica.ui.ParticlePreset
import com.naury.kinetica.ui.holographic
import com.naury.kinetica.ui.liquidMorph
import com.naury.kinetica.ui.rememberParticleFieldState
import com.naury.kinetica.ui.rippleDistortion

/** Material 버튼과 카드에 새 효과(물결 굴절, 액체 모양, 홀로그램, 입자 배경, 뒤집기 카드)를 붙인 화면. */
@Composable
fun Effects2Screen(leave: () -> Unit) {
    var ripple by rememberSaveable { mutableIntStateOf(0) }
    var morph by rememberSaveable { mutableIntStateOf(0) }
    var holo by rememberSaveable { mutableIntStateOf(0) }
    var particle by rememberSaveable { mutableIntStateOf(0) }
    var blob by rememberSaveable { mutableIntStateOf(0) }
    var flipped by rememberSaveable { mutableStateOf(false) }
    val particles = rememberParticleFieldState(autoLaunch = true)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button({ ripple++ }, Modifier.rippleDistortion().testTag("rippleButton")) { Text("물결 $ripple", Modifier.testTag("rippleText")) }
        Card(
            Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp).liquidMorph(Color(0xFF7B61FF), cornerRadius = 16.dp)
                .clickable { morph++ }.testTag("morphCard"),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) { Box(Modifier.padding(16.dp)) { Text("액체 카드 $morph", Modifier.testTag("morphText"), color = Color.White) } }
        Card(
            onClick = { holo++ },
            modifier = Modifier.size(220.dp, 140.dp).holographic(RoundedCornerShape(12.dp), useSensor = true).testTag("holoCard"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF202030)),
        ) { Box(Modifier.padding(16.dp)) { Text("홀로그램 $holo", Modifier.testTag("holoText"), color = Color.White) } }
        Box(Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF101828)).testTag("particleBox")) {
            ParticleField(ParticlePreset.Fireworks, state = particles)
            Button({ particle++; particles.launch() }, Modifier.align(Alignment.Center).testTag("particleButton")) { Text("축하 $particle", Modifier.testTag("particleText")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FlipCard(
                flipped,
                { flipped = it },
                Modifier.size(200.dp, 120.dp).testTag("flipCard"),
                front = { Box(Modifier.fillMaxSize().background(Color(0xFF1E88E5)), contentAlignment = Alignment.Center) { Text("앞면", Modifier.testTag("flipFront"), color = Color.White) } },
                back = { Box(Modifier.fillMaxSize().background(Color(0xFFE53935)), contentAlignment = Alignment.Center) { Text("뒷면", Modifier.testTag("flipBack"), color = Color.White) } },
            )
            TextButton({ flipped = !flipped }, Modifier.testTag("flipToggle")) { Text("뒤집기") }
        }
        Text("뒤집힘 $flipped", Modifier.testTag("flipState"))
        GlassBlobButton("방울 $blob", onClick = { blob++ }, modifier = Modifier.padding(8.dp).testTag("blobButton"))
        TextButton(leave, Modifier.testTag("effects2Leave")) { Text("떠나기") }
    }
}
