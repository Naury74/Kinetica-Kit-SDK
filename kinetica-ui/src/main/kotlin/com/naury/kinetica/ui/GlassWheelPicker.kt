package com.naury.kinetica.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/**
 * iOS 휠 피커처럼 위아래로 굴려 항목 하나를 고르는 글래스 피커. 가운데에서 멀어질수록 항목이 원통 표면에 붙은 것처럼 기울고
 * 작아지며 흐려진다. 가운데 선택 영역은 글래스 캡슐로 그린다.
 *
 * 플링이 끝나면 가장 가까운 항목에 스냅하고, 항목을 하나 지날 때마다 가벼운 햅틱을 준다. [onSelect]는 스크롤이 멈춘 뒤에만
 * 호출한다. 받은 값을 [selectedIndex]로 돌려주지 않으면 원래 위치로 돌아간다. 키보드 위아래 방향키로 한 칸씩 움직이고,
 * TalkBack은 현재 항목을 읽으며 값 조절이나 '다음', '이전' 동작으로 바꿀 수 있다.
 *
 * @param items 보여 줄 항목. 비어 있으면 [IllegalArgumentException]을 던진다.
 * @param selectedIndex 선택한 항목의 위치. [items] 범위를 벗어나면 가까운 끝 항목을 선택한 것으로 그린다.
 * @param onSelect 사용자가 고른 항목의 위치를 받는다.
 * @param visibleCount 한 번에 보이는 줄 수. 3 이상의 홀수가 아니면 [IllegalArgumentException]을 던진다.
 * @param cyclic `true`면 마지막 항목 다음에 첫 항목이 이어져 끝없이 굴릴 수 있다.
 * @param contentDescription TalkBack이 값 앞에 읽는 이름. 예를 들면 "시"나 "분"이다.
 */
@Composable
public fun GlassWheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleCount: Int = DEFAULT_VISIBLE_COUNT,
    cyclic: Boolean = false,
    contentDescription: String? = null,
    style: GlassStyle = LocalGlassStyle.current,
) {
    Wheel(WheelSpec(items, selectedIndex, visibleCount, cyclic, contentDescription), onSelect, modifier, style, band = true)
}

/** 휠 하나를 그리는 데 필요한 값. 날짜와 시간 피커가 여러 휠을 한 줄에 놓을 때도 쓴다. */
internal class WheelSpec(
    val items: List<String>,
    val selectedIndex: Int,
    val visibleCount: Int,
    val cyclic: Boolean,
    val contentDescription: String?,
)

/** 휠 한 줄의 높이. 글자 크기를 키운 사용자에게는 그만큼 줄 높이도 키운다. */
@Composable
internal fun wheelItemHeight(): Dp = WHEEL_ITEM_HEIGHT * LocalDensity.current.fontScale.coerceAtLeast(1f)

/** 가운데 선택 영역을 나타내는 글래스 캡슐. 휠 뒤에 그린다. */
@Composable
internal fun WheelBand(style: GlassStyle, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(wheelItemHeight())
            .padding(horizontal = BAND_INSET)
            .glass(style, CircleShape),
    )
}

/** 휠 하나를 그린다. 여러 휠이 선택 영역 하나를 함께 쓸 때는 [band]를 `false`로 하고 [WheelBand]를 따로 그린다. */
@Composable
internal fun Wheel(spec: WheelSpec, onSelect: (Int) -> Unit, modifier: Modifier, style: GlassStyle, band: Boolean = false) {
    require(spec.items.isNotEmpty()) { "items가 비어 있습니다." }
    require(spec.visibleCount >= MIN_VISIBLE_COUNT && spec.visibleCount % 2 == 1) { "visibleCount는 3 이상의 홀수여야 합니다." }
    val size = spec.items.size
    val selected = spec.selectedIndex.coerceIn(0, size - 1)
    // 끝없이 굴리는 휠은 같은 항목을 여러 번 이어 붙이고 가운데 바퀴에서 시작한다.
    val base = if (spec.cyclic) size * (CYCLE_REPEAT / 2) else 0
    val state = remember(size, spec.cyclic) { LazyListState(base + selected) }
    val itemHeight = wheelItemHeight()
    val itemPx = with(LocalDensity.current) { itemHeight.roundToPx().toFloat() }
    val geometry = remember(state) { WheelGeometry(state) }
    geometry.itemPx = itemPx
    geometry.itemAngle = WHEEL_MAX_ANGLE / (spec.visibleCount / 2 + HALF_ROW)
    val latestSelect by rememberUpdatedState(onSelect)
    val latestSelected by rememberUpdatedState(selected)
    val still = reducedMotion()
    WheelEffects(geometry, size) { if (it != latestSelected) latestSelect(it) }
    LaunchedEffect(selected, state) {
        if (state.isScrollInProgress) return@LaunchedEffect
        val current = geometry.centered()
        if (current % size == selected) return@LaunchedEffect
        val target = if (spec.cyclic) nearestCyclic(current, selected, size) else selected
        if (still) state.scrollToItem(target) else state.animateScrollToItem(target)
    }
    val move: (Int) -> Boolean = { delta ->
        val next = selected + delta
        val target = if (spec.cyclic) Math.floorMod(next, size) else next
        if (target in 0 until size && target != selected) {
            latestSelect(target)
            true
        } else {
            false
        }
    }
    val labels = WheelLabels(stringResource(R.string.kinetica_ui_picker_next), stringResource(R.string.kinetica_ui_picker_previous))
    val parts = WheelParts(state, geometry, band, move, Modifier.wheelSemantics(spec, selected, labels, move))
    WheelList(spec, parts, modifier, style)
}

/** 항목을 지날 때 햅틱을 주고, 스크롤이 멈추면 가운데 항목을 [onSettle]로 알린다. */
@Composable
private fun WheelEffects(geometry: WheelGeometry, size: Int, onSettle: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val state = geometry.state
    LaunchedEffect(geometry) {
        snapshotFlow { geometry.centered() }.drop(1).collect {
            if (state.isScrollInProgress) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    LaunchedEffect(geometry, size) {
        snapshotFlow { state.isScrollInProgress }.drop(1).filter { !it }.collect { onSettle(geometry.centered() % size) }
    }
}

@Composable
private fun WheelList(spec: WheelSpec, parts: WheelParts, modifier: Modifier, style: GlassStyle) {
    val state = parts.state
    val geometry = parts.geometry
    val itemHeight = wheelItemHeight()
    var focused by remember { mutableStateOf(false) }
    val focusColor = GlassTheme.colors.accent
    val textStyle = TextStyle(
        color = style.onGlass,
        fontSize = WHEEL_FONT_SIZE,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
    // 의미 정보와 키 입력은 바깥 상자에 두어, 목록 자체의 스크롤 동작이 TalkBack에 따로 노출되지 않게 한다.
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .wheelKeys(parts.move)
            // 의미 정보를 지우는 수정자는 포커스 수정자 뒤에 두어야 포커스 정보가 남는다.
            .then(parts.semantics)
            .drawWithCache {
                val ring = Stroke(FOCUS_RING.toPx())
                val band = Size(size.width - BAND_INSET.toPx() * 2, itemHeight.toPx())
                val origin = Offset(BAND_INSET.toPx(), (size.height - band.height) / 2)
                onDrawWithContent {
                    drawContent()
                    if (focused) drawRoundRect(focusColor, origin, band, CornerRadius(band.height / 2), style = ring)
                }
            },
    ) {
        if (parts.band) WheelBand(style, Modifier.align(Alignment.Center))
        LazyColumn(
            Modifier.fillMaxWidth().height(itemHeight * spec.visibleCount),
            state = state,
            contentPadding = PaddingValues(vertical = itemHeight * (spec.visibleCount / 2)),
            flingBehavior = rememberSnapFlingBehavior(state),
        ) {
            items(if (spec.cyclic) spec.items.size * CYCLE_REPEAT else spec.items.size) { index ->
                ThemedText(
                    spec.items[index % spec.items.size],
                    Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .padding(horizontal = TEXT_INSET)
                        .graphicsLayer { geometry.applyTo(this, index) },
                    style = textStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private class WheelParts(
    val state: LazyListState,
    val geometry: WheelGeometry,
    val band: Boolean,
    val move: (Int) -> Boolean,
    val semantics: Modifier,
)

/** 스크롤 위치에서 각 항목의 원통 위 각도를 계산한다. 그리기 단계에서만 읽어 스크롤 중에 재구성하지 않는다. */
private class WheelGeometry(val state: LazyListState) {
    var itemPx: Float = 1f

    /** 한 항목이 차지하는 원통 각도(도). */
    var itemAngle: Float = 0f

    fun center(): Float = state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / itemPx

    fun centered(): Int = state.firstVisibleItemIndex + (state.firstVisibleItemScrollOffset / itemPx).roundToInt()

    fun applyTo(layer: androidx.compose.ui.graphics.GraphicsLayerScope, index: Int) {
        val distance = index - center()
        val degrees = (distance * itemAngle).coerceIn(-RIGHT_ANGLE, RIGHT_ANGLE)
        val radians = degrees * PI.toFloat() / HALF_TURN
        // 원통 반지름. 가운데 근처에서 항목 간격이 줄 높이와 같도록 잡는다.
        val radius = itemPx / (itemAngle * PI.toFloat() / HALF_TURN)
        layer.translationY = radius * sin(radians) - distance * itemPx
        // 위쪽 항목은 아래 가장자리가, 아래쪽 항목은 위 가장자리가 보는 사람 쪽으로 오게 기울인다.
        layer.rotationX = -degrees
        layer.cameraDistance = CAMERA_DISTANCE * layer.density
        val fade = 1f - abs(degrees) / RIGHT_ANGLE
        layer.alpha = (fade * fade).coerceIn(0f, 1f)
        val shrink = 1f - abs(distance) * SHRINK_PER_ITEM
        layer.scaleX = shrink.coerceAtLeast(MIN_SCALE)
        layer.scaleY = layer.scaleX
    }
}

/** TalkBack에서 '다음', '이전' 동작에 붙일 이름. */
private class WheelLabels(val next: String, val previous: String)

private fun Modifier.wheelSemantics(spec: WheelSpec, selected: Int, labels: WheelLabels, move: (Int) -> Boolean): Modifier =
    clearAndSetSemantics {
        role = Role.ValuePicker
        spec.contentDescription?.let { contentDescription = it }
        stateDescription = spec.items[selected]
        val last = spec.items.size - 1
        if (last > 0) {
            progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 0f..last.toFloat(), (last - 1).coerceAtLeast(0))
            setProgress { target -> move(target.roundToInt().coerceIn(0, last) - selected) }
        }
        customActions = listOf(
            CustomAccessibilityAction(labels.next) { move(1) },
            CustomAccessibilityAction(labels.previous) { move(-1) },
        )
    }

private fun Modifier.wheelKeys(move: (Int) -> Boolean): Modifier = onKeyEvent { event ->
    val delta = when {
        event.type != KeyEventType.KeyDown -> null
        event.key == Key.DirectionDown -> 1
        event.key == Key.DirectionUp -> -1
        else -> null
    }
    delta != null && move(delta)
}.focusable()

/** 끝없이 도는 휠에서 [current] 위치로부터 [target] 항목까지 가장 가까운 위치를 찾는다. */
internal fun nearestCyclic(current: Int, target: Int, size: Int): Int {
    var delta = Math.floorMod(target - current, size)
    if (delta > size / 2) delta -= size
    return current + delta
}

internal const val DEFAULT_VISIBLE_COUNT = 5
private const val MIN_VISIBLE_COUNT = 3
private const val CYCLE_REPEAT = 2_000
private val WHEEL_ITEM_HEIGHT = 36.dp
private val WHEEL_FONT_SIZE = 20.sp
private val BAND_INSET = 4.dp
private val TEXT_INSET = 8.dp
private val FOCUS_RING = 2.dp

/** 가장 바깥 줄이 놓이는 원통 각도(도). 90도에 가까울수록 바깥 줄이 납작해진다. */
private const val WHEEL_MAX_ANGLE = 70f
private const val HALF_ROW = 0.5f
private const val RIGHT_ANGLE = 90f
private const val HALF_TURN = 180f
private const val CAMERA_DISTANCE = 12f
private const val SHRINK_PER_ITEM = 0.04f
private const val MIN_SCALE = 0.8f
