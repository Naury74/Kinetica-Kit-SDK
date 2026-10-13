package com.naury.kinetica.editor

import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.scene.NodeId

/** 시간 축에서 키를 고른 상태. */
internal data class SelectedKey(val nodeId: NodeId, val property: AnimatableProperty, val timeMs: Long)
