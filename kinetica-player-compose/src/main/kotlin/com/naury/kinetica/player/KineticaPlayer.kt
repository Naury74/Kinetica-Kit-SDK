package com.naury.kinetica.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.Bindings
import com.naury.kinetica.core.scene.Layouts
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.motion.PlaybackController
import com.naury.kinetica.motion.TimelineEvaluator
import com.naury.kinetica.render.HOST_VIEW_UNITS_PER_DP
import com.naury.kinetica.render.SceneImageProvider
import com.naury.kinetica.render.SceneLayout
import com.naury.kinetica.render.SceneSlots
import com.naury.kinetica.render.rememberSceneImages

/**
 * 읽기 전용 장면 재생기(docs 00 Player). 편집 도구·실행 취소·저장소에 의존하지 않는다.
 *
 * [fit]에 따라 artboard를 영역에 맞춰 그린다. [playback]이 있으면 그 재생 상태의 시각으로 평가해 그리고,
 * 재생 중에는 화면 프레임마다 [PlaybackController.onFrame]을 부른다. 없으면 정지 장면(timeline 0ms)을 그린다.
 *
 * @param playback 같은 문서의 timeline으로 만든 재생기. [rememberPlaybackController]로 만든다.
 * @param images 이미지 노드를 그릴 이미지. `null`이면 `content://` 이미지를 직접 읽는다.
 * @param data 바인딩에 쓸 호스트 데이터. 바뀌면 바로 다시 그린다.
 * @param slots 호스트 뷰 노드에 그릴 Composable(키 → 내용). 실제로 동작한다.
 * @param onAction 동작이 있는 노드를 누르면 동작 이름으로 불린다.
 * @param fit 영역에 맞추는 방식. 기본은 비율을 지키는 [SceneFit.Contain]이다.
 */
@Composable
public fun KineticaPlayer(
    document: SceneDocument,
    modifier: Modifier = Modifier,
    playback: PlaybackController? = null,
    images: SceneImageProvider? = null,
    background: Color = Color.Transparent,
    data: SceneData = SceneData.Empty,
    slots: SceneSlots = emptyMap(),
    onAction: (String) -> Unit = {},
    fit: SceneFit = SceneFit.Contain,
) {
    val sceneImages = images ?: rememberSceneImages(document)
    val timeline = playback?.timeline ?: document.timelines.firstOrNull()
    val state = playback?.state?.collectAsState()
    val timeMs = state?.value?.timeMs ?: 0.0
    if (playback != null && state?.value?.isPlaying == true) {
        LaunchedEffect(playback) {
            while (true) {
                withFrameNanos { }
                if (!playback.onFrame().state.isPlaying) break
            }
        }
    }
    var size by remember { mutableStateOf(0 to 0) }
    val density = LocalDensity.current.density
    val fitted = remember(document, size, fit, density) {
        val width = size.first / density * HOST_VIEW_UNITS_PER_DP
        val height = size.second / density * HOST_VIEW_UNITS_PER_DP
        if (fit == SceneFit.Responsive && width > 0f && height > 0f) {
            Layouts.resizeArtboard(document, width.toDouble(), height.toDouble())
        } else {
            document
        }
    }
    val viewport = remember(fitted.artboard, size) {
        val artboard = fitted.artboard
        Viewport.fitting(Rect(0.0, 0.0, artboard.width, artboard.height), size.first.toDouble(), size.second.toDouble())
    }
    val shown = remember(fitted, timeline, timeMs, data) {
        val evaluated = if (timeline == null) fitted else TimelineEvaluator.evaluate(fitted, timeline, timeMs)
        Bindings.apply(evaluated, data)
    }
    Box(modifier.onSizeChanged { size = it.width to it.height }) {
        SceneLayout(
            shown,
            viewport,
            Modifier.matchParentSize().background(background),
            sceneImages,
            slots,
            interactive = true,
            onAction = onAction,
            clipToArtboard = true,
        )
    }
}

/** [timeline]을 재생하는 [PlaybackController]. 구성이 바뀌어도 같은 timeline이면 유지된다. */
@Composable
public fun rememberPlaybackController(timeline: Timeline, reducedMotion: Boolean = false): PlaybackController =
    remember(timeline, reducedMotion) { PlaybackController(timeline, reducedMotion = reducedMotion) }
