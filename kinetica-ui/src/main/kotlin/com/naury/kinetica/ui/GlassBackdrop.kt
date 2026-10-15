package com.naury.kinetica.ui

import android.graphics.BlurMaskFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.random.Random

/**
 * 유리 표면이 비춰 볼 뒤 화면. [glassBackdrop]을 붙인 요소의 그림을 기록해 두고, [glass] 표면이 자기 자리만큼 흐려서 그린다.
 *
 * 유리 표면은 뒤 화면 요소 밖(형제·위층)에 둔다. [GlassScaffold]는 이를 알아서 맞춘다: 막대·팝업은 화면 전체를,
 * 내용 안의 유리는 바탕([GlassScaffold]의 `background`)만 비춘다.
 */
@Stable
public class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
    internal var size by mutableStateOf(IntSize.Zero)

    /** 지금 뒤 화면을 기록하는 중인지. 잘못해 이 뒤 화면 안에 둔 유리가 자기 자신을 담지 않도록(그리기 순환) 이때는 그리지 않는다. */
    internal var recording: Boolean = false
}

/** 이 화면에서 쓸 [GlassBackdrop]. */
@Composable
public fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** 가장 가까운 [GlassScaffold]·[ProvideGlassBackdrop]의 뒤 화면. 없으면 `null`이며 유리는 [GlassStyle.fallback] 색으로 보인다. */
public val LocalGlassBackdrop: androidx.compose.runtime.ProvidableCompositionLocal<GlassBackdrop?> = staticCompositionLocalOf { null }

/**
 * 이 요소의 그림을 [backdrop]에 기록한다. 화면에는 평소처럼 그린다.
 *
 * 이 [backdrop]을 비추는 유리는 이 요소 **밖**에 둔다. 안에 두면 기록이 자기 자신을 담게 되므로 그리지 않는다.
 * 카드 안 사진 위의 유리처럼 안쪽에 유리가 필요하면, 그 사진에 따로 [glassBackdrop]을 붙이고 유리에 그 뒤 화면을 넘긴다.
 */
public fun Modifier.glassBackdrop(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned {
        backdrop.origin = it.positionInRoot()
        backdrop.size = it.size
    }
    .drawWithContent {
        backdrop.recording = true
        try {
            backdrop.layer.record { this@drawWithContent.drawContent() }
        } finally {
            backdrop.recording = false
        }
        drawLayer(backdrop.layer)
    }

/**
 * 유리 표면을 그린다: [shape] 안에 [backdrop]의 같은 자리를 [style]대로 흐리고 색을 덮은 뒤, 빛 테두리·그림자·알갱이를 더한다.
 * 내용은 그 위에 평소처럼 그린다. [backdrop]이 `null`이거나 API 31 미만이면 [GlassStyle.fallback] 색을 칠한다.
 *
 * @param intensity 0..1. 흐림·색 덮기·그림자를 함께 줄인다(팝업 배경이 서서히 흐려질 때 쓴다).
 */
@Composable
public fun Modifier.glass(
    style: GlassStyle,
    shape: Shape = RectangleShape,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    intensity: Float = 1f,
): Modifier {
    val blurLayer = rememberGraphicsLayer()
    val position = remember { PositionHolder() }
    val effects = remember { EffectCache() }
    return this
        .onGloballyPositioned { position.offset = it.positionInRoot() }
        .drawBehind {
            if (backdrop?.recording == true) return@drawBehind
            val outline = shape.createOutline(size, layoutDirection, this)
            val level = intensity.coerceIn(0f, 1f)
            drawOuterShadow(outline, style, level, effects)
            val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            clipOutline(outline) {
                if (canBlur && backdrop != null) {
                    drawBackdrop(backdrop, Sampler(blurLayer, effects, position.offset), style, outline, level)
                    drawRect(style.tint.copy(alpha = style.tint.alpha * level))
                } else {
                    drawRect(style.fallback.copy(alpha = style.fallback.alpha * level))
                }
                if (style.grain > 0f) drawRect(GrainBrush.value, alpha = style.grain * level)
            }
            drawEdgeLight(outline, style, level)
        }
}

private class PositionHolder {
    var offset: Offset = Offset.Zero
}

/** 매 프레임 효과를 새로 만들지 않도록 마지막 값을 둔다. */
private class EffectCache {
    var key: Any? = null
    var effect: androidx.compose.ui.graphics.RenderEffect? = null

    // 그림자 붓과 모양은 크기·반지름이 바뀔 때만 새로 만든다(프레임마다 만들면 흐림 무늬를 다시 굽는다).
    val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    var shadowRadius = -1f
    var outlineKey: Any? = null
    var path: Path = Path()
}

/** 기록한 영역: 크기, 가장자리 여유, 모서리 반지름(px). */
private class Region(val size: IntSize, val pad: Float, val corner: Float)

/** 유리 하나가 뒤 화면을 옮겨 그릴 때 쓰는 층·효과 캐시와 화면 위치. */
private class Sampler(val layer: GraphicsLayer, val cache: EffectCache, val offset: Offset)

private fun DrawScope.drawBackdrop(backdrop: GlassBackdrop, sampler: Sampler, style: GlassStyle, outline: Outline, level: Float) {
    val layer = sampler.layer
    val cache = sampler.cache
    val offset = sampler.offset
    val blurPx = style.blur.toPx() * level
    // 흐림이 가장자리 밖을 끌어오므로 그만큼 넓게 기록한다. 굴절도 바깥을 끌어온다.
    val pad = ceil(blurPx * 2f + style.refraction * REFRACTION_DP * density).toInt()
    val recordSize = IntSize(ceil(size.width).toInt() + pad * 2, ceil(size.height).toInt() + pad * 2)
    if (recordSize.width <= 0 || recordSize.height <= 0) return
    val relative = offset - backdrop.origin
    val key =
        listOf(
            blurPx,
            style.saturation * level + (1 - level),
            style.brightness * level,
            style.refraction * level,
            recordSize,
            cornerOf(outline),
        )
    if (cache.key != key && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cache.key = key
        cache.effect = glassEffect(blurPx, style, level, Region(recordSize, pad.toFloat(), cornerOf(outline)))
    }
    layer.renderEffect = cache.effect
    layer.record(recordSize) {
        translate(-relative.x + pad, -relative.y + pad) { drawLayer(backdrop.layer) }
    }
    translate(-pad.toFloat(), -pad.toFloat()) { drawLayer(layer) }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun glassEffect(blurPx: Float, style: GlassStyle, level: Float, region: Region): androidx.compose.ui.graphics.RenderEffect? {
    val saturation = 1f + (style.saturation - 1f) * level
    val brightness = style.brightness * level * BRIGHTNESS_SCALE
    val color = ColorMatrix().apply { setSaturation(saturation) }.also {
        it.postConcat(
            ColorMatrix(
                floatArrayOf(1f, 0f, 0f, 0f, brightness, 0f, 1f, 0f, 0f, brightness, 0f, 0f, 1f, 0f, brightness, 0f, 0f, 0f, 1f, 0f),
            ),
        )
    }
    var effect: RenderEffect = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(color))
    if (blurPx >
        MIN_PX
    ) {
        effect = RenderEffect.createChainEffect(effect, RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP))
    }
    val refraction = style.refraction * level
    if (refraction > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // 기기 GPU가 셰이더를 못 쓰면 굴절 없이 그린다.
        runCatching { refractionEffect(region.size, region.pad, region.corner, refraction) }.getOrNull()?.let {
            effect =
                RenderEffect.createChainEffect(effect, it)
        }
    }
    return effect.asComposeRenderEffect()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun refractionEffect(size: IntSize, pad: Float, corner: Float, strength: Float): RenderEffect {
    val shader = RuntimeShader(REFRACTION_SHADER)
    shader.setFloatUniform("size", size.width - pad * 2, size.height - pad * 2)
    shader.setFloatUniform("pad", pad)
    shader.setFloatUniform("radius", corner)
    shader.setFloatUniform("strength", strength * pad.coerceAtMost(MAX_REFRACTION_PX))
    return RenderEffect.createRuntimeShaderEffect(shader, "content")
}

/** 둥근 사각형 가장자리 가까이에서 바깥쪽 화면을 끌어와 렌즈처럼 휘게 한다. */
private const val REFRACTION_SHADER = """
uniform shader content;
uniform float2 size;
uniform float pad;
uniform float radius;
uniform float strength;

float roundRect(float2 p, float2 extent, float r) {
    float2 q = abs(p) - extent + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 coord) {
    float2 p = coord - pad - size * 0.5;
    float d = roundRect(p, size * 0.5, min(radius, min(size.x, size.y) * 0.5));
    float band = max(min(size.x, size.y) * 0.22, 1.0);
    float edge = clamp(1.0 + d / band, 0.0, 1.0);
    float2 dir = normalize(p + float2(0.0001, 0.0001));
    return content.eval(coord + dir * strength * edge * edge);
}
"""

private fun cornerOf(outline: Outline): Float = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f

private fun DrawScope.clipOutline(outline: Outline, block: DrawScope.() -> Unit) {
    clipPath(outline.toPath(), block = block)
}

private fun Outline.toPath(): Path = when (this) {
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Generic -> path
}

/** 위·왼쪽이 밝고 아래·오른쪽으로 옅어지는 빛 테두리. */
private fun DrawScope.drawEdgeLight(outline: Outline, style: GlassStyle, level: Float) {
    if (style.edgeLight.alpha == 0f || style.edgeWidth.value == 0f) return
    val light = style.edgeLight.copy(alpha = style.edgeLight.alpha * level)
    val brush = Brush.linearGradient(
        0f to light,
        EDGE_FADE_AT to light.copy(alpha = light.alpha * EDGE_FADE),
        1f to light.copy(alpha = light.alpha * 0.6f),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    val stroke = style.edgeWidth.toPx()
    val path = outline.toPath()
    clipPath(path) { drawPath(path, brush, style = Stroke(stroke * 2)) }
}

/** 표면 밖에만 지는 부드러운 그림자. 반투명 유리 안으로 그림자가 비치지 않게 안쪽은 잘라 낸다. */
private fun DrawScope.drawOuterShadow(outline: Outline, style: GlassStyle, level: Float, cache: EffectCache) {
    val radius = style.shadow.toPx() * level
    if (radius < MIN_PX) return
    val path = cachedPath(outline, cache)
    if (cache.shadowRadius != radius) {
        cache.shadowRadius = radius
        cache.shadowPaint.maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
    }
    cache.shadowPaint.color = Color.Black.copy(alpha = SHADOW_ALPHA * level).toArgb()
    clipPath(path, clipOp = ClipOp.Difference) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.save()
            canvas.nativeCanvas.translate(0f, radius * SHADOW_DROP)
            canvas.nativeCanvas.drawPath(path.asAndroidPath(), cache.shadowPaint)
            canvas.nativeCanvas.restore()
        }
    }
}

/** 모양이 같으면 지난번 Path를 다시 쓴다. */
private fun cachedPath(outline: Outline, cache: EffectCache): Path {
    val key = when (outline) {
        is Outline.Rectangle -> outline.rect
        is Outline.Rounded -> outline.roundRect
        is Outline.Generic -> outline.path
    }
    if (cache.outlineKey != key) {
        cache.outlineKey = key
        cache.path = outline.toPath()
    }
    return cache.path
}

/** 64×64 고운 알갱이 무늬. 한 번 만들어 반복해 깐다. */
private object GrainBrush {
    val value: Brush by lazy { ShaderBrush(ImageShader(noise(), TileMode.Repeated, TileMode.Repeated)) }

    private fun noise(): ImageBitmap {
        val random = Random(GRAIN_SEED)
        val pixels = IntArray(GRAIN_SIZE * GRAIN_SIZE) {
            val v = random.nextInt(GRAY_LEVELS)
            android.graphics.Color.argb(GRAY_LEVELS - 1, v, v, v)
        }
        return android.graphics.Bitmap.createBitmap(
            pixels,
            GRAIN_SIZE,
            GRAIN_SIZE,
            android.graphics.Bitmap.Config.ARGB_8888,
        ).asImageBitmap()
    }
}

private const val REFRACTION_DP = 14f
private const val MIN_PX = 0.5f
private const val EDGE_FADE_AT = 0.45f
private const val EDGE_FADE = 0.25f
private const val GRAY_LEVELS = 256
private const val MAX_REFRACTION_PX = 48f
private const val BRIGHTNESS_SCALE = 255f
private const val SHADOW_ALPHA = 0.22f
private const val SHADOW_DROP = 0.35f
private const val GRAIN_SIZE = 64
private const val GRAIN_SEED = 7
