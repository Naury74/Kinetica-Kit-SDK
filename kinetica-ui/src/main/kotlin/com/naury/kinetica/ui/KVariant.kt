package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 컴포넌트의 모양 변형. 색 역할([KTone])과 표면 스타일([SurfaceStyle])과는 따로 고른다. 예를 들어 글래스 표면 위에서도
 * [Outlined] 버튼을 쓸 수 있다. 컴포넌트마다 어울리는 변형만 받고, 받지 않는 변형은 가장 가까운 모양으로 그린다.
 */
public enum class KVariant {
    /** 색 역할의 색으로 꽉 채운다. 화면에서 가장 중요한 동작 하나에 쓴다. */
    Filled,

    /** 색 역할의 색을 옅게 깔고 글자를 그 색으로 쓴다. 두 번째로 중요한 동작에 쓴다. */
    Tonal,

    /** 배경 없이 색 역할의 색으로 테두리를 두른다. */
    Outlined,

    /** 배경과 테두리 없이 글자만 쓴다. 누르는 동안에만 옅은 배경이 보인다. */
    Text,

    /** 표면 스타일로 그리고 그림자를 키워 떠 보이게 한다. */
    Elevated,

    /** 표면 스타일 그대로 그린다. 글래스 표면이면 블러가 비치고, 단색 표면이면 단색이다. 사진이나 그라디언트 위에 어울린다. */
    Glass,
}

/**
 * 색 역할. [KineticaColors]의 어느 색으로 칠할지 정한다. [KVariant]와 함께 조합한다.
 *
 * [KVariant.Glass]와 [KVariant.Elevated]는 표면 글자 색을 쓰고, [Destructive]와 [Positive]일 때만 그 색으로 글자를 칠한다.
 */
public enum class KTone {
    /** [KineticaColors.accent] */
    Accent,

    /** 표면 글자 색([SurfaceStyle.contentColor]). 강조 없이 조용한 동작에 쓴다. */
    Neutral,

    /** [KineticaColors.destructive]. 삭제처럼 되돌리기 어려운 동작에 쓴다. */
    Destructive,

    /** [KineticaColors.positive]. 저장, 완료처럼 긍정적인 동작에 쓴다. */
    Positive,
}

/** 색 역할을 푼 결과. [color]는 채우거나 강조할 색, [onColor]는 [color]로 채운 면 위 글자 색이다. */
@Immutable
internal class ToneColors(val color: Color, val onColor: Color)

/** [surface] 위에서 이 색 역할이 쓸 색. */
@Composable
@ReadOnlyComposable
internal fun KTone.colors(surface: SurfaceStyle): ToneColors {
    val colors = KineticaTheme.colors
    return when (this) {
        KTone.Accent -> ToneColors(colors.accent, colors.onAccent)
        KTone.Destructive -> ToneColors(colors.destructive, colors.onDestructive)
        KTone.Positive -> ToneColors(colors.positive, colors.onPositive)
        KTone.Neutral -> ToneColors(surface.contentColor, surface.fillColor.copy(alpha = 1f))
    }
}

/** [variant] 모양에서 글자와 아이콘에 쓸 색. */
internal fun variantContent(variant: KVariant, tone: KTone, colors: ToneColors, surface: SurfaceStyle): Color = when (variant) {
    KVariant.Filled -> colors.onColor
    KVariant.Tonal, KVariant.Outlined, KVariant.Text -> readableOnDark(colors.color, tone, surface)
    KVariant.Glass, KVariant.Elevated -> if (tone == KTone.Destructive || tone == KTone.Positive) colors.color else surface.contentColor
}

/**
 * 어두운 표면에서 강조색 글자를 조금 밝힌다. 진한 강조색(예: #0A84FF)을 옅은 강조색 배경이나 어두운 표면 위에 그대로 쓰면
 * 명암비가 3:1대로 떨어져 읽기 어렵다. 흰색을 섞어 4.5:1 안팎으로 올린다. 표면 글자 색을 쓰는 [KTone.Neutral]은 그대로다.
 */
private fun readableOnDark(color: Color, tone: KTone, surface: SurfaceStyle): Color =
    if (tone == KTone.Neutral || !surface.isDark) color else lerp(color, Color.White, DARK_TEXT_LIFT)

/** [variant] 모양의 배경을 [shape]으로 그린다. 눌림 표시는 호출한 쪽이 붙인다. */
@Composable
internal fun Modifier.variantBackground(variant: KVariant, colors: ToneColors, surface: SurfaceStyle, shape: Shape): Modifier =
    when (variant) {
        KVariant.Filled -> background(colors.color, shape)
        KVariant.Tonal -> background(colors.color.copy(alpha = TONAL_ALPHA), shape)
        KVariant.Outlined -> border(OUTLINE_WIDTH, colors.color.copy(alpha = OUTLINE_ALPHA), shape)
        KVariant.Text -> this
        KVariant.Elevated -> kSurface(surface.elevated(), shape)
        KVariant.Glass -> kSurface(surface, shape)
    }

/** 그림자를 [ELEVATED_SHADOW] 이상으로 키운 표면 */
internal fun SurfaceStyle.elevated(): SurfaceStyle = when (this) {
    is SurfaceStyle.Glass -> SurfaceStyle.Glass(style.copy(shadow = maxOf(style.shadow, ELEVATED_SHADOW)))
    is SurfaceStyle.Solid -> copy(elevation = maxOf(elevation, ELEVATED_SHADOW))
    is SurfaceStyle.Tonal -> copy(elevation = maxOf(elevation, ELEVATED_SHADOW))
}

/** 배경이 없어 눌림 표시만이 눌린 걸 알리는 변형인지 */
internal val KVariant.bare: Boolean get() = this == KVariant.Text || this == KVariant.Outlined

private const val TONAL_ALPHA = 0.16f
private const val DARK_TEXT_LIFT = 0.3f
private const val OUTLINE_ALPHA = 0.55f
private val OUTLINE_WIDTH: Dp = 1.dp
private val ELEVATED_SHADOW: Dp = 14.dp
