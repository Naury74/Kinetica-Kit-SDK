package com.naury.kinetica.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId

/** 글자 노드의 내용을 고치는 창. 확인을 누르면 실행 취소 한 단계로 적용하고, 올바르지 않으면 이유를 보여 준다. */
@Composable
internal fun TextEditDialog(state: EditorState, edits: PropertyEdits, id: NodeId, onDismiss: () -> Unit) {
    val current = (state.document.node(id)?.content as? NodeContent.Text)?.text ?: return onDismiss()
    var text by remember(id) { mutableStateOf(current) }
    var invalid by remember(id) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.kinetica_edit_text)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    invalid = false
                },
                isError = invalid,
                supportingText = if (invalid) {
                    { Text(stringResource(R.string.kinetica_invalid_value)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(TEXT_EDIT_TAG),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (edits.setText(id, text)) onDismiss() else invalid = true
            }) { Text(stringResource(R.string.kinetica_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.kinetica_cancel)) } },
    )
    LaunchedEffect(id) { focus.requestFocus() }
}

/** 테스트에서 글자 편집 입력칸을 찾을 때 쓰는 tag. */
internal const val TEXT_EDIT_TAG = "kinetica-text-edit"
