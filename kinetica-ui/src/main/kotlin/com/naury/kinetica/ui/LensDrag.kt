package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 선택 인디케이터를 손가락으로 끌어 옮기는 렌즈 조작의 상태. [grab]은 렌즈가 부푼 정도(0..1, 출렁이면 조금 넘는다)로,
 * 그리기 단계에서만 읽는다. 세그먼트 컨트롤과 떠 있는 탭 바가 함께 쓴다.
 */
@Stable
internal class IndicatorLens(val indicator: LiquidIndicator, val scope: CoroutineScope) {
    val grab = Animatable(0f)
    var motion: KineticaMotion = KineticaMotion.Bouncy
    var still: Boolean = false
    var haptics: HapticFeedback? = null

    /**
     * 끌어 놓을 때마다 1씩 는다. 인디케이터를 선택 칸으로 옮기는 효과의 키로 쓴다.
     * 호스트가 놓은 칸을 고르지 않으면 선택이 그대로라 그 효과가 다시 돌지 않으므로, 이 값으로 다시 돌려 선택 칸으로 되돌린다.
     */
    var drops by mutableIntStateOf(0)
        private set

    /** 칸마다 물리 좌표 px의 왼쪽, 오른쪽 끝을 이어 둔 배열. 끌기를 시작할 때 한 번 읽는다. 구성할 때마다 새로 넣는다. */
    var cells: () -> FloatArray = { FloatArray(0) }

    /** 끌어 놓은 칸 위치를 받는다. 구성할 때마다 새로 넣는다. */
    var onSettle: (Int) -> Unit = {}

    /** 렌즈 진행 값. 출렁여도 0..1 밖으로 나가지 않는다. */
    fun progress(): Float = grab.value.coerceIn(0f, 1f)

    fun press(down: Boolean) {
        val target = if (down) 1f else 0f
        scope.launch { if (still) grab.snapTo(target) else grab.animateTo(target, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    }

    /** 인디케이터 왼쪽 끝을 [left]로, 폭은 [width]로 바로 옮긴다. */
    fun follow(left: Float, width: Float) {
        scope.launch {
            indicator.left.snapTo(left)
            indicator.right.snapTo(left + width)
        }
    }

    /** [cells]의 [index]번 칸으로 인디케이터를 미끄러뜨린다. */
    fun settle(cells: FloatArray, index: Int) {
        scope.launch { indicator.moveTo(cells[index * 2], cells[index * 2 + 1], motion) }
        press(down = false)
        drops++
    }
}

/**
 * [cells](칸마다 물리 좌표 px의 왼쪽, 오른쪽 끝을 이어 둔 배열)에서 가운데가 [center]에 가장 가까운 칸 위치.
 * 칸이 없으면 -1이다.
 */
internal fun nearestCell(cells: FloatArray, center: Float): Int {
    var best = -1
    var distance = Float.MAX_VALUE
    for (i in 0 until cells.size / 2) {
        val d = abs((cells[i * 2] + cells[i * 2 + 1]) / 2f - center)
        if (d < distance) {
            distance = d
            best = i
        }
    }
    return best
}

/**
 * 인디케이터 위를 누르면 렌즈가 부풀고, 가로로 끌면 인디케이터가 손가락을 따라가며, 떼면 가장 가까운 칸에 붙어 [IndicatorLens.onSettle]을 부른다.
 * 인디케이터 밖을 누르거나 움직이지 않고 떼면 칸의 탭 처리에 맡긴다. 이 수정자의 좌표는 인디케이터를 그리는 좌표와 같아야 한다.
 */
internal fun Modifier.indicatorDrag(lens: IndicatorLens): Modifier = pointerInput(lens) {
    awaitEachGesture {
        // 칸의 탭 처리가 누름을 먼저 소비하므로 소비된 누름도 받는다.
        val down = awaitFirstDown(requireUnconsumed = false)
        val left = lens.indicator.left.value
        val right = lens.indicator.right.value
        if (right <= left || down.position.x !in left..right) return@awaitEachGesture
        lens.press(down = true)
        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
        if (start == null) {
            lens.press(down = false)
            return@awaitEachGesture
        }
        val edges = lens.cells()
        if (edges.size < 2) {
            lens.press(down = false)
            return@awaitEachGesture
        }
        val width = right - left
        val grip = down.position.x - left
        val min = edges.min()
        val max = edges.max()
        var shownLeft = left
        fun move(x: Float) {
            shownLeft = (x - grip).coerceIn(min, (max - width).coerceAtLeast(min))
            lens.follow(shownLeft, width)
        }
        move(start.position.x)
        horizontalDrag(start.id) { change ->
            move(change.position.x)
            change.consume()
        }
        val index = nearestCell(edges, shownLeft + width / 2f)
        if (index >= 0) {
            lens.haptics?.performHapticFeedback(HapticFeedbackType.SegmentTick)
            lens.settle(edges, index)
            lens.onSettle(index)
        } else {
            lens.press(down = false)
        }
    }
}

/**
 * 인디케이터 렌즈에 쓸 수정자 둘. [outer]는 렌즈를 그릴 바깥 요소(판 모양까지 포함하도록 표면 수정자보다 앞)에,
 * [inner]는 인디케이터를 그리는 좌표와 같은 안쪽 요소에 붙인다. 렌즈가 꺼져 있으면 둘 다 빈 수정자다.
 */
internal class IndicatorLensParts(val outer: Modifier, val inner: Modifier, private val lens: IndicatorLens?) {
    /** 렌즈가 부푼 만큼 인디케이터를 옅게 할 정도(0..1). 렌즈가 그 자리를 맡으므로 두 겹으로 보이지 않게 한다. */
    fun clear(): Float = (lens?.progress() ?: 0f) * INDICATOR_CLEAR

    /** [IndicatorLens.drops]. 인디케이터를 선택 칸으로 옮기는 효과의 키에 넣는다. 렌즈가 꺼져 있으면 0이다. */
    val drops: Int get() = lens?.drops ?: 0
}

private val NoIndicatorLens = IndicatorLensParts(Modifier, Modifier, null)

/**
 * [on]이면 [indicator]를 끌어 옮기는 렌즈를 만든다. [insetX]는 바깥 요소 왼쪽에서 안쪽 요소까지 거리다. 렌즈 가운데는 바깥 요소의 세로 가운데에 둔다.
 * [cells]와 [onSelect]는 매번 새로 넣으므로 바뀐 칸 경계와 콜백을 쓴다.
 */
@Composable
internal fun rememberIndicatorLens(
    on: Boolean,
    indicator: LiquidIndicator,
    insetX: Dp,
    cells: () -> FloatArray,
    onSelect: (Int) -> Unit,
): IndicatorLensParts {
    if (!on) return NoIndicatorLens
    val scope = rememberCoroutineScope()
    val lens = remember(indicator, scope) { IndicatorLens(indicator, scope) }
    lens.motion = KineticaTheme.design.motion
    lens.still = isReducedMotion()
    lens.haptics = kHapticFeedback()
    lens.cells = cells
    lens.onSettle = onSelect
    val inset = with(LocalDensity.current) { insetX.toPx() }
    val outer = Modifier.lens(
        progress = lens::progress,
        center = { size -> Offset(inset + (indicator.left.value + indicator.right.value) / 2f, size.height / 2f) },
        lensSize = { size ->
            Size(indicator.right.value - indicator.left.value + LENS_WIDER.toPx(), size.height + LENS_TALLER.toPx())
        },
        optics = IndicatorOptics,
    )
    return IndicatorLensParts(outer, Modifier.indicatorDrag(lens), lens)
}

private val IndicatorOptics = LensOptics(INDICATOR_ZOOM, 1f, KLensDefaults.Tint)
private const val INDICATOR_ZOOM = 1.18f
private const val INDICATOR_CLEAR = 0.7f
private val LENS_WIDER = 12.dp
private val LENS_TALLER = 8.dp
