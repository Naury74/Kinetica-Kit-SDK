package com.naury.kinetica.editor

import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.scene.SceneTransitionEdge
import com.naury.kinetica.core.scene.SceneTransitionKind
import com.naury.kinetica.core.scene.SceneTransitionSpec

/** 이 장면이 들어올 때의 전환 입력을 명령으로 바꾼다. 입력 하나가 실행 취소 한 단계이고, 받을 수 없는 값이면 `false`를 돌려준다. */
internal class TransitionEdits(private val state: EditorState) {
    /** 전환 종류를 정한다. `null`이면 전환을 지운다. 방향과 시간은 지금 값을 유지한다. */
    fun setKind(kind: SceneTransitionKind?): Boolean =
        set(kind?.let { state.document.transition?.copy(kind = it) ?: SceneTransitionSpec(it) })

    /** 들어오는 장면이 나오는 가장자리. 전환이 없으면 `false`를 돌려준다. */
    fun setFrom(edge: SceneTransitionEdge): Boolean = state.document.transition?.let { set(it.copy(from = edge)) } ?: false

    /** 전환 시간(ms). 0..10000 정수가 아니거나 전환이 없으면 `false`를 돌려준다. */
    fun setDuration(text: String): Boolean {
        val current = state.document.transition ?: return false
        val ms =
            text.trim().toIntOrNull()?.takeIf { it in SceneTransitionSpec.MIN_DURATION_MS..SceneTransitionSpec.MAX_DURATION_MS }
                ?: return false
        return set(current.copy(durationMs = ms))
    }

    private fun set(next: SceneTransitionSpec?): Boolean =
        next == state.document.transition || state.execute(SceneCommand.SetTransition(next))
}

/** 방향을 고를 수 있는 전환이면 `true`다. */
internal val SceneTransitionKind.hasDirection: Boolean
    get() = when (this) {
        SceneTransitionKind.Slide, SceneTransitionKind.Push, SceneTransitionKind.Cover, SceneTransitionKind.Uncover,
        SceneTransitionKind.Wipe, SceneTransitionKind.Rotate, SceneTransitionKind.Flip, SceneTransitionKind.Cube,
        -> true

        else -> false
    }
