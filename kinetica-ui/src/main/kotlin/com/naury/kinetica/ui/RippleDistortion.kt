package com.naury.kinetica.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * 누른 자리에서 물결이 동심원으로 퍼지며 요소의 내용이 물에 비친 것처럼 일렁인다.
 * Android 13(API 33)부터는 AGSL 셰이더로 내용을 실제로 굴절시키고, 그보다 낮은 버전에서는 밝은 원이 퍼지는 것으로 대신한다.
 * 연달아 누르면 물결이 겹치며, 동시에 최대 4개까지 보이고 그보다 많으면 가장 오래된 물결을 지운다.
 * 터치 이벤트를 소비하지 않으므로 클릭과 스크롤은 그대로 동작한다.
 * [enabled]가 false이거나 '애니메이션 삭제' 접근성 설정이 켜져 있으면 아무것도 하지 않는다.
 *
 * ```kotlin
 * Image(painter, null, Modifier.size(240.dp).rippleDistortion().clickable { open() })
 * ```
 *
 * @param strength 물결 마루에서 내용이 밀리는 최대 거리
 * @param wavelength 물결 한 겹의 간격
 * @param durationMs 물결 하나가 퍼져서 사라질 때까지의 시간(ms). 0 이하면 [IllegalArgumentException]을 던진다.
 * @param highlight 물결 마루에 더하는 빛의 색. 알파로 진하기를 정하며, 투명이면 빛을 더하지 않는다.
 */
@Composable
public fun Modifier.rippleDistortion(
    enabled: Boolean = true,
    strength: Dp = 4.dp,
    wavelength: Dp = 28.dp,
    durationMs: Int = 1400,
    highlight: Color = Color.White.copy(alpha = 0.35f),
): Modifier {
    require(durationMs > 0) { "durationMs는 0보다 커야 합니다." }
    if (!enabled || reducedMotion()) return this
    val waves = remember { RippleWaves() }
    val duration = durationMs / MS_PER_SECOND
    // 누를 때마다 컴포지션을 다시 하지 않도록 generation은 컴포지션이 아니라 이 코루틴에서 읽는다.
    LaunchedEffect(waves, duration) {
        snapshotFlow { waves.generation }.collect {
            do {
                withFrameNanos { waves.tick(it / NANOS_PER_SECOND) }
            } while (waves.activeCount(duration) > 0)
        }
    }
    val painter = remember { RipplePainter() }
    val base = this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            waves.add(down.position)
        }
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        base.graphicsLayer {
            val now = waves.now
            renderEffect = painter.effect(waves, now, duration, RippleLook(size, strength.toPx(), wavelength.toPx(), highlight))
        }
    } else {
        base.drawWithCache {
            val stroke = Stroke(wavelength.toPx() * RING_WIDTH)
            val gap = wavelength.toPx()
            onDrawWithContent {
                drawContent()
                val now = waves.now
                for (i in 0 until MAX_WAVES) {
                    val progress = waves.progress(i, now, duration)
                    if (progress !in 0f..1f) continue
                    val center = Offset(waves.x[i], waves.y[i])
                    val radius = progress * reachOf(center, size, gap)
                    val fade = (1f - progress) * (1f - progress)
                    for (ring in 0 until FALLBACK_RINGS) {
                        val r = radius - ring * gap
                        if (r >
                            0f
                        ) {
                            drawCircle(
                                highlight,
                                r,
                                center,
                                alpha = minOf(1f, highlight.alpha * FALLBACK_BOOST) * fade / (ring + 1),
                                style = stroke,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 동시에 보이는 물결들. 누른 시각은 다음 프레임 시각으로 정해서 터치 시각과 프레임 시계가 어긋나지 않게 한다.
 * 값은 그리기 단계에서 [now]를 읽어 프레임마다 재구성하지 않는다.
 */
internal class RippleWaves {
    val x = FloatArray(MAX_WAVES)
    val y = FloatArray(MAX_WAVES)
    private val start = FloatArray(MAX_WAVES) { Float.NEGATIVE_INFINITY }

    // 더한 순서. 빈 자리는 0이라 가장 먼저 채운다.
    private val serial = IntArray(MAX_WAVES)
    private var count = 0

    /** 마지막 프레임 시각(초). */
    var now by mutableFloatStateOf(0f)
        private set

    /** 물결을 더할 때마다 1씩 오른다. 멈춘 프레임 루프를 다시 돌리는 열쇠다. */
    var generation by mutableIntStateOf(0)
        private set

    /** [point]에서 새 물결을 시작한다. 자리가 없으면 가장 먼저 시작한 물결을 지운다. */
    fun add(point: Offset) {
        var slot = 0
        for (i in 1 until MAX_WAVES) {
            if (serial[i] < serial[slot]) slot = i
        }
        x[slot] = point.x
        y[slot] = point.y
        start[slot] = Float.NaN
        serial[slot] = ++count
        generation++
    }

    fun tick(seconds: Float) {
        for (i in 0 until MAX_WAVES) {
            if (start[i].isNaN()) start[i] = seconds
        }
        now = seconds
    }

    /** [i]번째 물결의 진행률. 아직 시작하지 않았으면 0, 끝났거나 비었으면 1보다 크다. */
    fun progress(i: Int, at: Float, duration: Float): Float = if (start[i].isNaN()) 0f else (at - start[i]) / duration

    fun activeCount(duration: Float): Int = (0 until MAX_WAVES).count { progress(it, now, duration) < 1f }
}

/** 셰이더에 넘길 그리기 값(px). */
private data class RippleLook(val size: Size, val strength: Float, val wavelength: Float, val highlight: Color)

/** 셰이더와 균일값 배열을 한 번 만들어 재사용한다. 물결이 없으면 효과를 끄고 레이어를 그대로 그린다. */
private class RipplePainter {
    private val waveUniform = FloatArray(MAX_WAVES * VEC4)
    private val sizeUniform = FloatArray(2)
    private val scalar = FloatArray(1)
    private val tint = FloatArray(VEC4)
    private var shader: Any? = null
    private var failed = false
    private var lastNow = Float.NaN
    private var lastLook: RippleLook? = null
    private var lastEffect: androidx.compose.ui.graphics.RenderEffect? = null

    /**
     * 지금 프레임의 효과. 같은 프레임 시각과 같은 값으로 레이어를 다시 그리면(부모 무효화 등) 만들어 둔 효과를 그대로 쓴다.
     * 물결이 움직이는 프레임마다 새로 만드는 비용은 남지만, 물결이 없으면 효과를 끄므로 그 비용은 물결이 보이는 동안만 든다.
     */
    fun effect(waves: RippleWaves, now: Float, duration: Float, look: RippleLook): androidx.compose.ui.graphics.RenderEffect? {
        if (now == lastNow && look == lastLook) return lastEffect
        lastNow = now
        lastLook = look
        lastEffect = compute(waves, now, duration, look)
        return lastEffect
    }

    private fun compute(waves: RippleWaves, now: Float, duration: Float, look: RippleLook): androidx.compose.ui.graphics.RenderEffect? {
        if (failed || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || look.size.minDimension <= 0f) return null
        var any = false
        for (i in 0 until MAX_WAVES) {
            val progress = waves.progress(i, now, duration)
            val live = progress in 0f..1f
            val center = Offset(waves.x[i], waves.y[i])
            val at = i * VEC4
            waveUniform[at + SLOT_X] = center.x
            waveUniform[at + SLOT_Y] = center.y
            waveUniform[at + SLOT_RADIUS] = if (live) progress * reachOf(center, look.size, look.wavelength) else 0f
            waveUniform[at + SLOT_AMPLITUDE] = if (live) (1f - progress) * (1f - progress) else 0f
            any = any || live
        }
        return if (any) build(look) else null
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun build(look: RippleLook): androidx.compose.ui.graphics.RenderEffect? {
        val runtime = (shader as? RuntimeShader) ?: runCatching { RuntimeShader(RIPPLE_SHADER) }.getOrNull()
        if (runtime == null) {
            // 셰이더를 지원하지 않는 GPU에서는 물결 없이 그린다.
            failed = true
            return null
        }
        shader = runtime
        sizeUniform[0] = look.size.width
        sizeUniform[1] = look.size.height
        runtime.setFloatUniform("size", sizeUniform)
        runtime.setFloatUniform("waves", waveUniform)
        scalar[0] = look.strength
        runtime.setFloatUniform("strength", scalar)
        scalar[0] = look.wavelength
        runtime.setFloatUniform("wavelength", scalar)
        tint[SLOT_X] = look.highlight.red
        tint[SLOT_Y] = look.highlight.green
        tint[SLOT_RADIUS] = look.highlight.blue
        tint[SLOT_AMPLITUDE] = look.highlight.alpha
        runtime.setFloatUniform("tint", tint)
        // RenderEffect는 만들 때의 균일값을 복사해 두므로 물결이 움직이는 동안에는 프레임마다 새로 만들어야 한다.
        return RenderEffect.createRuntimeShaderEffect(runtime, "content").asComposeRenderEffect()
    }
}

/** 누른 점에서 가장 먼 모서리까지 물결이 넘어가도록 퍼질 거리. */
private fun reachOf(center: Offset, size: Size, wavelength: Float): Float {
    val dx = maxOf(center.x, size.width - center.x)
    val dy = maxOf(center.y, size.height - center.y)
    return hypot(dx, dy) + wavelength * 2f
}

// 물결 마루 앞뒤로 사인파 몇 겹이 가우시안 모양으로 줄어들며 퍼진다. 마루의 기울기에 따라 빛을 더해 수면처럼 보이게 한다.
private const val RIPPLE_SHADER = """
uniform shader content;
uniform float2 size;
uniform float4 waves[4];
uniform float strength;
uniform float wavelength;
uniform float4 tint;

half4 main(float2 p) {
    float2 shift = float2(0.0);
    float light = 0.0;
    for (int i = 0; i < 4; i++) {
        float4 w = waves[i];
        float2 d = p - w.xy;
        float dist = length(d);
        float x = (dist - w.z) / wavelength;
        float envelope = exp(-x * x * 1.2) * w.w;
        float wave = sin(x * 6.2831853) * envelope;
        shift += d / max(dist, 1.0) * wave * strength;
        light += max(cos(x * 6.2831853), 0.0) * envelope;
    }
    float2 source = clamp(p - shift, float2(0.5), size - 0.5);
    half4 color = content.eval(source);
    color.rgb += half3(tint.rgb * clamp(light, 0.0, 1.0) * tint.a) * color.a;
    return color;
}
"""

private const val MAX_WAVES = 4
private const val VEC4 = 4
private const val SLOT_X = 0
private const val SLOT_Y = 1
private const val SLOT_RADIUS = 2
private const val SLOT_AMPLITUDE = 3
private const val FALLBACK_RINGS = 3
private const val RING_WIDTH = 0.12f
private const val FALLBACK_BOOST = 2f
private const val MS_PER_SECOND = 1_000f
private const val NANOS_PER_SECOND = 1_000_000_000f
