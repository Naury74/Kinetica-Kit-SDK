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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 움직임을 줄여야 하는지. 이 키트의 컴포넌트가 반복 애니메이션, 관성, 기울기 효과를 멈출 때 쓰는 기준과 같다.
 * [LocalKineticaReducedMotion]에 값이 있으면 그 값을, 없으면 시스템의 '애니메이션 삭제' 접근성 설정을 따른다.
 * 호스트가 직접 만든 애니메이션도 이 값을 따르면 키트와 같이 멈춘다.
 */
@Composable
public fun isReducedMotion(): Boolean {
    LocalKineticaReducedMotion.current?.let { return it }
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * 움직임을 줄일지 직접 정한다. 기본값 null이면 시스템의 '애니메이션 삭제' 설정을 따른다.
 * 앱 설정에 '움직임 줄이기'가 따로 있으면 화면 루트에서 그 값을 넣는다. [isReducedMotion]과 이 키트의 모든 컴포넌트가 따른다.
 */
public val LocalKineticaReducedMotion: ProvidableCompositionLocal<Boolean?> = staticCompositionLocalOf { null }

@Composable
internal fun reducedMotion(): Boolean = isReducedMotion()

/**
 * Siri나 Apple Intelligence처럼 가장자리를 따라 색이 회전하는 글로우 테두리를 그린다. 바깥쪽으로 [glow]만큼 은은하게 번진다.
 * 화면 전체 가장자리에 붙이면 음성 입력 중임을 알리는 표시로 쓸 수 있다. '애니메이션 삭제' 접근성 설정이 켜져 있으면 회전하지 않고,
 * 요소가 창 밖으로 스크롤되어 나가 있는 동안에도 멈춘다. 테두리는 콘텐츠 위에 그리므로 [shape]을 요소의 모양과 맞춘다.
 *
 * 번짐은 테두리 안팎으로 함께 퍼진다. 부모가 `clip`하는 요소(잘라 그리는 카드, `clipToBounds`, 스크롤 영역 등) 안에 두면 바깥 번짐이
 * 잘리고 안쪽 번짐만 남아 얼룩처럼 보이므로, 자르지 않는 부모 안에 두거나 사방에 [glow]만큼 여백을 둔다.
 *
 * 켜고 끌 때 서서히 나타나게 하려면 [intensity]를 애니메이션한다. 매 프레임 바뀌는 값이면 그리기 단계에서 읽는 람다 오버로드를 쓴다.
 *
 * @param colors 테두리 색. 두 개 미만이면 [IllegalArgumentException]을 던진다.
 * @param width 테두리 두께
 * @param glow 테두리 바깥으로 번지는 블러 반경
 * @param periodMs 한 바퀴 도는 시간(ms). 0 이하면 [IllegalArgumentException]을 던진다.
 * @param intensity 진하기(0..1). 0이면 그리지 않고 회전도 멈춘다. 범위를 벗어난 값은 0..1로 맞춘다.
 */
@Composable
public fun Modifier.glowBorder(
    shape: Shape = RectangleShape,
    colors: List<Color> = GlowColors,
    width: Dp = 3.dp,
    glow: Dp = 14.dp,
    periodMs: Int = 3_000,
    intensity: Float = 1f,
): Modifier {
    val level = intensity.coerceIn(0f, 1f)
    return drawGlowBorder(GlowSpec(shape, colors, width, glow, periodMs), running = level > 0f) { level }
}

/**
 * 진하기를 그리기 단계에서 읽는 [glowBorder]. 켜고 끌 때 `animateFloatAsState`의 값을 넘겨도 다시 구성하지 않고 그리기만 다시 한다.
 *
 * ```kotlin
 * val level by animateFloatAsState(if (listening) 1f else 0f, label = "glow")
 * Box(Modifier.glowBorder({ level }, RoundedCornerShape(24.dp)))
 * ```
 *
 * @param intensity 진하기(0..1)를 돌려주는 함수. 범위를 벗어난 값은 0..1로 맞춘다. 0이면 그리지 않는다.
 *   회전 시계는 늘 돌므로, 오래 꺼 둘 때는 수정자를 빼거나 Float 오버로드에 0을 넘긴다.
 * @see glowBorder 나머지 파라미터의 뜻과 실패 조건
 */
@Composable
public fun Modifier.glowBorder(
    intensity: () -> Float,
    shape: Shape = RectangleShape,
    colors: List<Color> = GlowColors,
    width: Dp = 3.dp,
    glow: Dp = 14.dp,
    periodMs: Int = 3_000,
): Modifier = drawGlowBorder(GlowSpec(shape, colors, width, glow, periodMs), running = true, intensity)

/** [glowBorder]의 모양 설정 */
private class GlowSpec(val shape: Shape, val colors: List<Color>, val width: Dp, val glow: Dp, val periodMs: Int)

@Composable
private fun Modifier.drawGlowBorder(spec: GlowSpec, running: Boolean, intensity: () -> Float): Modifier {
    require(spec.colors.size >= 2) { "colors는 두 개 이상이어야 합니다." }
    require(spec.periodMs > 0) { "periodMs는 0보다 커야 합니다." }
    val clock = rememberLoopClock(running = running && !reducedMotion())
    val colors = spec.colors
    val argb = remember(colors) { (colors + colors.first()).map { it.toArgb() }.toIntArray() }
    val brush = remember(argb) { GlowPaints(argb) }
    return this
        .loopVisibility(clock)
        .drawWithCache {
            val path = spec.shape.createOutline(size, layoutDirection, this).asPath().asAndroidPath()
            val stroke = spec.width.toPx()
            val blur = spec.glow.toPx()
            onDrawWithContent {
                drawContent()
                val level = intensity().coerceIn(0f, 1f)
                if (level <= 0f) return@onDrawWithContent
                val angle = (clock.seconds * MS_PER_SECOND / spec.periodMs % 1f) * FULL_TURN
                brush.update(size.width, size.height, stroke, blur, angle)
                brush.level(level)
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawPath(path, brush.glow)
                    canvas.nativeCanvas.drawPath(path, brush.line)
                }
            }
        }
}

/** 글로우 테두리용 Paint. 크기가 바뀔 때만 SweepGradient를 새로 만들고, 회전할 때는 행렬만 바꾼다. */
private class GlowPaints(private val colors: IntArray) {
    val glow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        alpha = GLOW_ALPHA
    }
    val line = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE }
    private val matrix = Matrix()
    private var shader: SweepGradient? = null
    private var size = 0f to 0f
    private var blur = -1f

    fun update(width: Float, height: Float, stroke: Float, glowPx: Float, angle: Float) {
        if (shader == null || size != width to height) {
            size = width to height
            shader = SweepGradient(width / 2, height / 2, colors, null)
            glow.shader = shader
            line.shader = shader
        }
        if (blur != glowPx) {
            blur = glowPx
            glow.maskFilter = if (glowPx > 0f) BlurMaskFilter(glowPx, BlurMaskFilter.Blur.NORMAL) else null
        }
        glow.strokeWidth = stroke + glowPx
        line.strokeWidth = stroke
        matrix.setRotate(angle, width / 2, height / 2)
        shader?.setLocalMatrix(matrix)
    }

    /** 진하기([level] 0..1)를 두 Paint의 불투명도에 반영한다. */
    fun level(level: Float) {
        glow.alpha = (GLOW_ALPHA * level).roundToInt()
        line.alpha = (OPAQUE * level).roundToInt()
    }
}

/** [glowBorder]의 기본 색. Siri와 비슷한 색 조합이다. */
public val GlowColors: List<Color> = listOf(Color(0xFF7B61FF), Color(0xFFFF5CAA), Color(0xFFFF9F43), Color(0xFF2ED3FF))

/**
 * 누르면 젤리처럼 납작해졌다가 손을 떼면 출렁이며 돌아온다. 터치 이벤트를 소비하지 않으므로 클릭과 스크롤은 그대로 동작한다.
 *
 * @param amount 납작해지는 정도(0..0.3). 범위를 벗어나면 [IllegalArgumentException]을 던진다.
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
 * 자석 버튼 효과. 손가락을 대고 움직이면 요소가 손가락 쪽으로 끌려가고, 손을 떼면 튕기며 제자리로 돌아온다.
 * 터치 이벤트를 소비하지 않으며, 목록 안에서 부모가 스크롤을 시작하면 끌려가기를 멈추고 제자리로 돌아온다.
 *
 * @param strength 손가락이 중심에서 떨어진 거리에 곱할 비율(0..1). 범위를 벗어나면 [IllegalArgumentException]을 던진다.
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
                // Final 단계에서 보면 부모 스크롤이 이동을 소비했는지 알 수 있다. 스크롤이 시작되면 끌려가기를 멈춘다.
                var change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull()
                while (change != null && change.pressed && !change.isConsumed) {
                    val target = (change.position - Offset(size.width / 2f, size.height / 2f)) * strength
                    scope.launch { pull.snapTo(target) }
                    change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull()
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
 * Apple TV 포스터처럼 기기를 기울이면 요소가 3D로 따라 기울고, 빛 반사가 반대쪽으로 흐른다.
 * 중력 센서(없으면 가속도 센서)를 쓰며, 컴포지션에 있고 화면이 STARTED 이상인 동안에만 센서 이벤트를 받는다.
 * 센서가 없거나 '애니메이션 삭제' 접근성 설정이 켜져 있으면 움직이지 않는다.
 *
 * @param maxDegrees 최대 기울기 각도(도)
 * @param shine 빛 반사의 진하기(0..1). 0이면 반사를 그리지 않는다.
 */
@Composable
public fun Modifier.motionTilt(maxDegrees: Float = 10f, shine: Float = 0.25f): Modifier {
    val context = LocalContext.current
    val still = reducedMotion()
    val tilt = remember { TiltState() }
    // 앱이 백그라운드로 가면 센서를 놓아 배터리를 쓰지 않는다.
    LifecycleStartEffect(context, still) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!still && sensor != null) manager?.registerListener(tilt, sensor, SensorManager.SENSOR_DELAY_GAME)
        onStopOrDispose { manager?.unregisterListener(tilt) }
    }
    return this
        .graphicsLayer {
            rotationY = tilt.value.x * maxDegrees
            rotationX = -tilt.value.y * maxDegrees
            cameraDistance = CAMERA * density
        }
        .drawWithCache {
            // 원점에 만든 붓을 옮겨 그려서 기울기가 바뀔 때마다 그라디언트를 새로 만들지 않는다.
            val radius = size.maxDimension * SHINE_RADIUS
            val brush = Brush.radialGradient(listOf(Color.White.copy(alpha = shine), Color.Transparent), Offset.Zero, radius)
            onDrawWithContent {
                drawContent()
                if (shine > 0f) {
                    // 빛 반사는 기울어진 반대쪽으로 이동한다.
                    val t = tilt.value
                    val center = Offset(size.width * (HALF - t.x * SHINE_TRAVEL), size.height * (SHINE_TOP + t.y * SHINE_TRAVEL))
                    translate(center.x, center.y) { drawRect(brush, Offset(-center.x, -center.y), size) }
                }
            }
        }
}

/** 중력 센서 값을 -1..1 기울기로 바꿔 보관한다. 값은 그리기 단계에서 읽는다. */
private class TiltState : SensorEventListener {
    var value by mutableStateOf(Offset.Zero)
        private set

    override fun onSensorChanged(event: SensorEvent) {
        // 중력 방향(x는 좌우, y는 위아래)을 -1..1로 정규화하고, 떨림을 줄이려고 조금씩 따라가게 한다.
        val x = (event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        val y = ((event.values[1] / SensorManager.GRAVITY_EARTH) - REST_Y).coerceIn(-1f, 1f)
        value = Offset(value.x + (x - value.x) * SMOOTH, value.y + (y - value.y) * SMOOTH)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/** 누르고 떼는 순간만 관찰하고 이벤트는 소비하지 않는다. */
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
private const val MS_PER_SECOND = 1_000f
private const val GLOW_ALPHA = 150
private const val OPAQUE = 255
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
