package com.naury.kinetica.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 아이콘이 위, 이름이 아래인 작은 동작 버튼. 편집기 하단 도구 줄이나 영상 위 동작 묶음처럼 동작 여럿을 나란히 놓을 때 쓴다.
 * 바탕은 칠하지 않고, 누르는 동안 [shape] 모양의 [KIndication]이 덮인다. TalkBack은 [label]을 버튼 이름으로 읽는다.
 *
 * ```kotlin
 * Row {
 *     KLabeledIconButton(KIcons.Crop, "자르기", onClick = ::crop)
 *     KLabeledIconButton(KIcons.Trash, "삭제", onClick = ::delete, tint = KineticaTheme.colors.destructive)
 * }
 * ```
 *
 * @param label 아이콘 아래 이름. 한 줄을 넘으면 말줄임표로 줄인다.
 * @param tint 아이콘과 이름 색. 지정하지 않으면 [LocalKineticaContentColor]다. 선택된 도구는 강조색, 삭제는 경고색처럼 항목마다 준다.
 * @param enabled `false`면 누를 수 없고 흐리게 그린다.
 * @param iconSize 아이콘 크기. 기본값은 20dp다.
 * @param shape 눌림 표시 모양
 */
@Composable
public fun KLabeledIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    iconSize: Dp = KMetrics.ButtonIcon,
    shape: Shape = shapeFor { KShapeToken.Medium },
) {
    KLabeledIconButton(rememberVectorPainter(icon), label, onClick, modifier, tint, enabled, iconSize, shape)
}

/**
 * 아이콘으로 `painterResource`로 읽은 drawable 같은 [Painter]를 받는 [KLabeledIconButton]. 아이콘은 [tint]로 칠하고, 그 밖의 규칙은 같다.
 *
 * ```kotlin
 * KLabeledIconButton(painterResource(R.drawable.ic_trim), "자르기", onClick = ::trim)
 * ```
 */
@Composable
public fun KLabeledIconButton(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    iconSize: Dp = KMetrics.ButtonIcon,
    shape: Shape = shapeFor { KShapeToken.Medium },
) {
    val ink = if (tint.isSpecified) tint else LocalKineticaContentColor.current
    val haptic = kHapticFeedback()
    Column(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .defaultMinSize(minWidth = MIN_WIDTH, minHeight = KMetrics.Touch)
            .clip(shape)
            .clickable(null, KIndication(shape, ink, PRESS_ALPHA), enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = SIDE_PADDING, vertical = VERTICAL_PADDING),
        verticalArrangement = Arrangement.spacedBy(ICON_LABEL_GAP, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(icon, null, Modifier.size(iconSize), colorFilter = ColorFilter.tint(ink))
        ThemedText(
            label,
            style = KineticaTheme.typography.captionSmall.copy(color = ink, textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val MIN_WIDTH = 56.dp
private val SIDE_PADDING = 8.dp
private val VERTICAL_PADDING = 6.dp
private val ICON_LABEL_GAP = 4.dp
private const val DISABLED_ALPHA = 0.4f
private const val PRESS_ALPHA = 0.12f
