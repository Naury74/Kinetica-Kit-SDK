package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 판이 열려 있는 동안의 검색어와 고른 줄. 판을 닫으면 버리므로 다음에 열 때 검색어가 비어 있다.
 * [rows]는 구성 때마다 [PalettePanel]이 새로 넣고, 키 처리는 마지막으로 구성한 줄을 쓴다.
 */
@Stable
private class PaletteController(var state: KCommandPaletteState) {
    var query by mutableStateOf("")
        private set
    private var picked by mutableIntStateOf(-1)
    var rows: List<PaletteRow> = emptyList()

    /** 고른 줄 번호. 고른 줄이 없거나 실행할 수 없게 되면 첫 명령이다. 명령이 하나도 없으면 -1이다. */
    val selected: Int
        get() = if (picked in rows.indices && rows[picked].isSelectable()) picked else nextSelectable(rows, -1, 1)

    fun updateQuery(text: String) {
        query = text
        picked = -1
    }

    fun select(index: Int) {
        picked = index
    }

    fun move(step: Int) {
        picked = nextSelectable(rows, selected, step)
    }

    fun run(command: KCommand) {
        // 닫히는 동안 한 번 더 눌러도 두 번 실행하지 않는다.
        if (!command.enabled || !state.isOpen) return
        state.close()
        state.addRecent(command.id)
        command.onRun()
    }

    fun runSelected() {
        (rows.getOrNull(selected) as? PaletteRow.Item)?.let { run(it.command) }
    }

    fun onKey(event: KeyEvent): Boolean {
        if (state.handleShortcut(event)) return true
        if (event.type != KeyEventType.KeyDown) return false
        when (event.key) {
            Key.DirectionDown -> move(1)
            Key.DirectionUp -> move(-1)
            Key.Enter, Key.NumPadEnter -> runSelected()
            Key.Escape -> state.close()
            else -> return false
        }
        return true
    }
}

/** 명령 판. 넓은 창이면 가운데에 고정 높이로, 좁은 창이면 위쪽에 내용 높이로 놓는다. */
@Composable
internal fun BoxScope.PalettePanel(spec: PaletteSpec, progress: () -> Float, modifier: Modifier) {
    val recentTitle = stringResource(R.string.kinetica_ui_palette_recent)
    val controller = remember { PaletteController(spec.state) }
    controller.state = spec.state
    val query = controller.query
    val rows =
        remember(query, spec.sections, spec.state.recentIds, recentTitle) {
            paletteRows(spec.sections, query, spec.state.recentIds, recentTitle)
        }
    controller.rows = rows
    val resultCount = remember(rows) { rows.filterIsInstance<PaletteRow.Item>().distinctBy { it.command.id }.size }
    val title = stringResource(R.string.kinetica_ui_palette_title)
    val shape = shapeFor { dialog }
    val surface = spec.surface
    val rise = if (spec.wide) PANEL_RISE_WIDE else -PANEL_RISE_COMPACT
    Column(
        Modifier
            .align(if (spec.wide) Alignment.Center else Alignment.TopCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = if (spec.wide) 24.dp else 10.dp, vertical = if (spec.wide) 24.dp else 8.dp)
            .then(modifier)
            .widthIn(max = PANEL_MAX_WIDTH)
            .fillMaxWidth()
            .then(if (spec.wide) Modifier.heightIn(max = PANEL_WIDE_HEIGHT).fillMaxHeight() else Modifier.heightIn(max = PANEL_COMPACT_MAX))
            .graphicsLayer {
                val p = progress()
                alpha = p.coerceIn(0f, 1f)
                val scale = PANEL_FROM_SCALE + (1f - PANEL_FROM_SCALE) * p
                scaleX = scale
                scaleY = scale
                translationY = (1f - p.coerceIn(0f, 1f)) * rise.toPx()
            }
            .kSurface(surface.forPopup(), shape)
            .clip(shape)
            .semantics { paneTitle = title }
            .blockTouches()
            .onPreviewKeyEvent(controller::onKey),
    ) {
        CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor, LocalGlassLayered provides true) {
            PaletteField(controller, spec)
            PaletteDivider(surface)
            if (resultCount == 0) {
                PaletteEmpty(controller.query, spec, Modifier.weight(1f, fill = spec.wide))
            } else {
                PaletteList(controller, rows, controller.selected, surface, Modifier.weight(1f, fill = spec.wide))
            }
            PaletteDivider(surface)
            PaletteFooter(resultCount, spec.wide, surface)
        }
    }
}

@Composable
private fun PaletteField(controller: PaletteController, spec: PaletteSpec) {
    val requester = remember { FocusRequester() }
    val surface = spec.surface
    val accent = KineticaTheme.colors.accent
    LaunchedEffect(Unit) {
        // 창이나 오버레이 레이어에 붙은 다음 프레임에 요청해야 초점이 들어간다.
        withFrameNanos { }
        requester.requestFocus()
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = FIELD_HEIGHT).padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(KGlyphs.Search, surface.muted(MUTED_ALPHA), size = 20.dp)
        Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            ProvideFieldSelection(accent) {
                BasicTextField(
                    controller.query,
                    controller::updateQuery,
                    Modifier.fillMaxWidth().focusRequester(requester),
                    singleLine = true,
                    textStyle = themed(TextStyle(color = surface.contentColor, fontSize = 17.sp)),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { controller.runSelected() }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            FieldNameText(
                                spec.placeholder,
                                controller.query.isEmpty(),
                                TextStyle(color = surface.muted(MUTED_ALPHA), fontSize = 17.sp),
                            )
                            inner()
                        }
                    },
                )
            }
        }
        FieldClearButton(controller.query.isNotEmpty(), surface.contentColor) { controller.updateQuery("") }
    }
}

@Composable
private fun PaletteList(controller: PaletteController, rows: List<PaletteRow>, selected: Int, surface: SurfaceStyle, modifier: Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(selected, rows) { list.reveal(rows, selected) }
    LazyColumn(modifier.fillMaxWidth(), state = list, contentPadding = PaddingValues(vertical = 6.dp)) {
        itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
            when (row) {
                is PaletteRow.Header -> SectionHeader(row.title, surface)

                is PaletteRow.Item -> CommandRow(row, index == selected, surface, {
                    controller.run(row.command)
                }) { controller.select(index) }
            }
        }
    }
}

/** 고른 줄이 보이도록 목록을 조금만 굴린다. 첫 명령이면 그 위 묶음 제목까지 보이게 맨 위로 간다. */
private suspend fun LazyListState.reveal(rows: List<PaletteRow>, index: Int) {
    if (index !in rows.indices) return
    if (rows.subList(0, index).none { it.isSelectable() }) {
        scrollToItem(0)
        return
    }
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (info == null) {
        scrollToItem(index)
        return
    }
    val start = layoutInfo.viewportStartOffset + layoutInfo.beforeContentPadding
    val end = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
    when {
        info.offset < start -> scrollBy((info.offset - start).toFloat())
        info.offset + info.size > end -> scrollBy((info.offset + info.size - end).toFloat())
    }
}

@Composable
private fun SectionHeader(title: String, surface: SurfaceStyle) {
    ThemedText(
        title,
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp).semantics { heading() },
        style = TextStyle(color = surface.muted(SECTION_ALPHA), fontSize = 12.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
private fun CommandRow(item: PaletteRow.Item, selected: Boolean, surface: SurfaceStyle, onRun: () -> Unit, onHover: () -> Unit) {
    val command = item.command
    val ink = surface.contentColor
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onHover() }
    val runLabel = stringResource(R.string.kinetica_ui_palette_hint_run)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .heightIn(min = KMetrics.Touch)
            .clip(RowShape)
            .background(if (selected) surface.raisedFill(SELECTED_ALPHA) else Color.Transparent)
            .kClickable(RowShape, enabled = command.enabled, onClickLabel = runLabel, interactionSource = source, onClick = onRun)
            .semantics { this.selected = selected }
            .graphicsLayer { alpha = if (command.enabled) 1f else DISABLED_ALPHA }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ICON_TILE).background(ink.copy(alpha = TILE_ALPHA), TileShape), contentAlignment = Alignment.Center) {
            command.icon?.let { Glyph(it, surface.muted(ICON_ALPHA), size = 18.dp) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            BasicText(
                highlight(command.title, item.match.ranges, ink),
                style = themed(TextStyle(color = ink.copy(alpha = TITLE_ALPHA), fontSize = 15.sp)),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            command.subtitle?.let {
                ThemedText(
                    it,
                    style = TextStyle(color = surface.muted(MUTED_ALPHA), fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        command.shortcut?.let { Keycaps(it, ink, stringResource(R.string.kinetica_ui_palette_shortcut, it)) }
    }
}

/** 맞은 글자는 굵고 진하게, 나머지는 조금 옅게 둔다. 강조색은 글래스 위에서 대비가 모자랄 수 있어 쓰지 않는다. */
private fun highlight(text: String, ranges: List<IntRange>, ink: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    val strong = SpanStyle(fontWeight = FontWeight.Bold, color = ink)
    ranges.forEach { r -> if (r.first >= 0 && r.last < text.length) addStyle(strong, r.first, r.last + 1) }
}

/** 단축키 글을 키캡으로 나눈다. `"Ctrl+Shift+P"`는 세 개, `"⌘K"`는 하나다. */
internal fun shortcutKeys(shortcut: String): List<String> = shortcut.split('+').map { it.trim() }.filter { it.isNotEmpty() }

/** 단축키 키캡. 키 순서는 RTL에서도 왼쪽부터 읽으므로 방향을 고정한다. TalkBack에는 [label] 한 번으로 읽힌다. */
@Composable
private fun Keycaps(shortcut: String, ink: Color, label: String) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.clearAndSetSemantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            shortcutKeys(shortcut).forEach { Keycap(it, ink) }
        }
    }
}

@Composable
private fun Keycap(text: String, ink: Color) {
    Box(
        Modifier
            .widthIn(min = KEYCAP_MIN)
            .border(1.dp, ink.copy(alpha = KEYCAP_BORDER_ALPHA), KeycapShape)
            .background(ink.copy(alpha = TILE_ALPHA), KeycapShape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        ThemedText(
            text,
            style = TextStyle(color = ink.copy(alpha = KEYCAP_TEXT_ALPHA), fontSize = 12.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

@Composable
private fun PaletteEmpty(query: String, spec: PaletteSpec, modifier: Modifier) {
    val surface = spec.surface
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val custom = spec.empty
        if (custom != null) {
            custom(query)
            return@Box
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph(KGlyphs.Search, surface.muted(EMPTY_ICON_ALPHA), size = 32.dp)
            Spacer(Modifier.height(12.dp))
            ThemedText(
                stringResource(R.string.kinetica_ui_palette_empty),
                Modifier.semantics { heading() },
                style = TextStyle(color = surface.contentColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            )
            if (query.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                ThemedText(
                    stringResource(R.string.kinetica_ui_palette_empty_message, query.trim()),
                    style = TextStyle(color = surface.muted(MUTED_ALPHA), fontSize = 14.sp, textAlign = TextAlign.Center),
                )
            }
        }
    }
}

@Composable
private fun PaletteFooter(count: Int, wide: Boolean, surface: SurfaceStyle) {
    val muted = surface.muted(MUTED_ALPHA)
    val countText = pluralStringResource(R.plurals.kinetica_ui_palette_results, count, count)
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        // 검색어를 칠 때마다 결과 수가 바뀌면 TalkBack이 읽는다. 역할을 주면 알림 앞에 역할 이름이 붙으므로 글자만 둔다.
        ThemedText(
            countText,
            Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            style = TextStyle(color = muted, fontSize = 12.sp),
            maxLines = 1,
        )
        if (wide) {
            // 키보드 안내는 보는 사람용이다. TalkBack은 키 이름만 늘어놓게 되므로 읽지 않는다.
            Row(
                Modifier.clearAndSetSemantics {
                },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FooterHint("↑↓", stringResource(R.string.kinetica_ui_palette_hint_move), surface)
                FooterHint("↵", stringResource(R.string.kinetica_ui_palette_hint_run), surface)
                FooterHint("esc", stringResource(R.string.kinetica_ui_close), surface)
            }
        }
    }
}

@Composable
private fun FooterHint(key: String, label: String, surface: SurfaceStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Keycap(key, surface.contentColor)
        ThemedText(label, style = TextStyle(color = surface.muted(MUTED_ALPHA), fontSize = 12.sp), maxLines = 1)
    }
}

@Composable
private fun PaletteDivider(surface: SurfaceStyle) {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(surface.contentColor.copy(alpha = LINE_ALPHA)))
}

private val PANEL_MAX_WIDTH = 640.dp
private val PANEL_WIDE_HEIGHT = 460.dp
private val PANEL_COMPACT_MAX = 560.dp
private val PANEL_RISE_WIDE = 10.dp
private val PANEL_RISE_COMPACT = 16.dp
private val FIELD_HEIGHT = 56.dp
private val ICON_TILE = 32.dp
private val KEYCAP_MIN = 22.dp
private val RowShape = RoundedCornerShape(12.dp)
private val TileShape = RoundedCornerShape(8.dp)
private val KeycapShape = RoundedCornerShape(6.dp)
private const val PANEL_FROM_SCALE = 0.96f
private const val MUTED_ALPHA = 0.6f
private const val SECTION_ALPHA = 0.55f
private const val ICON_ALPHA = 0.75f
private const val TITLE_ALPHA = 0.82f
private const val TILE_ALPHA = 0.06f
private const val SELECTED_ALPHA = 0.1f
private const val DISABLED_ALPHA = 0.4f
private const val KEYCAP_BORDER_ALPHA = 0.18f
private const val KEYCAP_TEXT_ALPHA = 0.7f
private const val EMPTY_ICON_ALPHA = 0.4f
private const val LINE_ALPHA = 0.1f
