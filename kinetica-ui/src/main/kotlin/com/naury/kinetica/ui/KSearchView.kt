package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** [KSearchView]의 제안과 최근 검색 목록, 그 동작 */
private class SearchLists(
    val suggestions: List<String>,
    val recent: List<String>,
    val onRemoveRecent: ((String) -> Unit)?,
    val onClearRecent: (() -> Unit)?,
)

/**
 * 누르면 펼쳐지는 검색 화면. 접혀 있을 때는 캡슐 검색창 모양이고, 누르면 그 캡슐이 커지며 검색 화면이 된다.
 * 폰처럼 좁은 창에서는 화면 전체를, 600dp 이상 넓은 창에서는 캡슐 자리에서 아래로 펼쳐지는 판(최대 720dp)을 쓴다.
 * 펼쳐지면 검색어 칸에 초점이 가고 키보드가 올라온다.
 *
 * 검색어가 비어 있으면 [recent]를, 글자가 있으면 [suggestions]를 보여 준다. 제안에서 검색어와 같은 부분은 굵게 쓴다.
 * 최근 검색이나 제안을 누르면 [onQueryChange]와 [onSearch]를 차례로 부르고, 제안 끝의 화살표를 누르면 검색하지 않고 검색어 칸에만 채운다.
 * 키보드의 검색 키를 누르면 [onSearch]를 부르고 키보드를 내린다. 화면을 닫을지는 호스트가 [onExpandedChange]로 정한다.
 * 뒤로 버튼이나 시스템 뒤로 가기를 하면 `onExpandedChange(false)`를 부른다. '애니메이션 삭제' 설정에서는 바로 펼치고 접는다.
 *
 * [KineticaScaffold] 안에서는 오버레이 레이어에 그려 글래스 표면에 화면이 비치고, 밖에서는 전체 화면 대화 상자 창에 띄운다.
 *
 * ```kotlin
 * KSearchView(open, { open = it }, query, { query = it }, onSearch = ::search, suggestions = suggest(query), recent = history)
 * ```
 *
 * @param onRemoveRecent null이 아니면 최근 검색 줄 끝에 지우기 버튼을 놓는다.
 * @param onClearRecent null이 아니면 최근 검색 제목 줄에 '모두 지우기'를 놓는다.
 * @param placeholder 검색어가 비어 있을 때 보이는 안내 글
 * @param content 제안 아래에 이어 그릴 내용(검색 결과 미리 보기 등). 판 안에서 함께 스크롤된다.
 */
@Composable
public fun KSearchView(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    suggestions: List<String> = emptyList(),
    recent: List<String> = emptyList(),
    onRemoveRecent: ((String) -> Unit)? = null,
    onClearRecent: (() -> Unit)? = null,
    placeholder: String = stringResource(R.string.kinetica_ui_search),
    surface: SurfaceStyle = KineticaTheme.surface,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var bar by remember { mutableStateOf(Rect.Zero) }
    val ink = surface.contentColor
    Row(
        modifier
            .heightIn(min = BAR_HEIGHT)
            .onGloballyPositioned { bar = it.boundsOnScreen() }
            .kSurface(surface.forField(), CircleShape)
            .clip(CircleShape)
            .kClickable(CircleShape, onClickLabel = placeholder) { onExpandedChange(true) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(KGlyphs.Search, ink.copy(alpha = FIELD_HINT_ALPHA), size = 20.dp)
        Spacer(Modifier.width(10.dp))
        ThemedText(
            query.ifEmpty { placeholder },
            style = TextStyle(color = if (query.isEmpty()) ink.copy(alpha = FIELD_HINT_ALPHA) else ink, fontSize = 17.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val wide = !rememberKWindowLayout().isCompact
    val lists = SearchLists(suggestions, recent, onRemoveRecent, onClearRecent)
    val latestChange by rememberUpdatedState(onExpandedChange)
    KOverlay(expanded) { transition ->
        val still = reducedMotion()
        val progress by transition.animateFloat(
            transitionSpec = {
                when {
                    still -> tween(0)
                    targetState -> spring(dampingRatio = OPEN_DAMPING, stiffness = OPEN_STIFFNESS)
                    else -> tween(CLOSE_MS)
                }
            },
            label = "search-view",
        ) { if (it) 1f else 0f }
        val keyboard = LocalSoftwareKeyboardController.current
        val close = {
            keyboard?.hide()
            latestChange(false)
        }
        BackHandler(enabled = transition.targetState, onBack = close)
        SearchLayer(
            SearchFrame(bar, wide, surface, placeholder),
            progress,
            SearchField(query, onQueryChange, onSearch, close),
            lists,
            content,
        )
    }
}

/** 펼친 검색 화면의 고정 값. [bar]는 접힌 캡슐의 화면 좌표(px)다. */
private class SearchFrame(val bar: Rect, val wide: Boolean, val surface: SurfaceStyle, val placeholder: String)

/** 검색어 칸 값과 동작 */
private class SearchField(val query: String, val onQueryChange: (String) -> Unit, val onSearch: (String) -> Unit, val onClose: () -> Unit)

@Composable
private fun SearchLayer(
    frame: SearchFrame,
    progress: Float,
    field: SearchField,
    lists: SearchLists,
    content: (@Composable ColumnScope.() -> Unit)?,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val level = progress.coerceIn(0f, 1f)
    val density = LocalDensity.current
    val title = stringResource(R.string.kinetica_ui_search)
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() }) {
        if (frame.wide) Scrim(level, field.onClose)
        val from = frame.bar.translate(-origin)
        val corner = if (frame.wide) DOCKED_CORNER else 0.dp
        val barCorner = with(density) { (from.height / 2).toDp() }
        // 판 배경만 캡슐 자리에서 목표 자리로 커진다. 내용은 목표 크기로 한 번 배치해 두고 나타나기만 한다.
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val goal = targetRect(from, constraints, frame.wide, this)
                    val rect = if (from.isEmpty) goal else lerp(from, goal, progress)
                    val placeable = measurable.measure(
                        Constraints.fixed(rect.width.roundToInt().coerceAtLeast(0), rect.height.roundToInt().coerceAtLeast(0)),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(rect.left.roundToInt(), rect.top.roundToInt()) }
                }
                .kSurface(frame.surface.forSearchPanel(frame.wide), RoundedCornerShape(lerpDp(barCorner, corner, level))),
        )
        Column(
            Modifier
                .layout { measurable, constraints ->
                    val goal = targetRect(from, constraints, frame.wide, this)
                    val placeable = measurable.measure(Constraints.fixed(goal.width.roundToInt(), goal.height.roundToInt()))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(goal.left.roundToInt(), goal.top.roundToInt()) }
                }
                .graphicsLayer {
                    alpha = ((progress - CONTENT_START) / (1f - CONTENT_START)).coerceIn(0f, 1f)
                    translationY = (1f - level) * CONTENT_RISE.toPx()
                }
                .semantics { paneTitle = title },
        ) {
            CompositionLocalProvider(LocalKineticaContentColor provides frame.surface.contentColor, LocalGlassLayered provides true) {
                SearchTopRow(frame, field)
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(frame.surface.contentColor.copy(alpha = LINE_ALPHA)))
                Column(Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                    SearchSuggestions(field, lists, frame.surface)
                    content?.invoke(this)
                }
            }
        }
    }
}

/** 펼친 판의 목표 자리. 좁은 창은 화면 전체, 넓은 창은 캡슐 위치에서 아래로 내려오는 판이다. */
private fun targetRect(bar: Rect, constraints: Constraints, wide: Boolean, density: androidx.compose.ui.unit.Density): Rect {
    val width = constraints.maxWidth.toFloat()
    val height = constraints.maxHeight.toFloat()
    if (!wide || bar.isEmpty) return Rect(0f, 0f, width, height)
    with(density) {
        val panelWidth = maxOf(bar.width, DOCKED_MIN_WIDTH.toPx()).coerceAtMost(minOf(DOCKED_MAX_WIDTH.toPx(), width - EDGE.toPx() * 2))
        val left = bar.left.coerceIn(EDGE.toPx(), maxOf(EDGE.toPx(), width - EDGE.toPx() - panelWidth))
        val panelHeight = height * DOCKED_HEIGHT
        // 캡슐이 화면 아래쪽에 있으면 판이 들어가도록 위로 올려 펼친다.
        val top = bar.top.coerceIn(0f, maxOf(0f, height - EDGE.toPx() - panelHeight))
        return Rect(left, top, left + panelWidth, top + panelHeight)
    }
}

/**
 * 검색 판 표면. 화면 전체를 덮는 글래스는 뒤 화면 글자가 비쳐 제안 목록을 읽기 어려우므로 블러와 색을 더 진하게 한다.
 */
private fun SurfaceStyle.forSearchPanel(wide: Boolean): SurfaceStyle {
    val popup = forPopup()
    if (wide || popup !is SurfaceStyle.Glass) return popup
    val style = popup.style
    return SurfaceStyle.Glass(
        style.copy(blur = maxOf(style.blur, FULL_BLUR), tint = style.tint.copy(alpha = maxOf(style.tint.alpha, FULL_TINT_ALPHA))),
    )
}

private fun lerpDp(a: Dp, b: Dp, t: Float): Dp = a + (b - a) * t

@Composable
private fun SearchTopRow(frame: SearchFrame, field: SearchField) {
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val surface = frame.surface
    val accent = KineticaTheme.colors.accent
    LaunchedEffect(Unit) {
        withFrameNanos { }
        requester.requestFocus()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (frame.wide) {
                    Modifier
                } else {
                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                },
            )
            .heightIn(min = TOP_ROW_HEIGHT)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KIconButton(
            KGlyphs.ArrowBack,
            stringResource(R.string.kinetica_ui_search_close),
            field.onClose,
            variant = KVariant.Text,
            tone = KTone.Neutral,
            surface = surface,
        )
        Box(Modifier.weight(1f).padding(start = 4.dp), contentAlignment = Alignment.CenterStart) {
            if (field.query.isEmpty()) {
                ThemedText(
                    frame.placeholder,
                    style = TextStyle(color = surface.contentColor.copy(alpha = FIELD_HINT_ALPHA), fontSize = 18.sp),
                    maxLines = 1,
                )
            }
            ProvideFieldSelection(accent) {
                BasicTextField(
                    field.query,
                    field.onQueryChange,
                    Modifier.fillMaxWidth().focusRequester(requester),
                    singleLine = true,
                    textStyle = themed(TextStyle(color = surface.contentColor, fontSize = 18.sp)),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        field.onSearch(field.query)
                        keyboard?.hide()
                    }),
                )
            }
        }
        FieldClearButton(field.query.isNotEmpty(), surface.contentColor) { field.onQueryChange("") }
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun SearchSuggestions(field: SearchField, lists: SearchLists, surface: SurfaceStyle) {
    val ink = surface.contentColor
    val pick: (String) -> Unit = {
        field.onQueryChange(it)
        field.onSearch(it)
    }
    if (field.query.isEmpty()) {
        if (lists.recent.isEmpty()) return
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ThemedText(
                stringResource(R.string.kinetica_ui_search_recent),
                Modifier.weight(1f).semantics { heading() },
                style = TextStyle(color = ink.copy(alpha = SECTION_ALPHA), fontSize = 13.sp),
            )
            lists.onClearRecent?.let {
                KButton(
                    stringResource(R.string.kinetica_ui_search_clear_recent),
                    it,
                    variant = KVariant.Text,
                    size = KButtonSize.Small,
                    surface = surface,
                )
            }
        }
        lists.recent.forEach { text ->
            val remove = lists.onRemoveRecent
            SearchRow(text, AnnotatedString(text), KGlyphs.Clock, ink, { pick(text) }) {
                if (remove != null) {
                    TrailingButton(KGlyphs.Close, stringResource(R.string.kinetica_ui_search_remove_recent, text), ink) { remove(text) }
                }
            }
        }
    } else {
        lists.suggestions.forEach { text ->
            SearchRow(text, emphasize(text, field.query), KGlyphs.Search, ink, { pick(text) }) {
                TrailingButton(KGlyphs.ArrowInsert, stringResource(R.string.kinetica_ui_search_fill, text), ink) {
                    field.onQueryChange(text)
                }
            }
        }
    }
}

@Composable
private fun SearchRow(
    label: String,
    text: AnnotatedString,
    icon: ImageVector,
    ink: Color,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .heightIn(min = KMetrics.Touch)
            .kClickable(RowShape, onClickLabel = label, onClick = onClick)
            .padding(start = 14.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(icon, ink.copy(alpha = ICON_ALPHA))
        BasicText(
            text,
            Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp),
            style = themed(TextStyle(color = ink, fontSize = 16.sp)),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}

@Composable
private fun TrailingButton(icon: ImageVector, label: String, ink: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(KMetrics.MinTouch)
            .kClickable(CircleShape, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, ink.copy(alpha = ICON_ALPHA), size = 18.dp) }
}

private val BAR_HEIGHT = 48.dp
private val TOP_ROW_HEIGHT = 60.dp
private val DOCKED_CORNER = 28.dp
private val DOCKED_MIN_WIDTH = 360.dp
private val DOCKED_MAX_WIDTH = 720.dp
private val EDGE = 16.dp
private val CONTENT_RISE = 12.dp
private val RowShape = RoundedCornerShape(12.dp)
private const val DOCKED_HEIGHT = 0.7f
private const val FULL_TINT_ALPHA = 0.62f
private val FULL_BLUR = 48.dp
private const val CONTENT_START = 0.35f
private const val OPEN_DAMPING = 0.86f
private const val OPEN_STIFFNESS = 420f
private const val CLOSE_MS = 200
private const val LINE_ALPHA = 0.1f
private const val SECTION_ALPHA = 0.6f
private const val ICON_ALPHA = 0.55f
