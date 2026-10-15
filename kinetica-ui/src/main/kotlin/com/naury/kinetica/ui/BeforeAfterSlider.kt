package com.naury.kinetica.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 두 이미지를 겹쳐 놓고 가운데 글래스 손잡이를 좌우로 끌어 비교한다. [before]는 시작 쪽(LTR에서 왼쪽, RTL에서 오른쪽)에,
 * [after]는 끝 쪽에 보인다. 아무 곳이나 누르면 손잡이가 그 위치로 미끄러진다. 가로 드래그만 받으므로 세로 스크롤 목록 안에 넣어도 스크롤을 막지 않는다.
 *
 * 크기는 [modifier]나 [after]의 크기를 따른다. [before]는 같은 크기로 겹쳐 그린다.
 * 키보드 방향키로 5%씩 움직이고, TalkBack에서는 값을 조절하는 컨트롤로 읽혀 볼륨 키나 위아래 스와이프로 바꿀 수 있다.
 * 위치는 화면 회전 뒤에도 유지된다.
 *
 * ```kotlin
 * BeforeAfterSlider(
 *     before = { Image(painterResource(R.drawable.raw), null, contentScale = ContentScale.Crop) },
 *     after = { Image(painterResource(R.drawable.edited), null, contentScale = ContentScale.Crop) },
 *     modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
 * )
 * ```
 *
 * @param initial 처음 손잡이 위치. 시작 쪽 가장자리가 0, 끝 쪽 가장자리가 1이다. 0..1 밖이면 [IllegalArgumentException]을 던진다.
 * @param beforeLabel [before] 쪽 위에 띄우는 라벨. `null`이면 띄우지 않는다. 손잡이가 라벨을 덮을 만큼 다가오면 사라진다.
 * @param afterLabel [after] 쪽 위에 띄우는 라벨. `null`이면 띄우지 않는다.
 * @param style 손잡이와 라벨의 글래스 스타일. 두 이미지를 비춘다.
 */
@Composable
public fun BeforeAfterSlider(
    before: @Composable () -> Unit,
    after: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initial: Float = 0.5f,
    beforeLabel: String? = stringResource(R.string.kinetica_ui_compare_before),
    afterLabel: String? = stringResource(R.string.kinetica_ui_compare_after),
    style: GlassStyle = LocalGlassStyle.current,
) {
    require(initial in 0f..1f) { "initial은 0..1이어야 합니다." }
    val split = rememberSaveable { mutableFloatStateOf(initial) }
    val compare = remember(split) { CompareState(split) }
    CompareBox(compare, before, after, modifier, CompareLabels(beforeLabel, afterLabel), style)
}

/**
 * 손잡이 위치를 호스트가 들고 있는 [BeforeAfterSlider]. 끌기, 누르기, 키보드, TalkBack으로 바꾼 위치를 [onPositionChange]로 받고,
 * 호스트가 [position]을 바꾸면 손잡이가 그 위치로 간다. 누른 곳으로 미끄러지는 동안에는 [onPositionChange]가 프레임마다 불린다.
 * 위치를 저장하거나 화면 회전 뒤에 되살리는 일은 호스트가 한다.
 *
 * ```kotlin
 * var split by rememberSaveable { mutableFloatStateOf(0.5f) }
 * BeforeAfterSlider(split, { split = it }, before = { Raw() }, after = { Edited() }, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
 * ```
 *
 * @param position 손잡이 위치. 시작 쪽 가장자리가 0, 끝 쪽 가장자리가 1이다. 0..1 밖의 값은 가장자리로 맞춰 그린다.
 * @param onPositionChange 사용자가 바꾼 새 위치(0..1)를 받는다.
 */
@Composable
public fun BeforeAfterSlider(
    position: Float,
    onPositionChange: (Float) -> Unit,
    before: @Composable () -> Unit,
    after: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    beforeLabel: String? = stringResource(R.string.kinetica_ui_compare_before),
    afterLabel: String? = stringResource(R.string.kinetica_ui_compare_after),
    style: GlassStyle = LocalGlassStyle.current,
) {
    val split = remember { mutableFloatStateOf(position.coerceIn(0f, 1f)) }
    // 그리기 단계에서 읽는 값이므로 컴포지션마다 호스트 값으로 맞춘다. 같은 값이면 상태가 바뀌지 않는다.
    split.floatValue = position.coerceIn(0f, 1f)
    val latest by rememberUpdatedState(onPositionChange)
    val compare = remember(split) { CompareState(split) { latest(it) } }
    CompareBox(compare, before, after, modifier, CompareLabels(beforeLabel, afterLabel), style)
}

/** 손잡이 양쪽 위에 띄우는 라벨. */
private class CompareLabels(val before: String?, val after: String?)

@Composable
private fun CompareBox(
    compare: CompareState,
    before: @Composable () -> Unit,
    after: @Composable () -> Unit,
    modifier: Modifier,
    labels: CompareLabels,
    style: GlassStyle,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    compare.rtl = rtl
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val backdrop = rememberGlassBackdrop()
    val label = stringResource(R.string.kinetica_ui_compare)
    val percent = (compare.split * PERCENT).roundToInt()
    val state = stringResource(R.string.kinetica_ui_compare_state, percent, PERCENT.toInt() - percent)
    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { compare.width = it.width }
            .semantics {
                contentDescription = label
                stateDescription = state
                progressBarRangeInfo = ProgressBarRangeInfo(compare.split, 0f..1f)
                setProgress { target ->
                    compare.split = target.coerceIn(0f, 1f)
                    true
                }
            }
            .holdViewParentDrag(Orientation.Horizontal)
            .compareInput(compare, interaction),
    ) {
        Box(Modifier.glassBackdrop(backdrop)) {
            after()
            Box(
                Modifier.matchParentSize().drawWithContent {
                    val x = compare.splitX()
                    if (rtl) {
                        clipRect(left = x) {
                            this@drawWithContent.drawContent()
                        }
                    } else {
                        clipRect(right = x) { this@drawWithContent.drawContent() }
                    }
                },
            ) { before() }
        }
        labels.before?.let { CompareLabel(it, Alignment.TopStart, style, backdrop) { compare.startRoom() } }
        labels.after?.let { CompareLabel(it, Alignment.TopEnd, style, backdrop) { compare.width - compare.startRoom() } }
        Divider(compare::splitX)
        Handle(compare::splitX, compare.dragging, focused, style, backdrop)
    }
}

/** 손잡이 위치([split], 시작 쪽 0..1)와 터치 좌표 변환. 터치와 그리기 좌표는 레이아웃 방향과 관계없이 왼쪽 기준이다. */
@Stable
private class CompareState(private val position: MutableFloatState, private val onChange: (Float) -> Unit = {}) {
    var split: Float
        get() = position.floatValue
        set(value) {
            if (value == position.floatValue) return
            position.floatValue = value
            onChange(value)
        }
    var width by mutableIntStateOf(0)
    var dragging by mutableStateOf(false)
    var rtl = false

    // 누른 위치로 미끄러지는 애니메이션. 그 사이에 끌기 시작하면 멈춘다.
    var slide: Job? = null

    /** 손잡이의 x(px, 왼쪽 기준). */
    fun splitX(): Float = if (rtl) width * (1f - split) else width * split

    /** 시작 쪽 가장자리에서 손잡이까지의 너비(px). */
    fun startRoom(): Float = width * split

    fun fractionAt(x: Float): Float = (x / width.coerceAtLeast(1)).coerceIn(0f, 1f).let { if (rtl) 1f - it else it }

    /** 화면 방향으로 [step]만큼(오른쪽이 양수) 옮긴다. */
    fun nudge(step: Float) {
        split = (split + if (rtl) -step else step).coerceIn(0f, 1f)
    }
}

private fun Modifier.compareInput(compare: CompareState, interaction: MutableInteractionSource): Modifier = this
    .onKeyEvent { event ->
        val step = when (event.key) {
            Key.DirectionLeft -> -KEY_STEP
            Key.DirectionRight -> KEY_STEP
            else -> return@onKeyEvent false
        }
        if (event.type == KeyEventType.KeyDown) compare.nudge(step)
        true
    }
    .focusable(interactionSource = interaction)
    .pointerInput(compare) {
        coroutineScope {
            detectTapGestures { tap ->
                compare.slide?.cancel()
                compare.slide = launch {
                    animate(compare.split, compare.fractionAt(tap.x), animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) {
                            v,
                            _,
                        ->
                        compare.split = v
                    }
                }
            }
        }
    }
    .pointerInput(compare) {
        detectHorizontalDragGestures(
            onDragStart = {
                compare.slide?.cancel()
                compare.dragging = true
            },
            onDragEnd = { compare.dragging = false },
            onDragCancel = { compare.dragging = false },
        ) { change, _ ->
            change.consume()
            compare.split = compare.fractionAt(change.position.x)
        }
    }

/** 손잡이 위아래로 이어지는 세로선. */
@Composable
private fun BoxScope.Divider(splitX: () -> Float) {
    Box(
        Modifier.matchParentSize().drawBehind {
            val x = splitX()
            val w = LINE_WIDTH.toPx()
            drawLine(Color.Black.copy(alpha = LINE_SHADOW), Offset(x + w / 2, 0f), Offset(x + w / 2, size.height), w * 2)
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), w)
        },
    )
}

@Composable
private fun BoxScope.Handle(splitX: () -> Float, dragging: Boolean, focused: Boolean, style: GlassStyle, backdrop: GlassBackdrop) {
    val grow by animateFloatAsState(
        if (dragging) HANDLE_GROW else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "handle",
    )
    val accent = GlassTheme.colors.accent
    val ink = style.onGlass
    val chevrons = remember { Path() }
    Box(
        Modifier
            .align(AbsoluteAlignment.CenterLeft)
            .absoluteOffset { IntOffset((splitX() - HANDLE_SIZE.toPx() / 2).roundToInt(), 0) }
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .size(HANDLE_SIZE)
            .glass(style, CircleShape, backdrop)
            .drawBehind {
                drawChevrons(chevrons, ink)
                if (focused) drawCircle(accent, size.minDimension / 2 - FOCUS_WIDTH.toPx() / 2, style = Stroke(FOCUS_WIDTH.toPx()))
            },
    )
}

/** 손잡이 가운데 양쪽을 가리키는 작은 삼각형 두 개. 화면 방향 그대로 그린다. */
private fun DrawScope.drawChevrons(path: Path, color: Color) {
    val h = size.height * CHEVRON_HEIGHT
    val w = h * CHEVRON_RATIO
    val gap = size.width * CHEVRON_GAP
    val cx = size.width / 2
    val cy = size.height / 2
    path.rewind()
    path.moveTo(cx - gap, cy - h / 2)
    path.lineTo(cx - gap - w, cy)
    path.lineTo(cx - gap, cy + h / 2)
    path.close()
    path.moveTo(cx + gap, cy - h / 2)
    path.lineTo(cx + gap + w, cy)
    path.lineTo(cx + gap, cy + h / 2)
    path.close()
    drawPath(path, color)
}

/** 위쪽 모서리에 띄우는 라벨. [room]은 라벨 쪽에 남은 너비(px)로, 라벨이 들어갈 자리가 모자라면 흐려진다. */
@Composable
private fun BoxScope.CompareLabel(text: String, alignment: Alignment, style: GlassStyle, backdrop: GlassBackdrop, room: () -> Float) {
    var labelWidth by remember { mutableIntStateOf(0) }
    Box(
        Modifier
            .align(alignment)
            .padding(LABEL_MARGIN)
            .onSizeChanged { labelWidth = it.width }
            .graphicsLayer {
                val needed = labelWidth + LABEL_MARGIN.toPx() * 2
                alpha = ((room() - needed) / LABEL_FADE.toPx()).coerceIn(0f, 1f)
            }
            .glass(style, CircleShape, backdrop)
            .clearAndSetSemantics { }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        ThemedText(text, style = TextStyle(color = style.onGlass, fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

private const val PERCENT = 100f
private const val KEY_STEP = 0.05f
private val LINE_WIDTH = 2.5.dp
private const val LINE_SHADOW = 0.18f
private val HANDLE_SIZE = 44.dp
private const val HANDLE_GROW = 1.12f
private val FOCUS_WIDTH = 2.5.dp
private const val CHEVRON_HEIGHT = 0.26f
private const val CHEVRON_RATIO = 0.7f
private const val CHEVRON_GAP = 0.07f
private val LABEL_MARGIN = 10.dp
private val LABEL_FADE = 24.dp
