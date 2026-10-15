package com.naury.kinetica.ui

import android.graphics.CornerPathEffect
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 재생 삼각형과 일시정지 두 막대 사이를 모핑하는 아이콘. 삼각형을 좌우 두 조각으로 나눠 각 꼭짓점을 막대의 꼭짓점으로 옮긴다.
 * 미디어 컨트롤이라 RTL에서도 뒤집지 않는다. 기본 크기는 24dp이고 `modifier`의 크기가 있으면 그것을 따른다.
 * 아이콘만 그리므로 TalkBack 이름과 클릭은 감싸는 버튼에 둔다.
 *
 * ```kotlin
 * GlassButton(..., onClick = { playing = !playing }) { PlayPauseIcon(playing) }
 * ```
 *
 * @param playing true면 일시정지 막대(재생 중), false면 재생 삼각형을 그린다.
 */
@Composable
public fun PlayPauseIcon(playing: Boolean, modifier: Modifier = Modifier, color: Color = LocalGlassContentColor.current) {
    val progress = morphProgress(playing)
    val paint = remember { MorphPaint() }
    Canvas(modifier.size(ICON_SIZE)) {
        val p = progress.value
        paint.prepare(color, size.minDimension * CORNER_RATIO)
        val left = paint.left
        val right = paint.right
        left.reset()
        right.reset()
        for (i in 0 until QUAD) {
            val lx = lerpUnit(PLAY_LEFT[i * 2], PAUSE_LEFT[i * 2], p) * size.width
            val ly = lerpUnit(PLAY_LEFT[i * 2 + 1], PAUSE_LEFT[i * 2 + 1], p) * size.height
            val rx = lerpUnit(PLAY_RIGHT[i * 2], PAUSE_RIGHT[i * 2], p) * size.width
            val ry = lerpUnit(PLAY_RIGHT[i * 2 + 1], PAUSE_RIGHT[i * 2 + 1], p) * size.height
            if (i == 0) left.moveTo(lx, ly) else left.lineTo(lx, ly)
            if (i == 0) right.moveTo(rx, ry) else right.lineTo(rx, ry)
        }
        left.close()
        right.close()
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawPath(left, paint.fill)
            canvas.nativeCanvas.drawPath(right, paint.fill)
        }
    }
}

/**
 * 햄버거 메뉴 세 줄과 닫기(×) 사이를 모핑하는 아이콘. 위아래 줄이 가운데로 모이며 엇갈려 돌고, 가운데 줄은 줄어들어 사라진다.
 * 기본 크기는 24dp이다. TalkBack 이름과 클릭은 감싸는 버튼에 둔다.
 *
 * @param open true면 ×, false면 세 줄을 그린다.
 */
@Composable
public fun MenuCloseIcon(open: Boolean, modifier: Modifier = Modifier, color: Color = LocalGlassContentColor.current) {
    val progress = morphProgress(open)
    Canvas(modifier.size(ICON_SIZE)) {
        val p = progress.value
        val stroke = size.minDimension * STROKE_RATIO
        val half = size.width * lerpUnit(MENU_HALF, CROSS_HALF, p)
        val spread = size.height * MENU_SPREAD * (1f - p)
        rotate(HALF_TURN * p) {
            drawBar(Offset(center.x, center.y - spread), half, ROTATE_45 * p, color, stroke)
            drawBar(Offset(center.x, center.y + spread), half, -ROTATE_45 * p, color, stroke)
            val middle = half * (1f - (p * 2f).coerceAtMost(1f))
            if (middle > 0f) drawBar(center, middle, 0f, color.copy(alpha = color.alpha * (1f - p).coerceIn(0f, 1f)), stroke)
        }
    }
}

/**
 * 더하기(+)가 45도 돌아 닫기(×)가 되는 아이콘. 플로팅 버튼이나 펼치는 메뉴에 쓴다. 기본 크기는 24dp이다.
 * TalkBack 이름과 클릭은 감싸는 버튼에 둔다.
 *
 * @param open true면 ×, false면 +를 그린다.
 */
@Composable
public fun PlusCloseIcon(open: Boolean, modifier: Modifier = Modifier, color: Color = LocalGlassContentColor.current) {
    val progress = morphProgress(open)
    Canvas(modifier.size(ICON_SIZE)) {
        val stroke = size.minDimension * STROKE_RATIO
        val half = size.width * PLUS_HALF
        rotate(ROTATE_45 * progress.value) {
            drawBar(center, half, 0f, color, stroke)
            drawBar(center, half, ROTATE_90, color, stroke)
        }
    }
}

@Composable
private fun morphProgress(target: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (target) 1f else 0f,
    animationSpec = spring(dampingRatio = MORPH_DAMPING, stiffness = Spring.StiffnessMedium),
    label = "morph-icon",
)

/** [center]를 지나고 [degrees]만큼 기운, 반 길이 [half]인 둥근 끝 막대를 그린다. */
private fun DrawScope.drawBar(center: Offset, half: Float, degrees: Float, color: Color, stroke: Float) {
    val radians = Math.toRadians(degrees.toDouble())
    val dx = (cos(radians) * half).toFloat()
    val dy = (sin(radians) * half).toFloat()
    drawLine(color, Offset(center.x - dx, center.y - dy), Offset(center.x + dx, center.y + dy), stroke, StrokeCap.Round)
}

/** 모서리를 둥글게 칠하는 Paint와 두 조각 Path. 색이나 크기가 바뀔 때만 Paint를 고친다. */
private class MorphPaint {
    val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL }
    val left = android.graphics.Path()
    val right = android.graphics.Path()
    private var argb = 0
    private var corner = -1f

    fun prepare(color: Color, cornerPx: Float) {
        val next = color.toArgb()
        if (next != argb) {
            argb = next
            fill.color = next
        }
        if (cornerPx != corner) {
            corner = cornerPx
            fill.pathEffect = CornerPathEffect(cornerPx)
        }
    }
}

private fun lerpUnit(from: Float, to: Float, t: Float): Float = from + (to - from) * t

// 꼭짓점은 아이콘 크기 기준 비율(x, y)이다. 삼각형은 무게중심이 가운데 오도록 오른쪽으로 조금 옮겼다.
private const val TRI_LEFT = 0.24f
private const val TRI_TIP = 0.86f
private const val TRI_TOP = 0.16f
private const val TRI_BOTTOM = 0.84f
private const val TRI_MID = (TRI_LEFT + TRI_TIP) / 2f
private const val TRI_SLOPE = (0.5f - TRI_TOP) / (TRI_TIP - TRI_LEFT)

// 두 조각을 이음매에서 겹쳐 둔다. 모서리를 둥글게 깎을 때 이음매 꼭짓점도 깎여 홈이 보이지 않게 하려는 것이다.
private const val SEAM = 0.1f
private const val LEFT_END = TRI_MID + SEAM
private const val LEFT_END_TOP = TRI_TOP + TRI_SLOPE * (LEFT_END - TRI_LEFT)
private const val RIGHT_START = TRI_MID - SEAM
private const val RIGHT_START_TOP = TRI_TOP + TRI_SLOPE * (RIGHT_START - TRI_LEFT)
private val PLAY_LEFT = floatArrayOf(TRI_LEFT, TRI_TOP, LEFT_END, LEFT_END_TOP, LEFT_END, 1f - LEFT_END_TOP, TRI_LEFT, TRI_BOTTOM)
private val PLAY_RIGHT = floatArrayOf(RIGHT_START, RIGHT_START_TOP, TRI_TIP, 0.5f, TRI_TIP, 0.5f, RIGHT_START, 1f - RIGHT_START_TOP)
private val PAUSE_LEFT = floatArrayOf(0.22f, 0.18f, 0.42f, 0.18f, 0.42f, 0.82f, 0.22f, 0.82f)
private val PAUSE_RIGHT = floatArrayOf(0.58f, 0.18f, 0.78f, 0.18f, 0.78f, 0.82f, 0.58f, 0.82f)
private const val QUAD = 4
private val ICON_SIZE = 24.dp
private const val CORNER_RATIO = 0.06f
private const val STROKE_RATIO = 0.09f
private const val MENU_HALF = 0.32f
private const val CROSS_HALF = 0.3f
private const val MENU_SPREAD = 0.22f
private const val PLUS_HALF = 0.3f
private const val ROTATE_45 = 45f
private const val ROTATE_90 = 90f
private const val HALF_TURN = 180f
private const val MORPH_DAMPING = 0.7f
