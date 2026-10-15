package com.naury.kinetica.board

import android.animation.ValueAnimator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.geometry.Rect
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** 스택. 한 위치에서 옆으로 넘겨 보고, 아래 점 인디케이터로 몇 번째인지 보여 준다. 각 위젯은 호스트의 `content(슬롯 id)`로 그린다. */
@Composable
internal fun StackPager(members: List<String>, content: @Composable (String) -> Unit, colors: BoardColors, autoAdvanceMs: Long = 0L) {
    val pager = rememberPagerState { members.size }
    val animations = remember { ValueAnimator.areAnimatorsEnabled() }
    if (autoAdvanceMs > 0 && animations && members.size > 1) {
        // 사용자가 직접 넘긴 경우를 포함해 페이지가 바뀔 때마다 시간을 다시 재고, 스크롤 중이면 넘기지 않는다.
        LaunchedEffect(pager, autoAdvanceMs, members.size) {
            snapshotFlow { pager.currentPage }.collectLatest { page ->
                delay(autoAdvanceMs)
                if (!pager.isScrollInProgress) pager.animateScrollToPage((page + 1) % members.size)
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { members[it] }) { page ->
            Box(Modifier.fillMaxSize()) { content(members[page]) }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            members.indices.forEach { index ->
                val color = if (index == pager.currentPage) colors.accent else colors.outline.copy(alpha = DOT_ALPHA)
                Box(Modifier.size(DOT).background(color, CircleShape))
            }
        }
    }
}

/** 편집 중에 스택 오른쪽 위에 위젯 수를 표시하는 배지. 누르면 스택을 푼다. */
@Composable
internal fun StackBadge(rect: Rect, zoom: Float, count: Int, colors: BoardColors, onUnstack: () -> Unit) {
    val density = LocalDensity.current
    val description = stringResource(R.string.kinetica_board_unstack)
    val right = with(density) { ((rect.right * zoom).toFloat()).toDp() }
    Text(
        "$count",
        color = colors.onAccent,
        modifier = Modifier
            .offset { IntOffset(0, (rect.top * zoom).roundToInt()) }
            .offset(x = right - BADGE_WIDTH - 6.dp, y = 6.dp)
            .background(colors.accent, RoundedCornerShape(12.dp))
            .semantics { contentDescription = description }
            .clickable(onClick = onUnstack)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** 편집 중인 스택 테두리에 붙는 접근성 이름. "첫 위젯 외 n개" 형식이다. */
@Composable
internal fun stackLabel(state: KineticaBoardState, members: List<String>): String {
    val first = state.widgetKeyOf(members.first())?.let { key -> state.catalog.firstOrNull { it.key == key }?.label } ?: members.first()
    return pluralStringResource(R.plurals.kinetica_board_stack_label, members.size - 1, first, members.size - 1)
}

/** 스택이면 TalkBack 동작에 "스택 풀기"를 추가한다. */
@Composable
internal fun unstackActions(state: KineticaBoardState, key: String, members: List<String>): List<CustomAccessibilityAction> {
    if (members.isEmpty()) return emptyList()
    val label = stringResource(R.string.kinetica_board_unstack)
    return listOf(CustomAccessibilityAction(label) { state.unstack(key) })
}

private val DOT = 6.dp
private val BADGE_WIDTH = 28.dp
private const val DOT_ALPHA = 0.5f
