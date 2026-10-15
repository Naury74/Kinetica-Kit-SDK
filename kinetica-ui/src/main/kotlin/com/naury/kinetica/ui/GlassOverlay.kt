package com.naury.kinetica.ui

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * [visible]인 동안, 그리고 닫힘 애니메이션이 끝날 때까지 [content]를 [GlassScaffold]의 오버레이 레이어에 그린다.
 * [GlassScaffold] 밖에서 부르면 호출한 위치에 그린다. [content]는 열림 전환(`true`가 열린 상태)을 받아 애니메이션을 맞춘다.
 */
@Composable
internal fun GlassOverlay(visible: Boolean, content: @Composable (Transition<Boolean>) -> Unit) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    val shown = state.currentState || state.targetState
    if (!shown) return
    val latest = rememberUpdatedState(content)
    val host = LocalGlassHost.current
    if (host == null) {
        latest.value(rememberTransition(state, label = "glass-overlay"))
        return
    }
    // content는 한 번만 정하고 최신 람다는 상태로 읽는다. 컴포지션 도중에 오버레이 상태를 다시 쓰지 않기 위해서다.
    val entry = remember {
        OverlayEntry(Any()).also { entry ->
            entry.content = {
                CompositionLocalProvider(LocalOverlayEntry provides entry) {
                    latest.value(rememberTransition(state, label = "glass-overlay"))
                }
            }
        }
    }
    DisposableEffect(entry) {
        host.entries += entry
        onDispose {
            entry.pushBack = 0f
            host.entries -= entry
        }
    }
}

/** 열린 시트가 뒤쪽 콘텐츠를 얼마나 밀어낼지(0..1) 알린다. [GlassScaffold] 밖에서는 아무 일도 하지 않는다. */
@Composable
internal fun ReportPushBack(amount: Float) {
    LocalOverlayEntry.current?.pushBack = amount
}

private val LocalOverlayEntry = staticCompositionLocalOf<OverlayEntry?> { null }
