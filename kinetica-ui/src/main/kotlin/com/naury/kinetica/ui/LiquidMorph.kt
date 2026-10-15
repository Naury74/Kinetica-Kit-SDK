package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * 요소 뒤에 [color]로 채운 둥근 모양을 그리고, 누르면 그 외곽이 액체처럼 출렁인다.
 * 누르는 동안 누른 자리가 오목하게 들어가며 옆으로 퍼지고, 떼면 반대로 튀었다가 물결치며 원래 모양으로 돌아온다.
 * 외곽선은 꼭짓점 40개를 스프링과 사인파로 움직여 만들며, 경로와 배열은 재사용한다.
 * 터치 이벤트를 소비하지 않으므로 `clickable`과 함께 쓴다. 모양은 요소 경계보다 조금 넘쳐 그려지므로 부모가 자르지 않게 여유를 둔다.
 * '애니메이션 삭제' 접근성 설정이 켜져 있으면 출렁이지 않고 누르는 동안만 살짝 눌린다.
 *
 * ```kotlin
 * Box(Modifier.liquidMorph(Color(0xFF7B61FF)).clickable { buy() }.padding(24.dp, 14.dp)) { Text("구매") }
 * ```
 *
 * @param color 채우는 색. 위쪽은 조금 밝게 칠해 볼록해 보이게 한다.
 * @param cornerRadius 쉴 때 모서리 반경. 높이의 절반보다 크면 캡슐 모양이 된다.
 * @param wobble 출렁이는 세기 배율(0..2). 0이면 출렁이지 않는다. 범위를 벗어나면 [IllegalArgumentException]을 던진다.
 * @param enabled false면 눌러도 모양이 바뀌지 않는다.
 */
@Composable
public fun Modifier.liquidMorph(color: Color, cornerRadius: Dp = 999.dp, wobble: Float = 1f, enabled: Boolean = true): Modifier {
    require(wobble in 0f..MAX_WOBBLE) { "wobble은 0..2여야 합니다." }
    val still = reducedMotion()
    val squash = remember { Animatable(0f) }
    val energy = remember { Animatable(0f) }
    val touch = remember { FloatArray(2) }
    val scope = rememberCoroutineScope()
    val amount = if (still) 0f else wobble
    return this
        .pointerInput(still, enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touch[0] = down.position.x
                touch[1] = down.position.y
                scope.launch { squash.animateTo(1f, spring(PRESS_DAMPING, PRESS_STIFFNESS)) }
                if (!still) scope.launch { kick(energy, PRESS_KICK) }
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
                scope.launch { squash.animateTo(0f, if (still) spring() else spring(RELEASE_DAMPING, RELEASE_STIFFNESS)) }
                if (!still) scope.launch { kick(energy, 1f) }
            }
        }
        .drawWithCache {
            val outline = BlobOutline(size, minOf(cornerRadius.toPx(), size.minDimension / 2f))
            val fill = Brush.verticalGradient(listOf(lerp(color, Color.White, TOP_LIGHT), color, lerp(color, Color.Black, BOTTOM_DARK)))
            val rim = Brush.verticalGradient(listOf(Color.White.copy(alpha = RIM_ALPHA), Color.Transparent), 0f, size.height * RIM_REACH)
            val stroke = Stroke(RIM_DP.dp.toPx())
            val maxWave = WAVE_DP.dp.toPx()
            onDrawWithContent {
                val wave = maxWave * amount * energy.value * energy.value
                // 출렁임 시간은 감쇠 진행률에서 얻는다. 따로 시계를 돌리지 않아도 된다.
                val time = (1f - energy.value) * ENERGY_SECONDS
                val path = outline.build(BlobPose(squash.value, Offset(touch[0], touch[1]), wave, time))
                drawPath(path, fill)
                drawPath(path, rim, style = stroke)
                drawContent()
            }
        }
}

/** 누르는 순간 [to]만큼 출렁일 힘을 채우고 일정한 속도로 잦아들게 한다. */
private suspend fun kick(energy: Animatable<Float, *>, to: Float) {
    energy.snapTo(maxOf(energy.value, to))
    energy.animateTo(0f, tween((energy.value * ENERGY_SECONDS * MS_PER_SECOND).toInt(), easing = LinearEasing))
}

/** 한 프레임의 모양을 정하는 값. [squash]는 눌림 정도로, 0보다 작으면 위아래로 늘어난다. */
internal class BlobPose(val squash: Float, val touch: Offset, val wave: Float, val time: Float)

/**
 * 둥근 사각형 둘레에 같은 간격으로 놓은 꼭짓점과 바깥 방향. 매 프레임 꼭짓점을 옮긴 뒤 이웃 꼭짓점의 중점을 잇는 2차 곡선으로 매끄럽게 닫는다.
 */
internal class BlobOutline(private val size: Size, corner: Float) {
    private val px = FloatArray(VERTICES)
    private val py = FloatArray(VERTICES)
    private val nx = FloatArray(VERTICES)
    private val ny = FloatArray(VERTICES)
    private val ox = FloatArray(VERTICES)
    private val oy = FloatArray(VERTICES)
    private val path = Path()

    init {
        sampleRoundRect(size, corner.coerceAtLeast(0f))
    }

    fun build(pose: BlobPose): Path {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val reach = size.minDimension.coerceAtLeast(1f)
        for (i in 0 until VERTICES) {
            val u = i * TWO_PI / VERTICES
            val ripple = pose.wave * (sin(u * LOBES_A + pose.time * SPEED_A) * MIX_A + sin(u * LOBES_B - pose.time * SPEED_B) * MIX_B)
            // 누른 자리 가까운 꼭짓점일수록 안으로 들어간다.
            val dx = (px[i] - pose.touch.x) / reach
            val dy = (py[i] - pose.touch.y) / reach
            val dent = pose.squash * DENT * reach * exp(-(dx * dx + dy * dy) * DENT_FOCUS)
            val stretchX = 1f + pose.squash * WIDEN
            val stretchY = 1f - pose.squash * FLATTEN
            ox[i] = cx + (px[i] - cx) * stretchX + nx[i] * (ripple - dent)
            oy[i] = cy + (py[i] - cy) * stretchY + ny[i] * (ripple - dent)
        }
        path.rewind()
        path.moveTo((ox[VERTICES - 1] + ox[0]) / 2f, (oy[VERTICES - 1] + oy[0]) / 2f)
        for (i in 0 until VERTICES) {
            val next = (i + 1) % VERTICES
            path.quadraticTo(ox[i], oy[i], (ox[i] + ox[next]) / 2f, (oy[i] + oy[next]) / 2f)
        }
        path.close()
        return path
    }

    /** 둘레를 따라 같은 길이마다 꼭짓점을 놓는다. 위쪽 가운데에서 시작해 시계 방향으로 돈다. */
    private fun sampleRoundRect(size: Size, corner: Float) {
        val r = minOf(corner, size.minDimension / 2f)
        val straightX = size.width - r * 2f
        val straightY = size.height - r * 2f
        val arc = (PI.toFloat() / 2f) * r
        // 위 변, 오른쪽 위 모서리, 오른쪽 변 순서로 시계 방향을 따라 도는 구간 길이
        val lengths = floatArrayOf(straightX, arc, straightY, arc, straightX, arc, straightY, arc)
        val total = lengths.sum()
        for (i in 0 until VERTICES) {
            var s = (i.toFloat() / VERTICES * total + straightX / 2f) % total
            var side = TOP
            while (side < TOP_LEFT && s > lengths[side]) {
                s -= lengths[side]
                side++
            }
            place(i, side, s, r, size)
        }
    }

    private fun place(i: Int, side: Int, s: Float, r: Float, size: Size) {
        val w = size.width
        val h = size.height
        when (side) {
            TOP -> set(i, r + s, 0f, 0f, -1f)

            RIGHT -> set(i, w, r + s, 1f, 0f)

            BOTTOM -> set(i, w - r - s, h, 0f, 1f)

            LEFT -> set(i, 0f, h - r - s, -1f, 0f)

            else -> {
                // 모서리는 위쪽(-90도)부터 시계 방향으로 90도씩 이어지는 원호다.
                val start = (side / 2) * (PI.toFloat() / 2f) - PI.toFloat() / 2f
                val a = start + if (r > 0f) s / r else 0f
                val cx = if (side == TOP_RIGHT || side == BOTTOM_RIGHT) w - r else r
                val cy = if (side == TOP_RIGHT || side == TOP_LEFT) r else h - r
                set(i, cx + cos(a) * r, cy + sin(a) * r, cos(a), sin(a))
            }
        }
    }

    private fun set(i: Int, x: Float, y: Float, normalX: Float, normalY: Float) {
        px[i] = x
        py[i] = y
        nx[i] = normalX
        ny[i] = normalY
    }
}

/**
 * 누르면 액체처럼 출렁이는 캡슐 버튼. [liquidMorph]에 버튼 역할과 글자를 더한 것이다.
 *
 * ```kotlin
 * GlassBlobButton("시작하기", onClick = { start() })
 * ```
 *
 * @param enabled false면 누를 수 없고 흐리게 그린다.
 * @param color 버튼 색
 * @param contentColor 글자 색
 */
@Composable
public fun GlassBlobButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = GlassTheme.colors.accent,
    contentColor: Color = GlassTheme.colors.onAccent,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .liquidMorph(color, enabled = enabled)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .defaultMinSize(minWidth = BUTTON_MIN_WIDTH, minHeight = BUTTON_HEIGHT)
            .padding(horizontal = BUTTON_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        ThemedText(
            text,
            style = TextStyle(color = contentColor, fontSize = BUTTON_FONT, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val VERTICES = 40
private const val TOP = 0
private const val TOP_RIGHT = 1
private const val RIGHT = 2
private const val BOTTOM_RIGHT = 3
private const val BOTTOM = 4
private const val LEFT = 6
private const val TOP_LEFT = 7
private const val TWO_PI = 6.2831855f
private const val MAX_WOBBLE = 2f
private const val PRESS_DAMPING = 0.55f
private const val PRESS_STIFFNESS = 700f
private const val RELEASE_DAMPING = 0.28f
private const val RELEASE_STIFFNESS = 320f
private const val PRESS_KICK = 0.55f
private const val ENERGY_SECONDS = 1.1f
private const val MS_PER_SECOND = 1_000f
private const val WAVE_DP = 5f
private const val LOBES_A = 3f
private const val LOBES_B = 5f
private const val SPEED_A = 13f
private const val SPEED_B = 9f
private const val MIX_A = 0.65f
private const val MIX_B = 0.35f
private const val DENT = 0.16f
private const val DENT_FOCUS = 5f
private const val WIDEN = 0.07f
private const val FLATTEN = 0.1f
private const val TOP_LIGHT = 0.22f
private const val BOTTOM_DARK = 0.12f
private const val RIM_ALPHA = 0.55f
private const val RIM_REACH = 0.6f
private const val RIM_DP = 1f
private const val DISABLED_ALPHA = 0.45f
private val BUTTON_MIN_WIDTH = 120.dp
private val BUTTON_HEIGHT = 52.dp
private val BUTTON_PADDING = 28.dp
private val BUTTON_FONT = 17.sp
