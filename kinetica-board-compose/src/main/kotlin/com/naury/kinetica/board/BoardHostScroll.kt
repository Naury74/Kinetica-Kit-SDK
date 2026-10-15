package com.naury.kinetica.board

import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Velocity

/**
 * 위젯을 누른 순간부터 손을 뗄 때까지 바깥 View(NestedScrollView, ViewPager2, SwipeRefreshLayout 등)가 터치를 가로채지 않게 한다.
 * 편집 중 위젯을 세로나 가로로 끌 때 바깥 컨테이너가 스크롤이나 페이지 넘김으로 가져가면 드래그가 끊기기 때문이다.
 */
internal fun Modifier.holdParentTouches(view: View): Modifier = pointerInput(view) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        view.parent?.requestDisallowInterceptTouchEvent(true)
    }
}

/**
 * 보드가 창에서 실제로 보이는 세로 범위를 잰다. 보드가 NestedScrollView처럼 바깥에서 스크롤되는 컨테이너 안에 있거나,
 * CoordinatorLayout에서 앱 바 때문에 아래쪽이 화면 밖에 있으면 보드 영역 전체가 아니라 보이는 부분을 기준으로 가장자리를 판단해야 한다.
 */
internal class BoardVisibility(private val view: View) {
    /** 보드 바깥 상자의 배치 좌표. 배치할 때마다 갱신한다. */
    var coordinates: LayoutCoordinates? = null

    private val rect = android.graphics.Rect()
    private val location = IntArray(2)

    /** [visibleRows]를 문서 단위로 바꾼 값. [zoom]은 문서 1단위의 px 크기다. */
    fun visibleDocumentRows(zoom: Float): ClosedFloatingPointRange<Double>? =
        visibleRows()?.let { it.start / zoom.toDouble()..it.endInclusive / zoom.toDouble() }

    /** 보이는 부분의 위와 아래(보드 위쪽 기준 px). 배치 전이거나 창에 붙어 있지 않으면 `null`이다. */
    fun visibleRows(): ClosedFloatingPointRange<Float>? {
        val coords = coordinates?.takeIf { it.isAttached } ?: return null
        if (!view.isAttachedToWindow) return null
        val bounds = coords.boundsInRoot()
        var top = bounds.top
        var bottom = bounds.bottom
        if (view.getGlobalVisibleRect(rect)) {
            view.getLocationInWindow(location)
            top = maxOf(top, (rect.top - location[1]).toFloat())
            bottom = minOf(bottom, (rect.bottom - location[1]).toFloat())
        } else {
            bottom = top
        }
        val origin = coords.positionInRoot().y
        return (top - origin)..maxOf(top, bottom) - origin
    }
}

/**
 * 보드를 [delta]px 스크롤한다. 보드 자체가 더 스크롤되지 않으면 남은 양을 nested scroll로 바깥 스크롤 컨테이너에 넘긴다.
 * 실제로 움직인 양(px)을 돌려준다. [usedParent]는 바깥에 넘긴 적이 있으면 `true`로 바뀐다.
 */
internal suspend fun scrollBoardOrParent(
    scroll: ScrollState,
    dispatcher: NestedScrollDispatcher,
    delta: Float,
    usedParent: BooleanArray,
): Float {
    val own = scroll.scrollBy(delta)
    if (own != 0f) return own
    usedParent[0] = true
    // nested scroll의 delta는 손가락이 움직인 방향이라 스크롤 방향과 부호가 반대다.
    val consumed = dispatcher.dispatchPostScroll(Offset.Zero, Offset(0f, -delta), NestedScrollSource.UserInput)
    return -consumed.y
}

/** 바깥 컨테이너에 넘긴 스크롤을 끝낸다. View 쪽 nested scroll은 fling을 알려야 멈춘다. */
internal suspend fun finishParentScroll(dispatcher: NestedScrollDispatcher) {
    dispatcher.dispatchPostFling(Velocity.Zero, Velocity.Zero)
}
