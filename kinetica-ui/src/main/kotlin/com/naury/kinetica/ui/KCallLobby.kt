package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 통화에 들어가기 전 화면. 카메라 미리보기, 마이크 소리 크기, 마이크와 카메라 켜고 끄기, 이름 입력, 참여 버튼을 놓는다.
 * 세로 화면은 위아래로, 폭이 600dp 이상이면 미리보기와 입력을 나란히 놓는다. 키보드가 뜨면 입력란과 버튼이 그 위로 올라온다.
 * 넘기지 않은 콜백의 버튼이나 입력란은 없다.
 *
 * ```kotlin
 * KCallLobby(
 *     title = "주간 싱크", onJoin = vm::join,
 *     preview = if (cameraOn) ({ CameraPreview() }) else null,
 *     micOn = micOn, onToggleMic = vm::toggleMic, cameraOn = cameraOn, onToggleCamera = vm::toggleCamera,
 *     micLevel = { vm.micLevel.value }, name = name, onNameChange = vm::setName,
 * )
 * ```
 *
 * @param preview 카메라 미리보기 슬롯. null이면 [name]의 아바타와 '카메라가 꺼져 있어요'를 보여 준다. 거울처럼 뒤집는 것은 호스트가 정한다.
 * @param subtitle 제목 아래 문구(예: '3명이 기다리는 중')
 * @param micLevel 마이크 소리 크기(0..1). 그리기 단계에서 읽는다.
 * @param onNameChange 이름이 바뀔 때. null이면 이름 입력란이 없다.
 * @param joining true면 참여 버튼이 도는 링을 보이고 누를 수 없다.
 */
@Composable
public fun KCallLobby(
    title: String,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
    preview: (@Composable BoxScope.() -> Unit)? = null,
    subtitle: String? = null,
    micOn: Boolean = true,
    onToggleMic: (() -> Unit)? = null,
    cameraOn: Boolean = true,
    onToggleCamera: (() -> Unit)? = null,
    micLevel: () -> Float = { 0f },
    name: String = "",
    onNameChange: ((String) -> Unit)? = null,
    joinLabel: String = stringResource(R.string.kinetica_ui_call_lobby_join),
    joining: Boolean = false,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val ink = surface.contentColor
    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        val wide = maxWidth >= WIDE
        val previewBox: @Composable (Modifier) -> Unit = { m ->
            LobbyPreview(m, preview, name, LobbyMedia(micOn, onToggleMic, cameraOn, onToggleCamera, micLevel), surface)
        }
        val form: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    ThemedText(
                        title,
                        Modifier.semantics { heading() },
                        style = TextStyle(color = ink, fontSize = 26.sp, fontWeight = KineticaTheme.design.title(FontWeight.Bold)),
                    )
                    subtitle?.let { CallCaption(it, Modifier.padding(top = 4.dp)) }
                }
                onNameChange?.let {
                    KTextField(name, it, stringResource(R.string.kinetica_ui_call_lobby_name), Modifier.fillMaxWidth(), surface = surface)
                }
                KButton(
                    joinLabel,
                    onJoin,
                    fullWidth = true,
                    size = KButtonSize.Large,
                    loading = joining,
                    enabled = !joining,
                    surface = surface,
                )
            }
        }
        if (wide) {
            Row(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                previewBox(Modifier.weight(PREVIEW_WEIGHT))
                form(Modifier.weight(1f).verticalScroll(rememberScrollState()))
            }
        } else {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                previewBox(Modifier.widthIn(max = PREVIEW_MAX))
                form(Modifier.widthIn(max = PREVIEW_MAX))
            }
        }
    }
}

/** 미리보기 위 마이크와 카메라 상태 */
private class LobbyMedia(
    val micOn: Boolean,
    val onToggleMic: (() -> Unit)?,
    val cameraOn: Boolean,
    val onToggleCamera: (() -> Unit)?,
    val micLevel: () -> Float,
)

@Composable
private fun LobbyPreview(
    modifier: Modifier,
    preview: (@Composable BoxScope.() -> Unit)?,
    name: String,
    media: LobbyMedia,
    surface: SurfaceStyle,
) {
    val shape = shapeFor { card }
    val back = lerp(surface.inkBackground(), surface.contentColor, PREVIEW_TINT)
    // 카메라 영상 위에서는 밝기를 알 수 없으므로 어두운 표면으로 버튼을 그린다.
    val dark = if (preview != null) surface.forMedia() else surface
    Box(modifier.fillMaxWidth().aspectRatio(PREVIEW_RATIO).clip(shape).background(back)) {
        if (preview != null) {
            preview()
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CallAvatar(KAvatarData(name.ifBlank { "?" }), AVATAR)
                CallCaption(stringResource(R.string.kinetica_ui_call_lobby_camera_off), Modifier.padding(top = 12.dp))
            }
        }
        if (media.micOn) {
            val label = stringResource(R.string.kinetica_ui_call_lobby_mic_level)
            Box(Modifier.align(Alignment.TopEnd).padding(12.dp).clearAndSetSemantics { contentDescription = label }) {
                KAudioLevelIndicator(
                    media.micLevel,
                    size = 24.dp,
                    color = if (preview !=
                        null
                    ) {
                        Color.White
                    } else {
                        KineticaTheme.colors.accent
                    },
                )
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            media.onToggleMic?.let {
                KCallControlButton(
                    KIcons.Mic,
                    stringResource(R.string.kinetica_ui_call_mic),
                    it,
                    checked = !media.micOn,
                    checkedIcon = KIcons.MicOff,
                    showLabel = false,
                    surface = dark,
                )
            }
            media.onToggleCamera?.let {
                KCallControlButton(
                    KIcons.Video,
                    stringResource(R.string.kinetica_ui_call_camera),
                    it,
                    checked = !media.cameraOn,
                    checkedIcon = KIcons.VideoOff,
                    showLabel = false,
                    surface = dark,
                )
            }
        }
    }
}

private val WIDE = 600.dp
private const val PREVIEW_WEIGHT = 1.2f
private val PREVIEW_MAX = 420.dp
private val AVATAR = 88.dp
private const val PREVIEW_RATIO = 0.8f
private const val PREVIEW_TINT = 0.1f
