package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 상태 아이콘. 상태 색으로 채운 원(경고는 둥근 삼각형) 안에 기호를 그린다. 정보는 i, 성공은 체크, 경고와 오류는 느낌표다.
 * [KStatus.Neutral]은 표면 글자 색 원 안에 i를 그린다. 토스트, 스낵바, 배너, 알림 상자가 같은 아이콘을 쓴다.
 *
 * @param contentDescription TalkBack이 읽을 이름. null이면 장식으로 보고 읽지 않는다. 옆 글자에 상태가 이미 드러나면 null로 둔다.
 * @param size 아이콘 지름. 글자 옆이면 18~22dp가 알맞다.
 * @param color 채울 색. [Color.Unspecified]면 상태 색을 쓴다.
 */
@Composable
public fun KStatusIcon(
    status: KStatus,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 20.dp,
    color: Color = Color.Unspecified,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val fill = if (color.isSpecified()) color else status.feedbackColor(surface)
    val ink = if (status == KStatus.Warning && !color.isSpecified()) Color(WARNING_INK) else onStatus(status, surface, fill)
    val label = Modifier.clearAndSetSemantics { contentDescription?.let { this.contentDescription = it } }
    Spacer(
        modifier
            .size(size)
            .then(label)
            .drawWithCache {
                val s = this.size.minDimension
                val glyph = statusGlyph(status, s)
                val back = if (status == KStatus.Warning) roundedTriangle(s) else null
                val stroke = Stroke(s * GLYPH_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind {
                    if (back != null) drawPath(back, fill) else drawCircle(fill, s / 2, Offset(s / 2, s / 2))
                    drawPath(glyph.lines, ink, style = stroke)
                    glyph.dot?.let { drawCircle(ink, s * DOT_RADIUS, it) }
                }
            },
    )
}

/** 상태 아이콘 이름("정보", "성공" 등). 아이콘과 글자를 하나로 묶어 읽게 할 때 앞에 붙인다. */
@Composable
@ReadOnlyComposable
internal fun statusLabel(status: KStatus): String? = when (status) {
    KStatus.Neutral -> null
    KStatus.Info -> stringResource(R.string.kinetica_ui_feedback_info)
    KStatus.Success -> stringResource(R.string.kinetica_ui_feedback_success)
    KStatus.Warning -> stringResource(R.string.kinetica_ui_feedback_warning)
    KStatus.Error -> stringResource(R.string.kinetica_ui_feedback_error)
}

/** [surface] 위에서 이 상태가 쓸 색. 정보는 강조색, 성공과 오류는 테마 색, 경고는 주황이다. */
@Composable
@ReadOnlyComposable
internal fun KStatus.feedbackColor(surface: SurfaceStyle): Color {
    val colors = KineticaTheme.colors
    return when (this) {
        KStatus.Neutral -> surface.contentColor
        KStatus.Info -> colors.accent
        KStatus.Success -> colors.positive
        KStatus.Warning -> Color(WARNING_COLOR)
        KStatus.Error -> colors.destructive
    }
}

/** 상태 색으로 채운 면 위 글자 색 */
@Composable
@ReadOnlyComposable
internal fun onStatus(status: KStatus, surface: SurfaceStyle, fill: Color): Color {
    val colors = KineticaTheme.colors
    return when (status) {
        KStatus.Neutral -> surface.inkBackground()
        KStatus.Info -> colors.onAccent
        KStatus.Success -> colors.onPositive
        KStatus.Warning -> Color(WARNING_INK)
        KStatus.Error -> colors.onDestructive
    }.let { if (contrast(it, fill) < MIN_GLYPH_CONTRAST) readableOn(it, fill) else it }
}

/** 색 역할을 가장 가까운 상태로 옮긴다. 옛 토스트의 `tone`을 아이콘으로 그릴 때 쓴다. */
internal fun KTone.asStatus(): KStatus = when (this) {
    KTone.Accent -> KStatus.Info
    KTone.Neutral -> KStatus.Neutral
    KTone.Destructive -> KStatus.Error
    KTone.Positive -> KStatus.Success
}

private fun Color.isSpecified(): Boolean = this != Color.Unspecified

/** 기호 선과 점. 좌표는 [s] 크기 정사각형 기준이다. */
private class Glyph(val lines: Path, val dot: Offset?)

private fun statusGlyph(status: KStatus, s: Float): Glyph = when (status) {
    KStatus.Success -> Glyph(
        Path().apply {
            moveTo(s * CHECK_X0, s * CHECK_Y0)
            lineTo(s * CHECK_X1, s * CHECK_Y1)
            lineTo(s * CHECK_X2, s * CHECK_Y2)
        },
        null,
    )

    KStatus.Warning -> Glyph(line(s, WARN_TOP, WARN_BOTTOM), Offset(s / 2, s * WARN_DOT))

    KStatus.Error -> Glyph(line(s, BANG_TOP, BANG_BOTTOM), Offset(s / 2, s * BANG_DOT))

    KStatus.Info, KStatus.Neutral -> Glyph(line(s, INFO_TOP, INFO_BOTTOM), Offset(s / 2, s * INFO_DOT))
}

private fun line(s: Float, top: Float, bottom: Float): Path = Path().apply {
    moveTo(s / 2, s * top)
    lineTo(s / 2, s * bottom)
}

/** 모서리를 둥글린 삼각형. 꼭짓점 사이를 이차 곡선으로 이어 경고 표지처럼 보이게 한다. */
private fun roundedTriangle(s: Float): Path {
    val points = listOf(Offset(s / 2, s * TRI_TOP), Offset(s * TRI_RIGHT, s * TRI_BOTTOM), Offset(s * TRI_LEFT, s * TRI_BOTTOM))
    val r = s * TRI_ROUND
    return Path().apply {
        points.indices.forEach { i ->
            val p = points[i]
            val prev = points[(i + points.size - 1) % points.size]
            val next = points[(i + 1) % points.size]
            val a = p + (prev - p).unit() * r
            val b = p + (next - p).unit() * r
            if (i == 0) moveTo(a.x, a.y) else lineTo(a.x, a.y)
            quadraticTo(p.x, p.y, b.x, b.y)
        }
        close()
    }
}

private fun Offset.unit(): Offset = this / getDistance()

private const val WARNING_COLOR = 0xFFFF9F0A
private const val WARNING_INK = 0xFF1F1300
private const val MIN_GLYPH_CONTRAST = 3f
private const val GLYPH_STROKE = 0.11f
private const val DOT_RADIUS = 0.065f
private const val CHECK_X0 = 0.29f
private const val CHECK_Y0 = 0.52f
private const val CHECK_X1 = 0.44f
private const val CHECK_Y1 = 0.66f
private const val CHECK_X2 = 0.72f
private const val CHECK_Y2 = 0.36f
private const val INFO_TOP = 0.46f
private const val INFO_BOTTOM = 0.72f
private const val INFO_DOT = 0.3f
private const val BANG_TOP = 0.27f
private const val BANG_BOTTOM = 0.53f
private const val BANG_DOT = 0.71f
private const val WARN_TOP = 0.38f
private const val WARN_BOTTOM = 0.6f
private const val WARN_DOT = 0.76f
private const val TRI_TOP = 0.06f
private const val TRI_BOTTOM = 0.92f
private const val TRI_LEFT = 0.03f
private const val TRI_RIGHT = 0.97f
private const val TRI_ROUND = 0.16f
