// 파일 이름은 대표 컴포저블인 KListItem을 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** [KListItem] 앞쪽에 놓는 것 */
@Immutable
public sealed interface KListLeading {
    /**
     * 아이콘. [container]를 주면 설정 화면처럼 그 색의 둥근 사각형 안에 흰 아이콘을 그린다.
     *
     * @property tint 아이콘 색. [Color.Unspecified]면 [container]가 있을 때 흰색, 없을 때 표면 글자 색을 옅게 쓴다.
     * @property container 아이콘 뒤 둥근 사각형 색. `null`이면 그리지 않는다.
     */
    public data class Icon(val icon: ImageVector, val tint: Color = Color.Unspecified, val container: Color? = null) : KListLeading

    /**
     * [Icon]과 같지만 `painterResource`로 읽은 drawable처럼 [Painter]를 받는다. 색과 크기 규칙은 [Icon]과 같다.
     * 아이콘에 [tint]를 입히므로, 여러 색으로 된 그림을 원래 색대로 보이려면 [Thumbnail]이나 [Custom]을 쓴다.
     */
    public data class PainterIcon(val painter: Painter, val tint: Color = Color.Unspecified, val container: Color? = null) : KListLeading

    /**
     * 40dp 원형 아바타. [image]가 없으면 [initials]를 [color] 원 위에 쓴다.
     *
     * @property initials 이름 첫 글자. 두 글자까지 보인다.
     * @property color 이니셜 바탕색. [Color.Unspecified]면 강조색을 쓴다.
     */
    public data class Avatar(val initials: String, val image: Painter? = null, val color: Color = Color.Unspecified) : KListLeading

    /** 56dp 정사각형 썸네일. [wide]면 16:10 가로형(90×56dp)이다. 모서리는 8dp로 둥글다. */
    public data class Thumbnail(val image: Painter, val wide: Boolean = false) : KListLeading

    /** 직접 그리는 앞쪽 내용 */
    public class Custom(public val content: @Composable () -> Unit) : KListLeading
}

/**
 * [KListItem] 끝쪽에 놓는 것. [Switch], [Checkbox], [Radio]를 넣으면 행 전체가 하나의 스위치, 체크박스, 라디오 버튼이 된다.
 * 행 어디를 눌러도 바뀌고, TalkBack은 행을 하나로 읽고 상태를 알린다.
 */
@Immutable
public sealed interface KListTrailing {
    /**
     * 현재 값. 예: "켜짐", "1.2GB", "−12,000원". [color]가 [Color.Unspecified]면 행 글자 색을 옅게 쓰고, 지정하면 그 색을 그대로 쓴다.
     * 금액의 들어옴과 나감, 상태 강조처럼 값 자체에 색이 필요할 때 지정한다.
     */
    public data class Value(val text: String, val color: Color = Color.Unspecified) : KListTrailing

    /**
     * 스위치. 행 전체가 [Role.Switch]가 된다. [variant]가 기본값 [KSwitchVariant.Capsule]이면
     * [KineticaDesign.controls]의 스위치 모양을 따른다. 기본 디자인 언어에서는 그대로 캡슐이다.
     */
    public data class Switch(
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        val variant: KSwitchVariant = KSwitchVariant.Capsule,
    ) : KListTrailing

    /** 체크박스. 행 전체가 [Role.Checkbox]가 된다. */
    public data class Checkbox(
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        val shape: KCheckboxShape = KCheckboxShape.Square,
    ) : KListTrailing

    /** 라디오 버튼. 행 전체가 [Role.RadioButton]이 된다. 묶음 부모에 `Modifier.selectableGroup()`을 붙이면 TalkBack이 묶음으로 읽는다. */
    public data class Radio(val selected: Boolean, val onSelect: () -> Unit) : KListTrailing

    /** 다음 화면으로 넘어간다는 꺾쇠. [value]가 있으면 꺾쇠 앞에 옅게 쓴다. 오른쪽에서 왼쪽 언어에서는 꺾쇠 방향이 바뀐다. */
    public data class Disclosure(val value: String? = null) : KListTrailing

    /** 직접 그리는 끝쪽 내용 */
    public class Custom(public val content: @Composable () -> Unit) : KListTrailing
}

/**
 * 목록 행. 제목 한 줄에 [overline]과 [supporting]을 더해 1~3줄로 쓴다. 높이는 한 줄 48dp, 두 줄 64dp, 세 줄(위 글자, 제목, 설명) 84dp부터 시작하고,
 * 글자가 커지면 내용에 맞춰 커진다. 좌우 여백은 16dp다.
 *
 * [trailing]이 [KListTrailing.Switch], [KListTrailing.Checkbox], [KListTrailing.Radio]면 행 전체가 그 컨트롤이 되고 [onClick]은 쓰지 않는다.
 * 그 밖에는 [onClick]이 있을 때 행 전체를 누를 수 있다. 눌림 표시는 [KListSection] 안에서 섹션 모서리에 맞춰 잘린다.
 *
 * ```kotlin
 * KListItem("Wi-Fi", leading = KListLeading.Icon(Icons.Wifi, container = Color(0xFF0A84FF)),
 *     trailing = KListTrailing.Disclosure("Home"), onClick = { open() })
 * KListItem("방해 금지", supporting = "밤 11시부터 아침 7시까지", trailing = KListTrailing.Switch(dnd, { dnd = it }))
 * ```
 *
 * @param headline 제목. 한 줄을 넘으면 말줄임표로 줄인다.
 * @param supporting 제목 아래 설명. [supportingMaxLines]줄까지 보인다.
 * @param overline 제목 위 작은 글자. 메일 보낸 사람이나 분류 이름에 쓴다.
 * @param supportingMaxLines 설명의 최대 줄 수. 1 미만이면 [IllegalArgumentException]을 던진다.
 * @param enabled `false`면 누를 수 없고 흐리게 그린다.
 */
@Composable
public fun KListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    overline: String? = null,
    leading: KListLeading? = null,
    trailing: KListTrailing? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    supportingMaxLines: Int = 1,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(supportingMaxLines >= 1) { "supportingMaxLines는 1 이상이어야 합니다." }
    val row = LocalKListRow.current
    // 설명이 실제로 몇 줄이 될지는 그려 봐야 알므로, 최소 높이는 설명을 한 줄로 보고 정한다. 길어지면 행이 내용에 맞춰 커진다.
    val lines = 1 + (if (overline != null) 1 else 0) + (if (supporting != null) 1 else 0)
    val tall = lines >= THREE_LINES
    val density = KineticaTheme.design.density
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = density.height(minHeightFor(lines)))
            .rowInteraction(trailing, onClick, enabled, row)
            .padding(horizontal = ROW_PADDING, vertical = density.padding(if (lines == 1) ROW_VERTICAL_ONE else ROW_VERTICAL)),
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
        verticalAlignment = if (tall) Alignment.Top else Alignment.CenterVertically,
    ) {
        val ink = surface.contentColor.copy(alpha = if (enabled) 1f else CHOICE_DISABLED_ALPHA)
        leading?.let {
            Box(if (tall) Modifier.padding(top = LEADING_TOP) else Modifier) { ListLeading(it, ink, surface.mutedLike(ink, ICON_ALPHA)) }
        }
        Column(
            Modifier.weight(1f).then(if (row != null) Modifier.textStartIn(row) else Modifier),
            verticalArrangement = Arrangement.spacedBy(TEXT_GAP),
        ) {
            overline?.let { ListText(it, surface.mutedLike(ink, OVERLINE_ALPHA), OVERLINE_SIZE, FontWeight.Medium, 1) }
            ListText(headline, ink, HEADLINE_SIZE, FontWeight.Normal, 1)
            supporting?.let {
                ListText(it, surface.mutedLike(ink, SUPPORTING_ALPHA), SUPPORTING_SIZE, FontWeight.Normal, supportingMaxLines)
            }
        }
        trailing?.let { Box(Modifier.align(Alignment.CenterVertically)) { ListTrailing(it, ink, enabled, surface) } }
    }
}

/** 끝쪽 컨트롤에 따라 행 전체를 스위치, 체크박스, 라디오, 버튼으로 만든다. */
private fun Modifier.rowInteraction(trailing: KListTrailing?, onClick: (() -> Unit)?, enabled: Boolean, row: KListRowContext?): Modifier {
    val indication = KIndication(row?.shape ?: RectangleShape)
    return when (trailing) {
        is KListTrailing.Switch ->
            toggleable(trailing.checked, null, indication, enabled, Role.Switch, trailing.onCheckedChange)

        is KListTrailing.Checkbox ->
            toggleable(trailing.checked, null, indication, enabled, Role.Checkbox, trailing.onCheckedChange)

        is KListTrailing.Radio -> selectable(trailing.selected, null, indication, enabled, Role.RadioButton, trailing.onSelect)

        else -> if (onClick != null) clickable(null, indication, enabled, role = Role.Button, onClick = onClick) else this
    }
}

private fun minHeightFor(lines: Int) = when (lines) {
    1 -> ONE_LINE_HEIGHT
    2 -> TWO_LINE_HEIGHT
    else -> THREE_LINE_HEIGHT
}

/** 보조 글자 색. 비활성 행이면 [ink]에 건 흐림을 그대로 얹는다. */
private fun SurfaceStyle.mutedLike(ink: Color, alpha: Float): Color = muted(alpha).let { it.copy(alpha = it.alpha * ink.alpha) }

@Composable
private fun ListText(text: String, color: Color, size: TextUnit, weight: FontWeight, maxLines: Int) {
    ThemedText(
        text,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LeadingIcon(painter: Painter, requestedTint: Color, container: Color?, iconInk: Color) {
    val tint = when {
        requestedTint.isSpecified -> requestedTint
        container != null -> Color.White
        else -> iconInk
    }
    Box(
        if (container != null) {
            Modifier.size(ICON_CONTAINER).background(container, shapeFor { image })
        } else {
            Modifier.size(KMetrics.ListIcon)
        },
        contentAlignment = Alignment.Center,
    ) {
        val icon = if (container != null) ICON_IN_CONTAINER else KMetrics.ListIcon
        Image(painter, null, Modifier.size(icon), colorFilter = ColorFilter.tint(tint))
    }
}

@Composable
private fun ListLeading(leading: KListLeading, ink: Color, iconInk: Color) {
    when (leading) {
        is KListLeading.Icon -> LeadingIcon(rememberVectorPainter(leading.icon), leading.tint, leading.container, iconInk)

        is KListLeading.PainterIcon -> LeadingIcon(leading.painter, leading.tint, leading.container, iconInk)

        is KListLeading.Avatar -> ListAvatar(leading)

        is KListLeading.Thumbnail -> Image(
            leading.image,
            null,
            Modifier
                .size(if (leading.wide) THUMB_WIDE else THUMB_SIZE, THUMB_SIZE)
                .clip(shapeFor { image }),
            contentScale = ContentScale.Crop,
        )

        is KListLeading.Custom -> CompositionLocalProvider(LocalKineticaContentColor provides ink) { leading.content() }
    }
}

@Composable
private fun ListAvatar(avatar: KListLeading.Avatar) {
    val image = avatar.image
    val shape = shapeFor { this.avatar }
    if (image != null) {
        Image(image, null, Modifier.size(AVATAR_SIZE).clip(shape), contentScale = ContentScale.Crop)
        return
    }
    val color = if (avatar.color.isSpecified) avatar.color else KineticaTheme.colors.accent
    Box(Modifier.size(AVATAR_SIZE).background(color, shape), contentAlignment = Alignment.Center) {
        ThemedText(
            avatar.initials.take(2),
            style = TextStyle(color = Color.White, fontSize = AVATAR_FONT, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
    }
}

@Composable
private fun ListTrailing(trailing: KListTrailing, ink: Color, enabled: Boolean, surface: SurfaceStyle) {
    when (trailing) {
        is KListTrailing.Value -> ListText(
            trailing.text,
            trailing.color.takeOrElse { surface.mutedLike(ink, VALUE_ALPHA) },
            VALUE_SIZE,
            FontWeight.Normal,
            1,
        )

        is KListTrailing.Switch -> KSwitch(
            trailing.checked,
            null,
            enabled = enabled,
            surface = surface,
            variant = if (trailing.variant == KSwitchVariant.Capsule) KineticaTheme.design.controls.switch else trailing.variant,
        )

        is KListTrailing.Checkbox -> KCheckbox(trailing.checked, null, enabled = enabled, shape = trailing.shape, surface = surface)

        is KListTrailing.Radio -> KRadio(trailing.selected, null, enabled = enabled, surface = surface)

        is KListTrailing.Disclosure -> Row(
            horizontalArrangement = Arrangement.spacedBy(DISCLOSURE_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            trailing.value?.let { ListText(it, surface.mutedLike(ink, VALUE_ALPHA), VALUE_SIZE, FontWeight.Normal, 1) }
            DisclosureChevron(surface.mutedLike(ink, CHEVRON_ALPHA), Modifier.size(CHEVRON_SIZE))
        }

        is KListTrailing.Custom -> CompositionLocalProvider(LocalKineticaContentColor provides ink) { trailing.content() }
    }
}

private const val THREE_LINES = 3
private val ONE_LINE_HEIGHT = 48.dp
private val TWO_LINE_HEIGHT = 64.dp
private val THREE_LINE_HEIGHT = 84.dp
private val ROW_PADDING = 16.dp
private val ROW_VERTICAL_ONE = 8.dp
private val ROW_VERTICAL = 10.dp
private val ROW_GAP = 14.dp
private val TEXT_GAP = 2.dp
private val LEADING_TOP = 2.dp
private val HEADLINE_SIZE = 16.sp
private val SUPPORTING_SIZE = 14.sp
private val OVERLINE_SIZE = 12.sp
private val VALUE_SIZE = 15.sp
private const val SUPPORTING_ALPHA = 0.62f
private const val OVERLINE_ALPHA = 0.6f
private const val VALUE_ALPHA = 0.55f
private const val ICON_ALPHA = 0.7f
private const val CHEVRON_ALPHA = 0.35f
private val ICON_CONTAINER = 30.dp
private val ICON_IN_CONTAINER = 19.dp
private val AVATAR_SIZE = 40.dp
private val AVATAR_FONT = 15.sp
private val THUMB_SIZE = 56.dp
private val THUMB_WIDE = 90.dp
private val CHEVRON_SIZE = 14.dp
private val DISCLOSURE_GAP = 6.dp
