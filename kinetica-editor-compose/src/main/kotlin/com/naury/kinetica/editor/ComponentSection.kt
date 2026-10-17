package com.naury.kinetica.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.scene.ComponentVariants
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode

// 아무것도 선택하지 않았을 때 보이는 문서 설정(디자인 토큰, 컴포넌트).
@Composable
internal fun DocumentPanel(
    document: SceneDocument,
    edits: ComponentEdits,
    theme: StudioTheme,
    onInsert: (SceneNode) -> Unit,
    modifier: Modifier = Modifier,
    documentFields: @Composable () -> Unit = {},
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.kinetica_properties_empty), color = theme.onPanel.copy(alpha = MUTED))
        documentFields()
        Title(R.string.kinetica_section_tokens, theme)
        document.tokens.toSortedMap().forEach { (name, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(SWATCH).background(Color(color.argb), CircleShape))
                CommitTextField(
                    current = color.toHex(),
                    key = name + color,
                    label = name,
                    keyboardType = KeyboardType.Ascii,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                ) { edits.setToken(name, it) }
                StudioTextButton(onClick = {
                    edits.removeToken(name)
                }) { Text(stringResource(R.string.kinetica_remove), color = theme.accent) }
            }
        }
        NewToken(edits)
        Title(R.string.kinetica_section_components, theme)
        val mains = document.nodes.values.filter { it.component != null }.sortedBy { it.component }
        if (mains.isEmpty()) Text(stringResource(R.string.kinetica_components_empty), color = theme.onPanel.copy(alpha = MUTED))
        mains.forEach { main ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(main.component.orEmpty(), color = theme.onPanel, modifier = Modifier.weight(1f))
                StudioTextButton(onClick = {
                    onInsert(main)
                }) { Text(stringResource(R.string.kinetica_insert_instance), color = theme.accent) }
            }
        }
        StudioTextButton(onClick = {
            edits.insertStarterKit()
        }) { AddLabel(stringResource(R.string.kinetica_insert_kit), theme.accent) }
    }
}

@Composable
private fun NewToken(edits: ComponentEdits) {
    var name by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CommitTextField(
            current = name,
            key = "new-token-name$name",
            label = stringResource(R.string.kinetica_field_token_name),
            keyboardType = KeyboardType.Ascii,
            enabled = true,
            modifier = Modifier.weight(1f),
        ) {
            name = it.trim()
            name.isNotEmpty()
        }
        CommitTextField(
            current = "",
            key = "new-token-color$name",
            label = stringResource(R.string.kinetica_field_token_color),
            keyboardType = KeyboardType.Ascii,
            enabled = name.isNotEmpty(),
            modifier = Modifier.weight(1f),
        ) { color ->
            edits.setToken(name, color).also { if (it) name = "" }
        }
    }
}

// 메인 컴포넌트면 이름을, 인스턴스면 메인으로 이동·오버라이드 재설정·분리 버튼을 보여 준다.
@Composable
internal fun ComponentNodeSection(node: SceneNode, document: SceneDocument, edits: ComponentEdits, theme: StudioTheme) {
    val component = node.component
    val link = node.instanceOf
    when {
        component != null -> {
            Title(R.string.kinetica_section_component, theme)
            CommitTextField(
                current = component,
                key = node.id.value + "component" + component,
                label = stringResource(R.string.kinetica_field_component_name),
                keyboardType = KeyboardType.Text,
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
            ) { edits.rename(node.id, it) }
            StudioTextButton(onClick = { edits.variants.addVariant(node.id) }) {
                AddLabel(stringResource(R.string.kinetica_add_variant), theme.accent)
            }
        }

        link != null -> {
            Title(R.string.kinetica_section_instance, theme)
            val variants = ComponentVariants.variantsOf(document, link.main)
            if (variants.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    variants.forEach { variant ->
                        val label = variant.component?.let(ComponentVariants::variantName).orEmpty()
                        ToggleChip(label, variant.id == link.main, theme) { edits.variants.swapVariant(node.id, variant.id) }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StudioTextButton(onClick = {
                    edits.selectMain(node.id)
                }) { Text(stringResource(R.string.kinetica_go_to_main), color = theme.accent) }
                StudioTextButton(onClick = { edits.resetOverrides(node.id) }, enabled = link.overrides.isNotEmpty()) {
                    Text(stringResource(R.string.kinetica_reset_overrides, link.overrides.size), color = theme.accent)
                }
                StudioTextButton(onClick = {
                    edits.detach(node.id)
                }) { Text(stringResource(R.string.kinetica_detach), color = theme.accent) }
            }
        }
    }
}

// 채우기 색을 문서 토큰에 연결하는 칩.
@Composable
internal fun FillTokenChips(node: SceneNode, tokens: Set<String>, edits: ComponentEdits, theme: StudioTheme) {
    if (tokens.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tokens.sorted().forEach { name ->
            val linked = node.style.fillToken == name
            ToggleChip(name, linked, theme) { edits.linkFill(node.id, if (linked) null else name) }
        }
    }
}

@Composable
private fun Title(label: Int, theme: StudioTheme) {
    Text(stringResource(label), color = theme.onPanel.copy(alpha = MUTED))
}

private const val MUTED = 0.6f
private val SWATCH = 24.dp
