// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KCallActions
import com.naury.kinetica.ui.KCallDock
import com.naury.kinetica.ui.KCallDockItem
import com.naury.kinetica.ui.KCallIcons
import com.naury.kinetica.ui.KCallLayer
import com.naury.kinetica.ui.KCallParticipant
import com.naury.kinetica.ui.KCallPhase
import com.naury.kinetica.ui.KCallScreen
import com.naury.kinetica.ui.KCallState
import com.naury.kinetica.ui.KCallTopBar
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KIslandContent
import com.naury.kinetica.ui.KIslandState
import com.naury.kinetica.ui.KNetworkQuality
import com.naury.kinetica.ui.KParticipantGrid
import com.naury.kinetica.ui.KParticipantTile
import com.naury.kinetica.ui.KRaisedHandPill
import com.naury.kinetica.ui.KReactionOverlay
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.LocalGlassBackdrop
import com.naury.kinetica.ui.LocalScaffoldBackground
import com.naury.kinetica.ui.kCallChromeTouches
import com.naury.kinetica.ui.rememberKCallChromeState
import com.naury.kinetica.ui.rememberKParticipantGridState
import com.naury.kinetica.ui.rememberKReactionState
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * 그룹 영상 통화. 같은 상태를 두 방식으로 그린다.
 * - 완성: `KCallScreen` 하나에 상태와 콜백만 넘긴다.
 * - 직접 조합: `KCallTopBar`, `KParticipantGrid`, `KParticipantTile`, `KCallDock`, `KReactionOverlay`를 호스트가 직접 배치한다.
 *   영상은 `background`에 [KCallLayer.Video], 이름표와 글로우 테두리는 `content`에 [KCallLayer.Overlay]로 같은 자리에 그린다(docs/35 '영상 위 글래스').
 * 카메라 대신 천천히 흐르는 그라디언트를 영상 슬롯에 넣는다.
 */
@Composable
internal fun VideoCall(startedAt: Long, island: KIslandState, onEnd: () -> Unit) {
    val meeting = rememberSaveable(saver = Meeting.Saver) { Meeting() }
    var composed by rememberSaveable { mutableStateOf(false) }
    val reactions = rememberKReactionState()
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_400)
            meeting.speaking = 1 + (meeting.speaking % (meeting.people - 1).coerceAtLeast(1))
        }
    }
    // 잠시 뒤 채팅 한 줄과 반응이 들어온다.
    val accent = com.naury.kinetica.ui.KineticaTheme.colors.accent
    LaunchedEffect(Unit) {
        delay(4_000)
        island.show(KIslandContent("윤아", "화면 잘 보여요! 자료 공유해 주세요", KIcons.Message, accent), durationMs = 3_500)
        reactions.send("👏", "윤아")
    }
    val clock = rememberFrameClock()
    val level: (String) -> Float = { id -> fakeLevel(id, clock.value, id == "p${meeting.speaking}") }
    val state = meeting.toState(startedAt)
    val switch = @Composable {
        KButton(if (composed) "완성 화면" else "직접 조합", {
            composed = !composed
        }, size = KButtonSize.Small, variant = KVariant.Glass, tone = KTone.Neutral)
    }
    if (composed) {
        ComposedCall(meeting, state, level, reactions, onEnd, switch)
    } else {
        KCallScreen(
            state = state,
            actions = meeting.actions(onEnd, reactions),
            video = { p -> FakeVideo(p) },
            audioLevel = level,
            reactions = reactions,
            island = island,
            topBarActions = { switch() },
        )
    }
}

/**
 * 부품으로 직접 조합한 통화 화면. 호스트가 자기 상태를 그대로 넘기고, 도크에는 '효과' 같은 앱 고유 버튼을 섞는다.
 * 완성 화면과 같은 부품이라 모양과 동작이 같다.
 */
@Composable
private fun ComposedCall(
    meeting: Meeting,
    state: KCallState,
    level: (String) -> Float,
    reactions: com.naury.kinetica.ui.KReactionState,
    onEnd: () -> Unit,
    switch: @Composable () -> Unit,
) {
    val chrome = rememberKCallChromeState(autoHide = true, holdVisible = meeting.sharing)
    val grid = rememberKParticipantGridState()
    val tiles: @Composable (KCallLayer) -> Unit = { layer -> ComposedTiles(meeting, state, level, chrome, grid, layer) }
    KineticaScaffold(
        Modifier.fillMaxSize().kCallChromeTouches(chrome),
        systemBarIcons = false,
        edgeFade = false,
        background = { tiles(KCallLayer.Video) },
        topBar = {
            VideoOnly {
                AnimatedVisibility(
                    chrome.isVisible,
                    enter = fadeIn() + slideInVertically { -it },
                    exit =
                        fadeOut() + slideOutVertically { -it },
                ) {
                    KCallTopBar(
                        Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(12.dp),
                        title = "주간 디자인 싱크",
                        startedAtMillis = state.startedAtMillis,
                        participantCount = state.participants.size,
                        quality = KNetworkQuality.Good,
                        recording = true,
                        actions = { switch() },
                    )
                }
            }
        },
        bottomBar = {
            VideoOnly {
                AnimatedVisibility(
                    chrome.isVisible,
                    enter = fadeIn() + slideInVertically { it },
                    exit =
                        fadeOut() + slideOutVertically { it },
                ) {
                    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp), Alignment.Center) {
                        KCallDock(meeting.dockItems(onEnd, reactions))
                    }
                }
            }
        },
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            tiles(KCallLayer.Overlay)
            Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 96.dp), Alignment.TopCenter) {
                KRaisedHandPill(meeting.hand, { meeting.hand = false })
            }
            KReactionOverlay(reactions, bottomPadding = 140.dp)
        }
    }
}

/** 직접 조합한 무대 한 층. 두 층이 같은 [grid] 상태를 넘겨 같은 자리에 그린다. */
@Composable
private fun ComposedTiles(
    meeting: Meeting,
    state: KCallState,
    level: (String) -> Float,
    chrome: com.naury.kinetica.ui.KCallChromeState,
    grid: com.naury.kinetica.ui.KParticipantGridState,
    layer: KCallLayer,
) {
    // 바 높이만큼 무대를 비운다. 완성 화면은 바 높이를 재서 맞추고, 여기서는 시스템 바 인셋에 대략의 바 높이를 더한다.
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val stage = PaddingValues(
        start = 8.dp,
        top = insets.calculateTopPadding() + 84.dp,
        end = 8.dp,
        bottom =
            insets.calculateBottomPadding() + 112.dp,
    )
    KParticipantGrid(state.participants, key = {
        it.id
    }, Modifier.fillMaxSize(), grid, focusKey = meeting.pinned, selfKey = "p0", contentPadding = stage, layer = layer) { p, _ ->
        KParticipantTile(
            if (p.isLocal) "나" else p.name,
            video = if (p.isVideoOn) ({ FakeVideo(p) }) else null,
            avatar = p.avatar,
            muted = p.isMuted,
            speaking = p.isSpeaking,
            audioLevel = { level(p.id) },
            handRaised = p.hasRaisedHand,
            quality = p.networkQuality,
            pinned = p.id == meeting.pinned,
            mirror = p.isLocal,
            onClick = { if (chrome.isVisible) meeting.pinned = p.id.takeIf { meeting.pinned != it } else chrome.show() },
            onTogglePin = { meeting.pinned = p.id.takeIf { meeting.pinned != it } },
            layer = layer,
        )
    }
}

/** 바가 영상만 비추게 한다. 콘텐츠 층의 테두리가 돌 때 바 블러를 다시 하지 않는다. */
@Composable
private fun VideoOnly(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlassBackdrop provides LocalScaffoldBackground.current, content = content)
}

/** 영상 통화 화면 상태. 회전하거나 접었다 펴도 남는다. */
@Stable
private class Meeting(people: Int = 5, muted: Boolean = false, camera: Boolean = true, hand: Boolean = false) {
    var people by mutableIntStateOf(people)
    var muted by mutableStateOf(muted)
    var camera by mutableStateOf(camera)
    var hand by mutableStateOf(hand)
    var sharing by mutableStateOf(false)
    var pinned by mutableStateOf<String?>(null)
    var speaking by mutableIntStateOf(1)

    fun toState(startedAt: Long): KCallState = KCallState(
        phase = KCallPhase.Connected,
        participants = (0 until people).map { i ->
            KCallParticipant(
                id = "p$i",
                name = if (i == 0) CALL_ME.name else CALL_CONTACTS[i].name,
                avatar = if (i == 0) CALL_ME else CALL_CONTACTS[i].avatar,
                isLocal = i == 0,
                isMuted = if (i == 0) muted else i == 3,
                isVideoOn = if (i == 0) camera else i != 2,
                isSpeaking = i == speaking,
                hasRaisedHand = if (i == 0) hand else i == 4,
                networkQuality = if (i == 3) KNetworkQuality.Poor else KNetworkQuality.Good,
            )
        },
        title = "주간 디자인 싱크",
        startedAtMillis = startedAt,
        isRecording = true,
        isEncrypted = true,
        isSharingScreen = sharing,
        unreadMessages = 2,
    )

    fun actions(onEnd: () -> Unit, reactions: com.naury.kinetica.ui.KReactionState): KCallActions = KCallActions(
        onEnd = onEnd,
        onToggleMute = { muted = !muted },
        onToggleCamera = { camera = !camera },
        onSwitchCamera = {},
        onToggleSpeaker = {},
        onShareScreen = { sharing = !sharing },
        onRaiseHand = { hand = !hand },
        onReact = { reactions.send(it) },
        onOpenChat = {},
        onAddPeople = { if (people < CALL_CONTACTS.size) people++ },
    )

    fun dockItems(onEnd: () -> Unit, reactions: com.naury.kinetica.ui.KReactionState): List<KCallDockItem> = listOf(
        KCallDockItem("mic", KIcons.Mic, "마이크", { muted = !muted }, checked = muted, checkedIcon = KIcons.MicOff, essential = true),
        KCallDockItem("camera", KIcons.Video, "카메라", {
            camera = !camera
        }, checked = !camera, checkedIcon = KIcons.VideoOff, essential = true),
        KCallDockItem("share", KCallIcons.ScreenShare, "화면 공유", { sharing = !sharing }, checked = sharing, tone = KTone.Accent),
        KCallDockItem("hand", KCallIcons.Hand, "손 들기", { hand = !hand }, checked = hand, tone = KTone.Accent),
        KCallDockItem("effects", KCallIcons.Effects, "효과", { reactions.send("✨") }),
        KCallDockItem("people", KIcons.Users, "초대", { if (people < CALL_CONTACTS.size) people++ }),
        KCallDockItem("end", KIcons.CallEnd, "나가기", onEnd, tone = KTone.Destructive, essential = true),
    )

    companion object {
        val Saver = listSaver<Meeting, Any>(
            save = { listOf(it.people, it.muted, it.camera, it.hand) },
            restore = { Meeting(it[0] as Int, it[1] as Boolean, it[2] as Boolean, it[3] as Boolean) },
        )
    }
}

/** 매 프레임 바뀌는 시각(ns). 그리기 단계에서만 읽어 소리 크기를 흉내 낸다. */
@Composable
private fun rememberFrameClock(): State<Long> {
    val time = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { time.longValue = it }
    }
    return time
}

/** 말하는 사람은 출렁이는 소리 크기, 나머지는 0 */
private fun fakeLevel(id: String, nanos: Long, speaking: Boolean): Float {
    if (!speaking) return 0f
    val t = nanos / 1_000_000_000f + id.hashCode() % 7
    return (0.35f + 0.65f * abs(sin(t * 5.3f) * sin(t * 2.1f))).coerceIn(0f, 1f)
}

/** 카메라 영상 대신 천천히 흐르는 그라디언트와 사람 실루엣 */
@Composable
private fun BoxScope.FakeVideo(p: KCallParticipant) {
    val index = p.id.drop(1).toIntOrNull() ?: 0
    val flow = rememberInfiniteTransition(label = "video")
    val t by flow.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(6_000 + index * 900, easing = LinearEasing), RepeatMode.Reverse),
        label = "flow",
    )
    val colors = artPalette(index * 3 + 1)
    Box(Modifier.matchParentSize().background(Color(0xFF1C1C22)))
    Canvas(Modifier.matchParentSize()) {
        drawRect(Brush.linearGradient(colors, Offset(size.width * t, 0f), Offset(size.width * (1 - t), size.height)))
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
