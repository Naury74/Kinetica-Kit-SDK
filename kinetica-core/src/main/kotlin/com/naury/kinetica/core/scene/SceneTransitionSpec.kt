package com.naury.kinetica.core.scene

import com.naury.kinetica.core.animation.EasingSpec

/** 장면이 바뀔 때의 전환 종류. [jsonName]은 장면 JSON의 `transition.kind` 값이다. */
public enum class SceneTransitionKind(public val jsonName: String) {
    /** 애니메이션 없이 바로 바꾼다. */
    None("none"),

    /** 두 장면에서 `morphKey`나 id가 같은 노드의 위치, 크기, 색을 이어 준다. 짝이 없는 노드는 페이드한다. */
    Morph("morph"),

    /** 나가는 장면과 들어오는 장면을 겹쳐 서서히 바꾼다. */
    Fade("fade"),

    /** 들어오는 장면이 노이즈 모양으로 조금씩 드러난다. */
    Dissolve("dissolve"),

    /** 두 장면이 함께 한 방향으로 밀려 바뀐다. */
    Slide("slide"),

    /** 들어오는 장면이 밀고 들어오고, 나가는 장면은 조금만 물러나며 어두워진다. */
    Push("push"),

    /** 들어오는 장면이 나가는 장면 위를 덮는다. 나가는 장면은 움직이지 않는다. */
    Cover("cover"),

    /** 나가는 장면이 밀려 나가며 아래에 있던 들어오는 장면을 드러낸다. */
    Uncover("uncover"),

    /** 경계선이 가장자리에서 반대쪽으로 쓸고 지나가며 들어오는 장면을 드러낸다. */
    Wipe("wipe"),

    /** 원이 [SceneTransitionSpec.originX], [SceneTransitionSpec.originY]에서 퍼지며 들어오는 장면을 드러낸다. */
    Iris("iris"),

    /** 들어오는 장면이 조금 작은 크기에서 커지며 나타나고, 나가는 장면은 커지며 사라진다. */
    Zoom("zoom"),

    /** [Zoom]에 블러를 더한다. 블러를 지원하지 않는 Android 12 미만에서는 [Zoom]과 같다. */
    ZoomBlur("zoomBlur"),

    /** 나가는 장면이 돌며 사라지고 들어오는 장면이 반대로 돌며 나타난다. */
    Rotate("rotate"),

    /** 카드를 뒤집듯 3D로 돌린다. 절반을 지나면 들어오는 장면이 보인다. */
    Flip("flip"),

    /** 정육면체의 옆면으로 넘어가듯 3D로 돌린다. */
    Cube("cube"),
    ;

    public companion object {
        /** [jsonName]이 [name]인 종류. 없으면 `null`이다. */
        @JvmStatic
        public fun fromJsonName(name: String): SceneTransitionKind? = entries.firstOrNull { it.jsonName == name }
    }
}

/**
 * 들어오는 장면이 나오는 가장자리. [Start]와 [End]는 레이아웃 방향을 따르므로 RTL에서는 좌우가 바뀐다.
 * [jsonName]은 장면 JSON의 `transition.from` 값이다.
 */
public enum class SceneTransitionEdge(public val jsonName: String) {
    /** 끝 쪽(LTR에서 오른쪽). 앞으로 가는 화면 이동의 기본 방향이다. */
    End("end"),
    Start("start"),
    Top("top"),
    Bottom("bottom"),
    ;

    /** 반대쪽 가장자리. 되돌아가는 전환에 쓴다. */
    public val opposite: SceneTransitionEdge
        get() = when (this) {
            End -> Start
            Start -> End
            Top -> Bottom
            Bottom -> Top
        }

    public companion object {
        /** [jsonName]이 [name]인 가장자리. 없으면 `null`이다. */
        @JvmStatic
        public fun fromJsonName(name: String): SceneTransitionEdge? = entries.firstOrNull { it.jsonName == name }
    }
}

/**
 * 장면이 바뀔 때 쓰는 전환. 문서의 [SceneDocument.transition]에 두면 그 장면이 들어올 때의 기본 전환이 된다.
 *
 * @property from 들어오는 장면이 나오는 가장자리. 방향이 없는 종류(Fade, Iris 등)는 무시한다.
 * @property durationMs 전환 시간(ms). [MIN_DURATION_MS]..[MAX_DURATION_MS]를 벗어나면 [IllegalArgumentException]을 던진다.
 * @property easing 진행도에 적용하는 곡선. [EasingSpec.Steps]와 [EasingSpec.Spring]도 쓸 수 있다.
 * @property originX Iris와 Zoom 계열의 중심. 장면 너비에 대한 비율이며 0..1을 벗어나면 [IllegalArgumentException]을 던진다.
 * @property originY Iris와 Zoom 계열의 중심. 장면 높이에 대한 비율이며 0..1을 벗어나면 [IllegalArgumentException]을 던진다.
 */
public data class SceneTransitionSpec(
    val kind: SceneTransitionKind,
    val from: SceneTransitionEdge = SceneTransitionEdge.End,
    val durationMs: Int = DEFAULT_DURATION_MS,
    val easing: EasingSpec = EasingSpec.EaseInOut,
    val originX: Double = CENTER,
    val originY: Double = CENTER,
) {
    init {
        require(durationMs in MIN_DURATION_MS..MAX_DURATION_MS) { "durationMs는 $MIN_DURATION_MS..$MAX_DURATION_MS 이어야 합니다: $durationMs" }
        require(originX in 0.0..1.0 && originY in 0.0..1.0) { "originX, originY는 0..1이어야 합니다: $originX, $originY" }
    }

    /**
     * 액션 파라미터로 값을 덮어쓴 전환. `go?scene=promo&transition=wipe&from=start&durationMs=300&originX=0.2`처럼 쓴다.
     * 알 수 없는 값이나 범위를 벗어난 값은 무시하고 지금 값을 쓴다.
     */
    public fun overriddenBy(action: SceneAction): SceneTransitionSpec {
        val kind = action[PARAM_KIND]?.let(SceneTransitionKind::fromJsonName) ?: kind
        val edge = action[PARAM_FROM]?.let(SceneTransitionEdge::fromJsonName) ?: from
        val duration = action[PARAM_DURATION]?.toIntOrNull()?.takeIf { it in MIN_DURATION_MS..MAX_DURATION_MS } ?: durationMs
        val x = action[PARAM_ORIGIN_X]?.toDoubleOrNull()?.takeIf { it in 0.0..1.0 } ?: originX
        val y = action[PARAM_ORIGIN_Y]?.toDoubleOrNull()?.takeIf { it in 0.0..1.0 } ?: originY
        return copy(kind = kind, from = edge, durationMs = duration, originX = x, originY = y)
    }

    public companion object {
        public const val DEFAULT_DURATION_MS: Int = 400
        public const val MIN_DURATION_MS: Int = 0
        public const val MAX_DURATION_MS: Int = 10_000

        /** 액션에서 전환 종류를 정하는 파라미터 이름 */
        public const val PARAM_KIND: String = "transition"
        public const val PARAM_FROM: String = "from"
        public const val PARAM_DURATION: String = "durationMs"
        public const val PARAM_ORIGIN_X: String = "originX"
        public const val PARAM_ORIGIN_Y: String = "originY"
        private const val CENTER = 0.5

        /**
         * 액션에 `transition` 파라미터가 있으면 그 전환을, 없으면 [fallback]을 돌려준다. [fallback]이 있으면 그 값을 바탕으로 덮어쓴다.
         * `transition` 값을 알 수 없으면 [fallback]을 그대로 돌려준다.
         */
        @JvmStatic
        public fun fromAction(action: SceneAction, fallback: SceneTransitionSpec? = null): SceneTransitionSpec? {
            val kind = action[PARAM_KIND]?.let(SceneTransitionKind::fromJsonName)
            val base = fallback ?: kind?.let { SceneTransitionSpec(it) } ?: return null
            return base.overriddenBy(action)
        }
    }
}
