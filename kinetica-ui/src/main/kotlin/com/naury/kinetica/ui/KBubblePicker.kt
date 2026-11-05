package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 둥근 거품을 눌러 여러 개를 고르는 선택기. 음악 앱에서 좋아하는 장르를 고르는 화면처럼 거품들이 가운데로 모여 떠 있고,
 * 하나를 누르면 그 거품이 커지며 골라지고 둘레의 거품이 밀려난다. 다 자리를 잡으면 물리 계산을 멈추고, 화면 밖으로 스크롤되어도 멈춘다.
 * '애니메이션 삭제' 설정이 켜져 있으면 움직이지 않고 정착한 배치를 바로 보여 준다.
 *
 * 높이를 정하지 않으면 거품이 다 들어갈 만큼 정한다. TalkBack에서는 거품마다 체크박스로 읽힌다.
 *
 * ```kotlin
 * var picked by remember { mutableStateOf(setOf("재즈")) }
 * KBubblePicker(listOf("팝", "재즈", "힙합", "록", "클래식"), picked, { picked = it })
 * ```
 *
 * @param items 거품 이름. 같은 이름이 둘 있으면 [IllegalArgumentException]을 던진다.
 * @param selected 골라진 거품 이름
 * @param onSelectedChange 거품을 누르면 바뀐 선택 전체를 넘긴다.
 * @param bubbleSize 고르지 않은 거품의 지름
 * @param selectedScale 골랐을 때 커지는 배율(1..2). 범위를 벗어나면 가까운 끝 값으로 맞춘다.
 * @param colors 고른 거품의 색. 거품 순서대로 돌려 쓴다. 비어 있으면 [IllegalArgumentException]을 던진다.
 */
@Composable
public fun KBubblePicker(
    items: List<String>,
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    bubbleSize: Dp = 84.dp,
    selectedScale: Float = 1.4f,
    colors: List<Color> = if (KineticaTheme.surface.isDark) KChartPalettes.CategoricalDark else KChartPalettes.Categorical,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(items.toSet().size == items.size) { "items에 같은 이름이 있으면 안 됩니다." }
    require(colors.isNotEmpty()) { "colors는 비어 있으면 안 됩니다." }
    val scale = selectedScale.coerceIn(1f, MAX_SCALE)
    val still = reducedMotion()
    val physics = remember { BubblePhysics() }
    val frame = remember { mutableIntStateOf(0) }
    val motion = remember { BubbleMotion() }
    val latest by rememberUpdatedState(selected)
    val haptic = kHapticFeedback()
    BubbleLoop(physics, frame, motion, still)
    Layout(
        content = {
            items.forEachIndexed { i, label ->
                // 이름으로 묶어야 가운데 항목을 지울 때 뒤 거품이 앞 거품의 누름 상태를 물려받지 않는다.
                key(label) {
                    val on = label in selected
                    Bubble(label, on, colors[i % colors.size], surface, { physics.scaleOf(i, scale, frame) }) {
                        haptic.performHapticFeedback(if (on) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                        onSelectedChange(if (label in latest) latest - label else latest + label)
                    }
                }
            }
        },
        modifier = modifier.onVisibilityChanged(minFractionVisible = 0f) { motion.onScreen = it },
    ) { measurables, constraints ->
        val base = bubbleSize.toPx() / 2f
        val itemsChanged = physics.sync(items, base)
        items.forEachIndexed { i, label -> physics.target(i, if (label in selected) base * scale else base) }
        val diameter = ceil(base * scale * 2f).toInt()
        val placeables = measurables.map { it.measure(Constraints.fixed(diameter, diameter)) }
        val size = pickerSize(constraints, physics, diameter)
        // 항목이 바뀌면 거품 번호가 달라지므로 다음 물리 걸음을 기다리지 않고 크기를 새 번호로 다시 읽게 한다.
        if (motion.arrange(physics, size, selected, itemsChanged, still) || itemsChanged) frame.intValue++
        layout(size.width, size.height) {
            // 위치는 여기서만 읽으므로 거품이 움직이는 동안 다시 구성하지 않고 배치만 다시 한다.
            frame.intValue
            placeables.forEachIndexed { i, placeable ->
                placeable.place((physics.x[i] - diameter / 2f).roundToInt(), (physics.y[i] - diameter / 2f).roundToInt())
            }
        }
    }
}

/** 거품 수와 크기에 맞춘 선택기 크기. 높이가 정해져 있지 않으면 거품 넓이의 합을 폭으로 나눠 정한다. */
private fun pickerSize(constraints: Constraints, physics: BubblePhysics, diameter: Int): IntSize {
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else diameter * WIDE_BUBBLES
    if (constraints.hasBoundedHeight && constraints.maxHeight > 0 && constraints.minHeight == constraints.maxHeight) {
        return IntSize(width, constraints.maxHeight)
    }
    var area = 0f
    for (i in 0 until physics.count) area += (PI * physics.goalOf(i) * physics.goalOf(i)).toFloat()
    val wanted = maxOf(area * FILL_ROOM / width.coerceAtLeast(1), diameter * MIN_ROWS, sqrt(area))
    return IntSize(width, constraints.constrainHeight(ceil(wanted).toInt()))
}

/** 거품의 지금 크기를 고른 거품 지름에 대한 비율로 돌려준다. [frame]을 읽어 거품이 자랄 때 그리기 단계에서 갱신되게 한다. */
private fun BubblePhysics.scaleOf(index: Int, scale: Float, frame: IntState): Float {
    frame.intValue
    val max = baseRadius * scale
    return if (index < count && max > 0f) radius[index] / max else 1f / scale
}

/** 물리가 돌아야 하는지와 마지막 상자 크기를 기억한다. */
private class BubbleMotion {
    var onScreen by mutableStateOf(false)
    var awake by mutableStateOf(true)
    private var size = IntSize.Zero
    private var chosen: Set<String> = emptySet()

    /** 크기, 선택, 항목이 바뀌었으면 물리를 깨운다. '애니메이션 삭제'면 그 자리에서 정착시키고 true를 돌려준다. */
    fun arrange(physics: BubblePhysics, newSize: IntSize, selected: Set<String>, itemsChanged: Boolean, still: Boolean): Boolean {
        val changed = itemsChanged || newSize != size || selected != chosen
        size = newSize
        chosen = selected
        physics.bounds(newSize.width.toFloat(), newSize.height.toFloat())
        if (!changed) return false
        if (still) {
            physics.settle()
            return true
        }
        awake = true
        return false
    }
}

/** 깨어 있고 화면에 보이는 동안 프레임마다 물리를 돌린다. 다 멈추면 스스로 잠든다. */
@Composable
private fun BubbleLoop(physics: BubblePhysics, frame: MutableIntState, motion: BubbleMotion, still: Boolean) {
    val running = motion.awake && motion.onScreen && !still
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        var carry = 0f
        var moving = true
        while (true) {
            val now = withFrameNanos { it }
            var left = ((now - last) / NANOS_PER_SECOND).coerceAtMost(MAX_FRAME_SECONDS) + carry
            last = now
            carry = 0f
            var stepped = false
            while (left > 0f) {
                // 아주 짧은 나머지는 다음 프레임에 붙여 걸음 길이를 고르게 한다.
                if (left < MIN_VELOCITY_SECONDS) {
                    carry = left
                    break
                }
                val dt = minOf(left, STEP_SECONDS)
                if (!stepped) moving = false
                moving = physics.step(dt) || moving
                stepped = true
                left -= dt
            }
            frame.intValue++
            if (!moving) break
        }
        motion.awake = false
    }
}

@Composable
private fun Bubble(label: String, on: Boolean, tint: Color, surface: SurfaceStyle, scale: () -> Float, onToggle: () -> Unit) {
    val fill = if (on) tint else surface.contentColor.copy(alpha = IDLE_ALPHA)
    val ink = when {
        !on -> surface.contentColor
        tint.luminance() > LIGHT_FILL -> Color.Black
        else -> Color.White
    }
    Box(
        Modifier
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            }
            .drawBehind { drawCircle(fill) }
            .toggleable(on, interactionSource = null, indication = KIndication(CircleShape), role = Role.Checkbox) { onToggle() }
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        ThemedText(
            label,
            style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val MAX_SCALE = 2f
private const val IDLE_ALPHA = 0.1f
private const val LIGHT_FILL = 0.45f
private const val FILL_ROOM = 1.9f
private const val MIN_ROWS = 1.6f
private const val WIDE_BUBBLES = 4
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MAX_FRAME_SECONDS = 1f / 20f
