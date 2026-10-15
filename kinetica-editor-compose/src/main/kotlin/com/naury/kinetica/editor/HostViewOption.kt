package com.naury.kinetica.editor

/**
 * 편집기의 "호스트 뷰" 메뉴에 보일 항목. [key]는 문서에 저장되고, 화면에는 같은 키로 등록한 Composable(`slots`)이 그려진다.
 *
 * @property label 메뉴에 보일 이름
 * @property width 처음 넣을 때의 너비(문서 단위, 0 초과)
 * @property height 처음 넣을 때의 높이(문서 단위, 0 초과)
 * @throws IllegalArgumentException 키 형식이 틀리거나 크기가 0 이하이거나 유한하지 않을 때
 */
public data class HostViewOption(val key: String, val label: String, val width: Double, val height: Double) {
    init {
        require(com.naury.kinetica.core.scene.isValidDataKey(key)) { "키는 영문자·숫자와 . _ -로 된 1..128자여야 합니다." }
        require(width > 0.0 && height > 0.0 && width.isFinite() && height.isFinite()) { "크기는 0보다 커야 합니다." }
    }
}
