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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.floor
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
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier.clipToBounds().semantics(mergeDescendants = true) {
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
 * 위치는 그리기 단계에서만 읽고, 보이는 숫자가 바뀔 때만 다시 구성한다.
 */
@Composable
private fun RollingDigit(digit: Int, up: Boolean, still: Boolean, style: TextStyle) {
    val wheel = remember { DigitWheel(digit) }
    LaunchedEffect(digit, still) {
        val goal = wheel.goalFor(digit, up)
        if (still) {
            wheel.position.snapTo(goal)
        } else {
            // 굴러가던 중이면 Animatable이 지금 위치와 속도에서 이어 받는다.
            wheel.position.animateTo(goal, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
        }
    }
    val base by remember { derivedStateOf { floor(wheel.position.value).toInt() } }
    val moving by remember { derivedStateOf { wheel.position.value != base.toFloat() } }
    Box {
        DigitFace(base, wheel, style, offset = 0)
        if (moving) DigitFace(base + 1, wheel, style, offset = 1)
    }
}

/** 숫자 띠 위 위치와 마지막으로 향한 정수 위치 */
internal class DigitWheel(digit: Int) {
    val position = Animatable(digit.toFloat())
    private var goal = digit

    /** [digit]으로 가는 정수 위치. 올라가면 띠를 따라 앞으로, 내려가면 뒤로 가장 가까운 칸이다. */
    fun goalFor(digit: Int, up: Boolean): Float {
        val now = Math.floorMod(goal, DIGITS)
        goal += if (up) Math.floorMod(digit - now, DIGITS) else -Math.floorMod(now - digit, DIGITS)
        return goal.toFloat()
    }
}

/** 띠의 [slot]번째 칸 숫자. [offset]이 1이면 아래 칸이라 다음 숫자가 올라오는 자리에 놓인다. */
@Composable
private fun DigitFace(slot: Int, wheel: DigitWheel, style: TextStyle, offset: Int) {
    ThemedText(
        Math.floorMod(slot, DIGITS).toString(),
        Modifier
            .then(if (offset == 0) Modifier else Modifier.clearAndSetSemantics {})
            .graphicsLayer {
                val shift = offset - (wheel.position.value - floor(wheel.position.value))
                translationY = shift * size.height
                alpha = (1f - abs(shift)).coerceIn(0f, 1f)
            },
        style = style,
    )
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
