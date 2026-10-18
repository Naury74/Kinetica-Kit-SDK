package com.naury.kinetica.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp

/**
 * 차지하는 높이를 최소 터치 크기(48dp) 이상으로 늘리고 내용은 가운데에 둔다. 늘어난 자리에는 아무것도 겹치지 않으므로
 * Compose가 누르는 영역을 그만큼 넓혀 준다.
 */
internal fun Modifier.minTouchHeight(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val height = maxOf(placeable.height, MIN_TOUCH.roundToPx()).coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(placeable.width, height) { placeable.placeRelative(0, (height - placeable.height) / 2) }
}

private val MIN_TOUCH = 48.dp
