package com.naury.kinetica.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics

/**
 * 입력란 접근성 노드에 이름을 붙인다. 안내 글이나 라벨은 화면에만 그리는 글자라서 글을 쓰기 시작해 사라지면 TalkBack이
 * '편집 상자'라고만 읽는다. 이름을 입력란 semantics에 따로 넣어 두면 글자가 있든 없든 같은 이름을 읽는다.
 * [name]이 null이거나 비어 있으면 아무것도 하지 않는다.
 */
internal fun Modifier.fieldName(name: String?): Modifier = if (name.isNullOrEmpty()) this else semantics { contentDescription = name }

/** [fieldName]으로 이름을 붙인 입력란 안의 안내 글이나 라벨. 같은 말을 두 번 읽지 않게 접근성 트리에서 숨긴다. */
internal fun Modifier.fieldNameText(): Modifier = semantics { hideFromAccessibility() }
