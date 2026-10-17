package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** 떠오르는 반응 하나 */
internal class FloatingReaction(val id: Long, val emoji: String, val lane: Float, val sway: Float) {
    val progress = Animatable(0f)

    // '애니메이션 삭제'를 바꿔 효과가 다시 시작해도 색종이는 한 번만 터뜨린다.
    var burst = false
}

/**
 * [KReactionOverlay]에 반응을 띄우는 상태. [send]를 부르면 아래에서 이모지가 떠올라 사라진다.
 * 한 번에 최대 [MAX_FLOATING]개만 그리고, 넘치면 오래된 것부터 지운다.
 */
@Stable
public class KReactionState internal constructor(private val random: Random) {
    internal val items = mutableStateListOf<FloatingReaction>()
    internal var announcement by mutableStateOf<Pair<String?, String>?>(null)
    private var nextId = 0L

    /**
     * 반응 하나를 띄운다. TalkBack에는 마지막 반응을 조용히 읽어 준다.
     *
     * @param emoji 띄울 이모지나 짧은 글자
     * @param from 보낸 사람 이름. TalkBack이 '민지 님의 반응 👍'처럼 읽는다. null이면 이모지만 읽는다.
     */
    public fun send(emoji: String, from: String? = null) {
        while (items.size >= MAX_FLOATING) items.removeAt(0)
        items += FloatingReaction(nextId++, emoji, LANE_MIN + random.nextFloat() * (LANE_MAX - LANE_MIN), random.nextFloat() * 2 - 1)
        announcement = from to emoji
    }

    internal fun remove(item: FloatingReaction) {
        items.remove(item)
    }

    public companion object {
        /** 한 번에 그리는 최대 개수 */
        public const val MAX_FLOATING: Int = 8

        /** [KReactionOverlay]의 색종이가 이보다 많이 날리고 있으면 새 반응으로 더 터뜨리지 않는다. */
        public const val CONFETTI_MAX_PIECES: Int = 160
    }
}

/** [KReactionState]를 만든다. */
@Composable
public fun rememberKReactionState(): KReactionState = remember { KReactionState(Random.Default) }

/**
 * 받은 반응이 아래에서 위로 흔들리며 떠오르다 사라지는 층. 화면 전체를 덮지만 터치는 막지 않는다. 조작 막대가 있으면 [bottomPadding]으로
 * 그 위에서 떠오르게 한다. 움직임은 그리기 단계에서 읽어 떠오르는 동안 다시 구성하지 않는다. '애니메이션 삭제'면 제자리에서 나타났다 사라진다.
 *
 * ```kotlin
 * val reactions = rememberKReactionState()
 * LaunchedEffect(Unit) { room.reactions.collect { reactions.send(it.emoji, it.senderName) } }
 * KReactionOverlay(reactions, bottomPadding = 120.dp)
 * ```
 *
 * @param bottomPadding 떠오르기 시작하는 높이(아래 끝에서)
 * @param confetti 이 이모지가 오면 떠오르는 자리에서 [ConfettiHost] 색종이도 함께 터뜨린다. 예: `setOf("🎉")`. 기본값은 빈 집합(끔)이다.
 *   '애니메이션 삭제'면 터뜨리지 않는다. 이미 날리는 색종이가 [KReactionState.CONFETTI_MAX_PIECES]개를 넘으면 그 반응은 건너뛰어 연달아 와도 쌓이지 않는다.
 */
@Composable
public fun KReactionOverlay(
    state: KReactionState,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 120.dp,
    confetti: Set<String> = emptySet(),
) {
    ReactionOverlay(state, modifier, bottomPadding, confetti, if (confetti.isEmpty()) null else rememberConfettiState())
}

@Composable
internal fun ReactionOverlay(state: KReactionState, modifier: Modifier, bottomPadding: Dp, confetti: Set<String>, burst: ConfettiState?) {
    val still = reducedMotion()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(modifier.fillMaxSize()) {
        val travel = maxHeight * TRAVEL
        val width = maxWidth
        val height = maxHeight
        state.items.forEach { item ->
            key(item.id) {
                LaunchedEffect(item, still) {
                    if (burst != null && !still && item.emoji in confetti) {
                        // 이모지 자리는 시작 쪽 기준으로 미러링되지만 색종이 층은 화면 좌표라 RTL에서 뒤집는다.
                        val x = constraints.maxWidth * if (rtl) 1f - item.lane else item.lane
                        burst.burstFor(item, Offset(x, constraints.maxHeight - with(density) { (bottomPadding + EMOJI / 2).toPx() }))
                    }
                    item.progress.animateTo(1f, tween(if (still) STILL_MS else RISE_MS, easing = LinearEasing))
                    state.remove(item)
                }
                BasicText(
                    item.emoji,
                    Modifier
                        .offset {
                            IntOffset(
                                ((width * item.lane).toPx() - EMOJI.toPx() / 2).roundToInt(),
                                (height - bottomPadding - EMOJI).toPx().roundToInt(),
                            )
                        }
                        .size(EMOJI)
                        .graphicsLayer {
                            val p = item.progress.value
                            alpha = when {
                                p < FADE_IN -> p / FADE_IN
                                p > FADE_OUT -> (1f - p) / (1f - FADE_OUT)
                                else -> 1f
                            }
                            if (!still) {
                                translationY = -travel.toPx() * p
                                translationX = SWAY.toPx() * item.sway * sin(p * PI.toFloat() * 2)
                                val pop = if (p < POP_END) POP_FROM + (1f - POP_FROM) * (p / POP_END) else 1f
                                scaleX = pop
                                scaleY = pop
                            }
                        }
                        .clearAndSetSemantics { },
                    style = TextStyle(fontSize = EMOJI_SIZE, textAlign = TextAlign.Center),
                )
            }
        }
        burst?.let { ConfettiHost(it) }
        ReactionAnnouncer(state)
    }
}

/** [item]의 색종이를 [origin]에서 한 번만 터뜨린다. 이미 상한만큼 날리고 있으면 건너뛴다. */
private fun ConfettiState.burstFor(item: FloatingReaction, origin: Offset) {
    if (item.burst || pieces.size >= KReactionState.CONFETTI_MAX_PIECES) return
    item.burst = true
    burst(origin, CONFETTI_COUNT)
}

/** 마지막 반응을 TalkBack에 조용히 읽어 주는 보이지 않는 요소 */
@Composable
private fun ReactionAnnouncer(state: KReactionState) {
    val last = state.announcement ?: return
    val text = last.first?.let { stringResource(R.string.kinetica_ui_call_reaction_from, it, last.second) } ?: last.second
    Box(
        Modifier.size(1.dp).clearAndSetSemantics {
            contentDescription = text
            liveRegion = LiveRegionMode.Polite
        },
    )
}

/**
 * 반응 고르기. 이모지를 한 줄로 늘어놓고, 누르면 [onPick]으로 보낸다. 바텀 시트 안에 넣어 쓴다.
 *
 * @param reactions 고를 이모지. 기본값은 [KCallReactions]다.
 */
@Composable
public fun KReactionPicker(
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    reactions: List<String> = KCallReactions,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val shape = roundedShapeFor(PICK_RADIUS) { button }
    Row(modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
        reactions.forEach { emoji ->
            Box(
                Modifier
                    .size(PICK)
                    .jellyPress(KineticaTheme.design.motion.press * 2)
                    .variantBackground(KVariant.Tonal, KTone.Neutral.colors(surface), surface, shape)
                    .kClickable(shape, role = Role.Button) { onPick(emoji) }
                    .clearAndSetSemantics {
                        contentDescription = emoji
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(emoji, style = TextStyle(fontSize = PICK_EMOJI, textAlign = TextAlign.Center))
            }
        }
    }
}

private val EMOJI = 44.dp
private val EMOJI_SIZE = 34.sp
private val SWAY = 18.dp
private val PICK = 52.dp
private val PICK_RADIUS = 16.dp
private val PICK_EMOJI = 28.sp
private const val TRAVEL = 0.55f
private const val CONFETTI_COUNT = 60
private const val RISE_MS = 2_600
private const val STILL_MS = 1_600
private const val FADE_IN = 0.08f
private const val FADE_OUT = 0.7f
private const val POP_END = 0.12f
private const val POP_FROM = 0.4f
private const val LANE_MIN = 0.12f
private const val LANE_MAX = 0.88f
