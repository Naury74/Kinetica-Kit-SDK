package com.naury.kinetica.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * [KDropdownMenu]의 한 줄. 동작 항목, 체크 항목, 하위 메뉴, 섹션 제목, 구분선 중 하나다.
 *
 * ```kotlin
 * listOf(
 *     KMenuEntry.Section("보기"),
 *     KMenuEntry.Check("숨긴 파일 보기", showHidden) { showHidden = it },
 *     KMenuEntry.Divider,
 *     KMenuEntry.Submenu("정렬", listOf(KMenuEntry.Action("이름순") { sort(Name) }, KMenuEntry.Action("날짜순") { sort(Date) })),
 *     KMenuEntry.Action("삭제", destructive = true) { delete() },
 * )
 * ```
 */
@Immutable
public sealed interface KMenuEntry {
    /**
     * 누르면 메뉴를 닫고 [onClick]을 부르는 항목.
     *
     * @property trailing 끝에 옅게 놓는 글자. 단축키(`Ctrl+C`)나 현재 값에 쓴다.
     * @property destructive true면 삭제처럼 되돌리기 어려운 동작으로 보고 [KineticaColors.destructive] 색으로 그린다.
     * @property enabled false면 흐리게 그리고 누를 수 없다.
     */
    @Immutable
    public data class Action(
        val label: String,
        val icon: ImageVector? = null,
        val trailing: String? = null,
        val destructive: Boolean = false,
        val enabled: Boolean = true,
        val onClick: () -> Unit,
    ) : KMenuEntry

    /**
     * 켜고 끄는 항목. 켜져 있으면 앞에 체크 표시를 그린다. 누르면 메뉴를 닫지 않고 [onCheckedChange]에 바뀐 값을 넘겨,
     * 여러 항목을 연달아 바꿀 수 있다.
     */
    @Immutable
    public data class Check(
        val label: String,
        val checked: Boolean,
        val icon: ImageVector? = null,
        val enabled: Boolean = true,
        val onCheckedChange: (Boolean) -> Unit,
    ) : KMenuEntry

    /**
     * 누르면 같은 판 안에서 [entries]로 넘어가는 항목. 넘어간 판 맨 위의 뒤로 줄을 누르면 돌아온다.
     * 좁은 폰 화면에서도 옆으로 펼칠 자리가 필요 없도록 판 안에서 넘긴다. 비어 있으면 [IllegalArgumentException]을 던진다.
     */
    @Immutable
    public data class Submenu(
        val label: String,
        val entries: List<KMenuEntry>,
        val icon: ImageVector? = null,
        val enabled: Boolean = true,
    ) : KMenuEntry {
        init {
            require(entries.isNotEmpty()) { "Submenu의 entries가 비어 있으면 안 됩니다." }
        }
    }

    /** 아래 항목들을 묶는 작은 제목. 누를 수 없고 TalkBack은 제목으로 읽는다. */
    @Immutable
    public data class Section(val title: String) : KMenuEntry

    /** 항목 묶음 사이의 가는 선 */
    public data object Divider : KMenuEntry
}

/** [KDropdownMenu]의 기본값 */
public object KDropdownMenuDefaults {
    /** 메뉴 판의 최소 폭 */
    public val MinWidth: Dp = 200.dp

    /** 메뉴 판의 최대 폭. 화면이 더 좁으면 화면에 맞춘다. */
    public val MaxWidth: Dp = 320.dp
}

/**
 * 버튼에 붙어 펼쳐지는 메뉴. 버튼과 함께 `Box`로 감싸고 그 안에서 부르면 그 Box가 앵커가 된다. 앵커 아래에 공간이 있으면 아래,
 * 없으면 위에 뜨고, 키보드가 올라와 있으면 키보드 위까지만 공간으로 본다. 가로는 앵커 시작 쪽 가장자리에 맞추되 화면 밖으로 나가지 않는다.
 * 길면 판 안에서 스크롤한다.
 *
 * 바깥을 누르거나 뒤로 가기(Esc)를 하면 [onDismissRequest]를 부른다. [KMenuEntry.Action]을 누르면 메뉴를 닫고 그 동작을 실행한다.
 * 키보드로 열면 첫 항목에 초점이 가고, 위아래 방향키로 항목을 옮기며, 하위 메뉴는 끝 방향키(LTR이면 오른쪽)로 들어가고 시작 방향키나 Esc로 나온다.
 * 열린 동안 뒤 화면은 TalkBack에서 숨는다.
 *
 * [KineticaScaffold] 안에서는 오버레이 레이어에 그려 글래스 표면에 배경이 비친다. 밖에서는 팝업 창에 그린다.
 * 보이는 동안 크기가 0인 요소 하나가 이 자리에 놓이므로 `Arrangement.spacedBy`를 쓰는 Row나 Column에 바로 두지 말고 Box로 감싼다.
 *
 * ```kotlin
 * Box {
 *     KIconButton(MoreIcon, "더 보기", onClick = { open = true })
 *     KDropdownMenu(open, { open = false }, entries)
 * }
 * ```
 *
 * @param entries 메뉴 항목. 비어 있으면 [IllegalArgumentException]을 던진다.
 * @param alignEnd true면 판의 끝 가장자리를 앵커 끝에 맞춘다. 화면 끝에 놓인 버튼에 쓴다.
 * @param minWidth 판의 최소 폭. [KDropdownMenuDefaults.MaxWidth]보다 크면 그 값으로 맞춘다.
 */
@Composable
public fun KDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    entries: List<KMenuEntry>,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    minWidth: Dp = KDropdownMenuDefaults.MinWidth,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(entries.isNotEmpty()) { "entries가 비어 있으면 안 됩니다." }
    val anchor = remember { PopupAnchor() }
    val dismiss by rememberUpdatedState(onDismissRequest)
    val latest by rememberUpdatedState(entries)
    val frame = remember(alignEnd) { AnchoredFrame(focusable = true, onDismiss = { dismiss() }, alignEnd = alignEnd) }
    AnchoredPopup(expanded, anchor, frame) { transition, geometry ->
        PopupPanel(transition, geometry, surface, modifier) {
            MenuPages(latest, MenuLook(surface, minWidth, transition.targetState)) { dismiss() }
        }
    }
}

/** 메뉴 판을 그리는 고정 값. [active]는 열리는 중인지로, 닫히는 동안 항목이 다시 눌리지 않게 한다. */
internal class MenuLook(
    val surface: SurfaceStyle,
    val minWidth: Dp,
    val active: Boolean,
    val maxWidth: Dp = KDropdownMenuDefaults.MaxWidth,
)

/** 하위 메뉴로 넘어간 판 하나. [depth]는 맨 위 판이 0이다. */
private class MenuPage(val depth: Int, val title: String?, val entries: List<KMenuEntry>)

/** 하위 메뉴 판을 쌓아 두고 맨 위 판을 그린다. 넘어갈 때는 끝 방향에서, 돌아올 때는 시작 방향에서 밀려 들어온다. */
@Composable
internal fun MenuPages(entries: List<KMenuEntry>, look: MenuLook, onDismiss: () -> Unit) {
    var stack by remember { mutableStateOf(listOf<MenuPage>()) }
    val root = MenuPage(0, null, entries)
    val top = stack.lastOrNull() ?: root
    val still = reducedMotion()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val requester = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    val started = remember { booleanArrayOf(false) }
    LaunchedEffect(top.depth) {
        // 처음 열 때의 초점은 오버레이가 옮긴다. 판을 오갈 때는 초점을 둔 줄이 사라지므로 새 판 첫 줄로 옮긴다.
        if (!started[0]) {
            started[0] = true
            return@LaunchedEffect
        }
        withFrameNanos { }
        if (inputMode.inputMode == InputMode.Keyboard) requester.requestFocus(FocusDirection.Enter)
    }
    val pop = { stack = stack.dropLast(1) }
    val maxWidth = look.maxWidth
    Box(
        Modifier
            .focusRequester(requester)
            .focusGroup()
            .onPreviewKeyEvent { event -> menuKey(event, rtl, stack.isNotEmpty(), onDismiss, pop) },
    ) {
        AnimatedContent(
            top,
            transitionSpec = { pageTransition(targetState.depth > initialState.depth, rtl, still) },
            contentKey = { it.depth },
            label = "menu-pages",
        ) { page ->
            Column(
                Modifier
                    .width(IntrinsicSize.Max)
                    .widthIn(min = look.minWidth.coerceAtMost(maxWidth), max = maxWidth)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (page.title != null) {
                    MenuBackRow(page.title, look.surface, pop)
                    MenuDivider(look.surface)
                }
                val slots = MenuSlots(page.entries.any { it is KMenuEntry.Check }, page.entries.any { it.icon != null })
                page.entries.forEach { entry ->
                    MenuEntryRow(entry, look, slots, onDismiss) { sub -> stack = stack + MenuPage(page.depth + 1, sub.label, sub.entries) }
                }
            }
        }
    }
}

/** 판 전체에서 받는 키. Esc는 하위 판이면 돌아가고 맨 위 판이면 닫는다. 시작 방향키와 Backspace는 하위 판에서만 돌아간다. */
private fun menuKey(event: KeyEvent, rtl: Boolean, nested: Boolean, onDismiss: () -> Unit, pop: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val back = if (rtl) Key.DirectionRight else Key.DirectionLeft
    return when {
        event.key == Key.Escape && !nested -> {
            onDismiss()
            true
        }

        nested && (event.key == Key.Escape || event.key == back || event.key == Key.Backspace) -> {
            pop()
            true
        }

        else -> false
    }
}

private fun pageTransition(forward: Boolean, rtl: Boolean, still: Boolean): ContentTransform {
    if (still) return EnterTransition.None togetherWith ExitTransition.None
    // 넘어갈 때는 끝 방향(LTR이면 오른쪽)에서 들어온다. RTL에서는 반대다.
    val sign = (if (forward) 1 else -1) * (if (rtl) -1 else 1)
    return (slideInHorizontally(tween(PAGE_MS)) { it / PAGE_SHIFT * sign } + fadeIn(tween(PAGE_MS))) togetherWith
        (slideOutHorizontally(tween(PAGE_MS)) { -it / PAGE_SHIFT * sign } + fadeOut(tween(PAGE_MS / PAGE_SHIFT)))
}

@Composable
private fun MenuEntryRow(
    entry: KMenuEntry,
    look: MenuLook,
    slots: MenuSlots,
    onDismiss: () -> Unit,
    onOpenSubmenu: (KMenuEntry.Submenu) -> Unit,
) {
    val surface = look.surface
    when (entry) {
        is KMenuEntry.Divider -> MenuDivider(surface)

        is KMenuEntry.Section -> ThemedText(
            entry.title,
            Modifier
                .padding(start = ROW_INSET + ROW_PADDING, end = ROW_INSET + ROW_PADDING, top = 10.dp, bottom = 4.dp)
                .semantics { heading() },
            style = TextStyle(color = surface.muted(SECTION_ALPHA), fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )

        is KMenuEntry.Action -> MenuRow(
            MenuRowSpec(entry.label, entry.icon, entry.trailing, slots.checkOf(false), reserveIcon = slots.icon),
            if (entry.destructive) KineticaTheme.colors.destructive else surface.contentColor,
            Modifier.kClickable(MenuItemShape, entry.enabled) {
                if (look.active) {
                    onDismiss()
                    entry.onClick()
                }
            },
            entry.enabled,
        )

        is KMenuEntry.Check -> MenuRow(
            MenuRowSpec(entry.label, entry.icon, null, entry.checked, reserveIcon = slots.icon),
            surface.contentColor,
            Modifier.kToggleable(entry.checked, MenuItemShape, entry.enabled, Role.Checkbox) { if (look.active) entry.onCheckedChange(it) },
            entry.enabled,
        )

        is KMenuEntry.Submenu -> SubmenuRow(entry, look, slots, onOpenSubmenu)
    }
}

@Composable
private fun SubmenuRow(entry: KMenuEntry.Submenu, look: MenuLook, slots: MenuSlots, onOpen: (KMenuEntry.Submenu) -> Unit) {
    val hint = stringResource(R.string.kinetica_ui_menu_submenu)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val forward = if (rtl) Key.DirectionLeft else Key.DirectionRight
    MenuRow(
        MenuRowSpec(entry.label, entry.icon, null, slots.checkOf(false), chevron = true, reserveIcon = slots.icon),
        look.surface.contentColor,
        Modifier
            .onKeyEvent { event ->
                val open = event.type == KeyEventType.KeyDown && event.key == forward && entry.enabled
                if (open) onOpen(entry)
                open
            }
            .kClickable(MenuItemShape, entry.enabled) { if (look.active) onOpen(entry) }
            .semantics { stateDescription = hint },
        entry.enabled,
    )
}

@Composable
private fun MenuBackRow(title: String, surface: SurfaceStyle, onBack: () -> Unit) {
    val label = stringResource(R.string.kinetica_ui_menu_back)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ROW_INSET)
            .heightIn(min = KMetrics.MinTouch)
            .kClickable(MenuItemShape, onClickLabel = label, onClick = onBack)
            .padding(horizontal = ROW_PADDING - 2.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 다음 판으로 넘어갈 때 쓴 꺾쇠를 반대로 돌려 돌아간다는 걸 보여 준다.
        Glyph(KGlyphs.ChevronForward, surface.contentColor, Modifier.graphicsLayer { rotationZ = HALF_TURN })
        Spacer(Modifier.width(6.dp))
        ThemedText(
            title,
            Modifier.weight(1f, fill = false),
            style = TextStyle(color = surface.contentColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 한 판에 체크 칸과 아이콘 칸을 둘지. 한 항목이라도 쓰면 모든 줄에 칸을 비워 두어 글자 시작선을 맞춘다. */
private class MenuSlots(val check: Boolean, val icon: Boolean) {
    /** 체크 칸을 두는 판이면 [checked], 아니면 칸 없음(null) */
    fun checkOf(checked: Boolean): Boolean? = if (check) checked else null
}

private val KMenuEntry.icon: ImageVector?
    get() = when (this) {
        is KMenuEntry.Action -> icon
        is KMenuEntry.Check -> icon
        is KMenuEntry.Submenu -> icon
        else -> null
    }

/**
 * 메뉴 한 줄의 내용. [checked]가 null이 아니면 체크 표시 칸을 두고(false면 빈 칸), [reserveIcon]이면 아이콘이 없어도 아이콘 칸을 비워 두어
 * 다른 줄과 글자 시작선을 맞춘다.
 */
internal class MenuRowSpec(
    val label: String,
    val icon: ImageVector?,
    val trailing: String?,
    val checked: Boolean?,
    val chevron: Boolean = false,
    val reserveIcon: Boolean = icon != null,
)

@Composable
internal fun MenuRow(spec: MenuRowSpec, color: androidx.compose.ui.graphics.Color, clickable: Modifier, enabled: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ROW_INSET)
            .heightIn(min = KMetrics.MinTouch)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .then(clickable)
            .padding(horizontal = ROW_PADDING, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (spec.checked != null) {
            Box(Modifier.size(KMetrics.ButtonIcon)) { if (spec.checked) Glyph(KGlyphs.Check, KineticaTheme.colors.accent) }
            Spacer(Modifier.width(ICON_GAP))
        }
        if (spec.reserveIcon || spec.icon != null) {
            Box(Modifier.size(KMetrics.ButtonIcon)) { spec.icon?.let { Glyph(it, color) } }
            Spacer(Modifier.width(ICON_GAP + 2.dp))
        }
        ThemedText(
            spec.label,
            Modifier.weight(1f),
            style = TextStyle(color = color, fontSize = 16.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        spec.trailing?.let {
            ThemedText(
                it,
                Modifier.padding(start = 16.dp),
                style = TextStyle(color = color.copy(alpha = TRAILING_ALPHA), fontSize = 14.sp),
                maxLines = 1,
            )
        }
        if (spec.chevron) {
            Spacer(Modifier.width(8.dp))
            Glyph(KGlyphs.ChevronForward, color.copy(alpha = TRAILING_ALPHA), size = 16.dp)
        }
    }
}

@Composable
internal fun MenuDivider(surface: SurfaceStyle) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ROW_INSET + ROW_PADDING, vertical = 5.dp)
            .height(1.dp)
            .background(surface.contentColor.copy(alpha = DIVIDER_ALPHA)),
    )
}

/** 24 격자 선 아이콘을 [color]로 칠해 그린다. */
@Composable
internal fun Glyph(
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    size: Dp = KMetrics.ButtonIcon,
) {
    Image(rememberVectorPainter(icon), null, modifier.size(size), colorFilter = ColorFilter.tint(color))
}

/** 메뉴 항목 눌림 표시 모양. 판 모서리와 같은 중심으로 굽도록 판 안쪽 여백만큼 작은 반지름을 쓴다. */
internal val MenuItemShape: Shape
    @Composable
    @ReadOnlyComposable
    get() = currentShapes.inner(currentShapes.menu, PopupPanelPadding)

private val ROW_INSET = PopupPanelPadding
private val ROW_PADDING = 12.dp
private val ICON_GAP = 10.dp
private const val SECTION_ALPHA = 0.55f
private const val TRAILING_ALPHA = 0.5f
private const val DIVIDER_ALPHA = 0.12f
private const val DISABLED_ALPHA = 0.38f
private const val HALF_TURN = 180f
private const val PAGE_MS = 220
private const val PAGE_SHIFT = 3
