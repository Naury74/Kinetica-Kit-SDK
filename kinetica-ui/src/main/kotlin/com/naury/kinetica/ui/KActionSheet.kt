// 파일 이름은 대표 컴포저블인 KActionSheet를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** [KActionSheet]를 어떤 모양으로 띄울지 정한다. */
public enum class KSheetPresentation {
    /** 창 폭이 600dp 미만이면 [Sheet], 그 이상이면 [Popover]다. 폴더블을 펼치거나 접으면 다음에 열 때 바뀐다. */
    Auto,

    /** 화면 아래에서 올라오는 동작 시트 */
    Sheet,

    /** 앵커 옆에 뜨는 작은 판. 바깥을 누르면 닫히므로 취소 버튼을 그리지 않는다. */
    Popover,
}

/**
 * 동작 시트. 아래에서 올라오는 판에 동작 목록을 놓고, 그 밑에 떨어진 판으로 취소 버튼을 둔다. 판을 아래로 끌면 닫히고,
 * 손을 떼면 가까운 쪽(닫힘 또는 제자리)으로 스프링처럼 정착한다. 바깥을 누르거나 뒤로 가기를 해도 닫힌다.
 * 동작을 누르면 [onDismissRequest]를 부른 뒤 그 동작을 실행한다. 고르면 끝나는 메뉴이므로 [KDialogAction.dismissOnClick]은 보지 않고 늘 닫는다.
 *
 * [KDialogActionStyle.Cancel] 동작이 있으면 그것을 취소 판에 쓰고, 없으면 '취소' 버튼을 만들어 [onDismissRequest]에 잇는다.
 * [KDialogActionStyle.Preferred]는 굵게, [KDialogActionStyle.Destructive]는 빨갛게 쓴다.
 *
 * [presentation]이 팝오버로 정해지면(넓은 화면의 [KSheetPresentation.Auto] 포함) 이 함수를 부른 자리의 부모 레이아웃 옆에 메뉴처럼 띄운다.
 * 그래서 시트를 여는 버튼과 함께 `Box`로 감싸 부르는 것이 좋다. [KineticaScaffold] 밖에서는 시트는 대화 상자 창에, 팝오버는 팝업 창에 띄운다.
 *
 * ```kotlin
 * Box {
 *     KButton("공유", onClick = { sharing = true })
 *     val actions = listOf(KDialogAction("링크 복사") { copy() }, KDialogAction("삭제", KDialogActionStyle.Destructive) { delete() })
 *     KActionSheet(sharing, { sharing = false }, actions)
 * }
 * ```
 *
 * @param actions 동작 목록. 취소를 뺀 동작이 하나도 없으면 [IllegalArgumentException]을 던진다.
 * @param title 목록 위에 작게 놓는 제목. TalkBack이 창 제목으로 읽는다.
 * @param message 제목 아래 설명
 */
@Composable
public fun KActionSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<KDialogAction>,
    modifier: Modifier = Modifier,
    title: String? = null,
    message: String? = null,
    presentation: KSheetPresentation = KSheetPresentation.Auto,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val main = actions.filter { it.style != KDialogActionStyle.Cancel }
    require(main.isNotEmpty()) { "취소가 아닌 동작이 하나 이상 있어야 합니다." }
    val popover = when (presentation) {
        KSheetPresentation.Popover -> true
        KSheetPresentation.Sheet -> false
        KSheetPresentation.Auto -> !rememberKWindowLayout().isCompact
    }
    val dismiss by rememberUpdatedState(onDismissRequest)
    if (popover) {
        ActionPopover(visible, main, ActionHeader(title, message), surface, modifier) { dismiss() }
        return
    }
    val cancelLabel = stringResource(R.string.kinetica_ui_action_cancel)
    val cancel = actions.firstOrNull { it.style == KDialogActionStyle.Cancel } ?: KDialogAction(cancelLabel, KDialogActionStyle.Cancel) {}
    val spec = ActionSheetSpec(main, cancel, ActionHeader(title, message), surface, modifier)
    KOverlay(visible) { transition -> ActionSheetLayer(spec, transition) { dismiss() } }
}

/** 시트 모양 동작 시트의 고정 값. 파라미터 수를 줄이려고 묶었다. */
private class ActionSheetSpec(
    val actions: List<KDialogAction>,
    val cancel: KDialogAction,
    val header: ActionHeader,
    val surface: SurfaceStyle,
    val modifier: Modifier,
)

@Composable
private fun ActionSheetLayer(spec: ActionSheetSpec, transition: Transition<Boolean>, dismiss: () -> Unit) {
    val still = reducedMotion()
    val progress by transition.animateFloat(
        transitionSpec = {
            when {
                still -> tween(0)
                targetState -> spring(dampingRatio = SHEET_DAMPING, stiffness = Spring.StiffnessMediumLow)
                else -> tween(CLOSE_MS)
            }
        },
        label = "action-sheet",
    ) { if (it) 1f else 0f }
    BackHandler(enabled = transition.targetState, onBack = dismiss)
    val drag = remember { SettleOffset() }
    var height by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    val settle: (Float) -> Unit = { velocity -> scope.launch { settleActionSheet(drag, height, velocity, dismiss) } }
    val run: (KDialogAction) -> Unit = { action ->
        if (transition.targetState) {
            dismiss()
            action.onClick()
        }
    }
    Box(Modifier.fillMaxSize()) {
        Scrim(progress.coerceIn(0f, 1f) * (1f - (drag.value / height).coerceIn(0f, 1f)), dismiss)
        Column(
            spec.modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(SHEET_MARGIN)
                .widthIn(max = SHEET_MAX_WIDTH)
                .fillMaxWidth()
                .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
                .graphicsLayer { translationY = (height + SHEET_MARGIN.toPx()) * (1f - progress) + drag.value }
                // 동작 목록이 스크롤 컨테이너라 목록 위에서 끈 거리는 중첩 스크롤로 넘어온다. 남은 아래쪽 거리로 시트를 끌어 내린다.
                .nestedScroll(remember(drag) { ActionSheetDrag(drag) { settle(it) } })
                .draggable(
                    rememberDraggableState { delta -> drag.drag(delta, min = 0f) },
                    Orientation.Vertical,
                    onDragStopped = { velocity -> settle(velocity) },
                )
                .settleAfterRelease({ drag.value > 0f && !drag.isSettling }) { settle(0f) }
                .semantics {
                    spec.header.title?.let { paneTitle = it }
                    dismiss {
                        dismiss()
                        true
                    }
                }
                .blockTouches(),
        ) {
            ActionGroup(spec.actions, spec.header, spec.surface, run)
            Spacer(Modifier.height(SHEET_MARGIN))
            ActionGroup(listOf(spec.cancel), ActionHeader(null, null), spec.surface, run)
        }
    }
}

/** 높이의 30% 넘게 끌었거나 빠르게 아래로 던졌으면 닫고, 아니면 위로 던진 속도를 이어받아 제자리로 돌아온다. */
private suspend fun settleActionSheet(drag: SettleOffset, height: Float, velocity: Float, dismiss: () -> Unit) {
    if (drag.value > height * DISMISS_FRACTION || velocity > DISMISS_VELOCITY) {
        dismiss()
        drag.animateTo(0f, tween(CLOSE_MS))
    } else {
        drag.animateTo(0f, spring(dampingRatio = SHEET_DAMPING, stiffness = Spring.StiffnessMediumLow), velocity.coerceAtMost(0f))
    }
}

/** 목록이 맨 위에 닿은 뒤 남은 아래쪽 스크롤로 시트를 끌어 내리고, 다시 위로 밀면 끌어 내린 거리부터 되돌린다. */
private class ActionSheetDrag(private val drag: SettleOffset, private val settle: (Float) -> Unit) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y >= 0f || drag.value <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
        return Offset(0f, drag.drag(available.y, min = 0f))
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
        return Offset(0f, drag.drag(available.y, min = 0f))
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (drag.value <= 0f) return Velocity.Zero
        settle(available.y)
        return available
    }
}

/** 동작 목록 위 제목과 설명 */
private class ActionHeader(val title: String?, val message: String?)

@Composable
private fun ActionGroup(actions: List<KDialogAction>, header: ActionHeader, surface: SurfaceStyle, onAction: (KDialogAction) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .kSurface(surface.forPopup(), GroupShape)
            .clip(GroupShape)
            .heightIn(max = GROUP_MAX_HEIGHT)
            .verticalScroll(rememberScrollState()),
    ) {
        if (header.title != null || header.message != null) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                header.title?.let {
                    ThemedText(
                        it,
                        style = TextStyle(
                            color = surface.muted(HEADER_ALPHA),
                            fontSize = 13.sp,
                            fontWeight = KineticaTheme.design.title(FontWeight.SemiBold),
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
                header.message?.let {
                    ThemedText(
                        it,
                        Modifier.padding(top = 2.dp),
                        style = TextStyle(color = surface.muted(HEADER_ALPHA), fontSize = 13.sp, textAlign = TextAlign.Center),
                    )
                }
            }
            Hairline(surface)
        }
        actions.forEachIndexed { index, action ->
            if (index > 0) Hairline(surface)
            ActionRow(action, surface) { onAction(action) }
        }
    }
}

@Composable
private fun ActionRow(action: KDialogAction, surface: SurfaceStyle, onClick: () -> Unit) {
    val color = when (action.style) {
        KDialogActionStyle.Destructive -> KineticaTheme.colors.destructive
        KDialogActionStyle.Cancel -> surface.contentColor
        else -> KineticaTheme.colors.accent
    }
    val bold = action.style == KDialogActionStyle.Preferred || action.style == KDialogActionStyle.Cancel
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .graphicsLayer { alpha = if (action.enabled) 1f else DISABLED_ALPHA }
            .kClickable(RectangleShape, action.enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        action.icon?.let {
            Glyph(it, color)
            Spacer(Modifier.width(KMetrics.IconTextGap))
        }
        ThemedText(
            action.label,
            style = TextStyle(color = color, fontSize = 18.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Hairline(surface: SurfaceStyle) {
    Box(Modifier.fillMaxWidth().height(HAIRLINE).background(surface.contentColor.copy(alpha = HAIRLINE_ALPHA)))
}

/** 넓은 화면에서 시트 대신 앵커 옆에 띄우는 판. 취소는 바깥을 누르면 되므로 그리지 않는다. */
@Composable
private fun ActionPopover(
    visible: Boolean,
    actions: List<KDialogAction>,
    header: ActionHeader,
    surface: SurfaceStyle,
    modifier: Modifier,
    onDismiss: () -> Unit,
) {
    val anchor = remember { PopupAnchor() }
    val frame = remember { AnchoredFrame(focusable = true, onDismiss = onDismiss) }
    AnchoredPopup(visible, anchor, frame) { transition, geometry ->
        PopupPanel(transition, geometry, surface, modifier.width(POPOVER_WIDTH)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                header.title?.let {
                    ThemedText(
                        it,
                        Modifier.padding(horizontal = 18.dp, vertical = 8.dp).semantics { paneTitle = it },
                        style = TextStyle(
                            color = surface.muted(HEADER_ALPHA),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
                header.message?.let {
                    ThemedText(
                        it,
                        Modifier.padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
                        style = TextStyle(color = surface.muted(HEADER_ALPHA), fontSize = 13.sp),
                    )
                }
                actions.forEach { action ->
                    val color = if (action.style ==
                        KDialogActionStyle.Destructive
                    ) {
                        KineticaTheme.colors.destructive
                    } else {
                        surface.contentColor
                    }
                    MenuRow(
                        MenuRowSpec(action.label, action.icon, null, checked = null),
                        color,
                        Modifier.kClickable(MenuItemShape, action.enabled) {
                            if (transition.targetState) {
                                onDismiss()
                                action.onClick()
                            }
                        },
                        action.enabled,
                    )
                }
            }
        }
    }
}

private val GroupShape: Shape
    @Composable
    @ReadOnlyComposable
    get() = shapeFor { menu }
private val SHEET_MARGIN = 8.dp
private val SHEET_MAX_WIDTH = 480.dp
private val GROUP_MAX_HEIGHT = 520.dp
private val ROW_HEIGHT = 56.dp
private val HAIRLINE = 0.5.dp
private val POPOVER_WIDTH = 280.dp
private const val HAIRLINE_ALPHA = 0.14f
private const val HEADER_ALPHA = 0.6f
private const val DISABLED_ALPHA = 0.38f
private const val SHEET_DAMPING = 0.86f
private const val CLOSE_MS = 180
private const val DISMISS_FRACTION = 0.3f
private const val DISMISS_VELOCITY = 1800f
