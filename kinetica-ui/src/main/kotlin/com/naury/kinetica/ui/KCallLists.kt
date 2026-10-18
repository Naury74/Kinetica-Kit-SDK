package com.naury.kinetica.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * 참가자 목록 행. 앞에 아바타(말하는 동안 둘레에 물결), 가운데에 이름과 상태 문구, 끝에 상태 아이콘과 동작 버튼을 놓는다.
 * 상태(역할, 손 듦, 말하는 중, 마이크 꺼짐, 카메라 꺼짐, 약한 연결)는 이름 아래 한 줄 글자로도 쓰므로 TalkBack은 행 하나로 다 읽는다.
 * 넘기지 않은 동작의 버튼은 없다.
 *
 * ```kotlin
 * KParticipantRow(p.name, role = "진행자", muted = p.muted, speaking = p.speaking, onPin = { pin(p.id) }, onRemove = { kick(p.id) })
 * ```
 *
 * @param role 이름 아래 앞쪽에 쓸 역할(진행자 등)
 * @param videoOff 카메라가 꺼졌는지
 * @param audioLevel 소리 크기(0..1). 그리기 단계에서 읽는다.
 * @param onClick 행을 눌렀을 때(프로필 열기 등)
 * @param onPin 고정과 해제 버튼
 * @param onMute 상대 마이크 끄기 버튼. 권한이 있을 때만 넘긴다.
 * @param onRemove 통화에서 내보내기 버튼. 권한이 있을 때만 넘긴다.
 */
@Composable
public fun KParticipantRow(
    name: String,
    modifier: Modifier = Modifier,
    avatar: KAvatarData? = null,
    role: String? = null,
    muted: Boolean = false,
    videoOff: Boolean = false,
    handRaised: Boolean = false,
    speaking: Boolean = false,
    audioLevel: () -> Float = { 0f },
    quality: KNetworkQuality = KNetworkQuality.Unknown,
    pinned: Boolean = false,
    onClick: (() -> Unit)? = null,
    onPin: (() -> Unit)? = null,
    onMute: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val states = buildList {
        role?.let(::add)
        if (pinned) add(stringResource(R.string.kinetica_ui_call_pinned))
        if (handRaised) add(stringResource(R.string.kinetica_ui_call_hand_raised))
        if (speaking) add(stringResource(R.string.kinetica_ui_call_speaking))
        if (muted) add(stringResource(R.string.kinetica_ui_call_mic_off))
        if (videoOff) add(stringResource(R.string.kinetica_ui_call_camera_off))
        if (quality.isWeak) add(quality.label())
    }
    val accent = KineticaTheme.colors.accent
    KListItem(
        name,
        modifier,
        supporting = states.joinToString(" · ").ifEmpty { null },
        leading = KListLeading.Custom {
            Box(Modifier.size(ROW_AVATAR).speakingRipple(speaking, audioLevel, accent)) {
                CallAvatar(avatar ?: KAvatarData(name), ROW_AVATAR)
            }
        },
        trailing = KListTrailing.Custom { RowTrailing(RowFlags(muted, videoOff, handRaised, pinned), onPin, onMute, onRemove, surface) },
        onClick = onClick,
        supportingMaxLines = 2,
        surface = surface,
    )
}

/** 행 끝 아이콘을 고를 상태 */
private class RowFlags(val muted: Boolean, val videoOff: Boolean, val handRaised: Boolean, val pinned: Boolean)

@Composable
private fun RowTrailing(flags: RowFlags, onPin: (() -> Unit)?, onMute: (() -> Unit)?, onRemove: (() -> Unit)?, surface: SurfaceStyle) {
    val ink = surface.muted(ICON_ALPHA)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clearAndSetSemantics {
            },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (flags.handRaised) StatusGlyph(KCallIcons.Hand, Color(HAND))
            if (flags.videoOff) StatusGlyph(KIcons.VideoOff, ink)
            StatusGlyph(if (flags.muted) KIcons.MicOff else KIcons.Mic, if (flags.muted) Color(CALL_WARNING) else ink)
        }
        onPin?.let {
            KIconButton(
                KCallIcons.Pin,
                stringResource(if (flags.pinned) R.string.kinetica_ui_call_unpin else R.string.kinetica_ui_call_pin),
                it,
                variant = if (flags.pinned) KVariant.Tonal else KVariant.Text,
                tone = if (flags.pinned) KTone.Accent else KTone.Neutral,
                size = KIconButtonSizes.Small,
                surface = surface,
            )
        }
        onMute?.let {
            KIconButton(
                KIcons.MicOff,
                stringResource(R.string.kinetica_ui_call_mute_person),
                it,
                variant = KVariant.Text,
                tone = KTone.Neutral,
                size = KIconButtonSizes.Small,
                surface = surface,
            )
        }
        onRemove?.let {
            KIconButton(
                KIcons.Close,
                stringResource(R.string.kinetica_ui_call_remove),
                it,
                variant = KVariant.Text,
                tone = KTone.Destructive,
                size = KIconButtonSizes.Small,
                surface = surface,
            )
        }
    }
}

@Composable
private fun StatusGlyph(icon: ImageVector, tint: Color) {
    Image(rememberVectorPainter(icon), null, Modifier.size(GLYPH), colorFilter = ColorFilter.tint(tint))
}

/**
 * 오디오 출력 고르기. 경로마다 종류 아이콘과 이름을 쓰고, 지금 쓰는 경로에 라디오 표시를 둔다. 행 어디를 눌러도 고르고,
 * TalkBack은 행 하나를 라디오 버튼으로 읽는다. 고를 수 없는 경로는 흐리게 그리고 이유를 아래에 쓴다. 바텀 시트 안에 넣어 쓴다.
 *
 * ```kotlin
 * KSheet(open, { open = false }, title = "오디오 출력") {
 *     KAudioRouteList(routes, selected, onSelect = { audio.select(it); open = false })
 * }
 * ```
 */
@Composable
public fun KAudioRouteList(
    routes: List<KAudioRoute>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    Column(modifier) {
        routes.forEach { route ->
            KListItem(
                route.name,
                supporting = route.disabledReason.takeIf { !route.enabled } ?: route.type.label().takeIf { it != route.name },
                leading = KListLeading.Icon(route.type.icon()),
                trailing = KListTrailing.Radio(route.id == selectedId) { onSelect(route.id) },
                enabled = route.enabled,
                surface = surface,
            )
        }
    }
}

/** 오디오 출력 종류 아이콘 */
public fun KAudioRouteType.icon(): ImageVector = when (this) {
    KAudioRouteType.Earpiece -> KCallIcons.Phone
    KAudioRouteType.Speaker -> KIcons.Speaker
    KAudioRouteType.Wired -> KCallIcons.Headphones
    KAudioRouteType.Bluetooth -> KCallIcons.Bluetooth
}

@Composable
internal fun KAudioRouteType.label(): String = stringResource(
    when (this) {
        KAudioRouteType.Earpiece -> R.string.kinetica_ui_call_route_earpiece
        KAudioRouteType.Speaker -> R.string.kinetica_ui_call_route_speaker
        KAudioRouteType.Wired -> R.string.kinetica_ui_call_route_wired
        KAudioRouteType.Bluetooth -> R.string.kinetica_ui_call_route_bluetooth
    },
)

private val ROW_AVATAR = 40.dp
private val GLYPH = 18.dp
private const val ICON_ALPHA = 0.6f
private const val HAND = 0xFFFFB800
