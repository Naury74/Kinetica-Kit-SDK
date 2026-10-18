package com.naury.kinetica.ui

import androidx.compose.ui.semantics.CustomAccessibilityAction

/** Android가 노드 하나에 받는 사용자 정의 동작 수. 넘기면 Compose가 TalkBack 노드 정보를 만들다 앱을 멈춘다. */
internal const val MAX_CUSTOM_ACTIONS = 32

/** 데이터 수만큼 만드는 사용자 정의 동작을 [MAX_CUSTOM_ACTIONS]개까지만 남긴다. 넘치는 동작은 화면의 다른 길로 할 수 있어야 한다. */
internal fun List<CustomAccessibilityAction>.fitted(): List<CustomAccessibilityAction> = take(MAX_CUSTOM_ACTIONS)
