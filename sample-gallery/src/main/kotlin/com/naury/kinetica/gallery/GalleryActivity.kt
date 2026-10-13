package com.naury.kinetica.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.res.stringResource
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

class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Gallery()
            }
        }
    }
}

private val background = Color(0xFF101216)
private val hintColor = Color(0x99FFFFFF)

@Composable
private fun Gallery() {
    // 고른 장면 id. 회전·재생성 뒤에도 같은 화면을 보여 준다(docs 20 Journey C).
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = GalleryScenes.items.firstOrNull { it.id == selectedId }
    SharedTransitionLayout(Modifier.fillMaxSize().background(background)) {
        AnimatedContent(targetState = selected, label = "gallery") { item ->
            if (item == null) {
                SceneGrid(this@SharedTransitionLayout, this) { selectedId = it.id }
            } else {
                SceneDetail(item, this@SharedTransitionLayout, this) { selectedId = null }
            }
        }
    }
}

@Composable
private fun SceneGrid(shared: SharedTransitionScope, visibility: AnimatedVisibilityScope, onOpen: (GalleryItem) -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Text(
            stringResource(R.string.gallery_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(16.dp),
        )
        LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), contentPadding = PaddingValues(12.dp)) {
            items(GalleryScenes.items, key = { it.id }) { item ->
                val description = stringResource(R.string.gallery_open, item.title)
                with(shared) {
                    SharedScenePlayer(
                        key = item.id,
                        document = item.card,
                        animatedVisibilityScope = visibility,
                        modifier = Modifier
                            .padding(6.dp)
                            .aspectRatio(CARD_ASPECT)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOpen(item) }
                            .semantics { contentDescription = description },
                    )
                }
            }
        }
    }
}

/**
 * 카드 장면에서 상세 장면으로 morph한다. 열릴 때는 0→1로 가고, 아래로 끌거나 뒤로 가기를 누르면 되돌아가며
 * 다 닫힌 때 한 번만 [onClosed]를 부른다. 끄는 도중 놓으면 속도에 따라 다시 열리거나 닫힌다(docs 20 Journey B).
 */
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
        val travel = with(LocalDensity.current) { maxHeight.toPx() } * DISMISS_TRAVEL_FRACTION
        with(shared) {
            Box(
                Modifier
                    .fillMaxSize()
                    .sharedBounds(rememberSharedContentState(item.id), visibility)
                    .dragToDismiss(closing, travel),
            ) {
                SceneTransition(morph, open.progress * (1.0 - close.progress), Modifier.fillMaxSize(), background = background)
                Text(
                    stringResource(R.string.gallery_hint),
                    color = hintColor,
                    modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp),
                )
            }
        }
    }
}

private const val CARD_ASPECT = 1080f / 1920f
private const val DISMISS_TRAVEL_FRACTION = 0.6f
