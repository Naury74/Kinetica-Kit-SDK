package com.naury.kinetica.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 카드 하나를 옆으로 미는 상태. 손가락을 따라가는 값은 [x]에 바로 쓰고, 놓은 뒤 돌아가거나 날아가는 움직임만 애니메이션으로 돌린다.
 * 값은 그리기 단계(graphicsLayer)에서만 읽는다.
 */
@Stable
internal class SwipeAwayState {
    var x by mutableFloatStateOf(0f)
    var width = 0f

    // '애니메이션 삭제'면 놓은 뒤 바로 자리를 바꾼다. 카드를 구성할 때마다 맞춘다.
    var still = false
    private var settle: Job? = null

    fun stop() {
        settle?.cancel()
        settle = null
    }

    /**
     * 놓은 위치와 속도(px/s)로 지울지 정한다. [flingThreshold](px/s)보다 빠르게 민 쪽으로 튕기면 덜 밀었어도 지운다.
     * 손을 떼지 않고 취소된 끌기는 [completed]가 false라 늘 제자리로 돌아간다.
     */
    fun release(scope: CoroutineScope, completed: Boolean, velocity: Float, flingThreshold: Float, onDismiss: () -> Unit) {
        val far = abs(x) > width * DISMISS_SHARE
        val flung = abs(velocity) > flingThreshold && sign(velocity) == sign(x)
        val dismiss = completed && width > 0f && (far || flung)
        val target = if (dismiss) sign(x) * width * OUT_SHARE else 0f
        settle = scope.launch {
            if (still) {
                x = target
            } else if (dismiss) {
                animate(x, target, initialVelocity = velocity, animationSpec = tween(OUT_MS)) { v, _ -> x = v }
            } else {
                animate(x, target, initialVelocity = velocity, animationSpec = spring(stiffness = Spring.StiffnessMedium)) { v, _ -> x = v }
            }
            if (dismiss) onDismiss()
        }
    }
}

/**
 * 가로로 밀어 지우는 손짓. 세로로 먼저 움직이면 부모 스크롤에 넘기고, 가로 터치 슬롭을 넘은 뒤에만 가로챈다.
 * Compose는 취소된 터치를 소비된 손 뗌으로 보내므로 [horizontalDrag]가 false를 돌려주고, 그때는 지우지 않는다.
 */
internal suspend fun PointerInputScope.swipeAway(swipe: SwipeAwayState, scope: CoroutineScope, onDismiss: () -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        swipe.width = size.width.toFloat()
        val tracker = VelocityTracker()
        tracker.addPointerInputChange(down)
        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            swipe.stop()
            swipe.x += over
        } ?: return@awaitEachGesture
        val completed = horizontalDrag(start.id) { change ->
            tracker.addPointerInputChange(change)
            swipe.x += change.positionChange().x
            change.consume()
        }
        swipe.release(scope, completed, tracker.calculateVelocity().x, FLING_DP_PER_S.toPx(), onDismiss)
    }
}

/** 카드 안 내용. 앱 이름과 시각, 굵은 제목, 본문을 놓는다. [more]가 0보다 크면 시각 옆에 남은 알림 수를 붙인다. */
@Composable
internal fun NotificationBody(
    appName: String,
    item: KNotification,
    more: String?,
    icon: (@Composable () -> Unit)?,
    surface: SurfaceStyle,
) {
    val muted = surface.muted(MUTED_ALPHA)
    Row(Modifier.fillMaxWidth().padding(CARD_PADDING), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) Box(Modifier.size(ICON_SIZE), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThemedText(
                    appName,
                    Modifier.weight(1f, fill = false),
                    style = TextStyle(color = muted, fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                if (more != null) {
                    ThemedText(more, style = TextStyle(color = muted, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                }
                ThemedText(item.time, style = TextStyle(color = muted, fontSize = 13.sp), maxLines = 1)
            }
            ThemedText(
                item.title,
                style = TextStyle(color = surface.contentColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ThemedText(
                item.text,
                style = TextStyle(color = surface.contentColor, fontSize = 15.sp),
                maxLines = BODY_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 펼친 묶음 위 줄. 앱 이름과 '간단히', '모두 지우기' 버튼을 놓는다. */
@Composable
internal fun NStackHeader(
    appName: String,
    surface: SurfaceStyle,
    shape: Shape,
    labels: NStackLabels,
    onCollapse: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ThemedText(
            appName,
            Modifier.weight(1f),
            style = TextStyle(color = surface.contentColor, fontSize = 20.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        KButton(
            labels.showLess,
            onCollapse,
            variant = KVariant.Tonal,
            tone = KTone.Neutral,
            size = KButtonSize.Small,
            surface = surface,
            shape = shape,
        )
        Spacer(Modifier.width(8.dp))
        KIconButton(
            KIcons.Close,
            labels.clearAll,
            onClearAll,
            variant = KVariant.Tonal,
            tone = KTone.Neutral,
            size = CLEAR_SIZE,
            surface = surface,
        )
    }
}

/** 묶음이 쓰는 번역 글. 구성마다 한 번 읽어 넘긴다. */
internal class NStackLabels(val showLess: String, val clearAll: String, val dismiss: String, val expand: String)

private const val DISMISS_SHARE = 0.4f
private const val OUT_SHARE = 1.15f
private const val OUT_MS = 180
private const val MUTED_ALPHA = 0.6f
private const val BODY_LINES = 3
private val FLING_DP_PER_S = 700.dp
private val CARD_PADDING = 14.dp
private val ICON_SIZE = 32.dp
private val CLEAR_SIZE = 32.dp
