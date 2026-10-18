package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * [KSlider]의 기준값 스냅. 손으로 끌거나 탭한 값이 [value]에서 [range] 안이면 [value]로 붙고, 붙는 순간 햅틱을 한 번 낸다.
 * 키보드와 TalkBack으로 바꾼 값에는 붙지 않는다. 한 칸이 [range]보다 작으면 기준값을 지나가지 못하기 때문이다.
 *
 * @param value 붙을 기준값. 슬라이더의 `valueRange` 밖이면 가까운 끝으로 맞춘다. 유한한 수가 아니면 [IllegalArgumentException]을 던진다.
 * @param range 붙는 거리로, 값과 같은 단위다. 0이면 정확히 [value]에 닿을 때만 햅틱을 낸다. 음수거나 유한한 수가 아니면
 *   [IllegalArgumentException]을 던진다.
 */
@Immutable
public class KSliderSnap(public val value: Float, public val range: Float) {
    init {
        require(value.isFinite()) { "value는 유한한 수여야 합니다." }
        require(range.isFinite() && range >= 0f) { "range는 0 이상의 유한한 수여야 합니다." }
    }

    override fun equals(other: Any?): Boolean = other is KSliderSnap && other.value == value && other.range == range

    override fun hashCode(): Int = 31 * value.hashCode() + range.hashCode()

    override fun toString(): String = "KSliderSnap(value=$value, range=$range)"
}

/** [KSlider]의 값 배지를 둘 자리. */
public enum class KSliderBadgePosition {
    /** 손잡이 바로 위. 손잡이를 따라 움직이고, 슬라이더 높이가 배지 높이만큼 늘어난다. */
    AboveThumb,

    /** 트랙 끝 옆. RTL에서는 왼쪽이다. */
    End,
}

/**
 * 볼륨 조절 같은 곳에 어울리는 슬라이더. 잡으면 트랙이 굵어지고 손잡이가 커진다. 끝을 넘어서 드래그하면
 * 트랙이 고무줄처럼 늘어났다가 손을 떼면 튕겨 돌아온다. 트랙을 탭하면 그 값으로 이동한다. 가로로 움직이기 시작해야 드래그로 보므로
 * 세로 스크롤 목록 안에 두어도 스크롤을 막지 않는다. 키보드 방향키로 조절할 수 있고, TalkBack은 값과 범위를 읽고 값을 바꿀 수 있다.
 *
 * 사진 보정처럼 기준값이 있는 조절에는 [snap]과 [resetValue]를 준다.
 *
 * ```kotlin
 * KSlider(
 *     exposure, { exposure = it }, valueRange = -1f..1f, onValueChangeFinished = ::commit,
 *     snap = KSliderSnap(0f, 0.04f), resetValue = 0f,
 *     valueBadge = { Text("%+.2f".format(it)) }, excludeSystemGestures = true,
 * )
 * ```
 *
 * @param value [valueRange] 안의 값. 범위를 벗어나면 가까운 끝 값으로 그린다.
 * @param valueRange 값 범위. 끝이 시작보다 크지 않으면 [IllegalArgumentException]을 던진다.
 * @param steps 양 끝 사이의 눈금 수. 0이면 연속 값이고, 1 이상이면 눈금 위치에만 멈춘다. 음수면 [IllegalArgumentException]을 던진다.
 * @param enabled `false`면 터치·키보드·TalkBack으로 값을 바꿀 수 없고 채워진 구간을 흐리게 그린다.
 * @param onValueChangeFinished 손을 떼거나 키보드·TalkBack으로 값을 바꾼 뒤, 또는 초기화한 뒤 한 번 호출된다.
 * @param accent 채워진 구간의 색. 기본값은 [KineticaTheme]의 `accent`다.
 * @param thumb 손잡이 모양. 기본값은 [KineticaDesign.controls]의 [KineticaControls.sliderThumb]다.
 * @param snap 기준값 스냅. 기본값 null이면 붙지 않는다.
 * @param resetValue 두 번 탭하거나 TalkBack의 '초기화' 동작을 고르면 돌아갈 값으로, [valueRange] 밖이면 가까운 끝으로 맞춘다.
 *   null이면 [snap]의 기준값으로 돌아가고, 둘 다 null이면 초기화가 없다. [isReducedMotion]이 true면 손잡이가 미끄러지지 않고 바로 옮겨 간다.
 * @param valueBadge 값을 보여 줄 배지. 지금 값을 받는다. TalkBack은 배지를 읽지 않고 슬라이더의 값을 읽는다.
 * @param badgePosition [valueBadge]를 둘 자리.
 * @param inactiveTrackColor 채워지지 않은 트랙 색. [Color.Unspecified]면 [surface]에 맞춘 옅은 색이다.
 * @param excludeSystemGestures `true`면 슬라이더 영역을 시스템 제스처에서 빼서, 화면 가장자리에서 끌어도 뒤로 가기가 되지 않는다.
 *   Android 10(API 29) 미만에서는 아무 일도 하지 않는다.
 * @param rubberBand `false`면 끝을 넘어 끌어도 트랙이 늘어나지 않는다.
 */
@Composable
public fun KSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    accent: Color = KineticaTheme.colors.accent,
    surface: SurfaceStyle = KineticaTheme.surface,
    thumb: KSliderThumb = KineticaTheme.design.controls.sliderThumb,
    snap: KSliderSnap? = null,
    resetValue: Float? = null,
    valueBadge: (@Composable (value: Float) -> Unit)? = null,
    badgePosition: KSliderBadgePosition = KSliderBadgePosition.AboveThumb,
    inactiveTrackColor: Color = Color.Unspecified,
    excludeSystemGestures: Boolean = false,
    rubberBand: Boolean = true,
) {
    require(steps >= 0) { "steps는 0 이상이어야 합니다." }
    require(valueRange.endInclusive > valueRange.start) { "valueRange의 끝이 시작보다 커야 합니다." }
    val paint = sliderPaint(if (enabled) accent else surface.contentColor.copy(alpha = DISABLED_ALPHA), surface, inactiveTrackColor)
    val scope = rememberCoroutineScope()
    val model = remember { SliderModel(scope) }
    model.value = value
    model.range = valueRange
    model.steps = steps
    model.onChange = onValueChange
    model.onFinished = onValueChangeFinished
    model.snap = snap
    model.resetTarget = (resetValue ?: snap?.value)?.coerceIn(valueRange)
    model.rubberBand = rubberBand
    model.haptics = kHapticFeedback()
    model.reduced = isReducedMotion()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val grab = remember { Animatable(0f) }
    val stretch = remember { Animatable(0f) }
    val fraction = model.fractionOf(value)
    val core = Modifier
        .then(if (excludeSystemGestures) Modifier.systemGestureExclusion() else Modifier)
        .sliderSemantics(model, enabled, stringResource(R.string.kinetica_ui_slider_reset))
        // onFocusChanged는 focusable보다 앞에 있어야 그 포커스 변화를 받는다.
        .onFocusChanged { focused -> scope.launch { grab.animateTo(if (focused.isFocused) 1f else 0f) } }
        .focusable(enabled)
        .onKeyEvent { event -> enabled && event.type == KeyEventType.KeyDown && model.key(event.key, rtl) }
        .then(if (enabled) Modifier.sliderGesture(model, rtl, grab, stretch) else Modifier)
        .drawBehind { drawSlider(SliderLook(model.glide.shown(fraction), grab.value, stretch.value, rtl, thumb), paint) }
    SliderFrame(modifier, core, valueBadge, badgePosition, value) { model.glide.shown(fraction) }
}

/** 이전 시그니처를 컴파일된 호출자를 위해 남긴다. */
@Deprecated("기준값 스냅, 초기화, 값 배지를 더한 KSlider를 씁니다.", level = DeprecationLevel.HIDDEN)
@Composable
public fun KSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    accent: Color = KineticaTheme.colors.accent,
    surface: SurfaceStyle = KineticaTheme.surface,
    thumb: KSliderThumb = KineticaTheme.design.controls.sliderThumb,
) {
    KSlider(value, onValueChange, modifier, valueRange, steps, enabled, onValueChangeFinished, accent, surface, thumb, snap = null)
}

/** 배지가 없으면 슬라이더만, 있으면 [position]에 맞춰 배지와 함께 놓는다. [shownFraction]은 지금 손잡이가 그려진 비율이다. */
@Composable
private fun SliderFrame(
    modifier: Modifier,
    core: Modifier,
    badge: (@Composable (Float) -> Unit)?,
    position: KSliderBadgePosition,
    value: Float,
    shownFraction: () -> Float,
) {
    if (badge == null) {
        Box(modifier.fillMaxWidth().height(SLIDER_HEIGHT).then(core))
        return
    }
    // 값은 슬라이더 노드가 읽으므로 배지는 TalkBack에서 숨겨 같은 값을 두 번 읽지 않게 한다.
    val badgeBox = Modifier.clearAndSetSemantics {}
    if (position == KSliderBadgePosition.End) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(SLIDER_HEIGHT).then(core))
            Spacer(Modifier.width(BADGE_GAP))
            Box(badgeBox) { badge(value) }
        }
        return
    }
    Layout(
        content = {
            Box(badgeBox) { badge(value) }
            Box(Modifier.fillMaxWidth().height(SLIDER_HEIGHT).then(core))
        },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val track = measurables[1].measure(constraints.copy(minHeight = 0))
        val label = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val gap = BADGE_GAP.roundToPx()
        layout(track.width, label.height + gap + track.height) {
            val inset = THUMB_WIDTH.toPx() / 2
            val along = inset + (track.width - inset * 2) * shownFraction()
            val center = if (layoutDirection == LayoutDirection.Rtl) track.width - along else along
            val x = (center - label.width / 2f).roundToInt().coerceIn(0, (track.width - label.width).coerceAtLeast(0))
            // 손잡이의 실제 x에 맞추므로 RTL에서 다시 뒤집지 않게 place를 쓴다.
            label.place(x, 0)
            track.place(0, label.height + gap)
        }
    }
}

/**
 * 스냅이나 초기화로 값이 건너뛸 때 손잡이를 [from]에서 [to]로 미끄러지게 그린다. 그리는 쪽이 받은 비율이 [to]일 때만 쓰므로,
 * 그 사이 손가락이 다른 값으로 옮기면 바로 손가락을 따른다.
 */
private class SliderGlide {
    private var from by mutableFloatStateOf(0f)
    private var to by mutableFloatStateOf(0f)
    private var progress by mutableFloatStateOf(1f)
    private var job: Job? = null

    fun start(scope: CoroutineScope, from: Float, to: Float) {
        job?.cancel()
        if (from == to) {
            progress = 1f
            return
        }
        this.from = from
        this.to = to
        progress = 0f
        job = scope.launch {
            try {
                animate(0f, 1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> progress = v }
            } finally {
                progress = 1f
            }
        }
    }

    fun shown(fraction: Float): Float = if (progress < 1f && abs(fraction - to) < GLIDE_MATCH) from + (to - from) * progress else fraction
}

/**
 * 슬라이더의 값 계산과 동작. 구성할 때마다 최신 인자를 받으므로, 한 번 만든 터치 처리 코루틴에서도 지금 값과 콜백을 쓴다.
 */
private class SliderModel(val scope: CoroutineScope) {
    var value = 0f
    var range: ClosedFloatingPointRange<Float> = 0f..1f
    var steps = 0
    var onChange: (Float) -> Unit = {}
    var onFinished: (() -> Unit)? = null
    var snap: KSliderSnap? = null
    var resetTarget: Float? = null
    var rubberBand = true
    var haptics: HapticFeedback? = null
    var reduced = false
    val glide = SliderGlide()

    // 지금 기준값에 붙어 있는지. 붙는 순간을 가려 햅틱을 한 번만 낸다.
    private var snapped = false

    private val snapValue: Float? get() = snap?.value?.coerceIn(range)

    fun fractionOf(v: Float): Float = ((v.coerceIn(range) - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

    fun valueAt(raw: Float): Float {
        val quantized = if (steps == 0) raw else (raw * (steps + 1)).roundToInt().toFloat() / (steps + 1)
        return range.start + quantized * (range.endInclusive - range.start)
    }

    fun begin() {
        snapped = snapValue == value
    }

    /** 손으로 정한 값. 키보드와 TalkBack 값에는 스냅을 걸지 않는다. */
    fun pointerChange(next: Float) {
        val target = snapValue
        val distance = snap?.range ?: 0f
        val result = if (target != null && abs(next - target) <= distance) target else next
        val inside = target != null && result == target
        if (inside && !snapped) {
            haptics?.performHapticFeedback(HapticFeedbackType.SegmentTick)
            if (!reduced) glide.start(scope, fractionOf(next), fractionOf(result))
        }
        snapped = inside
        onChange(result)
    }

    fun finish() {
        onFinished?.invoke()
    }

    /** 초기화할 값이 있으면 그 값으로 옮기고 true를, 없으면 false를 돌려준다. */
    fun resetNow(): Boolean {
        val target = resetTarget ?: return false
        if (!reduced) glide.start(scope, fractionOf(value), fractionOf(target))
        snapped = snapValue == target
        onChange(target)
        finish()
        return true
    }

    fun setProgress(target: Float): Boolean {
        onChange(valueAt(fractionOf(target)))
        finish()
        return true
    }

    /** 방향키 한 번. 눈금이 있으면 한 칸, 없으면 범위의 5%를 움직인다. 슬라이더 키가 아니면 false. */
    fun key(key: Key, rtl: Boolean): Boolean {
        val delta = keyStep(key, rtl) ?: return false
        val stepFraction = if (steps > 0) 1f / (steps + 1) else KEY_FRACTION
        onChange(valueAt((fractionOf(value) + delta * stepFraction).coerceIn(0f, 1f)))
        finish()
        return true
    }
}

private fun Modifier.sliderSemantics(model: SliderModel, enabled: Boolean, resetLabel: String): Modifier {
    val info = ProgressBarRangeInfo(model.value.coerceIn(model.range), model.range, model.steps)
    val canReset = enabled && model.resetTarget != null
    return semantics {
        progressBarRangeInfo = info
        if (!enabled) disabled()
        setProgress { target -> enabled && model.setProgress(target) }
        if (canReset) customActions = listOf(CustomAccessibilityAction(resetLabel) { model.resetNow() })
    }
}

/**
 * 가로로 터치 슬롭을 넘으면 드래그를 시작하고, 움직이지 않고 떼면 탭으로 본다. 세로로 먼저 움직이면 부모 스크롤에 넘긴다.
 * 초기화할 값이 있으면 두 번째 탭은 초기화다.
 */
private fun Modifier.sliderGesture(model: SliderModel, rtl: Boolean, grab: Animatable<Float, *>, stretch: Animatable<Float, *>): Modifier =
    pointerInput(model, rtl, model.steps, model.range) {
        val scope = model.scope
        // 앞 탭을 뗀 시각과 자리. 다음 탭이 시간과 거리 안에 들어오면 두 번 탭이다.
        var lastTapUp = Long.MIN_VALUE
        var lastTapAt = Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown()
            model.begin()
            val width = size.width.toFloat()
            val inset = THUMB_WIDTH.toPx() / 2
            val usable = (width - inset * 2).coerceAtLeast(1f)
            fun rawAt(x: Float): Float = ((if (rtl) width - x else x) - inset) / usable
            val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
            if (start == null) {
                // 움직이지 않고 뗐으면 탭이다. 세로 스크롤이 가져갔으면(소비됨) 아무것도 하지 않는다.
                val up = currentEvent.changes.firstOrNull { it.id == down.id }
                if (up != null && !up.pressed && !up.isConsumed) {
                    val second = lastTapUp != Long.MIN_VALUE &&
                        down.uptimeMillis - lastTapUp <= viewConfiguration.doubleTapTimeoutMillis &&
                        (up.position - lastTapAt).getDistance() <= DOUBLE_TAP_SLOP.toPx()
                    if (second && model.resetNow()) {
                        lastTapUp = Long.MIN_VALUE
                    } else {
                        model.pointerChange(model.valueAt(rawAt(up.position.x).coerceIn(0f, 1f)))
                        model.finish()
                        lastTapUp = up.uptimeMillis
                        lastTapAt = up.position
                    }
                }
                return@awaitEachGesture
            }
            lastTapUp = Long.MIN_VALUE
            scope.launch { grab.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
            model.pointerChange(model.valueAt(rawAt(start.position.x).coerceIn(0f, 1f)))
            horizontalDrag(start.id) { change ->
                val raw = rawAt(change.position.x)
                model.pointerChange(model.valueAt(raw.coerceIn(0f, 1f)))
                if (model.rubberBand) {
                    // 끝을 넘은 거리가 커질수록 늘어나는 비율을 줄여 고무줄처럼 버티게 한다.
                    val over = (if (raw < 0f) raw else (raw - 1f).coerceAtLeast(0f)) * usable
                    scope.launch { stretch.snapTo(over / (abs(over) + RUBBER_SOFTNESS * density)) }
                }
                change.consume()
            }
            scope.launch { grab.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
            scope.launch {
                stretch.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
            model.finish()
        }
    }

/** 슬라이더 색. [active]는 채워진 구간과 막대 손잡이 색이고, [track]이 지정되지 않았으면 [surface]의 올라온 면 색을 옅게 쓴다. */
@Composable
internal fun sliderPaint(active: Color, surface: SurfaceStyle, track: Color = Color.Unspecified): ThumbPaint = ThumbPaint(
    active,
    surface.contentColor,
    surface.fillColor.copy(alpha = 1f),
    surface.isDark,
    KineticaTheme.design.borderWidth,
    if (track.isSpecified) track else surface.raisedFill(TRACK_ALPHA),
)

/** 방향키를 값 변화 방향(+1 증가, -1 감소)으로 바꾼다. RTL에서는 좌우가 뒤집힌다. 슬라이더 키가 아니면 `null`. */
private fun keyStep(key: Key, rtl: Boolean): Int? = when (key) {
    Key.DirectionRight -> if (rtl) -1 else 1
    Key.DirectionLeft -> if (rtl) 1 else -1
    Key.DirectionUp -> 1
    Key.DirectionDown -> -1
    else -> null
}

/** 그리기에 쓰는 슬라이더 상태. 채운 비율, 잡은 정도(0..1), 끝을 넘어 늘어난 정도(음수면 시작 쪽), RTL 여부를 담는다. */
private class SliderLook(val fraction: Float, val grab: Float, val stretch: Float, val rtl: Boolean, val thumb: KSliderThumb)

private fun DrawScope.drawSlider(look: SliderLook, paint: ThumbPaint) {
    val fraction = look.fraction
    val grab = look.grab
    val stretch = look.stretch
    val rtl = look.rtl
    val inset = THUMB_WIDTH.toPx() / 2
    val trackHeight = TRACK_HEIGHT.toPx() * (1f + TRACK_GROW * grab)
    // 고무줄처럼 보이도록 넘친 쪽 반대편 끝을 고정점으로 가로로 늘리고 세로는 살짝 가늘게 한다.
    val pull = abs(stretch) * RUBBER_MAX
    val pivot = if ((stretch < 0f) != rtl) size.width else 0f
    scale(1f + pull, 1f - pull * RUBBER_THIN, Offset(pivot, size.height / 2)) {
        val filled = inset + (size.width - inset * 2) * fraction
        val center = if (rtl) size.width - filled else filled
        drawSliderTrack(look.thumb, center, trackHeight, paint, rtl)
        drawSliderThumb(look.thumb, Offset(center, size.height / 2), grab, paint)
    }
}

private val SLIDER_HEIGHT = 44.dp
private val TRACK_HEIGHT = 6.dp
private const val TRACK_GROW = 1f
private const val TRACK_ALPHA = 0.16f
private val THUMB_WIDTH = 36.dp
private const val RUBBER_SOFTNESS = 120f
private const val RUBBER_MAX = 0.06f
private const val RUBBER_THIN = 0.8f
private const val KEY_FRACTION = 0.05f
private const val DISABLED_ALPHA = 0.3f
private val BADGE_GAP = 4.dp
private val DOUBLE_TAP_SLOP = 48.dp
private const val GLIDE_MATCH = 1e-4f
