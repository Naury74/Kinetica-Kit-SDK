package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/**
 * 태그를 입력하는 입력란. 글자를 입력하다 쉼표를 치거나 Enter(완료)를 누르면 입력한 글자가 칩이 된다. 칩의 ×를 누르면 지워지고,
 * 입력란이 비어 있을 때 지우기 키를 누르면 마지막 칩이 선택되었다가 한 번 더 누르면 지워진다. 칩이 많으면 다음 줄로 넘긴다.
 *
 * 이미 있는 태그는 대소문자를 가리지 않고 다시 추가하지 않으며, 대신 그 칩이 살짝 흔들린다. [suggestions]가 있으면 입력한 글자가
 * 들어간 추천 항목을 입력란 아래에 뜨는 글래스 목록으로 보여 주고, 누르면 바로 추가한다. 목록은 아래 내용을 밀어내지 않고 위에 겹쳐 뜨며,
 * 아래 공간(키보드 위)이 모자라면 입력란 위로 뜬다. [KineticaScaffold] 안에서는 오버레이 레이어에, 밖에서는 팝업 창에 그린다.
 * TalkBack에서 칩마다 삭제 동작을 쓸 수 있다.
 *
 * ```kotlin
 * var tags by remember { mutableStateOf(listOf("디자인")) }
 * KTagInput(tags, { tags = it }, placeholder = "태그 추가", suggestions = listOf("개발", "디자인", "기획"))
 * ```
 *
 * @param tags 지금 태그 목록. 순서대로 칩을 그린다.
 * @param onTagsChange 태그를 추가하거나 지운 새 목록을 받는다. 추가한 태그는 앞뒤 공백을 지운 값이다.
 * @param placeholder 태그가 하나도 없고 입력란이 비어 있을 때 보이는 안내 문구이자 TalkBack이 읽는 입력란 이름. 비어 있으면 이름은 '태그 추가'다.
 * @param suggestions 추천 태그. 입력한 글자로 시작하는 항목을 먼저, 글자가 들어간 항목을 그다음에 최대 5개 보여 준다.
 *   이미 추가한 태그는 빠진다.
 * @param imeAction 키보드 동작 키의 종류. 입력 중인 글자가 있으면 동작 키는 그 글자를 칩으로 만들고 입력란에 머문다.
 *   입력란이 비어 있을 때 누르면 [keyboardActions]로 넘기므로, [ImeAction.Next]로 두면 칩을 다 넣은 뒤 한 번 더 눌러 다음 입력란으로 갈 수 있다.
 * @param keyboardActions 빈 입력란에서 동작 키를 눌렀을 때 할 일. [imeAction]에 맞는 콜백이 없으면 Next는 다음 입력란으로 포커스를 옮기고
 *   Done은 키보드를 내리는 기본 동작을 한다.
 * @param fieldModifier 안쪽 입력란에 붙일 Modifier. `testTag`나 `focusRequester`처럼 입력란 자체에 걸어야 하는 것을 여기에 준다.
 *   [modifier]는 그대로 칩과 입력란을 감싼 바깥 상자에 붙는다.
 */
@Composable
public fun KTagInput(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    suggestions: List<String> = emptyList(),
    surface: SurfaceStyle = KineticaTheme.surface,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    fieldModifier: Modifier = Modifier,
) {
    val model = remember { TagInputModel() }
    model.tags = tags
    model.onChange = onTagsChange
    val requester = remember { FocusRequester() }
    val focus = remember { Animatable(0f) }
    LaunchedEffect(model.focused) { focus.animateTo(if (model.focused) 1f else 0f) }
    val matches = remember(suggestions, tags, model.text) { matchSuggestions(suggestions, tags, model.text, MAX_SUGGESTIONS) }
    val accent = KineticaTheme.colors.accent
    val anchor = remember { SuggestionAnchor() }
    Box(modifier.keepVisibleWhileFocused(model.focused)) {
        FlowRow(
            Modifier
                .fillMaxWidth()
                .heightIn(min = BOX_MIN_HEIGHT)
                .onGloballyPositioned { anchor.bounds = it.boundsOnScreen() }
                .kSurface(surface.forField(), BOX_SHAPE)
                .focusRing(accent) { focus.value }
                .pointerInput(Unit) { detectTapGestures { requester.requestFocus() } }
                .padding(BOX_PADDING),
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
            verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            tags.forEachIndexed { index, tag ->
                key(tag) {
                    TagChip(tag, TagLook(model.armed && index == tags.lastIndex, accent, surface), model.bumps[tag] ?: 0) {
                        model.remove(tag)
                    }
                }
            }
            TagField(
                model,
                TagFieldLook(placeholder, surface, imeAction, keyboardActions),
                Modifier.weight(1f).focusRequester(requester).then(fieldModifier),
            )
        }
        SuggestionPopup(model.focused && matches.isNotEmpty(), SuggestionContent(matches, model.text, surface, anchor)) { picked ->
            model.add(listOf(picked))
            model.text = ""
        }
    }
}

/** 태그 입력란의 글자, 마지막 칩 선택 여부, 중복으로 흔들 칩을 담고 태그 목록 변경을 처리한다. */
internal class TagInputModel {
    var text by mutableStateOf("")
    var armed by mutableStateOf(false)
    var focused by mutableStateOf(false)
    val bumps = mutableStateMapOf<String, Int>()
    var tags: List<String> = emptyList()
    var onChange: (List<String>) -> Unit = {}

    fun add(parts: List<String>) {
        val result = mergeTags(tags, parts)
        result.duplicates.forEach { existing -> bumps[existing] = (bumps[existing] ?: 0) + 1 }
        if (result.tags != tags) change(result.tags)
    }

    fun remove(tag: String) {
        armed = false
        change(tags - tag)
    }

    // 다음 컴포지션 전에 입력이 또 오면(빠른 키 입력, 붙여 넣은 뒤 바로 완료) 앞 변경을 덮어쓰지 않도록 보낸 목록을 바로 기준으로 삼는다.
    // 호스트가 받아들이지 않으면 다음 컴포지션에서 호스트 값으로 돌아간다.
    private fun change(next: List<String>) {
        tags = next
        onChange(next)
    }

    fun type(next: String) {
        armed = false
        val split = splitTagInput(next)
        if (split.committed.isNotEmpty()) add(split.committed)
        text = split.rest
    }

    /** 빈 입력란에서 지우기 키를 누르면 처음에는 마지막 칩을 고르고, 한 번 더 누르면 지운다. 처리했으면 `true`. */
    fun backspace(): Boolean {
        if (text.isNotEmpty() || tags.isEmpty()) return false
        if (armed) change(tags.dropLast(1))
        armed = !armed
        return true
    }
}

/** 태그 입력란의 안내 문구와 모양, 키보드 동작 설정. */
private class TagFieldLook(val placeholder: String, val surface: SurfaceStyle, val imeAction: ImeAction, val actions: KeyboardActions)

@Composable
private fun TagField(model: TagInputModel, look: TagFieldLook, modifier: Modifier) {
    val placeholder = look.placeholder
    val surface = look.surface
    val onAction: KeyboardActionScope.() -> Unit = {
        if (model.text.isNotBlank()) {
            model.add(listOf(model.text))
            model.text = ""
        } else {
            model.text = ""
            look.actions.callbackFor(look.imeAction)?.invoke(this) ?: defaultKeyboardAction(look.imeAction)
        }
    }
    ProvideFieldSelection(KineticaTheme.colors.accent) { TagTextField(model, look, modifier, onAction) }
}

@Composable
private fun TagTextField(model: TagInputModel, look: TagFieldLook, modifier: Modifier, onAction: KeyboardActionScope.() -> Unit) {
    val placeholder = look.placeholder
    val surface = look.surface
    val name = placeholder.ifEmpty { stringResource(R.string.kinetica_ui_tag_field) }
    BasicTextField(
        model.text,
        model::type,
        modifier
            .widthIn(min = FIELD_MIN_WIDTH)
            .padding(horizontal = FIELD_PADDING)
            .onFocusChanged { model.focused = it.isFocused }
            .onPreviewKeyEvent { event -> event.type == KeyEventType.KeyDown && event.key == Key.Backspace && model.backspace() }
            .fieldName(name),
        singleLine = true,
        textStyle = themed(TextStyle(color = surface.contentColor, fontSize = FIELD_FONT_SIZE)),
        cursorBrush = SolidColor(KineticaTheme.colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = look.imeAction),
        keyboardActions = KeyboardActions(
            onDone = onAction,
            onGo = onAction,
            onNext = onAction,
            onPrevious = onAction,
            onSearch = onAction,
            onSend = onAction,
        ),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (model.text.isEmpty() && model.tags.isEmpty() && placeholder.isNotEmpty()) {
                    ThemedText(
                        placeholder,
                        Modifier.fieldNameText(),
                        style = TextStyle(color = surface.contentColor.copy(alpha = PLACEHOLDER_ALPHA), fontSize = FIELD_FONT_SIZE),
                        maxLines = 1,
                    )
                }
                inner()
            }
        },
    )
}

/** [action]에 해당하는 콜백. 없으면 `null`이다. */
internal fun KeyboardActions.callbackFor(action: ImeAction): (KeyboardActionScope.() -> Unit)? = when (action) {
    ImeAction.Done -> onDone
    ImeAction.Go -> onGo
    ImeAction.Next -> onNext
    ImeAction.Previous -> onPrevious
    ImeAction.Search -> onSearch
    ImeAction.Send -> onSend
    else -> null
}

/** 입력란 글자를 쉼표와 줄 바꿈으로 나눈 결과. [committed]는 칩으로 만들 조각, [rest]는 입력란에 남길 마지막 조각이다. */
internal class TagSplit(val committed: List<String>, val rest: String)

internal fun splitTagInput(text: String): TagSplit {
    val parts = text.split(',', '\n', '，')
    if (parts.size == 1) return TagSplit(emptyList(), text)
    return TagSplit(parts.dropLast(1), parts.last().trimStart())
}

/** [tags]에 [parts]를 더한 결과. 빈 조각은 버리고, 이미 있는 태그(대소문자 무시)는 더하지 않고 [duplicates]에 기존 이름으로 담는다. */
internal class TagMerge(val tags: List<String>, val duplicates: List<String>)

internal fun mergeTags(tags: List<String>, parts: List<String>): TagMerge {
    val result = tags.toMutableList()
    val duplicates = mutableListOf<String>()
    parts.map(String::trim).filter(String::isNotEmpty).forEach { part ->
        val existing = result.firstOrNull { it.equals(part, ignoreCase = true) }
        if (existing == null) result += part else duplicates += existing
    }
    return TagMerge(result, duplicates)
}

/** [query]로 시작하는 추천을 먼저, 들어 있는 추천을 그다음에 [limit]개까지 고른다. 이미 있는 태그와 빈 질의는 제외한다. */
internal fun matchSuggestions(suggestions: List<String>, tags: List<String>, query: String, limit: Int): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val fresh = suggestions.distinct().filter { s -> tags.none { it.equals(s, ignoreCase = true) } }
    val starts = fresh.filter { it.startsWith(q, ignoreCase = true) }
    val contains = fresh.filter { !it.startsWith(q, ignoreCase = true) && it.contains(q, ignoreCase = true) }
    return (starts + contains).take(limit)
}

private class TagLook(val armed: Boolean, val accent: Color, val surface: SurfaceStyle)

@Composable
private fun TagChip(tag: String, look: TagLook, bump: Int, onRemove: () -> Unit) {
    val still = reducedMotion()
    val enter = remember { Animatable(if (still) 1f else 0f) }
    val armedLevel = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = ENTER_DAMPING, stiffness = ENTER_STIFFNESS)) }
    LaunchedEffect(look.armed) { armedLevel.animateTo(if (look.armed) 1f else 0f) }
    val removeLabel = stringResource(R.string.kinetica_ui_tag_remove, tag)
    val armedLabel = stringResource(R.string.kinetica_ui_tag_armed, tag)
    val accent = look.accent
    val onAccent = KineticaTheme.colors.onAccent
    val ink = look.surface.contentColor
    Row(
        Modifier
            .shake(bump, distance = 5.dp)
            .graphicsLayer {
                scaleX = ENTER_FROM + (1f - ENTER_FROM) * enter.value
                scaleY = scaleX
                alpha = enter.value.coerceIn(0f, 1f)
            }
            .heightIn(min = CHIP_HEIGHT)
            .kSurface(look.surface, CircleShape)
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(lerpAlpha(accent, CHIP_FILL, 1f, armedLevel.value), cornerRadius = radius)
            }
            .semantics(mergeDescendants = true) {
                contentDescription = tag
                if (look.armed) {
                    stateDescription = armedLabel
                    liveRegion = LiveRegionMode.Polite
                }
                customActions = listOf(
                    CustomAccessibilityAction(removeLabel) {
                        onRemove()
                        true
                    },
                )
            }
            .padding(start = CHIP_PADDING_START, end = CHIP_PADDING_END),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemedText(
            tag,
            style = TextStyle(fontSize = CHIP_FONT_SIZE, fontWeight = FontWeight.Medium),
            color = { lerp(ink, onAccent, armedLevel.value) },
            maxLines = 1,
        )
        RemoveButton(removeLabel, { lerp(ink, onAccent, armedLevel.value) }, onRemove)
    }
}

private fun lerpAlpha(color: Color, from: Float, to: Float, t: Float): Color = color.copy(alpha = from + (to - from) * t.coerceIn(0f, 1f))

@Composable
private fun RemoveButton(label: String, color: () -> Color, onRemove: () -> Unit) {
    Box(
        Modifier
            .size(REMOVE_TOUCH)
            .clickable(null, KIndication(CircleShape), role = Role.Button, onClick = onRemove)
            .semantics { contentDescription = label }
            .drawWithCache {
                val radius = REMOVE_SIZE.toPx() / 2
                val arm = radius * CROSS_ARM
                val width = CROSS_WIDTH.toPx()
                val center = Offset(size.width / 2, size.height / 2)
                onDrawBehind {
                    val ink = color()
                    drawCircle(ink.copy(alpha = REMOVE_FILL), radius, center)
                    drawLine(ink, center + Offset(-arm, -arm), center + Offset(arm, arm), width, StrokeCap.Round)
                    drawLine(ink, center + Offset(-arm, arm), center + Offset(arm, -arm), width, StrokeCap.Round)
                }
            },
    )
}

/** 추천 목록을 붙일 입력란의 화면 좌표 경계(px). 배치 단계에서만 읽는다. */
@Stable
private class SuggestionAnchor {
    var bounds by mutableStateOf(Rect.Zero)
}

private class SuggestionContent(val items: List<String>, val query: String, val surface: SurfaceStyle, val anchor: SuggestionAnchor)

/**
 * 입력란 아래(모자라면 위)에 추천 목록을 겹쳐 띄운다. 닫히는 동안에는 마지막 목록을 그대로 보인다.
 * 팝업 창은 초점을 가져가지 않으므로 키보드와 입력란 초점이 그대로 남는다.
 */
@Composable
private fun SuggestionPopup(visible: Boolean, content: SuggestionContent, onPick: (String) -> Unit) {
    var last by remember { mutableStateOf(content) }
    if (content.items.isNotEmpty()) last = content
    val hosted = LocalKineticaHost.current != null
    val gap = with(LocalDensity.current) { LIST_GAP.roundToPx() }
    KOverlay(visible, OverlayKind.InPlace) { transition ->
        val progress = transition.animateFloat({ tween(LIST_FADE_MS) }, label = "glass-tag-suggestions") { if (it) 1f else 0f }
        if (hosted) {
            HostedSuggestions(last, gap, { progress.value }, onPick)
        } else {
            val position = remember(gap) { SuggestionPosition(gap) }
            Popup(popupPositionProvider = position, properties = PopupProperties(focusable = false)) {
                val width = with(LocalDensity.current) { last.anchor.bounds.width.toDp() }
                SuggestionPanel(last, Modifier.width(width), { progress.value }, onPick)
            }
        }
    }
}

/** [KineticaScaffold]의 오버레이 레이어 전체를 차지하고 입력란 경계에 맞춰 목록을 놓는다. 빈 곳은 터치를 받지 않는다. */
@Composable
private fun HostedSuggestions(content: SuggestionContent, gap: Int, progress: () -> Float, onPick: (String) -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Layout(
        content = { SuggestionPanel(content, Modifier, progress, onPick) },
        modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() },
    ) { measurables, constraints ->
        val anchor = content.anchor.bounds.translate(-origin)
        val width = anchor.width.roundToInt().coerceIn(0, constraints.maxWidth)
        val panel = measurables.first().measure(Constraints(minWidth = width, maxWidth = width))
        val top = suggestionTop(anchor.top.roundToInt(), anchor.bottom.roundToInt(), constraints.maxHeight, panel.height, gap)
        layout(constraints.maxWidth, constraints.maxHeight) { panel.place(anchor.left.roundToInt(), top) }
    }
}

/** 팝업 창을 입력란 시작 쪽에 맞추고, 아래 공간이 모자라면 위로 올린다. */
private class SuggestionPosition(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset =
        IntOffset(anchorBounds.left, suggestionTop(anchorBounds.top, anchorBounds.bottom, windowSize.height, popupContentSize.height, gap))
}

/** 목록 위쪽 y(px). 아래에 들어가면 아래, 아니면 위에 들어가면 위, 둘 다 안 되면 공간이 넓은 쪽에 놓는다. */
internal fun suggestionTop(anchorTop: Int, anchorBottom: Int, windowHeight: Int, height: Int, gap: Int): Int {
    val below = anchorBottom + gap
    val above = anchorTop - gap - height
    return when {
        below + height <= windowHeight -> below
        above >= 0 -> above
        windowHeight - anchorBottom >= anchorTop -> below
        else -> above
    }
}

@Composable
private fun SuggestionPanel(content: SuggestionContent, modifier: Modifier, progress: () -> Float, onPick: (String) -> Unit) {
    val title = stringResource(R.string.kinetica_ui_tag_suggestions)
    val accent = KineticaTheme.colors.accent
    val surface = content.surface
    Column(
        modifier
            .graphicsLayer {
                val p = progress()
                alpha = p.coerceIn(0f, 1f)
                translationY = -LIST_SLIDE.toPx() * (1f - p)
            }
            .kSurface(surface.forPopup(), LIST_SHAPE)
            .semantics { contentDescription = title }
            .padding(vertical = LIST_PADDING),
    ) {
        content.items.forEach { item ->
            BasicText(
                highlight(item, content.query, accent),
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LIST_PADDING)
                    .clickable(null, SuggestionPress, role = Role.Button) { onPick(item) }
                    .heightIn(min = ROW_HEIGHT)
                    .padding(horizontal = ROW_PADDING, vertical = ROW_VERTICAL_PADDING),
                style = themed(TextStyle(color = surface.contentColor, fontSize = FIELD_FONT_SIZE)),
                maxLines = 1,
            )
        }
    }
}

/** [text]에서 [query]와 맞는 첫 부분을 굵은 강조색으로 표시한다. */
private fun highlight(text: String, query: String, accent: Color): AnnotatedString = buildAnnotatedString {
    val q = query.trim()
    val start = if (q.isEmpty()) -1 else text.indexOf(q, ignoreCase = true)
    if (start < 0) {
        append(text)
        return@buildAnnotatedString
    }
    append(text.substring(0, start))
    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(text.substring(start, start + q.length)) }
    append(text.substring(start + q.length))
}

/** 포커스를 받으면 강조색 테두리를 그린다. 값은 그리기 단계에서 읽는다. */
private fun Modifier.focusRing(accent: Color, level: () -> Float): Modifier = drawWithCache {
    val width = RING_WIDTH.toPx()
    val stroke = Stroke(width)
    val corner = CornerRadius(BOX_CORNER.toPx() - width / 2)
    val inset = Offset(width / 2, width / 2)
    val box = Size(size.width - width, size.height - width)
    onDrawWithContent {
        drawContent()
        val alpha = level()
        if (alpha > 0f) drawRoundRect(accent.copy(alpha = alpha), inset, box, corner, style = stroke)
    }
}

private const val MAX_SUGGESTIONS = 5
private val BOX_SHAPE = RoundedCornerShape(18.dp)
private val BOX_CORNER = 18.dp
private val BOX_MIN_HEIGHT = 56.dp
private val BOX_PADDING = 10.dp
private val RING_WIDTH = 1.5.dp
private val CHIP_GAP = 6.dp
private val CHIP_HEIGHT = 34.dp
private val CHIP_PADDING_START = 12.dp
private val CHIP_PADDING_END = 2.dp
private val CHIP_FONT_SIZE = 14.sp
private val FIELD_FONT_SIZE = 16.sp
private val FIELD_MIN_WIDTH = 96.dp
private val FIELD_PADDING = 6.dp
private val REMOVE_TOUCH = 32.dp
private val REMOVE_SIZE = 18.dp
private val CROSS_WIDTH = 1.6.dp
private val LIST_SHAPE = RoundedCornerShape(16.dp)

// 목록 모서리와 같은 중심을 갖도록 바깥 반지름에서 안쪽 여백을 뺀다.
private val SuggestionPress = KIndication(RoundedCornerShape(10.dp))
private val LIST_GAP = 6.dp
private val LIST_PADDING = 6.dp
private val LIST_SLIDE = 6.dp
private const val LIST_FADE_MS = 150
private val ROW_HEIGHT = 44.dp
private val ROW_PADDING = 16.dp
private val ROW_VERTICAL_PADDING = 11.dp
private const val CROSS_ARM = 0.32f
private const val REMOVE_FILL = 0.14f
private const val CHIP_FILL = 0.16f
private const val PLACEHOLDER_ALPHA = 0.45f
private const val ENTER_FROM = 0.6f
private const val ENTER_DAMPING = 0.55f
private const val ENTER_STIFFNESS = 500f
