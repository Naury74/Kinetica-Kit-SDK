package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 입력란 모양의 선택 상자. 누르면 상자 바로 아래(공간이 없으면 위)에 상자와 같은 폭의 목록이 펼쳐지고, 고른 항목에 체크 표시가 붙는다.
 * 항목을 누르면 [onSelect]를 부르고 목록을 닫는다. 바깥을 누르거나 뒤로 가기를 하면 고르지 않고 닫는다.
 * 열려 있는 동안 상자 둘레에 입력란 포커스와 같은 강조색 테두리가 보이고, 끝의 꺾쇠가 위로 돌아간다.
 *
 * TalkBack은 [label]과 고른 값을 읽고, 아무것도 고르지 않았으면 '선택 안 됨'이라고 알린다.
 *
 * ```kotlin
 * KSelect(listOf("서울", "부산", "제주"), city, { city = it }, label = "지역")
 * ```
 *
 * @param options 고를 항목. 비어 있으면 [IllegalArgumentException]을 던진다.
 * @param selectedIndex 고른 항목 위치(0부터). 범위 밖이면(-1 등) 고르지 않은 상태로 그린다.
 * @param label 상자 이름. 고른 값이 없으면 상자 가운데에, 있으면 값 위에 작게 보인다.
 * @param placeholder 고른 값이 없을 때 [label] 대신 보일 안내 글. null이면 [label]을 쓴다.
 * @param enabled false면 흐리게 그리고 열 수 없다.
 */
@Composable
public fun KSelect(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(options.isNotEmpty()) { "options가 비어 있으면 안 됩니다." }
    var open by remember { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(0) }
    val levels = remember { FieldLevels(focused = false, error = false) }
    LaunchedEffect(open) { levels.focusTo(open) }
    val turn by animateFloatAsState(if (open) HALF_TURN else 0f, label = "select-chevron")
    val accent = KineticaTheme.colors.accent
    val selected = options.getOrNull(selectedIndex)
    val none = stringResource(R.string.kinetica_ui_select_none)
    val openLabel = stringResource(R.string.kinetica_ui_select_open)
    val anchor = remember { PopupAnchor() }
    val frame = remember { AnchoredFrame(focusable = true, onDismiss = { open = false }) }
    val ink = surface.contentColor
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { width = it.width }
                .heightIn(min = KineticaTheme.design.density.height(SELECT_HEIGHT))
                .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
                .kSurface(surface.forField(), SelectShape)
                .fieldFocusFrame(SelectShape, levels, accent, accent)
                .clip(SelectShape)
                .clickable(null, KIndication(SelectShape), enabled, openLabel, Role.DropdownList) { open = true }
                .semantics { stateDescription = selected ?: none }
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectValue(label, selected, placeholder, surface, Modifier.weight(1f))
            Glyph(KGlyphs.ChevronDown, surface.muted(LABEL_ALPHA), Modifier.graphicsLayer { rotationZ = turn })
        }
        val widthDp = with(LocalDensity.current) { width.toDp() }
        AnchoredPopup(open, anchor, frame) { transition, geometry ->
            PopupPanel(transition, geometry, surface, Modifier.width(widthDp)) {
                SelectOptions(options, selectedIndex, ink) { index ->
                    if (transition.targetState) {
                        open = false
                        onSelect(index)
                    }
                }
            }
        }
    }
}

/** 선택 상자 안 글자. 고른 값이 있으면 작은 이름 아래에 값을, 없으면 안내 글을 쓴다. */
@Composable
private fun SelectValue(label: String, selected: String?, placeholder: String?, surface: SurfaceStyle, modifier: Modifier) {
    val ink = surface.contentColor
    Column(modifier) {
        if (selected != null) {
            ThemedText(label, style = TextStyle(color = surface.muted(LABEL_ALPHA), fontSize = 12.sp), maxLines = 1)
            ThemedText(selected, style = TextStyle(color = ink, fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            ThemedText(
                placeholder ?: label,
                style = TextStyle(color = surface.muted(FIELD_HINT_ALPHA), fontSize = 16.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SelectOptions(options: List<String>, selectedIndex: Int, ink: Color, onPick: (Int) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        options.forEachIndexed { index, option ->
            MenuRow(
                MenuRowSpec(option, null, null, checked = index == selectedIndex),
                ink,
                Modifier.kSelectable(index == selectedIndex, MenuItemShape, role = Role.RadioButton) { onPick(index) },
            )
        }
    }
}

/** 자동 완성의 키보드 선택 위치와, 닫아 둔 검색어. 같은 검색어에서는 목록을 다시 띄우지 않는다. */
@Stable
private class AutocompleteState {
    var highlight by mutableIntStateOf(-1)
    var closedFor by mutableStateOf<String?>(null)

    /** 아래위 방향키로 고를 줄을 옮기고, Enter로 고르고, Esc로 닫는다. 처리했으면 true다. */
    fun onKey(event: KeyEvent, suggestions: List<String>, query: String, visible: Boolean, pick: (String) -> Unit): Boolean {
        if (event.type != KeyEventType.KeyDown || suggestions.isEmpty()) return false
        return when (event.key) {
            Key.DirectionDown -> {
                closedFor = null
                highlight = (highlight + 1) % suggestions.size
                true
            }

            Key.DirectionUp -> {
                highlight = if (highlight <= 0) suggestions.lastIndex else highlight - 1
                true
            }

            Key.Enter, Key.NumPadEnter -> picked(suggestions, visible)?.let(pick) != null

            Key.Escape -> visible.also { if (it) closedFor = query }

            else -> false
        }
    }

    /** 키보드로 고른 제안. 목록이 안 보이거나 고른 줄이 없으면 null이다. */
    fun picked(suggestions: List<String>, visible: Boolean): String? = suggestions.getOrNull(highlight)?.takeIf { visible }
}

/**
 * 글자를 치면 아래에 제안이 뜨는 입력란(콤보 상자). 제안 목록은 호스트가 [query]로 걸러 [suggestions]에 넘긴다.
 * 입력란에 초점이 있고 제안이 있을 때만 목록이 보이며, 제안에서 [query]와 같은 부분을 굵게 표시한다.
 * 목록이 떠 있어도 초점과 키보드는 입력란에 남아 계속 칠 수 있다. 키보드가 올라와 아래 공간이 모자라면 입력란 위로 뜬다.
 *
 * 제안을 누르거나, 아래 방향키로 고른 뒤 Enter를 누르면 [onSuggestionSelect]를 부르고 목록을 닫는다. 보통 그 안에서 [query]를
 * 고른 값으로 바꾼다. Esc를 누르면 다음 글자를 칠 때까지 목록을 닫는다. TalkBack은 제안 수가 바뀔 때 알린다.
 *
 * ```kotlin
 * KAutocomplete(query, { query = it }, cities.filter { it.contains(query) }, { query = it }, label = "도시")
 * ```
 *
 * @param suggestions 지금 보일 제안. 비어 있으면 목록을 띄우지 않는다.
 * @param placeholder 초점을 받았는데 비어 있을 때 보이는 예시 글
 * @param enabled false면 입력과 제안을 받지 않고 흐리게 그린다.
 */
@Composable
public fun KAutocomplete(
    query: String,
    onQueryChange: (String) -> Unit,
    suggestions: List<String>,
    onSuggestionSelect: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    var focused by remember { mutableStateOf(false) }
    val state = remember { AutocompleteState() }
    var width by remember { mutableIntStateOf(0) }
    LaunchedEffect(suggestions) { state.highlight = -1 }
    val visible = focused && enabled && suggestions.isNotEmpty() && state.closedFor != query
    val pick: (String) -> Unit = { value ->
        // 고른 값으로 검색어가 바뀌어도 같은 목록이 다시 뜨지 않게, 고른 값에서는 닫힌 채로 둔다.
        state.closedFor = value
        state.highlight = -1
        onSuggestionSelect(value)
    }
    val anchor = remember { PopupAnchor() }
    val frame = remember { AnchoredFrame(focusable = false, onDismiss = {}) }
    Box(modifier.onSizeChanged { width = it.width }) {
        KTextField(
            query,
            onQueryChange,
            label,
            Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.hasFocus }
                .onPreviewKeyEvent { state.onKey(it, suggestions, query, visible, pick) },
            enabled = enabled,
            surface = surface,
            placeholder = placeholder,
            keyboardActions = KeyboardActions(onDone = { state.picked(suggestions, visible)?.let(pick) }),
        )
        val widthDp = with(LocalDensity.current) { width.toDp() }
        val announce = stringResource(R.string.kinetica_ui_suggestions, suggestions.size)
        AnchoredPopup(visible, anchor, frame) { transition, geometry ->
            PopupPanel(transition, geometry, surface, Modifier.width(widthDp)) {
                Box(
                    Modifier.size(0.dp).semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = announce
                    },
                )
                Column(Modifier.heightIn(max = SUGGESTIONS_MAX_HEIGHT).verticalScroll(rememberScrollState())) {
                    suggestions.forEachIndexed { index, suggestion ->
                        SuggestionRow(suggestion, query, index == state.highlight, surface) {
                            if (transition.targetState) pick(suggestion)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(text: String, query: String, highlighted: Boolean, surface: SurfaceStyle, onClick: () -> Unit) {
    val ink = surface.contentColor
    val styled = remember(text, query) { emphasize(text, query) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PopupPanelPadding)
            .heightIn(min = KMetrics.MinTouch)
            .clip(MenuItemShape)
            // 키보드로 고른 줄을 옅게 칠한다.
            .then(if (highlighted) Modifier.background(ink.copy(alpha = HIGHLIGHT_ALPHA)) else Modifier)
            .kClickable(MenuItemShape, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(KGlyphs.Search, surface.muted(LABEL_ALPHA), size = 18.dp)
        BasicText(
            styled,
            Modifier.padding(start = 12.dp).weight(1f),
            style = themed(TextStyle(color = ink, fontSize = 16.sp)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** [text]에서 [query]와 같은 부분(대소문자 무시)을 굵게 만든다. 없으면 그대로 둔다. */
internal fun emphasize(text: String, query: String): AnnotatedString {
    val start = if (query.isBlank()) -1 else text.indexOf(query.trim(), ignoreCase = true)
    if (start < 0) return AnnotatedString(text)
    val end = start + query.trim().length
    return buildAnnotatedString {
        append(text.substring(0, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(start, end)) }
        append(text.substring(end))
    }
}

private val SelectShape: Shape
    @Composable
    @ReadOnlyComposable
    get() = fieldShape
private val SELECT_HEIGHT = 52.dp
private val SUGGESTIONS_MAX_HEIGHT = 280.dp
private const val LABEL_ALPHA = 0.6f
private const val DISABLED_ALPHA = 0.4f
private const val HIGHLIGHT_ALPHA = 0.08f
private const val HALF_TURN = 180f
