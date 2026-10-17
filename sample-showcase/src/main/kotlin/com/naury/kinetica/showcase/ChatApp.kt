// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
// 예시 데이터 클래스는 필드를 생성자에 나란히 두는 편이 읽기 쉬워 인자가 많다.
@file:Suppress("MagicNumber", "LongParameterList")

package com.naury.kinetica.showcase

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KActionSheet
import com.naury.kinetica.ui.KAttachmentChip
import com.naury.kinetica.ui.KAttachmentStatus
import com.naury.kinetica.ui.KAvatar
import com.naury.kinetica.ui.KAvatarData
import com.naury.kinetica.ui.KAvatarSize
import com.naury.kinetica.ui.KAvatarStatus
import com.naury.kinetica.ui.KChatBubble
import com.naury.kinetica.ui.KChatInput
import com.naury.kinetica.ui.KChatSide
import com.naury.kinetica.ui.KChatStatus
import com.naury.kinetica.ui.KDialogAction
import com.naury.kinetica.ui.KDialogActionStyle
import com.naury.kinetica.ui.KEmptyIcon
import com.naury.kinetica.ui.KEmptyIconKind
import com.naury.kinetica.ui.KEmptyState
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KListDetail
import com.naury.kinetica.ui.KSearchBar
import com.naury.kinetica.ui.KShapeToken
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KTopAppBar
import com.naury.kinetica.ui.KTypingIndicator
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.MeshGradientBackground
import com.naury.kinetica.ui.MeshGradients
import com.naury.kinetica.ui.SurfaceStyle
import com.naury.kinetica.ui.SwipeAction
import com.naury.kinetica.ui.SwipeActionsRow
import com.naury.kinetica.ui.chatGroupPosition
import com.naury.kinetica.ui.kBadge
import com.naury.kinetica.ui.kClickable
import com.naury.kinetica.ui.rememberSwipeActionsGroup
import com.naury.kinetica.ui.rememberSwipeActionsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 메신저 앱: 대화 목록과 대화 화면. 메시 그라디언트 위 Acrylic 글래스라 말풍선 뒤로 배경색이 은은하게 비친다.
 * 보내면 상대가 입력 중 표시를 띄운 뒤 답장하고, 첨부 버튼으로 사진이나 파일을 올리면 진행 막대가 찬 뒤 말풍선으로 들어간다.
 * 펼친 폴더블과 태블릿에서는 목록과 대화가 두 칸으로 나란히 보인다.
 */
@Composable
internal fun ChatApp(onBack: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    // 보낸 메시지는 "대화 id|보낸 쪽(s/r)|종류(t/p/f)|내용"으로 담는다.
    val sent = rememberStringList()
    val read = rememberIntMap()
    AppTheme(CHAT_LOOK) {
        KListDetail(
            selected,
            onBack = { selected = null },
            modifier = Modifier.fillMaxSize(),
            emptyDetail = { ChatBackdropBox { NoChatSelected() } },
            list = {
                ChatList(sent, read, selected, onBack) { id ->
                    selected = id
                    read[id] = 1
                }
            },
        ) { id -> Conversation(CHATS.first { it.id == id }, sent, onClose = { selected = null }) }
    }
}

@Composable
private fun ChatBackdropBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        ChatBackdrop()
        content()
    }
}

@Composable
private fun ChatBackdrop() {
    val dark = LocalKineticaContentColor.current.isLight()
    MeshGradientBackground(Modifier.fillMaxSize(), palette = if (dark) MeshGradients.Midnight else MeshGradients.Sunset)
}

@Composable
private fun NoChatSelected() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        KSurface(Modifier.padding(24.dp).widthIn(max = 360.dp), shape = kitShape { card }) {
            KEmptyState(
                "대화를 골라 주세요",
                Modifier.padding(24.dp),
                message = "왼쪽 목록에서 대화를 누르면 여기에 열립니다.",
                art = { KEmptyIcon(KEmptyIconKind.EmptyInbox, Modifier.size(64.dp)) },
            )
        }
    }
}

@Composable
private fun ChatList(
    sent: SnapshotStateList<String>,
    read: SnapshotStateMap<Int, Int>,
    selected: Int?,
    onBack: () -> Unit,
    onOpen: (Int) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val pinned = rememberIntMap(1 to 1)
    val hidden = rememberIntMap()
    val group = rememberSwipeActionsGroup()
    val shown = CHATS
        .filter { hidden[it.id] != 1 && (query.isBlank() || query.trim() in it.name) }
        .sortedByDescending { pinned[it.id] == 1 }
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        background = { ChatBackdrop() },
        topBar = {
            KTopAppBar("채팅", onBack = onBack, actions = {
                KIconButton(KIcons.Edit, "새 대화", {}, variant = KVariant.Text, tone = KTone.Neutral)
            })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = navigationBarBottom() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item("search") { KSearchBar(query, { query = it }, {}, placeholder = "대화 검색") }
            item("online") { OnlineRow(onOpen) }
            items(shown, key = { it.id }) { chat ->
                val last = sent.lastOrNull { it.startsWith("${chat.id}|") }?.let { parseSent(it, 0) }
                val unread = if (read[chat.id] == 1) 0 else chat.unread
                SwipeActionsRow(
                    endActions = listOf(SwipeAction("나가기", KineticaTheme.colors.destructive, removesRow = true) { hidden[chat.id] = 1 }),
                    modifier = Modifier.animateItem(),
                    startActions = listOf(
                        SwipeAction(if (pinned[chat.id] == 1) "고정 해제" else "고정", Color(0xFFFF9F0A)) {
                            pinned[chat.id] = if (pinned[chat.id] == 1) 0 else 1
                        },
                    ),
                    state = rememberSwipeActionsState(group),
                    shape = ROW_SHAPE,
                ) {
                    ChatRow(chat, last, unread, pinned[chat.id] == 1, chat.id == selected) { onOpen(chat.id) }
                }
            }
        }
    }
}

/** 지금 온라인인 사람을 가로로 늘어놓은 줄. 누르면 그 사람과의 대화를 연다. */
@Composable
private fun OnlineRow(onOpen: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
        items(CHATS.filter { it.online }, key = { it.id }) { chat ->
            Column(
                Modifier.kClickable(kitShape { KShapeToken.Medium }) { onOpen(chat.id) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                KAvatar(chat.avatar, size = KAvatarSize.Large, status = KAvatarStatus.Online)
                Label(chat.name.take(3), 12, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun ChatRow(chat: Chat, last: Message?, unread: Int, pinned: Boolean, selected: Boolean, onClick: () -> Unit) {
    val ink = LocalKineticaContentColor.current
    val preview = last?.preview ?: chat.preview
    val time = if (last != null) "방금" else chat.time
    KSurface(
        onClick,
        Modifier.fillMaxWidth(),
        shape = ROW_SHAPE,
        variant = if (selected) KVariant.Tonal else KVariant.Glass,
        tone = if (selected) KTone.Accent else KTone.Neutral,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            KAvatar(chat.avatar, size = KAvatarSize.Large, status = if (chat.online) KAvatarStatus.Online else null)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label(chat.name, 16, FontWeight.SemiBold, Modifier.weight(1f, fill = false))
                    if (pinned) Label("고정", 11, FontWeight.SemiBold, Modifier.padding(start = 6.dp), color = KineticaTheme.colors.accent)
                }
                Label(preview, 14, color = ink.copy(alpha = if (unread > 0) 0.9f else 0.6f), modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Label(time, 12, color = ink.copy(alpha = 0.55f))
                Box(Modifier.size(22.dp).padding(top = 4.dp).kBadge(unread.takeIf { it > 0 }))
            }
        }
    }
}

@Composable
private fun Conversation(chat: Chat, sent: SnapshotStateList<String>, onClose: () -> Unit) {
    var draft by rememberSaveable(chat.id) { mutableStateOf("") }
    var typing by remember(chat.id) { mutableStateOf(false) }
    var attachOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Message?>(null) }
    val upload = remember(chat.id) { Upload() }
    val scope = rememberCoroutineScope()
    val messages = chat.history + sent.withIndex().filter { it.value.startsWith("${chat.id}|") }.map { parseSent(it.value, it.index) }
    val mine = messages.count { it.side == KChatSide.Sent }

    // 내가 보낼 때마다 잠시 뒤 상대가 입력 중 표시를 띄우고 답한다.
    LaunchedEffect(chat.id, mine) {
        if (mine <= chat.history.count { it.side == KChatSide.Sent }) return@LaunchedEffect
        delay(700)
        typing = true
        delay(1_600)
        typing = false
        sent.add("${chat.id}|r|t|${REPLIES[mine % REPLIES.size]}")
    }
    val send = { kind: Char, text: String -> sent.add("${chat.id}|s|$kind|$text") }

    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        background = { ChatBackdrop() },
        topBar = {
            KTopAppBar(
                chat.name,
                onBack = onClose,
                subtitle = when {
                    typing -> "입력 중…"
                    chat.online -> "온라인"
                    else -> "1시간 전 활동"
                },
                actions = {
                    KIconButton(ShowcaseIcons.Phone, "음성 통화", {}, variant = KVariant.Text, tone = KTone.Neutral)
                    KIconButton(KIcons.Video, "영상 통화", {}, variant = KVariant.Text, tone = KTone.Neutral)
                },
            )
        },
        bottomBar = {
            Composer(chat, draft, { draft = it }, upload, onAttach = { attachOpen = true }) {
                if (draft.isNotBlank()) send('t', draft.trim())
                draft = ""
            }
        },
    ) { padding -> MessageList(chat.id, messages, typing, padding) { menuFor = it } }
    KActionSheet(
        attachOpen,
        { attachOpen = false },
        listOf(
            KDialogAction("사진", icon = KIcons.Image) { upload.start(scope, "IMG_2048.jpg", 'p') { send('p', "사진") } },
            KDialogAction("파일", icon = KIcons.File) { upload.start(scope, "회의록_10월.pdf", 'f') { send('f', "회의록_10월.pdf") } },
            KDialogAction("취소", KDialogActionStyle.Preferred) {},
        ),
        title = "첨부",
    )
    MessageMenu(menuFor, { menuFor = null }, onReply = { draft = "「${it.text.take(12)}」 " }) { sent.remove(it.raw) }
}

/** 말풍선을 길게 누르면 뜨는 메뉴. 내가 보낸 메시지만 지울 수 있다. */
@Composable
private fun MessageMenu(target: Message?, onDismiss: () -> Unit, onReply: (Message) -> Unit, onDelete: (Message) -> Unit) {
    KActionSheet(
        target != null,
        onDismiss,
        listOfNotNull(
            KDialogAction("답장") { target?.let(onReply) },
            KDialogAction("복사") {},
            target?.takeIf { it.key.startsWith("s") }?.let { KDialogAction("삭제", KDialogActionStyle.Destructive) { onDelete(it) } },
        ),
        title = target?.text?.take(40),
    )
}

/** 올리는 중인 첨부 하나. 진행 막대를 1.4초 동안 채운 뒤 말풍선으로 보내고 칩을 치운다. */
@Stable
private class Upload {
    var file by mutableStateOf<Pair<String, Char>?>(null)
    val progress = Animatable(0f)

    fun start(scope: CoroutineScope, name: String, kind: Char, done: () -> Unit) {
        scope.launch {
            file = name to kind
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1_400, easing = LinearEasing))
            file = null
            done()
        }
    }
}

/** 대화 아래 입력줄. 올리는 중인 첨부가 있으면 입력란 위에 진행 칩을 띄운다. */
@Composable
private fun Composer(chat: Chat, draft: String, onDraft: (String) -> Unit, upload: Upload, onAttach: () -> Unit, onSend: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        upload.file?.let { (name, kind) ->
            val photo = kind == 'p'
            KAttachmentChip(
                name,
                detail = if (photo) "사진 · 2.4MB" else "PDF · 860KB",
                status = KAttachmentStatus.Uploading,
                progress = upload.progress.value,
                thumbnail = if (photo) ({ ArtBox(artPalette(chat.id + 2), 1, Modifier.size(40.dp), kitShape { image }) }) else null,
                onRemove = { upload.file = null },
            )
        }
        KChatInput(draft, onDraft, onSend = onSend, onAttach = onAttach, placeholder = "${chat.name}에게 메시지")
    }
}

/** 말풍선 목록. 새 메시지가 아래에 붙도록 뒤집어 그리고, 메시지가 늘거나 상대가 입력하기 시작하면 맨 아래로 내린다. */
@Composable
private fun MessageList(chatId: Int, messages: List<Message>, typing: Boolean, padding: PaddingValues, onLongClick: (Message) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, typing) { listState.animateScrollToItem(0) }
    val reversed = messages.asReversed()
    val lastMine = reversed.indexOfFirst { it.side == KChatSide.Sent }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
    ) {
        if (typing) item("typing") { Box(Modifier.padding(vertical = 4.dp)) { KTypingIndicator() } }
        items(reversed.size, key = { reversed[it].key }) { index ->
            val message = reversed[index]
            // 뒤집힌 목록이라 index-1이 다음(아래) 메시지다.
            val position = chatGroupPosition(
                reversed.getOrNull(index + 1)?.side == message.side,
                reversed.getOrNull(index - 1)?.side == message.side,
            )
            val status = when {
                message.side != KChatSide.Sent -> null
                index == lastMine && !typing -> KChatStatus.Delivered
                else -> KChatStatus.Read
            }
            MessageBubble(message, position, status, chatId) { onLongClick(message) }
        }
        item("day") {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), Alignment.Center) {
                KSurface(shape = kitShape { chip }) {
                    Label("오늘", 12, FontWeight.SemiBold, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    position: com.naury.kinetica.ui.KChatGroupPosition,
    status: KChatStatus?,
    chatId: Int,
    onLongClick: () -> Unit,
) {
    val time = if (position == com.naury.kinetica.ui.KChatGroupPosition.Last ||
        position == com.naury.kinetica.ui.KChatGroupPosition.Single
    ) {
        message.time
    } else {
        null
    }
    when (message.kind) {
        'p' -> KChatBubble(message.side, position = position, time = time, status = status, onLongClick = onLongClick) {
            ArtBox(artPalette(chatId + 2), message.key.hashCode(), Modifier.size(200.dp, 150.dp), kitShape { KShapeToken.Medium })
        }

        'f' -> KChatBubble(message.side, position = position, time = time, status = status, onLongClick = onLongClick) {
            KAttachmentChip(message.text, detail = "PDF · 860KB", thumbnail = null)
        }

        else -> KChatBubble(message.text, message.side, position = position, time = time, status = status, onLongClick = onLongClick)
    }
}

private class Message(val key: String, val side: KChatSide, val kind: Char, val text: String, val time: String, val raw: String = "") {
    val preview get() = when (kind) {
        'p' -> "사진을 보냈습니다"
        'f' -> "파일: $text"
        else -> text
    }
}

private fun parseSent(raw: String, index: Int): Message {
    val parts = raw.split('|', limit = 4)
    val side = if (parts[1] == "s") KChatSide.Sent else KChatSide.Received
    return Message("${parts[1]}$index", side, parts[2][0], parts[3], "오후 3:12", raw)
}

private class Chat(
    val id: Int,
    val name: String,
    val preview: String,
    val time: String,
    val unread: Int,
    val online: Boolean,
    history: List<Pair<Boolean, String>>,
) {
    val avatar = KAvatarData(name, artPalette(id + 5)[0])
    val history = history.mapIndexed { i, (mine, text) ->
        Message("h$id-$i", if (mine) KChatSide.Sent else KChatSide.Received, 't', text, "오후 2:${10 + i * 3}")
    }
}

private val CHATS = listOf(
    Chat(
        1,
        "디자인팀",
        "윤아: 시안 2안으로 가는 게 좋겠어요",
        "오후 2:41",
        3,
        true,
        listOf(
            false to "오늘 리뷰 자료 올려 두었어요",
            false to "2안 버튼 색이 조금 진한 것 같아서 한 번만 봐 주세요",
            true to "확인했어요! 대비는 충분한데 톤만 살짝 낮춰 볼게요",
            false to "좋아요",
            false to "시안 2안으로 가는 게 좋겠어요",
        ),
    ),
    Chat(
        2,
        "엄마",
        "저녁 먹고 들어오니?",
        "오후 1:15",
        1,
        false,
        listOf(true to "오늘 조금 늦을 것 같아요", false to "그래 알겠어", false to "저녁 먹고 들어오니?"),
    ),
    Chat(
        3,
        "서준",
        "주말에 등산 어때?",
        "오전 11:02",
        0,
        true,
        listOf(false to "주말에 등산 어때?", true to "좋지! 토요일 아침?", false to "7시에 역 앞에서 보자"),
    ),
    Chat(
        4,
        "도윤",
        "사진 보냈어요",
        "어제",
        0,
        true,
        listOf(false to "어제 찍은 사진이에요", false to "사진 보냈어요"),
    ),
    Chat(
        5,
        "독서 모임",
        "다음 책은 투표로 정해요",
        "어제",
        12,
        false,
        listOf(false to "이번 달 모임은 셋째 주 목요일이에요", false to "다음 책은 투표로 정해요"),
    ),
    Chat(
        6,
        "하은",
        "고마워 잘 받았어!",
        "월요일",
        0,
        false,
        listOf(true to "빌려준 책 우편함에 넣어 뒀어", false to "고마워 잘 받았어!"),
    ),
    Chat(
        7,
        "택배 알림",
        "오늘 도착 예정입니다",
        "월요일",
        0,
        false,
        listOf(false to "주문하신 상품이 오늘 도착 예정입니다"),
    ),
)
private val REPLIES = listOf("오 좋다 ㅎㅎ", "알겠어, 이따 다시 얘기하자", "잠깐만 확인해 볼게!", "고마워", "그래 그렇게 하자")

private val ROW_SHAPE: Shape
    @Composable
    @ReadOnlyComposable
    get() = kitShape { KShapeToken.Large }
internal val CHAT_LOOK = AppLook(
    SurfaceStyle.Glass(GlassStyles.Acrylic),
    SurfaceStyle.Glass(GlassStyles.AcrylicDark),
    defaultDark = false,
    accent = Color(0xFF6E56CF),
    accentDark = Color(0xFF9D8CFF),
)
