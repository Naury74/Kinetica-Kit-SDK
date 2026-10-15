// 파일 이름은 대표 컴포저블을 따른다. 함께 쓰는 공개 타입이 하나뿐이라 detekt가 그 이름을 요구한다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** [GlassBlobGroup] 안에서 쓰는 범위. `Row`나 `Column` 안에서도 [blob]을 쓸 수 있도록 레이아웃 범위를 따로 두지 않는다. */
@Stable
public interface GlassBlobGroupScope {
    /**
     * 이 요소 자리에 방울을 그린다. 같은 그룹의 다른 방울과 가까워지면 둘 사이가 액체처럼 이어진다.
     * 방울 위치는 배치 단계 위치를 따르므로 `Modifier.offset { }`처럼 배치를 바꾸는 방법으로 움직여야 한다. `graphicsLayer`의 이동은 반영되지 않는다.
     *
     * @param cornerRadius 방울 모서리 반경. 높이의 절반보다 크면 캡슐 모양이 된다.
     */
    @Composable
    public fun Modifier.blob(cornerRadius: Dp = 999.dp): Modifier
}

/**
 * 메타볼처럼 가까운 방울끼리 이어져 보이는 그룹. 안쪽 요소에 [GlassBlobGroupScope.blob]을 붙이면 그 자리에 [color] 방울이 깔리고,
 * 방울끼리 [merge]보다 가까워지면 사이가 녹아 붙은 것처럼 이어진다. 펼쳐지는 버튼 묶음이나 탭 인디케이터에 어울린다.
 * Android 12(API 31)부터 블러와 알파 임계값으로 잇고, 그보다 낮은 버전에서는 방울을 따로 그린다.
 * 방울은 그룹 경계 밖으로 [merge]만큼까지 그려진다.
 *
 * ```kotlin
 * GlassBlobGroup {
 *     Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
 *         Icon(Icons.Default.Add, null, Modifier.blob().padding(14.dp))
 *         Icon(Icons.Default.Share, null, Modifier.blob().padding(14.dp))
 *     }
 * }
 * ```
 *
 * @param color 방울 색. 위쪽을 조금 밝게 칠한다.
 * @param merge 방울이 이어지기 시작하는 거리. 클수록 멀리서도 이어지고 모서리가 더 둥글어진다.
 */
@Composable
public fun GlassBlobGroup(
    modifier: Modifier = Modifier,
    color: Color = GlassTheme.colors.accent,
    merge: Dp = 16.dp,
    content: @Composable GlassBlobGroupScope.() -> Unit,
) {
    Box(modifier) {
        val scope = remember { BlobGroupScope() }
        Spacer(
            Modifier
                .matchParentSize()
                .onPlaced { scope.group = it }
                .bleed(merge * 2)
                .graphicsLayer {
                    val radius = merge.toPx()
                    if (scope.effectRadius != radius && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        scope.effectRadius = radius
                        scope.effect = gooeyEffect(radius)
                    }
                    renderEffect = scope.effect
                }
                .drawWithCache {
                    val pad = merge.toPx() * 2f
                    // 방울마다 붓을 만들지 않도록 그룹 높이에 걸친 그라디언트 하나를 같이 쓴다.
                    val brush = Brush.verticalGradient(listOf(lerp(color, Color.White, BLOB_TOP_LIGHT), color), 0f, size.height - pad * 2f)
                    onDrawBehind {
                        translate(pad, pad) {
                            for ((rect, corner) in scope.blobs.values) {
                                val r = minOf(corner.toPx(), rect.minDimension / 2f)
                                drawRoundRect(brush, rect.topLeft, rect.size, CornerRadius(r))
                            }
                        }
                    }
                },
        )
        scope.content()
    }
}

private class BlobGroupScope : GlassBlobGroupScope {
    val blobs = mutableStateMapOf<Any, Pair<Rect, Dp>>()
    var group: LayoutCoordinates? = null
    var effectRadius = -1f
    var effect: androidx.compose.ui.graphics.RenderEffect? = null

    @Composable
    override fun Modifier.blob(cornerRadius: Dp): Modifier {
        val key = remember { Any() }
        DisposableEffect(key) { onDispose { blobs.remove(key) } }
        return onGloballyPositioned { coordinates ->
            val parent = group?.takeIf { it.isAttached } ?: return@onGloballyPositioned
            val rect = parent.localBoundingBoxOf(coordinates, clipBounds = false)
            if (blobs[key]?.first != rect || blobs[key]?.second != cornerRadius) blobs[key] = rect to cornerRadius
        }
    }
}

/** 요소를 사방으로 [amount]만큼 넓혀 그리되 배치 크기는 그대로 둔다. 블러가 가장자리에서 잘리지 않게 한다. */
private fun Modifier.bleed(amount: Dp): Modifier = layout { measurable, constraints ->
    val pad = amount.roundToPx()
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val placeable = measurable.measure(Constraints.fixed(width + pad * 2, height + pad * 2))
    layout(width, height) { placeable.place(-pad, -pad) }
}

/** 흐린 뒤 알파를 급하게 끊어, 겹친 흐림이 하나의 매끈한 덩어리로 보이게 한다. */
private fun gooeyEffect(radius: Float): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radius <= 0f) return null
    val threshold = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, ALPHA_GAIN, ALPHA_CUT,
        ),
    )
    val blur = RenderEffect.createBlurEffect(radius / 2f, radius / 2f, Shader.TileMode.DECAL)
    return RenderEffect.createChainEffect(RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(threshold)), blur)
        .asComposeRenderEffect()
}

private const val BLOB_TOP_LIGHT = 0.22f
private const val ALPHA_GAIN = 20f
private const val ALPHA_CUT = -9f * 255f
