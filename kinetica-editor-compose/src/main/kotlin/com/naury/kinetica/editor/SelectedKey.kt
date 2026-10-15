package com.naury.kinetica.editor

import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.scene.NodeId

// 타임라인에서 선택한 keyframe.
internal data class SelectedKey(val nodeId: NodeId, val property: AnimatableProperty, val timeMs: Long)
