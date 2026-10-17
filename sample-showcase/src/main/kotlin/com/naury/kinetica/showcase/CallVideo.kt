// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KActionSheet
import com.naury.kinetica.ui.KAvatar
import com.naury.kinetica.ui.KAvatarSize
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KDialogAction
import com.naury.kinetica.ui.KDialogActionStyle
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KIconToggleButton
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KIndexBar
import com.naury.kinetica.ui.KIndexBarDefaults
import com.naury.kinetica.ui.KIslandContent
import com.naury.kinetica.ui.KIslandHost
import com.naury.kinetica.ui.KIslandResult
import com.naury.kinetica.ui.KKeypad
import com.naury.kinetica.ui.KKeypadMode
import com.naury.kinetica.ui.KListItem
import com.naury.kinetica.ui.KListLeading
import com.naury.kinetica.ui.KListSection
import com.naury.kinetica.ui.KListTrailing
import com.naury.kinetica.ui.KSheet
import com.naury.kinetica.ui.KSlideToConfirm
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KTab
import com.naury.kinetica.ui.KTabBar
import com.naury.kinetica.ui.KToaster
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalGlassBackdrop
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.LocalScaffoldBackground
import com.naury.kinetica.ui.PushNavigation
import com.naury.kinetica.ui.RollingText
import com.naury.kinetica.ui.SurfaceStyle
import com.naury.kinetica.ui.glowBorder
import com.naury.kinetica.ui.jellyPress
import com.naury.kinetica.ui.rememberKIslandState
import com.naury.kinetica.ui.rememberKToasterState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 그룹 영상 통화. 영상 타일은 `background`에, 말하는 사람 글로우 테두리는 `content`에 같은 자리로 그린다(docs/35 '영상 위 글래스').
 * 아래 도크는 배경만 비추게 해서 테두리가 돌 때마다 도크 블러를 다시 하지 않는다.
 */
@Composable
internal fun VideoCall(startedAt: Long, island: com.naury.kinetica.ui.KIslandState, onEnd: () -> Unit) {
    val meeting = rememberSaveable(saver = Meeting.Saver) { Meeting() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_400)
            meeting.speaking = (meeting.speaking + 1) % meeting.people
        }
    }
    // 잠시 뒤 채팅 한 줄이 아일랜드로 들어온다.
    val accent = KineticaTheme.colors.accent
    LaunchedEffect(Unit) {
        delay(4_000)
        island.show(KIslandContent("윤아", "화면 잘 보여요! 자료 공유해 주세요", KIcons.Message, accent), durationMs = 3_500)
    }
    LaunchedEffect(meeting.hand) {
        if (meeting.hand) {
            val content = KIslandContent("손을 들었어요", "진행자에게 알렸어요", AppIcons.Hand, Color(0xFFFFCC00))
            island.show(content, durationMs = 2_500, key = "hand", replaceShowing = true)
        }
    }
    val tiles = (0 until meeting.people).toList()
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        edgeFade = false,
        background = {
            VideoGrid(tiles) { index, modifier ->
                VideoTile(
                    index,
                    if (index == 0 &&
                        !meeting.camera
                    ) {
                        null
                    } else {
                        index
                    },
                    modifier,
                )
            }
        },
        topBar = {
            // 바 뒤로 보일 것은 영상뿐이라 배경만 비추게 해, 콘텐츠 층의 테두리가 돌 때 바 블러를 다시 하지 않는다.
            CompositionLocalProvider(LocalGlassBackdrop provides LocalScaffoldBackground.current) { MeetingBar(meeting, startedAt) }
        },
        bottomBar = {
            CompositionLocalProvider(LocalGlassBackdrop provides LocalScaffoldBackground.current) { MeetingDock(meeting, onEnd) }
        },
    ) { _ -> SpeakerOverlay(tiles, meeting) }
    KSheet(meeting.roster, { meeting.roster = false }, title = "참가자") { Roster(tiles, meeting) }
}

/** 영상 통화 화면 상태. 회전하거나 접었다 펴도 남는다. */
@Stable
private class Meeting(people: Int = 4, muted: Boolean = false, camera: Boolean = true, hand: Boolean = false) {
    var people by mutableIntStateOf(people)
    var muted by mutableStateOf(muted)
    var camera by mutableStateOf(camera)
    var hand by mutableStateOf(hand)
    var roster by mutableStateOf(false)
    var speaking by mutableIntStateOf(1)

    companion object {
        val Saver = listSaver<Meeting, Any>(
            save = { listOf(it.people, it.muted, it.camera, it.hand) },
            restore = { Meeting(it[0] as Int, it[1] as Boolean, it[2] as Boolean, it[3] as Boolean) },
        )
    }
}

@Composable
private fun MeetingBar(meeting: Meeting, startedAt: Long) {
    val ink = LocalKineticaContentColor.current
    KSurface(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(12.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label("주간 디자인 싱크", 15, FontWeight.SemiBold)
                RollingText(rememberCallElapsed(startedAt), style = TextStyle(color = ink.copy(alpha = 0.7f), fontSize = 12.sp))
            }
            KSurface({ meeting.roster = true }, shape = CircleShape, onClickLabel = "참가자 보기") {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShowcaseIcon(KIcons.Users, 18.dp)
                    RollingText(
                        "${meeting.people}",
                        Modifier.padding(start = 6.dp),
                        style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

@Composable
private fun MeetingDock(meeting: Meeting, onEnd: () -> Unit) {
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp), Alignment.Center) {
        KSurface(shape = RoundedCornerShape(36.dp)) {
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KIconToggleButton(meeting.muted, {
                    meeting.muted = it
                }, KIcons.Mic, "음소거", checkedIcon = KIcons.MicOff, size = 52.dp, tone = KTone.Neutral)
                KIconToggleButton(
                    !meeting.camera,
                    { meeting.camera = !it },
                    KIcons.Video,
                    "카메라 끄기",
                    checkedIcon = KIcons.VideoOff,
                    size = 52.dp,
                    tone = KTone.Neutral,
                )
                KIconToggleButton(meeting.hand, { meeting.hand = it }, AppIcons.Hand, "손 들기", size = 52.dp)
                KIconButton(KIcons.Users, "참가자", { meeting.roster = true }, size = 52.dp, tone = KTone.Neutral)
                KIconButton(KIcons.CallEnd, "나가기", onEnd, variant = KVariant.Filled, tone = KTone.Destructive, size = 52.dp)
            }
        }
    }
}

/** 배경 타일과 같은 자리에 말하는 사람 테두리와 이름표만 올린다. */
@Composable
private fun SpeakerOverlay(tiles: List<Int>, meeting: Meeting) {
    VideoGrid(tiles) { index, modifier ->
        Box(modifier) {
            if (index == meeting.speaking && !(index == 0 && meeting.muted)) {
                Box(Modifier.fillMaxSize().glowBorder(TILE_SHAPE, SPEAKER_GLOW, width = 3.dp, glow = 10.dp))
            }
            Label(
                if (index == 0) "나" else CALL_CONTACTS[index].name,
                13,
                FontWeight.SemiBold,
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun Roster(tiles: List<Int>, meeting: Meeting) {
    Label("참가자 ${meeting.people}명", 20, FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    tiles.forEach { i ->
        val mutedMe = i == 0 && meeting.muted
        KListItem(
            if (i == 0) "나 (김하늘)" else CALL_CONTACTS[i].name,
            supporting = when {
                i == meeting.speaking -> "말하는 중"
                mutedMe -> "음소거"
                else -> null
            },
            leading = KListLeading.Custom { KAvatar(if (i == 0) CALL_ME else CALL_CONTACTS[i].avatar, size = KAvatarSize.Small) },
            trailing = KListTrailing.Custom { ShowcaseIcon(if (mutedMe) KIcons.MicOff else KIcons.Mic, 20.dp) },
        )
    }
    Spacer(Modifier.height(8.dp))
    KButton(
        "참가자 초대",
        { if (meeting.people < 6) meeting.people++ },
        fullWidth = true,
        enabled = meeting.people < 6,
        variant = KVariant.Tonal,
        icon = KIcons.Plus,
    )
}

/** 참가자 수와 화면 비율에 맞춰 타일을 격자로 놓는다. 배경과 콘텐츠 층이 같은 배치를 쓰도록 한 곳에서 계산한다. */
@Composable
private fun VideoGrid(tiles: List<Int>, tile: @Composable (Int, Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 8.dp, vertical = 84.dp)) {
        val landscape = maxWidth > maxHeight
        val columns = when {
            tiles.size <= 1 -> 1
            landscape -> minOf(3, tiles.size)
            else -> 2
        }
        val rows = (tiles.size + columns - 1) / columns
        val gap = 8.dp
        val w = (maxWidth - gap * (columns - 1)) / columns
        val h = (maxHeight - gap * (rows - 1)) / rows
        tiles.forEachIndexed { i, index ->
            val col = i % columns
            val row = i / columns
            tile(index, Modifier.offset { IntOffset(((w + gap) * col).roundToPx(), ((h + gap) * row).roundToPx()) }.size(w, h))
        }
    }
}

/** 카메라 영상 대신 천천히 흐르는 그라디언트와 사람 실루엣. [seed]가 null이면 카메라가 꺼진 타일이다. */
@Composable
private fun VideoTile(index: Int, seed: Int?, modifier: Modifier) {
    val flow = rememberInfiniteTransition(label = "video")
    val t by flow.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(6_000 + index * 900, easing = LinearEasing), RepeatMode.Reverse),
        label = "flow",
    )
    val colors = artPalette(index * 3 + 1)
    Box(modifier.background(Color(0xFF1C1C22), TILE_SHAPE), Alignment.Center) {
        if (seed == null) {
            KAvatar(if (index == 0) CALL_ME else CALL_CONTACTS[index].avatar, size = KAvatarSize.Large)
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val radius = 24.dp.toPx()
                drawRoundRect(
                    Brush.linearGradient(colors, Offset(size.width * t, 0f), Offset(size.width * (1 - t), size.height)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                )
                val cx = size.width / 2 + (t - 0.5f) * size.width * 0.06f
                val head = size.minDimension * 0.16f
                drawCircle(Color.Black.copy(alpha = 0.22f), head, Offset(cx, size.height * 0.45f))
                drawOval(
                    Color.Black.copy(alpha = 0.22f),
                    Offset(cx - head * 2f, size.height * 0.45f + head * 1.3f),
                    androidx.compose.ui.geometry.Size(head * 4f, head * 3f),
                )
            }
        }
    }
}

private val TILE_SHAPE = RoundedCornerShape(24.dp)
private val SPEAKER_GLOW = listOf(Color(0xFF34C759), Color(0xFF7CFFB2), Color(0xFF34C759))
