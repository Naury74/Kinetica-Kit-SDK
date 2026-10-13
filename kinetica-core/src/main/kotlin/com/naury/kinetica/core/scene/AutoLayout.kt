package com.naury.kinetica.core.scene

/**
 * Frame 자식을 한 줄로 자동 배치하는 규칙(Figma의 Auto Layout). 자식 위치는 이 규칙이 정하므로 자식의 이동 값은 저장할 때마다 다시 계산된다.
 * 숨긴 자식은 자리를 차지하지 않는다. 자식 순서([NodeContent.Frame.childIds])가 배치 순서다.
 *
 * @property gap 자식 사이 간격(문서 단위, 0 이상)
 * @property padding Frame 안쪽 여백(문서 단위, 0 이상)
 * @property hugWidth `true`면 Frame 너비를 자식에 맞춘다
 * @property hugHeight `true`면 Frame 높이를 자식에 맞춘다
 */
public data class AutoLayout(
    val direction: LayoutDirection = LayoutDirection.Vertical,
    val gap: Double = 0.0,
    val padding: Insets = Insets.Zero,
    val mainAlign: MainAlign = MainAlign.Start,
    val crossAlign: CrossAlign = CrossAlign.Start,
    val hugWidth: Boolean = false,
    val hugHeight: Boolean = false,
)

/** 자동 배치 방향. [wireName]은 저장 형식에서 쓰는 이름이다. */
public enum class LayoutDirection(public val wireName: String) {
    Horizontal("row"),
    Vertical("column"),
    ;

    public companion object {
        public fun fromWireName(name: String): LayoutDirection? = entries.firstOrNull { it.wireName == name }
    }
}

/** 배치 방향 정렬. [SpaceBetween]은 남는 공간을 자식 사이에 고르게 나눈다(간격은 최소값이 된다). */
public enum class MainAlign(public val wireName: String) {
    Start("start"),
    Center("center"),
    End("end"),
    SpaceBetween("between"),
    ;

    public companion object {
        public fun fromWireName(name: String): MainAlign? = entries.firstOrNull { it.wireName == name }
    }
}

/**
 * 교차 방향 정렬. [Stretch]는 크기가 있고 회전·배율이 없는 자식을 Frame 안쪽 폭(높이)까지 늘인다.
 * 그 축을 내용에 맞추는 자동 배치 Frame 자식은 늘이지 않고 시작에 둔다.
 */
public enum class CrossAlign(public val wireName: String) {
    Start("start"),
    Center("center"),
    End("end"),
    Stretch("stretch"),
    ;

    public companion object {
        public fun fromWireName(name: String): CrossAlign? = entries.firstOrNull { it.wireName == name }
    }
}

/** 안쪽 여백(문서 단위). */
public data class Insets(val left: Double = 0.0, val top: Double = 0.0, val right: Double = 0.0, val bottom: Double = 0.0) {
    internal val values: List<Double> get() = listOf(left, top, right, bottom)

    public companion object {
        public val Zero: Insets = Insets()

        public fun all(value: Double): Insets = Insets(value, value, value, value)
    }
}

/**
 * 부모(Frame 또는 artboard)의 크기가 바뀔 때 노드가 따라가는 방식(Figma의 constraints). 자동 배치 Frame의 자식에는 쓰지 않는다.
 */
public data class LayoutConstraints(val horizontal: Pin = Pin.Start, val vertical: Pin = Pin.Start) {
    public companion object {
        public val Default: LayoutConstraints = LayoutConstraints()
    }
}

/**
 * 한 축의 고정 방식.
 *
 * - [Start]: 왼쪽(위쪽)과의 거리 유지
 * - [End]: 오른쪽(아래쪽)과의 거리 유지
 * - [Both]: 양쪽 거리를 유지하며 크기가 늘어난다(크기가 없는 노드는 [Start]처럼 움직인다)
 * - [Center]: 가운데로부터의 거리 유지
 * - [Scale]: 위치·크기가 부모 크기 비율대로 바뀐다
 */
public enum class Pin(public val wireName: String) {
    Start("start"),
    End("end"),
    Both("both"),
    Center("center"),
    Scale("scale"),
    ;

    public companion object {
        public fun fromWireName(name: String): Pin? = entries.firstOrNull { it.wireName == name }
    }
}
