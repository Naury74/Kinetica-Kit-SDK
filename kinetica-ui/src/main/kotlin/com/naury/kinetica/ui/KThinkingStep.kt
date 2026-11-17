package com.naury.kinetica.ui

import androidx.compose.runtime.Immutable

/**
 * 추론 단계 하나. [id]로 단계를 구분하므로 목록 안에서 겹치지 않게 준다.
 *
 * @param done 끝난 단계인지. 끝나지 않은 단계는 생각하는 동안 진행 중으로, 생각이 끝나면 멈춤으로 보인다.
 */
@Immutable
public data class KThinkingStep(val id: String, val title: String, val done: Boolean)
