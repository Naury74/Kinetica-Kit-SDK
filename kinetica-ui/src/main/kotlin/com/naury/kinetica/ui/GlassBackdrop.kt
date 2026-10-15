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
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.random.Random

/**
 * 글래스 표면이 비춰 보일 배경. [glassBackdrop]을 붙인 요소의 그리기 내용을 기록해 두면, [glass] 표면이 자기 위치에 해당하는
 * 부분을 블러 처리해 그린다.
 *
 * 글래스 표면은 배경 요소 바깥, 즉 형제나 그 위 레이어에 둬야 한다. [GlassScaffold]는 이 배치를 알아서 맞춘다.
 * 바와 다이얼로그는 화면 전체를 비추고, 콘텐츠 안의 글래스는 [GlassScaffold]의 `background`만 비춘다.
 */
@Stable
public class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    // 기록한 내용의 왼쪽 위(화면 좌표, px). 글래스가 Dialog나 Popup 같은 다른 창에 있어도 같은 기준으로 위치를 맞추려고 화면 좌표를 쓴다.
    internal var origin by mutableStateOf(Offset.Zero)
    internal var size by mutableStateOf(IntSize.Zero)

    /**
     * true면 [origin] 대신 글래스가 속한 Compose 루트의 왼쪽 위를 기록 원점으로 본다. XML 화면용 View 기록이 쓴다.
     * 그 기록은 글래스를 담은 ComposeView 기준으로 매 프레임 다시 하므로, 루트 좌표로 맞춰야 스크롤 중에도 한 프레임 어긋나지 않는다.
     */
    internal var rootRelative: Boolean = false

    /** 배경을 기록하는 중인지 나타낸다. 실수로 배경 안에 둔 글래스가 자기 자신을 기록해 그리기가 순환하지 않도록, 이때는 글래스를 그리지 않는다. */
    internal var recording: Boolean = false
}

/** 컴포지션에 유지되는 [GlassBackdrop]을 만든다. */
@Composable
public fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** 가장 가까운 [GlassScaffold]가 제공하는 배경. 없으면 `null`이고, 이때 글래스는 [GlassStyle.fallback] 색으로 칠해진다. */
public val LocalGlassBackdrop: androidx.compose.runtime.ProvidableCompositionLocal<GlassBackdrop?> = staticCompositionLocalOf { null }

// 다이얼로그·시트 같은 팝업 판 안이면 true. 그 안의 글래스는 판 위에 얹힌 한 겹으로 그린다.
internal val LocalGlassLayered = staticCompositionLocalOf { false }

private const val LAYERED_ALPHA = 0.06f

/**
 * 이 요소의 그리기 내용을 [backdrop]에 기록한다. 화면에는 평소처럼 그린다.
 *
 * 이 [backdrop]을 비추는 글래스는 반드시 이 요소 바깥에 둔다. 안쪽에 두면 기록에 자기 자신이 들어가므로 그리지 않는다.
 * 카드 안 사진 위에 글래스를 올리는 경우처럼 안쪽에 글래스가 필요하면, 그 사진에 따로 [glassBackdrop]을 붙이고
 * 글래스에 그 [GlassBackdrop]을 넘긴다.
 *
 * 안에 `AndroidView`로 넣은 호스트 View도 함께 기록되고, View가 다시 그려지면 글래스도 다음 프레임에 따라 바뀐다.
 * 단 SurfaceView(지도, 영상, 카메라 미리보기)는 창과 다른 표면에 그려져 기록에 들어가지 않으므로, 그 위의 글래스는 블러 없이
 * 투명하게 비친다. 이런 화면에서는 TextureView를 쓰거나 그 위 글래스에 `backdrop = null`을 넘겨 fallback 색을 칠한다.
 * 하드웨어 가속이 꺼진 창이나 소프트웨어 캔버스로 그릴 때(View.draw로 Bitmap 만들기)는 블러가 빠지고 tint만 덮인다.
 */
public fun Modifier.glassBackdrop(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned {
        backdrop.origin = it.positionOnScreen()
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
 * 글래스 표면을 그린다. [shape] 안에 [backdrop]의 같은 위치를 [style]에 따라 블러 처리하고 색을 덮은 뒤,
 * 글로우 테두리와 그림자, 노이즈 질감을 더한다. 콘텐츠는 그 위에 평소처럼 그린다.
 * [backdrop]이 `null`이거나 API 31 미만이면 [GlassStyle.fallback] 색을 칠한다.
 *
 * @param intensity 효과 강도(0..1). 블러, 색 덮기, 그림자를 함께 줄인다. 다이얼로그 배경이 서서히 흐려지는 애니메이션에 쓴다.
 *   범위를 벗어난 값은 0..1로 맞춘다.
 */
@Composable
public fun Modifier.glass(
    style: GlassStyle,
    shape: Shape = RectangleShape,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    intensity: Float = 1f,
): Modifier {
    val blurLayer = rememberGraphicsLayer()
    // 팝업 판 안에 놓인 글래스가 주변 배경을 그대로 비추면 판을 뚫고 뒤 화면이 또렷하게 보인다.
    // 그래서 따로 배경을 넘기지 않았다면 판 위에 한 겹 얹힌 유리처럼 옅은 색만 덮는다.
    val layered = LocalGlassLayered.current && backdrop === LocalGlassBackdrop.current
    val position = remember { PositionHolder() }
    val effects = remember { EffectCache() }
    return this
        .onGloballyPositioned {
            position.offset = it.positionOnScreen()
            position.root = it.positionInRoot()
        }
        .drawBehind {
            if (backdrop?.recording == true) return@drawBehind
            val outline = shape.createOutline(size, layoutDirection, this)
            val level = intensity.coerceIn(0f, 1f)
            drawOuterShadow(outline, style, level, effects)
            val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            clipOutline(outline) {
                if (layered) {
                    drawRect(style.tint.copy(alpha = style.tint.alpha * level))
                    drawRect(style.onGlass.copy(alpha = LAYERED_ALPHA * level))
                } else if (canBlur && backdrop != null) {
                    val at = if (backdrop.rootRelative) position.root else position.offset
                    drawBackdrop(backdrop, Sampler(blurLayer, effects, at), style, outline, level)
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
    var root: Offset = Offset.Zero
}

/** 프레임마다 RenderEffect를 새로 만들지 않도록 마지막 값을 보관한다. */
private class EffectCache {
    var key: Any? = null
    var effect: androidx.compose.ui.graphics.RenderEffect? = null

    // 그림자 Paint와 Path는 크기나 반경이 바뀔 때만 새로 만든다. 프레임마다 만들면 블러 마스크를 매번 다시 계산한다.
    val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    var shadowRadius = -1f
    var outlineKey: Any? = null
    var path: Path = Path()
}

/** 기록한 영역의 크기, 가장자리 여백, 모서리 반경(px). */
private class Region(val size: IntSize, val pad: Float, val corner: Float)

/** 글래스 하나가 배경을 옮겨 그릴 때 쓰는 레이어, 효과 캐시, 루트 기준 위치. */
private class Sampler(val layer: GraphicsLayer, val cache: EffectCache, val offset: Offset)

private fun DrawScope.drawBackdrop(backdrop: GlassBackdrop, sampler: Sampler, style: GlassStyle, outline: Outline, level: Float) {
    val layer = sampler.layer
    val cache = sampler.cache
    val offset = sampler.offset
    val blurPx = style.blur.toPx() * level
    // 블러와 굴절은 가장자리 바깥의 픽셀을 끌어오므로 그만큼 넓게 기록한다.
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
        // 기기 GPU가 셰이더를 지원하지 않으면 굴절 없이 그린다.
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

/** 둥근 사각형 가장자리 근처에서 바깥쪽 픽셀을 끌어와 렌즈처럼 휘어 보이게 한다. */
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

/** 위쪽과 왼쪽이 밝고 아래쪽과 오른쪽으로 갈수록 옅어지는 글로우 테두리. */
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

/** 표면 바깥에만 드리우는 부드러운 그림자. 반투명한 글래스 안으로 그림자가 비치지 않도록 안쪽은 잘라 낸다. */
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

/** 모양이 같으면 이전 Path를 재사용한다. */
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

/** 64×64 노이즈 텍스처. 한 번만 만들어 타일로 반복한다. */
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
