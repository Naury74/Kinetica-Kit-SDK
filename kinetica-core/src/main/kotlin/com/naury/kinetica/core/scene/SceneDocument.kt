package com.naury.kinetica.core.scene

import com.naury.kinetica.core.animation.Timeline

/**
 * 저장·편집 단위인 scene 문서. 불변이며 편집은 명령으로 새 문서를 만든다.
 *
 * 좌표는 픽셀·dp가 아닌 문서 단위이고 원점은 [artboard] 왼쪽 위, y축은 아래로 향한다.
 * 생성자는 구조를 검사하지 않으므로 외부에서 받은 문서는 [SceneValidator]로 확인한다.
 *
 * @property nodes id로 찾는 모든 노드. 키는 노드의 id와 같아야 한다.
 * @property rootOrder 최상위 노드 순서. 뒤에 있을수록 위에 그려진다.
 * @property timelines 애니메이션. 문서 상태만 담고 재생 상태는 담지 않는다.
 * @property extensions 이 버전이 해석하지 않는 확장 데이터. 키별 JSON 문자열을 그대로 보존한다.
 * @property tokens 디자인 토큰(이름 → 색). 노드의 [NodeStyle.fillToken]·[NodeStyle.strokeToken]이 이름으로 쓰며, 바꾸면 쓰는 노드가 모두 바뀐다.
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
) {
    public fun node(id: NodeId): SceneNode? = nodes[id]

    /** [parentId]의 자식 순서. `null`이면 최상위 순서다. 컨테이너가 아니거나 없으면 빈 목록이다. */
    public fun childrenOf(parentId: NodeId?): List<NodeId> = if (parentId == null) {
        rootOrder
    } else {
        (nodes[parentId]?.content as? NodeContent.Container)?.childIds.orEmpty()
    }

    public companion object {
        /** 이 SDK가 읽고 쓰는 문서 형식 버전. SDK 배포 버전과 별개다. */
        public const val CURRENT_SCHEMA_VERSION: Int = 1

        public fun empty(artboard: Artboard, documentId: String = "scene"): SceneDocument =
            SceneDocument(documentId = documentId, artboard = artboard)
    }
}

/**
 * 문서의 작업 영역. 내보내기 기본 크기이기도 하다.
 *
 * @property width 문서 단위, 0보다 커야 한다.
 * @property height 문서 단위, 0보다 커야 한다.
 * @property background `null`이면 투명이다.
 */
public data class Artboard(val width: Double, val height: Double, val background: ArgbColor? = null)
