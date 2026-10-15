package com.naury.kinetica.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.scene.SceneDocument

/** 위젯 추가 버튼을 끝쪽 아래 모서리(RTL이면 왼쪽 아래)에 두고, 버튼과 메뉴의 글자는 호스트의 레이아웃 방향으로 그린다. */
@Composable
internal fun BoxScope.AddWidgetCorner(hostDirection: LayoutDirection, mirrored: Boolean, content: @Composable (Modifier) -> Unit) {
    val corner = Modifier.align(if (mirrored) Alignment.BottomStart else Alignment.BottomEnd).padding(16.dp)
    CompositionLocalProvider(LocalLayoutDirection provides hostDirection) { content(corner) }
}

@Composable
internal fun AddWidgetButton(state: KineticaBoardState, document: SceneDocument, colors: BoardColors, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    // document를 받아 배치가 바뀔 때마다 다시 계산한다.
    val available = remember(document) { state.availableWidgets }
    Box(modifier) {
        TextButton(onClick = { open = true }, modifier = Modifier.background(colors.accent, RoundedCornerShape(24.dp))) {
            AddLabel(stringResource(R.string.kinetica_board_add), colors.onAccent)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (available.isEmpty()) {
                DropdownMenuItem(text = {
                    Text(stringResource(R.string.kinetica_board_nothing_to_add))
                }, onClick = { open = false }, enabled = false)
            }
            available.forEach { widget ->
                DropdownMenuItem(text = { Text(widget.label) }, onClick = {
                    open = false
                    state.add(widget.key)
                })
            }
        }
    }
}

// 기호와 글자를 따로 두어야 RTL 언어에서도 + 기호가 시작쪽에 온다.
@Composable
private fun AddLabel(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlusMark(color)
        Spacer(Modifier.width(6.dp))
        Text(text, color = color)
    }
}

// 글꼴에 따라 굵기가 달라지지 않도록 직접 그린다.
@Composable
private fun PlusMark(color: Color) {
    Canvas(Modifier.size(MARK_SIZE)) {
        val stroke = MARK_STROKE.toPx()
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke)
        drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), stroke)
    }
}

/** 삭제 버튼의 − 기호. */
@Composable
internal fun RemoveMark(color: Color) {
    Canvas(Modifier.size(MARK_SIZE)) {
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), MARK_STROKE.toPx())
    }
}

private val MARK_SIZE = 10.dp
private val MARK_STROKE = 2.dp
