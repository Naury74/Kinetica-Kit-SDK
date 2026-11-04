package com.naury.kinetica.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 녹음하는 동안 입력란 자리에 놓는 줄. 깜박이는 빨간 점과 녹음 시간, [KLiveWaveform] 소리 막대, '밀어서 취소' 안내를 보여 준다.
 * 손가락을 시작 쪽으로 밀면 안내가 따라오며 옅어지고, 잠긴 뒤에는 안내 대신 누를 수 있는 '녹음 취소'가 나온다.
 * [state]가 녹음 중이 아니어도 자리를 차지하므로 `if (state.isActive)` 안에서 그린다.
 *
 * 녹음 시간은 초가 바뀔 때만 다시 구성하고, 소리 막대와 안내 위치는 그리기 단계에서만 바뀐다.
 * TalkBack은 시간을 읽지 않는다. 버튼이 '녹음 중' 상태를 알리고 취소 동작을 가진다.
 *
 * ```kotlin
 * Box {
 *     KChatInput(draft, { draft = it }, onSend = ::send, voiceButton = { KVoiceRecorderButton(..., state = recorder) })
 *     if (recorder.isActive) KVoiceRecordingBar(recorder, mic::level, Modifier.matchParentSize().padding(end = 52.dp))
 * }
 * ```
 *
 * @param level 지금 소리 크기(0..1)
 */
@Composable
public fun KVoiceRecordingBar(
    state: KVoiceRecorderState,
    level: () -> Float,
    modifier: Modifier = Modifier,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val shape = shapeFor { search }
    val ink = surface.contentColor
    val latestLevel by rememberUpdatedState(level)
    val elapsed by rememberRecordingTime(state)
    Row(
        modifier
            .heightIn(min = KMetrics.MinTouch)
            .kSurface(surface.forField(), shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
            BlinkDot(KineticaTheme.colors.destructive)
            Box(Modifier.padding(start = 8.dp)) {
                RollingText(elapsedText(elapsed), style = themed(TextStyle(color = ink, fontSize = 15.sp, fontFeatureSettings = "tnum")))
            }
        }
        KLiveWaveform(
            { latestLevel() },
            Modifier.weight(1f).height(WAVE_HEIGHT),
            active = state.isActive,
            color = surface.muted(WAVE_ALPHA),
        )
        if (state.phase == KVoiceRecorderPhase.Locked) {
            CancelText(state, ink, shape)
        } else {
            SlideHint(state, surface.muted(HINT_ALPHA))
        }
    }
}

@Composable
private fun SlideHint(state: KVoiceRecorderState, color: Color) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val arrow = if (rtl) "›" else "‹"
    ThemedText(
        "$arrow " + stringResource(R.string.kinetica_ui_voice_slide_cancel),
        Modifier
            .clearAndSetSemantics { }
            .graphicsLayer {
                val p = (state.slide.value / state.cancelDistance).coerceIn(0f, 1f)
                translationX = (if (rtl) 1f else -1f) * state.slide.value * HINT_FOLLOW
                alpha = 1f - p
            },
        style = TextStyle(color = color, fontSize = 14.sp),
        maxLines = 1,
    )
}

@Composable
private fun CancelText(state: KVoiceRecorderState, color: Color, shape: Shape) {
    ThemedText(
        stringResource(R.string.kinetica_ui_voice_cancel),
        Modifier
            .fillMaxHeight()
            .clip(shape)
            .clickable(role = Role.Button) { state.cancel() }
            .padding(horizontal = 6.dp, vertical = 12.dp),
        style = TextStyle(color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        maxLines = 1,
    )
}

/** 녹음 시간(ms). 초가 바뀔 때만 값이 바뀐다. */
@Composable
private fun rememberRecordingTime(state: KVoiceRecorderState): State<Long> = produceState(state.elapsedMillis(), state, state.isActive) {
    while (state.isActive) {
        val elapsed = state.elapsedMillis()
        value = elapsed
        delay(MILLIS_PER_SECOND - elapsed % MILLIS_PER_SECOND)
    }
}

private const val MILLIS_PER_SECOND = 1_000L
private const val WAVE_ALPHA = 0.6f
private const val HINT_ALPHA = 0.6f
private const val HINT_FOLLOW = 0.5f
private val WAVE_HEIGHT = 24.dp
