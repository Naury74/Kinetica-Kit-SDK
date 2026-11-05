package com.naury.kinetica.ui

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * 스크롤 영역의 위아래 끝에서 내용이 점점 흐려지며 사라지게 한다(iOS 26의 스크롤 가장자리 효과). 가장자리에 가까울수록 블러가 세지고,
 * 맨 끝에서는 투명해진다. 바 아래로 지나가는 목록이나 시트 안 목록 끝에 붙인다. 배경과 상관없이 내용만 흐려지므로 글래스나 사진 위에서도 쓸 수 있다.
 *
 * 블러는 세기가 다른 세 겹을 띠 높이의 1/3씩 엇갈려 겹쳐 점진적으로 보이게 하고, 띠만 절반 해상도로 기록해 블러 비용을 띠 넓이로 묶는다.
 * 블러는 Android 12(API 31)부터이고, 그보다 낮으면 [horizontalFadingEdges]처럼 투명도로만 사라진다.
 * 띠 크기가 바뀔 때만 레이어를 다시 기록하므로 스크롤하는 동안에는 내용 기록만 새로 한다.
 *
 * 수정자 순서상 `verticalScroll`보다 앞(바깥)에 붙여야 보이는 영역 기준으로 그린다. `LazyColumn`이면 그 수정자에 바로 붙인다.
 * [KineticaScaffold]의 상단 바 아래에 쓰려면 [top]을 바 높이(`content`가 받는 위쪽 패딩)에 띠 높이를 더한 값으로 준다.
 *
 * ```kotlin
 * val list = rememberLazyListState()
 * LazyColumn(Modifier.scrollEdgeBlur(list, top = padding.calculateTopPadding() + 24.dp), state = list, contentPadding = padding) { … }
 * ```
 *
 * @param state 이 상태로 더 스크롤할 수 없는 쪽은 흐리지 않는다. null이면 두 끝을 늘 흐린다. 흐림은 짧게 나타나고 사라지며,
 *   '애니메이션 삭제' 설정이 켜져 있으면 바로 바뀐다.
 * @param top 위쪽 띠 높이. 0이면 위쪽은 흐리지 않는다. 영역 높이의 절반을 넘지 않는다.
 * @param bottom 아래쪽 띠 높이. 0이면 아래쪽은 흐리지 않는다. 영역 높이의 절반을 넘지 않는다.
 * @param blur 가장 끝 겹의 블러 반경. 0이면 블러 없이 투명도로만 사라진다.
 */
@Composable
public fun Modifier.scrollEdgeBlur(
    state: ScrollableState? = null,
    top: Dp = DEFAULT_EDGE,
    bottom: Dp = DEFAULT_EDGE,
    blur: Dp = DEFAULT_BLUR,
): Modifier {
    val topAmount = edgeAmount(state, top) { it.canScrollBackward }
    val bottomAmount = edgeAmount(state, bottom) { it.canScrollForward }
    val content = rememberGraphicsLayer()
    val topBand = rememberEdgeBand(atTop = true)
    val bottomBand = rememberEdgeBand(atTop = false)
    return drawWithContent {
        val topShown = topAmount.value
        val bottomShown = bottomAmount.value
        if (topShown <= 0f && bottomShown <= 0f) {
            drawContent()
            return@drawWithContent
        }
        content.record { this@drawWithContent.drawContent() }
        val topPx = bandHeight(top.toPx(), topShown, size.height)
        val bottomPx = bandHeight(bottom.toPx(), bottomShown, size.height)
        // 다 나타난 띠는 아래 원래 내용을 그리지 않는다. 띠 안 내용은 띠가 흐린 것으로 대신 그린다.
        val clipTop = if (topShown >= 1f) topPx else 0f
        val clipBottom = if (bottomShown >= 1f) bottomPx else 0f
        clipRect(top = clipTop, bottom = size.height - clipBottom) { drawLayer(content) }
        val blurPx = blur.toPx()
        if (topPx > 0f) topBand.draw(this, content, topPx, blurPx, topShown)
        if (bottomPx > 0f) bottomBand.draw(this, content, bottomPx, blurPx, bottomShown)
    }
}

/** 한쪽 띠가 나타난 정도(0..1). [band]가 0이거나 [state]가 그쪽으로 더 갈 수 없으면 0으로 짧게 사라진다. '애니메이션 삭제'면 바로 바뀐다. */
@Composable
private fun edgeAmount(state: ScrollableState?, band: Dp, canScroll: (ScrollableState) -> Boolean): State<Float> {
    val still = isReducedMotion()
    val can by remember(state) { derivedStateOf { state?.let(canScroll) ?: true } }
    val target = if (can && band > 0.dp) 1f else 0f
    return animateFloatAsState(target, tween(if (still) 0 else FADE_MS), label = "scroll-edge")
}

/**
 * [scrollEdgeBlur]를 건 상자. 스크롤 목록이 아닌 내용(긴 글, 큰 사진 묶음)을 담고 위아래 끝을 점진 블러로 사라지게 할 때 쓴다.
 * 파라미터는 [scrollEdgeBlur]와 같다.
 */
@Composable
public fun KProgressiveBlur(
    modifier: Modifier = Modifier,
    state: ScrollableState? = null,
    top: Dp = DEFAULT_EDGE,
    bottom: Dp = DEFAULT_EDGE,
    blur: Dp = DEFAULT_BLUR,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.scrollEdgeBlur(state, top, bottom, blur), content = content)
}

/** 나타난 정도가 [amount]인 띠의 높이(px). 영역 높이 [total]의 절반을 넘지 않고, 숨은 띠는 0이다. */
private fun bandHeight(band: Float, amount: Float, total: Float): Float = if (amount > 0f) band.coerceAtMost(total / 2) else 0f

@Composable
private fun rememberEdgeBand(atTop: Boolean): EdgeBand {
    val composite = rememberGraphicsLayer()
    val a = rememberGraphicsLayer()
    val b = rememberGraphicsLayer()
    val c = rememberGraphicsLayer()
    return remember(composite, a, b, c) { EdgeBand(atTop, composite, listOf(a, b, c)) }
}

/**
 * 한쪽 가장자리 띠. [levels]는 세기가 다른 블러 겹이고, [composite]는 선명한 내용 위에 블러 겹을 쌓고 끝을 투명하게 지운 띠다.
 * 모든 레이어는 [GraphicsLayer] 하나(요소 내용 기록)만 참조하므로, 내용이 바뀌어도 띠 크기가 그대로면 다시 기록하지 않는다.
 */
internal class EdgeBand(private val atTop: Boolean, private val composite: GraphicsLayer, private val levels: List<GraphicsLayer>) {
    private var keyWidth = Float.NaN
    private var keyHeight = Float.NaN
    private var keyTop = Float.NaN
    private var keyBlur = Float.NaN
    private var keyContent: GraphicsLayer? = null

    fun draw(scope: DrawScope, content: GraphicsLayer, height: Float, blurPx: Float, alpha: Float) = with(scope) {
        val top = if (atTop) 0f else size.height - height
        val same = keyWidth == size.width && keyHeight == height && keyTop == top && keyBlur == blurPx && keyContent === content
        if (!same) {
            keyWidth = size.width
            keyHeight = height
            keyTop = top
            keyBlur = blurPx
            keyContent = content
            GlassFxStats.edgeBuilds++
            record(content, Size(size.width, height), top, blurPx)
        }
        composite.alpha = alpha
        translate(top = top) { drawLayer(composite) }
    }

    private fun DrawScope.record(content: GraphicsLayer, band: Size, top: Float, blurPx: Float) {
        val canBlur = blurPx > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val small = IntSize(ceil(band.width * SCALE).toInt().coerceAtLeast(1), ceil(band.height * SCALE).toInt().coerceAtLeast(1))
        if (canBlur) {
            levels.forEachIndexed { i, level ->
                val strength = (i + 1f) / levels.size
                level.renderEffect = blurEffect(blurPx * SCALE * strength)?.asComposeRenderEffect()
                val mask = levelMask(i, band.height)
                level.record(small) {
                    scale(SCALE, pivot = Offset.Zero) {
                        translate(top = -top) { drawLayer(content) }
                        drawRect(mask, size = band, blendMode = BlendMode.DstIn)
                    }
                }
            }
        }
        val fade = fadeMask(band.height)
        composite.compositingStrategy = CompositingStrategy.Offscreen
        composite.record(IntSize(ceil(band.width).toInt(), ceil(band.height).toInt())) {
            translate(top = -top) { drawLayer(content) }
            if (canBlur) levels.forEach { scale(1f / SCALE, pivot = Offset.Zero) { drawLayer(it) } }
            drawRect(fade, size = band, blendMode = BlendMode.DstIn)
        }
    }

    /**
     * [index]번 겹이 덮는 범위. 가장자리 쪽에서 불투명하고 안쪽으로 띠 높이의 1/3에 걸쳐 사라진다. 겹이 셀수록 가장자리에 가깝다.
     * 위쪽 띠는 y=0이, 아래쪽 띠는 y=[height]가 가장자리다.
     */
    private fun levelMask(index: Int, height: Float): Brush {
        val part = height / levels.size
        val solid = height - part * (index + 1)
        val clear = height - part * index
        return if (atTop) {
            Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = solid, endY = clear)
        } else {
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = height - clear, endY = height - solid)
        }
    }

    /** 맨 끝에서 투명하고 띠 높이의 [FADE_PART]만큼 안쪽에서 불투명해지는 지우개. */
    private fun fadeMask(height: Float): Brush = if (atTop) {
        Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = height * FADE_PART)
    } else {
        Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = height * (1f - FADE_PART), endY = height)
    }
}

private val DEFAULT_EDGE = 32.dp
private val DEFAULT_BLUR = 12.dp
private const val FADE_MS = 150
private const val SCALE = 0.5f
private const val FADE_PART = 0.5f
