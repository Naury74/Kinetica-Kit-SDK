package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 유리 위 글자·아이콘 색. 유리 요소 안에서 [GlassStyle.onGlass]로 정해진다. */
public val LocalGlassContentColor: androidx.compose.runtime.ProvidableCompositionLocal<Color> =
    staticCompositionLocalOf { Color(0xFF15161A) }

/** 유리 표면 하나. [backdrop]을 비추며(기본은 둘러싼 화면) 내용 색은 [LocalGlassContentColor]로 받는다. */
@Composable
public fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    style: GlassStyle = LocalGlassStyle.current,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.glass(style, shape, backdrop)) {
        CompositionLocalProvider(LocalGlassContentColor provides style.onGlass) { content() }
    }
}

/**
 * 유리 팝업. 열리면 뒤 화면이 서서히 흐려지고 어두워지며, 판이 [origin](누른 자리, 화면 좌표)에서 튀어나오듯 커진다.
 * 바깥을 누르거나 뒤로 가기로 [onDismissRequest]를 부른다. [GlassScaffold] 안에서 쓰면 뒤 화면을 흐리게 비춘다.
 *
 * @param origin 판이 커져 나올 자리. [Modifier.glassOrigin]으로 버튼 가운데를 얻는다. `null`이면 화면 가운데에서 커진다.
 */
@Composable
public fun GlassDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassStyle = LocalGlassStyle.current,
    shape: Shape = RoundedCornerShape(32.dp),
    origin: Offset? = null,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassOverlay(visible) { transition ->
        val progress by transition.animateFloat(
            transitionSpec = { if (targetState) spring(dampingRatio = DIALOG_DAMPING, stiffness = DIALOG_STIFFNESS) else tween(CLOSE_MS) },
            label = "glass-dialog",
        ) { if (it) 1f else 0f }
        BackHandler(enabled = visible, onBack = onDismissRequest)
        val level = progress.coerceIn(0f, 1f)
        var panelCenter by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize()) {
            Scrim(level, onDismissRequest)
            Column(
                modifier
                    .align(Alignment.Center)
                    .padding(28.dp)
                    .widthIn(max = DIALOG_MAX_WIDTH)
                    .onGloballyPositioned { panelCenter = it.boundsInRoot().center }
                    .graphicsLayer {
                        val scale = DIALOG_START_SCALE + (1f - DIALOG_START_SCALE) * progress
                        scaleX = scale
                        scaleY = scale
                        alpha = level
                        // 누른 자리에서 가운데로 날아오며 커진다.
                        val from = origin ?: panelCenter
                        translationX = (from.x - panelCenter.x) * (1f - level)
                        translationY = (from.y - panelCenter.y) * (1f - level)
                        transformOrigin = TransformOrigin.Center
                    }
                    .glass(style.forPopup(), shape)
                    .semantics {
                        title?.let { paneTitle = it }
                        dismiss {
                            onDismissRequest()
                            true
                        }
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(24.dp),
            ) {
                CompositionLocalProvider(LocalGlassContentColor provides style.onGlass) { content() }
            }
        }
    }
}

/**
 * 아래에서 올라오는 유리 시트. 열리면 뒤 내용이 살짝 물러나고 둥글어지며([GlassScaffold] 안), 손잡이·내용을 아래로 끌어 닫는다.
 */
@Composable
public fun GlassSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassStyle = LocalGlassStyle.current,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassOverlay(visible) { transition ->
        val progress by transition.animateFloat(
            transitionSpec = {
                if (targetState) spring(dampingRatio = SHEET_DAMPING, stiffness = Spring.StiffnessMediumLow) else tween(CLOSE_MS)
            },
            label = "glass-sheet",
        ) { if (it) 1f else 0f }
        BackHandler(enabled = visible, onBack = onDismissRequest)
        val drag = remember { Animatable(0f) }
        var height by remember { mutableFloatStateOf(1f) }
        val level = progress.coerceIn(0f, 1f)
        val dragged = (drag.value / height).coerceIn(0f, 1f)
        ReportPushBack(level * (1f - dragged))
        Box(Modifier.fillMaxSize()) {
            Scrim(level * (1f - dragged), onDismissRequest)
            Column(
                modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
                    .graphicsLayer { translationY = height * (1f - progress) + drag.value }
                    .glass(style.forPopup(), RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER))
                    .semantics {
                        title?.let { paneTitle = it }
                        dismiss {
                            onDismissRequest()
                            true
                        }
                    }
                    .dragToDismiss(drag, { height }, onDismissRequest)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 12.dp)
                        .size(40.dp, 5.dp)
                        .background(style.onGlass.copy(alpha = 0.28f), RoundedCornerShape(3.dp)),
                )
                CompositionLocalProvider(LocalGlassContentColor provides style.onGlass) { content() }
                Box(Modifier.padding(bottom = 16.dp))
            }
        }
    }
}

/** 아래로 끌어 닫기. 높이의 30% 넘게 끌거나 빠르게 튕기면 닫고, 아니면 제자리로 돌아온다. */
@Composable
private fun Modifier.dragToDismiss(drag: Animatable<Float, *>, height: () -> Float, onDismiss: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    return draggable(
        rememberDraggableState { delta -> scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) } },
        Orientation.Vertical,
        onDragStopped = { velocity ->
            if (drag.value > height() * SHEET_DISMISS_FRACTION || velocity > SHEET_FLING) {
                onDismiss()
                drag.animateTo(0f, tween(CLOSE_MS))
            } else {
                drag.animateTo(0f, spring(dampingRatio = SHEET_DAMPING))
            }
        },
    )
}

/** 팝업 뒤 배경: 서서히 흐려지고 어두워진다. 누르면 닫는다. */
@Composable
internal fun BoxScope.Scrim(level: Float, onDismiss: () -> Unit) {
    Box(
        Modifier
            .matchParentSize()
            .glass(GlassStyles.Clear, intensity = level)
            .background(Color.Black.copy(alpha = SCRIM_ALPHA * level))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    )
}

/** 이 요소 가운데(화면 좌표)를 [onOrigin]으로 알린다. [GlassDialog]의 `origin`에 넘겨 누른 버튼에서 팝업이 커져 나오게 한다. */
public fun Modifier.glassOrigin(onOrigin: (Offset) -> Unit): Modifier = onGloballyPositioned { onOrigin(it.boundsInRoot().center) }

private const val DIALOG_DAMPING = 0.72f
private const val DIALOG_STIFFNESS = 420f
private const val DIALOG_START_SCALE = 0.6f
private val DIALOG_MAX_WIDTH = 420.dp
private const val SHEET_DAMPING = 0.85f
private val SHEET_CORNER = 32.dp
private const val SHEET_DISMISS_FRACTION = 0.3f
private const val SHEET_FLING = 1800f
private const val SCRIM_ALPHA = 0.22f
private const val CLOSE_MS = 180
