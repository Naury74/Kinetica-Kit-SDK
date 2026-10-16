// 파일 이름은 대표 컴포저블인 KIconToggleButton를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 원형 아이콘 버튼 지름. [KIconButton]과 [KIconToggleButton]의 `size`에 넘긴다. 아이콘은 지름의 절반 크기로 그린다. */
public object KIconButtonSizes {
    /** 36dp. 카드 위 닫기 버튼처럼 좁은 곳에 쓴다. 주변에 4dp 이상 여백을 두어 터치 영역을 지킨다. */
    public val Small: Dp = KMetrics.SmallIconButton

    /** 44dp. 기본 크기 */
    public val Medium: Dp = KMetrics.IconButton

    /** 56dp. 미디어 재생처럼 화면의 주 동작에 쓴다. */
    public val Large: Dp = 56.dp
}

/**
 * 켜고 끄는 아이콘 버튼. 즐겨찾기, 음소거, 북마크처럼 상태가 있는 동작에 쓴다.
 * 켜지면 [tone] 색이 가운데에서 퍼지며 버튼을 채우고, 아이콘이 [checkedIcon]으로 바뀐다. 꺼진 동안은 [variant] 모양에 중립 색 아이콘을 그린다.
 * 누르면 햅틱을 주고, TalkBack은 체크박스처럼 '선택됨'과 '선택 안 됨'을 읽는다. '애니메이션 삭제' 설정에서는 바로 바뀐다.
 *
 * ```kotlin
 * KIconToggleButton(saved, { saved = it }, Icons.BookmarkOutline, "저장", checkedIcon = Icons.Bookmark)
 * ```
 *
 * @param checked 켜져 있는지
 * @param onCheckedChange 새 상태를 받는다.
 * @param contentDescription TalkBack이 읽을 이름. 상태는 따로 읽으므로 '저장'처럼 동작 이름만 쓴다.
 * @param checkedIcon 켜졌을 때 그릴 아이콘. null이면 [icon]을 그대로 쓴다. 채운 아이콘을 넘기면 상태가 더 잘 보인다.
 * @param size 버튼 지름. [KIconButtonSizes] 값을 권장하고, 32dp보다 작으면 32dp로 맞춘다.
 * @param variant 꺼졌을 때 모양. 기본값은 [KVariant.Glass]다. 켜지면 늘 [tone] 색으로 채운다.
 * @param shape 버튼 모양. 기본값은 원이다.
 */
@Composable
public fun KIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    checkedIcon: ImageVector? = null,
    variant: KVariant = KVariant.Glass,
    tone: KTone = KTone.Accent,
    size: Dp = KMetrics.IconButton,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val diameter = size.coerceAtLeast(MIN_TOGGLE)
    val haptic = LocalHapticFeedback.current
    val on = tone.colors(surface)
    val neutral = KTone.Neutral.colors(surface)
    val offInk = buttonInk(variant, KTone.Neutral, neutral, surface)
    val level = rememberToggleLevel(checked)
    val fill = on.color
    Box(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED }
            .size(diameter)
            .variantBackground(variant, neutral, surface, shape)
            .clip(shape)
            .drawWithCache {
                val outline = shape.createOutline(this.size, layoutDirection, this)
                onDrawBehind {
                    val v = level.value
                    if (v > 0f) scale(v.coerceAtMost(MAX_FILL_SCALE)) { drawOutline(outline, fill.copy(alpha = v.coerceIn(0f, 1f))) }
                }
            }
            .toggleable(checked, null, KIndication(shape, offInk, PRESS_ALPHA), enabled, Role.Checkbox) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onCheckedChange(it)
            }
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val iconSize = Modifier.size(diameter / 2)
        Image(
            rememberVectorPainter(icon),
            null,
            iconSize.graphicsLayer { alpha = (1f - level.value).coerceIn(0f, 1f) },
            colorFilter = ColorFilter.tint(offInk),
        )
        Image(
            rememberVectorPainter(checkedIcon ?: icon),
            null,
            iconSize.graphicsLayer {
                val v = level.value
                alpha = v.coerceIn(0f, 1f)
                // 채워질 때 아이콘이 살짝 커졌다 돌아와 눌린 결과가 손에 느껴진다.
                val pop = 1f + POP * (v * (1f - v)) * PEAK_NORM
                scaleX = pop
                scaleY = pop
            },
            colorFilter = ColorFilter.tint(on.onColor),
        )
    }
}

@Composable
private fun rememberToggleLevel(checked: Boolean): Animatable<Float, *> {
    val level = remember { Animatable(if (checked) 1f else 0f) }
    val still = reducedMotion()
    LaunchedEffect(checked, still) {
        val target = if (checked) 1f else 0f
        if (still) level.snapTo(target) else level.animateTo(target, spring(dampingRatio = DAMPING, stiffness = STIFFNESS))
    }
    return level
}

private val MIN_TOGGLE = 32.dp
private const val DISABLED = 0.4f
private const val PRESS_ALPHA = 0.12f
private const val DAMPING = 0.62f
private const val STIFFNESS = 520f
private const val POP = 0.18f
private const val MAX_FILL_SCALE = 1.2f

// v(1 - v)는 v = 0.5에서 0.25로 가장 크므로 4를 곱해 꼭짓점을 1로 맞춘다.
private const val PEAK_NORM = 4f
