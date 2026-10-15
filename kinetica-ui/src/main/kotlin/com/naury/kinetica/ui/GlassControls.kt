package com.naury.kinetica.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 글래스 세그먼트 버튼. 보기 방식 전환 같은 곳에 쓴다. 선택 인디케이터가 [GlassTabBar]처럼 늘어났다 줄어들며 이동하고,
 * TalkBack은 항목마다 선택 상태를 읽는다.
 *
 * @param options 항목 이름. 두 개 미만이면 [IllegalArgumentException]을 던진다.
 * @param selectedIndex 선택된 항목의 위치(0부터)
 */
@Composable
public fun GlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: GlassStyle = LocalGlassStyle.current,
) {
    require(options.size >= 2) { "options는 두 개 이상이어야 합니다." }
    var width by remember { mutableIntStateOf(0) }
    val indicator = remember { LiquidIndicator() }
    LaunchedEffect(selectedIndex, width, options.size) { indicator.moveTo(selectedIndex, options.size, width.toFloat()) }
    Box(modifier.height(SEGMENT_HEIGHT).glass(style, CircleShape).padding(SEGMENT_INSET)) {
        Box(
            Modifier
                .matchParentSize()
                .onSizeChanged { width = it.width }
                .drawBehind {
                    val left = indicator.left.value
                    val right = indicator.right.value
                    if (right > left) {
                        drawRoundRect(
                            style.onGlass.copy(alpha = SEGMENT_ALPHA),
                            Offset(left, 0f),
                            Size(right - left, size.height),
                            CornerRadius(size.height / 2),
                        )
                    }
                },
        )
        Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected,
                            role = Role.Tab,
                            interactionSource = remember {
                                MutableInteractionSource()
                            },
                            indication = null,
                        ) {
                            onSelect(index)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        label,
                        style = TextStyle(
                            color = style.onGlass.copy(alpha = if (selected) 1f else UNSELECTED),
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * 글래스 스위치. 누르고 있는 동안 손잡이가 옆으로 늘어나고, 켜고 끌 때 iOS 26 스위치처럼 출렁이며 넘어간다.
 * 켜지면 트랙이 [onColor]로 바뀐다.
 */
@Composable
public fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onColor: Color = Color(ON_GREEN),
    style: GlassStyle = LocalGlassStyle.current,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val position = remember { Animatable(if (checked) 1f else 0f) }
    val stretch = remember { Animatable(0f) }
    LaunchedEffect(checked) {
        position.animateTo(if (checked) 1f else 0f, spring(dampingRatio = SWITCH_DAMPING, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(pressed) { stretch.animateTo(if (pressed) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    val track by animateColorAsState(if (checked) onColor else style.onGlass.copy(alpha = OFF_ALPHA), label = "switch-track")
    Box(
        modifier
            .size(SWITCH_WIDTH, SWITCH_HEIGHT)
            .toggleable(checked, interactionSource = interaction, indication = null, role = Role.Switch, onValueChange = onCheckedChange)
            .drawBehind {
                drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
                val inset = THUMB_INSET.toPx()
                val thumbHeight = size.height - inset * 2
                val thumbWidth = thumbHeight * (1f + THUMB_STRETCH * stretch.value)
                val travel = size.width - inset * 2 - thumbWidth
                val left = inset + travel * position.value
                drawRoundRect(
                    Color.Black.copy(alpha = THUMB_SHADOW),
                    Offset(left, inset + THUMB_DROP.toPx()),
                    Size(thumbWidth, thumbHeight),
                    CornerRadius(thumbHeight / 2),
                )
                drawRoundRect(Color.White, Offset(left, inset), Size(thumbWidth, thumbHeight), CornerRadius(thumbHeight / 2))
            },
    )
}

private val SEGMENT_HEIGHT = 44.dp
private val SEGMENT_INSET = 4.dp
private const val SEGMENT_ALPHA = 0.14f
private const val UNSELECTED = 0.6f
private val SWITCH_WIDTH = 56.dp
private val SWITCH_HEIGHT = 32.dp
private val THUMB_INSET = 3.dp
private val THUMB_DROP = 1.dp
private const val THUMB_STRETCH = 0.35f
private const val THUMB_SHADOW = 0.15f
private const val SWITCH_DAMPING = 0.62f
private const val OFF_ALPHA = 0.18f
private const val ON_GREEN = 0xFF34C759
