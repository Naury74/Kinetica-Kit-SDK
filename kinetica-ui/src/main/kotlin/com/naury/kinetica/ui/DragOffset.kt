package com.naury.kinetica.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 손으로 끈 거리(px, 0 이상). 한 프레임에 스크롤이 여러 번 와도 빠지지 않게 상태에 바로 더하고,
 * 놓은 뒤의 움직임만 애니메이션으로 한다. 다시 끌면 진행 중인 움직임을 멈춘다.
 */
@Stable
internal class DragOffset {
    var value by mutableFloatStateOf(0f)
        private set
    private var settling: Job? = null

    fun drag(delta: Float) {
        settling?.cancel()
        value = (value + delta).coerceAtLeast(0f)
    }

    fun settle(scope: CoroutineScope, target: Float, damping: Float) {
        settling?.cancel()
        settling = scope.launch { animate(value, target, animationSpec = spring(dampingRatio = damping)) { v, _ -> value = v } }
    }
}
