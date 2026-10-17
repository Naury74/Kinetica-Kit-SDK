// 파일 이름은 이 파일이 [KCallScreen]의 부분을 모아 둔 곳임을 나타낸다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** [KCallScreen]이 연 시트 */
internal enum class CallSheet { None, People, Audio, Reactions, More }

/**
 * [state]와 [actions]로 기본 도크 버튼을 만든다. 넘기지 않은 콜백의 버튼은 넣지 않는다. 마이크, 카메라, 끊기는 막대에 늘 남고,
 * 나머지는 앞쪽부터 남는다.
 */
@Composable
internal fun defaultDockItems(state: KCallState, actions: KCallActions, openSheet: (CallSheet) -> Unit): List<KCallDockItem> {
    val selected = state.audioRoutes.firstOrNull { it.id == state.selectedRouteId }
    val end =
        KCallDockItem(
            KEY_END,
            KIcons.CallEnd,
            stringResource(R.string.kinetica_ui_call_end),
            actions.onEnd,
            tone = KTone.Destructive,
            essential = true,
        )
    return mediaItems(state, actions) + listOfNotNull(audioItem(state, actions, selected, openSheet)) + videoItems(state, actions) +
        socialItems(state, actions, openSheet) + end
}

/** 마이크와 카메라. 도크가 좁아도 남는다. */
@Composable
private fun mediaItems(state: KCallState, actions: KCallActions): List<KCallDockItem> {
    val local = state.local
    val mic = actions.onToggleMute?.let {
        KCallDockItem(
            KEY_MIC,
            KIcons.Mic,
            stringResource(R.string.kinetica_ui_call_mic),
            it,
            checked = local?.isMuted == true,
            checkedIcon = KIcons.MicOff,
            essential = true,
        )
    }
    val camera = actions.onToggleCamera?.takeIf { state.isVideoCall }?.let {
        val off = local?.isVideoOn == false
        KCallDockItem(
            KEY_CAMERA,
            KIcons.Video,
            stringResource(R.string.kinetica_ui_call_camera),
            it,
            checked = off,
            checkedIcon = KIcons.VideoOff,
            essential = true,
        )
    }
    return listOfNotNull(mic, camera)
}

/** 카메라 전환과 화면 공유. 영상 통화에만 있다. */
@Composable
private fun videoItems(state: KCallState, actions: KCallActions): List<KCallDockItem> {
    if (!state.isVideoCall) return emptyList()
    val flip = actions.onSwitchCamera?.let {
        KCallDockItem(
            KEY_FLIP,
            KCallIcons.SwitchCamera,
            stringResource(R.string.kinetica_ui_call_flip),
            it,
            enabled =
                state.local?.isVideoOn != false,
        )
    }
    val share = actions.onShareScreen?.let {
        KCallDockItem(
            KEY_SHARE,
            KCallIcons.ScreenShare,
            stringResource(R.string.kinetica_ui_call_share),
            it,
            checked = state.isSharingScreen,
            tone = KTone.Accent,
        )
    }
    return listOfNotNull(flip, share)
}

/** 채팅, 손 들기, 반응 */
@Composable
private fun socialItems(state: KCallState, actions: KCallActions, openSheet: (CallSheet) -> Unit): List<KCallDockItem> {
    val chat = actions.onOpenChat?.let {
        KCallDockItem(KEY_CHAT, KIcons.Message, stringResource(R.string.kinetica_ui_call_chat), it, badge = state.unreadMessages)
    }
    val hand = actions.onRaiseHand?.let {
        KCallDockItem(
            KEY_HAND,
            KCallIcons.Hand,
            stringResource(R.string.kinetica_ui_call_hand),
            it,
            checked =
                state.local?.hasRaisedHand == true,
            tone = KTone.Accent,
        )
    }
    val react = actions.onReact?.let {
        KCallDockItem(KEY_REACT, KCallIcons.Reaction, stringResource(R.string.kinetica_ui_call_react), { openSheet(CallSheet.Reactions) })
    }
    return listOfNotNull(chat, hand, react)
}

/** 오디오 버튼. 출력 경로가 있으면 출력 선택 시트를, 없으면 스피커 켜고 끄기를 한다. */
@Composable
private fun audioItem(state: KCallState, actions: KCallActions, selected: KAudioRoute?, openSheet: (CallSheet) -> Unit): KCallDockItem? {
    if (state.audioRoutes.isNotEmpty() && actions.onSelectAudioRoute != null) {
        return KCallDockItem(
            KEY_AUDIO,
            (selected?.type ?: KAudioRouteType.Speaker).icon(),
            stringResource(R.string.kinetica_ui_call_audio),
            { openSheet(CallSheet.Audio) },
            stateDescription = selected?.name,
        )
    }
    val toggle = actions.onToggleSpeaker ?: return null
    return KCallDockItem(KEY_AUDIO, KIcons.Speaker, stringResource(R.string.kinetica_ui_call_speaker), toggle, checked = state.isSpeakerOn)
}

/** 상단 바 아래 경고 배너들. 나타날 때 아래 내용을 밀어내며 펼쳐진다. */
@Composable
internal fun CallBanners(state: KCallState, actions: KCallActions, surface: SurfaceStyle) {
    val enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow), expandFrom = Alignment.Top) + fadeIn()
    val exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow), shrinkTowards = Alignment.Top) + fadeOut()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(state.isSharingScreen, enter = enter, exit = exit) {
            KCallBanner(
                stringResource(R.string.kinetica_ui_call_sharing_warning),
                status = KStatus.Error,
                actionLabel = actions.onShareScreen?.let { stringResource(R.string.kinetica_ui_call_stop_sharing) },
                onAction = actions.onShareScreen,
                surface = surface,
            )
        }
        AnimatedVisibility(state.warning != null, enter = enter, exit = exit) {
            KCallBanner(state.warning.orEmpty(), status = KStatus.Warning, surface = surface)
        }
        AnimatedVisibility(state.phase == KCallPhase.Reconnecting, enter = enter, exit = exit) {
            KCallBanner(
                stringResource(R.string.kinetica_ui_call_reconnecting),
                supporting = state.reconnectSecondsLeft?.let {
                    androidx.compose.ui.res.pluralStringResource(R.plurals.kinetica_ui_call_reconnecting_left, it, it)
                },
                status = KStatus.Warning,
                liveRegion = LiveRegionMode.Assertive,
                surface = surface,
            )
        }
    }
}

/**
 * 음성 통화 무대. 1:1은 큰 아바타와 말할 때 퍼지는 물결, 이름, 통화 시간, 상태 문구를 놓고, 여럿이면 아바타 격자를 세로로 넘긴다.
 * 물결은 매 프레임 움직이므로 [KineticaScaffold]의 `content` 층에 둔다.
 */
@Composable
internal fun VoiceStage(state: KCallState, audioLevel: (String) -> Float, contentPadding: PaddingValues, surface: SurfaceStyle) {
    val remotes = state.remotes
    val ink = surface.contentColor
    BoxWithConstraints(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
        val focus = remotes.singleOrNull()
        if (remotes.size <= 1) {
            val avatarSize = (maxHeight * VOICE_AVATAR).coerceIn(88.dp, 168.dp)
            val roomy = maxHeight >= 520.dp
            // 물결이 아바타 밖으로 퍼지므로 스크롤 영역이 자르지 않게 폭을 채우고 위아래에 자리를 둔다.
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = avatarSize * RIPPLE_ROOM),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (focus != null) {
                    Box(Modifier.size(avatarSize).speakingRipple(focus.isSpeaking, { audioLevel(focus.id) }, KineticaTheme.colors.accent)) {
                        CallAvatar(focus.avatar ?: KAvatarData(focus.name), avatarSize)
                        HandBadge(focus.hasRaisedHand, Modifier.align(Alignment.TopEnd), 36.dp)
                    }
                    Spacer(Modifier.height(if (roomy) 44.dp else 20.dp))
                    ThemedText(
                        focus.name,
                        style = TextStyle(
                            color = ink,
                            fontSize = 30.sp,
                            fontWeight = KineticaTheme.design.title(FontWeight.SemiBold),
                            textAlign = TextAlign.Center,
                        ),
                    )
                    state.startedAtMillis?.let {
                        KCallTimer(it, Modifier.padding(top = 6.dp), TextStyle(fontSize = 18.sp, color = ink.copy(alpha = 0.8f)))
                    }
                    (
                        state.statusText ?: focus.networkQuality.takeIf {
                            it.isWeak
                        }?.label()
                        )?.let { CallCaption(it, Modifier.padding(top = 4.dp)) }
                    if (focus.isMuted) {
                        KCallBanner(
                            stringResource(R.string.kinetica_ui_call_mic_off),
                            Modifier.padding(top = 16.dp).width(220.dp),
                            surface = surface,
                        )
                    }
                } else {
                    KCallStatusView(KCallStatus.Waiting, surface = surface)
                }
            }
        } else {
            VoiceGroupGrid(state.participants, audioLevel, surface)
        }
    }
}

/** 음성 그룹 통화의 아바타 격자. 적으면 가운데에 모으고, 많으면 위에서부터 넘긴다. */
@Composable
private fun VoiceGroupGrid(people: List<KCallParticipant>, audioLevel: (String) -> Float, surface: SurfaceStyle) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        val columns = when {
            people.size <= SMALL_GROUP && maxWidth < 600.dp -> NARROW_COLUMNS
            maxWidth < 840.dp -> MEDIUM_COLUMNS
            else -> WIDE_COLUMNS
        }
        val cell = (maxWidth - 16.dp * (columns - 1)) / columns
        val avatar = (cell * VOICE_CELL_AVATAR).coerceIn(56.dp, 120.dp)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            people.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                    row.forEach { p ->
                        key(p.id) { VoiceCell(p, audioLevel, cell, avatar, surface) }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceCell(
    p: KCallParticipant,
    audioLevel: (String) -> Float,
    cell: androidx.compose.ui.unit.Dp,
    avatar: androidx.compose.ui.unit.Dp,
    surface: SurfaceStyle,
) {
    val name = if (p.isLocal) stringResource(R.string.kinetica_ui_call_you_named, p.name) else p.name
    val description = listOfNotNull(
        name,
        stringResource(R.string.kinetica_ui_call_speaking).takeIf { p.isSpeaking },
        stringResource(R.string.kinetica_ui_call_mic_off).takeIf { p.isMuted },
        stringResource(R.string.kinetica_ui_call_hand_raised).takeIf { p.hasRaisedHand },
    ).joinToString(", ")
    Column(
        Modifier.width(cell).clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(avatar).speakingRipple(p.isSpeaking, { audioLevel(p.id) }, KineticaTheme.colors.accent)) {
            CallAvatar(p.avatar ?: KAvatarData(p.name), avatar)
            HandBadge(p.hasRaisedHand, Modifier.align(Alignment.TopEnd), 28.dp)
        }
        Row(
            Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (p.isMuted) {
                Image(rememberVectorPainter(KIcons.MicOff), null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(Color(CALL_WARNING)))
            }
            ThemedText(
                name,
                style = TextStyle(color = surface.contentColor, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 원격 참가자가 들어오고 나가거나 손을 들면 [island]에 알린다. 화면에 들어온 뒤 [SETTLE_MS] 동안의 변화와 첫 목록은 알리지 않는다.
 */
@Composable
internal fun ParticipantNotices(remotes: List<KCallParticipant>, island: KIslandState) {
    val previous = remember { arrayOfNulls<List<KCallParticipant>>(1) }
    val settled = remember { booleanArrayOf(false) }
    val joined = stringResource(R.string.kinetica_ui_call_joined, "%s")
    val left = stringResource(R.string.kinetica_ui_call_left, "%s")
    val hand = stringResource(R.string.kinetica_ui_call_raised_by, "%s")
    val accent = KineticaTheme.colors.accent
    val current by rememberUpdatedState(remotes)
    // 목록이 다시 바뀌어도 보이던 알림이 잘리지 않도록 알림은 효과 밖의 범위에서 띄운다.
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        delay(SETTLE_MS)
        settled[0] = true
        previous[0] = current
    }
    LaunchedEffect(remotes) {
        val before = previous[0]
        if (!settled[0] || before == null) return@LaunchedEffect
        previous[0] = remotes
        val beforeIds = before.associateBy { it.id }
        val nowIds = remotes.associateBy { it.id }
        val notes = buildList {
            remotes.filter {
                it.id !in beforeIds && !it.isScreenShare
            }.forEach { add(KIslandContent(joined.format(it.name), icon = KIcons.Person, accent = accent)) }
            before.filter {
                it.id !in nowIds && !it.isScreenShare
            }.forEach { add(KIslandContent(left.format(it.name), icon = KIcons.Person)) }
            remotes.filter { it.hasRaisedHand && beforeIds[it.id]?.hasRaisedHand == false }
                .forEach { add(KIslandContent(hand.format(it.name), icon = KCallIcons.Hand, accent = Color(HAND_YELLOW))) }
        }
        notes.take(MAX_NOTICES).forEach { note -> scope.launch { island.show(note, durationMs = NOTICE_MS) } }
    }
}

/** 참가자 시트 내용 */
@Composable
internal fun PeopleSheetContent(
    state: KCallState,
    pinnedId: String?,
    onTogglePin: ((String) -> Unit)?,
    audioLevel: (String) -> Float,
    onAddPeople: (() -> Unit)?,
    surface: SurfaceStyle,
) {
    SheetTitle(pluralPeople(state.participants.count { !it.isScreenShare }))
    val people = state.participants.filterNot { it.isScreenShare }
    // 손을 든 사람을 맨 위로 올려 진행자가 바로 보게 한다.
    val ordered = people.filter { it.hasRaisedHand } + people.filterNot { it.hasRaisedHand }
    Column {
        ordered.forEach { p ->
            key(p.id) {
                KParticipantRow(
                    if (p.isLocal) stringResource(R.string.kinetica_ui_call_you_named, p.name) else p.name,
                    avatar = p.avatar,
                    role = p.role,
                    muted = p.isMuted,
                    videoOff = state.isVideoCall && !p.isVideoOn,
                    handRaised = p.hasRaisedHand,
                    speaking = p.isSpeaking,
                    audioLevel = { audioLevel(p.id) },
                    quality = p.networkQuality,
                    pinned = p.id == pinnedId,
                    onPin = onTogglePin?.let { pin -> { pin(p.id) } },
                    surface = surface,
                )
            }
        }
    }
    onAddPeople?.let {
        Spacer(Modifier.height(8.dp))
        KButton(
            stringResource(R.string.kinetica_ui_call_add_people),
            it,
            fullWidth = true,
            variant = KVariant.Tonal,
            icon = KIcons.Plus,
            surface = surface,
        )
    }
}

@Composable
private fun pluralPeople(count: Int): String = androidx.compose.ui.res.pluralStringResource(R.plurals.kinetica_ui_call_people, count, count)

internal const val KEY_MIC = "mic"
internal const val KEY_CAMERA = "camera"
internal const val KEY_AUDIO = "audio"
internal const val KEY_FLIP = "flip"
internal const val KEY_SHARE = "share"
internal const val KEY_CHAT = "chat"
internal const val KEY_HAND = "hand"
internal const val KEY_REACT = "react"
internal const val KEY_END = "end"
private const val HAND_YELLOW = 0xFFFFCC00
private const val VOICE_AVATAR = 0.24f
private const val VOICE_CELL_AVATAR = 0.62f
private const val SMALL_GROUP = 4
private const val NARROW_COLUMNS = 2
private const val MEDIUM_COLUMNS = 3
private const val WIDE_COLUMNS = 4
private const val RIPPLE_ROOM = 0.36f
private const val SETTLE_MS = 1_500L
private const val NOTICE_MS = 2_500L
private const val MAX_NOTICES = 3
