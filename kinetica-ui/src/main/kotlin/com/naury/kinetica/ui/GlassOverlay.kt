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
 * [visible]인 동안과 닫히는 움직임이 끝날 때까지 [content]를 [GlassScaffold] 위층에 그린다. [GlassScaffold] 밖이면 이 자리에 그린다.
 * [content]는 열림 전환(`true`가 열림)을 받아 움직임을 맞춘다.
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
    // 내용은 한 번만 정하고 최신 람다를 상태로 읽어, 구성 중에 위층 상태를 다시 쓰지 않는다.
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

/** 열린 시트가 뒤 내용을 물러나게 할 정도(0..1)를 알린다. [GlassScaffold] 밖이면 아무 일도 없다. */
@Composable
internal fun ReportPushBack(amount: Float) {
    LocalOverlayEntry.current?.pushBack = amount
}

private val LocalOverlayEntry = staticCompositionLocalOf<OverlayEntry?> { null }
