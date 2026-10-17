package com.naury.kinetica.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 통화 화면 전체. 상태 하나([state])와 콜백 묶음([actions])을 받아 상태 화면, 상단 바, 참가자 격자, 조작 막대, 경고 배너, 반응, 입퇴장 알림,
 * 참가자와 오디오와 반응 시트를 모두 배치한다. 안쪽은 [KCallTopBar], [KParticipantGrid], [KParticipantTile], [KCallDock], [KCallStatusView],
 * [KCallBanner], [KReactionOverlay], [KParticipantRow], [KAudioRouteList]로만 만들었으므로, 일부만 바꾸고 싶으면 같은 부품으로 직접 조합하면 된다.
 *
 * - 단계: 연결 중과 호출 중, 종료, 실패는 상태 화면을, 통화 중과 재연결 중은 통화 화면을 그린다. 단계가 바뀌면 TalkBack이 '연결됨'처럼 읽는다.
 * - 화면 모드: [KCallState.isVideoCall]이 false면 음성 화면(1:1은 큰 아바타, 그룹은 아바타 격자), true면 영상 무대다.
 *   영상은 1:1이면 상대가 화면 전체에 내 화면이 모서리 작은 창, 여럿이면 격자다. 화면 공유가 있으면 공유 화면을 크게 놓는다.
 * - 타일: 누르면 크게 보고, 길게 누르면 고정한다. 크게 볼 때 큰 타일을 누르면 조작 막대를 숨기거나 보인다. 3명 이상이면 상단 바에서
 *   발표자 보기로 바꿀 수 있고, 발표자는 1.5초 넘게 말해야 바뀐다.
 * - 조작 막대 자동 숨김: 영상 통화에서 4초 동안 만지지 않으면 상단 바와 막대를 숨긴다. 시트가 열렸거나 재연결 중, 경고가 있거나, 화면을 공유하거나,
 *   상대가 없거나, TalkBack이 켜져 있거나, [holdControls]가 true면 숨기지 않는다. 숨은 동안 타일을 누르면 막대가 먼저 나온다.
 * - 가로 화면: 창이 가로로 길고 높이가 480dp 미만(compact height)이면 조작 막대를 끝 쪽(RTL이면 왼쪽) 세로 막대로 옮기고 원을 줄이며 이름표를 뺀다.
 *   무대는 그 폭만큼 끝 쪽을 비우고, 숨을 때는 옆으로 빠진다. 세로 막대 높이가 모자라면 '더보기'로 접는다.
 * - 성능: 영상과 아바타는 [KineticaScaffold]의 `background`에, 이름표와 글로우 테두리, 말하기 물결은 `content`에 같은 자리로 그리고,
 *   바는 영상만 비춘다. 그래서 테두리가 돌아도 바 블러를 다시 하지 않는다. 소리 크기는 [audioLevel]을 그리기 단계에서 읽는다.
 *
 * ```kotlin
 * KCallScreen(
 *     state = call.toKCallState(),
 *     actions = KCallActions(onEnd = vm::hangUp, onToggleMute = vm::toggleMute, onToggleCamera = vm::toggleCamera),
 *     video = { p -> VideoRenderer(p.id, Modifier.fillMaxSize()) },
 *     audioLevel = { id -> levels.value[id] ?: 0f },
 * )
 * ```
 *
 * @param video 참가자 영상 슬롯. [KCallParticipant.isVideoOn]인 참가자에게만 부른다. 타일 크기를 꽉 채워 그린다.
 * @param audioLevel 참가자 id로 소리 크기(0..1)를 돌려준다. 그리기 단계에서 읽는다.
 * @param pictureInPicture 호스트 Activity가 화면 속 화면 창에 있으면 true. 조작과 시트, 알림을 빼고 한 사람만 그린다.
 * @param autoHideControls 영상 통화에서 조작 막대를 자동으로 숨길지
 * @param holdControls true인 동안 조작 막대를 숨기지 않는다. 호스트만 아는 조건(말풍선 표시 등)을 넣는다.
 * @param reactions 반응 층의 상태. 받은 반응은 [KReactionState.send]로 띄운다.
 * @param confettiReactions 이 반응이 오면 색종이도 함께 터뜨린다. 기본값은 🎉 하나이고, 빈 집합이면 끈다. '애니메이션 삭제'면 터뜨리지 않는다.
 * @param island 입퇴장과 손 들기 알림을 띄울 아일랜드. null이면 알리지 않는다.
 * @param extraDockItems 기본 버튼 뒤(끊기 앞)에 덧붙일 버튼. 배경 효과, 품질 보기처럼 호스트 시트를 여는 버튼에 쓴다.
 * @param topBarActions 상단 바 버튼 앞에 덧붙일 버튼
 * @param dock 조작 막대를 바꿀 슬롯. 기본 버튼 목록과, 끝 쪽 세로 자리에 놓이는지(`vertical`)를 받는다. 세로면 끝 쪽 가운데에, 아니면 아래
 *   가운데에 놓인다. null이면 [KCallDock]이다.
 * @param nameTag 타일 이름표를 바꿀 슬롯. null이면 [KParticipantNameTag]다.
 * @param surface 바와 시트의 표면. 기본값은 영상 위에 맞춘 테마 표면의 어두운 짝이다.
 */
@Composable
public fun KCallScreen(
    state: KCallState,
    actions: KCallActions,
    modifier: Modifier = Modifier,
    video: @Composable BoxScope.(KCallParticipant) -> Unit = {},
    audioLevel: (String) -> Float = { 0f },
    pictureInPicture: Boolean = false,
    autoHideControls: Boolean = true,
    holdControls: Boolean = false,
    reactions: KReactionState = rememberKReactionState(),
    confettiReactions: Set<String> = setOf("🎉"),
    island: KIslandState? = rememberKIslandState(),
    extraDockItems: List<KCallDockItem> = emptyList(),
    topBarActions: @Composable RowScope.() -> Unit = {},
    dock: (@Composable (items: List<KCallDockItem>, vertical: Boolean) -> Unit)? = null,
    nameTag: (@Composable BoxScope.(KCallParticipant) -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface.forMedia(),
) {
    val inCall = state.phase == KCallPhase.Connected || state.phase == KCallPhase.Reconnecting
    val ui = rememberCallUi()
    val grid = rememberKParticipantGridState()
    val still = reducedMotion()
    Box(modifier.fillMaxSize().background(Color.Black)) {
        PhaseAnnouncer(state.phase)
        AnimatedContent(
            targetState = when {
                pictureInPicture -> ScreenKind.Pip
                inCall -> ScreenKind.Call
                else -> ScreenKind.Status
            },
            transitionSpec = { fadeIn(tween(if (still) 0 else FADE_MS)) togetherWith fadeOut(tween(if (still) 0 else FADE_MS)) },
            label = "call-screen",
        ) { kind ->
            when (kind) {
                ScreenKind.Pip -> PipContent(state, ui, video, audioLevel)

                ScreenKind.Status -> StatusContent(state, actions, video, surface)

                ScreenKind.Call -> CallContent(
                    CallInputs(state, actions, video, audioLevel, nameTag, extraDockItems, surface),
                    CallOptions(autoHideControls, holdControls, reactions, confettiReactions, island, dock, topBarActions),
                    ui,
                    grid,
                )
            }
        }
    }
}

private enum class ScreenKind { Status, Call, Pip }

/** 통화 화면 안에서 사용자가 고른 것. 화면을 돌리거나 화면 속 화면에 들어갔다 나와도 남는다. */
@Stable
internal class CallUi {
    var sheet by mutableStateOf(CallSheet.None)
    var pinnedId by mutableStateOf<String?>(null)
    var layout by mutableStateOf(KCallLayout.Gallery)
    var swapped by mutableStateOf(false)
    var topHeight by mutableIntStateOf(0)
    var bottomHeight by mutableIntStateOf(0)
    var sideWidth by mutableIntStateOf(0)

    // 버튼 동작을 담고 있어 저장하지 않는다. 다시 만들어져 비었으면 시트를 닫는다.
    var moreItems by mutableStateOf(emptyList<KCallDockItem>())
}

@Composable
private fun rememberCallUi(): CallUi = rememberSaveable(saver = CallUiSaver) { CallUi() }

private val CallUiSaver = androidx.compose.runtime.saveable.listSaver<CallUi, Any?>(
    save = { listOf(it.pinnedId, it.layout.ordinal, it.swapped, it.sheet.ordinal) },
    restore = {
        CallUi().apply {
            pinnedId = it[0] as String?
            layout = KCallLayout.entries[it[1] as Int]
            swapped = it[2] as Boolean
            sheet = CallSheet.entries[it[3] as Int]
        }
    },
)

/** [KCallScreen]이 받은 값 묶음 */
@Suppress("LongParameterList") // 공개 함수의 파라미터를 안쪽 함수에 한 덩어리로 넘기려고 묶는다.
internal class CallInputs(
    val state: KCallState,
    val actions: KCallActions,
    val video: @Composable BoxScope.(KCallParticipant) -> Unit,
    val audioLevel: (String) -> Float,
    val nameTag: (@Composable BoxScope.(KCallParticipant) -> Unit)?,
    val extraDockItems: List<KCallDockItem>,
    val surface: SurfaceStyle,
)

/** [KCallScreen]의 동작 옵션 */
@Suppress("LongParameterList") // 공개 함수의 파라미터를 안쪽 함수에 한 덩어리로 넘기려고 묶는다.
internal class CallOptions(
    val autoHide: Boolean,
    val hold: Boolean,
    val reactions: KReactionState,
    val confetti: Set<String>,
    val island: KIslandState?,
    val dock: (@Composable (List<KCallDockItem>, Boolean) -> Unit)?,
    val topBarActions: @Composable RowScope.() -> Unit,
)

/** 단계가 바뀌면 TalkBack이 읽도록 보이지 않는 곳에 단계 이름을 둔다. */
@Composable
private fun PhaseAnnouncer(phase: KCallPhase) {
    val text = stringResource(
        when (phase) {
            KCallPhase.Connecting -> R.string.kinetica_ui_call_connecting
            KCallPhase.Ringing -> R.string.kinetica_ui_call_ringing
            KCallPhase.Connected -> R.string.kinetica_ui_call_connected
            KCallPhase.Reconnecting -> R.string.kinetica_ui_call_reconnecting
            KCallPhase.Ended -> R.string.kinetica_ui_call_ended
            KCallPhase.Failed -> R.string.kinetica_ui_call_failed
        },
    )
    Box(
        Modifier.size(1.dp).clearAndSetSemantics {
            contentDescription = text
            liveRegion = LiveRegionMode.Polite
        },
    )
}

/** 연결 중, 호출 중, 종료, 실패 화면. 영상 통화를 거는 중이면 내 카메라 미리보기를 어둡게 깐다. */
@Composable
private fun StatusContent(
    state: KCallState,
    actions: KCallActions,
    video: @Composable BoxScope.(KCallParticipant) -> Unit,
    surface: SurfaceStyle,
) {
    val other = state.remotes.firstOrNull { !it.isScreenShare }
    val local = state.local
    val preview = local?.takeIf {
        state.isVideoCall && it.isVideoOn &&
            (state.phase == KCallPhase.Connecting || state.phase == KCallPhase.Ringing)
    }
    KineticaScaffold(
        Modifier.fillMaxSize(),
        surface = surface,
        background = {
            CallBackdrop(state.backdropTint())
            preview?.let { me ->
                Box(Modifier.matchParentSize()) { video(me) }
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = PREVIEW_DIM)))
            }
        },
        edgeFade = false,
        systemBarIcons = true,
    ) {
        KCallStatusView(
            status = state.phase.toStatus(),
            title = state.title ?: other?.name,
            message = state.errorMessage?.takeIf { state.phase == KCallPhase.Failed } ?: state.statusText,
            avatar = other?.avatar,
            detail = state.errorCode?.let { stringResource(R.string.kinetica_ui_call_error_code, it) },
            onCancel = actions.onEnd,
            onRetry = actions.onRetry,
            onClose = actions.onClose,
            actions = actions.onSaveReport?.let { save ->
                {
                    KButton(
                        stringResource(R.string.kinetica_ui_call_save_report),
                        save,
                        variant = KVariant.Text,
                        tone = KTone.Neutral,
                        surface = surface,
                    )
                }
            },
            surface = surface,
        )
    }
}

/** 화면 속 화면 창. 화면 공유, 고정한 사람, 말하는 사람, 첫 상대, 나 순서로 한 사람만 그린다. */
@Composable
private fun PipContent(
    state: KCallState,
    ui: CallUi,
    video: @Composable BoxScope.(KCallParticipant) -> Unit,
    audioLevel: (String) -> Float,
) {
    val people = state.participants
    val focus = people.firstOrNull { it.isScreenShare } ?: people.firstOrNull { it.id == ui.pinnedId }
        ?: state.remotes.firstOrNull { it.isSpeaking } ?: state.remotes.firstOrNull { it.isVideoOn } ?: state.remotes.firstOrNull()
        ?: state.local
    if (focus == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    KParticipantTile(
        focus.name,
        Modifier.fillMaxSize(),
        video = if (focus.isVideoOn && state.isVideoCall) ({ video(focus) }) else null,
        avatar = focus.avatar,
        muted = focus.isMuted,
        speaking = focus.isSpeaking,
        audioLevel = { audioLevel(focus.id) },
        screenShare = focus.isScreenShare,
        mirror = focus.isLocal && state.isFrontCamera,
        shape = RectangleShape,
    )
}

@Composable
private fun CallContent(inputs: CallInputs, options: CallOptions, ui: CallUi, grid: KParticipantGridState) {
    val state = inputs.state
    val video = state.isVideoCall
    val chrome = rememberKCallChromeState(autoHide = options.autoHide && video, holdVisible = options.hold || mustShowControls(state, ui))
    // 크게 보던 사람이 나가면 고정을 푼다.
    LaunchedEffect(state.participants, ui.pinnedId) { if (state.participants.none { it.id == ui.pinnedId }) ui.pinnedId = null }
    val speaker = rememberKStableSpeaker(state.remotes.firstOrNull { it.isSpeaking && !it.isScreenShare }?.id)
    val side = callWindowSize().let { sideDock(it.width, it.height) }
    val stage = StageSpec(focusOf(state, ui, speaker), chrome, rememberStagePadding(ui, chrome, side))
    KineticaScaffold(
        Modifier.fillMaxSize().kCallChromeTouches(chrome),
        surface = inputs.surface,
        background = {
            if (video) {
                VideoStage(inputs, ui, grid, stage, KCallLayer.Video)
            } else {
                CallBackdrop(state.backdropTint())
            }
        },
        topBar = { BarsSeeVideoOnly { TopChrome(inputs, options, ui, chrome) } },
        bottomBar = { if (!side) BarsSeeVideoOnly { BottomChrome(inputs, options, ui, chrome) } },
        edgeFade = false,
        systemBarIcons = true,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (video) {
                Box(Modifier.fillMaxSize().pointerInput(chrome) { detectTapGestures { chrome.toggle() } })
                VideoStage(inputs, ui, grid, stage, KCallLayer.Overlay)
            } else {
                VoiceStage(state, inputs.audioLevel, stage.padding, inputs.surface)
            }
            CallOverlays(inputs, options, ui, chrome, side)
            // 스캐폴드 바 칸은 위아래에만 있으므로 세로 막대는 콘텐츠 맨 위에 둔다. 바와 같이 영상만 비춘다.
            if (side) BarsSeeVideoOnly { SideChrome(inputs, options, ui, chrome) }
        }
        CallSheets(inputs, ui)
    }
}

/**
 * 조작 막대를 숨기면 안 되는 때. 시트가 열렸거나 재연결 중, 경고가 있거나, 내 화면을 공유하거나, 상대가 아직 없을 때다.
 * 공유 중에는 모든 화면이 상대에게 보인다는 것을 늘 알려야 하고, 상대가 없으면 숨길 이유가 없다.
 */
private fun mustShowControls(state: KCallState, ui: CallUi): Boolean =
    ui.sheet != CallSheet.None || state.phase == KCallPhase.Reconnecting || state.warning != null || state.isSharingScreen ||
        state.remotes.isEmpty()

/** 상태 화면과 음성 통화 배경을 물들일 색. 첫 상대의 아바타 색이다. */
private fun KCallState.backdropTint(): Color {
    val other = remotes.firstOrNull { !it.isScreenShare }
    return (other?.avatar ?: KAvatarData(other?.name ?: title.orEmpty())).backgroundColor()
}

private fun KCallPhase.toStatus(): KCallStatus = when (this) {
    KCallPhase.Ringing -> KCallStatus.Ringing
    KCallPhase.Ended -> KCallStatus.Ended
    KCallPhase.Failed -> KCallStatus.Failed
    else -> KCallStatus.Connecting
}

/** 바가 영상만 비추게 한다. 콘텐츠 층의 글로우 테두리가 돌 때 바 블러를 다시 하지 않기 위해서다. */
@Composable
private fun BarsSeeVideoOnly(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlassBackdrop provides LocalScaffoldBackground.current, content = content)
}

@Composable
private fun TopChrome(inputs: CallInputs, options: CallOptions, ui: CallUi, chrome: KCallChromeState) {
    val state = inputs.state
    val actions = inputs.actions
    AnimatedVisibility(
        chrome.isVisible,
        enter = fadeIn() + slideInVertically {
            -it / 2
        },
        exit = fadeOut() + slideOutVertically { -it / 2 },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { if (it.height > 0) ui.topHeight = it.height }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            KCallTopBar(
                title = state.title ?: state.remotes.singleOrNull()?.name,
                startedAtMillis = state.startedAtMillis,
                participantCount = state.participants.count { !it.isScreenShare }.takeIf { it > 2 },
                quality = state.local?.networkQuality?.takeIf { it != KNetworkQuality.Unknown },
                recording = state.isRecording,
                encrypted = state.isEncrypted,
                navigation = actions.onMinimize?.let { minimize ->
                    {
                        KIconButton(
                            KCallIcons.Minimize,
                            stringResource(R.string.kinetica_ui_call_minimize),
                            minimize,
                            tone = KTone.Neutral,
                            surface = inputs.surface,
                        )
                    }
                },
                actions = {
                    options.topBarActions(this)
                    TopBarButtons(inputs, ui)
                },
                surface = inputs.surface,
            )
            CallBanners(state, actions, inputs.surface)
        }
    }
}

@Composable
private fun TopBarButtons(inputs: CallInputs, ui: CallUi) {
    val state = inputs.state
    val surface = inputs.surface
    val sharing = state.remotes.any { it.isScreenShare }
    if (state.isVideoCall && state.participants.size >= SPEAKER_MIN && !sharing) {
        val toSpeaker = ui.layout == KCallLayout.Gallery
        KIconButton(
            if (toSpeaker) KCallIcons.SpeakerView else KCallIcons.GalleryView,
            stringResource(if (toSpeaker) R.string.kinetica_ui_call_view_speaker else R.string.kinetica_ui_call_view_gallery),
            {
                ui.pinnedId = null
                ui.layout = if (toSpeaker) KCallLayout.Speaker else KCallLayout.Gallery
            },
            tone = KTone.Neutral,
            surface = surface,
        )
    }
    inputs.actions.onEnterPictureInPicture?.let {
        KIconButton(KCallIcons.PictureInPicture, stringResource(R.string.kinetica_ui_call_pip), it, tone = KTone.Neutral, surface = surface)
    }
    if (state.participants.size > 1 || inputs.actions.onAddPeople != null) {
        KIconButton(KIcons.Users, stringResource(R.string.kinetica_ui_call_people_title), {
            ui.sheet = CallSheet.People
        }, tone = KTone.Neutral, surface = surface)
    }
}

/** 손 든 캡슐, 입퇴장 알림, 반응 층 */
@Composable
private fun CallOverlays(inputs: CallInputs, options: CallOptions, ui: CallUi, chrome: KCallChromeState, side: Boolean) {
    val state = inputs.state
    val density = LocalDensity.current
    val top by animateDpAsState(if (chrome.isVisible) with(density) { ui.topHeight.toDp() } else 0.dp, label = "notice-top")
    options.island?.let { island ->
        ParticipantNotices(state.remotes, island)
        KIslandHost(island, Modifier.offset { IntOffset(0, top.roundToPx()) })
    }
    val onHand = inputs.actions.onRaiseHand
    if (onHand != null) {
        Box(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(top = top + 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            KRaisedHandPill(state.local?.hasRaisedHand == true, onHand, surface = inputs.surface)
        }
    }
    val bottom by animateDpAsState(
        if (chrome.isVisible && !side) {
            with(density) {
                ui.bottomHeight.toDp()
            }
        } else {
            BOTTOM_HIDDEN
        },
        label = "reaction-bottom",
    )
    // 세로 막대 위로 반응이 지나가지 않게 막대 폭만큼 끝 쪽을 비운다.
    val end by animateDpAsState(if (chrome.isVisible && side) with(density) { ui.sideWidth.toDp() } else 0.dp, label = "reaction-end")
    KReactionOverlay(options.reactions, Modifier.padding(end = end), bottomPadding = bottom, confetti = options.confetti)
}

/** 참가자, 오디오 출력, 반응 시트. 닫히며 내려가는 동안에도 마지막 내용을 보여 준다. */
@Composable
private fun CallSheets(inputs: CallInputs, ui: CallUi) {
    var shown by remember { mutableStateOf(ui.sheet) }
    if (ui.sheet != CallSheet.None && ui.sheet != CallSheet.More) shown = ui.sheet
    val close = { ui.sheet = CallSheet.None }
    val title = sheetTitle(shown)
    LaunchedEffect(ui.sheet, ui.moreItems) { if (ui.sheet == CallSheet.More && ui.moreItems.isEmpty()) close() }
    KCallMoreSheet(ui.moreItems, ui.sheet == CallSheet.More, close, inputs.surface)
    KSheet(ui.sheet != CallSheet.None && ui.sheet != CallSheet.More, close, surface = inputs.surface.forPopup(), title = title) {
        SheetBody(shown, title.orEmpty(), inputs, ui, close)
    }
}

@Composable
private fun sheetTitle(sheet: CallSheet): String? = when (sheet) {
    CallSheet.People -> stringResource(R.string.kinetica_ui_call_people_title)
    CallSheet.Audio -> stringResource(R.string.kinetica_ui_call_audio_title)
    CallSheet.Reactions -> stringResource(R.string.kinetica_ui_call_reactions_title)
    CallSheet.None, CallSheet.More -> null
}

/** 시트 내용. 고르면 시트를 닫는다. 반응 시트에는 손 들기 버튼도 둔다. */
@Composable
private fun SheetBody(sheet: CallSheet, title: String, inputs: CallInputs, ui: CallUi, close: () -> Unit) {
    val state = inputs.state
    val actions = inputs.actions
    val surface = inputs.surface.forPopup()
    val pin = { id: String ->
        ui.pinnedId = if (ui.pinnedId == id) null else id
        close()
    }
    when (sheet) {
        CallSheet.People -> PeopleSheetContent(
            state,
            ui.pinnedId,
            pin.takeIf {
                state.isVideoCall
            },
            inputs.audioLevel,
            actions.onAddPeople,
            surface,
        )

        CallSheet.Audio -> {
            SheetTitle(title)
            KAudioRouteList(state.audioRoutes, state.selectedRouteId, { id ->
                actions.onSelectAudioRoute?.invoke(id)
                close()
            }, surface = surface)
        }

        CallSheet.Reactions -> {
            SheetTitle(title)
            KReactionPicker({ emoji ->
                actions.onReact?.invoke(emoji)
                close()
            }, surface = surface)
            actions.onRaiseHand?.let { hand ->
                val raised = state.local?.hasRaisedHand == true
                androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
                KButton(
                    stringResource(if (raised) R.string.kinetica_ui_call_hand_lower else R.string.kinetica_ui_call_hand),
                    {
                        hand()
                        close()
                    },
                    fullWidth = true,
                    variant = KVariant.Tonal,
                    icon = KCallIcons.Hand,
                    surface = surface,
                )
            }
        }

        CallSheet.None, CallSheet.More -> Unit
    }
}

private const val FADE_MS = 280
internal const val SPEAKER_MIN = 3
private const val PREVIEW_DIM = 0.45f
private val BOTTOM_HIDDEN = 32.dp
