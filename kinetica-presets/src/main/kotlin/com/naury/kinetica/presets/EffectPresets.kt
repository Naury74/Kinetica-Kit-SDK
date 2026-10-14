// 효과 묶음의 세기·시간은 디자인 값이라 이름 붙인 상수로 빼지 않는다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.presets

import com.naury.kinetica.core.scene.AppearEffect
import com.naury.kinetica.core.scene.AppearKind
import com.naury.kinetica.core.scene.LoopEffect
import com.naury.kinetica.core.scene.LoopKind
import com.naury.kinetica.core.scene.NodeEffects
import com.naury.kinetica.core.scene.PressEffect
import com.naury.kinetica.core.scene.PressKind

/**
 * 자주 쓰는 움직임 효과 묶음. 그대로 쓰거나 `copy`로 고쳐 쓴다.
 * Compose는 `Modifier.kineticaEffects(EffectPresets.button)`, XML View는 `ViewEffects.apply(view, EffectPresets.button)`,
 * 장면 노드는 `SceneNode.effects`, 보드 위젯은 `BoardWidget(effects = …)`에 넣는다.
 */
public object EffectPresets {
    /** 버튼: 리플 + 살짝 눌림. */
    @JvmField
    public val button: NodeEffects = NodeEffects(press = listOf(PressEffect(PressKind.Ripple), PressEffect(PressKind.Scale, amount = 0.96)))

    /** 카드: 누르면 기울고, 아래에서 올라오며 나타남. */
    @JvmField
    public val card: NodeEffects = NodeEffects(
        press = listOf(PressEffect(PressKind.Tilt, amount = 6.0), PressEffect(PressKind.Scale, amount = 0.98)),
        appear = AppearEffect(AppearKind.SlideUp, durationMs = 450),
    )

    /** 목록 항목: 어둡게 + 페이드로 나타남. [index]만큼 늦게 나타나 차례로 보인다. */
    @JvmStatic
    public fun listItem(index: Int): NodeEffects = NodeEffects(
        press = listOf(PressEffect(PressKind.Dim)),
        appear = AppearEffect(AppearKind.SlideUp, durationMs = 350, delayMs = 60L * index.coerceIn(0, 30)),
    )

    /** 새 알림 배지: 두근두근. */
    @JvmField
    public val badge: NodeEffects =
        NodeEffects(appear = AppearEffect(AppearKind.Pop), loop = LoopEffect(LoopKind.Pulse, periodMs = 1_200, amount = 1.12))

    /** 불러오는 중 자리(스켈레톤): 빛 띠가 지나감. */
    @JvmField
    public val skeleton: NodeEffects = NodeEffects(loop = LoopEffect(LoopKind.Shimmer, periodMs = 1_400))

    /** 강조 버튼(구매·시작): 리플 + 눌림 + 빛 번짐. */
    @JvmField
    public val callToAction: NodeEffects = button.copy(loop = LoopEffect(LoopKind.Glow, periodMs = 2_000, amount = 18.0))

    /** 오류·주의: 이따금 흔들림. */
    @JvmField
    public val attention: NodeEffects = NodeEffects(loop = LoopEffect(LoopKind.Shake, periodMs = 2_400, amount = 8.0))

    /** 떠 있는 아이콘·일러스트: 둥실. */
    @JvmField
    public val floating: NodeEffects =
        NodeEffects(appear = AppearEffect(AppearKind.Zoom), loop = LoopEffect(LoopKind.Float, periodMs = 3_000, amount = 10.0))

    /** 이름으로 고를 때 쓰는 목록. */
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
    )
}
