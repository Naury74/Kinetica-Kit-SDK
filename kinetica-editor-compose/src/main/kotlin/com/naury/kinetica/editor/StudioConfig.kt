package com.naury.kinetica.editor

/**
 * 편집기 동작 설정.
 *
 * @property snapping 옮길 때 노드·artboard 안내선에 붙인다.
 * @property gridSize 문서 단위 격자 간격. `null`이면 격자에 붙지 않는다.
 * @property rotationSnapDegrees 0보다 크면 회전을 이 배수로 맞춘다.
 */
public data class StudioConfig(val snapping: Boolean = true, val gridSize: Double? = null, val rotationSnapDegrees: Double = 0.0)
