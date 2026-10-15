package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 고른 탭 알약의 두 가장자리. 가는 쪽 가장자리는 빠르게, 반대쪽은 늦게 따라가 늘어난다. */
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
