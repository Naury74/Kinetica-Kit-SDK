// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KAudioLevelIndicator
import com.naury.kinetica.ui.KAudioLevelStyle
import com.naury.kinetica.ui.KAudioRoute
import com.naury.kinetica.ui.KAudioRouteList
import com.naury.kinetica.ui.KAudioRouteType
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KCallBanner
import com.naury.kinetica.ui.KCallBubble
import com.naury.kinetica.ui.KCallControlButton
import com.naury.kinetica.ui.KCallDock
import com.naury.kinetica.ui.KCallDockItem
import com.naury.kinetica.ui.KCallIcons
import com.naury.kinetica.ui.KCallLobby
import com.naury.kinetica.ui.KCallStatus
import com.naury.kinetica.ui.KCallStatusView
import com.naury.kinetica.ui.KCallTimer
import com.naury.kinetica.ui.KCallTopBar
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KIncomingCall
import com.naury.kinetica.ui.KNetworkQuality
import com.naury.kinetica.ui.KNetworkQualityIndicator
import com.naury.kinetica.ui.KOngoingCallPill
import com.naury.kinetica.ui.KParticipantGrid
import com.naury.kinetica.ui.KParticipantRow
import com.naury.kinetica.ui.KParticipantTile
import com.naury.kinetica.ui.KRaisedHandPill
import com.naury.kinetica.ui.KReactionOverlay
import com.naury.kinetica.ui.KReactionPicker
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KSelfView
import com.naury.kinetica.ui.KStatus
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.rememberKReactionState

/** 컴포넌트 데모에서 '통화' 탭의 순서 */
internal const val CALL_TAB = 9

/** 소셜·모션 탭과 통화 탭. 컴포넌트 데모의 탭 분기를 늘리지 않으려고 둘을 여기서 고른다. */
@Composable
internal fun SocialOrCallTab(page: Int, padding: PaddingValues, connection: NestedScrollConnection) {
    if (page == CALL_TAB) CallTab(padding, connection) else SocialMotionTab(padding, connection)
}

/** 컴포넌트 데모의 '통화' 탭. 통화 화면을 만드는 부품을 하나씩 보여 준다. 완성 화면은 앱 화면 예의 전화 앱에 있다. */
@Composable
internal fun CallTab(padding: PaddingValues, connection: NestedScrollConnection) {
    ComponentTabList(padding, connection) {
        item { ControlsCard() }
        item { TilesCard() }
        item { GridCard() }
        item { BarCard() }
        item { StatusCard() }
        item { IncomingCard() }
        item { ListsCard() }
        item { NoticesCard() }
        item { LobbyCard() }
    }
}

@Composable
private fun ControlsCard() {
    var muted by remember { mutableStateOf(true) }
    var sharing by remember { mutableStateOf(false) }
    DemoCard("조작 버튼과 도크", "KCallControlButton은 켜짐, 진행 중, 위험 상태와 배지를 보여 줍니다. KCallDock은 좁으면 원을 줄이고 남는 버튼을 '더보기'로 접습니다.") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            KCallControlButton(KIcons.Mic, "마이크", { muted = !muted }, checked = muted, checkedIcon = KIcons.MicOff)
            KCallControlButton(KCallIcons.ScreenShare, "화면 공유", {
                sharing = !sharing
            }, checked = sharing, tone = KTone.Accent, pending = sharing)
            KCallControlButton(KIcons.Message, "채팅", {}, badge = 3)
            KCallControlButton(KIcons.CallEnd, "종료", {}, tone = KTone.Destructive)
        }
        Label("폭 300dp 도크", 13)
        KCallDock(demoDock(muted, sharing, { muted = !muted }, { sharing = !sharing }), Modifier.width(300.dp))
    }
}

private fun demoDock(muted: Boolean, sharing: Boolean, onMute: () -> Unit, onShare: () -> Unit) = listOf(
    KCallDockItem("mic", KIcons.Mic, "마이크", onMute, checked = muted, checkedIcon = KIcons.MicOff, essential = true),
    KCallDockItem("cam", KIcons.Video, "카메라", {}, checked = false, checkedIcon = KIcons.VideoOff, essential = true),
    KCallDockItem("share", KCallIcons.ScreenShare, "화면 공유", onShare, checked = sharing, tone = KTone.Accent),
    KCallDockItem("chat", KIcons.Message, "채팅", {}, badge = 2),
    KCallDockItem("hand", KCallIcons.Hand, "손 들기", {}, badge = 1),
    KCallDockItem("end", KIcons.CallEnd, "종료", {}, tone = KTone.Destructive, essential = true),
)

@Composable
private fun TilesCard() {
    var pinned by remember { mutableStateOf(false) }
    DemoCard("참가자 타일", "영상 슬롯, 말하는 중 글로우 테두리, 마이크 꺼짐, 손 들기, 약한 연결 표시. 길게 누르면 고정합니다.") {
        Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KParticipantTile(
                "윤아",
                Modifier.weight(1f),
                video = { DemoVideo(1) },
                speaking = true,
                audioLevel = { 0.7f },
                pinned = pinned,
                onTogglePin = { pinned = !pinned },
            )
            KParticipantTile(
                "박준호",
                Modifier.weight(1f),
                avatar = CALL_CONTACTS[1].avatar,
                muted = true,
                handRaised = true,
                quality = KNetworkQuality.Poor,
            )
        }
    }
}

@Composable
private fun GridCard() {
    var count by rememberSaveable { mutableIntStateOf(4) }
    var focus by rememberSaveable { mutableStateOf(false) }
    DemoCard("참가자 격자", "인원과 화면 비율에 맞춰 열 수를 고르고, 크게 보기는 큰 화면과 한 줄 띠로 바뀝니다. 2명이면 내 화면이 모서리 작은 창이 되어 끌어 옮길 수 있습니다.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton("빼기", { if (count > 1) count-- }, size = KButtonSize.Small, tone = KTone.Neutral)
            KButton("더하기", { if (count < 12) count++ }, size = KButtonSize.Small)
            KButton(if (focus) "격자" else "크게 보기", { focus = !focus }, size = KButtonSize.Small, tone = KTone.Neutral)
        }
        val people = (0 until count).map { "p$it" }
        Box(Modifier.fillMaxWidth().height(360.dp).clip(kitShape { card }).background(androidx.compose.ui.graphics.Color.Black)) {
            KParticipantGrid(people, key = {
                it
            }, Modifier.fillMaxSize(), focusKey = "p1".takeIf { focus }, selfKey = "p0", contentPadding = PaddingValues(6.dp)) { p, _ ->
                val i = p.drop(1).toInt()
                KParticipantTile(
                    if (i ==
                        0
                    ) {
                        "나"
                    } else {
                        CALL_CONTACTS[i].name
                    },
                    video = { DemoVideo(i) },
                    speaking = i == 1,
                    audioLevel = { 0.5f },
                )
            }
        }
    }
}

@Composable
private fun BarCard() {
    val start = remember { SystemClock.elapsedRealtime() - 754_000 }
    DemoCard("상단 바와 표시기", "글자가 커져도 끝 버튼은 밀려나지 않습니다. 시간은 바뀐 자리만 굴러 올라가고, 소리 크기는 그리기 단계에서만 읽습니다.") {
        KCallTopBar(
            title = "주간 디자인 싱크",
            startedAtMillis = start,
            participantCount = 6,
            quality = KNetworkQuality.Good,
            recording = true,
            encrypted = true,
            actions = { KIconButton(KIcons.Users, "참가자", {}) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            KCallTimer(start)
            KNetworkQuality.entries.take(4).forEach { KNetworkQualityIndicator(it) }
            KAudioLevelIndicator({ 0.6f })
            KAudioLevelIndicator({ 0.6f }, style = KAudioLevelStyle.Ring, size = 24.dp)
        }
    }
}

@Composable
private fun StatusCard() {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val statuses = listOf(KCallStatus.Connecting, KCallStatus.Reconnecting, KCallStatus.Ended, KCallStatus.Failed)
    DemoCard("상태 화면", "연결 중과 재연결은 펄스 링이 퍼지고, 문구가 바뀌면 TalkBack이 읽어 줍니다. 실패하면 다시 시도와 오류 코드를 보여 줍니다.") {
        KSegmentedControl(listOf("연결", "재연결", "종료", "실패"), index, { index = it }, Modifier.fillMaxWidth())
        Box(
            Modifier.fillMaxWidth().consumeWindowInsets(WindowInsets.safeDrawing).height(460.dp).clip(
                kitShape {
                    card
                },
            ).background(Brush.verticalGradient(artPalette(5))),
        ) {
            KCallStatusView(
                statuses[index],
                title = CALL_CONTACTS[2].name,
                avatar = CALL_CONTACTS[2].avatar,
                detail = "E-403".takeIf { statuses[index] == KCallStatus.Failed },
                onCancel = {},
                onRetry = {},
                onClose = {},
            )
        }
    }
}

@Composable
private fun IncomingCard() {
    DemoCard("걸려 온 전화", "KIncomingCall은 내용만, KIncomingCallScreen은 배경까지 그립니다. 영상 통화면 음성과 영상 받기를 나눕니다.") {
        Box(
            Modifier.fillMaxWidth().consumeWindowInsets(WindowInsets.safeDrawing).height(520.dp).clip(
                kitShape {
                    card
                },
            ).background(Brush.verticalGradient(artPalette(2))),
        ) {
            KIncomingCall("윤아", {
            }, {}, avatar = CALL_CONTACTS[2].avatar, subtitle = "영상 통화", isVideo = true, onAcceptVideo = {}, onMessage = {})
        }
    }
}

@Composable
private fun ListsCard() {
    var route by remember { mutableStateOf("bt") }
    DemoCard("참가자 목록과 오디오 출력", "참가자 행은 상태를 글자로도 써서 TalkBack이 한 번에 읽습니다. 오디오 출력은 행 전체가 라디오 버튼입니다.") {
        KParticipantRow("박준호", role = "진행자", muted = true, handRaised = true, onPin = {}, onRemove = {})
        KParticipantRow("윤아", speaking = true, audioLevel = { 0.6f }, onPin = {})
        KAudioRouteList(
            listOf(
                KAudioRoute("ear", "휴대전화", KAudioRouteType.Earpiece),
                KAudioRoute("spk", "스피커", KAudioRouteType.Speaker),
                KAudioRoute("bt", "Galaxy Buds3", KAudioRouteType.Bluetooth),
                KAudioRoute("wired", "유선 이어폰", KAudioRouteType.Wired, enabled = false, disabledReason = "연결되지 않음"),
            ),
            route,
            { route = it },
        )
    }
}

@Composable
private fun NoticesCard() {
    val reactions = rememberKReactionState()
    var bubble by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    var hand by remember { mutableStateOf(true) }
    val start = remember { SystemClock.elapsedRealtime() - 95_000 }
    DemoCard("알림과 반응", "경고 배너, 버튼을 가리키는 말풍선, 손 든 동안의 캡슐, 통화 중 캡슐, 떠오르는 반응입니다.") {
        KCallBanner("화면을 공유하고 있어요. 알림도 함께 보여요.", status = KStatus.Error, actionLabel = "중지", onAction = {})
        KRaisedHandPill(hand, { hand = false })
        KOngoingCallPill(start, {}, label = "윤아", onEnd = {})
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            KCallDock(
                listOf(
                    KCallDockItem("audio", KCallIcons.Bluetooth, "오디오", { bubble = true }),
                    KCallDockItem("react", KCallIcons.Reaction, "반응", { reactions.send(listOf("👍", "❤️", "🎉").random(), "나") }),
                ),
                Modifier.fillMaxWidth().align(androidx.compose.ui.Alignment.BottomCenter),
                onItemPositioned = { key, bounds -> if (key == "audio") anchor = bounds },
            )
            KReactionOverlay(reactions, bottomPadding = 90.dp)
            KCallBubble(bubble, "Galaxy Buds3로 듣는 중", anchor, onDismiss = { bubble = false }, actionLabel = "되돌리기", onAction = {})
        }
        KReactionPicker({ reactions.send(it, "나") })
    }
}

@Composable
private fun LobbyCard() {
    var mic by remember { mutableStateOf(true) }
    var camera by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("김하늘") }
    DemoCard("입장 전 화면과 내 작은 창", "카메라 미리보기, 마이크 소리 크기, 켜고 끄기, 이름, 참여 버튼. 미리보기 위 작은 창은 끌어서 모서리에 붙습니다.") {
        Box(Modifier.fillMaxWidth().consumeWindowInsets(WindowInsets.safeDrawing).height(640.dp)) {
            KCallLobby(
                "주간 디자인 싱크",
                {},
                preview = if (camera) ({ LobbyPreview() }) else null,
                subtitle = "3명이 기다리는 중",
                micOn = mic,
                onToggleMic = { mic = !mic },
                cameraOn = camera,
                onToggleCamera = { camera = !camera },
                micLevel = { 0.55f },
                name = name,
                onNameChange = { name = it },
            )
        }
    }
}

@Composable
private fun BoxScope.LobbyPreview() {
    DemoVideo(4)
    KSelfView(size = androidx.compose.ui.unit.DpSize(72.dp, 96.dp), contentPadding = PaddingValues(12.dp)) { DemoVideo(0) }
}

/** 카메라 영상 대신 쓰는 그라디언트 */
@Composable
private fun BoxScope.DemoVideo(seed: Int) {
    Box(Modifier.matchParentSize().background(Brush.linearGradient(artPalette(seed * 3 + 1))))
}
