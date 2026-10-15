// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.naury.kinetica.motion.MorphResult
import com.naury.kinetica.motion.SceneMorph
import com.naury.kinetica.motion.TransitionEnd
import com.naury.kinetica.player.SceneTransition
import com.naury.kinetica.player.SharedScenePlayer
import com.naury.kinetica.player.dragToDismiss
import com.naury.kinetica.player.rememberTransitionDriver
import com.naury.kinetica.ui.GlassScaffold
import com.naury.kinetica.ui.GlassStyles

/**
 * 장면 전환: 카드를 누르면 같은 morphKey끼리 이어지며 상세 화면이 된다. 아래로 끌거나 뒤로 가기로 되돌아가며,
 * 끄는 도중 놓으면 속도에 따라 다시 열리거나 닫힌다. 재생기 모듈만 쓴다(편집기 없음).
 */
@Composable
internal fun TransitionDemo(onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = GalleryScenes.items.firstOrNull { it.id == selectedId }
    SharedTransitionLayout(Modifier.fillMaxSize().background(DARK)) {
        AnimatedContent(targetState = selected, label = "transition-demo") { item ->
            if (item == null) {
                SceneGrid(this@SharedTransitionLayout, this, onBack) { selectedId = it.id }
            } else {
                SceneDetail(item, this@SharedTransitionLayout, this) { selectedId = null }
            }
        }
    }
}

@Composable
private fun SceneGrid(
    shared: SharedTransitionScope,
    visibility: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onOpen: (GalleryItem) -> Unit,
) {
    GlassScaffold(
        Modifier.fillMaxSize(),
        style = GlassStyles.LiquidDark,
        background = { Aurora(Modifier.fillMaxSize(), dark = true) },
        topBar = { DemoTopBar("장면 전환", "카드를 누르면 이어지며 열리고, 아래로 끌면 닫힌다", onBack) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
        ) {
            items(GalleryScenes.items, key = { it.id }) { item ->
                with(shared) {
                    SharedScenePlayer(
                        key = item.id,
                        document = item.card,
                        animatedVisibilityScope = visibility,
                        modifier = Modifier
                            .padding(6.dp)
                            .aspectRatio(1080f / 1920f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onOpen(item) }
                            .semantics { contentDescription = "${item.title} 열기" },
                    )
                }
            }
        }
    }
}

/** 카드 장면에서 상세 장면으로 morph한다. 다 닫힌 때 한 번만 [onClosed]를 부른다. */
@Composable
private fun SceneDetail(item: GalleryItem, shared: SharedTransitionScope, visibility: AnimatedVisibilityScope, onClosed: () -> Unit) {
    val morph = remember(item) { (SceneMorph.create(item.card, item.detail) as MorphResult.Ready).morph }
    val opening = rememberTransitionDriver()
    val closing = rememberTransitionDriver(onEnd = { if (it == TransitionEnd.Completed) onClosed() })
    LaunchedEffect(opening) { opening.animateTo(forward = true) }
    BackHandler { closing.animateTo(forward = true) }
    val open by opening.state.collectAsState()
    val close by closing.state.collectAsState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val travel = with(LocalDensity.current) { maxHeight.toPx() } * 0.6f
        with(shared) {
            Box(Modifier.fillMaxSize().sharedBounds(rememberSharedContentState(item.id), visibility).dragToDismiss(closing, travel)) {
                SceneTransition(morph, open.progress * (1.0 - close.progress), Modifier.fillMaxSize(), background = DARK)
                Label(
                    "아래로 끌어 닫기",
                    13,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp),
                )
            }
        }
    }
}

private val DARK = Color(0xFF101216)
