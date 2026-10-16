// 파일 이름은 대표 컴포저블인 KTextArea를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
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

/**
 * [TextFieldState]를 받는 여러 줄 입력란. 자주 다시 그려지는 화면에서도 입력 중인 글자를 잃지 않는다.
 * 줄 수, 글자 수 제한과 표시, 모양은 String을 받는 [KTextArea]와 같다.
 *
 * ```kotlin
 * val memo = rememberTextFieldState()
 * KTextArea(memo, "메모", maxLength = 200)
 * ```
 *
 * @param minLines 최소 줄 수. 1 미만이거나 [maxLines]보다 크면 [IllegalArgumentException]을 던진다.
 * @param maxLength 최대 글자 수. 꽉 찬 뒤의 입력은 받지 않고 아래 끝에 글자 수를 보여 준다. `null`이면 제한이 없다.
 * @param inputTransformation 사용자 입력을 거르거나 고치는 변환. [maxLength]가 있으면 이 변환 뒤에 글자 수 제한을 이어 붙인다.
 * @param onKeyboardAction IME 동작 키를 눌렀을 때 할 일. 여러 줄 입력이라 기본 키는 줄 바꿈이고, 이 값은 [keyboardOptions]로
 *   다른 IME 동작을 정했을 때만 불린다.
 */
@Composable
public fun KTextArea(
    state: TextFieldState,
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
    onKeyboardAction: KeyboardActionHandler? = null,
    inputTransformation: InputTransformation? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    KTextField(
        state = state,
        label = label,
        modifier = modifier,
        error = error,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        inputTransformation = inputTransformation,
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
