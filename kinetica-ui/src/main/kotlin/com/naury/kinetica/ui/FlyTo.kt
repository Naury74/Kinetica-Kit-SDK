// 파일 이름은 대표 API인 flyTo 수정자을(를) 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 장바구니로 날아가기 효과의 상태. 출발점([flyToSource])과 목표([flyToTarget])의 위치를 기억하고, [launch]로 날린 항목을 [KFlyToHost]가 그린다.
 * [rememberKFlyToState]로 만든다.
 */
@Stable
public class KFlyToState internal constructor() {
    internal val sources = HashMap<Any, Rect>()
    internal var target: Rect? = null
    internal var host: Offset = Offset.Zero
    internal val flights = mutableStateListOf<Flight>()
    internal val bump = Animatable(0f)
    private var nextId = 0L

    /**
     * [key] 출발점에서 목표로 [content]를 포물선을 그리며 날린다. 도착하면 목표가 한 번 통통 튀고 [onArrive]가 불린다.
     * 출발점이나 목표가 아직 배치되지 않았으면 날리지 않고 바로 [onArrive]를 부른다. 여러 번 불러 여러 개를 함께 날릴 수 있다.
     */
    public fun launch(key: Any, onArrive: () -> Unit = {}, content: @Composable () -> Unit) {
        val from = sources[key]
        val to = target
        if (from == null || to == null) {
            onArrive()
            return
        }
        flights += Flight(nextId++, from, to, content, onArrive)
    }

    internal class Flight(val id: Long, val from: Rect, val to: Rect, val content: @Composable () -> Unit, val onArrive: () -> Unit) {
        val progress = Animatable(0f)
    }
}

/** [KFlyToState]를 만든다. */
@Composable
public fun rememberKFlyToState(): KFlyToState = remember { KFlyToState() }

/**
 * [KFlyToState.launch]로 날린 항목을 [content] 위에 그린다. 출발점과 목표가 모두 이 안에 있어야 한다. 보통 화면 루트를 감싼다.
 * 날아가는 항목은 TalkBack에서 숨는다. '애니메이션 삭제' 설정에서는 날지 않고 바로 도착한다.
 *
 * ```kotlin
 * val fly = rememberKFlyToState()
 * KFlyToHost(fly) {
 *     KIconButton(CartIcon, "장바구니", ::openCart, Modifier.flyToTarget(fly))
 *     ProductImage(Modifier.flyToSource(fly, product.id))
 *     KButton("담기", { fly.launch(product.id, onArrive = { count++ }) { ProductImage() } })
 * }
 * ```
 */
@Composable
public fun KFlyToHost(state: KFlyToState, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val still = reducedMotion()
    val scope = rememberCoroutineScope()
    Box(modifier.onGloballyPositioned { state.host = it.boundsInRoot().topLeft }) {
        content()
        state.flights.toList().forEach { flight ->
            key(flight.id) { FlightLayer(state, flight, still, scope) }
        }
    }
}

@Composable
private fun FlightLayer(state: KFlyToState, flight: KFlyToState.Flight, still: Boolean, scope: CoroutineScope) {
    val density = LocalDensity.current
    val lift = with(density) { ARC_LIFT.toPx() }
    LaunchedEffect(flight) {
        if (!still) flight.progress.animateTo(1f, tween(FLIGHT_MS, easing = FLIGHT_EASING))
        flight.onArrive()
        // 이 항목은 곧 컴포지션에서 빠지므로 목표가 튀는 애니메이션은 호스트 범위에서 돌린다.
        if (!still) {
            scope.launch {
                state.bump.snapTo(0f)
                state.bump.animateTo(1f, tween(BUMP_UP_MS))
                state.bump.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
        }
        state.flights.remove(flight)
    }
    val from = flight.from
    val to = flight.to
    val width = with(density) { from.width.toDp() }
    val height = with(density) { from.height.toDp() }
    Box(
        Modifier
            .size(width, height)
            .clearAndSetSemantics { }
            .graphicsLayer {
                val t = flight.progress.value
                // 시작점과 끝점 위쪽에 둔 조절점으로 2차 베지어를 그려 위로 솟았다가 떨어지게 한다.
                val start = from.center - state.host
                val end = to.center - state.host
                val control = Offset((start.x + end.x) / 2, minOf(start.y, end.y) - lift)
                val u = 1f - t
                val x = u * u * start.x + 2 * u * t * control.x + t * t * end.x
                val y = u * u * start.y + 2 * u * t * control.y + t * t * end.y
                transformOrigin = TransformOrigin.Center
                translationX = x - from.width / 2
                translationY = y - from.height / 2
                val endScale = (minOf(to.width, to.height) / maxOf(from.width, from.height, 1f) * END_SHRINK).coerceAtMost(1f)
                val scale = 1f + (endScale - 1f) * t
                scaleX = scale
                scaleY = scale
                rotationZ = t * SPIN
                alpha = if (t > FADE_FROM) 1f - (t - FADE_FROM) / (1f - FADE_FROM) else 1f
            },
    ) { flight.content() }
}

/** 날아갈 항목의 출발점. [key]는 [KFlyToState.launch]에 넘기는 값과 같아야 한다. */
@Composable
public fun Modifier.flyToSource(state: KFlyToState, key: Any): Modifier {
    DisposableEffect(state, key) { onDispose { state.sources.remove(key) } }
    return onGloballyPositioned { state.sources[key] = it.boundsInRoot() }
}

/** 항목이 날아가 닿을 목표. 도착하면 이 요소가 살짝 커졌다가 통통 튀며 돌아온다. */
public fun Modifier.flyToTarget(state: KFlyToState): Modifier = this
    .onGloballyPositioned { state.target = it.boundsInRoot() }
    .graphicsLayer {
        val scale = 1f + state.bump.value * BUMP
        scaleX = scale
        scaleY = scale
    }

private val ARC_LIFT = 120.dp
private val FLIGHT_EASING = CubicBezierEasing(0.35f, 0f, 0.25f, 1f)
private const val FLIGHT_MS = 620
private const val BUMP_UP_MS = 90
private const val BUMP = 0.22f
private const val END_SHRINK = 0.8f
private const val SPIN = 20f
private const val FADE_FROM = 0.85f
