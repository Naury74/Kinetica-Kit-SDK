// 파일 이름은 대표 컴포저블인 KTextArea를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 여러 줄 입력란. 비어 있어도 [minLines]줄 높이를 차지하고, 글자가 늘면 [maxLines]줄까지 자라다가 그 뒤로는 안에서 스크롤한다.
 * 라벨은 가운데가 아니라 첫 줄 자리에 있다가 위로 떠오른다. 지우기 버튼은 없다. 모양과 글자 수 표시는 [KTextField]와 같다.
 *
 * ```kotlin
 * KTextArea(memo, { memo = it }, "메모", maxLength = 200)
 * ```
 *
 * @param minLines 최소 줄 수. 1 미만이거나 [maxLines]보다 크면 [IllegalArgumentException]을 던진다.
 * @param maxLines 자라는 최대 줄 수
 * @param maxLength 최대 글자 수. 넘는 입력은 잘라서 넘기고 아래 끝에 글자 수를 보여 준다. `null`이면 제한이 없다.
 * @see KTextField
 */
@Composable
public fun KTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    variant: KTextFieldVariant = KTextFieldVariant.Surface,
    minLines: Int = 3,
    maxLines: Int = 8,
    maxLength: Int? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    KTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        error = error,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = false,
        maxLines = maxLines,
        surface = surface,
        placeholder = placeholder,
        supportingText = supportingText,
        clearable = false,
        minLines = minLines,
        variant = variant,
        maxLength = maxLength,
    )
}
