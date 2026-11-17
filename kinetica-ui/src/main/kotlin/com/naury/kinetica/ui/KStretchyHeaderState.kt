package com.naury.kinetica.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * [KStretchyHeader]와 [KStretchyHeaderBar]가 함께 쓰는 상태. 스크롤 위치는 목록 상태에서 바로 읽으므로 `scrollToItem` 같은 코드 스크롤도
 * 그대로 반영된다. [connection]을 목록의 부모에 `Modifier.nestedScroll(state.connection)`로 붙이면 맨 위에서 더 당긴 거리를 받아
 * 헤더 이미지를 늘린다. 연결하지 않으면 늘어나지 않을 뿐 패럴랙스와 제목 넘김은 그대로 동작한다.
 */
@Stable
public class KStretchyHeaderState internal constructor(private val position: () -> Float) {
    internal var stretch by mutableFloatStateOf(0f)
    internal var stretchLimit = 1f

    // 늘이기 전 헤더 높이와 바 높이(px). 헤더와 바가 측정하며 알려 준다.
    internal var headerHeight by mutableFloatStateOf(0f)
    internal var barHeight by mutableFloatStateOf(0f)

    // 목록이 맨 위에서 내려간 거리(px). 헤더가 이미 지나갔으면 무한대다.
    internal val offset: Float get() = position().coerceAtLeast(0f)

    /** 헤더가 접힌 정도(0..1). 헤더 아래 끝이 바 아래 끝에 닿으면 1이다. 헤더를 아직 측정하지 못했으면 0이다. */
    public val collapsedFraction: Float
        get() {
            val range = headerHeight - barHeight
            return if (headerHeight <= 0f || range <= 0f) 0f else (offset / range).coerceIn(0f, 1f)
        }

    /** 맨 위에서 아래로 더 당긴 정도(0..1). 1이면 늘어날 수 있는 끝까지 당긴 것이다. */
    public val stretchFraction: Float get() = (stretch / stretchLimit).coerceIn(0f, 1f)

    /** 제목이 헤더에서 바로 넘어갔는지. [collapsedFraction]이 0.8을 넘으면 true다. */
    public val isTitleInBar: Boolean get() = collapsedFraction >= TITLE_SWITCH

    /** 목록의 부모에 붙일 중첩 스크롤 연결. 맨 위에서 더 당긴 거리만 소비하고 목록 스크롤은 가로채지 않는다. */
    public val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (stretch <= 0f || available.y >= 0f) return Offset.Zero
            // 늘인 채로 다시 위로 밀면 목록보다 먼저 늘어난 헤더를 되돌린다.
            val used = maxOf(available.y, -stretch)
            stretch += used
            return Offset(0f, used)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
            val room = (1f - stretch / stretchLimit).coerceAtLeast(0f)
            stretch += available.y * STRETCH_RESISTANCE * room
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (stretch <= 0f) return Velocity.Zero
            animate(stretch, 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = RELEASE_STIFFNESS)) {
                    v,
                    _,
                ->
                stretch = v
            }
            return available
        }
    }

    // 바 배경은 헤더 아래 끝이 바 아래 끝에 닿기 직전부터 나타나 닿을 때 다 차오른다.
    internal val barState: KTopAppBarState = KTopAppBarState(0f) { barOffset() }

    private fun barOffset(): Float {
        if (headerHeight <= 0f) return 0f
        return (offset - (headerHeight - barHeight) + barState.fadeDistance).coerceAtLeast(0f)
    }

    /** 헤더 안 큰 제목의 불투명도. 접히기 시작하면 흐려져 바로 넘어가기 전에 사라진다. */
    internal fun headerTitleAlpha(): Float =
        1f - ((collapsedFraction - HEADER_FADE_START) / (TITLE_SWITCH - HEADER_FADE_START)).coerceIn(0f, 1f)

    /** 바 제목의 불투명도 */
    internal fun barTitleAlpha(): Float = ((collapsedFraction - TITLE_SWITCH) / (1f - TITLE_SWITCH)).coerceIn(0f, 1f)
}

/**
 * [listState]로 [KStretchyHeader] 상태를 만든다. 헤더는 목록의 첫 항목이어야 한다. 첫 항목이 지나가면 다 접힌 것으로 본다.
 */
@Composable
public fun rememberKStretchyHeaderState(listState: LazyListState): KStretchyHeaderState = remember(listState) {
    KStretchyHeaderState {
        if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else Float.POSITIVE_INFINITY
    }
}

/** [scrollState]로 [KStretchyHeader] 상태를 만든다. 헤더는 스크롤 콘텐츠의 맨 위에 있어야 한다. */
@Composable
public fun rememberKStretchyHeaderState(scrollState: ScrollState): KStretchyHeaderState = remember(scrollState) {
    KStretchyHeaderState { scrollState.value.toFloat() }
}

private const val TITLE_SWITCH = 0.8f
private const val HEADER_FADE_START = 0.45f
private const val STRETCH_RESISTANCE = 0.5f
private const val RELEASE_STIFFNESS = 380f
