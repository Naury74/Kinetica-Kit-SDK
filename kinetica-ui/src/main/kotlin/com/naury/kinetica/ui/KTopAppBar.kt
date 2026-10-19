package com.naury.kinetica.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** [KTopAppBar]의 모양 */
public enum class KTopAppBarVariant {
    /** 한 줄 바. 제목은 뒤로 버튼 다음 시작 쪽에 놓인다. 상세 화면의 기본 바다. */
    Small,

    /** 한 줄 바. 제목을 바 가운데에 놓는다. */
    CenterAligned,

    /**
     * 두 줄 바. 아래 줄에 26sp 제목이 있고, 스크롤하면 그 제목이 콘텐츠와 함께 올라가 사라지며 위 줄에 작은 제목이 나타난다.
     * 바 높이는 고정이라 콘텐츠 패딩이 스크롤 중에 바뀌지 않는다.
     */
    Medium,
}

/**
 * [KTopAppBar]의 스크롤 상태. [connection]을 목록의 부모에 `Modifier.nestedScroll(state.connection)`로 붙이면 목록이 바 아래로
 * 지나갈 때 바 배경이 나타나고, [KTopAppBarVariant.Medium]이면 큰 제목이 접힌다. 목록 스크롤은 가로채지 않는다.
 *
 * 인자 없는 [rememberKTopAppBarState]는 중첩 스크롤로 전해진 거리만 세므로, 코드로 옮긴 스크롤까지 반영하려면 목록 상태를 받는 오버로드를 쓴다.
 */
@Stable
public class KTopAppBarState internal constructor(initialScrolled: Float, private val position: (() -> Float)? = null) {
    internal var scrolled by mutableFloatStateOf(initialScrolled)
    internal var range by mutableFloatStateOf(0f)
    internal var fadeDistance = 1f

    // 목록이 맨 위에서 내려간 거리(px). 목록 상태를 받았으면 실제 위치를, 아니면 중첩 스크롤로 센 거리를 쓴다.
    internal val offset: Float get() = position?.invoke() ?: scrolled

    /** Medium 바의 큰 제목이 접힌 정도(0..1). 다른 모양이거나 아직 측정하지 못했으면 0이다. */
    public val collapsedFraction: Float get() = if (range <= 0f) 0f else (offset / range).coerceIn(0f, 1f)

    /** 콘텐츠가 바 아래로 지나간 정도(0..1). 바 배경이 이 값만큼 나타난다. */
    public val overlappedFraction: Float
        get() = if (range > 0f) {
            ((collapsedFraction - MEDIUM_FADE_START) / (1f - MEDIUM_FADE_START)).coerceIn(0f, 1f)
        } else {
            (offset / fadeDistance).coerceIn(0f, 1f)
        }

    /** 목록의 부모에 붙일 중첩 스크롤 연결 */
    public val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            scrolled = (scrolled - consumed.y).coerceAtLeast(0f)
            // 목록이 맨 위에 닿아 남은 아래쪽 스크롤이 있으면 오차가 쌓이지 않게 0으로 맞춘다.
            if (available.y > 0f) scrolled = 0f
            return Offset.Zero
        }
    }

    internal companion object {
        val Saver: Saver<KTopAppBarState, Float> = Saver(save = { it.scrolled }, restore = { KTopAppBarState(it) })
    }
}

/** 중첩 스크롤로 전해진 거리로 바 배경과 접힘을 정하는 상태를 만든다. 화면을 회전해도 유지된다. */
@Composable
public fun rememberKTopAppBarState(): KTopAppBarState = rememberSaveable(saver = KTopAppBarState.Saver) { KTopAppBarState(0f) }

/** [listState]의 실제 스크롤 위치로 바 배경과 접힘을 정하는 상태를 만든다. 첫 항목이 지나가면 다 접힌 것으로 본다. */
@Composable
public fun rememberKTopAppBarState(listState: LazyListState): KTopAppBarState = remember(listState) {
    KTopAppBarState(0f) {
        if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else Float.POSITIVE_INFINITY
    }
}

/** [gridState]의 실제 스크롤 위치로 바 배경과 접힘을 정하는 상태를 만든다. 첫 줄이 지나가면 다 접힌 것으로 본다. 사진 격자에 쓴다. */
@Composable
public fun rememberKTopAppBarState(gridState: LazyGridState): KTopAppBarState = remember(gridState) {
    KTopAppBarState(0f) {
        if (gridState.firstVisibleItemIndex == 0) gridState.firstVisibleItemScrollOffset.toFloat() else Float.POSITIVE_INFINITY
    }
}

/** [gridState]의 실제 스크롤 위치로 바 배경과 접힘을 정하는 상태를 만든다. 첫 항목이 지나가면 다 접힌 것으로 본다. */
@Composable
public fun rememberKTopAppBarState(gridState: LazyStaggeredGridState): KTopAppBarState = remember(gridState) {
    KTopAppBarState(0f) {
        if (gridState.firstVisibleItemIndex == 0) gridState.firstVisibleItemScrollOffset.toFloat() else Float.POSITIVE_INFINITY
    }
}

/** [scrollState]의 스크롤 위치(px)로 바 배경과 접힘을 정하는 상태를 만든다. */
@Composable
public fun rememberKTopAppBarState(scrollState: ScrollState): KTopAppBarState = remember(scrollState) {
    KTopAppBarState(0f) { scrollState.value.toFloat() }
}

/**
 * 상단 앱 바. 뒤로 버튼, 제목, 끝 쪽 버튼을 한 줄에 놓는다. [state]를 주면 맨 위에서는 배경 없이 화면과 이어져 보이다가,
 * 콘텐츠가 바 아래로 지나가면 표면 스타일 배경과 아래 경계선이 서서히 나타난다. [state]가 null이면 배경을 늘 그린다.
 *
 * [KLargeTitleBar]와의 차이: 큰 제목 바는 34sp 큰 제목이 가운데 작은 제목으로 바뀌고 당기면 늘어난다.
 * 이 바는 큰 제목이 필요 없는 상세 화면이나 편집 화면에 쓴다. [KTopAppBarVariant.Medium]은 그 사이로
 * 26sp 제목이 시작 쪽 작은 제목으로 접힌다.
 *
 * 바 높이는 고정이라 [KineticaScaffold]의 `topBar`에 두면 콘텐츠 패딩이 스크롤 중에 바뀌지 않는다.
 *
 * ```kotlin
 * val bar = rememberKTopAppBarState(listState)
 * KineticaScaffold(topBar = { KTopAppBar("사진", state = bar, onBack = ::finish, actions = { KIconButton(...) }) }) { padding ->
 *     LazyColumn(state = listState, contentPadding = padding) { ... }
 * }
 * ```
 *
 * @param onBack null이 아니면 시작 쪽에 뒤로 버튼을 놓는다. RTL에서는 화살표가 뒤집힌다. [navigationIcon]을 주면 그쪽이 우선한다.
 * @param navigationIcon 시작 쪽에 직접 놓을 버튼(메뉴 버튼 등)
 * @param subtitle 제목 아래 작은 글. [KTopAppBarVariant.Medium]에서는 큰 제목 아래에 놓인다.
 * @param actions 끝 쪽 버튼들. 보통 `KIconButton(variant = KVariant.Text, tone = KTone.Neutral)`을 넣는다.
 * @param windowInsets 바가 피할 시스템 영역. 기본값은 상태 표시줄과 양옆 컷아웃이다.
 */
@Composable
public fun KTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    variant: KTopAppBarVariant = KTopAppBarVariant.Small,
    state: KTopAppBarState? = null,
    onBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    surface: SurfaceStyle = KineticaTheme.surface,
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
) {
    TopAppBarLayout(BarTitle(title, null), modifier, variant, state, onBack, navigationIcon, subtitle, actions, surface, windowInsets)
}

/**
 * 제목 자리에 직접 만든 내용을 놓는 [KTopAppBar]. 제목을 눌러 앨범이나 계정을 고르는 화면처럼 제목이 버튼인 경우에 쓴다.
 * 그런 드롭다운 제목은 [KTopAppBarDropdownTitle]을 쓰면 글자 모양, 화살표, TalkBack 제목 표시가 바에 맞춰진다.
 *
 * [title]은 [KTopAppBarTitleScope] 안에서 그리며, 그 자리에서 String 제목이 쓰는 글자 모양을 [KTopAppBarTitleScope.textStyle]로 받는다.
 * 제목 자리는 TalkBack에서 제목(heading)으로 읽힌다. 안에서 따로 `clickable`처럼 의미를 묶는 수정자를 쓰면 그 노드가 따로 읽히므로,
 * 그때는 그 노드에 `semantics { heading() }`을 함께 붙인다.
 *
 * [KTopAppBarVariant.Medium]이면 [title]을 큰 제목 줄과 위 줄에 한 번씩 그린다. 둘 중 하나만 화면에 놓이며, 큰 제목이 70% 넘게 접히면
 * 위 줄 쪽으로 바뀐다. 놓이지 않은 쪽은 터치와 TalkBack에서 빠지므로 제목이 두 번 읽히거나 보이지 않는 버튼이 눌리지 않는다.
 * 두 곳에 그려지므로 [title] 안에서 `remember`한 상태는 서로 나뉜다. 펼침 여부 같은 상태는 바깥에서 넘긴다.
 *
 * ```kotlin
 * KTopAppBar(
 *     title = { KTopAppBarDropdownTitle(album.name, expanded = picking, onClick = { picking = !picking }) },
 *     onBack = ::finish,
 * )
 * ```
 *
 * 그 밖의 파라미터는 String 제목 [KTopAppBar]와 같다.
 */
@Composable
public fun KTopAppBar(
    title: @Composable KTopAppBarTitleScope.() -> Unit,
    modifier: Modifier = Modifier,
    variant: KTopAppBarVariant = KTopAppBarVariant.Small,
    state: KTopAppBarState? = null,
    onBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    surface: SurfaceStyle = KineticaTheme.surface,
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
) {
    TopAppBarLayout(BarTitle(null, title), modifier, variant, state, onBack, navigationIcon, subtitle, actions, surface, windowInsets)
}

/** 글자 제목이나 제목 슬롯 가운데 하나. */
private class BarTitle(val text: String?, val slot: (@Composable KTopAppBarTitleScope.() -> Unit)?)

@Suppress("LongParameterList") // 두 공개 오버로드의 파라미터를 그대로 받는다.
@Composable
private fun TopAppBarLayout(
    title: BarTitle,
    modifier: Modifier,
    variant: KTopAppBarVariant,
    state: KTopAppBarState?,
    onBack: (() -> Unit)?,
    navigationIcon: (@Composable () -> Unit)?,
    subtitle: String?,
    actions: @Composable RowScope.() -> Unit,
    surface: SurfaceStyle,
    windowInsets: WindowInsets,
) {
    val medium = variant == KTopAppBarVariant.Medium
    val level by rememberBarLevel(state, medium)
    // 제목 슬롯은 Medium에서 두 곳에 그려지므로, 한쪽만 놓아 터치와 TalkBack이 겹치지 않게 한다.
    val smallActive = remember(state, medium) {
        derivedStateOf { !medium || (state?.collapsedFraction ?: 0f) >= SMALL_TITLE_START }
    }
    var rowHeight by remember { mutableIntStateOf(0) }
    val nav: (@Composable () -> Unit)? = navigationIcon ?: onBack?.let { back -> { BackIconButton(surface, back) } }
    Box(modifier.fillMaxWidth()) {
        if (level > 0f) BarFill(surface, level) { rowHeight }
        Column(Modifier.fillMaxWidth()) {
            BarRow(
                BarRowSpec(title, if (medium) null else subtitle, variant, surface, slotHidden = !smallActive.value),
                nav,
                { if (medium) state?.let { smallTitleAlpha(it.collapsedFraction) } ?: 0f else 1f },
                { title.slot == null || smallActive.value },
                Modifier.onSizeChanged { rowHeight = it.height }.windowInsetsPadding(windowInsets),
                actions,
            )
            if (medium) {
                MediumTitle(MediumTitleSpec(title, subtitle, surface, state, windowInsets, slotHidden = smallActive.value)) {
                    !smallActive.value
                }
            }
        }
    }
}

/** [state]에 바 모양을 알리고, 바 배경이 보이는 정도를 몇 단계로만 바꿔 스크롤하는 동안 바를 매 프레임 다시 그리지 않게 한다. */
@Composable
private fun rememberBarLevel(state: KTopAppBarState?, medium: Boolean): State<Float> {
    if (state != null) {
        state.fadeDistance = with(LocalDensity.current) { FADE_DISTANCE.toPx() }
        if (!medium) state.range = 0f
    }
    return remember(state) {
        derivedStateOf { if (state == null) 1f else (state.overlappedFraction * LEVEL_STEPS).roundToInt() / LEVEL_STEPS }
    }
}

private fun smallTitleAlpha(collapsed: Float): Float = ((collapsed - SMALL_TITLE_START) / (1f - SMALL_TITLE_START)).coerceIn(0f, 1f)

/** 바 배경. 높이는 [height](px)이고 아래 가장자리에 옅은 경계선을 긋는다. */
@Composable
private fun BarFill(surface: SurfaceStyle, level: Float, height: () -> Int) {
    val line = surface.contentColor.copy(alpha = LINE_ALPHA * level)
    Box(
        Modifier
            .fillMaxWidth()
            .layoutHeight(height)
            .kSurface(surface.withoutShadow(), RectangleShape, intensity = level)
            .drawWithContent {
                drawContent()
                val stroke = LINE.toPx()
                drawRect(line, Offset(0f, size.height - stroke), androidx.compose.ui.geometry.Size(size.width, stroke))
            },
    )
}

private fun Modifier.layoutHeight(height: () -> Int): Modifier = layout { measurable, constraints ->
    val h = height()
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, h))
    layout(constraints.maxWidth, h) { placeable.place(0, 0) }
}

/** 바 한 줄의 고정 값. 파라미터 수를 줄이려고 묶었다. */
private class BarRowSpec(
    val title: BarTitle,
    val subtitle: String?,
    val variant: KTopAppBarVariant,
    val surface: SurfaceStyle,
    val slotHidden: Boolean,
)

/**
 * 시작 버튼, 제목, 끝 버튼을 한 줄에 놓는다. 가운데 정렬이면 제목을 바 가운데에 두되 양쪽 버튼과 겹치지 않게 폭을 줄인다.
 * [titleAlpha]는 그리기 단계에서 읽고, [titlePlaced]는 배치 단계에서 읽는다. 놓지 않은 제목은 터치와 TalkBack에서 빠진다.
 */
@Suppress("LongMethod") // 디자인 언어 토큰(모양, 밀도, 굵기)을 읽는 줄이 더해졌을 뿐 한 덩어리 배치라 나누면 오히려 읽기 어렵다.
@Composable
private fun BarRow(
    spec: BarRowSpec,
    nav: (@Composable () -> Unit)?,
    titleAlpha: () -> Float,
    titlePlaced: () -> Boolean,
    modifier: Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    val medium = spec.variant == KTopAppBarVariant.Medium
    val center = spec.variant == KTopAppBarVariant.CenterAligned
    val slot = spec.title.slot
    val titleStyle = TextStyle(
        color = spec.surface.contentColor,
        fontSize = 18.sp,
        fontWeight = KineticaTheme.design.title(FontWeight.SemiBold),
    )
    Layout(
        content = {
            Box(Modifier.layoutId(NAV)) { nav?.invoke() }
            Column(
                Modifier
                    .layoutId(TITLE)
                    .graphicsLayer { alpha = titleAlpha() }
                    .then(
                        when {
                            // 슬롯은 큰 제목과 이 줄 가운데 놓인 쪽만 TalkBack에 남긴다. 놓지 않아도 의미 트리에는 남기 때문이다.
                            slot != null -> if (spec.slotHidden) Modifier.clearAndSetSemantics { } else Modifier

                            // Medium의 작은 제목은 큰 제목과 같은 글이라 TalkBack이 두 번 읽지 않게 한다.
                            medium -> Modifier.clearAndSetSemantics { }

                            else -> Modifier.semantics { heading() }
                        },
                    ),
                horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                if (slot != null) {
                    TitleSlot(slot, TitleScope(isLarge = false, textStyle = titleStyle))
                } else {
                    ThemedText(
                        spec.title.text.orEmpty(),
                        style = titleStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                spec.subtitle?.let {
                    ThemedText(
                        it,
                        style = TextStyle(color = spec.surface.muted(SUBTITLE_ALPHA), fontSize = 13.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(Modifier.layoutId(ACTIONS), verticalAlignment = Alignment.CenterVertically, content = actions)
        },
        modifier = modifier.fillMaxWidth().heightIn(min = BAR_HEIGHT).padding(horizontal = EDGE),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val navPlaceable = measurables.first { it.layoutId == NAV }.measure(loose)
        val actionsPlaceable = measurables.first { it.layoutId == ACTIONS }.measure(loose)
        val width = constraints.maxWidth
        val inset = TITLE_INSET.roundToPx()
        val side = maxOf(navPlaceable.width, actionsPlaceable.width)
        val titleMax = if (center) {
            width - side * 2 - inset * 2
        } else {
            width - navPlaceable.width - actionsPlaceable.width - inset * 2
        }.coerceAtLeast(0)
        val titlePlaceable = measurables.first { it.layoutId == TITLE }.measure(loose.copy(maxWidth = titleMax))
        val height = maxOf(constraints.minHeight, navPlaceable.height, actionsPlaceable.height, titlePlaceable.height)
        layout(width, height) {
            navPlaceable.placeRelative(0, (height - navPlaceable.height) / 2)
            actionsPlaceable.placeRelative(width - actionsPlaceable.width, (height - actionsPlaceable.height) / 2)
            val titleX = when {
                center -> (width - titlePlaceable.width) / 2
                navPlaceable.width > 0 -> navPlaceable.width + inset / 2
                else -> inset
            }
            if (titlePlaced()) titlePlaceable.placeRelative(titleX, (height - titlePlaceable.height) / 2)
        }
    }
}

/** Medium 큰 제목 줄의 고정 값. 파라미터 수를 줄이려고 묶었다. */
private class MediumTitleSpec(
    val title: BarTitle,
    val subtitle: String?,
    val surface: SurfaceStyle,
    val state: KTopAppBarState?,
    val insets: WindowInsets,
    val slotHidden: Boolean,
)

/** [placed]는 배치 단계에서 읽는다. 제목 슬롯이 위 줄로 넘어가면 큰 쪽은 놓지 않고 의미도 지워 터치와 TalkBack에서 빠진다. */
@Composable
private fun MediumTitle(spec: MediumTitleSpec, placed: () -> Boolean) {
    val state = spec.state
    val surface = spec.surface
    val slot = spec.title.slot
    val style = TextStyle(color = surface.contentColor, fontSize = 26.sp, fontWeight = KineticaTheme.design.title(FontWeight.SemiBold))
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(spec.insets.only(WindowInsetsSides.Horizontal))
            .onSizeChanged { size -> state?.range = size.height.toFloat() }
            .drawWithContent {
                // 위로 올라가는 제목이 위 줄을 덮지 않게 위쪽만 자른다.
                clipRect(left = -size.width, top = 0f, right = size.width * 2, bottom = size.height * 2) {
                    this@drawWithContent.drawContent()
                }
            }
            .graphicsLayer {
                val offset = state?.offset?.coerceAtMost(state.range) ?: 0f
                translationY = -offset
                alpha = 1f - (state?.collapsedFraction ?: 0f) * TITLE_FADE
            }
            .padding(start = EDGE + TITLE_INSET, end = EDGE + TITLE_INSET, bottom = 14.dp, top = 2.dp),
    ) {
        if (slot != null) {
            val quiet = if (spec.slotHidden) Modifier.clearAndSetSemantics { } else Modifier
            Box(Modifier.placedWhen(placed).then(quiet)) { TitleSlot(slot, TitleScope(isLarge = true, textStyle = style)) }
        } else {
            ThemedText(
                spec.title.text.orEmpty(),
                Modifier.semantics { heading() },
                style = style,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        spec.subtitle?.let {
            ThemedText(
                it,
                Modifier.padding(top = 2.dp),
                style = TextStyle(color = surface.muted(SUBTITLE_ALPHA), fontSize = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 제목 슬롯. 묶은 노드 하나로 제목(heading)이 되게 해 글자만 넣은 슬롯도 String 제목처럼 읽힌다.
 * 안쪽 글자는 [LocalKineticaContentColor]로 바 글자 색을 받는다.
 */
@Composable
private fun TitleSlot(slot: @Composable KTopAppBarTitleScope.() -> Unit, scope: KTopAppBarTitleScope) {
    Box(Modifier.semantics(mergeDescendants = true) { heading() }) {
        CompositionLocalProvider(LocalKineticaContentColor provides scope.textStyle.color) { scope.slot() }
    }
}

/** 크기는 재되 [placed]가 false면 놓지 않는다. 놓지 않은 내용은 그려지지 않고 터치도 받지 않는다. 의미 트리에는 남으므로 따로 지운다. */
private fun Modifier.placedWhen(placed: () -> Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { if (placed()) placeable.place(0, 0) }
}

/** 앱 바 시작 쪽 뒤로 버튼. 화살표는 RTL에서 뒤집힌다. */
@Composable
internal fun BackIconButton(surface: SurfaceStyle, onBack: () -> Unit) {
    KIconButton(
        KGlyphs.ArrowBack,
        stringResource(R.string.kinetica_ui_large_title_back),
        onBack,
        variant = KVariant.Text,
        tone = KTone.Neutral,
        surface = surface,
    )
}

private const val NAV = "nav"
private const val TITLE = "title"
private const val ACTIONS = "actions"
private val BAR_HEIGHT = 56.dp
private val EDGE = 4.dp
private val TITLE_INSET = 12.dp
private val FADE_DISTANCE = 24.dp
private val LINE = 0.5.dp
private const val LINE_ALPHA = 0.12f
private const val SUBTITLE_ALPHA = 0.6f
private const val LEVEL_STEPS = 20f
private const val SMALL_TITLE_START = 0.7f
private const val MEDIUM_FADE_START = 0.6f
private const val TITLE_FADE = 1.3f
