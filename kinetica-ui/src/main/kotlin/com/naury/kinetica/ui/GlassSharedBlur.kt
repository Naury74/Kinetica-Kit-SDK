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

    /**
     * [key]에 맞는 블러 기록. 스타일이 [MAX_SHARED]개보다 많으면 null이고, 그 글래스는 혼자 블러를 건다.
     * 다른 글래스가 아직 그리고 있을 수 있어 쓰던 기록을 중간에 버리지 않는다.
     */
    fun obtain(key: SharedBlurKey): SharedBlur? {
        entries[key]?.let { return it }
        if (entries.size >= MAX_SHARED) return null
        return SharedBlur(context.createGraphicsLayer()).also { entries[key] = it }
    }

    fun release() {
        entries.values.forEach { context.releaseGraphicsLayer(it.layer) }
        entries.clear()
    }
}

/** 함께 쓰는 블러를 가르는 값. 블러 반경(px), 채도, 밝기, 기록 배율이 같으면 같은 기록을 쓴다. */
internal data class SharedBlurKey(val blurPx: Float, val saturation: Float, val brightness: Float, val scale: Float)

/** 배경 전체를 [SharedBlurKey.scale]배로 줄여 블러 처리한 레이어. */
internal class SharedBlur(val layer: GraphicsLayer) {
    var recordedSize: IntSize = IntSize.Zero
    var recordedLayer: GraphicsLayer? = null
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
 * 굴절이 있으면 잘라 온 블러 위에 굴절 셰이더만 요소마다 건다. 블러는 다시 하지 않는다.
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
    val sample = sampler.sample
    sample.record(sharedSize) { drawLayer(shared.layer) }
    sample.pivotOffset = Offset.Zero
    sample.scaleX = 1f
    sample.scaleY = 1f
    val refraction = style.refraction
    if (refraction <= 0f || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        sampler.position.update(backdrop, sample, pad = 0f, scale = scale)
        sampler.layer.renderEffect = null
        // 겹쳐 그리는 것이 블러 레이어 하나뿐이라 알파를 그 그리기에 바로 곱한다. 겹침 처리로 두면 알파가 1보다 작을 때
        // 기록 전체 크기의 화면 밖 레이어를 하나 더 만든다.
        sample.compositingStrategy = CompositingStrategy.ModulateAlpha
        sample.alpha = cut.alpha
        scale(1f / scale, pivot = Offset.Zero) { drawLayer(sample) }
    } else {
        sample.compositingStrategy = CompositingStrategy.Auto
        sample.alpha = 1f
        drawRefracted(backdrop, sampler, style, Refraction(blurPx, scale, cut.corner), cut.alpha)
    }
    return true
}

/** 굴절을 거는 데 필요한 값. 블러 반경(px), 기록 배율, 모서리 반경(px). */
private class Refraction(val blurPx: Float, val scale: Float, val corner: Float)

private fun DrawScope.drawRefracted(backdrop: GlassBackdrop, sampler: Sampler, style: GlassStyle, refraction: Refraction, alpha: Float) {
    val scale = refraction.scale
    // 굴절 세기는 혼자 블러를 걸 때와 같은 식으로 정해 모양이 달라지지 않게 한다. 기록 여백은 굴절이 끌어올 거리만큼만 둔다.
    val legacyPad = ceil(refraction.blurPx * 2f + style.refraction * REFRACTION_DP * density) * scale
    val strength = style.refraction * legacyPad.coerceAtMost(MAX_REFRACTION_PX)
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
    translate(-pad, -pad) { scale(1f / scale, pivot = Offset.Zero) { drawLayer(layer) } }
}

/** 레이어 크기(px)를 [LAYER_BUCKET] 단위로 올린다. */
internal fun bucket(px: Int): Int = (px + LAYER_BUCKET - 1) / LAYER_BUCKET * LAYER_BUCKET

/** 글래스 효과를 새로 만든 횟수. 강도 애니메이션 동안 효과를 다시 만들지 않는지 테스트에서 확인한다. */
internal object GlassStats {
    var effectBuilds: Int = 0
}

private const val MAX_SHARED = 4
private const val LAYER_BUCKET = 32
private const val SHARED_REFRACTION = "shared"
