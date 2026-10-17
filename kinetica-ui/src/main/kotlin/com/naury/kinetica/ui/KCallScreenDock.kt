// 파일 이름은 [KCallScreen]의 조작 막대 자리(아래 가로 막대와 끝 쪽 세로 막대)를 모아 둔 곳임을 나타낸다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** 기본 버튼 뒤(끊기 앞)에 호스트 버튼을 덧붙인 조작 막대 목록 */
@Composable
private fun dockItems(inputs: CallInputs, ui: CallUi): List<KCallDockItem> =
    defaultDockItems(inputs.state, inputs.actions) { ui.sheet = it }.let { base ->
        if (inputs.extraDockItems.isEmpty()) base else base.dropLast(1) + inputs.extraDockItems + base.last()
    }

/** 조작 막대 하나. 낮은 화면이면 원을 줄이고 이름표를 빼서 무대를 넓힌다. */
@Composable
private fun CallDockSlot(inputs: CallInputs, options: CallOptions, ui: CallUi, vertical: Boolean) {
    val items = dockItems(inputs, ui)
    val short = vertical || callWindowSize().height < SHORT_SCREEN
    options.dock?.invoke(items, vertical) ?: KCallDock(
        items,
        vertical = vertical,
        buttonSize = if (short) KCallControlDefaults.CompactSize else KCallControlDefaults.Size,
        // 막대가 숨으면 막대 안에서 연 시트도 사라지므로 시트는 화면이 들고 있는다.
        onMore = { overflow ->
            ui.moreItems = overflow
            ui.sheet = CallSheet.More
        },
        showLabels = !short,
        surface = inputs.surface,
    )
}

@Composable
internal fun BottomChrome(inputs: CallInputs, options: CallOptions, ui: CallUi, chrome: KCallChromeState) {
    AnimatedVisibility(
        chrome.isVisible,
        enter = fadeIn() + slideInVertically {
            it / 2
        },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { if (it.height > 0) ui.bottomHeight = it.height }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            CallDockSlot(inputs, options, ui, vertical = false)
        }
    }
}

/** 가로로 낮은 화면의 끝 쪽 세로 막대. 상단 바 아래 남은 높이 가운데에 놓고, 숨을 때는 끝 쪽으로 빠진다. */
@Composable
internal fun BoxScope.SideChrome(inputs: CallInputs, options: CallOptions, ui: CallUi, chrome: KCallChromeState) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // 미끄러지기 거리는 화면 좌표라 RTL에서 끝 쪽(왼쪽)으로 빠지도록 뒤집는다.
    val toEnd: (Int) -> Int = { if (rtl) -it / 2 else it / 2 }
    val top = with(LocalDensity.current) { ui.topHeight.toDp() }
    AnimatedVisibility(
        chrome.isVisible,
        Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        enter = fadeIn() + slideInHorizontally(initialOffsetX = toEnd),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = toEnd),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .onSizeChanged { if (it.width > 0) ui.sideWidth = it.width }
                .padding(top = top)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            CallDockSlot(inputs, options, ui, vertical = true)
        }
    }
}

/** 이 창의 크기(dp) */
@Composable
internal fun callWindowSize(): DpSize = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.let { DpSize(it.width.toDp(), it.height.toDp()) }
}

/** 창이 가로로 길고 높이가 낮으면(compact height, 480dp 미만) 조작 막대를 끝 쪽 세로 막대로 놓는다. */
internal fun sideDock(width: Dp, height: Dp): Boolean = width > height && height < SHORT_SCREEN

private val SHORT_SCREEN = 480.dp
