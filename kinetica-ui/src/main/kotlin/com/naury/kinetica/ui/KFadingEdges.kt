package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 가로로 스크롤되는 줄의 양 끝을 흐리게 해, 화면 밖에 항목이 더 있다는 것을 알린다. 더 갈 곳이 없는 쪽은 흐리지 않고,
 * 흐림은 짧게 나타나고 사라진다. 배경과 상관없이 내용만 투명해지므로 글래스나 사진 위에서도 쓸 수 있다.
 *
 * 수정자 순서상 `horizontalScroll`보다 앞(바깥)에 붙여야 보이는 영역 기준으로 그린다. RTL에서는 시작과 끝이 바뀐다.
 *
 * ```kotlin
 * val scroll = rememberScrollState()
 * Row(Modifier.horizontalFadingEdges(scroll).horizontalScroll(scroll)) { … }
 * ```
 *
 * @param width 흐림 폭. 줄 폭의 1/3을 넘지 않는다.
 */
@Composable
public fun Modifier.horizontalFadingEdges(state: ScrollState, width: Dp = DEFAULT_FADE): Modifier {
    val start by remember(state) { derivedStateOf { state.value > 0 } }
    val end by remember(state) { derivedStateOf { state.value < state.maxValue } }
    return fadingEdges(start, end, width, vertical = false)
}

/** `LazyRow`용 [horizontalFadingEdges] */
@Composable
public fun Modifier.horizontalFadingEdges(state: LazyListState, width: Dp = DEFAULT_FADE): Modifier {
    val start by remember(state) { derivedStateOf { state.canScrollBackward } }
    val end by remember(state) { derivedStateOf { state.canScrollForward } }
    return fadingEdges(start, end, width, vertical = false)
}

/** 세로로 스크롤되는 영역의 위아래 끝을 흐리게 한다. `verticalScroll`보다 앞(바깥)에 붙인다. */
@Composable
public fun Modifier.verticalFadingEdges(state: ScrollState, height: Dp = DEFAULT_FADE): Modifier {
    val top by remember(state) { derivedStateOf { state.value > 0 } }
    val bottom by remember(state) { derivedStateOf { state.value < state.maxValue } }
    return fadingEdges(top, bottom, height, vertical = true)
}

/** `LazyColumn`용 [verticalFadingEdges] */
@Composable
public fun Modifier.verticalFadingEdges(state: LazyListState, height: Dp = DEFAULT_FADE): Modifier {
    val top by remember(state) { derivedStateOf { state.canScrollBackward } }
    val bottom by remember(state) { derivedStateOf { state.canScrollForward } }
    return fadingEdges(top, bottom, height, vertical = true)
}

@Composable
private fun Modifier.fadingEdges(start: Boolean, end: Boolean, width: Dp, vertical: Boolean): Modifier {
    val still = isReducedMotion()
    val startAmount by animateFloatAsState(if (start) 1f else 0f, tween(if (still) 0 else FADE_MS), label = "fade-start")
    val endAmount by animateFloatAsState(if (end) 1f else 0f, tween(if (still) 0 else FADE_MS), label = "fade-end")
    return this
        // 배경은 그대로 두고 내용만 투명해지도록 따로 그린 층에서 DstIn으로 가린다.
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            // 가로 줄에서 RTL이면 시작이 오른쪽이다.
            val flip = !vertical && layoutDirection == LayoutDirection.Rtl
            drawFades(if (flip) endAmount else startAmount, if (flip) startAmount else endAmount, width.toPx(), vertical)
        }
}

/** 앞쪽과 뒤쪽 끝을 [lead], [trail] 정도로 지운다. 흐림 폭은 길이의 1/3을 넘지 않는다. */
private fun DrawScope.drawFades(lead: Float, trail: Float, width: Float, vertical: Boolean) {
    val length = if (vertical) size.height else size.width
    val fade = width.coerceAtMost(length / MAX_FADE_PARTS)
    val band = if (vertical) Size(size.width, fade) else Size(fade, size.height)
    if (lead > 0f) {
        val clear = Color.Black.copy(alpha = 1f - lead)
        val brush = if (vertical) {
            Brush.verticalGradient(0f to clear, 1f to Color.Black, startY = 0f, endY = fade)
        } else {
            Brush.horizontalGradient(0f to clear, 1f to Color.Black, startX = 0f, endX = fade)
        }
        drawRect(brush, size = band, blendMode = BlendMode.DstIn)
    }
    if (trail > 0f) {
        val clear = Color.Black.copy(alpha = 1f - trail)
        val brush = if (vertical) {
            Brush.verticalGradient(0f to Color.Black, 1f to clear, startY = length - fade, endY = length)
        } else {
            Brush.horizontalGradient(0f to Color.Black, 1f to clear, startX = length - fade, endX = length)
        }
        drawRect(
            brush,
            topLeft = if (vertical) Offset(0f, length - fade) else Offset(length - fade, 0f),
            size = band,
            blendMode = BlendMode.DstIn,
        )
    }
}

private val DEFAULT_FADE = 32.dp
private const val FADE_MS = 150
private const val MAX_FADE_PARTS = 3f
