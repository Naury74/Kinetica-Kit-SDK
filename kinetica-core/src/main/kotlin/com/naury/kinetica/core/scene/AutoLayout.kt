package com.naury.kinetica.core.scene

import com.naury.kinetica.core.geometry.Size

/**
 * Frame의 자식을 한 줄로 자동 배치하는 규칙으로 Figma의 Auto Layout에 해당한다. 자식 위치는 이 규칙이 정하므로
 * 자식의 이동 값은 저장할 때마다 다시 계산된다. 숨긴 자식은 공간을 차지하지 않으며, [NodeContent.Frame.childIds] 순서대로 배치한다.
 *
 * @property gap 자식 사이 간격. 문서 단위이며 0 이상이다.
 * @property padding Frame 안쪽 여백. 문서 단위이며 0 이상이다.
 * @property hugWidth `true`면 Frame 너비를 자식에 맞춘다.
 * @property hugHeight `true`면 Frame 높이를 자식에 맞춘다.
 * @property wrap `true`면 배치 방향의 안쪽 길이를 넘는 자식을 다음 줄로 넘긴다. 배치 방향 크기를 내용에 맞추는 Frame에서는 무시한다.
 *   줄 바꿈을 켜면 [SceneNode.layoutFill]과 [CrossAlign.Stretch]는 적용하지 않는다.
 *   교차 정렬은 각 줄 안에 적용하고, 고정 크기 Frame이면 여러 줄 전체에도 적용한다.
 * @property lineGap 줄 바꿈할 때의 줄 간격. 문서 단위이며 0 이상이다.
 */
public data class AutoLayout(
    val direction: LayoutDirection = LayoutDirection.Vertical,
    val gap: Double = 0.0,
    val padding: Insets = Insets.Zero,
    val mainAlign: MainAlign = MainAlign.Start,
    val crossAlign: CrossAlign = CrossAlign.Start,
    val hugWidth: Boolean = false,
    val hugHeight: Boolean = false,
    val wrap: Boolean = false,
    val lineGap: Double = 0.0,
)

/** 자동 레이아웃 방향. [wireName]은 JSON에 저장할 때 쓰는 이름이다. */
public enum class LayoutDirection(public val wireName: String) {
    Horizontal("row"),
    Vertical("column"),
    ;

    public companion object {
        public fun fromWireName(name: String): LayoutDirection? = entries.firstOrNull { it.wireName == name }
    }
}

/** 배치 방향 정렬. [SpaceBetween]은 남는 공간을 자식 사이에 고르게 나누며, 이때 [AutoLayout.gap]은 최소 간격이 된다. */
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
 * 교차 방향 정렬. [Stretch]는 크기가 있고 회전이나 배율이 없는 자식을 Frame의 안쪽 너비 또는 높이까지 늘인다.
 * 자식이 그 축의 크기를 내용에 맞추는 자동 레이아웃 Frame이면 늘이지 않고 시작 쪽에 둔다.
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

/**
 * 노드의 최소·최대 크기. 문서 단위이며 `null`이면 제한이 없다. 자동 레이아웃, 제약, fill, stretch가 크기를 바꿀 때
 * 이 범위 안으로 맞추므로 저장된 문서는 항상 범위를 지킨다. 같은 축의 최솟값은 최댓값보다 클 수 없다.
 */
public data class SizeLimits(
    val minWidth: Double? = null,
    val maxWidth: Double? = null,
    val minHeight: Double? = null,
    val maxHeight: Double? = null,
) {
    /** [size]를 이 범위 안으로 맞춘 크기를 돌려준다. */
    public fun clamp(size: Size): Size = Size(clampAxis(size.width, minWidth, maxWidth), clampAxis(size.height, minHeight, maxHeight))

    internal val values: List<Double> get() = listOfNotNull(minWidth, maxWidth, minHeight, maxHeight)

    public companion object {
        public val None: SizeLimits = SizeLimits()

        private fun clampAxis(value: Double, min: Double?, max: Double?): Double {
            val atLeast = if (min != null) maxOf(value, min) else value
            return if (max != null) minOf(atLeast, max) else atLeast
        }
    }
}

/** 안쪽 여백. 문서 단위다. */
public data class Insets(val left: Double = 0.0, val top: Double = 0.0, val right: Double = 0.0, val bottom: Double = 0.0) {
    internal val values: List<Double> get() = listOf(left, top, right, bottom)

    public companion object {
        public val Zero: Insets = Insets()

        public fun all(value: Double): Insets = Insets(value, value, value, value)
    }
}

/**
 * 부모 Frame이나 아트보드의 크기가 바뀔 때 노드가 따라가는 방식으로 Figma의 constraints에 해당한다.
 * 자동 레이아웃 Frame의 자식에는 적용하지 않는다.
 */
public data class LayoutConstraints(val horizontal: Pin = Pin.Start, val vertical: Pin = Pin.Start) {
    public companion object {
        public val Default: LayoutConstraints = LayoutConstraints()
    }
}

/**
 * 한 축의 고정 방식.
 *
 * - [Start]: 왼쪽 또는 위쪽과의 거리를 유지한다.
 * - [End]: 오른쪽 또는 아래쪽과의 거리를 유지한다.
 * - [Both]: 양쪽 거리를 유지하며 크기가 늘어난다. 크기가 없는 노드는 [Start]처럼 움직인다.
 * - [Center]: 가운데로부터의 거리를 유지한다.
 * - [Scale]: 위치와 크기가 부모 크기 비율대로 바뀐다.
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
