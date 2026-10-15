// 프리셋의 세기와 시간은 디자인 값이라 이름 붙인 상수로 빼지 않는다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.presets

import com.naury.kinetica.core.scene.AppearEffect
import com.naury.kinetica.core.scene.AppearKind
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.LoopEffect
import com.naury.kinetica.core.scene.LoopKind
import com.naury.kinetica.core.scene.NodeEffects
import com.naury.kinetica.core.scene.PressEffect
import com.naury.kinetica.core.scene.PressKind

/**
 * 자주 쓰는 모션 효과 프리셋. 그대로 쓰거나 `copy`로 일부만 바꿔 쓴다.
 * Compose에서는 `Modifier.kineticaEffects(EffectPresets.button)`, XML View에서는 `ViewEffects.apply(view, EffectPresets.button)`로 적용하고,
 * 장면 노드는 `SceneNode.effects`에, 보드 위젯은 `BoardWidget(effects = …)`에 넣는다.
 */
public object EffectPresets {
    /** 버튼. 리플과 함께 살짝 작아진다. */
    @JvmField
    public val button: NodeEffects = NodeEffects(press = listOf(PressEffect(PressKind.Ripple), PressEffect(PressKind.Scale, amount = 0.96)))

    /** 카드. 누르면 기울어지고, 아래에서 올라오며 등장한다. */
    @JvmField
    public val card: NodeEffects = NodeEffects(
        press = listOf(PressEffect(PressKind.Tilt, amount = 6.0), PressEffect(PressKind.Scale, amount = 0.98)),
        appear = AppearEffect(AppearKind.SlideUp, durationMs = 450),
    )

    /**
     * 목록 항목. 누르면 어두워지고, 아래에서 올라오며 등장한다. [index]에 60ms씩 비례해 늦게 시작하므로 항목이 차례로 나타난다.
     *
     * 지연은 10번째 항목(600ms)에서 멈춘다. `LazyColumn`은 스크롤해 새로 들어온 항목도 처음부터 등장 애니메이션을 재생하므로,
     * 목록 전체 위치 대신 처음 화면에 보이는 순서를 넘기거나 스크롤로 들어온 항목에는 `listItem(0)`을 쓴다.
     */
    @JvmStatic
    public fun listItem(index: Int): NodeEffects = NodeEffects(
        press = listOf(PressEffect(PressKind.Dim)),
        appear = AppearEffect(AppearKind.SlideUp, durationMs = 350, delayMs = 60L * index.coerceIn(0, 10)),
    )

    /** 새 알림 배지. 튀어나오듯 등장한 뒤 맥박처럼 커졌다 작아진다. */
    @JvmField
    public val badge: NodeEffects =
        NodeEffects(appear = AppearEffect(AppearKind.Pop), loop = LoopEffect(LoopKind.Pulse, periodMs = 1_200, amount = 1.12))

    /** 로딩 중 스켈레톤. 빛 띠가 지나간다. */
    @JvmField
    public val skeleton: NodeEffects = NodeEffects(loop = LoopEffect(LoopKind.Shimmer, periodMs = 1_400))

    /** 구매나 시작 같은 강조 버튼. 리플, 축소, 글로우를 함께 쓴다. */
    @JvmField
    public val callToAction: NodeEffects = button.copy(loop = LoopEffect(LoopKind.Glow, periodMs = 2_000, amount = 18.0))

    /** 오류나 주의 표시. 이따금 흔들린다. */
    @JvmField
    public val attention: NodeEffects = NodeEffects(loop = LoopEffect(LoopKind.Shake, periodMs = 2_400, amount = 8.0))

    /** 떠 있는 아이콘이나 일러스트. 위아래로 둥실 떠다닌다. */
    @JvmField
    public val floating: NodeEffects =
        NodeEffects(appear = AppearEffect(AppearKind.Zoom), loop = LoopEffect(LoopKind.Float, periodMs = 3_000, amount = 10.0))

    /** 좋아요나 완료 버튼. 누르면 작아졌다가 손을 뗄 때 조각이 터진다. */
    @JvmField
    public val like: NodeEffects = NodeEffects(
        press = listOf(PressEffect(PressKind.Scale, amount = 0.85), PressEffect(PressKind.Burst, color = ArgbColor(0xFFFF4D6D.toInt()))),
    )

    /** 떠오르는 카드. 누르면 그림자가 커지며 떠오르고, 블러가 걷히며 등장한다. */
    @JvmField
    public val liftCard: NodeEffects = NodeEffects(
        press = listOf(PressEffect(PressKind.Lift)),
        appear = AppearEffect(AppearKind.Blur, durationMs = 500),
    )

    /** 실시간 상태나 연결됨을 나타내는 점. 고리가 퍼져 나간다. */
    @JvmField
    public val live: NodeEffects =
        NodeEffects(loop = LoopEffect(LoopKind.Ping, periodMs = 1_800, amount = 10.0, color = ArgbColor(0xFF22C55E.toInt())))

    /** 새 소식을 알리는 하트나 아이콘. 콩닥콩닥 뛴다. */
    @JvmField
    public val heartbeat: NodeEffects = NodeEffects(loop = LoopEffect(LoopKind.Heartbeat, periodMs = 1_400))

    /** 이름으로 프리셋을 고를 때 쓰는 목록. */
    @JvmField
    public val all: Map<String, NodeEffects> = mapOf(
        "button" to button,
        "card" to card,
        "listItem" to listItem(0),
        "badge" to badge,
        "skeleton" to skeleton,
        "callToAction" to callToAction,
        "attention" to attention,
        "floating" to floating,
        "like" to like,
        "liftCard" to liftCard,
        "live" to live,
        "heartbeat" to heartbeat,
    )
}
