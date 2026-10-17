package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 주 동작 버튼과 꺾쇠 버튼을 붙여 놓은 분할 버튼. 앞쪽을 누르면 [onClick], 꺾쇠를 누르면 [onMenuClick]이 불린다.
 * 바깥 모서리는 버튼 모양 토큰 반지름으로 둥글고 맞붙은 안쪽 모서리는 작게 깎는다. [menuExpanded]가 `true`면 꺾쇠 버튼의 안쪽 모서리도 둥글어지고 꺾쇠가 위를 향한다.
 * 메뉴 자체는 호출한 쪽이 띄운다. TalkBack은 꺾쇠 버튼을 '옵션 더 보기'와 펼침 상태로 읽는다. RTL에서는 꺾쇠가 왼쪽에 놓인다.
 *
 * ```kotlin
 * var menu by remember { mutableStateOf(false) }
 * KSplitButton("보내기", onClick = ::send, onMenuClick = { menu = !menu }, menuExpanded = menu)
 * ```
 *
 * @param menuLabel 꺾쇠 버튼의 TalkBack 이름. null이면 '옵션 더 보기'다.
 * @param variant 두 버튼의 모양. 기본값은 [KVariant.Filled]다.
 */
@Composable
public fun KSplitButton(
    text: String,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    menuExpanded: Boolean = false,
    variant: KVariant = KVariant.Filled,
    tone: KTone = KTone.Accent,
    size: KButtonSize = KButtonSize.Medium,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    menuLabel: String? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val colors = tone.colors(surface)
    val ink = buttonInk(variant, tone, colors, surface)
    val haptic = LocalHapticFeedback.current
    val open by animateFloatAsState(
        if (menuExpanded) 1f else 0f,
        spring(dampingRatio = OPEN_DAMPING, stiffness = OPEN_STIFFNESS),
        label = "split-open",
    )
    val height = KineticaTheme.design.density.height(size.height)
    val round = buttonRoundFraction(height)
    val inner = minOf(INNER, round)
    val leading = SideRoundShape(round, inner)
    // 열리면 꺾쇠 버튼의 안쪽 모서리도 둥글어져 바깥 모서리와 같아진다. 짧은 순간이라 모양은 재구성으로 바꾼다.
    val trailing = SideRoundShape(inner + (round - inner) * open, round)
    val press = if (variant.bare) PLAIN_PRESS_ALPHA else PRESS_ALPHA
    Row(
        modifier.graphicsLayer { alpha = if (enabled) 1f else DISABLED },
        horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .jellyPress(JELLY)
                .height(height)
                .variantBackground(variant, colors, surface, leading)
                .clip(leading)
                .clickable(null, KIndication(leading, ink, press), enabled, role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                }
                .padding(start = if (icon != null) size.padding - ICON_TRIM else size.padding, end = size.padding - INNER_TRIM),
            horizontalArrangement = Arrangement.spacedBy(size.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Image(rememberVectorPainter(it), null, Modifier.size(size.icon), colorFilter = ColorFilter.tint(ink)) }
            ThemedText(
                text,
                style = TextStyle(color = ink, fontSize = size.font, fontWeight = KineticaTheme.design.label(FontWeight.SemiBold)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MenuSegment(SegmentLook(variant, colors, surface, trailing, ink, press), size, menuExpanded, enabled, { open }, menuLabel) {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            onMenuClick()
        }
    }
}

private class SegmentLook(
    val variant: KVariant,
    val colors: ToneColors,
    val surface: SurfaceStyle,
    val shape: SideRoundShape,
    val ink: Color,
    val press: Float,
)

@Composable
private fun MenuSegment(
    look: SegmentLook,
    size: KButtonSize,
    expanded: Boolean,
    enabled: Boolean,
    open: () -> Float,
    label: String?,
    onClick: () -> Unit,
) {
    val name = label ?: stringResource(R.string.kinetica_ui_more_options)
    val state = stringResource(if (expanded) R.string.kinetica_ui_expanded else R.string.kinetica_ui_collapsed)
    val height = KineticaTheme.design.density.height(size.height)
    Box(
        Modifier
            .jellyPress(JELLY)
            .height(height)
            .defaultMinSize(minWidth = height)
            .width(height + MENU_EXTRA)
            .variantBackground(look.variant, look.colors, look.surface, look.shape)
            .clip(look.shape)
            .clickable(null, KIndication(look.shape, look.ink, look.press), enabled, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = name
                stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) {
        val ink = look.ink
        Box(
            Modifier
                .size(size.icon)
                .graphicsLayer { rotationZ = HALF_TURN * open() }
                .drawBehind {
                    drawChevronDown(
                        ink,
                        Offset(this.size.width / 2, this.size.height / 2),
                        this.size.width * CHEVRON_WIDTH,
                        this.size.width * CHEVRON_STROKE,
                    )
                },
        )
    }
}

private val SEGMENT_GAP = 2.dp
private val MENU_EXTRA = 4.dp
private val ICON_TRIM = 4.dp
private val INNER_TRIM = 2.dp
private const val INNER = 0.16f
private const val JELLY = 0.05f
private const val DISABLED = 0.4f
private const val PRESS_ALPHA = 0.12f
private const val PLAIN_PRESS_ALPHA = 0.1f
private const val OPEN_DAMPING = 0.7f
private const val OPEN_STIFFNESS = 500f
private const val HALF_TURN = 180f
private const val CHEVRON_STROKE = 0.1f
private const val CHEVRON_WIDTH = 0.6f
