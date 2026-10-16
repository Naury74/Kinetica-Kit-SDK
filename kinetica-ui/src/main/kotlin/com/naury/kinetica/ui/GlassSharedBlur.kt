package com.naury.kinetica.ui

import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

/**
 * 한 배경을 비추는 글래스들이 함께 쓰는 블러 기록. 같은 블러, 채도, 밝기면 배경 전체를 한 번만 블러 처리해 두고,
 * 글래스마다 자기 자리만 잘라 그린다.
 *
 * 글래스마다 블러를 따로 걸면 요소마다 화면 밖 레이어와 블러 중간 텍스처가 생기고, 목록을 스크롤해 새 카드가 들어올 때마다
 * GPU 메모리를 새로 잡느라 프레임이 밀린다. 배경 하나를 함께 쓰면 블러는 한 번이고 크기도 바뀌지 않아 메모리를 다시 잡지 않는다.
 */
internal class SharedBlurs(private val context: GraphicsContext, val owner: View) {
    private val entries = HashMap<SharedBlurKey, SharedBlur>()

    /** 지금 들고 있는 블러 기록 수. 테스트에서 글래스가 기록을 함께 쓰는지 확인하는 데 쓴다. */
    val count: Int get() = entries.size

    /** 굴절 지도들에 등록된 자리 수의 합. 테스트에서 굴절 있는 글래스가 지도를 함께 쓰는지 확인하는 데 쓴다. */
    val regions: Int get() = entries.values.sumOf { it.map.count }

    /**
     * [key]에 맞는 블러 기록. 스타일이 [MAX_SHARED]개보다 많으면 null이고, 그 글래스는 혼자 블러를 건다.
     * 다른 글래스가 아직 그리고 있을 수 있어 쓰던 기록을 중간에 버리지 않는다.
     */
    fun obtain(key: SharedBlurKey): SharedBlur? {
        entries[key]?.let { return it }
        if (entries.size >= MAX_SHARED) return null
        return SharedBlur(context.createGraphicsLayer(), context.createGraphicsLayer()).also { entries[key] = it }
    }

    fun release() {
        entries.values.forEach {
            it.map.release()
            context.releaseGraphicsLayer(it.layer)
            context.releaseGraphicsLayer(it.refracted)
        }
        entries.clear()
    }
}

/** 함께 쓰는 블러를 가르는 값. 블러 반경(px), 채도, 밝기, 기록 배율이 같으면 같은 기록을 쓴다. */
internal data class SharedBlurKey(val blurPx: Float, val saturation: Float, val brightness: Float, val scale: Float)

/**
 * 배경 전체를 [SharedBlurKey.scale]배로 줄여 블러 처리한 [layer]와, 그 위에 굴절 있는 글래스들의 굴절을 한 번에 건 [refracted].
 * [refracted]는 굴절 있는 글래스가 처음 쓸 때 기록한다.
 */
internal class SharedBlur(val layer: GraphicsLayer, val refracted: GraphicsLayer) {
    var recordedSize: IntSize = IntSize.Zero
    var recordedLayer: GraphicsLayer? = null
    var refractedSize: IntSize = IntSize.Zero
    val map = RefractionMap(refracted)
}

/**
 * [backdrop]을 비추는 같은 창의 글래스가 블러 기록을 함께 쓰게 한다. [KineticaScaffold]가 콘텐츠 배경에 켠다.
 * 화면 밖으로 나가면 기록 레이어를 놓는다.
 */
@Composable
internal fun ShareBlurs(backdrop: GlassBackdrop) {
    val context = LocalGraphicsContext.current
    val view = LocalView.current
    val blurs = remember(backdrop, context, view) { SharedBlurs(context, view).also { backdrop.shared = it } }
    DisposableEffect(blurs) {
        backdrop.shared = blurs
        onDispose {
            if (backdrop.shared === blurs) backdrop.shared = null
            blurs.release()
        }
    }
}

/** 글래스 하나가 배경을 그릴 때 쓰는 레이어와 캐시. [Modifier.glass]가 요소마다 하나씩 들고 있다. */
internal class Sampler(val layer: GraphicsLayer, val sample: GraphicsLayer, val cache: EffectCache, val position: PositionHolder)

/** 함께 쓰는 블러에서 잘라 그릴 모양의 모서리 반경(px)과 덮는 알파(0..1). */
internal class Cut(val corner: Float, val alpha: Float)

/**
 * 함께 쓰는 블러에서 이 글래스 자리를 잘라 그린다. 함께 쓸 수 없으면(다른 창, 스타일이 너무 많음) false를 돌려주고 아무것도 그리지 않는다.
 * 굴절이 있으면 같은 블러 위에 여러 글래스의 굴절을 한 번에 건 지도([RefractionMap])에서 잘라 온다. 요소마다 화면 밖 레이어를 두지 않는다.
 * 강도가 1보다 작으면 같은 블러를 [Cut.alpha]만큼만 덮으므로 강도가 바뀌어도 함께 쓰는 기록을 그대로 쓴다.
 */
internal fun DrawScope.drawSharedBackdrop(
    backdrop: GlassBackdrop,
    blurs: SharedBlurs,
    sampler: Sampler,
    style: GlassStyle,
    cut: Cut,
): Boolean {
    val source = backdrop.layer.size
    if (source.width <= 0 || source.height <= 0) return false
    val blurPx = style.blur.toPx()
    val scale = sampleScale(blurPx)
    val shared = blurs.obtain(SharedBlurKey(blurPx, style.saturation, style.brightness, scale)) ?: return false
    val sharedSize = IntSize(ceil(source.width * scale).toInt().coerceAtLeast(1), ceil(source.height * scale).toInt().coerceAtLeast(1))
    if (shared.recordedSize != sharedSize || shared.recordedLayer !== backdrop.layer) {
        shared.recordedSize = sharedSize
        shared.recordedLayer = backdrop.layer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            shared.layer.renderEffect = colorBlurEffect(blurPx * scale, style).asComposeRenderEffect()
            GlassStats.effectBuilds++
        }
        shared.layer.record(sharedSize) { scale(scale, pivot = Offset.Zero) { drawLayer(backdrop.layer) } }
    }
    val refraction = style.refraction
    val bent = refraction > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val geometry = Refraction(blurPx, scale, cut.corner)
    val sample = sampler.sample
    sample.pivotOffset = Offset.Zero
    sample.scaleX = 1f
    sample.scaleY = 1f
    // 굴절은 함께 쓰는 지도에 자리를 잡아 건다. 지도가 꽉 찼으면 이 글래스만 혼자 굴절 레이어를 쓴다.
    if (bent && !mapRegion(shared, sampler, style, geometry, sharedSize)) {
        sample.record(sharedSize) { drawLayer(shared.layer) }
        sample.compositingStrategy = CompositingStrategy.Auto
        sample.alpha = 1f
        drawRefracted(backdrop, sampler, style, geometry, cut.alpha)
    } else {
        sample.record(sharedSize) { drawLayer(if (bent) shared.refracted else shared.layer) }
        drawCut(backdrop, sampler, scale, cut.alpha)
    }
    return true
}

/** [Sampler.sample]에 기록한 함께 쓰는 블러(또는 굴절 지도)를 이 글래스 자리에 맞춰 [alpha]만큼 그린다. */
private fun DrawScope.drawCut(backdrop: GlassBackdrop, sampler: Sampler, scale: Float, alpha: Float) {
    val sample = sampler.sample
    sampler.position.update(backdrop, sample, pad = 0f, scale = scale)
    sampler.layer.renderEffect = null
    // 겹쳐 그리는 것이 블러 레이어 하나뿐이라 알파를 그 그리기에 바로 곱한다. 겹침 처리로 두면 알파가 1보다 작을 때
    // 기록 전체 크기의 화면 밖 레이어를 하나 더 만든다.
    sample.compositingStrategy = CompositingStrategy.ModulateAlpha
    sample.alpha = alpha
    scale(1f / scale, pivot = Offset.Zero) { drawLayer(sample) }
}

/**
 * 이 글래스의 굴절 자리를 [shared]의 지도에 잡고 크기와 세기를 맞춘다. 위치는 [PositionHolder.update]가 맞춘다.
 * 자리를 못 잡으면 false다.
 */
private fun DrawScope.mapRegion(
    shared: SharedBlur,
    sampler: Sampler,
    style: GlassStyle,
    refraction: Refraction,
    sharedSize: IntSize,
): Boolean {
    val region = sampler.position.region
    if (!region.bind(shared.map)) return false
    if (shared.refractedSize != sharedSize) {
        shared.refractedSize = sharedSize
        shared.map.bounds = sharedSize
        shared.refracted.record(sharedSize) { drawLayer(shared.layer) }
    }
    val scale = refraction.scale
    region.shape(size.width * scale, size.height * scale, refraction.corner * scale, refractionStrength(refraction, style, density))
    region.used = true
    return true
}

/** 혼자 블러를 걸 때와 같은 식으로 정한 굴절 세기(기록 px). 함께 쓸 때도 모양이 달라지지 않게 같은 값을 쓴다. */
private fun refractionStrength(refraction: Refraction, style: GlassStyle, density: Float): Float {
    val legacyPad = ceil(refraction.blurPx * 2f + style.refraction * REFRACTION_DP * density) * refraction.scale
    return style.refraction * legacyPad.coerceAtMost(MAX_REFRACTION_PX)
}

/** 굴절을 거는 데 필요한 값. 블러 반경(px), 기록 배율, 모서리 반경(px). */
private class Refraction(val blurPx: Float, val scale: Float, val corner: Float)

private fun DrawScope.drawRefracted(backdrop: GlassBackdrop, sampler: Sampler, style: GlassStyle, refraction: Refraction, alpha: Float) {
    val scale = refraction.scale
    // 기록 여백은 굴절이 끌어올 거리만큼만 둔다.
    val strength = refractionStrength(refraction, style, density)
    val padScaled = ceil(strength).toInt() + 1
    val content = IntSize(ceil(size.width * scale).toInt().coerceAtLeast(1), ceil(size.height * scale).toInt().coerceAtLeast(1))
    val regionSize = IntSize(content.width + padScaled * 2, content.height + padScaled * 2)
    val cache = sampler.cache
    val key = listOf(SHARED_REFRACTION, regionSize, padScaled, refraction.corner * scale, strength)
    if (cache.key != key) {
        cache.key = key
        cache.effect = runCatching {
            refractionEffect(regionSize, padScaled.toFloat(), refraction.corner * scale, strength).asComposeRenderEffect()
        }.getOrNull()
        GlassStats.effectBuilds++
    }
    val layer = sampler.layer
    layer.renderEffect = cache.effect
    layer.alpha = alpha
    val pad = padScaled / scale
    sampler.position.update(backdrop, sampler.sample, pad, scale)
    // 레이어 크기를 단위로 맞추면 스크롤로 빠진 카드의 텍스처를 새로 들어온 비슷한 크기의 카드가 그대로 이어 쓴다.
    layer.record(IntSize(bucket(regionSize.width), bucket(regionSize.height))) { drawLayer(sampler.sample) }
    GlassStats.elementLayers++
    translate(-pad, -pad) { scale(1f / scale, pivot = Offset.Zero) { drawLayer(layer) } }
}

/** 레이어 크기(px)를 [LAYER_BUCKET] 단위로 올린다. */
internal fun bucket(px: Int): Int = (px + LAYER_BUCKET - 1) / LAYER_BUCKET * LAYER_BUCKET

/** 글래스 효과를 새로 만든 횟수. 강도 애니메이션 동안 효과를 다시 만들지 않는지 테스트에서 확인한다. */
internal object GlassStats {
    var effectBuilds: Int = 0

    /** 함께 쓰는 굴절 지도의 효과를 새로 만든 횟수. 배경만 바뀌는 프레임에는 늘지 않아야 한다. */
    var refractionBuilds: Int = 0

    /** 글래스가 자기 화면 밖 레이어(혼자 거는 블러나 굴절)를 기록한 횟수. 배경이 바뀔 때 요소마다 렌더 패스가 생기는지 가늠한다. */
    var elementLayers: Int = 0
}

private const val MAX_SHARED = 4
private const val LAYER_BUCKET = 32
private const val SHARED_REFRACTION = "shared"
