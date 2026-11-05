package com.example.consumer.recent

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.consumer.R
import com.naury.kinetica.ui.KAnimatedBeam
import com.naury.kinetica.ui.KBeam
import com.naury.kinetica.ui.KBentoCell
import com.naury.kinetica.ui.KBentoGrid
import com.naury.kinetica.ui.KBentoSpan
import com.naury.kinetica.ui.KBlurTextReveal
import com.naury.kinetica.ui.KBubblePicker
import com.naury.kinetica.ui.KChatInput
import com.naury.kinetica.ui.KCircularRangeSlider
import com.naury.kinetica.ui.KCircularSlider
import com.naury.kinetica.ui.KCommand
import com.naury.kinetica.ui.KCommandPalette
import com.naury.kinetica.ui.KCommandSection
import com.naury.kinetica.ui.KCountdownPill
import com.naury.kinetica.ui.KDataTable
import com.naury.kinetica.ui.KDock
import com.naury.kinetica.ui.KDockItem
import com.naury.kinetica.ui.KFloatingTabBar
import com.naury.kinetica.ui.KGlassGroup
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KLiveActivity
import com.naury.kinetica.ui.KLiveActivityHost
import com.naury.kinetica.ui.KLiveActivityPosition
import com.naury.kinetica.ui.KLiveWaveform
import com.naury.kinetica.ui.KMarquee
import com.naury.kinetica.ui.KMenuEntry
import com.naury.kinetica.ui.KMorphMenu
import com.naury.kinetica.ui.KOrbit
import com.naury.kinetica.ui.KOrbitingIcons
import com.naury.kinetica.ui.KScratchCard
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KShapeMorphButton
import com.naury.kinetica.ui.KShapeToggle
import com.naury.kinetica.ui.KSlider
import com.naury.kinetica.ui.KStackedCards
import com.naury.kinetica.ui.KSwitch
import com.naury.kinetica.ui.KTab
import com.naury.kinetica.ui.KTableColumn
import com.naury.kinetica.ui.KTimePill
import com.naury.kinetica.ui.KVoiceRecorderButton
import com.naury.kinetica.ui.KWaveform
import com.naury.kinetica.ui.KWavyProgressBar
import com.naury.kinetica.ui.KWavyProgressRing
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.SurfaceStyles
import com.naury.kinetica.ui.rememberKCommandPaletteState
import com.naury.kinetica.ui.rememberKDataTableState
import com.naury.kinetica.ui.rememberKFloatingTabBarState
import com.naury.kinetica.ui.rememberKLiveActivityState
import com.naury.kinetica.ui.rememberKScratchCardState
import com.naury.kinetica.ui.rememberKStackedCardsState
import com.naury.kinetica.ui.scrollEdgeBlur

/** 주문 표의 한 줄. 호스트 앱의 데이터 모델 흉내다. */
data class Order(val id: String, val customer: String, val amount: Int)

val ORDERS = listOf(Order("#3", "하늘", 30_000), Order("#1", "바다", 10_000), Order("#2", "숲", 20_000))

/**
 * 시나리오 (가): Kinetica 테마나 스캐폴드 없이 Material 3 앱에 새 컴포넌트를 넣는다. 떠 있는 것(칩 메뉴, 명령 판, 라이브 활동)은
 * 호스트 창 위에 따로 창을 띄워야 하고, 나머지는 기본 테마로 그려져야 한다.
 */
class NewKitM3Activity : ComponentActivity() {
    var picked by mutableStateOf("")
    var ran by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF3B6CF6))) {
                Scaffold { padding ->
                    M3Body(Modifier.padding(padding))
                }
            }
        }
    }

    @Composable
    private fun M3Body(modifier: Modifier) {
        var menu by remember { mutableStateOf(false) }
        val palette = rememberKCommandPaletteState()
        val live = rememberKLiveActivityState()
        var draft by remember { mutableStateOf("") }
        Column(modifier.fillMaxSize().background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KMorphMenu(
                    "정렬",
                    menu,
                    { menu = it },
                    listOf(KMenuEntry.Action("최신순") { picked = "최신순" }, KMenuEntry.Action("인기순") { picked = "인기순" }),
                    Modifier.testTag("morph"),
                )
                Button({ palette.open() }, Modifier.testTag("openPalette")) { Text("명령") }
                Button({ live.start(timerActivity()) }, Modifier.testTag("startLive")) { Text("타이머") }
            }
            Text("고름: $picked / 실행: $ran", Modifier.testTag("result"))
            KChatInput(
                draft,
                { draft = it },
                onSend = { draft = "" },
                voiceButton = { KVoiceRecorderButton(onStart = {}, onSend = { ran = "녹음 ${it}ms" }, modifier = Modifier.testTag("voice")) },
            )
        }
        KCommandPalette(
            palette,
            listOf(
                KCommandSection(
                    "파일",
                    listOf(KCommand("new", "새 주문 만들기", { ran = "새 주문" }), KCommand("open", "주문 열기", { ran = "열기" })),
                ),
            ),
        )
        KLiveActivityHost(live, position = KLiveActivityPosition.Bottom)
    }

    private fun timerActivity() = KLiveActivity(
        "timer",
        "타이머",
        "5분 남음",
        leading = { Text("⏱") },
        trailing = { KCountdownPill(System.currentTimeMillis() + 300_000L, 300_000L) },
        expanded = { Text("타이머 펼침", Modifier.testTag("liveExpanded")) },
    )
}

/**
 * 시나리오 (나): KineticaScaffold 안 글래스 화면에서 새 컴포넌트를 조작한다. 상태는 rememberSaveable이라 다시 만들어도 남아야 한다.
 */
class NewKitScaffoldActivity : ComponentActivity() {
    var volume by mutableFloatStateOf(0.2f)
    var segment by mutableIntStateOf(0)
    var tab by mutableIntStateOf(0)
    var timer by mutableFloatStateOf(25f)
    var sleep by mutableStateOf(23f to 7f)
    var revealed by mutableStateOf(false)
    var toggled by mutableStateOf(false)
    var launched by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KineticaTheme(surface = SurfaceStyles.Liquid) {
                val tabs = rememberKFloatingTabBarState()
                KineticaScaffold(
                    bottomBar = {
                        KFloatingTabBar(
                            listOf(KTab("홈", KIcons.Home), KTab("검색", KIcons.Search), KTab("설정", KIcons.Settings)),
                            tab,
                            { tab = it },
                            Modifier.testTag("tabBar"),
                            state = tabs,
                            lens = true,
                        )
                    },
                ) { padding -> ScaffoldList(Modifier.padding(padding)) }
            }
        }
    }

    @Composable
    private fun ScaffoldList(modifier: Modifier) {
        val list = rememberLazyListState()
        LazyColumn(modifier.fillMaxSize().scrollEdgeBlur(list).testTag("list"), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            item { KSlider(volume, { volume = it }, Modifier.fillMaxWidth().testTag("lensSlider"), label = "볼륨", lens = true) }
            item {
                KSegmentedControl(listOf("일", "주", "월"), segment, { segment = it }, Modifier.fillMaxWidth().testTag("lensSegment"), lens = true)
            }
            item { var on by remember { mutableStateOf(false) }; KSwitch(on, { on = it }, lens = true) }
            item { KWavyProgressBar({ 0.4f }); KWavyProgressRing({ 0.6f }); KWavyProgressBar() }
            item { KCircularSlider(timer, { timer = it }, "타이머", Modifier.size(240.dp).testTag("circular"), valueRange = 0f..60f, step = 5f) }
            item {
                KCircularRangeSlider(sleep.first, sleep.second, { s, e -> sleep = s to e }, "취침", "기상", Modifier.size(260.dp), step = 0.25f)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KShapeMorphButton(KIcons.Heart, "좋아요", {})
                    KShapeToggle(toggled, { toggled = it }, KIcons.Star, "즐겨찾기", Modifier.testTag("shapeToggle"))
                }
            }
            item {
                val stack = rememberKStackedCardsState()
                KStackedCards(3, Modifier.fillMaxWidth().testTag("stack"), state = stack) { i -> Text("카드 $i", Modifier.padding(16.dp)) }
            }
            item {
                val scratch = rememberKScratchCardState()
                KScratchCard(Modifier.fillMaxWidth().height(140.dp).testTag("scratch"), scratch, onRevealed = { revealed = true }, label = "쿠폰") {
                    Text("쿠폰", Modifier.padding(16.dp))
                }
            }
            item {
                val table = rememberKDataTableState()
                KDataTable(
                    ORDERS,
                    listOf(
                        KTableColumn.text("주문", { it.id }),
                        KTableColumn.text("고객", { it.customer }),
                        KTableColumn("금액", numeric = true, comparator = compareBy { it.amount }) { Text("${it.amount}") },
                    ),
                    key = { it.id },
                    modifier = Modifier.fillMaxWidth().height(260.dp).testTag("table"),
                    state = table,
                    selectable = true,
                )
            }
            item {
                KBentoGrid(Modifier.fillMaxWidth()) {
                    KBentoCell(span = KBentoSpan.Large) { Text("매출") }
                    KBentoCell { Text("주문") }
                    KBentoCell { Text("배송") }
                }
            }
            item { KBlurTextReveal("오늘 서울은 맑겠어요.", Modifier.fillMaxWidth()) }
            item { KBubblePicker(listOf("팝", "재즈", "록"), setOf("재즈"), {}, Modifier.fillMaxWidth().height(240.dp)) }
            item { KMarquee(edgeFade = 24.dp) { Row { repeat(6) { Text("태그 $it  ") } } } }
            item {
                KAnimatedBeam(listOf(KBeam("a", "b")), Modifier.fillMaxWidth().height(80.dp)) {
                    Text("A", Modifier.beamAnchor("a"))
                    Text("B", Modifier.align(androidx.compose.ui.Alignment.CenterEnd).beamAnchor("b"))
                }
            }
            item {
                KDock(
                    listOf("메일", "사진", "지도").map { name -> KDockItem(name, onClick = { launched = name }) { Box(Modifier.size(48.dp).background(Color(0xFF3B6CF6))) } },
                    Modifier.testTag("dock"),
                )
            }
            item { KOrbitingIcons(listOf(KOrbit(80.dp, icons = List(3) { { Text("•") } }))) { Text("K") } }
            item {
                KGlassGroup(Modifier.size(200.dp)) {
                    Box(Modifier.size(56.dp).glassId("a"))
                    Box(Modifier.padding(start = 60.dp).size(56.dp).glassId("b"))
                }
            }
            item { KTimePill(System.currentTimeMillis()); KLiveWaveform({ 0.5f }, Modifier.fillMaxWidth().height(32.dp)) }
            item { KWaveform(FloatArray(40) { (it % 7) / 7f }, { 0.3f }, Modifier.fillMaxWidth().height(40.dp), onSeek = {}) }
            item { Box(Modifier.height(120.dp)) }
        }
    }
}

/** 시나리오 (다): XML 화면의 NestedScrollView 안 ComposeView에서 원형 슬라이더를 세로로 끌어도 부모 스크롤에 빼앗기지 않는다. */
class NewKitXmlActivity : AppCompatActivity() {
    var timer by mutableFloatStateOf(0f)
    var picked by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_kit_xml)
        val value = findViewById<TextView>(R.id.newKitValue)
        findViewById<ComposeView>(R.id.newKitCompose).setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    var menu by remember { mutableStateOf(false) }
                    KMorphMenu(
                        "보기",
                        menu,
                        { menu = it },
                        listOf(KMenuEntry.Action("목록") { picked = "목록" }, KMenuEntry.Action("격자") { picked = "격자" }),
                        Modifier.testTag("xmlMorph"),
                    )
                    KCircularSlider(timer, {
                        timer = it
                        value.text = "값: ${it.toInt()}"
                    }, "타이머", Modifier.size(260.dp).testTag("xmlCircular"), valueRange = 0f..60f)
                    val scratch = rememberKScratchCardState()
                    KScratchCard(Modifier.fillMaxWidth().height(120.dp), scratch) { Text("쿠폰") }
                }
            }
        }
    }
}
