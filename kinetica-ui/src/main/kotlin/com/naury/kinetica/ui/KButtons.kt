// 파일 이름은 대표 컴포저블인 KButton을 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
 * [success]가 `true`가 되면 버튼이 가운데로 줄어 원이 되고 [KineticaColors.positive]로 물들며 체크 표시가 그려진다. 레이아웃 폭은 그대로라
 * 이웃 요소가 움직이지 않는다. 그동안 누를 수 없고 TalkBack은 '완료'를 덧붙인다. `false`로 돌리면 원래 버튼으로 펼쳐진다.
 * 보통 [loading]에서 바로 [success]로 넘긴 뒤 1~2초 뒤 되돌린다.
 *
 * 글자가 표면 위에 놓이는 [KVariant.Tonal], [KVariant.Outlined], [KVariant.Text]에서는 색 역할의 색이 표면과 대비 3:1이 안 되면
 * (흰 표면 위 초록처럼) 글자 색을 조금 어둡게(어두운 표면이면 밝게) 고쳐 쓴다.
 *
 * ```kotlin
 * KButton("결제하기", onClick = ::pay, loading = paying, success = paid)
 * KButton("삭제", onClick = ::delete, variant = KVariant.Outlined, tone = KTone.Destructive)
 * KButton("다음", onClick = ::next, trailingIcon = Icons.Arrow, fullWidth = true, size = KButtonSize.Large)
 * ```
 *
 * @param variant 모양. 기본값은 [KVariant.Filled]다. [KVariant.Glass]와 [KVariant.Elevated]는 [surface]로 그린다.
 * @param tone 색 역할. 기본값은 [KTone.Accent]다. 색은 [KineticaTheme]의 [KineticaColors]에서 가져온다.
 * @param icon 글자 앞에 놓을 아이콘. 글자와 같은 색으로 칠한다.
 * @param trailingIcon 글자 뒤에 놓을 아이콘. 다음 단계로 넘어가는 화살표나 펼침 꺾쇠에 쓴다. RTL에서도 그대로 그리므로 방향이 있는 아이콘은 호출한 쪽이 뒤집는다.
 * @param enabled `false`면 누를 수 없고 흐리게 그린다.
 * @param fullWidth `true`면 받은 폭을 모두 채운다. 내용은 가운데에 놓는다. 시트나 폼 아래의 주 동작에 쓴다.
 * @param success `true`면 원으로 줄어 체크 표시를 보여 준다. [loading]보다 우선한다.
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
    trailingIcon: ImageVector? = null,
    loading: Boolean = false,
    success: Boolean = false,
    enabled: Boolean = true,
    fullWidth: Boolean = false,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val haptic = LocalHapticFeedback.current
    val active = enabled && !loading && !success
    val busyLabel = stringResource(if (success) R.string.kinetica_ui_done else R.string.kinetica_ui_loading)
    val loadingLevel by animateFloatAsState(if (loading && !success) 1f else 0f, tween(LOADING_FADE_MS), label = "button-loading")
    val morph = rememberSuccessMorph(success)
    val colors = tone.colors(surface)
    val content = buttonInk(variant, tone, colors, surface)
    val positive = KineticaTheme.colors
    Box(
        modifier
            .then(if (active) Modifier.jellyPress(JELLY_AMOUNT) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .successMorph(morph, positive.positive, positive.onPositive)
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .defaultMinSize(minWidth = size.height)
            .height(size.height)
            .variantBackground(variant, colors, surface, CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = null,
                // Text와 Outlined는 채운 배경이 없으므로 누르는 동안 깔리는 옅은 배경이 눌린 걸 알리는 유일한 표시다.
                indication = KIndication(CircleShape, content, if (variant.bare) PLAIN_PRESS_ALPHA else PRESS_ALPHA),
                enabled = active,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .semantics { if (loading || success) stateDescription = busyLabel }
            // 앞 아이콘은 글자보다 시각적 무게가 가벼워, 그쪽 여백을 조금 줄여야 내용이 가운데로 보인다.
            .padding(start = size.sidePadding(icon != null), end = size.sidePadding(trailingIcon != null)),
        contentAlignment = Alignment.Center,
    ) {
        ButtonLabel(text, icon, trailingIcon, size, content) { 1f - maxOf(loadingLevel, morph.value) }
        if (loadingLevel > 0f) LoadingRing(content, loadingLevel, Modifier.size(size.icon))
    }
}

/** 아이콘이 있는 쪽 여백. 아이콘은 글자보다 시각적 무게가 가벼워 조금 줄여야 내용이 가운데로 보인다. */
private fun KButtonSize.sidePadding(hasIcon: Boolean): Dp = if (hasIcon) padding - ICON_SIDE_TRIM else padding

/** 버튼 글자와 앞뒤 아이콘. [alpha]는 그리기 단계에서 읽는다. */
@Composable
private fun ButtonLabel(text: String, icon: ImageVector?, trailing: ImageVector?, size: KButtonSize, ink: Color, alpha: () -> Float) {
    Row(
        Modifier.graphicsLayer { this.alpha = alpha().coerceIn(0f, 1f) },
        horizontalArrangement = Arrangement.spacedBy(size.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Image(rememberVectorPainter(it), null, Modifier.size(size.icon), colorFilter = ColorFilter.tint(ink)) }
        ThemedText(
            text,
            Modifier.weight(1f, fill = false),
            style = TextStyle(color = ink, fontSize = size.font, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.let { Image(rememberVectorPainter(it), null, Modifier.size(size.icon), colorFilter = ColorFilter.tint(ink)) }
    }
}

/** 표면 위에 놓이는 글자는 대비를 맞춘 색을 쓴다. 채운 모양은 [ToneColors.onColor] 그대로다. */
internal fun buttonInk(variant: KVariant, tone: KTone, colors: ToneColors, surface: SurfaceStyle): Color {
    val ink = variantContent(variant, tone, colors, surface)
    return if (variant == KVariant.Filled) ink else readableOn(ink, variantInkBackground(variant, colors, surface))
}

/** 성공 모핑 진행(0..1). '애니메이션 삭제'면 바로 바뀐다. */
@Composable
private fun rememberSuccessMorph(success: Boolean): Animatable<Float, AnimationVector1D> {
    val morph = remember { Animatable(if (success) 1f else 0f) }
    val still = reducedMotion()
    LaunchedEffect(success, still) {
        val target = if (success) 1f else 0f
        if (still) morph.snapTo(target) else morph.animateTo(target, spring(dampingRatio = MORPH_DAMPING, stiffness = MORPH_STIFFNESS))
    }
    return morph
}

/**
 * [morph]만큼 버튼을 가운데로 좁혀 원으로 만들고, 그 위에 [fill]을 덮어 체크 표시를 그린다.
 * 레이아웃은 건드리지 않고 그리기 단계에서 잘라 내므로 이웃 요소가 움직이지 않는다.
 */
private fun Modifier.successMorph(morph: Animatable<Float, AnimationVector1D>, fill: Color, ink: Color): Modifier = this
    .graphicsLayer {
        val level = morph.value
        clip = level > 0f
        if (level > 0f) shape = MorphPillShape(level.coerceIn(0f, 1f))
    }
    .drawWithContent {
        drawContent()
        val level = morph.value
        if (level <= 0f) return@drawWithContent
        drawRect(fill.copy(alpha = (level * FILL_SPEED).coerceIn(0f, 1f)))
        val box = size.height * CHECK_BOX
        val check = ((level - CHECK_START) / (1f - CHECK_START)).coerceIn(0f, 1f)
        drawCheckStroke(ink, check, Offset((size.width - box) / 2, (size.height - box) / 2), box, size.height * CHECK_STROKE)
    }

/** 폭이 [level]만큼 높이 쪽으로 좁아진 가운데 캡슐. 1이면 지름이 높이인 원이다. */
private class MorphPillShape(private val level: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val target = minOf(size.width, size.height)
        val width = size.width + (target - size.width) * level
        val left = (size.width - width) / 2
        return Outline.Rounded(RoundRect(left, 0f, left + width, size.height, CornerRadius(size.height / 2)))
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
private const val MORPH_DAMPING = 0.78f
private const val MORPH_STIFFNESS = 320f
private const val FILL_SPEED = 2.2f
private const val CHECK_START = 0.45f
private const val CHECK_BOX = 0.46f
private const val CHECK_STROKE = 0.06f
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
