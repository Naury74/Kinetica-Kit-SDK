package com.naury.kinetica.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import kotlin.math.hypot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * 요소를 [center]에서 퍼지는 원으로 잘라 그린다. [progress]가 0이면 아무것도 보이지 않고 1이면 가장 먼 모서리까지 덮는다.
 * 두 람다는 그리기 단계에서 읽으므로 애니메이션 값을 넘겨도 다시 구성하지 않는다. 터치는 자르지 않는다.
 *
 * @param progress 드러난 정도(0..1). 범위를 벗어나면 0..1로 맞춘다.
 * @param center 원의 중심(요소 기준 px). null이면 가운데다.
 */
public fun Modifier.circularReveal(progress: () -> Float, center: () -> Offset? = { null }): Modifier = drawWithCache {
    val path = Path()
    onDrawWithContent {
        val p = progress().coerceIn(0f, 1f)
        if (p >= 1f) {
            drawContent()
            return@onDrawWithContent
        }
        if (p <= 0f) return@onDrawWithContent
        val c = center() ?: Offset(size.width / 2, size.height / 2)
        val far = maxOf(
            hypot(c.x, c.y),
            hypot(size.width - c.x, c.y),
            hypot(c.x, size.height - c.y),
            hypot(
                size.width - c.x,
                size.height - c.y,
            ),
        )
        val radius = far * p
        path.reset()
        path.addOval(Rect(c, radius))
        clipPath(path) { this@onDrawWithContent.drawContent() }
    }
}

/**
 * [visible]이 바뀌면 [origin]에서 원이 퍼지며 [content]가 드러나고, 거꾸로 원이 줄어들며 사라진다. 다크 모드 전환이나 검색 화면처럼
 * 누른 자리에서 새 화면이 번지는 전환에 쓴다. 사라지는 애니메이션이 끝나면 [content]를 컴포지션에서 뺀다.
 * [onBack]을 주면 보이는 동안 예측형 뒤로 가기를 받는다. 제스처를 하는 동안 원이 손가락을 따라 [origin] 쪽으로 줄어들고, 놓으면 [onBack]을 부른다.
 * 취소하면 다시 퍼진다. '애니메이션 삭제' 설정에서는 원 없이 바로 바뀐다.
 *
 * ```kotlin
 * KCircularReveal(searching, Modifier.fillMaxSize(), origin = searchButtonCenter, onBack = { searching = false }) { SearchScreen() }
 * ```
 *
 * @param origin 원이 퍼지기 시작하는 점(화면 좌표, px). [Modifier.kOrigin]으로 누른 버튼의 중심을 얻는다. null이면 가운데다.
 * @param durationMs 퍼지는 시간(ms). 0 이하면 [IllegalArgumentException]을 던진다.
 */
@Composable
public fun KCircularReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    origin: Offset? = null,
    onBack: (() -> Unit)? = null,
    durationMs: Int = 480,
    content: @Composable () -> Unit,
) {
    require(durationMs > 0) { "durationMs는 0보다 커야 합니다." }
    val still = reducedMotion()
    val reveal = remember { Animatable(if (visible) 1f else 0f) }
    val back = remember { Animatable(0f) }
    LaunchedEffect(visible, still) {
        if (visible) back.snapTo(0f)
        val target = if (visible) 1f else 0f
        if (still) reveal.snapTo(target) else reveal.animateTo(target, tween(durationMs, easing = FastOutSlowInEasing))
    }
    val latestBack = rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled = visible && onBack != null) { events ->
        try {
            events.collect { back.snapTo(it.progress) }
            latestBack.value?.invoke()
        } catch (cancelled: CancellationException) {
            // 제스처를 취소하면 이 코루틴도 취소되므로 되돌리는 애니메이션은 취소되지 않게 돌린다.
            withContext(NonCancellable) { back.animateTo(0f) }
            throw cancelled
        }
    }
    // 진행 값은 그리기에서만 읽고, 컴포지션은 다 사라졌는지만 따진다.
    val hidden by remember { derivedStateOf { reveal.value <= 0f && !reveal.isRunning } }
    if (!visible && hidden) return
    var topLeft by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .onGloballyPositioned { topLeft = it.positionOnScreen() }
            .circularReveal({ reveal.value * (1f - back.value * BACK_SHRINK) }) { origin?.let { it - topLeft } },
    ) { content() }
}

private const val BACK_SHRINK = 0.4f
