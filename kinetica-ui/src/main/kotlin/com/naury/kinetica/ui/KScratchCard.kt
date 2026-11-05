package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.floor
import kotlin.math.hypot

/**
 * 손가락으로 긁어 아래 [content]를 드러내는 복권 카드. 긁은 자리만 덮개가 지워지고, 긁은 비율이 [threshold]를 넘으면 남은 덮개가
 * 옅어지며 사라지고 [onRevealed]를 한 번 부른다. 덮개는 [coverColor]나 [coverBrush]로 칠하고, 그 위에 [cover] 슬롯(안내 글자나 무늬)을 얹는다.
 *
 * 긁은 자국은 경로 하나에 이어 그리고 덮개 레이어에서 지우는 방식이라 획이 길어져도 비트맵을 만들지 않는다. 긁은 비율은 덮개를 칸으로
 * 나눠 새로 긁힌 칸만 더해 센다. 카드 위에서 누른 손가락은 바로 긁기로 받으므로 부모 스크롤로 넘어가지 않는다.
 * TalkBack은 덮개를 [label] 이름의 버튼 하나로 읽고 두 번 탭하면 바로 다 열린다. 열리기 전에는 아래 내용을 읽지 않는다.
 * '애니메이션 삭제' 설정에서는 남은 덮개가 바로 사라진다.
 *
 * ```kotlin
 * KScratchCard(Modifier.size(280.dp, 160.dp), onRevealed = { haptics.success() }) { Prize("커피 쿠폰") }
 * ```
 *
 * @param threshold 다 여는 긁은 비율(0..1). 0이면 처음 닿을 때 바로 열린다. 범위 밖이면 [IllegalArgumentException]을 던진다.
 * @param brushSize 긁는 붓 지름. 0 이하이면 [IllegalArgumentException]을 던진다.
 * @param coverColor 덮개 색. [coverBrush]가 있으면 쓰지 않는다.
 * @param coverBrush 덮개를 칠할 브러시. 은박처럼 보이려면 대각선 그라디언트를 넘긴다.
 * @param label TalkBack이 읽을 덮개 이름이자 여는 동작 이름. null이면 "긁어서 열기"다.
 * @param enabled `false`면 긁을 수 없다. TalkBack 동작과 [KScratchCardState.reveal]은 그대로 된다.
 * @param cover 덮개 위에 얹을 내용. 긁으면 함께 지워진다.
 */
@Composable
public fun KScratchCard(
    modifier: Modifier = Modifier,
    state: KScratchCardState = rememberKScratchCardState(),
    threshold: Float = 0.5f,
    brushSize: Dp = 40.dp,
    coverColor: Color = DEFAULT_COVER,
    coverBrush: Brush? = null,
    shape: Shape = shapeFor { card },
    onRevealed: (() -> Unit)? = null,
    label: String? = null,
    enabled: Boolean = true,
    cover: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    require(threshold in 0f..1f) { "threshold는 0..1 사이여야 합니다." }
    require(brushSize.value > 0f) { "brushSize는 0보다 커야 합니다." }
    val fade = rememberRevealFade(state, onRevealed)
    val gone by remember(fade) { derivedStateOf { fade.value >= 1f } }
    val name = label ?: stringResource(R.string.kinetica_ui_scratch_reveal)
    // 열리기 전에는 카드 전체가 '열기' 버튼 하나다. 호스트가 넘긴 수정자와 같은 노드에 두어야 호스트의 testTag와 semantics가 이 동작에 닿는다.
    val revealAction = if (state.isRevealed) {
        Modifier
    } else {
        Modifier.clearAndSetSemantics {
            contentDescription = name
            role = Role.Button
            onClick(name) {
                state.reveal()
                true
            }
        }
    }
    Box(modifier.then(revealAction).clip(shape)) {
        Box { content() }
        if (!gone) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        // 긁은 자국을 지우기(Clear)로 그리려면 덮개가 자기 레이어에 따로 그려져야 아래 내용까지 지워지지 않는다.
                        compositingStrategy = CompositingStrategy.Offscreen
                        alpha = 1f - fade.value
                    }
                    .then(if (enabled) Modifier.scratchInput(state, brushSize, threshold) else Modifier)
                    .drawWithCache {
                        val stroke = Stroke(brushSize.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        onDrawWithContent {
                            val strokes = state.path
                            if (coverBrush != null) drawRect(coverBrush) else drawRect(coverColor)
                            drawContent()
                            drawPath(strokes, Color.Black, style = stroke, blendMode = BlendMode.Clear)
                        }
                    },
            ) { cover?.invoke(this) }
        }
    }
}

/** 다 열릴 때 남은 덮개를 옅게 지우는 값(0..1). 이번 화면에서 처음 열릴 때만 [onRevealed]를 부른다. */
@Composable
private fun rememberRevealFade(state: KScratchCardState, onRevealed: (() -> Unit)?): Animatable<Float, *> {
    val fade = remember(state) { Animatable(if (state.isRevealed) 1f else 0f) }
    val callback = rememberUpdatedState(onRevealed)
    val still = reducedMotion()
    LaunchedEffect(state, fade, still) {
        snapshotFlow { state.isRevealed }.collect { revealed ->
            if (!revealed) {
                fade.snapTo(0f)
                return@collect
            }
            if (!state.notified) {
                state.notified = true
                callback.value?.invoke()
            }
            if (still) fade.snapTo(1f) else fade.animateTo(1f, tween(FADE_MS))
        }
    }
    return fade
}

private fun Modifier.scratchInput(state: KScratchCardState, brushSize: Dp, threshold: Float): Modifier =
    pointerInput(state, threshold, brushSize) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (state.isRevealed) return@awaitEachGesture
            val radius = brushSize.toPx() / 2
            down.consume()
            state.scratch(null, down.position, radius, size.toSize(), threshold)
            var last = down.position
            var change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
            while (change != null && change.pressed) {
                if (change.position != last) {
                    state.scratch(last, change.position, radius, size.toSize(), threshold)
                    last = change.position
                }
                change.consume()
                change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
            }
        }
    }

private val DEFAULT_COVER = Color(0xFFB8BDC6)
private const val FADE_MS = 360
