package com.naury.kinetica.ui

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart

/**
 * 영상 통화의 상단 바와 조작 막대를 잠시 뒤 숨기는 상태. [isVisible]이 false면 바를 숨긴다. 화면 어디를 만지든 숨김 시계를 다시 맞추고,
 * 빈 곳을 누르면 [toggle]로 보였다 숨겼다 한다.
 */
@Stable
public class KCallChromeState internal constructor(visible: MutableVisibility) {
    private val holder = visible

    /** 바를 보일지 */
    public val isVisible: Boolean get() = holder.value

    // 터치마다 상태를 바꾸면 이 상태를 읽는 화면 전체가 다시 구성되므로, 흐름으로 알려 숨김 시계만 다시 시작한다.
    internal val touches = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 바를 보이고 숨김 시계를 처음부터 다시 잰다. */
    public fun show() {
        holder.value = true
        touches.tryEmit(Unit)
    }

    /** 보이면 숨기고, 숨었으면 보인다. */
    public fun toggle() {
        if (holder.value) holder.value = false else show()
    }

    internal fun hide() {
        holder.value = false
    }

    internal fun touched() {
        touches.tryEmit(Unit)
    }
}

/** 저장 가능한 참/거짓 칸. [rememberSaveable]로 만든 상태를 [KCallChromeState]에 넘길 때 쓴다. */
internal class MutableVisibility(initial: Boolean) {
    var value by mutableStateOf(initial)
}

/**
 * [KCallChromeState]를 만든다. [autoHide]이고 [holdVisible]이 false인 동안 마지막 터치 뒤 [timeoutMillis]가 지나면 바를 숨긴다.
 * TalkBack처럼 화면을 더듬어 쓰는 동안에는 손가락 터치가 오지 않아 다시 꺼낼 수 없으므로 숨기지 않는다. 접근성 설정의
 * '조치를 취할 시간'이 더 길면 그 값을 따른다. 숨길 수 없는 동안에는 늘 보인다.
 *
 * [holdVisible]에는 숨기면 안 되는 조건을 모두 넣는다. 예: 시트가 열림, 재연결 중, 미디어 오류, 내 화면 공유 중, 다른 통화가 오디오를 가져감,
 * 버튼을 가리키는 말풍선이 떠 있음, 상대가 아직 없음.
 *
 * ```kotlin
 * val chrome = rememberKCallChromeState(autoHide = isVideo, holdVisible = sheetOpen || reconnecting || sharing)
 * Box(Modifier.kCallChromeTouches(chrome)) { ... AnimatedVisibility(chrome.isVisible) { KCallDock(...) } }
 * ```
 *
 * @param timeoutMillis 마지막 터치 뒤 숨기기까지 기다리는 시간(ms). 0 이하면 [IllegalArgumentException]을 던진다.
 */
@Composable
public fun rememberKCallChromeState(autoHide: Boolean, holdVisible: Boolean = false, timeoutMillis: Long = 4_000L): KCallChromeState {
    require(timeoutMillis > 0) { "timeoutMillis는 0보다 커야 합니다." }
    val visible =
        rememberSaveable(
            saver = androidx.compose.runtime.saveable.Saver<MutableVisibility, Boolean>({
                it.value
            }, { MutableVisibility(it) }),
        ) {
            MutableVisibility(true)
        }
    val state = remember(visible) { KCallChromeState(visible) }
    val exploring = rememberTouchExploration()
    val accessibility = LocalAccessibilityManager.current
    val canHide = autoHide && !holdVisible && !exploring
    LaunchedEffect(canHide) { if (!canHide) state.show() }
    LaunchedEffect(canHide, state.isVisible) {
        if (!canHide || !state.isVisible) return@LaunchedEffect
        val timeout = accessibility?.calculateRecommendedTimeoutMillis(
            timeoutMillis,
            containsIcons = true,
            containsText = true,
            containsControls = true,
        ) ?: timeoutMillis
        if (timeout == Long.MAX_VALUE) return@LaunchedEffect
        state.touches.onStart { emit(Unit) }.collectLatest {
            delay(timeout)
            state.hide()
        }
    }
    return state
}

/**
 * 이 요소 안 어디를 만지든 [state]의 숨김 시계를 다시 맞춘다. 이벤트를 소비하지 않고 Initial 단계에서 보기만 하므로 안쪽 버튼과
 * 스크롤은 그대로 동작한다. 통화 화면 루트에 붙인다.
 */
public fun Modifier.kCallChromeTouches(state: KCallChromeState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            state.touched()
        }
    }
}

/** TalkBack처럼 화면을 더듬는 접근성 서비스가 켜져 있는지. 켜고 끌 때 바로 바뀐다. */
@Composable
internal fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}
