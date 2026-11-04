// 파일 이름은 대표 컴포저블인 KMarquee를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** [KMarquee]가 흐르는 쪽 */
public enum class KMarqueeDirection {
    /** 시작 쪽으로 흐른다. 왼쪽에서 오른쪽으로 읽는 화면에서는 왼쪽으로, RTL 화면에서는 오른쪽으로 간다. */
    Start,

    /** 끝 쪽으로 흐른다. [Start]의 반대다. */
    End,
}

/**
 * 아무 내용이든 끝없이 흘려 보내는 띠. 협력사 로고 줄, 후기 카드 줄, 태그 줄처럼 장식으로 흐르는 내용에 쓴다.
 * 내용 끝에 [gap]을 두고 처음부터 다시 이어 붙여 그리므로 띠가 끊기지 않는다. 내용이 띠보다 짧아도 그 너비를 채울 만큼 반복한다.
 *
 * 내용은 한 번만 구성하고, 반복해 보이는 부분은 같은 그리기 결과를 옆에 다시 그린다. 그래서 TalkBack은 내용을 한 번만 읽고,
 * 흐르는 동안 다시 구성하거나 다시 재지 않는다. 화면 밖으로 스크롤되거나 '애니메이션 삭제' 설정이 켜져 있으면 멈춘다.
 *
 * ```kotlin
 * KMarquee(edgeFade = 32.dp) {
 *     Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { brands.forEach { BrandLogo(it) } }
 * }
 * ```
 *
 * 반복해 그린 부분은 누를 수 없다. 누를 수 있는 내용을 넣을 때는 [pauseOnTouch]를 켜고, 띠보다 짧은 내용에 쓴다.
 *
 * @param direction 흐르는 쪽
 * @param speed 1초에 흐르는 거리. 음수면 [IllegalArgumentException]을 던진다. 0이면 멈춰 있다.
 * @param gap 내용 끝과 다음 반복 사이 간격
 * @param edgeFade 양 끝을 흐리게 지우는 폭. 0이면 흐리지 않는다.
 * @param pauseOnTouch 누르거나 끄는 동안 멈출지
 * @param paused true면 멈춘다. 재생 단추처럼 호스트가 직접 멈출 때 쓴다.
 */
@Composable
public fun KMarquee(
    modifier: Modifier = Modifier,
    direction: KMarqueeDirection = KMarqueeDirection.Start,
    speed: Dp = 40.dp,
    gap: Dp = 24.dp,
    edgeFade: Dp = 0.dp,
    pauseOnTouch: Boolean = false,
    paused: Boolean = false,
    content: @Composable () -> Unit,
) {
    require(speed >= 0.dp) { "speed는 0 이상이어야 합니다." }
    val still = reducedMotion()
    var touching by remember { mutableStateOf(false) }
    val clock = rememberLoopClock(running = !still && !paused && !touching && speed > 0.dp)
    val copies = remember { mutableIntStateOf(1) }
    Layout(
        content = { Box(Modifier.repeatCopies(copies, gap)) { content() } },
        modifier = modifier
            .then(if (edgeFade > 0.dp) Modifier.fadingEdges(start = true, end = true, width = edgeFade, vertical = false) else Modifier)
            .clipToBounds()
            .loopVisibility(clock)
            .then(if (pauseOnTouch) Modifier.pauseWhilePressed { touching = it } else Modifier),
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
        val cycle = placeable.width + gap.roundToPx()
        copies.intValue = if (cycle > 0) ceil((width + cycle).toFloat() / cycle).toInt().coerceAtLeast(1) else 1
        val leftward = (direction == KMarqueeDirection.Start) == (layoutDirection == LayoutDirection.Ltr)
        layout(constraints.constrainWidth(width), constraints.constrainHeight(placeable.height)) {
            // 흐름은 레이어의 이동으로만 바꿔, 프레임마다 다시 배치하거나 다시 그리지 않고 레이어 위치만 옮긴다.
            placeable.placeWithLayer(0, 0) {
                if (cycle <= 0) return@placeWithLayer
                val travel = clock.seconds * speed.toPx() % cycle
                translationX = if (leftward) -travel else travel - cycle
            }
        }
    }
}

/** 내용을 오른쪽으로 [copies]번 이어 그린다. 내용 폭에 [gap]을 더한 거리마다 같은 그리기 결과를 다시 그린다. */
private fun Modifier.repeatCopies(copies: androidx.compose.runtime.IntState, gap: Dp): Modifier = drawWithContent {
    val step = size.width + gap.toPx()
    drawContent()
    for (k in 1..copies.intValue) translate(left = step * k) { this@drawWithContent.drawContent() }
}

/** 손가락이나 마우스 단추가 하나라도 눌려 있는 동안 [onPressed]에 true를 알린다. 이벤트를 쓰지 않으므로 안쪽 버튼과 바깥 스크롤은 그대로 동작한다. */
private fun Modifier.pauseWhilePressed(onPressed: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onPressed(true)
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
        } finally {
            onPressed(false)
        }
    }
}
