// 파일 이름은 대표 컴포저블인 KRadio를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 라디오 버튼. 선택되면 테두리가 [tone] 색으로 바뀌고 가운데 점이 살짝 튀며 커진다. '애니메이션 삭제' 설정이 켜져 있으면 바로 바뀐다.
 *
 * [onClick]이 `null`이면 누를 수 없는 표시만 그린다. 행 전체를 `selectable`로 만들 때 쓴다([KRadioGroup], [KRadioRow]).
 * 그 밖에는 48dp 터치 영역을 갖고 TalkBack에 라디오 버튼으로 읽힌다. 여러 개를 함께 놓을 때는 부모에 `Modifier.selectableGroup()`을 붙인다.
 *
 * @param enabled `false`면 누를 수 없고 흐리게 그린다.
 * @param tone 선택됐을 때 색 역할
 * @param surface 선택되지 않은 테두리 색을 정하는 표면
 */
@Composable
public fun KRadio(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: KTone = KTone.Accent,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val colors = tone.colors(surface)
    val level = remember { Animatable(if (selected) 1f else 0f) }
    val still = reducedMotion()
    LaunchedEffect(selected, still) {
        val target = if (selected) 1f else 0f
        when {
            still -> level.snapTo(target)
            selected -> level.animateTo(target, spring(dampingRatio = DOT_DAMPING, stiffness = DOT_STIFFNESS))
            else -> level.animateTo(target, tween(DOT_OUT_MS))
        }
    }
    val idle = surface.contentColor.copy(alpha = CHOICE_IDLE_ALPHA)
    val shape = shapeFor { radio }
    Box(
        modifier
            .size(if (onClick != null) KMetrics.Touch else CHOICE_BOX + CHOICE_PAD)
            .then(
                if (onClick != null) {
                    Modifier.selectable(
                        selected,
                        interactionSource = null,
                        indication = KIndication(CenteredCircleShape(CHOICE_HALO_INSET), colors.color, CHOICE_HALO_ALPHA),
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .graphicsLayer { alpha = if (enabled) 1f else CHOICE_DISABLED_ALPHA }
            .drawWithCache {
                val box = CHOICE_BOX.toPx()
                val border = CHOICE_BORDER.toPx()
                val dot = DOT_RADIUS.toPx() * 2
                val ring = shape.createOutline(Size(box - border, box - border), layoutDirection, this)
                val fill = shape.createOutline(Size(dot, dot), layoutDirection, this)
                val stroke = Stroke(border)
                val center = Offset(size.width / 2, size.height / 2)
                onDrawBehind {
                    val t = level.value
                    val color = lerp(idle, colors.color, t.coerceIn(0f, 1f))
                    translate(center.x - (box - border) / 2, center.y - (box - border) / 2) { drawOutline(ring, color, style = stroke) }
                    if (t > 0f) {
                        scale(t, center) { translate(center.x - dot / 2, center.y - dot / 2) { drawOutline(fill, colors.color) } }
                    }
                }
            },
    )
}

/**
 * 하나만 고르는 라디오 목록. 행마다 라디오와 이름을 놓고 행 전체를 누를 수 있다. 행 높이는 48dp부터 시작해 글자가 커지면 함께 커진다.
 * TalkBack은 목록을 한 묶음으로 읽고 행마다 '선택됨'을 알린다.
 *
 * 목록 섹션 안에서 쓸 때는 [KRadioRow]를 [KListSection]에 넣는 편이 행 구분선과 모서리가 맞는다.
 *
 * @param options 항목 이름
 * @param selectedIndex 선택된 항목의 위치(0부터). 범위 밖이면 아무것도 선택되지 않은 상태로 그린다.
 * @param onSelect 항목을 눌렀을 때 그 위치를 받는다. 이미 선택된 항목을 눌러도 호출한다.
 * @param enabled `false`면 모든 행을 누를 수 없고 흐리게 그린다.
 */
@Composable
public fun KRadioGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: KTone = KTone.Accent,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    Column(modifier.selectableGroup()) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = KMetrics.Touch)
                    .selectable(
                        selected,
                        interactionSource = null,
                        indication = KIndication(GROUP_ROW_SHAPE),
                        enabled = enabled,
                        role = Role.RadioButton,
                    ) { onSelect(index) }
                    .padding(horizontal = GROUP_ROW_PADDING),
                horizontalArrangement = Arrangement.spacedBy(GROUP_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KRadio(selected, null, enabled = enabled, tone = tone, surface = surface)
                ThemedText(
                    label,
                    Modifier.weight(1f).padding(vertical = GROUP_TEXT_PADDING),
                    style = TextStyle(
                        color = surface.contentColor.copy(alpha = if (enabled) 1f else CHOICE_DISABLED_ALPHA),
                        fontSize = 16.sp,
                    ),
                )
            }
        }
    }
}

private val DOT_RADIUS = 5.dp
private const val DOT_DAMPING = 0.45f
private const val DOT_STIFFNESS = 900f
private const val DOT_OUT_MS = 120
private val GROUP_ROW_SHAPE: Shape
    @Composable
    @ReadOnlyComposable
    get() = currentShapes.shape(KShapeToken.Medium)
private val GROUP_ROW_PADDING = 8.dp
private val GROUP_GAP = 8.dp
private val GROUP_TEXT_PADDING = 10.dp
