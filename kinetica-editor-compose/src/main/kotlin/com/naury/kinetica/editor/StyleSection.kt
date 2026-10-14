package com.naury.kinetica.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.scene.LineCap
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.TextAutoSize
import com.naury.kinetica.core.scene.TextDecoration

/** 채우기(없음·단색·선형·원형 그라디언트). 글자는 글자 색으로 쓴다. */
@Composable
internal fun FillSection(node: SceneNode, edits: StyleEdits, property: PropertyEdits, theme: StudioTheme) {
    if (node.content is NodeContent.Image || node.content is NodeContent.HostSlot || node.content is NodeContent.Group) return
    val mode = edits.fillMode(node.style)
    SectionTitle(R.string.kinetica_section_fill, theme)
    Chips {
        FILL_MODES.forEach { (value, label) -> ToggleChip(label, mode == value, theme) { edits.setFillMode(node.id, value) } }
    }
    when (mode) {
        FillMode.None -> Unit

        FillMode.Solid -> Field(node, "fill", node.style.fill?.toHex().orEmpty(), R.string.kinetica_field_fill, KeyboardType.Ascii) {
            property.setFill(node.id, it)
        }

        FillMode.Linear, FillMode.Radial -> {
            val stops = node.style.gradient?.stops.orEmpty()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stops.take(2).forEachIndexed { index, stop ->
                    val label = if (index == 0) R.string.kinetica_field_gradient_start else R.string.kinetica_field_gradient_end
                    Field(node, "stop$index", stop.color.toHex(), label, KeyboardType.Ascii, Modifier.weight(1f)) {
                        edits.setStopColor(node.id, index, it)
                    }
                }
            }
            if (mode == FillMode.Linear) {
                Field(
                    node,
                    "angle",
                    formatNumber(node.style.gradient?.angleDegrees ?: 0.0),
                    R.string.kinetica_field_angle,
                    KeyboardType.Decimal,
                ) {
                    edits.setAngle(node.id, it)
                }
            }
        }
    }
}

/** 선 색·두께·점선, 열린 경로의 끝 모양. */
@Composable
internal fun StrokeSection(node: SceneNode, edits: StyleEdits, theme: StudioTheme) {
    val content = node.content
    val strokable = when (content) {
        is NodeContent.Rectangle, is NodeContent.Ellipse, is NodeContent.Polygon, is NodeContent.Path, is NodeContent.Frame -> true
        is NodeContent.Text, is NodeContent.Image, is NodeContent.HostSlot, is NodeContent.Group -> false
    }
    if (!strokable) return
    SectionTitle(R.string.kinetica_section_stroke, theme)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(
            node,
            "stroke",
            node.style.stroke?.toHex().orEmpty(),
            R.string.kinetica_field_stroke,
            KeyboardType.Ascii,
            Modifier.weight(1f),
        ) {
            edits.setStroke(node.id, it)
        }
        Field(
            node,
            "strokeWidth",
            formatNumber(node.style.strokeWidth),
            R.string.kinetica_field_stroke_width,
            KeyboardType.Decimal,
            Modifier.weight(1f),
        ) {
            edits.setStrokeWidth(node.id, it)
        }
    }
    Chips {
        ToggleChip(R.string.kinetica_field_dash, node.style.strokeDash.isNotEmpty(), theme) { edits.toggleDash(node.id) }
        if (content is NodeContent.Path) {
            ToggleChip(R.string.kinetica_cap_start_arrow, node.style.startCap == LineCap.Arrow, theme) {
                edits.setCap(node.id, start = true, if (node.style.startCap == LineCap.Arrow) LineCap.None else LineCap.Arrow)
            }
            ToggleChip(R.string.kinetica_cap_end_arrow, node.style.endCap == LineCap.Arrow, theme) {
                edits.setCap(node.id, start = false, if (node.style.endCap == LineCap.Arrow) LineCap.None else LineCap.Arrow)
            }
        }
    }
}

/** 그림자 목록(바깥·안쪽)과 레이어 흐림. */
@Composable
internal fun EffectsSection(node: SceneNode, edits: EffectEdits, theme: StudioTheme) {
    if (node.content is NodeContent.Text) return
    SectionTitle(R.string.kinetica_section_effects, theme)
    node.style.shadows.forEachIndexed { index, shadow ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (shadow.inner) R.string.kinetica_inner_shadow else R.string.kinetica_drop_shadow),
                    color = theme.onPanel,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    edits.removeShadow(node.id, index)
                }) { Text(stringResource(R.string.kinetica_remove), color = theme.accent) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SHADOW_FIELDS.forEach { (field, label) ->
                    val value = when (field) {
                        ShadowField.X -> shadow.offsetX
                        ShadowField.Y -> shadow.offsetY
                        ShadowField.Blur -> shadow.blur
                        ShadowField.Spread -> shadow.spread
                    }
                    Field(node, "shadow$index$field", formatNumber(value), label, KeyboardType.Decimal, Modifier.weight(1f)) {
                        edits.setShadow(node.id, index, field, it)
                    }
                }
            }
            Field(node, "shadow${index}color", shadow.color.toHex(), R.string.kinetica_field_color, KeyboardType.Ascii) {
                edits.setShadowColor(node.id, index, it)
            }
        }
    }
    Chips {
        TextButton(onClick = {
            edits.addShadow(node.id, inner = false)
        }) { Text("+ " + stringResource(R.string.kinetica_drop_shadow), color = theme.accent) }
        TextButton(onClick = {
            edits.addShadow(node.id, inner = true)
        }) { Text("+ " + stringResource(R.string.kinetica_inner_shadow), color = theme.accent) }
    }
    Field(node, "blur", formatNumber(node.style.blur), R.string.kinetica_field_layer_blur, KeyboardType.Decimal) {
        edits.setBlur(node.id, it)
    }
}

/** 사각형·Frame의 모서리별 반지름. */
@Composable
internal fun CornersSection(node: SceneNode, edits: ShapeEdits, theme: StudioTheme) {
    val corners = when (val content = node.content) {
        is NodeContent.Rectangle -> content.corners
        is NodeContent.Frame -> content.corners
        else -> return
    }
    ToggleChip(R.string.kinetica_field_corners_each, corners != null, theme) { edits.toggleCorners(node.id) }
    corners ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft).forEachIndexed { index, value ->
            Field(node, "corner$index", formatNumber(value), CORNER_LABELS[index], KeyboardType.Decimal, Modifier.weight(1f)) {
                edits.setCorner(node.id, index, it)
            }
        }
    }
}

/** 다각형 꼭짓점 수와 별 비율. */
@Composable
internal fun PolygonSection(node: SceneNode, polygon: NodeContent.Polygon, edits: ShapeEdits, theme: StudioTheme) {
    SectionTitle(R.string.kinetica_section_polygon, theme)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(node, "sides", polygon.sides.toString(), R.string.kinetica_field_sides, KeyboardType.Number, Modifier.weight(1f)) {
            edits.setSides(node.id, it)
        }
        Field(
            node,
            "ratio",
            formatNumber(polygon.innerRatio),
            R.string.kinetica_field_star_ratio,
            KeyboardType.Decimal,
            Modifier.weight(1f),
        ) {
            edits.setInnerRatio(node.id, it)
        }
    }
}

/** 글자 전체의 글꼴·기울임·밑줄·취소선. 일부만 바꾸려면 글자 수정 창에서 고른다. */
@Composable
internal fun FontSection(node: SceneNode, text: NodeContent.Text, edits: TextEdits, theme: StudioTheme) {
    SectionTitle(R.string.kinetica_section_font, theme)
    Chips {
        FONT_CHOICES.forEach { (family, label) ->
            ToggleChip(label, text.fontFamily == family, theme) { edits.setFontFamily(node.id, family) }
        }
    }
    Chips {
        ToggleChip(R.string.kinetica_text_italic, text.italic, theme) { edits.toggleItalic(node.id) }
        ToggleChip(R.string.kinetica_text_underline, text.decoration == TextDecoration.Underline, theme) {
            edits.toggleDecoration(node.id, TextDecoration.Underline)
        }
        ToggleChip(R.string.kinetica_text_strike, text.decoration == TextDecoration.Strikethrough, theme) {
            edits.toggleDecoration(node.id, TextDecoration.Strikethrough)
        }
    }
    Chips {
        AUTO_SIZE_CHOICES.forEach { (mode, label) ->
            ToggleChip(label, text.autoSize == mode, theme) { edits.setAutoSize(node.id, mode) }
        }
    }
}

private val AUTO_SIZE_CHOICES = listOf(
    TextAutoSize.Fixed to R.string.kinetica_text_size_fixed,
    TextAutoSize.Height to R.string.kinetica_text_size_height,
    TextAutoSize.WidthAndHeight to R.string.kinetica_text_size_auto,
)

@Composable
private fun SectionTitle(label: Int, theme: StudioTheme) {
    Text(stringResource(label), color = theme.onPanel.copy(alpha = SECTION_ALPHA))
}

@Composable
private fun Chips(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
@Suppress("LongParameterList")
private fun Field(
    node: SceneNode,
    name: String,
    current: String,
    label: Int,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onCommit: (String) -> Boolean,
) {
    CommitTextField(
        current = current,
        key = node.id.value + name + current,
        label = stringResource(label),
        keyboardType = keyboard,
        enabled = true,
        modifier = modifier,
        onCommit = onCommit,
    )
}

private val FILL_MODES = listOf(
    FillMode.None to R.string.kinetica_fill_none,
    FillMode.Solid to R.string.kinetica_fill_solid,
    FillMode.Linear to R.string.kinetica_fill_linear,
    FillMode.Radial to R.string.kinetica_fill_radial,
)

private val SHADOW_FIELDS = listOf(
    ShadowField.X to R.string.kinetica_field_x,
    ShadowField.Y to R.string.kinetica_field_y,
    ShadowField.Blur to R.string.kinetica_field_blur,
    ShadowField.Spread to R.string.kinetica_field_spread,
)

private val CORNER_LABELS = listOf(
    R.string.kinetica_corner_top_left,
    R.string.kinetica_corner_top_right,
    R.string.kinetica_corner_bottom_right,
    R.string.kinetica_corner_bottom_left,
)

private const val SECTION_ALPHA = 0.6f
