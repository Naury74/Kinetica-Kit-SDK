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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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
    val medium = variant == KTopAppBarVariant.Medium
    if (state != null) {
        state.fadeDistance = with(LocalDensity.current) { FADE_DISTANCE.toPx() }
        if (!medium) state.range = 0f
    }
    // 배경은 몇 단계로만 바꿔, 스크롤하는 동안 바를 매 프레임 다시 그리지 않게 한다.
    val level by remember(state) {
        derivedStateOf { if (state == null) 1f else (state.overlappedFraction * LEVEL_STEPS).roundToInt() / LEVEL_STEPS }
    }
    var rowHeight by remember { mutableIntStateOf(0) }
    val nav: (@Composable () -> Unit)? = navigationIcon ?: onBack?.let { back -> { BackIconButton(surface, back) } }
    Box(modifier.fillMaxWidth()) {
        if (level > 0f) BarFill(surface, level) { rowHeight }
        Column(Modifier.fillMaxWidth()) {
            BarRow(
                BarRowSpec(title, if (medium) null else subtitle, variant, surface),
                nav,
                { if (medium) state?.let { smallTitleAlpha(it.collapsedFraction) } ?: 0f else 1f },
                Modifier.onSizeChanged { rowHeight = it.height }.windowInsetsPadding(windowInsets),
                actions,
            )
            if (medium) MediumTitle(title, subtitle, surface, state, windowInsets)
        }
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
private class BarRowSpec(val title: String, val subtitle: String?, val variant: KTopAppBarVariant, val surface: SurfaceStyle)

/**
 * 시작 버튼, 제목, 끝 버튼을 한 줄에 놓는다. 가운데 정렬이면 제목을 바 가운데에 두되 양쪽 버튼과 겹치지 않게 폭을 줄인다.
 * [titleAlpha]는 그리기 단계에서 읽는다.
 */
@Composable
private fun BarRow(
    spec: BarRowSpec,
    nav: (@Composable () -> Unit)?,
    titleAlpha: () -> Float,
    modifier: Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    val medium = spec.variant == KTopAppBarVariant.Medium
    val center = spec.variant == KTopAppBarVariant.CenterAligned
    Layout(
        content = {
            Box(Modifier.layoutId(NAV)) { nav?.invoke() }
            Column(
                Modifier
                    .layoutId(TITLE)
                    .graphicsLayer { alpha = titleAlpha() }
                    // Medium의 작은 제목은 큰 제목과 같은 글이라 TalkBack이 두 번 읽지 않게 한다.
                    .then(if (medium) Modifier.clearAndSetSemantics { } else Modifier.semantics { heading() }),
                horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                ThemedText(
                    spec.title,
                    style = TextStyle(color = spec.surface.contentColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                spec.subtitle?.let {
                    ThemedText(
                        it,
                        style = TextStyle(color = spec.surface.contentColor.copy(alpha = SUBTITLE_ALPHA), fontSize = 13.sp),
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
            titlePlaceable.placeRelative(titleX, (height - titlePlaceable.height) / 2)
        }
    }
}

@Composable
private fun MediumTitle(title: String, subtitle: String?, surface: SurfaceStyle, state: KTopAppBarState?, insets: WindowInsets) {
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal))
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
        ThemedText(
            title,
            Modifier.semantics { heading() },
            style = TextStyle(color = surface.contentColor, fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            ThemedText(
                it,
                Modifier.padding(top = 2.dp),
                style = TextStyle(color = surface.contentColor.copy(alpha = SUBTITLE_ALPHA), fontSize = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
