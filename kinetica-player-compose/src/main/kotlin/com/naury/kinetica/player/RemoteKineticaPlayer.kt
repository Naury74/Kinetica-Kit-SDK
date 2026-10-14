package com.naury.kinetica.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.render.SceneSlots
import com.naury.kinetica.serialization.RemoteSceneBundleLoader
import com.naury.kinetica.serialization.RemoteSceneLoader
import com.naury.kinetica.serialization.RemoteSceneState

/**
 * 서버가 내려준 장면을 재생한다. 처음 보일 때와 [refreshKey]가 바뀔 때마다 [RemoteSceneLoader.refresh]를 부른다.
 * 받는 중이거나 실패해도 보여 줄 문서(캐시·기본 문서)가 있으면 그것을 그리고, 없으면 [placeholder]를 그린다.
 *
 * 데이터·호스트 뷰·동작은 [KineticaPlayer]와 같다. 서버 문서가 모르는 동작 이름을 보내도 [onAction]이 무시하면 아무 일도 없다.
 *
 * @param autoplay 문서의 첫 timeline을 바로 재생한다. 문서가 바뀌면 새 timeline을 처음부터 재생한다.
 */
@Composable
public fun RemoteKineticaPlayer(
    loader: RemoteSceneLoader,
    modifier: Modifier = Modifier,
    refreshKey: Any? = Unit,
    data: SceneData = SceneData.Empty,
    slots: SceneSlots = emptyMap(),
    onAction: (String) -> Unit = {},
    fit: SceneFit = SceneFit.Contain,
    background: Color = Color.Transparent,
    autoplay: Boolean = false,
    placeholder: @Composable (RemoteSceneState) -> Unit = {},
) {
    LaunchedEffect(loader, refreshKey) { loader.refresh() }
    val state by loader.state.collectAsState()
    RemoteContent(state, modifier, RemoteOptions(data, slots, onAction, fit, background, autoplay), placeholder)
}

/**
 * 서버가 여러 화면을 한 번에 내려줄 때 그중 [sceneId] 화면을 재생한다. 처음 보일 때와 [refreshKey]가 바뀔 때 묶음을 새로 고치며,
 * 같은 묶음을 쓰는 화면 여러 개가 함께 열려도 받기는 한 번이다. 나머지는 [RemoteKineticaPlayer]와 같다.
 */
@Composable
public fun RemoteKineticaPlayer(
    bundle: RemoteSceneBundleLoader,
    sceneId: String,
    modifier: Modifier = Modifier,
    refreshKey: Any? = Unit,
    data: SceneData = SceneData.Empty,
    slots: SceneSlots = emptyMap(),
    onAction: (String) -> Unit = {},
    fit: SceneFit = SceneFit.Contain,
    background: Color = Color.Transparent,
    autoplay: Boolean = false,
    placeholder: @Composable (RemoteSceneState) -> Unit = {},
) {
    LaunchedEffect(bundle, refreshKey) { bundle.refresh() }
    val flow = remember(bundle, sceneId) { bundle.state(sceneId) }
    val state by flow.collectAsState(RemoteSceneState.Loading)
    RemoteContent(state, modifier, RemoteOptions(data, slots, onAction, fit, background, autoplay), placeholder)
}

/** 재생기에 그대로 넘기는 값 묶음. */
private class RemoteOptions(
    val data: SceneData,
    val slots: SceneSlots,
    val onAction: (String) -> Unit,
    val fit: SceneFit,
    val background: Color,
    val autoplay: Boolean,
)

@Composable
private fun RemoteContent(
    state: RemoteSceneState,
    modifier: Modifier,
    options: RemoteOptions,
    placeholder: @Composable (RemoteSceneState) -> Unit,
) {
    val ready = state as? RemoteSceneState.Ready
    if (ready == null) {
        placeholder(state)
        return
    }
    val timeline = ready.document.timelines.firstOrNull()
    val playback = if (options.autoplay && timeline != null) rememberPlaybackController(timeline) else null
    if (playback != null) LaunchedEffect(playback) { playback.play() }
    KineticaPlayer(
        ready.document,
        modifier,
        playback = playback,
        background = options.background,
        data = options.data,
        slots = options.slots,
        onAction = options.onAction,
        fit = options.fit,
    )
}
