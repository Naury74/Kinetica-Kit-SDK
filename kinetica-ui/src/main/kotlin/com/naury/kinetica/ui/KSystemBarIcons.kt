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
 * 컴포지션에서 빠지면 처음 바꾸기 전의 모양으로 되돌린다. 단, 요청이 걸려 있는 동안 호스트가 창의 아이콘 설정을 직접 바꿨으면
 * 그 값을 덮어쓰지 않고 그대로 둔다.
 *
 * 이 컴포저블이 놓인 창에 적용한다. [KineticaScaffold] 밖에서 오버레이가 별도 창에 뜨면 그 창에, 아니면 호스트 액티비티 창에 적용한다.
 * 같은 창에서 여러 개가 겹치면 가장 나중에 들어온 것이 이긴다. [KExpandHost]처럼 내용 색을 호스트가 정하는 오버레이에서는
 * 상세 화면 안에 이것을 두어 배경에 맞는 아이콘을 고른다.
 *
 * ```kotlin
 * KExpandHost(expand) { key -> KSystemBarIcons(light = true); DarkDetail(key) }
 * ```
 */
@Composable
public fun KSystemBarIcons(light: Boolean) {
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

/**
 * 창마다 처음 모양을 기억해 두고, 남은 요청 중 가장 나중 것을 적용한다. 요청이 모두 빠지면 처음 모양으로 돌린다.
 * 요청이 걸려 있는 동안 호스트가 창 설정을 직접 바꿨으면(앱 안 다크 모드 전환 같은 경우) 그 값을 새 기준으로 삼고 덮어쓰지 않는다.
 */
private object SystemBarRequests {
    private class WindowBars(var statusLight: Boolean, var navigationLight: Boolean) {
        val requests = ArrayList<BarRequest>()

        // 마지막으로 이 객체가 창에 쓴 값. 창의 지금 값과 다르면 그 사이 호스트가 직접 바꾼 것이다.
        var appliedStatus: Boolean? = null
        var appliedNavigation: Boolean? = null
    }

    private val windows = WeakHashMap<Window, WindowBars>()

    fun add(window: Window, request: BarRequest) {
        val bars = windows.getOrPut(window) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            WindowBars(controller.isAppearanceLightStatusBars, controller.isAppearanceLightNavigationBars)
        }
        adoptHostChange(window, bars)
        bars.requests += request
        apply(window, bars)
    }

    fun remove(window: Window, request: BarRequest) {
        val bars = windows[window] ?: return
        bars.requests -= request
        // 호스트가 직접 바꾼 값이 있으면 그 값을 지키고, 남은 요청이 있어도 지금은 다시 쓰지 않는다.
        if (!adoptHostChange(window, bars)) apply(window, bars)
        if (bars.requests.isEmpty()) windows.remove(window)
    }

    /** 마지막으로 쓴 뒤 호스트가 창 설정을 바꿨으면 그 값을 기준으로 삼고 true를 돌려준다. */
    private fun adoptHostChange(window: Window, bars: WindowBars): Boolean {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val status = controller.isAppearanceLightStatusBars
        val navigation = controller.isAppearanceLightNavigationBars
        val changed = (bars.appliedStatus != null && bars.appliedStatus != status) ||
            (bars.appliedNavigation != null && bars.appliedNavigation != navigation)
        if (changed) {
            bars.statusLight = status
            bars.navigationLight = navigation
            bars.appliedStatus = status
            bars.appliedNavigation = navigation
        }
        return changed
    }

    private fun apply(window: Window, bars: WindowBars) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val top = bars.requests.lastOrNull()
        val status = top?.let { !it.light } ?: bars.statusLight
        val navigation = top?.let { !it.light } ?: bars.navigationLight
        controller.isAppearanceLightStatusBars = status
        controller.isAppearanceLightNavigationBars = navigation
        bars.appliedStatus = status
        bars.appliedNavigation = navigation
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
