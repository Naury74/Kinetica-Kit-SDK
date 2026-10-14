package com.naury.kinetica.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.scene.BoundProperty
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.SceneNode

/** 글자에 데이터를 묶었을 때 숫자를 보여 줄 형식(앞 글자·뒤 글자·소수 자릿수). 묶지 않았으면 보이지 않는다. */
@Composable
internal fun NumberFormatFields(node: SceneNode, content: NodeContent.Text, edits: TextEdits, theme: StudioTheme) {
    if (BoundProperty.Text !in node.bindings) return
    val format = content.numberFormat
    Text(stringResource(R.string.kinetica_number_format), color = theme.onPanel.copy(alpha = HINT))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(
            node.id.value + "nf-p" + format?.prefix,
            format?.prefix.orEmpty(),
            R.string.kinetica_number_prefix,
            KeyboardType.Text,
            Modifier.weight(1f),
        ) {
            edits.setNumberFormat(node.id, prefix = it)
        }
        Field(
            node.id.value + "nf-s" + format?.suffix,
            format?.suffix.orEmpty(),
            R.string.kinetica_number_suffix,
            KeyboardType.Text,
            Modifier.weight(1f),
        ) {
            edits.setNumberFormat(node.id, suffix = it)
        }
        Field(
            node.id.value + "nf-d" + format?.decimals,
            format?.decimals?.toString().orEmpty(),
            R.string.kinetica_number_decimals,
            KeyboardType.Number,
            Modifier.weight(1f),
        ) { edits.setNumberFormat(node.id, decimalsText = it) }
    }
}

@Composable
private fun Field(key: String, current: String, label: Int, keyboard: KeyboardType, modifier: Modifier, onCommit: (String) -> Boolean) {
    CommitTextField(
        current = current,
        key = key,
        label = stringResource(label),
        keyboardType = keyboard,
        enabled = true,
        modifier = modifier,
        onCommit = onCommit,
    )
}

private const val HINT = 0.7f
