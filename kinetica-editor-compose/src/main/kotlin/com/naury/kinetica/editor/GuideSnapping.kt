package com.naury.kinetica.editor

import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.gestures.DragSnap
import com.naury.kinetica.gestures.DragSnapFactory
import com.naury.kinetica.guides.MoveSnapper
import com.naury.kinetica.guides.SnapConfig

/** 옮기는 동안 [MoveSnapper]로 이동량을 고치고, 붙은 안내선을 [EditorState.guides]에 둔다. */
internal class GuideSnapping(private val state: EditorState, private val config: SnapConfig) : DragSnapFactory {
    override fun start(graph: SceneGraph, moving: List<NodeId>): DragSnap {
        val snapper = MoveSnapper(graph, moving, config)
        return object : DragSnap {
            override fun adjust(documentDelta: Vec2, zoom: Double): Vec2 {
                val result = snapper.snap(documentDelta, zoom)
                state.guides = result.guides
                return result.delta
            }

            override fun finish() {
                state.guides = emptyList()
            }
        }
    }
}
