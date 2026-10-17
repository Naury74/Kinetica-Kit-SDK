package com.naury.kinetica.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneTransitionKind
import com.naury.kinetica.core.scene.SceneTransitionSpec
import com.naury.kinetica.motion.Easing
import com.naury.kinetica.motion.MorphResult
import com.naury.kinetica.motion.SceneMorph
import com.naury.kinetica.render.SceneImageProvider
import com.naury.kinetica.render.SceneLayout
import com.naury.kinetica.render.SceneSlots
import com.naury.kinetica.render.SceneTransitionRole
import com.naury.kinetica.render.incomingOnTop
import com.naury.kinetica.render.sceneTransitionLayer

/**
 * [sceneKey]가 바뀌면 이전 장면에서 새 장면으로 전환하며 [content]를 그린다. 키가 같은 문서로 바뀌면(서버에서 새로 받은 같은 화면 등)
 * 전환 없이 바로 바꾼다. 전환은 [transition]을 쓰고, `null`이면 새 문서의 [SceneDocument.transition]을, 그것도 없으면 바로 바꾼다.
 *
 * 전환하는 동안에는 두 장면 모두 터치를 받지 않고, 나가는 장면은 TalkBack에서 숨긴다. 전환이 끝나면 새 장면만 남으므로 접근성 초점이 새 장면으로 옮겨 간다.
 * '애니메이션 삭제' 설정([reducedMotion])이 켜져 있으면 바로 바꾼다. 전환 중에 문서가 또 바뀌면 지금 보이는 장면에서 새 전환을 시작한다.
 *
 * ```kotlin
 * AnimatedScene(scenes.getValue(current), forward = !goingBack) { scene ->
 *     KineticaPlayer(scene, onAction = { raw -> SceneAction.parse(raw)["scene"]?.let { current = it } })
 * }
 * ```
 *
 * [SceneTransitionKind.Morph]는 두 장면에서 `morphKey`나 id가 같은 노드를 이어 그린 장면을 [content]에 넘긴다. 이어 그린 장면에는 timeline이 없다.
 * 짝을 지을 수 없는 두 장면이면 페이드로 바꾼다.
 *
 * @param sceneKey 장면을 구분하는 값. 기본값은 `documentId`다. 번들처럼 여러 화면이 같은 `documentId`를 쓰면 화면 id를 넘긴다.
 * @param forward 앞으로 가는 이동이면 `true`. `false`면 같은 전환을 거꾸로 재생해, 들어오는 장면이 반대쪽에서 나오고 덮었던 장면이 걷힌다.
 * @param onTransitionEnd 전환이 끝까지 진행되면 호출한다. 바로 바꾼 경우와 도중에 다른 전환으로 바뀐 경우에는 호출하지 않는다.
 * @param content 장면 하나를 그린다. 보통 [KineticaPlayer]를 부른다. 장면마다 [sceneKey]로 따로 구성하므로 전환이 끝나도 새 장면의 상태가 유지된다.
 */
@Composable
public fun AnimatedScene(
    document: SceneDocument,
    modifier: Modifier = Modifier,
    transition: SceneTransitionSpec? = null,
    forward: Boolean = true,
    sceneKey: Any = document.documentId,
    reducedMotion: Boolean = rememberSystemReducedMotion(),
    onTransitionEnd: () -> Unit = {},
    content: @Composable (SceneDocument) -> Unit,
) {
    val switch = remember { SceneSwitch(KeyedScene(sceneKey, document)) }
    val latestEnd by rememberUpdatedState(onTransitionEnd)
    LaunchedEffect(sceneKey) {
        if (switch.follow(
                KeyedScene(sceneKey, document),
                animatedSpec(transition ?: document.transition, reducedMotion),
                forward,
            )
        ) {
            latestEnd()
        }
    }
    // 바로 바꾸는 경우에는 효과가 상태를 바꾸기를 기다리지 않고 이번 프레임부터 새 장면을 그린다.
    val immediate = sceneKey == switch.current.key || animatedSpec(transition ?: document.transition, reducedMotion) == null
    val current = if (immediate) KeyedScene(sceneKey, document) else switch.current
    // 같은 키로 문서만 바뀌면(서버에서 다시 받음, 번들 재생기가 키보다 한 프레임 늦게 넘긴 문서) 기억한 장면도 바꾼다.
    // 그러지 않으면 다음 전환에서 나가는 장면을 옛 문서로 그린다.
    SideEffect {
        val remembered = switch.current
        if (remembered.key == sceneKey && remembered.document !== document) switch.current = KeyedScene(sceneKey, document)
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(modifier) {
        val previous = switch.previous?.takeIf { it.key != current.key && current.key == switch.current.key }
        val spec = switch.spec
        val morph = remember(previous, current, spec) {
            if (previous != null &&
                spec?.kind == SceneTransitionKind.Morph
            ) {
                (SceneMorph.create(previous.document, current.document) as? MorphResult.Ready)?.morph
            } else {
                null
            }
        }
        if (morph != null) {
            key(MORPH_KEY) { Box(Modifier.fillMaxSize().blockTouches()) { content(morph.frame(switch.eased().toDouble())) } }
        } else {
            TransitionLayers(previous.takeIf { spec != null }, current, spec?.fadeIfMorph(), switch.forward, rtl, switch::eased, content)
        }
    }
}

/**
 * 두 장면 사이의 전환을 호스트가 정한 진행도 [progress](0..1)로 그린다. 예측형 뒤로 가기나 [dragToDismiss]처럼 손가락을 따라 움직이는 전환에 쓴다.
 * [progress]에는 easing을 적용하지 않고 그대로 쓰며, 그리기 단계에서 읽으므로 매 프레임 다시 구성하지 않는다. 단 [SceneTransitionKind.Morph]는
 * 진행도마다 장면을 새로 만들어야 하므로 진행도가 바뀔 때마다 다시 구성한다. 두 장면은 각자의 artboard를 영역에 맞춰 그리며 누를 수 없다.
 *
 * @param forward `false`면 같은 전환을 거꾸로 그린다. [AnimatedScene]의 같은 이름 파라미터와 같다.
 */
@Composable
public fun SceneTransition(
    from: SceneDocument,
    to: SceneDocument,
    spec: SceneTransitionSpec,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    forward: Boolean = true,
    images: SceneImageProvider = SceneImageProvider.None,
    background: Color = Color.Transparent,
) {
    val morph = remember(from, to, spec.kind) {
        if (spec.kind == SceneTransitionKind.Morph) (SceneMorph.create(from, to) as? MorphResult.Ready)?.morph else null
    }
    if (morph != null) {
        SceneTransition(morph, progress().toDouble(), modifier, images, background)
        return
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(modifier) {
        TransitionLayers(KeyedScene(FROM_KEY, from), KeyedScene(TO_KEY, to), spec.fadeIfMorph(), forward, rtl, progress) { scene ->
            StillScene(scene, images, background)
        }
    }
}

/**
 * 두 장면을 [spec]에 맞는 순서로 겹쳐 그린다. [outgoing]이 없으면 [incoming]만 그린다. 어느 경우든 같은 자리에서 장면 키로 그리므로
 * 전환이 끝나도 장면을 새로 구성하지 않는다.
 */
@Composable
private fun TransitionLayers(
    outgoing: KeyedScene?,
    incoming: KeyedScene,
    spec: SceneTransitionSpec?,
    forward: Boolean,
    rtl: Boolean,
    progress: () -> Float,
    content: @Composable (SceneDocument) -> Unit,
) {
    val layers = when {
        outgoing == null || spec == null -> listOf(incoming)
        spec.incomingOnTop(forward) -> listOf(outgoing, incoming)
        else -> listOf(incoming, outgoing)
    }
    layers.forEach { scene ->
        key(scene.key) {
            val layer = if (spec == null || outgoing == null) {
                Modifier
            } else {
                val role = if (scene === incoming) SceneTransitionRole.Incoming else SceneTransitionRole.Outgoing
                val hidden = if (role == SceneTransitionRole.Outgoing) Modifier.clearAndSetSemantics { } else Modifier
                Modifier.sceneTransitionLayer(spec, role, forward, rtl, progress).then(hidden).blockTouches()
            }
            Box(Modifier.fillMaxSize().then(layer)) { content(scene.document) }
        }
    }
}

/** 재생하지 않는 장면 하나를 영역에 맞춰 그린다. */
@Composable
private fun StillScene(document: SceneDocument, images: SceneImageProvider, background: Color) {
    var size by remember { mutableStateOf(0 to 0) }
    val viewport = remember(document.artboard, size) {
        Viewport.fitting(Rect(0.0, 0.0, document.artboard.width, document.artboard.height), size.first.toDouble(), size.second.toDouble())
    }
    Box(Modifier.fillMaxSize().onSizeChanged { size = it.width to it.height }) {
        SceneLayout(document, viewport, Modifier.matchParentSize().background(background), images)
    }
}

// 전환 중에 누른 것이 아래 장면이나 들어오는 중인 장면에 전달되지 않게 모든 터치를 먼저 소비한다.
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}

private fun animatedSpec(spec: SceneTransitionSpec?, reducedMotion: Boolean): SceneTransitionSpec? =
    spec?.takeUnless { reducedMotion || it.kind == SceneTransitionKind.None || it.durationMs == 0 }

private fun SceneTransitionSpec.fadeIfMorph(): SceneTransitionSpec = if (kind ==
    SceneTransitionKind.Morph
) {
    copy(kind = SceneTransitionKind.Fade)
} else {
    this
}

/** [AnimatedScene]이 그리는 장면과 전환 상태 */
@Stable
private class SceneSwitch(initial: KeyedScene) {
    var current by mutableStateOf(initial)
    var previous by mutableStateOf<KeyedScene?>(null)
    var spec by mutableStateOf<SceneTransitionSpec?>(null)
    var forward = true
    val progress = Animatable(1f)

    // 가장 최근에 시작한 전환. 취소된 전환이 늦게 정리하면서 새 전환의 상태를 지우지 않게 구분한다.
    var token: Any? = null

    /**
     * [next]로 바꾼다. [spec]이 없으면 바로 바꾸고, 있으면 전환을 끝까지 진행한 뒤 `true`를 돌려준다.
     * 도중에 다른 전환이 시작되면 이 전환은 취소되고, 이전 장면은 새 전환이 정한 값을 그대로 둔다.
     */
    suspend fun follow(next: KeyedScene, spec: SceneTransitionSpec?, isForward: Boolean): Boolean {
        if (current.key == next.key) return false
        val mine = Any()
        token = mine
        if (spec == null) {
            show(next)
            return false
        }
        start(next, spec, isForward)
        try {
            progress.animateTo(1f, tween(spec.durationMs, easing = LinearEasing))
            return token === mine
        } finally {
            if (token === mine) previous = null
        }
    }

    fun show(document: KeyedScene) {
        previous = null
        current = document
    }

    suspend fun start(document: KeyedScene, next: SceneTransitionSpec, isForward: Boolean) {
        progress.snapTo(0f)
        previous = current
        current = document
        spec = next
        forward = isForward
    }

    fun eased(): Float {
        val s = spec ?: return 1f
        return Easing.evaluate(s.easing, progress.value.toDouble(), s.durationMs.toDouble()).toFloat()
    }
}

/** [key]로 구분하는 장면 */
private data class KeyedScene(val key: Any, val document: SceneDocument)

private const val MORPH_KEY = "kinetica.scene.morph"
private const val FROM_KEY = "kinetica.scene.from"
private const val TO_KEY = "kinetica.scene.to"
