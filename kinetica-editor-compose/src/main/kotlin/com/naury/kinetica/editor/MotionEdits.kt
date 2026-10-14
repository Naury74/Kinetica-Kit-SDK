package com.naury.kinetica.editor

import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.scene.AppearEffect
import com.naury.kinetica.core.scene.AppearKind
import com.naury.kinetica.core.scene.LoopEffect
import com.naury.kinetica.core.scene.LoopKind
import com.naury.kinetica.core.scene.NodeEffects
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.PressEffect
import com.naury.kinetica.core.scene.PressKind

/** 움직임 효과(누름·나타나기·반복) 입력을 명령으로 바꾼다. 입력 하나가 실행 취소 한 단계이며, 받을 수 없는 값이면 `false`다. */
internal class MotionEdits(private val state: EditorState) {
    /** 누름 효과 [kind]를 켜거나 끈다. */
    fun togglePress(id: NodeId, kind: PressKind): Boolean = edit(id) { effects ->
        val on = effects.press.any { it.kind == kind }
        effects.copy(press = if (on) effects.press.filterNot { it.kind == kind } else effects.press + PressEffect(kind))
    }

    /** 나타나기 효과. `null`이면 끈다. 시간·지연은 지금 값을 유지한다. */
    fun setAppear(id: NodeId, kind: AppearKind?): Boolean = edit(id) { effects ->
        effects.copy(appear = kind?.let { effects.appear?.copy(kind = it) ?: AppearEffect(it) })
    }

    /** 나타나기 시간·지연(ms, 0 이상). 나타나기가 없으면 `false`. */
    fun setAppearTiming(id: NodeId, durationText: String? = null, delayText: String? = null): Boolean {
        val appear = state.document.node(id)?.effects?.appear ?: return false
        val duration = durationText?.let { parseMs(it) ?: return false } ?: appear.durationMs
        val delay = delayText?.let { parseMs(it) ?: return false } ?: appear.delayMs
        return edit(id) { it.copy(appear = appear.copy(durationMs = duration, delayMs = delay)) }
    }

    /** 반복 효과. `null`이면 끈다. */
    fun setLoop(id: NodeId, kind: LoopKind?): Boolean = edit(id) { effects ->
        effects.copy(loop = kind?.let { LoopEffect(it, periodMs = effects.loop?.periodMs ?: LoopEffect.DEFAULT_PERIOD_MS) })
    }

    /** 반복 한 번의 시간(ms). */
    fun setLoopPeriod(id: NodeId, text: String): Boolean {
        val loop = state.document.node(id)?.effects?.loop ?: return false
        val period = parseMs(text) ?: return false
        return edit(id) { it.copy(loop = loop.copy(periodMs = period)) }
    }

    private fun parseMs(text: String): Long? = text.trim().toLongOrNull()?.takeIf { it >= 0 }

    private fun edit(id: NodeId, change: (NodeEffects) -> NodeEffects): Boolean {
        val node = state.document.node(id) ?: return false
        val next = change(node.effects)
        if (next == node.effects) return true
        return state.execute(SceneCommand.UpdateProperties(node.copy(effects = next)))
    }
}
