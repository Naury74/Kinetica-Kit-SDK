package com.naury.kinetica.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

/**
 * 자릿수마다 숫자가 위아래로 굴러가며 바뀌는 텍스트로, 주행 거리계와 비슷하다. 값이 커지면 새 숫자가 아래에서 올라오고,
 * 작아지면 위에서 내려온다. 바뀐 자릿수만 움직이고 쉼표나 다른 문자는 그대로 둔다. TalkBack은 전체 텍스트를 한 번에 읽는다.
 *
 * ```kotlin
 * RollingText(NumberFormat.getInstance().format(sales) + "원", style = TextStyle(fontSize = 32.sp))
 * ```
 */
@Composable
public fun RollingText(text: String, modifier: Modifier = Modifier, style: TextStyle = TextStyle.Default) {
    var previous by remember { mutableStateOf(text) }
    val up = compareNumbers(text, previous) >= 0
    LaunchedEffect(text) { previous = text }
    Row(
        modifier.clipToBounds().semantics(mergeDescendants = true) {
            contentDescription = text
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 자릿수가 늘어도 끝자리끼리 맞춰 비교하도록 오른쪽부터 센다.
        text.forEachIndexed { index, char ->
            val fromEnd = text.length - index
            key(fromEnd) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val spring =
                            spring<androidx.compose.ui.unit.IntOffset>(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            )
                        if (up) {
                            (slideInVertically(spring) { it } + fadeIn()) togetherWith (slideOutVertically(spring) { -it } + fadeOut())
                        } else {
                            (slideInVertically(spring) { -it } + fadeIn()) togetherWith (slideOutVertically(spring) { it } + fadeOut())
                        } using SizeTransform(clip = false)
                    },
                    label = "rolling-$fromEnd",
                ) { shown -> BasicText(shown.toString(), style = style) }
            }
        }
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
    BasicText(text.take(shown) + if (cursor) caret else "", modifier.semantics { contentDescription = text }, style = style)
}

private const val BLINK_MS = 530L
