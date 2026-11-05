package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 겹쳐 쌓인 카드 묶음(지갑 앱 방식). 처음에는 카드 위쪽 띠만 보이게 겹쳐 쌓여 있고, 누르면 아코디언처럼 펼쳐져 카드마다 [peek]만큼 보인다.
 * 펼친 상태에서 한 장을 누르면 그 카드만 위로 올라와 다 보이고 나머지는 아래로 접혀 쌓인다. 고른 카드나 아래 묶음을 누르거나
 * 뒤로 가기를 하면 선택이 풀려 펼친 상태로 돌아간다. 쌓일 때는 [maxStacked]장까지만 띠가 보이고 나머지는 맨 앞 카드 뒤에 숨는다.
 *
 * 카드 수가 적은 묶음(지갑, 계좌, 탑승권)을 위한 것으로 모든 카드를 한 번에 구성한다. 많은 목록에는 지연 목록을 쓴다.
 * 카드 위치, 크기, 묶음 높이는 배치 단계에서만 움직여 다시 구성하지 않는다. '애니메이션 삭제' 설정에서는 바로 옮겨 간다.
 * TalkBack은 카드를 번호 순서대로 목록 항목으로 읽고(고른 카드는 맨 먼저), 누르기 동작 이름이 지금 상태에서 할 일을 알려 준다.
 *
 * ```kotlin
 * val wallet = rememberKStackedCardsState()
 * KStackedCards(cards.size, state = wallet) { i -> PaymentCard(cards[i]) }
 * ```
 *
 * @param count 카드 수. 음수면 [IllegalArgumentException]을 던진다.
 * @param cardHeight 카드 한 장 높이. 카드 폭은 이 컴포넌트 폭과 같다.
 * @param peek 펼쳤을 때 카드마다 보이는 위쪽 높이. 고른 상태에서 아래 묶음이 보이는 높이이기도 하다.
 * @param stackGap 쌓였을 때 카드 띠 사이 간격
 * @param maxStacked 쌓였을 때 띠가 보이는 카드 수. 1보다 작으면 [IllegalArgumentException]을 던진다.
 * @param shape 카드 모양. 누름 표시와 잘라 내기에 쓴다. 카드 배경은 [content]가 그린다.
 * @param content 카드 내용. 카드 번호를 받는다.
 */
@Composable
public fun KStackedCards(
    count: Int,
    modifier: Modifier = Modifier,
    state: KStackedCardsState = rememberKStackedCardsState(),
    cardHeight: Dp = 200.dp,
    peek: Dp = 64.dp,
    stackGap: Dp = 12.dp,
    maxStacked: Int = 3,
    shape: Shape = shapeFor { card },
    content: @Composable BoxScope.(index: Int) -> Unit,
) {
    require(count >= 0) { "count는 0 이상이어야 합니다." }
    require(maxStacked >= 1) { "maxStacked는 1 이상이어야 합니다." }
    val selected = state.selected?.takeIf { it < count }
    val mode = when {
        selected != null -> StackMode.Selected
        state.expanded -> StackMode.Expanded
        else -> StackMode.Stacked
    }
    val density = LocalDensity.current
    val spec = with(density) { StackSpec(count, cardHeight.toPx(), peek.toPx(), stackGap.toPx(), SELECTED_GAP.toPx(), maxStacked) }
    val target = stackTargets(spec, mode, selected)
    val motion = rememberStackMotion(count, target)
    BackHandler(enabled = selected != null) { state.clearSelection() }
    val labels = StackLabels(
        stringResource(R.string.kinetica_ui_stacked_expand),
        stringResource(R.string.kinetica_ui_stacked_open),
        stringResource(R.string.kinetica_ui_stacked_back),
    )
    Layout(
        content = {
            repeat(count) { i ->
                key(i) {
                    val card = StackCardSpec(i, i == selected, target.alpha[i] == 0f, labels.of(mode))
                    StackCard(card, stackClick(state, mode, i), shape) { content(i) }
                }
            }
        },
        modifier = modifier
            .clipToBounds()
            .semantics {
                isTraversalGroup = true
                collectionInfo = CollectionInfo(count, 1)
            },
    ) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else (spec.cardHeight * FALLBACK_ASPECT).roundToInt()
        val placeables = measurables.map { it.measure(Constraints.fixed(width, spec.cardHeight.roundToInt())) }
        // 높이와 자리는 여기와 배치 블록에서만 읽어, 움직이는 동안 배치만 다시 한다.
        val height = motion.height.value.roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            placeables.forEachIndexed { i, p ->
                val card = motion.cards.getOrNull(i) ?: return@forEachIndexed
                p.placeWithLayer(0, card.y.value.roundToInt(), zIndex = target.z[i]) {
                    transformOrigin = TOP_CENTER
                    scaleX = card.scale.value
                    scaleY = card.scale.value
                    alpha = card.alpha.value.coerceIn(0f, 1f)
                }
            }
        }
    }
}

/** 카드 한 장의 번호와 상태. [hidden]은 묶음 뒤에 숨어 보이지 않는 카드다. */
private class StackCardSpec(val index: Int, val chosen: Boolean, val hidden: Boolean, val clickLabel: String)

/** 카드 한 장. TalkBack 목록 항목이고, 고른 카드는 맨 먼저 읽힌다. 보이지 않는 카드는 TalkBack이 멈추지 않게 숨기고 누를 수 없게 한다. */
@Composable
private fun StackCard(card: StackCardSpec, onClick: () -> Unit, shape: Shape, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .then(
                if (card.hidden) {
                    // 카드 안 글까지 함께 숨겨야 TalkBack이 보이지 않는 카드에 멈추지 않는다.
                    Modifier.clearAndSetSemantics { hideFromAccessibility() }
                } else {
                    Modifier.semantics {
                        collectionItemInfo = CollectionItemInfo(card.index, 1, 0, 1)
                        traversalIndex = if (card.chosen) -1f else card.index.toFloat()
                        selected = card.chosen
                    }
                },
            )
            .clip(shape)
            .clickable(enabled = !card.hidden, onClickLabel = card.clickLabel, role = Role.Button, onClick = onClick),
        content = content,
    )
}

private enum class StackMode { Stacked, Expanded, Selected }

private class StackLabels(val expand: String, val open: String, val back: String) {
    fun of(mode: StackMode): String = when (mode) {
        StackMode.Stacked -> expand
        StackMode.Expanded -> open
        StackMode.Selected -> back
    }
}

private fun stackClick(state: KStackedCardsState, mode: StackMode, index: Int): () -> Unit = when (mode) {
    StackMode.Stacked -> state::expand
    StackMode.Expanded -> { -> state.select(index) }
    StackMode.Selected -> state::clearSelection
}

/** 크기 값(px)과 카드 수 */
private data class StackSpec(
    val count: Int,
    val cardHeight: Float,
    val peek: Float,
    val gap: Float,
    val selectedGap: Float,
    val maxStacked: Int,
)

/** 카드마다 가야 할 자리. 배열 순서는 카드 번호다. */
private class StackTargets(val y: FloatArray, val scale: FloatArray, val alpha: FloatArray, val z: FloatArray, val height: Float) {
    override fun equals(other: Any?): Boolean = other is StackTargets &&
        y.contentEquals(other.y) &&
        scale.contentEquals(other.scale) &&
        alpha.contentEquals(other.alpha) &&
        z.contentEquals(other.z) &&
        height == other.height

    override fun hashCode(): Int = y.contentHashCode() * HASH + height.hashCode()
}

private fun stackTargets(spec: StackSpec, mode: StackMode, selected: Int?): StackTargets {
    val n = spec.count
    val t = StackTargets(FloatArray(n), FloatArray(n) { 1f }, FloatArray(n) { 1f }, FloatArray(n) { it.toFloat() }, 0f)
    return when (mode) {
        StackMode.Expanded -> {
            for (i in 0 until n) t.y[i] = i * spec.peek
            t.withHeight(if (n == 0) 0f else (n - 1) * spec.peek + spec.cardHeight)
        }

        StackMode.Stacked -> {
            val bottom = pile(spec, (0 until n).toList(), 0f, t)
            t.withHeight(if (n == 0) 0f else bottom + spec.cardHeight)
        }

        StackMode.Selected -> {
            val chosen = selected ?: 0
            t.z[chosen] = n.toFloat()
            val rest = (0 until n).filter { it != chosen }
            val top = spec.cardHeight + spec.selectedGap
            val bottom = pile(spec, rest, top, t)
            t.withHeight(if (rest.isEmpty()) spec.cardHeight else bottom + spec.peek)
        }
    }
}

/**
 * [order] 카드를 [top]부터 띠만 보이게 쌓고 맨 앞 카드의 y를 돌려준다. 앞쪽 [StackSpec.maxStacked] - 1장과 마지막 장만 보이고
 * 그 사이 카드는 마지막 장 뒤에 숨긴다. 뒤에 있는 카드일수록 조금 작게 그려 깊이가 보이게 한다.
 */
private fun pile(spec: StackSpec, order: List<Int>, top: Float, t: StackTargets): Float {
    if (order.isEmpty()) return top
    val visible = minOf(order.size, spec.maxStacked)
    order.forEachIndexed { j, card ->
        val slot = minOf(j, visible - 1)
        t.y[card] = top + slot * spec.gap
        t.scale[card] = 1f - (visible - 1 - slot) * STACK_SHRINK
        t.alpha[card] = if (j < visible - 1 || j == order.size - 1) 1f else 0f
    }
    return top + (visible - 1) * spec.gap
}

private fun StackTargets.withHeight(h: Float) = StackTargets(y, scale, alpha, z, h)

private class CardMotion(y: Float, scale: Float, alpha: Float) {
    val y = Animatable(y)
    val scale = Animatable(scale)
    val alpha = Animatable(alpha)
}

private class StackMotion(val cards: List<CardMotion>, val height: Animatable<Float, *>)

/** 목표 자리로 용수철처럼 옮겨 가는 값들. 카드 수가 바뀌면 새로 만든다. */
@Composable
private fun rememberStackMotion(count: Int, target: StackTargets): StackMotion {
    val motion = remember(count) {
        StackMotion(List(count) { CardMotion(target.y[it], target.scale[it], target.alpha[it]) }, Animatable(target.height))
    }
    val still = reducedMotion()
    LaunchedEffect(motion, target, still) {
        val move: AnimationSpec<Float> = if (still) snap() else spring(STACK_DAMPING, STACK_STIFFNESS)
        launch { motion.height.animateTo(target.height, move) }
        motion.cards.forEachIndexed { i, card ->
            launch { card.y.animateTo(target.y[i], move) }
            launch { card.scale.animateTo(target.scale[i], move) }
            launch { card.alpha.animateTo(target.alpha[i], move) }
        }
    }
    return motion
}

private val SELECTED_GAP = 16.dp
private val TOP_CENTER = TransformOrigin(0.5f, 0f)
private const val HASH = 31
private const val STACK_SHRINK = 0.04f
private const val STACK_DAMPING = 0.82f
private const val STACK_STIFFNESS = 380f
private const val FALLBACK_ASPECT = 1.6f
