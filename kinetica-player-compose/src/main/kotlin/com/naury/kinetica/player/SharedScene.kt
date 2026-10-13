package com.naury.kinetica.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.motion.PlaybackController
import com.naury.kinetica.render.SceneImageProvider
import com.naury.kinetica.render.SceneSlots

/**
 * 호스트의 Compose 공유 전환 안에서 장면을 그린다(docs 09 host bridge, ADR-005). 같은 [key]를 쓴 두 화면 사이에서
 * Compose가 범위를 이어 준다. navigation과 [AnimatedVisibilityScope]는 호스트가 소유하며, 이 함수는 이를 바꾸지 않는다.
 */
@Composable
public fun SharedTransitionScope.SharedScenePlayer(
    key: Any,
    document: SceneDocument,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
    playback: PlaybackController? = null,
    images: SceneImageProvider? = null,
    background: Color = Color.Transparent,
    data: SceneData = SceneData.Empty,
    slots: SceneSlots = emptyMap(),
    onAction: (String) -> Unit = {},
) {
    KineticaPlayer(
        document = document,
        modifier = modifier.sharedBounds(rememberSharedContentState(key), animatedVisibilityScope),
        playback = playback,
        images = images,
        background = background,
        data = data,
        slots = slots,
        onAction = onAction,
    )
}
