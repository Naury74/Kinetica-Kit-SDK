package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** [KColorPicker]의 기본값. */
public object KColorPickerDefaults {
    /** 기본 프리셋 색. 자주 쓰는 선명한 색 열 가지와 흰색, 검정이다. */
    public val Presets: List<Color> = listOf(
        Color(0xFFFF3B30),
        Color(0xFFFF9500),
        Color(0xFFFFCC00),
        Color(0xFF34C759),
        Color(0xFF00C7BE),
        Color(0xFF32ADE6),
        Color(0xFF007AFF),
        Color(0xFF5856D6),
        Color(0xFFAF52DE),
        Color(0xFFFF2D55),
        Color(0xFFFFFFFF),
        Color(0xFF1C1C1E),
    )
}

/**
 * 글래스 색상 선택기. 바깥 고리에서 색조를, 가운데 사각형에서 채도(가로)와 명도(세로)를 고른다. 아래에는 투명도 슬라이더,
 * HEX 입력란, 최근 색과 프리셋 칩이 있다. 손잡이는 지금 색을 담은 유리 구슬처럼 그리고, 잡을 때와 색조가 30도 넘어갈 때마다 햅틱을 준다.
 * 고리와 사각형 안에서 끄는 동안에는 바깥 스크롤이 움직이지 않는다.
 *
 * TalkBack은 "밝은 파랑, #7FB2FF"처럼 대략적인 색 이름과 HEX를 읽고, 색조와 밝기, 선명도를 한 단계씩 바꾸는 동작을 제공한다.
 * 키보드로는 Tab으로 고리, 사각형, 투명도 슬라이더 순서로 초점을 옮긴다. 고리에서는 방향키로 색조를 돌리고, 사각형에서는 왼쪽과 오른쪽으로
 * 선명도를, 위와 아래로 밝기를 바꾼다. 초점이 있는 손잡이에는 고리를 그린다.
 * 최근 색은 이 선택기 안에서만 기억하며 화면 회전 뒤에도 남는다.
 *
 * ```kotlin
 * var color by remember { mutableStateOf(Color(0xFF0A84FF)) }
 * KColorPicker(color, { color = it })
 * ```
 *
 * @param color 지금 색. sRGB 색 공간으로 다룬다.
 * @param onColorChange 새 색을 받는다. 끄는 동안 계속 호출된다.
 * @param presets 프리셋 칩으로 보일 색. 비어 있으면 프리셋 줄을 그리지 않는다.
 * @param showAlpha 투명도 슬라이더를 보일지. `false`여도 [color]의 알파는 그대로 유지한다.
 * @param showHexInput HEX 입력란을 보일지. 6자리(RRGGBB)나 8자리(AARRGGBB)를 다 입력하면 바로 적용한다.
 */
@Composable
public fun KColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    presets: List<Color> = KColorPickerDefaults.Presets,
    showAlpha: Boolean = true,
    showHexInput: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val latest by rememberUpdatedState(onColorChange)
    val model = remember { PickerModel(color) }
    model.sync(color)
    var recents by rememberSaveable { mutableStateOf(IntArray(0)) }
    val addRecent = { c: Color ->
        val argb = c.toArgb()
        if (presets.none { it.toArgb() == argb }) {
            recents =
                (intArrayOf(argb) + recents.filter { it != argb }).take(MAX_RECENTS).toIntArray()
        }
    }
    val emit = { next: Color ->
        model.emitted = next
        latest(next)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
        HueWheel(
            model,
            { next ->
                model.hsv = next
                emit(next.toColor())
            },
            { addRecent(model.hsv.toColor()) },
        )
        if (showAlpha) {
            ColorAlphaSlider(
                color,
                { alpha ->
                    model.hsv = model.hsv.copy(a = alpha)
                    emit(color.copy(alpha = alpha))
                },
                { addRecent(model.hsv.toColor()) },
                Modifier.widthIn(max = WHEEL_MAX).padding(horizontal = SLIDER_PADDING),
            )
        }
        if (showHexInput) {
            ColorHexField(color, { next ->
                model.sync(next)
                emit(next)
                addRecent(next)
            }, surface, Modifier.widthIn(max = WHEEL_MAX))
        }
        SwatchRow(color, recents, presets, surface) { picked ->
            model.sync(picked)
            emit(picked)
        }
    }
}

/**
 * 최근 색과 프리셋 칩을 가로로 스크롤하는 한 줄에 놓는다. 둘 다 있으면 사이에 구분선을 둔다. 프리셋은 지금 알파를 유지한다.
 * 폭이 남으면 위의 색 바퀴와 같은 축에 오도록 가운데에 모은다.
 */
@Composable
private fun SwatchRow(color: Color, recents: IntArray, presets: List<Color>, surface: SurfaceStyle, onPick: (Color) -> Unit) {
    if (recents.isEmpty() && presets.isEmpty()) return
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SwatchItems(color, recents, presets, surface, onPick) }
}

@Composable
private fun SwatchItems(color: Color, recents: IntArray, presets: List<Color>, surface: SurfaceStyle, onPick: (Color) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        recents.forEach { argb -> ColorSwatch(Color(argb), color.toArgb() == argb) { onPick(Color(argb)) } }
        if (recents.isNotEmpty() && presets.isNotEmpty()) {
            Box(Modifier.padding(horizontal = DIVIDER_PADDING).width(1.dp).height(DIVIDER_HEIGHT).divider(surface.contentColor))
        }
        presets.forEach { preset ->
            val target = preset.copy(alpha = color.alpha)
            ColorSwatch(preset, color.toArgb() == target.toArgb()) { onPick(target) }
        }
    }
}

private fun Modifier.divider(ink: Color): Modifier = drawWithCache { onDrawBehind { drawRect(ink.copy(alpha = DIVIDER_ALPHA)) } }

/** 색조(0..360 미만), 채도(0..1), 명도(0..1), 알파(0..1). */
@Immutable
internal data class PickerHsv(val h: Float, val s: Float, val v: Float, val a: Float) {
    fun toColor(): Color = Color.hsv(((h % FULL_TURN) + FULL_TURN) % FULL_TURN, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f), a.coerceIn(0f, 1f))
}

/** 화면에 그리는 HSV 값. 무채색에서도 색조를 잃지 않도록 마지막으로 내보낸 색과 같으면 바깥 값을 다시 계산하지 않는다. */
private class PickerModel(initial: Color) {
    var hsv by mutableStateOf(initial.toPickerHsv())
    var emitted: Color = initial

    /** 키보드 초점이 있는 부분. 그리기 단계에서 읽는다. */
    var focused: HueWheelPart? by mutableStateOf(null)

    fun sync(color: Color) {
        if (color != emitted) {
            hsv = color.toPickerHsv(fallbackHue = hsv.h)
            emitted = color
        }
    }
}

internal enum class HueWheelPart { Ring, Square }

@Composable
private fun HueWheel(model: PickerModel, onChange: (PickerHsv) -> Unit, onFinished: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val ringGrab = remember { Animatable(0f) }
    val squareGrab = remember { Animatable(0f) }
    val latestChange by rememberUpdatedState(onChange)
    val latestFinished by rememberUpdatedState(onFinished)
    val hsv = model.hsv
    val title = stringResource(R.string.kinetica_ui_color_picker)
    val description = colorDescription(hsv.toColor())
    val actions = wheelActions(hsv) { next ->
        latestChange(next)
        latestFinished()
    }
    Box(
        Modifier
            // fillMaxWidth가 먼저 오면 고정 폭 제약이 되어 최대 폭이 먹지 않는다. 태블릿 가로에서 바퀴가 화면 폭만큼 커지지 않게 최대 폭을 먼저 건다.
            .widthIn(max = WHEEL_MAX)
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics {
                contentDescription = title
                stateDescription = description
                customActions = actions
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val geometry = HueWheelGeometry(size.width.toFloat(), size.height.toFloat(), RING_WIDTH.toPx(), RING_GAP.toPx())
                    val part = geometry.partAt(down.position) ?: return@awaitEachGesture
                    down.consume()
                    // View 부모(NestedScrollView 등)는 소비 여부를 보지 않고 가로채므로 끄는 동안 막아 둔다.
                    view.holdParent(true)
                    val grab = if (part == HueWheelPart.Ring) ringGrab else squareGrab
                    scope.grab(grab, 1f)
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    try {
                        trackWheel(down, part, geometry, model) { next, crossed ->
                            if (crossed) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            latestChange(next)
                        }
                    } finally {
                        view.holdParent(false)
                    }
                    scope.grab(grab, 0f)
                    latestFinished()
                }
            }
            .drawWheel(model, ringGrab, squareGrab),
    ) {
        for (part in HueWheelPart.entries) {
            WheelKeys(part, model, { latestChange(it) }, { latestFinished() })
        }
    }
}

/** 손을 뗄 때까지 [down] 손가락을 따라 [part]의 값을 바꾼다. 고리에서 색조가 눈금을 넘으면 `crossed`가 `true`다. */
private suspend fun AwaitPointerEventScope.trackWheel(
    down: PointerInputChange,
    part: HueWheelPart,
    geometry: HueWheelGeometry,
    model: PickerModel,
    onChange: (PickerHsv, crossed: Boolean) -> Unit,
) {
    val tracker = HueTicker(model.hsv.h)
    var position: Offset? = down.position
    while (position != null) {
        val next = geometry.apply(part, position, model.hsv)
        val crossed = part == HueWheelPart.Ring && tracker.crossed(next.h)
        if (next != model.hsv || crossed) onChange(next, crossed)
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
        change?.consume()
        position = change?.takeIf { it.pressed }?.position
    }
}

/** 고리나 사각형 하나를 키보드 초점 대상으로 만든다. 화면 읽기에는 따로 나타나지 않고, 화면 읽기는 부모의 사용자 지정 동작을 쓴다. */
@Composable
private fun BoxScope.WheelKeys(part: HueWheelPart, model: PickerModel, onChange: (PickerHsv) -> Unit, onFinished: () -> Unit) {
    Spacer(
        Modifier
            .matchParentSize()
            .onFocusChanged {
                if (it.isFocused) {
                    model.focused = part
                } else if (model.focused == part) {
                    model.focused = null
                }
            }
            .onKeyEvent { event ->
                val next = wheelKeyStep(part, event.key, model.hsv) ?: return@onKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> onChange(next)
                    KeyEventType.KeyUp -> onFinished()
                }
                true
            }
            .focusTarget(),
    )
}

/** 방향키 한 번으로 바뀐 색. 고리는 오른쪽과 위로 색조를 올리고, 사각형은 가로로 선명도, 세로로 밝기를 바꾼다. 다른 키면 `null`. */
internal fun wheelKeyStep(part: HueWheelPart, key: Key, hsv: PickerHsv): PickerHsv? {
    val sign = when (key) {
        Key.DirectionRight, Key.DirectionUp -> 1f
        Key.DirectionLeft, Key.DirectionDown -> -1f
        else -> return null
    }
    return when (part) {
        HueWheelPart.Ring -> hsv.copy(h = ((hsv.h + sign * HUE_KEY_STEP) % FULL_TURN + FULL_TURN) % FULL_TURN)

        HueWheelPart.Square -> if (key == Key.DirectionLeft || key == Key.DirectionRight) {
            hsv.copy(s = (hsv.s + sign * COLOR_KEY_STEP).coerceIn(0f, 1f))
        } else {
            hsv.copy(v = (hsv.v + sign * COLOR_KEY_STEP).coerceIn(0f, 1f))
        }
    }
}

private fun CoroutineScope.grab(anim: Animatable<Float, *>, target: Float) {
    launch { anim.animateTo(target, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
}

/** 색조가 [HUE_TICK]도 경계를 넘을 때를 알려 햅틱 눈금으로 쓴다. */
private class HueTicker(start: Float) {
    private var bucket = (start / HUE_TICK).toInt()

    fun crossed(hue: Float): Boolean {
        val next = (hue / HUE_TICK).toInt()
        val changed = next != bucket
        bucket = next
        return changed
    }
}

/** 고리와 가운데 사각형의 위치. 사각형은 고리 안쪽 원에 내접한다. */
private class HueWheelGeometry(width: Float, height: Float, val ring: Float, gap: Float) {
    val center = Offset(width / 2, height / 2)
    val outer = minOf(width, height) / 2 - ring * EDGE_ROOM
    val ringRadius = outer - ring / 2
    val half = (outer - ring - gap) / sqrt(2f)
    val left = center.x - half
    val top = center.y - half
    val side = half * 2

    fun partAt(point: Offset): HueWheelPart? {
        val distance = hypot(point.x - center.x, point.y - center.y)
        val inSquare = point.x in left - TOUCH_SLACK * ring..left + side + TOUCH_SLACK * ring &&
            point.y in top - TOUCH_SLACK * ring..top + side + TOUCH_SLACK * ring
        return when {
            distance >= outer - ring * (1f + TOUCH_SLACK) && distance <= outer + ring * TOUCH_SLACK -> HueWheelPart.Ring
            inSquare -> HueWheelPart.Square
            else -> null
        }
    }

    fun apply(part: HueWheelPart, point: Offset, hsv: PickerHsv): PickerHsv = when (part) {
        HueWheelPart.Ring -> {
            val degrees = Math.toDegrees(atan2(point.y - center.y, point.x - center.x).toDouble()).toFloat()
            hsv.copy(h = (degrees + FULL_TURN) % FULL_TURN)
        }

        HueWheelPart.Square -> hsv.copy(
            s = ((point.x - left) / side).coerceIn(0f, 1f),
            v = (1f - (point.y - top) / side).coerceIn(0f, 1f),
        )
    }

    fun hueHandle(hue: Float): Offset {
        val radians = Math.toRadians(hue.toDouble())
        return center + Offset((cos(radians) * ringRadius).toFloat(), (sin(radians) * ringRadius).toFloat())
    }

    fun squareHandle(hsv: PickerHsv): Offset = Offset(left + hsv.s * side, top + (1f - hsv.v) * side)
}

/**
 * 색조 고리와 채도, 명도 사각형을 그린다. 사각형은 순색을 칠한 위에 왼쪽에서 오른쪽으로 옅어지는 흰색과 위에서 아래로 짙어지는 검정을 겹쳐 만든다.
 * 붓은 크기가 바뀔 때만 만들고, 색조와 손잡이 위치는 그리기 단계에서 읽는다.
 */
private fun Modifier.drawWheel(model: PickerModel, ringGrab: Animatable<Float, *>, squareGrab: Animatable<Float, *>): Modifier =
    drawWithCache {
        val geometry = HueWheelGeometry(size.width, size.height, RING_WIDTH.toPx(), RING_GAP.toPx())
        val hues = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
        val sweep = Brush.sweepGradient(hues, geometry.center)
        val ringStroke = Stroke(geometry.ring)
        val squareOrigin = Offset(geometry.left, geometry.top)
        val squareSize = Size(geometry.side, geometry.side)
        val whiteFade = Brush.horizontalGradient(
            listOf(Color.White, Color.White.copy(alpha = 0f)),
            geometry.left,
            geometry.left + geometry.side,
        )
        val blackFade = Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0f), Color.Black),
            geometry.top,
            geometry.top + geometry.side,
        )
        val corner = CornerRadius(SQUARE_CORNER.toPx())
        val handle = HANDLE_RADIUS.toPx()
        onDrawBehind {
            val hsv = model.hsv
            drawCircle(sweep, geometry.ringRadius, geometry.center, style = ringStroke)
            drawRoundRect(Color.hsv(hsv.h, 1f, 1f), squareOrigin, squareSize, corner)
            drawRoundRect(whiteFade, squareOrigin, squareSize, corner)
            drawRoundRect(blackFade, squareOrigin, squareSize, corner)
            val ringHandle = handle * (1f + HANDLE_GROW * ringGrab.value)
            val squareHandle = handle * (1f + HANDLE_GROW * squareGrab.value)
            drawColorHandle(geometry.hueHandle(hsv.h), ringHandle, Color.hsv(hsv.h, 1f, 1f))
            drawColorHandle(geometry.squareHandle(hsv), squareHandle, hsv.copy(a = 1f).toColor())
            when (model.focused) {
                HueWheelPart.Ring -> drawFocusRing(geometry.hueHandle(hsv.h), ringHandle)
                HueWheelPart.Square -> drawFocusRing(geometry.squareHandle(hsv), squareHandle)
                null -> Unit
            }
        }
    }

/** TalkBack 사용자 지정 동작. 색조는 [HUE_STEP]도, 밝기와 선명도는 [TONE_STEP]씩 바꾼다. */
@Composable
private fun wheelActions(hsv: PickerHsv, apply: (PickerHsv) -> Unit): List<CustomAccessibilityAction> {
    fun action(label: String, next: PickerHsv) = CustomAccessibilityAction(label) {
        apply(next)
        true
    }
    return listOf(
        action(stringResource(R.string.kinetica_ui_color_hue_next), hsv.copy(h = (hsv.h + HUE_STEP) % FULL_TURN)),
        action(stringResource(R.string.kinetica_ui_color_hue_previous), hsv.copy(h = (hsv.h - HUE_STEP + FULL_TURN) % FULL_TURN)),
        action(stringResource(R.string.kinetica_ui_color_lighter), hsv.copy(v = (hsv.v + TONE_STEP).coerceAtMost(1f))),
        action(stringResource(R.string.kinetica_ui_color_darker), hsv.copy(v = (hsv.v - TONE_STEP).coerceAtLeast(0f))),
        action(stringResource(R.string.kinetica_ui_color_more_vivid), hsv.copy(s = (hsv.s + TONE_STEP).coerceAtMost(1f))),
        action(stringResource(R.string.kinetica_ui_color_more_muted), hsv.copy(s = (hsv.s - TONE_STEP).coerceAtLeast(0f))),
    )
}

private const val MAX_RECENTS = 6
private const val HUE_TICK = 30f
private const val HUE_STEP = 15f
private const val HUE_KEY_STEP = 2f
private const val TONE_STEP = 0.1f
private const val HANDLE_GROW = 0.22f
private const val EDGE_ROOM = 0.1f
private const val TOUCH_SLACK = 0.4f
private const val DIVIDER_ALPHA = 0.2f
private val WHEEL_MAX = 320.dp
private val RING_WIDTH = 28.dp
private val RING_GAP = 14.dp
private val SQUARE_CORNER = 10.dp
private val HANDLE_RADIUS = 13.dp
private val SECTION_GAP = 16.dp
private val SLIDER_PADDING = 4.dp
private val DIVIDER_PADDING = 6.dp
private val DIVIDER_HEIGHT = 24.dp
private const val FULL_TURN = 360f
