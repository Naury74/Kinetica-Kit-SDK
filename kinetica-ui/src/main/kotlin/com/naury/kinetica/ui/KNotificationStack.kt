package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 잠금 화면 알림 묶음. 같은 앱의 알림을 겹쳐 쌓아 맨 앞 알림만 보이고, 뒤 알림은 아래 가장자리로 조금씩 비친다. 맨 앞 카드에 남은 알림 수가 붙는다.
 * 누르면 카드들이 아래로 풀려 목록이 되고, 위 줄의 '간단히'로 다시 접으며 '모두 지우기'로 묶음 전체를 지운다.
 *
 * 카드를 옆으로 끝까지 밀거나 빠르게 튕기면 지워진다. 접힌 묶음을 밀면 묶음 전체가, 펼친 목록의 카드를 밀면 그 알림만 지워진다.
 * 세로로 먼저 움직이면 부모 스크롤로 넘기고, 끌다가 터치가 취소되면 지우지 않고 제자리로 돌아간다. 지우는 동작은 카드가 화면 밖으로 날아간 뒤
 * [onDismiss]나 [onDismissAll]로 알리므로, 목록에서 빼는 일은 호스트가 한다. 카드 상태는 [KNotification.id]에 묶여 목록이 바뀌어도 섞이지 않는다.
 *
 * TalkBack은 접힌 묶음을 '메시지 알림 5개'와 맨 앞 알림 내용으로 한 번 읽고, 두 번 탭하면 펼친다. 펼친 목록은 목록 구조로 읽히고
 * 카드마다 '지우기' 동작이 있다. 묶음에는 '모두 지우기' 동작이 있다. '애니메이션 삭제'가 켜져 있으면 펼침과 지우기가 바로 바뀐다.
 *
 * ```kotlin
 * val messages = remember { mutableStateListOf(*loaded.toTypedArray()) }
 * KNotificationStack("메시지", messages, onDismiss = { id -> messages.removeAll { it.id == id } }, onDismissAll = { messages.clear() })
 * ```
 *
 * @param appName 앱 이름. 카드 위 줄과 펼친 묶음 제목, TalkBack 요약에 쓴다.
 * @param notifications 알림 목록. 첫 항목이 맨 앞 카드다. 비어 있으면 아무것도 그리지 않는다. [KNotification.id]가 겹치면 [IllegalArgumentException]을 던진다.
 * @param onDismiss 알림 하나를 지웠을 때 그 id로 부른다.
 * @param onDismissAll 묶음 전체를 지웠을 때. null이면 알림마다 [onDismiss]를 부른다.
 * @param onClick 펼친 목록에서 카드를 눌렀을 때 그 id로 부른다. 알림이 하나뿐이면 그 카드를 눌렀을 때도 부른다. null이면 누를 수 없다.
 * @param icon 카드 왼쪽에 놓을 앱 아이콘. 32dp 자리에 가운데 놓인다.
 */
@Composable
public fun KNotificationStack(
    appName: String,
    notifications: List<KNotification>,
    onDismiss: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    state: KNotificationStackState = rememberKNotificationStackState(),
    onDismissAll: (() -> Unit)? = null,
    onClick: ((id: String) -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(notifications.distinctBy { it.id }.size == notifications.size) { "notifications의 id가 겹칩니다." }
    if (notifications.isEmpty()) return
    val count = notifications.size
    val stacked = count > 1
    LaunchedEffect(stacked) { if (!stacked) state.collapse() }
    val still = isReducedMotion()
    val open = state.expanded && stacked
    val progress = rememberExpandProgress(open, still)
    // 접는 움직임이 끝나야 뒤 카드와 위 줄을 구성에서 뺀다. 접힌 동안 TalkBack과 터치에 남지 않게 한다.
    val unfolding by remember(progress) { derivedStateOf { progress.value > 0f } }
    val dismissOne by rememberUpdatedState(onDismiss)
    val dismissAll by rememberUpdatedState(onDismissAll ?: { notifications.forEach { onDismiss(it.id) } })
    val actions = StackActions(
        expand = { state.expand() },
        collapse = { state.collapse() },
        dismissOne = { dismissOne(it) },
        dismissAll = { dismissAll() },
        click = onClick,
    )
    val more = if (stacked) pluralStringResource(R.plurals.kinetica_ui_nstack_more, count - 1, count - 1) else null
    val look = NStackLook(
        appName,
        icon,
        surface,
        shapeFor { card },
        still,
        StackTexts(pluralStringResource(R.plurals.kinetica_ui_nstack_summary, count, appName, count), more),
    )
    val shown = if (stacked && (open || unfolding)) count else 1
    Layout(
        content = { StackContent(notifications, shown, open, look, actions) { progress.value } },
        modifier = modifier.semantics { if (open) collectionInfo = CollectionInfo(count, 1) },
    ) { measurables, constraints ->
        measureStack(measurables, constraints, StackCounts(count, shown), progress.value)
    }
}

/** 묶음이 카드와 위 줄에 넘기는 동작 */
private class StackActions(
    val expand: () -> Unit,
    val collapse: () -> Unit,
    val dismissOne: (String) -> Unit,
    val dismissAll: () -> Unit,
    val click: ((String) -> Unit)?,
)

/** 판, 위 줄, 카드를 [measureStack]이 기대하는 차례로 구성한다. 앞에서 [shown]개 카드만 구성한다. */
@Composable
private fun StackContent(
    notifications: List<KNotification>,
    shown: Int,
    open: Boolean,
    look: NStackLook,
    actions: StackActions,
    progress: () -> Float,
) {
    val labels = rememberStackLabels()
    val count = notifications.size
    repeat(PLATES) { plate -> NStackPlate(plate, look.surface, look.shape, progress) }
    if (shown > 1) {
        NStackHeader(
            look.appName,
            look.surface,
            shapeFor { button },
            labels,
            onCollapse = actions.collapse,
            onClearAll = actions.dismissAll,
            modifier = Modifier.graphicsLayer { alpha = progress() },
        )
    }
    for (index in 0 until shown) {
        val item = notifications[index]
        key(item.id) {
            val collapsedFront = index == 0 && count > 1 && !open
            val click = actions.click
            NotificationCard(
                item,
                look,
                NStackCardRole(index, count, open, if (collapsedFront) look.texts.more else null, look.texts.summary),
                labels,
                fade = { if (index == 0) 1f else progress() },
                onTap = when {
                    collapsedFront -> actions.expand
                    click != null -> ({ click(item.id) })
                    else -> null
                },
                onDismiss = if (collapsedFront) actions.dismissAll else ({ actions.dismissOne(item.id) }),
                onDismissAll = actions.dismissAll,
            )
        }
    }
}

@Composable
private fun rememberStackLabels(): NStackLabels = NStackLabels(
    showLess = stringResource(R.string.kinetica_ui_nstack_show_less),
    clearAll = stringResource(R.string.kinetica_ui_nstack_clear_all),
    dismiss = stringResource(R.string.kinetica_ui_nstack_dismiss),
    expand = stringResource(R.string.kinetica_ui_nstack_expand),
)

/** 묶음 전체 알림 수 [total]과 지금 구성한 카드 수 [shown]. [shown]이 1보다 크면 위 줄도 구성되어 있다. */
private class StackCounts(val total: Int, val shown: Int)

/** 펼친 정도(0..1). '애니메이션 삭제'면 바로 바꾼다. */
@Composable
private fun rememberExpandProgress(expanded: Boolean, still: Boolean): Animatable<Float, *> {
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded, still) {
        val target = if (expanded) 1f else 0f
        if (still) {
            progress.snapTo(target)
        } else {
            progress.animateTo(target, spring(dampingRatio = EXPAND_DAMPING, stiffness = Spring.StiffnessMediumLow))
        }
    }
    return progress
}

/** 카드들이 함께 쓰는 고정 값 */
private class NStackLook(
    val appName: String,
    val icon: (@Composable () -> Unit)?,
    val surface: SurfaceStyle,
    val shape: Shape,
    val still: Boolean,
    val texts: StackTexts,
)

/** TalkBack 요약 [summary]와 맨 앞 카드에 붙는 남은 알림 수 [more] */
private class StackTexts(val summary: String, val more: String?)

/** 카드 하나의 자리와 TalkBack 정보 */
private class NStackCardRole(val index: Int, val count: Int, val expanded: Boolean, val more: String?, val summary: String)

/** 접힌 묶음에서 맨 앞 카드 아래로 비치는 판. 펼치면 사라진다. 의미 노드는 없다. */
@Composable
private fun NStackPlate(plate: Int, surface: SurfaceStyle, shape: Shape, progress: () -> Float) {
    val narrow = PLATE_NARROW * (plate + 1)
    Box(
        Modifier
            .graphicsLayer {
                alpha = (1f - progress()) * (1f - PLATE_FADE * plate)
                scaleX = 1f - narrow
                scaleY = 1f - narrow
                transformOrigin = TransformOrigin(HALF, 1f)
            }
            .kSurface(surface, shape),
    )
}

@Composable
private fun NotificationCard(
    item: KNotification,
    look: NStackLook,
    role: NStackCardRole,
    labels: NStackLabels,
    fade: () -> Float,
    onTap: (() -> Unit)?,
    onDismiss: () -> Unit,
    onDismissAll: () -> Unit,
) {
    val swipe = remember { SwipeAwayState() }
    swipe.still = look.still
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val tap by rememberUpdatedState(onTap)
    val collapsedFront = role.index == 0 && role.count > 1 && !role.expanded
    Box(
        Modifier
            .graphicsLayer {
                translationX = swipe.x
                val w = size.width
                val away = if (w > 0f) (abs(swipe.x) / w).coerceIn(0f, 1f) else 0f
                alpha = fade() * (1f - away * SWIPE_FADE)
            }
            .pointerInput(swipe) { swipeAway(swipe, scope) { dismiss() } }
            .then(
                if (onTap != null) {
                    Modifier.kClickable(look.shape, onClickLabel = if (collapsedFront) labels.expand else null) { tap?.invoke() }
                } else {
                    Modifier
                },
            )
            .kSurface(look.surface, look.shape)
            .clearAndSetSemantics {
                if (collapsedFront) {
                    contentDescription = "${role.summary}. ${item.title}, ${item.text}"
                    customActions = listOf(
                        CustomAccessibilityAction(labels.clearAll) {
                            onDismissAll()
                            true
                        },
                    )
                } else {
                    contentDescription = "${item.title}, ${item.text}, ${item.time}"
                    if (role.expanded) collectionItemInfo = CollectionItemInfo(role.index, 1, 0, 1)
                    customActions = listOf(
                        CustomAccessibilityAction(labels.dismiss) {
                            dismiss()
                            true
                        },
                    )
                }
            },
    ) {
        NotificationBody(look.appName, item, role.more, look.icon, look.surface)
    }
}

/**
 * 판 [PLATES]개, 위 줄, 카드를 차례로 받아 놓는다. 위 줄과 뒤 카드는 펼치는 동안에만 구성되어 있다.
 * 접힌 자리와 펼친 자리를 [progress]로 섞고, 다 펼치면 판을 놓지 않는다.
 */
private fun MeasureScope.measureStack(
    measurables: List<Measurable>,
    constraints: Constraints,
    counts: StackCounts,
    progress: Float,
): MeasureResult {
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth.coerceAtLeast(FALLBACK_WIDTH.roundToPx())
    val exact = Constraints(minWidth = width, maxWidth = width)
    val shown = counts.shown
    val header = if (shown > 1) measurables[PLATES].measure(exact) else null
    val firstCard = if (header != null) PLATES + 1 else PLATES
    val cards = Array(shown) { measurables[firstCard + it].measure(exact) }
    val frontHeight = cards[0].height
    val plates = minOf(counts.total - 1, PLATES)
    val plateConstraints = Constraints.fixed(width, frontHeight)
    val peek = PEEK.roundToPx()
    val gap = GAP.roundToPx()
    val collapsedHeight = frontHeight + peek * plates
    val headerSpace = if (header != null) header.height + gap else 0
    var expandedHeight = headerSpace - gap
    for (card in cards) expandedHeight += card.height + gap
    if (header == null) expandedHeight = collapsedHeight
    val p = progress.coerceIn(0f, 1f)
    val height = lerpInt(collapsedHeight, expandedHeight, p).coerceIn(constraints.minHeight, constraints.maxHeight)
    val plateList = Array(PLATES) { measurables[it].measure(plateConstraints) }
    return layout(width, height) {
        header?.placeRelative(0, lerpInt(-header.height / 2, 0, p))
        if (p < 1f) {
            for (k in 0 until plates) plateList[k].placeRelative(0, peek * (k + 1), zIndex = -(PLATES + k).toFloat())
        }
        var y = headerSpace
        for (i in cards.indices) {
            val card = cards[i]
            val expandedY = if (header == null) 0 else y
            card.placeRelative(0, lerpInt(peek * minOf(i, plates), expandedY, p), zIndex = -i.toFloat())
            y += card.height + gap
        }
    }
}

private fun lerpInt(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).roundToInt()

private const val PLATES = 2
private const val PLATE_NARROW = 0.05f
private const val PLATE_FADE = 0.25f
private const val HALF = 0.5f
private const val SWIPE_FADE = 0.6f
private const val EXPAND_DAMPING = 0.82f
private val PEEK = 9.dp
private val GAP = 10.dp
private val FALLBACK_WIDTH = 320.dp
