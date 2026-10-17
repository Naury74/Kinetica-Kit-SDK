package com.naury.kinetica.ui

import androidx.compose.runtime.Immutable

/** 참가자 한 명의 연결 품질. [KNetworkQualityIndicator]가 막대 수와 색으로 그린다. */
@Suppress("MagicNumber") // 생성자 값이 막대 수 그 자체다.
public enum class KNetworkQuality(internal val bars: Int) {
    /** 막대 셋. 아주 좋음 */
    Excellent(3),

    /** 막대 둘. 좋음 */
    Good(2),

    /** 막대 하나. 약함. 타일에 약한 연결 표시가 붙는다. */
    Poor(1),

    /** 막대 없음. 끊김. 타일에 약한 연결 표시가 붙는다. */
    Lost(0),

    /** 아직 모름. 아무 표시도 하지 않는다. */
    Unknown(0),
    ;

    /** 약하거나 끊긴 연결인지. 타일과 참가자 행이 경고 표시를 붙일 때 쓴다. */
    public val isWeak: Boolean get() = this == Poor || this == Lost
}

/** 통화 단계. [KCallScreen]이 이 값에 따라 상태 화면과 통화 화면을 바꾼다. */
public enum class KCallPhase {
    /** 발신 중이거나 방에 들어가는 중. 상대 아바타 뒤로 펄스 링이 퍼진다. */
    Connecting,

    /** 상대가 받기를 기다리는 중 */
    Ringing,

    /** 통화 중 */
    Connected,

    /** 연결이 끊겨 다시 잇는 중. 통화 화면 위에 배너가 뜬다. */
    Reconnecting,

    /** 통화가 끝남 */
    Ended,

    /** 연결 실패나 통화 끊김 */
    Failed,
}

/** 영상 무대 보기 방식 */
public enum class KCallLayout {
    /** 모두 같은 크기의 격자 */
    Gallery,

    /** 말하는 사람을 크게, 나머지는 한 줄 띠로 */
    Speaker,
}

/** 오디오 출력 종류. [KAudioRouteList]가 아이콘을 고를 때 쓴다. */
public enum class KAudioRouteType {
    /** 휴대전화 수화부 */
    Earpiece,

    /** 스피커폰 */
    Speaker,

    /** 유선 이어폰이나 USB 헤드셋 */
    Wired,

    /** 블루투스 기기 */
    Bluetooth,
}

/**
 * 오디오 출력 하나.
 *
 * @property id 호스트가 경로를 구분하는 값. [KAudioRouteList]의 `onSelect`로 돌려준다.
 * @property name 보일 이름(예: "Galaxy Buds3")
 * @property type 아이콘을 고를 종류
 * @property enabled false면 고를 수 없고 [disabledReason]을 함께 보여 준다.
 * @property disabledReason 고를 수 없는 이유. null이면 쓰지 않는다.
 */
@Immutable
public data class KAudioRoute(
    val id: String,
    val name: String,
    val type: KAudioRouteType,
    val enabled: Boolean = true,
    val disabledReason: String? = null,
)

/**
 * [KCallScreen]이 그릴 참가자 한 명. 화면 공유는 같은 사람이라도 따로 한 명으로 넣고 [isScreenShare]를 켠다.
 *
 * 소리 크기는 일부러 넣지 않았다. 초당 수십 번 바뀌는 값을 데이터 클래스에 넣으면 그때마다 화면 전체를 다시 구성하므로,
 * [KCallScreen]의 `audioLevel` 람다로 따로 받아 그리기 단계에서 읽는다.
 *
 * @property id 참가자를 구분하는 값. 같은 사람은 목록 순서가 바뀌어도 같은 값이어야 타일이 다시 만들어지지 않는다(영상이 끊기지 않는다).
 * @property name 이름표와 TalkBack에 쓰는 이름
 * @property avatar 영상이 꺼졌을 때 그릴 아바타. null이면 [name]의 이니셜이다.
 * @property isLocal 나인지. 내 영상은 거울처럼 뒤집어 그리고, 1:1 통화에서는 작은 창에 놓는다.
 * @property isMuted 마이크가 꺼졌는지
 * @property isVideoOn 카메라가 켜졌는지. false면 마지막 프레임 대신 아바타를 그린다.
 * @property isSpeaking 말하는 중인지. 글로우 테두리와 발표자 보기가 따른다.
 * @property hasRaisedHand 손을 들었는지
 * @property networkQuality 연결 품질
 * @property isScreenShare 화면 공유 트랙인지. 이름표에 '(화면)'을 붙이고 무대에서 크게 놓는다.
 * @property role 참가자 목록에 보일 역할(예: "진행자"). null이면 쓰지 않는다.
 */
@Immutable
public data class KCallParticipant(
    val id: String,
    val name: String,
    val avatar: KAvatarData? = null,
    val isLocal: Boolean = false,
    val isMuted: Boolean = false,
    val isVideoOn: Boolean = true,
    val isSpeaking: Boolean = false,
    val hasRaisedHand: Boolean = false,
    val networkQuality: KNetworkQuality = KNetworkQuality.Unknown,
    val isScreenShare: Boolean = false,
    val role: String? = null,
)

/**
 * [KCallScreen]의 상태 하나. 호스트는 자기 상태에서 이 값을 만들어 넘기고, 사용자의 조작은 [KCallActions]로 받는다.
 *
 * @property phase 통화 단계
 * @property participants 나를 포함한 참가자. 비어 있어도 된다.
 * @property title 상단 바 제목(방 이름이나 상대 이름). null이면 상대 이름이나 인원을 쓴다.
 * @property isVideoCall 영상 통화인지. false면 음성 통화 화면(큰 아바타, 그룹은 아바타 격자)을 그린다.
 * @property startedAtMillis 통화가 연결된 시각. `SystemClock.elapsedRealtime()` 기준 ms다. null이면 통화 시간을 쓰지 않는다.
 * @property isRecording 녹화 중 배지를 보일지
 * @property isEncrypted 종단 간 암호화 배지를 보일지
 * @property isSharingScreen 내가 화면을 공유하는 중인지. 공유 경고 배너가 뜨고 조작 막대가 숨지 않는다.
 * @property isFrontCamera 전면 카메라인지. 카메라 전환 버튼의 상태로 읽는다.
 * @property isSpeakerOn [audioRoutes]가 비었을 때 스피커 버튼의 켜짐 상태
 * @property audioRoutes 고를 수 있는 오디오 출력. 비어 있으면 오디오 버튼이 스피커 켜고 끄기가 된다.
 * @property selectedRouteId 지금 쓰는 오디오 출력의 [KAudioRoute.id]
 * @property unreadMessages 읽지 않은 채팅 수. 채팅 버튼 배지에 보인다.
 * @property statusText 상태 화면이나 음성 통화 이름 아래 쓸 문구(예: "휴대전화", "연결 안정"). null이면 단계에 맞는 기본 문구다.
 * @property errorMessage 실패 화면에 보일 이유
 * @property errorCode 실패 화면 아래 작게 보일 기술 코드
 * @property warning 통화 화면 위에 띄울 경고 배너 문구(미디어 오류, 다른 통화가 오디오를 가져감 등). null이면 띄우지 않는다.
 * @property reconnectSecondsLeft 재연결 배너에 보일 남은 시간(초). null이면 쓰지 않는다.
 */
@Immutable
public data class KCallState(
    val phase: KCallPhase,
    val participants: List<KCallParticipant> = emptyList(),
    val title: String? = null,
    val isVideoCall: Boolean = true,
    val startedAtMillis: Long? = null,
    val isRecording: Boolean = false,
    val isEncrypted: Boolean = false,
    val isSharingScreen: Boolean = false,
    val isFrontCamera: Boolean = true,
    val isSpeakerOn: Boolean = false,
    val audioRoutes: List<KAudioRoute> = emptyList(),
    val selectedRouteId: String? = null,
    val unreadMessages: Int = 0,
    val statusText: String? = null,
    val errorMessage: String? = null,
    val errorCode: String? = null,
    val warning: String? = null,
    val reconnectSecondsLeft: Int? = null,
) {
    /** 나. 목록에 없으면 null */
    val local: KCallParticipant? get() = participants.firstOrNull { it.isLocal }

    /** 나를 뺀 참가자 */
    val remotes: List<KCallParticipant> get() = participants.filterNot { it.isLocal }
}

/**
 * [KCallScreen]의 콜백 묶음. 넘기지 않은(null) 콜백의 버튼은 화면에 나오지 않는다. 예를 들어 [onShareScreen]이 null이면
 * 화면 공유 버튼이 없다.
 *
 * @property onToggleMute 마이크 켜고 끄기
 * @property onToggleCamera 카메라 켜고 끄기
 * @property onSwitchCamera 전면과 후면 카메라 바꾸기. 카메라가 꺼져 있으면 버튼이 흐려진다.
 * @property onToggleSpeaker [KCallState.audioRoutes]가 비었을 때 스피커 켜고 끄기
 * @property onSelectAudioRoute 오디오 출력 고르기. 경로가 있으면 오디오 버튼이 출력 선택 시트를 연다.
 * @property onShareScreen 화면 공유 시작이나 중지. 시스템 승인 대화 상자는 호스트가 띄운다.
 * @property onRaiseHand 손 들기와 내리기
 * @property onReact 반응 보내기. 고른 이모지를 받는다. 넘기면 반응 버튼과 반응 시트가 생긴다.
 * @property onOpenChat 채팅 열기. 채팅 화면은 호스트가 띄운다.
 * @property onAddPeople 참가자 초대. 참가자 시트 아래에 버튼이 생긴다.
 * @property onEnd 통화 끊기. 늘 있어야 하므로 null을 받지 않는다.
 * @property onEnterPictureInPicture 화면 속 화면으로 줄이기. 상단 바에 버튼이 생긴다. `enterPictureInPictureMode`는 호스트가 부른다.
 * @property onRetry 실패 화면의 다시 시도
 * @property onClose 종료와 실패 화면의 닫기
 * @property onSaveReport 종료와 실패 화면의 진단 저장
 * @property onMinimize 상단 바 앞쪽의 줄이기 버튼. 통화를 [KOngoingCallPill]로 접을 때 쓴다.
 */
@Immutable
public data class KCallActions(
    val onEnd: () -> Unit,
    val onToggleMute: (() -> Unit)? = null,
    val onToggleCamera: (() -> Unit)? = null,
    val onSwitchCamera: (() -> Unit)? = null,
    val onToggleSpeaker: (() -> Unit)? = null,
    val onSelectAudioRoute: ((String) -> Unit)? = null,
    val onShareScreen: (() -> Unit)? = null,
    val onRaiseHand: (() -> Unit)? = null,
    val onReact: ((String) -> Unit)? = null,
    val onOpenChat: (() -> Unit)? = null,
    val onAddPeople: (() -> Unit)? = null,
    val onEnterPictureInPicture: (() -> Unit)? = null,
    val onRetry: (() -> Unit)? = null,
    val onClose: (() -> Unit)? = null,
    val onSaveReport: (() -> Unit)? = null,
    val onMinimize: (() -> Unit)? = null,
)

/** 통화 반응 기본 이모지 여섯 개. [KCallScreen]의 반응 시트가 이 순서로 늘어놓는다. */
public val KCallReactions: List<String> = listOf("👍", "❤️", "😂", "😮", "👏", "🎉")
