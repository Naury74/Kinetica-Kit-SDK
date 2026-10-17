package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 켜고 끄는 필터 칩. 켜지면 [tone] 색으로 물들고 앞쪽에 체크 표시가 그려지며, 그동안 칩 폭이 부드럽게 늘어난다.
 * 여러 칩을 한꺼번에 다루려면 [KChipGroup]을 쓴다. 이 칩은 그 안의 칩과 같은 모양이다. TalkBack은 체크박스로 읽는다.
 *
 * ```kotlin
 * KFilterChip("무료 배송", freeShipping, { freeShipping = it })
 * ```
 *
 * @param leadingIcon 꺼진 동안 앞에 놓을 아이콘. 켜지면 체크 표시가 그 자리를 대신한다.
 * @param variant 꺼진 칩의 모양. 기본값 [KVariant.Glass]는 [surface]로 그리고, [KVariant.Tonal]은 옅은 회색, [KVariant.Outlined]는 테두리 칩이다.
 *   [KVariant.Filled]는 [KVariant.Tonal]과 같게 그린다.
 */
@Composable
public fun KFilterChip(
    label: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    variant: KVariant = KVariant.Glass,
    tone: KTone = KTone.Accent,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val haptic = LocalHapticFeedback.current
    FilterChipBody(
        ChipSpec(label, selected, enabled, tone.colors(surface).color, surface, variant),
        modifier,
        leadingIcon,
    ) {
        Modifier.toggleable(selected, null, KIndication(chipShape), enabled, Role.Checkbox) {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onSelectedChange(it)
        }
    }
}

/**
 * 입력한 값을 보여 주는 칩. 받는 사람, 태그, 고른 필터처럼 지울 수 있는 값에 쓴다. 끝의 × 버튼을 누르면 [onRemove]가 불린다.
 * [avatar]를 주면 앞에 원형으로 잘라 놓는다. TalkBack에서는 × 버튼이 '○○ 지우기'로 읽히고, 칩에도 같은 사용자 지정 동작이 붙는다.
 *
 * ```kotlin
 * KInputChip("민지", onRemove = { remove(it) }, avatar = { KAvatar(KAvatarData("민지"), size = KAvatarSize.ExtraSmall) })
 * ```
 *
 * @param onClick 칩 본문을 눌렀을 때. null이면 본문은 누를 수 없다.
 * @param avatar 앞에 놓을 24dp 원 안 콘텐츠. [leadingIcon]보다 우선한다.
 */
@Composable
public fun KInputChip(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    avatar: (@Composable () -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    variant: KVariant = KVariant.Glass,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val removeLabel = stringResource(R.string.kinetica_ui_chip_remove, label)
    val neutral = KTone.Neutral.colors(surface)
    val ink = buttonInk(variant.chipVariant(), KTone.Neutral, neutral, surface)
    Row(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .heightIn(min = chipHeight)
            .variantBackground(variant.chipVariant(), neutral, surface, chipShape)
            .clip(chipShape)
            .then(
                if (onClick !=
                    null
                ) {
                    Modifier.clickable(null, KIndication(chipShape, ink), enabled, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(removeLabel) {
                        onRemove()
                        true
                    },
                )
            }
            .padding(
                start = if (avatar !=
                    null
                ) {
                    AVATAR_INSET
                } else if (leadingIcon != null) {
                    ICON_START
                } else {
                    CHIP_PADDING
                },
                end = REMOVE_INSET,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            avatar != null -> Box(Modifier.size(AVATAR_SIZE).clip(CircleShape), contentAlignment = Alignment.Center) { avatar() }
            leadingIcon != null -> ChipIcon(leadingIcon, ink)
        }
        if (avatar != null || leadingIcon != null) Box(Modifier.size(ICON_GAP))
        ChipLabel(label, ink, Modifier.weight(1f, fill = false))
        RemoveButton(ink, removeLabel, enabled, onRemove)
    }
}

/**
 * 동작을 바로 실행하는 칩. '길 찾기', '일정에 추가'처럼 화면 내용과 이어진 동작을 앞 아이콘과 함께 보여 준다. 아이콘은 [tone] 색이다.
 *
 * ```kotlin
 * KAssistChip("일정에 추가", onClick = ::addEvent, leadingIcon = Icons.Calendar)
 * ```
 */
@Composable
public fun KAssistChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    variant: KVariant = KVariant.Glass,
    tone: KTone = KTone.Accent,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val neutral = KTone.Neutral.colors(surface)
    val ink = buttonInk(variant.chipVariant(), KTone.Neutral, neutral, surface)
    val iconInk = readableOn(tone.colors(surface).color, variantInkBackground(variant.chipVariant(), neutral, surface))
    ActionChip(
        label,
        onClick,
        modifier,
        ActionLook(
            enabled,
            variant,
            surface,
            ink,
            if (tone ==
                KTone.Neutral
            ) {
                ink
            } else {
                iconInk
            },
        ),
        leadingIcon,
    )
}

/**
 * 제안을 보여 주는 칩. 검색어 추천, 빠른 답장처럼 누르면 그 글자를 쓰는 곳에 쓴다. 아이콘을 주면 글자와 같은 색으로 그린다.
 *
 * ```kotlin
 * KSuggestionChip("좋아요!", onClick = { reply("좋아요!") })
 * ```
 */
@Composable
public fun KSuggestionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    variant: KVariant = KVariant.Glass,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val neutral = KTone.Neutral.colors(surface)
    val ink = buttonInk(variant.chipVariant(), KTone.Neutral, neutral, surface)
    ActionChip(label, onClick, modifier, ActionLook(enabled, variant, surface, ink, ink), icon)
}

/**
 * 칩을 한 줄로 늘어놓고 가로로 스크롤한다. 칩이 많아 줄을 넘기면 화면이 길어지는 필터 줄에 쓴다.
 * 화면 가장자리까지 스크롤되게 하려면 이 줄을 화면 폭에 두고 [contentPadding]에 본문 여백을 준다.
 *
 * ```kotlin
 * KChipRow(contentPadding = PaddingValues(horizontal = 20.dp)) { filters.forEach { KFilterChip(it.name, it.on, { … }) } }
 * ```
 */
@Composable
public fun KChipRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        // 스크롤 영역은 내용을 잘라 그리므로, 칩 그림자가 경계에서 끊겨 띠처럼 보이지 않게 위아래 여유를 둔다.
        modifier.horizontalScroll(rememberScrollState()).padding(contentPadding).padding(vertical = SHADOW_ROOM),
        horizontalArrangement = Arrangement.spacedBy(ChipRowGap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 필터 칩 한 개를 그리는 데 필요한 값 */
internal class ChipSpec(
    val label: String,
    val selected: Boolean,
    val enabled: Boolean,
    val accent: Color,
    val surface: SurfaceStyle,
    val variant: KVariant,
)

/** 필터 칩 앞 체크 표시. [icon]이 있으면 그 자리에서 아이콘과 체크가 바뀌고, 없으면 체크가 나타나며 자리가 늘어난다. */
@Composable
private fun CheckSlot(icon: ImageVector?, ink: Color, accentInk: Color, level: () -> Float) {
    if (icon != null) {
        Box(contentAlignment = Alignment.Center) {
            ChipIcon(icon, ink, Modifier.graphicsLayer { alpha = 1f - level().coerceIn(0f, 1f) })
            Box(Modifier.size(CHECK_SIZE).drawBehind { drawCheckStroke(accentInk, level(), Offset.Zero, size.width, CHECK_STROKE.toPx()) })
        }
        Box(Modifier.size(ICON_GAP))
    } else {
        Box(Modifier.checkSlot(level).drawBehind { drawCheckStroke(accentInk, level(), Offset.Zero, size.width, CHECK_STROKE.toPx()) })
    }
}

/** 필터 칩 본체. [pick]은 선택 방식에 맞춘 누르기 수정자를 돌려준다. [KChipGroup]과 [KFilterChip]이 함께 쓴다. */
@Composable
internal fun FilterChipBody(spec: ChipSpec, modifier: Modifier, icon: ImageVector? = null, pick: @Composable () -> Modifier) {
    val shown = remember { Animatable(if (spec.selected) 1f else 0f) }
    val still = reducedMotion()
    LaunchedEffect(spec.selected, still) {
        val target = if (spec.selected) 1f else 0f
        if (still) {
            shown.snapTo(target)
        } else {
            shown.animateTo(target, spring(dampingRatio = CHIP_DAMPING, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val variant = spec.variant.chipVariant()
    val neutral = KTone.Neutral.colors(spec.surface)
    val ink = buttonInk(variant, KTone.Neutral, neutral, spec.surface)
    val accent = spec.accent
    // 글래스 위에서는 칩 뒤로 무엇이 비칠지 알 수 없어 옅은 강조색 바탕으로는 대비를 보장할 수 없다. 강조색으로 꽉 채우고 그 위에 흰 글자나 검은 글자를 쓴다.
    val solid = spec.surface is SurfaceStyle.Glass
    val fillAlpha = if (solid) 1f else SELECTED_FILL
    val accentInk = if (solid) onFill(accent) else readableOn(accent, accent.copy(alpha = SELECTED_FILL).compositeOn(spec.surface))
    val shape = chipShape
    Row(
        modifier
            .graphicsLayer { alpha = if (spec.enabled) 1f else DISABLED_ALPHA }
            .heightIn(min = chipHeight)
            .variantBackground(variant, neutral, spec.surface, chipShape)
            .drawBehind {
                val level = shown.value.coerceIn(0f, 1f)
                if (level > 0f) {
                    drawShape(shape, accent.copy(alpha = accent.alpha * fillAlpha * level), Offset.Zero, size)
                    val stroke = BORDER.toPx()
                    drawShape(
                        shape,
                        accent.copy(alpha = SELECTED_BORDER * level),
                        Offset(stroke / 2, stroke / 2),
                        size.copy(size.width - stroke, size.height - stroke),
                        Stroke(stroke),
                    )
                }
            }
            .then(pick())
            .padding(start = if (icon != null) ICON_START else CHIP_PADDING, end = CHIP_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckSlot(icon, ink, accentInk) { shown.value }
        ThemedText(
            spec.label,
            style = TextStyle(fontSize = CHIP_FONT, fontWeight = KineticaTheme.design.label(FontWeight.Medium)),
            color = { lerp(ink, accentInk, shown.value.coerceIn(0f, 1f)) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private class ActionLook(val enabled: Boolean, val variant: KVariant, val surface: SurfaceStyle, val ink: Color, val iconInk: Color)

@Composable
private fun ActionChip(label: String, onClick: () -> Unit, modifier: Modifier, look: ActionLook, icon: ImageVector?) {
    val variant = look.variant.chipVariant()
    Row(
        modifier
            .graphicsLayer { alpha = if (look.enabled) 1f else DISABLED_ALPHA }
            .heightIn(min = chipHeight)
            .variantBackground(variant, KTone.Neutral.colors(look.surface), look.surface, chipShape)
            .clip(chipShape)
            .clickable(null, KIndication(chipShape, look.ink), look.enabled, role = Role.Button, onClick = onClick)
            .padding(start = if (icon != null) ICON_START else CHIP_PADDING, end = CHIP_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            ChipIcon(icon, look.iconInk)
            Box(Modifier.size(ICON_GAP))
        }
        ChipLabel(label, look.ink)
    }
}

@Composable
private fun ChipIcon(icon: ImageVector, ink: Color, modifier: Modifier = Modifier) {
    Image(rememberVectorPainter(icon), null, modifier.size(CHIP_ICON), colorFilter = ColorFilter.tint(ink))
}

@Composable
private fun ChipLabel(label: String, ink: Color, modifier: Modifier = Modifier) {
    ThemedText(
        label,
        modifier,
        style = TextStyle(color = ink, fontSize = CHIP_FONT, fontWeight = KineticaTheme.design.label(FontWeight.Medium)),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 입력 칩 끝의 × 버튼. 보이는 원은 18dp지만 누르는 영역은 칩 높이만큼 잡는다. */
@Composable
private fun RemoveButton(ink: Color, label: String, enabled: Boolean, onRemove: () -> Unit) {
    Box(
        Modifier
            .size(REMOVE_TOUCH)
            .clip(CircleShape)
            .clickable(null, KIndication(CenteredCircleShape(REMOVE_RING_INSET), ink), enabled, role = Role.Button, onClick = onRemove)
            .semantics { contentDescription = label }
            .drawBehind {
                val r = REMOVE_RADIUS.toPx()
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(ink.copy(alpha = REMOVE_FILL), r, c)
                drawCross(ink, c, r * CROSS_RATIO, REMOVE_STROKE.toPx())
            },
    )
}

/** 체크 표시 자리. 나타난 정도에 맞춰 폭이 0에서 아이콘과 간격을 더한 폭까지 늘어난다. 레이아웃 단계에서 읽으므로 재구성하지 않는다. */
private fun Modifier.checkSlot(level: () -> Float): Modifier = layout { measurable, constraints ->
    val icon = CHECK_SIZE.roundToPx()
    val full = icon + CHECK_GAP.roundToPx()
    val width = (full * level().coerceAtLeast(0f)).roundToInt()
    val placeable = measurable.measure(constraints.copy(minWidth = icon, maxWidth = icon, minHeight = icon, maxHeight = icon))
    layout(width, icon) { placeable.placeRelative(0, 0) }
}

/** 칩은 채운 모양이 선택 표시와 겹치므로 Filled는 Tonal로 그린다. */
internal fun KVariant.chipVariant(): KVariant = if (this == KVariant.Filled) KVariant.Tonal else this

private fun Color.compositeOn(surface: SurfaceStyle): Color = compositeOver(surface.inkBackground())

/** 강조색으로 꽉 채운 칩 위의 글자 색. 흰 글자가 읽히면 흰색, 아니면 검은색이다. */
private fun onFill(fill: Color): Color = if (contrast(Color.White, fill.copy(alpha = 1f)) >= READABLE_ON_FILL) Color.White else Color.Black

private const val READABLE_ON_FILL = 3f

internal val ChipRowGap = 8.dp
private val CHIP_HEIGHT = 34.dp

/** 디자인 언어의 칩 모양 */
internal val chipShape: Shape
    @Composable
    @ReadOnlyComposable
    get() = shapeFor { chip }

/** 밀도에 맞춘 칩 높이 */
private val chipHeight: Dp
    @Composable
    @ReadOnlyComposable
    get() = LocalKineticaDesign.current.density.height(CHIP_HEIGHT)
private val SHADOW_ROOM = 6.dp
private val CHIP_PADDING = 14.dp
private val ICON_START = 10.dp
private val CHIP_ICON = 18.dp
private val ICON_GAP = 6.dp
private val AVATAR_SIZE = 24.dp
private val AVATAR_INSET = 5.dp
private val REMOVE_INSET = 3.dp
private val REMOVE_TOUCH = 30.dp
private val REMOVE_RADIUS = 9.dp
private val REMOVE_RING_INSET = 4.dp
private val REMOVE_STROKE = 1.6.dp
private val CHECK_SIZE = 16.dp
private val CHECK_GAP = 6.dp
private val CHECK_STROKE = 2.dp
private val BORDER = 1.dp
private val CHIP_FONT = 14.sp
private const val CROSS_RATIO = 0.75f
private const val REMOVE_FILL = 0.12f
private const val CHIP_DAMPING = 0.7f
private const val SELECTED_FILL = 0.16f
private const val SELECTED_BORDER = 0.45f
private const val DISABLED_ALPHA = 0.4f
