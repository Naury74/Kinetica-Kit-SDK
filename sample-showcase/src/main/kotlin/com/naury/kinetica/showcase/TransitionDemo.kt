// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
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
import com.naury.kinetica.motion.TransitionEnd
import com.naury.kinetica.player.SceneTransition
import com.naury.kinetica.player.SharedScenePlayer
import com.naury.kinetica.player.dragToDismiss
import com.naury.kinetica.player.rememberTransitionDriver
import com.naury.kinetica.ui.GlassScaffold
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.glassClickable

/**
 * 장면 전환 데모. 카드를 누르면 morphKey가 같은 노드끼리 이어지며 상세 화면이 된다. 아래로 끌거나 뒤로 가기를 누르면 닫히고,
 * 끄는 도중에 손을 떼면 속도에 따라 다시 열리거나 닫힌다. 편집기 없이 재생기 모듈만 쓴다.
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
        topBar = { DemoTopBar("장면 전환", "카드가 상세 화면으로 이어지고, 아래로 끌면 닫힙니다", onBack) },
    ) { padding ->
        LazyVerticalGrid(
            // 폰에서도 넓은 화면에서도 두 열로 둔다. 넓은 화면에서 네 장이 한 줄에 작게 놓이고 아래가 비지 않게 한다.
            columns = GridCells.Fixed(2),
            // 카드 칸마다 6dp 여백이 있어 그만큼 빼면 카드 가장자리가 상단 바 캡슐과 맞는다. 넓은 화면에서는 같은 읽기 폭으로 가운데 둔다.
            contentPadding = PaddingValues(
                start = readingPadding(16.dp) - 6.dp,
                end = readingPadding(16.dp) - 6.dp,
                top = padding.calculateTopPadding(),
                bottom =
                    navigationBarBottom() + 24.dp,
            ),
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
                            .glassClickable(RoundedCornerShape(20.dp)) { onOpen(item) }
                            .semantics { contentDescription = "${item.title} 열기" },
                    )
                }
            }
        }
    }
}

/** 카드 장면에서 상세 장면으로 morph한다. 완전히 닫혔을 때 [onClosed]를 한 번만 부른다. */
@Composable
private fun SceneDetail(item: GalleryItem, shared: SharedTransitionScope, visibility: AnimatedVisibilityScope, onClosed: () -> Unit) {
    val opening = rememberTransitionDriver()
    val closing = rememberTransitionDriver(onEnd = { if (it == TransitionEnd.Completed) onClosed() })
    LaunchedEffect(opening) { opening.animateTo(forward = true) }
    BackHandler { closing.animateTo(forward = true) }
    val open by opening.state.collectAsState()
    val close by closing.state.collectAsState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val travel = with(LocalDensity.current) { maxHeight.toPx() } * 0.6f
        // 상세 장면 높이를 화면 비율에 맞춰 위아래에 빈 띠가 생기지 않게 한다.
        val height = 1080.0 * maxHeight.value / maxWidth.value.coerceAtLeast(1f)
        val morph = remember(item, height) { item.morph(height) }
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

// 장면 바탕색과 같게 둬서 화면 비율이 달라도 위아래에 띠가 보이지 않게 한다.
private val DARK = Color(0xFF15181E)
