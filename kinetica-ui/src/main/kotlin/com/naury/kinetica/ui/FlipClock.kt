package com.naury.kinetica.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.delay

/**
 * 숫자가 바뀔 때 기계식 플립 시계처럼 카드 위쪽 절반이 접혀 내려가며 새 숫자를 보여 주는 텍스트.
 * 숫자만 카드에 담고 콜론 같은 다른 글자는 접지 않고 그대로 둔다. 바뀐 자리만 넘어가며, 자릿수가 바뀌어도 끝자리끼리 맞춰 비교한다.
 * '애니메이션 삭제' 접근성 설정이 켜져 있으면 접지 않고 바로 바꾼다. TalkBack은 [text] 전체를 한 번에 읽는다.
 *
 * ```kotlin
 * val left by rememberCountdown(launchAt)
 * FlipDigits(countdownText(left))
 * ```
 *
 * @param textStyle 숫자 글꼴. 카드 크기는 이 글꼴로 그린 가장 넓은 숫자에 맞춘다. 글자 색은 [contentColor]를 쓴다.
 * @param cardColor 카드 배경색. 카드 밖에 놓이는 콜론 같은 글자도 이 색으로 그린다.
 * @param contentColor 숫자 색
 */
@Composable
public fun FlipDigits(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = DefaultFlipTextStyle,
    cardColor: Color = Color(CARD),
    contentColor: Color = Color(CARD_INK),
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = textStyle.merge(TextStyle(color = contentColor, textAlign = TextAlign.Center))
    val card = remember(style, density) { flipCardSize(measurer, style, density) }
    val look = FlipLook(measurer, style, card, cardColor, reducedMotion())
    Row(
        modifier.clearAndSetSemantics { contentDescription = text },
        horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        text.forEachIndexed { index, char ->
            key(text.length - index) {
                if (char.isDigit()) FlipCard(char, look) else BasicText(char.toString(), style = style.copy(color = cardColor))
            }
        }
    }
}

/**
 * 지금 시각을 [FlipDigits]로 보여 주는 플립 시계. 매초 정각에 맞춰 갱신한다.
 *
 * @param showSeconds `true`면 초까지 보여 준다.
 * @param is24Hour `true`면 0..23시로, `false`면 1..12시로 보여 준다. 12시간제에서도 오전/오후는 표시하지 않는다. 기본값은 기기 설정을 따른다.
 */
@Composable
public fun FlipClock(
    modifier: Modifier = Modifier,
    showSeconds: Boolean = false,
    is24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
    textStyle: TextStyle = DefaultFlipTextStyle,
    cardColor: Color = Color(CARD),
    contentColor: Color = Color(CARD_INK),
) {
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(MILLIS_PER_SECOND - System.currentTimeMillis() % MILLIS_PER_SECOND)
        }
    }
    FlipDigits(clockText(now, showSeconds, is24Hour), modifier, textStyle, cardColor, contentColor)
}

/**
 * [targetMillis]까지 남은 시간(ms)을 매초 갱신해 돌려준다. 0 아래로는 내려가지 않고, 0이 되면 갱신을 멈춘다.
 * [countdownText]와 함께 쓰면 남은 시간을 시계 형식으로 보여 줄 수 있다.
 *
 * @param targetMillis 끝나는 시각. [System.currentTimeMillis]와 같은 에포크 기준 ms다.
 */
@Composable
public fun rememberCountdown(targetMillis: Long): State<Long> = rememberCountdown(targetMillis, System::currentTimeMillis)

@Composable
internal fun rememberCountdown(targetMillis: Long, now: () -> Long): State<Long> =
    produceState((targetMillis - now()).coerceAtLeast(0L), targetMillis) {
        while (true) {
            val left = (targetMillis - now()).coerceAtLeast(0L)
            value = left
            if (left == 0L) break
            // 표시하는 초가 바뀌는 순간에 맞춰 깨어난다.
            delay((left - 1) % MILLIS_PER_SECOND + 1)
        }
    }

/**
 * 남은 시간(ms)을 'MM:SS'로, 1시간 이상이면 'HH:MM:SS'로 바꾼다. 초는 올림하므로 0.2초가 남았으면 '00:01'이다.
 * 시는 24를 넘어도 그대로 센다. 음수는 0으로 본다.
 */
public fun countdownText(remainingMillis: Long): String {
    val total = (remainingMillis.coerceAtLeast(0L) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
    val hours = total / SECONDS_PER_HOUR
    val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = total % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

internal fun clockText(time: LocalTime, showSeconds: Boolean, is24Hour: Boolean): String {
    val hour = if (is24Hour) time.hour else (time.hour + HALF_DAY - 1) % HALF_DAY + 1
    val clock = String.format(Locale.ROOT, "%02d:%02d", hour, time.minute)
    return if (showSeconds) clock + String.format(Locale.ROOT, ":%02d", time.second) else clock
}

private class FlipLook(val measurer: TextMeasurer, val style: TextStyle, val card: DpSize, val cardColor: Color, val still: Boolean)

private fun flipCardSize(measurer: TextMeasurer, style: TextStyle, density: Density): DpSize {
    var width = 0
    var height = 0
    for (digit in '0'..'9') {
        val size = measurer.measure(digit.toString(), style).size
        width = max(width, size.width)
        height = max(height, size.height)
    }
    return with(density) {
        DpSize((width + height * CARD_PAD_X * 2).toDp(), (height * (1f + CARD_PAD_Y * 2)).toDp())
    }
}

/**
 * 숫자 카드 하나. 넘기는 중([turn] 0..1)에는 네 조각을 겹쳐 그린다. 바닥에는 새 숫자의 위쪽과 이전 숫자의 아래쪽이 깔리고,
 * 앞쪽 절반 동안 이전 숫자의 위쪽이 경첩을 축으로 접혀 내려가며, 뒤쪽 절반 동안 새 숫자의 아래쪽이 펼쳐져 내려온다.
 */
@Composable
private fun FlipCard(char: Char, look: FlipLook) {
    var faces by remember { mutableStateOf(char to char) }
    val turn = remember { Animatable(1f) }
    LaunchedEffect(char, look.still) {
        if (faces.second == char) return@LaunchedEffect
        faces = faces.second to char
        if (look.still) {
            turn.snapTo(1f)
        } else {
            turn.snapTo(0f)
            turn.animateTo(1f, tween(FLIP_MS, easing = FastOutSlowInEasing))
        }
    }
    val (old, new) = faces
    val camera = CAMERA_DISTANCE * LocalDensity.current.density
    Box(Modifier.size(look.card)) {
        // 접히는 조각이 다가올수록 그 아래 조각에 그늘이 진다.
        CardHalf(new, top = true, look, shade = { (1f - turn.value * 2f).coerceIn(0f, 1f) * UNDER_SHADE })
        CardHalf(old, top = false, look, shade = { ((turn.value - HALF) * 2f).coerceIn(0f, 1f) * UNDER_SHADE })
        CardHalf(
            old,
            top = true,
            look,
            shade = { (turn.value * 2f).coerceIn(0f, 1f) * FLAP_SHADE },
            modifier = Modifier.graphicsLayer {
                val progress = turn.value
                alpha = if (progress < HALF) 1f else 0f
                rotationX = -FOLD_DEGREES * (progress * 2f).coerceAtMost(1f)
                transformOrigin = TransformOrigin(HALF, 1f)
                cameraDistance = camera
            },
        )
        CardHalf(
            new,
            top = false,
            look,
            shade = { (1f - (turn.value - HALF) * 2f).coerceIn(0f, 1f) * FLAP_SHADE },
            modifier = Modifier.graphicsLayer {
                val progress = turn.value
                alpha = if (progress >= HALF) 1f else 0f
                rotationX = FOLD_DEGREES * (1f - (progress - HALF) * 2f).coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(HALF, 0f)
                cameraDistance = camera
            },
        )
    }
}

/** 카드의 위쪽이나 아래쪽 절반. [shade]는 그리기 단계에서 읽는 그늘 농도(0..1)다. */
@Composable
private fun CardHalf(char: Char, top: Boolean, look: FlipLook, shade: () -> Float, modifier: Modifier = Modifier) {
    val half = look.card.height / 2
    Box(
        // 아래쪽 조각은 레이어보다 먼저 옮겨 두어야 회전 축이 카드 가운데에 놓인다.
        (if (top) Modifier else Modifier.offset(y = half))
            .then(modifier)
            .fillMaxWidth()
            .height(half)
            .drawWithCache {
                val layout = look.measurer.measure(char.toString(), look.style)
                val radius = CornerRadius(CARD_RADIUS.toPx())
                val shape = Path().apply {
                    addRoundRect(
                        if (top) {
                            RoundRect(0f, 0f, size.width, size.height, topLeftCornerRadius = radius, topRightCornerRadius = radius)
                        } else {
                            RoundRect(0f, 0f, size.width, size.height, bottomLeftCornerRadius = radius, bottomRightCornerRadius = radius)
                        },
                    )
                }
                val textTop = (size.height * 2 - layout.size.height) / 2 - if (top) 0f else size.height
                val textLeft = (size.width - layout.size.width) / 2
                val hinge = HINGE.toPx()
                onDrawBehind {
                    drawPath(shape, look.cardColor)
                    clipRect { drawText(layout, topLeft = Offset(textLeft, textTop)) }
                    val dark = shade()
                    if (dark > 0f) drawPath(shape, Color.Black, alpha = dark)
                    // 위아래 조각 사이의 틈을 그려 두 장이 겹친 카드처럼 보이게 한다.
                    val y = if (top) size.height - hinge / 2 else hinge / 2
                    drawLine(Color.Black.copy(alpha = HINGE_ALPHA), Offset(0f, y), Offset(size.width, y), hinge)
                }
            },
    )
}

private val DefaultFlipTextStyle = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)

private val CARD_GAP = 4.dp
private val CARD_RADIUS = 8.dp
private val HINGE = 1.5.dp
private const val HINGE_ALPHA = 0.45f
private const val CARD_PAD_X = 0.14f
private const val CARD_PAD_Y = 0.08f
private const val CAMERA_DISTANCE = 16f
private const val FOLD_DEGREES = 90f
private const val FLIP_MS = 560
private const val HALF = 0.5f
private const val UNDER_SHADE = 0.35f
private const val FLAP_SHADE = 0.5f
private const val CARD = 0xFF202226
private const val CARD_INK = 0xFFF4F4F6
private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
private const val HALF_DAY = 12
