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

    suspend fun moveTo(index: Int, count: Int, width: Float, rtl: Boolean = false, motion: KineticaMotion = KineticaMotion.Bouncy) {
        if (width <= 0f) return
        val cell = width / count
        // Row는 RTL에서 0번 칸을 오른쪽 끝에 놓는다. 인디케이터는 물리 좌표로 그리므로 칸 순서를 뒤집는다.
        val targetLeft = cell * (if (rtl) count - 1 - index else index)
        moveTo(targetLeft, targetLeft + cell, motion)
    }

    /** 칸 폭이 서로 다를 때 쓴다. [targetLeft]와 [targetRight]는 물리 좌표(px)다. */
    suspend fun moveTo(targetLeft: Float, targetRight: Float, motion: KineticaMotion = KineticaMotion.Bouncy) = coroutineScope {
        if (targetRight <= targetLeft) return@coroutineScope
        if (!placed) {
            placed = true
            left.snapTo(targetLeft)
            right.snapTo(targetRight)
            return@coroutineScope
        }
        val forward = targetLeft > left.value
        // 차분하거나 빠른 모션이면 두 끝을 같은 스프링으로 옮겨 늘어나지 않게 한다.
        val fast = motion.tuned<Float>(FAST_DAMPING, Spring.StiffnessMedium)
        val slow = if (motion == KineticaMotion.Bouncy) spring(dampingRatio = SLOW_DAMPING, stiffness = Spring.StiffnessLow) else fast
        launch { left.animateTo(targetLeft, if (forward) slow else fast) }
        launch { right.animateTo(targetRight, if (forward) fast else slow) }
    }
}

private const val FAST_DAMPING = 0.8f
private const val SLOW_DAMPING = 0.7f
