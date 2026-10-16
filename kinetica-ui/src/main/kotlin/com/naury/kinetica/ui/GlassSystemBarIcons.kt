package com.naury.kinetica.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import java.util.WeakHashMap

/**
 * 컴포지션에 있는 동안 상태 표시줄과 내비게이션 바 아이콘을 [light]에 맞춘다. `true`면 어두운 배경용 밝은 아이콘, `false`면 밝은 배경용 어두운 아이콘이다.
 * 컴포지션에서 빠지면 처음 바꾸기 전의 모양으로 되돌린다.
 *
 * 이 컴포저블이 놓인 창에 적용한다. [GlassScaffold] 밖에서 오버레이가 별도 창에 뜨면 그 창에, 아니면 호스트 액티비티 창에 적용한다.
 * 같은 창에서 여러 개가 겹치면 가장 나중에 들어온 것이 이긴다. [GlassExpandHost]처럼 내용 색을 호스트가 정하는 오버레이에서는
 * 상세 화면 안에 이것을 두어 배경에 맞는 아이콘을 고른다.
 *
 * ```kotlin
 * GlassExpandHost(expand) { key -> GlassSystemBarIcons(light = true); DarkDetail(key) }
 * ```
 */
@Composable
public fun GlassSystemBarIcons(light: Boolean) {
    SystemBarIcons(light)
}

/** [light]가 `null`이면 아이콘을 건드리지 않는다. 진행도처럼 자주 바뀌는 값은 호출한 쪽에서 참/거짓으로 줄여 넘긴다. */
@Composable
internal fun SystemBarIcons(light: Boolean?) {
    val view = LocalView.current
    val window = remember(view) { if (view.isInEditMode) null else view.hostWindow() }
    DisposableEffect(window, light) {
        if (window == null || light == null) return@DisposableEffect onDispose { }
        val request = BarRequest(light)
        SystemBarRequests.add(window, request)
        onDispose { SystemBarRequests.remove(window, request) }
    }
}

private class BarRequest(val light: Boolean)

/** 창마다 처음 모양을 기억해 두고, 남은 요청 중 가장 나중 것을 적용한다. 요청이 모두 빠지면 처음 모양으로 돌린다. */
private object SystemBarRequests {
    private class WindowBars(val statusLight: Boolean, val navigationLight: Boolean) {
        val requests = ArrayList<BarRequest>()
    }

    private val windows = WeakHashMap<Window, WindowBars>()

    fun add(window: Window, request: BarRequest) {
        val bars = windows.getOrPut(window) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            WindowBars(controller.isAppearanceLightStatusBars, controller.isAppearanceLightNavigationBars)
        }
        bars.requests += request
        apply(window, bars)
    }

    fun remove(window: Window, request: BarRequest) {
        val bars = windows[window] ?: return
        bars.requests -= request
        apply(window, bars)
        if (bars.requests.isEmpty()) windows.remove(window)
    }

    private fun apply(window: Window, bars: WindowBars) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val top = bars.requests.lastOrNull()
        controller.isAppearanceLightStatusBars = top?.let { !it.light } ?: bars.statusLight
        controller.isAppearanceLightNavigationBars = top?.let { !it.light } ?: bars.navigationLight
    }
}

/** 대화 상자 창 안이면 그 창, 아니면 액티비티 창. */
private fun View.hostWindow(): Window? {
    var parent = parent
    while (parent != null) {
        if (parent is DialogWindowProvider) return parent.window
        parent = parent.parent
    }
    return context.activity()?.window
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
