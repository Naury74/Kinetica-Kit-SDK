package com.naury.kinetica.core.command

import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneViolation

/** 명령 적용 결과. 거부되면 원래 문서는 그대로다. */
public sealed interface CommandResult {
    public data class Applied(val document: SceneDocument) : CommandResult

    public data class Rejected(val error: CommandError) : CommandResult
}

/** 명령을 거부한 이유. 사용자 콘텐츠를 담지 않는다. */
public sealed interface CommandError {
    public data class NodeNotFound(val id: NodeId) : CommandError

    public data class NodeAlreadyExists(val id: NodeId) : CommandError

    /** [SceneCommand.UpdateProperties]가 바꿀 수 없는 id·종류·부모·자식 목록을 바꾸려 했다. */
    public data class StructureChange(val id: NodeId) : CommandError

    /** 묶을 노드들의 부모가 서로 다르거나 비었다. */
    public data class MixedParents(val ids: List<NodeId>) : CommandError

    /** 변환을 합친 결과가 회전·배율·이동만으로 나타낼 수 없다(기울임). */
    public data class UnrepresentableTransform(val id: NodeId) : CommandError

    public data class IndexOutOfRange(val index: Int, val size: Int) : CommandError

    public data class TimelineNotFound(val timelineId: String) : CommandError

    public data class TrackNotFound(val nodeId: NodeId, val property: AnimatableProperty) : CommandError

    public data class KeyframeNotFound(val nodeId: NodeId, val property: AnimatableProperty, val timeMs: Long) : CommandError

    public data class KeyframeCollision(val nodeId: NodeId, val property: AnimatableProperty, val timeMs: Long) : CommandError

    /** 명령을 적용한 결과가 문서 규칙을 어긴다. */
    public data class InvalidResult(val violations: List<SceneViolation>) : CommandError

    /** 트랜잭션 규칙 위반(중첩 시작, 시작 없는 commit 등). */
    public data class TransactionState(val reason: String) : CommandError
}
