package com.naury.kinetica.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.motion.PlaybackController
import com.naury.kinetica.render.SceneSlots
import com.naury.kinetica.serialization.RemoteSceneBundleLoader
import com.naury.kinetica.serialization.RemoteSceneLoader
import com.naury.kinetica.serialization.RemoteSceneState

/**
 * 서버가 내려준 장면을 재생한다. 처음 화면에 보일 때와 [refreshKey]가 바뀔 때마다 [RemoteSceneLoader.refresh]를 호출한다.
 * 받아 오는 중이거나 실패했을 때 캐시나 기본 문서가 있으면 그 문서를 그리고, 없으면 [placeholder]를 그린다.
 *
 * 데이터, 호스트 뷰, 동작은 [KineticaPlayer]와 같다. 서버 문서에 앱이 모르는 동작 이름이 있어도 [onAction]에서 무시하면 아무 일도 일어나지 않는다.
 *
 * @param autoplay 문서의 첫 timeline을 바로 재생할지. 문서가 바뀌면 새 timeline을 처음부터 재생한다. 화면을 회전해도 재생 위치를 이어 가고,
 *   이미 끝난 애니메이션은 다시 재생하지 않는다.
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
 * 서버가 여러 화면을 번들로 한 번에 내려줄 때 그중 [sceneId] 화면을 재생한다. 처음 보일 때와 [refreshKey]가 바뀔 때 번들을 새로 받으며,
 * 같은 번들을 쓰는 화면이 여러 개 열려 있어도 요청은 한 번만 보낸다. 나머지는 [RemoteKineticaPlayer]와 같다.
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

/** 재생기에 그대로 넘기는 값을 모은 객체. */
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
    if (playback != null) AutoPlay(playback)
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

/**
 * [playback]을 처음부터 재생한다. 화면 회전이나 프로세스 종료로 다시 만들어졌으면 저장해 둔 위치부터 이어서 재생하고,
 * 이미 끝난 애니메이션이었으면 다시 재생하지 않고 끝 장면에 둔다. 호스트가 컨트롤러를 갖지 않으므로 위치는 여기서 저장한다.
 */
@Composable
private fun AutoPlay(playback: PlaybackController) {
    val current by rememberUpdatedState(playback)
    val saved = rememberSaveable(
        saver = Saver<SavedPosition, DoubleArray>(
            save = { current.state.value.let { doubleArrayOf(it.timeMs, if (it.isFinished) 1.0 else 0.0) } },
            restore = { SavedPosition(it[0], it[1] == 1.0) },
        ),
    ) { SavedPosition(null, false) }
    LaunchedEffect(playback) {
        val restored = saved.timeMs
        // 저장한 위치는 다시 만든 첫 컨트롤러에만 쓴다. 문서가 바뀌어 새로 만든 컨트롤러는 처음부터 재생한다.
        saved.timeMs = null
        when {
            restored == null -> playback.play()

            saved.finished -> playback.seek(playback.timeline.durationMs.toDouble())

            else -> {
                playback.seek(restored)
                playback.play()
            }
        }
    }
}

private class SavedPosition(var timeMs: Double?, val finished: Boolean)
