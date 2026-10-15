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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.AvatarStack
import com.naury.kinetica.ui.GlassAvatar
import com.naury.kinetica.ui.GlassMiniPlayer
import com.naury.kinetica.ui.GlassRating
import com.naury.kinetica.ui.GlassScaffold
import com.naury.kinetica.ui.GlassSkeletonText
import com.naury.kinetica.ui.GlassSlider
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.GlassSurface
import com.naury.kinetica.ui.LikeButton
import com.naury.kinetica.ui.PlayPauseIcon
import com.naury.kinetica.ui.rememberMiniPlayerState
import com.naury.kinetica.ui.skeleton
import kotlinx.coroutines.delay

/** 음악 앱: 처음 1.5초는 스켈레톤으로 불러오는 척하고, 아래 미니 플레이어를 펼치면 전체 화면 플레이어가 된다. */
@Composable
internal fun MusicApp(onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var track by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(1500)
        loading = false
    }
    val player = rememberMiniPlayerState()
    GlassScaffold(
        Modifier.fillMaxSize(),
        style = GlassStyles.LiquidDark,
        background = { Aurora(Modifier.fillMaxSize(), dark = true) },
        topBar = { DemoTopBar("음악", "미니 플레이어를 누르거나 위로 끌어 보세요", onBack) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Label("지금 많이 듣는 곡", 20, FontWeight.Bold, color = Color.White) }
            TRACKS.forEachIndexed { index, song ->
                item(song.title) {
                    TrackRow(song, loading, index == track && playing) {
                        track = index
                        playing = true
                    }
                }
            }
        }
        GlassMiniPlayer(
            player,
            artwork = { Artwork(TRACKS[track]) },
            collapsed = { MiniBar(TRACKS[track], playing) { playing = !playing } },
            expanded = { FullPlayer(TRACKS[track], playing) { playing = !playing } },
        )
    }
}

@Composable
private fun TrackRow(song: Song, loading: Boolean, current: Boolean, onClick: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).skeleton(loading).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(song.colors)))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                if (loading) {
                    GlassSkeletonText(2)
                } else {
                    Label(song.title, 16, FontWeight.SemiBold)
                    Label(song.artist, 13, color = Color.White.copy(alpha = 0.7f))
                }
            }
            if (current) Label("재생 중", 12, FontWeight.SemiBold, color = Color(0xFF7AE0FF))
        }
    }
}

@Composable
private fun Artwork(song: Song) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(song.colors)))
}

@Composable
private fun MiniBar(song: Song, playing: Boolean, toggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Label(song.title, 15, FontWeight.SemiBold)
            Label(song.artist, 12, color = Color.White.copy(alpha = 0.7f))
        }
        PlayButton(playing, 40, toggle)
    }
}

@Composable
private fun FullPlayer(song: Song, playing: Boolean, toggle: () -> Unit) {
    var liked by remember { mutableStateOf(false) }
    var likes by remember { mutableIntStateOf(1284) }
    var rating by remember { mutableFloatStateOf(4f) }
    var position by remember { mutableFloatStateOf(0.32f) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(song.title, 24, FontWeight.Bold)
                Label(song.artist, 15, color = Color.White.copy(alpha = 0.7f))
            }
            LikeButton(liked, {
                liked = it
                likes += if (it) 1 else -1
            }, count = likes)
        }
        GlassSlider(position, { position = it })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            PlayButton(playing, 72, toggle)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Label("이 곡 평가", 13, color = Color.White.copy(alpha = 0.7f))
            GlassRating(rating, { rating = it }, allowHalf = true, starSize = 24.dp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AvatarStack(FRIENDS, max = 4, size = 32.dp)
            Label("친구 6명이 이 곡을 좋아해요", 13, color = Color.White.copy(alpha = 0.8f))
        }
        LaunchedEffect(playing) {
            while (playing) {
                delay(1000)
                position = (position + 0.01f) % 1f
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
            .clickable(role = Role.Button, onClick = toggle)
            .semantics { contentDescription = if (playing) "일시 정지" else "재생" },
        contentAlignment = Alignment.Center,
    ) { PlayPauseIcon(playing, Modifier.size((size / 2).dp), Color.White) }
}

private class Song(val title: String, val artist: String, val colors: List<Color>)

private val TRACKS = listOf(
    Song("밤하늘의 별", "오로라", listOf(Color(0xFF8E54E9), Color(0xFF4776E6))),
    Song("파도", "블루 웨이브", listOf(Color(0xFF00C6FF), Color(0xFF0072FF))),
    Song("노을 산책", "선셋 클럽", listOf(Color(0xFFFF7E5F), Color(0xFFFEB47B))),
    Song("숲속의 아침", "그린 노트", listOf(Color(0xFF11998E), Color(0xFF38EF7D))),
    Song("도시의 밤", "네온", listOf(Color(0xFFFF5C8A), Color(0xFF8E54E9))),
    Song("첫눈", "윈터", listOf(Color(0xFFB993D6), Color(0xFF8CA6DB))),
)
private val FRIENDS = listOf("민지", "서준", "하은", "도윤", "지우", "Ana").map { GlassAvatar(it) }
