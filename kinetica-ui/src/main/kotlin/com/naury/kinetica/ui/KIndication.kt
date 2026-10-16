package com.naury.kinetica.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * kinetica-ui의 눌림 표시. 사각 리플 대신 [shape] 모양으로 잘린 은은한 밝기 오버레이를 덮는다.
 * 빠르게 탭해도 [MIN_VISIBLE_MS]만큼은 보이고, 손을 떼면 부드럽게 사라진다. 키보드 포커스와 마우스 호버도 옅게 표시한다.
 * 시스템 '애니메이션 삭제' 설정이 켜져 있으면 페이드와 축소 없이 바로 켜고 끈다.
 *
 * [color]가 [Color.Unspecified]면 [LocalKineticaContentColor]를 보고 정한다. 글자가 밝으면(어두운 글래스나 강조색 버튼) 흰 오버레이,
 * 글자가 어두우면(밝은 글래스나 밝은 배경) 어두운 오버레이를 쓴다. 흰 오버레이는 같은 불투명도에서 덜 보여 조금 진하게 그린다.
 *
 * ```kotlin
 * Box(Modifier.clickable(interactionSource = null, indication = KIndication(RoundedCornerShape(16.dp))) { ... })
 * ```
 *
 * [KineticaTheme]과 [KineticaScaffold]는 기본으로 이 표시를 `LocalIndication`에 넣어 호스트가 쓴 일반 `clickable`도 사각 리플이 나오지 않게 한다.
 *
 * @param shape 오버레이 모양. 눌리는 요소의 모양과 맞춘다. 기본값은 사각형이다.
 * @param color 오버레이 색. [Color.Unspecified]면 위 규칙으로 자동으로 고른다.
 * @param pressedAlpha 눌렸을 때 오버레이 불투명도. 0~1 범위로 잘라 쓴다.
 * @param pressedScale 눌렸을 때 줄이는 배율. 1이면 줄이지 않는다. 0.8~1 범위로 잘라 쓴다. 수정자 체인에서 이 표시보다 뒤(안쪽)에 그려지는
 *   내용만 줄어들므로, 배경까지 함께 줄이려면 `Modifier.indication`을 배경보다 앞에 둔다.
 */
@Stable
public class KIndication(
    public val shape: Shape = RectangleShape,
    public val color: Color = Color.Unspecified,
    public val pressedAlpha: Float = DEFAULT_PRESSED_ALPHA,
    public val pressedScale: Float = 1f,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = KIndicationNode(interactionSource, this)

    override fun equals(other: Any?): Boolean = other is KIndication &&
        shape == other.shape &&
        color == other.color &&
        pressedAlpha == other.pressedAlpha &&
        pressedScale == other.pressedScale

    override fun hashCode(): Int {
        var result = shape.hashCode()
        result = HASH * result + color.hashCode()
        result = HASH * result + pressedAlpha.hashCode()
        result = HASH * result + pressedScale.hashCode()
        return result
    }

    public companion object {
        /** 기본 눌림 불투명도 */
        public const val DEFAULT_PRESSED_ALPHA: Float = 0.10f

        /** 빠르게 탭해도 눌림 표시가 유지되는 최소 시간(ms) */
        public const val MIN_VISIBLE_MS: Long = 110L

        /** 버튼처럼 눌렀을 때 살짝 줄어드는 요소에 쓰는 배율 */
        public const val BUTTON_PRESSED_SCALE: Float = 0.97f
    }
}

/**
 * 모양에 맞춘 [KIndication]으로 누를 수 있게 한다. `clickable`과 같고 눌림 표시만 다르다.
 *
 * ```kotlin
 * Row(Modifier.kClickable(RoundedCornerShape(12.dp), onClickLabel = "열기") { open() })
 * ```
 *
 * @param shape 눌림 표시 모양. 요소를 자르지는 않으므로 배경 모양과 같게 넘긴다.
 * @param role 접근성 역할. 기본값은 [Role.Button]이다.
 */
@Suppress("LongParameterList") // foundation의 같은 수정자와 파라미터를 맞춘다.
public fun Modifier.kClickable(
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = clickable(interactionSource, KIndication(shape), enabled, onClickLabel, role, onClick)

/**
 * 길게 누르기와 두 번 누르기도 받는 [kClickable].
 *
 * @param onLongClick 길게 눌렀을 때. null이면 받지 않는다.
 * @param onLongClickLabel 길게 누르기 동작을 TalkBack에 알리는 이름
 */
@Suppress("LongParameterList") // combinedClickable과 같은 파라미터를 그대로 받는다.
public fun Modifier.kCombinedClickable(
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = combinedClickable(
    interactionSource = interactionSource,
    indication = KIndication(shape),
    enabled = enabled,
    onClickLabel = onClickLabel,
    role = role,
    onLongClickLabel = onLongClickLabel,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
    onClick = onClick,
)

/** 하나만 고르는 항목(탭, 라디오)을 [KIndication]으로 누르게 한다. `selectable`과 같고 눌림 표시만 다르다. */
@Suppress("LongParameterList") // foundation의 같은 수정자와 파라미터를 맞춘다.
public fun Modifier.kSelectable(
    selected: Boolean,
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
    role: Role? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = selectable(selected, interactionSource, KIndication(shape), enabled, role, onClick)

/** 켜고 끄는 항목(체크박스, 스위치)을 [KIndication]으로 누르게 한다. `toggleable`과 같고 눌림 표시만 다르다. */
@Suppress("LongParameterList") // foundation의 같은 수정자와 파라미터를 맞춘다.
public fun Modifier.kToggleable(
    value: Boolean,
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
    role: Role? = null,
    interactionSource: MutableInteractionSource? = null,
    onValueChange: (Boolean) -> Unit,
): Modifier = toggleable(value, interactionSource, KIndication(shape), enabled, role, onValueChange)

private class KIndicationNode(private val source: InteractionSource, private val spec: KIndication) :
    Modifier.Node(),
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {
    private val press = Animatable(0f)
    private val highlight = Animatable(0f)
    private var pressJob: Job? = null
    private var pressStart = 0L
    private var hovers = 0
    private var focuses = 0
    private var reduced = false
    private var outline: Outline? = null
    private var outlineKey: OutlineKey? = null

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressDown()
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressUp()
                    is HoverInteraction.Enter -> hovers++
                    is HoverInteraction.Exit -> hovers = (hovers - 1).coerceAtLeast(0)
                    is FocusInteraction.Focus -> focuses++
                    is FocusInteraction.Unfocus -> focuses = (focuses - 1).coerceAtLeast(0)
                }
                if (interaction is HoverInteraction || interaction is FocusInteraction) updateHighlight()
            }
        }
    }

    // 설정은 앱 실행 중에도 바뀔 수 있으므로 누를 때마다 다시 읽는다.
    private fun readReducedMotion() {
        reduced = Settings.Global.getFloat(
            currentValueOf(LocalContext).contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    private fun pressDown() {
        readReducedMotion()
        pressJob?.cancel()
        pressStart = NOT_STARTED
        pressJob = coroutineScope.launch {
            pressStart = withFrameMillis { it }
            if (reduced) press.snapTo(1f) else press.animateTo(1f, tween(PRESS_IN_MS, easing = LinearOutSlowInEasing))
            invalidateDraw()
        }
    }

    private fun pressUp() {
        pressJob?.cancel()
        pressJob = coroutineScope.launch {
            if (pressStart == NOT_STARTED) pressStart = withFrameMillis { it }
            if (press.value < 1f) {
                if (reduced) press.snapTo(1f) else press.animateTo(1f, tween(PRESS_IN_MS, easing = LinearOutSlowInEasing))
            }
            // 스크롤 안에서는 탭을 뗄 때 누르기와 떼기가 한 프레임에 함께 오므로, 최소 시간만큼 붙잡아야 표시가 보인다.
            do {
                val now = withFrameMillis { it }
            } while (now - pressStart < KIndication.MIN_VISIBLE_MS)
            if (reduced) press.snapTo(0f) else press.animateTo(0f, tween(PRESS_OUT_MS, easing = FastOutSlowInEasing))
            invalidateDraw()
        }
    }

    private fun updateHighlight() {
        readReducedMotion()
        val target = when {
            hovers > 0 && focuses > 0 -> FOCUS_LEVEL + HOVER_LEVEL
            focuses > 0 -> FOCUS_LEVEL
            hovers > 0 -> HOVER_LEVEL
            else -> 0f
        }
        coroutineScope.launch {
            if (reduced) highlight.snapTo(target) else highlight.animateTo(target, tween(HIGHLIGHT_MS))
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val pressed = press.value
        val level = (pressed + highlight.value).coerceAtMost(1f)
        val scale = if (reduced) 1f else 1f - (1f - spec.pressedScale.coerceIn(MIN_SCALE, 1f)) * pressed
        if (scale == 1f) {
            drawContent()
            drawOverlay(level)
        } else {
            scale(scale) {
                this@draw.drawContent()
                this@draw.drawOverlay(level)
            }
        }
    }

    private fun ContentDrawScope.drawOverlay(level: Float) {
        if (level <= 0f) return
        val base = if (spec.color.isSpecified()) {
            spec.color
        } else if (currentValueOf(LocalKineticaContentColor).luminance() > LIGHT_CONTENT) {
            Color.White
        } else {
            Color.Black
        }
        val boost = if (spec.color.isSpecified() || base == Color.Black) 1f else WHITE_BOOST
        val alpha = (spec.pressedAlpha.coerceIn(0f, 1f) * boost * level).coerceIn(0f, 1f)
        drawOutline(outlineFor(size, layoutDirection, this), base, alpha = alpha)
    }

    private fun outlineFor(size: Size, direction: LayoutDirection, density: Density): Outline {
        val key = OutlineKey(size, direction, density.density)
        val cached = outline
        if (cached != null && key == outlineKey) return cached
        return spec.shape.createOutline(size, direction, density).also {
            outline = it
            outlineKey = key
        }
    }

    private data class OutlineKey(val size: Size, val direction: LayoutDirection, val density: Float)
}

private fun Color.isSpecified(): Boolean = this != Color.Unspecified

private const val HASH = 31
private const val NOT_STARTED = -1L
private const val PRESS_IN_MS = 70
private const val PRESS_OUT_MS = 220
private const val HIGHLIGHT_MS = 150
private const val HOVER_LEVEL = 0.4f
private const val FOCUS_LEVEL = 0.8f
private const val LIGHT_CONTENT = 0.5f
private const val WHITE_BOOST = 1.6f
private const val MIN_SCALE = 0.8f
