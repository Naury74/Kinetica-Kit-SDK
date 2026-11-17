package com.naury.kinetica.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * [KRadarChart]에 그릴 계열 하나.
 *
 * 생성할 때 [values]에 NaN이나 무한대가 있으면 [IllegalArgumentException]을 던진다.
 *
 * @property values 축마다 값. 축 순서와 같고 개수도 축 수와 같아야 한다.
 * @property color 선과 채우기 색. 채우기는 이 색을 반투명하게 칠한다.
 * @property label 계열 이름. 범례, 값 말풍선, TalkBack이 읽는다. `null`이면 "1번째 계열"처럼 순서로 읽는다.
 */
@Immutable
public data class RadarSeries(val values: List<Float>, val color: Color, val label: String? = null) {
    init {
        require(values.all { it.isFinite() }) { "values에 NaN이나 무한대를 넣을 수 없습니다." }
    }
}

/**
 * [KTreemap]의 항목 하나.
 *
 * 생성할 때 [value]가 음수이거나 NaN, 무한대이면 [IllegalArgumentException]을 던진다.
 *
 * @property key 항목을 구별하는 값. 선택 강조와 재구성 사이 상태를 이 값에 묶으므로 목록 안에서 겹치면 안 된다.
 * @property label 이름. 칸 안에 들어가면 적고 TalkBack이 읽는다.
 * @property value 크기. 모든 항목 합에 대한 비율로 넓이를 정한다. 0이면 그리지 않는다.
 * @property color 칸 색. `null`이면 큰 항목부터 차례로 팔레트 색을 쓴다.
 */
@Immutable
public data class TreemapItem(val key: Any, val label: String, val value: Float, val color: Color? = null) {
    init {
        require(value.isFinite() && value >= 0f) { "value는 0 이상의 유한한 값이어야 합니다." }
    }
}
