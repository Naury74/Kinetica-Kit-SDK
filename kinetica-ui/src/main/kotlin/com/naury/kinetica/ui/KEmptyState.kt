package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/**
 * 빈 목록, 오류, 오프라인 화면. 원판 위의 그림이 천천히 둥실거리고, 아래에 제목과 설명, 동작 버튼을 가운데 정렬로 놓는다.
 * 그림은 화면에 보이는 동안만 움직이며, '애니메이션 삭제' 접근성 설정이 켜져 있으면 멈춘다. 제목은 TalkBack에서 제목으로 읽힌다.
 *
 * ```kotlin
 * KEmptyState(
 *     title = "검색 결과가 없어요",
 *     message = "다른 검색어로 다시 찾아보세요",
 *     art = { KEmptyIcon(KEmptyIconKind.NoResults) },
 *     action = { KButton("검색어 지우기", onClick = ::clear) },
 * )
 * ```
 *
 * @param art 원판 안에 놓을 그림. 기본값은 [KEmptyIconKind.NoResults] 그림이다. 원판은 지름 112dp이므로 72dp 안팎이 알맞다.
 * @param action 설명 아래에 놓을 버튼 등. `null`이면 두지 않는다.
 */
@Composable
public fun KEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    art: @Composable () -> Unit = { KEmptyIcon(KEmptyIconKind.NoResults) },
    action: (@Composable () -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val ink = LocalKineticaContentColor.current
    Column(modifier.padding(OUTER_PADDING).widthIn(max = MAX_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingDisc(surface, art)
        Spacer(Modifier.height(ART_GAP))
        ThemedText(
            title,
            Modifier.semantics { heading() },
            style = TextStyle(
                color = ink,
                fontSize = 20.sp,
                fontWeight = KineticaTheme.design.title(FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                lineBreak = PhraseBreak,
            ),
        )
        message?.let {
            Spacer(Modifier.height(TEXT_GAP))
            ThemedText(
                it,
                style = TextStyle(
                    color = ink.copy(alpha = MESSAGE_ALPHA),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        action?.let {
            Spacer(Modifier.height(ART_GAP))
            it()
        }
    }
}

/** 둥실거리는 원판과 그 아래 그림자. 그림자는 원판이 올라갈수록 작고 옅어진다. */
@Composable
private fun FloatingDisc(surface: SurfaceStyle, art: @Composable () -> Unit) {
    val clock = rememberLoopClock(running = !reducedMotion())
    Box(Modifier.loopVisibility(clock), contentAlignment = Alignment.TopCenter) {
        Spacer(
            Modifier
                .padding(top = DISC_SIZE + SHADOW_GAP)
                .size(DISC_SIZE * SHADOW_WIDTH, SHADOW_HEIGHT)
                .drawBehind {
                    val lift = (1f + sin(clock.seconds * BOB_SPEED)) / 2f
                    val scale = 1f - lift * SHADOW_SHRINK
                    val w = size.width * scale
                    drawOval(
                        Color.Black.copy(alpha = SHADOW_ALPHA * (1f - lift * SHADOW_FADE)),
                        Offset((size.width - w) / 2, 0f),
                        Size(w, size.height),
                    )
                },
        )
        Box(
            Modifier
                .graphicsLayer {
                    val phase = clock.seconds * BOB_SPEED
                    translationY = -BOB_HEIGHT.toPx() * (1f + sin(phase)) / 2f
                    rotationZ = sin(phase * TILT_SPEED) * TILT_DEGREES
                }
                .size(DISC_SIZE)
                .kSurface(surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) { art() }
    }
}

private val OUTER_PADDING = 32.dp
private val MAX_WIDTH = 360.dp
private val ART_GAP = 24.dp
private val TEXT_GAP = 8.dp
private const val MESSAGE_ALPHA = 0.68f

private val DISC_SIZE: Dp = 112.dp
private val SHADOW_GAP = 6.dp
private const val SHADOW_WIDTH = 0.62f
private val SHADOW_HEIGHT = 10.dp
private const val SHADOW_ALPHA = 0.16f
private const val SHADOW_SHRINK = 0.25f
private const val SHADOW_FADE = 0.45f
private val BOB_HEIGHT = 8.dp

// 3.2초에 한 번 오르내린다.
private const val BOB_SPEED = 1.9635f
private const val TILT_SPEED = 0.5f
private const val TILT_DEGREES = 2.5f
