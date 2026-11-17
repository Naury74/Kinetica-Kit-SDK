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
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.consumer.R
import com.naury.kinetica.ui.Candle
import com.naury.kinetica.ui.KCandlestickChart
import com.naury.kinetica.ui.KCodeBlock
import com.naury.kinetica.ui.KDayEvent
import com.naury.kinetica.ui.KDayTimeline
import com.naury.kinetica.ui.KKanbanBoard
import com.naury.kinetica.ui.KKanbanColumn
import com.naury.kinetica.ui.KMarkdownText
import com.naury.kinetica.ui.KMentionCandidate
import com.naury.kinetica.ui.KMentionInput
import com.naury.kinetica.ui.KNotification
import com.naury.kinetica.ui.KNotificationStack
import com.naury.kinetica.ui.KPromptInput
import com.naury.kinetica.ui.KRadarChart
import com.naury.kinetica.ui.KReviewSummary
import com.naury.kinetica.ui.KShineButton
import com.naury.kinetica.ui.KStretchyHeader
import com.naury.kinetica.ui.KStretchyHeaderBar
import com.naury.kinetica.ui.KThinkingIndicator
import com.naury.kinetica.ui.KThinkingStep
import com.naury.kinetica.ui.KTreeView
import com.naury.kinetica.ui.KTreemap
import com.naury.kinetica.ui.KWordRotate
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.RadarSeries
import com.naury.kinetica.ui.SurfaceStyles
import com.naury.kinetica.ui.TreemapItem
import com.naury.kinetica.ui.rememberKMentionInputState
import com.naury.kinetica.ui.rememberKStretchyHeaderState
import com.naury.kinetica.ui.rememberKTreeState
import java.time.LocalTime

/** 호스트 앱의 파일 모델. 일부러 Bundle에 담을 수 없는 키(Path)를 쓴다. */
class FileNode(val path: Path, val name: String, val children: List<FileNode> = emptyList())

/** Serializable도 Parcelable도 아닌 호스트 키 */
data class Path(val value: String)

/** 호스트 앱의 업무 카드. 키도 일반 클래스다. */
data class Task(val id: Path, val title: String)

private val FILES = listOf(
    FileNode(Path("/app"), "app", listOf(FileNode(Path("/app/src"), "src", listOf(FileNode(Path("/app/src/Main.kt"), "Main.kt"))))),
    FileNode(Path("/README.md"), "README.md"),
)

private val PEOPLE = listOf(KMentionCandidate("kim", "김민지"), KMentionCandidate("park", "박서준"))

/**
 * 시나리오 (라): Kinetica 테마와 스캐폴드 없는 Material 3 앱에 AI 화면 부품과 멘션 입력을 넣는다.
 * 멘션 제안 팝업은 호스트 창 위에 따로 떠야 한다.
 */
class NewKit2M3Activity : ComponentActivity() {
    var sent by mutableStateOf("")
    var copied by mutableStateOf(false)
    var link by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF3B6CF6))) {
                Scaffold { padding -> Body(Modifier.padding(padding)) }
            }
        }
    }

    @Composable
    private fun Body(modifier: Modifier) {
        val prompt = rememberTextFieldState()
        var generating by remember { mutableStateOf(false) }
        val mention = rememberKMentionInputState()
        LazyColumn(modifier.fillMaxSize().background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { KThinkingIndicator(thinking = false, steps = listOf(KThinkingStep("a", "문서 찾기", true)), elapsedSeconds = 3) }
            item {
                KMarkdownText(
                    "# 제목\n\n**굵게**와 `코드`, 링크 https://example.com 입니다.\n\n1. 하나\n2. 둘\n\n```kotlin\nval x = 1\n```",
                    Modifier.testTag("markdown"),
                    onLinkClick = { link = it },
                )
            }
            item { KCodeBlock("fun main() = println(1)", Modifier.testTag("code"), language = "kotlin", onCopy = { copied = true }) }
            item {
                KMentionInput(mention, { _, q -> PEOPLE.filter { it.label.contains(q) } }, Modifier.fillMaxWidth().testTag("mention"))
            }
            item {
                KPromptInput(
                    prompt,
                    onSend = {
                        sent = prompt.text.toString()
                        generating = true
                    },
                    modifier = Modifier.testTag("prompt"),
                    generating = generating,
                    onStop = { generating = false },
                )
            }
            item { Box(Modifier.height(320.dp)) }
        }
    }
}

/**
 * 시나리오 (마): KineticaScaffold 글래스 화면에서 업무 도구와 차트, 연출 부품을 쓴다. 목록은 호스트가 mutableStateListOf로 들고
 * 제자리에서 바꾼다. 트리와 칸반 키는 Bundle에 담을 수 없는 일반 클래스다.
 */
class NewKit2ScaffoldActivity : ComponentActivity() {
    val notes = mutableStateListOf(KNotification("1", "지수", "저녁 어때?", "지금"), KNotification("2", "민호", "사진", "1분 전"))
    val todo = mutableStateListOf(Task(Path("t1"), "디자인 리뷰"), Task(Path("t2"), "배포 준비"))
    val done = mutableStateListOf<Task>()
    val events = mutableStateListOf(KDayEvent("e1", "회의", LocalTime.of(9, 0), LocalTime.of(10, 0)))
    var selected by mutableStateOf<String?>(null)
    var filter by mutableStateOf<Int?>(null)
    var shine by mutableStateOf(0)
    var treemapClicked by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KineticaTheme(surface = SurfaceStyles.Liquid) {
                KineticaScaffold { padding -> Content(Modifier.padding(padding)) }
            }
        }
    }

    @Composable
    private fun Content(modifier: Modifier) {
        LazyColumn(modifier.fillMaxSize().testTag("list"), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            item {
                KNotificationStack("메시지", notes, onDismiss = { id -> notes.removeAll { it.id == id } }, modifier = Modifier.testTag("stack"))
            }
            item {
                KTreeView(FILES, key = { it.path }, children = { it.children }, label = { it.name }, Modifier.height(240.dp).testTag("tree"), rememberKTreeState(), onSelect = { selected = it.name })
            }
            item {
                KKanbanBoard(
                    listOf(KKanbanColumn("todo", "할 일", todo), KKanbanColumn("done", "완료", done)),
                    cardKey = { it.id },
                    onMove = { key, to, index ->
                        val task = (todo + done).first { it.id == key }
                        todo.remove(task)
                        done.remove(task)
                        val target = if (to == "done") done else todo
                        target.add(index.coerceIn(0, target.size), task)
                    },
                    modifier = Modifier.height(260.dp).testTag("kanban"),
                ) { task, _ -> Text(task.title, Modifier.padding(12.dp)) }
            }
            item { KDayTimeline(events, Modifier.height(300.dp).testTag("timeline"), startHour = 8, endHour = 12, scrollToNow = false) }
            item { KRadarChart(listOf("속도", "힘", "지구력"), listOf(RadarSeries(listOf(3f, 4f, 5f), Color.Blue, "나")), Modifier.size(220.dp)) }
            item {
                KTreemap(
                    listOf(TreemapItem("food", "식비", 50f), TreemapItem("home", "주거", 80f)),
                    Modifier.fillMaxWidth().height(160.dp).testTag("treemap"),
                    onItemClick = { treemapClicked = it.label },
                )
            }
            item { KCandlestickChart(List(60) { Candle(10f + it % 5, 12f + it % 5, 9f, 11f + it % 3, 100f) }, Modifier.fillMaxWidth().height(220.dp).testTag("candle")) }
            item { KReviewSummary(4.6f, 120, listOf(80, 20, 10, 5, 5), onFilter = { filter = it }) }
            item { KWordRotate(listOf("산책", "캠핑"), prefix = "오늘은 ", suffix = " 하기 좋은 날") }
            item { KShineButton("시작하기", { shine++ }, Modifier.testTag("shine")) }
            item { Box(Modifier.height(200.dp)) }
        }
    }
}

/** 시나리오 (바): 늘어나는 헤더를 화면 전체에 쓴다. 상단 바는 목록 위에 겹친다. */
class NewKit2HeaderActivity : ComponentActivity() {
    var back by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                val list = rememberLazyListState()
                val header = rememberKStretchyHeaderState(list)
                Box(Modifier.fillMaxSize().nestedScroll(header.connection)) {
                    LazyColumn(Modifier.fillMaxSize().testTag("headerList"), state = list) {
                        item { KStretchyHeader(header, "제주 동쪽 한 바퀴") { Box(Modifier.fillMaxSize().background(Color(0xFF3B5BDB))) } }
                        items(40) { Text("장소 $it", Modifier.padding(16.dp)) }
                    }
                    KStretchyHeaderBar(header, "제주 동쪽 한 바퀴", Modifier.testTag("headerBar"), onBack = { back++ })
                }
            }
        }
    }
}

/** 시나리오 (사): XML NestedScrollView 안 ComposeView에서 캔들을 가로로 끌고 칸반 카드를 옮긴다. 부모 스크롤에 빼앗기지 않아야 한다. */
class NewKit2XmlActivity : AppCompatActivity() {
    val todo = mutableStateListOf(Task(Path("x1"), "알림 설정"), Task(Path("x2"), "결제 화면"))
    val done = mutableStateListOf<Task>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_kit2_xml)
        val label = findViewById<TextView>(R.id.newKit2Label)
        findViewById<ComposeView>(R.id.newKit2Compose).setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KCandlestickChart(List(120) { Candle(10f + it % 7, 13f + it % 7, 9f, 11f + it % 4, 50f) }, Modifier.fillMaxWidth().height(240.dp).testTag("xmlCandle"))
                    KKanbanBoard(
                        listOf(KKanbanColumn("todo", "할 일", todo), KKanbanColumn("done", "완료", done)),
                        cardKey = { it.id },
                        onMove = { key, to, index ->
                            val task = (todo + done).first { it.id == key }
                            todo.remove(task)
                            done.remove(task)
                            (if (to == "done") done else todo).add(index, task)
                            label.text = "옮김: ${task.title}"
                        },
                        modifier = Modifier.height(260.dp).testTag("xmlKanban"),
                        columnWidth = 160.dp,
                    ) { task, _ -> Row { Text(task.title, Modifier.padding(12.dp)) } }
                }
            }
        }
    }
}

/** 컴파일 확인용: Material 버튼과 섞어 쓴다. */
@Composable
fun MixedButtons(onClick: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick) { Text("Material") }
        KShineButton("Kinetica", onClick)
    }
}
