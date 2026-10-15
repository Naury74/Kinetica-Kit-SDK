package com.naury.kinetica.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlin.math.roundToInt

/** 폴더블과 태블릿에서 화면 폭에 따라 배치를 바꾸는 컴포넌트([GlassListDetail], [GlassAdaptiveNavigation])의 기본값. */
public object GlassAdaptiveDefaults {
    /** 이 폭보다 좁으면 한 칸 배치(폰, 접은 폴더블)로 본다. Material의 compact 너비 구간 경계와 같다. */
    public val TwoPaneMinWidth: Dp = 600.dp

    /** 두 칸 배치에서 목록 칸의 기본 폭. 화면 폭의 절반을 넘지 않게 줄인다. */
    public val ListPaneWidth: Dp = 360.dp
}

/**
 * 목록과 상세를 화면 폭에 맞춰 배치한다. 폭이 [twoPaneMinWidth]보다 좁으면 [PushNavigation]처럼 상세가 옆에서 밀려 들어오고,
 * 넓으면(펼친 폴더블, 태블릿) 목록을 [listWidth] 폭으로 시작 쪽에 고정하고 나머지에 상세를 나란히 놓는다. 두 칸 사이에는 글래스 경계선을 그린다.
 *
 * 폴더블의 힌지가 화면을 세로로 가르면(반쯤 접었거나 힌지가 화면을 가리는 기기) 두 칸 경계를 힌지에 맞춰 내용이 접힌 선에 걸리지 않게 한다.
 * 힌지 양쪽 중 한쪽이 200dp보다 좁으면 힌지를 무시한다.
 *
 * 선택 상태는 호스트가 들고 있으므로 접고 펼치거나 멀티 윈도우로 크기가 바뀌어도 그대로다. 목록과 상세의 `rememberSaveable` 상태(스크롤 위치 등)도
 * 배치가 바뀌거나 화면이 회전할 때 유지된다. 상세 상태는 지금 고른 항목 것만 기억하고, 다른 항목을 고르면 이전 항목의 상태는 버린다.
 * 그래서 많은 항목을 차례로 열어도 저장 상태가 쌓이지 않고, 예전 항목을 다시 고르면 처음 상태로 열린다. 좁은 화면에서 상세를 보는 중이면 뒤로 가기와 시작 쪽 가장자리 드래그가 [onBack]을 부른다.
 * 넓은 화면에서는 뒤로 가기를 가로채지 않는다. 좁은 화면에서는 상세가 목록 위로 밀려 들어오므로, 배경이 투명한 상세는 그동안 목록이 비쳐 보인다.
 * 상세마다 배경을 칠하는 대신 [detailBackground]에 목록과 같은 배경을 한 번 주면 된다.
 *
 * ```kotlin
 * var selected by rememberSaveable { mutableStateOf<String?>(null) }
 * GlassListDetail(selected, onBack = { selected = null }, list = { MailList(onOpen = { selected = it }) }) { id -> MailDetail(id) }
 * ```
 *
 * @param selected 상세에 보일 항목. `null`이면 목록만 보이고, 두 칸 배치에서는 상세 칸에 [emptyDetail]을 그린다.
 *   항목은 `equals`로 구분한다. 같은 항목을 다시 넘기면 상세 상태가 그대로 이어진다.
 * @param onBack 좁은 화면에서 상세를 닫아 달라는 요청. 보통 [selected]를 `null`로 바꾼다.
 * @param listWidth 두 칸 배치에서 목록 칸 폭. 화면 폭의 절반을 넘으면 절반으로 줄인다.
 * @param twoPaneMinWidth 두 칸 배치로 바꾸는 최소 폭. 이 컴포저블이 받은 폭으로 판단하므로 멀티 윈도우에서도 맞게 동작한다.
 * @param emptyDetail 두 칸 배치에서 아무것도 고르지 않았을 때 상세 칸에 그릴 내용
 * @param detailBackground 상세 칸 뒤에 깔 배경. 상세 칸 크기로 그리므로 내용에 `Modifier.fillMaxSize()`를 붙인다. 두 칸 배치에서는 상세가
 *   바뀌어도 그대로 있고 [emptyDetail] 뒤에도 깔린다. 좁은 화면에서는 상세와 함께 밀려 들어와 아래 목록을 가린다. `null`이면 투명하다.
 *   상세 안의 [GlassScaffold] 글래스는 자기 `background`만 블러로 비추므로, 상세 위 글래스에 배경이 비쳐야 하면 그쪽 `background`에 준다.
 */
@Composable
public fun <T : Any> GlassListDetail(
    selected: T?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listWidth: Dp = GlassAdaptiveDefaults.ListPaneWidth,
    twoPaneMinWidth: Dp = GlassAdaptiveDefaults.TwoPaneMinWidth,
    style: GlassStyle = LocalGlassStyle.current,
    emptyDetail: @Composable () -> Unit = {},
    detailBackground: (@Composable () -> Unit)? = null,
    list: @Composable () -> Unit,
    detail: @Composable (T) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    // 상세 저장 키의 번호. 회전이나 프로세스 재시작 뒤에도 지금 항목이 같은 키를 다시 쓰도록 저장한다.
    val generation = rememberSaveable { IntArray(1) }
    val panes = remember(holder) { Panes(holder, generation) }
    // 좁은 화면에서 상세를 빼는 애니메이션 동안에는 selected가 이미 null이므로 마지막 항목을 계속 그린다.
    var shown by remember { mutableStateOf(selected) }
    if (selected != null) {
        shown = selected
        panes.select(selected)
    }
    panes.list = list
    val latestDetail by rememberUpdatedState(detail)
    // 상세 람다가 바뀔 때마다 상세 내용 전체를 무효화하지 않도록, 한 번 만든 람다가 최신 람다를 부르게 한다.
    @Suppress("UNCHECKED_CAST")
    panes.detail = remember { { item: Any -> latestDetail(item as T) } }
    BoxWithConstraints(modifier) {
        if (maxWidth < twoPaneMinWidth) {
            val stack = if (selected == null) listOf(LIST_KEY) else listOf(LIST_KEY, DETAIL_KEY)
            PushNavigation(stack, onBack, Modifier.fillMaxSize()) { key ->
                if (key == LIST_KEY) {
                    panes.listContent()
                } else {
                    DetailPane(detailBackground) { shown?.let { panes.Detail(it) } }
                }
            }
        } else {
            TwoPane(listWidth, style, panes, selected, emptyDetail, detailBackground)
        }
    }
}

/**
 * 목록과 상세를 배치가 바뀌어도 이어지는 내용으로 감싼다. 한 칸과 두 칸 배치는 같은 프레임에 서로 바뀌므로, 저장 키만으로는
 * 새 배치가 상태를 읽은 뒤에야 이전 배치가 상태를 저장해 상태를 잃는다. 그래서 내용을 `movableContentOf`로 옮기고,
 * 저장 키는 회전과 프로세스 재시작에 쓴다. 상세 키는 항목을 고를 때마다 새 번호로 만든다. 항목 자체는 Bundle에 넣을 수 없을 수 있어 키로 쓰지 않는다.
 */
internal class Panes(private val holder: SaveableStateHolder, private val generation: IntArray) {
    var list: @Composable () -> Unit by mutableStateOf({})
    var detail: @Composable (Any) -> Unit by mutableStateOf({})

    val listContent: @Composable () -> Unit = movableContentOf { holder.SaveableStateProvider(LIST_KEY) { list() } }

    // 두 칸 배치에서는 바뀌는 동안 나가는 항목도 함께 그리므로, 최근에 고른 몇 항목의 내용을 기억한다.
    private val details = LinkedHashMap<Any, DetailEntry>()
    private var current: Any? = null

    /** [item]을 지금 상세로 정한다. 다른 항목이면 새 키를 주고, 이전 항목의 저장 상태는 화면에서 빠질 때 버리게 한다. */
    fun select(item: Any) {
        if (item == current) return
        val previous = current
        if (previous != null) {
            details[previous]?.let { holder.removeState(it.key) }
            generation[0]++
        }
        details.remove(item)
        details[item] = entry("$DETAIL_KEY:${generation[0]}")
        while (details.size > MAX_REMEMBERED_DETAILS) details.remove(details.keys.first())
        current = item
    }

    @Composable
    fun Detail(item: Any) {
        val entry = details[item]
        if (entry != null) {
            entry.content(item)
        } else {
            // 기억에서 밀려난 아주 오래된 항목이 아직 나가는 중이면 임시 키로 그린다.
            holder.SaveableStateProvider("$DETAIL_KEY:gone:${System.identityHashCode(item)}") { detail(item) }
        }
    }

    private fun entry(key: String) = DetailEntry(key, movableContentOf { item: Any -> holder.SaveableStateProvider(key) { detail(item) } })
}

internal class DetailEntry(val key: String, val content: @Composable (Any) -> Unit)

@Composable
private fun <T : Any> TwoPane(
    listWidth: Dp,
    style: GlassStyle,
    panes: Panes,
    selected: T?,
    emptyDetail: @Composable () -> Unit,
    detailBackground: (@Composable () -> Unit)?,
) {
    val fold = rememberVerticalFold()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val still = reducedMotion()
    val line = style.edgeLight.takeIf { it.alpha > 0f } ?: style.onGlass.copy(alpha = DIVIDER_FALLBACK_ALPHA)
    Layout(
        content = {
            Box(Modifier.layoutId(LIST_KEY).semantics { isTraversalGroup = true }) { panes.listContent() }
            Box(
                Modifier.layoutId(DETAIL_KEY).semantics {
                    isTraversalGroup = true
                    traversalIndex = 1f
                },
            ) {
                DetailPane(detailBackground) { DetailSwitch(selected, still, panes, emptyDetail) }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .paneDivider(line, rtl) { splitPanes(it, listWidth.toPx(), MIN_FOLD_PANE.toPx(), fold?.translate(-origin), rtl) },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val s = splitPanes(width.toFloat(), listWidth.toPx(), MIN_FOLD_PANE.toPx(), fold?.translate(-origin), rtl)
        val start = s.start.roundToInt()
        val gap = s.gap.roundToInt()
        val detailWidth = (width - start - gap).coerceAtLeast(0)
        val height = constraints.maxHeight
        val list = measurables.first { it.layoutId == LIST_KEY }.measure(Constraints.fixed(start, height))
        val detail = measurables.first { it.layoutId == DETAIL_KEY }.measure(Constraints.fixed(detailWidth, height))
        layout(width, height) {
            list.placeRelative(0, 0)
            detail.placeRelative(start + gap, 0)
        }
    }
}

@Composable
private fun <T : Any> DetailSwitch(selected: T?, still: Boolean, panes: Panes, emptyDetail: @Composable () -> Unit) {
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            if (still) {
                fadeIn(snap()) togetherWith fadeOut(snap())
            } else {
                (fadeIn(tween(DETAIL_FADE_MS)) + scaleIn(spring(stiffness = Spring.StiffnessMediumLow), DETAIL_START_SCALE))
                    .togetherWith(fadeOut(tween(DETAIL_FADE_MS / 2)))
            }
        },
        contentKey = { it },
        label = "glass-list-detail",
    ) { item -> if (item == null) emptyDetail() else panes.Detail(item) }
}

/** 상세 칸. [background]를 상세 칸 크기로 먼저 깔고 그 위에 [content]를 그린다. */
@Composable
private fun DetailPane(background: (@Composable () -> Unit)?, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        background?.invoke()
        content()
    }
}

/** 시작 쪽 칸의 폭과 두 칸 사이 간격(px). 간격은 힌지가 화면을 가릴 때만 0보다 크다. */
internal class Split(val start: Float, val gap: Float)

/** [fold]는 이 배치 기준 좌표(px)의 접힌 선이다. 양쪽 칸이 [minPane]보다 좁아지면 접힌 선을 무시하고 [listWidth]를 쓴다. */
internal fun splitPanes(width: Float, listWidth: Float, minPane: Float, fold: Rect?, rtl: Boolean): Split {
    if (fold != null) {
        val left = fold.left
        val right = fold.right
        val start = if (rtl) width - right else left
        val gap = right - left
        if (start >= minPane && width - start - gap >= minPane) return Split(start, gap)
    }
    return Split(listWidth.coerceAtMost(width / 2f), 0f)
}

/**
 * 두 칸 경계에 세로 글래스 경계선을 그린다. 위아래 끝은 흐리게 사라지고, 상세 칸 쪽으로 옅은 그림자를 드리운다.
 * 힌지가 칸 사이를 가리면 그 가운데에 그린다.
 */
private fun Modifier.paneDivider(line: Color, rtl: Boolean, split: Density.(Float) -> Split): Modifier = drawWithCache {
    val s = split(size.width)
    val center = s.start + s.gap / 2f
    val x = if (rtl) size.width - center else center
    val stroke = DIVIDER_WIDTH.toPx()
    val lineBrush = Brush.verticalGradient(
        0f to line.copy(alpha = 0f),
        DIVIDER_FADE to line,
        1f - DIVIDER_FADE to line,
        1f to line.copy(alpha = 0f),
    )
    val shade = DIVIDER_SHADE.toPx()
    val shadeColors = listOf(Color.Black.copy(alpha = SHADE_ALPHA), Color.Transparent)
    val shadeStart = if (rtl) x - shade else x
    val shadeBrush = Brush.horizontalGradient(
        if (rtl) shadeColors.reversed() else shadeColors,
        startX = shadeStart,
        endX = shadeStart + shade,
    )
    onDrawWithContent {
        drawContent()
        drawRect(shadeBrush, Offset(shadeStart, 0f), Size(shade, size.height))
        drawRect(lineBrush, Offset(x - stroke / 2f, 0f), Size(stroke, size.height))
    }
}

/**
 * 창을 세로로 가르는 접힌 선의 위치(창 좌표, px). 반쯤 접었거나 힌지가 화면을 가리는 경우만 돌려주고,
 * 평평하게 펼쳐 접힌 선이 보이지 않는 화면이나 Activity가 없는 곳에서는 `null`이다.
 */
@Composable
internal fun rememberVerticalFold(): Rect? {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val fold by produceState<Rect?>(null, activity) {
        if (activity == null) return@produceState
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
            value = info.displayFeatures
                .filterIsInstance<FoldingFeature>()
                .firstOrNull { it.orientation == FoldingFeature.Orientation.VERTICAL && it.isSeparating }
                ?.bounds
                ?.toComposeRect()
        }
    }
    return fold
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val LIST_KEY = "kinetica-list"
private const val DETAIL_KEY = "kinetica-detail"
private const val MAX_REMEMBERED_DETAILS = 8
private val MIN_FOLD_PANE = 200.dp
private val DIVIDER_WIDTH = 1.dp
private val DIVIDER_SHADE = 10.dp
private const val DIVIDER_FADE = 0.08f
private const val DIVIDER_FALLBACK_ALPHA = 0.2f
private const val SHADE_ALPHA = 0.06f
private const val DETAIL_FADE_MS = 220
private const val DETAIL_START_SCALE = 0.98f
