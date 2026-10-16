// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.naury.kinetica.ui.shouldPauseBackground
import kotlin.math.cos
import kotlin.math.sin

/** 색 덩어리가 천천히 떠다니는 배경. 글래스 뒤로 비칠 색을 깔아 준다. */
@Composable
internal fun Aurora(modifier: Modifier, dark: Boolean) {
    // 글래스가 이 배경을 비추므로 바뀔 때마다 블러를 모두 다시 한다. 시트나 다이얼로그가 열린 동안은 뒤가 가려지므로 멈춘다.
    val clock = remember { mutableFloatStateOf(0f) }
    val paused = shouldPauseBackground()
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        val base = clock.floatValue
        var start = -1L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (start < 0) start = now
                clock.floatValue = base + (now - start) / 1e9f
            }
        }
    }
    val colors = if (dark) DARK else LIGHT
    Canvas(modifier) {
        val time = (clock.floatValue % CYCLE_S) / CYCLE_S * (2 * Math.PI).toFloat()
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

private const val CYCLE_S = 24f
private val LIGHT = listOf(Color(0xFFFF7EB3), Color(0xFF7AA2FF), Color(0xFF5EF0C8), Color(0xFFFFC56E), Color(0xFFB98CFF))
private val DARK = listOf(Color(0xFFB0247A), Color(0xFF2A4BD8), Color(0xFF0E9F87), Color(0xFFD87A12), Color(0xFF6A2BD8))
