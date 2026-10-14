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
 * 호스트의 아무 Composable에 미리 만든 움직임 효과([NodeEffects])를 붙인다. 리플·눌림·기울기, 나타나기, 둥실·반짝임·빛 번짐 등.
 *
 * 누름 효과는 손가락을 관찰만 하고 가로채지 않으므로 그 Composable의 클릭·스크롤은 그대로 동작한다. 거리 값(둥실·통통·빛 번짐)은 dp다.
 * 시스템 "애니메이션 줄이기"(애니메이터 배율 0)가 켜져 있으면 나타나기·반복은 하지 않는다.
 *
 * @param shape 리플·어둡게 덮기·빛 번짐의 모양. 둥근 버튼이면 그 모양을 준다.
 * @param enabled `false`면 효과 없이 그대로 그린다(편집 화면 등).
 */
@Composable
public fun Modifier.kineticaEffects(effects: NodeEffects, shape: Shape = RectangleShape, enabled: Boolean = true): Modifier =
    nodeEffects(effects, shape, enabled, LocalDensity.current.density)

/** 장면 렌더러용. [unitPx]는 효과 거리 1단위의 픽셀 수다(장면 안에서는 문서 단위 = 1 px). */
@Composable
internal fun Modifier.nodeEffects(effects: NodeEffects, shape: Shape, enabled: Boolean, unitPx: Float): Modifier {
    if (!enabled || effects.isEmpty) return this
    val reduced = rememberReducedMotion()
    val running = RunningEffects(
        effects = effects,
        appear = appearProgress(effects.appear.takeUnless { reduced }),
        loop = loopProgress(effects.loop.takeUnless { reduced }),
        press = remember { PressState() },
        unitPx = unitPx,
    )
    val scope = rememberCoroutineScope()
    val watched = if (effects.press.isEmpty()) this else watchPress(running.press, scope)
    return watched.graphicsLayer { running.applyTo(this) }.drawWithContent { running.draw(this, shape) }
}

/** 손가락을 관찰만 하고(소비하지 않음) 누름 상태를 바꾼다. */
private fun Modifier.watchPress(press: PressState, scope: kotlinx.coroutines.CoroutineScope): Modifier = pointerInput(press) {
    awaitPointerEventScope {
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull() ?: continue
            when {
                change.pressed && !press.down -> scope.launch { press.press(change.position, size.width.toFloat(), size.height.toFloat()) }
                !change.pressed && press.down -> scope.launch { press.release() }
            }
        }
    }
}

/** 지금 움직이는 효과들. 그리기 단계에서 값을 읽어 다시 구성하지 않고 프레임마다 반영한다. */
private class RunningEffects(
    val effects: NodeEffects,
    val appear: State<Float>,
    val loop: State<Float>?,
    val press: PressState,
    val unitPx: Float,
) {
    private val kinds = effects.press.associateBy { it.kind }

    fun applyTo(layer: androidx.compose.ui.graphics.GraphicsLayerScope) {
        val transform = EffectTransform().apply {
            applyAppear(effects.appear?.kind, appear.value, unitPx * SLIDE_DISTANCE)
            loop?.let { applyLoop(effects.loop, it.value, unitPx) }
            applyPress(kinds, press)
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
            // 반짝임은 그린 픽셀 위에만 겹쳐야 해서 따로 합성한다.
            if (effects.loop?.kind == LoopKind.Shimmer) compositingStrategy = CompositingStrategy.Offscreen
        }
    }

    fun draw(scope: ContentDrawScope, shape: Shape) = with(scope) {
        val looping = effects.loop
        val t = loop?.value
        if (looping?.kind == LoopKind.Glow && t != null) drawGlow(shape, looping, t, unitPx)
        drawContent()
        if (looping?.kind == LoopKind.Shimmer && t != null) drawShimmer(looping, t)
        kinds[PressKind.Dim]?.let { dim ->
            drawShape(shape, (dim.color?.toColor() ?: Color.Black).copy(alpha = dim.resolvedAmount.toFloat() * press.amount.value))
        }
        kinds[PressKind.Ripple]?.let { ripple -> drawRipple(shape, press, ripple.color?.toColor(), ripple.resolvedAmount.toFloat()) }
    }
}

/** 시스템 애니메이터 배율이 0이면 움직임을 줄인다. */
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

/** 효과들이 함께 만드는 변환. 여러 효과가 같은 값을 쓰면 곱하거나 더한다. */
private class EffectTransform {
    var scale = 1f
    var alpha = 1f
    var x = 0f
    var y = 0f
    var rotationX = 0f
    var rotationY = 0f
    var rotationZ = 0f

    // 종류마다 한 갈래인 분기다. 나누면 어느 효과가 어떤 값을 바꾸는지 흐려진다.
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
        }
    }

    fun applyPress(kinds: Map<PressKind, com.naury.kinetica.core.scene.PressEffect>, press: PressState) {
        val amount = press.amount.value
        kinds[PressKind.Scale]?.let { scale *= 1f + (it.resolvedAmount.toFloat() - 1f) * amount }
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

            LoopKind.Shimmer, LoopKind.Glow -> Unit
        }
    }
}

/** 누른 상태와 리플 진행. 손을 떼도 리플은 끝까지 퍼지며 사라진다. */
private class PressState {
    var down by mutableStateOf(false)
        private set
    val amount = Animatable(0f)
    val ripple = Animatable(0f)
    var origin by mutableStateOf(Offset.Zero)
        private set

    /** 누른 자리의 가로·세로 치우침(-1..1). 기울기 방향에 쓴다. */
    var tiltX = 0f
        private set
    var tiltY = 0f
        private set

    suspend fun press(position: Offset, width: Float, height: Float) {
        down = true
        origin = position
        tiltX = if (width > 0f) (position.x / width - HALF) * 2f else 0f
        tiltY = if (height > 0f) (position.y / height - HALF) * 2f else 0f
        ripple.snapTo(0f)
        kotlinx.coroutines.coroutineScope {
            launch { amount.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
            launch { ripple.animateTo(1f, tween(RIPPLE_MS, easing = FastOutSlowInEasing)) }
        }
    }

    suspend fun release() {
        down = false
        amount.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
}

private fun ContentDrawScope.drawShape(shape: Shape, color: Color) {
    if (color.alpha <= 0f) return
    val path = androidx.compose.ui.graphics.Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawShape)) }
    clipPath(path) { drawRect(color) }
}

private fun ContentDrawScope.drawRipple(shape: Shape, press: PressState, color: Color?, opacity: Float) {
    val p = press.ripple.value
    // 누르는 동안은 퍼진 채로 있고, 손을 떼면 퍼짐이 끝난 뒤 흐려진다.
    val fade = if (press.down) 1f else press.amount.value
    if (p <= 0f || fade <= 0f) return
    val radius = hypot(size.width, size.height) * p
    val path = androidx.compose.ui.graphics.Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawRipple)) }
    clipPath(path) { drawCircle((color ?: Color.Black).copy(alpha = opacity * fade), radius, press.origin) }
}

private fun ContentDrawScope.drawShimmer(loop: LoopEffect, t: Float) {
    val band = size.width * SHIMMER_BAND
    val start = -band + (size.width + 2 * band) * t
    val color = loop.color?.toColor() ?: Color.White
    val brush = Brush.linearGradient(
        listOf(Color.Transparent, color.copy(alpha = loop.resolvedAmount.toFloat()), Color.Transparent),
        start = Offset(start - band, 0f),
        end = Offset(start + band, size.height),
    )
    drawRect(brush, blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
}

private fun ContentDrawScope.drawGlow(shape: Shape, loop: LoopEffect, t: Float, unitPx: Float) {
    val wave = sin(PI * t).toFloat()
    val radius = loop.resolvedAmount.toFloat() * unitPx * (GLOW_MIN + (1f - GLOW_MIN) * wave * wave)
    if (radius <= 0f) return
    val color = loop.color?.toColor() ?: Color.White
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = androidx.compose.ui.graphics.Path().apply { addOutline(outline) }
    drawContext.canvas.nativeCanvas.drawPath(
        path.asAndroidPath(),
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.copy(alpha = color.alpha * GLOW_ALPHA).toArgb()
            maskFilter = android.graphics.BlurMaskFilter(radius, android.graphics.BlurMaskFilter.Blur.OUTER)
        },
    )
}

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
