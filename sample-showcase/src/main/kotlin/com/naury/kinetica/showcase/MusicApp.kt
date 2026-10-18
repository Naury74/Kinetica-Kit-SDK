// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naury.kinetica.ui.AvatarStack
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KAvatar
import com.naury.kinetica.ui.KAvatarData
import com.naury.kinetica.ui.KFloatingTabBar
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KIndication
import com.naury.kinetica.ui.KMiniPlayerBar
import com.naury.kinetica.ui.KMiniPlayerFullScreen
import com.naury.kinetica.ui.KRating
import com.naury.kinetica.ui.KSkeletonText
import com.naury.kinetica.ui.KSlider
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KTab
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LikeButton
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.PlayPauseIcon
import com.naury.kinetica.ui.isReducedMotion
import com.naury.kinetica.ui.rememberKFloatingTabBarState
import com.naury.kinetica.ui.rememberKMiniPlayerState
import com.naury.kinetica.ui.skeleton
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * 음악 앱: 처음 1.5초는 스켈레톤으로 불러오는 척한다. 아래 떠 있는 탭 바는 목록을 내리면 작은 캡슐로 줄고,
 * 탭 바 위에 붙은 미니 플레이어 막대를 누르거나 위로 끌면 앨범 아트가 이어지며 커지는 전체 화면 플레이어가 열린다.
 */
@Composable
internal fun MusicApp(onBack: () -> Unit, model: MusicViewModel = viewModel()) {
    var loading by remember { mutableStateOf(true) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val track = model.track
    LaunchedEffect(Unit) {
        delay(1500)
        loading = false
    }
    val bar = rememberKFloatingTabBarState()
    val list = rememberLazyListState()
    val player = rememberKMiniPlayerState()
    val song = TRACKS[track]
    KineticaScaffold(
        Modifier.fillMaxSize(),
        surface = demoSurface(MUSIC_APP_LOOK),
        background = { DemoBackdrop() },
        topBar = { DemoTopBar("음악", "목록을 내리면 탭 바가 줄어듭니다", onBack, scrolled = { list.canScrollBackward }) },
        bottomBar = {
            KFloatingTabBar(MUSIC_TABS, tab, { tab = it }, state = bar, accessory = {
                KMiniPlayerBar(player, artwork = { CoverArt(song) }) { MiniBar(song, model.playing, model::togglePlaying) }
            })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().nestedScroll(bar.nestedScrollConnection),
            state = list,
            contentPadding = PaddingValues(
                start = readingPadding(),
                end = readingPadding(),
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (tab == 0) {
                item { Label("오늘의 추천", 20, FontWeight.Bold) }
                item { FeaturedRow(model::play) }
                item { Spacer(Modifier.height(4.dp)) }
            }
            item { Label(MUSIC_HEADERS[tab], 20, FontWeight.Bold) }
            tabTracks(tab).forEachIndexed { rank, index ->
                item(TRACKS[index].title) {
                    TrackRow(rank + 1, TRACKS[index], loading, index == track && model.playing) { model.play(index) }
                }
            }
        }
        KMiniPlayerFullScreen(player, artwork = { CoverArt(song) }) { FullPlayer(song, model) }
    }
}

/** 탭마다 보여 줄 곡 번호. 둘러보기는 순서를 뒤집고, 보관함은 좋아요한 곡만 보여 준다. */
private fun tabTracks(tab: Int): List<Int> = when (tab) {
    1 -> TRACKS.indices.reversed().toList()
    2 -> listOf(0, 2, 4)
    else -> TRACKS.indices.toList()
}

@Composable
private fun TrackRow(rank: Int, song: Song, loading: Boolean, current: Boolean, onClick: () -> Unit) {
    val muted = LocalKineticaContentColor.current.copy(alpha = 0.62f)
    KSurface(onClick, Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                if (current) {
                    PlayingBars(KineticaTheme.colors.accent)
                } else {
                    Label("$rank", 15, FontWeight.Bold, color = muted)
                }
            }
            Box(Modifier.padding(start = 6.dp).size(54.dp).skeleton(loading).clip(kitInnerShape(10.dp))) { CoverArt(song) }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                if (loading) {
                    KSkeletonText(2)
                } else {
                    Label(
                        song.title,
                        16,
                        FontWeight.SemiBold,
                        color = if (current) KineticaTheme.colors.accent else LocalKineticaContentColor.current,
                    )
                    Label("${song.artist} · ${song.album}", 13, color = muted)
                }
            }
            if (!loading) Label(song.duration, 13, color = muted)
        }
    }
}

/** 재생 중인 곡 앞에서 오르내리는 막대 세 개 */
@Composable
private fun PlayingBars(color: Color) {
    val reduced = isReducedMotion()
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { time.floatValue = (it - start) / 1000f }
    }
    Canvas(Modifier.size(16.dp).semantics { contentDescription = "재생 중" }) {
        val bar = size.width / 5
        repeat(3) { i ->
            val wave = if (reduced) 0.6f else 0.35f + 0.65f * abs(sin(time.floatValue * 5f + i * 1.7f))
            val h = size.height * wave
            drawRoundRect(color, Offset(bar * (i * 2), size.height - h), Size(bar, h), CornerRadius(bar / 2))
        }
    }
}

/** 오늘의 추천: 큰 앨범 표지를 가로로 늘어놓는다. */
@Composable
private fun FeaturedRow(onPlay: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(FEATURED.size) { i ->
            val index = FEATURED[i]
            val song = TRACKS[index]
            Column(Modifier.width(148.dp).clip(kitInnerShape(14.dp)).clickable { onPlay(index) }) {
                Box(Modifier.size(148.dp).clip(kitInnerShape(14.dp))) { CoverArt(song) }
                Label(song.album, 15, FontWeight.SemiBold, Modifier.padding(top = 8.dp))
                Label(song.artist, 13, color = LocalKineticaContentColor.current.copy(alpha = 0.62f))
            }
        }
    }
}

private val FEATURED = listOf(2, 0, 4, 1)

/** 곡마다 다른 그림의 앨범 표지. 실제 앱이라면 이미지가 들어갈 자리다. */
@Composable
private fun CoverArt(song: Song) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.linearGradient(song.colors, start = Offset.Zero, end = Offset(size.width, size.height)))
        val w = size.width
        val h = size.height
        val white = Color.White
        when (song.cover) {
            Cover.Stars -> {
                drawCircle(white.copy(alpha = 0.9f), w * 0.16f, Offset(w * 0.68f, h * 0.32f))
                drawCircle(song.colors.first(), w * 0.14f, Offset(w * 0.62f, h * 0.27f))
                STAR_POINTS.forEach { (x, y, r) -> drawCircle(white.copy(alpha = 0.85f), w * r, Offset(w * x, h * y)) }
            }

            Cover.Waves -> repeat(4) { i ->
                val path = Path()
                val base = h * (0.45f + i * 0.14f)
                path.moveTo(0f, base)
                path.cubicTo(w * 0.25f, base - h * 0.1f, w * 0.5f, base + h * 0.1f, w, base - h * 0.04f)
                path.lineTo(w, h)
                path.lineTo(0f, h)
                path.close()
                drawPath(path, white.copy(alpha = 0.14f + i * 0.06f))
            }

            Cover.Sunset -> {
                drawCircle(Color(0xFFFFF1C1), w * 0.24f, Offset(w * 0.5f, h * 0.62f))
                repeat(4) { i ->
                    drawRect(song.colors.first().copy(alpha = 0.55f), Offset(0f, h * (0.62f + i * 0.07f)), Size(w, h * 0.025f))
                }
                drawRect(song.colors.first().copy(alpha = 0.8f), Offset(0f, h * 0.86f), Size(w, h * 0.14f))
            }

            Cover.Hills -> listOf(0.55f to 0.25f, 0.68f to 0.4f, 0.8f to 0.6f).forEach { (top, alpha) ->
                val path = Path()
                path.moveTo(0f, h * (top + 0.1f))
                path.quadraticTo(w * 0.35f, h * (top - 0.12f), w * 0.7f, h * top)
                path.quadraticTo(w * 0.9f, h * (top + 0.05f), w, h * (top - 0.02f))
                path.lineTo(w, h)
                path.lineTo(0f, h)
                path.close()
                drawPath(path, Color(0xFF06261C).copy(alpha = alpha))
            }

            Cover.Neon -> {
                drawCircle(Color(0xFFFF4FD8).copy(alpha = 0.85f), w * 0.2f, Offset(w * 0.5f, h * 0.42f))
                repeat(6) { i ->
                    val y = h * (0.62f + i * i * 0.012f)
                    drawLine(Color(0xFF00E5FF).copy(alpha = 0.7f), Offset(0f, y), Offset(w, y), strokeWidth = 1.2f.dp.toPx())
                }
                repeat(7) { i ->
                    val x = w * (i / 6f)
                    drawLine(
                        Color(0xFF00E5FF).copy(alpha = 0.5f),
                        Offset(w / 2 + (x - w / 2) * 0.25f, h * 0.62f),
                        Offset(x, h),
                        1.2f.dp.toPx(),
                    )
                }
            }

            Cover.Snow -> SNOW_POINTS.forEach { (x, y, r) -> drawCircle(white.copy(alpha = 0.9f), w * r, Offset(w * x, h * y)) }
        }
    }
}

private val STAR_POINTS = listOf(
    Triple(0.18f, 0.2f, 0.012f),
    Triple(0.3f, 0.55f, 0.009f),
    Triple(0.12f, 0.72f, 0.014f),
    Triple(0.45f, 0.15f, 0.008f),
    Triple(0.85f, 0.62f, 0.011f),
    Triple(0.55f, 0.78f, 0.01f),
    Triple(0.78f, 0.85f, 0.008f),
    Triple(0.38f, 0.38f, 0.007f),
)

private val SNOW_POINTS = listOf(
    Triple(0.15f, 0.18f, 0.02f), Triple(0.42f, 0.1f, 0.014f), Triple(0.7f, 0.22f, 0.024f), Triple(0.88f, 0.12f, 0.012f),
    Triple(0.25f, 0.45f, 0.016f), Triple(0.55f, 0.4f, 0.02f), Triple(0.82f, 0.5f, 0.015f), Triple(0.1f, 0.7f, 0.022f),
    Triple(0.4f, 0.68f, 0.012f), Triple(0.65f, 0.75f, 0.02f), Triple(0.9f, 0.82f, 0.014f), Triple(0.3f, 0.9f, 0.018f),
)

@Composable
private fun MiniBar(song: Song, playing: Boolean, toggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Label(song.title, 14, FontWeight.SemiBold)
            Label("${song.artist} · ${song.album}", 11, color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
        }
        PlayButton(playing, 36, toggle)
    }
}

@Composable
private fun FullPlayer(song: Song, model: MusicViewModel) {
    val track = model.track
    // 폰처럼 내용 자리가 화면 폭 전체이면 큰 아트와 같은 가장자리 여백을 둔다. 넓은 화면에서는 플레이어가 이미 바깥 여백과
    // 아트와의 간격을 두고 자리를 주므로, 여기서 또 여백을 두면 아트와 내용 사이만 벌어진다.
    val windowWidth = LocalWindowInfo.current.containerSize.width
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = if (constraints.maxWidth >= windowWidth - 1) ART_SIDE else 0.dp
        FullPlayerBody(song, model, track, side)
    }
}

@Composable
private fun FullPlayerBody(song: Song, model: MusicViewModel, track: Int, side: Dp) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
            start = side,
            end = side,
            bottom =
                navigationBarBottom() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(song.title, 24, FontWeight.Bold)
                Label("${song.artist} · ${song.album}", 15, color = Color.White.copy(alpha = 0.7f))
            }
            LikeButton(model.isLiked(track), { model.setLiked(track, it) }, count = model.likes(track))
        }
        KSlider(model.position, { model.position = it })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            PlayButton(model.playing, 72, model::togglePlaying)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Label("이 곡 평가", 13, color = Color.White.copy(alpha = 0.7f))
            KRating(model.rating(track), { model.setRating(track, it) }, allowHalf = true, starSize = 24.dp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AvatarStack(FRIENDS, max = 4, size = 32.dp)
            Label("친구 6명이 이 곡을 좋아해요", 13, color = Color.White.copy(alpha = 0.8f))
        }
        LaunchedEffect(model.playing) {
            while (model.playing) {
                delay(1000)
                model.position = (model.position + 0.01f) % 1f
            }
        }
    }
}

@Composable
private fun PlayButton(playing: Boolean, size: Int, toggle: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(null, KIndication(CircleShape, Color.White), role = Role.Button, onClick = toggle)
            .semantics { contentDescription = if (playing) "일시 정지" else "재생" },
        contentAlignment = Alignment.Center,
    ) { PlayPauseIcon(playing, Modifier.size((size / 2).dp), Color.White) }
}

private val MUSIC_TABS =
    listOf(KTab("홈", KIcons.Home), KTab("둘러보기", ShowcaseIcons.Sparkle), KTab("보관함", KIcons.Grid))
private val MUSIC_HEADERS = listOf("지금 많이 듣는 곡", "새로 나온 곡", "좋아요한 곡")

/** 펼친 플레이어가 큰 아트 양옆에 두는 여백. 글자 시작선을 아트에 맞춘다. */
private val ART_SIDE = 32.dp

private enum class Cover { Stars, Waves, Sunset, Hills, Neon, Snow }

private class Song(
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val colors: List<Color>,
    val cover: Cover,
)

private val TRACKS = listOf(
    Song("Starlight", "오로라", "Night Bloom", "3:28", listOf(Color(0xFF2B1055), Color(0xFF7597DE)), Cover.Stars),
    Song("파도 위의 우리", "블루웨이브", "Summer Tide", "3:51", listOf(Color(0xFF00C6FF), Color(0xFF0050C8)), Cover.Waves),
    Song("Golden Hour", "선셋클럽", "Afterglow", "4:05", listOf(Color(0xFFFF5E62), Color(0xFFFFC371)), Cover.Sunset),
    Song("숲으로 가는 길", "그린노트", "Morning Walk", "3:12", listOf(Color(0xFF0B486B), Color(0xFF3BB78F)), Cover.Hills),
    Song("Neon City", "네온블룸", "Midnight Drive", "2:58", listOf(Color(0xFF12002E), Color(0xFFE0119D)), Cover.Neon),
    Song("첫눈이 오면", "윈터", "White Letter", "3:44", listOf(Color(0xFF5B6FA8), Color(0xFFC9D6FF)), Cover.Snow),
)
private val FRIENDS = listOf("민지", "서준", "하은", "도윤", "지우", "Ana").map { KAvatarData(it) }

/**
 * 음악 데모 상태. 화면보다 오래 살아 있어 음악 화면을 나갔다 들어오거나 회전해도 고른 곡, 재생 여부,
 * 곡마다 누른 좋아요와 평점이 남는다. 실제 앱이라면 저장소에 둘 값이다.
 */
internal class MusicViewModel : ViewModel() {
    var track by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
        private set

    /** 재생 위치. 0 이상 1 미만이다. */
    var position by mutableFloatStateOf(START_POSITION)
    private val liked = mutableStateMapOf<Int, Boolean>()
    private val ratings = mutableStateMapOf<Int, Float>()

    fun play(index: Int) {
        track = index
        playing = true
    }

    fun togglePlaying() {
        playing = !playing
    }

    fun isLiked(index: Int): Boolean = liked[index] == true

    fun setLiked(index: Int, value: Boolean) {
        liked[index] = value
    }

    /** 다른 사람이 누른 좋아요 수에 내 좋아요를 더한다. */
    fun likes(index: Int): Int = BASE_LIKES + index * LIKES_STEP + if (isLiked(index)) 1 else 0

    fun rating(index: Int): Float = ratings[index] ?: DEFAULT_RATING

    fun setRating(index: Int, value: Float) {
        ratings[index] = value
    }

    private companion object {
        const val START_POSITION = 0.32f
        const val BASE_LIKES = 1284
        const val LIKES_STEP = 317
        const val DEFAULT_RATING = 4f
    }
}

private val MUSIC_APP_LOOK = DemoLook(GlassStyles.Liquid, GlassStyles.LiquidDark, defaultDark = true)
