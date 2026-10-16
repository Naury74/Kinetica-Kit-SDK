package com.naury.kinetica.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.dp

/**
 * 화면 아래에 붙는 도구 막대. 시작 쪽에 [actions] 버튼들을, 끝 쪽에 [floatingActionButton]을 놓는다. 편집, 사진 보기처럼
 * 화면 동작이 여러 개인 곳에 쓴다. 위 가장자리에 옅은 경계선을 긋고, 내비게이션 바 위로 올라온다.
 * [KineticaScaffold]의 `bottomBar`에 두면 콘텐츠 아래 패딩으로 높이가 전달된다.
 *
 * ```kotlin
 * KBottomAppBar(floatingActionButton = { KIconButton(PlusIcon, "새 메모", ::create, variant = KVariant.Filled) }) {
 *     KIconButton(CheckIcon, "체크리스트", {}, variant = KVariant.Text, tone = KTone.Neutral)
 * }
 * ```
 *
 * @param windowInsets 막대가 피할 시스템 영역. 기본값은 내비게이션 바, 키보드, 양옆 컷아웃이다.
 */
@Composable
public fun KBottomAppBar(
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable () -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
    actions: @Composable RowScope.() -> Unit,
) {
    val line = surface.contentColor.copy(alpha = LINE_ALPHA)
    Box(
        modifier
            .fillMaxWidth()
            .kSurface(surface.withoutShadow(), RectangleShape)
            .drawWithContent {
                drawContent()
                drawRect(line, Offset.Zero, Size(size.width, LINE.toPx()))
            }
            .windowInsetsPadding(windowInsets),
    ) {
        CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = BAR_HEIGHT).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = actions)
                floatingActionButton?.let { Box(Modifier.padding(end = 8.dp)) { it() } }
            }
        }
    }
}

/**
 * [KFloatingToolbar]가 펼쳐져 있는지. [connection]을 목록의 부모에 붙이면 아래로 스크롤할 때 접히고 위로 스크롤하면 다시 펼쳐진다.
 * 화면을 회전해도 유지된다.
 */
@Stable
public class KFloatingToolbarState internal constructor(initiallyExpanded: Boolean) {
    /** 펼쳐져 있으면 true다. 직접 바꿔도 된다. */
    public var expanded: Boolean by mutableStateOf(initiallyExpanded)

    private var travel = 0f

    /** 목록의 부모에 붙일 중첩 스크롤 연결. 같은 방향으로 [SCROLL_SLOP_PX] 넘게 움직였을 때만 바꿔 손떨림에 깜박이지 않게 한다. */
    public val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val dy = consumed.y
            if (dy == 0f) return Offset.Zero
            travel = if (travel * dy < 0f) dy else travel + dy
            if (travel < -SCROLL_SLOP_PX) expanded = false
            if (travel > SCROLL_SLOP_PX) expanded = true
            return Offset.Zero
        }
    }

    internal companion object {
        const val SCROLL_SLOP_PX = 48f
        val Saver: Saver<KFloatingToolbarState, Boolean> = Saver(save = { it.expanded }, restore = { KFloatingToolbarState(it) })
    }
}

/** [KFloatingToolbar]의 상태를 만든다. */
@Composable
public fun rememberKFloatingToolbarState(initiallyExpanded: Boolean = true): KFloatingToolbarState =
    rememberSaveable(saver = KFloatingToolbarState.Saver) { KFloatingToolbarState(initiallyExpanded) }

/**
 * 화면 아래 가운데에 떠 있는 캡슐 도구 막대. [content] 버튼들을 캡슐에 담고,
 * [floatingActionButton]은 캡슐 옆에 따로 둔다. [expanded]가 false가 되면 캡슐이 오므라들며 사라지고 [floatingActionButton]만 남는다.
 * '애니메이션 삭제' 설정에서는 바로 숨는다.
 *
 * 놓을 자리는 호스트가 정한다(보통 `Box` 안에서 `Modifier.align(Alignment.BottomCenter)`). 내비게이션 바와 키보드 위로 올라온다.
 *
 * ```kotlin
 * val toolbar = rememberKFloatingToolbarState()
 * LazyColumn(Modifier.nestedScroll(toolbar.connection)) { ... }
 * KFloatingToolbar(Modifier.align(Alignment.BottomCenter), expanded = toolbar.expanded) { ... }
 * ```
 */
@Composable
public fun KFloatingToolbar(
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    floatingActionButton: (@Composable () -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
    content: @Composable RowScope.() -> Unit,
) {
    val still = reducedMotion()
    Row(
        modifier.windowInsetsPadding(windowInsets).padding(TOOLBAR_MARGIN),
        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            expanded,
            enter = if (still) {
                fadeIn(tween(0))
            } else {
                scaleIn(spring(dampingRatio = TOOLBAR_DAMPING, stiffness = Spring.StiffnessMedium)) +
                    fadeIn()
            },
            exit = if (still) fadeOut(tween(0)) else scaleOut(tween(EXIT_MS), targetScale = EXIT_SCALE) + fadeOut(tween(EXIT_MS)),
        ) {
            Row(
                Modifier
                    .kSurface(surface.elevated(), CircleShape)
                    .heightIn(min = TOOLBAR_HEIGHT)
                    .padding(horizontal = TOOLBAR_INSET)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor, LocalGlassLayered provides true) {
                    content()
                }
            }
        }
        floatingActionButton?.invoke()
    }
}

private val BAR_HEIGHT = 72.dp
private val LINE = 0.5.dp
private const val LINE_ALPHA = 0.12f
private val TOOLBAR_HEIGHT = 56.dp
private val TOOLBAR_INSET = 6.dp
private val TOOLBAR_MARGIN = 16.dp
private val TOOLBAR_GAP = 10.dp
private const val TOOLBAR_DAMPING = 0.7f
private const val EXIT_MS = 160
private const val EXIT_SCALE = 0.8f
