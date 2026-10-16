// 파일 이름은 대표 컴포저블인 KButton을 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * [KButton]의 크기. 높이와 좌우 여백, 글자 크기가 함께 바뀐다.
 * Small은 32dp, Medium은 44dp, Large는 54dp 높이다. Small도 터치 영역은 레이아웃에 맡기므로, 촘촘하게 놓을 때는 주변 여백을 둔다.
 */
public enum class KButtonSize(
    internal val height: Dp,
    internal val padding: Dp,
    internal val font: TextUnit,
    internal val icon: Dp,
    internal val gap: Dp,
) {
    Small(32.dp, 14.dp, 14.sp, 16.dp, 6.dp),
    Medium(44.dp, 20.dp, 16.sp, KMetrics.ButtonIcon, KMetrics.IconTextGap),
    Large(54.dp, 28.dp, 18.sp, 22.dp, KMetrics.IconTextGap),
}

/**
 * 캡슐 모양 버튼. 누르면 젤리처럼 눌렸다가 출렁이며 돌아오고, 손을 떼 클릭이 되면 햅틱을 준다.
 *
 * [loading]이 `true`면 글자와 아이콘 자리에 도는 링이 나타나고 누를 수 없다. 글자는 투명하게 남겨 두므로 버튼 폭은 바뀌지 않는다.
 * TalkBack은 글자를 그대로 읽고 상태로 '불러오는 중'을 덧붙인다. '애니메이션 삭제' 접근성 설정이 켜져 있으면 링이 돌지 않는다.
 *
 * ```kotlin
 * KButton("결제하기", onClick = ::pay, loading = paying)
 * KButton("삭제", onClick = ::delete, variant = KVariant.Outlined, tone = KTone.Destructive)
 * ```
 *
 * @param variant 모양. 기본값은 [KVariant.Filled]다. [KVariant.Glass]와 [KVariant.Elevated]는 [surface]로 그린다.
 * @param tone 색 역할. 기본값은 [KTone.Accent]다. 색은 [KineticaTheme]의 [KineticaColors]에서 가져온다.
 * @param icon 글자 앞에 놓을 아이콘. 글자와 같은 색으로 칠한다.
 * @param enabled `false`면 누를 수 없고 흐리게 그린다.
 */
@Composable
public fun KButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: KVariant = KVariant.Filled,
    tone: KTone = KTone.Accent,
    size: KButtonSize = KButtonSize.Medium,
    icon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val active = enabled && !loading
    val loadingLabel = stringResource(R.string.kinetica_ui_loading)
    val loadingLevel by animateFloatAsState(if (loading) 1f else 0f, tween(LOADING_FADE_MS), label = "button-loading")
    val colors = tone.colors(surface)
    val content = variantContent(variant, tone, colors, surface)
    Box(
        modifier
            .then(if (active) Modifier.jellyPress(JELLY_AMOUNT) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .defaultMinSize(minWidth = size.height)
            .height(size.height)
            .variantBackground(variant, colors, surface, CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                // Text와 Outlined는 채운 배경이 없으므로 누르는 동안 깔리는 옅은 배경이 눌린 걸 알리는 유일한 표시다.
                indication = KIndication(CircleShape, content, if (variant.bare) PLAIN_PRESS_ALPHA else PRESS_ALPHA),
                enabled = active,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .semantics { if (loading) stateDescription = loadingLabel }
            // 앞 아이콘은 글자보다 시각적 무게가 가벼워, 시작 쪽 여백을 조금 줄여야 내용이 가운데로 보인다.
            .padding(start = if (icon != null) size.padding - ICON_SIDE_TRIM else size.padding, end = size.padding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.graphicsLayer { alpha = 1f - loadingLevel },
            horizontalArrangement = Arrangement.spacedBy(size.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Image(rememberVectorPainter(it), null, Modifier.size(size.icon), colorFilter = ColorFilter.tint(content)) }
            ThemedText(
                text,
                style = TextStyle(color = content, fontSize = size.font, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (loadingLevel > 0f) LoadingRing(content, loadingLevel, Modifier.size(size.icon))
    }
}

/** 버튼 안에서 도는 링. [level]은 나타난 정도(0..1)이다. */
@Composable
private fun LoadingRing(color: Color, level: Float, modifier: Modifier) {
    val still = reducedMotion()
    val angle = rememberInfiniteTransition(label = "button-ring").animateFloat(
        0f,
        if (still) 0f else FULL_TURN,
        infiniteRepeatable(tween(RING_PERIOD_MS, easing = LinearEasing)),
        label = "angle",
    )
    Box(
        modifier
            .graphicsLayer {
                alpha = level
                rotationZ = angle.value
            }
            .drawBehind { drawSpinnerArc(color) },
    )
}

/** 4분의 3쯤 열린 원호를 그린다. 회전은 바깥 graphicsLayer가 맡는다. */
private fun DrawScope.drawSpinnerArc(color: Color) {
    val stroke = size.minDimension * RING_STROKE
    val inset = stroke / 2
    drawArc(
        color,
        startAngle = -QUARTER_TURN,
        sweepAngle = RING_SWEEP,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
}

private val ICON_SIDE_TRIM = 4.dp
private const val JELLY_AMOUNT = 0.07f
private const val DISABLED_ALPHA = 0.4f
private const val PRESS_ALPHA = 0.12f
private const val PLAIN_PRESS_ALPHA = 0.1f
private const val LOADING_FADE_MS = 160
private const val RING_PERIOD_MS = 800
private const val RING_STROKE = 0.14f
private const val RING_SWEEP = 270f
private const val QUARTER_TURN = 90f
private const val FULL_TURN = 360f
