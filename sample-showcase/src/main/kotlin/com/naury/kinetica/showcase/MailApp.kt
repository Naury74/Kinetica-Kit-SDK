// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Brush
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
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KAdaptiveDefaults
import com.naury.kinetica.ui.KDrawer
import com.naury.kinetica.ui.KEmptyIcon
import com.naury.kinetica.ui.KEmptyIconKind
import com.naury.kinetica.ui.KEmptyState
import com.naury.kinetica.ui.KExpandable
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KListDetail
import com.naury.kinetica.ui.KSnackbarHost
import com.naury.kinetica.ui.KSnackbarResult
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.LocalKineticaSurface
import com.naury.kinetica.ui.MenuCloseIcon
import com.naury.kinetica.ui.ReorderableItem
import com.naury.kinetica.ui.SurfaceStyle
import com.naury.kinetica.ui.SurfaceStyles
import com.naury.kinetica.ui.SwipeAction
import com.naury.kinetica.ui.SwipeActionsRow
import com.naury.kinetica.ui.coachMarkTarget
import com.naury.kinetica.ui.kBadge
import com.naury.kinetica.ui.kClickable
import com.naury.kinetica.ui.kSurface
import com.naury.kinetica.ui.rememberCoachMarkState
import com.naury.kinetica.ui.rememberKDrawerState
import com.naury.kinetica.ui.rememberKSnackbarState
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
    // 코치 마크와 서랍은 스캐폴드 밖에 뜨므로 테마로 같은 표면을 넘긴다.
    KineticaTheme(surface = demoSurface(MAIL_LOOK), design = LocalShowcaseLook.current.selectedDesign) {
        // 두 칸 배치에서는 목록이 옆에 그대로 보이므로 상세 칸에 뒤로 버튼을 두지 않는다.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val twoPane = maxWidth >= KAdaptiveDefaults.TwoPaneMinWidth
            MailPanes(selected, { selected = it }, mails, twoPane, onBack)
        }
    }
}

@Composable
private fun MailPanes(selected: Int?, select: (Int?) -> Unit, mails: SnapshotStateList<Mail>, twoPane: Boolean, onBack: () -> Unit) {
    KListDetail(
        selected,
        onBack = { select(null) },
        modifier = Modifier.fillMaxSize(),
        surface = demoSurface(MAIL_LOOK),
        emptyDetail = { NoMailSelected() },
        list = {
            MailList(mails, selected, onBack, onOpen = { select(it) }, onDelete = { if (selected == it) select(null) })
        },
    ) { id -> MAILS.firstOrNull { it.id == id }?.let { MailDetail(it, if (twoPane) null else ({ select(null) })) } }
}

@Composable
private fun MailList(mails: SnapshotStateList<Mail>, selected: Int?, onBack: () -> Unit, onOpen: (Int) -> Unit, onDelete: (Int) -> Unit) {
    val drawer = rememberKDrawerState()
    val coach = rememberCoachMarkState()
    var toured by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (!toured) coach.start() }
    KDrawer(drawerContent = { MailFolders(onBack) { scope.launch { drawer.close() } } }, state = drawer) {
        KineticaScaffold(
            Modifier.fillMaxSize(),
            surface = demoSurface(MAIL_LOOK),
            background = { DemoBackdrop() },
            topBar = {
                // 목록이 제목 아래로 지나갈 때 글자가 겹쳐 보이지 않게 위쪽을 화면 바탕색으로 덮고 아래로 갈수록 투명하게 한다.
                Row(
                    Modifier.fillMaxWidth()
                        .background(headerScrim())
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                        ).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KSurface(
                        { scope.launch { drawer.open() } },
                        Modifier
                            .size(44.dp)
                            .coachMarkTarget(coach, "menu")
                            .semantics { contentDescription = "메뉴" },
                        shape = CircleShape,
                    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { MenuCloseIcon(drawer.isOpen) } }
                    Label("받은편지함", 22, FontWeight.Bold, Modifier.padding(start = 14.dp))
                }
            },
        ) { padding -> ReadingContainer { Inbox(mails, selected, padding.calculateTopPadding(), coach, onOpen, onDelete) } }
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
    val snackbar = rememberKSnackbarState()
    val scope = rememberCoroutineScope()
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = readingPadding(16.dp),
            end = readingPadding(16.dp),
            top = top + 4.dp,
            bottom =
                navigationBarBottom() + 40.dp,
        ),
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
                            scope.launch { if (snackbar.show("메일을 지웠어요", "되돌리기") == KSnackbarResult.Action) mails.add(index, mail) }
                        },
                        SwipeAction("보관", Color(0xFF0A84FF)) {},
                    ),
                    modifier = Modifier.coachMarkTarget(coach, if (mail == MAILS.first()) "row" else "row-${mail.id}"),
                    startActions = listOf(SwipeAction("읽음", Color(0xFF34C759)) {}),
                    state = rememberSwipeActionsState(group),
                    shape = MAIL_ROW_SHAPE,
                ) { MailRow(mail, mail.id == selected) { onOpen(mail.id) } }
            }
        }
    }
    KSnackbarHost(snackbar)
}

@Composable
private fun MailRow(mail: Mail, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MAIL_ROW_SHAPE)
            .kSurface(LocalKineticaSurface.current, MAIL_ROW_SHAPE)
            .background(if (selected) KineticaTheme.colors.accent.copy(alpha = 0.14f) else Color.Transparent)
            .kClickable(MAIL_ROW_SHAPE, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).background(mail.color, CircleShape).kBadge(
                mail.unread.takeIf {
                    it > 0
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Label(mail.from.take(1), 17, FontWeight.Bold, color = Color.White)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Label(mail.from, 15, FontWeight.SemiBold)
            Label(mail.subject, 13, color = LocalKineticaContentColor.current.copy(alpha = 0.62f))
        }
    }
}

/** 메일 본문. 좁은 화면에서는 목록 위로 밀려 들어오므로 배경을 직접 칠한다. */
@Composable
private fun MailDetail(mail: Mail, onClose: (() -> Unit)?) {
    KineticaScaffold(
        Modifier.fillMaxSize(),
        surface = demoSurface(MAIL_LOOK),
        background = { DemoBackdrop() },
        topBar = { DemoTopBar(mail.from, "받은편지함", onClose) },
    ) { padding ->
        ReadingContainer { MailBody(mail, padding.calculateTopPadding()) }
    }
}

@Composable
private fun MailBody(mail: Mail, top: Dp) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = readingPadding(),
                end = readingPadding(),
                top = top + 8.dp,
                bottom =
                    navigationBarBottom() + 40.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Label(mail.subject, 22, FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(mail.color, CircleShape), contentAlignment = Alignment.Center) {
                Label(mail.from.take(1), 16, FontWeight.Bold, color = Color.White)
            }
            Column(Modifier.padding(start = 12.dp)) {
                Label(mail.from, 15, FontWeight.SemiBold)
                Label("나에게 · 오전 9:41", 12, color = LocalKineticaContentColor.current.copy(alpha = 0.6f))
            }
        }
        KSurface(Modifier.fillMaxWidth()) {
            Label(mail.body, 15, modifier = Modifier.padding(18.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("답장", filled = true) {}
            Pill("전달") {}
        }
    }
}

/** 두 칸 배치에서 아직 메일을 고르지 않았을 때 오른쪽 칸. */
@Composable
private fun NoMailSelected() {
    Box(Modifier.fillMaxSize()) {
        Aurora(Modifier.fillMaxSize(), dark = false)
        KEmptyState(
            "메일을 골라 주세요",
            Modifier.align(Alignment.Center).padding(24.dp),
            message = "왼쪽 목록에서 메일을 누르면 여기에 본문이 열립니다.",
            art = { KEmptyIcon(KEmptyIconKind.EmptyInbox, Modifier.size(72.dp)) },
        )
    }
}

@Composable
private fun Tips() {
    var open by rememberSaveable { mutableStateOf(false) }
    KExpandable(open, { open = it }, header = { Label("이 화면에서 해 볼 것", 15, FontWeight.SemiBold) }) {
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
private fun MailFolders(onBack: () -> Unit, close: () -> Unit) {
    var current by rememberSaveable { mutableIntStateOf(0) }
    Column(
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Label("메일함", 22, FontWeight.Bold, Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
        FOLDERS.forEachIndexed { index, (name, icon) ->
            val selected = index == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(FOLDER_SHAPE)
                    .background(if (selected) LocalKineticaContentColor.current.copy(alpha = 0.08f) else Color.Transparent)
                    .kClickable(FOLDER_SHAPE) {
                        current = index
                        close()
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShowcaseIcon(icon, 22.dp)
                Label(name, 16, if (selected) FontWeight.SemiBold else FontWeight.Normal, Modifier.padding(start = 14.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Pill("데모 나가기", Modifier.padding(start = 4.dp), onClick = onBack)
    }
}

private val MAIL_ROW_SHAPE = RoundedCornerShape(16.dp)
private val FOLDER_SHAPE = RoundedCornerShape(14.dp)
private val FOLDERS = listOf(
    "받은편지함" to KIcons.Inbox,
    "보낸편지함" to KIcons.Send,
    "보관함" to KIcons.Archive,
    "휴지통" to KIcons.Trash,
)

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

private val HEADER_SCRIM = Color(0xF2F6F5FB)
private val HEADER_SCRIM_DARK = Color(0xF20E0F14)

/** 목록이 제목 아래로 지나갈 때 글자가 겹치지 않게 덮는 그라디언트. 단색과 톤 표면이면 그 바탕색을 쓴다. */
@Composable
private fun headerScrim(): Brush {
    val surface = LocalKineticaSurface.current
    val color = when {
        surface !is SurfaceStyle.Glass -> surface.background.copy(alpha = 0.95f)
        surface.isDark -> HEADER_SCRIM_DARK
        else -> HEADER_SCRIM
    }
    return Brush.verticalGradient(0f to color, 0.7f to color, 1f to color.copy(alpha = 0f))
}

private val MAIL_LOOK =
    DemoLook(
        GlassStyles.OneUi,
        GlassStyles.OneUiDark,
        defaultDark = false,
        solidLight = SurfaceStyles.OneUi,
        solidDark = SurfaceStyles.OneUiDark,
    )
