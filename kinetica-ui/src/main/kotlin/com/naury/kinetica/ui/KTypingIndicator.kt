package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 상대가 입력 중임을 알리는 점 세 개. 점이 차례로 튀어 오르며 밝아진다. [bubble]이면 받은 말풍선과 같은 모양(꼬리 포함) 안에 그려
 * [KChatBubble] 목록 끝에 그대로 놓을 수 있다. 화면 밖으로 스크롤되면 멈추고, '애니메이션 삭제' 설정에서는 움직이지 않는다.
 * TalkBack은 나타날 때 '입력 중'을 알린다.
 *
 * @param color 점 색. 기본값은 표면 글자 색이다.
 */
@Composable
public fun KTypingIndicator(
    modifier: Modifier = Modifier,
    bubble: Boolean = true,
    color: Color = Color.Unspecified,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val label = stringResource(R.string.kinetica_ui_chat_typing)
    val clock = rememberLoopClock(running = !reducedMotion())
    val ink = if (color == Color.Unspecified) surface.contentColor else color
    val dots = Modifier
        .size(DOTS_WIDTH, DOTS_HEIGHT)
        .loopVisibility(clock)
        .drawBehind { drawTypingDots(ink, clock.seconds) }
    val described = Modifier.clearAndSetSemantics {
        contentDescription = label
        liveRegion = LiveRegionMode.Polite
    }
    if (!bubble) {
        Box(modifier.then(described).then(dots))
        return
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
        Box(
            described
                .kSurface(surface, ChatBubbleShape(sent = false, KChatGroupPosition.Single, tail = true))
                .padding(start = PADDING + TAIL_GUTTER, end = PADDING, top = 13.dp, bottom = 13.dp),
        ) { Box(dots) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTypingDots(color: Color, seconds: Float) {
    val radius = size.height / 2 * DOT_SHARE
    val gap = (size.width - radius * 2 * DOTS) / (DOTS - 1)
    for (i in 0 until DOTS) {
        // 점마다 위상을 밀어 왼쪽부터 차례로 튄다. 마이너스 구간은 0으로 잘라 쉬는 시간을 둔다.
        val wave = sin((seconds / PERIOD_S - i * PHASE) * 2 * PI).toFloat().coerceAtLeast(0f)
        val x = radius + i * (radius * 2 + gap)
        val y = size.height - radius - wave * (size.height - radius * 2)
        drawCircle(color.copy(alpha = color.alpha * (REST_ALPHA + (1f - REST_ALPHA) * wave)), radius, Offset(x, y))
    }
}

private val PADDING = 14.dp
private val DOTS_WIDTH = 34.dp
private val DOTS_HEIGHT = 12.dp
private const val DOTS = 3
private const val DOT_SHARE = 0.62f
private const val PERIOD_S = 1.25f
private const val PHASE = 0.16f
private const val REST_ALPHA = 0.4f
