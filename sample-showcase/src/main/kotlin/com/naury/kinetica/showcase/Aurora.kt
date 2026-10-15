// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/** 천천히 떠다니는 색 덩어리 바탕. 유리가 비춰 보일 색을 준다. */
@Composable
internal fun Aurora(modifier: Modifier, dark: Boolean) {
    val time by rememberInfiniteTransition(label = "aurora").animateFloat(
        0f,
        (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(AURORA_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val colors = if (dark) DARK else LIGHT
    Canvas(modifier) {
        drawRect(if (dark) Color(0xFF0B0C14) else Color(0xFFF1F0FF))
        colors.forEachIndexed { i, color ->
            val phase = time + i * 1.7f
            val center = Offset(
                size.width * (0.5f + 0.38f * cos(phase * (1f + i * 0.13f))),
                size.height * (0.5f + 0.36f * sin(phase * (0.8f + i * 0.11f))),
            )
            val radius = size.minDimension * (0.55f + 0.1f * sin(phase))
            drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius), radius, center)
        }
    }
}

private const val AURORA_MS = 24_000
private val LIGHT = listOf(Color(0xFFFF7EB3), Color(0xFF7AA2FF), Color(0xFF5EF0C8), Color(0xFFFFC56E), Color(0xFFB98CFF))
private val DARK = listOf(Color(0xFFB0247A), Color(0xFF2A4BD8), Color(0xFF0E9F87), Color(0xFFD87A12), Color(0xFF6A2BD8))
