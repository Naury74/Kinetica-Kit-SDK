package com.naury.kinetica.editor

/**
 * 편집기의 "호스트 뷰" 메뉴에 보일 항목. [key]는 문서에 저장되고, 화면에서는 같은 키의 Composable(`slots`)로 그려진다.
 *
 * @property label 메뉴 이름(사용자에게 보임)
 * @property width 처음 넣을 때 너비(문서 단위)
 * @property height 처음 넣을 때 높이(문서 단위)
 * @throws IllegalArgumentException 키 형식이 틀리거나 크기가 0 이하일 때
 */
public data class HostViewOption(val key: String, val label: String, val width: Double, val height: Double) {
    init {
        require(com.naury.kinetica.core.scene.isValidDataKey(key)) { "키는 영문자·숫자와 . _ -로 된 1..128자여야 합니다." }
        require(width > 0.0 && height > 0.0 && width.isFinite() && height.isFinite()) { "크기는 0보다 커야 합니다." }
    }
}
