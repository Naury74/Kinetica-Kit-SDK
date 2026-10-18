package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 좋아요 버튼. 누르면 하트가 작아졌다 튀어 오르며 빨갛게 채워지고, 주위로 고리와 작은 입자가 터진다.
 * 좋아요를 취소할 때는 효과 없이 채움만 빠진다. [count]가 있으면 하트 옆에 숫자가 굴러가며 바뀐다([RollingText]).
 * TalkBack은 "좋아요"와 현재 상태, 숫자를 함께 읽는다.
 *
 * ```kotlin
 * LikeButton(liked, onLikedChange = { liked = it; likes += if (it) 1 else -1 }, count = likes)
 * ```
 *
 * @param onLikedChange 눌렀을 때 바뀔 상태를 받는다. 이 값을 [liked]에 반영해야 모양이 바뀐다.
 * @param count 하트 옆에 보일 숫자. `null`이면 하트만 그린다. 현재 로캘 형식으로 자릿수를 구분한다.
 * @param color 채운 하트 색
 * @param iconSize 하트 크기. 버튼의 터치 영역은 48dp 이상이다.
 */
@Composable
public fun LikeButton(
    liked: Boolean,
    onLikedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    color: Color = Color(LIKE_RED),
    iconSize: Dp = 26.dp,
) {
    val motion = remember { LikeMotion(liked) }
    LaunchedEffect(motion, liked) { motion.animateTo(liked, this) }
    val haptic = kHapticFeedback()
    val content = LocalKineticaContentColor.current
    val label = stringResource(R.string.kinetica_ui_like)
    val state = stringResource(if (liked) R.string.kinetica_ui_liked else R.string.kinetica_ui_not_liked)
    Row(
        modifier
            .defaultMinSize(minWidth = TOUCH_MIN, minHeight = TOUCH_MIN)
            .toggleable(
                value = liked,
                interactionSource = remember { MutableInteractionSource() },
                // 하트가 튀어 오르는 애니메이션이 눌림 표시를 대신한다.
                indication = null,
                role = Role.Button,
            ) { next ->
                haptic.performHapticFeedback(if (next) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onLikedChange(next)
            }
            .semantics {
                contentDescription = label
                stateDescription = state
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(iconSize).drawWithCache { heartDraw(motion, content, color) })
        if (count != null) {
            val number = remember { NumberFormat.getInstance() }
            Spacer(Modifier.size(COUNT_GAP))
            // 숫자는 RTL에서도 왼쪽부터 쓰므로 자릿수 배치를 LTR로 고정한다.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                RollingText(
                    number.format(count),
                    style = TextStyle(color = content, fontSize = COUNT_SIZE, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

private fun CacheDrawScope.heartDraw(motion: LikeMotion, content: Color, color: Color): DrawResult {
    val heart = heartPath(size.minDimension)
    val outline = Stroke(width = size.minDimension * OUTLINE_RATIO)
    return onDrawBehind {
        drawBurst(motion.burst.value, color)
        val fill = motion.fill.value.coerceIn(0f, 1f)
        scale(motion.scale.value) {
            if (fill < 1f) drawPath(heart, lerp(content, color, fill), style = outline)
            if (fill > 0f) drawPath(heart, color, alpha = fill)
        }
    }
}

/** 터지는 고리와 입자를 그린다. [t]는 0에서 시작해 1이 되면 사라진다. */
private fun DrawScope.drawBurst(t: Float, color: Color) {
    if (t <= 0f || t >= 1f) return
    val unit = size.minDimension
    val ring = (t / RING_END).coerceIn(0f, 1f)
    if (ring < 1f) {
        val width = unit * RING_WIDTH * (1f - ring)
        drawCircle(color.copy(alpha = 1f - ring * ring), unit * (RING_START + RING_GROW * ring), style = Stroke(width))
    }
    val spark = ((t - SPARK_START) / (1f - SPARK_START)).coerceIn(0f, 1f)
    if (spark <= 0f) return
    val eased = 1f - (1f - spark) * (1f - spark)
    for (i in 0 until SPARK_GROUPS) {
        val base = 2 * PI * i / SPARK_GROUPS - PI / 2
        drawSpark(base, unit * (SPARK_FROM + SPARK_TRAVEL * eased), unit * SPARK_DOT * (1f - spark), SPARK_COLORS[i % SPARK_COLORS.size])
        drawSpark(
            base + SPARK_SPLIT,
            unit * (SPARK_FROM + SPARK_TRAVEL * eased * SPARK_INNER),
            unit * SPARK_DOT_SMALL * (1f - spark),
            color,
        )
    }
}

private fun DrawScope.drawSpark(angle: Double, distance: Float, radius: Float, color: Color) {
    if (radius <= 0f) return
    drawCircle(color, radius, Offset(center.x + (cos(angle) * distance).toFloat(), center.y + (sin(angle) * distance).toFloat()))
}

/** 하트의 채움, 크기, 터짐 진행. 처음 그릴 때는 효과 없이 지금 상태로 보인다. */
private class LikeMotion(initial: Boolean) {
    val fill = Animatable(if (initial) 1f else 0f)
    val scale = Animatable(1f)
    val burst = Animatable(1f)
    private var last = initial

    fun animateTo(liked: Boolean, scope: CoroutineScope) {
        if (liked == last) return
        last = liked
        scope.launch {
            if (!liked) {
                burst.snapTo(1f)
                fill.animateTo(0f, tween(UNLIKE_MS))
                return@launch
            }
            launch { fill.animateTo(1f, tween(FILL_MS)) }
            launch {
                burst.snapTo(0f)
                burst.animateTo(1f, tween(BURST_MS, easing = LinearOutSlowInEasing))
            }
            scale.snapTo(SCALE_FROM)
            scale.animateTo(1f, spring(dampingRatio = SCALE_DAMPING, stiffness = SCALE_STIFFNESS))
        }
    }
}

/** 단위 정사각형에 맞춘 하트를 [sizePx] 크기로 만든다. */
private fun heartPath(sizePx: Float): Path = Path().apply {
    moveTo(HEART_START_X * sizePx, HEART_START_Y * sizePx)
    HEART.forEach { c -> cubicTo(c.x1 * sizePx, c.y1 * sizePx, c.x2 * sizePx, c.y2 * sizePx, c.x3 * sizePx, c.y3 * sizePx) }
    close()
}

private class Curve(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x3: Float, val y3: Float)

// 아래 꼭짓점에서 시작해 왼쪽 볼록, 가운데 홈, 오른쪽 볼록을 지나 다시 아래 꼭짓점으로 돌아오는 3차 곡선 여섯 개(크기 기준 비율).
private const val HEART_START_X = 0.5f
private const val HEART_START_Y = 0.9f
private val HEART = listOf(
    Curve(0.18f, 0.68f, 0.04f, 0.5f, 0.06f, 0.32f),
    Curve(0.08f, 0.16f, 0.2f, 0.08f, 0.32f, 0.08f),
    Curve(0.41f, 0.08f, 0.47f, 0.13f, 0.5f, 0.21f),
    Curve(0.53f, 0.13f, 0.59f, 0.08f, 0.68f, 0.08f),
    Curve(0.8f, 0.08f, 0.92f, 0.16f, 0.94f, 0.32f),
    Curve(0.96f, 0.5f, 0.82f, 0.68f, 0.5f, 0.9f),
)

private const val LIKE_RED = 0xFFFF2D55
private val TOUCH_MIN = 48.dp
private val COUNT_GAP = 6.dp
private val COUNT_SIZE = 15.sp
private const val OUTLINE_RATIO = 0.085f
private const val UNLIKE_MS = 160
private const val FILL_MS = 120
private const val BURST_MS = 650
private const val SCALE_FROM = 0.55f
private const val SCALE_DAMPING = 0.38f
private const val SCALE_STIFFNESS = 420f
private const val RING_END = 0.55f
private const val RING_START = 0.2f
private const val RING_GROW = 0.6f
private const val RING_WIDTH = 0.22f
private const val SPARK_START = 0.18f
private const val SPARK_GROUPS = 7
private const val SPARK_FROM = 0.5f
private const val SPARK_TRAVEL = 0.62f
private const val SPARK_INNER = 0.78f
private const val SPARK_SPLIT = 0.28
private const val SPARK_DOT = 0.09f
private const val SPARK_DOT_SMALL = 0.06f
private val SPARK_COLORS = listOf(Color(0xFFFF9F0A), Color(0xFFFF375F), Color(0xFFBF5AF2), Color(0xFF30D158), Color(0xFF64D2FF))
