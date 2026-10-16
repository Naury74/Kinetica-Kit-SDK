// 파일 이름은 대표 API인 floatUp을(를) 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/** [floatUp]이 띄울 글자를 받는 상태. [rememberKFloatUpState]로 만든다. */
@Stable
public class KFloatUpState internal constructor() {
    internal val items = mutableStateListOf<Item>()
    private var nextId = 0L

    /** [text]를 요소 위로 띄운다. 빠르게 여러 번 부르면 조금씩 옆으로 비켜 겹쳐 떠오른다. */
    public fun emit(text: String) {
        items += Item(nextId++, text, Random.nextFloat() * 2f - 1f)
    }

    internal class Item(val id: Long, val text: String, val drift: Float) {
        val progress = Animatable(0f)
        var layout: TextLayoutResult? = null
    }
}

/** [KFloatUpState]를 만든다. */
@Composable
public fun rememberKFloatUpState(): KFloatUpState = remember { KFloatUpState() }

/**
 * 좋아요나 포인트를 얻었을 때 요소 위로 '+1' 같은 글자가 톡 튀어나와 떠오르며 사라진다. [KFloatUpState.emit]으로 띄운다.
 * 글자는 요소 경계 밖(위쪽)에 그리므로 부모가 잘라 내지 않는 자리에 둔다. 값은 그리기 단계에서 읽는다.
 * 장식이라 TalkBack에는 나타나지 않는다. 숫자가 바뀐 것은 호출한 쪽 글자로 알린다. '애니메이션 삭제' 설정에서는 띄우지 않는다.
 *
 * ```kotlin
 * val plus = rememberKFloatUpState()
 * LikeButton(liked, { liked = it; if (it) plus.emit("+1") }, Modifier.floatUp(plus))
 * ```
 *
 * @param rise 떠오르는 높이
 */
@Composable
public fun Modifier.floatUp(
    state: KFloatUpState,
    color: Color = KineticaTheme.colors.accent,
    fontSize: TextUnit = 16.sp,
    rise: Dp = 40.dp,
): Modifier {
    val still = reducedMotion()
    val measurer = rememberTextMeasurer()
    val style = themed(TextStyle(color = color, fontSize = fontSize, fontWeight = FontWeight.Bold))
    state.items.toList().forEach { item ->
        key(item.id) {
            LaunchedEffect(item) {
                if (!still) item.progress.animateTo(1f, tween(FLOAT_MS, easing = FastOutSlowInEasing))
                state.items.remove(item)
            }
        }
    }
    return drawWithContent {
        drawContent()
        val lift = rise.toPx()
        state.items.forEach { item ->
            val layout = item.layout ?: measurer.measure(item.text, style).also { item.layout = it }
            val p = item.progress.value
            val pop = if (p <
                POP_END
            ) {
                POP_START + (POP_PEAK - POP_START) * (p / POP_END)
            } else {
                POP_PEAK -
                    (POP_PEAK - 1f) * ((p - POP_END) / (1f - POP_END))
            }
            val x = size.width / 2 - layout.size.width / 2 + item.drift * layout.size.width * DRIFT
            val y = -layout.size.height - lift * p
            val pivot = Offset(x + layout.size.width / 2, y + layout.size.height / 2)
            scale(pop, pivot) { drawText(layout, topLeft = Offset(x, y), alpha = (1f - p * p).coerceIn(0f, 1f)) }
        }
    }
}

private const val FLOAT_MS = 900
private const val POP_START = 0.5f
private const val POP_PEAK = 1.15f
private const val POP_END = 0.18f
private const val DRIFT = 0.6f
