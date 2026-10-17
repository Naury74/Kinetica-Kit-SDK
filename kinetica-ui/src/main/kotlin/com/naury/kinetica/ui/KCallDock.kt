package com.naury.kinetica.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * [KCallDock]에 놓을 버튼 하나. 값의 뜻은 [KCallControlButton]과 같다.
 *
 * @property key 버튼을 구분하는 값. [KCallDock]의 `onItemPositioned`가 이 값으로 위치를 알린다.
 * @property essential true면 도크가 좁아도 막대에 남는다. false인 버튼은 넘치면 앞에서부터 남고 뒤쪽이 '더보기'로 옮겨 간다.
 * @property stateDescription TalkBack이 읽을 상태와 '더보기' 시트 행 끝에 쓸 상태. null이면 [checked]로 고른다.
 * @property badgeDescription TalkBack이 [badge] 대신 읽을 문구. 뜻은 [KCallControlButton]의 같은 인자와 같다.
 */
@Immutable
public data class KCallDockItem(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val checked: Boolean? = null,
    val checkedIcon: ImageVector? = null,
    val tone: KTone = KTone.Neutral,
    val pending: Boolean = false,
    val badge: Int = 0,
    val enabled: Boolean = true,
    val essential: Boolean = false,
    val stateDescription: String? = null,
    val badgeDescription: String? = null,
)

/**
 * 통화 조작 버튼을 담는 떠 있는 막대. [items]를 [KCallControlButton]으로 늘어놓고, 폭이 모자라면 원을 48dp까지 줄인 다음 그래도 넘치는
 * 버튼을 '더보기' 하나로 접는다. '더보기' 배지는 접힌 버튼 배지의 합이다. [KCallDockItem.essential] 버튼(마이크, 끊기 등)은 늘 남고,
 * 끊기처럼 [KTone.Destructive]인 마지막 버튼 앞에 '더보기'가 놓인다. 버튼이 늘거나 줄면 막대 폭이 스프링으로 바뀐다.
 *
 * [vertical]이면 세로 막대가 되어 가로 화면 옆쪽에 놓기 좋다. 이때는 높이를 기준으로 접는다.
 *
 * ```kotlin
 * KCallDock(
 *     listOf(
 *         KCallDockItem("mic", KIcons.Mic, "마이크", vm::toggleMute, checked = muted, checkedIcon = KIcons.MicOff, essential = true),
 *         KCallDockItem("share", KCallIcons.ScreenShare, "화면 공유", vm::share, checked = sharing),
 *         KCallDockItem("end", KIcons.CallEnd, "종료", vm::hangUp, tone = KTone.Destructive, essential = true),
 *     ),
 * )
 * ```
 *
 * @param buttonSize 넉넉할 때의 원 지름
 * @param onMore '더보기'를 눌렀을 때 접힌 버튼을 받는다. null이면 접힌 버튼을 목록으로 보여 주는 바텀 시트를 직접 연다.
 * @param onItemPositioned 버튼의 화면 위치(루트 기준 px)를 알린다. 접힌 버튼은 '더보기' 버튼 위치로 알린다.
 *   오디오 버튼 위에 말풍선을 띄울 때 쓴다([KCallBubble]).
 * @param showLabels false면 이름표 없이 원만 늘어놓는다.
 */
@Composable
public fun KCallDock(
    items: List<KCallDockItem>,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    buttonSize: Dp = KCallControlDefaults.Size,
    onMore: ((List<KCallDockItem>) -> Unit)? = null,
    onItemPositioned: ((key: String, bounds: Rect) -> Unit)? = null,
    showLabels: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    var sheetItems by remember { mutableStateOf<List<KCallDockItem>?>(null) }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val available = if (vertical) maxHeight else maxWidth
        val extent = if (vertical) VERTICAL_ITEM else HORIZONTAL_ITEM
        val plan = planDock(items, available, buttonSize, extent)
        val openMore: (List<KCallDockItem>) -> Unit = { overflow -> onMore?.invoke(overflow) ?: run { sheetItems = overflow } }
        KCallDock(vertical = vertical, surface = surface) {
            plan.shown.forEachIndexed { index, item ->
                if (index == plan.moreIndex && plan.overflow.isNotEmpty()) MoreButton(plan, showLabels, surface, onItemPositioned, openMore)
                val positioned = onItemPositioned?.let { report ->
                    Modifier.onGloballyPositioned { report(item.key, it.boundsInRoot()) }
                } ?: Modifier
                DockButton(item, plan.size, showLabels, surface, positioned)
            }
            if (plan.overflow.isNotEmpty() &&
                plan.moreIndex >= plan.shown.size
            ) {
                MoreButton(plan, showLabels, surface, onItemPositioned, openMore)
            }
        }
    }
    KCallMoreSheet(sheetItems.orEmpty(), sheetItems != null, onDismiss = { sheetItems = null })
}

/**
 * 버튼을 직접 넣는 통화 막대. [KCallDock]의 떠 있는 표면과 간격만 쓰고 배치는 [content]에 맡긴다. 접기가 필요 없거나 버튼 모양을
 * 바꾸고 싶을 때 [KCallControlButton]이나 직접 만든 버튼을 넣는다.
 */
@Composable
public fun KCallDock(
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    surface: SurfaceStyle = KineticaTheme.surface,
    content: @Composable () -> Unit,
) {
    val shape = shapeFor { tabBar }
    val spring = spring<androidx.compose.ui.unit.IntSize>(dampingRatio = RESIZE_DAMPING, stiffness = Spring.StiffnessMediumLow)
    Box(modifier.kSurface(surface, shape).animateContentSize(spring).padding(horizontal = DOCK_PADDING, vertical = DOCK_PADDING_V)) {
        if (vertical) {
            Column(verticalArrangement = Arrangement.spacedBy(ITEM_GAP), horizontalAlignment = Alignment.CenterHorizontally) { content() }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(ITEM_GAP), verticalAlignment = Alignment.Top) { content() }
        }
    }
}

@Composable
private fun DockButton(item: KCallDockItem, size: Dp, showLabel: Boolean, surface: SurfaceStyle, modifier: Modifier) {
    KCallControlButton(
        item.icon,
        item.label,
        item.onClick,
        modifier,
        checked = item.checked,
        checkedIcon = item.checkedIcon,
        tone = item.tone,
        pending = item.pending,
        badge = item.badge,
        enabled = item.enabled,
        size = size,
        showLabel = showLabel,
        stateDescription = item.stateDescription,
        badgeDescription = item.badgeDescription,
        surface = surface,
    )
}

@Composable
private fun MoreButton(
    plan: DockPlan,
    showLabel: Boolean,
    surface: SurfaceStyle,
    onItemPositioned: ((String, Rect) -> Unit)?,
    onClick: (List<KCallDockItem>) -> Unit,
) {
    val overflow = plan.overflow
    val positioned = onItemPositioned?.let { report ->
        Modifier.onGloballyPositioned { coords -> overflow.forEach { report(it.key, coords.boundsInRoot()) } }
    } ?: Modifier
    KCallControlButton(
        KIcons.More,
        stringResource(R.string.kinetica_ui_call_more),
        { onClick(overflow) },
        positioned,
        badge = overflow.sumOf { it.badge.coerceAtLeast(0) },
        size = plan.size,
        showLabel = showLabel,
        surface = surface,
    )
}

/**
 * [KCallDock]에서 접힌 버튼을 목록으로 보여 주는 바텀 시트. 행을 누르면 그 버튼의 동작을 부르고 시트를 닫는다.
 * 상태가 있는 버튼은 행 끝에 상태를 쓴다. [KCallDock]의 `onMore`로 접힌 버튼을 받아 직접 띄울 때 쓴다.
 */
@Composable
public fun KCallMoreSheet(
    items: List<KCallDockItem>,
    visible: Boolean,
    onDismiss: () -> Unit,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val title = stringResource(R.string.kinetica_ui_call_more)
    KSheet(visible, onDismiss, surface = surface.forPopup(), title = title) {
        SheetTitle(title)
        items.forEach { item ->
            val state = item.stateDescription ?: when (item.checked) {
                true -> stringResource(R.string.kinetica_ui_call_state_on)
                false -> stringResource(R.string.kinetica_ui_call_state_off)
                null -> null
            }
            KListItem(
                item.label,
                leading = KListLeading.Icon(if (item.checked == true) item.checkedIcon ?: item.icon else item.icon),
                trailing = when {
                    item.badge > 0 -> KListTrailing.Custom { KBadge(item.badge) }
                    state != null -> KListTrailing.Custom { CallCaption(state) }
                    else -> null
                },
                onClick = {
                    item.onClick()
                    onDismiss()
                },
                enabled = item.enabled,
            )
        }
    }
}

/** 시트 맨 위 제목 */
@Composable
internal fun SheetTitle(text: String) {
    ThemedText(
        text,
        Modifier.padding(start = SHEET_TITLE_START, bottom = SHEET_TITLE_BOTTOM),
        style = TextStyle(
            color = LocalKineticaContentColor.current,
            fontSize = SHEET_TITLE_SIZE,
            fontWeight = KineticaTheme.design.title(androidx.compose.ui.text.font.FontWeight.Bold),
        ),
    )
}

/** 보조 글자 한 줄 */
@Composable
internal fun CallCaption(text: String, modifier: Modifier = Modifier, alpha: Float = CAPTION_ALPHA) {
    val ink = LocalKineticaContentColor.current
    ThemedText(text, modifier, style = TextStyle(color = ink.copy(alpha = ink.alpha * alpha), fontSize = CAPTION_SIZE), maxLines = 1)
}

/**
 * 도크 배치 결과.
 *
 * @property shown 막대에 남는 버튼(원래 순서)
 * @property overflow '더보기'로 접힌 버튼
 * @property size 원 지름
 * @property moreIndex [shown]에서 '더보기'가 들어갈 자리. [overflow]가 비었으면 쓰지 않는다.
 */
internal class DockPlan(val shown: List<KCallDockItem>, val overflow: List<KCallDockItem>, val size: Dp, val moreIndex: Int)

/** [count]개 버튼을 [itemExtent]만큼씩 놓을 때 막대의 길이 */
internal fun dockLength(count: Int, itemExtent: Dp): Dp = itemExtent * count + ITEM_GAP * (count - 1).coerceAtLeast(0) + DOCK_PADDING * 2

/**
 * 폭에 맞춘 도크 배치. [preferred] 지름으로 다 들어가면 그대로, 아니면 48dp로 줄여 보고, 그래도 넘치면 필수가 아닌 버튼을 뒤에서부터
 * '더보기'로 접는다. [extentOf]는 원 지름에서 버튼 하나가 차지하는 길이를 구한다.
 */
internal fun planDock(items: List<KCallDockItem>, available: Dp, preferred: Dp, extentOf: (Dp) -> Dp): DockPlan {
    val compact = KCallControlDefaults.CompactSize
    val size = when {
        dockLength(items.size, extentOf(preferred)) <= available -> preferred
        else -> compact
    }
    if (dockLength(items.size, extentOf(size)) <= available) return DockPlan(items, emptyList(), size, items.size)
    var slots = items.size
    while (slots > 1 && dockLength(slots, extentOf(compact)) > available) slots--
    val essentials = items.count { it.essential }
    val keep = items.filterNot { it.essential }.take((slots - essentials - 1).coerceAtLeast(0)).toSet()
    val shown = items.filter { it.essential || it in keep }
    val overflow = items.filterNot { it.essential || it in keep }
    // 끊기 버튼은 늘 끝에 두고 '더보기'는 그 앞에 둔다. 그래야 손에 익은 위치가 바뀌지 않는다.
    val endsWithHangUp = shown.lastOrNull()?.tone == KTone.Destructive
    return DockPlan(shown, overflow, compact, if (endsWithHangUp && overflow.isNotEmpty()) shown.size - 1 else shown.size)
}

private val HORIZONTAL_ITEM: (Dp) -> Dp = { it + 8.dp }
private val VERTICAL_ITEM: (Dp) -> Dp = { it + 24.dp }
private val ITEM_GAP = 4.dp
private val DOCK_PADDING = 12.dp
private val DOCK_PADDING_V = 10.dp
private val SHEET_TITLE_START = 4.dp
private val SHEET_TITLE_BOTTOM = 8.dp
private val SHEET_TITLE_SIZE = 20.sp
private val CAPTION_SIZE = 13.sp
private const val CAPTION_ALPHA = 0.65f
private const val RESIZE_DAMPING = 0.8f
