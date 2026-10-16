package com.naury.kinetica.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.then
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation

/**
 * 입력란 글자를 어디에 두는지. String 값과 콜백을 받는 입력란과 [TextFieldState]를 받는 입력란이 장식, 애니메이션, 글자 수 표시를
 * 함께 쓰도록 BasicTextField 호출만 갈라 둔다.
 */
internal sealed class FieldSource {
    /** 지금 글자. 컴포지션에서 읽으면 글자가 바뀔 때 다시 구성한다. */
    abstract val text: CharSequence

    /** 글자를 모두 지운다. 지우기 버튼이 쓴다. */
    abstract fun clear()

    /** String 값 방식. [maxLength]를 넘는 입력은 잘라서 [onValueChange]에 넘긴다. */
    class Value(
        val value: String,
        onValueChange: (String) -> Unit,
        val keyboardActions: KeyboardActions = KeyboardActions.Default,
        val visualTransformation: VisualTransformation = VisualTransformation.None,
        maxLength: Int? = null,
    ) : FieldSource() {
        val onValueChange: (String) -> Unit = if (maxLength == null) onValueChange else { next -> onValueChange(next.take(maxLength)) }
        override val text: CharSequence get() = value

        override fun clear() = onValueChange("")
    }

    /** [TextFieldState] 방식. [maxLength]는 호출한 쪽 [inputTransformation] 뒤에 이어 붙인다. */
    class State(
        val state: TextFieldState,
        inputTransformation: InputTransformation? = null,
        val outputTransformation: OutputTransformation? = null,
        val onKeyboardAction: KeyboardActionHandler? = null,
        maxLength: Int? = null,
    ) : FieldSource() {
        val inputTransformation: InputTransformation? = when {
            maxLength == null -> inputTransformation
            inputTransformation == null -> MaxLengthTransformation(maxLength)
            else -> inputTransformation.then(MaxLengthTransformation(maxLength))
        }
        override val text: CharSequence get() = state.text

        override fun clear() = state.clearText()
    }
}

/**
 * 최대 글자 수를 지키는 입력 변환. 이미 꽉 찬 상태에서 더 넣으면 그 입력을 되돌리고, 여유가 있을 때 길게 붙여 넣으면 넘치는 끝부분만 자른다.
 * String 방식 입력란이 넘친 값을 잘라 넘기는 것과 결과를 맞춘다.
 */
internal class MaxLengthTransformation(private val max: Int) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (length <= max) return
        if (originalText.length >= max) revertAllChanges() else replace(max, length, "")
    }

    override fun equals(other: Any?): Boolean = other is MaxLengthTransformation && other.max == max

    override fun hashCode(): Int = max
}

/** 두 방식에 같은 BasicTextField 설정 */
internal class FieldBasics(
    val enabled: Boolean,
    val readOnly: Boolean,
    val lines: FieldLines,
    val textStyle: TextStyle,
    val cursorBrush: Brush,
    val input: FieldInput = FieldInput(KeyboardOptions.Default, null),
)

/** [source] 방식에 맞는 BasicTextField를 그린다. [decoration]은 두 방식이 같이 쓰는 장식이다. */
@Composable
internal fun SourceTextField(
    source: FieldSource,
    modifier: Modifier,
    basics: FieldBasics,
    decoration: @Composable (innerTextField: @Composable () -> Unit) -> Unit,
) {
    val lines = basics.lines
    when (source) {
        is FieldSource.Value -> BasicTextField(
            source.value,
            source.onValueChange,
            modifier,
            enabled = basics.enabled,
            readOnly = basics.readOnly,
            textStyle = basics.textStyle,
            keyboardOptions = basics.input.keyboardOptions,
            keyboardActions = source.keyboardActions,
            singleLine = lines.single,
            maxLines = lines.visibleMax,
            minLines = lines.visibleMin,
            visualTransformation = source.visualTransformation,
            interactionSource = basics.input.interactionSource,
            cursorBrush = basics.cursorBrush,
            decorationBox = decoration,
        )

        is FieldSource.State -> BasicTextField(
            source.state,
            modifier,
            enabled = basics.enabled,
            readOnly = basics.readOnly,
            inputTransformation = source.inputTransformation,
            textStyle = basics.textStyle,
            keyboardOptions = basics.input.keyboardOptions,
            onKeyboardAction = source.onKeyboardAction,
            lineLimits = if (lines.single) {
                TextFieldLineLimits.SingleLine
            } else {
                TextFieldLineLimits.MultiLine(lines.visibleMin, lines.visibleMax)
            },
            interactionSource = basics.input.interactionSource,
            cursorBrush = basics.cursorBrush,
            outputTransformation = source.outputTransformation,
            decorator = TextFieldDecorator { inner -> decoration(inner) },
        )
    }
}
