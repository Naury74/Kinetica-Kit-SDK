package com.naury.kinetica.ui

import androidx.compose.runtime.Immutable

/**
 * AI 프롬프트 입력창에 붙은 첨부 파일 하나. [id]로 칩을 구분하므로 목록 안에서 겹치지 않게 준다.
 *
 * @param progress 올린 정도(0..1). [status]가 [KAttachmentStatus.Uploading]일 때만 쓴다. null이면 진행 링이 돈다.
 */
@Immutable
public data class KPromptAttachment(
    val id: String,
    val name: String,
    val detail: String? = null,
    val status: KAttachmentStatus = KAttachmentStatus.Ready,
    val progress: Float? = null,
)
