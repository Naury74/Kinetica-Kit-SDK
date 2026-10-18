// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KAlertDialog
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KCheckbox
import com.naury.kinetica.ui.KCheckboxShape
import com.naury.kinetica.ui.KChipGroup
import com.naury.kinetica.ui.KDialogAction
import com.naury.kinetica.ui.KDialogActionStyle
import com.naury.kinetica.ui.KEmptyIcon
import com.naury.kinetica.ui.KEmptyIconKind
import com.naury.kinetica.ui.KEmptyState
import com.naury.kinetica.ui.KExtendedFab
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KProgressRing
import com.naury.kinetica.ui.KShapeToken
import com.naury.kinetica.ui.KSheet
import com.naury.kinetica.ui.KSnackbarHost
import com.naury.kinetica.ui.KSnackbarResult
import com.naury.kinetica.ui.KSnackbarState
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KSwitchRow
import com.naury.kinetica.ui.KTextArea
import com.naury.kinetica.ui.KTextField
import com.naury.kinetica.ui.KTopAppBar
import com.naury.kinetica.ui.KTopAppBarVariant
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KWeekStrip
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.PushNavigation
import com.naury.kinetica.ui.SurfaceStyles
import com.naury.kinetica.ui.SwipeAction
import com.naury.kinetica.ui.SwipeActionsRow
import com.naury.kinetica.ui.kClickable
import com.naury.kinetica.ui.rememberKSnackbarState
import com.naury.kinetica.ui.rememberKTopAppBarState
import com.naury.kinetica.ui.rememberSwipeActionsGroup
import com.naury.kinetica.ui.rememberSwipeActionsState
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 할 일 앱: 주간 띠와 오늘 할 일 목록, 할 일 상세 두 화면과 추가 바텀 시트. Material 3 느낌의 보라빛 단색 표면이다.
 * 체크하면 줄이 그어지고 진행 링이 차오르며, 밀어서 지운 할 일은 스낵바에서 되돌릴 수 있다.
 */
@Composable
internal fun TasksApp(onBack: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(listOf("list")) }
    val tasks = rememberStringList(SEED_TASKS.map { it.encode() })
    val pop = { stack = stack.dropLast(1) }
    AppTheme(TASKS_LOOK) {
        PushNavigation(stack, onBack = pop) { screen ->
            if (screen == "list") {
                TaskList(tasks, onBack) { stack = stack + "task/$it" }
            } else {
                val id = screen.substringAfter('/').toInt()
                tasks.map(::decode).firstOrNull { it.id == id }?.let { task -> TaskDetail(task, tasks, pop) }
            }
        }
    }
}

@Composable
private fun TaskList(tasks: SnapshotStateList<String>, onBack: () -> Unit, onOpen: (Int) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val bar = rememberKTopAppBarState(listState)
    val snackbar = rememberKSnackbarState()
    val scope = rememberCoroutineScope()
    val group = rememberSwipeActionsGroup()
    val all = tasks.map(::decode)
    val destructive = KineticaTheme.colors.destructive
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        background = { DemoBackdrop() },
        topBar = { KTopAppBar("할 일", variant = KTopAppBarVariant.Medium, state = bar, onBack = onBack) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize().nestedScroll(bar.connection),
                state = listState,
                contentPadding = PaddingValues(
                    start = appPadding(16.dp, 680.dp),
                    end = appPadding(16.dp, 680.dp),
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = navigationBarBottom() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item("week") { TaskWeek() }
                item("progress") { ProgressCard(all.count { it.done }, all.size) }
                LISTS.indices.forEach { listIndex ->
                    taskGroup(listIndex, all.filter { it.list == listIndex }) { task ->
                        SwipeActionsRow(
                            listOf(SwipeAction("삭제", destructive, removesRow = true) { removeWithUndo(tasks, task.id, scope, snackbar) }),
                            Modifier.animateItem(),
                            state = rememberSwipeActionsState(group),
                            shape = TASK_SHAPE,
                        ) {
                            TaskRow(task, { checked -> update(tasks, task.copy(done = checked)) }) { onOpen(task.id) }
                        }
                    }
                }
                if (all.isEmpty()) item("empty") { NoTasks() }
            }
            KExtendedFab(
                "할 일 추가",
                KIcons.Plus,
                { adding = true },
                Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.navigationBars).padding(20.dp),
                expanded = !listState.canScrollBackward,
            )
            KSnackbarHost(snackbar)
        }
    }
    AddTaskSheet(adding, { adding = false }, tasks)
}

@Composable
private fun AddTaskSheet(visible: Boolean, onDismiss: () -> Unit, tasks: SnapshotStateList<String>) {
    KSheet(visible, onDismiss, title = "새 할 일") {
        AddTask { title, list, alarm ->
            val id = (tasks.maxOfOrNull { decode(it).id } ?: 0) + 1
            tasks.add(Task(id, title, list, if (alarm) "오후 6:00" else "", false, "").encode())
            onDismiss()
        }
    }
}

@Composable
private fun TaskWeek() {
    var day by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val dot = KineticaTheme.colors.accent
    KWeekStrip(LocalDate.ofEpochDay(day), { day = it.toEpochDay() }, events = { d ->
        if (d.dayOfMonth % 3 ==
            0
        ) {
            listOf(dot)
        } else {
            emptyList()
        }
    })
}

@Composable
private fun NoTasks() {
    KEmptyState(
        "할 일을 모두 끝냈어요",
        Modifier.padding(top = 32.dp),
        message = "아래 버튼으로 새 할 일을 더해 보세요.",
        art = { KEmptyIcon(KEmptyIconKind.Success, Modifier.size(72.dp)) },
    )
}

/** 밀어서 지운 할 일은 스낵바의 되돌리기로 같은 자리에 다시 넣는다. */
private fun removeWithUndo(tasks: SnapshotStateList<String>, id: Int, scope: CoroutineScope, snackbar: KSnackbarState) {
    val index = tasks.indexOfFirst { decode(it).id == id }
    if (index < 0) return
    val raw = tasks.removeAt(index)
    scope.launch {
        if (snackbar.show("할 일을 지웠어요", "되돌리기") == KSnackbarResult.Action) tasks.add(index.coerceAtMost(tasks.size), raw)
    }
}

/** 목록 하나의 머리글과 행들. 빈 목록은 머리글도 넣지 않는다. */
private fun LazyListScope.taskGroup(listIndex: Int, rows: List<Task>, row: @Composable LazyItemScope.(Task) -> Unit) {
    if (rows.isEmpty()) return
    item("h-$listIndex") {
        Row(Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(LIST_COLORS[listIndex], CircleShape))
            Label(LISTS[listIndex], 15, FontWeight.Bold, Modifier.padding(start = 8.dp))
            Label(
                "${rows.count {
                    !it.done
                }}",
                13,
                modifier = Modifier.padding(start = 6.dp),
                color = LocalKineticaContentColor.current.copy(alpha = 0.5f),
            )
        }
    }
    items(rows, key = { it.id }) { row(it) }
}

@Composable
private fun ProgressCard(done: Int, total: Int) {
    KSurface(Modifier.fillMaxWidth().padding(top = 4.dp), shape = kitShape { card }) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            KProgressRing(if (total == 0) 1f else done / total.toFloat(), Modifier.size(64.dp), thickness = 7.dp) {
                Label("$done/$total", 14, FontWeight.Bold, Modifier.align(Alignment.Center))
            }
            Column(Modifier.padding(start = 16.dp)) {
                Label(if (done == total && total > 0) "오늘 할 일을 다 했어요" else "오늘 ${total - done}개 남았어요", 17, FontWeight.Bold)
                Label("체크하면 링이 차오릅니다", 13, color = LocalKineticaContentColor.current.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, onCheck: (Boolean) -> Unit, onClick: () -> Unit) {
    val ink = LocalKineticaContentColor.current
    KSurface(Modifier.fillMaxWidth(), shape = TASK_SHAPE) {
        Row(
            Modifier.fillMaxWidth().kClickable(
                TASK_SHAPE,
                onClickLabel = "자세히",
                onClick = onClick,
            ).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KCheckbox(task.done, onCheck, shape = KCheckboxShape.Circle)
            Column(Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
                androidx.compose.foundation.text.BasicText(
                    task.title,
                    style = androidx.compose.ui.text.TextStyle(
                        color = ink.copy(alpha = if (task.done) 0.45f else 1f),
                        fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp),
                        textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    ),
                )
                if (task.time.isNotEmpty()) Label(task.time, 12, color = ink.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun AddTask(onAdd: (String, Int, Boolean) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var list by rememberSaveable { mutableStateOf(0) }
    var alarm by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        KTextField(title, { title = it.replace("|", "") }, "할 일", placeholder = "예: 우유 사기")
        KChipGroup(LISTS, setOf(list), { list = it.firstOrNull() ?: list })
        KSwitchRow("오후 6시에 알림", alarm, { alarm = it })
        KButton("추가", { onAdd(title.trim(), list, alarm) }, size = KButtonSize.Large, fullWidth = true, enabled = title.isNotBlank())
    }
}

@Composable
private fun TaskDetail(task: Task, tasks: SnapshotStateList<String>, onBack: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        background = { DemoBackdrop() },
        topBar = { KTopAppBar(LISTS[task.list], onBack = onBack) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
                start = appPadding(20.dp, 640.dp),
                end = appPadding(20.dp, 640.dp),
                top = padding.calculateTopPadding() + 8.dp,
                bottom = navigationBarBottom() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KTextField(task.title, { update(tasks, task.copy(title = it.replace("|", ""))) }, "제목", clearable = false)
            KSurface(Modifier.fillMaxWidth(), shape = kitShape { card }) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    KSwitchRow("완료", task.done, { update(tasks, task.copy(done = it)) })
                    KSwitchRow("오후 6시에 알림", task.time.isNotEmpty(), { update(tasks, task.copy(time = if (it) "오후 6:00" else "")) })
                }
            }
            Label("목록", 14, FontWeight.SemiBold, Modifier.padding(start = 4.dp))
            KChipGroup(LISTS, setOf(task.list), { update(tasks, task.copy(list = it.firstOrNull() ?: task.list)) })
            KTextArea(task.note, { update(tasks, task.copy(note = it.replace("|", ""))) }, "메모", placeholder = "자세한 내용을 적어 두세요")
            Spacer(Modifier.height(8.dp))
            KButton("할 일 지우기", {
                confirm = true
            }, variant = KVariant.Tonal, tone = com.naury.kinetica.ui.KTone.Destructive, fullWidth = true)
        }
    }
    KAlertDialog(
        confirm,
        { confirm = false },
        "이 할 일을 지울까요?",
        listOf(
            KDialogAction("취소"),
            KDialogAction("지우기", KDialogActionStyle.Destructive) {
                tasks.removeAll { decode(it).id == task.id }
                onBack()
            },
        ),
        message = "지운 할 일은 되돌릴 수 없어요.",
    )
}

private data class Task(val id: Int, val title: String, val list: Int, val time: String, val done: Boolean, val note: String) {
    fun encode() = listOf(id, title, list, time, if (done) 1 else 0, note).joinToString("|")
}

private fun decode(raw: String): Task {
    val p = raw.split('|', limit = 6)
    return Task(p[0].toInt(), p[1], p[2].toInt(), p[3], p[4] == "1", p[5])
}

private fun update(tasks: SnapshotStateList<String>, task: Task) {
    val index = tasks.indexOfFirst { decode(it).id == task.id }
    if (index >= 0) tasks[index] = task.encode()
}

private val LISTS = listOf("업무", "개인", "장보기")
private val LIST_COLORS = listOf(Color(0xFF6750A4), Color(0xFF2E9E6B), Color(0xFFE8833A))
private val SEED_TASKS = listOf(
    Task(1, "주간 보고서 초안 보내기", 0, "오전 11:00", true, ""),
    Task(2, "디자인 리뷰 회의", 0, "오후 2:00", false, "2안 버튼 색 의견 정리해 가기"),
    Task(3, "릴리스 노트 검토", 0, "", false, ""),
    Task(4, "헬스장 PT", 1, "오후 7:30", false, ""),
    Task(5, "엄마께 전화", 1, "", false, ""),
    Task(6, "우유, 달걀", 2, "", false, ""),
    Task(7, "세제 리필", 2, "", true, ""),
)
private val TASK_SHAPE: Shape
    @Composable
    @ReadOnlyComposable
    get() = kitShape { KShapeToken.Large }

internal val TASKS_LOOK = AppLook(
    SurfaceStyles.Material,
    SurfaceStyles.MaterialDark,
    defaultDark = false,
    accent = Color(0xFF6750A4),
    accentDark = Color(0xFFD0BCFF),
    onAccentDark = Color(0xFF381E72),
)
