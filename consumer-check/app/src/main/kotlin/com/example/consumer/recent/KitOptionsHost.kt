package com.example.consumer.recent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.player.RemoteKineticaPlayer
import com.naury.kinetica.serialization.DecodeResult
import com.naury.kinetica.serialization.RemoteSceneBundleLoader
import com.naury.kinetica.serialization.SceneBundleCodec
import com.naury.kinetica.serialization.SceneCodec
import com.naury.kinetica.serialization.SceneFetch
import com.naury.kinetica.ui.KAvatar
import com.naury.kinetica.ui.KAvatarData
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KCard
import com.naury.kinetica.ui.KContainerTransform
import com.naury.kinetica.ui.KExpandHost
import com.naury.kinetica.ui.KFilterChip
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KIslandContent
import com.naury.kinetica.ui.KIslandHost
import com.naury.kinetica.ui.KKeypad
import com.naury.kinetica.ui.KKeypadMode
import com.naury.kinetica.ui.KListItem
import com.naury.kinetica.ui.KListTrailing
import com.naury.kinetica.ui.KNavTransitions
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KSwitch
import com.naury.kinetica.ui.KTransitionContent
import com.naury.kinetica.ui.KTransitions
import com.naury.kinetica.ui.KineticaDesigns
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.PushNavigation
import com.naury.kinetica.ui.SurfaceStyles
import com.naury.kinetica.ui.expandSource
import com.naury.kinetica.ui.rememberKExpandState
import com.naury.kinetica.ui.rememberKIslandState
import com.naury.kinetica.ui.rememberKNavTransition
import kotlinx.coroutines.launch

/**
 * 시나리오 (a): Material 3 앱 안에서 디자인 언어를 고른다. 고르기 버튼은 호스트의 Material 버튼이고, 그 아래 Kinetica 컴포넌트만 형태가 바뀐다.
 * `square`는 디자인 언어와 상관없이 shape를 직접 덮어쓴 버튼이다.
 */
@Composable
fun DesignHostScreen() {
    var design by rememberSaveable { mutableIntStateOf(0) }
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF3B6CF6))) {
        Scaffold { padding ->
            Column(
                Modifier.fillMaxSize().background(Color.White).padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 3, 4).forEach { index ->
                        Button({ design = index }, Modifier.testTag("pick_${KineticaDesigns.all[index].first}")) {
                            Text(KineticaDesigns.all[index].first)
                        }
                    }
                }
                KineticaTheme(
                    surface = SurfaceStyles.CleanLight,
                    colors = com.naury.kinetica.ui.KineticaColors(accent = MaterialTheme.colorScheme.primary),
                    design = KineticaDesigns.all[design].second,
                ) {
                    Text("형태 ${KineticaTheme.design.depth}", Modifier.testTag("depth"))
                    KButton("저장", {}, Modifier.testTag("button"))
                    KButton("각진 버튼", {}, Modifier.testTag("square"), shape = RectangleShape)
                    KCard(Modifier.fillMaxWidth()) { Text("카드") }
                    var checked by remember { mutableStateOf(true) }
                    KSwitch(checked, { checked = it }, Modifier.testTag("switch"))
                    KFilterChip("필터", checked, { checked = it })
                    KListItem(
                        "잔액",
                        leading = null,
                        trailing = KListTrailing.Value("+12,000원", color = Color(0xFF16A34A)),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KAvatar(KAvatarData("민지"), Modifier.testTag("avatar"))
                        KIconButton(KIcons.Cart, "장바구니", {}, Modifier.testTag("cart"))
                        KIconButton(KIcons.Share, "공유", {})
                    }
                }
            }
        }
    }
}

/**
 * 시나리오 (b): 화면 이동 전환. PushNavigation(CardStack)으로 목록에서 상세, KTransitionContent 탭,
 * KExpandHost(listRow), 그리고 호스트가 쓰는 Navigation Compose NavHost에 KNavTransitions를 넣는다.
 */
@Composable
fun NavigationHostScreen() {
    var stack by rememberSaveable { mutableStateOf(listOf("list")) }
    KineticaTheme(surface = SurfaceStyles.CleanLight, design = KineticaDesigns.Material) {
        PushNavigation(stack, onBack = { stack = stack.dropLast(1) }, transition = KTransitions.CardStack, paneTitle = { it }) { screen ->
            Box(Modifier.fillMaxSize().background(Color.White).testTag("page_$screen")) {
                when (screen) {
                    "list" -> ListPage(open = { stack = stack + it })
                    "nav" -> NavHostPage()
                    else -> Text("상세 $screen", Modifier.padding(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ListPage(open: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val expand = rememberKExpandState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KButton("상세 열기", { open("detail") }, Modifier.testTag("openDetail"))
        KButton("NavHost 열기", { open("nav") }, Modifier.testTag("openNav"))
        KSegmentedControl(listOf("받은편지함", "보낸편지함"), tab, { tab = it }, Modifier.fillMaxWidth())
        KTransitionContent(tab, Modifier.fillMaxWidth().height(60.dp), transition = KTransitions.TabSlide, isForward = { a, b -> b > a }) {
            Text(if (it == 0) "받은 메일 3통" else "보낸 메일 1통", Modifier.testTag("tab_$it"))
        }
        KListItem(
            "메일 한 통",
            Modifier.expandSource(expand, "mail").testTag("mailRow"),
            onClick = { expand.expand("mail") },
        )
    }
    KExpandHost(expand, transform = KContainerTransform.listRow(Color.White), title = "메일") { key ->
        Text("$key 본문", Modifier.padding(top = 64.dp, start = 24.dp).testTag("expanded_$key"))
    }
}

@Composable
private fun NavHostPage() {
    val controller = rememberNavController()
    val nav = rememberKNavTransition(KNavTransitions.Push)
    NavHost(
        controller,
        startDestination = "a",
        enterTransition = nav.enter,
        exitTransition = nav.exit,
        popEnterTransition = nav.popEnter,
        popExitTransition = nav.popExit,
    ) {
        composable("a") {
            Column(Modifier.fillMaxSize().background(Color.White).padding(24.dp)) {
                Text("NavHost 첫 화면", Modifier.testTag("navA"))
                KButton("다음", { controller.navigate("b") }, Modifier.testTag("navNext"))
            }
        }
        composable("b") {
            Text("NavHost 둘째 화면", Modifier.fillMaxSize().background(Color.White).padding(24.dp).testTag("navB"))
        }
    }
}

/**
 * 시나리오 (c): 서버 번들 두 화면을 RemoteKineticaPlayer(bundle, sceneId)로 재생한다. 장면 전환은 JSON의 `transition`에 있고,
 * 뒤로 갈 때는 forward = false로 되감는다. 장면 안 버튼의 동작(`go?scene=…`, `back`)으로도 바꾼다.
 */
@Composable
fun SceneBundleHostScreen() {
    val loader = remember { HostScenes.loader() }
    var scene by rememberSaveable { mutableStateOf("home") }
    var forward by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Text("장면 $scene", Modifier.testTag("sceneName"))
        RemoteKineticaPlayer(
            loader,
            scene,
            Modifier.fillMaxWidth().weight(1f).testTag("player"),
            forward = forward,
            onAction = { raw ->
                if (raw == "back") {
                    forward = false
                    scene = "home"
                } else if (raw.startsWith("go")) {
                    forward = true
                    scene = raw.substringAfter("scene=")
                }
            },
        )
    }
}

/** 네트워크 없이 같은 번들을 돌려주는 두 화면. 홈은 push, 상세는 cover 전환으로 들어온다. */
private object HostScenes {
    private fun json(id: String, kind: String, color: String, action: String, label: String) = """
        {
          "schemaVersion": 1,
          "documentId": "host-$id",
          "canvas": { "width": 1080, "height": 1920, "background": "$color" },
          "transition": { "kind": "$kind", "durationMs": 300 },
          "nodes": [{ "id": "button", "type": "rect", "transform": { "x": 140, "y": 800 }, "bounds": { "width": 800, "height": 240 },
            "cornerRadius": 120, "style": { "fill": "#3B6CF6" }, "action": "$action",
            "accessibility": { "label": "$label", "role": "button" } }],
          "rootOrder": ["button"]
        }
    """.trimIndent()

    private val documents: Map<String, SceneDocument> by lazy {
        val codec = SceneCodec()
        mapOf(
            "home" to json("home", "push", "#F4F5F9", "go?scene=detail", "상세 장면 열기"),
            "detail" to json("detail", "cover", "#FFFFFF", "back", "홈 장면으로"),
        ).mapValues { (id, text) ->
            when (val result = codec.decode(text)) {
                is DecodeResult.Success -> result.document
                is DecodeResult.Failure -> error("$id 장면을 읽지 못했습니다: ${result.error}")
            }
        }
    }

    fun loader(): RemoteSceneBundleLoader {
        val bytes = SceneBundleCodec().encode(documents).toByteArray()
        return RemoteSceneBundleLoader({ SceneFetch.Body(bytes, etag = "host-v1") }, fallback = documents)
    }
}

/**
 * 시나리오 (d): 새로 들어온 API. 아일랜드 내용만 바꾸는 KIslandState.update(코루틴 없이), 전화 키패드(* #, 0을 길게 누르면 +),
 * KIcons 아이콘.
 */
@Composable
fun IslandKeypadHostScreen() {
    val island = rememberKIslandState()
    val scope = rememberCoroutineScope()
    var number by rememberSaveable { mutableStateOf("") }
    var updated by rememberSaveable { mutableStateOf<Boolean?>(null) }
    KineticaTheme(surface = SurfaceStyles.CleanLight) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(number, Modifier.testTag("number"))
                Text("update $updated", Modifier.testTag("updateResult"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KButton("통화 알림", { scope.launch { island.show(KIslandContent("통화 중", "00:01", KIcons.Call)) } }, Modifier.testTag("showIsland"))
                    KButton("시간 바꾸기", { updated = island.update(KIslandContent("통화 중", "00:02", KIcons.Call)) }, Modifier.testTag("updateIsland"))
                }
                KKeypad({ number += it }, { number = number.dropLast(1) }, mode = KKeypadMode.Phone, extraKey = {
                    KIconButton(KIcons.CallEnd, "통화 끊기", {}, Modifier.size(56.dp))
                })
            }
            KIslandHost(island)
        }
    }
}

class DesignHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DesignHostScreen() }
    }
}

class NavigationHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NavigationHostScreen() }
    }
}

class SceneBundleHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SceneBundleHostScreen() }
    }
}

class IslandKeypadHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { IslandKeypadHostScreen() }
    }
}
