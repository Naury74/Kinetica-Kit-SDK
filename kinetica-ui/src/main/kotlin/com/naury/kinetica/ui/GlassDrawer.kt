// 파일 이름은 대표 컴포저블인 GlassDrawer를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** [GlassDrawer]가 열린 정도. [rememberGlassDrawerState]로 만든다. */
@Stable
public class GlassDrawerState internal constructor(initiallyOpen: Boolean) {
    private val anim = Animatable(if (initiallyOpen) 1f else 0f)

    /** 열린 정도(0..1). 0이면 닫혔고 1이면 다 열렸다. 끄는 동안 손가락을 따라 바뀐다. */
    public val progress: Float get() = anim.value

    /** 열려 있거나 열리는 중이면 true다. 끄는 동안에는 절반 넘게 열렸는지로 판단한다. */
    public val isOpen: Boolean get() = anim.targetValue > HALF

    /** 서랍을 열고, 다 열릴 때까지 일시 중단한다. 사용자가 끌면 멈추고 `CancellationException`을 던진다. */
    public suspend fun open() {
        anim.animateTo(1f, spring(dampingRatio = DRAWER_DAMPING, stiffness = Spring.StiffnessMediumLow))
    }

    /** 서랍을 닫고, 다 닫힐 때까지 일시 중단한다. 사용자가 끌면 멈추고 `CancellationException`을 던진다. */
    public suspend fun close() {
        anim.animateTo(0f, spring(dampingRatio = DRAWER_DAMPING, stiffness = Spring.StiffnessMediumLow))
    }

    internal suspend fun snapTo(value: Float) = anim.snapTo(value.coerceIn(0f, 1f))

    internal var drawerWidth: Float = 1f

    internal companion object {
        val Saver: Saver<GlassDrawerState, Boolean> = Saver(save = { it.isOpen }, restore = { GlassDrawerState(it) })
    }
}

/** [GlassDrawer]의 상태를 만들고 화면 회전 뒤에도 열림 여부를 기억한다. */
@Composable
public fun rememberGlassDrawerState(initiallyOpen: Boolean = false): GlassDrawerState =
    rememberSaveable(saver = GlassDrawerState.Saver) { GlassDrawerState(initiallyOpen) }

/**
 * 시작 쪽 가장자리(RTL이면 오른쪽)에서 끌어 여는 글래스 서랍. 열리면 One UI나 Telegram처럼 [content]가 옆으로 밀리면서 작아지고
 * 모서리가 둥글어지며, 서랍 유리에는 밀려난 화면이 블러 처리되어 비친다. 화면 가장자리 24dp 안에서 끌면 열리고, 열린 동안에는
 * 어디서든 반대로 끌거나 밀려난 화면을 누르거나 뒤로 가기를 하면 닫힌다. 뒤로 가기 예측 제스처를 따라 조금씩 닫히기도 한다.
 * 제스처 내비게이션(Android 10 이상)에서는 시작 가장자리의 세로 가운데 200dp를 시스템 뒤로 가기 제스처에서 빼서 서랍이 먼저 받는다.
 *
 * TalkBack에서는 가장자리 끌기를 쓸 수 없으므로 메뉴 버튼에서 [GlassDrawerState.open]을 불러 연다.
 *
 * ```kotlin
 * val drawer = rememberGlassDrawerState()
 * val scope = rememberCoroutineScope()
 * GlassDrawer(drawerContent = { MenuItems() }, state = drawer) {
 *     GlassScaffold(topBar = { MenuButton { scope.launch { drawer.open() } } }) { ... }
 * }
 * ```
 *
 * @param drawerContent 서랍 안 내용. 상태 표시줄과 내비게이션 바 여백은 서랍이 넣는다.
 * @param gesturesEnabled false면 끌어서 열고 닫지 않는다. 열린 서랍은 바깥을 누르거나 뒤로 가기로 닫을 수 있다.
 * @param content 서랍 뒤 화면
 */
@Composable
public fun GlassDrawer(
    drawerContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    state: GlassDrawerState = rememberGlassDrawerState(),
    gesturesEnabled: Boolean = true,
    style: GlassStyle = LocalGlassStyle.current,
    content: @Composable () -> Unit,
) {
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val scope = rememberCoroutineScope()
    val backdrop = rememberGlassBackdrop()
    val shown by remember(state) { derivedStateOf { state.progress > 0f } }
    PredictiveBackHandler(enabled = state.isOpen) { events ->
        try {
            events.collect { state.snapTo(1f - it.progress * BACK_PEEK) }
            state.close()
        } catch (cancelled: CancellationException) {
            state.open()
            throw cancelled
        }
    }
    val behind = if (style.onGlass.luminance() > HALF) DarkBehind else LightBehind
    Box(modifier.fillMaxSize().then(if (gesturesEnabled) Modifier.drawerDrag(state, direction, scope) else Modifier)) {
        Box(Modifier.fillMaxSize().glassBackdrop(backdrop).background(behind)) {
            Box(Modifier.fillMaxSize().pushedBack(state, direction)) {
                content()
                if (shown) CloseCatcher { scope.launch { state.close() } }
            }
        }
        DrawerPanel(DrawerLook(state, direction, style, backdrop), { scope.launch { state.close() } }, drawerContent)
        if (gesturesEnabled) EdgeGestureGuard()
    }
}

/**
 * 시작 가장자리 일부를 시스템 뒤로 가기 제스처에서 빼서 끌어 열기가 먹히게 한다(Android 10 이상).
 * 시스템이 한 가장자리에 200dp까지만 빼 주므로 세로 가운데 200dp만 뺀다. 터치는 받지 않는다.
 */
@Composable
private fun BoxScope.EdgeGestureGuard() {
    Box(
        Modifier
            .align(Alignment.CenterStart)
            .width(EDGE_WIDTH)
            .heightIn(max = EDGE_EXCLUSION_HEIGHT)
            .fillMaxHeight()
            .systemGestureExclusion(),
    )
}

@Composable
private fun CloseCatcher(onClose: () -> Unit) {
    val label = stringResource(R.string.kinetica_ui_drawer_close)
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = label,
                role = Role.Button,
                onClick = onClose,
            )
            .semantics { contentDescription = label },
    )
}

/** 서랍 패널을 그리는 데 필요한 값. 파라미터 수를 줄이려고 묶었다. */
private class DrawerLook(val state: GlassDrawerState, val direction: Float, val style: GlassStyle, val backdrop: GlassBackdrop)

@Composable
private fun BoxScope.DrawerPanel(look: DrawerLook, onClose: () -> Unit, drawerContent: @Composable ColumnScope.() -> Unit) {
    val state = look.state
    val title = stringResource(R.string.kinetica_ui_drawer_title)
    val closed by remember(state) { derivedStateOf { state.progress == 0f } }
    Column(
        Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .fillMaxWidth(DRAWER_FRACTION)
            .widthIn(max = DRAWER_MAX_WIDTH)
            .onSizeChanged { state.drawerWidth = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                translationX = -look.direction * state.drawerWidth * (1f - state.progress)
                // 닫혀 있을 때 그림자가 화면 가장자리에 비치지 않게 한다.
                alpha = if (state.progress > 0f) 1f else 0f
            }
            .glass(look.style.forPopup(), DrawerShape, look.backdrop)
            .then(
                if (closed) {
                    Modifier.clearAndSetSemantics { }
                } else {
                    Modifier.semantics {
                        paneTitle = title
                        dismiss {
                            onClose()
                            true
                        }
                    }
                },
            )
            .systemBarsPadding()
            .padding(vertical = 12.dp),
    ) {
        CompositionLocalProvider(LocalGlassContentColor provides look.style.onGlass, LocalGlassBackdrop provides look.backdrop) {
            drawerContent()
        }
    }
}

/**
 * 서랍이 열린 만큼 화면을 옆으로 밀고 작게 줄이며 모서리를 둥글게 깎는다. 모양은 1dp 단위로 미리 만들어 둔 것을 골라
 * 프레임마다 새로 만들지 않는다.
 */
private fun Modifier.pushedBack(state: GlassDrawerState, direction: Float): Modifier = graphicsLayer {
    val p = state.progress
    val scale = 1f - PUSH_SCALE * p
    scaleX = scale
    scaleY = scale
    transformOrigin = TransformOrigin(if (direction > 0f) 0f else 1f, HALF)
    translationX = direction * state.drawerWidth * PUSH_SHIFT * p
    shadowElevation = PUSH_SHADOW.toPx() * p
    shape = CornerShapes[(CornerShapes.lastIndex * p).roundToInt().coerceIn(0, CornerShapes.lastIndex)]
    clip = p > 0f
}.drawWithContent {
    drawContent()
    drawRect(Color.Black, alpha = PUSH_DIM * state.progress)
}

/**
 * 닫혀 있으면 시작 쪽 가장자리에서 여는 방향으로 끄는 것만, 열려 있으면 어디서든 닫는 방향으로 끄는 것만 가로챈다.
 * 화면 안 버튼이 움직임을 먼저 소비해도 끌기를 놓치지 않도록 Initial 단계에서 판단하고, 가로로 움직이기 전에 손을 떼면
 * 아무것도 소비하지 않아 버튼이 그대로 눌린다.
 */
private fun Modifier.drawerDrag(state: GlassDrawerState, direction: Float, scope: CoroutineScope): Modifier = pointerInput(direction) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val open = state.progress > 0f
        val edge = EDGE_WIDTH.toPx()
        val fromEdge = if (direction > 0f) down.position.x <= edge else down.position.x >= size.width - edge
        if (!open && !fromEdge) return@awaitEachGesture
        val opening = if (open) -1f else 1f
        val start = awaitDrawerSlop(down, direction * opening) ?: return@awaitEachGesture
        val tracker = VelocityTracker()
        var value = state.progress
        var change = start
        while (change.pressed) {
            tracker.addPosition(change.uptimeMillis, change.position)
            value = (value + direction * (change.position.x - change.previousPosition.x) / state.drawerWidth).coerceIn(0f, 1f)
            val next = value
            scope.launch { state.snapTo(next) }
            change.consume()
            change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
        }
        change.consume()
        val velocity = direction * tracker.calculateVelocity().x
        val fling = FLING_VELOCITY.toPx()
        scope.launch { if (velocity > fling || (velocity > -fling && value > HALF)) state.open() else state.close() }
    }
}

/** 가로로 터치 슬롭을 넘게 [sign] 방향(+면 오른쪽)으로 움직이면 그 이벤트를 돌려준다. 세로로 먼저 움직이거나 손을 떼면 `null`이다. */
private suspend fun AwaitPointerEventScope.awaitDrawerSlop(down: PointerInputChange, sign: Float): PointerInputChange? {
    val slop = viewConfiguration.touchSlop
    var change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
    var decided = false
    while (change != null && change.pressed && !decided) {
        val delta = change.position - down.position
        // 세로로 먼저 움직였거나 가로 슬롭을 넘었으면 더 기다리지 않는다.
        decided = (abs(delta.y) > slop && abs(delta.y) > abs(delta.x)) || abs(delta.x) > slop
        if (!decided) change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
    }
    val delta = change?.let { it.position - down.position } ?: return null
    val horizontal = change.pressed && abs(delta.x) > slop && abs(delta.x) >= abs(delta.y) && delta.x * sign > 0f
    return if (horizontal) change.also { it.consume() } else null
}

private val CornerShapes = Array(MAX_CORNER_DP + 1) { RoundedCornerShape(it.dp) }
private val DrawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
private val LightBehind = Color(0xFFE4E7EE)
private val DarkBehind = Color(0xFF0D0E12)
private val DRAWER_MAX_WIDTH = 304.dp
private val EDGE_WIDTH = 24.dp
private val EDGE_EXCLUSION_HEIGHT = 200.dp
private val PUSH_SHADOW = 24.dp
private val FLING_VELOCITY = 400.dp
private const val MAX_CORNER_DP = 32
private const val DRAWER_FRACTION = 0.82f
private const val DRAWER_DAMPING = 0.86f
private const val PUSH_SCALE = 0.14f
private const val PUSH_SHIFT = 0.86f
private const val PUSH_DIM = 0.08f
private const val BACK_PEEK = 0.4f
private const val HALF = 0.5f
