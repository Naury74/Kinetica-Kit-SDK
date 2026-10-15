package com.naury.kinetica.ui

import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 누르는 동안 손가락이 있는 쪽이 눌려 들어가도록 요소를 3D로 기울인다. 떼면 스프링으로 돌아온다.
 * 터치 이벤트를 소비하지 않으므로 클릭과 스크롤은 그대로 동작한다. '애니메이션 삭제' 접근성 설정이 켜져 있으면 기울이지 않는다.
 *
 * ```kotlin
 * GlassSurface(Modifier.tiltOnPress().clickable { open() }) { ... }
 * ```
 *
 * @param maxDegrees 가장자리를 눌렀을 때의 최대 기울기(도). 음수면 [IllegalArgumentException]을 던진다.
 */
@Composable
public fun Modifier.tiltOnPress(maxDegrees: Float = 8f): Modifier {
    require(maxDegrees >= 0f) { "maxDegrees는 0 이상이어야 합니다." }
    if (reducedMotion()) return this
    val touch = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    return this
        .pointerInput(Unit) { trackTilt(scope, touch, null) }
        .graphicsLayer {
            rotationY = touch.value.x * maxDegrees
            rotationX = -touch.value.y * maxDegrees
            cameraDistance = HOLO_CAMERA * density
        }
}

/**
 * 포켓몬 카드의 홀로그램처럼 카드 표면에 무지개빛 광택을 입힌다. 누른 위치나 기기 기울기에 따라 무지개 띠와 반사광, 반짝이가 움직인다.
 * 누르는 동안에는 [tiltOnPress]처럼 카드가 3D로 기울고 광택이 진해진다. [useSensor]가 true면 기기를 기울여도 움직이며,
 * 센서는 화면이 STARTED 이상인 동안에만 받는다. 터치 이벤트를 소비하지 않으므로 클릭과 스크롤은 그대로 동작한다.
 * '애니메이션 삭제' 접근성 설정이 켜져 있으면 기울이지 않고 광택을 가만히 그린다.
 *
 * 광택은 콘텐츠 위에 [shape] 안으로만 그리므로 카드 모양과 맞춘다. 어두운 카드나 사진 위에서 잘 보인다.
 *
 * ```kotlin
 * Image(art, null, Modifier.size(220.dp, 308.dp).holographic(RoundedCornerShape(16.dp), useSensor = true))
 * ```
 *
 * @param intensity 광택 진하기(0..1). 범위를 벗어나면 [IllegalArgumentException]을 던진다.
 * @param maxDegrees 누를 때 최대 기울기(도). 0이면 기울이지 않고 광택만 움직인다. 음수면 [IllegalArgumentException]을 던진다.
 * @param useSensor 기기 기울기에 반응할지. 중력 센서가 없으면 가속도 센서를 쓰고, 둘 다 없으면 무시한다.
 */
@Composable
public fun Modifier.holographic(
    shape: Shape = RectangleShape,
    intensity: Float = 0.6f,
    maxDegrees: Float = 10f,
    useSensor: Boolean = false,
): Modifier {
    require(intensity in 0f..1f) { "intensity는 0..1이어야 합니다." }
    require(maxDegrees >= 0f) { "maxDegrees는 0 이상이어야 합니다." }
    val still = reducedMotion()
    val touch = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val pressed = remember { Animatable(0f) }
    val sensor = rememberHoloSensor(useSensor && !still)
    val scope = rememberCoroutineScope()
    val foil = remember { HoloFoil() }
    val angle = {
        val s = sensor?.value ?: Offset.Zero
        touch.value + s * (1f - pressed.value)
    }
    return this
        .then(if (still) Modifier else Modifier.pointerInput(Unit) { trackTilt(scope, touch, pressed) })
        .graphicsLayer {
            rotationY = touch.value.x * maxDegrees
            rotationX = -touch.value.y * maxDegrees
            cameraDistance = HOLO_CAMERA * density
        }
        .drawWithCache {
            foil.resize(size)
            val clip = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            onDrawWithContent {
                drawContent()
                val level = intensity * (REST_LEVEL + (1f - REST_LEVEL) * pressed.value)
                if (level > 0f) clipPath(clip) { foil.draw(this, angle(), level) }
            }
        }
}

/** 누르고 끄는 동안 손가락 위치를 -1..1 기울기로 바꿔 [touch]를 따라가게 하고, 떼면 0으로 돌린다. */
private suspend fun PointerInputScope.trackTilt(scope: CoroutineScope, touch: Animatable<Offset, *>, pressed: Animatable<Float, *>?) {
    fun aim(position: Offset) {
        val w = size.width.coerceAtLeast(1)
        val h = size.height.coerceAtLeast(1)
        val target = Offset((position.x / w * 2f - 1f).coerceIn(-1f, 1f), (position.y / h * 2f - 1f).coerceIn(-1f, 1f))
        scope.launch { touch.animateTo(target, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)) }
    }
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        aim(down.position)
        pressed?.let { scope.launch { it.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) } }
        var change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
        while (change != null && change.pressed) {
            aim(change.position)
            change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
        }
        scope.launch { touch.animateTo(Offset.Zero, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
        pressed?.let { scope.launch { it.animateTo(0f, spring(stiffness = Spring.StiffnessLow)) } }
    }
}

/** [enabled]면 기기 기울기를 받는 센서 상태를 만들고, 화면이 멈추거나 컴포지션을 떠나면 해제한다. */
@Composable
private fun rememberHoloSensor(enabled: Boolean): HoloSensor? {
    if (!enabled) return null
    val context = LocalContext.current
    val sensor = remember { HoloSensor() }
    // 가로 화면에서는 센서 축과 화면 축이 다르므로 화면 회전에 맞춰 돌린다. 회전을 직접 처리하는 앱도 있어 구성이 바뀔 때마다 다시 읽는다.
    val configuration = LocalConfiguration.current
    sensor.rotation = remember(context, configuration) { context.displayRotation() }
    LifecycleStartEffect(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val device = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (device != null) manager?.registerListener(sensor, device, SensorManager.SENSOR_DELAY_GAME)
        onStopOrDispose { manager?.unregisterListener(sensor) }
    }
    return sensor
}

/** 중력 방향을 -1..1 기울기로 바꿔 둔다. 떨림을 줄이려고 새 값을 조금씩 따라간다. */
private class HoloSensor : SensorEventListener {
    var value by mutableStateOf(Offset.Zero)
        private set

    @Volatile
    var rotation: Int = Surface.ROTATION_0

    override fun onSensorChanged(event: SensorEvent) {
        val tilt = screenTilt(event.values[0], event.values[1], rotation)
        val x = (-tilt.x / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        val y = ((tilt.y / SensorManager.GRAVITY_EARTH) - SENSOR_REST_Y).coerceIn(-1f, 1f)
        value = Offset(value.x + (x - value.x) * SENSOR_SMOOTH, value.y + (y - value.y) * SENSOR_SMOOTH)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/** 기기 기준 센서 값([x], [y])을 지금 화면 회전([rotation], `Surface.ROTATION_*`) 기준 축으로 돌린다. */
internal fun screenTilt(x: Float, y: Float, rotation: Int): Offset = when (rotation) {
    Surface.ROTATION_90 -> Offset(-y, x)
    Surface.ROTATION_180 -> Offset(-x, -y)
    Surface.ROTATION_270 -> Offset(y, -x)
    else -> Offset(x, y)
}

@Suppress("DEPRECATION")
private fun Context.displayRotation(): Int {
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // 창이 없는 Context에서는 display를 읽을 수 없으므로 기본 방향으로 둔다.
        runCatching { display }.getOrNull()
    } else {
        (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
    }
    return display?.rotation ?: Surface.ROTATION_0
}

/**
 * 무지개 띠, 반사광, 반짝이 세 겹으로 홀로그램 광택을 그린다. 셰이더는 크기가 바뀔 때만 만들고 기울기는 행렬로 옮겨 반영한다.
 */
private class HoloFoil {
    private val rainbowMatrix = Matrix()
    private val glareMatrix = Matrix()
    private var rainbow: ShaderBrush? = null
    private var glare: ShaderBrush? = null
    private var rainbowShader: Shader? = null
    private var glareShader: Shader? = null
    private var size = Size.Zero
    private val random = Random(SPARKLE_SEED)
    private val sparkX = FloatArray(SPARKLES) { random.nextFloat() }
    private val sparkY = FloatArray(SPARKLES) { random.nextFloat() }
    private val sparkPhase = FloatArray(SPARKLES) { random.nextFloat() * SPARKLE_PHASES }

    fun resize(newSize: Size) {
        if (newSize == size) return
        size = newSize
        val band = newSize.maxDimension * BAND_LENGTH
        val colors = RAINBOW.map { it.toArgb() }.toIntArray()
        rainbowShader =
            LinearGradient(0f, 0f, band, band * BAND_SLOPE, colors, null, Shader.TileMode.MIRROR).also { rainbow = ShaderBrush(it) }
        val glareColors = intArrayOf(Color.White.toArgb(), Color.White.copy(alpha = GLARE_MID).toArgb(), Color.Transparent.toArgb())
        glareShader = RadialGradient(0f, 0f, newSize.maxDimension * GLARE_RADIUS, glareColors, null, Shader.TileMode.CLAMP)
            .also { glare = ShaderBrush(it) }
    }

    fun draw(scope: DrawScope, tilt: Offset, level: Float) {
        val w = size.width
        val h = size.height
        // 기울기가 바뀌면 무지개 띠는 대각선을 따라 흐르고 반사광은 누른 쪽으로 따라간다.
        val shift = (tilt.x * RAINBOW_TRAVEL_X + tilt.y * RAINBOW_TRAVEL_Y) * size.maxDimension
        rainbowMatrix.setTranslate(shift, shift * BAND_SLOPE)
        rainbowShader?.setLocalMatrix(rainbowMatrix)
        glareMatrix.setTranslate(w * (HALF_F + tilt.x * GLARE_TRAVEL), h * (GLARE_TOP + tilt.y * GLARE_TRAVEL))
        glareShader?.setLocalMatrix(glareMatrix)
        rainbow?.let { scope.drawRect(it, alpha = level * RAINBOW_ALPHA, blendMode = BlendMode.Overlay) }
        rainbow?.let { scope.drawRect(it, alpha = level * RAINBOW_TINT, blendMode = BlendMode.Screen) }
        glare?.let { scope.drawRect(it, alpha = level * GLARE_ALPHA, blendMode = BlendMode.Screen) }
        val twinkle = (tilt.x + tilt.y) * SPARKLE_SPEED
        val dot = size.minDimension * SPARKLE_SIZE
        for (i in 0 until SPARKLES) {
            val s = max(0f, sin(sparkPhase[i] + twinkle))
            val glow = s * s * s * s
            if (glow > SPARKLE_MIN) {
                scope.drawCircle(Color.White, dot * (HALF_F + glow), Offset(sparkX[i] * w, sparkY[i] * h), alpha = glow * level)
            }
        }
    }
}

private val RAINBOW = listOf(
    Color(0xFFFF4D6D),
    Color(0xFFFFB347),
    Color(0xFFFFF275),
    Color(0xFF6BFFB8),
    Color(0xFF4DD8FF),
    Color(0xFF8C7BFF),
    Color(0xFFFF6BE0),
)
private const val HOLO_CAMERA = 12f
private const val REST_LEVEL = 0.45f
private const val SENSOR_REST_Y = 0.55f
private const val SENSOR_SMOOTH = 0.15f
private const val BAND_LENGTH = 0.38f
private const val BAND_SLOPE = 0.7f
private const val GLARE_RADIUS = 0.6f
private const val GLARE_MID = 0.35f
private const val GLARE_TRAVEL = 0.45f
private const val GLARE_TOP = 0.4f
private const val HALF_F = 0.5f
private const val RAINBOW_TRAVEL_X = 0.5f
private const val RAINBOW_TRAVEL_Y = 0.3f
private const val RAINBOW_ALPHA = 0.9f
private const val RAINBOW_TINT = 0.3f
private const val GLARE_ALPHA = 0.55f
private const val SPARKLES = 36
private const val SPARKLE_SEED = 11
private const val SPARKLE_PHASES = 6.2831855f
private const val SPARKLE_SPEED = 6f
private const val SPARKLE_SIZE = 0.01f
private const val SPARKLE_MIN = 0.05f
