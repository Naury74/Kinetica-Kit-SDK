package com.naury.kinetica.core.scene

/**
 * 문서 검증에서 찾은 문제 하나. [detail]에는 텍스트, URI, 이름 같은 사용자 콘텐츠를 넣지 않으므로 로그에 남겨도 된다.
 *
 * @property nodeId 문제가 있는 노드의 id. 특정 노드의 문제가 아니면 `null`이다.
 */
public data class SceneViolation(val code: ViolationCode, val detail: String, val nodeId: NodeId? = null)

public enum class ViolationCode {
    InvalidDocumentId,
    UnsupportedSchemaVersion,
    InvalidArtboard,
    TooManyNodes,
    NodeKeyMismatch,
    MissingNode,
    ParentMismatch,
    ParentNotGroup,
    DuplicateChild,
    UnreachableNode,
    Cycle,
    TooDeep,
    InvalidTransform,
    InvalidStyle,
    InvalidGeometry,
    InvalidText,
    InvalidUri,
    InvalidName,
    InvalidMetadata,
    InvalidTimeline,
    InvalidTrack,
    InvalidKeyframe,
    InvalidEasing,
    TooManyKeyframes,
    InvalidExtension,
    InvalidBinding,
}
