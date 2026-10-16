package com.naury.kinetica.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** [KTextField]와 [KTextArea]의 모양 */
public enum class KTextFieldVariant {
    /** 표면 스타일로 칠한 둥근 상자. 라벨이 상자 안에서 위로 떠오른다. 글래스 화면에 어울리는 기본 모양이다. */
    Surface,

    /** 배경 없이 테두리만 두른다. 라벨이 떠오르면 위 테두리에 걸치고, 그 자리만큼 테두리가 끊긴다. */
    Outlined,

    /** 위 모서리만 둥근 옅은 바탕에 아래 선을 긋는다. 포커스를 받으면 아래 선이 강조색으로 두꺼워진다. */
    Filled,

    /** 배경 없이 아래 선만 긋는다. 좌우 여백이 없어 글자가 바깥 글자와 나란히 놓인다. 설정 화면이나 목록 안 입력에 어울린다. */
    Underline,

    /**
     * 라벨을 왼쪽에 고정하고 값을 오른쪽 끝에 맞춘다. 배경과 선이 없어 [KListSection] 안 행으로 넣는 설정 화면 입력에 쓴다.
     * 라벨은 떠오르지 않고, [KTextField]의 prefix와 suffix는 늘 보인다.
     */
    Inline,
}

/** 입력란에 보이는 글자들 */
internal class FieldTexts(
    val label: String,
    val placeholder: String?,
    val prefix: String?,
    val suffix: String?,
    val error: String?,
    val supporting: String?,
)

/** 줄 수 설정 */
internal class FieldLines(val single: Boolean, val min: Int, val max: Int) {
    /** BasicTextField에 넘길 최대 줄 수. 한 줄 입력이면 1이다. */
    val visibleMax: Int get() = if (single) 1 else max

    /** BasicTextField에 넘길 최소 줄 수. 한 줄 입력이면 1이다. */
    val visibleMin: Int get() = if (single) 1 else min
}

private fun variantTextStyle(ink: Color, inline: Boolean): TextStyle =
    TextStyle(color = ink, fontSize = VARIANT_FONT, textAlign = if (inline) TextAlign.End else TextAlign.Unspecified)

/** 키보드 설정과 상호작용 관찰 */
internal class FieldInput(val keyboardOptions: KeyboardOptions, val interactionSource: MutableInteractionSource?)

/** 앞쪽과 끝쪽 슬롯 */
internal class FieldSlots(val leading: (@Composable () -> Unit)?, val trailing: (@Composable () -> Unit)?)

/** 입력 글자 앞뒤 고정 글자 */
internal class FieldAffix(val prefix: String?, val suffix: String?)

/** 지우기 버튼과 입력 가능 여부 */
internal class FieldFlags(val clearable: Boolean, val enabled: Boolean, val readOnly: Boolean)

/** 입력란 설정. 파라미터 수를 줄이려고 묶었다. */
internal class FieldConfig(
    val variant: KTextFieldVariant,
    val texts: FieldTexts,
    val lines: FieldLines,
    val maxLength: Int?,
    val slots: FieldSlots,
    val flags: FieldFlags,
)

/** 라벨과 앞쪽 아이콘의 잰 크기(px). 라벨 위치와 테두리 끊김을 그리기 단계에서 계산할 때 읽는다. */
private class LabelGeometry {
    var field by mutableFloatStateOf(0f)
    var labelHeight by mutableFloatStateOf(0f)
    var labelWidth by mutableFloatStateOf(0f)
    var leading by mutableFloatStateOf(0f)
}

/** 모양마다 다른 치수 */
private class VariantMetrics(val minHeight: Dp, val start: Dp, val end: Dp, val inputTop: Dp, val inputBottom: Dp, val supportStart: Dp)

private fun metricsOf(variant: KTextFieldVariant): VariantMetrics = when (variant) {
    KTextFieldVariant.Outlined -> VariantMetrics(56.dp, 16.dp, 4.dp, 16.dp, 16.dp, 16.dp)
    KTextFieldVariant.Filled -> VariantMetrics(56.dp, 16.dp, 4.dp, 24.dp, 8.dp, 16.dp)
    KTextFieldVariant.Underline -> VariantMetrics(52.dp, 0.dp, 0.dp, 22.dp, 6.dp, 0.dp)
    else -> VariantMetrics(KMetrics.Touch, 16.dp, 4.dp, 12.dp, 12.dp, 16.dp)
}

@Composable
internal fun VariantTextField(source: FieldSource, config: FieldConfig, input: FieldInput, surface: SurfaceStyle, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val texts = config.texts
    val inline = config.variant == KTextFieldVariant.Inline
    val empty = source.text.isEmpty()
    val motion = rememberVariantMotion(!empty, focused, inline, texts.error)
    val geometry = remember { LabelGeometry() }
    val colors = KineticaTheme.colors
    val metrics = metricsOf(config.variant)
    val basics = FieldBasics(
        enabled = config.flags.enabled,
        readOnly = config.flags.readOnly,
        lines = config.lines,
        textStyle = themed(variantTextStyle(surface.contentColor, inline)),
        cursorBrush = SolidColor(colors.accent),
        input = input,
    )
    Column(modifier.keepVisibleWhileFocused(focused)) {
        ProvideFieldSelection(colors.accent) {
            SourceTextField(
                source,
                Modifier
                    .fillMaxWidth()
                    // 떠오른 라벨이 위 테두리에 걸쳐 반쯤 밖으로 나오므로 그만큼 자리를 비운다.
                    .padding(top = if (config.variant == KTextFieldVariant.Outlined) OUTLINED_TOP_SPACE else 0.dp)
                    .graphicsLayer {
                        translationX = motion.shake.value * density
                        alpha = if (config.flags.enabled) 1f else VARIANT_DISABLED_ALPHA
                    }
                    .variantChrome(config.variant, motion, geometry, surface.contentColor)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { texts.error?.let { error(it) } },
                basics,
            ) { field ->
                val showClear = config.flags.clearable && config.flags.enabled && !config.flags.readOnly && focused && !empty
                VariantDecoration(config, metrics, surface, motion, geometry, empty, field) {
                    FieldClearButton(showClear, surface.contentColor) { source.clear() }
                }
            }
        }
        FieldSupportRow(texts.error, texts.supporting, surface, config.maxLength?.let { source.text.length to it }, metrics.supportStart)
    }
}

/** 라벨 떠오름, 포커스, 오류 애니메이션. [inline]이면 라벨이 떠오르지 않으므로 처음부터 떠오른 상태로 둔다. */
@Composable
private fun rememberVariantMotion(filled: Boolean, focused: Boolean, inline: Boolean, error: String?): FieldMotion {
    val motion = remember { FieldMotion(inline || filled, error) }
    val colors = KineticaTheme.colors
    motion.accent = colors.accent
    motion.errorColor = colors.destructive
    val still = reducedMotion()
    LaunchedEffect(focused, filled, still) {
        val target = if (inline || focused || filled) 1f else 0f
        if (still) motion.float.snapTo(target) else motion.float.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(focused) { motion.levels.focusTo(focused) }
    LaunchedEffect(error) { motion.onError(error, still) }
    return motion
}

/** 모양에 맞는 테두리, 바탕, 아래 선. 포커스와 오류 정도는 그리기 단계에서 읽는다. */
private fun Modifier.variantChrome(variant: KTextFieldVariant, motion: FieldMotion, geometry: LabelGeometry, ink: Color): Modifier =
    drawWithCache {
        val corner = VARIANT_CORNER.toPx()
        val filled = Path().apply {
            addRoundRect(
                RoundRect(Rect(Offset.Zero, size), CornerRadius(corner), CornerRadius(corner), CornerRadius.Zero, CornerRadius.Zero),
            )
        }
        val thin = THIN_LINE.toPx()
        val thick = THICK_LINE.toPx()
        val notchPad = NOTCH_PAD.toPx()
        val labelX = OUTLINED_LABEL_X.toPx()
        onDrawBehind {
            val focus = motion.levels.focus.value.coerceIn(0f, 1f)
            val error = motion.levels.error.value.coerceIn(0f, 1f)
            val idle = ink.copy(alpha = if (variant == KTextFieldVariant.Outlined) OUTLINE_IDLE_ALPHA else LINE_IDLE_ALPHA)
            val color = lerp(lerp(idle, motion.accent, focus), motion.errorColor, error)
            val width = thin + (thick - thin) * maxOf(focus, error)
            when (variant) {
                KTextFieldVariant.Outlined -> {
                    val float = motion.float.value.coerceIn(0f, 1f)
                    val gap = if (float > 0f) (geometry.labelWidth * VARIANT_LABEL_SCALE + notchPad * 2) * float else 0f
                    val start = labelX - notchPad
                    val left = if (layoutDirection == LayoutDirection.Rtl) size.width - start - gap else start
                    clipRect(left, -width, left + gap, width * 2, ClipOp.Difference) {
                        drawRoundRect(
                            color,
                            Offset(width / 2, width / 2),
                            Size(size.width - width, size.height - width),
                            CornerRadius(corner - width / 2),
                            style = Stroke(width),
                        )
                    }
                }

                KTextFieldVariant.Filled -> {
                    drawPath(filled, ink.copy(alpha = FILLED_ALPHA + FILLED_FOCUS_ALPHA * focus))
                    drawRect(color, Offset(0f, size.height - width), Size(size.width, width))
                }

                KTextFieldVariant.Underline -> drawRect(color, Offset(0f, size.height - width), Size(size.width, width))

                else -> Unit
            }
        }
    }

@Composable
private fun VariantDecoration(
    config: FieldConfig,
    metrics: VariantMetrics,
    surface: SurfaceStyle,
    motion: FieldMotion,
    geometry: LabelGeometry,
    empty: Boolean,
    field: @Composable () -> Unit,
    clear: @Composable () -> Unit,
) {
    val grow = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val inline = config.variant == KTextFieldVariant.Inline
    val texts = config.texts
    Box(Modifier.onSizeChanged { geometry.field = it.height.toFloat() }) {
        Row(
            Modifier
                .heightIn(min = metrics.minHeight * grow)
                .padding(
                    start = if (config.slots.leading != null &&
                        metrics.start > 0.dp
                    ) {
                        LEADING_START
                    } else {
                        metrics.start
                    },
                    end = metrics.end,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            config.slots.leading?.let {
                Row(Modifier.onSizeChanged { geometry.leading = it.width.toFloat() }, verticalAlignment = Alignment.CenterVertically) {
                    CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor.copy(alpha = VARIANT_ICON_ALPHA)) {
                        it()
                    }
                    Spacer(Modifier.width(LEADING_GAP))
                }
            }
            if (inline) InlineLabel(texts.label, surface, motion)
            FieldAffixes(
                texts.prefix,
                texts.suffix,
                surface.contentColor,
                { motion.float.value },
                Modifier
                    .weight(1f)
                    .padding(top = metrics.inputTop * (if (inline) 1f else grow), bottom = metrics.inputBottom)
                    .padding(end = if (metrics.end > 0.dp) VARIANT_TEXT_END else 0.dp),
                // 입력 칸은 글자 폭만큼만 차지하므로, 값을 끝에 맞추려면 칸 자체를 끝으로 보낸다.
                if (inline) Alignment.TopEnd else Alignment.TopStart,
            ) {
                if (empty && texts.placeholder != null) VariantPlaceholder(texts.placeholder, surface, motion, inline)
                field()
            }
            clear()
            config.slots.trailing?.invoke()
        }
        if (!inline) FloatingVariantLabel(texts.label, config, metrics, surface, motion, geometry, grow)
    }
}

@Composable
private fun InlineLabel(label: String, surface: SurfaceStyle, motion: FieldMotion) {
    ThemedText(
        label,
        Modifier
            // 가중치를 주면 남는 폭이 값 칸으로 가지 않으므로, 라벨은 최소와 최대 폭만 정한다.
            .widthIn(min = INLINE_LABEL_MIN, max = INLINE_LABEL_MAX)
            .padding(end = INLINE_LABEL_GAP),
        style = TextStyle(fontSize = VARIANT_FONT),
        color = { lerp(surface.contentColor, motion.errorColor, motion.levels.error.value) },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun VariantPlaceholder(text: String, surface: SurfaceStyle, motion: FieldMotion, inline: Boolean) {
    ThemedText(
        text,
        Modifier.fillMaxWidth().graphicsLayer { alpha = if (inline) 1f else motion.levels.focus.value },
        style = TextStyle(
            color = surface.contentColor.copy(alpha = VARIANT_PLACEHOLDER_ALPHA),
            fontSize = VARIANT_FONT,
            textAlign = if (inline) TextAlign.End else TextAlign.Unspecified,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 떠오르는 라벨. 위치는 잰 크기로 그리기 단계에서 정해 애니메이션 중에 재구성하지 않는다. */
@Composable
private fun BoxScope.FloatingVariantLabel(
    label: String,
    config: FieldConfig,
    metrics: VariantMetrics,
    surface: SurfaceStyle,
    motion: FieldMotion,
    geometry: LabelGeometry,
    grow: Float,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val outlined = config.variant == KTextFieldVariant.Outlined
    val multiLine = !config.lines.single && config.lines.min > 1
    val leadingStart = if (config.slots.leading != null && metrics.start > 0.dp) LEADING_START else metrics.start
    ThemedText(
        label,
        Modifier
            .align(Alignment.TopStart)
            .onSizeChanged {
                geometry.labelHeight = it.height.toFloat()
                geometry.labelWidth = it.width.toFloat()
            }
            .graphicsLayer {
                val up = motion.float.value
                transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0f)
                scaleX = 1f - (1f - VARIANT_LABEL_SCALE) * up
                scaleY = scaleX
                val textX = leadingStart.toPx() + geometry.leading
                val floatX = if (outlined) OUTLINED_LABEL_X.toPx() else textX
                val x = textX + (floatX - textX) * up
                translationX = if (rtl) -x else x
                val rest = if (multiLine) {
                    (metrics.inputTop * (if (outlined) 1f else grow)).toPx()
                } else {
                    (
                        geometry.field -
                            geometry.labelHeight
                        ) /
                        2
                }
                val floated = if (outlined) -geometry.labelHeight * VARIANT_LABEL_SCALE / 2 else (FLOAT_TOP * grow).toPx()
                translationY = rest + (floated - rest) * up
            },
        style = TextStyle(fontSize = VARIANT_FONT),
        color = {
            val idle = surface.contentColor.copy(alpha = FIELD_HINT_ALPHA)
            val focus = motion.levels.focus.value * motion.float.value
            lerp(lerp(idle, motion.accent, focus), motion.errorColor, motion.levels.error.value)
        },
        maxLines = 1,
    )
}

/**
 * 입력 글자 앞뒤의 고정 글자. 둘 다 없으면 [content]만 그린다. 고정 글자는 [level]이 1에 가까울수록 진해진다(라벨이 떠오를 때 나타난다).
 */
@Composable
internal fun FieldAffixes(
    prefix: String?,
    suffix: String?,
    ink: Color,
    level: () -> Float,
    modifier: Modifier,
    alignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    if (prefix == null && suffix == null) {
        Box(modifier, alignment, content = content)
        return
    }
    Row(modifier, verticalAlignment = Alignment.Top) {
        prefix?.let { AffixText(it, ink, level, Modifier.padding(end = AFFIX_GAP)) }
        Box(Modifier.weight(1f), alignment, content = content)
        suffix?.let { AffixText(it, ink, level, Modifier.padding(start = AFFIX_GAP)) }
    }
}

@Composable
private fun AffixText(text: String, ink: Color, level: () -> Float, modifier: Modifier) {
    ThemedText(
        text,
        modifier.graphicsLayer { alpha = level().coerceIn(0f, 1f) },
        style = TextStyle(color = ink.copy(alpha = AFFIX_ALPHA), fontSize = VARIANT_FONT),
        maxLines = 1,
    )
}

/**
 * 입력란 아래 줄. 왼쪽에 오류나 도움말, 오른쪽 끝에 글자 수([counter]: 지금 글자 수와 최대)를 놓는다.
 * 글자 수가 최대를 넘으면 빨갛게 그린다. 글자 수가 없고 시작 여백이 기본값이면 예전 [FieldSupportText]를 그대로 쓴다.
 */
@Composable
internal fun FieldSupportRow(error: String?, helper: String?, surface: SurfaceStyle, counter: Pair<Int, Int>?, start: Dp) {
    if (counter == null && start == DEFAULT_SUPPORT_START) {
        FieldSupportText(error, helper, surface)
        return
    }
    val colors = KineticaTheme.colors
    val text = error ?: helper
    Row(
        Modifier.fillMaxWidth().animateContentSize(tween(SUPPORT_RESIZE_MS)).padding(start = start, end = start, top = SUPPORT_GAP),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.weight(1f)) {
            if (text != null) {
                ThemedText(
                    text,
                    style = TextStyle(
                        color = if (error != null) colors.destructive else surface.contentColor.copy(alpha = SUPPORT_ALPHA),
                        fontSize = SUPPORT_FONT,
                    ),
                )
            }
        }
        counter?.let { (count, max) ->
            val label = stringResource(R.string.kinetica_ui_char_count, count, max)
            ThemedText(
                "$count/$max",
                Modifier.padding(start = AFFIX_GAP).semantics { contentDescription = label },
                style = TextStyle(
                    color = if (count > max) colors.destructive else surface.contentColor.copy(alpha = SUPPORT_ALPHA),
                    fontSize = SUPPORT_FONT,
                ),
            )
        }
    }
}

private val VARIANT_FONT = 16.sp
private val SUPPORT_FONT = 13.sp
private val VARIANT_CORNER = 12.dp
private val THIN_LINE = 1.dp
private val THICK_LINE = 2.dp
private val NOTCH_PAD = 4.dp
private val OUTLINED_LABEL_X = 12.dp
private val FLOAT_TOP = 8.dp
private val OUTLINED_TOP_SPACE = 8.dp
private val LEADING_START = 12.dp
private val LEADING_GAP = 10.dp
private val VARIANT_TEXT_END = 12.dp
private val INLINE_LABEL_MIN = 88.dp
private val INLINE_LABEL_GAP = 16.dp
private val INLINE_LABEL_MAX = 160.dp
private val AFFIX_GAP = 4.dp
private val SUPPORT_GAP = 6.dp
private val DEFAULT_SUPPORT_START = 16.dp
private const val SUPPORT_RESIZE_MS = 160
private const val VARIANT_LABEL_SCALE = 0.75f
private const val VARIANT_DISABLED_ALPHA = 0.5f
private const val VARIANT_ICON_ALPHA = 0.6f
private const val VARIANT_PLACEHOLDER_ALPHA = 0.38f
private const val AFFIX_ALPHA = 0.6f
private const val SUPPORT_ALPHA = 0.6f
private const val OUTLINE_IDLE_ALPHA = 0.3f
private const val LINE_IDLE_ALPHA = 0.4f
private const val FILLED_ALPHA = 0.06f
private const val FILLED_FOCUS_ALPHA = 0.03f
