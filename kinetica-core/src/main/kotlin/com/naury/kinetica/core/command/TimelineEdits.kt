package com.naury.kinetica.core.command

import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.PropertyTrack
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument

internal object TimelineEdits {
    fun put(document: SceneDocument, command: SceneCommand.PutTimeline): CommandResult {
        val timeline = command.timeline
        val index = document.timelines.indexOfFirst { it.id == timeline.id }
        val timelines = if (index < 0) document.timelines + timeline else document.timelines.toMutableList().apply { set(index, timeline) }
        return applied(document.copy(timelines = timelines))
    }

    fun remove(document: SceneDocument, command: SceneCommand.RemoveTimeline): CommandResult {
        if (document.timelines.none { it.id == command.timelineId }) return rejected(CommandError.TimelineNotFound(command.timelineId))
        return applied(document.copy(timelines = document.timelines.filterNot { it.id == command.timelineId }))
    }

    fun setKeyframe(document: SceneDocument, command: SceneCommand.SetKeyframe): CommandResult =
        editTrack(document, TrackRef(command.timelineId, command.nodeId, command.property), createIfMissing = true) { keys ->
            val key = command.keyframe
            (keys.filterNot { it.timeMs == key.timeMs } + key).sortedBy { it.timeMs }
        }

    fun moveKeyframe(document: SceneDocument, command: SceneCommand.MoveKeyframe): CommandResult =
        editTrack(document, TrackRef(command.timelineId, command.nodeId, command.property)) { keys ->
            val moving = keys.firstOrNull { it.timeMs == command.fromTimeMs }
                ?: return rejected(CommandError.KeyframeNotFound(command.nodeId, command.property, command.fromTimeMs))
            if (command.toTimeMs != command.fromTimeMs && keys.any { it.timeMs == command.toTimeMs }) {
                return rejected(CommandError.KeyframeCollision(command.nodeId, command.property, command.toTimeMs))
            }
            (keys - moving + moving.copy(timeMs = command.toTimeMs)).sortedBy { it.timeMs }
        }

    fun deleteKeyframes(document: SceneDocument, command: SceneCommand.DeleteKeyframes): CommandResult =
        editTrack(document, TrackRef(command.timelineId, command.nodeId, command.property)) { keys ->
            command.timesMs.firstOrNull { time -> keys.none { it.timeMs == time } }
                ?.let { return rejected(CommandError.KeyframeNotFound(command.nodeId, command.property, it)) }
            keys.filterNot { it.timeMs in command.timesMs }
        }

    fun setEasing(document: SceneDocument, command: SceneCommand.SetKeyframeEasing): CommandResult =
        editTrack(document, TrackRef(command.timelineId, command.nodeId, command.property)) { keys ->
            if (keys.none { it.timeMs == command.timeMs }) {
                return rejected(CommandError.KeyframeNotFound(command.nodeId, command.property, command.timeMs))
            }
            keys.map { if (it.timeMs == command.timeMs) it.copy(easing = command.easing) else it }
        }

    /** track의 keyframe 목록을 바꾼다. 결과가 비면 track을 지운다. */
    private inline fun editTrack(
        document: SceneDocument,
        ref: TrackRef,
        createIfMissing: Boolean = false,
        edit: (List<Keyframe>) -> List<Keyframe>,
    ): CommandResult {
        val (timelineId, nodeId, property) = ref
        val timeline = document.timelines.firstOrNull { it.id == timelineId } ?: return rejected(CommandError.TimelineNotFound(timelineId))
        val existing = timeline.track(nodeId, property)
        if (existing == null && !createIfMissing) {
            return rejected(CommandError.TrackNotFound(nodeId, property))
        }
        val keyframes = edit(existing?.keyframes.orEmpty())
        val others = timeline.tracks.filterNot { it.nodeId == nodeId && it.property == property }
        val tracks = when {
            keyframes.isEmpty() -> others
            existing == null -> others + PropertyTrack(nodeId, property, keyframes)
            else -> timeline.tracks.map { if (it === existing) existing.copy(keyframes = keyframes) else it }
        }
        return applied(document.replaceTimeline(timeline.copy(tracks = tracks)))
    }

    private data class TrackRef(val timelineId: String, val nodeId: NodeId, val property: AnimatableProperty)

    private fun SceneDocument.replaceTimeline(timeline: Timeline): SceneDocument =
        copy(timelines = timelines.map { if (it.id == timeline.id) timeline else it })
}
