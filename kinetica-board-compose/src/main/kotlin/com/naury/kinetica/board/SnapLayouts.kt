package com.naury.kinetica.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.geometry.Rect
import kotlin.math.ceil

/**
 * Windows 11 스냅 레이아웃처럼, 위젯을 보드 위쪽 가장자리로 끌면 뜨는 배치 틀 고르기판.
 * 틀마다 보이는 영역을 나누는 칸이 있고, 손가락을 칸에 올려 놓으면 그 칸 자리로 들어간다. 모든 값은 화면 폭 문서 단위다.
 */
internal object SnapLayouts {
    /** 보이는 영역 안쪽을 0..1 비율로 나눈 칸. */
    data class Fraction(val left: Double, val top: Double, val right: Double, val bottom: Double)

    /** 고르기판의 칸 하나: [layout]번째 틀의 [zone]번째 칸. [hit]은 고르기판 위 손가락이 닿는 자리다. */
    data class Slot(val layout: Int, val zone: Int, val hit: Rect)

    /** 고르기판 모양. [panel]은 판 전체, [thumbs]는 틀 그림 자리, [slots]는 칸이 닿는 자리다. */
    class Picker(val panel: Rect, val thumbs: List<Rect>, val slots: List<Slot>) {
        fun slotAt(x: Double, y: Double): Slot? = slots.firstOrNull { x in it.hit.left..it.hit.right && y in it.hit.top..it.hit.bottom }
    }

    private const val TWO_THIRDS = 2.0 / 3.0
    private const val ONE_THIRD = 1.0 / 3.0

    /** 반반, 2:1, 1:2, 3등분, 위아래, 큰 칸+작은 두 칸, 4분할. */
    val layouts: List<List<Fraction>> = listOf(
        listOf(Fraction(0.0, 0.0, 0.5, 1.0), Fraction(0.5, 0.0, 1.0, 1.0)),
        listOf(Fraction(0.0, 0.0, TWO_THIRDS, 1.0), Fraction(TWO_THIRDS, 0.0, 1.0, 1.0)),
        listOf(Fraction(0.0, 0.0, ONE_THIRD, 1.0), Fraction(ONE_THIRD, 0.0, 1.0, 1.0)),
        listOf(Fraction(0.0, 0.0, ONE_THIRD, 1.0), Fraction(ONE_THIRD, 0.0, TWO_THIRDS, 1.0), Fraction(TWO_THIRDS, 0.0, 1.0, 1.0)),
        listOf(Fraction(0.0, 0.0, 1.0, 0.5), Fraction(0.0, 0.5, 1.0, 1.0)),
        listOf(Fraction(0.0, 0.0, 0.5, 1.0), Fraction(0.5, 0.0, 1.0, 0.5), Fraction(0.5, 0.5, 1.0, 1.0)),
        listOf(Fraction(0.0, 0.0, 0.5, 0.5), Fraction(0.5, 0.0, 1.0, 0.5), Fraction(0.0, 0.5, 0.5, 1.0), Fraction(0.5, 0.5, 1.0, 1.0)),
    )

    /** 칸 [fraction]이 보이는 영역 [visible]에서 차지하는 자리. 가장자리와 칸 사이에 [gap]을 둔다. */
    fun zoneRect(fraction: Fraction, visible: Rect, gap: Double): Rect {
        val inner = Rect(visible.left + gap, visible.top + gap, visible.right - gap, visible.bottom - gap)
        val half = gap / 2.0
        fun x(f: Double) = inner.left + inner.width * f
        fun y(f: Double) = inner.top + inner.height * f
        return Rect(
            x(fraction.left) + if (fraction.left > 0.0) half else 0.0,
            y(fraction.top) + if (fraction.top > 0.0) half else 0.0,
            x(fraction.right) - if (fraction.right < 1.0) half else 0.0,
            y(fraction.bottom) - if (fraction.bottom < 1.0) half else 0.0,
        )
    }

    /** 보이는 영역 [visible] 위쪽에 놓을 고르기판. [unit]은 1dp의 문서 단위다. */
    fun picker(visible: Rect, gap: Double, unit: Double): Picker {
        val pad = PAD_DP * unit
        val inner = THUMB_INSET_DP * unit
        val panelLeft = visible.left + gap
        val panelWidth = visible.width - gap * 2
        val thumbWidth = (panelWidth - pad * (COLUMNS + 1)) / COLUMNS
        val thumbHeight = thumbWidth * THUMB_RATIO
        val rows = ceil(layouts.size / COLUMNS.toDouble()).toInt()
        val panelTop = visible.top + gap
        val panel = Rect(panelLeft, panelTop, panelLeft + panelWidth, panelTop + pad * (rows + 1) + thumbHeight * rows)
        val thumbs = layouts.indices.map { index ->
            val left = panelLeft + pad + (index % COLUMNS) * (thumbWidth + pad)
            val top = panelTop + pad + (index / COLUMNS) * (thumbHeight + pad)
            Rect(left, top, left + thumbWidth, top + thumbHeight)
        }
        val slots = layouts.flatMapIndexed { layout, zones ->
            val thumb = thumbs[layout]
            val area = Rect(thumb.left - inner, thumb.top - inner, thumb.right + inner, thumb.bottom + inner)
            zones.mapIndexed { zone, fraction -> Slot(layout, zone, zoneRect(fraction, area, inner * 2)) }
        }
        return Picker(panel, thumbs, slots)
    }

    private const val COLUMNS = 4
    private const val PAD_DP = 10.0
    private const val THUMB_INSET_DP = 3.0
    private const val THUMB_RATIO = 0.72
}

/** 끄는 동안 손가락이 올라간 고르기판 칸. 둘 다 `null`이면 고르기판만 떠 있다. */
internal data class PickerHover(val layout: Int?, val zone: Int?)

/** 고르기판을 그린다. 손가락이 올라간 칸은 강조색, 같은 틀의 다른 칸은 옅게 보인다. */
@Composable
internal fun SnapPickerView(picker: SnapLayouts.Picker, hover: PickerHover, zoom: Float, colors: BoardColors) {
    val panel = picker.panel
    Canvas(
        Modifier
            .rectPlacement(panel, zoom)
            .shadow(16.dp, RoundedCornerShape(20.dp)),
    ) {
        val scale = zoom
        fun Rect.offset() = Offset(((left - panel.left) * scale).toFloat(), ((top - panel.top) * scale).toFloat())
        fun Rect.size() = Size((width * scale).toFloat(), (height * scale).toFloat())
        drawRoundRect(Color.White.copy(alpha = PANEL_ALPHA), cornerRadius = CornerRadius(20.dp.toPx()))
        picker.thumbs.forEach { thumb -> drawRoundRect(THUMB, thumb.offset(), thumb.size(), CornerRadius(8.dp.toPx())) }
        picker.slots.forEach { slot ->
            val color = when {
                slot.layout == hover.layout && slot.zone == hover.zone -> colors.accent
                slot.layout == hover.layout -> colors.accent.copy(alpha = SAME_LAYOUT_ALPHA)
                else -> ZONE
            }
            drawRoundRect(color, slot.hit.offset(), slot.hit.size(), CornerRadius(4.dp.toPx()))
        }
    }
}

private const val PANEL_ALPHA = 0.94f
private const val SAME_LAYOUT_ALPHA = 0.35f
private val THUMB = Color(0xFFE9EBF2)
private val ZONE = Color(0xFFC9CDD9)
