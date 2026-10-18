package com.naury.kinetica.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * 입력란 이름 글자. 입력란 장식 상자 안에 둬서 편집 노드에 합쳐지게 한다. [showing]이면 안내 글로 보이고, 글을 쓰기 시작하면
 * 투명하게 남아 TalkBack이 계속 이름으로 읽는다. 투명할 때는 상자 크기에 끼어들지 않는다.
 *
 * 편집 노드의 contentDescription은 이름으로 쓰지 않는다. Compose는 자식이 있는 편집 노드의 contentDescription을 따로 만든 자식 노드로
 * 옮기는데, TalkBack은 입력란 구조에 따라 그것을 읽기도 하고 안 읽기도 해서 이름이 빠지거나 두 번 읽혔다.
 */
@Composable
internal fun BoxScope.FieldNameText(name: String, showing: Boolean, style: TextStyle) {
    ThemedText(
        name,
        if (showing) Modifier else Modifier.matchParentSize(),
        style = if (showing) style else style.copy(color = Color.Transparent),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
    )
}
