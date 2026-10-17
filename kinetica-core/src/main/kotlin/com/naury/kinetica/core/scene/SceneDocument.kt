package com.naury.kinetica.core.scene

import com.naury.kinetica.core.animation.Timeline

/**
 * 저장과 편집의 단위인 장면 문서. 불변이며, 편집하면 명령이 새 문서를 만든다.
 *
 * 좌표는 픽셀이나 dp가 아닌 문서 단위다. 원점은 [artboard]의 왼쪽 위이고 y축은 아래로 향한다.
 * 생성자는 구조를 검사하지 않으므로 외부에서 받은 문서는 [SceneValidator]로 확인해야 한다.
 *
 * @property nodes id로 찾는 모든 노드. 키는 노드의 id와 같아야 한다.
 * @property rootOrder 최상위 노드 순서. 뒤에 있을수록 위에 그려진다.
 * @property timelines 애니메이션 목록. 문서에 저장할 내용만 담고 재생 상태는 담지 않는다.
 * @property extensions 이 버전이 해석하지 않는 확장 데이터. 키별 JSON 문자열을 그대로 보존한다.
 * @property tokens 디자인 토큰 이름별 색. 노드는 [NodeStyle.fillToken]이나 [NodeStyle.strokeToken]에 이름을 적어 쓰며,
 *   토큰 값을 바꾸면 그 토큰을 쓰는 노드가 모두 바뀐다.
 * @property transition 다른 장면에서 이 장면으로 바뀔 때 쓰는 기본 전환. `null`이면 바로 바뀐다.
 */
public data class SceneDocument(
    val documentId: String,
    val artboard: Artboard,
    val nodes: Map<NodeId, SceneNode> = emptyMap(),
    val rootOrder: List<NodeId> = emptyList(),
    val timelines: List<Timeline> = emptyList(),
    val extensions: Map<String, String> = emptyMap(),
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val tokens: Map<String, ArgbColor> = emptyMap(),
    val transition: SceneTransitionSpec? = null,
) {
    public fun node(id: NodeId): SceneNode? = nodes[id]

    /** [parentId]의 자식 순서. `null`이면 최상위 순서를 돌려주고, 컨테이너가 아니거나 없는 노드면 빈 목록이다. */
    public fun childrenOf(parentId: NodeId?): List<NodeId> = if (parentId == null) {
        rootOrder
    } else {
        (nodes[parentId]?.content as? NodeContent.Container)?.childIds.orEmpty()
    }

    public companion object {
        /** 이 SDK가 읽고 쓰는 문서 스키마 버전. SDK 배포 버전과는 별개다. */
        public const val CURRENT_SCHEMA_VERSION: Int = 1

        public fun empty(artboard: Artboard, documentId: String = "scene"): SceneDocument =
            SceneDocument(documentId = documentId, artboard = artboard)
    }
}

/**
 * 문서의 작업 영역인 아트보드. 내보내기의 기본 크기로도 쓴다.
 *
 * @property width 문서 단위 너비. 0보다 커야 한다.
 * @property height 문서 단위 높이. 0보다 커야 한다.
 * @property background `null`이면 투명이다.
 */
public data class Artboard(val width: Double, val height: Double, val background: ArgbColor? = null)
