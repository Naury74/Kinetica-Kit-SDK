package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex

/**
 * [targetState]가 바뀌면 [transition]으로 내용을 바꾸는 컨테이너. Compose `AnimatedContent`와 비슷하지만 [KTransitions]의 전환을 그대로 쓰고,
 * [isForward]가 `false`를 돌려주면 같은 전환을 되감아 방향을 거꾸로 보여 준다. 탭처럼 좌우 순서가 있는 화면에서 왼쪽 탭으로 가면
 * 내용이 반대 방향으로 밀린다. 가로 방향은 RTL에서 뒤집힌다.
 *
 * 전환하는 동안 사라지는 내용은 터치를 받지 않고 TalkBack에도 드러나지 않는다. 전환 도중에 [targetState]가 또 바뀌면 진행 중이던 전환을
 * 끝난 상태로 건너뛰고 새 전환을 시작한다. '애니메이션 삭제' 설정이 켜져 있으면 바로 바꾼다. 내용의 상태는 화면에서 빠지면 지워진다.
 *
 * ```kotlin
 * KTransitionContent(tab, transition = KTransitions.TabSlide, isForward = { from, to -> to > from }) { page -> Page(page) }
 * ```
 *
 * @param isForward 이전 값에서 새 값으로 가는 것이 앞으로 가는 방향이면 `true`
 * @param contentAlignment 두 내용의 크기가 다를 때 맞추는 위치
 */
@Composable
public fun <T> KTransitionContent(
    targetState: T,
    modifier: Modifier = Modifier,
    transition: KScreenTransition = KTransitions.FadeThrough,
    isForward: (initial: T, target: T) -> Boolean = { _, _ -> true },
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable (T) -> Unit,
) {
    val state = remember { SwitchState(targetState) }
    val forward by rememberUpdatedState(isForward)
    val still = reducedMotion()
    val host = remember { TransitionHost() }
    host.endSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    LaunchedEffect(targetState) { state.switchTo(targetState, forward, transition, still) }
    Box(modifier.onGloballyPositioned { host.screenOrigin = it.positionOnScreen() }, contentAlignment = contentAlignment) {
        val below = state.below
        val visible = if (below != null) listOf(below, state.top) else listOf(state.top)
        visible.forEach { item ->
            key(item) {
                val layer = if (item == state.top && below != null) {
                    Modifier.screenTransition(transition, TransitionRole.Entering, host) { state.progress.value }
                } else if (below != null) {
                    Modifier
                        .zIndex(if (transition.enteringOnTop) 0f else 1f)
                        .screenTransition(transition, TransitionRole.Exiting, host) { state.progress.value }
                        .coveredLayer()
                } else {
                    Modifier
                }
                Box(layer, propagateMinConstraints = true) { content(item) }
            }
        }
    }
}

/**
 * 위에 그리는 내용([top])과 가려지는 내용([below]). 앞으로 가면 새 값이 [top]이 되어 진행률이 0에서 1로 가고,
 * 뒤로 가면 새 값이 [below]가 되어 진행률이 1에서 0으로 되감긴다.
 */
private class SwitchState<T>(initial: T) {
    var top by mutableStateOf(initial)
    var below by mutableStateOf<T?>(null)
    val progress = Animatable(1f)
    private var target: T = initial

    suspend fun switchTo(next: T, isForward: (T, T) -> Boolean, transition: KScreenTransition, still: Boolean) {
        val previous = target
        if (next == previous && below == null) return
        target = next
        // 앞 전환이 끝나기 전에 다시 바뀌었으면 앞 전환을 끝난 상태로 건너뛴다.
        top = previous
        below = null
        progress.snapTo(1f)
        if (next == previous) return
        if (still) {
            top = next
            return
        }
        if (isForward(previous, next)) {
            below = previous
            top = next
            progress.snapTo(0f)
            progress.animateTo(1f, transition.animationSpec)
        } else {
            below = next
            progress.animateTo(0f, transition.animationSpec)
            top = next
            progress.snapTo(1f)
        }
        below = null
    }
}
