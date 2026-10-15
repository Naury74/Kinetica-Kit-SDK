package com.example.consumer.m3

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.naury.kinetica.presets.EffectPresets
import com.naury.kinetica.render.kineticaEffects
import com.naury.kinetica.ui.glassBadge
import com.naury.kinetica.ui.glowBorder
import com.naury.kinetica.ui.jellyPress
import com.naury.kinetica.ui.shake
import com.naury.kinetica.ui.skeleton
import com.naury.kinetica.ui.sparkle
import com.naury.kinetica.ui.spotlight

/** Material 컴포넌트에 Kinetica 효과를 붙였을 때 클릭이 그대로 전달되는지 보는 화면. */
@Composable
fun EffectsScreen(openList: () -> Unit) {
    var buttonClicks by rememberSaveable { mutableIntStateOf(0) }
    var cardClicks by rememberSaveable { mutableIntStateOf(0) }
    var itemClicks by rememberSaveable { mutableIntStateOf(0) }
    var shakeCount by rememberSaveable { mutableIntStateOf(0) }
    var loading by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            { buttonClicks++ },
            Modifier.kineticaEffects(EffectPresets.button, RoundedCornerShape(20.dp)).glassBadge(buttonClicks.takeIf { it > 0 }).testTag("effectButton"),
        ) { Text("효과 버튼 $buttonClicks", Modifier.testTag("effectButtonText")) }

        Card(
            Modifier.fillMaxWidth().height(96.dp).testTag("jellyCard")
                .glowBorder(RoundedCornerShape(12.dp))
                .jellyPress()
                .clickable { cardClicks++ },
        ) { Box(Modifier.padding(16.dp)) { Text("젤리 카드 $cardClicks", Modifier.testTag("jellyCardText")) } }

        ListItem(
            headlineContent = { Text("목록 항목 $itemClicks", Modifier.testTag("listItemText")) },
            supportingContent = { Text("shake, sparkle, spotlight") },
            modifier = Modifier.testTag("effectListItem")
                .kineticaEffects(EffectPresets.listItem(0))
                .shake(shakeCount)
                .sparkle()
                .spotlight()
                .clickable { itemClicks++; shakeCount++ },
        )

        Card(
            Modifier.fillMaxWidth().height(72.dp).testTag("skeletonCard").skeleton(loading).clickable { loading = !loading },
        ) { Text("불러온 내용", Modifier.padding(16.dp)) }

        TextButton(openList, Modifier.testTag("openList500")) { Text("효과 붙인 500개 목록") }
    }
}

/** 항목 500개마다 효과를 붙인 LazyColumn. 스크롤과 재사용 뒤에도 클릭이 맞는 항목에 가는지 본다. */
@Composable
fun EffectList500() {
    var clicked by rememberSaveable { mutableStateOf(-1) }
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        Text("누른 항목: $clicked", Modifier.padding(8.dp).testTag("clickedIndex"))
        LazyColumn(Modifier.fillMaxSize().testTag("list500"), state = listState) {
            items(500, key = { it }) { index ->
                ListItem(
                    headlineContent = { Text("항목 $index") },
                    modifier = Modifier.testTag("row$index")
                        .kineticaEffects(if (index % 2 == 0) EffectPresets.listItem(index) else EffectPresets.card, RoundedCornerShape(8.dp))
                        .let { if (index % 7 == 0) it.glassBadge(index) else it }
                        .clickable { clicked = index },
                )
            }
        }
    }
}
