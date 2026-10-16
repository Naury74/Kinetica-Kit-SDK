package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** [KDetentSheet]가 멈추는 높이. */
@Immutable
public sealed interface Detent {
    /**
     * 화면 아래에서 [height]만큼만 보이는 높이. 지도 위에 제목만 살짝 보이게 둘 때 쓴다.
     * [height]가 음수면 [IllegalArgumentException]을 던진다.
     */
    public data class Peek(val height: Dp) : Detent {
        init {
            require(height >= 0.dp) { "height는 0 이상이어야 합니다." }
        }
    }

    /** 시트를 담은 영역 높이의 [fraction]만큼 보이는 높이. [fraction]이 0..1 밖이면 [IllegalArgumentException]을 던진다. */
    public data class Fraction(val fraction: Float) : Detent {
        init {
            require(fraction in 0f..1f) { "fraction은 0..1이어야 합니다." }
        }
    }

    /** 상태 표시줄 바로 아래까지 펼친 높이. 이 높이에서만 시트 안 목록이 스크롤된다. */
    public data object Full : Detent

    /** 화면 아래로 완전히 내려가 보이지 않는 상태. `dismissible`인 상태에서만 쓸 수 있다. */
    public data object Hidden : Detent
}

/**
 * [KDetentSheet]의 높이 상태. [rememberKDetentSheetState]로 만든다.
 *
 * @property detents 시트가 멈출 수 있는 높이. 순서는 상관없다.
 * @property dismissible true면 가장 낮은 높이에서 더 끌어내려 [Detent.Hidden]으로 숨길 수 있다.
 */
@Stable
public class KDetentSheetState internal constructor(
    public val detents: List<Detent>,
    initialDetent: Detent,
    public val dismissible: Boolean,
) {
    /** 시트가 마지막으로 멈춘 높이. 끄는 동안에는 바뀌지 않는다. */
    public var currentDetent: Detent by mutableStateOf(initialDetent)
        private set

    internal val stops: List<Detent> = if (dismissible) detents + Detent.Hidden else detents

    // 시트 윗변의 y 위치(px, 시트를 담은 영역 기준). 측정 전에는 NaN이다.
    internal var offset by mutableFloatStateOf(Float.NaN)
        private set
    internal var containerHeight by mutableFloatStateOf(0f)
        private set
    private var fullTop = 0f
    private var bottomInset = 0f
    private var density: Density = Density(1f)
    private var animation: Job? = null

    init {
        require(detents.isNotEmpty()) { "detents가 비어 있으면 안 됩니다." }
        require(Detent.Hidden !in detents) { "Hidden은 detents에 넣지 말고 dismissible로 켭니다." }
        require(initialDetent in stops) { "initialDetent가 detents에 없습니다: $initialDetent" }
    }

    /**
     * [detent] 높이로 용수철 애니메이션과 함께 이동하고, 도착할 때까지 일시 중단한다. 사용자가 시트를 끌면 애니메이션이 멈추고
     * `CancellationException`을 던진다. [detent]가 [detents]에 없거나, `dismissible`이 아닌데 [Detent.Hidden]이면
     * [IllegalArgumentException]을 던진다.
     */
    public suspend fun animateTo(detent: Detent) {
        require(detent in stops) { "detent가 이 시트의 높이 목록에 없습니다: $detent" }
        animateTo(detent, 0f)
    }

    /** 애니메이션 없이 [detent] 높이로 바로 옮긴다. 인자 조건은 [animateTo]와 같다. */
    public fun snapTo(detent: Detent) {
        require(detent in stops) { "detent가 이 시트의 높이 목록에 없습니다: $detent" }
        animation?.cancel()
        currentDetent = detent
        if (containerHeight > 0f) offset = positionOf(detent)
    }

    /** [bottom]은 내비게이션 바나 키보드가 가리는 아래쪽 높이(px)다. [Detent.Peek]과 [Detent.Fraction]은 그 위에서 잰다. */
    internal fun updateLayout(height: Float, top: Float, density: Density, bottom: Float = 0f) {
        this.density = density
        val same = height == containerHeight && top == fullTop && bottom == bottomInset
        if (same && !offset.isNaN()) return
        containerHeight = height
        fullTop = top.coerceAtMost(height)
        bottomInset = bottom.coerceIn(0f, height)
        animation?.cancel()
        offset = positionOf(currentDetent)
    }

    internal fun positionOf(detent: Detent): Float {
        val h = containerHeight
        val y = when (detent) {
            is Detent.Peek -> h - bottomInset - with(density) { detent.height.toPx() }
            is Detent.Fraction -> (h - bottomInset) * (1f - detent.fraction)
            Detent.Full -> fullTop
            Detent.Hidden -> h
        }
        return y.coerceIn(fullTop, h)
    }

    private val minOffset: Float get() = stops.minOf { positionOf(it) }
    private val maxOffset: Float get() = stops.maxOf { positionOf(it) }

    /** 시트를 [delta](px, 아래가 양수)만큼 옮기고 실제로 옮긴 양을 돌려준다. 진행 중인 애니메이션은 멈춘다. */
    internal fun dragBy(delta: Float): Float {
        if (offset.isNaN() || delta == 0f) return 0f
        animation?.cancel()
        val next = (offset + delta).coerceIn(minOffset, maxOffset)
        val used = next - offset
        offset = next
        return used
    }

    internal val isSettled: Boolean get() = offset.isNaN() || abs(offset - positionOf(currentDetent)) < SETTLED_PX

    /** 높이를 옮기는 애니메이션이 도는 중이면 true다. */
    internal val isAnimating: Boolean get() = animation?.isActive == true

    /** 손을 뗀 속도(px/s, 아래가 양수)를 보고 다음 높이를 골라 그리로 간다. */
    internal suspend fun settle(velocity: Float) {
        if (offset.isNaN()) return
        animateTo(targetFor(velocity), velocity)
    }

    internal fun targetFor(velocity: Float): Detent {
        val projected = offset + velocity * PROJECTION_SECONDS
        val nearest = stops.minBy { abs(positionOf(it) - projected) }
        if (abs(velocity) < with(density) { FLING_VELOCITY.toPx() }) return nearest
        // 빠르게 튕기면 적어도 한 칸은 그 방향으로 간다.
        val ahead = stops.filter { (positionOf(it) - offset) * velocity > SETTLED_PX }
        if (ahead.isEmpty() || nearest in ahead) return nearest
        return ahead.minBy { abs(positionOf(it) - offset) }
    }

    private suspend fun animateTo(detent: Detent, velocity: Float) {
        if (offset.isNaN()) {
            currentDetent = detent
            return
        }
        animation?.cancel()
        coroutineScope {
            animation = coroutineContext[Job]
            animate(offset, positionOf(detent), velocity, spring(SHEET_DAMPING, Spring.StiffnessMediumLow)) { value, _ -> offset = value }
            currentDetent = detent
        }
    }

    /** 맨 위가 [Detent.Full]일 때 그 바로 아래 높이에서 Full로 갈수록 0에서 1로 커지는 값. 스크림 진하기에 쓴다. */
    internal fun scrimLevel(): Float {
        if (offset.isNaN() || Detent.Full !in detents) return 0f
        val top = positionOf(Detent.Full)
        val below = detents.map { positionOf(it) }.filter { it > top + SETTLED_PX }.minOrNull() ?: return 0f
        val t = ((below - offset) / (below - top)).coerceIn(0f, 1f)
        return t * t
    }

    /** 위로 한 칸 펼친 높이. 가장 높이 펼쳐져 있으면 `null`이다. */
    internal fun higher(): Detent? = detents.filter {
        positionOf(it) < positionOf(currentDetent) - SETTLED_PX
    }.maxByOrNull { positionOf(it) }

    /** 아래로 한 칸 접은 높이. [Detent.Hidden]은 [includeHidden]일 때만 고른다. */
    internal fun lower(includeHidden: Boolean): Detent? = (if (includeHidden) stops else detents)
        .filter { positionOf(it) > positionOf(currentDetent) + SETTLED_PX }
        .minByOrNull { positionOf(it) }

    internal val lowestVisible: Detent get() = detents.maxBy { positionOf(it) }

    internal companion object {
        fun saver(detents: List<Detent>, dismissible: Boolean): Saver<KDetentSheetState, Int> = Saver(
            save = { it.stops.indexOf(it.currentDetent) },
            restore = { index ->
                val stops = if (dismissible) detents + Detent.Hidden else detents
                KDetentSheetState(detents, stops.getOrElse(index) { detents.first() }, dismissible)
            },
        )
    }
}

/**
 * [KDetentSheet]의 상태를 만들고 화면 회전 뒤에도 마지막 높이를 기억한다.
 *
 * @param detents 멈출 높이 목록. 비어 있거나 [Detent.Hidden]이 들어 있으면 [IllegalArgumentException]을 던진다.
 * @param initialDetent 처음 높이. [detents]에 없으면(`dismissible`일 때 [Detent.Hidden]은 허용) [IllegalArgumentException]을 던진다.
 * @param dismissible true면 가장 낮은 높이에서 아래로 끌거나 뒤로 가기를 해서 시트를 숨길 수 있다. 지도 위에 늘 떠 있어야 하면 false로 둔다.
 */
@Composable
public fun rememberKDetentSheetState(
    detents: List<Detent> = DefaultDetents,
    initialDetent: Detent = detents.first(),
    dismissible: Boolean = false,
): KDetentSheetState = rememberSaveable(detents, dismissible, saver = KDetentSheetState.saver(detents, dismissible)) {
    KDetentSheetState(detents, initialDetent, dismissible)
}

/**
 * Apple 지도처럼 여러 높이에 멈추는 바텀 시트. 손가락을 따라 움직이고, 손을 떼면 속도를 보고 다음 높이를 골라 용수철처럼 멈춘다.
 * 시트 안 `LazyColumn`은 [Detent.Full]에서만 스크롤되고, 그 아래 높이에서 목록을 위로 밀면 시트가 먼저 펼쳐진다.
 * 목록이 맨 위에 있을 때 아래로 끌면 목록 대신 시트가 내려간다. 시트가 [Detent.Full]에 가까워질수록 배경 스크림이 진해지며,
 * 스크림을 누르거나 뒤로 가기를 하면 한 단계 접힌다.
 *
 * 부모 영역을 꽉 채우고 그 아래쪽에 시트를 그리므로 지도나 목록 위에 겹쳐 둔다. TalkBack에서는 펼치기와 접기 사용자 지정 동작을 쓸 수 있다.
 * [KineticaScaffold] 밖에서도 그 자리에 그대로 그린다(창을 따로 띄우지 않는다).
 *
 * 시트 높이는 부모 높이에서 위쪽 안전 영역과 8dp를 뺀 값으로 고정이고, [content]는 머리 영역 아래 남은 높이를 받는다.
 * [contentWindowInsets]의 아래쪽(내비게이션 바, 키보드)은 시트가 피한다. [Detent.Peek]과 [Detent.Fraction]은 그 위에서 재므로
 * 키보드가 올라오면 시트도 그만큼 올라가고, 펼친 시트의 내용은 그만큼 안쪽으로 들어간다.
 *
 * ```kotlin
 * val sheet = rememberKDetentSheetState()
 * Box {
 *     MapView()
 *     KDetentSheet(sheet, header = { SearchField() }) {
 *         LazyColumn(Modifier.fillMaxSize()) { items(places) { PlaceRow(it) } }
 *     }
 * }
 * ```
 *
 * @param title 시트 이름. TalkBack이 창 제목으로 읽는다.
 * @param header 핸들 아래에 고정되어 스크롤되지 않는 영역
 * @param content 남은 높이를 채우는 본문. 스크롤 목록은 `fillMaxSize`로 둔다.
 * @param contentWindowInsets 시트가 피할 시스템 영역. 기본값은 [KSheetDefaults.windowInsets]다.
 */
@Composable
public fun KDetentSheet(
    state: KDetentSheetState,
    modifier: Modifier = Modifier,
    surface: SurfaceStyle = KineticaTheme.surface,
    title: String? = null,
    header: @Composable ColumnScope.() -> Unit = {},
    contentWindowInsets: @Composable () -> WindowInsets = { KSheetDefaults.windowInsets },
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val topGap = with(density) { (WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + FULL_GAP).toPx() }
    val insets = contentWindowInsets()
    val bottomGap = insets.getBottom(density).toFloat()
    val scope = rememberCoroutineScope()
    val scrimShown by remember(state) { derivedStateOf { state.scrimLevel() > 0f } }
    // 뒤로 가기는 펼친 시트를 가장 낮은 높이로 접고, 이미 가장 낮으면 숨길 수 있을 때만 숨긴다.
    val backTarget = when (state.currentDetent) {
        Detent.Hidden -> null
        state.lowestVisible -> Detent.Hidden.takeIf { state.dismissible }
        else -> state.lowestVisible
    }
    BackHandler(enabled = backTarget != null) { backTarget?.let { scope.launch { state.animateTo(it) } } }
    Box(modifier.fillMaxSize().onSizeChanged { state.updateLayout(it.height.toFloat(), topGap, density, bottomGap) }) {
        LaunchedEffect(topGap, bottomGap) {
            if (state.containerHeight >
                0f
            ) {
                state.updateLayout(state.containerHeight, topGap, density, bottomGap)
            }
        }
        if (scrimShown) SheetScrim(state) { state.lower(includeHidden = false)?.let { scope.launch { state.animateTo(it) } } }
        // 세로로 반쯤 접은 폴더블에서는 접힌 선에 걸치지 않도록 시작 쪽 칸에 둔다.
        Box(Modifier.fillMaxSize().avoidHinge(horizontal = false)) {
            Column(
                Modifier
                    // 태블릿이나 펼친 폴더블에서는 가운데에 읽기 좋은 폭으로 둔다.
                    .align(Alignment.TopCenter)
                    .widthIn(max = KSheetDefaults.MaxWidth)
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val height = (constraints.maxHeight - topGap.toInt()).coerceAtLeast(0)
                        val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .graphicsLayer { translationY = state.offset.takeUnless { it.isNaN() } ?: state.containerHeight }
                    .kSurface(surface.forPopup(), SheetShape)
                    .sheetSemantics(state, title, scope)
                    .nestedScroll(remember(state) { SheetScrollConnection(state) })
                    .draggable(
                        rememberDraggableState { state.dragBy(it) },
                        Orientation.Vertical,
                        onDragStopped = { velocity -> state.settle(velocity) },
                    )
                    .settleAfterRelease({ !state.isSettled && !state.isAnimating }) { scope.launch { state.settle(0f) } },
            ) {
                SheetHandle(surface) {
                    val next = state.higher() ?: state.lowestVisible
                    scope.launch { state.animateTo(next) }
                }
                CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor, LocalGlassLayered provides true) {
                    header()
                    Column(Modifier.weight(1f).windowInsetsPadding(insets), content = content)
                }
            }
        }
    }
}

@Composable
private fun SheetScrim(state: KDetentSheetState, onClick: () -> Unit) {
    val closeLabel = stringResource(R.string.kinetica_ui_sheet_collapse)
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black, alpha = SCRIM_ALPHA * state.scrimLevel()) }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = closeLabel,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = closeLabel },
    )
}

@Composable
private fun SheetHandle(surface: SurfaceStyle, onTap: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { }
            .pointerInput(Unit) { detectTapGestures { onTap() } }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(HANDLE_WIDTH, HANDLE_HEIGHT).background(surface.contentColor.copy(alpha = HANDLE_ALPHA), CircleShape))
    }
}

/** 시트 이름, 현재 높이, 펼치기와 접기 동작을 TalkBack에 알린다. 완전히 숨으면 읽지 않는다. */
@Composable
private fun Modifier.sheetSemantics(state: KDetentSheetState, title: String?, scope: kotlinx.coroutines.CoroutineScope): Modifier {
    val current = state.currentDetent
    if (current == Detent.Hidden) return clearAndSetSemantics { }
    val expandLabel = stringResource(R.string.kinetica_ui_sheet_expand)
    val collapseLabel = stringResource(R.string.kinetica_ui_sheet_collapse)
    val stateText = stringResource(
        when {
            state.higher() == null -> R.string.kinetica_ui_sheet_expanded
            current == state.lowestVisible -> R.string.kinetica_ui_sheet_collapsed
            else -> R.string.kinetica_ui_sheet_partial
        },
    )
    val up = state.higher()
    val down = state.lower(includeHidden = false)
    return semantics {
        title?.let { paneTitle = it }
        stateDescription = stateText
        customActions = listOfNotNull(
            up?.let {
                CustomAccessibilityAction(expandLabel) {
                    scope.launch { state.animateTo(it) }
                    true
                }
            },
            down?.let {
                CustomAccessibilityAction(collapseLabel) {
                    scope.launch { state.animateTo(it) }
                    true
                }
            },
        )
        if (state.dismissible) {
            dismiss {
                scope.launch { state.animateTo(Detent.Hidden) }
                true
            }
        }
    }
}

/**
 * 시트와 안쪽 목록의 스크롤을 잇는다. 위로 밀면 시트가 맨 위에 닿을 때까지 시트가 먼저 움직이고, 아래로 끌면 목록이 맨 위에 닿은 뒤
 * 남은 양으로 시트가 내려간다. 플링은 시트가 높이 사이에 있을 때만 가로채 가장 알맞은 높이로 보낸다.
 */
private class SheetScrollConnection(private val state: KDetentSheetState) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0f && source == NestedScrollSource.UserInput) Offset(0f, state.dragBy(available.y)) else Offset.Zero

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (available.y > 0f && source == NestedScrollSource.UserInput) Offset(0f, state.dragBy(available.y)) else Offset.Zero

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (state.isSettled) return Velocity.Zero
        state.settle(available.y)
        return available
    }
}

internal val DefaultDetents: List<Detent> = listOf(Detent.Peek(120.dp), Detent.Fraction(0.5f), Detent.Full)
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val FULL_GAP = 8.dp
private val FLING_VELOCITY = 400.dp
private val HANDLE_WIDTH = 36.dp
private val HANDLE_HEIGHT = 5.dp
private const val HANDLE_ALPHA = 0.3f
private const val SHEET_DAMPING = 0.86f
private const val PROJECTION_SECONDS = 0.18f
private const val SETTLED_PX = 1f
private const val SCRIM_ALPHA = 0.32f
