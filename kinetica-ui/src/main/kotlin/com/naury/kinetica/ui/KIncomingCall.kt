// 파일 이름은 대표 컴포저블인 KIncomingCall을 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/** [KIncomingCall]의 받기 방식 */
public enum class KIncomingCallStyle {
    /** 거절과 받기 원형 버튼. 영상 통화면 음성으로 받기와 영상으로 받기를 나눠 둔다. */
    Buttons,

    /** 밀어서 받기 트랙. 주머니에서 잘못 눌러 받는 일을 막는다. 거절과 메시지는 작은 버튼으로 둔다. */
    Slide,
}

/**
 * 걸려 온 전화의 내용. 위쪽에 '영상 통화 수신' 같은 머리글과 발신자 이름, 부가 문구를 두고 가운데에 펄스 링이 퍼지는 아바타를,
 * 아래에 받기와 거절을 놓는다. 받기 버튼은 살짝 위아래로 흔들려 눈길을 끈다. '애니메이션 삭제'면 흔들리지 않는다.
 *
 * 화면 전체 배경과 시스템 바 처리까지 한 번에 하려면 [KIncomingCallScreen]을 쓴다. 이것은 호스트 화면 안에 내용만 넣을 때 쓴다.
 *
 * ```kotlin
 * KIncomingCall("김민지", onAccept = ::answer, onDecline = ::decline, subtitle = "휴대전화", isVideo = true, onAcceptVideo = ::answerVideo)
 * ```
 *
 * @param onAccept 받기. 영상 통화에서 [onAcceptVideo]가 있으면 '음성으로 받기'다.
 * @param subtitle 이름 아래 문구(번호, 회사 이름 등)
 * @param isVideo 영상 통화인지. 머리글과 받기 아이콘이 바뀐다.
 * @param onAcceptVideo 영상으로 받기. 영상 통화일 때만 쓴다.
 * @param onMessage 메시지로 답장. 넘기면 메시지 버튼이 생기고, 누르면 [quickReplies]를 고르는 바텀 시트가 열린다. 고른 문구를 받는다.
 * @param quickReplies 빠른 답장 문구
 * @param style 받기 방식
 */
@Composable
public fun KIncomingCall(
    callerName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: KAvatarData? = null,
    subtitle: String? = null,
    isVideo: Boolean = false,
    onAcceptVideo: (() -> Unit)? = null,
    onMessage: ((String) -> Unit)? = null,
    quickReplies: List<String> = emptyList(),
    style: KIncomingCallStyle = KIncomingCallStyle.Buttons,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    var replying by remember { mutableStateOf(false) }
    val ink = surface.contentColor
    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val compact = maxHeight < COMPACT_HEIGHT
        Column(
            Modifier.fillMaxWidth().verticalScroll(
                rememberScrollState(),
            ).heightIn(min = maxHeight).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    liveRegion =
                        LiveRegionMode.Polite
                },
            ) {
                CallCaption(
                    stringResource(if (isVideo) R.string.kinetica_ui_call_incoming_video else R.string.kinetica_ui_call_incoming_voice),
                    alpha = HEADER_ALPHA,
                )
                Spacer(Modifier.height(6.dp))
                ThemedText(
                    callerName,
                    Modifier.semantics { heading() },
                    style = TextStyle(
                        color = ink,
                        fontSize = if (compact) 28.sp else NAME_SIZE,
                        fontWeight = KineticaTheme.design.title(FontWeight.SemiBold),
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
                )
                subtitle?.let { CallCaption(it, Modifier.padding(top = 4.dp), alpha = SUBTITLE_ALPHA) }
            }
            Box(Modifier.padding(vertical = if (compact) 16.dp else 40.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(if (compact) 88.dp else AVATAR).speakingRipple(true, { RING_LEVEL }, KineticaTheme.colors.positive)) {
                    CallAvatar(avatar ?: KAvatarData(callerName), if (compact) 88.dp else AVATAR)
                }
            }
            IncomingActions(
                IncomingHandlers(
                    onAccept,
                    onDecline,
                    onAcceptVideo.takeIf {
                        isVideo
                    },
                    onMessage?.let { { replying = true } },
                ),
                isVideo,
                style,
                surface,
            )
        }
    }
    if (onMessage != null) QuickReplySheet(replying, quickReplies, onDismiss = { replying = false }, onPick = onMessage, surface = surface)
}

/** 받기 화면 버튼 동작 묶음 */
private class IncomingHandlers(val accept: () -> Unit, val decline: () -> Unit, val acceptVideo: (() -> Unit)?, val message: (() -> Unit)?)

@Composable
private fun IncomingActions(handlers: IncomingHandlers, isVideo: Boolean, style: KIncomingCallStyle, surface: SurfaceStyle) {
    val decline = stringResource(R.string.kinetica_ui_call_decline)
    val message = stringResource(R.string.kinetica_ui_call_message)
    if (style == KIncomingCallStyle.Slide) {
        SlideActions(handlers, decline, message, surface)
        return
    }
    ButtonActions(handlers, isVideo, decline, message, surface)
}

/** 밀어서 받기 트랙과 그 위 작은 버튼들 */
@Composable
private fun SlideActions(handlers: IncomingHandlers, decline: String, message: String, surface: SurfaceStyle) {
    Column(Modifier.widthIn(max = ACTION_MAX).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            KCallControlButton(KIcons.CallEnd, decline, handlers.decline, tone = KTone.Destructive, surface = surface)
            handlers.message?.let { KCallControlButton(KIcons.Message, message, it, surface = surface) }
            handlers.acceptVideo?.let {
                KCallControlButton(
                    KIcons.Video,
                    stringResource(R.string.kinetica_ui_call_accept_video),
                    it,
                    tone = KTone.Positive,
                    surface = surface,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        KSlideToConfirm(
            stringResource(R.string.kinetica_ui_call_slide_answer),
            onConfirm = handlers.accept,
            modifier = Modifier.fillMaxWidth(),
            surface = surface,
        )
    }
}

/** 거절과 받기 원형 버튼. 영상 통화면 음성과 영상 받기를 나눈다. */
@Composable
private fun ButtonActions(handlers: IncomingHandlers, isVideo: Boolean, decline: String, message: String, surface: SurfaceStyle) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        handlers.message?.let {
            KCallControlButton(KIcons.Message, message, it, size = KCallControlDefaults.CompactSize, surface = surface)
        }
        Row(
            Modifier.widthIn(max = ACTION_MAX).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            KCallControlButton(
                KIcons.CallEnd,
                decline,
                handlers.decline,
                tone = KTone.Destructive,
                size = KCallControlDefaults.LargeSize,
                surface = surface,
            )
            val video = handlers.acceptVideo
            if (video != null) {
                AcceptButton(
                    KIcons.Call,
                    stringResource(R.string.kinetica_ui_call_accept_voice),
                    stringResource(R.string.kinetica_ui_call_accept_voice_long),
                    handlers.accept,
                    surface,
                    0f,
                )
                AcceptButton(
                    KIcons.Video,
                    stringResource(R.string.kinetica_ui_call_accept_video),
                    stringResource(R.string.kinetica_ui_call_accept_video_long),
                    video,
                    surface,
                    HALF_TURN,
                )
            } else {
                val label = stringResource(R.string.kinetica_ui_call_accept)
                AcceptButton(if (isVideo) KIcons.Video else KIcons.Call, label, label, handlers.accept, surface, 0f)
            }
        }
    }
}

/** 위아래로 살짝 흔들리는 받기 버튼. [phase]만큼 어긋나 둘이 함께 있어도 번갈아 흔들린다. */
@Composable
private fun AcceptButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    longLabel: String,
    onClick: () -> Unit,
    surface: SurfaceStyle,
    phase: Float,
) {
    val still = reducedMotion()
    val clock = rememberLoopClock(running = !still)
    val bob = BOB.value
    KCallControlButton(
        icon,
        label,
        onClick,
        Modifier.loopVisibility(clock).graphicsLayer {
            if (!still) translationY = -bob * density * sin((clock.seconds / BOB_PERIOD_S * 2 * PI + phase).toFloat()).coerceAtLeast(0f)
        },
        tone = KTone.Positive,
        size = KCallControlDefaults.LargeSize,
        stateDescription = longLabel.takeIf { it != label },
        surface = surface,
    )
}

/** 빠른 답장 문구를 고르는 바텀 시트 */
@Composable
private fun QuickReplySheet(
    visible: Boolean,
    replies: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    surface: SurfaceStyle,
) {
    val title = stringResource(R.string.kinetica_ui_call_reply_title)
    KSheet(visible, onDismiss, surface = surface.forPopup(), title = title) {
        SheetTitle(title)
        replies.forEach { reply ->
            KListItem(reply, leading = KListLeading.Icon(KIcons.Message), onClick = {
                onPick(reply)
                onDismiss()
            })
        }
    }
}

/**
 * 걸려 온 전화 화면 전체. 발신자 색으로 물든 어두운 배경 위에 [KIncomingCall]을 놓는다. 영상 통화면 [background]에 내 카메라 미리보기를 깔 수 있다.
 * 배경이 어두우므로 표면은 [SurfaceStyle]의 어두운 짝으로 바꿔 쓴다(글래스는 LiquidDark, 단색은 CleanDark, 톤은 어두운 톤).
 * 메시지 시트도 이 안에서 블러 배경으로 열린다.
 *
 * ```kotlin
 * KIncomingCallScreen("김민지", onAccept = ::answer, onDecline = ::decline, onMessage = ::reply, quickReplies = replies)
 * ```
 *
 * @param background 배경 위에 깔 내용(영상 통화의 내 카메라 미리보기 등). null이면 발신자 색 그라디언트만 그린다.
 * @see KIncomingCall 나머지 파라미터
 */
@Composable
public fun KIncomingCallScreen(
    callerName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: KAvatarData? = null,
    subtitle: String? = null,
    isVideo: Boolean = false,
    onAcceptVideo: (() -> Unit)? = null,
    onMessage: ((String) -> Unit)? = null,
    quickReplies: List<String> = emptyList(),
    style: KIncomingCallStyle = KIncomingCallStyle.Buttons,
    background: (@Composable BoxScope.() -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface.forMedia(),
) {
    val tint = (avatar ?: KAvatarData(callerName)).backgroundColor()
    KineticaScaffold(
        modifier.fillMaxSize(),
        surface = surface,
        background = {
            CallBackdrop(tint)
            background?.invoke(this)
        },
        edgeFade = false,
        systemBarIcons = true,
    ) {
        KIncomingCall(
            callerName,
            onAccept,
            onDecline,
            Modifier,
            avatar,
            subtitle,
            isVideo,
            onAcceptVideo,
            onMessage,
            quickReplies,
            style,
            surface,
        )
    }
}

/** 어두운 영상이나 사진 위에 쓸 표면. 이미 어두우면 그대로다. */
internal fun SurfaceStyle.forMedia(): SurfaceStyle = if (isDark) {
    this
} else {
    when (this) {
        is SurfaceStyle.Glass -> SurfaceStyles.LiquidDark
        is SurfaceStyle.Solid -> SurfaceStyles.CleanDark
        is SurfaceStyle.Tonal -> copy(dark = true)
    }
}

/** [tint]로 물든 어두운 통화 배경. 위쪽이 조금 밝고 아래로 갈수록 어두워진다. */
@Composable
internal fun BoxScope.CallBackdrop(tint: Color) {
    val top = lerp(tint, Color.Black, BACK_TOP)
    val bottom = lerp(tint, Color.Black, BACK_BOTTOM)
    Box(
        Modifier.matchParentSize().background(Brush.verticalGradient(listOf(top, bottom))).background(
            Brush.radialGradient(
                listOf(tint.copy(alpha = GLOW_ALPHA), Color.Transparent),
                center = Offset(GLOW_X, GLOW_Y),
                radius = GLOW_RADIUS,
            ),
        ),
    )
}

private val COMPACT_HEIGHT = 560.dp
private val AVATAR = 128.dp
private val NAME_SIZE = 34.sp
private val ACTION_MAX = 360.dp
private val BOB = 4.dp
private const val BOB_PERIOD_S = 1.6f
private const val HALF_TURN = PI.toFloat()
private const val HEADER_ALPHA = 0.7f
private const val SUBTITLE_ALPHA = 0.7f
private const val RING_LEVEL = 0.6f
private const val BACK_TOP = 0.45f
private const val BACK_BOTTOM = 0.85f
private const val GLOW_ALPHA = 0.45f
private const val GLOW_X = 300f
private const val GLOW_Y = 200f
private const val GLOW_RADIUS = 900f
