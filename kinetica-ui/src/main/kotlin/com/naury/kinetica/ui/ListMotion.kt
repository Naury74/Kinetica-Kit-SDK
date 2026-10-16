package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * [staggerIn]이 이미 나타난 항목을 기억하는 상태. 지연 목록에서 항목이 화면 밖으로 나갔다 들어와도 다시 등장하지 않게 한다.
 * [rememberKStaggerState]로 만든다.
 */
@Stable
public class KStaggerState internal constructor() {
    internal val shown = HashSet<Int>()
    internal var startNanos = -1L

    /** 기억을 지운다. 다음에 그려지는 항목부터 다시 차례로 등장한다. 목록 내용을 통째로 바꿀 때 부른다. */
    public fun reset() {
        shown.clear()
        startNanos = -1L
    }
}

/** [KStaggerState]를 만든다. */
@Composable
public fun rememberKStaggerState(): KStaggerState = remember { KStaggerState() }

/**
 * 항목이 [index] 순서대로 조금씩 늦게 아래에서 떠오르며 나타난다. 화면을 처음 열 때 목록이 차례로 채워지는 효과다.
 * 순번마다 [delayMs]씩 늦게 시작하되 [state]가 처음 쓰인 시각을 기준으로 하므로, 나중에 스크롤해 들어온 항목은 기다리지 않고 바로 나타난다.
 * 지연 목록에서는 [state]를 넘겨야 화면 밖으로 나갔다 돌아온 항목이 다시 등장하지 않는다. 값은 그리기 단계에서 읽으므로 등장하는 동안 다시 구성하지 않는다.
 * '애니메이션 삭제' 설정에서는 바로 보인다.
 *
 * ```kotlin
 * val stagger = rememberKStaggerState()
 * LazyColumn { itemsIndexed(mails) { i, mail -> MailRow(mail, Modifier.staggerIn(i, stagger)) } }
 * ```
 *
 * @param delayMs 순번 하나당 늦추는 시간(ms). 음수면 [IllegalArgumentException]을 던진다.
 * @param maxDelayMs 가장 오래 기다리는 시간(ms). 긴 목록에서 아래 항목이 너무 늦게 나타나지 않게 한다.
 * @param distance 떠오르기 시작하는 아래쪽 거리
 */
@Composable
public fun Modifier.staggerIn(
    index: Int,
    state: KStaggerState? = null,
    delayMs: Int = 45,
    maxDelayMs: Int = 450,
    distance: Dp = 18.dp,
): Modifier {
    require(delayMs >= 0) { "delayMs는 0 이상이어야 합니다." }
    val still = reducedMotion()
    val done = still || state?.shown?.contains(index) == true
    val progress = remember(index) { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(index, state) {
        if (progress.value >= 1f) {
            state?.shown?.add(index)
            return@LaunchedEffect
        }
        val now = System.nanoTime()
        val start = state?.let { if (it.startNanos < 0) now.also { t -> it.startNanos = t } else it.startNanos } ?: now
        val waited = (now - start) / NANOS_PER_MS
        val wait = (minOf(index.toLong() * delayMs, maxDelayMs.toLong()) - waited).coerceAtLeast(0L)
        if (wait > 0) delay(wait)
        state?.shown?.add(index)
        progress.animateTo(1f, spring(dampingRatio = STAGGER_DAMPING, stiffness = STAGGER_STIFFNESS))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * distance.toPx()
    }
}

/** [scrollTransform]이 화면 가장자리 항목에 주는 효과 */
public enum class KScrollEffect {
    /** 위아래 끝으로 갈수록 작아지고 흐려진다. Wear OS 목록 느낌이다. */
    ScaleFade,

    /** 위아래 끝으로 갈수록 화면 안쪽으로 눕는다. 휠 선택기 느낌이다. */
    Wheel,

    /** 위쪽 끝으로 사라지는 항목만 작아지며 흐려진다. 아래는 그대로다. 위젯 카드가 위로 쌓이는 느낌이다. */
    TopStack,
}

/**
 * 지연 목록 항목이 화면 위아래 가장자리 [edge] 안으로 들어가면 [effect]에 따라 크기, 투명도, 기울기가 바뀐다.
 * 목록 배치 정보를 그리기 단계에서 읽으므로 스크롤하는 동안 항목을 다시 구성하지 않는다. '애니메이션 삭제' 설정과 관계없이 스크롤 위치에 붙은
 * 효과라 그대로 그린다.
 *
 * ```kotlin
 * LazyColumn(state = list) { items(100) { i -> Row(Modifier.scrollTransform(list, i)) { … } } }
 * ```
 *
 * @param index 이 항목의 목록 순번. `items`의 순번과 같아야 한다.
 * @param edge 효과가 걸리는 가장자리 폭
 * @param minScale 가장자리 끝에서의 크기 배율(0..1). 범위를 벗어나면 0..1로 맞춘다.
 */
public fun Modifier.scrollTransform(
    state: LazyListState,
    index: Int,
    effect: KScrollEffect = KScrollEffect.ScaleFade,
    edge: Dp = 120.dp,
    minScale: Float = 0.82f,
): Modifier = graphicsLayer {
    val info = state.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@graphicsLayer
    val zone = edge.toPx().coerceAtLeast(1f)
    val top = item.offset - info.viewportStartOffset
    val bottom = info.viewportEndOffset - (item.offset + item.size)
    val topCut = ((zone - top) / zone).coerceIn(0f, 1f)
    val bottomCut = if (effect == KScrollEffect.TopStack) 0f else ((zone - bottom) / zone).coerceIn(0f, 1f)
    val cut = maxOf(topCut, bottomCut)
    if (cut <= 0f) return@graphicsLayer
    val floor = minScale.coerceIn(0f, 1f)
    val atTop = topCut >= bottomCut
    transformOrigin = TransformOrigin(HALF, if (atTop) 1f else 0f)
    when (effect) {
        KScrollEffect.ScaleFade, KScrollEffect.TopStack -> {
            val scale = 1f - (1f - floor) * cut
            scaleX = scale
            scaleY = scale
            alpha = 1f - cut * FADE
        }

        KScrollEffect.Wheel -> {
            rotationX = (if (atTop) 1f else -1f) * cut * WHEEL_DEGREES
            cameraDistance = CAMERA * density
            alpha = 1f - cut * FADE * HALF
            val scale = 1f - (1f - floor) * cut * HALF
            scaleX = scale
            scaleY = scale
        }
    }
}

private const val NANOS_PER_MS = 1_000_000L
private const val STAGGER_DAMPING = 0.8f
private const val STAGGER_STIFFNESS = 260f
private const val HALF = 0.5f
private const val FADE = 0.75f
private const val WHEEL_DEGREES = 55f
private const val CAMERA = 12f
