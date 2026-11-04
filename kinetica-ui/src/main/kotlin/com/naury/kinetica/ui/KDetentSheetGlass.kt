package com.naury.kinetica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * 가장 높은 높이에 가까운 정도(0..1). 바로 아래 높이에서 0, 가장 높은 높이에서 1이다. 높이가 하나뿐이면 화면 아래에서 그 높이까지로 잰다.
 * 그리기 단계에서 시트 위치를 읽으므로, 이 값을 읽는 곳은 시트가 움직일 때만 다시 그린다.
 */
internal fun KDetentSheetState.attachLevel(): Float {
    val now = offset
    if (now.isNaN()) return 0f
    var top = Float.MAX_VALUE
    for (detent in detents) top = minOf(top, positionOf(detent))
    var below = Float.MAX_VALUE
    for (detent in detents) {
        val y = positionOf(detent)
        if (y > top + 1f) below = minOf(below, y)
    }
    if (below == Float.MAX_VALUE) below = containerHeight
    if (below <= top) return 1f
    return ((below - now) / (below - top)).coerceIn(0f, 1f)
}

/**
 * 떠 있다가 가장 높이 올리면 가장자리에 붙는 글래스 시트 판. 낮은 높이에서는 양옆과 아래에서 [FLOAT_MARGIN]만큼 떨어진 둥근 유리 판이고,
 * [attachLevel]이 1에 가까울수록 여백이 줄고 아래 모서리가 펴지며 불투명한 표면 색이 덮인다. 모두 그리기 단계에서 바뀌므로
 * 시트를 끄는 동안 다시 구성하거나 다시 배치하지 않는다. 내용도 같은 모양으로 자른다.
 */
@Composable
internal fun Modifier.settlingGlassSheet(state: KDetentSheetState, surface: SurfaceStyle, attachedCorner: Dp): Modifier {
    val base = (surface as? SurfaceStyle.Glass)?.style ?: LocalGlassStyle.current
    // 끄는 동안 모양이 프레임마다 바뀌므로, 굴절을 켜 두면 함께 쓰는 굴절 지도를 프레임마다 다시 만든다.
    val glass = base.forPopup().copy(refraction = 0f)
    val solid = surface.fillColor.copy(alpha = 1f)
    val density = LocalDensity.current
    val shape = remember(state, density, attachedCorner) {
        with(density) {
            SettlingSheetShape(state, FLOAT_MARGIN.toPx(), FLOAT_CORNER.toPx(), attachedCorner.coerceAtMost(MAX_CORNER).toPx())
        }
    }
    val path = remember { Path() }
    return this
        .glass(glass, shape)
        .drawBehind {
            val level = state.attachLevel()
            if (level <= 0f) return@drawBehind
            path.fill(shape.createOutline(size, layoutDirection, this))
            drawPath(path, solid, alpha = level)
        }
        .drawWithContent {
            path.fill(shape.createOutline(size, layoutDirection, this))
            clipPath(path) { this@drawWithContent.drawContent() }
        }
}

private fun Path.fill(outline: Outline) {
    reset()
    when (outline) {
        is Outline.Rounded -> addRoundRect(outline.roundRect)
        is Outline.Rectangle -> addRect(outline.rect)
        is Outline.Generic -> addPath(outline.path)
    }
}

/**
 * [state]의 위치에 따라 바뀌는 시트 모양. [margin]은 떠 있을 때 양옆과 아래 여백, [floating]은 떠 있을 때 네 모서리 반경,
 * [attached]는 붙었을 때 위 모서리 반경이다(모두 px). 아래쪽은 화면에 보이는 부분 끝에서 여백만큼 올린다.
 */
private class SettlingSheetShape(
    private val state: KDetentSheetState,
    private val margin: Float,
    private val floating: Float,
    private val attached: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val level = state.attachLevel()
        val inset = margin * (1f - level)
        val position = state.offset.takeUnless { it.isNaN() } ?: state.containerHeight
        val visible = (state.containerHeight - position).coerceIn(0f, size.height)
        val bottom = (visible - inset).coerceAtLeast(minOf(visible, floating * 2))
        val top = CornerRadius(lerp(floating, attached, level))
        val low = CornerRadius(floating * (1f - level))
        return Outline.Rounded(RoundRect(inset, 0f, size.width - inset, bottom, top, top, low, low))
    }
}

private val FLOAT_MARGIN = 10.dp
private val FLOAT_CORNER = 32.dp
private val MAX_CORNER = 48.dp
