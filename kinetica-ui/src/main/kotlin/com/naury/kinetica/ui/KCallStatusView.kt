// 파일 이름은 대표 컴포저블인 KCallStatusView를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** [KCallStatusView]가 보여 줄 상태 */
public enum class KCallStatus {
    /** 연결 중. 아바타 뒤로 펄스 링이 퍼진다. */
    Connecting,

    /** 상대를 호출하는 중. 펄스 링이 퍼진다. */
    Ringing,

    /** 방에 들어왔지만 다른 사람을 기다리는 중 */
    Waiting,

    /** 연결이 끊겨 다시 잇는 중. 링이 주황색이다. */
    Reconnecting,

    /** 통화가 끝남 */
    Ended,

    /** 연결 실패나 통화 끊김. 아바타 둘레가 빨갛다. */
    Failed,
}

/**
 * 통화 상태 화면. 가운데 큰 아바타와 이름, 상태 문구를 놓고, 연결 중이면 아바타 뒤로 펄스 링이 퍼진다. 상태 문구가 바뀌면 서서히 바뀌고
 * TalkBack이 새 문구를 읽어 준다. 아래에 단계에 맞는 버튼(연결 중이면 취소, 끝나면 닫기, 실패하면 다시 시도와 닫기)을 둔다.
 * 넘기지 않은 콜백의 버튼은 없다. 글자가 크거나 가로 화면이라 넘치면 세로로 스크롤된다. '애니메이션 삭제'면 링이 퍼지지 않는다.
 *
 * ```kotlin
 * KCallStatusView(KCallStatus.Failed, title = "김민지", message = error.text, detail = "E-403", onRetry = ::rejoin, onClose = ::finish)
 * ```
 *
 * @param title 큰 글자(상대 이름이나 방 이름). null이면 쓰지 않는다.
 * @param message 상태 문구. null이면 [status]에 맞는 기본 문구다.
 * @param avatar 가운데 아바타. null이고 [title]이 있으면 [title]의 이니셜이다.
 * @param detail 문구 아래 작은 글자(오류 코드, 통화 시간)
 * @param onCancel 연결 중, 호출 중, 기다리는 중에 보이는 취소(끊기) 버튼
 * @param onRetry 실패했을 때 보이는 다시 시도 버튼
 * @param onClose 끝나거나 실패했을 때 보이는 닫기 버튼
 * @param summary 문구 아래 내용(통화 품질 요약 카드 등)
 * @param actions 버튼 아래 덧붙일 버튼(진단 저장 등)
 */
@Composable
public fun KCallStatusView(
    status: KCallStatus,
    modifier: Modifier = Modifier,
    title: String? = null,
    message: String? = null,
    avatar: KAvatarData? = null,
    detail: String? = null,
    onCancel: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    summary: (@Composable () -> Unit)? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val ink = surface.contentColor
    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(SIDE),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(TOP_ROOM))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val who = avatar ?: title?.let { KAvatarData(it) }
                if (who != null) StatusAvatar(who, status, AVATAR)
                Spacer(Modifier.height(RING_ROOM))
                title?.let {
                    ThemedText(
                        it,
                        Modifier.semantics { heading() },
                        style = TextStyle(
                            color = ink,
                            fontSize = TITLE,
                            fontWeight = KineticaTheme.design.title(FontWeight.Bold),
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 2,
                    )
                }
                StatusMessage(message ?: status.defaultMessage(), ink)
                detail?.let { CallCaption(it, Modifier.padding(top = 4.dp), alpha = DETAIL_ALPHA) }
                summary?.let {
                    Spacer(Modifier.height(SECTION))
                    it()
                }
            }
            Column(Modifier.padding(top = SECTION), horizontalAlignment = Alignment.CenterHorizontally) {
                StatusButtons(status, onCancel, onRetry, onClose, surface)
                actions?.let {
                    Column(
                        Modifier.padding(top = 8.dp).widthIn(max = BUTTON_MAX),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        content = it,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusMessage(text: String, ink: Color) {
    AnimatedContent(
        text,
        transitionSpec = { fadeIn(tween(FADE_IN_MS)) togetherWith fadeOut(tween(FADE_OUT_MS)) },
        label = "call-status",
        modifier = Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
    ) { shown ->
        ThemedText(shown, style = TextStyle(color = ink.copy(alpha = MESSAGE_ALPHA), fontSize = MESSAGE, textAlign = TextAlign.Center))
    }
}

@Composable
private fun StatusButtons(
    status: KCallStatus,
    onCancel: (() -> Unit)?,
    onRetry: (() -> Unit)?,
    onClose: (() -> Unit)?,
    surface: SurfaceStyle,
) {
    val waiting = status == KCallStatus.Connecting || status == KCallStatus.Ringing || status == KCallStatus.Waiting ||
        status == KCallStatus.Reconnecting
    if (waiting) {
        onCancel?.let {
            KCallControlButton(
                KIcons.CallEnd,
                stringResource(R.string.kinetica_ui_call_cancel),
                it,
                tone = KTone.Destructive,
                size = KCallControlDefaults.LargeSize,
                surface = surface,
            )
        }
        return
    }
    Column(Modifier.widthIn(max = BUTTON_MAX).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (status == KCallStatus.Failed) {
            onRetry?.let {
                KButton(stringResource(R.string.kinetica_ui_call_retry), it, fullWidth = true, size = KButtonSize.Large, surface = surface)
            }
        }
        onClose?.let {
            KButton(
                stringResource(R.string.kinetica_ui_call_close),
                it,
                fullWidth = true,
                size = KButtonSize.Large,
                variant = if (status == KCallStatus.Failed && onRetry != null) KVariant.Tonal else KVariant.Filled,
                tone = if (status == KCallStatus.Failed && onRetry != null) KTone.Neutral else KTone.Accent,
                surface = surface,
            )
        }
    }
}

/** 상태에 맞춰 링이 퍼지거나 둘레가 물든 아바타 */
@Composable
private fun StatusAvatar(avatar: KAvatarData, status: KCallStatus, size: Dp) {
    val pulsing = status != KCallStatus.Ended && status != KCallStatus.Failed
    val ring = when (status) {
        KCallStatus.Reconnecting -> Color(CALL_WARNING)
        KCallStatus.Failed -> KineticaTheme.colors.destructive
        else -> KineticaTheme.colors.accent
    }
    val still = reducedMotion()
    val clock = rememberLoopClock(running = pulsing && !still)
    Box(
        Modifier
            .size(size)
            .loopVisibility(clock)
            .drawBehind {
                val base = this.size.minDimension / 2
                if (status == KCallStatus.Failed) {
                    drawCircle(ring, base + FAILED_RING.toPx(), style = Stroke(FAILED_STROKE.toPx()))
                    return@drawBehind
                }
                if (!pulsing) return@drawBehind
                val phase = if (still) PULSE_STILL else (clock.seconds / PULSE_PERIOD_S) % 1f
                repeat(PULSE_COUNT) { i ->
                    val p = (phase + i / PULSE_COUNT.toFloat()) % 1f
                    drawCircle(ring.copy(alpha = (1f - p) * PULSE_ALPHA), base * (1f + PULSE_SPREAD * p))
                }
            }
            .graphicsLayer { alpha = if (status == KCallStatus.Ended) ENDED_ALPHA else 1f },
        contentAlignment = Alignment.Center,
    ) {
        CallAvatar(avatar, size)
    }
}

@Composable
private fun KCallStatus.defaultMessage(): String = stringResource(
    when (this) {
        KCallStatus.Connecting -> R.string.kinetica_ui_call_connecting
        KCallStatus.Ringing -> R.string.kinetica_ui_call_ringing
        KCallStatus.Waiting -> R.string.kinetica_ui_call_waiting
        KCallStatus.Reconnecting -> R.string.kinetica_ui_call_reconnecting
        KCallStatus.Ended -> R.string.kinetica_ui_call_ended
        KCallStatus.Failed -> R.string.kinetica_ui_call_failed
    },
)

private val SIDE = 24.dp
private val TOP_ROOM = 8.dp
private val AVATAR = 120.dp
private val RING_ROOM = 52.dp
private val SECTION = 20.dp
private val TITLE = 28.sp
private val MESSAGE = 16.sp
private val BUTTON_MAX = 360.dp
private val FAILED_RING = 6.dp
private val FAILED_STROKE = 3.dp
private const val MESSAGE_ALPHA = 0.72f
private const val DETAIL_ALPHA = 0.55f
private const val FADE_IN_MS = 240
private const val FADE_OUT_MS = 160
private const val PULSE_COUNT = 3
private const val PULSE_PERIOD_S = 2.4f
private const val PULSE_SPREAD = 0.75f
private const val PULSE_ALPHA = 0.32f
private const val PULSE_STILL = 0.35f
private const val ENDED_ALPHA = 0.7f
