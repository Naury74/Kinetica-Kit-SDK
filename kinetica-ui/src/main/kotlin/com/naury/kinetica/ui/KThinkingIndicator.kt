package com.naury.kinetica.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * AI가 생각하는 중임을 알리는 줄. [thinking]인 동안 '생각 중'과 경과 시간을 반짝이는 글자로 보여 주고,
 * 끝나면 '12초 동안 생각함'처럼 접힌 문구로 바뀐다. [steps]가 있으면 줄을 눌러 펼쳐 추론 단계 목록을 볼 수 있다.
 * 단계 앞에는 끝난 단계는 체크, 진행 중인 단계는 도는 고리를 그린다.
 *
 * 반짝임과 고리는 줄이 화면 밖에 있거나 '애니메이션 삭제' 설정이면 멈추고 글자를 고르게 그린다.
 * TalkBack은 줄을 버튼으로 읽고 펼침 상태를 알리며, 생각이 끝나면 끝난 문구를 한 번 알린다.
 *
 * ```kotlin
 * KThinkingIndicator(thinking = phase == Phase.Thinking, steps = steps)
 * ```
 *
 * @param thinking 지금 생각 중인지. false에서 true로 바뀌면 경과 시간을 0부터 다시 센다.
 * @param elapsedSeconds 보여 줄 경과 시간(초). null이면 [thinking]인 동안 이 컴포넌트가 1초마다 센다. 음수는 0으로 본다.
 * @param initiallyExpanded 처음에 단계 목록을 펼쳐 둘지
 */
@Composable
public fun KThinkingIndicator(
    thinking: Boolean,
    modifier: Modifier = Modifier,
    steps: List<KThinkingStep> = emptyList(),
    elapsedSeconds: Int? = null,
    initiallyExpanded: Boolean = false,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val counted = countedSeconds(thinking, counting = elapsedSeconds == null)
    val seconds = (elapsedSeconds ?: counted).coerceAtLeast(0)
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val expandable = steps.isNotEmpty()
    val still = reducedMotion()
    val clock = rememberLoopClock(running = thinking && !still)
    Column(modifier.loopVisibility(clock)) {
        ThinkingHeader(ThinkingHeaderState(thinking, seconds, expandable, expandable && expanded, still), clock, surface) {
            expanded = it
        }
        AnimatedVisibility(
            expandable && expanded,
            enter = if (still) EnterTransition.None else expandVertically() + fadeIn(),
            exit = if (still) ExitTransition.None else shrinkVertically() + fadeOut(),
        ) {
            ThinkingSteps(steps, thinking, clock, still, surface)
        }
    }
}

/** 컴포넌트가 직접 센 경과 시간. 회전 같은 구성 변경에도 이어 센다. */
@Composable
private fun countedSeconds(thinking: Boolean, counting: Boolean): Int {
    var seconds by rememberSaveable { mutableIntStateOf(0) }
    var running by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(thinking, counting) {
        if (!thinking) running = false
        if (!thinking || !counting) return@LaunchedEffect
        if (!running) {
            seconds = 0
            running = true
        }
        while (true) {
            delay(SECOND_MS)
            seconds++
        }
    }
    return seconds
}

private class ThinkingHeaderState(
    val thinking: Boolean,
    val seconds: Int,
    val expandable: Boolean,
    val expanded: Boolean,
    val still: Boolean,
)

@Composable
private fun ThinkingHeader(state: ThinkingHeaderState, clock: LoopClock, surface: SurfaceStyle, onExpandedChange: (Boolean) -> Unit) {
    val duration = durationText(state.seconds)
    val text = when {
        !state.thinking -> stringResource(R.string.kinetica_ui_ai_thought_for, duration)
        state.seconds == 0 -> stringResource(R.string.kinetica_ui_ai_thinking)
        else -> stringResource(R.string.kinetica_ui_ai_thinking_for, duration)
    }
    val stateText = stringResource(if (state.expanded) R.string.kinetica_ui_expanded else R.string.kinetica_ui_collapsed)
    val ink = surface.contentColor
    val shape = shapeFor { chip }
    val rotation = animateFloatAsState(
        if (state.expanded) HALF_TURN else 0f,
        if (state.still) snap() else spring(stiffness = CHEVRON_STIFFNESS),
        label = "thinking-chevron",
    )
    Row(
        Modifier
            .heightIn(min = KMetrics.MinTouch)
            .then(
                if (state.expandable) {
                    Modifier
                        .kClickable(shape, role = Role.Button) { onExpandedChange(!state.expanded) }
                        .semantics {
                            expandedStateText(stateText)
                            if (state.expanded) {
                                collapse {
                                    onExpandedChange(false)
                                    true
                                }
                            } else {
                                expand {
                                    onExpandedChange(true)
                                    true
                                }
                            }
                        }
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) { if (!state.thinking) liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = HEADER_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HEADER_GAP),
    ) {
        Spark(state.thinking && !state.still, clock, if (state.thinking) KineticaTheme.colors.accent else surface.muted(MUTED))
        ShimmerText(text, state.thinking && !state.still, clock, ink, surface.muted(MUTED), Modifier.weight(1f, fill = false))
        if (state.expandable) {
            Image(
                rememberVectorPainter(KIcons.ChevronDown),
                null,
                Modifier.size(CHEVRON).graphicsLayer { rotationZ = rotation.value },
                colorFilter = ColorFilter.tint(surface.muted(MUTED)),
            )
        }
    }
}

@Composable
private fun durationText(seconds: Int): String = if (seconds < MINUTE) {
    stringResource(R.string.kinetica_ui_ai_seconds, seconds)
} else {
    stringResource(R.string.kinetica_ui_ai_minutes, seconds / MINUTE, seconds % MINUTE)
}

/**
 * 흐린 글자 위로 밝은 띠가 지나가는 글자. 띠는 따로 기록한 층에서 글자 모양으로만 칠한다.
 * 붓과 크기는 크기가 바뀔 때만 만들고, 프레임마다 옮기기만 한다.
 */
@Composable
private fun ShimmerText(text: String, shimmering: Boolean, clock: LoopClock, ink: Color, muted: Color, modifier: Modifier) {
    BasicText(
        text,
        modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val band = SHIMMER_BAND.toPx().coerceAtLeast(1f)
                val brush = Brush.horizontalGradient(listOf(Color.Transparent, ink, Color.Transparent), startX = 0f, endX = band)
                val bandSize = Size(band, size.height)
                val rtl = layoutDirection == LayoutDirection.Rtl
                onDrawWithContent {
                    drawContent()
                    if (!shimmering) return@onDrawWithContent
                    val travel = size.width + band * 2
                    val phase = clock.seconds / SHIMMER_PERIOD_S % 1f
                    val start = phase * travel - band
                    val x = if (rtl) size.width - start - band else start
                    translate(left = x) { drawRect(brush, Offset.Zero, bandSize, blendMode = BlendMode.SrcAtop) }
                }
            },
        style = themed(TextStyle(color = if (shimmering) muted else ink.copy(alpha = DONE_ALPHA), fontWeight = FontWeight.Medium)),
        maxLines = 2,
    )
}

/** 작은 반짝이 별. 생각하는 동안 천천히 돈다. */
@Composable
private fun Spark(spinning: Boolean, clock: LoopClock, color: Color) {
    Box(
        Modifier
            .size(SPARK)
            .graphicsLayer { if (spinning) rotationZ = clock.seconds * SPARK_SPEED % FULL_TURN }
            .drawWithCache {
                val path = Path().apply {
                    val c = size.minDimension / 2
                    val inner = c * SPARK_WAIST
                    moveTo(c, 0f)
                    quadraticTo(c + inner, c - inner, size.width, c)
                    quadraticTo(c + inner, c + inner, c, size.height)
                    quadraticTo(c - inner, c + inner, 0f, c)
                    quadraticTo(c - inner, c - inner, c, 0f)
                    close()
                }
                onDrawBehind { drawPath(path, color) }
            },
    )
}

@Composable
private fun ThinkingSteps(steps: List<KThinkingStep>, thinking: Boolean, clock: LoopClock, still: Boolean, surface: SurfaceStyle) {
    val doneText = stringResource(R.string.kinetica_ui_ai_step_done)
    val runningText = stringResource(R.string.kinetica_ui_ai_step_running)
    val stoppedText = stringResource(R.string.kinetica_ui_ai_step_stopped)
    Column(
        Modifier
            .padding(start = HEADER_PADDING + SPARK + HEADER_GAP, end = HEADER_PADDING, bottom = STEP_GAP)
            .semantics { collectionInfo = CollectionInfo(steps.size, 1) },
        verticalArrangement = Arrangement.spacedBy(STEP_GAP),
    ) {
        steps.forEachIndexed { index, step ->
            key(step.id) {
                val phase = when {
                    step.done -> StepPhase.Done
                    thinking -> StepPhase.Running
                    else -> StepPhase.Stopped
                }
                Row(
                    Modifier.semantics(mergeDescendants = true) {
                        stateDescription = when (phase) {
                            StepPhase.Done -> doneText
                            StepPhase.Running -> runningText
                            StepPhase.Stopped -> stoppedText
                        }
                        collectionItemInfo = CollectionItemInfo(index, 1, 0, 1)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(STEP_ICON_GAP),
                ) {
                    StepIcon(phase, clock, still, surface)
                    ThemedText(
                        step.title,
                        Modifier.weight(1f),
                        style = TextStyle(color = if (step.done) surface.contentColor else surface.muted(MUTED)),
                    )
                }
            }
        }
    }
}

private enum class StepPhase { Done, Running, Stopped }

@Composable
private fun StepIcon(phase: StepPhase, clock: LoopClock, still: Boolean, surface: SurfaceStyle) {
    val positive = KineticaTheme.colors.positive
    val accent = KineticaTheme.colors.accent
    val muted = surface.muted(MUTED)
    Box(
        Modifier.size(STEP_ICON).drawWithCache {
            val stroke = Stroke(STEP_STROKE.toPx(), cap = StrokeCap.Round)
            val radius = (size.minDimension - stroke.width) / 2
            val inset = Offset(stroke.width / 2, stroke.width / 2)
            val arcSize = Size(radius * 2, radius * 2)
            onDrawBehind {
                when (phase) {
                    StepPhase.Done -> drawCircle(positive.copy(alpha = DONE_FILL))

                    StepPhase.Stopped -> drawCircle(muted, radius, style = stroke)

                    StepPhase.Running -> {
                        drawCircle(accent.copy(alpha = TRACK_ALPHA), radius, style = stroke)
                        val start = if (still) -QUARTER else clock.seconds * RING_SPEED % FULL_TURN
                        drawArc(accent, start, RING_SWEEP, false, inset, arcSize, style = stroke)
                    }
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (phase == StepPhase.Done) {
            Image(rememberVectorPainter(KIcons.Check), null, Modifier.size(CHECK), colorFilter = ColorFilter.tint(positive))
        }
    }
}

private const val SECOND_MS = 1_000L
private const val MINUTE = 60
private const val HALF_TURN = 180f
private const val FULL_TURN = 360f
private const val QUARTER = 90f
private const val CHEVRON_STIFFNESS = 600f
private val HEADER_PADDING = 4.dp
private val HEADER_GAP = 8.dp
private val CHEVRON = 16.dp
private val SPARK = 16.dp
private const val SPARK_WAIST = 0.18f
private const val SPARK_SPEED = 90f
private val SHIMMER_BAND = 72.dp
private const val SHIMMER_PERIOD_S = 1.6f
private const val MUTED = 0.55f
private const val DONE_ALPHA = 0.7f
private val STEP_GAP = 8.dp
private val STEP_ICON_GAP = 10.dp
private val STEP_ICON = 18.dp
private val STEP_STROKE = 2.dp
private val CHECK = 12.dp
private const val DONE_FILL = 0.16f
private const val TRACK_ALPHA = 0.18f
private const val RING_SPEED = 300f
private const val RING_SWEEP = 100f
