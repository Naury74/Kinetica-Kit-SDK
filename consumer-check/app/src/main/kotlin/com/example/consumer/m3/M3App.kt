package com.example.consumer.m3

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.naury.kinetica.ui.Detent
import com.naury.kinetica.ui.KDetentSheet
import com.naury.kinetica.ui.KDialog
import com.naury.kinetica.ui.KExpandHost
import com.naury.kinetica.ui.KMenuItem
import com.naury.kinetica.ui.KMiniPlayer
import com.naury.kinetica.ui.KSheet
import com.naury.kinetica.ui.KSnackbarHost
import com.naury.kinetica.ui.KSnackbarResult
import com.naury.kinetica.ui.KToastHost
import com.naury.kinetica.ui.PushNavigation
import com.naury.kinetica.ui.expandSource
import com.naury.kinetica.ui.kContextMenu
import com.naury.kinetica.ui.kOrigin
import com.naury.kinetica.ui.rememberKDetentSheetState
import com.naury.kinetica.ui.rememberKExpandState
import com.naury.kinetica.ui.rememberKSnackbarState
import com.naury.kinetica.ui.rememberKToastState
import com.naury.kinetica.ui.rememberKMiniPlayerState
import kotlinx.coroutines.launch

/** 아래 탭 세 개(홈, 효과, 폼)와 그 밖에서 여는 화면의 경로. */
object Routes {
    const val HOME = "home"
    const val EFFECTS = "effects"
    const val FORM = "form"
    const val DETAIL = "detail"
    const val MEDIA = "media"
    const val PUSH = "push"
    const val LIST = "list"
    const val AUTH = "auth"
    const val WELCOME = "welcome"
    const val PHOTOS = "photos"
    const val COMPARE = "compare"
    const val MAIL = "mail"
    const val ADAPTIVE_TABS = "adaptiveTabs"
    const val FLOATING = "floating"
    const val EFFECTS2 = "effects2"

    /** 홈 아래 버튼으로 여는, 3차 컴포넌트를 쓰는 화면들. */
    val extra = listOf(AUTH to "가입", PHOTOS to "사진", COMPARE to "비교", MAIL to "메일", ADAPTIVE_TABS to "적응형 탭", FLOATING to "떠 있는 탭", EFFECTS2 to "효과 2")
}

/**
 * Material3 앱에 Kinetica 글래스 UI를 섞어 쓰는 화면. KineticaScaffold 없이 Material Scaffold와 Navigation Compose 안에서 부른다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3App() {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        Scaffold(
            topBar = { TopAppBar(title = { Text("호스트 앱 · ${route ?: ""}", Modifier.testTag("title")) }) },
            bottomBar = {
                NavigationBar {
                    listOf(Triple(Routes.HOME, "홈", Icons.Filled.Home), Triple(Routes.EFFECTS, "효과", Icons.Filled.Star), Triple(Routes.FORM, "폼", Icons.Filled.Edit))
                        .forEach { (r, label, icon) ->
                            NavigationBarItem(
                                selected = route == r,
                                onClick = { nav.navigate(r) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true } },
                                icon = { Icon(icon, null) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("tab_$r"),
                            )
                        }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding).fillMaxSize()) {
                composable(Routes.HOME) { HomeScreen(nav) }
                composable(Routes.EFFECTS) { EffectsScreen(openList = { nav.navigate(Routes.LIST) }) }
                composable(Routes.FORM) { FormScreen() }
                composable(Routes.DETAIL) { DetailScreen() }
                composable(Routes.MEDIA) { MediaScreen() }
                composable(Routes.PUSH) { PushScreen() }
                composable(Routes.LIST) { EffectList500() }
                composable(Routes.AUTH) { SignUpScreen(onVerified = { nav.navigate("${Routes.WELCOME}/${it.ifEmpty { "-" }}") { popUpTo(Routes.AUTH) { inclusive = true } } }) }
                composable("${Routes.WELCOME}/{email}") { WelcomeScreen(it.arguments?.getString("email").orEmpty()) }
                composable(Routes.PHOTOS) { PhotosScreen() }
                composable(Routes.COMPARE) { CompareScreen() }
                composable(Routes.MAIL) { MailScreen() }
                composable(Routes.ADAPTIVE_TABS) { AdaptiveTabsScreen() }
                composable(Routes.FLOATING) { FloatingTabsScreen() }
                composable(Routes.EFFECTS2) { Effects2Screen(leave = { nav.popBackStack() }) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(nav: NavHostController) {
    var kSheet by rememberSaveable { mutableStateOf(false) }
    var kDialog by rememberSaveable { mutableStateOf(false) }
    var materialSheet by rememberSaveable { mutableStateOf(false) }
    var materialDialog by rememberSaveable { mutableStateOf(false) }
    var lastEvent by rememberSaveable { mutableStateOf("없음") }
    var origin by remember { mutableStateOf<Offset?>(null) }
    val toast = rememberKToastState()
    val snackbar = rememberKSnackbarState()
    val expand = rememberKExpandState()
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("마지막 이벤트: $lastEvent", Modifier.testTag("lastEvent"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ kSheet = true }, Modifier.testTag("openKSheet")) { Text("글래스 시트") }
                Button({ kDialog = true }, Modifier.testTag("openKDialog").kOrigin { origin = it }) { Text("글래스 다이얼로그") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ materialSheet = true }, Modifier.testTag("openMaterialSheet")) { Text("Material 시트") }
                OutlinedButton({ materialDialog = true }, Modifier.testTag("openMaterialDialog")) { Text("Material 다이얼로그") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ scope.launch { toast.show("저장했어요", durationMs = 6_000) } }, Modifier.testTag("showToast")) { Text("토스트") }
                Button({
                    scope.launch {
                        val result = snackbar.show("항목을 지웠어요", actionLabel = "되돌리기", durationMs = 10_000)
                        lastEvent = if (result == KSnackbarResult.Action) "snackbar-action" else "snackbar-dismissed"
                    }
                }, Modifier.testTag("showSnackbar")) { Text("스낵바") }
            }
            ListItem(
                headlineContent = { Text("길게 눌러 메뉴 열기") },
                supportingContent = { Text("kContextMenu를 붙인 Material ListItem") },
                modifier = Modifier.testTag("contextItem").kContextMenu(
                    listOf(
                        KMenuItem("공유") { lastEvent = "menu-share" },
                        KMenuItem("삭제", destructive = true) { lastEvent = "menu-delete" },
                    ),
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ nav.navigate(Routes.DETAIL) }, Modifier.testTag("goDetail")) { Text("상세 화면") }
                TextButton({ nav.navigate(Routes.MEDIA) }, Modifier.testTag("goMedia")) { Text("미디어") }
                TextButton({ nav.navigate(Routes.PUSH) }, Modifier.testTag("goPush")) { Text("푸시") }
            }
            Card(
                Modifier.fillMaxWidth().height(120.dp).testTag("expandCard").expandSource(expand, "card1").clickable { expand.expand("card1") },
            ) { Box(Modifier.fillMaxSize().padding(16.dp)) { Text("카드를 누르면 커진다") } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Routes.extra.forEach { (r, label) -> TextButton({ nav.navigate(r) }, Modifier.testTag("go_$r")) { Text(label) } }
            }
        }

        KSheet(kSheet, { kSheet = false }, title = "글래스 시트") {
            Text("시트 내용", Modifier.testTag("kSheetBody"))
            Button({ kSheet = false; lastEvent = "sheet-ok" }, Modifier.testTag("kSheetOk")) { Text("확인") }
        }
        KDialog(kDialog, { kDialog = false }, origin = origin, title = "글래스 다이얼로그") {
            Text("다이얼로그 내용", Modifier.testTag("kDialogBody"))
            Button({ materialDialog = true }, Modifier.testTag("dialogOpensMaterial")) { Text("Material 다이얼로그 겹치기") }
        }
        if (materialSheet) {
            ModalBottomSheet(onDismissRequest = { materialSheet = false }) {
                Column(Modifier.padding(16.dp)) {
                    Text("Material 시트 내용", Modifier.testTag("materialSheetBody"))
                    Button({ kDialog = true }, Modifier.testTag("materialSheetOpensKSheet")) { Text("글래스 다이얼로그 열기") }
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
        if (materialDialog) {
            AlertDialog(
                onDismissRequest = { materialDialog = false },
                confirmButton = { TextButton({ materialDialog = false; lastEvent = "material-ok" }, Modifier.testTag("materialDialogOk")) { Text("확인") } },
                title = { Text("Material 다이얼로그") },
                text = { Text("AlertDialog 내용", Modifier.testTag("materialDialogBody")) },
            )
        }
        KToastHost(toast)
        // 호스트의 NavigationBar 위에 띄우려고 modifier로 아래 여백을 준다.
        KSnackbarHost(snackbar, bottomOffset = 80.dp)
        KExpandHost(expand, title = "카드 상세") { key ->
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(24.dp)) {
                Text("상세: $key", Modifier.testTag("expandDetail"))
            }
        }
    }
}

/** NavHost 뒤로 가기와 시트 닫기 순서를 보는 화면. */
@Composable
private fun DetailScreen() {
    var sheet by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("상세 화면", Modifier.testTag("detailScreen"))
        Button({ sheet = true }, Modifier.testTag("detailOpenSheet")) { Text("시트 열기") }
    }
    KSheet(sheet, { sheet = false }, title = "상세 시트") {
        Text("상세 시트 내용", Modifier.testTag("detailSheetBody"))
        LazyColumn(Modifier.fillMaxWidth()) { items(40) { Text("행 $it", Modifier.padding(8.dp)) } }
    }
}

/** 부른 자리에 그리는 미니 플레이어와 여러 높이 시트. 문서대로 화면을 채우는 Box 안에 둔다. */
@Composable
private fun MediaScreen() {
    val player = rememberKMiniPlayerState()
    val detents = remember { listOf(Detent.Peek(96.dp), Detent.Fraction(0.5f), Detent.Full) }
    val sheet = rememberKDetentSheetState(detents)
    var clicks by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(Color(0xFF335577))) {
        Button({ clicks++ }, Modifier.align(Alignment.Center).testTag("mediaBehind")) { Text("뒤 버튼 $clicks") }
        Text("펼침: ${player.isExpanded}", Modifier.align(Alignment.TopStart).padding(8.dp).testTag("miniState"), color = Color.White)
        KDetentSheet(sheet, title = "장소") {
            LazyColumn(Modifier.fillMaxWidth()) { items(30) { Text("장소 $it", Modifier.padding(12.dp).testTag("place$it")) } }
        }
        KMiniPlayer(
            player,
            artwork = { Box(Modifier.size(48.dp).background(Color(0xFFFF8800), RoundedCornerShape(8.dp))) },
            barBottomPadding = 8.dp,
            collapsed = { Text("재생 중인 곡", Modifier.testTag("miniCollapsed")) },
            expanded = { Text("전체 화면 플레이어", Modifier.testTag("miniExpanded")) },
        )
    }
}

/** 탭 안에 넣은 iOS식 push 스택. */
@Composable
private fun PushScreen() {
    var stack by rememberSaveable { mutableStateOf(listOf("root")) }
    PushNavigation(stack, onBack = { stack = stack.dropLast(1) }, modifier = Modifier.fillMaxSize()) { screen ->
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            Text("푸시 화면: $screen", Modifier.testTag("push_$screen"))
            Button({ stack = stack + "child${stack.size}" }, Modifier.testTag("pushNext_$screen")) { Text("다음 화면") }
        }
    }
}
