// 효과 종류마다의 기본 세기·범위는 디자인 값이라 이름 붙인 상수로 빼지 않는다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.core.scene

/**
 * 노드에 붙이는 미리 만든 움직임 효과. 장면의 모든 노드(호스트 뷰 자리 포함)에 쓰며, 재생기·미리보기에서만 움직이고
 * 편집 캔버스에서는 움직이지 않는다. 시스템 "애니메이션 줄이기"가 켜져 있으면 [appear]·[loop]은 쓰지 않고 [press]는 그대로 쓴다.
 *
 * @property press 누르는 동안의 반응. 같은 종류는 하나만 둘 수 있고 여러 종류를 겹쳐 쓴다(예: 리플 + 눌림).
 *   누름을 가로채지 않으므로 호스트 뷰의 클릭·스크롤은 그대로 동작한다.
 * @property appear 처음 화면에 나타날 때 한 번
 * @property loop 계속 반복
 */
public data class NodeEffects(val press: List<PressEffect> = emptyList(), val appear: AppearEffect? = null, val loop: LoopEffect? = null) {
    public val isEmpty: Boolean get() = press.isEmpty() && appear == null && loop == null

    public companion object {
        public val None: NodeEffects = NodeEffects()

        /** 효과 하나의 시간 상한(ms). */
        public const val MAX_DURATION_MS: Long = 60_000L
    }
}

/**
 * 누르는 동안의 반응.
 *
 * @property color [PressKind.Ripple]·[PressKind.Dim]의 색(`null`이면 반투명 검정), [PressKind.Lift]의 그림자 색(`null`이면 검정),
 *   [PressKind.Burst]의 조각 색(`null`이면 파랑 `#5B6CFF`).
 * @property amount 종류별 세기. `null`이면 기본값([PressKind.defaultAmount]).
 *   [PressKind.Scale]은 눌렸을 때 배율(0.5..1.5), [PressKind.Tilt]는 최대 기울기(도, 0..45), [PressKind.Dim]은 덮는 불투명도(0..1),
 *   [PressKind.Ripple]은 퍼지는 원의 불투명도(0..1), [PressKind.Lift]는 떠오르는 높이(문서 단위, 0..50),
 *   [PressKind.Burst]는 터지는 조각 수(3..24)다.
 */
public data class PressEffect(val kind: PressKind, val color: ArgbColor? = null, val amount: Double? = null) {
    public val resolvedAmount: Double get() = amount ?: kind.defaultAmount
}

public enum class PressKind(
    public val wireName: String,
    public val defaultAmount: Double,
    internal val range: ClosedFloatingPointRange<Double>,
) {
    /** 누른 자리에서 원이 퍼진다. */
    Ripple("ripple", 0.24, 0.0..1.0),

    /** 살짝 작아졌다가 놓으면 튕기며 돌아온다. */
    Scale("scale", 0.95, 0.5..1.5),

    /** 누른 쪽으로 3D로 기운다. */
    Tilt("tilt", 8.0, 0.0..45.0),

    /** 어둡게 덮는다. */
    Dim("dim", 0.12, 0.0..1.0),

    /** 그림자가 커지며 위로 떠오른다(카드). */
    Lift("lift", 6.0, 0.0..50.0),

    /** 손을 뗄 때 조각이 사방으로 터진다(좋아요·완료). */
    Burst("burst", 10.0, 3.0..24.0),
    ;

    public companion object {
        public fun fromWireName(name: String): PressKind? = entries.firstOrNull { it.wireName == name }
    }
}

/**
 * 처음 나타날 때의 효과.
 *
 * @property durationMs 걸리는 시간(ms, 0..[NodeEffects.MAX_DURATION_MS])
 * @property delayMs 시작 전 기다리는 시간(ms). 여러 노드를 차례로 나타낼 때 쓴다.
 */
public data class AppearEffect(val kind: AppearKind, val durationMs: Long = DEFAULT_DURATION_MS, val delayMs: Long = 0L) {
    public companion object {
        public const val DEFAULT_DURATION_MS: Long = 400L
    }
}

public enum class AppearKind(public val wireName: String) {
    Fade("fade"),
    SlideUp("slideUp"),
    SlideDown("slideDown"),
    SlideLeft("slideLeft"),
    SlideRight("slideRight"),

    /** 작게 시작해 튕기며 커진다. */
    Pop("pop"),

    /** 크게 시작해 줄어들며 나타난다. */
    Zoom("zoom"),

    /** 위쪽 축으로 뒤집히며 나타난다. */
    Flip("flip"),

    /** 흐릿하다가 또렷해지며 나타난다. Android 12(API 31) 미만에서는 흐림 없이 서서히 나타난다. */
    Blur("blur"),
    ;

    public companion object {
        public fun fromWireName(name: String): AppearKind? = entries.firstOrNull { it.wireName == name }
    }
}

/**
 * 계속 반복하는 효과.
 *
 * @property periodMs 한 번 도는 시간(ms, 100..[NodeEffects.MAX_DURATION_MS])
 * @property amount 종류별 세기. `null`이면 기본값([LoopKind.defaultAmount]). [LoopKind.Pulse]는 최대 배율(1..2),
 *   [LoopKind.Breathe]는 가장 흐릴 때 불투명도(0..1), [LoopKind.Float]·[LoopKind.Bounce]는 움직이는 거리(문서 단위, 0..200),
 *   [LoopKind.Shake]는 흔드는 각도(도, 0..45), [LoopKind.Shimmer]는 빛 띠 불투명도(0..1), [LoopKind.Glow]는 빛 번짐 반지름(문서 단위, 0..200),
 *   [LoopKind.Heartbeat]는 최대 배율(1..2), [LoopKind.Wiggle]은 흔드는 각도(도, 0..45), [LoopKind.Ping]은 고리가 퍼지는 거리(문서 단위, 0..200),
 *   [LoopKind.Spin]은 쓰지 않는다.
 * @property color [LoopKind.Shimmer]·[LoopKind.Glow]의 색(`null`이면 흰색), [LoopKind.Ping] 고리 색(`null`이면 파랑 `#5B6CFF`).
 */
public data class LoopEffect(
    val kind: LoopKind,
    val periodMs: Long = DEFAULT_PERIOD_MS,
    val amount: Double? = null,
    val color: ArgbColor? = null,
) {
    public val resolvedAmount: Double get() = amount ?: kind.defaultAmount

    public companion object {
        public const val DEFAULT_PERIOD_MS: Long = 1_600L
        public const val MIN_PERIOD_MS: Long = 100L
    }
}

public enum class LoopKind(
    public val wireName: String,
    public val defaultAmount: Double,
    internal val range: ClosedFloatingPointRange<Double>,
) {
    /** 커졌다 작아졌다 한다. */
    Pulse("pulse", 1.06, 1.0..2.0),

    /** 흐려졌다 또렷해졌다 한다. */
    Breathe("breathe", 0.6, 0.0..1.0),

    /** 위아래로 둥실 떠 있다. */
    Float("float", 12.0, 0.0..200.0),

    /** 좌우로 흔들린다(알림·오류 강조). 한 주기 앞부분에서만 흔들고 나머지는 쉰다. */
    Shake("shake", 6.0, 0.0..45.0),

    /** 빛 띠가 지나간다(불러오는 중·강조). */
    Shimmer("shimmer", 0.45, 0.0..1.0),

    /** 주변으로 빛이 번졌다 줄었다 한다. */
    Glow("glow", 24.0, 0.0..200.0),

    /** 계속 돈다. */
    Spin("spin", 0.0, 0.0..0.0),

    /** 통통 튄다. */
    Bounce("bounce", 16.0, 0.0..200.0),

    /** 두 번 콩닥 뛰고 쉰다(좋아요·알림). */
    Heartbeat("heartbeat", 1.12, 1.0..2.0),

    /** 좌우로 계속 갸웃거린다. */
    Wiggle("wiggle", 3.0, 0.0..45.0),

    /** 고리가 바깥으로 퍼지며 사라진다(실시간·연결됨 표시). */
    Ping("ping", 16.0, 0.0..200.0),
    ;

    public companion object {
        public fun fromWireName(name: String): LoopKind? = entries.firstOrNull { it.wireName == name }
    }
}

internal fun checkEffects(effects: NodeEffects, id: NodeId, sink: ViolationSink) {
    if (effects.isEmpty) return
    val appearOk = effects.appear?.let { it.durationMs in 0..NodeEffects.MAX_DURATION_MS && it.delayMs in 0..NodeEffects.MAX_DURATION_MS }
    val loopOk = effects.loop?.let {
        it.periodMs in LoopEffect.MIN_PERIOD_MS..NodeEffects.MAX_DURATION_MS &&
            inRange(it.amount, it.kind.range)
    }
    if (!pressOk(effects.press) || appearOk == false || loopOk == false) {
        sink.add(ViolationCode.InvalidStyle, "움직임 효과 값이 범위를 벗어났습니다.", id)
    }
}

private fun pressOk(press: List<PressEffect>): Boolean =
    press.map { it.kind }.toSet().size == press.size && press.all { inRange(it.amount, it.kind.range) }

private fun inRange(amount: Double?, range: ClosedFloatingPointRange<Double>): Boolean =
    amount == null || (amount.isFinite() && amount in range)
