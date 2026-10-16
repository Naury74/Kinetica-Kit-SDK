package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * 자릿수마다 숫자가 위아래로 굴러가며 바뀌는 텍스트로, 주행 거리계와 비슷하다. 값이 커지면 새 숫자가 아래에서 올라오고,
 * 작아지면 위에서 내려온다. 3에서 6처럼 여러 칸 차이 나면 사이 숫자를 지나 굴러간다. 굴러가는 도중에 값이 또 바뀌면
 * 지금 위치와 속도에서 새 숫자로 이어 굴린다. 바뀐 자릿수만 움직이고 쉼표나 다른 문자는 그대로 둔다.
 * TalkBack은 전체 텍스트를 한 번에 읽는다. '애니메이션 삭제' 설정에서는 바로 바뀐다.
 *
 * ```kotlin
 * RollingText(NumberFormat.getInstance().format(sales) + "원", style = TextStyle(fontSize = 32.sp))
 * ```
 */
@Composable
public fun RollingText(text: String, modifier: Modifier = Modifier, style: TextStyle = TextStyle.Default) {
    val last = remember { RollingLast(text) }
    val up = remember(text) { compareNumbers(text, last.text) >= 0 }
    SideEffect { last.text = text }
    val still = reducedMotion()
    // 글자를 한 칸씩 Row에 늘어놓으므로 RTL에서는 순서가 뒤집힌다("1,235"가 "532,1"). 숫자는 어느 언어에서나 왼쪽부터 쓰므로 LTR로 고정한다.
    // 바깥을 자르지 않는다. 자리가 모자라 폭이 줄면 Text처럼 글자가 넘쳐 보여야 하는데, 자르면 숫자가 통째로 사라진다.
    // 굴러가는 숫자의 위아래는 자리마다 drawDigits가 자른다.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier.semantics(mergeDescendants = true) {
                contentDescription = text
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 자릿수가 늘어도 끝자리끼리 맞춰 비교하도록 오른쪽부터 센다.
            text.forEachIndexed { index, char ->
                val digit = char.digitToIntOrNull()
                key(text.length - index, digit != null) {
                    if (digit != null) RollingDigit(digit, up, still, style) else ThemedText(char.toString(), style = style)
                }
            }
        }
    }
}

/** 직전에 받은 [RollingText] 값. 올라가는지 내려가는지 비교하는 데만 쓰므로 상태로 두지 않는다. */
private class RollingLast(var text: String)

/**
 * 숫자 한 자리. 위치는 끝없이 이어지는 숫자 띠 위의 실수 좌표이고, 정수 부분이 지금 보이는 숫자, 소수 부분이 다음 숫자까지 굴러간 정도다.
 *
 * 보이는 두 숫자와 그 자리는 한 번의 그리기에서 같은 위치 값으로 함께 정한다. 예전에는 보이는 숫자를 구성 단계에서, 이동량과 투명도를
 * 숫자마다 둔 레이어에서 따로 읽었는데, 값이 빠르게 이어 바뀔 때 둘이 어긋난 채 멈추면 숫자가 투명한 채로 남을 수 있었다.
 * 이제 위치는 그리기 단계에서만 읽으므로 굴러가는 동안 다시 구성하지 않고, 어떤 위치에서도 두 숫자의 불투명도 합이 1이라 빈칸이 생기지 않는다.
 */
@Composable
private fun RollingDigit(digit: Int, up: Boolean, still: Boolean, style: TextStyle) {
    val wheel = remember { DigitWheel(digit) }
    LaunchedEffect(digit, still) {
        val goal = wheel.goalFor(digit, up)
        if (!still) {
            // 굴러가던 중이면 Animatable이 지금 위치와 속도에서 이어 받는다.
            wheel.position.animateTo(goal, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
        }
        // 다 굴렀거나 '애니메이션 삭제'면 띠를 0..9 칸으로 되돌려, 값이 오래 바뀌어도 위치가 커지지 않고 정확히 칸에 멈추게 한다.
        wheel.settle()
    }
    val faces = rememberDigitFaces(themed(style))
    Box(
        Modifier
            .layout { measurable, constraints ->
                // 자리 너비는 목표 숫자 기준이라 굴러가는 동안 다시 재지 않는다.
                val face = faces[digit]
                val placeable = measurable.measure(Constraints.fixed(face.size.width, faces.height))
                // 받은 폭보다 크게 보고하면 Compose가 줄인 폭의 가운데에 맞춰 놓아 숫자가 앞 글자 쪽으로 밀린다.
                // Text처럼 받은 폭만 차지하고 숫자는 그 자리 왼쪽부터 그려 오른쪽으로 넘치게 한다.
                layout(
                    constraints.constrainWidth(placeable.width),
                    constraints.constrainHeight(placeable.height),
                    mapOf(FirstBaseline to face.firstBaseline.roundToInt(), LastBaseline to face.lastBaseline.roundToInt()),
                ) { placeable.place(0, 0) }
            }
            .drawBehind { drawDigits(wheel.position.value, faces) },
    )
}

/** 숫자 0..9를 [style]로 미리 잰 결과. 높이는 가장 큰 숫자 기준이다. */
private class DigitFaces(private val layouts: List<TextLayoutResult>) {
    val height: Int = layouts.maxOf { it.size.height }

    operator fun get(digit: Int): TextLayoutResult = layouts[Math.floorMod(digit, DIGITS)]
}

@Composable
private fun rememberDigitFaces(style: TextStyle): DigitFaces {
    val measurer = rememberTextMeasurer(cacheSize = DIGITS)
    return remember(measurer, style) { DigitFaces(List(DIGITS) { measurer.measure(it.toString(), style) }) }
}

/** 띠 위 [position]에 걸친 두 숫자를 그린다. 위 숫자는 위로 빠지며 옅어지고 아래 숫자는 올라오며 짙어진다. */
private fun DrawScope.drawDigits(position: Float, faces: DigitFaces) {
    if (!position.isFinite()) return
    val base = floor(position)
    val rolled = position - base
    clipRect {
        drawFace(faces[base.toInt()], shift = -rolled, alpha = 1f - rolled)
        if (rolled > 0f) drawFace(faces[base.toInt() + 1], shift = 1f - rolled, alpha = rolled)
    }
}

private fun DrawScope.drawFace(face: TextLayoutResult, shift: Float, alpha: Float) {
    val left = (size.width - face.size.width) / 2f
    val top = (size.height - face.size.height) / 2f + shift * size.height
    drawText(face, topLeft = Offset(left, top), alpha = alpha.coerceIn(0f, 1f))
}

/** 숫자 띠 위 위치와 마지막으로 향한 정수 위치 */
internal class DigitWheel(digit: Int) {
    val position = Animatable(Math.floorMod(digit, DIGITS).toFloat())
    private var goal = Math.floorMod(digit, DIGITS)

    /** [digit]으로 가는 정수 위치. 올라가면 띠를 따라 앞으로, 내려가면 뒤로 가장 가까운 칸이다. */
    fun goalFor(digit: Int, up: Boolean): Float {
        val now = Math.floorMod(goal, DIGITS)
        goal += if (up) Math.floorMod(digit - now, DIGITS) else -Math.floorMod(now - digit, DIGITS)
        return goal.toFloat()
    }

    /** 목표 칸에 정확히 멈추고 위치와 목표를 0..9 칸으로 되돌린다. 보이는 숫자는 그대로다. */
    suspend fun settle() {
        goal = Math.floorMod(goal, DIGITS)
        position.snapTo(goal.toFloat())
    }
}

/** 두 문자열에 든 숫자를 비교한다. 숫자를 읽을 수 없으면 0을 돌려준다. */
private fun compareNumbers(a: String, b: String): Int {
    val x = a.filter { it.isDigit() || it == '-' || it == '.' }.toBigDecimalOrNull() ?: return 0
    val y = b.filter { it.isDigit() || it == '-' || it == '.' }.toBigDecimalOrNull() ?: return 0
    return x.compareTo(y)
}

/**
 * 한 글자씩 타이핑되는 텍스트와 깜박이는 커서. [text]가 바뀌면 처음부터 다시 타이핑한다.
 * '애니메이션 삭제' 접근성 설정이 켜져 있으면 처음부터 전체 텍스트를 보여 준다. TalkBack은 전체 텍스트를 읽는다.
 *
 * @param charMs 한 글자를 표시하는 간격(ms)
 * @param cursor 깜박이는 커서를 표시할지 여부
 */
@Composable
public fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    charMs: Long = 45L,
    cursor: Boolean = true,
) {
    val still = reducedMotion()
    var shown by remember(text) { mutableIntStateOf(if (still) text.length else 0) }
    var blink by remember { mutableStateOf(true) }
    LaunchedEffect(text, still) {
        while (shown < text.length) {
            delay(charMs)
            shown++
        }
    }
    LaunchedEffect(cursor, still) {
        while (cursor && !still) {
            delay(BLINK_MS)
            blink = !blink
        }
    }
    val caret = if (cursor && (blink || shown < text.length)) "▍" else " "
    ThemedText(text.take(shown) + if (cursor) caret else "", modifier.semantics { contentDescription = text }, style = style)
}

private const val BLINK_MS = 530L
private const val DIGITS = 10
