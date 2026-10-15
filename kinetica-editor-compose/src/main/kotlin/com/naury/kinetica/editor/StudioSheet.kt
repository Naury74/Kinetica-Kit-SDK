package com.naury.kinetica.editor

import android.animation.ValueAnimator
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 좁은 화면에서 패널을 띄우는 바텀 시트. 손잡이나 맨 위까지 스크롤된 내용을 아래로 끌면 따라 내려오고,
 * 손을 떼면 높이의 35%를 넘었거나 아래로 빠르게 던졌을 때 닫히고 아니면 스프링으로 제자리에 돌아간다.
 * 뒤로 가기와 배경 누르기도 닫는다. 내비게이션 바와 키보드 위에 놓인다.
 */
@Composable
internal fun StudioSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val theme = LocalStudioTheme.current
    val scope = rememberCoroutineScope()
    val fling = with(LocalDensity.current) { FLING_VELOCITY.toPx() }
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val sheet = remember { SheetMotion(scope, fling, !ValueAnimator.areAnimatorsEnabled()) { latestDismiss() } }
    Dialog(sheet::dismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = SCRIM_ALPHA * sheet.shownFraction))
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = sheet::dismiss),
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = SHEET_MAX_WIDTH)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * MAX_HEIGHT_FRACTION)
                    .onSizeChanged { sheet.measured(it.height) }
                    .offset { IntOffset(0, sheet.visibleOffset.roundToInt()) }
                    .background(theme.panel, SheetShape)
                    .nestedScroll(sheet.nested)
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
            ) {
                SheetHandle(sheet::dismiss, { sheet.drag(it) }, sheet::settle)
                Column(Modifier.weight(1f, fill = false), content = content)
            }
        }
    }
}

// 시트 위치(아래로 내려간 픽셀)와 끌기, 정착 애니메이션. 0이면 다 올라온 상태다.
private class SheetMotion(
    private val scope: CoroutineScope,
    private val fling: Float,
    private val reduced: Boolean,
    private val onDismissed: () -> Unit,
) {
    private var height by mutableIntStateOf(0)
    private var offset by mutableFloatStateOf(Float.MAX_VALUE)
    private var closing = false
    private var motion: Job? = null

    val shownFraction: Float get() = if (height == 0) 0f else (1f - offset / height).coerceIn(0f, 1f)
    val visibleOffset: Float get() = offset.coerceAtMost(height.toFloat())

    fun measured(value: Int) {
        val first = height == 0
        height = value
        if (first) {
            offset = value.toFloat()
            moveTo(0f)
        }
    }

    fun dismiss() {
        if (closing) return
        closing = true
        moveTo(height.toFloat()) { onDismissed() }
    }

    fun drag(delta: Float): Float {
        if (closing) return 0f
        motion?.cancel()
        val next = (offset + delta).coerceIn(0f, height.toFloat())
        val used = next - offset
        offset = next
        return used
    }

    // 높이의 35%를 넘게 내렸거나 아래로 빠르게 던지면 닫고, 아니면 스프링으로 제자리에 돌아간다.
    fun settle(velocity: Float) {
        if (closing) return
        if (velocity > fling || (offset > height * CLOSE_FRACTION && velocity > -fling)) dismiss() else moveTo(0f, velocity)
    }

    private fun moveTo(target: Float, velocity: Float = 0f, after: () -> Unit = {}) {
        motion?.cancel()
        motion = scope.launch {
            if (reduced) {
                offset = target
            } else {
                val spec: AnimationSpec<Float> = if (target > 0f) tween(CLOSE_MS) else spring(SETTLE_DAMPING, Spring.StiffnessMediumLow)
                animate(offset, target, velocity, spec) { value, _ -> offset = value }
            }
            after()
        }
    }

    val nested = object : NestedScrollConnection {
        // 시트가 내려가 있으면 위로 끄는 만큼 시트부터 올린다.
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0f && offset > 0f && source == NestedScrollSource.UserInput) Offset(0f, drag(available.y)) else Offset.Zero

        // 내용이 맨 위에 닿은 뒤 남은 아래쪽 끌기는 시트를 내린다.
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (available.y > 0f && source == NestedScrollSource.UserInput) Offset(0f, drag(available.y)) else Offset.Zero

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (offset <= 0f) return Velocity.Zero
            settle(available.y)
            return available
        }
    }
}

@Composable
private fun SheetHandle(onDismiss: () -> Unit, onDrag: (Float) -> Unit, onRelease: (Float) -> Unit) {
    val theme = LocalStudioTheme.current
    val description = stringResource(R.string.kinetica_close_sheet)
    Box(
        Modifier
            .fillMaxWidth()
            .height(HANDLE_AREA)
            .draggable(rememberDraggableState(onDrag), Orientation.Vertical, onDragStopped = { onRelease(it) })
            .semantics {
                contentDescription = description
                onClick {
                    onDismiss()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(HANDLE_WIDTH, HANDLE_HEIGHT).background(theme.onPanel.copy(alpha = HANDLE_ALPHA), CapsuleShape))
    }
}

private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val SHEET_MAX_WIDTH = 640.dp
private val HANDLE_AREA = 28.dp
private val HANDLE_WIDTH = 36.dp
private val HANDLE_HEIGHT = 4.dp
private val FLING_VELOCITY = 1000.dp
private const val MAX_HEIGHT_FRACTION = 0.9f
private const val CLOSE_FRACTION = 0.35f
private const val SCRIM_ALPHA = 0.32f
private const val HANDLE_ALPHA = 0.3f
private const val SETTLE_DAMPING = 0.85f
private const val CLOSE_MS = 200
