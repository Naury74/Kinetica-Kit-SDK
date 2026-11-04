package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.floor
import kotlin.math.hypot

/**
 * [KScratchCard]의 상태. 긁은 자국과 긁은 비율, 다 열렸는지를 가진다. [rememberKScratchCardState]로 만든다.
 * 화면 회전 뒤에는 다 열렸는지만 남고 긁은 자국은 지워진다.
 */
@Stable
public class KScratchCardState internal constructor(revealed: Boolean) {
    /** 다 열렸는지. 긁은 비율이 문턱을 넘거나 [reveal]을 부르면 true가 된다. */
    public var isRevealed: Boolean by mutableStateOf(revealed)
        private set

    /** 긁은 넓이의 대략적인 비율(0..1). 덮개를 24×24 칸으로 나눠 칸 가운데가 긁혔는지로 센다. 다 열리면 1이다. */
    public var scratchedFraction: Float by mutableFloatStateOf(if (revealed) 1f else 0f)
        private set

    // 같은 경로 객체를 다시 넣어 그리기만 다시 하게 한다. 획마다 새 경로를 만들지 않는다.
    private val pathState = mutableStateOf(Path(), neverEqualPolicy())
    internal val path: Path get() = pathState.value
    internal var notified = revealed
    private val cells = BooleanArray(GRID * GRID)
    private var marked = 0

    /** 덮개를 모두 걷어 낸다. 이미 열렸으면 아무 일도 하지 않는다. */
    public fun reveal() {
        if (isRevealed) return
        isRevealed = true
        scratchedFraction = 1f
    }

    /** 긁은 자국을 지우고 다시 덮는다. */
    public fun reset() {
        path.reset()
        cells.fill(false)
        marked = 0
        scratchedFraction = 0f
        isRevealed = false
        notified = false
        pathState.value = pathState.value
    }

    /**
     * [from]에서 [to]까지 반지름 [radius]로 긁는다. [from]이 null이면 새 획을 시작한다. 긁힌 칸을 그때그때 더해 세므로
     * 전체 넓이를 다시 재지 않는다. 비율이 [threshold] 이상이 되면 다 연다.
     */
    internal fun scratch(from: Offset?, to: Offset, radius: Float, size: Size, threshold: Float) {
        if (isRevealed || size.width <= 0f || size.height <= 0f) return
        if (from == null) {
            path.moveTo(to.x, to.y)
            // 점 하나만 찍어도 둥근 끝으로 보이도록 아주 짧은 선을 긋는다.
            path.lineTo(to.x + DOT_NUDGE, to.y)
        } else {
            path.lineTo(to.x, to.y)
        }
        pathState.value = pathState.value
        val start = from ?: to
        val distance = hypot(to.x - start.x, to.y - start.y)
        val steps = (distance / (radius / 2)).toInt().coerceAtLeast(0) + 1
        for (i in 0..steps) {
            val f = i.toFloat() / steps
            markAround(Offset(start.x + (to.x - start.x) * f, start.y + (to.y - start.y) * f), radius, size)
        }
        scratchedFraction = marked.toFloat() / cells.size
        if (scratchedFraction >= threshold) reveal()
    }

    private fun markAround(point: Offset, radius: Float, size: Size) {
        val cw = size.width / GRID
        val ch = size.height / GRID
        val x0 = floor((point.x - radius) / cw).toInt().coerceAtLeast(0)
        val x1 = floor((point.x + radius) / cw).toInt().coerceAtMost(GRID - 1)
        val y0 = floor((point.y - radius) / ch).toInt().coerceAtLeast(0)
        val y1 = floor((point.y + radius) / ch).toInt().coerceAtMost(GRID - 1)
        for (gy in y0..y1) {
            for (gx in x0..x1) {
                val index = gy * GRID + gx
                if (cells[index]) continue
                if (hypot((gx + CELL_CENTER) * cw - point.x, (gy + CELL_CENTER) * ch - point.y) <= radius) {
                    cells[index] = true
                    marked++
                }
            }
        }
    }

    internal companion object {
        val Saver: Saver<KScratchCardState, Boolean> = Saver(save = { it.isRevealed }, restore = { KScratchCardState(it) })
    }
}

/** [KScratchCardState]를 만든다. */
@Composable
public fun rememberKScratchCardState(): KScratchCardState = rememberSaveable(saver = KScratchCardState.Saver) { KScratchCardState(false) }

private const val GRID = 24
private const val CELL_CENTER = 0.5f
private const val DOT_NUDGE = 0.01f
