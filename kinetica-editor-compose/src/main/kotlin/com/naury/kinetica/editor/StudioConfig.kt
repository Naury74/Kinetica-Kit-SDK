package com.naury.kinetica.editor

/**
 * 편집기 동작 설정.
 *
 * @property snapping 드래그할 때 다른 노드와 artboard의 가이드선에 스냅할지 여부
 * @property gridSize 격자 간격(문서 단위). `null`이면 격자에 스냅하지 않는다.
 * @property rotationSnapDegrees 회전 스냅 간격(도). 0보다 크면 회전 각도를 이 값의 배수로 맞춘다.
 */
public data class StudioConfig(val snapping: Boolean = true, val gridSize: Double? = null, val rotationSnapDegrees: Double = 0.0)
