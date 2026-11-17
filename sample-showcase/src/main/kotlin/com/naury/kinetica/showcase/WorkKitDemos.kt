// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KAvatar
import com.naury.kinetica.ui.KAvatarData
import com.naury.kinetica.ui.KAvatarSize
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KDayEvent
import com.naury.kinetica.ui.KDayTimeline
import com.naury.kinetica.ui.KFilterChip
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KKanbanBoard
import com.naury.kinetica.ui.KKanbanColumn
import com.naury.kinetica.ui.KMention
import com.naury.kinetica.ui.KMentionCandidate
import com.naury.kinetica.ui.KMentionInput
import com.naury.kinetica.ui.KTreeView
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.rememberKMentionInputState
import com.naury.kinetica.ui.rememberKTreeState
import java.time.LocalTime

/** 업무 도구 묶음: 파일 트리, 칸반 보드, 하루 일정, 멘션 입력. */
internal fun LazyListScope.workKitDemos() {
    item { FileTreeCard() }
    item { KanbanCard() }
    item { TodayCard() }
    item { MentionCard() }
}

private data class WorkFileNode(val path: String, val name: String, val children: List<WorkFileNode> = emptyList())

private fun folder(path: String, vararg children: WorkFileNode) = WorkFileNode(path, path.substringAfterLast('/'), children.toList())

private fun file(path: String) = WorkFileNode(path, path.substringAfterLast('/'))

private val PROJECT = listOf(
    folder(
        "app",
        folder(
            "app/src",
            folder(
                "app/src/main",
                folder(
                    "app/src/main/kotlin",
                    file("app/src/main/kotlin/MainActivity.kt"),
                    file("app/src/main/kotlin/HomeScreen.kt"),
                    file("app/src/main/kotlin/OrderViewModel.kt"),
                ),
                folder("app/src/main/res", file("app/src/main/res/strings.xml"), file("app/src/main/res/colors.xml")),
                file("app/src/main/AndroidManifest.xml"),
            ),
            folder("app/src/test", file("app/src/test/OrderViewModelTest.kt")),
        ),
        file("app/build.gradle.kts"),
    ),
    folder("docs", file("docs/README.md"), file("docs/CHANGELOG.md")),
    file("settings.gradle.kts"),
    file("gradle.properties"),
)

@Composable
private fun FileTreeCard() {
    val state = rememberKTreeState(initialExpanded = setOf("app", "app/src", "app/src/main"))
    var checkable by rememberSaveable { mutableStateOf(false) }
    var guides by rememberSaveable { mutableStateOf(true) }
    var opened by rememberSaveable { mutableStateOf("파일을 고르세요") }
    DemoCard(
        "파일 트리",
        "폴더를 누르면 펼쳐지고 화살표가 돕니다. 하드웨어 키보드에서는 방향키로 옮기고 오른쪽은 펼치기, 왼쪽은 접기, Enter는 열기입니다. " +
            "체크박스를 켜면 폴더는 아래 파일이 일부만 골라졌을 때 가로 막대로 보입니다.",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KFilterChip("체크박스", checkable, { checkable = it })
            KFilterChip("안내선", guides, { guides = it })
        }
        KTreeView(
            PROJECT,
            key = { it.path },
            children = { it.children },
            label = { it.name },
            modifier = Modifier.fillMaxWidth().height(340.dp),
            state = state,
            checkable = checkable,
            guides = guides,
            onSelect = { if (it.children.isEmpty()) opened = "열림: ${it.path}" },
            leading = { node, expanded -> FileIcon(node.children.isNotEmpty(), expanded) },
        )
        val count = state.checked.size
        Label(if (checkable) "$opened, 고른 파일 ${count}개" else opened, 13, color = muted())
    }
}

@Composable
private fun FileIcon(isFolder: Boolean, expanded: Boolean) {
    if (isFolder) {
        Box(
            Modifier
                .size(width = 18.dp, height = 14.dp)
                .background(Color(if (expanded) 0xFFFFB648 else 0xFFF5A524), RoundedCornerShape(3.dp)),
        )
    } else {
        ShowcaseIcon(KIcons.File, size = 18.dp, color = muted())
    }
}

private data class BoardTask(val id: String, val title: String, val tag: String, val tagColor: Color, val owner: String)

private val BLUE = Color(0xFF3B82F6)
private val PINK = Color(0xFFEC4899)
private val GREEN = Color(0xFF22A06B)
private val ORANGE = Color(0xFFF97316)

private fun initialBoard() = listOf(
    KKanbanColumn(
        "todo",
        "할 일",
        listOf(
            BoardTask("t1", "결제 실패 화면 문구 다듬기", "디자인", PINK, "김민지"),
            BoardTask("t2", "주문 목록 무한 스크롤", "앱", BLUE, "박서준"),
            BoardTask("t3", "푸시 알림 설정 화면", "앱", BLUE, "이하은"),
        ),
        Color(0xFF94A3B8),
    ),
    KKanbanColumn(
        "doing",
        "진행 중",
        listOf(BoardTask("t4", "배송 추적 지도 연동", "앱", BLUE, "정우진"), BoardTask("t5", "다크 모드 색 대비 점검", "디자인", PINK, "김민지")),
        BLUE,
    ),
    KKanbanColumn("review", "검토", listOf(BoardTask("t6", "쿠폰 API 응답 캐시", "서버", GREEN, "최유나")), ORANGE),
    KKanbanColumn("done", "완료", listOf(BoardTask("t7", "로그인 화면 접근성", "앱", BLUE, "이하은")), GREEN),
)

/** 카드를 [key]를 빼고 [toColumn] 열의 [toIndex] 자리에 넣은 새 보드. */
private fun List<KKanbanColumn<BoardTask>>.moved(key: Any, toColumn: Any, toIndex: Int): List<KKanbanColumn<BoardTask>> {
    val task = flatMap { it.cards }.firstOrNull { it.id == key } ?: return this
    return map { column ->
        val rest = column.cards.filter { it.id != key }
        if (column.id == toColumn) {
            column.copy(cards = rest.toMutableList().apply { add(toIndex.coerceIn(0, rest.size), task) })
        } else {
            column.copy(cards = rest)
        }
    }
}

@Composable
private fun KanbanCard() {
    var board by remember { mutableStateOf(initialBoard()) }
    var last by remember { mutableStateOf("카드를 길게 눌러 들어 올리세요") }
    DemoCard(
        "칸반 보드",
        "카드를 길게 눌러 들어 올린 뒤 같은 열 안이나 옆 열로 옮깁니다. 가장자리로 끌면 보드와 열이 스크롤됩니다. " +
            "TalkBack과 키보드(Alt+방향키)로도 옮길 수 있고, 펼친 폴더블과 태블릿에서는 네 열이 한 화면에 들어옵니다.",
    ) {
        KKanbanBoard(
            board,
            cardKey = { it.id },
            onMove = { key, toColumn, toIndex ->
                board = board.moved(key, toColumn, toIndex)
                val title = board.first { it.id == toColumn }.title
                last = "‘${board.flatMap { it.cards }.first { it.id == key }.title}’ → $title ${toIndex + 1}번째"
            },
            modifier = Modifier.fillMaxWidth().height(440.dp),
            columnWidth = 240.dp,
        ) { task, dragging -> TaskCard(task, dragging) }
        Label(last, 13, color = muted())
    }
}

@Composable
private fun TaskCard(task: BoardTask, dragging: Boolean) {
    val dark = KineticaTheme.surface.isDark
    val shape = kitInnerShape(10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(if (dragging) 0.dp else 1.dp, shape)
            .background(if (dark) Color(0xFF262A33) else Color.White, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Label(
            task.tag,
            11,
            FontWeight.SemiBold,
            Modifier.background(task.tagColor.copy(alpha = 0.14f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
            color = task.tagColor,
        )
        Label(task.title, 14, FontWeight.SemiBold, color = if (dark) Color.White else Color(0xFF1F2430))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KAvatar(KAvatarData(task.owner), size = KAvatarSize.ExtraSmall)
            Label(task.owner, 12, color = if (dark) Color(0xFFB6BCCB) else Color(0xFF6B7280))
        }
    }
}

private val TODAY = listOf(
    KDayEvent("standup", "팀 스탠드업", LocalTime.of(9, 0), LocalTime.of(9, 20), BLUE, "회의실 A"),
    KDayEvent("design", "결제 화면 디자인 리뷰", LocalTime.of(9, 30), LocalTime.of(11, 0), PINK, "Figma"),
    KDayEvent("oneonone", "민지와 1:1", LocalTime.of(10, 0), LocalTime.of(10, 30), Color(0xFF8B5CF6)),
    KDayEvent("lunch", "점심", LocalTime.of(12, 0), LocalTime.of(13, 0), GREEN, "사내 식당"),
    KDayEvent("sprint", "스프린트 계획", LocalTime.of(14, 0), LocalTime.of(15, 30), ORANGE, "대회의실"),
    KDayEvent("client", "고객사 통화", LocalTime.of(15, 0), LocalTime.of(16, 0), BLUE, "Meet"),
    KDayEvent("review", "코드 리뷰", LocalTime.of(17, 30), LocalTime.of(18, 0), Color(0xFF14B8A6)),
)

@Composable
private fun TodayCard() {
    // 컴포넌트는 목록이 바뀐 것을 참조로 알아보므로 같은 목록에 더하지 않고 새 목록을 넘긴다.
    var events by remember { mutableStateOf(TODAY) }
    var note by remember { mutableStateOf("빈 시간을 길게 누르면 30분 일정이 생깁니다") }
    DemoCard(
        "오늘 일정",
        "겹치는 일정은 나란히 나뉘고, 강조색 선은 지금 시각입니다(1분마다 움직이고 화면 밖이면 멈춥니다). 처음 보일 때 지금 시각 근처로 스크롤합니다.",
    ) {
        KDayTimeline(
            events,
            Modifier.fillMaxWidth().height(420.dp),
            startHour = 7,
            endHour = 22,
            hourHeight = 60.dp,
            onEventClick = { note = "${it.title}, ${it.start}–${it.end}" },
            onLongPressTime = { time ->
                val end = if (time.hour >= 23) LocalTime.MAX else time.plusMinutes(30)
                events = events + KDayEvent("new-${events.size}", "새 일정", time, end, Color(0xFF64748B))
                note = "$time 에 새 일정을 만들었습니다"
            },
        )
        Label(note, 13, color = muted())
    }
}

private val PEOPLE = listOf(
    KMentionCandidate("u1", "김민지", "디자인팀", KAvatarData("김민지", Color(0xFFEC4899))),
    KMentionCandidate("u2", "김도현", "앱 개발", KAvatarData("김도현", Color(0xFF3B82F6))),
    KMentionCandidate("u3", "박서준", "앱 개발", KAvatarData("박서준", Color(0xFF22A06B))),
    KMentionCandidate("u4", "이하은", "QA", KAvatarData("이하은", Color(0xFFF97316))),
    KMentionCandidate("u5", "정우진", "서버", KAvatarData("정우진", Color(0xFF8B5CF6))),
    KMentionCandidate("u6", "최유나", "PM", KAvatarData("최유나", Color(0xFF14B8A6))),
)

private val TOPICS = listOf("릴리스", "버그", "디자인리뷰", "회고", "온보딩").map { KMentionCandidate("tag-$it", it) }

private fun suggest(trigger: Char, query: String): List<KMentionCandidate> {
    val pool = if (trigger == '#') TOPICS else PEOPLE
    return pool.filter { it.label.contains(query, ignoreCase = true) || it.supporting?.contains(query, ignoreCase = true) == true }
}

@Composable
private fun MentionCard() {
    val state = rememberKMentionInputState()
    var mentions by remember { mutableStateOf(emptyList<KMention>()) }
    val sent = remember { mutableStateListOf<String>() }
    DemoCard(
        "멘션 입력",
        "@를 치면 사람, #를 치면 주제를 제안합니다. 키보드에서는 위아래와 Enter로 고르고 Esc로 닫습니다. 들어간 멘션은 지우기 한 번에 통째로 지워집니다.",
    ) {
        sent.forEach { Label(it, 14, modifier = Modifier.padding(vertical = 2.dp)) }
        KMentionInput(
            state,
            ::suggest,
            Modifier.fillMaxWidth(),
            triggers = setOf('@', '#'),
            placeholder = "댓글을 남겨 보세요",
            onMentionsChange = { mentions = it },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label(
                if (mentions.isEmpty()) "멘션 없음" else mentions.joinToString(" ") { "${it.trigger}${it.label}" },
                13,
                modifier = Modifier.weight(1f),
                color = muted(),
            )
            KButton(
                "보내기",
                {
                    if (state.text.isNotBlank()) sent += state.text.trim()
                    state.clear()
                    mentions = emptyList()
                },
                variant = KVariant.Tonal,
                size = KButtonSize.Small,
                icon = KIcons.Send,
            )
        }
    }
}

@Composable
private fun muted(): Color = LocalKineticaContentColor.current.copy(alpha = 0.65f)
