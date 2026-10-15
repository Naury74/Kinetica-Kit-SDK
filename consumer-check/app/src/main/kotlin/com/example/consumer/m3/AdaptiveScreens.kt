package com.example.consumer.m3

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.GlassAdaptiveNavigation
import com.naury.kinetica.ui.GlassFloatingTabBar
import com.naury.kinetica.ui.GlassListDetail
import com.naury.kinetica.ui.GlassTab
import com.naury.kinetica.ui.rememberGlassFloatingTabBarState

/** 메일 목록과 상세. 화면 폭이 600dp보다 좁으면 상세가 밀려 들어오고 넓으면 두 칸이 된다. */
@Composable
fun MailScreen() {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    GlassListDetail(
        selected,
        onBack = { selected = null },
        modifier = Modifier.fillMaxSize().testTag("listDetail"),
        emptyDetail = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("메일을 고르세요", Modifier.testTag("emptyDetail")) } },
        list = {
            LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).testTag("mailList")) {
                items(40) { i ->
                    ListItem(
                        headlineContent = { Text("메일 $i") },
                        modifier = Modifier.testTag("mail$i").clickable { selected = "m$i" },
                    )
                }
            }
        },
    ) { id ->
        // 좁은 화면에서는 상세가 목록 위로 밀려 들어오므로 배경을 칠한다(KDoc 안내).
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp).testTag("mailDetail")) {
            Text("본문 $id", Modifier.testTag("mailBody"))
        }
    }
}

private val adaptiveTabs = listOf(
    GlassTab("홈", Icons.Filled.Home),
    GlassTab("검색", Icons.Filled.Search),
    GlassTab("보관함", Icons.Filled.Favorite),
)

/** 폭에 따라 아래 탭 바와 시작 쪽 레일을 바꾸는 탭 화면. 목록은 탭 바가 차지하는 여백을 contentPadding으로 받는다. */
@Composable
fun AdaptiveTabsScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    GlassAdaptiveNavigation(adaptiveTabs, tab, onSelect = { tab = it }, modifier = Modifier.fillMaxSize().testTag("adaptiveNav")) { padding ->
        val list = rememberLazyListState()
        Column(Modifier.fillMaxSize()) {
            Text("탭 $tab 첫 항목 ${list.firstVisibleItemIndex}", Modifier.padding(padding).padding(8.dp).testTag("adaptiveState"))
            LazyColumn(Modifier.fillMaxSize().testTag("adaptiveList"), state = list, contentPadding = padding) {
                items(80) { i -> Text("${adaptiveTabs[tab].label} 항목 $i", Modifier.padding(16.dp)) }
            }
        }
    }
}

/** 화면 아래에 떠 있다가 스크롤하면 접히는 탭 바. GlassScaffold 없이 Material Scaffold 안 Box에 둔다. */
@Composable
fun FloatingTabsScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val bar = rememberGlassFloatingTabBarState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().nestedScroll(bar.nestedScrollConnection).testTag("floatingList"),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            items(80) { i -> Text("${adaptiveTabs[tab].label} 글 $i", Modifier.padding(16.dp)) }
        }
        Text(
            "탭 $tab 접힘 ${bar.isCollapsed}",
            Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.surface).padding(8.dp).testTag("floatingState"),
        )
        GlassFloatingTabBar(adaptiveTabs, tab, onSelect = { tab = it }, modifier = Modifier.align(Alignment.BottomCenter).testTag("floatingBar"), state = bar)
    }
}
