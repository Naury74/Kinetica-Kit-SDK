package com.naury.kinetica.core.command

import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneNode

/**
 * 문서를 바꾸는 유일한 수단. [SceneReducer]가 적용하고 결과 문서 전체를 검증한다.
 * 명령은 값이므로 기록·재실행·테스트에 그대로 쓸 수 있다.
 */
public sealed interface SceneCommand {
    /**
     * 노드를 [SceneNode.parentId] 그룹(또는 최상위)의 [index] 위치에 넣는다. `null`이면 맨 위(목록 끝)다.
     * 그룹은 빈 상태로만 넣을 수 있고 자식은 이어서 하나씩 넣는다.
     */
    public data class AddNode(val node: SceneNode, val index: Int? = null) : SceneCommand

    /** 노드와 그 자손을 모두 지운다. 지운 노드를 가리키는 track도 함께 지운다. */
    public data class RemoveNodes(val ids: Set<NodeId>) : SceneCommand

    /** 노드의 속성을 [node]로 바꾼다. id·종류·부모·자식 목록은 바꿀 수 없다. */
    public data class UpdateProperties(val node: SceneNode) : SceneCommand

    /** 여러 노드의 local 변환을 한꺼번에 바꾼다. */
    public data class TransformNodes(val transforms: Map<NodeId, Transform2D>) : SceneCommand

    /** 같은 부모 안에서 노드의 순서를 [toIndex]로 옮긴다. 0이 맨 아래다. */
    public data class ReorderNode(val id: NodeId, val toIndex: Int) : SceneCommand

    public data class SetArtboard(val artboard: Artboard) : SceneCommand

    /** 같은 id의 timeline이 있으면 바꾸고 없으면 더한다. */
    public data class PutTimeline(val timeline: Timeline) : SceneCommand

    public data class RemoveTimeline(val timelineId: String) : SceneCommand

    /** keyframe을 넣는다. 같은 시간에 keyframe이 있으면 바꾼다. track이 없으면 만든다. */
    public data class SetKeyframe(val timelineId: String, val nodeId: NodeId, val property: AnimatableProperty, val keyframe: Keyframe) :
        SceneCommand

    /** [fromTimeMs]의 keyframe을 [toTimeMs]로 옮긴다. 도착 시간에 다른 keyframe이 있으면 거부한다. */
    public data class MoveKeyframe(
        val timelineId: String,
        val nodeId: NodeId,
        val property: AnimatableProperty,
        val fromTimeMs: Long,
        val toTimeMs: Long,
    ) : SceneCommand

    /** keyframe을 지운다. track이 비면 track도 지운다. */
    public data class DeleteKeyframes(
        val timelineId: String,
        val nodeId: NodeId,
        val property: AnimatableProperty,
        val timesMs: Set<Long>,
    ) : SceneCommand

    public data class SetKeyframeEasing(
        val timelineId: String,
        val nodeId: NodeId,
        val property: AnimatableProperty,
        val timeMs: Long,
        val easing: EasingSpec,
    ) : SceneCommand
}
