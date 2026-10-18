// 파일 이름은 대표 컴포저블인 KQuantityStepper를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** [KQuantityStepper]의 모양 */
public enum class KQuantityStyle {
    /** 테두리 칸 안에 − 숫자 + 를 나눠 놓는다. 장바구니 줄과 상품 상세 화면에 쓴다. */
    Outlined,

    /**
     * 0이면 + 버튼 하나만 보이고, 누르면 − 숫자 + 캡슐로 펼쳐진다. 1에서 빼기 버튼은 휴지통으로 바뀌어 누르면 0으로 접힌다.
     * 배달 앱 메뉴 목록처럼 담기 전에는 자리를 적게 차지해야 할 때 쓴다.
     */
    Collapsible,
}

/**
 * 쇼핑 수량 스테퍼. [KStepper]가 설정 값 조절용 캡슐이라면, 이 컴포넌트는 장바구니 수량에 맞춘 두 모양([KQuantityStyle])을 준다.
 * 숫자는 자릿수가 굴러가며 바뀌고, 바뀔 때마다 가벼운 햅틱을 준다. 끝 값에 닿으면 그쪽 버튼이 흐려진다.
 * 접히는 모양은 펼치고 접힐 때 폭이 스프링으로 바뀌며, '애니메이션 삭제' 설정에서는 바로 바뀐다. RTL에서는 + 가 시작 쪽(오른쪽)에 온다.
 *
 * TalkBack은 하나의 조절 항목으로 읽고(예: '수량, 2'), 볼륨 키나 '늘리기', '줄이기' 동작으로 바꾼다. 0이면 '담기' 버튼으로 읽는다.
 *
 * ```kotlin
 * KQuantityStepper(count, { count = it }, style = KQuantityStyle.Collapsible, range = 0..10)
 * ```
 *
 * @param range 값 범위. 비어 있으면 [IllegalArgumentException]을 던진다. 접히는 모양은 [range]의 첫 값에서 접힌다.
 *   기본값은 테두리 모양이 1..99, 접히는 모양이 0..99다.
 * @param label TalkBack이 값 앞에 읽을 이름. 기본값은 '수량'
 */
@Composable
public fun KQuantityStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: KQuantityStyle = KQuantityStyle.Outlined,
    range: IntRange = if (style == KQuantityStyle.Collapsible) 0..MAX_DEFAULT else 1..MAX_DEFAULT,
    enabled: Boolean = true,
    label: String? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(!range.isEmpty()) { "range가 비어 있습니다." }
    val shown = value.coerceIn(range)
    val haptic = kHapticFeedback()
    val latest by rememberUpdatedState(onValueChange)
    val change: (Int) -> Unit = { next ->
        val target = next.coerceIn(range)
        if (target != shown) {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            latest(target)
        }
    }
    val a11y = QuantityA11y(
        label ?: stringResource(R.string.kinetica_ui_quantity),
        shown,
        range,
        enabled,
        stringResource(R.string.kinetica_ui_increase),
        stringResource(R.string.kinetica_ui_decrease),
    )
    when (style) {
        KQuantityStyle.Outlined -> OutlinedQuantity(shown, range, enabled, surface, modifier.quantitySemantics(a11y, change), change)
        KQuantityStyle.Collapsible -> CollapsibleQuantity(shown, range, enabled, surface, modifier, a11y, change)
    }
}

@Composable
private fun OutlinedQuantity(
    value: Int,
    range: IntRange,
    enabled: Boolean,
    surface: SurfaceStyle,
    modifier: Modifier,
    change: (Int) -> Unit,
) {
    val ink = surface.contentColor
    val shapes = currentShapes
    val corner = shapes.cornerOf(shapes.button)
    val radius = shapes.radiusOr(shapes.button, OUTLINE_RADIUS)
    val shape = shapeOf(corner, radius)
    val weight = KineticaTheme.design.label(FontWeight.SemiBold)
    Row(
        modifier
            .height(KineticaTheme.design.density.height(OUTLINE_HEIGHT))
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED }
            .border(1.dp, ink.copy(alpha = BORDER_ALPHA), shape)
            .clip(shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val inner = shapeOf(corner, if (radius > 0.dp) KMetrics.concentric(radius, 1.dp) else radius)
        QuantityButton(KDataGlyphs.Minus, enabled && value > range.first, ink, inner) { change(value - 1) }
        Divider(ink)
        Box(Modifier.widthIn(min = VALUE_MIN).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            RollingText(value.toString(), style = themed(TextStyle(color = ink, fontSize = 16.sp, fontWeight = weight)))
        }
        Divider(ink)
        QuantityButton(KDataGlyphs.Plus, enabled && value < range.last, ink, inner) { change(value + 1) }
    }
}

@Composable
private fun Divider(ink: Color) {
    Box(Modifier.width(1.dp).fillMaxHeight().background(ink.copy(alpha = BORDER_ALPHA)))
}

@Composable
private fun CollapsibleQuantity(
    value: Int,
    range: IntRange,
    enabled: Boolean,
    surface: SurfaceStyle,
    modifier: Modifier,
    a11y: QuantityA11y,
    change: (Int) -> Unit,
) {
    val colors = KineticaTheme.colors
    val open = value > range.first
    val still = reducedMotion()
    val add = stringResource(R.string.kinetica_ui_quantity_add)
    val grow = spring<IntSize>(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium)
    val shape = shapeFor { button }
    val weight = KineticaTheme.design.label(FontWeight.SemiBold)
    Row(
        modifier
            .height(COLLAPSED)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED }
            .background(if (open) colors.accent.copy(alpha = TONAL) else Color.Transparent, shape)
            .then(if (open) Modifier.quantitySemantics(a11y, change) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            open,
            enter = if (still) fadeIn() else expandHorizontally(grow, Alignment.End) + fadeIn(),
            exit = if (still) fadeOut() else shrinkHorizontally(spring(stiffness = Spring.StiffnessMedium), Alignment.End) + fadeOut(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val removes = value == range.first + 1
                QuantityButton(if (removes) KDataGlyphs.Trash else KDataGlyphs.Minus, enabled, colors.accent, shape) {
                    change(value - 1)
                }
                Box(Modifier.widthIn(min = VALUE_MIN_SMALL).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    RollingText(
                        value.coerceAtLeast(range.first + 1).toString(),
                        style = themed(TextStyle(color = surface.contentColor, fontSize = 16.sp, fontWeight = weight)),
                    )
                }
            }
        }
        val canAdd = enabled && value < range.last
        Box(
            Modifier
                .size(COLLAPSED)
                .graphicsLayer { alpha = if (canAdd || !open) 1f else EDGE }
                .background(colors.accent, shape)
                .clip(shape)
                .then(
                    if (open) {
                        Modifier.kClickable(shape, enabled = canAdd, role = null) { change(value + 1) }
                    } else {
                        Modifier.kClickable(shape, enabled = enabled, onClickLabel = add) { change(value + 1) }
                            .clearAndSetSemantics {
                                contentDescription = "$add, ${a11y.label}"
                                if (!enabled) disabled()
                            }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) { Glyph(KDataGlyphs.Plus, colors.onAccent, size = 18.dp) }
    }
}

/** 누를 수 있는 아이콘 칸. 의미 정보는 스테퍼 전체가 맡는다. */
@Composable
private fun QuantityButton(icon: ImageVector, enabled: Boolean, tint: Color, shape: Shape, onClick: () -> Unit) {
    Box(
        Modifier
            .size(BUTTON)
            .clip(shape)
            .kClickable(shape, enabled = enabled, role = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, tint.copy(alpha = if (enabled) 1f else EDGE), size = 18.dp) }
}

private class QuantityA11y(
    val label: String,
    val value: Int,
    val range: IntRange,
    val enabled: Boolean,
    val increase: String,
    val decrease: String,
)

private fun Modifier.quantitySemantics(a11y: QuantityA11y, onChange: (Int) -> Unit): Modifier = clearAndSetSemantics {
    contentDescription = a11y.label
    stateDescription = a11y.value.toString()
    val range = a11y.range
    progressBarRangeInfo =
        ProgressBarRangeInfo(
            a11y.value.toFloat(),
            range.first.toFloat()..range.last.toFloat(),
            (range.last - range.first - 1).coerceAtLeast(0),
        )
    if (!a11y.enabled) {
        disabled()
        return@clearAndSetSemantics
    }
    setProgress { target ->
        val next = target.roundToInt().coerceIn(range)
        if (next != a11y.value) onChange(next)
        next != a11y.value
    }
    customActions = buildList {
        if (a11y.value < range.last) add(CustomAccessibilityAction(a11y.increase) { onChange(a11y.value + 1).let { true } })
        if (a11y.value > range.first) add(CustomAccessibilityAction(a11y.decrease) { onChange(a11y.value - 1).let { true } })
    }
}

private const val MAX_DEFAULT = 99
private const val BORDER_ALPHA = 0.18f
private const val DISABLED = 0.5f
private const val EDGE = 0.32f
private const val TONAL = 0.14f
private val OUTLINE_HEIGHT = 40.dp
private val OUTLINE_RADIUS = 10.dp
private val BUTTON = 40.dp
private val VALUE_MIN = 40.dp
private val VALUE_MIN_SMALL = 28.dp
private val COLLAPSED = 40.dp
