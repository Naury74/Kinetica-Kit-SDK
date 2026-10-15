package com.naury.kinetica.render

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.naury.kinetica.core.scene.AppearEffect
import com.naury.kinetica.core.scene.AppearKind
import com.naury.kinetica.core.scene.LoopEffect
import com.naury.kinetica.core.scene.LoopKind
import com.naury.kinetica.core.scene.NodeEffects
import com.naury.kinetica.core.scene.PressKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 호스트의 Composable 어디에나 미리 만든 모션 효과([NodeEffects])를 적용한다. 리플, 누르면 작아지기, 기울기 같은 누름 효과와
 * 등장 애니메이션, Float, Shimmer, Glow 같은 반복 애니메이션이 있다.
 *
 * 누름 효과는 포인터 이벤트를 관찰만 하고 가로채지 않으므로 그 Composable의 클릭과 스크롤은 평소대로 동작한다.
 * Float, Bounce, Glow 같은 효과의 거리 값은 dp다. 시스템의 '애니메이션 삭제' 설정(애니메이터 배율 0)이 켜져 있으면 등장 애니메이션과
 * 반복 애니메이션은 재생하지 않는다.
 *
 * 수정자 체인의 맨 앞, 적어도 `background`, `clip`, `clickable`보다 앞에 둔다. 그래야 크기와 기울기 변화가 배경과 리플까지 함께 움직이고,
 * Dim이나 Ripple이 배경 위에 덮인다. 뒤에 두면 클릭은 그대로 되지만 배경은 움직이지 않는다.
 *
 * ```kotlin
 * Button(onClick = ::buy, modifier = Modifier.kineticaEffects(EffectPresets.button, CircleShape)) { Text("구매") }
 * ```
 *
 * @param shape 리플, 어둡게 덮기, Glow에 쓸 모양. 둥근 버튼이면 버튼과 같은 모양을 넘긴다.
 * @param enabled `false`면 효과 없이 그대로 그린다. 편집 화면 등에서 쓴다.
 */
@Composable
public fun Modifier.kineticaEffects(effects: NodeEffects, shape: Shape = RectangleShape, enabled: Boolean = true): Modifier =
    nodeEffects(effects, shape, enabled, LocalDensity.current.density)

/** 장면 렌더러용. [unitPx]는 효과 거리 1단위에 해당하는 픽셀 수다. 장면 안에서는 문서 단위 1이 1px이다. */
@Composable
internal fun Modifier.nodeEffects(effects: NodeEffects, shape: Shape, enabled: Boolean, unitPx: Float): Modifier {
    if (!enabled || effects.isEmpty) return this
    val reduced = rememberReducedMotion()
    val cache = remember { EffectDrawCache() }
    val running = RunningEffects(
        cache = cache,
        effects = effects,
        appear = appearProgress(effects.appear.takeUnless { reduced }),
        loop = loopProgress(effects.loop.takeUnless { reduced }),
        appearBlur = effects.appear?.kind == AppearKind.Blur && !reduced,
        unitPx = unitPx,
    )
    val scope = rememberCoroutineScope()
    val watched = if (effects.press.isEmpty()) this else watchPress(running.press, scope)
    return watched.graphicsLayer { running.applyTo(this, shape) }.drawWithContent { running.draw(this, shape) }
}

/** 포인터 이벤트를 소비하지 않고 관찰만 해서 누름 상태를 바꾼다. */
private fun Modifier.watchPress(press: PressState, scope: kotlinx.coroutines.CoroutineScope): Modifier = pointerInput(press) {
    awaitPointerEventScope {
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull() ?: continue
            val bounds = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())
            when {
                change.pressed && !press.down -> press.start(scope, change.position, bounds)
                change.pressed -> press.track(change.position, viewConfiguration.touchSlop)
                press.down -> press.finish(scope, change.position, bounds)
            }
        }
    }
}

/** 재생 중인 효과. 값은 그리기 단계에서 읽으므로 재구성 없이 프레임마다 반영된다. */
private class RunningEffects(
    val cache: EffectDrawCache,
    val effects: NodeEffects,
    val appear: State<Float>,
    val loop: State<Float>?,
    val unitPx: Float,
    val appearBlur: Boolean,
) {
    val press: PressState get() = cache.press
    private val kinds = effects.press.associateBy { it.kind }

    init {
        press.bursts = PressKind.Burst in kinds
    }

    fun applyTo(layer: androidx.compose.ui.graphics.GraphicsLayerScope, shape: Shape) {
        val transform = cache.transform.apply {
            reset()
            applyAppear(effects.appear?.kind, appear.value, unitPx * SLIDE_DISTANCE)
            loop?.let { applyLoop(effects.loop, it.value, unitPx) }
            applyPress(kinds, press, unitPx)
        }
        with(layer) {
            scaleX = transform.scale
            scaleY = transform.scale
            alpha = transform.alpha
            translationX = transform.x
            translationY = transform.y
            rotationX = transform.rotationX
            rotationY = transform.rotationY
            rotationZ = transform.rotationZ
            cameraDistance = CAMERA_DISTANCE * density
            // Lift는 플랫폼 그림자(elevation)로 그린다. 모든 API에서 부드러운 그림자가 나오고 프레임마다 새로 만들 객체도 없다.
            kinds[PressKind.Lift]?.let { lift ->
                this.shape = shape
                shadowElevation = lift.resolvedAmount.toFloat() * unitPx * LIFT_ELEVATION * press.amount.value
                lift.color?.toColor()?.let {
                    ambientShadowColor = it
                    spotShadowColor = it
                }
            }
            if (appearBlur && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val radius = (1f - appear.value) * BLUR_FROM * density
                renderEffect = if (radius > MIN_BLUR) androidx.compose.ui.graphics.BlurEffect(radius, radius) else null
            }
            // Shimmer는 이미 그린 픽셀 위에만 겹쳐야 하므로 따로 합성한다.
            if (effects.loop?.kind == LoopKind.Shimmer) compositingStrategy = CompositingStrategy.Offscreen
        }
    }

    fun draw(scope: ContentDrawScope, shape: Shape) = with(scope) {
        val looping = effects.loop
        val t = loop?.value
        if (looping?.kind == LoopKind.Glow && t != null) drawGlow(cache, shape, looping, t, unitPx)
        if (looping?.kind == LoopKind.Ping && t != null) drawPing(cache.path(this, shape), looping, t, unitPx)
        drawContent()
        if (looping?.kind == LoopKind.Shimmer && t != null) drawShimmer(cache, looping, t)
        kinds[PressKind.Dim]?.let { dim ->
            val color = (dim.color?.toColor() ?: Color.Black).copy(alpha = dim.resolvedAmount.toFloat() * press.amount.value)
            if (color.alpha > 0f) clipPath(cache.path(this, shape)) { drawRect(color) }
        }
        kinds[PressKind.Ripple]?.let { ripple ->
            drawRipple(cache.path(this, shape), press, ripple.color?.toColor(), ripple.resolvedAmount.toFloat())
        }
        kinds[PressKind.Burst]?.let { burst -> drawBurst(burst, press.burst.value, unitPx) }
    }
}

/**
 * 프레임마다 객체를 만들지 않도록 모양 경로, 붓, Paint를 보관한다. 크기나 모양이 바뀔 때만 새로 만든다.
 * 목록에 효과를 수백 개 붙여도 프레임마다 쓰레기가 생기지 않게 하려는 것이다.
 */
private class EffectDrawCache {
    val transform = EffectTransform()
    val press = PressState()
    private val outline = androidx.compose.ui.graphics.Path()
    private var outlineSize = androidx.compose.ui.geometry.Size.Unspecified
    private var outlineDirection: androidx.compose.ui.unit.LayoutDirection? = null
    private var outlineShape: Shape? = null
    private var shimmerBrush: Brush? = null
    private var shimmerSize = androidx.compose.ui.geometry.Size.Unspecified
    private var shimmerColor = Color.Unspecified
    private val glow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private var glowRadius = -1f

    fun path(scope: ContentDrawScope, shape: Shape): androidx.compose.ui.graphics.Path {
        if (scope.size != outlineSize || scope.layoutDirection != outlineDirection || shape != outlineShape) {
            outlineSize = scope.size
            outlineDirection = scope.layoutDirection
            outlineShape = shape
            outline.reset()
            outline.addOutline(shape.createOutline(scope.size, scope.layoutDirection, scope))
        }
        return outline
    }

    fun shimmer(size: androidx.compose.ui.geometry.Size, color: Color, amount: Float, band: Float): Brush {
        val tint = color.copy(alpha = amount)
        val cached = shimmerBrush
        if (cached != null && size == shimmerSize && tint == shimmerColor) return cached
        shimmerSize = size
        shimmerColor = tint
        return Brush.linearGradient(
            listOf(Color.Transparent, tint, Color.Transparent),
            start = Offset.Zero,
            end = Offset(band * 2, size.height),
        ).also { shimmerBrush = it }
    }

    /** 블러 반경은 1px 단위로 바꿔서 BlurMaskFilter를 프레임마다 새로 만들지 않게 한다. */
    fun glowPaint(argb: Int, radius: Float): android.graphics.Paint {
        val rounded = kotlin.math.ceil(radius)
        if (rounded != glowRadius) {
            glowRadius = rounded
            glow.maskFilter = android.graphics.BlurMaskFilter(rounded, android.graphics.BlurMaskFilter.Blur.OUTER)
        }
        glow.color = argb
        return glow
    }
}

/** 시스템 애니메이터 배율이 0이면 애니메이션을 줄인다. */
@Composable
private fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

@Composable
private fun appearProgress(appear: AppearEffect?): State<Float> {
    val progress = remember(appear) { Animatable(if (appear == null) 1f else 0f) }
    LaunchedEffect(appear) {
        if (appear == null) return@LaunchedEffect
        delay(appear.delayMs)
        val spec = if (appear.kind == AppearKind.Pop) {
            spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
        } else {
            tween(appear.durationMs.toInt(), easing = FastOutSlowInEasing)
        }
        progress.animateTo(1f, spec)
    }
    return progress.asState()
}

@Composable
private fun loopProgress(loop: LoopEffect?): State<Float>? {
    loop ?: return null
    val transition = rememberInfiniteTransition(label = "kinetica-loop")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(loop.periodMs.toInt(), easing = LinearEasing), RepeatMode.Restart),
        label = "kinetica-loop-progress",
    )
}

/** 여러 효과를 합친 변환. 같은 값을 쓰는 효과가 여럿이면 곱하거나 더한다. */
private class EffectTransform {
    var scale = 1f
    var alpha = 1f
    var x = 0f
    var y = 0f
    var rotationX = 0f
    var rotationY = 0f
    var rotationZ = 0f

    fun reset() {
        scale = 1f
        alpha = 1f
        x = 0f
        y = 0f
        rotationX = 0f
        rotationY = 0f
        rotationZ = 0f
    }

    // 효과 종류마다 분기가 하나씩이다. 함수를 나누면 어느 효과가 어떤 값을 바꾸는지 알아보기 어려워진다.
    @Suppress("CyclomaticComplexMethod")
    fun applyAppear(kind: AppearKind?, p: Float, slide: Float) {
        kind ?: return
        val rest = 1f - p
        alpha *= p.coerceIn(0f, 1f)
        when (kind) {
            AppearKind.Fade -> Unit
            AppearKind.SlideUp -> y += rest * slide
            AppearKind.SlideDown -> y -= rest * slide
            AppearKind.SlideLeft -> x += rest * slide
            AppearKind.SlideRight -> x -= rest * slide
            AppearKind.Pop -> scale *= POP_FROM + (1f - POP_FROM) * p
            AppearKind.Zoom -> scale *= 1f + (ZOOM_FROM - 1f) * rest
            AppearKind.Flip -> rotationX += rest * FLIP_DEGREES
            AppearKind.Blur -> scale *= 1f + (BLUR_SCALE - 1f) * rest
        }
    }

    fun applyPress(kinds: Map<PressKind, com.naury.kinetica.core.scene.PressEffect>, press: PressState, unitPx: Float) {
        val amount = press.amount.value
        kinds[PressKind.Scale]?.let { scale *= 1f + (it.resolvedAmount.toFloat() - 1f) * amount }
        kinds[PressKind.Lift]?.let {
            y -= it.resolvedAmount.toFloat() * unitPx * amount
            scale *= 1f + LIFT_SCALE * amount
        }
        kinds[PressKind.Tilt]?.let {
            rotationX += -press.tiltY * it.resolvedAmount.toFloat() * amount
            rotationY += press.tiltX * it.resolvedAmount.toFloat() * amount
        }
    }

    @Suppress("CyclomaticComplexMethod")
    fun applyLoop(loop: LoopEffect?, t: Float, unitPx: Float) {
        loop ?: return
        val amount = loop.resolvedAmount.toFloat()
        val wave = sin(PI * t).toFloat()
        when (loop.kind) {
            LoopKind.Pulse -> scale *= 1f + (amount - 1f) * wave * wave

            LoopKind.Breathe -> alpha *= 1f - (1f - amount) * wave * wave

            LoopKind.Float -> y += -amount * unitPx * sin(2 * PI * t).toFloat()

            LoopKind.Bounce -> y += -amount * unitPx * abs(wave)

            LoopKind.Shake ->
                if (t <
                    SHAKE_PART
                ) {
                    rotationZ += amount * sin(2 * PI * SHAKES * t / SHAKE_PART).toFloat() * (1f - t / SHAKE_PART)
                }

            LoopKind.Spin -> rotationZ += FULL_TURN * t

            LoopKind.Heartbeat -> scale *= 1f + (amount - 1f) * heartbeat(t)

            LoopKind.Wiggle -> rotationZ += amount * sin(2 * PI * t).toFloat()

            LoopKind.Shimmer, LoopKind.Glow, LoopKind.Ping -> Unit
        }
    }
}

/** 누름 상태와 리플 진행도. 손을 떼도 리플은 끝까지 퍼진 뒤 사라진다. */
private class PressState {
    var down by mutableStateOf(false)
        private set
    val amount = Animatable(0f)
    val ripple = Animatable(0f)
    val burst = Animatable(0f)
    var origin by mutableStateOf(Offset.Zero)
        private set

    /** 손을 뗄 때 Burst 효과를 재생할지. */
    var bursts = false

    /** 실행 중인 누르기 애니메이션. */
    var pressing: kotlinx.coroutines.Job? = null

    /** 누른 위치가 가운데에서 가로, 세로로 치우친 정도(-1..1). 기울기 방향에 쓴다. */
    var tiltX = 0f
        private set
    var tiltY = 0f
        private set

    /** 누른 뒤 touch slop보다 멀리 움직여 스크롤로 판단했는지. */
    var moved = false

    /** 누르기를 시작한다. 빠른 탭에서 떼기를 놓치지 않도록 상태는 바로 바꾸고 애니메이션만 코루틴으로 돌린다. */
    fun start(scope: kotlinx.coroutines.CoroutineScope, position: Offset, bounds: androidx.compose.ui.geometry.Size) {
        down(position, bounds.width, bounds.height)
        pressing = scope.launch { animatePress() }
    }

    /** touch slop보다 멀리 움직였으면 스크롤로 보고, 손을 떼도 Burst를 재생하지 않는다. */
    fun track(position: Offset, slop: Float) {
        if ((position - origin).getDistance() > slop) moved = true
    }

    /** 손을 뗀다. 누르기 애니메이션이 아직 시작 전이면 떼기 애니메이션을 덮어쓰므로 먼저 취소한다. Burst는 탭일 때만 재생한다. */
    fun finish(scope: kotlinx.coroutines.CoroutineScope, position: Offset, bounds: androidx.compose.ui.geometry.Size) {
        up()
        pressing?.cancel()
        scope.launch { animateRelease() }
        val inside = position.x in 0f..bounds.width && position.y in 0f..bounds.height
        if (bursts && !moved && inside) scope.launch { animateBurst() }
    }

    private fun down(position: Offset, width: Float, height: Float) {
        down = true
        moved = false
        origin = position
        tiltX = if (width > 0f) (position.x / width - HALF) * 2f else 0f
        tiltY = if (height > 0f) (position.y / height - HALF) * 2f else 0f
    }

    private fun up() {
        down = false
    }

    suspend fun animatePress() {
        ripple.snapTo(0f)
        kotlinx.coroutines.coroutineScope {
            launch { amount.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
            launch { ripple.animateTo(1f, tween(RIPPLE_MS, easing = FastOutSlowInEasing)) }
        }
    }

    suspend fun animateRelease() {
        amount.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }

    suspend fun animateBurst() {
        burst.snapTo(0f)
        burst.animateTo(1f, tween(BURST_MS, easing = FastOutSlowInEasing))
    }
}

private fun ContentDrawScope.drawRipple(path: androidx.compose.ui.graphics.Path, press: PressState, color: Color?, opacity: Float) {
    val p = press.ripple.value
    // 누르는 동안은 퍼진 상태를 유지하고, 손을 떼면 다 퍼진 뒤 페이드 아웃된다.
    val fade = if (press.down) 1f else press.amount.value
    if (p <= 0f || fade <= 0f) return
    val radius = hypot(size.width, size.height) * p
    clipPath(path) { drawCircle((color ?: Color.Black).copy(alpha = opacity * fade), radius, press.origin) }
}

private fun ContentDrawScope.drawShimmer(cache: EffectDrawCache, loop: LoopEffect, t: Float) {
    val band = size.width * SHIMMER_BAND
    val start = -band + (size.width + 2 * band) * t
    val brush = cache.shimmer(size, loop.color?.toColor() ?: Color.White, loop.resolvedAmount.toFloat(), band)
    // 붓은 크기가 바뀔 때만 만들고, 프레임마다 옮겨 그린다.
    val left = start - band
    translate(left = left) {
        drawRect(brush, topLeft = Offset(-left, 0f), size = size, blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
    }
}

private fun ContentDrawScope.drawGlow(cache: EffectDrawCache, shape: Shape, loop: LoopEffect, t: Float, unitPx: Float) {
    val wave = sin(PI * t).toFloat()
    val radius = loop.resolvedAmount.toFloat() * unitPx * (GLOW_MIN + (1f - GLOW_MIN) * wave * wave)
    if (radius <= 0f) return
    val color = loop.color?.toColor() ?: Color.White
    val paint = cache.glowPaint(color.copy(alpha = color.alpha * GLOW_ALPHA).toArgb(), radius)
    drawContext.canvas.nativeCanvas.drawPath(cache.path(this, shape).asAndroidPath(), paint)
}

/** 한 주기 앞부분에서 두 번 뛰는 크기 변화(0..1). */
private fun heartbeat(t: Float): Float {
    fun beat(start: Float): Float {
        val x = (t - start) / BEAT_LENGTH
        return if (x in 0f..1f) sin(PI * x).toFloat() else 0f
    }
    return maxOf(beat(0f), beat(SECOND_BEAT) * SECOND_BEAT_STRENGTH)
}

private fun ContentDrawScope.drawPing(path: androidx.compose.ui.graphics.Path, loop: LoopEffect, t: Float, unitPx: Float) {
    val distance = loop.resolvedAmount.toFloat() * unitPx
    if (distance <= 0f || size.width <= 0f || size.height <= 0f) return
    val color = loop.color?.toColor() ?: ACCENT
    drawPingRing(path, color, distance, t)
    drawPingRing(path, color, distance, (t + HALF) % 1f)
}

private fun ContentDrawScope.drawPingRing(path: androidx.compose.ui.graphics.Path, color: Color, distance: Float, phase: Float) {
    val grow = distance * phase
    withTransform({ scale((size.width + 2 * grow) / size.width, (size.height + 2 * grow) / size.height, center) }) {
        drawPath(path, color.copy(alpha = color.alpha * PING_ALPHA * (1f - phase)))
    }
}

private fun ContentDrawScope.drawBurst(burst: com.naury.kinetica.core.scene.PressEffect, p: Float, unitPx: Float) {
    if (p <= 0f || p >= 1f) return
    val count = burst.resolvedAmount.toInt()
    val short = minOf(size.width, size.height)
    val dot = maxOf(MIN_DOT * unitPx, short * DOT_RATIO) * (1f - p * HALF)
    val base = burst.color?.toColor() ?: ACCENT
    // 조각마다 진한 색과 옅은 색을 번갈아 써서 반짝이는 느낌을 낸다.
    val light = androidx.compose.ui.graphics.lerp(base, Color.White, LIGHT_MIX)
    val fade = 1f - p * p
    // 가로로 긴 버튼에서도 조각이 가장자리 밖으로 나가도록 버튼 크기에 맞춘 타원을 따라 퍼뜨린다.
    val spread = 1f + BURST_REACH * p
    val rx = size.width * HALF * spread
    val ry = size.height * HALF * spread
    repeat(count) { i ->
        val angle = 2 * PI * i / count - PI / 2
        val color = (if (i % 2 == 0) base else light).let { it.copy(alpha = it.alpha * fade) }
        val reach = if (i % 2 == 0) 1f else INNER_REACH
        drawCircle(color, dot, center + Offset((kotlin.math.cos(angle) * rx * reach).toFloat(), (sin(angle) * ry * reach).toFloat()))
    }
}

private val ACCENT = Color(0xFF5B6CFF)
private const val SLIDE_DISTANCE = 24f
private const val POP_FROM = 0.6f
private const val ZOOM_FROM = 1.15f
private const val FLIP_DEGREES = 90f
private const val SHAKE_PART = 0.35f
private const val SHAKES = 3
private const val FULL_TURN = 360f
private const val HALF = 0.5f
private const val RIPPLE_MS = 450
private const val SHIMMER_BAND = 0.35f
private const val GLOW_MIN = 0.4f
private const val GLOW_ALPHA = 0.8f
private const val CAMERA_DISTANCE = 12f
private const val BLUR_FROM = 16f
private const val MIN_BLUR = 0.5f
private const val BLUR_SCALE = 1.04f
private const val LIFT_SCALE = 0.02f
private const val LIFT_ELEVATION = 2f
private const val BEAT_LENGTH = 0.15f
private const val SECOND_BEAT = 0.22f
private const val SECOND_BEAT_STRENGTH = 0.8f
private const val PING_ALPHA = 0.45f
private const val BURST_MS = 560
private const val BURST_REACH = 1.3f
private const val MIN_DOT = 3f
private const val DOT_RATIO = 0.09f
private const val LIGHT_MIX = 0.45f
private const val INNER_REACH = 0.8f
