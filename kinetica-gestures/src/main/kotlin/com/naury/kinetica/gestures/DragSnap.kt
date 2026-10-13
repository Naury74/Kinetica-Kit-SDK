package com.naury.kinetica.gestures

import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneGraph

/**
 * 옮기기 제스처 하나 동안 이동량을 고친다(안내선 스냅 등). 제스처가 끝나거나 취소되면 [finish]가 불린다.
 */
public interface DragSnap {
    /** 손가락 이동량 [documentDelta]를 고친 문서 이동량. [zoom]은 지금 viewport 배율이다. */
    public fun adjust(documentDelta: Vec2, zoom: Double): Vec2

    public fun finish()
}

/** 옮기기를 시작할 때 [DragSnap]을 만든다. 스냅하지 않으면 `null`. */
public fun interface DragSnapFactory {
    public fun start(graph: SceneGraph, moving: List<NodeId>): DragSnap?
}
