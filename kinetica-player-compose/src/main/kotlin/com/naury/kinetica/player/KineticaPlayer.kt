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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.Bindings
import com.naury.kinetica.core.scene.ComponentVariants
import com.naury.kinetica.core.scene.Layouts
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.motion.PlaybackController
import com.naury.kinetica.motion.TimelineEvaluator
import com.naury.kinetica.render.HOST_VIEW_UNITS_PER_DP
import com.naury.kinetica.render.LocalSceneFonts
import com.naury.kinetica.render.SceneImageProvider
import com.naury.kinetica.render.SceneLayout
import com.naury.kinetica.render.SceneSlots
import com.naury.kinetica.render.SceneTextMeasurer
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
 * @param numberAnimationMs 데이터의 숫자 값이 바뀌면 이 시간(ms) 동안 올라가거나 내려가며 바뀐다([animateSceneNumbers]).
 *   0이면 바로 바뀐다. 시스템 "애니메이션 삭제"가 켜져 있으면 바로 바뀐다.
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
    numberAnimationMs: Int = DEFAULT_NUMBER_ANIMATION_MS,
) {
    val data = animateSceneNumbers(data, if (rememberSystemReducedMotion()) 0 else numberAnimationMs)
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
    val fitted = remember(document, size, fit, density) { fitted(document, size, fit, density) }
    val viewport = remember(fitted.artboard, size) {
        val artboard = fitted.artboard
        Viewport.fitting(Rect(0.0, 0.0, artboard.width, artboard.height), size.first.toDouble(), size.second.toDouble())
    }
    val context = LocalContext.current
    val fonts = LocalSceneFonts.current
    val measurer = remember(context, fonts) { SceneTextMeasurer(context, fonts) }
    // 변형은 타임라인 평가 전에 한 번만 바꿔, 프레임마다 인스턴스를 다시 만들거나 키 값을 덮지 않게 한다.
    var pressed by remember { mutableStateOf<NodeId?>(null) }
    val resolved = remember(fitted, data, pressed) { withVariants(fitted, data, pressed) }
    val shown = remember(resolved, timeline, timeMs, data, measurer) {
        val evaluated = if (timeline == null) resolved else TimelineEvaluator.evaluate(resolved, timeline, timeMs)
        // 데이터로 글자가 바뀌면 이 기기 글꼴로 다시 재 글자에 맞춘 상자와 자동 배치가 따라간다.
        Bindings.apply(evaluated, data, measurer = measurer)
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
            onPressChange = { id, down -> pressed = if (down) id else pressed?.takeIf { it != id } },
        )
    }
}

/**
 * [timeline]을 재생하는 [PlaybackController]. 다시 구성돼도 같은 timeline이면 유지된다. 화면 회전 같은 구성 변경에서는 새로 만들므로
 * 재생 위치까지 지키려면 [PlaybackController]를 ViewModel에 둔다.
 *
 * @param reducedMotion 움직임 줄이기. 기본은 시스템 "애니메이션 삭제"(애니메이터 배율 0) 설정을 따른다.
 */
@Composable
public fun rememberPlaybackController(timeline: Timeline, reducedMotion: Boolean = rememberSystemReducedMotion()): PlaybackController =
    remember(timeline, reducedMotion) { PlaybackController(timeline, reducedMotion = reducedMotion) }

/** 시스템 "애니메이션 삭제"가 켜져 있는지(애니메이터 배율 0). */
@Composable
public fun rememberSystemReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** 데이터가 고른 변형으로 바꾸고, 누르고 있는 인스턴스는 눌림 변형으로 바꾼다. 바인딩보다 먼저 바꿔야 묶인 글자가 그대로다. */
private fun withVariants(document: SceneDocument, data: SceneData, pressed: NodeId?): SceneDocument {
    val chosen = Bindings.resolveVariants(document, data)
    return pressed?.let { ComponentVariants.pressed(chosen, it) } ?: chosen
}

/** [SceneFit.Responsive]면 artboard를 영역 크기(문서 단위)로 바꾼 문서. 그 밖에는 [document] 그대로다. */
private fun fitted(document: SceneDocument, size: Pair<Int, Int>, fit: SceneFit, density: Float): SceneDocument {
    val width = size.first / density * HOST_VIEW_UNITS_PER_DP
    val height = size.second / density * HOST_VIEW_UNITS_PER_DP
    return if (fit == SceneFit.Responsive && width > 0f && height > 0f) {
        Layouts.resizeArtboard(document, width.toDouble(), height.toDouble())
    } else {
        document
    }
}
