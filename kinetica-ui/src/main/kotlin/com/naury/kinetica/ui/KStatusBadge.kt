// 파일 이름은 대표 컴포저블인 KStatusBadge를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** [KStatusBadge]가 알리는 상태. 색만 다르고 모양은 같다. */
public enum class KStatus {
    /** 표면 글자 색. 초안, 보관됨처럼 강조할 필요 없는 상태 */
    Neutral,

    /** 강조색. 진행 중, 새 기능 */
    Info,

    /** [KineticaColors.positive]. 완료, 배송 완료, 활성 */
    Success,

    /** 주황. 대기, 곧 만료 */
    Warning,

    /** [KineticaColors.destructive]. 실패, 취소, 품절 */
    Error,
}

/**
 * 글자 배지. 'NEW', 'Beta', '1+1'처럼 짧은 꼬리표를 글자 옆이나 카드 위에 놓는다. 숫자 배지는 `KBadge(count)`를,
 * 다른 요소 모서리에 겹치는 배지는 [kBadge]를 쓴다.
 *
 * ```kotlin
 * KBadge("NEW")
 * KBadge("품절", tone = KTone.Destructive, variant = KVariant.Tonal)
 * ```
 *
 * @param variant 모양. [KVariant.Filled](기본값), [KVariant.Tonal], [KVariant.Outlined]를 받고 나머지는 [KVariant.Tonal]처럼 그린다.
 */
@Composable
public fun KBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: KTone = KTone.Accent,
    variant: KVariant = KVariant.Filled,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val colors = tone.colors(surface)
    Pill(text, PillLook(colors.color, colors.onColor, variant, surface, PillSize.Text), modifier, dot = false)
}

/**
 * 상태 배지. 주문, 결제, 서버 상태처럼 목록 행이나 카드에 상태를 짧게 보여 준다. 기본 모양은 배지 모양 토큰의 옅은 색 판 앞에 같은 색 점을 둔 형태다.
 * 글자 색은 표면과 대비가 4.5:1 이상이 되도록 고쳐 쓴다. TalkBack은 [text]를 그대로 읽으므로 '배송 완료'처럼 상태를 글자로 다 쓴다.
 *
 * ```kotlin
 * KStatusBadge("배송 중", KStatus.Info)
 * KStatusBadge("결제 실패", KStatus.Error, variant = KVariant.Filled, dot = false)
 * ```
 *
 * @param variant 모양. [KVariant.Tonal](기본값), [KVariant.Filled], [KVariant.Outlined]를 받고 나머지는 [KVariant.Tonal]처럼 그린다.
 * @param dot `true`면 글자 앞에 상태 색 점을 둔다.
 */
@Composable
public fun KStatusBadge(
    text: String,
    status: KStatus,
    modifier: Modifier = Modifier,
    variant: KVariant = KVariant.Tonal,
    dot: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val color = status.color(surface)
    val on = if (status == KStatus.Warning) {
        Color(WARNING_INK)
    } else if (status == KStatus.Neutral) {
        surface.inkBackground()
    } else {
        Color.White
    }
    Pill(text, PillLook(color, on, variant, surface, PillSize.Status), modifier, dot)
}

@Composable
@ReadOnlyComposable
private fun KStatus.color(surface: SurfaceStyle): Color {
    val colors = KineticaTheme.colors
    return when (this) {
        KStatus.Neutral -> surface.contentColor
        KStatus.Info -> colors.accent
        KStatus.Success -> colors.positive
        KStatus.Warning -> Color(WARNING)
        KStatus.Error -> colors.destructive
    }
}

private class PillLook(val color: Color, val onColor: Color, val variant: KVariant, val surface: SurfaceStyle, val size: PillSize)

/** 배지 글자 크기와 높이, 좌우 여백 */
private enum class PillSize(val font: TextUnit, val height: Dp, val padding: Dp) {
    Text(11.sp, 20.dp, 7.dp),
    Status(12.sp, 24.dp, 10.dp),
}

@Composable
private fun Pill(text: String, look: PillLook, modifier: Modifier, dot: Boolean) {
    val back = look.surface.inkBackground()
    val (fill, ink) = pillColors(look.variant, look.color, look.onColor, back)
    val shape = shapeFor { badge }
    Row(
        modifier
            .heightIn(min = look.size.height)
            .background(fill, shape)
            .then(
                if (look.variant == KVariant.Outlined) {
                    Modifier.border(OUTLINE, look.color.copy(alpha = OUTLINE_ALPHA), shape)
                } else {
                    Modifier
                },
            )
            .padding(start = if (dot) look.size.padding - DOT_TRIM else look.size.padding, end = look.size.padding),
        horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) Box(Modifier.size(DOT).background(if (look.variant == KVariant.Filled) ink else look.color, CircleShape))
        ThemedText(
            text,
            style = TextStyle(color = ink, fontSize = look.size.font, fontWeight = KineticaTheme.design.label(FontWeight.SemiBold)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 배지 바탕과 글자 색. 작은 글자라 4.5:1을 맞춘다. 채운 배지는 글자 대신 채우는 색을 어둡게(어두운 글자면 밝게) 고쳐
 * 흰 글자를 지킨다. [back]은 배지가 놓인 표면 색이다.
 */
internal fun pillColors(variant: KVariant, color: Color, onColor: Color, back: Color): Pair<Color, Color> = when (variant) {
    KVariant.Filled -> {
        val fixed = readableOn(color, onColor, TEXT_CONTRAST)
        fixed to readableOn(onColor, fixed, TEXT_CONTRAST)
    }

    KVariant.Outlined -> Color.Transparent to readableOn(color, back, TEXT_CONTRAST)

    else -> {
        val tint = color.copy(alpha = TINT).compositeOver(back)
        color.copy(alpha = TINT) to readableOn(color, tint, TEXT_CONTRAST)
    }
}

private val DOT = 6.dp
private val DOT_GAP = 6.dp
private val DOT_TRIM = 2.dp
private val OUTLINE = 1.dp
private const val TINT = 0.14f
private const val TEXT_CONTRAST = 4.5f
private const val OUTLINE_ALPHA = 0.5f
private const val WARNING = 0xFFFF9F0A
private const val WARNING_INK = 0xFF1F1300
