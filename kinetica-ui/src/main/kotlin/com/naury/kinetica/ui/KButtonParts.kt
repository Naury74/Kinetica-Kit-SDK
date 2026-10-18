package com.naury.kinetica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * 글자로 쓸 [color]가 [background] 위에서 [minRatio] 대비를 넘도록 어둡게(배경이 어두우면 밝게) 고친다.
 * 밝은 초록 같은 강조색을 흰 표면 위 글자로 쓰면 대비가 2:1 남짓이라 읽기 어렵다. 이미 충분하면 그대로 돌려주고,
 * 모자라면 [minRatio]를 막 넘는 만큼만 검정(또는 흰색) 쪽으로 옮겨 색이 필요 이상 칙칙해지지 않게 한다.
 */
internal fun readableOn(color: Color, background: Color, minRatio: Float = READABLE_RATIO): Color {
    val under = if (background.luminance() > HALF) Color.White else Color.Black
    val base = if (background.alpha < 1f) background.compositeOver(under) else background
    if (contrast(color, base) >= minRatio) return color
    val toward = if (base.luminance() > HALF) Color.Black else Color.White
    // 섞는 비율이 커질수록 대비가 (한 번 내려갔다가도) 결국 커지므로, 처음 기준을 넘는 비율을 이분 탐색으로 찾는다.
    var low = 0f
    var high = 1f
    repeat(READABLE_SEARCH_STEPS) {
        val mid = (low + high) / 2
        if (contrast(lerp(color, toward, mid), base) >= minRatio) high = mid else low = mid
    }
    return lerp(color, toward, high)
}

/** 두 색의 WCAG 대비 비율(1..21) */
internal fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + CONTRAST_OFFSET
    val lb = b.luminance() + CONTRAST_OFFSET
    return if (la > lb) la / lb else lb / la
}

/** [variant] 모양에서 글자가 실제로 놓이는 바탕색. 채운 모양은 그 색, 나머지는 표면 색이다. */
internal fun variantInkBackground(variant: KVariant, colors: ToneColors, surface: SurfaceStyle): Color = when (variant) {
    KVariant.Filled -> colors.fill
    KVariant.Tonal -> colors.color.copy(alpha = TONAL_BACK).compositeOver(surface.inkBackground())
    else -> surface.inkBackground()
}

/**
 * 표면 위 글자의 바탕. 글래스는 투명해 뒤가 비치므로 글자 색의 반대 밝기로 본다. 거의 불투명한 색은 불투명으로 돌려줘,
 * 대비를 고칠 때와 잴 때 같은 바탕을 보게 한다.
 */
internal fun SurfaceStyle.inkBackground(): Color {
    val fill = fillColor
    return if (fill.alpha >= OPAQUE) {
        fill.copy(alpha = 1f)
    } else if (isDark) {
        Color(DARK_BACK)
    } else {
        Color.White
    }
}

/**
 * 체크 표시를 [level](0..1)만큼 그린다. 짧은 획을 먼저 긋고 긴 획을 이어 그어 펜으로 쓰듯 나타난다.
 * [size]는 체크 표시가 들어갈 정사각형 한 변(px), [topLeft]는 그 사각형의 왼쪽 위다.
 */
internal fun DrawScope.drawCheckStroke(color: Color, level: Float, topLeft: Offset, size: Float, stroke: Float) {
    if (level <= 0f) return
    val start = topLeft + Offset(size * CHECK_X0, size * CHECK_Y0)
    val corner = topLeft + Offset(size * CHECK_X1, size * CHECK_Y1)
    val end = topLeft + Offset(size * CHECK_X2, size * CHECK_Y2)
    val first = (level / CHECK_SPLIT).coerceIn(0f, 1f)
    val second = ((level - CHECK_SPLIT) / (1f - CHECK_SPLIT)).coerceIn(0f, 1f)
    drawLine(color, start, start + (corner - start) * first, stroke, StrokeCap.Round)
    if (second > 0f) drawLine(color, corner, corner + (end - corner) * second, stroke, StrokeCap.Round)
}

/** 가운데가 [center]이고 폭이 [size]인 아래 방향 꺾쇠. 돌리는 것은 호출한 쪽이 맡는다. */
internal fun DrawScope.drawChevronDown(color: Color, center: Offset, size: Float, stroke: Float) {
    val half = size / 2
    val drop = size * CHEVRON_DROP
    drawLine(color, center + Offset(-half, -drop), center + Offset(0f, drop), stroke, StrokeCap.Round)
    drawLine(color, center + Offset(0f, drop), center + Offset(half, -drop), stroke, StrokeCap.Round)
}

/** 가운데가 [center]이고 한 변이 [size]인 × 표시 */
internal fun DrawScope.drawCross(color: Color, center: Offset, size: Float, stroke: Float) {
    val half = size / 2
    drawLine(color, center + Offset(-half, -half), center + Offset(half, half), stroke, StrokeCap.Round)
    drawLine(color, center + Offset(-half, half), center + Offset(half, -half), stroke, StrokeCap.Round)
}

/**
 * 시작 쪽과 끝 쪽 모서리 반지름을 따로 정하는 둥근 사각형. 반지름은 높이의 비율(0..0.5)이고, 0.5면 캡슐 끝처럼 반원이다.
 * 이어 붙인 버튼 묶음에서 바깥 모서리는 둥글게, 안쪽 모서리는 작게 깎을 때 쓴다. RTL이면 시작과 끝이 바뀐다.
 */
internal class SideRoundShape(private val start: Float, private val end: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val left = if (layoutDirection == LayoutDirection.Rtl) end else start
        val right = if (layoutDirection == LayoutDirection.Rtl) start else end
        val l = CornerRadius(minOf(size.height * left.coerceIn(0f, HALF), size.width / 2))
        val r = CornerRadius(minOf(size.height * right.coerceIn(0f, HALF), size.width / 2))
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, l, r, r, l))
    }

    override fun equals(other: Any?): Boolean = other is SideRoundShape && other.start == start && other.end == end

    override fun hashCode(): Int = start.hashCode() * HASH + end.hashCode()
}

/** 버튼 토큰 반지름을 높이 [height]에 대한 비율(0..0.5)로 바꾼 값. 캡슐이면 0.5다. [SideRoundShape]에 넘긴다. */
@Composable
@ReadOnlyComposable
internal fun buttonRoundFraction(height: Dp): Float {
    val shapes = currentShapes
    val radius = shapes.radius(shapes.button)
    return if (radius == Dp.Infinity || height <= Dp(0f)) HALF else (radius / height).coerceIn(0f, HALF)
}

/**
 * [token] 역할의 모양. 토큰이 캡슐이면 반지름 [capsule]로 깎은 둥근 사각형을 쓴다.
 * 캡슐로는 어색한 넓은 판이나 정사각형 칸이 기본 디자인에서 원래 모양을 지키고, 다른 디자인 언어에서는 그 반지름을 따르게 한다.
 */
@Composable
@ReadOnlyComposable
internal fun roundedShapeFor(capsule: Dp, token: KineticaShapes.() -> KShapeToken): Shape {
    val shapes = currentShapes
    val picked = shapes.token()
    return shapeOf(shapes.cornerOf(picked), shapes.radiusOr(picked, capsule))
}

/** [token]의 반지름. 캡슐이면 [capsule]이다. */
internal fun KineticaShapes.radiusOr(token: KShapeToken, capsule: Dp): Dp = radius(token).let { if (it == Dp.Infinity) capsule else it }

private const val HASH = 31
private const val HALF = 0.5f
private const val OPAQUE = 0.95f
private const val DARK_BACK = 0xFF1C1C1E
private const val TONAL_BACK = 0.16f
private const val READABLE_RATIO = 3f

/** 보통 크기 글자의 최소 대비(WCAG 1.4.3). 버튼 글자는 모두 이 기준이다. */
internal const val INK_TEXT_CONTRAST = 4.5f

/** 글자 없는 아이콘의 최소 대비(WCAG 1.4.11) */
internal const val INK_GRAPHIC_CONTRAST = 3f
private const val READABLE_SEARCH_STEPS = 12
private const val CONTRAST_OFFSET = 0.05f
private const val CHECK_X0 = 0.15f
private const val CHECK_Y0 = 0.52f
private const val CHECK_X1 = 0.4f
private const val CHECK_Y1 = 0.76f
private const val CHECK_X2 = 0.86f
private const val CHECK_Y2 = 0.26f
private const val CHECK_SPLIT = 0.35f
private const val CHEVRON_DROP = 0.28f
