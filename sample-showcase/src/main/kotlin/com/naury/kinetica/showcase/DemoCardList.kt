// 화면 모양 값(간격·크기)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.GlassWindowWidth
import com.naury.kinetica.ui.rememberGlassWindowLayout

/** 넓은 창(840dp 이상)에서 데모 카드를 두 열로 놓을 때 본문 최대 폭 */
internal val WIDE_READING_WIDTH = 1120.dp

/** 이 창에서 데모 카드 목록과 상단 바가 함께 쓸 본문 최대 폭. 넓은 창이면 두 열 폭이다. */
@Composable
internal fun demoContentWidth(): Dp =
    if (rememberGlassWindowLayout().widthClass == GlassWindowWidth.Expanded) WIDE_READING_WIDTH else READING_WIDTH

/**
 * 데모 카드 목록. 폰과 펼친 폴더블 세로처럼 좁은 창에서는 한 열, 태블릿 가로처럼 넓은 창에서는 두 열로 놓아 양옆이 비지 않게 한다.
 * 카드 높이가 서로 달라도 빈틈이 없도록 엇갈린 격자를 쓴다. 제목처럼 한 줄 전체를 쓰는 항목은 `FullLine`으로 넣는다.
 */
@Composable
internal fun DemoCardList(top: Dp, bottom: Dp, modifier: Modifier = Modifier, content: LazyStaggeredGridScope.() -> Unit) {
    val maxWidth = demoContentWidth()
    val columns = if (maxWidth == WIDE_READING_WIDTH) 2 else 1
    BoxWithConstraints(modifier.fillMaxSize()) {
        val side = maxOf(20.dp, (this.maxWidth - maxWidth) / 2)
        LazyVerticalStaggeredGrid(
            StaggeredGridCells.Fixed(columns),
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = side, end = side, top = top, bottom = bottom),
            verticalItemSpacing = 16.dp,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}
