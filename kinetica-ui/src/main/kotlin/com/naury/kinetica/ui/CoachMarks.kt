package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import kotlin.math.min

/**
 * 기능 안내의 한 단계.
 *
 * @property id 비출 요소의 id. [Modifier.coachMarkTarget]에 넘긴 값과 같아야 한다. 그런 요소가 없으면 구멍 없이 카드만 가운데에 띄운다.
 * @property title 카드 제목
 * @property body 카드 설명
 */
@Immutable
public data class CoachStep(val id: String, val title: String, val body: String)

/** 기능 안내의 진행 상태와 비출 요소 위치. [rememberCoachMarkState]로 만들고 [CoachMarkHost]와 [Modifier.coachMarkTarget]에 함께 넘긴다. */
@Stable
public class CoachMarkState internal constructor() {
    /** 보이는 단계의 위치(0부터). 안내가 꺼져 있으면 -1이다. */
    public var currentStep: Int by mutableIntStateOf(-1)
        private set

    /** 안내가 보이는 중인지 나타낸다. */
    public val isShowing: Boolean get() = currentStep >= 0

    internal val targets = mutableStateMapOf<String, Rect>()
    internal var stepCount: Int = 0
    internal var onFinished: (Boolean) -> Unit = {}

    /** [step]번째 단계부터 안내를 시작한다. [step]이 음수면 [IllegalArgumentException]을 던진다. */
    public fun start(step: Int = 0) {
        require(step >= 0) { "step은 0 이상이어야 합니다." }
        currentStep = step
    }

    /** 다음 단계로 넘어간다. 마지막 단계였으면 안내를 마치고 `onFinish(true)`를 부른다. 안내가 꺼져 있으면 아무 일도 하지 않는다. */
    public fun next() {
        if (!isShowing) return
        if (currentStep + 1 >= stepCount) finish(completed = true) else currentStep++
    }

    /** 남은 단계를 건너뛰고 안내를 닫은 뒤 `onFinish(false)`를 부른다. 안내가 꺼져 있으면 아무 일도 하지 않는다. */
    public fun skip() {
        if (isShowing) finish(completed = false)
    }

    private fun finish(completed: Boolean) {
        currentStep = -1
        onFinished(completed)
    }
}

/** 컴포지션에 유지되는 [CoachMarkState]를 만든다. */
@Composable
public fun rememberCoachMarkState(): CoachMarkState = remember { CoachMarkState() }

/**
 * 이 요소를 [state]의 안내에서 [id]로 비출 대상으로 등록한다. 요소가 움직이거나 크기가 바뀌면 구멍도 따라간다.
 * 요소가 컴포지션에서 빠지면 등록도 지운다. 대상이 없는 단계에서는 구멍이 화면 가운데로 오그라든다.
 */
public fun Modifier.coachMarkTarget(state: CoachMarkState, id: String): Modifier = this then CoachTargetElement(state, id)

private data class CoachTargetElement(val state: CoachMarkState, val id: String) : ModifierNodeElement<CoachTargetNode>() {
    override fun create(): CoachTargetNode = CoachTargetNode(state, id)

    override fun update(node: CoachTargetNode) {
        if (node.id != id || node.state !== state) node.forget()
        node.state = state
        node.id = id
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "coachMarkTarget"
    }
}

private class CoachTargetNode(var state: CoachMarkState, var id: String) :
    Modifier.Node(),
    GlobalPositionAwareModifierNode {
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val bounds = Rect(coordinates.positionOnScreen(), coordinates.size.toSize())
        if (state.targets[id] != bounds) state.targets[id] = bounds
    }

    override fun onDetach() = forget()

    fun forget() {
        state.targets.remove(id)
    }
}

/**
 * 기능 안내 오버레이. [CoachMarkState.start]를 부르면 화면이 어두워지고 지금 단계의 대상 요소 둘레만 둥글게 뚫려 밝게 보인다.
 * 옆에는 제목, 설명, 단계 표시, 다음과 건너뛰기 버튼이 있는 글래스 카드가 뜬다. 다음 단계로 넘어가면 구멍이 새 대상으로 부드럽게
 * 옮겨 가며 크기가 바뀐다. 구멍을 눌러도 다음 단계로 가고, 뒤로 가기를 하면 건너뛴다.
 *
 * [GlassScaffold] 안에서는 바까지 덮는 오버레이 레이어에 그린다. 밖에서 쓰면 호출한 위치에 그리므로 화면을 감싼 `Box`의 마지막 자식으로 둔다.
 * TalkBack은 단계가 바뀔 때마다 제목과 설명을 읽으며, 설명에 있는 사용자 지정 동작으로 다음 단계로 가거나 건너뛸 수 있다.
 *
 * ```kotlin
 * val coach = rememberCoachMarkState()
 * Box {
 *     IconButton(Modifier.coachMarkTarget(coach, "search")) { ... }
 *     CoachMarkHost(coach, listOf(CoachStep("search", "검색", "여기서 장소를 찾을 수 있어요")))
 * }
 * LaunchedEffect(Unit) { coach.start() }
 * ```
 *
 * @param steps 안내 단계. 비어 있으면 안내를 띄우지 않는다.
 * @param onFinish 안내가 끝나면 부른다. 마지막 단계까지 봤으면 true, 건너뛰었으면 false를 받는다.
 */
@Composable
public fun CoachMarkHost(
    state: CoachMarkState,
    steps: List<CoachStep>,
    modifier: Modifier = Modifier,
    onFinish: (completed: Boolean) -> Unit = {},
    style: GlassStyle = LocalGlassStyle.current,
) {
    val finish by rememberUpdatedState(onFinish)
    SideEffect {
        state.stepCount = steps.size
        state.onFinished = { finish(it) }
    }
    val visible = state.isShowing && state.currentStep < steps.size
    // 닫히는 동안 steps가 줄거나 비어도 마지막으로 보인 단계를 그리도록 목록과 위치를 함께 기억해 둔다.
    val shown = remember { ShownStep() }
    if (visible) {
        shown.steps = steps
        shown.index = state.currentStep
    }
    GlassOverlay(visible) { transition ->
        val last = shown.steps
        if (last.isNotEmpty()) {
            CoachLayer(CoachFrame(state, last, shown.index.coerceIn(last.indices), style), transition, modifier)
        }
    }
}

/** 마지막으로 보인 단계 */
private class ShownStep {
    var steps: List<CoachStep> by mutableStateOf(emptyList())
    var index: Int by mutableIntStateOf(0)
}

/** 한 프레임에 그릴 안내 내용. 파라미터 수를 줄이려고 묶었다. */
private class CoachFrame(val state: CoachMarkState, val steps: List<CoachStep>, val index: Int, val style: GlassStyle) {
    val step: CoachStep get() = steps[index]
}

@Composable
private fun CoachLayer(frame: CoachFrame, transition: Transition<Boolean>, modifier: Modifier) {
    val level by transition.animateFloat(
        transitionSpec = { if (targetState) tween(FADE_IN_MS) else tween(FADE_OUT_MS) },
        label = "coach-mark",
    ) { if (it) 1f else 0f }
    BackHandler(enabled = transition.targetState) { frame.state.skip() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val target = frame.state.targets[frame.step.id]?.translate(-origin)
    val hole = rememberHole(target)
    val ring = rememberRingPulse()
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionOnScreen() }
            .graphicsLayer { alpha = level },
    ) {
        Spotlight(hole, ring) { frame.state.next() }
        CoachCard(frame, hole)
    }
}

/** 구멍 위치(px, 레이어 기준). 처음에는 대상에 바로 놓고, 단계가 바뀌면 새 대상으로 용수철처럼 옮긴다. 대상이 없으면 가운데로 오그라든다. */
@Composable
private fun rememberHole(target: Rect?): State<Rect> {
    val hole = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val pad = with(LocalDensity.current) { HOLE_PADDING.toPx() }
    val padded = target?.inflate(pad)
    LaunchedEffect(padded) {
        val next = padded ?: Rect(hole.value.center, hole.value.center)
        if (hole.value.isEmpty) {
            hole.snapTo(next)
        } else {
            hole.animateTo(next, spring(dampingRatio = HOLE_DAMPING, stiffness = Spring.StiffnessMediumLow))
        }
    }
    return hole.asState()
}

/** 구멍 둘레에서 바깥으로 번지며 사라지는 테두리의 진행(0..1). '애니메이션 삭제'가 켜져 있으면 0에 머문다. */
@Composable
private fun rememberRingPulse(): State<Float> {
    if (reducedMotion()) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "coach-ring").animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(RING_MS), RepeatMode.Restart),
        label = "coach-ring",
    )
}

@Composable
private fun Spotlight(hole: State<Rect>, ring: State<Float>, onHoleTap: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                drawRect(Color.Black.copy(alpha = DIM_ALPHA))
                val r = hole.value
                if (r.isEmpty) return@drawBehind
                val corner = min(min(r.width, r.height) / 2f, HOLE_CORNER.toPx())
                drawRoundRect(Color.Black, r.topLeft, r.size, CornerRadius(corner), blendMode = BlendMode.Clear)
                val p = ring.value
                val grow = RING_GROW.toPx() * p
                drawRoundRect(
                    Color.White.copy(alpha = RING_ALPHA * (1f - p)),
                    Offset(r.left - grow, r.top - grow),
                    r.size.copy(r.width + grow * 2, r.height + grow * 2),
                    CornerRadius(corner + grow),
                    style = Stroke(RING_WIDTH.toPx()),
                )
            }
            .clearAndSetSemantics { }
            .pointerInput(Unit) {
                // 아래 화면이 눌리지 않도록 모든 터치를 소비하고, 구멍 안을 누른 경우만 다음 단계로 간다.
                awaitEachGesture {
                    awaitFirstDown().consume()
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    up.consume()
                    if (hole.value.contains(up.position)) onHoleTap()
                }
            },
    )
}

@Composable
private fun CoachCard(frame: CoachFrame, hole: State<Rect>) {
    val style = frame.style
    Column(
        Modifier
            .layout { measurable, constraints ->
                val margin = CARD_MARGIN.toPx()
                val maxWidth = (constraints.maxWidth - margin * 2).toInt().coerceIn(0, CARD_MAX_WIDTH.roundToPx())
                val card = measurable.measure(Constraints(minWidth = maxWidth, maxWidth = maxWidth))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val container = IntSize(constraints.maxWidth, constraints.maxHeight)
                    val r = hole.value
                    val anchor = if (r.isEmpty) Rect(Offset(container.width / 2f, container.height / 2f), 0f) else r
                    val spacing = TooltipSpacing(margin, CARD_GAP.toPx(), 0f)
                    val placed = placeTooltip(anchor, container, IntSize(card.width, card.height), GlassTooltipPlacement.Below, spacing)
                    val y = if (r.isEmpty) (container.height - card.height) / 2 else placed.y
                    card.place(placed.x, y)
                }
            }
            .glass(style.forPopup(), RoundedCornerShape(CARD_CORNER))
            .semantics { paneTitle = frame.step.title }
            .padding(20.dp),
    ) {
        CoachText(frame)
        Spacer(Modifier.height(16.dp))
        CoachControls(frame)
    }
}

@Composable
private fun CoachText(frame: CoachFrame) {
    val state = frame.state
    val nextLabel = stringResource(R.string.kinetica_ui_coach_next)
    val skipLabel = stringResource(R.string.kinetica_ui_coach_skip)
    val stepText = stringResource(R.string.kinetica_ui_coach_step, frame.index + 1, frame.steps.size)
    AnimatedContent(
        targetState = frame.index,
        transitionSpec = {
            fadeIn(tween(TEXT_IN_MS, TEXT_DELAY_MS)) togetherWith fadeOut(tween(TEXT_OUT_MS)) using
                SizeTransform(clip = false)
        },
        label = "coach-text",
    ) { index ->
        val step = frame.steps[index]
        Column(
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "${step.title}. ${step.body}. $stepText"
                    customActions = listOf(
                        CustomAccessibilityAction(nextLabel) {
                            state.next()
                            true
                        },
                        CustomAccessibilityAction(skipLabel) {
                            state.skip()
                            true
                        },
                    )
                },
        ) {
            ThemedText(
                step.title,
                Modifier.semantics { heading() },
                style = TextStyle(color = frame.style.onGlass, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(6.dp))
            ThemedText(
                step.body,
                style = TextStyle(color = frame.style.onGlass.copy(alpha = BODY_ALPHA), fontSize = 15.sp, lineHeight = 21.sp),
            )
        }
    }
}

@Composable
private fun CoachControls(frame: CoachFrame) {
    val last = frame.index == frame.steps.lastIndex
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepDots(frame.steps.size, frame.index, frame.style.onGlass)
        Spacer(Modifier.weight(1f))
        if (!last) {
            CoachButton(stringResource(R.string.kinetica_ui_coach_skip), filled = false, frame.style) { frame.state.skip() }
        }
        CoachButton(
            stringResource(if (last) R.string.kinetica_ui_coach_done else R.string.kinetica_ui_coach_next),
            filled = true,
            frame.style,
        ) { frame.state.next() }
    }
}

@Composable
private fun StepDots(count: Int, current: Int, color: Color) {
    Row(Modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) DOT_ACTIVE else DOT_SIZE, spring(dampingRatio = 0.7f), label = "coach-dot")
            Box(
                Modifier
                    .size(width, DOT_SIZE)
                    .background(color.copy(alpha = if (i == current) 1f else DOT_IDLE_ALPHA), CircleShape),
            )
        }
    }
}

@Composable
private fun CoachButton(text: String, filled: Boolean, style: GlassStyle, onClick: () -> Unit) {
    val ink = style.onGlass
    val onInk = if (ink.luminance() > LIGHT_INK) Color.Black else Color.White
    Box(
        Modifier
            .heightIn(min = BUTTON_HEIGHT)
            .then(if (filled) Modifier.background(ink, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        ThemedText(
            text,
            style = TextStyle(
                color = if (filled) onInk else ink.copy(alpha = BODY_ALPHA),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
    if (!filled) Spacer(Modifier.width(4.dp))
}

private const val FADE_IN_MS = 260
private const val FADE_OUT_MS = 180
private const val TEXT_IN_MS = 220
private const val TEXT_DELAY_MS = 90
private const val TEXT_OUT_MS = 90
private const val HOLE_DAMPING = 0.8f
private const val DIM_ALPHA = 0.62f
private const val RING_ALPHA = 0.7f
private const val RING_MS = 1_400
private const val BODY_ALPHA = 0.78f
private const val DOT_IDLE_ALPHA = 0.3f
private const val LIGHT_INK = 0.5f
private val HOLE_CORNER = 28.dp
private val HOLE_PADDING = 8.dp
private val RING_GROW = 10.dp
private val RING_WIDTH = 2.dp
private val CARD_MARGIN = 16.dp
private val CARD_GAP = 14.dp
private val CARD_MAX_WIDTH = 360.dp
private val CARD_CORNER = 24.dp
private val DOT_SIZE = 6.dp
private val DOT_ACTIVE = 16.dp
private val BUTTON_HEIGHT = 40.dp
