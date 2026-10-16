package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlin.math.roundToInt

/** 창 폭 구간. 경계는 Material 창 크기 등급과 같다(600dp, 840dp). */
public enum class GlassWindowWidth {
    /** 600dp 미만. 폰 세로, 접은 폴더블 */
    Compact,

    /** 600dp 이상 840dp 미만. 펼친 폴더블, 작은 태블릿 세로 */
    Medium,

    /** 840dp 이상. 태블릿 가로, 데스크톱 창 */
    Expanded,
}

/** 폴더블 기기의 자세 */
public enum class GlassPosture {
    /** 평평하게 펼쳤거나 접는 부분이 없는 기기 */
    Flat,

    /** 접힌 선이 세로인 반쯤 접은 상태. 책처럼 양옆으로 나뉜다. */
    Book,

    /** 접힌 선이 가로인 반쯤 접은 상태. 위는 화면, 아래는 받침처럼 쓰는 탁자 모드다. */
    Tabletop,
}

/**
 * 지금 창의 크기와 접힘 상태. [rememberGlassWindowLayout]으로 얻는다. 접고 펼치거나 멀티 윈도우로 크기가 바뀌면 새 값이 온다.
 *
 * @property width 창 폭(dp)
 * @property height 창 높이(dp)
 * @property posture 폴더블 자세. 접는 부분이 없거나 알 수 없으면 [GlassPosture.Flat]이다.
 * @property hinge 화면을 실제로 가르는 접힌 선의 창 좌표(px). 반쯤 접었거나 힌지가 화면을 가릴 때만 있고, 그 밖에는 `null`이다.
 */
@Immutable
public class GlassWindowLayout(
    public val width: Dp,
    public val height: Dp,
    public val posture: GlassPosture = GlassPosture.Flat,
    public val hinge: Rect? = null,
) {
    /** 창 폭 구간 */
    public val widthClass: GlassWindowWidth
        get() = when {
            width < GlassWindowDefaults.MediumMinWidth -> GlassWindowWidth.Compact
            width < GlassWindowDefaults.ExpandedMinWidth -> GlassWindowWidth.Medium
            else -> GlassWindowWidth.Expanded
        }

    /** 폭이 600dp 미만이라 한 칸 배치가 맞는 창이다. */
    public val isCompact: Boolean get() = widthClass == GlassWindowWidth.Compact

    /** 창 높이가 480dp 미만이다. 폰 가로 화면처럼 세로 여유가 없을 때 시트 높이나 큰 제목을 줄이는 데 쓴다. */
    public val isShort: Boolean get() = height < SHORT_HEIGHT

    /**
     * 폭 [available]에 칸 최소 폭 [minCellWidth]와 간격 [spacing]으로 넣을 수 있는 열 수. 1 이상 [maxColumns] 이하다.
     * [available]을 주지 않으면 창 폭을 쓴다. 목록 안쪽 여백을 뺀 폭을 주는 편이 정확하다.
     */
    public fun columns(minCellWidth: Dp, maxColumns: Int = 4, spacing: Dp = 0.dp, available: Dp = width): Int {
        require(minCellWidth > 0.dp) { "minCellWidth는 0보다 커야 합니다." }
        require(maxColumns >= 1) { "maxColumns는 1 이상이어야 합니다." }
        val count = ((available + spacing) / (minCellWidth + spacing)).toInt()
        return count.coerceIn(1, maxColumns)
    }

    override fun equals(other: Any?): Boolean = other is GlassWindowLayout &&
        width == other.width && height == other.height && posture == other.posture && hinge == other.hinge

    override fun hashCode(): Int = ((width.hashCode() * 31 + height.hashCode()) * 31 + posture.hashCode()) * 31 + hinge.hashCode()

    override fun toString(): String = "GlassWindowLayout(width=$width, height=$height, posture=$posture, hinge=$hinge)"
}

/** 넓은 화면에서 내용과 팝업이 지나치게 늘어나지 않게 하는 폭 기준 */
public object GlassWindowDefaults {
    /** 이 폭부터 [GlassWindowWidth.Medium]이다. */
    public val MediumMinWidth: Dp = 600.dp

    /** 이 폭부터 [GlassWindowWidth.Expanded]다. */
    public val ExpandedMinWidth: Dp = 840.dp

    /** 본문 글과 설정 목록처럼 한 줄로 읽는 내용의 최대 폭 */
    public val ReadingMaxWidth: Dp = 640.dp

    /** 다이얼로그, 확인 창의 최대 폭 */
    public val DialogMaxWidth: Dp = 560.dp

    /** 바텀 시트의 최대 폭. 넓은 화면에서는 이 폭으로 가운데에 뜬다. */
    public val SheetMaxWidth: Dp = 640.dp

    /**
     * 폭 [containerWidth]인 스크롤 목록에서 내용 폭을 [maxContentWidth]로 맞추는 양옆 여백. [minimum]보다 작아지지 않는다.
     * `LazyColumn`의 `contentPadding`에 쓰면 목록은 화면 끝까지 스크롤되면서 항목만 가운데에 놓인다.
     */
    public fun sidePadding(containerWidth: Dp, maxContentWidth: Dp = ReadingMaxWidth, minimum: Dp = 16.dp): Dp =
        maxOf(minimum, (containerWidth - maxContentWidth) / 2)
}

/**
 * 지금 창의 크기와 폴더블 자세를 돌려준다. 창 크기는 Compose 창 크기에서, 자세와 힌지는 Jetpack WindowManager에서 읽는다.
 * Activity 밖(미리 보기, 일부 테스트)에서는 자세가 [GlassPosture.Flat], 힌지가 `null`이다.
 *
 * ```kotlin
 * val window = rememberGlassWindowLayout()
 * LazyVerticalGrid(GridCells.Fixed(window.columns(minCellWidth = 320.dp))) { ... }
 * ```
 */
@Composable
public fun rememberGlassWindowLayout(): GlassWindowLayout {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val fold = rememberFoldingFeature()
    val width = with(density) { size.width.toDp() }
    val height = with(density) { size.height.toDp() }
    return remember(width, height, fold) {
        GlassWindowLayout(width, height, fold.posture(), fold?.takeIf { it.isSeparating }?.bounds?.toComposeRect())
    }
}

/**
 * 탁자 모드(가로로 반쯤 접음)일 때 [top]을 접힌 선 위에, [bottom]을 아래에 놓는다. 영상과 조작부, 사진과 도구처럼
 * 보는 내용과 만지는 내용을 나눌 때 쓴다. 탁자 모드가 아니거나 접힌 선이 이 레이아웃을 가로지르지 않으면
 * [top]을 내용 높이대로 위에 놓고 [bottom]이 남은 높이를 채운다.
 *
 * 접힌 선 위나 아래 한쪽이 [minPaneHeight]보다 낮아지면 접힌 선을 무시한다. 레이아웃은 받은 크기를 모두 차지한다.
 */
@Composable
public fun GlassTabletopLayout(
    modifier: Modifier = Modifier,
    minPaneHeight: Dp = 120.dp,
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    val window = rememberGlassWindowLayout()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val hinge = window.hinge?.takeIf { window.posture == GlassPosture.Tabletop }
    Layout(
        content = {
            Box(Modifier.layoutId(TOP_ID)) { top() }
            Box(Modifier.layoutId(BOTTOM_ID)) { bottom() }
        },
        modifier = modifier.onGloballyPositioned { origin = it.positionInWindow() },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
        val split = hinge?.translate(-origin)?.let { tabletopSplit(height.toFloat(), it.top, it.bottom, minPaneHeight.toPx()) }
        val topMeasurable = measurables.first { it.layoutId == TOP_ID }
        val bottomMeasurable = measurables.first { it.layoutId == BOTTOM_ID }
        if (split != null) {
            val topHeight = split.first.roundToInt()
            val bottomStart = split.second.roundToInt()
            val t = topMeasurable.measure(Constraints.fixed(width, topHeight))
            val b = bottomMeasurable.measure(Constraints.fixed(width, (height - bottomStart).coerceAtLeast(0)))
            layout(width, height) {
                t.placeRelative(0, 0)
                b.placeRelative(0, bottomStart)
            }
        } else {
            val t = topMeasurable.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
            val rest = (height - t.height).coerceAtLeast(0)
            val b = bottomMeasurable.measure(Constraints.fixed(width, rest))
            layout(width, height) {
                t.placeRelative(0, 0)
                b.placeRelative(0, t.height)
            }
        }
    }
}

/**
 * 창을 가르는 접힌 선이 있으면 내용을 접힌 선 한쪽 칸에 넣는다. 가로 접힌 선(탁자 모드)이면 위아래 중 [prefer]가 있는 칸,
 * 세로 접힌 선(책 모드)이면 좌우 중 [prefer]가 있는 칸을 쓰고, [prefer]가 없으면 위 칸이나 시작 쪽 칸을 쓴다.
 * 고른 칸이 [minPane]보다 좁으면 넓은 쪽 칸을, 둘 다 좁으면 접힌 선을 무시한다. 내용은 고른 칸 크기로 잰다.
 * [prefer]는 창 좌표(px)다. [horizontal]이 false면 가로 접힌 선은 무시한다. 아래에서 올라오는 시트처럼 탁자 모드에서 아래 칸에 두는 편이 맞는 요소에 쓴다.
 */
@Composable
internal fun Modifier.avoidHinge(prefer: Offset? = null, minPane: Dp = 200.dp, horizontal: Boolean = true): Modifier {
    val hinge = rememberGlassWindowLayout().hinge?.takeIf { horizontal || it.height > it.width } ?: return this
    val origin = remember { mutableStateOf(Offset.Zero) }
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    return onGloballyPositioned { origin.value = it.positionInWindow() }.layout { measurable, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val area =
            hingeFreeArea(
                Size(width.toFloat(), height.toFloat()),
                hinge.translate(-origin.value),
                prefer?.minus(origin.value),
                minPane.toPx(),
                rtl,
            )
        val placeable = measurable.measure(Constraints.fixed(area.width.roundToInt(), area.height.roundToInt()))
        layout(width, height) { placeable.place(area.left.roundToInt(), area.top.roundToInt()) }
    }
}

/** [avoidHinge]가 고르는 칸(px, 이 레이아웃 기준). 접힌 선이 레이아웃을 가로지르지 않으면 전체 영역이다. */
internal fun hingeFreeArea(size: Size, hinge: Rect, prefer: Offset?, minPane: Float, rtl: Boolean): Rect {
    val whole = Rect(Offset.Zero, size)
    val pane = if (hinge.width >= hinge.height) {
        horizontalHingePane(size, hinge, prefer, minPane)
    } else {
        verticalHingePane(size, hinge, prefer, minPane, rtl)
    }
    return pane ?: whole
}

private fun horizontalHingePane(size: Size, hinge: Rect, prefer: Offset?, minPane: Float): Rect? {
    if (hinge.top <= 0f || hinge.bottom >= size.height) return null
    val top = Rect(0f, 0f, size.width, hinge.top)
    val bottom = Rect(0f, hinge.bottom, size.width, size.height)
    val wantBottom = prefer != null && prefer.y >= hinge.bottom
    return pickPane(if (wantBottom) bottom else top, if (wantBottom) top else bottom, minPane, vertical = false)
}

private fun verticalHingePane(size: Size, hinge: Rect, prefer: Offset?, minPane: Float, rtl: Boolean): Rect? {
    if (hinge.left <= 0f || hinge.right >= size.width) return null
    val left = Rect(0f, 0f, hinge.left, size.height)
    val right = Rect(hinge.right, 0f, size.width, size.height)
    val wantRight = if (prefer != null) prefer.x >= hinge.right else rtl
    return pickPane(if (wantRight) right else left, if (wantRight) left else right, minPane, vertical = true)
}

private fun pickPane(first: Rect, second: Rect, minPane: Float, vertical: Boolean): Rect? {
    fun size(r: Rect) = if (vertical) r.width else r.height
    return when {
        size(first) >= minPane -> first
        size(second) >= minPane -> second
        else -> null
    }
}

/**
 * 이 요소를 [maxWidth] 폭으로 줄여 가로 가운데에 둔다. 좁은 화면에서는 받은 폭을 다 쓴다.
 * 넓은 화면에서 글, 입력 양식, 설정 카드처럼 한 줄로 읽는 내용이 끝없이 늘어나지 않게 한다.
 */
public fun Modifier.glassReadableWidth(maxWidth: Dp = GlassWindowDefaults.ReadingMaxWidth): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = maxWidth).fillMaxWidth()

/**
 * 높이 [height]인 레이아웃 기준 좌표의 가로 접힌 선([hingeTop]~[hingeBottom])으로 나눈 위 칸 높이와 아래 칸 시작 위치.
 * 접힌 선이 레이아웃 밖이거나 한쪽이 [minPane]보다 낮으면 `null`이다.
 */
internal fun tabletopSplit(height: Float, hingeTop: Float, hingeBottom: Float, minPane: Float): Pair<Float, Float>? {
    if (hingeTop < minPane || height - hingeBottom < minPane) return null
    return hingeTop to hingeBottom
}

private fun FoldingFeature?.posture(): GlassPosture = when {
    this == null || state != FoldingFeature.State.HALF_OPENED -> GlassPosture.Flat
    orientation == FoldingFeature.Orientation.HORIZONTAL -> GlassPosture.Tabletop
    else -> GlassPosture.Book
}

@Composable
private fun rememberFoldingFeature(): FoldingFeature? {
    val context = LocalContext.current
    val activity = remember(context) { context.findHostActivity() }
    val feature by produceState<FoldingFeature?>(null, activity) {
        if (activity == null) return@produceState
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
            value = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
        }
    }
    return feature
}

private tailrec fun android.content.Context.findHostActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

private val SHORT_HEIGHT = 480.dp
private const val TOP_ID = "kinetica-tabletop-top"
private const val BOTTOM_ID = "kinetica-tabletop-bottom"
