package com.naury.kinetica.ui

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.sin

/**
 * [MeshGradientBackground]의 색 구성. [base] 위로 [colors]의 색 덩어리가 하나씩 떠다닌다.
 *
 * [colors]가 2개보다 적거나 6개보다 많으면 [IllegalArgumentException]을 던진다. 색의 알파는 무시한다.
 *
 * @property base 색 덩어리 사이로 비치는 바탕색
 * @property colors 떠다니는 색 덩어리의 색(2..6개)
 */
@Immutable
public data class MeshPalette(val base: Color, val colors: List<Color>) {
    init {
        require(colors.size in MIN_BLOBS..MAX_BLOBS) { "colors는 2개 이상 6개 이하여야 합니다." }
    }
}

/** [MeshGradientBackground]용 색 구성 프리셋. */
public object MeshGradients {
    /** 해 질 녘처럼 주황, 분홍, 보라가 섞인다. */
    @JvmField
    public val Sunset: MeshPalette = MeshPalette(
        base = Color(0xFF3B1E54),
        colors = listOf(Color(0xFFFF7A59), Color(0xFFFF4F8B), Color(0xFFFFC15E), Color(0xFF9B5DE5), Color(0xFFE63E6D)),
    )

    /** 맑은 바다처럼 파랑, 청록, 하늘색이 섞인다. */
    @JvmField
    public val Ocean: MeshPalette = MeshPalette(
        base = Color(0xFF0B2545),
        colors = listOf(Color(0xFF1E88E5), Color(0xFF00C2D1), Color(0xFF7FDBFF), Color(0xFF3A47D5), Color(0xFF00A889)),
    )

    /** 오로라처럼 어두운 남색 위로 초록과 보라가 흐른다. */
    @JvmField
    public val Aurora: MeshPalette = MeshPalette(
        base = Color(0xFF07122B),
        colors = listOf(Color(0xFF2EE59D), Color(0xFF1FB5C8), Color(0xFF7B61FF), Color(0xFFB04FE0), Color(0xFF0E6B8C)),
    )

    /** 사탕처럼 밝은 분홍, 라일락, 민트, 복숭아색이 섞인다. 밝은 테마에 어울린다. */
    @JvmField
    public val Candy: MeshPalette = MeshPalette(
        base = Color(0xFFFFF1F6),
        colors = listOf(Color(0xFFFF9EC7), Color(0xFFC9A7FF), Color(0xFF9EF0D6), Color(0xFFFFC8A2), Color(0xFF8FD3FF)),
    )

    /** 깊은 밤처럼 남색과 보라가 은은하게 섞인다. 다크 테마에 어울린다. */
    @JvmField
    public val Midnight: MeshPalette = MeshPalette(
        base = Color(0xFF05060F),
        colors = listOf(Color(0xFF1B1F5E), Color(0xFF4B1D6B), Color(0xFF0F3B66), Color(0xFF7A2A7F), Color(0xFF14143A)),
    )
}

/**
 * Apple 배경화면처럼 큰 색 덩어리가 천천히 떠다니며 섞이는 움직이는 배경을 그린다.
 * Android 13(API 33)부터는 AGSL 셰이더로 부드럽게 섞고, 그보다 낮은 버전에서는 원형 그라디언트를 겹쳐 그린다.
 * '애니메이션 삭제' 접근성 설정이 켜져 있거나 [speed]가 0이면 멈춘 그림으로 보인다.
 * 글래스 [KineticaScaffold]의 `background`에 두면 그 스캐폴드에 다이얼로그나 시트가 열려 있는 동안 멈췄다가 닫히면 이어서 움직인다.
 * 그림은 초당 30번 바뀐다. 이 배경을 비추는 글래스가 바뀔 때마다 블러를 다시 하므로 화면 주사율만큼 바꾸지 않는다.
 *
 * 부모를 가득 채우며, 크기를 제한하려면 [modifier]에 크기를 지정한다. 장식이므로 접근성 트리에 나타나지 않는다.
 *
 * ```kotlin
 * KineticaScaffold(background = { MeshGradientBackground(palette = MeshGradients.Aurora) }) { padding -> ... }
 * ```
 *
 * @param palette 색 구성
 * @param speed 움직이는 속도 배율. 1이면 기본 속도이고 0이면 멈춘다. 음수면 [IllegalArgumentException]을 던진다.
 */
@Composable
public fun MeshGradientBackground(modifier: Modifier = Modifier, palette: MeshPalette = MeshGradients.Aurora, speed: Float = 1f) {
    require(speed >= 0f) { "speed는 0 이상이어야 합니다." }
    val still = reducedMotion() || speed == 0f
    val clock = rememberLoopClock(running = !still && !shouldPauseBackground(), frameRate = BACKGROUND_FRAME_RATE)
    val painter = remember(palette) { MeshPainter(palette) }
    val time = remember { MeshTime() }
    Spacer(
        modifier
            .fillMaxSize()
            .loopVisibility(clock)
            .drawBehind { painter.draw(this, time.at(clock.seconds, speed)) },
    )
}

/** 그림에 쓰는 시간. 속도가 바뀌면 바뀐 시점까지 흐른 시간을 기준으로 이어 가서 그림이 튀지 않는다. */
private class MeshTime {
    private var base = STILL_TIME
    private var since = 0f
    private var speed = Float.NaN

    fun at(seconds: Float, newSpeed: Float): Float {
        if (speed != newSpeed) {
            if (!speed.isNaN()) base += (seconds - since) * speed
            since = seconds
            speed = newSpeed
        }
        return base + (seconds - since) * speed
    }
}

/** 색 덩어리 위치를 계산하고 그린다. 프레임마다 객체를 만들지 않도록 셰이더와 배열을 재사용한다. */
private class MeshPainter(palette: MeshPalette) {
    private val count = palette.colors.size
    private val base = palette.base
    private val colors = palette.colors
    private val centers = FloatArray(MAX_BLOBS * 2)
    private val sizeUniform = FloatArray(2)
    private val radiusUniform = FloatArray(1)
    private val timeUniform = FloatArray(1)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shader: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) createShader() else null
    private val blobPaints = Array(count) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val matrices = Array(count) { Matrix() }
    private var radius = -1f

    fun draw(scope: DrawScope, time: Float) {
        val width = scope.size.width
        val height = scope.size.height
        if (width <= 0f || height <= 0f) return
        val r = maxOf(width, height) * BLOB_RADIUS
        for (i in 0 until count) {
            val orbit = MOTION[i]
            centers[i * 2] = width * (HALF + ORBIT * sin(time * orbit.speedX + orbit.phaseX))
            centers[i * 2 + 1] = height * (HALF + ORBIT * cos(time * orbit.speedY + orbit.phaseY))
        }
        scope.drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            // 소프트웨어 캔버스(비트맵으로 화면 캡처 등)는 RuntimeShader를 그리지 못하고 예외를 던진다.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader is RuntimeShader && native.isHardwareAccelerated) {
                updateShader(shader, width, height, r, time)
                native.drawRect(0f, 0f, width, height, paint)
            } else {
                drawBlobs(native, width, height, r)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun createShader(): RuntimeShader? {
        // 셰이더를 만들 수 없으면 원형 그라디언트로 그린다.
        val created = runCatching { RuntimeShader(MESH_SHADER) }.getOrNull() ?: return null
        val rgba = colors.flatMap { listOf(it.red, it.green, it.blue, 1f) }.toFloatArray().copyOf(MAX_BLOBS * RGBA)
        created.setFloatUniform("colors", rgba)
        created.setFloatUniform("base", floatArrayOf(base.red, base.green, base.blue, 1f))
        created.setFloatUniform("count", floatArrayOf(count.toFloat()))
        paint.shader = created
        return created
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun updateShader(shader: RuntimeShader, width: Float, height: Float, r: Float, time: Float) {
        // 배열판만 쓴다. 값 여러 개를 따로 넘기는 판은 Robolectric에서 무시되어 시험할 수 없다.
        sizeUniform[0] = width
        sizeUniform[1] = height
        radiusUniform[0] = r
        timeUniform[0] = time
        shader.setFloatUniform("size", sizeUniform)
        shader.setFloatUniform("centers", centers)
        shader.setFloatUniform("radius", radiusUniform)
        shader.setFloatUniform("time", timeUniform)
    }

    private fun drawBlobs(native: android.graphics.Canvas, width: Float, height: Float, r: Float) {
        if (radius != r) {
            radius = r
            colors.forEachIndexed { i, color ->
                val argb = color.copy(alpha = 1f).toArgb()
                val stops = FALLBACK_ALPHAS.map { a -> color.copy(alpha = a).toArgb() }.toIntArray()
                stops[0] = argb
                blobPaints[i].shader = RadialGradient(0f, 0f, r, stops, FALLBACK_STOPS, Shader.TileMode.CLAMP)
            }
        }
        native.drawColor(base.toArgb())
        for (i in 0 until count) {
            matrices[i].setTranslate(centers[i * 2], centers[i * 2 + 1])
            blobPaints[i].shader?.setLocalMatrix(matrices[i])
            native.drawRect(0f, 0f, width, height, blobPaints[i])
        }
    }
}

/** 색 덩어리 하나의 궤도. 덩어리끼리 겹쳐 다니지 않도록 속도와 위상을 서로 어긋나게 골랐다. */
private class Orbit(val speedX: Float, val speedY: Float, val phaseX: Float, val phaseY: Float)

private val MOTION: Array<Orbit> = arrayOf(
    Orbit(speedX = 0.21f, speedY = 0.17f, phaseX = 0.0f, phaseY = 1.2f),
    Orbit(speedX = 0.13f, speedY = 0.23f, phaseX = 2.1f, phaseY = 4.0f),
    Orbit(speedX = 0.19f, speedY = 0.11f, phaseX = 4.2f, phaseY = 2.6f),
    Orbit(speedX = 0.09f, speedY = 0.15f, phaseX = 1.0f, phaseY = 5.3f),
    Orbit(speedX = 0.16f, speedY = 0.07f, phaseX = 5.5f, phaseY = 0.4f),
    Orbit(speedX = 0.11f, speedY = 0.19f, phaseX = 3.3f, phaseY = 3.1f),
)

// 각 덩어리를 가우시안으로 섞고 좌표를 조금 휘어 경계가 둥글게 흐르게 한다. 띠가 생기지 않도록 노이즈를 아주 약하게 더한다.
private const val MESH_SHADER = """
uniform float2 size;
uniform float4 base;
uniform float4 colors[6];
uniform float2 centers[6];
uniform float count;
uniform float radius;
uniform float time;

float4 main(float2 p) {
    float2 warp = float2(sin(p.y / size.y * 3.0 + time * 0.3), cos(p.x / size.x * 3.0 + time * 0.25)) * radius * 0.08;
    float2 q = p + warp;
    float3 acc = base.rgb * 0.3;
    float total = 0.3;
    for (int i = 0; i < 6; i++) {
        float on = step(float(i) + 0.5, count);
        float d = distance(q, centers[i]) / radius;
        float k = exp(-d * d * 2.2) * on;
        acc += colors[i].rgb * k;
        total += k;
    }
    float3 color = acc / total;
    float n = fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    color += (n - 0.5) / 255.0;
    return float4(color, 1.0);
}
"""

private val FALLBACK_STOPS = floatArrayOf(0f, 0.35f, 0.7f, 1f)
private val FALLBACK_ALPHAS = floatArrayOf(1f, 0.6f, 0.2f, 0f).toList()
private const val MIN_BLOBS = 2
private const val MAX_BLOBS = 6
private const val RGBA = 4
private const val STILL_TIME = 7f
private const val HALF = 0.5f
private const val ORBIT = 0.38f
private const val BLOB_RADIUS = 0.62f
