package com.naury.kinetica.ui

import android.view.View
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import kotlin.math.abs

/**
 * 이 요소에서 시작한 드래그를 안드로이드 View 부모(NestedScrollView, ViewPager2, RecyclerView 등)가 가로채지 못하게 한다.
 * Compose 스크롤은 소비 여부를 보고 물러나지만, View 부모는 터치 슬롭을 넘는 순간 onInterceptTouchEvent로 이벤트를 가져가므로
 * 누르는 순간 requestDisallowInterceptTouchEvent를 요청해야 한다.
 *
 * [axis]가 있으면 움직임이 터치 슬롭을 넘을 때 방향을 본다. [axis] 쪽이 더 크면 끝까지 붙잡고, 반대쪽이면 놓아서
 * 가로 슬라이더 위에서 시작한 세로 스크롤이 바깥 ScrollView로 가게 한다. [axis]가 `null`이면 손을 뗄 때까지 붙잡는다.
 * 이벤트는 소비하지 않는다.
 */
@Composable
internal fun Modifier.holdViewParentDrag(axis: Orientation?, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val view = LocalView.current
    return pointerInput(axis, view) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            view.holdParent(true)
            var deciding = axis != null
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (deciding && change != null) {
                    val delta = change.position - down.position
                    val along = if (axis == Orientation.Horizontal) abs(delta.x) else abs(delta.y)
                    val across = if (axis == Orientation.Horizontal) abs(delta.y) else abs(delta.x)
                    if (maxOf(along, across) > viewConfiguration.touchSlop) {
                        deciding = false
                        if (across > along) view.holdParent(false)
                    }
                }
            } while (event.changes.any { it.pressed })
            view.holdParent(false)
        }
    }
}

/** [hold]가 `true`면 부모가 이번 터치를 가로채지 못하게 하고, `false`면 다시 허용한다. */
internal fun View.holdParent(hold: Boolean) {
    parent?.requestDisallowInterceptTouchEvent(hold)
}
