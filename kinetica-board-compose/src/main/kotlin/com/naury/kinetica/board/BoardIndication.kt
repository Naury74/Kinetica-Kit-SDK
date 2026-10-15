package com.naury.kinetica.board

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * 보드 편집 화면의 눌림 표시. 기본 리플 대신 [shape] 안에만 [color]를 옅게 덮는다.
 * 누르는 순간 바로 나타나고 손을 떼면 짧게 사라진다. 시스템의 '애니메이션 삭제'가 켜져 있으면 바로 사라진다.
 */
internal class BoardIndication(private val shape: Shape, private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressOverlayNode(interactionSource, shape, color)

    override fun equals(other: Any?): Boolean = other is BoardIndication && other.shape == shape && other.color == color

    override fun hashCode(): Int = 31 * shape.hashCode() + color.hashCode()
}

private class PressOverlayNode(private val source: InteractionSource, private val shape: Shape, private val color: Color) :
    Modifier.Node(),
    DrawModifierNode {
    private val alpha = Animatable(0f)
    private var pressed = 0
    private var hovered = 0
    private var focused = 0

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                track(interaction)
                val target = when {
                    pressed > 0 -> PRESSED_ALPHA
                    focused > 0 -> FOCUSED_ALPHA
                    hovered > 0 -> HOVERED_ALPHA
                    else -> 0f
                }
                launch {
                    // 빨리 탭해도 눌림이 보이도록 나타날 때는 바로 그리고, 사라질 때만 애니메이션한다.
                    if (target >= alpha.value || !ValueAnimator.areAnimatorsEnabled()) {
                        alpha.snapTo(target)
                    } else {
                        alpha.animateTo(target, tween(FADE_OUT_MS))
                    }
                    invalidateDraw()
                }
            }
        }
    }

    private fun track(interaction: Interaction) {
        when (interaction) {
            is PressInteraction.Press -> pressed++
            is PressInteraction.Release, is PressInteraction.Cancel -> pressed = (pressed - 1).coerceAtLeast(0)
            is HoverInteraction.Enter -> hovered++
            is HoverInteraction.Exit -> hovered = (hovered - 1).coerceAtLeast(0)
            is FocusInteraction.Focus -> focused++
            is FocusInteraction.Unfocus -> focused = (focused - 1).coerceAtLeast(0)
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val value = alpha.value
        if (value > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), color.copy(alpha = color.alpha * value))
    }
}

private const val PRESSED_ALPHA = 0.14f
private const val FOCUSED_ALPHA = 0.12f
private const val HOVERED_ALPHA = 0.06f
private const val FADE_OUT_MS = 220
