package com.naury.kinetica.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.RotationDirection
import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.scene.NodeId

/**
 * 타임라인에서 고른 키 여러 개를 한꺼번에 바꾼다. 동작 하나가 실행 취소 한 단계이고, 명령이 하나라도 거부되면 모두 되돌린다.
 * 복사한 키는 문서가 아니라 이 객체에 둔다.
 */
internal class KeyframeEdits(private val state: EditorState) {
    /** 복사해 둔 키. 비어 있으면 붙여넣을 것이 없다. */
    var clipboard by mutableStateOf<List<CopiedKey>>(emptyList())
        private set

    fun keyframe(key: SelectedKey): Keyframe? =
        state.timeline?.track(key.nodeId, key.property)?.keyframes?.firstOrNull { it.timeMs == key.timeMs }

    /** [keys]를 [deltaMs]만큼 옮겼을 때 0 미만이나 timeline 길이를 넘지 않도록 줄인 이동량. */
    fun clampDelta(keys: Set<SelectedKey>, deltaMs: Long): Long {
        if (keys.isEmpty()) return 0L
        val duration = state.timeline?.durationMs ?: return 0L
        return deltaMs.coerceIn(-keys.minOf { it.timeMs }, duration - keys.maxOf { it.timeMs })
    }

    /**
     * [keys]를 모두 같은 양만큼 옮긴다. 범위를 넘는 만큼은 [clampDelta]로 줄인다.
     * 옮긴 자리에 고르지 않은 키가 있으면 `MoveKeyframe` 규칙대로 거부하고 아무것도 바꾸지 않는다.
     *
     * @return 옮긴 뒤의 키. 거부되면 `null`, 움직이지 않았으면 [keys] 그대로.
     */
    fun move(keys: Set<SelectedKey>, deltaMs: Long): Set<SelectedKey>? {
        val delta = clampDelta(keys, deltaMs)
        if (delta == 0L) return keys
        // 같은 track 안에서 고른 키끼리 부딪히지 않도록, 가는 방향의 앞쪽 키부터 옮긴다.
        val ordered = if (delta > 0) keys.sortedByDescending { it.timeMs } else keys.sortedBy { it.timeMs }
        val ok = state.transaction("move-keys") {
            ordered.all { key ->
                execute(SceneCommand.MoveKeyframe(EditorState.MAIN_TIMELINE, key.nodeId, key.property, key.timeMs, key.timeMs + delta))
            }
        }
        return if (ok) keys.mapTo(LinkedHashSet()) { it.copy(timeMs = it.timeMs + delta) } else null
    }

    fun delete(keys: Set<SelectedKey>): Boolean {
        if (keys.isEmpty()) return false
        return state.transaction("delete-keys") {
            keys.groupBy { it.nodeId to it.property }.all { (track, picked) ->
                execute(
                    SceneCommand.DeleteKeyframes(
                        EditorState.MAIN_TIMELINE,
                        track.first,
                        track.second,
                        picked.mapTo(HashSet()) {
                            it.timeMs
                        },
                    ),
                )
            }
        }
    }

    /** [keys]를 복사한다. 시각은 가장 이른 키를 0으로 한 상대 시각으로 저장한다. */
    fun copy(keys: Set<SelectedKey>) {
        val first = keys.minOfOrNull { it.timeMs } ?: return
        clipboard = keys.mapNotNull { key -> keyframe(key)?.let { CopiedKey(key.nodeId, key.property, key.timeMs - first, it) } }
            .sortedBy { it.offsetMs }
    }

    /**
     * 복사한 키를 [atMs]부터 붙여넣는다. [target]이 있으면 그 노드에, 없으면 복사한 노드에 넣는다.
     * 같은 시각의 키는 바꾸고, timeline 길이를 넘는 키는 버린다.
     *
     * @return 붙여넣은 키. 넣은 것이 없거나 거부되면 빈 집합.
     */
    fun paste(atMs: Long, target: NodeId?): Set<SelectedKey> {
        val duration = state.timeline?.durationMs ?: return emptySet()
        val placed = clipboard.mapNotNull { copied ->
            val time = atMs + copied.offsetMs
            if (time !in 0..duration) return@mapNotNull null
            SelectedKey(target ?: copied.nodeId, copied.property, time) to copied.keyframe.copy(timeMs = time)
        }
        if (placed.isEmpty()) return emptySet()
        val ok = state.transaction("paste-keys") {
            placed.all { (key, keyframe) ->
                execute(SceneCommand.SetKeyframe(EditorState.MAIN_TIMELINE, key.nodeId, key.property, keyframe))
            }
        }
        return if (ok) placed.mapTo(LinkedHashSet()) { it.first } else emptySet()
    }

    fun setEasing(keys: Set<SelectedKey>, easing: EasingSpec): Boolean {
        if (keys.isEmpty()) return false
        return state.transaction("key-easing") {
            keys.all { key ->
                execute(SceneCommand.SetKeyframeEasing(EditorState.MAIN_TIMELINE, key.nodeId, key.property, key.timeMs, easing))
            }
        }
    }

    /** 고른 키 가운데 회전 키에만 [direction]을 정한다. 회전 키가 없으면 `false`. */
    fun setRotation(keys: Set<SelectedKey>, direction: RotationDirection): Boolean {
        val rotations = keys.filter {
            it.property == AnimatableProperty.RotationDegrees
        }.mapNotNull { key -> keyframe(key)?.let { key to it } }
        if (rotations.isEmpty()) return false
        val changed = rotations.filter { (_, keyframe) -> keyframe.rotation != direction }
        return state.transaction("key-rotation") {
            changed.all { (key, keyframe) ->
                execute(SceneCommand.SetKeyframe(EditorState.MAIN_TIMELINE, key.nodeId, key.property, keyframe.copy(rotation = direction)))
            }
        }
    }

    private fun execute(command: SceneCommand): Boolean = state.session.commands.execute(command) is CommandResult.Applied
}

/** 복사한 키 하나. [offsetMs]는 복사한 키 가운데 가장 이른 키부터 잰 시각이다. */
internal data class CopiedKey(val nodeId: NodeId, val property: AnimatableProperty, val offsetMs: Long, val keyframe: Keyframe)
