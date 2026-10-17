// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
// 예시 데이터 클래스는 필드를 생성자에 나란히 두는 편이 읽기 쉬워 인자가 많다.
@file:Suppress("MagicNumber", "LongParameterList")

package com.naury.kinetica.showcase

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KAdaptiveDefaults
import com.naury.kinetica.ui.KAdaptiveNavigation
import com.naury.kinetica.ui.KAvatar
import com.naury.kinetica.ui.KAvatarSize
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KChatInput
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KIconToggleButton
import com.naury.kinetica.ui.KProfileHeader
import com.naury.kinetica.ui.KProfileStat
import com.naury.kinetica.ui.KSearchBar
import com.naury.kinetica.ui.KSheet
import com.naury.kinetica.ui.KStoryRing
import com.naury.kinetica.ui.KStoryViewer
import com.naury.kinetica.ui.KTab
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.LikeButton
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.SurfaceStyle
import com.naury.kinetica.ui.kCombinedClickable
import com.naury.kinetica.ui.rememberKStoryViewerState
import kotlinx.coroutines.launch

/**
 * 소셜 피드 앱: 스토리 링, 게시물(두 번 눌러 좋아요), 댓글 바텀 시트, 둘러보기 격자, 프로필 탭. 사진이 돋보이도록 테두리 없는 검은 단색 표면을 쓴다.
 * 좁은 화면에서는 아래 탭 바, 펼친 폴더블과 태블릿에서는 옆 내비게이션 레일로 바뀐다.
 */
@Composable
internal fun FeedApp(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val liked = rememberIntMap(2 to 1)
    val saved = rememberIntMap()
    // 단 댓글은 "게시물 id|내용"으로 담는다.
    val comments = rememberStringList()
    AppTheme(FEED_LOOK) {
        KineticaScaffold(Modifier.fillMaxSize(), systemBarIcons = false, edgeFade = false) { _ ->
            KAdaptiveNavigation(
                FEED_TABS,
                tab,
                { tab = it },
                railHeader = { KIconButton(ShowcaseIcons.Back, "뒤로", onBack, variant = KVariant.Text, tone = KTone.Neutral) },
            ) { padding ->
                when (tab) {
                    0 -> HomeFeed(padding, liked, saved, comments, onBack)
                    1 -> Explore(padding)
                    else -> Profile(padding, liked)
                }
            }
        }
    }
}

@Composable
private fun HomeFeed(
    padding: PaddingValues,
    liked: SnapshotStateMap<Int, Int>,
    saved: SnapshotStateMap<Int, Int>,
    comments: SnapshotStateList<String>,
    onBack: () -> Unit,
) {
    val seen = rememberIntMap()
    var storyOpen by rememberSaveable { mutableStateOf(false) }
    var commentsFor by rememberSaveable { mutableStateOf<Int?>(null) }
    val story = rememberKStoryViewerState(PEOPLE.size)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = feedSide(padding),
            end = feedSide(padding),
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item("bar") { FeedHeader(onBack) }
        item("stories") {
            StoryRow(seen) { index ->
                story.jumpTo(index)
                storyOpen = true
            }
        }
        items(POSTS, key = { it.id }) { post ->
            PostCard(
                post,
                liked[post.id] == 1,
                { liked[post.id] = if (it) 1 else 0 },
                saved[post.id] == 1,
                { saved[post.id] = if (it) 1 else 0 },
                comments.count { it.startsWith("${post.id}|") },
            ) { commentsFor = post.id }
        }
    }
    LaunchedEffect(storyOpen, story.page) { if (storyOpen) seen[story.page] = 1 }
    Stories(storyOpen, { storyOpen = false }, story)
    val target = commentsFor
    KSheet(target != null, { commentsFor = null }, title = "댓글") {
        if (target != null) CommentSheet(target, comments)
    }
}

@Composable
private fun FeedHeader(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 넓은 화면에서는 레일 맨 위에 뒤로 버튼이 있으므로 여기서는 뺀다.
        if (LocalConfiguration.current.screenWidthDp.dp < KAdaptiveDefaults.TwoPaneMinWidth) {
            KIconButton(ShowcaseIcons.Back, "뒤로", onBack, variant = KVariant.Text, tone = KTone.Neutral)
        }
        Label("모먼트", 24, FontWeight.Black, Modifier.weight(1f).padding(start = 4.dp))
        KIconButton(AppIcons.Heart, "알림", {}, variant = KVariant.Text, tone = KTone.Neutral)
        KIconButton(ShowcaseIcons.Send, "메시지", {}, variant = KVariant.Text, tone = KTone.Neutral)
    }
}

@Composable
private fun StoryRow(seen: SnapshotStateMap<Int, Int>, onOpen: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(PEOPLE.size) { index ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                KStoryRing(seen[index] == 1, onClick = { onOpen(index) }, onClickLabel = "스토리 보기") {
                    KAvatar(KAvatar(PEOPLE[index], artPalette(index + 1)[0]), size = KAvatarSize.Large)
                }
                Label(if (index == 0) "내 스토리" else PEOPLE[index], 12, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** 사람마다 한 장씩인 전체 화면 스토리. */
@Composable
private fun Stories(open: Boolean, onClose: () -> Unit, story: com.naury.kinetica.ui.KStoryViewerState) {
    KStoryViewer(
        open,
        onClose,
        story,
        header = { page ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                KAvatar(KAvatar(PEOPLE[page], artPalette(page + 1)[0]), size = KAvatarSize.Small)
                Label(PEOPLE[page], 14, FontWeight.SemiBold, Modifier.padding(start = 10.dp), color = Color.White)
                Label("${page * 2 + 1}시간", 13, modifier = Modifier.padding(start = 8.dp), color = Color.White.copy(alpha = 0.7f))
            }
        },
    ) { page ->
        ArtBox(artPalette(page + 3), page, Modifier.fillMaxSize()) {
            Label(
                STORY_TEXT[page % STORY_TEXT.size],
                26,
                FontWeight.Bold,
                Modifier.align(Alignment.Center).padding(32.dp),
                color = Color.White,
            )
        }
    }
}

/** 넓은 화면에서 피드 폭을 600dp 남짓으로 묶는 양옆 여백. 레일이 있으면 그 폭만큼 시작 쪽 여백이 이미 들어 있다. */
@Composable
private fun feedSide(padding: PaddingValues) = appPadding(
    0.dp,
    600.dp + padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
)

@Composable
private fun PostCard(
    post: Post,
    liked: Boolean,
    onLike: (Boolean) -> Unit,
    saved: Boolean,
    onSave: (Boolean) -> Unit,
    added: Int,
    onComments: () -> Unit,
) {
    val ink = LocalKineticaContentColor.current
    val pop = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        PostHeader(post)
        Box(
            Modifier.fillMaxWidth().aspectRatio(post.ratio).kCombinedClickable(
                onClickLabel = null,
                onDoubleClick = {
                    onLike(true)
                    scope.launch {
                        pop.snapTo(0f)
                        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
                        pop.animateTo(0f, tween(260, delayMillis = 350))
                    }
                },
                role = null,
            ) {},
            contentAlignment = Alignment.Center,
        ) {
            ArtBox(artPalette(post.id * 3), post.id, Modifier.fillMaxSize())
            ShowcaseIcon(
                AppIcons.Heart,
                96.dp,
                Color.White,
                Modifier.graphicsLayer {
                    alpha = pop.value.coerceIn(0f, 1f)
                    scaleX = 0.6f + pop.value * 0.4f
                    scaleY = 0.6f + pop.value * 0.4f
                },
            )
        }
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            LikeButton(liked, onLike, count = post.likes + if (liked) 1 else 0)
            KIconButton(AppIcons.Comment, "댓글 ${post.comments + added}개", onComments, variant = KVariant.Text, tone = KTone.Neutral)
            KIconButton(ShowcaseIcons.Send, "공유", {}, variant = KVariant.Text, tone = KTone.Neutral)
            Spacer(Modifier.weight(1f))
            KIconToggleButton(saved, onSave, AppIcons.Bookmark, if (saved) "저장 취소" else "저장", variant = KVariant.Text, tone = KTone.Neutral)
        }
        Label("${post.author}  ${post.caption}", 14, modifier = Modifier.padding(horizontal = 14.dp))
        Label(
            "댓글 ${post.comments + added}개 모두 보기",
            13,
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = 4.dp,
            ).kCombinedClickable(RoundedCornerShape(6.dp), onClick = onComments),
            color = ink.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun PostHeader(post: Post) {
    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        KStoryRing(seen = post.id % 2 == 0, ringWidth = 2.dp, gap = 2.dp) {
            KAvatar(KAvatar(post.author, artPalette(post.id + 1)[0]), size = KAvatarSize.Small)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Label(post.author, 14, FontWeight.SemiBold)
            Label(post.place, 12, color = LocalKineticaContentColor.current.copy(alpha = 0.6f))
        }
        KIconButton(AppIcons.More, "더 보기", {}, variant = KVariant.Text, tone = KTone.Neutral)
    }
}

@Composable
private fun CommentSheet(postId: Int, comments: SnapshotStateList<String>) {
    var draft by rememberSaveable { mutableStateOf("") }
    val mine = comments.filter { it.startsWith("$postId|") }.map { "나" to it.substringAfter('|') }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        (COMMENTS + mine).forEachIndexed { index, (who, text) ->
            Row(verticalAlignment = Alignment.Top) {
                KAvatar(KAvatar(who, artPalette(index + postId)[0]), size = KAvatarSize.Small)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Label(who, 13, FontWeight.SemiBold)
                    Label(text, 14)
                }
            }
        }
        KChatInput(
            draft,
            { draft = it },
            onSend = {
                if (draft.isNotBlank()) comments.add("$postId|${draft.trim()}")
                draft = ""
            },
            placeholder = "댓글 달기",
        )
    }
}

@Composable
private fun Explore(padding: PaddingValues) {
    var query by rememberSaveable { mutableStateOf("") }
    LazyVerticalGrid(
        GridCells.Adaptive(120.dp),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + 2.dp,
            end = 2.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            KSearchBar(
                query,
                { query = it },
                {},
                Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(12.dp),
                placeholder = "검색",
            )
        }
        items(30) { ArtBox(artPalette(it * 7), it, Modifier.aspectRatio(1f)) }
    }
}

@Composable
private fun Profile(padding: PaddingValues, liked: SnapshotStateMap<Int, Int>) {
    var following by rememberSaveable { mutableStateOf(false) }
    LazyVerticalGrid(
        GridCells.Adaptive(120.dp),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), Alignment.TopCenter) {
                KProfileHeader(
                    "김하늘",
                    Modifier.widthIn(max = 600.dp).fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(12.dp),
                    avatar = KAvatar("김하늘", Color(0xFFFF6B6B)),
                    subtitle = "필름 카메라와 산책을 좋아해요 · 서울",
                    cover = { ArtBox(artPalette(8), 2, Modifier.fillMaxSize()) },
                    stats = listOf(
                        KProfileStat("128", "게시물"),
                        KProfileStat(if (following) "2,481" else "2,480", "팔로워"),
                        KProfileStat("${312 + liked.values.sum()}", "좋아요"),
                    ),
                    actions = {
                        KButton(
                            if (following) "팔로잉" else "팔로우",
                            { following = !following },
                            Modifier.weight(1f),
                            variant = if (following) KVariant.Tonal else KVariant.Filled,
                            size = KButtonSize.Small,
                        )
                        KButton("메시지", {}, Modifier.weight(1f), variant = KVariant.Tonal, size = KButtonSize.Small)
                    },
                )
            }
        }
        items(18) { ArtBox(artPalette(it * 5 + 2), it + 40, Modifier.aspectRatio(1f)) }
    }
}

private class Post(
    val id: Int,
    val author: String,
    val place: String,
    val caption: String,
    val likes: Int,
    val comments: Int,
    val ratio: Float = 1f,
)

private val FEED_TABS = listOf(
    KTab("홈", ShowcaseIcons.Home),
    KTab("둘러보기", AppIcons.Search),
    KTab("프로필", AppIcons.Person, dot = true),
)
private val PEOPLE = listOf("하늘", "서준", "윤아", "도윤", "지민", "하은", "태오", "다인")
private val STORY_TEXT = listOf("오늘의 하늘", "드디어 주말!", "새로 연 카페", "밤 산책", "첫 필름 현상")
private val POSTS = listOf(
    Post(1, "윤아", "제주 애월", "바다 색이 이렇게 예쁠 줄이야. 다음엔 일출 보러 다시 올게요.", 1_284, 42, 0.8f),
    Post(2, "도윤", "성수동", "골목 끝에서 찾은 작은 서점. 창가 자리가 명당이에요.", 356, 12),
    Post(3, "지민", "한강 공원", "퇴근하고 자전거 한 바퀴. 바람이 딱 좋았어요.", 892, 27, 1.25f),
    Post(4, "태오", "부산 영도", "흰여울 마을 계단에서 해 질 녘.", 2_031, 88, 0.8f),
)
private val COMMENTS = listOf(
    "서준" to "와 색감 진짜 좋다",
    "하은" to "여기 어디예요? 저장해 둘게요",
    "다인" to "다음에 같이 가요!",
)

internal val FEED_LOOK = AppLook(
    SurfaceStyle.Solid(Color.White, Color(0xFF111111), Color.White, border = Color.Black.copy(alpha = 0.08f), borderWidth = 1.dp),
    SurfaceStyle.Solid(Color(0xFF161616), Color(0xFFF2F2F2), Color.Black, border = Color.White.copy(alpha = 0.08f), borderWidth = 1.dp),
    defaultDark = true,
    accent = Color(0xFFFF4D6D),
)
