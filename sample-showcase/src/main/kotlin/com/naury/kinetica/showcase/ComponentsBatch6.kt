// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.AuroraBackground
import com.naury.kinetica.ui.KAttachmentChip
import com.naury.kinetica.ui.KAttachmentStatus
import com.naury.kinetica.ui.KAvatar
import com.naury.kinetica.ui.KAvatarSize
import com.naury.kinetica.ui.KAvatarStatus
import com.naury.kinetica.ui.KBreadcrumb
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KChatBubble
import com.naury.kinetica.ui.KChatBubbleDefaults
import com.naury.kinetica.ui.KChatInput
import com.naury.kinetica.ui.KChatSide
import com.naury.kinetica.ui.KChatStatus
import com.naury.kinetica.ui.KFileDropZone
import com.naury.kinetica.ui.KIndexBar
import com.naury.kinetica.ui.KIndexBarDefaults
import com.naury.kinetica.ui.KMapPin
import com.naury.kinetica.ui.KPagination
import com.naury.kinetica.ui.KPaginationStyle
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KStoryRing
import com.naury.kinetica.ui.KStoryViewer
import com.naury.kinetica.ui.KTypingIndicator
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.PaletteBackground
import com.naury.kinetica.ui.StarfieldBackground
import com.naury.kinetica.ui.chatGroupPosition
import com.naury.kinetica.ui.kClickable
import com.naury.kinetica.ui.rememberImagePalette
import com.naury.kinetica.ui.rememberKStaggerState
import com.naury.kinetica.ui.rememberKStoryViewerState
import com.naury.kinetica.ui.staggerIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 소셜·모션 탭: 채팅, 스토리, 첨부, 연락처 색인, 페이지 이동, 지도 핀, 전환, 마이크로 인터랙션, 배경 효과. */
@Composable
internal fun SocialMotionTab(padding: PaddingValues, connection: NestedScrollConnection) {
    ComponentTabList(padding, connection) {
        item { ChatScene() }
        item { StoriesCard() }
        item { FilesCard() }
        item { ContactsCard() }
        item { PagesCard() }
        item { MapCard() }
        item { TransitionsCard() }
        item { ListMotionCard() }
        item { MicroCard() }
        item { MorphCard() }
        item { SkyCard() }
    }
}

private data class ChatMessage(val id: Int, val text: String, val mine: Boolean, val time: String)

/** 메신저 화면 한 장. 보내면 읽음 표시가 붙고, 상대가 입력 중 점을 보이다 답한다. */
@Composable
private fun ChatScene() {
    val messages = remember { CHAT_SEED.toMutableStateList() }
    var draft by rememberSaveable { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf<Float?>(null) }
    val stagger = rememberKStaggerState()
    val scope = rememberCoroutineScope()
    DemoCard("채팅 장면", "같은 사람이 이어 보낸 말풍선은 묶이고 마지막에만 꼬리가 붙습니다. 보내면 상대가 입력 중 점을 보이다 답합니다. + 버튼은 파일을 첨부합니다.") {
        ChatHeader(typing)
        ChatMessages(messages, typing, stagger)
        uploading?.let {
            KAttachmentChip(
                "회의자료_최종.pdf",
                Modifier.fillMaxWidth(),
                detail = "2.4MB",
                status = KAttachmentStatus.Uploading,
                progress = it,
                onRemove = { uploading = null },
            )
        }
        KChatInput(
            draft,
            { draft = it },
            onSend = {
                messages += ChatMessage(messages.size, draft.trim(), mine = true, time = "오후 2:1${messages.size % 10}")
                draft = ""
                scope.launch {
                    delay(500)
                    typing = true
                    delay(1_600)
                    typing = false
                    messages +=
                        ChatMessage(
                            messages.size,
                            REPLIES[messages.size % REPLIES.size],
                            mine = false,
                            time = "오후 2:2${messages.size % 10}",
                        )
                }
            },
            onAttach = {
                scope.launch {
                    for (step in 0..20) {
                        uploading = step / 20f
                        delay(120)
                    }
                    uploading = null
                    messages += ChatMessage(messages.size, "📎 회의자료_최종.pdf", mine = true, time = "오후 2:30")
                }
            },
            onVoice = {},
        )
    }
}

/** 같은 사람이 이어 보낸 메시지는 묶고, 사람이 바뀌는 곳에만 간격과 시간, 읽음 표시를 둔다. */
@Composable
private fun ChatMessages(messages: List<ChatMessage>, typing: Boolean, stagger: com.naury.kinetica.ui.KStaggerState) {
    Column(verticalArrangement = Arrangement.spacedBy(KChatBubbleDefaults.GroupSpacing)) {
        messages.forEachIndexed { i, m ->
            val prev = messages.getOrNull(i - 1)
            val next = messages.getOrNull(i + 1)
            if (prev != null && prev.mine != m.mine) Box(Modifier.height(KChatBubbleDefaults.Spacing))
            KChatBubble(
                m.text,
                if (m.mine) KChatSide.Sent else KChatSide.Received,
                Modifier.staggerIn(i, stagger),
                position = chatGroupPosition(prev?.mine == m.mine, next?.mine == m.mine),
                time = m.time.takeIf { next?.mine != m.mine },
                status = if (m.mine && next?.mine != m.mine) KChatStatus.Read else null,
            )
        }
        if (typing) KTypingIndicator(Modifier.padding(top = KChatBubbleDefaults.Spacing))
    }
}

@Composable
private fun ChatHeader(typing: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KAvatar(KAvatar("서연"), size = KAvatarSize.Medium, status = KAvatarStatus.Online)
        Column {
            Label("서연", 16, FontWeight.SemiBold)
            Label(if (typing) "입력 중…" else "온라인", 12, color = LocalKineticaContentColor.current.copy(alpha = 0.6f))
        }
    }
}

/** 스토리 링 줄. 누르면 링이 잠깐 돌다 뷰어가 열리고, 다 본 사람은 회색 링이 된다. */
@Composable
private fun StoriesCard() {
    var seen by rememberSaveable { mutableStateOf(setOf<Int>()) }
    var loading by remember { mutableIntStateOf(-1) }
    var open by remember { mutableIntStateOf(-1) }
    val story = rememberKStoryViewerState(STORY_PAGES.size)
    LaunchedEffect(loading) {
        if (loading >= 0) {
            delay(600)
            story.jumpTo(0)
            open = loading
            loading = -1
        }
    }
    DemoCard("스토리", "안 본 스토리는 그라디언트 링입니다. 화면 오른쪽을 누르면 다음, 왼쪽은 이전, 길게 누르면 멈추고 아래로 끌면 닫힙니다.") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(PEOPLE_STORIES.size) { i ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    KStoryRing(i in seen, loading = loading == i, onClick = { loading = i }, onClickLabel = "스토리 보기") {
                        KAvatar(KAvatar(PEOPLE_STORIES[i]), size = KAvatarSize.Large)
                    }
                    Label(PEOPLE_STORIES[i], 12)
                }
            }
        }
    }
    KStoryViewer(open >= 0, {
        seen = seen + open
        open = -1
    }, story, header = { page ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KAvatar(KAvatar(PEOPLE_STORIES.getOrElse(open) { "" }), size = KAvatarSize.Small)
            Label("${PEOPLE_STORIES.getOrElse(open) { "" }}  ·  ${page + 1}시간 전", 14, FontWeight.SemiBold, color = Color.White)
        }
    }) { page ->
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(STORY_PAGES[page])), contentAlignment = Alignment.Center) {
            Label(STORY_CAPTIONS[page], 28, FontWeight.Bold, color = Color.White)
        }
    }
}

/** 파일 영역을 누르면 파일 세 개가 차례로 올라가고, 하나는 실패해 다시 시도할 수 있다. */
@Composable
private fun FilesCard() {
    val progress = remember { mutableStateListOf<Float>() }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val names = listOf("여행_사진_001.jpg", "견적서_2026_10.pdf", "녹음_회의.m4a")
    fun upload(i: Int) = scope.launch {
        while (progress.size <= i) progress += 0f
        for (s in 1..25) {
            delay(80L + i * 30)
            progress[i] = s / 25f
            if (i == 2 && s == 12 && !failed) {
                failed = true
                return@launch
            }
        }
    }
    DemoCard("파일 첨부", "영역을 누르면 업로드가 시작됩니다. 분할 화면에서는 다른 앱의 파일을 끌어다 놓을 수도 있습니다.") {
        KFileDropZone({
            names.indices.forEach { upload(it) }
        }, subtitle = "PDF, 이미지, 음성 최대 20MB", onDrop = { names.indices.forEach { upload(it) } })
        progress.forEachIndexed { i, p ->
            val status = when {
                i == 2 && failed -> KAttachmentStatus.Failed
                p >= 1f -> KAttachmentStatus.Ready
                else -> KAttachmentStatus.Uploading
            }
            KAttachmentChip(
                names[i],
                Modifier.fillMaxWidth(),
                detail = listOf("3.1MB", "820KB", "12.4MB")[i],
                status = status,
                progress = p,
                onRemove = { progress[i] = 1f },
                onRetry = {
                    failed = false
                    upload(i)
                },
            )
        }
    }
}

/** 연락처 목록과 한글·로마자 색인 막대 */
@Composable
private fun ContactsCard() {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val labels = KIndexBarDefaults.HangulAndLatin
    // 색인 막대 순서(한글 다음 로마자)대로 정렬해야 고른 글자의 첫 항목을 찾을 수 있다.
    val contacts = remember { CONTACTS.sortedWith(compareBy({ labels.indexOf(KIndexBarDefaults.keyOf(it)) }, { it })) }
    val current by remember {
        androidx.compose.runtime.derivedStateOf { labels.indexOf(KIndexBarDefaults.keyOf(contacts[list.firstVisibleItemIndex])) }
    }
    DemoCard("연락처 색인", "오른쪽 막대를 위아래로 끌면 큰 글자 말풍선이 따라오고 그 초성으로 이동합니다.") {
        Box(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(16.dp))) {
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(end = 28.dp)) {
                items(contacts.size) { i ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        KAvatar(KAvatar(contacts[i]), size = KAvatarSize.Small)
                        Label(contacts[i], 15)
                    }
                }
            }
            KIndexBar(labels, { i ->
                val target = contacts.indexOfFirst { labels.indexOf(KIndexBarDefaults.keyOf(it)) >= i }
                if (target >= 0) scope.launch { list.scrollToItem(target) }
            }, Modifier.align(Alignment.CenterEnd).fillMaxHeight(), selected = current)
        }
    }
}

@Composable
private fun PagesCard() {
    var page by rememberSaveable { mutableIntStateOf(4) }
    var path by rememberSaveable { mutableStateOf(listOf("내 드라이브", "디자인", "2026", "브랜드", "아이콘", "최종")) }
    DemoCard("페이지와 경로", "페이지 수가 많으면 처음과 끝을 남기고 가운데를 줄입니다. 경로는 길면 접히고 '…'을 누르면 펼쳐집니다.") {
        KPagination(page, 24, { page = it })
        KPagination(page, 24, { page = it }, style = KPaginationStyle.Compact)
        KBreadcrumb(path, { i -> if (i < path.lastIndex) path = path.take(i + 1) })
        if (path.size <
            6
        ) {
            KButton("경로 되돌리기", {
                path = listOf("내 드라이브", "디자인", "2026", "브랜드", "아이콘", "최종")
            }, variant = KVariant.Tonal, size = KButtonSize.Small)
        }
    }
}

/** 그린 지도 위의 가격 핀, 묶음 핀, 물방울 핀. 누르면 커지고 다시 넣으면 떨어지며 나타난다. */
@Composable
private fun MapCard() {
    var picked by rememberSaveable { mutableIntStateOf(1) }
    var round by remember { mutableIntStateOf(0) }
    DemoCard("지도 핀", "가격 핀을 누르면 강조색으로 바뀌며 커집니다. 다시 놓기를 누르면 핀이 위에서 떨어지며 튑니다.") {
        Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)).drawBehind { drawFakeMap() }) {
            androidx.compose.runtime.key(round) {
                PIN_SPOTS.forEachIndexed { i, (x, y) ->
                    KMapPin(
                        Modifier.offset(x.dp, y.dp),
                        label = PIN_PRICES[i],
                        selected = picked == i,
                        onClick = { picked = i },
                    )
                }
                KMapPin(Modifier.offset(250.dp, 150.dp), count = 8, onClick = {})
                KMapPin(Modifier.offset(40.dp, 170.dp), onClick = {}, contentDescription = "현재 위치")
            }
        }
        KButton("핀 다시 놓기", { round++ }, variant = KVariant.Tonal, size = KButtonSize.Small)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFakeMap() {
    drawRect(Color(0xFFE8EEE4))
    drawRect(Color(0xFFBFE0F5), Offset(size.width * 0.62f, 0f), androidx.compose.ui.geometry.Size(size.width * 0.38f, size.height * 0.45f))
    val road = Color.White
    for (k in 1..4) drawLine(road, Offset(0f, size.height * k / 5f), Offset(size.width, size.height * k / 5f - 30f), strokeWidth = 14f)
    for (k in 1..3) drawLine(road, Offset(size.width * k / 4f, 0f), Offset(size.width * k / 4f + 40f, size.height), strokeWidth = 10f)
    drawCircle(Color(0xFFCDE6C3), size.minDimension * 0.18f, Offset(size.width * 0.2f, size.height * 0.3f))
}

/** 이미지 대표색 배경을 보여 줄 앨범 표지. 그라디언트를 비트맵에 그려 만든다. */
internal fun coverBitmap(colors: List<Color>): ImageBitmap {
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        shader =
            android.graphics.LinearGradient(
                0f,
                0f,
                64f,
                64f,
                colors.map {
                    it.toArgb()
                }.toIntArray(),
                null,
                android.graphics.Shader.TileMode.CLAMP,
            )
    }
    canvas.drawRect(0f, 0f, 64f, 64f, paint)
    return bitmap.asImageBitmap()
}

/** 오로라, 별빛, 앨범 표지 대표색 배경을 고른다. */
@Composable
private fun SkyCard() {
    var kind by rememberSaveable { mutableIntStateOf(0) }
    var cover by rememberSaveable { mutableIntStateOf(0) }
    val image = remember(cover) { coverBitmap(COVERS[cover]) }
    val palette = rememberImagePalette(image)
    DemoCard("배경 효과", "화면 밖으로 스크롤하거나 앱을 내리면 움직임을 멈춥니다. 대표색 배경은 표지를 바꾸면 색이 부드럽게 넘어갑니다.") {
        KSegmentedControl(listOf("오로라", "별빛", "대표색"), kind, { kind = it }, Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp))) {
            when (kind) {
                0 -> AuroraBackground()

                1 -> StarfieldBackground()

                else -> {
                    PaletteBackground(palette)
                    Row(Modifier.align(Alignment.BottomCenter).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        COVERS.forEachIndexed { i, colors ->
                            Box(
                                Modifier
                                    .size(if (i == cover) 56.dp else 44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brush.linearGradient(colors))
                                    .kClickable(RoundedCornerShape(10.dp), onClickLabel = "표지 ${i + 1}") { cover = i },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val CHAT_SEED = listOf(
    ChatMessage(0, "내일 회의 몇 시에 할까요?", mine = false, time = "오후 2:01"),
    ChatMessage(1, "자료는 오늘 밤에 올려 둘게요", mine = false, time = "오후 2:01"),
    ChatMessage(2, "2시 괜찮아요", mine = true, time = "오후 2:04"),
    ChatMessage(3, "3층 회의실로 잡아 둘게요. 화면 연결 케이블도 챙겨 갈게요.", mine = true, time = "오후 2:05"),
)
private val REPLIES = listOf("좋아요 👍", "고마워요! 내일 봬요", "확인했어요", "자료 받았어요")
private val PEOPLE_STORIES = listOf("민지", "하늘", "준호", "Ana", "서연", "바다", "Leo")
private val STORY_PAGES = listOf(
    listOf(Color(0xFFFF9A8B), Color(0xFFFF6A88)),
    listOf(Color(0xFF43CEA2), Color(0xFF185A9D)),
    listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
)
private val STORY_CAPTIONS = listOf("오늘의 산책", "바다 보러 왔어요", "밤 산책 🌙")
private val CONTACTS = listOf(
    "강민지", "고하늘", "김서연", "김준호", "나영", "남궁도윤", "도하은", "류지안", "문수아", "박지후", "배서준", "서다인", "손예린",
    "송민재", "안유나", "오시우", "윤하린", "이도현", "임채원", "장서윤", "정우진", "조아라", "최예준", "한지민", "허윤서", "홍길동",
    "Ana Lee", "Ben Park", "Chloe", "David Kim", "Emma", "Leo", "Mia", "Noah", "Olivia", "Sophia",
)
private val PIN_SPOTS = listOf(30 to 30, 140 to 70, 220 to 20, 110 to 140)
private val PIN_PRICES = listOf("₩82,000", "₩120,000", "₩64,000", "₩98,000")
private val COVERS = listOf(
    listOf(Color(0xFFFF512F), Color(0xFFF09819)),
    listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
    listOf(Color(0xFF8E2DE2), Color(0xFFFF6CAB)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
)
