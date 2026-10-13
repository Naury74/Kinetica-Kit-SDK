package com.naury.kinetica.player

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
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.motion.PlaybackController
import com.naury.kinetica.motion.TimelineEvaluator
import com.naury.kinetica.render.SceneCanvas
import com.naury.kinetica.render.SceneImageProvider

/**
 * 읽기 전용 장면 재생기(docs 00 Player). 편집 도구·실행 취소·저장소에 의존하지 않는다.
 *
 * artboard를 영역 가운데에 꽉 차게 맞춰 그린다. [playback]이 있으면 그 재생 상태의 시각으로 평가해 그리고,
 * 재생 중에는 화면 프레임마다 [PlaybackController.onFrame]을 부른다. 없으면 정지 장면(timeline 0ms)을 그린다.
 *
 * @param playback 같은 문서의 timeline으로 만든 재생기. [rememberPlaybackController]로 만든다.
 */
@Composable
public fun KineticaPlayer(
    document: SceneDocument,
    modifier: Modifier = Modifier,
    playback: PlaybackController? = null,
    images: SceneImageProvider = SceneImageProvider.None,
    background: Color = Color.Transparent,
) {
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
    val viewport = remember(document.artboard, size) {
        val artboard = document.artboard
        Viewport.fitting(Rect(0.0, 0.0, artboard.width, artboard.height), size.first.toDouble(), size.second.toDouble())
    }
    val shown = remember(document, timeline, timeMs) {
        if (timeline == null) document else TimelineEvaluator.evaluate(document, timeline, timeMs)
    }
    Box(modifier.onSizeChanged { size = it.width to it.height }) {
        SceneCanvas(shown, viewport, Modifier.matchParentSize(), images, artboardOutside = background)
    }
}

/** [timeline]을 재생하는 [PlaybackController]. 구성이 바뀌어도 같은 timeline이면 유지된다. */
@Composable
public fun rememberPlaybackController(timeline: Timeline, reducedMotion: Boolean = false): PlaybackController =
    remember(timeline, reducedMotion) { PlaybackController(timeline, reducedMotion = reducedMotion) }
