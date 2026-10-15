package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 선택 인디케이터의 양쪽 가장자리. 이동하는 방향의 가장자리가 먼저 가고 반대쪽이 늦게 따라와서 인디케이터가 늘어났다 줄어든다. */
@Stable
internal class LiquidIndicator {
    val left = Animatable(0f)
    val right = Animatable(0f)
    private var placed = false

    suspend fun moveTo(index: Int, count: Int, width: Float) = coroutineScope {
        if (width <= 0f) return@coroutineScope
        val cell = width / count
        val targetLeft = cell * index
        val targetRight = targetLeft + cell
        if (!placed) {
            placed = true
            left.snapTo(targetLeft)
            right.snapTo(targetRight)
            return@coroutineScope
        }
        val forward = targetLeft > left.value
        val fast = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
        val slow = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
        launch { left.animateTo(targetLeft, if (forward) slow else fast) }
        launch { right.animateTo(targetRight, if (forward) fast else slow) }
    }
}
