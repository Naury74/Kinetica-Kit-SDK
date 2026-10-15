// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.CoachMarkHost
import com.naury.kinetica.ui.CoachMarkState
import com.naury.kinetica.ui.CoachStep
import com.naury.kinetica.ui.GlassDrawer
import com.naury.kinetica.ui.GlassEmptyIcon
import com.naury.kinetica.ui.GlassEmptyIconKind
import com.naury.kinetica.ui.GlassEmptyState
import com.naury.kinetica.ui.GlassExpandable
import com.naury.kinetica.ui.GlassListDetail
import com.naury.kinetica.ui.GlassScaffold
import com.naury.kinetica.ui.GlassSnackbarHost
import com.naury.kinetica.ui.GlassSnackbarResult
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.GlassSurface
import com.naury.kinetica.ui.LocalGlassContentColor
import com.naury.kinetica.ui.MenuCloseIcon
import com.naury.kinetica.ui.ReorderableItem
import com.naury.kinetica.ui.SwipeAction
import com.naury.kinetica.ui.SwipeActionsRow
import com.naury.kinetica.ui.coachMarkTarget
import com.naury.kinetica.ui.glassBadge
import com.naury.kinetica.ui.rememberCoachMarkState
import com.naury.kinetica.ui.rememberGlassDrawerState
import com.naury.kinetica.ui.rememberGlassSnackbarState
import com.naury.kinetica.ui.rememberReorderableListState
import com.naury.kinetica.ui.rememberSwipeActionsGroup
import com.naury.kinetica.ui.rememberSwipeActionsState
import kotlinx.coroutines.launch

/**
 * 메일 앱: 좁은 화면에서는 메일을 누르면 본문이 옆에서 밀려 들어오고, 펼친 폴더블과 태블릿에서는 목록과 본문을 두 칸으로 나란히 보여 준다.
 * 목록에서는 왼쪽으로 밀어 보관·삭제, 길게 눌러 순서 바꾸기, 메뉴 버튼으로 서랍, 처음 열면 기능 안내가 나온다.
 */
@Composable
internal fun MailApp(onBack: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val mails = remember { mutableStateListOf<Mail>().apply { addAll(MAILS) } }
    GlassListDetail(
        selected,
        onBack = { selected = null },
        modifier = Modifier.fillMaxSize(),
        style = GlassStyles.OneUi,
        emptyDetail = { NoMailSelected() },
        list = {
            MailList(mails, selected, onBack, onOpen = { selected = it }, onDelete = { if (selected == it) selected = null })
        },
    ) { id -> MAILS.firstOrNull { it.id == id }?.let { MailDetail(it) { selected = null } } }
}

@Composable
private fun MailList(mails: SnapshotStateList<Mail>, selected: Int?, onBack: () -> Unit, onOpen: (Int) -> Unit, onDelete: (Int) -> Unit) {
    val drawer = rememberGlassDrawerState()
    val coach = rememberCoachMarkState()
    var toured by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (!toured) coach.start() }
    GlassDrawer(drawerContent = { MailFolders(onBack) }, state = drawer) {
        GlassScaffold(
            Modifier.fillMaxSize(),
            style = GlassStyles.OneUi,
            background = { Aurora(Modifier.fillMaxSize(), dark = false) },
            topBar = {
                Row(Modifier.statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassSurface(
                        Modifier
                            .size(44.dp)
                            .coachMarkTarget(coach, "menu")
                            .clickable(role = Role.Button) { scope.launch { drawer.open() } }
                            .semantics { contentDescription = "메뉴" },
                        shape = CircleShape,
                    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { MenuCloseIcon(drawer.isOpen) } }
                    Label("받은편지함", 22, FontWeight.Bold, Modifier.padding(start = 14.dp))
                }
            },
        ) { padding -> Inbox(mails, selected, padding.calculateTopPadding(), coach, onOpen, onDelete) }
        CoachMarkHost(coach, TOUR, onFinish = { toured = true })
    }
}

@Composable
private fun Inbox(
    mails: SnapshotStateList<Mail>,
    selected: Int?,
    top: Dp,
    coach: CoachMarkState,
    onOpen: (Int) -> Unit,
    onDelete: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val reorder = rememberReorderableListState(listState) { from, to -> mails.add(to - 1, mails.removeAt(from - 1)) }
    val group = rememberSwipeActionsGroup()
    val snackbar = rememberGlassSnackbarState()
    val scope = rememberCoroutineScope()
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Tips() }
        items(mails, key = { it.id }) { mail ->
            ReorderableItem(reorder, mail.id) {
                SwipeActionsRow(
                    endActions = listOf(
                        SwipeAction("삭제", Color(0xFFFF3B30), removesRow = true) {
                            val index = mails.indexOf(mail)
                            mails.remove(mail)
                            onDelete(mail.id)
                            scope.launch { if (snackbar.show("메일을 지웠어요", "되돌리기") == GlassSnackbarResult.Action) mails.add(index, mail) }
                        },
                        SwipeAction("보관", Color(0xFF0A84FF)) {},
                    ),
                    modifier = Modifier.coachMarkTarget(coach, if (mail == MAILS.first()) "row" else "row-${mail.id}"),
                    startActions = listOf(SwipeAction("읽음", Color(0xFF34C759)) {}),
                    state = rememberSwipeActionsState(group),
                ) { MailRow(mail, mail.id == selected) { onOpen(mail.id) } }
            }
        }
    }
    GlassSnackbarHost(snackbar)
}

@Composable
private fun MailRow(mail: Mail, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFFE3E9FF) else Color.White)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).background(mail.color, CircleShape).glassBadge(
                mail.unread.takeIf {
                    it > 0
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Label(mail.from.take(1), 17, FontWeight.Bold, color = Color.White)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Label(mail.from, 15, FontWeight.SemiBold, color = Color(0xFF15161A))
            Label(mail.subject, 13, color = Color(0xFF55575E))
        }
    }
}

/** 메일 본문. 좁은 화면에서는 목록 위로 밀려 들어오므로 배경을 직접 칠한다. */
@Composable
private fun MailDetail(mail: Mail, onClose: () -> Unit) {
    GlassScaffold(
        Modifier.fillMaxSize(),
        style = GlassStyles.OneUi,
        background = { Aurora(Modifier.fillMaxSize(), dark = false) },
        topBar = { DemoTopBar(mail.from, "받은편지함", onClose) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Label(mail.subject, 22, FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(mail.color, CircleShape), contentAlignment = Alignment.Center) {
                    Label(mail.from.take(1), 16, FontWeight.Bold, color = Color.White)
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Label(mail.from, 15, FontWeight.SemiBold)
                    Label("나에게 · 오전 9:41", 12, color = LocalGlassContentColor.current.copy(alpha = 0.6f))
                }
            }
            GlassSurface(Modifier.fillMaxWidth()) {
                Label(mail.body, 15, modifier = Modifier.padding(18.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("답장", filled = true) {}
                Pill("전달") {}
            }
        }
    }
}

/** 두 칸 배치에서 아직 메일을 고르지 않았을 때 오른쪽 칸. */
@Composable
private fun NoMailSelected() {
    Box(Modifier.fillMaxSize()) {
        Aurora(Modifier.fillMaxSize(), dark = false)
        GlassEmptyState(
            "메일을 골라 주세요",
            Modifier.align(Alignment.Center).padding(24.dp),
            message = "왼쪽 목록에서 메일을 누르면 여기에 본문이 열립니다.",
            art = { GlassEmptyIcon(GlassEmptyIconKind.EmptyInbox, Modifier.size(72.dp)) },
        )
    }
}

@Composable
private fun Tips() {
    var open by remember { mutableStateOf(false) }
    GlassExpandable(open, { open = it }, header = { Label("이 화면에서 해 볼 것", 15, FontWeight.SemiBold) }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label("• 메일을 왼쪽으로 밀면 보관·삭제, 끝까지 밀면 바로 삭제", 13)
            Label("• 오른쪽으로 밀면 읽음 표시", 13)
            Label("• 길게 누른 채 끌면 순서 바꾸기", 13)
            Label("• 왼쪽 위 메뉴나 왼쪽 가장자리에서 끌어 서랍 열기", 13)
            Label("• 메일을 누르면 본문이 열리고, 펼친 폴더블이나 태블릿에서는 두 칸으로 나란히 보임", 13)
        }
    }
}

@Composable
private fun MailFolders(onBack: () -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Label("메일함", 22, FontWeight.Bold)
        listOf("받은편지함", "보낸편지함", "보관함", "휴지통").forEach { Label(it, 16) }
        Pill("데모 나가기", onClick = onBack)
    }
}

private class Mail(val id: Int, val from: String, val subject: String, val color: Color, val unread: Int) {
    val body: String
        get() = "안녕하세요.\n\n$subject\n\n자세한 내용은 아래에 정리해 두었어요. 확인하시고 궁금한 점은 편하게 답장 주세요.\n\n감사합니다.\n$from 드림"
}

private val MAILS = listOf(
    Mail(1, "민지", "이번 주 회의 자료 보내 드려요", Color(0xFF5B6CFF), 2),
    Mail(2, "배송 알림", "주문하신 상품이 오늘 도착합니다", Color(0xFFFF9F0A), 1),
    Mail(3, "서준", "주말에 등산 갈래?", Color(0xFF34C759), 0),
    Mail(4, "카드사", "10월 이용 내역 안내", Color(0xFFFF453A), 0),
    Mail(5, "하은", "사진 공유했어요", Color(0xFFBF5AF2), 3),
    Mail(6, "뉴스레터", "이번 주 디자인 소식", Color(0xFF0A84FF), 0),
    Mail(7, "도윤", "점심 같이 먹어요", Color(0xFF30B0C7), 0),
    Mail(8, "보안 알림", "새 기기에서 로그인했습니다", Color(0xFF8E8E93), 1),
)
private val TOUR = listOf(
    CoachStep("menu", "메뉴", "여기를 누르거나 왼쪽 가장자리에서 끌면 메일함 서랍이 열려요."),
    CoachStep("row", "밀어서 정리", "메일을 왼쪽으로 밀면 보관·삭제, 오른쪽으로 밀면 읽음 표시를 해요."),
)
