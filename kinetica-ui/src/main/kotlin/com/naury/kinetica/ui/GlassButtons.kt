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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
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

/** [GlassButton]의 채우기 방식. */
public enum class GlassButtonKind {
    /** 강조색으로 꽉 채우고 글자는 흰색이다. 화면에서 가장 중요한 동작 하나에 쓴다. */
    Filled,

    /** 강조색을 옅게 깔고 글자를 강조색으로 쓴다. 두 번째로 중요한 동작에 쓴다. */
    Tinted,

    /** 글래스 표면에 [GlassStyle.onGlass] 색 글자를 쓴다. 사진이나 그라디언트 위에 올리는 버튼에 어울린다. */
    Glass,

    /** 배경 없이 강조색 글자만 쓴다. 누르는 동안에만 옅은 배경이 보인다. */
    Plain,
}

/**
 * [GlassButton]의 크기. 높이와 좌우 여백, 글자 크기가 함께 바뀐다.
 * Small은 32dp, Medium은 44dp, Large는 54dp 높이다. Small도 터치 영역은 레이아웃에 맡기므로, 촘촘하게 놓을 때는 주변 여백을 둔다.
 */
public enum class GlassButtonSize(internal val height: Dp, internal val padding: Dp, internal val font: TextUnit, internal val icon: Dp) {
    Small(32.dp, 14.dp, 14.sp, 16.dp),
    Medium(44.dp, 20.dp, 16.sp, 20.dp),
    Large(54.dp, 28.dp, 18.sp, 22.dp),
}

/**
 * 캡슐 모양 글래스 버튼. 누르면 젤리처럼 눌렸다가 출렁이며 돌아오고, 손을 떼 클릭이 되면 햅틱을 준다.
 *
 * [loading]이 `true`면 글자와 아이콘 자리에 도는 링이 나타나고 누를 수 없다. 글자는 투명하게 남겨 두므로 버튼 폭은 바뀌지 않는다.
 * TalkBack은 글자를 그대로 읽고 상태로 '불러오는 중'을 덧붙인다. '애니메이션 삭제' 접근성 설정이 켜져 있으면 링이 돌지 않는다.
 *
 * ```kotlin
 * GlassButton("결제하기", onClick = ::pay, kind = GlassButtonKind.Filled, loading = paying)
 * ```
 *
 * @param icon 글자 앞에 놓을 아이콘. [GlassButtonKind]에 맞는 글자 색으로 칠한다.
 * @param enabled `false`면 누를 수 없고 흐리게 그린다.
 * @param accent [GlassButtonKind.Filled], [GlassButtonKind.Tinted], [GlassButtonKind.Plain]에 쓰는 강조색. 기본값은 iOS 파랑이다.
 */
@Composable
public fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: GlassButtonKind = GlassButtonKind.Filled,
    size: GlassButtonSize = GlassButtonSize.Medium,
    icon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
    accent: Color = Color(BUTTON_BLUE),
    style: GlassStyle = LocalGlassStyle.current,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val active = enabled && !loading
    val loadingLabel = stringResource(R.string.kinetica_ui_loading)
    val loadingLevel by animateFloatAsState(if (loading) 1f else 0f, tween(LOADING_FADE_MS), label = "button-loading")
    val content = buttonContentColor(kind, accent, style)
    Box(
        modifier
            .then(if (active) Modifier.jellyPress(JELLY_AMOUNT) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .defaultMinSize(minWidth = size.height)
            .height(size.height)
            .buttonBackground(kind, accent, style)
            .clip(CircleShape)
            .drawBehind {
                // Plain은 배경이 없으므로 누르는 동안만 옅은 배경을 깔아 눌린 걸 알린다.
                val pressAlpha = if (kind == GlassButtonKind.Plain) PLAIN_PRESS_ALPHA else PRESS_ALPHA
                if (pressed) drawRect(content.copy(alpha = pressAlpha))
            }
            .clickable(interactionSource = interaction, indication = null, enabled = active, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .semantics { if (loading) stateDescription = loadingLabel }
            .padding(horizontal = size.padding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.graphicsLayer { alpha = 1f - loadingLevel },
            horizontalArrangement = Arrangement.spacedBy(ICON_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Image(rememberVectorPainter(it), null, Modifier.size(size.icon), colorFilter = ColorFilter.tint(content)) }
            BasicText(
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

private fun buttonContentColor(kind: GlassButtonKind, accent: Color, style: GlassStyle): Color = when (kind) {
    GlassButtonKind.Filled -> Color.White
    GlassButtonKind.Tinted, GlassButtonKind.Plain -> accent
    GlassButtonKind.Glass -> style.onGlass
}

@Composable
private fun Modifier.buttonBackground(kind: GlassButtonKind, accent: Color, style: GlassStyle): Modifier = when (kind) {
    GlassButtonKind.Filled -> background(accent, CircleShape)
    GlassButtonKind.Tinted -> background(accent.copy(alpha = TINT_ALPHA), CircleShape)
    GlassButtonKind.Glass -> glass(style, CircleShape)
    GlassButtonKind.Plain -> this
}

private val ICON_GAP = 6.dp
private const val JELLY_AMOUNT = 0.07f
private const val DISABLED_ALPHA = 0.4f
private const val PRESS_ALPHA = 0.12f
private const val PLAIN_PRESS_ALPHA = 0.1f
private const val TINT_ALPHA = 0.16f
private const val LOADING_FADE_MS = 160
private const val RING_PERIOD_MS = 800
private const val RING_STROKE = 0.14f
private const val RING_SWEEP = 270f
private const val QUARTER_TURN = 90f
private const val FULL_TURN = 360f
private const val BUTTON_BLUE = 0xFF0A84FF
