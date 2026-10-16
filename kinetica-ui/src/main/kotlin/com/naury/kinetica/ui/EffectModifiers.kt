package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.coroutines.launch

/**
 * [trigger]가 바뀔 때마다 요소를 좌우로 흔든다. 비밀번호가 틀렸을 때처럼 입력 오류를 알릴 때 쓴다.
 * 처음 컴포지션될 때는 흔들지 않는다. '애니메이션 삭제' 접근성 설정이 켜져 있으면 흔들지 않으므로, 오류는 색이나 문구로도 알려야 한다.
 *
 * ```kotlin
 * KSurface(Modifier.shake(errorCount)) { ... }   // 틀릴 때마다 errorCount를 1 올린다
 * ```
 *
 * @param distance 처음 흔들리는 폭. 점점 줄어들며 멈춘다.
 * @param durationMs 흔들림 전체 시간(ms)
 */
@Composable
public fun Modifier.shake(trigger: Any?, distance: Dp = 10.dp, durationMs: Int = 420): Modifier {
    val still = reducedMotion()
    val offset = remember { Animatable(0f) }
    val first = remember { BooleanArray(1) { true } }
    LaunchedEffect(trigger) {
        if (first[0]) {
            first[0] = false
            return@LaunchedEffect
        }
        if (still) return@LaunchedEffect
        offset.snapTo(0f)
        offset.animateTo(
            0f,
            keyframes {
                this.durationMillis = durationMs
                SHAKE_STEPS.forEachIndexed { i, amount -> amount at durationMs * (i + 1) / (SHAKE_STEPS.size + 1) }
            },
        )
    }
    return graphicsLayer { translationX = offset.value * distance.toPx() }
}

/**
 * 요소 주위에 작은 별이 반짝였다 사라지기를 반복한다. 새 기능이나 보상을 강조할 때 쓴다.
 * 터치는 받지 않고 그리기만 한다. 별은 요소 바깥으로 조금 넘쳐 그리므로 부모가 자르지 않게 여유를 둔다.
 * [enabled]가 false이거나 '애니메이션 삭제' 접근성 설정이 켜져 있으면 그리지 않고, 요소가 창 밖에 있는 동안에는 멈춘다.
 *
 * ```kotlin
 * KButton(onClick = {}, modifier = Modifier.sparkle()) { Text("새 기능") }
 * ```
 *
 * @param color 별 색
 * @param count 동시에 반짝이는 별 수(1..64). 범위를 벗어나면 [IllegalArgumentException]을 던진다.
 * @param starSize 가장 클 때 별의 지름
 */
@Composable
public fun Modifier.sparkle(enabled: Boolean = true, color: Color = Color.White, count: Int = 10, starSize: Dp = 12.dp): Modifier {
    require(count in 1..MAX_SPARKLES) { "count는 1..64여야 합니다." }
    val active = enabled && !reducedMotion()
    if (!active) return this
    val clock = rememberLoopClock(running = true)
    val field = remember(count) { SparkleField(count) }
    return loopVisibility(clock).drawWithCache {
        val star = starPath()
        val spread = starSize.toPx()
        onDrawWithContent {
            drawContent()
            val now = clock.seconds
            for (i in 0 until field.count) {
                val life = field.life(i, now)
                // 0에서 커졌다가 다시 0으로 줄어드는 곡선
                val grow = 1f - (life * 2f - 1f) * (life * 2f - 1f)
                val x = -spread + (size.width + spread * 2f) * field.x[i]
                val y = -spread + (size.height + spread * 2f) * field.y[i]
                translate(x, y) {
                    rotate(life * STAR_SPIN, Offset.Zero) {
                        scale(grow * spread * field.scale[i], Offset.Zero) { drawPath(star, color, alpha = grow) }
                    }
                }
            }
        }
    }
}

/** 별 위치와 수명. 다 반짝인 별은 그리기 단계에서 새 자리로 옮기며, 프레임마다 객체를 만들지 않는다. */
private class SparkleField(val count: Int) {
    private val random = Random(count)
    val x = FloatArray(count) { random.nextFloat() }
    val y = FloatArray(count) { random.nextFloat() }
    val scale = FloatArray(count) { STAR_MIN + random.nextFloat() * (1f - STAR_MIN) }
    private val born = FloatArray(count) { -random.nextFloat() * STAR_LIFE_MAX }
    private val span = FloatArray(count) { STAR_LIFE_MIN + random.nextFloat() * (STAR_LIFE_MAX - STAR_LIFE_MIN) }

    /** [i]번째 별의 진행률(0..1)을 돌려준다. 수명이 끝났으면 새 위치에서 다시 태어나게 한다. */
    fun life(i: Int, now: Float): Float {
        if (now - born[i] >= span[i]) {
            born[i] = now
            x[i] = random.nextFloat()
            y[i] = random.nextFloat()
            scale[i] = STAR_MIN + random.nextFloat() * (1f - STAR_MIN)
        }
        return ((now - born[i]) / span[i]).coerceIn(0f, 1f)
    }
}

/** 지름 1, 중심이 원점인 네 갈래 별. 끝이 뾰족하고 가운데가 오목하다. */
private fun starPath(): Path = Path().apply {
    val r = HALF_UNIT
    val inner = r * STAR_INNER
    moveTo(0f, -r)
    quadraticTo(inner, -inner, r, 0f)
    quadraticTo(inner, inner, 0f, r)
    quadraticTo(-inner, inner, -r, 0f)
    quadraticTo(-inner, -inner, 0f, -r)
    close()
}

/**
 * [parallaxScroll]에서 위로 당겨 확대하는 효과의 상태. [nestedScrollConnection]을 스크롤 컨테이너의 `Modifier.nestedScroll`에 연결해야 동작한다.
 * 목록 맨 위에서 더 끌어내린 거리를 기억했다가 손을 떼면 스프링으로 되돌린다.
 */
@Stable
public class ParallaxState internal constructor() {
    internal var pull: Float by mutableFloatStateOf(0f)

    /** 맨 위에서 끌어내린 스크롤을 대신 받아 확대에 쓰는 연결. */
    public val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (pull <= 0f || available.y >= 0f) return Offset.Zero
            val used = maxOf(available.y * PULL_RESISTANCE, -pull)
            pull += used
            return Offset(0f, used / PULL_RESISTANCE)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
            pull += available.y * PULL_RESISTANCE
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (pull <= 0f) return Velocity.Zero
            animate(pull, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> pull = value }
            return available
        }
    }
}

/** 위로 당겨 확대하는 효과에 쓸 [ParallaxState]를 만든다. */
@Composable
public fun rememberParallaxState(): ParallaxState = remember { ParallaxState() }

/**
 * [scrollState]를 따라 헤더 이미지가 콘텐츠보다 느리게 올라가는 패럴랙스 효과를 준다. 스크롤 콘텐츠의 맨 위 요소에 붙인다.
 * [pullState]를 주면 맨 위에서 더 끌어내릴 때 헤더가 확대된다.
 *
 * ```kotlin
 * val scroll = rememberScrollState(); val pull = rememberParallaxState()
 * Column(Modifier.nestedScroll(pull.nestedScrollConnection).verticalScroll(scroll)) {
 *     Image(painter, null, Modifier.fillMaxWidth().height(280.dp).parallaxScroll(scroll, pullState = pull))
 * }
 * ```
 *
 * @param rate 콘텐츠가 스크롤한 거리 중 헤더가 덜 움직일 비율(0..1). 0이면 함께 움직이고 1이면 제자리에 머문다. 범위를 벗어나면 [IllegalArgumentException]을 던진다.
 */
public fun Modifier.parallaxScroll(scrollState: ScrollState, rate: Float = 0.5f, pullState: ParallaxState? = null): Modifier {
    require(rate in 0f..1f) { "rate는 0..1이어야 합니다." }
    return parallaxLayer(rate, pullState) { scrollState.value.toFloat() }
}

/**
 * [listState]를 따라 헤더가 느리게 움직이는 패럴랙스 효과를 준다. `LazyColumn`의 첫 항목에 붙인다. 나머지는 [ScrollState]판과 같다.
 *
 * ```kotlin
 * LazyColumn(Modifier.nestedScroll(pull.nestedScrollConnection), state = list) {
 *     item { Image(painter, null, Modifier.fillMaxWidth().height(280.dp).parallaxScroll(list, pullState = pull)) }
 * }
 * ```
 *
 * @param rate 헤더가 덜 움직일 비율(0..1). 범위를 벗어나면 [IllegalArgumentException]을 던진다.
 */
public fun Modifier.parallaxScroll(listState: LazyListState, rate: Float = 0.5f, pullState: ParallaxState? = null): Modifier {
    require(rate in 0f..1f) { "rate는 0..1이어야 합니다." }
    return parallaxLayer(rate, pullState) {
        if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 0f
    }
}

private fun Modifier.parallaxLayer(rate: Float, pullState: ParallaxState?, scrolled: () -> Float): Modifier = graphicsLayer {
    translationY = scrolled() * rate
    val pull = pullState?.pull ?: 0f
    if (size.height > 0f) {
        val zoom = 1f + pull / size.height
        scaleX = zoom
        scaleY = zoom
    }
    // 아래쪽을 기준으로 키워야 아래 콘텐츠와 경계가 벌어지지 않는다.
    transformOrigin = TransformOrigin(HALF_UNIT, 1f)
}

/**
 * 손가락을 대고 움직이면 그 자리에 빛이 따라다니는 카드 효과. 데스크톱의 마우스 호버 효과를 터치에 옮긴 것이다.
 * [shape] 안쪽을 은은하게 비추고 가장자리에는 같은 빛으로 글로우 테두리를 그린다. 손을 떼면 빛이 서서히 사라진다.
 * 터치 이벤트를 소비하지 않으므로 클릭과 스크롤은 그대로 동작한다.
 *
 * ```kotlin
 * KSurface(Modifier.spotlight(shape = RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp)) { ... }
 * ```
 *
 * @param color 빛 색. 알파로 진하기를 정한다.
 * @param radius 빛이 퍼지는 반경
 * @param borderWidth 가장자리 글로우 테두리 두께. 0이면 그리지 않는다.
 */
@Composable
public fun Modifier.spotlight(
    color: Color = Color.White.copy(alpha = 0.28f),
    shape: Shape = RectangleShape,
    radius: Dp = 160.dp,
    borderWidth: Dp = 1.5.dp,
): Modifier {
    val scope = rememberCoroutineScope()
    var point by remember { mutableStateOf(Offset.Unspecified) }
    val alpha = remember { Animatable(0f) }
    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                point = down.position
                scope.launch { alpha.animateTo(1f, tween(SPOT_IN_MS)) }
                var change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull()
                while (change != null && change.pressed) {
                    point = change.position
                    change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull()
                }
                scope.launch { alpha.animateTo(0f, tween(SPOT_OUT_MS)) }
            }
        }
        .drawWithCache {
            val r = radius.toPx()
            val glow = MovingRadial(color, r)
            val edge = MovingRadial(color.copy(alpha = minOf(1f, color.alpha * EDGE_BOOST)), r)
            val outline = shape.createOutline(size, layoutDirection, this)
            val stroke = Stroke(borderWidth.toPx())
            onDrawWithContent {
                drawContent()
                val a = alpha.value
                val at = point
                if (a > 0f && at != Offset.Unspecified) {
                    drawOutline(outline, glow.at(at), a)
                    if (borderWidth > 0.dp) drawOutline(outline, edge.at(at), a, stroke)
                }
            }
        }
}

/** 원점에 만든 원형 그라디언트를 행렬로 옮겨 쓴다. 손가락이 움직일 때 붓과 셰이더를 새로 만들지 않는다. */
private class MovingRadial(color: Color, radius: Float) {
    private val matrix = android.graphics.Matrix()
    private val shader = android.graphics.RadialGradient(
        0f,
        0f,
        radius,
        color.toArgb(),
        Color.Transparent.toArgb(),
        android.graphics.Shader.TileMode.CLAMP,
    )
    private val brush = ShaderBrush(shader)

    fun at(point: Offset): Brush {
        matrix.setTranslate(point.x, point.y)
        shader.setLocalMatrix(matrix)
        return brush
    }
}

private val SHAKE_STEPS = floatArrayOf(1f, -1f, 0.65f, -0.65f, 0.3f, -0.15f)
private const val MAX_SPARKLES = 64
private const val STAR_LIFE_MIN = 0.7f
private const val STAR_LIFE_MAX = 1.5f
private const val STAR_MIN = 0.45f
private const val STAR_SPIN = 90f
private const val STAR_INNER = 0.12f
private const val HALF_UNIT = 0.5f
private const val PULL_RESISTANCE = 0.5f
private const val SPOT_IN_MS = 180
private const val SPOT_OUT_MS = 420
private const val EDGE_BOOST = 2.5f
