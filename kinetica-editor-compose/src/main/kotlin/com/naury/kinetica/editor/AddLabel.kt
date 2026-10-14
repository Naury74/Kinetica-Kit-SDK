package com.naury.kinetica.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** "+ 글자" 버튼 글자. 기호와 글자를 따로 두어 오른쪽에서 왼쪽으로 쓰는 언어에서도 기호가 시작쪽에 온다. */
@Composable
internal fun AddLabel(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("+", color = color)
        Spacer(Modifier.width(4.dp))
        Text(text, color = color)
    }
}
