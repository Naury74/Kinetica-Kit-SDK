package com.naury.kinetica.ui

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.SweepGradient
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 시스템 "애니메이션 삭제"가 켜져 있는지. 켜져 있으면 반복·기울기 효과를 멈춘다. */
@Composable
internal fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * 가장자리를 따라 색이 돌아가는 빛 테두리(Siri·Apple Intelligence 느낌). 바깥으로 [glow]만큼 은은히 번진다.
 * 화면 전체 가장자리에 붙이면 "듣는 중" 표시가 된다. 애니메이션 삭제 설정이면 돌지 않는다.
 *
 * @param periodMs 한 바퀴 시간
 */
@Composable
public fun Modifier.glowBorder(
    shape: Shape = RectangleShape,
    colors: List<Color> = GlowColors,
    width: Dp = 3.dp,
    glow: Dp = 14.dp,
    periodMs: Int = 3_000,
): Modifier {
    require(colors.size >= 2) { "색이 둘 이상이어야 합니다." }
    val still = reducedMotion()
    val angle by rememberInfiniteTransition(label = "glow-border").animateFloat(
        0f,
        if (still) 0f else FULL_TURN,
        infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
        label = "angle",
    )
    val argb = remember(colors) { (colors + colors.first()).map { it.toArgb() }.toIntArray() }
    return drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = outline.asPath().asAndroidPath()
        val shader = SweepGradient(size.width / 2, size.height / 2, argb, null).apply {
            setLocalMatrix(Matrix().apply { setRotate(angle, size.width / 2, size.height / 2) })
        }
        drawIntoCanvas { canvas ->
            val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = width.toPx() + glow.toPx()
                this.shader = shader
                alpha = GLOW_ALPHA
                if (glow.value > 0f) maskFilter = BlurMaskFilter(glow.toPx(), BlurMaskFilter.Blur.NORMAL)
            }
            val linePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = width.toPx()
                this.shader = shader
            }
            canvas.nativeCanvas.drawPath(path, glowPaint)
            canvas.nativeCanvas.drawPath(path, linePaint)
        }
    }
}

/** Siri 느낌의 기본 빛 색. */
public val GlowColors: List<Color> = listOf(Color(0xFF7B61FF), Color(0xFFFF5CAA), Color(0xFFFF9F43), Color(0xFF2ED3FF))

/**
 * 누르면 젤리처럼 납작해졌다가 떼면 출렁이며 돌아온다. 손가락은 관찰만 하므로 클릭·스크롤은 그대로다.
 *
 * @param amount 납작해지는 정도(0..0.3)
 */
@Composable
public fun Modifier.jellyPress(amount: Float = 0.08f): Modifier {
    require(amount in 0f..MAX_JELLY) { "amount는 0..0.3이어야 합니다." }
    val scope = rememberCoroutineScope()
    val x = remember { Animatable(1f) }
    val y = remember { Animatable(1f) }
    val press = spring<Float>(stiffness = Spring.StiffnessHigh)
    val release = spring<Float>(dampingRatio = JELLY_DAMPING, stiffness = Spring.StiffnessMediumLow)
    return this
        .observePress(
            onDown = {
                scope.launch { x.animateTo(1f + amount * JELLY_WIDEN, press) }
                scope.launch { y.animateTo(1f - amount, press) }
            },
            onUp = {
                scope.launch { x.animateTo(1f, release) }
                scope.launch { y.animateTo(1f, release) }
            },
        )
        .graphicsLayer {
            scaleX = x.value
            scaleY = y.value
        }
}

/**
 * 손가락을 대고 움직이면 요소가 손가락 쪽으로 끌려가고, 떼면 튕기며 제자리로 돌아온다(자석 버튼).
 *
 * @param strength 손가락이 가운데에서 떨어진 거리의 몇 배만큼 따라갈지(0..1)
 */
@Composable
public fun Modifier.magnetic(strength: Float = 0.25f): Modifier {
    require(strength in 0f..1f) { "strength는 0..1이어야 합니다." }
    val scope = rememberCoroutineScope()
    val pull = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    return this
        .pointerInput(strength) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull()
                while (change != null && change.pressed) {
                    val target = (change.position - Offset(size.width / 2f, size.height / 2f)) * strength
                    scope.launch { pull.snapTo(target) }
                    change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull()
                }
                scope.launch {
                    pull.animateTo(Offset.Zero, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                }
            }
        }
        .graphicsLayer {
            translationX = pull.value.x
            translationY = pull.value.y
        }
}

/**
 * 기기를 기울이면 요소가 3D로 따라 기울고 빛 반사가 반대쪽으로 흐른다(Apple TV 포스터 느낌). 가속도 센서를 쓰며
 * 화면에 보이는 동안만 듣는다. 센서가 없거나 애니메이션 삭제 설정이면 움직이지 않는다.
 *
 * @param maxDegrees 가장 많이 기우는 각도
 * @param shine 빛 반사의 진하기(0..1). 0이면 반사를 그리지 않는다.
 */
@Composable
public fun Modifier.motionTilt(maxDegrees: Float = 10f, shine: Float = 0.25f): Modifier {
    val context = LocalContext.current
    val still = reducedMotion()
    var tilt by remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(context, still) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // 중력 방향(x: 좌우, y: 위아래)을 -1..1로 줄이고 부드럽게 따라간다.
                val x = (event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                val y = ((event.values[1] / SensorManager.GRAVITY_EARTH) - REST_Y).coerceIn(-1f, 1f)
                tilt = Offset(tilt.x + (x - tilt.x) * SMOOTH, tilt.y + (y - tilt.y) * SMOOTH)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (!still && sensor != null) manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager?.unregisterListener(listener) }
    }
    return this
        .graphicsLayer {
            rotationY = tilt.x * maxDegrees
            rotationX = -tilt.y * maxDegrees
            cameraDistance = CAMERA * density
        }
        .drawWithContent {
            drawContent()
            if (shine > 0f) {
                // 빛은 기운 반대쪽으로 흐른다.
                val center = Offset(size.width * (HALF - tilt.x * SHINE_TRAVEL), size.height * (SHINE_TOP + tilt.y * SHINE_TRAVEL))
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = shine), Color.Transparent),
                        center,
                        size.maxDimension * SHINE_RADIUS,
                    ),
                )
            }
        }
}

/** 손가락 누름·뗌을 관찰만 한다(소비하지 않음). */
private fun Modifier.observePress(onDown: () -> Unit, onUp: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onDown()
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
        } while (event.changes.any { it.pressed })
        onUp()
    }
}

private fun Outline.asPath(): Path = when (this) {
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Generic -> path
}

private const val FULL_TURN = 360f
private const val GLOW_ALPHA = 150
private const val MAX_JELLY = 0.3f
private const val JELLY_WIDEN = 0.6f
private const val JELLY_DAMPING = 0.35f
private const val REST_Y = 0.55f
private const val SMOOTH = 0.15f
private const val CAMERA = 12f
private const val HALF = 0.5f
private const val SHINE_TRAVEL = 0.6f
private const val SHINE_TOP = 0.3f
private const val SHINE_RADIUS = 0.7f
