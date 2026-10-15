package com.naury.kinetica.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * [GlassSignaturePad]에 그린 서명. 획을 되돌리거나 지우고, 결과를 이미지나 SVG로 내보낸다.
 * 좌표는 서명란 폭을 1로 둔 비율로 저장하므로 화면을 돌려 서명란 크기가 바뀌어도 서명 모양이 유지된다.
 * [rememberSignaturePadState]로 만들면 화면 회전과 프로세스 재시작 뒤에도 획이 남는다.
 */
@Stable
public class SignaturePadState internal constructor(saved: List<FloatArray>) {
    internal val strokes = mutableStateListOf<FloatArray>().apply { addAll(saved) }

    /** 그리는 중인 획. 손을 떼면 [strokes]로 옮긴다. */
    internal var current: InkStroke? = null

    /** 그리는 중인 획이 바뀔 때마다 오른다. 그리기 단계에서만 읽는다. */
    internal val currentVersion = mutableIntStateOf(0)

    /** 서명란이 마지막으로 배치된 크기(px). */
    internal var padSize: IntSize = IntSize.Zero

    /** 완성된 획이 하나도 없는지. 그리는 중인 획은 손을 뗀 뒤에 반영된다. */
    public val isEmpty: Boolean get() = strokes.isEmpty()

    /** 되돌릴 획이 있는지 */
    public val canUndo: Boolean get() = strokes.isNotEmpty()

    /** 마지막 획 하나를 지운다. 획이 없으면 아무것도 하지 않는다. */
    public fun undo() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
    }

    /** 모든 획을 지운다. */
    public fun clear() {
        strokes.clear()
        current = null
        currentVersion.intValue++
    }

    /**
     * 서명을 SVG `path`의 `d` 속성 값으로 돌려준다. 좌표는 서명란이 마지막으로 배치된 크기의 px이며, 획의 중심선을 2차 베지어 곡선으로 잇는다.
     * 속도에 따른 굵기 변화는 담기지 않는다. 획이 없으면 빈 문자열이다.
     */
    public fun toSvgPath(): String {
        val scale = padSize.width.toFloat().takeIf { it > 0f } ?: 1f
        return strokes.joinToString(" ") { svgPathOf(it, scale) }
    }

    /**
     * 서명을 SVG 문서 하나로 돌려준다. 크기와 `viewBox`는 서명란이 마지막으로 배치된 크기의 px이고, 선 굵기는 획 굵기의 평균이다.
     *
     * @param color 선 색. 알파는 `stroke-opacity`로 넣는다.
     */
    public fun toSvg(color: Color = Color.Black): String {
        val width = padSize.width
        val height = padSize.height
        val all = strokes.toList()
        val count = all.sumOf { it.size / INK_FIELDS }
        val averageWidth = if (count ==
            0
        ) {
            0f
        } else {
            all.sumOf { s -> (s.indices step INK_FIELDS).sumOf { s[it + 2].toDouble() } }.toFloat() / count
        }
        val hex = String.format(Locale.ROOT, "#%06X", color.toArgb() and RGB_MASK)
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" viewBox=\"0 0 $width $height\">" +
            "<path d=\"${toSvgPath()}\" fill=\"none\" stroke=\"$hex\" stroke-opacity=\"${fmt(color.alpha)}\" " +
            "stroke-width=\"${fmt(averageWidth * width)}\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg>"
    }

    /**
     * 서명을 [width] px 폭의 이미지로 그린다. 높이는 서명란의 가로세로 비율을 따른다. 속도에 따른 굵기 변화도 그대로 담는다.
     *
     * @param width 이미지 폭(px). 기본값은 서명란 폭이다. 1 미만이면 [IllegalArgumentException]을 던진다.
     * @param color 획 색
     * @param background 바탕색. 기본값은 투명이다.
     * @throws IllegalStateException 서명란이 아직 한 번도 배치되지 않아 가로세로 비율을 모를 때
     */
    public fun toImageBitmap(width: Int = padSize.width, color: Color = Color.Black, background: Color = Color.Transparent): ImageBitmap {
        check(padSize.width > 0 && padSize.height > 0) { "서명란이 배치된 뒤에 내보낼 수 있습니다." }
        require(width >= 1) { "width는 1 이상이어야 합니다." }
        val height = (width.toFloat() * padSize.height / padSize.width).roundToInt().coerceAtLeast(1)
        val bitmap = ImageBitmap(width, height)
        val path = Path()
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            if (background.alpha > 0f) drawRect(background)
            strokes.forEach { drawInk(it, it.size, color, width.toFloat(), path) }
        }
        return bitmap
    }

    internal fun begin(x: Float, y: Float, width: Float, time: Long) {
        current = InkStroke(width).also { it.add(x, y, width, time) }
        currentVersion.intValue++
    }

    internal fun extend(x: Float, y: Float, time: Long, baseWidth: Float, density: Float) {
        val stroke = current ?: return
        if (stroke.extend(x, y, time, baseWidth, density)) currentVersion.intValue++
    }

    internal fun end() {
        val stroke = current ?: return
        current = null
        if (padSize.width > 0) strokes += stroke.normalized(padSize.width.toFloat())
        currentVersion.intValue++
    }

    public companion object {
        /** [rememberSaveable]에 쓰는 저장기. 획마다 비율 좌표 배열 하나로 저장한다. */
        public val Saver: Saver<SignaturePadState, ArrayList<FloatArray>> = Saver(
            save = { state -> ArrayList(state.strokes.map(FloatArray::copyOf)) },
            restore = { SignaturePadState(it) },
        )
    }
}

/** 화면 회전과 프로세스 재시작 뒤에도 획을 유지하는 [SignaturePadState]를 만든다. */
@Composable
public fun rememberSignaturePadState(): SignaturePadState = rememberSaveable(saver = SignaturePadState.Saver) {
    SignaturePadState(emptyList())
}

/** 그리는 중인 획. 점마다 x, y, 굵기를 px로 담는다. */
internal class InkStroke(initialWidth: Float) {
    var data = FloatArray(INITIAL_CAPACITY)
    var size = 0
    private var lastTime = 0L
    private var width = initialWidth

    fun add(x: Float, y: Float, w: Float, time: Long) {
        if (size + INK_FIELDS > data.size) data = data.copyOf(data.size * 2)
        data[size] = x
        data[size + 1] = y
        data[size + 2] = w
        size += INK_FIELDS
        lastTime = time
    }

    /** 앞 점에서 충분히 떨어졌으면 속도에 맞춘 굵기로 점을 더하고 `true`를 돌려준다. 빨리 그을수록 가늘어진다. */
    fun extend(x: Float, y: Float, time: Long, baseWidth: Float, density: Float): Boolean {
        val dx = x - data[size - INK_FIELDS]
        val dy = y - data[size - 2]
        val distance = hypot(dx, dy)
        if (distance < MIN_STEP * density) return false
        val dt = (time - lastTime).coerceAtLeast(1L).toFloat()
        val speed = (distance / density / dt / FAST_SPEED).coerceIn(0f, 1f)
        val target = baseWidth * (THICK - (THICK - THIN) * speed)
        width += (target - width) * WIDTH_EASING
        add(x, y, width, time)
        return true
    }

    fun normalized(scale: Float): FloatArray = FloatArray(size) { data[it] / scale }
}

/**
 * 손글씨 서명을 받는 글래스 서명란. 손가락이나 펜 속도에 따라 획 굵기가 달라지고, 점 사이를 곡선으로 이어 매끄럽게 그린다.
 * 서명란 안에서 그리는 동안에는 바깥 스크롤 목록이나 페이저가 터치를 가져가지 않는다.
 * 서명이 있으면 오른쪽 위에 되돌리기와 지우기 버튼이 나타난다. 결과는 [SignaturePadState]에서 이미지나 SVG로 내보낸다.
 *
 * TalkBack은 서명란이 비었는지 읽고 되돌리기와 지우기 동작을 제공한다. 서명 자체는 TalkBack 제스처로 그릴 수 없으므로,
 * 꼭 서명을 받아야 하는 화면이라면 이름 입력 같은 대체 수단을 함께 둔다.
 *
 * ```kotlin
 * val signature = rememberSignaturePadState()
 * GlassSignaturePad(Modifier.fillMaxWidth().height(200.dp), signature)
 * GlassButton("제출", enabled = !signature.isEmpty, onClick = { upload(signature.toImageBitmap()) })
 * ```
 *
 * @param inkColor 획 색. 기본값은 글래스 위 글자 색이다.
 * @param strokeWidth 기본 획 굵기. 느리게 그으면 이보다 조금 굵게, 빠르게 그으면 가늘게 그린다.
 * @param hint 비어 있을 때 기준선 위에 보이는 안내 문구
 * @param showControls 되돌리기와 지우기 버튼을 보일지
 */
@Composable
public fun GlassSignaturePad(
    modifier: Modifier = Modifier,
    state: SignaturePadState = rememberSignaturePadState(),
    inkColor: Color = LocalGlassStyle.current.onGlass,
    strokeWidth: Dp = 3.dp,
    hint: String = stringResource(R.string.kinetica_ui_signature_hint),
    showControls: Boolean = true,
    style: GlassStyle = LocalGlassStyle.current,
) {
    val empty = state.isEmpty
    val hintAlpha by animateFloatAsState(if (empty) 1f else 0f, label = "hint")
    val title = stringResource(R.string.kinetica_ui_signature)
    val status = stringResource(if (empty) R.string.kinetica_ui_signature_empty else R.string.kinetica_ui_signature_signed)
    val undoLabel = stringResource(R.string.kinetica_ui_signature_undo)
    val clearLabel = stringResource(R.string.kinetica_ui_signature_clear)
    Box(
        modifier
            .heightIn(min = PAD_MIN_HEIGHT)
            .glass(style, PAD_SHAPE)
            .semantics {
                contentDescription = title
                stateDescription = status
                if (!empty) {
                    customActions = listOf(
                        CustomAccessibilityAction(undoLabel) {
                            state.undo()
                            true
                        },
                        CustomAccessibilityAction(clearLabel) {
                            state.clear()
                            true
                        },
                    )
                }
            },
    ) {
        Box(Modifier.matchParentSize().guideLine(style.onGlass))
        ThemedText(
            hint,
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = GUIDE_INSET + HINT_INDENT, bottom = HINT_BOTTOM)
                .graphicsLayer { alpha = hintAlpha },
            style = TextStyle(color = style.onGlass.copy(alpha = HINT_ALPHA), fontSize = HINT_FONT_SIZE),
            maxLines = 1,
        )
        Box(Modifier.matchParentSize().signatureInput(state, strokeWidth).inkLayer(state, inkColor, strokeWidth))
        if (showControls) {
            AnimatedVisibility(
                !empty,
                Modifier.align(Alignment.TopEnd).padding(CONTROLS_PADDING),
                enter = fadeIn() + scaleIn(initialScale = CONTROL_ENTER_SCALE),
                exit = fadeOut() + scaleOut(targetScale = CONTROL_ENTER_SCALE),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP)) {
                    PadButton(undoLabel, style) { state.undo() }
                    PadButton(clearLabel, style) { state.clear() }
                }
            }
        }
    }
}

@Composable
private fun PadButton(label: String, style: GlassStyle, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = CONTROL_HEIGHT)
            .glass(style, CircleShape)
            .glassClickable(CircleShape, onClick = onClick)
            .padding(horizontal = CONTROL_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        ThemedText(
            label,
            style = TextStyle(color = style.onGlass, fontSize = CONTROL_FONT_SIZE, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

/** 서명 기준선과 시작 표시(×). 서명란 아래쪽에 점선으로 그린다. */
private fun Modifier.guideLine(ink: Color): Modifier = drawWithCache {
    val inset = GUIDE_INSET.toPx()
    val y = size.height - GUIDE_BOTTOM.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), DASH_GAP.toPx()))
    val width = GUIDE_STROKE.toPx()
    val arm = CROSS_ARM.toPx()
    val cross = Offset(inset + arm, y - CROSS_LIFT.toPx())
    val color = ink.copy(alpha = GUIDE_ALPHA)
    onDrawBehind {
        drawLine(color, Offset(inset, y), Offset(size.width - inset, y), width, pathEffect = dash)
        drawLine(color, cross + Offset(-arm, -arm), cross + Offset(arm, arm), width * CROSS_WEIGHT, StrokeCap.Round)
        drawLine(color, cross + Offset(-arm, arm), cross + Offset(arm, -arm), width * CROSS_WEIGHT, StrokeCap.Round)
    }
}

/** 손가락 입력을 획으로 받는다. 누르는 순간부터 이벤트를 소비하고, 안드로이드 뷰 부모(ScrollView, ViewPager2 등)에도 가로채지 말라고 알린다. */
@Composable
private fun Modifier.signatureInput(state: SignaturePadState, strokeWidth: Dp): Modifier {
    val view = LocalView.current
    return this
        .onSizeChanged { state.padSize = it }
        .pointerInput(state, strokeWidth) {
            val base = strokeWidth.toPx()
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                view.parent?.requestDisallowInterceptTouchEvent(true)
                state.begin(down.position.x, down.position.y, base, down.uptimeMillis)
                do {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                    change?.historical?.forEach { state.extend(it.position.x, it.position.y, it.uptimeMillis, base, density) }
                    change?.let { state.extend(it.position.x, it.position.y, it.uptimeMillis, base, density) }
                    change?.consume()
                } while (change?.pressed == true)
                state.end()
                view.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
}

/** 완성된 획은 한 번 기록한 레이어로 다시 그리고, 그리는 중인 획만 매 프레임 그린다. */
private fun Modifier.inkLayer(state: SignaturePadState, color: Color, strokeWidth: Dp): Modifier = drawWithCache {
    val scale = size.width
    val strokes = state.strokes.toList()
    val path = Path()
    val committed = obtainGraphicsLayer()
    committed.record { strokes.forEach { drawInk(it, it.size, color, scale, path) } }
    val dot = strokeWidth.toPx()
    onDrawBehind {
        drawLayer(committed)
        state.currentVersion.intValue
        val current = state.current ?: return@onDrawBehind
        if (current.size == INK_FIELDS) {
            drawCircle(color, dot / 2, Offset(current.data[0], current.data[1]))
        } else {
            drawInk(current.data, current.size, color, 1f, path)
        }
    }
}

/**
 * 점 배열 [points]의 앞 [count]칸을 곡선 획으로 그린다. 이웃한 두 점의 중점을 이어 가며 앞 점을 조절점으로 쓰는 2차 베지어로 잇고,
 * 구간마다 앞 점의 굵기를 쓴다. 좌표와 굵기에 [scale]을 곱한다.
 */
internal fun DrawScope.drawInk(points: FloatArray, count: Int, color: Color, scale: Float, path: Path) {
    if (count < INK_FIELDS) return
    if (count == INK_FIELDS) {
        drawCircle(color, points[2] * scale / 2, Offset(points[0] * scale, points[1] * scale))
        return
    }
    var fromX = points[0] * scale
    var fromY = points[1] * scale
    var i = INK_FIELDS
    while (i < count) {
        val px = points[i - INK_FIELDS] * scale
        val py = points[i - 2] * scale
        val x = points[i] * scale
        val y = points[i + 1] * scale
        val last = i + INK_FIELDS >= count
        val toX = if (last) x else (px + x) / 2
        val toY = if (last) y else (py + y) / 2
        path.reset()
        path.moveTo(fromX, fromY)
        path.quadraticTo(px, py, toX, toY)
        val width = (points[i - 1] + points[i + 2]) / 2 * scale
        drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round))
        fromX = toX
        fromY = toY
        i += INK_FIELDS
    }
}

internal fun svgPathOf(points: FloatArray, scale: Float): String {
    if (points.size < INK_FIELDS) return ""
    val out = StringBuilder("M${fmt(points[0] * scale)} ${fmt(points[1] * scale)}")
    if (points.size == INK_FIELDS) {
        out.append(" L${fmt(points[0] * scale)} ${fmt(points[1] * scale)}")
        return out.toString()
    }
    var i = INK_FIELDS
    while (i < points.size) {
        val px = points[i - INK_FIELDS] * scale
        val py = points[i - 2] * scale
        val last = i + INK_FIELDS >= points.size
        val toX = if (last) points[i] * scale else (px + points[i] * scale) / 2
        val toY = if (last) points[i + 1] * scale else (py + points[i + 1] * scale) / 2
        out.append(" Q${fmt(px)} ${fmt(py)} ${fmt(toX)} ${fmt(toY)}")
        i += INK_FIELDS
    }
    return out.toString()
}

private fun fmt(value: Float): String = String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")

internal const val INK_FIELDS = 3
private const val INITIAL_CAPACITY = 96
private const val RGB_MASK = 0xFFFFFF

/** 점을 더하는 최소 간격(dp). 손떨림으로 생기는 잔물결을 줄인다. */
private const val MIN_STEP = 1.2f

/** 이 속도(dp/ms) 이상이면 가장 가늘게 그린다. */
private const val FAST_SPEED = 2.6f
private const val THICK = 1.45f
private const val THIN = 0.5f
private const val WIDTH_EASING = 0.3f

private val PAD_SHAPE = RoundedCornerShape(22.dp)
private val PAD_MIN_HEIGHT = 160.dp
private val GUIDE_INSET = 24.dp
private val GUIDE_BOTTOM = 40.dp
private val GUIDE_STROKE = 1.dp
private val DASH = 6.dp
private val DASH_GAP = 5.dp
private val CROSS_ARM = 5.dp
private val CROSS_LIFT = 12.dp
private const val GUIDE_ALPHA = 0.35f
private const val CROSS_WEIGHT = 1.2f
private val HINT_INDENT = 22.dp
private val HINT_BOTTOM = 46.dp
private val HINT_FONT_SIZE = 15.sp
private const val HINT_ALPHA = 0.45f

// 버튼 캡슐 반지름(높이의 절반)이 서명란 모서리 반지름에서 이 여백을 뺀 값과 같아야 모서리가 나란히 굽는다.
private val CONTROLS_PADDING = 6.dp
private val CONTROL_GAP = 6.dp
private val CONTROL_HEIGHT = 32.dp
private val CONTROL_PADDING = 12.dp
private val CONTROL_FONT_SIZE = 13.sp
private const val CONTROL_ENTER_SCALE = 0.8f
