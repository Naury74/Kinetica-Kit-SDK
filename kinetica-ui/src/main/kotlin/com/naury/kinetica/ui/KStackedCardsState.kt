package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * [KStackedCards]의 상태. 카드가 겹쳐 쌓인 상태, 펼친 상태, 한 장을 고른 상태 셋 중 하나다. [rememberKStackedCardsState]로 만든다.
 * 화면 회전 뒤에도 남는다.
 */
@Stable
public class KStackedCardsState internal constructor(expanded: Boolean, selected: Int?) {
    /** 펼쳐져 있는지. 한 장을 고른 동안에도 true다. */
    public var expanded: Boolean by mutableStateOf(expanded || selected != null)
        private set

    /** 고른 카드 번호. 고르지 않았으면 null이다. */
    public var selected: Int? by mutableStateOf(selected)
        private set

    /** 쌓인 카드를 펼친다. */
    public fun expand() {
        expanded = true
    }

    /** 선택을 풀고 다시 쌓는다. */
    public fun collapse() {
        selected = null
        expanded = false
    }

    /** [index]번 카드를 앞으로 꺼낸다. 음수면 [IllegalArgumentException]을 던진다. 카드 수보다 크면 고르지 않은 것처럼 그린다. */
    public fun select(index: Int) {
        require(index >= 0) { "index는 0 이상이어야 합니다." }
        expanded = true
        selected = index
    }

    /** 선택을 풀고 펼친 상태로 돌아간다. */
    public fun clearSelection() {
        selected = null
    }

    internal companion object {
        val Saver: Saver<KStackedCardsState, List<Int>> = Saver(
            save = { listOf(if (it.expanded) 1 else 0, it.selected ?: -1) },
            restore = { KStackedCardsState(it[0] == 1, it[1].takeIf { i -> i >= 0 }) },
        )
    }
}

/** [KStackedCardsState]를 만든다. [initiallyExpanded]가 true면 펼친 채로 시작한다. */
@Composable
public fun rememberKStackedCardsState(initiallyExpanded: Boolean = false): KStackedCardsState =
    rememberSaveable(saver = KStackedCardsState.Saver) { KStackedCardsState(initiallyExpanded, null) }
