// 슬라이더 손잡이와 트랙 그리기를 KSlider와 KRangeSlider가 함께 쓴다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 슬라이더 손잡이와 트랙 색. [ink]는 표면 글자 색, [fill]은 불투명한 표면 색, [border]는 디자인 언어의 테두리 두께,
 * [track]은 채워지지 않은 트랙 색이다.
 */
internal class ThumbPaint(
    val accent: Color,
    val ink: Color,
    val fill: Color,
    val dark: Boolean,
    val border: Dp,
    val track: Color = Color.Transparent,
)

/** [style] 손잡이를 [center]에 그린다. [grab]은 잡은 정도(0..1)다. */
internal fun DrawScope.drawSliderThumb(style: KSliderThumb, center: Offset, grab: Float, paint: ThumbPaint) {
    when (style) {
        KSliderThumb.Pill -> {
            val w = PILL_WIDTH.toPx() * (1f + PILL_GROW * grab)
            val h = PILL_HEIGHT.toPx() * (1f + PILL_GROW * grab)
            val topLeft = Offset(center.x - w / 2, center.y - h / 2)
            val radius = CornerRadius(h / 2)
            drawRoundRect(Color.Black.copy(alpha = SHADOW_ALPHA), topLeft + Offset(0f, DROP.toPx()), Size(w, h), radius)
            // 잡은 동안에는 손잡이를 반투명하게 해 뒤의 트랙이 비쳐 보이게 한다.
            drawRoundRect(Color.White.copy(alpha = 1f - PILL_CLEAR * grab), topLeft, Size(w, h), radius)
        }

        KSliderThumb.Circle -> {
            val r = CIRCLE.toPx() / 2 * (1f + CIRCLE_GROW * grab)
            drawCircle(Color.Black.copy(alpha = SHADOW_ALPHA), r, center + Offset(0f, DROP.toPx()))
            drawCircle(if (paint.dark) Color(DARK_KNOB) else Color.White, r, center)
        }

        KSliderThumb.Bar -> {
            val w = BAR_WIDTH.toPx() * (1f - BAR_THIN * grab)
            val h = BAR_HEIGHT.toPx()
            drawRoundRect(paint.accent, Offset(center.x - w / 2, center.y - h / 2), Size(w, h), CornerRadius(w / 2))
        }

        KSliderThumb.Ring -> {
            val outer = RING_OUTER.toPx() / 2
            drawCircle(paint.ink.copy(alpha = RING_EDGE), outer + 1.dp.toPx(), center)
            drawCircle(if (paint.dark) Color(DARK_KNOB) else Color.White, outer, center)
            drawCircle(paint.accent, RING_DOT.toPx() / 2 * (1f + RING_GROW * grab), center)
        }

        KSliderThumb.Square -> {
            val s = SQUARE.toPx() * (1f + SQUARE_GROW * grab)
            val stroke = paint.border.toPx().coerceAtLeast(1f)
            val drop = SQUARE_DROP.toPx()
            val topLeft = Offset(center.x - s / 2, center.y - s / 2)
            drawRect(paint.ink, topLeft + Offset(drop, drop), Size(s, s))
            drawRect(paint.fill, topLeft, Size(s, s))
            drawRect(paint.ink, topLeft + Offset(stroke / 2, stroke / 2), Size(s - stroke, s - stroke), style = Stroke(stroke))
        }
    }
}

/**
 * [style]에 맞는 트랙. [start]..[end]는 트랙 양 끝, [split]은 채워진 구간이 끝나는 x(px)다. [rtl]이면 오른쪽부터 채운다.
 * [KSliderThumb.Bar]는 손잡이 양옆을 비우고 두꺼운 트랙을, [KSliderThumb.Square]는 각진 트랙에 테두리를 그린다.
 */
internal fun DrawScope.drawSliderTrack(style: KSliderThumb, split: Float, height: Float, paint: ThumbPaint, rtl: Boolean) {
    val track = paint.track
    val top = (size.height - height) / 2
    when (style) {
        KSliderThumb.Bar -> {
            val h = BAR_TRACK.toPx()
            val y = (size.height - h) / 2
            val gap = BAR_GAP.toPx()
            val outer = CornerRadius(h / 2)
            val lead = (if (rtl) size.width - split else split) - gap
            val rest = size.width - (if (rtl) size.width - split else split) - gap
            if (lead > 0f) drawRoundRect(paint.accent, Offset(if (rtl) size.width - lead else 0f, y), Size(lead, h), outer)
            if (rest > 0f) drawRoundRect(track, Offset(if (rtl) 0f else size.width - rest, y), Size(rest, h), outer)
        }

        KSliderThumb.Square -> {
            val stroke = paint.border.toPx().coerceAtLeast(1f)
            val h = height.coerceAtLeast(SQUARE_TRACK.toPx())
            val y = (size.height - h) / 2
            drawRect(paint.fill, Offset(0f, y), Size(size.width, h))
            val filled = if (rtl) size.width - split else split
            drawRect(paint.accent, Offset(if (rtl) split else 0f, y), Size(filled, h))
            drawRect(paint.ink, Offset(stroke / 2, y + stroke / 2), Size(size.width - stroke, h - stroke), style = Stroke(stroke))
        }

        else -> {
            val radius = CornerRadius(height / 2)
            drawRoundRect(track, Offset(0f, top), Size(size.width, height), radius)
            val filled = if (rtl) size.width - split else split
            drawRoundRect(paint.accent, Offset(if (rtl) split else 0f, top), Size(filled, height), radius)
        }
    }
}

private val PILL_WIDTH = 36.dp
private val PILL_HEIGHT = 24.dp
private const val PILL_GROW = 0.25f
private const val PILL_CLEAR = 0.35f
private val DROP = 1.dp
private const val SHADOW_ALPHA = 0.14f
private val CIRCLE = 26.dp
private const val CIRCLE_GROW = 0.15f
private const val DARK_KNOB = 0xFFF2F2F5
private val BAR_WIDTH = 4.dp
private val BAR_HEIGHT = 40.dp
private const val BAR_THIN = 0.3f
private val BAR_TRACK = 16.dp
private val BAR_GAP = 6.dp
private val RING_OUTER = 20.dp
private val RING_DOT = 10.dp
private const val RING_GROW = 0.35f
private const val RING_EDGE = 0.14f
private val SQUARE = 24.dp
private const val SQUARE_GROW = 0.1f
private val SQUARE_DROP = 3.dp
private val SQUARE_TRACK = 10.dp
