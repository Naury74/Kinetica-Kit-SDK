// 파일 이름은 대표 컴포저블인 KCallControlButton이 있는 조작 버튼 묶음을 뜻한다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** [KCallControlButton]과 [KCallDock]의 기본값 */
public object KCallControlDefaults {
    /** 원 지름 기본값. 도크가 좁으면 [CompactSize]까지 줄인다. */
    public val Size: Dp = 56.dp

    /** 좁은 도크에서 쓰는 원 지름. 최소 터치 영역에 맞춘 값이다. */
    public val CompactSize: Dp = 48.dp

    /** 수신 화면 받기와 거절처럼 화면의 주 동작에 쓰는 원 지름 */
    public val LargeSize: Dp = 72.dp

    /** 이름표 글꼴이 시스템 글자 크기를 따라 커지는 최대 배율. 도크 폭은 고정이라 더 키우면 이름이 잘린다. */
    public const val MAX_LABEL_SCALE: Float = 1.3f
}

/**
 * 통화 조작 버튼. 원 안의 아이콘과 그 아래 이름표로 이루어진다. 음소거, 카메라, 스피커, 화면 공유처럼 켜고 끄는 조작과
 * 카메라 전환, 끊기 같은 한 번 누르는 조작에 모두 쓴다.
 *
 * [checked]가 true면 원이 표면 글자 색으로 채워지고 아이콘이 반전된다(음소거된 마이크처럼 '평소와 다른' 상태를 표시하는 데 쓴다).
 * [pending]이면 원 둘레가 맥박처럼 옅어졌다 진해진다. 엔진이 결과를 확인하기 전 상태에 쓴다.
 * [tone]이 [KTone.Destructive]나 [KTone.Positive]면 늘 그 색으로 채운다(끊기, 받기). [KTone.Accent]는 켜졌을 때 강조색으로 채운다.
 * 누르면 원이 젤리처럼 눌리고 햅틱을 준다. TalkBack은 버튼 하나로 '마이크, 꺼짐'처럼 이름과 상태, 배지 수를 함께 읽는다.
 *
 * ```kotlin
 * KCallControlButton(KIcons.Mic, "마이크", onClick = vm::toggleMute, checked = muted, checkedIcon = KIcons.MicOff)
 * KCallControlButton(KIcons.CallEnd, "종료", onClick = vm::hangUp, tone = KTone.Destructive)
 * ```
 *
 * @param label 이름표와 TalkBack 이름. 이름표는 한 줄이고 넘치면 말줄임한다.
 * @param checked 켜짐 상태. null이면 상태 없는 동작 버튼이다.
 * @param checkedIcon [checked]일 때 그릴 아이콘. null이면 [icon]을 쓴다.
 * @param badge 원 모서리에 겹칠 숫자. 0 이하면 숨긴다.
 * @param size 원 지름. 48dp보다 작으면 48dp로 맞춘다.
 * @param showLabel false면 이름표를 그리지 않는다. TalkBack 이름은 그대로 [label]이다.
 * @param stateDescription TalkBack이 읽을 상태. null이면 [checked]와 [pending]으로 '켜짐', '꺼짐', '바꾸는 중'을 고른다.
 * @param badgeDescription TalkBack이 [badge] 대신 읽을 문구. null이면 '새 항목 3개'처럼 읽고, 빈 문자열이면 배지를 읽지 않는다
 *   ([stateDescription]에 이미 개수를 넣었을 때).
 */
@Composable
public fun KCallControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    checked: Boolean? = null,
    checkedIcon: ImageVector? = null,
    tone: KTone = KTone.Neutral,
    pending: Boolean = false,
    badge: Int = 0,
    enabled: Boolean = true,
    size: Dp = KCallControlDefaults.Size,
    showLabel: Boolean = true,
    stateDescription: String? = null,
    badgeDescription: String? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val haptic = kHapticFeedback()
    val diameter = size.coerceAtLeast(KCallControlDefaults.CompactSize)
    val shape = shapeFor { button }
    val interaction = remember { MutableInteractionSource() }
    val state = stateDescription ?: callStateLabel(checked, pending)
    val badgeLabel = when {
        badge <= 0 -> null
        badgeDescription != null -> badgeDescription.takeIf { it.isNotEmpty() }
        else -> pluralStringResource(R.plurals.kinetica_ui_call_badge, badge, badge)
    }
    val click = {
        haptic.performHapticFeedback(if (tone == KTone.Destructive) HapticFeedbackType.LongPress else HapticFeedbackType.ContextClick)
        onClick()
    }
    Column(
        modifier
            .width(diameter + LABEL_SIDE)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = click)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                listOfNotNull(state, badgeLabel).joinToString(", ").takeIf { it.isNotEmpty() }?.let { this.stateDescription = it }
                if (!enabled) disabled()
                onClick(label) {
                    click()
                    true
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LABEL_GAP),
    ) {
        ControlCircle(ControlLook(icon, checkedIcon, checked == true, tone, pending, diameter, shape, surface), interaction, badge)
        if (showLabel) ControlLabel(label, surface.contentColor)
    }
}

/** 원 모양을 정하는 값 묶음 */
@Suppress("LongParameterList") // 원 하나를 그리는 데 필요한 값을 한 번에 넘기려고 묶는다.
private class ControlLook(
    val icon: ImageVector,
    val checkedIcon: ImageVector?,
    val checked: Boolean,
    val tone: KTone,
    val pending: Boolean,
    val diameter: Dp,
    val shape: Shape,
    val surface: SurfaceStyle,
)

@Composable
private fun ControlCircle(look: ControlLook, interaction: MutableInteractionSource, badge: Int) {
    val surface = look.surface
    val tone = look.tone
    val neutral = KTone.Neutral.colors(surface)
    val filledTone = tone == KTone.Destructive || tone == KTone.Positive
    val onColors = if (tone == KTone.Neutral) ToneColors(surface.contentColor, surface.inkBackground()) else tone.colors(surface)
    val restInk = if (filledTone) onColors.onColor else surface.contentColor
    val level = rememberCheckedLevel(look.checked || filledTone)
    val pulse = rememberPendingPulse(look.pending)
    val ring = KineticaTheme.colors.accent
    Box(
        Modifier
            .jellyPress(KineticaTheme.design.motion.press * PRESS_BOOST)
            .size(look.diameter)
            // 테두리(Brutal 등)가 배지를 덮지 않도록 배지를 바탕보다 바깥에서 그린다.
            .kBadge(badge.takeIf { it > 0 })
            .variantBackground(KVariant.Tonal, neutral, surface, look.shape)
            .drawWithCache {
                val outline = look.shape.createOutline(size, layoutDirection, this)
                val stroke = Stroke(PENDING_RING.toPx())
                onDrawBehind {
                    val v = level.value
                    if (v > 0f) scale(v.coerceAtMost(MAX_FILL)) { drawOutline(outline, onColors.color.copy(alpha = v.coerceIn(0f, 1f))) }
                    val p = pulse.value
                    if (p > 0f) drawOutline(outline, ring.copy(alpha = p), style = stroke)
                }
            }
            .indication(interaction, KIndication(look.shape, restInk, PRESS_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        val iconSize = Modifier.size(look.diameter * ICON_RATIO)
        val pendingAlpha = if (look.pending) PENDING_ICON_ALPHA else 1f
        if (!filledTone) {
            Image(
                rememberVectorPainter(look.icon),
                null,
                iconSize.graphicsLayer { alpha = (1f - level.value).coerceIn(0f, 1f) * pendingAlpha },
                colorFilter = ColorFilter.tint(restInk),
            )
        }
        Image(
            rememberVectorPainter(if (look.checked) look.checkedIcon ?: look.icon else look.icon),
            null,
            iconSize.graphicsLayer { alpha = level.value.coerceIn(0f, 1f) * pendingAlpha },
            colorFilter = ColorFilter.tint(onColors.onColor),
        )
    }
}

/** 이름표. 도크 폭이 고정이라 글꼴은 [KCallControlDefaults.MAX_LABEL_SCALE]배까지만 키운다. */
@Composable
private fun ControlLabel(label: String, ink: Color) {
    val fontScale = LocalDensity.current.fontScale
    val size = if (fontScale >
        KCallControlDefaults.MAX_LABEL_SCALE
    ) {
        LABEL_SIZE.value * KCallControlDefaults.MAX_LABEL_SCALE / fontScale
    } else {
        LABEL_SIZE.value
    }
    ThemedText(
        label,
        style = TextStyle(color = ink, fontSize = size.sp, textAlign = TextAlign.Center, fontWeight = KineticaTheme.design.idleWeight),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun callStateLabel(checked: Boolean?, pending: Boolean): String? = when {
    pending -> stringResource(R.string.kinetica_ui_call_state_pending)
    checked == true -> stringResource(R.string.kinetica_ui_call_state_on)
    checked == false -> stringResource(R.string.kinetica_ui_call_state_off)
    else -> null
}

/** 켜짐 정도(0..1). 가운데에서 퍼지며 채워진다. '애니메이션 삭제'면 바로 바뀐다. */
@Composable
private fun rememberCheckedLevel(checked: Boolean): Animatable<Float, *> {
    val level = remember { Animatable(if (checked) 1f else 0f) }
    val still = reducedMotion()
    LaunchedEffect(checked, still) {
        val target = if (checked) 1f else 0f
        if (still) level.snapTo(target) else level.animateTo(target, spring(dampingRatio = FILL_DAMPING, stiffness = FILL_STIFFNESS))
    }
    return level
}

/** 진행 중 테두리의 진하기. 그리기 단계에서 읽으므로 맥박이 뛰어도 다시 구성하지 않는다. */
@Composable
private fun rememberPendingPulse(pending: Boolean): State<Float> {
    if (!pending) return remember { mutableFloatStateOf(0f) }
    if (reducedMotion()) return remember { mutableFloatStateOf(PULSE_MAX) }
    return rememberInfiniteTransition(label = "call-pending").animateFloat(
        PULSE_MIN,
        PULSE_MAX,
        infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
        label = "pulse",
    )
}

private val LABEL_SIDE = 8.dp
private val LABEL_GAP = 6.dp
private val LABEL_SIZE = 12.sp
private val PENDING_RING = 2.dp
private const val ICON_RATIO = 0.43f
private const val DISABLED_ALPHA = 0.4f
private const val PRESS_ALPHA = 0.14f
private const val PRESS_BOOST = 1.6f
private const val MAX_FILL = 1.2f
private const val FILL_DAMPING = 0.62f
private const val FILL_STIFFNESS = 520f
private const val PENDING_ICON_ALPHA = 0.6f
private const val PULSE_MIN = 0.25f
private const val PULSE_MAX = 1f
private const val PULSE_MS = 600
