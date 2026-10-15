package com.naury.kinetica.core.animation

import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.NodeId

/**
 * 노드 속성을 시간에 따라 바꾸는 애니메이션 클립 하나.
 *
 * 한 timeline 안에서 같은 노드와 속성의 track은 하나뿐이다. 시간은 timeline 시작 시점부터 잰 밀리초다.
 *
 * @property durationMs 재생 길이(ms). 0 이상이며 0이면 정지 장면으로 재생한다.
 * @property fpsHint 내보내기와 미리보기에 쓰는 권장 초당 프레임 수. 평가 결과에는 영향이 없다.
 */
public data class Timeline(
    val id: String,
    val durationMs: Long,
    val tracks: List<PropertyTrack> = emptyList(),
    val markers: List<TimelineMarker> = emptyList(),
    val fpsHint: Int = DEFAULT_FPS,
) {
    public fun track(nodeId: NodeId, property: AnimatableProperty): PropertyTrack? =
        tracks.firstOrNull { it.nodeId == nodeId && it.property == property }

    public companion object {
        public const val DEFAULT_FPS: Int = 60
    }
}

/**
 * 한 노드의 한 속성에 대한 keyframe 목록. 시간 순서로 정렬되어 있으며, 같은 시간에 keyframe이 둘 이상 있을 수 없다.
 */
public data class PropertyTrack(val nodeId: NodeId, val property: AnimatableProperty, val keyframes: List<Keyframe>)

/**
 * @property timeMs timeline 시작 시점부터 잰 밀리초. `0..durationMs`
 * @property easing 이 keyframe에서 다음 keyframe까지 구간의 easing. 마지막 keyframe의 easing은 쓰지 않는다.
 * @property rotation 이 keyframe에서 다음 keyframe까지 도는 방향. 회전 track에서만 쓰고, 마지막 keyframe의 값은 쓰지 않는다.
 */
public data class Keyframe(
    val timeMs: Long,
    val value: KeyValue,
    val easing: EasingSpec = EasingSpec.Linear,
    val rotation: RotationDirection = RotationDirection.Shortest,
)

/** keyframe 값. 타입이 속성의 [AnimatableProperty.valueType]과 같아야 한다. */
public sealed interface KeyValue {
    public data class Scalar(val value: Double) : KeyValue

    public data class Color(val color: ArgbColor) : KeyValue
}

public enum class KeyValueType { Scalar, Color }

/**
 * 애니메이션할 수 있는 노드 속성. [wireName]은 JSON에 저장할 때 쓰는 이름이다.
 *
 * 회전은 기본적으로 가장 짧은 방향으로 보간하고, keyframe의 [Keyframe.rotation]으로 방향을 바꿀 수 있다. 색은 sRGB 채널 값을 그대로 선형 보간한다.
 */
public enum class AnimatableProperty(public val wireName: String, public val valueType: KeyValueType) {
    TranslationX("translationX", KeyValueType.Scalar),
    TranslationY("translationY", KeyValueType.Scalar),
    ScaleX("scaleX", KeyValueType.Scalar),
    ScaleY("scaleY", KeyValueType.Scalar),
    RotationDegrees("rotationDegrees", KeyValueType.Scalar),
    Opacity("opacity", KeyValueType.Scalar),
    Fill("fill", KeyValueType.Color),
    StrokeWidth("strokeWidth", KeyValueType.Scalar),
    Width("width", KeyValueType.Scalar),
    Height("height", KeyValueType.Scalar),
    CornerRadius("cornerRadius", KeyValueType.Scalar),
    Stroke("stroke", KeyValueType.Color),
    Blur("blur", KeyValueType.Scalar),
    ShadowBlur("shadowBlur", KeyValueType.Scalar),
    ShadowOffsetY("shadowY", KeyValueType.Scalar),

    /** 그라디언트의 첫 색. 그라디언트가 없는 노드에는 적용되지 않는다. */
    GradientStart("gradientStart", KeyValueType.Color),

    /** 그라디언트의 마지막 색. */
    GradientEnd("gradientEnd", KeyValueType.Color),

    /** 선형 그라디언트의 각도(도). */
    GradientAngle("gradientAngle", KeyValueType.Scalar),

    /** 첫 그림자의 색. */
    ShadowColor("shadowColor", KeyValueType.Color),

    /** 첫 그림자의 가로 오프셋(문서 단위). */
    ShadowOffsetX("shadowX", KeyValueType.Scalar),

    /** 첫 그림자의 spread(문서 단위). 음수면 그림자가 줄어든다. */
    ShadowSpread("shadowSpread", KeyValueType.Scalar),

    /** 왼쪽 위 모서리 반지름. 문서 단위이며 0 이상이다. 다른 모서리는 바꾸지 않는다. */
    CornerTopLeft("cornerTopLeft", KeyValueType.Scalar),

    /** 오른쪽 위 모서리 반지름. */
    CornerTopRight("cornerTopRight", KeyValueType.Scalar),

    /** 오른쪽 아래 모서리 반지름. */
    CornerBottomRight("cornerBottomRight", KeyValueType.Scalar),

    /** 왼쪽 아래 모서리 반지름. */
    CornerBottomLeft("cornerBottomLeft", KeyValueType.Scalar),
    ;

    public companion object {
        public fun fromWireName(name: String): AnimatableProperty? = entries.firstOrNull { it.wireName == name }
    }
}

/** 재생 중 이 시점을 지나면 알림을 받는 마커. */
public data class TimelineMarker(val name: String, val timeMs: Long)
