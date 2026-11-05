package com.naury.kinetica.ui

import android.os.SystemClock
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 원형 트랙 위 손잡이 하나를 돌려 값을 고르는 슬라이더. 타이머, 온도, 볼륨 다이얼처럼 가운데에 값을 크게 보여 주는 자리에 쓴다.
 *
 * [startAngle]에서 시계 방향으로 [sweepAngle]만큼이 트랙이고, 손잡이나 트랙을 누른 채 돌리면 손가락 각도를 따라간다. 끝을 지나
 * 반대쪽 끝으로 넘어가지 않고 끝에 머문다. 눈금을 넘을 때마다 짧은 햅틱을, 끝에 닿으면 분명한 햅틱을 낸다([LocalKineticaHaptics]를 따른다).
 * 원은 시계 방향이라 RTL에서도 방향이 같다. 키보드 방향키(오른쪽과 위가 증가)로 한 칸씩, TalkBack은 범위 정보와 값 설정 동작으로 조절한다.
 * TalkBack은 [label]과 [formatValue] 글자만 읽고 가운데 [content]는 읽지 않는다. 트랙 밖(가운데)을 누른 손가락은 받지 않는다.
 * 크기를 정하지 않으면 지름 200dp 이상, 가로세로 같은 크기다.
 *
 * ```kotlin
 * var minutes by remember { mutableFloatStateOf(25f) }
 * KCircularSlider(minutes, { minutes = it }, "타이머", valueRange = 0f..60f, step = 1f, ticks = 12)
 * ```
 *
 * @param value 현재 값. [valueRange] 밖이면 가까운 끝으로, NaN이면 범위 시작으로 그린다.
 * @param onValueChange 값이 바뀔 때마다 받는다. 늘 [valueRange] 안이고 [step]에 맞춘 값이다.
 * @param label TalkBack이 읽는 이름. 기본 가운데 내용에서는 값 아래에 작게 보인다.
 * @param valueRange 값 범위. 끝이 시작보다 큰 유한한 범위가 아니면 [IllegalArgumentException]을 던진다.
 * @param step 값을 붙일 간격(값 단위). 0이면 연속 값이다. 음수면 [IllegalArgumentException]을 던진다.
 * @param ticks 트랙 안쪽에 그릴 눈금 칸 수. 0이면 눈금이 없다. 눈금을 넘을 때 햅틱을 낸다. 음수면 [IllegalArgumentException]을 던진다.
 * @param startAngle 트랙 시작 각도(도). 3시 방향이 0이고 시계 방향으로 는다. 기본값 -90은 12시다.
 * @param sweepAngle 트랙 길이(도). 0보다 크고 360 이하여야 하며, 아니면 [IllegalArgumentException]을 던진다.
 * @param onValueChangeFinished 손을 뗀 뒤, 또는 방향키와 TalkBack으로 바꾼 뒤 한 번 호출된다.
 * @param formatValue 가운데에 보여 주고 TalkBack이 읽을 값 글자
 * @param thickness 트랙 두께. 손잡이 지름은 이보다 조금 작다.
 * @param enabled `false`면 터치, 키보드, TalkBack으로 값을 바꿀 수 없고 흐리게 그린다.
 * @param content 링 안쪽 가운데 슬롯. 지금 값을 받는다. 기본값은 큰 값 글자와 [label]이다.
 */
@Composable
public fun KCircularSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = PERCENT_RANGE,
    step: Float = 0f,
    ticks: Int = 0,
    startAngle: Float = TWELVE_O_CLOCK,
    sweepAngle: Float = 360f,
    onValueChangeFinished: (() -> Unit)? = null,
    formatValue: (Float) -> String = { it.roundToInt().toString() },
    thickness: Dp = 24.dp,
    enabled: Boolean = true,
    accent: Color = KineticaTheme.colors.accent,
    surface: SurfaceStyle = KineticaTheme.surface,
    content: @Composable BoxScope.(value: Float) -> Unit = { CircleCenterValue(formatValue(it), label, surface) },
) {
    val dial = remember(valueRange, startAngle, sweepAngle, step, ticks) {
        CircleDial(valueRange, startAngle, sweepAngle, step, ticks, periodic = false)
    }
    val shown = if (value.isNaN()) valueRange.start else dial.fit(value)
    val shownState = rememberUpdatedState(shown)
    val haptic = kHapticFeedback()
    val control = remember { SingleCircleControl() }
    control.dial = dial
    control.ticker = remember(haptic) { CircleTicker(haptic) }
    control.value = shown
    control.onChange = onValueChange
    control.onFinished = onValueChangeFinished
    var focused by remember { mutableStateOf(false) }
    val ink = circleInk(accent, surface)
    val text = formatValue(shown)
    Box(
        modifier
            .defaultMinSize(CIRCLE_MIN, CIRCLE_MIN)
            .aspectRatio(1f)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = text
                progressBarRangeInfo = ProgressBarRangeInfo(shown, valueRange, stepsOf(dial))
                if (enabled) setProgress { control.jump(it) } else disabled()
            }
            .onKeyEvent { event ->
                val direction = if (enabled && event.type == KeyEventType.KeyDown) circleKey(event.key) else null
                if (direction != null) control.jump(control.value + direction * dial.keyStep)
                direction != null
            }
            .then(if (enabled) Modifier.singleCircleDrag(control, thickness) else Modifier)
            .drawBehind {
                val m = circleMetrics(size, thickness.toPx(), 0f)
                val v = shownState.value
                drawCircleTrack(dial, m, ink, dial.startAngle, dial.relOf(v))
                drawCircleThumb(m, dial.angleOf(v), ink, null, focused)
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor) {
            Box(Modifier.padding(thickness + CENTER_GAP), contentAlignment = Alignment.Center) { content(shown) }
        }
    }
}

/**
 * 원형 트랙 위 손잡이 둘로 시작과 끝을 고르는 범위 슬라이더. 수면 시간(취침 23시 ~ 기상 7시)처럼 범위가 0을 지나 한 바퀴 돌아 이어질 수 있다.
 *
 * 범위의 두 끝([valueRange]의 시작과 끝)은 원 위 같은 자리이고, 시작 손잡이에서 시계 방향으로 끝 손잡이까지가 고른 구간이다.
 * 손잡이를 끌면 그 손잡이만, 두 손잡이 사이 원호를 끌면 길이를 그대로 둔 채 둘 다 돈다. 눈금을 넘을 때 짧은 햅틱을 낸다.
 * TalkBack에는 손잡이마다 노드가 따로 있어 [startLabel], [endLabel]과 값을 읽고 값을 바꿀 수 있으며, 키보드로는 손잡이마다
 * 초점을 옮겨 방향키(오른쪽과 위가 증가)로 조절한다. 기본 가운데 내용은 손잡이 노드와 같은 내용이라 TalkBack이 읽지 않는다.
 *
 * ```kotlin
 * KCircularRangeSlider(
 *     bed, wake, { s, e -> bed = s; wake = e }, "취침", "기상",
 *     valueRange = 0f..24f, step = 0.25f, ticks = 24, formatValue = ::hourText,
 * )
 * ```
 *
 * @param start 시작 값. [valueRange] 밖이면 한 바퀴 돌려 맞추고, NaN이면 범위 시작으로 본다.
 * @param end 끝 값. [start]보다 작으면 0을 지나 이어지는 구간이다. 맞추는 규칙은 [start]와 같다.
 * @param onValueChange 새 시작과 끝을 받는다. 둘 다 `valueRange.start` 이상 `valueRange.endInclusive` 미만이고 [step]에 맞춘 값이다.
 * @param startLabel 시작 손잡이 이름. TalkBack이 읽고 기본 가운데 내용에도 보인다.
 * @param endLabel 끝 손잡이 이름
 * @param valueRange 한 바퀴의 값 범위. 끝이 시작보다 큰 유한한 범위가 아니면 [IllegalArgumentException]을 던진다.
 * @param step 값을 붙일 간격. 0이면 연속 값이다. 음수면 [IllegalArgumentException]을 던진다.
 * @param ticks 한 바퀴의 눈금 칸 수. 0이면 눈금이 없다. 음수면 [IllegalArgumentException]을 던진다.
 * @param startAngle [valueRange] 시작 값이 놓이는 각도(도). 3시 방향이 0이고, 기본값 -90은 12시다.
 * @param startIcon 시작 손잡이 가운데 아이콘. 강조색으로 그린다.
 * @param endIcon 끝 손잡이 가운데 아이콘
 * @param content 링 안쪽 가운데 슬롯. 시작과 끝 값을 받는다.
 */
@Composable
public fun KCircularRangeSlider(
    start: Float,
    end: Float,
    onValueChange: (start: Float, end: Float) -> Unit,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = HOURS_RANGE,
    step: Float = 0f,
    ticks: Int = 0,
    startAngle: Float = TWELVE_O_CLOCK,
    onValueChangeFinished: (() -> Unit)? = null,
    formatValue: (Float) -> String = { it.roundToInt().toString() },
    thickness: Dp = 32.dp,
    startIcon: ImageVector? = null,
    endIcon: ImageVector? = null,
    enabled: Boolean = true,
    accent: Color = KineticaTheme.colors.accent,
    surface: SurfaceStyle = KineticaTheme.surface,
    content: @Composable BoxScope.(start: Float, end: Float) -> Unit = { s, e ->
        CircleCenterRange(CircleRangeText(startLabel, formatValue(s), endLabel, formatValue(e)), surface)
    },
) {
    val dial = remember(valueRange, startAngle, step, ticks) { CircleDial(valueRange, startAngle, FULL_TURN, step, ticks, periodic = true) }
    val s = if (start.isNaN()) valueRange.start else dial.wrap(start)
    val e = if (end.isNaN()) valueRange.start else dial.wrap(end)
    val startState = rememberUpdatedState(s)
    val endState = rememberUpdatedState(e)
    val haptic = kHapticFeedback()
    val control = remember { RangeCircleControl() }
    control.dial = dial
    control.ticker = remember(haptic) { CircleTicker(haptic) }
    control.start = s
    control.end = e
    control.onChange = onValueChange
    control.onFinished = onValueChangeFinished
    val focus = remember { mutableStateOf(-1) }
    val ink = circleInk(accent, surface)
    val icons = CircleIcons(startIcon?.let { rememberVectorPainter(it) }, endIcon?.let { rememberVectorPainter(it) })
    Box(
        modifier
            .defaultMinSize(CIRCLE_MIN, CIRCLE_MIN)
            .aspectRatio(1f)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .then(if (enabled) Modifier.rangeCircleDrag(control, thickness) else Modifier)
            .drawBehind {
                val m = circleMetrics(size, thickness.toPx(), 0f)
                val a = startState.value
                val b = endState.value
                drawCircleTrack(dial, m, ink, dial.angleOf(a), normalizeDegrees(dial.relOf(b) - dial.relOf(a)))
                drawCircleThumb(m, dial.angleOf(a), ink, icons.start, focus.value == 0)
                drawCircleThumb(m, dial.angleOf(b), ink, icons.end, focus.value == 1)
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor) {
            Box(Modifier.padding(thickness + CENTER_GAP), contentAlignment = Alignment.Center) { content(s, e) }
        }
        val node = CircleHandleSpec(dial, control, thickness, enabled, formatValue)
        CircleHandleNode(node, 0, startLabel, startState, focus)
        CircleHandleNode(node, 1, endLabel, endState, focus)
    }
}

/** 기본 가운데 내용: 큰 값과 작은 이름 */
@Composable
private fun CircleCenterValue(text: String, label: String, surface: SurfaceStyle) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val weight = KineticaTheme.design.label(FontWeight.Bold)
        ThemedText(
            text,
            style = TextStyle(fontSize = 40.sp, fontWeight = weight, fontFeatureSettings = "tnum", textAlign = TextAlign.Center),
        )
        ThemedText(
            label,
            style = TextStyle(color = surface.muted(MUTED_ALPHA), fontSize = 13.sp, textAlign = TextAlign.Center),
            maxLines = 2,
        )
    }
}

private class CircleRangeText(val startLabel: String, val start: String, val endLabel: String, val end: String)

/** 기본 범위 가운데 내용. 손잡이 노드가 같은 내용을 읽으므로 TalkBack에서는 숨긴다. */
@Composable
private fun CircleCenterRange(text: CircleRangeText, surface: SurfaceStyle) {
    Column(
        Modifier.clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CircleRangeLine(text.startLabel, text.start, surface)
        CircleRangeLine(text.endLabel, text.end, surface)
    }
}

@Composable
private fun CircleRangeLine(label: String, value: String, surface: SurfaceStyle) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ThemedText(label, style = TextStyle(color = surface.muted(MUTED_ALPHA), fontSize = 12.sp, textAlign = TextAlign.Center))
        val weight = KineticaTheme.design.label(FontWeight.SemiBold)
        ThemedText(
            value,
            style = TextStyle(fontSize = 22.sp, fontWeight = weight, fontFeatureSettings = "tnum", textAlign = TextAlign.Center),
        )
    }
}

private class CircleIcons(val start: VectorPainter?, val end: VectorPainter?)

@Composable
private fun circleInk(accent: Color, surface: SurfaceStyle): CircleInk =
    CircleInk(accent, surface.raisedFill(TRACK_ALPHA), surface.muted(TICK_ALPHA), Color.White, accent)

/** TalkBack에 알릴 단계 수. 간격이 있으면 양 끝 사이 멈출 자리 수다. */
private fun stepsOf(dial: CircleDial): Int = if (dial.step > 0f) ((dial.span / dial.step).roundToInt() - 1).coerceAtLeast(0) else 0

/** 손잡이 하나짜리 원형 슬라이더의 최신 값과 콜백. 재구성마다 바꿔 넣어 제스처를 다시 시작하지 않고도 새 값을 쓴다. */
private class SingleCircleControl {
    lateinit var dial: CircleDial
    lateinit var ticker: CircleTicker
    var value = 0f
    var onChange: (Float) -> Unit = {}
    var onFinished: (() -> Unit)? = null

    // 손가락이 가리키는 상대 각도. 값은 간격에 붙지만 이 각도는 붙이지 않아야 천천히 돌려도 다음 칸으로 넘어간다.
    var rel = 0f

    fun emit(next: Float) {
        if (next == value) return
        ticker.step(dial, value, next, SystemClock.uptimeMillis())
        value = next
        onChange(next)
    }

    fun jump(target: Float): Boolean {
        if (target.isNaN()) return false
        ticker.reset(dial, value)
        emit(dial.snap(target))
        onFinished?.invoke()
        return true
    }
}

private fun Modifier.singleCircleDrag(control: SingleCircleControl, thickness: Dp): Modifier = circleDrag(
    control,
    onDown = { position ->
        val m = circleMetrics(size.toSize(), thickness.toPx(), CIRCLE_TOUCH_SLACK.toPx())
        val dial = control.dial
        if (!m.onBand(position)) return@circleDrag false
        val rel = dial.relAt(position, m.center)
        val current = dial.relOf(control.value)
        // 손잡이를 잡았으면 지금 값에서 이어 간다. 한 바퀴짜리 원에서는 끝값과 처음 값의 손잡이가 같은 자리라 각도만 보면 반대 끝으로 튄다.
        control.rel = if (angleGap(rel, current) <= GRAB_DEGREES) current else dial.follow(rel, if (rel <= dial.sweep) rel else current)
        control.ticker.reset(dial, control.value)
        control.emit(dial.snap(dial.valueOfRel(control.rel)))
        true
    },
    onMove = { change ->
        val m = circleMetrics(size.toSize(), thickness.toPx(), 0f)
        val dial = control.dial
        control.rel = dial.follow(dial.relAt(change.position, m.center), control.rel)
        control.emit(dial.snap(dial.valueOfRel(control.rel)))
    },
    onEnd = { control.onFinished?.invoke() },
)

/** 범위 슬라이더에서 잡은 대상 */
private enum class CircleGrab { Start, End, Both }

private class RangeCircleControl {
    lateinit var dial: CircleDial
    lateinit var ticker: CircleTicker
    var start = 0f
    var end = 0f
    var onChange: (Float, Float) -> Unit = { _, _ -> }
    var onFinished: (() -> Unit)? = null
    var grab = CircleGrab.Both
    var lastRel = 0f

    // 원호를 끌 때 처음 값과 쌓인 이동량. 간격에 붙일 때 둘이 같은 만큼 움직여 길이가 변하지 않게 한다.
    var originStart = 0f
    var originEnd = 0f
    var shift = 0f

    fun emit(nextStart: Float, nextEnd: Float) {
        if (nextStart == start && nextEnd == end) return
        val now = SystemClock.uptimeMillis()
        if (nextStart != start) ticker.step(dial, start, nextStart, now) else ticker.step(dial, end, nextEnd, now)
        start = nextStart
        end = nextEnd
        onChange(nextStart, nextEnd)
    }

    fun jump(index: Int, target: Float): Boolean {
        if (target.isNaN()) return false
        val next = dial.snap(target)
        ticker.reset(dial, if (index == 0) start else end)
        if (index == 0) emit(next, end) else emit(start, next)
        onFinished?.invoke()
        return true
    }

    fun moveTo(rel: Float) {
        when (grab) {
            CircleGrab.Start -> emit(dial.snap(dial.valueOfRel(rel)), end)

            CircleGrab.End -> emit(start, dial.snap(dial.valueOfRel(rel)))

            CircleGrab.Both -> {
                shift += shortestDelta(rel - lastRel) / dial.sweep * dial.span
                lastRel = rel
                val moved = if (dial.step > 0f) (shift / dial.step).roundToInt() * dial.step else shift
                emit(dial.wrap(originStart + moved), dial.wrap(originEnd + moved))
            }
        }
    }
}

private fun Modifier.rangeCircleDrag(control: RangeCircleControl, thickness: Dp): Modifier = circleDrag(
    control,
    onDown = { position ->
        val m = circleMetrics(size.toSize(), thickness.toPx(), CIRCLE_TOUCH_SLACK.toPx())
        val grab = control.grabAt(position, m) ?: return@circleDrag false
        control.grab = grab
        control.lastRel = control.dial.relAt(position, m.center)
        control.originStart = control.start
        control.originEnd = control.end
        control.shift = 0f
        control.ticker.reset(control.dial, if (grab == CircleGrab.End) control.end else control.start)
        true
    },
    onMove = { change ->
        val m = circleMetrics(size.toSize(), thickness.toPx(), 0f)
        control.moveTo(control.dial.relAt(change.position, m.center))
    },
    onEnd = { control.onFinished?.invoke() },
)

/** [position]에서 잡을 대상. 손잡이 가까이면 더 가까운 손잡이, 두 손잡이 사이 원호 위면 둘 다, 아니면 `null`. */
private fun RangeCircleControl.grabAt(position: Offset, m: CircleMetrics): CircleGrab? {
    val ps = pointOn(m.center, m.radius, dial.angleOf(start))
    val pe = pointOn(m.center, m.radius, dial.angleOf(end))
    val ds = hypot(position.x - ps.x, position.y - ps.y)
    val de = hypot(position.x - pe.x, position.y - pe.y)
    return when {
        // 두 손잡이가 겹쳐 있으면 끝 손잡이를 잡아 구간을 벌릴 수 있게 한다.
        minOf(ds, de) <= m.hit -> if (de <= ds) CircleGrab.End else CircleGrab.Start

        !m.onBand(position) -> null

        normalizeDegrees(dial.relAt(position, m.center) - dial.relOf(start)) <= normalizeDegrees(dial.relOf(end) - dial.relOf(start)) ->
            CircleGrab.Both

        else -> null
    }
}

private class CircleHandleSpec(
    val dial: CircleDial,
    val control: RangeCircleControl,
    val thickness: Dp,
    val enabled: Boolean,
    val format: (Float) -> String,
)

/** 손잡이 하나의 TalkBack과 키보드 노드. 손잡이 자리에 놓이고 손가락은 받지 않는다. */
@Composable
private fun CircleHandleNode(spec: CircleHandleSpec, index: Int, name: String, value: State<Float>, focus: MutableState<Int>) {
    val shown = value.value
    val text = spec.format(shown)
    val range = spec.dial.range
    // 범위는 한 바퀴 이어지지만 TalkBack의 올리고 내리기는 범위 끝에서 멈추므로, 끝을 넘어 도는 동작을 따로 둔다.
    val increase = stringResource(R.string.kinetica_ui_increase)
    val decrease = stringResource(R.string.kinetica_ui_decrease)
    Box(
        Modifier
            .circleHandlePlacement(spec.dial, value, spec.thickness)
            .onFocusChanged {
                if (it.isFocused) {
                    focus.value = index
                } else if (focus.value == index) {
                    focus.value = -1
                }
            }
            .focusable(spec.enabled)
            .clearAndSetSemantics {
                contentDescription = name
                stateDescription = text
                progressBarRangeInfo = ProgressBarRangeInfo(shown, range, stepsOf(spec.dial))
                if (spec.enabled) {
                    setProgress { spec.control.jump(index, it) }
                    customActions = listOf(
                        CustomAccessibilityAction(increase) { spec.control.jump(index, shown + spec.dial.keyStep) },
                        CustomAccessibilityAction(decrease) { spec.control.jump(index, shown - spec.dial.keyStep) },
                    )
                } else {
                    disabled()
                }
            }
            .onKeyEvent { event ->
                val direction = if (spec.enabled && event.type == KeyEventType.KeyDown) circleKey(event.key) else null
                if (direction != null) spec.control.jump(index, shown + direction * spec.dial.keyStep)
                direction != null
            },
    )
}

private fun Modifier.circleHandlePlacement(dial: CircleDial, value: State<Float>, thickness: Dp): Modifier =
    layout { measurable, constraints ->
        val stroke = thickness.toPx()
        val side = (stroke + CIRCLE_TOUCH_SLACK.toPx()).roundToInt()
        val placeable = measurable.measure(Constraints.fixed(side, side))
        layout(constraints.maxWidth, constraints.maxHeight) {
            // 자리는 배치 단계에서 읽어 손잡이가 움직여도 이 노드를 다시 구성하지 않는다.
            val m = circleMetrics(Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()), stroke, 0f)
            val c = pointOn(m.center, m.radius, dial.angleOf(value.value))
            placeable.place((c.x - side / 2f).roundToInt(), (c.y - side / 2f).roundToInt())
        }
    }

private val CIRCLE_MIN = 200.dp
private val PERCENT_RANGE = 0f..100f
private val HOURS_RANGE = 0f..24f
private const val TWELVE_O_CLOCK = -90f
private val CENTER_GAP = 8.dp
private const val FULL_TURN = 360f
private const val DISABLED_ALPHA = 0.4f
private const val TRACK_ALPHA = 0.12f
private const val TICK_ALPHA = 0.45f
private const val MUTED_ALPHA = 0.6f

/** 두 각도(도) 사이의 가까운 쪽 차이(0..180). */
private fun angleGap(a: Float, b: Float): Float {
    val d = ((a - b) % FULL_CIRCLE + FULL_CIRCLE) % FULL_CIRCLE
    return minOf(d, FULL_CIRCLE - d)
}

private const val GRAB_DEGREES = 10f
private const val FULL_CIRCLE = 360f
