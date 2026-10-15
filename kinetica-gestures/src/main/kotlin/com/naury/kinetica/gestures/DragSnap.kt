package com.naury.kinetica.gestures

import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneGraph

/**
 * 이동 제스처 하나가 진행되는 동안 안내선 스냅 등으로 이동량을 보정한다. 제스처가 끝나거나 취소되면 [finish]가 호출된다.
 */
public interface DragSnap {
    /** 손가락 이동량 [documentDelta]를 보정한 문서 이동량. [zoom]은 현재 viewport 배율이다. */
    public fun adjust(documentDelta: Vec2, zoom: Double): Vec2

    public fun finish()
}

/** 이동을 시작할 때 [DragSnap]을 만든다. 스냅하지 않으면 `null`을 돌려준다. */
public fun interface DragSnapFactory {
    public fun start(graph: SceneGraph, moving: List<NodeId>): DragSnap?
}
