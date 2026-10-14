package com.naury.kinetica.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.scene.BoundProperty
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import java.util.Locale

/**
 * 속성 패널(docs 24): 변형(X·Y·너비·높이·회전), 모양(불투명도·채우기). 노드 하나를 골랐을 때만 고칠 수 있다.
 * 입력은 키보드 완료나 포커스를 잃을 때 적용하고, 올바르지 않으면 이전 값으로 되돌리며 오류를 알린다.
 */
@Composable
@Suppress("LongParameterList")
internal fun InspectorPanel(
    node: SceneNode?,
    document: SceneDocument,
    edits: InspectorEdits,
    theme: StudioTheme,
    modifier: Modifier = Modifier,
    onInsert: (SceneNode) -> Unit = {},
) {
    if (node == null) {
        DocumentPanel(document, edits.component, theme, onInsert, modifier)
        return
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ComponentNodeSection(node, document, edits.component, theme)
        TransformSection(node, edits.property)
        AppearanceSection(node, edits.property, theme)
        CornersSection(node, edits.shape, theme)
        (node.content as? NodeContent.Polygon)?.let { PolygonSection(node, it, edits.shape, theme) }
        (node.content as? NodeContent.Text)?.let {
            TextSection(node, it, edits.property)
            FontSection(node, it, edits.text, theme)
        }
        FillSection(node, edits.style, edits.property, theme)
        FillTokenChips(node, document.tokens.keys, edits.component, theme)
        StrokeSection(node, edits.style, theme)
        EffectsSection(node, edits.effects, theme)
        (node.content as? NodeContent.Frame)?.let { AutoLayoutSection(node, it, edits.layout, theme) }
        if (edits.layout.isLaidOut(node)) LayoutChildSection(node, edits.layout, theme) else ConstraintsSection(node, edits.layout, theme)
        SizeLimitsSection(node, edits.layout, theme)
        MotionSection(node, edits.motion, theme)
        BindingFields(node, edits.property, theme)
        AccessibilityFields(node, edits.accessibility, theme)
    }
}

@Composable
private fun TransformSection(node: SceneNode, edits: PropertyEdits) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberInput(node, NumericField.X, R.string.kinetica_field_x, edits, Modifier.weight(1f))
        NumberInput(node, NumericField.Y, R.string.kinetica_field_y, edits, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberInput(node, NumericField.Width, R.string.kinetica_field_width, edits, Modifier.weight(1f))
        NumberInput(node, NumericField.Height, R.string.kinetica_field_height, edits, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberInput(node, NumericField.Rotation, R.string.kinetica_field_rotation, edits, Modifier.weight(1f))
        NumberInput(node, NumericField.Opacity, R.string.kinetica_field_opacity, edits, Modifier.weight(1f))
    }
}

@Composable
private fun AppearanceSection(node: SceneNode, edits: PropertyEdits, theme: StudioTheme) {
    val content = node.content
    if (content is NodeContent.Rectangle || content is NodeContent.Frame) {
        NumberInput(node, NumericField.CornerRadius, R.string.kinetica_field_corner, edits, Modifier.fillMaxWidth())
    }
    (content as? NodeContent.Frame)?.let { frame ->
        ToggleChip(R.string.kinetica_field_clip, frame.clip, theme) { edits.toggleClip(node.id) }
    }
}

@Composable
private fun TextSection(node: SceneNode, content: NodeContent.Text, edits: PropertyEdits) {
    CommitTextField(
        current = content.text,
        key = node.id.value + "text" + content.text,
        label = stringResource(R.string.kinetica_field_text),
        keyboardType = KeyboardType.Text,
        enabled = true,
        singleLine = false,
        modifier = Modifier.fillMaxWidth(),
    ) { edits.setText(node.id, it) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CommitTextField(
            current = formatNumber(content.fontSize),
            key = node.id.value + "font" + content.fontSize,
            label = stringResource(R.string.kinetica_field_font_size),
            keyboardType = KeyboardType.Decimal,
            enabled = true,
            modifier = Modifier.weight(1f),
        ) { edits.setFontSize(node.id, it) }
        NumberInput(node, NumericField.FontWeight, R.string.kinetica_field_font_weight, edits, Modifier.weight(1f))
    }
}

/** 화면 읽기 이름과 장식(읽지 않음) 표시. */
@Composable
private fun AccessibilityFields(node: SceneNode, edits: AccessibilityEdits, theme: StudioTheme) {
    Text(stringResource(R.string.kinetica_section_accessibility), color = theme.onPanel.copy(alpha = HINT_ALPHA))
    CommitTextField(
        current = node.accessibility.label.orEmpty(),
        key = node.id.value + "a11y" + node.accessibility.label,
        label = stringResource(R.string.kinetica_field_accessibility_label),
        keyboardType = KeyboardType.Text,
        enabled = !node.accessibility.hidden,
        modifier = Modifier.fillMaxWidth(),
    ) { edits.set(node.id, label = it) }
    ToggleChip(R.string.kinetica_accessibility_hidden, node.accessibility.hidden, theme) {
        edits.set(node.id, hidden = !node.accessibility.hidden)
    }
}

/** 데이터 키·동작 이름 입력. 빈칸이면 연결을 푼다. */
@Composable
private fun BindingFields(node: SceneNode, edits: PropertyEdits, theme: StudioTheme) {
    Text(stringResource(R.string.kinetica_section_data), color = theme.onPanel.copy(alpha = HINT_ALPHA))
    if (node.content is NodeContent.Text) {
        KeyInput(node, "text", node.bindings[BoundProperty.Text], R.string.kinetica_field_bind_text) {
            edits.setBinding(node.id, BoundProperty.Text, it)
        }
    }
    KeyInput(node, "visible", node.bindings[BoundProperty.Visible], R.string.kinetica_field_bind_visible) {
        edits.setBinding(node.id, BoundProperty.Visible, it)
    }
    if (node.instanceOf != null) {
        KeyInput(node, "variant", node.bindings[BoundProperty.Variant], R.string.kinetica_field_bind_variant) {
            edits.setBinding(node.id, BoundProperty.Variant, it)
        }
    }
    KeyInput(node, "action", node.action, R.string.kinetica_field_action) { edits.setAction(node.id, it) }
}

@Composable
private fun KeyInput(node: SceneNode, field: String, current: String?, label: Int, onCommit: (String) -> Boolean) {
    CommitTextField(
        current = current.orEmpty(),
        key = node.id.value + field + current,
        label = stringResource(label),
        keyboardType = KeyboardType.Ascii,
        enabled = true,
        modifier = Modifier.fillMaxWidth(),
        onCommit = onCommit,
    )
}

@Composable
internal fun ToggleChip(label: Int, checked: Boolean, theme: StudioTheme, onToggle: () -> Unit) {
    ToggleChip(stringResource(label), checked, theme, onToggle)
}

@Composable
internal fun ToggleChip(label: String, checked: Boolean, theme: StudioTheme, onToggle: () -> Unit) {
    FilterChip(
        selected = checked,
        onClick = onToggle,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = theme.onPanel,
            selectedContainerColor = theme.accent.copy(alpha = CHIP_ALPHA),
        ),
    )
}

@Composable
internal fun NumberInput(node: SceneNode, field: NumericField, label: Int, edits: PropertyEdits, modifier: Modifier) {
    val value = edits.value(node, field)
    CommitTextField(
        current = value?.let(::formatNumber).orEmpty(),
        key = node.id.value + field + value,
        label = stringResource(label),
        keyboardType = KeyboardType.Decimal,
        enabled = value != null,
        modifier = modifier,
    ) { edits.setNumber(node.id, field, it) }
}

/** 입력 중에는 문서를 바꾸지 않고, 완료하거나 포커스를 잃을 때 [onCommit]한다. 실패하면 [current]로 되돌린다. */
@Composable
internal fun CommitTextField(
    current: String,
    key: String,
    label: String,
    keyboardType: KeyboardType,
    enabled: Boolean,
    modifier: Modifier,
    singleLine: Boolean = true,
    onCommit: (String) -> Boolean,
) {
    var text by remember(key) { mutableStateOf(current) }
    var invalid by remember(key) { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val latestText by rememberUpdatedState(text)
    val latestCommit by rememberUpdatedState(onCommit)
    fun commit() {
        if (text == current) return
        invalid = !onCommit(text)
        if (invalid) text = current
    }
    // 시트나 패널이 닫혀 입력칸이 사라질 때도 쓰던 값을 버리지 않는다.
    DisposableEffect(key) {
        onDispose { if (focused && latestText != current) latestCommit(latestText) }
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            invalid = false
        },
        label = { Text(label) },
        singleLine = singleLine,
        enabled = enabled,
        isError = invalid,
        supportingText = if (invalid) {
            { Text(stringResource(R.string.kinetica_invalid_value)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = modifier.onFocusChanged {
            if (focused && !it.isFocused) commit()
            focused = it.isFocused
        },
    )
}

private const val DECIMALS_SCALE = 100.0
private const val HINT_ALPHA = 0.6f
private const val CHIP_ALPHA = 0.3f

/** 소수점 둘째 자리까지, 끝의 0은 뺀다. 기기 언어와 상관없이 `.`을 쓴다. */
internal fun formatNumber(value: Double): String {
    val rounded = Math.round(value * DECIMALS_SCALE) / DECIMALS_SCALE
    return if (rounded == Math.rint(rounded)) rounded.toLong().toString() else String.format(Locale.ROOT, "%s", rounded)
}
