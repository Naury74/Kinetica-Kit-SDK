package com.naury.kinetica.core.command

import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.AutoLayout
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.SceneTransitionSpec

/**
 * 문서를 바꾸는 유일한 방법. [SceneReducer]가 명령을 적용하고 결과 문서 전체를 검증한다.
 * 명령은 값 객체이므로 기록하거나 다시 실행하거나 테스트에 그대로 쓸 수 있다.
 */
public sealed interface SceneCommand {
    /**
     * 노드를 [SceneNode.parentId] 그룹의 [index] 위치에 넣는다. 부모가 없으면 최상위에 넣는다. [index]가 `null`이면 목록 끝, 즉 맨 위다.
     * 그룹은 빈 상태로만 넣을 수 있고, 자식은 그 뒤에 하나씩 넣는다.
     */
    public data class AddNode(val node: SceneNode, val index: Int? = null) : SceneCommand

    /** 노드와 그 자손을 모두 지운다. 지운 노드를 가리키는 track도 함께 지운다. */
    public data class RemoveNodes(val ids: Set<NodeId>) : SceneCommand

    /** 노드의 속성을 [node]로 바꾼다. id, 종류, 부모, 자식 목록은 바꿀 수 없다. */
    public data class UpdateProperties(val node: SceneNode) : SceneCommand

    /** 여러 노드의 로컬 변환을 한꺼번에 바꾼다. */
    public data class TransformNodes(val transforms: Map<NodeId, Transform2D>) : SceneCommand

    /** 같은 부모 안에서 노드의 순서를 [toIndex]로 옮긴다. 0이 맨 아래다. */
    public data class ReorderNode(val id: NodeId, val toIndex: Int) : SceneCommand

    /**
     * 같은 부모를 가진 [ids]를 새 그룹 [groupId]로 묶는다. 그룹은 묶인 노드 중 맨 위 노드가 있던 위치에 들어가고,
     * 자식 순서는 원래 형제 순서를 따른다. 그룹 변환은 항등 변환이므로 화면 위치는 바뀌지 않는다.
     */
    public data class Group(val ids: List<NodeId>, val groupId: NodeId, val name: String = "") : SceneCommand

    /**
     * 같은 부모를 가진 [ids]를 새 Frame [frameId]로 감싼다. Frame 크기는 노드들의 범위에 [layout]의 여백을 더한 값이며 화면 위치는 바뀌지 않는다.
     * [layout]이 있으면 자식 순서를 배치 방향의 위치 순서로 정렬한 뒤 자동 레이아웃을 적용한다.
     */
    public data class WrapInFrame(val ids: List<NodeId>, val frameId: NodeId, val layout: AutoLayout? = null, val name: String = "") :
        SceneCommand

    /**
     * 그룹을 풀어 자식을 그룹이 있던 부모와 순서 위치에 놓는다. 그룹의 변환, opacity, 숨김, 잠금을 자식에 합쳐 화면 모습을 유지한다.
     * 그룹 변환을 합친 결과에 기울임(skew)이 생기면 거부한다.
     */
    public data class Ungroup(val groupId: NodeId) : SceneCommand

    public data class SetArtboard(val artboard: Artboard) : SceneCommand

    /** 이 장면이 들어올 때 쓰는 기본 전환을 [transition]으로 정한다. `null`이면 지운다. */
    public data class SetTransition(val transition: SceneTransitionSpec?) : SceneCommand

    /** 디자인 토큰 [name]의 색을 [color]로 정한다. `null`이면 토큰을 지우며, 연결돼 있던 노드는 현재 색을 그대로 유지한다. */
    public data class SetToken(val name: String, val color: ArgbColor?) : SceneCommand

    /** 같은 id의 timeline이 있으면 교체하고 없으면 추가한다. */
    public data class PutTimeline(val timeline: Timeline) : SceneCommand

    public data class RemoveTimeline(val timelineId: String) : SceneCommand

    /** keyframe을 추가한다. 같은 시간에 keyframe이 있으면 교체하고, track이 없으면 새로 만든다. */
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

    /** keyframe을 지운다. track이 비면 track도 함께 지운다. */
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
